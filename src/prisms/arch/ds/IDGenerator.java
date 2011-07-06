/*
 * IDGenerator.java Created Oct 26, 2010 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import java.sql.*;

import org.apache.log4j.Logger;

import prisms.arch.PrismsException;
import prisms.util.DBUtils;

/**
 * Generates IDs that are almost<b>*</b> guaranteed to be unique everywhere. This allows data to be
 * shared across PRISMS installations without ID clashes.
 * 
 * <p>
 * <b>*</b>When a new PRISMS installation is created there is a 1/{@link #ID_RANGE ID Range} chance
 * that it will clash with another given PRISMS installation. The probability of there being a clash
 * among <code>n</code> PRISMS installations is:
 * 
 * <pre>
 *         {@link #ID_RANGE ID Range}!
 * ----------------------------
 * {@link #ID_RANGE ID Range}^<code>n</code> * ({@link #ID_RANGE ID Range} - <code>n</code>)!
 * </pre>
 * 
 * For values of n much less than the square root of {@link #ID_RANGE ID Range}, the formula
 * <code>n</code>(<code>n</code>-1)/2/ {@link #ID_RANGE ID Range} approximates this well. For
 * perspective, 1,000 installations have a 0.05% chance of a clash; 4,484 installations have a 1%
 * chance; 10,000 have a 5% chance. 100,000 installations have a 0.67% chance of NOT encountering a
 * clash. Practically, coordination with thousands of centers may encounter problems, but for
 * smaller scales, this can be assumed to be safe.
 * </p>
 */
public class IDGenerator
{
	static final Logger log = Logger.getLogger(IDGenerator.class);

	/**
	 * Represents an instance of PRISMS that uses a common data source with the local PRISMS
	 * instance, i.e. that is in a common enterprise with this instance
	 */
	public static class PrismsInstance
	{
		/**
		 * The network location of the PRISMS instance that can be used to contact it. This string
		 * is of the form scheme:host:port
		 */
		public final String location;

		/** Whether this instance represents the local instance */
		public final boolean local;

		/** The time at which this instance was most recently started */
		public final long initTime;

		/**
		 * The last time at which activity was recorded for this instance. The granularity is
		 * {@link IDGenerator#INSTANCE_ACTIVE_GRANULARITY}. This value may or may not be an
		 * indicator that the instance is down or that there are no active sessions against it.
		 */
		public final long activeTime;

		PrismsInstance(String loc, boolean _local, long init, long active)
		{
			location = loc;
			local = _local;
			initTime = init;
			activeTime = active;
		}

		/**
		 * Performs a small network call to this instance to make sure the server is up
		 * 
		 * @param timeout The amount of time (in milliseconds) to wait for the remote server to
		 *        return a response. 0 means an infinite timeout. See
		 *        {@link java.net.URLConnection#setConnectTimeout(int)}
		 * @return Whether the PRISMS server is alive. For performance purposes, this call merely
		 *         checks that the PRISMS server is alive--it does not cause any configuration to
		 *         take place.
		 */
		public boolean check(int timeout)
		{
			StringBuilder result = null;
			java.net.URL url;
			java.io.InputStream in = null;
			try
			{
				url = new java.net.URL(location + "/prisms?method=test");
				java.net.URLConnection conn = url.openConnection();
				conn.setConnectTimeout(timeout);
				in = conn.getInputStream();
				java.io.Reader reader = new java.io.InputStreamReader(in);
				result = new StringBuilder();
				int read = reader.read();
				while(read >= 0)
				{
					result.append((char) read);
					read = reader.read();
				}
			} catch(java.io.IOException e)
			{
				log.error("Instance check failed", e);
				if(in != null)
					try
					{
						in.close();
					} catch(java.io.IOException e2)
					{}
			}
			return result != null && result.toString().equals("success");
		}
	}

	/**
	 * The granularity of {@link PrismsInstance#activeTime} and the maximum frequency with which
	 * this instance's active time is updated in the database
	 */
	public static long INSTANCE_ACTIVE_GRANULARITY = 60000;

	/** The range of IDS that may exist in a given PRISMS center */
	public static int ID_RANGE = 1000000000;

	/**
	 * @param objectID The ID of an object
	 * @return The ID of the center where the given object was created
	 */
	public static int getCenterID(long objectID)
	{
		return (int) (objectID / ID_RANGE);
	}

	/**
	 * @param centerID The ID of the center to get the minimum item ID for
	 * @return The minimum ID of an item local to the given center
	 */
	public static long getMinID(int centerID)
	{
		return centerID * 1L * ID_RANGE;
	}

	/**
	 * @param centerID The ID of the center to get the maximum item ID for
	 * @return The maximum ID of an item local to the given center
	 */
	public static long getMaxID(int centerID)
	{
		return (centerID + 1L) * ID_RANGE - 1;
	}

	private final prisms.arch.ds.Transactor<PrismsException> theTransactor;

	private Boolean isOracle;

	private int theCenterID;

	private String theLocalScheme;

	private String theLocalAddress;

	private int theLocalPort;

	private String theLocalPath;

	private String theLocalLocation;

	private String theLocalTrustStore;

	private String theLocalTrustPwd;

	private boolean isConfigured;

	private final boolean isShared;

	private boolean isNewInstall;

	private long theLastInstanceUpdate;

	private long theInitTime;

	private PreparedStatement theInstanceUpdate;

	private PreparedStatement theInstanceGetter;

	private PreparedStatement theSyncStatement;

	private PreparedStatement theUnsyncStatement;

	private PreparedStatement thePurgeSyncStatement;

	private long theLastSyncPurge;

	private long theLastInstancePurge;

	private PreparedStatement theAISelector;

	private PreparedStatement theAIInserter;

	private PreparedStatement theAIUpdater;

	/**
	 * Creates an ID generator for a given connection
	 * 
	 * @param factory The connection factory to use to get the database connection
	 * @param connEl The configuration to use to get the database connection
	 */
	public IDGenerator(prisms.arch.ConnectionFactory factory, prisms.arch.PrismsConfig connEl)
	{
		theTransactor = factory.getConnection(connEl, null,
			new prisms.arch.ds.Transactor.Thrower<PrismsException>()
			{
				public void error(String message) throws PrismsException
				{
					throw new PrismsException(message);
				}

				public void error(String message, Throwable cause) throws PrismsException
				{
					throw new PrismsException(message, cause);
				}
			});
		theTransactor.addReconnectListener(new prisms.arch.ds.Transactor.ReconnectListener()
		{
			public void reconnected(boolean initial)
			{
				createPreparedCalls();
			}

			public void released()
			{
				closePreparedCalls();
			}
		});
		isShared = Boolean.TRUE.equals(connEl.is("shared"));
		theCenterID = -1;
		createPreparedCalls();
	}

	/**
	 * Sets information that other enterprise instances can use to connect to this instance
	 * 
	 * @param scheme The scheme to use connect to this instance externally (e.g. HTTP or HTTPS)
	 * @param port The local port that other instances on the enterprise can use to connect to this
	 *        instance
	 * @param path The serlvet path to this instance
	 */
	public void setLocalConnectInfo(String scheme, int port, String path)
	{
		if(isConfigured)
			throw new IllegalStateException("Local connection information cannot be set after the"
				+ " ID generator has been configured");
		theLocalScheme = scheme;
		theLocalPort = port;
		theLocalPath = path;
		if(isShared && (theLocalScheme == null || theLocalPort <= 0 || theLocalPath == null))
			log.error("\n\nNo local port configured for shared environment\n");
		String addr;
		try
		{
			addr = java.net.InetAddress.getLocalHost().getHostAddress();
		} catch(java.net.UnknownHostException e)
		{
			log.error("Could not get local host address", e);
			addr = null;
		}
		theLocalAddress = addr;
		if(theLocalScheme != null && theLocalAddress != null && theLocalPort > 0
			&& theLocalPath != null)
			theLocalLocation = theLocalScheme + "://" + theLocalAddress + ":" + theLocalPort + "/"
				+ theLocalPath;
		else
			theLocalLocation = null;
	}

	/**
	 * Sets security information, if applicable, to use with HTTPS connections
	 * 
	 * @param trustStore The file location of the trust store to use when making connections to
	 *        other instances on the enterprise
	 * @param password The password to use with the trust store
	 */
	public void setSecureInfo(String trustStore, String password)
	{
		if(isConfigured)
			throw new IllegalStateException("Local connection security information cannot be set"
				+ " after the ID generator has been configured");
		theLocalTrustStore = trustStore;
		theLocalTrustPwd = password;
	}

	/**
	 * Finishes configuring this ID generator
	 * 
	 * @throws PrismsException If the ID generator cannot be configured
	 */
	public void setConfigured() throws PrismsException
	{
		isConfigured = true;
		theInitTime = System.currentTimeMillis();
		doStartup();
	}

	/**
	 * @return The file location of the trust store to use when making connections to other
	 *         instances on the enterprise
	 */
	public String getTrustStore()
	{
		return theLocalTrustStore;
	}

	/** @return The password to use with the trust store */
	public String getTrustPassword()
	{
		return theLocalTrustPwd;
	}

	/** @return The ID of this PRISMS installation */
	public int getCenterID()
	{
		return theCenterID;
	}

	/**
	 * @return Whether this ID generator may share the database with another instance and therefore
	 *         must lock on the database before it attempts to generate a new ID
	 */
	public boolean isShared()
	{
		return isShared;
	}

	/**
	 * Peforms initial functions to set up this data source
	 * 
	 * @throws PrismsException If an error occurs getting the setup data
	 */
	protected void doStartup() throws PrismsException
	{
		lock("prisms_installation", null);
		try
		{
			if(getInstallDate() < 0)
			{
				isNewInstall = true;
				theCenterID = (int) (Math.random() * ID_RANGE);
				install();
			}
		} finally
		{
			unlock("prisms_installation", null);
		}

		if(theLocalAddress != null && isShared)
		{
			String date = DBUtils.formatDate(theInitTime, isOracle());
			String loc = DBUtils.toSQL(theLocalLocation);
			String sql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_instance SET initTime=" + date + ", activeTime=" + date
				+ " WHERE location=" + loc;
			Statement stmt = null;
			try
			{
				stmt = theTransactor.getConnection().createStatement();
				if(stmt.executeUpdate(sql) == 0)
				{
					sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_instance"
						+ " (location, initTime, activeTime) VALUES (" + loc + ", " + date + ", "
						+ date + ")";
					stmt.executeUpdate(sql);
				}

				// Purge old instances that will probably not be up again
				date = DBUtils.formatDate(System.currentTimeMillis() - 12L * 60 * 60 * 1000,
					isOracle());
				sql = "DELETE FROM " + theTransactor.getTablePrefix() + "prisms_instance"
					+ " WHERE activeTime<" + date;
			} catch(SQLException e)
			{
				log.error("Could not insert instance entry: SQL=" + sql, e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{
						log.error("Connection error", e);
					}
			}
			theLastInstanceUpdate = System.currentTimeMillis();
			theLastInstancePurge = theLastInstanceUpdate;
		}
	}

	/** @return Whether this ID generator's connection is to an oracle database */
	public boolean isOracle()
	{
		if(isOracle == null)
			try
			{
				isOracle = Boolean.valueOf(DBUtils.isOracle(theTransactor.getConnection()));
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Connection failed!", e);
			}
		return isOracle.booleanValue();
	}

	/** @return Whether this IDGenerator had to install itself when it was loaded */
	public boolean isNewInstall()
	{
		return isNewInstall;
	}

	/**
	 * @param objectID The ID of the object to test
	 * @return Whether the object identified by the given ID belongs to this PRISMS installation's
	 *         local data set
	 */
	public boolean belongs(long objectID)
	{
		return getCenterID(objectID) == getCenterID();
	}

	/**
	 * Updates the active time on this instance so that this server always appears up in the
	 * database while it is up
	 */
	public void updateInstance()
	{
		if(theLocalLocation == null || !isShared)
			return;
		long now = System.currentTimeMillis();
		if(now - theLastInstanceUpdate > 60000)
		{
			synchronized(this)
			{
				if(now - theLastInstanceUpdate > 60000)
				{
					theLastInstanceUpdate = now;
					Statement stmt = null;
					try
					{
						theInstanceUpdate.setTimestamp(1,
							new java.sql.Timestamp(System.currentTimeMillis()));
						if(theInstanceUpdate.executeUpdate() == 0)
						{ // May happen if this instance is not used for a long time
							String sql = "INSERT INTO " + theTransactor.getTablePrefix()
								+ "prisms_instance (location, initTime, activeTime) VALUES ("
								+ DBUtils.toSQL(theLocalLocation) + ", "
								+ DBUtils.formatDate(theInitTime, isOracle()) + ", "
								+ DBUtils.formatDate(System.currentTimeMillis(), isOracle()) + ")";
							try
							{
								stmt = theTransactor.getConnection().createStatement();
							} catch(PrismsException e)
							{
								log.error("Could not get connection to re-insert PRISMS instance",
									e);
							}
							stmt.executeUpdate(sql);
						}

						if(now - theLastInstancePurge > 60L * 60 * 1000)
						{
							theLastInstancePurge = now;
							// Purge old instances that will probably not be up again
							String date = DBUtils.formatDate(System.currentTimeMillis() - 12L * 60
								* 60 * 1000, isOracle());
							String sql = "DELETE FROM " + theTransactor.getTablePrefix()
								+ "prisms_instance" + " WHERE activeTime<" + date;
							if(stmt == null)
								try
								{
									stmt = theTransactor.getConnection().createStatement();
								} catch(PrismsException e)
								{
									log.error(
										"Could not get connection to purge old PRISMS instances", e);
								}
							stmt.executeUpdate(sql);
						}
					} catch(SQLException e)
					{
						log.error("Could not update instance", e);
					} finally
					{
						if(stmt != null)
							try
							{
								stmt.close();
							} catch(SQLException e)
							{
								log.error("Connection error", e);
							}
					}
				}
			}
		}
	}

	void dropInstance()
	{
		if(theLocalLocation == null || !isShared)
			return;
		String sql = "DELETE FROM " + theTransactor.getTablePrefix()
			+ "prisms_instance WHERE location=" + DBUtils.toSQL(theLocalLocation);
		Statement stmt = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			stmt.executeUpdate(sql);
		} catch(PrismsException e)
		{
			log.error("Could not get connection to drop PRISMS instance", e);
		} catch(SQLException e)
		{
			log.error("Could not drop PRISMS instance", e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * @return The date when this set of records was installed
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	public long getInstallDate() throws PrismsException
	{
		updateInstance();
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT centerID, installDate FROM " + theTransactor.getTablePrefix()
			+ "prisms_installation";
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return -1;
			theCenterID = rs.getInt("centerID");
			return rs.getTimestamp("installDate").getTime();
		} catch(SQLException e)
		{
			throw new PrismsException("Could not query PRISMS installation: SQL=" + sql, e);
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
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * Installs this PRISMS instance into the database
	 * 
	 * @throws PrismsException If an error occurs installing this PRISMS instance
	 */
	private void install() throws PrismsException
	{
		Statement stmt = null;
		String sql = "INSERT INTO " + theTransactor.getTablePrefix()
			+ "prisms_installation (centerID, installDate) VALUES (" + theCenterID + ", "
			+ DBUtils.formatDate(System.currentTimeMillis(), isOracle()) + ")";
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			stmt.execute(sql);

			sql = "DELETE FROM " + theTransactor.getTablePrefix() + "prisms_auto_increment";
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not install PRISMS: SQL=" + sql, e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * @return This PRISMS instance, or null if the local instance is not configured for enterprise
	 *         registration
	 */
	public PrismsInstance getLocalInstance()
	{
		if(theLocalAddress == null)
			return null;
		return new PrismsInstance(theLocalLocation, true, theInitTime, System.currentTimeMillis());
	}

	/**
	 * Queries for all PRISMS instances in the local enterprise. Normally, all entries returned by
	 * this method will be available to take requests, as a normal server shutdown results in the
	 * instance entry being deleted. The {@link PrismsInstance#check(int)} method may be used to
	 * determine this for sure.
	 * 
	 * @return All PRISMS instances in the local enterprise except this one
	 * @throws PrismsException If an error occurs getting the data
	 */
	public PrismsInstance [] getOtherInstances() throws PrismsException
	{
		if(theInstanceGetter == null)
			return new PrismsInstance [0];

		java.util.ArrayList<PrismsInstance> ret = new java.util.ArrayList<PrismsInstance>();
		ResultSet rs = null;
		try
		{
			rs = theInstanceGetter.executeQuery();
			while(rs.next())
			{
				String loc = rs.getString("location");
				if(loc.equals(theLocalLocation))
					continue; // Don't return the local instance
				ret.add(new PrismsInstance(loc, false, rs.getTimestamp("initTime").getTime(), rs
					.getTimestamp("activeTime").getTime()));
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get other PRISMS instances on the enterprise", e);
		} finally
		{
			try
			{
				rs.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
		return ret.toArray(new PrismsInstance [ret.size()]);
	}

	private void lock(String table, String where)
	{
		if(!isShared)
			return;
		long now = System.currentTimeMillis();
		if(now - theLastSyncPurge > 10000)
		{
			try
			{
				thePurgeSyncStatement.setTimestamp(1, new java.sql.Timestamp(now));
				thePurgeSyncStatement.executeUpdate();
				thePurgeSyncStatement.clearParameters();
			} catch(SQLException e)
			{
				throw new IllegalStateException("Purge sync statement is messed up", e);
			}
		}
		try
		{
			theSyncStatement.setString(1, table);
			if(where == null)
				where = "NULL";
			theSyncStatement.setString(2, where);
			theSyncStatement.setTimestamp(3, new java.sql.Timestamp(now));
		} catch(SQLException e)
		{
			throw new IllegalStateException("Synchronization statement is messed up", e);
		}
		boolean locked = false;
		do
		{
			try
			{
				try
				{
					theSyncStatement.executeUpdate();
					locked = true;
				} catch(SQLException e)
				{
					log.warn("Normal sync error: table=" + table + ", where=" + where, e);
					// This is normal and means that the item has already been synchronized on
				}
			} finally
			{}
			if(!locked)
				try
				{
					Thread.sleep(100);
				} catch(InterruptedException e)
				{}
		} while(!locked);
		try
		{
			theSyncStatement.clearParameters();
		} catch(SQLException e)
		{
			throw new IllegalStateException("Could not reset sync statement", e);
		}
	}

	private void unlock(String table, String where)
	{
		if(!isShared)
			return;
		try
		{
			theUnsyncStatement.setString(1, table);
			if(where == null)
				where = "NULL";
			theUnsyncStatement.setString(2, where);
			theUnsyncStatement.executeUpdate();
		} catch(SQLException e)
		{
			throw new IllegalStateException("Unsync statement is messed up", e);
		}
	}

	/**
	 * Gets the next ID for the given table within this center and namespace
	 * 
	 * @param table The name of the table to get the next ID for (including any applicable prefix)
	 * @param column The ID column of the table
	 * @param extStmt The active statement pointing to the database where the actual implementation
	 *        data resides. If this is null it will be assumed that the implementation data resides
	 *        in the same database as the PRISMS records data.
	 * @param extPrefix The prefix that should be used to access tables in the external database
	 * @param where The where clause that should be used to get the next ID
	 * @return The next ID that should be used for an entry in the table
	 * @throws PrismsException If an error occurs deriving the data
	 */
	public synchronized long getNextID(String table, String column, Statement extStmt,
		String extPrefix, String where) throws PrismsException
	{
		updateInstance();
		ResultSet rs = null;
		String sql = null;
		lock(table, where);
		try
		{
			final long centerMin = getMinID(theCenterID);
			final long centerMax = getMaxID(theCenterID);

			theAISelector.setString(1, table);
			theAISelector.setString(2, where == null ? "none" : where);
			rs = theAISelector.executeQuery();

			boolean update = rs.next();
			long ret;
			if(update)
				ret = rs.getLong(1);
			else
				ret = -1;
			rs.close();
			rs = null;
			theAISelector.clearParameters();
			if(ret < centerMin || ret > centerMax)
				ret = -1;
			if(ret < 0)
			{
				sql = "SELECT MAX(" + column + ") FROM " + extPrefix + table + " WHERE " + column
					+ ">=" + centerMin + " AND " + column + " <=" + centerMax;
				if(where != null)
					sql += " AND " + where;
				rs = extStmt.executeQuery(sql);
				if(rs.next())
				{
					ret = rs.getLong(1);
					if(ret < centerMin || ret > centerMax)
						ret = centerMin;
					else
						ret++;
				}
				else
					ret = centerMin;
				rs.close();
				rs = null;
				if(ret > centerMax)
				{
					/* It may be the case that the maximum ID and a few others adjacent to it are
					 * used as standard values, with a large gap between them and the dynamic ID
					 * set. If this is the case, we'll search for a large gap below the IDs
					 * adjacent to the max ID. */
					sql = "SELECT " + column + " FROM " + extPrefix + table + " WHERE " + column
						+ ">=" + centerMin + " AND " + column + "<=" + centerMax;
					if(where != null)
						sql += " AND " + where;
					sql += " ORDER by " + column + " DESC";
					rs = extStmt.executeQuery(sql);
					long next = 0;
					long maxNext = centerMax + 1;
					while(rs.next())
					{
						next = rs.getLong(1);
						maxNext--;
						if(next != maxNext)
							break;
					}
					rs.close();
					rs = null;
					if(next == maxNext) // The higher end is used, but not the lower end
						ret = centerMin;
					else if(maxNext - next < 10) // Not a big enough gap
						throw new PrismsException("All " + table + " ids are used!");
					else
						ret = next + 1;
				}
				sql = null;
				// update the db
				if(update)
				{
					theAIUpdater.setLong(1, ret);
					theAIUpdater.setString(2, table);
					theAIUpdater.setString(3, where == null ? "none" : where);
					theAIUpdater.executeUpdate();
					theAIUpdater.clearParameters();
				}
				else
				{
					theAIInserter.setString(1, table);
					theAIInserter.setString(2, where == null ? "none" : where);
					theAIInserter.setLong(3, ret);
					theAIInserter.executeUpdate();
					theAIInserter.clearParameters();
				}
			}
			else
			{
				long nextTry = nextAvailableID(extStmt, extPrefix + table, column, ret + 1, where);
				if(nextTry > centerMax)
					nextTry = nextAvailableID(extStmt, extPrefix + table, column, centerMin, where);
				if(nextTry == ret || nextTry > centerMax)
					throw new PrismsException("All " + table + " ids are used!");

				ret = nextTry;
				theAIUpdater.setLong(1, nextTry);
				theAIUpdater.setString(2, table);
				theAIUpdater.setString(3, where == null ? "none" : where);
				theAIUpdater.executeUpdate();
				theAIUpdater.clearParameters();
			}
			return ret;
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get next ID: SQL=" + sql, e);
		} finally
		{
			unlock(table, where);
			try
			{
				if(rs != null)
					rs.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
	}

	/**
	 * Gets the next available ID in a table
	 * 
	 * @param stmt The statement to use to get the next ID
	 * @param table The table name (with prefix if applicable) to get the next ID for
	 * @param column The ID column to get the next available value of
	 * @param start The lowest value that will be returned from this method
	 * @param where A selector to append to the SQL if the ID is to be selected from only a part of
	 *        the table
	 * @return The next available ID for the table
	 * @throws PrismsException If an error occurs getting the next ID
	 */
	public static long nextAvailableID(Statement stmt, String table, String column, long start,
		String where) throws PrismsException
	{
		String sql = "SELECT DISTINCT " + column + " FROM " + table + " WHERE " + column + ">="
			+ start;
		if(where != null)
			sql += " AND " + where;
		sql += " ORDER BY " + column;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				long tempID = rs.getLong(1);
				if(start != tempID)
					break;
				start++;
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get next available table ID: SQL=" + sql, e);
		} finally
		{
			try
			{
				if(rs != null)
					rs.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
		return start;
	}

	/**
	 * Gets the next ID for a table whose value is not dependent on the center
	 * 
	 * @param stmt The statement pointing to the given table
	 * @param table The table to get the next ID for. This must be appended with any necessary
	 *        prefixes.
	 * @param prefix The prefix to set before the table name in a query
	 * @param column The ID column in the table
	 * @param where The where clause that should be used to get the next ID
	 * @return The next ID to use for an entry in the table
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	public synchronized int getNextIntID(Statement stmt, String table, String prefix,
		String column, String where) throws PrismsException
	{
		updateInstance();
		if(prefix == null)
			prefix = "";
		ResultSet rs = null;
		String sql = null;
		lock(table, where);
		try
		{
			theAISelector.setString(1, table);
			theAISelector.setString(2, where == null ? "none" : where);
			rs = theAISelector.executeQuery();

			long ret;
			final boolean update = rs.next();
			if(update)
			{
				ret = rs.getLong(1);
				rs.close();
				rs = null;

				ret = nextAvailableID(stmt, prefix + table, column, ret + 1, where);
				if(ret > Integer.MAX_VALUE)
				{
					ret = nextAvailableID(stmt, prefix + table, column, 0, where);
					if(ret > Integer.MAX_VALUE)
						throw new PrismsException("All " + table + " IDs are used");
				}
			}
			else
			{
				rs.close();
				rs = null;
				ret = nextAvailableID(stmt, prefix + table, column, 0, where);
				if(ret > Integer.MAX_VALUE)
				{
					ret = nextAvailableID(stmt, prefix + table, column, 0, where);
					if(ret > Integer.MAX_VALUE)
						throw new PrismsException("All " + table + " IDs are used");
				}
			}
			theAISelector.clearParameters();

			if(update)
			{
				theAIUpdater.setLong(1, ret);
				theAIUpdater.setString(2, table);
				theAIUpdater.setString(3, where == null ? "none" : where);
				theAIUpdater.executeUpdate();
				theAIUpdater.clearParameters();
			}
			else
			{
				theAIInserter.setString(1, table);
				theAIInserter.setString(2, where == null ? "none" : where);
				theAIInserter.setLong(3, ret);
				theAIInserter.executeUpdate();
				theAIInserter.clearParameters();
			}

			return (int) ret;
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get next ID: SQL=" + sql, e);
		} finally
		{
			unlock(table, where);
			try
			{
				if(rs != null)
					rs.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
	}

	/**
	 * Gets the maximum length of data for a field
	 * 
	 * @param tableName The name of the table
	 * @param fieldName The name of the field to get the length for
	 * @return The maximum length that data in the given field can be
	 * @throws PrismsException If an error occurs retrieving the information
	 */
	public int getFieldSize(String tableName, String fieldName) throws PrismsException
	{
		updateInstance();
		return getFieldSize(theTransactor.getConnection(), theTransactor.getTablePrefix()
			+ tableName, fieldName);
	}

	void createPreparedCalls()
	{
		java.sql.Connection conn;
		try
		{
			conn = theTransactor.getConnection();
		} catch(PrismsException e)
		{
			throw new IllegalStateException("Could not get connection", e);
		}
		String sql;
		try
		{
			sql = "INSERT INTO " + theTransactor.getTablePrefix()
				+ "prisms_increment_sync (tableName," + " whereClause, syncTime) VALUES (?, ?, ?)";
			theSyncStatement = conn.prepareStatement(sql);

			sql = "DELETE FROM " + theTransactor.getTablePrefix() + "prisms_increment_sync WHERE"
				+ " tableName=? AND whereClause=?";
			theUnsyncStatement = conn.prepareStatement(sql);

			sql = "DELETE FROM " + theTransactor.getTablePrefix()
				+ "prisms_increment_sync WHERE syncTime<?";
			thePurgeSyncStatement = conn.prepareStatement(sql);

			sql = "SELECT DISTINCT nextID FROM " + theTransactor.getTablePrefix()
				+ "prisms_auto_increment WHERE tableName=? AND whereClause=?";
			theAISelector = conn.prepareStatement(sql);

			sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_auto_increment"
				+ " (tableName, whereClause, nextID) VALUES (?, ?, ?)";
			theAIInserter = conn.prepareStatement(sql);

			sql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_auto_increment SET nextID=? WHERE tableName=? AND whereClause=?";
			theAIUpdater = conn.prepareStatement(sql);

			if(theLocalLocation != null && isShared)
			{
				sql = "UPDATE " + theTransactor.getTablePrefix()
					+ "prisms_instance SET activeTime=?" + " WHERE location="
					+ DBUtils.toSQL(theLocalLocation);
				theInstanceUpdate = conn.prepareStatement(sql);

				sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_instance";
				theInstanceGetter = conn.prepareStatement(sql);
			}
		} catch(SQLException e)
		{
			throw new IllegalStateException("Could not prepare calls for ID Generator", e);
		}
	}

	void closePreparedCalls()
	{
		try
		{
			theSyncStatement.close();
			theUnsyncStatement.close();
			thePurgeSyncStatement.close();
			theAISelector.close();
			theAIInserter.close();
			theAIUpdater.close();
			if(theInstanceUpdate != null)
				theInstanceUpdate.close();
			if(theInstanceGetter != null)
				theInstanceGetter.close();
		} catch(SQLException e)
		{
			log.error("Could not close prepared calls", e);
		} catch(Error e)
		{
			// Keep getting these from an HSQL bug--silence
			if(!e.getMessage().contains("compilation"))
				log.error("Error", e);
		}
	}

	/**
	 * Gets the maximum length of data for a field
	 * 
	 * @param conn The connection to get information from
	 * @param tableName The name of the table
	 * @param fieldName The name of the field to get the length for
	 * @return The maximum length that data in the given field can be
	 * @throws PrismsException If an error occurs retrieving the information, such as the table or
	 *         field not existing
	 */
	public static int getFieldSize(java.sql.Connection conn, String tableName, String fieldName)
		throws PrismsException
	{
		if(DBUtils.isOracle(conn))
			throw new PrismsException("Accessing Oracle metadata is unsafe--cannot get field size");
		ResultSet rs = null;
		try
		{
			String schema = null;
			tableName = tableName.toUpperCase();
			int dotIdx = tableName.indexOf('.');
			if(dotIdx >= 0)
			{
				schema = tableName.substring(0, dotIdx).toUpperCase();
				tableName = tableName.substring(dotIdx + 1).toUpperCase();
			}
			rs = conn.getMetaData().getColumns(null, schema, tableName, null);
			while(rs.next())
			{
				String name = rs.getString("COLUMN_NAME");
				if(name.equalsIgnoreCase(fieldName))
					return rs.getInt("COLUMN_SIZE");
			}

			throw new PrismsException("No such field " + fieldName + " in table "
				+ (schema != null ? schema + "." : "") + tableName);

		} catch(SQLException e)
		{
			throw new PrismsException("Could not get field length of " + tableName + "."
				+ fieldName, e);
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
		}
	}

	/** Performs steps to shut down this PRISMS database connection */
	public void destroy()
	{
		dropInstance();
		theTransactor.release();
	}
}
