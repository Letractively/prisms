/*
 * DefaultConnectionFactory.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;

import prisms.arch.*;
import prisms.arch.ds.Transactor;
import prisms.arch.ds.Transactor.ReconnectListener;
import prisms.arch.ds.Transactor.Thrower;

/** A utility class to help with persistence */
public class DefaultConnectionFactory implements prisms.arch.ConnectionFactory
{
	static final Logger log = Logger.getLogger(DefaultConnectionFactory.class);

	class DefaultTransactor implements Cloneable
	{
		private prisms.arch.PrismsConfig theConnConfig;

		private String theDuplicateID;

		private java.util.concurrent.locks.ReentrantReadWriteLock theLock;

		private volatile java.sql.Connection theConn;

		private int theConnectionID;

		private long theLastValidCheck;

		private String theTablePrefix;

		private prisms.arch.ds.Transactor.ReconnectListener[] theListeners;

		private int theRefCount;

		DefaultTransactor(prisms.arch.PrismsConfig connEl, String duplicateID)
		{
			theConnConfig = connEl;
			theDuplicateID = duplicateID;
			theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
			theListeners = new ReconnectListener [0];
		}

		void referred()
		{
			theRefCount++;
		}

		String getDuplicateID()
		{
			return theDuplicateID;
		}

		synchronized void addReconnectListener(ReconnectListener listener)
		{
			theListeners = prisms.util.ArrayUtils.add(theListeners, listener);
		}

		synchronized boolean removeReconnectListener(ReconnectListener listener)
		{
			int idx = prisms.util.ArrayUtils.indexOf(theListeners, listener);
			if(idx >= 0)
				theListeners = prisms.util.ArrayUtils.remove(theListeners, listener);
			return idx >= 0;
		}

		String getTablePrefix()
		{
			return theTablePrefix;
		}

		ReentrantReadWriteLock getLock()
		{
			return theLock;
		}

		int getConnectionID()
		{
			return theConnectionID;
		}

		@Override
		protected DefaultTransactor clone()
		{
			DefaultTransactor ret;
			try
			{
				ret = (DefaultTransactor) super.clone();
			} catch(CloneNotSupportedException e)
			{
				throw new IllegalStateException("Clone not supported", e);
			}
			ret.theConn = null;
			ret.theConnectionID = 0;
			// Keep the same lock since it points to the same database
			// ret.theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
			ret.theLastValidCheck = 0;
			ret.theListeners = new ReconnectListener [0];
			ret.theDuplicateID = Integer.toHexString(ret.hashCode());
			return ret;
		}

		<T extends Throwable> Connection getConnection(Thrower<T> thrower) throws T
		{
			checkConnected(thrower);
			return theConn;
		}

		<T extends Throwable> boolean checkConnected(Thrower<T> thrower) throws T
		{
			int initID = theConnectionID;
			boolean initial = theConn == null;
			boolean reconnect = initial;
			if(theConn != null)
			{
				try
				{
					if(theConn.isClosed())
					{
						log.warn("Connection closed!");
						theConn = null;
						reconnect = true;
					}
					long now = System.currentTimeMillis();
					if(theConn != null && now - theLastValidCheck > 90000
						&& prisms.util.PrismsUtils.isJava6())
					{
						if(!theConn.isValid(60))
						{
							log.warn("Connection is not valid! Reconnecting. Config=\n"
								+ theConnConfig);
							try
							{
								theConn.close();
							} catch(SQLException e)
							{}
							theConn = null;
							reconnect = true;
						}
						theLastValidCheck = now;
					}
				} catch(SQLException e)
				{
					log.warn("Transactor could not check closed and valid status of connection."
						+ " Config=\n" + theConnConfig, e);
					theConn = null;
					reconnect = true;
				} catch(NullPointerException e)
				{
					reconnect = true;
				}
			}
			if(!reconnect || theConnectionID != initID)
				return false;
			synchronized(this)
			{
				if(theConnectionID != initID)
					return false;
				try
				{
					theConn = DefaultConnectionFactory.this.connect(theConnConfig, theDuplicateID);
					theTablePrefix = DefaultConnectionFactory.this.getTablePrefix(theConnConfig);
					theConnectionID = (int) Math.round(Math.random() * Integer.MAX_VALUE);
					theLastValidCheck = System.currentTimeMillis();
					for(ReconnectListener listener : theListeners)
					{
						try
						{
							listener.reconnected(initial);
						} catch(RuntimeException e)
						{
							log.error("Reconnect Listener error: ", e);
						}
					}
				} catch(Exception e)
				{
					thrower.error("Transactor could not get connection!", e);
				}
			}
			return true;
		}

		<T extends Throwable> Object performTransaction(
			prisms.arch.ds.Transactor.TransactionOperation<? extends T> op, String ifError,
			Thrower<T> thrower) throws T
		{
			Statement stmt = null;
			checkConnected(thrower);
			boolean oldAutoCommit = true;
			boolean completed = false;
			java.util.concurrent.locks.Lock lock = theLock.writeLock();
			lock.lock();
			try
			{
				try
				{
					oldAutoCommit = theConn.getAutoCommit();
					theConn.setAutoCommit(false);
					stmt = theConn.createStatement();
				} catch(SQLException e)
				{
					thrower.error("Connection error: ", e);
				}
				Object ret = op.run(stmt);
				try
				{
					theConn.commit();
				} catch(SQLException e)
				{
					thrower.error(ifError, e);
				}
				completed = true;
				return ret;
			} finally
			{
				try
				{
					if(!completed)
					{
						try
						{
							theConn.rollback();
						} catch(SQLException e)
						{
							log.error("Transactor could not perform rollback", e);
						}
					}
					if(stmt != null)
					{
						try
						{
							stmt.close();
						} catch(SQLException e)
						{
							log.error("Connection error", e);
						}
					}
					try
					{
						theConn.setAutoCommit(oldAutoCommit);
					} catch(SQLException e)
					{
						log.error("Connection error", e);
					}
				} finally
				{
					lock.unlock();
				}
			}
		}

		<T extends Throwable, T2> T2 getDBItem(Statement stmt, String sql, Class<T2> type,
			Thrower<T> thrower) throws T
		{
			boolean closeStmt = stmt == null;
			java.sql.ResultSet rs = null;
			try
			{
				if(stmt == null)
					stmt = getConnection(thrower).createStatement();

				rs = stmt.executeQuery(sql);
				if(!rs.next())
					thrower.error("No rows selected: SQL=" + sql);
				Object ret;
				if(type != null)
				{
					if(String.class.equals(type))
						ret = rs.getString(1);
					else if(Number.class.isAssignableFrom(type))
						ret = Number.class.cast(rs.getObject(1));
					else if(java.util.Date.class.equals(type))
						ret = new java.util.Date(rs.getTimestamp(1).getTime());
					else if(byte.class.equals(type))
					{
						java.io.InputStream stream = rs.getBinaryStream(1);
						java.io.ByteArrayOutputStream retStr = new java.io.ByteArrayOutputStream();
						try
						{
							int read = stream.read();
							while(read >= 0)
							{
								retStr.write(read);
								read = stream.read();
							}
						} catch(java.io.IOException e)
						{
							thrower.error("Could not read binary data: SQL=" + sql, e);
							throw new IllegalStateException("Thrower didn't throw an exception!");
						} finally
						{
							try
							{
								stream.close();
							} catch(java.io.IOException e)
							{
								log.error("Could not close database input stream", e);
							}
						}
						return (T2) retStr.toByteArray();

					}
					else
					{
						thrower.error("Unrecognized databased item type: " + type.getName());
						throw new IllegalStateException("Thrower didn't throw an exception!");
					}
					ret = type.cast(ret);
				}
				else
				{
					int count = rs.getMetaData().getColumnCount();
					if(count == 1)
						return (T2) rs.getObject(1);
					Object [] retA = new Object [count];
					for(int i = 0; i < count; i++)
						retA[i] = rs.getObject(i + 1);
					ret = retA;
				}
				if(rs.next())
				{
					thrower.error("Multiple rows selected: SQL=" + sql);
					throw new IllegalStateException("Thrower didn't throw an exception!");
				}
				rs.close();
				rs = null;
				return (T2) ret;
			} catch(SQLException e)
			{
				thrower.error("Could not get databased field: SQL=" + sql, e);
				throw new IllegalStateException("Thrower didn't throw an exception!");
			} catch(ClassCastException e)
			{
				thrower.error("Databased field is not of type " + type.getName() + ": SQL=" + sql,
					e);
				throw new IllegalStateException("Thrower didn't throw an exception!");
			} finally
			{
				if(rs != null)
					try
					{
						rs.close();
					} catch(SQLException e)
					{
						log.error("Connection error", e);
					}
				if(stmt != null && closeStmt)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{
						log.error("Connection error", e);
					}
			}
		}

		synchronized void release()
		{
			for(ReconnectListener listener : theListeners)
			{
				try
				{
					listener.released();
				} catch(RuntimeException e)
				{
					log.error("Disconnect Listener error: ", e);
				}
			}
			theConnectionID = 0;
			theLock = null;
			if(theConn != null)
			{
				DefaultConnectionFactory.this.released(theConnConfig, theConn);
				theConn = null;
			}
		}
	}

	static class TransactorImpl<T extends Throwable> implements prisms.arch.ds.Transactor<T>
	{
		private final PrismsConfig theConnectionConfig;

		private DefaultTransactor theDefaultTransactor;

		private final Thrower<T> theThrower;

		private prisms.arch.ds.Transactor.ReconnectListener[] theListeners;

		private boolean isReleased;

		TransactorImpl(PrismsConfig connConfig, DefaultTransactor defTrans, Thrower<T> thrower)
		{
			theConnectionConfig = connConfig;
			theDefaultTransactor = defTrans;
			theThrower = thrower;
			theListeners = new ReconnectListener [0];
		}

		public PrismsConfig getConnectionConfig()
		{
			return theConnectionConfig;
		}

		public String getDuplicateID()
		{
			return theDefaultTransactor.getDuplicateID();
		}

		public synchronized void addReconnectListener(ReconnectListener listener)
		{
			if(isReleased)
				throw new IllegalStateException("This transactor has been released");
			theListeners = prisms.util.ArrayUtils.add(theListeners, listener);
			theDefaultTransactor.addReconnectListener(listener);
		}

		public synchronized boolean removeReconnectListener(ReconnectListener listener)
		{
			if(isReleased)
				throw new IllegalStateException("This transactor has been released");
			theListeners = prisms.util.ArrayUtils.remove(theListeners, listener);
			return theDefaultTransactor.removeReconnectListener(listener);
		}

		public String getTablePrefix()
		{
			return theDefaultTransactor.getTablePrefix();
		}

		public ReentrantReadWriteLock getLock()
		{
			if(isReleased)
				throw new IllegalStateException("This transactor has been released");
			return theDefaultTransactor.getLock();
		}

		public prisms.arch.ds.Transactor.Thrower<T> getThrower()
		{
			return theThrower;
		}

		@Override
		public prisms.arch.ds.Transactor<T> clone()
		{
			if(isReleased)
				throw new IllegalStateException("This transactor has been released");
			TransactorImpl<T> ret;
			try
			{
				ret = (TransactorImpl<T>) super.clone();
			} catch(CloneNotSupportedException e)
			{
				throw new IllegalStateException("Clone not supported", e);
			}
			ret.theListeners = new ReconnectListener [0];
			ret.theDefaultTransactor = theDefaultTransactor.clone();
			return ret;
		}

		public int getConnectionID()
		{
			if(isReleased)
				return 0;
			return theDefaultTransactor.getConnectionID();
		}

		public Connection getConnection() throws T
		{
			if(isReleased)
				theThrower.error("This transactor has been released");
			return theDefaultTransactor.getConnection(theThrower);
		}

		public boolean checkConnected() throws T
		{
			if(isReleased)
				theThrower.error("This transactor has been released");
			return theDefaultTransactor.checkConnected(theThrower);
		}

		public <T2 extends T> Object performTransaction(
			prisms.arch.ds.Transactor.TransactionOperation<T2> op, String ifError) throws T
		{
			if(isReleased)
				theThrower.error("This transactor has been released");
			return theDefaultTransactor.performTransaction(op, ifError, theThrower);
		}

		public <T2> T2 getDBItem(Statement stmt, String sql, Class<T2> type) throws T
		{
			if(isReleased)
				theThrower.error("This transactor has been released");
			return theDefaultTransactor.getDBItem(stmt, sql, type, theThrower);
		}

		public synchronized void release()
		{
			if(isReleased)
				return;
			for(ReconnectListener listener : theListeners)
			{
				theDefaultTransactor.removeReconnectListener(listener);
				try
				{
					listener.released();
				} catch(RuntimeException e)
				{
					log.error("Disconnect Listener error: ", e);
				}
			}
			theListeners = new ReconnectListener [0];
			isReleased = true;
		}
	}

	private static Thrower<SQLException> DEFAULT_THROWER;

	static
	{
		DEFAULT_THROWER = new Thrower<SQLException>()
		{
			public void error(String message) throws SQLException
			{
				throw new SQLException(message);
			}

			public void error(String message, Throwable cause) throws SQLException
			{
				throw new SQLException(message, cause);
			}
		};
	}

	private boolean isConfigured;

	private Map<String, PrismsConfig> theNamedConnEls;

	private Map<String, String> theConnectionAliases;

	private Map<String, Map<String, DefaultTransactor>> theCoreConnections;

	/** Creates this persister factory */
	public DefaultConnectionFactory()
	{
		theNamedConnEls = new HashMap<String, PrismsConfig>();
		theConnectionAliases = new HashMap<String, String>();
		theCoreConnections = new HashMap<String, Map<String, DefaultTransactor>>();
	}

	public void configure(PrismsConfig config)
	{
		if(isConfigured)
			throw new IllegalStateException("Connection factory has already been configured");
		for(PrismsConfig connEl : config.subConfigs("connection"))
		{
			String [] names = connEl.getAll("name");
			if(names.length == 0)
				throw new IllegalArgumentException("No name for connection: " + connEl);
			String ref = connEl.get("ref");
			String coreConnectionID;
			if(ref != null)
			{
				PrismsConfig refConfig = theNamedConnEls.get(ref);
				if(refConfig == null)
					throw new IllegalStateException("Connection " + ref
						+ " not present in connection factory configuration so far");
				coreConnectionID = theConnectionAliases.get(ref);
				connEl = new PrismsConfig.MergedConfig(connEl, refConfig);
			}
			else
			{
				coreConnectionID = prisms.util.PrismsUtils.getRandomString(16);
				theNamedConnEls.put(coreConnectionID, connEl);
			}

			for(String name : names)
			{
				theNamedConnEls.put(name, connEl);
				theConnectionAliases.put(name, coreConnectionID);
			}
		}
		isConfigured = true;
	}

	public synchronized <T extends Throwable> Transactor<T> getConnection(PrismsConfig config,
		String duplicateID, Thrower<T> thrower)
	{
		if(thrower == null)
			thrower = (Thrower<T>) DEFAULT_THROWER;
		String ref = config.get("ref");
		String coreConnectionID;
		PrismsConfig refConfig;
		PrismsConfig coreConfig;
		if(ref == null)
		{
			ref = config.get("url");
			if(ref == null)
				ref = config.toString();
			coreConnectionID = ref;
			refConfig = config;
			coreConfig = config;
		}
		else
		{
			refConfig = theNamedConnEls.get(ref);
			coreConnectionID = theConnectionAliases.get(ref);
			coreConfig = theNamedConnEls.get(coreConnectionID);
			if(coreConnectionID == null)
				throw new IllegalArgumentException("No such connection " + ref
					+ " configured in this factory");
		}
		if(duplicateID == null)
			duplicateID = config.get("duplicate");
		if(duplicateID == null)
			duplicateID = "";

		if(refConfig != null)
			config = new PrismsConfig.MergedConfig(config, refConfig);
		Map<String, DefaultTransactor> ccs = theCoreConnections.get(coreConnectionID);
		if(ccs == null)
		{
			ccs = new HashMap<String, DefaultTransactor>();
			theCoreConnections.put(coreConnectionID, ccs);
		}
		DefaultTransactor trans = ccs.get(duplicateID);
		if(trans == null)
		{
			trans = new DefaultTransactor(coreConfig, duplicateID);
			ccs.put(duplicateID, trans);
		}
		return new TransactorImpl<T>(config, trans, thrower);
	}

	public PrismsConfig getConnectionConfig(PrismsConfig config)
	{
		String ref = config.get("ref");
		if(ref == null)
			return config;

		PrismsConfig refConfig = theNamedConnEls.get(ref);
		if(refConfig == null)
			return null;
		return new PrismsConfig.MergedConfig(config, refConfig);
	}

	/**
	 * Instantiates a connection to a database. This method may be overridden by subclasses to
	 * connect to databases in ways other than simple url/username/password configuration.
	 * 
	 * @param config The configuration to use to connect to the database
	 * @param duplicateID The duplicate ID of the connection, or null if the connection is to be
	 *        made for the original
	 * @return The connection to the database--should NEVER be null
	 * @throws SQLException If an error occurs parsing the configuration or connecting to the
	 *         database
	 */
	protected Connection connect(PrismsConfig config, String duplicateID) throws SQLException
	{
		String url = config.get("url");
		if(url != null)
		{
			Connection ret;
			String driver = config.get("driver");
			try
			{
				if(driver != null)
					Class.forName(driver);
				String user = config.get("username");
				String pwd = config.get("password");
				if(user == null)
				{
					log.debug("Connecting to database at " + url);
					ret = java.sql.DriverManager.getConnection(url);
				}
				else
				{
					log.debug("Connecting to database at " + url + " as " + user);
					ret = java.sql.DriverManager.getConnection(url, user, pwd);
				}
			} catch(Throwable e)
			{
				throw new SQLException("Could not instantiate SQL Connection: " + config, e);
			}
			return ret;
		}
		else
			throw new SQLException("Unrecognized connection configuration type: " + config);
	}

	/**
	 * @param connConfig The configuration used to connect to the database
	 * @return The prefix that should be appended to all SQL commands before each table name
	 */
	protected String getTablePrefix(PrismsConfig connConfig)
	{
		String ret = connConfig.get("prefix");
		if(ret == null)
			ret = "";
		return ret;
	}

	/**
	 * Disconnects a connection
	 * 
	 * @param connConfig The configuration used to connect
	 * @param conn The connection to disconnect
	 */
	protected void released(PrismsConfig connConfig, Connection conn)
	{
		if(conn.getClass().getName().contains("hsql") && connConfig.get("noshutdown") == null)
		{
			// hsql connection--use shutdown command
			try
			{
				java.sql.Statement stmt = conn.createStatement();
				stmt.execute("SHUTDOWN");
				stmt.close();
			} catch(java.sql.SQLException e)
			{
				log.error("Could not execute HSQL shutdown statement", e);
			}
		}
		try
		{
			conn.close();
		} catch(java.sql.SQLException e)
		{
			log.error("Connection error", e);
		}
	}

	public void destroy()
	{
		for(Map<String, DefaultTransactor> conns : theCoreConnections.values())
		{
			for(DefaultTransactor conn : conns.values())
				conn.release();
			conns.clear();
		}
		theCoreConnections.clear();
		theNamedConnEls.clear();
		isConfigured = false;
	}
}
