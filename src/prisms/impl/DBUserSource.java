/**
 * DBUserSource.java Created Jun 24, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import static prisms.util.DBUtils.toSQL;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;

import org.apache.log4j.Logger;

import prisms.arch.*;
import prisms.arch.ds.*;
import prisms.util.DBUtils;

/**
 * A {@link prisms.arch.ds.ManageableUserSource} that obtains its information from a database
 */
public class DBUserSource implements prisms.arch.ds.ManageableUserSource
{
	private static final Logger log = Logger.getLogger(DBUserSource.class);

	static class PasswordData
	{
		int id;

		long [] thePasswordHash;

		long thePasswordTime;

		long thePasswordExpire;
	}

	private java.util.concurrent.locks.ReentrantReadWriteLock theLock;

	private java.sql.Connection thePRISMSConnection;

	private String DBOWNER;

	private org.dom4j.Element theConfigEl;

	private PersisterFactory thePersisterFactory;

	private String theAnonymousUserName;

	private Hashing theHashing;

	private java.util.Map<String, DBUser> theUsers;

	private java.util.Map<String, Integer> theAppIDs;

	private java.util.Map<Integer, DBApplication> theApps;

	private java.util.Set<String> theAppsConfigured;

	private java.util.Map<String, DBClientConfig> theClients;

	private java.util.Map<Integer, DBGroup> theGroups;

	/**
	 * Creates a DBUserSource
	 */
	public DBUserSource()
	{
		theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
		theUsers = new java.util.HashMap<String, DBUser>();
		theAppIDs = new java.util.HashMap<String, Integer>();
		theApps = new java.util.HashMap<Integer, DBApplication>();
		theAppsConfigured = new java.util.HashSet<String>();
		theClients = new java.util.HashMap<String, DBClientConfig>();
		theGroups = new java.util.HashMap<Integer, DBGroup>();
		theHashing = new Hashing();
		theAnonymousUserName = "anonymous";
	}

	/**
	 * @see prisms.arch.ds.UserSource#configure(org.dom4j.Element, prisms.arch.PersisterFactory)
	 */
	public void configure(org.dom4j.Element configEl, PersisterFactory factory)
		throws PrismsException
	{
		theConfigEl = configEl;
		thePersisterFactory = factory;
		try
		{
			thePRISMSConnection = factory.getConnection(configEl.element("connection"), null);
		} catch(Exception e)
		{
			throw new PrismsException("Could not connect to PRISMS configuration database", e);
		}
		DBOWNER = factory.getTablePrefix(thePRISMSConnection, configEl.element("connection"), null);
		String anonUser = configEl.elementTextTrim("anonymous");
		if(anonUser != null)
			theAnonymousUserName = anonUser;
		// Set up hashing
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT multiple, modulus FROM " + DBOWNER
				+ "PRISMS_HASHING ORDER BY id");
			java.util.ArrayList<long []> hashing = new java.util.ArrayList<long []>();
			while(rs.next())
			{
				hashing.add(new long [] {rs.getInt(1), rs.getInt(2)});
			}
			long [] mults = new long [hashing.size()];
			long [] mods = new long [hashing.size()];
			for(int h = 0; h < mults.length; h++)
			{
				mults[h] = hashing.get(h)[0];
				mods[h] = hashing.get(h)[1];
			}
			theHashing.setPrimaryHashing(mults, mods);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not access hashing parameters", e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
			stmt = null;
		}
		if(theHashing.getPrimaryMultiples() == null || theHashing.getPrimaryMultiples().length < 10)
		{
			theHashing.randomlyFillPrimary(10);
			/*
			java.sql.PreparedStatement pStmt = null;
			try
			{
				pStmt = thePRISMSConnection.prepareStatement("INSERT INTO " + DBOWNER
					+ "prisms_hashing (id, multiple, modulus)" + " VALUES (?, ?, ?)");
				for(int i = 0; i < theHashing.getPrimaryMultiples().length; i++)
				{
					pStmt.setInt(1, i);
					pStmt.setInt(2, theHashing.getPrimaryMultiples()[i]);
					pStmt.setInt(3, theHashing.getPrimaryModulos()[i]);
					pStmt.execute();
				}
			} catch(SQLException e)
			{
				throw new IllegalStateException("Cannot commit hashing values"
					+ "--passwords not persistable", e);
			} finally
			{
				if(pStmt != null)
					try
					{
						pStmt.close();
					} catch(SQLException e)
					{}
			}
			*/

			try
			{
				stmt = thePRISMSConnection.createStatement();
				for(int i = 0; i < theHashing.getPrimaryMultiples().length; i++)
				{
					stmt.execute("INSERT INTO " + DBOWNER
						+ "prisms_hashing (id, multiple, modulus) VALUES (" + i + ", "
						+ theHashing.getPrimaryMultiples()[i] + ", "
						+ theHashing.getPrimaryModulos()[i] + ")");
				}
			} catch(SQLException e)
			{
				throw new PrismsException("Cannot commit hashing values"
					+ "--passwords not persistable", e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			forceResetUsers();
		}
	}

	/**
	 * @return The database connection that this user source is using
	 */
	public java.sql.Connection getConnection()
	{
		return thePRISMSConnection;
	}

	/**
	 * @return The XML element used to configure this user source's database connection
	 */
	public org.dom4j.Element getConnectionConfig()
	{
		return theConfigEl.element("connection");
	}

	public prisms.arch.ds.PasswordConstraints getPasswordConstraints() throws PrismsException
	{
		checkConnection();
		prisms.arch.ds.PasswordConstraints ret = new prisms.arch.ds.PasswordConstraints();
		Statement stmt = null;
		ResultSet rs = null;
		boolean locked;
		Number minChars;
		Number minUpper;
		Number minLower;
		Number minDigits;
		Number minSpecial;
		Number maxDuration;
		Number numUnique;
		Number minChangeIntvl;
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT * FROM " + DBOWNER + "prisms_password_constraints");
			if(!rs.next())
				return ret;
			locked = DBUtils.getBoolean(rs.getString("constraintsLocked"));
			minChars = (Number) rs.getObject("minCharLength");
			minUpper = (Number) rs.getObject("minUpperCase");
			minLower = (Number) rs.getObject("minLowerCase");
			minDigits = (Number) rs.getObject("minDigits");
			minSpecial = (Number) rs.getObject("minSpecialChars");
			maxDuration = (Number) rs.getObject("maxPasswordDuration");
			numUnique = (Number) rs.getObject("numPreviousUnique");
			minChangeIntvl = (Number) rs.getObject("minChangeInterval");
		} catch(SQLException e)
		{
			throw new PrismsException("Could not retrieve password constraints", e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		if(minChars != null)
			ret.setMinCharacterLength(minChars.intValue());
		if(minUpper != null)
			ret.setMinUpperCase(minUpper.intValue());
		if(minLower != null)
			ret.setMinLowerCase(minLower.intValue());
		if(minDigits != null)
			ret.setMinDigits(minDigits.intValue());
		if(minSpecial != null)
			ret.setMinSpecialChars(minSpecial.intValue());
		if(maxDuration != null)
			ret.setMaxPasswordDuration(maxDuration.longValue());
		if(numUnique != null)
			ret.setNumPreviousUnique(numUnique.intValue());
		if(minChangeIntvl != null)
			ret.setMinPasswordChangeInterval(minChangeIntvl.longValue());
		if(locked)
			ret.lock();
		return ret;
	}

	/**
	 * @see prisms.arch.ds.UserSource#getUser(java.lang.String)
	 */
	public synchronized User getUser(String name)
	{
		if(name == null)
			name = theAnonymousUserName;
		if(name == null)
			return null;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		DBUser ret;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			ret = theUsers.get(name + "/null");
			if(ret == null)
			{
				stmt = thePRISMSConnection.createStatement();
				rs = stmt.executeQuery("SELECT id FROM " + DBOWNER
					+ "prisms_user WHERE userName = " + toSQL(name));
				if(!rs.next())
					return null;
				int userID = rs.getInt(1);
				ret = new DBUser(this, name, userID);
			}
		} catch(SQLException e)
		{
			log.error("Could not retrieve user " + name, e);
			return null;
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		lock = theLock.writeLock();
		lock.lock();
		try
		{
			if(!theUsers.containsKey(name + "/null"))
				theUsers.put(name + "/null", ret);
		} finally
		{
			lock.unlock();
		}
		return ret;
	}

	/**
	 * @see prisms.arch.ds.UserSource#getUser(prisms.arch.ds.User, prisms.arch.PrismsApplication)
	 */
	public synchronized User getUser(User serverUser, PrismsApplication app)
	{
		if(serverUser == null || app == null)
			return null;
		String cacheKey = serverUser.getName() + "/" + app.getName();
		DBUser ret = theUsers.get(cacheKey);
		if(ret != null)
			return ret;
		checkConnection();
		Statement stmt = null;
		ResultSet rs = null;
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			ret = theUsers.get(cacheKey);
			if(ret == null)
			{
				int userID = ((DBUser) serverUser).getID();
				int appID = theAppIDs.get(app.getName()).intValue();
				ret = new DBUser((DBUser) serverUser, app);
				stmt = thePRISMSConnection.createStatement();
				rs = stmt.executeQuery("SELECT encryption, validationClass FROM " + DBOWNER
					+ "prisms_user_app_assoc WHERE assocUser = " + userID + " AND assocApp = "
					+ appID);
				if(!rs.next())
					return null;
				ret.setEncryptionRequired(DBUtils.getBoolean(rs.getString(1)));
				String valClass = rs.getString(2);
				if(valClass != null)
				{
					try
					{
						Validator validator = (Validator) Class.forName(valClass).newInstance();
						ret.setValidator(validator);
					} catch(Throwable e)
					{
						ret.setValidator(new PlaceholderValidator(valClass));
					}
				}
			}
		} catch(SQLException e)
		{
			log.error("Could not get groups and permissions for user " + serverUser + " and app "
				+ app.getName(), e);
			return null;
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		UserGroup [] groups = getGroups(ret, app);
		for(UserGroup group : groups)
			ret.addTo(group);
		lock = theLock.writeLock();
		lock.lock();
		try
		{
			theUsers.put(cacheKey, ret);
		} finally
		{
			lock.unlock();
		}
		return ret;
	}

	public void lockUser(User user)
	{
		user.setLocked(true);
	}

	private UserGroup getGroup(int id)
	{
		DBGroup ret;
		Statement stmt = null;
		ResultSet rs = null;
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			ret = theGroups.get(new Integer(id));
			if(ret != null)
				return ret;
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT groupName, groupDescrip, groupApp FROM " + DBOWNER
				+ "prisms_user_group" + " WHERE id = " + id);
			if(!rs.next())
				throw new IllegalArgumentException("No such group with ID " + id);
			String name = rs.getString(1);
			String descrip = rs.getString(2);
			int appID = rs.getInt(3);
			rs.close();
			rs = null;
			PrismsApplication app = theApps.get(new Integer(appID));
			if(app == null)
				throw new IllegalStateException("Requesting a group for an application that has"
					+ " not been loaded yet");

			ret = new DBGroup(this, name, app, id);
			ret.setDescription(descrip);
			SimplePermissions perms = new SimplePermissions();
			ret.setPermissions(perms);
			rs = stmt.executeQuery("SELECT id, pName, pDescrip, pApp FROM " + DBOWNER
				+ "prisms_permission INNER JOIN " + DBOWNER + "prisms_group_permissions ON "
				+ DBOWNER + "prisms_permission.id = " + DBOWNER
				+ "prisms_group_permissions.assocPermission WHERE " + DBOWNER
				+ "prisms_group_permissions.assocGroup = " + id);
			while(rs.next())
			{
				if(appID != rs.getInt(4))
				{
					log.error("Invalid permission " + rs.getString(2)
						+ "--application is different than its associated group");
					continue;
				}
				perms.addPermission(new DBPermission(rs.getString(2), rs.getString(3), app, rs
					.getInt(1)));
			}
		} catch(SQLException e)
		{
			log.error("Could not get group with ID " + id, e);
			return null;
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		lock = theLock.writeLock();
		lock.lock();
		try
		{
			if(!theGroups.containsKey(new Integer(id)))
				theGroups.put(new Integer(id), ret);
		} finally
		{
			lock.unlock();
		}
		return ret;
	}

	private UserGroup [] getGroups(DBUser user, PrismsApplication app)
	{
		Statement stmt = null;
		ResultSet rs = null;
		Lock lock = theLock.readLock();
		lock.lock();
		ArrayList<Integer> groupIDs = new ArrayList<Integer>();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT " + DBOWNER + "prisms_user_group.id FROM " + DBOWNER
				+ "prisms_user_group INNER JOIN " + DBOWNER + "prisms_user_group_assoc" + " ON "
				+ DBOWNER + "prisms_user_group_assoc.assocGroup = " + DBOWNER
				+ "prisms_user_group.id WHERE " + DBOWNER + "prisms_user_group_assoc.assocUser = "
				+ user.getID() + " AND " + DBOWNER + "prisms_user_group.groupApp = "
				+ theAppIDs.get(app.getName()));
			while(rs.next())
				groupIDs.add(new Integer(rs.getInt(1)));
		} catch(SQLException e)
		{
			log.error("Could not get groups for user " + user + "and app " + app, e);
			return null;
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		DBGroup [] ret = new DBGroup [groupIDs.size()];
		for(int g = 0; g < ret.length; g++)
			ret[g] = (DBGroup) getGroup(groupIDs.get(g).intValue());
		java.util.Arrays.sort(ret, new java.util.Comparator<DBGroup>()
		{
			public int compare(DBGroup o1, DBGroup o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return ret;
	}

	public DBApplication getApp(String appName) throws PrismsException
	{
		DBApplication ret;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			Integer id = theAppIDs.get(appName);
			if(id != null)
			{
				ret = theApps.get(id);
				if(ret != null)
					return ret;
			}
		} finally
		{
			lock.unlock();
		}
		lock = theLock.writeLock();
		lock.lock();
		try
		{
			Integer idInt = theAppIDs.get(appName);
			if(idInt != null)
			{
				ret = theApps.get(idInt);
				if(ret != null)
					return ret;
			}

			int id;
			String descrip;
			String configClassStr;
			String configXML;
			java.sql.Statement stmt = null;
			java.sql.ResultSet rs = null;
			checkConnection();
			try
			{
				stmt = thePRISMSConnection.createStatement();
				rs = stmt.executeQuery("SELECT id, appdescrip, configClass, configXML FROM "
					+ DBOWNER + "prisms_application WHERE appName = " + toSQL(appName));
				if(!rs.next())
					throw new PrismsException("The application \"" + appName
						+ "\" is not recognized.");
				id = rs.getInt(1);
				descrip = rs.getString(2);
				configClassStr = rs.getString(3);
				configXML = rs.getString(4);
				rs.close();
				rs = null;
				ret = theApps.get(new Integer(id));
				if(ret != null)
					return ret;
				ret = new DBApplication();

				rs = stmt.executeQuery("SELECT adminGroup FROM " + DBOWNER
					+ "prisms_app_admin_group" + " WHERE adminApp = " + id);
				while(rs.next())
					ret.addAdminGroup(getGroup(rs.getInt(1)));
			} catch(java.sql.SQLException e)
			{
				throw new PrismsException("Could not query for application " + appName, e);
			} finally
			{
				if(rs != null)
					try
					{
						rs.close();
					} catch(SQLException e)
					{
						log.warn("Could not close app result set", e);
					}
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{
						log.warn("Could not close app statement", e);
					}
			}
			ret.setDataSource(this);
			ret.setName(appName);
			ret.setDescription(descrip);
			ret.setConfigXML(configXML);
			ret.setConfigClass(configClassStr);
			theAppIDs.put(appName, new Integer(id));
			theApps.put(new Integer(id), ret);
		} finally
		{
			lock.unlock();
		}
		return ret;
	}

	private void configure(PrismsApplication app) throws PrismsException
	{
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application object " + app
				+ " was not created by this user source");
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			if(theAppsConfigured.contains(app.getName()))
				return;
		} finally
		{
			lock.unlock();
		}
		DBApplication dbApp = (DBApplication) app;
		if(dbApp.getConfig() == null)
			throw new PrismsException("Application " + app
				+ " does not have a valid configuration class");
		if(dbApp.getConfigXML() == null)
			throw new PrismsException("Application " + app
				+ " does not have a valid configuration XML");
		org.dom4j.Element configEl = dbApp.parseConfigXML();
		if(theAppsConfigured.contains(app.getName()))
			return;
		try
		{
			dbApp.getConfig().configureApp(app, configEl);
		} catch(Throwable e)
		{
			throw new PrismsException("Could not configure application " + app.getName(), e);
		}
		lock = theLock.writeLock();
		lock.lock();
		try
		{
			theAppsConfigured.add(app.getName());
		} catch(Throwable e)
		{
			throw new PrismsException("Could not configure application " + app.getName(), e);
		} finally
		{
			lock.unlock();
		}
		app.setConfigured();
	}

	private void configure(ClientConfig client) throws PrismsException
	{
		if(!(client.getApp() instanceof DBApplication))
			throw new PrismsException("Application " + client.getApp()
				+ " was not created by this user source");
		if(!(client instanceof DBClientConfig))
			throw new PrismsException("Client " + client + " of application " + client.getApp()
				+ " was not created by this user source");
		AppConfig config = ((DBApplication) client.getApp()).getConfig();
		org.dom4j.Element configEl = getConfigXML(((DBClientConfig) client).getConfigXML());
		config.configureClient(client, configEl);
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#addAdminGroup(prisms.arch.PrismsApplication,
	 *      prisms.arch.ds.UserGroup)
	 */
	public void addAdminGroup(PrismsApplication app, UserGroup adminGroup)
	{
		// TODO: Implement this
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#removeAdminGroup(prisms.arch.PrismsApplication,
	 *      prisms.arch.ds.UserGroup)
	 */
	public void removeAdminGroup(PrismsApplication app, UserGroup adminGroup)
	{
		// TODO: Implement this
	}

	/**
	 * @see prisms.arch.ds.UserSource#getClient(PrismsApplication, java.lang.String)
	 */
	public ClientConfig getClient(PrismsApplication app, String name) throws PrismsException
	{
		int id;
		String descrip;
		String serializerStr;
		String configXML;
		Number timeout;
		java.sql.Statement stmt = null;
		java.sql.ResultSet rs = null;
		checkConnection();
		DBClientConfig ret;
		Lock lock = theLock.writeLock();
		lock.lock();

		try
		{
			ret = theClients.get(app.getName() + "/" + name);
			if(ret != null)
				return ret;

			stmt = thePRISMSConnection.createStatement();

			rs = stmt.executeQuery("SELECT * FROM " + DBOWNER
				+ "prisms_client_config WHERE configApp = " + theAppIDs.get(app.getName())
				+ " AND configName = " + toSQL(name));

			if(!rs.next())
			{
				log.info("The client configuration \"" + name + "\" for application "
					+ app.getName() + " is not recognized.");

				throw new PrismsException("The client configuration \"" + name
					+ "\" for application " + app.getName() + " is not recognized.");
			}
			id = rs.getInt("id");
			descrip = rs.getString("configDescrip");
			serializerStr = rs.getString("configSerializer");
			configXML = rs.getString("configXML");
			timeout = (Number) rs.getObject("sessionTimeout");
		} catch(java.sql.SQLException e)
		{
			throw new PrismsException("Could not query for client " + name + " of application "
				+ app.getName(), e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.warn("Could not close client config result set", e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.warn("Could not close client config statement", e);
				}
		}
		ret = new DBClientConfig(id, app, name);
		ret.setDescription(descrip);
		ret.setSerializerClass(serializerStr);
		ret.setConfigXML(configXML);
		if(timeout != null)
			ret.setSessionTimeout(timeout.longValue());
		lock = theLock.writeLock();
		lock.lock();
		try
		{
			if(!theClients.containsKey(app.getName() + "/" + name))
				theClients.put(app.getName() + "/" + name, ret);
		} finally
		{
			lock.unlock();
		}
		return ret;
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#getAllClients(prisms.arch.PrismsApplication)
	 */
	public ClientConfig [] getAllClients(PrismsApplication app) throws PrismsException
	{
		java.sql.Statement stmt = null;
		java.sql.ResultSet rs = null;
		ArrayList<ClientConfig> ret = new ArrayList<ClientConfig>();
		checkConnection();
		Lock lock = theLock.writeLock();
		lock.lock();

		try
		{
			stmt = thePRISMSConnection.createStatement();

			rs = stmt.executeQuery("SELECT configName" + " FROM " + DBOWNER
				+ "prisms_client_config WHERE configApp = " + theAppIDs.get(app.getName()));

			while(rs.next())
				ret.add(getClient(app, rs.getString(1)));
		} catch(java.sql.SQLException e)
		{
			throw new PrismsException(
				"Could not query for clients of application " + app.getName(), e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.warn("Could not close client config result set", e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.warn("Could not close client config statement", e);
				}
		}
		java.util.Collections.sort(ret, new java.util.Comparator<ClientConfig>()
		{
			public int compare(ClientConfig o1, ClientConfig o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return ret.toArray(new ClientConfig [ret.size()]);
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#createClient(prisms.arch.PrismsApplication,
	 *      java.lang.String)
	 */
	public ClientConfig createClient(PrismsApplication app, String name)
	{
		// TODO: Implement this
		return null;
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#deleteClient(prisms.arch.ClientConfig)
	 */
	public void deleteClient(ClientConfig client)
	{
		// TODO: Implement this
	}

	/**
	 * @see prisms.arch.ds.UserSource#createSession(prisms.arch.ClientConfig, prisms.arch.ds.User,
	 *      boolean)
	 */
	public PrismsSession createSession(ClientConfig client, User user, boolean asService)
		throws PrismsException
	{
		if(user.getApp() != client.getApp())
		{
			User appUser = getUser(user, client.getApp());
			if(appUser == null)
				throw new PrismsException("User " + user
					+ " is not permitted to access application " + client.getApp().getName());
			user = appUser;
		}
		if(!client.getApp().isConfigured())
			configure(client.getApp());
		if(!client.isConfigured())
			configure(client);
		PrismsSession ret = new PrismsSession(client.getApp(), client, user, asService);
		client.getApp().configureSession(ret);
		try
		{
			client.configure(ret);
		} catch(Throwable e)
		{
			throw new PrismsException("Could not configure client " + client.getName()
				+ " of application " + client.getApp().getName() + " for user " + user, e);
		}

		prisms.arch.AppConfig appConfig;
		java.sql.Statement stmt = null;
		java.sql.ResultSet rs = null;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT configClass FROM " + DBOWNER
				+ "prisms_application WHERE" + " id = " + theAppIDs.get(client.getApp().getName()));
			if(!rs.next())
				throw new PrismsException("No application named " + client.getApp().getName());
			String configClass = rs.getString(1);
			try
			{
				appConfig = (prisms.arch.AppConfig) Class.forName(configClass).newInstance();
			} catch(Throwable e)
			{
				throw new PrismsException("Could not instantiate AppConfig class " + configClass, e);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not query for application configuration "
				+ client.getApp().getName(), e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.warn("Could not close server config result set", e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.warn("Could not close server config statement", e);
				}
		}
		String config = ((DBClientConfig) client).getConfigXML();
		org.dom4j.Element configEl = getConfigXML(config);
		try
		{
			appConfig.configureSession(ret, configEl);
		} catch(Throwable e)
		{
			throw new PrismsException("Could not configure session of application "
				+ client.getApp().getName(), e);
		}
		return ret;
	}

	org.dom4j.Element getConfigXML(String configLocation) throws PrismsException
	{
		java.net.URL configURL;
		if(configLocation.startsWith("classpath://"))
		{
			configURL = prisms.arch.ds.UserSource.class.getResource(configLocation
				.substring("classpath:/".length()));
			if(configURL == null)
				throw new PrismsException("Classpath configuration URL " + configLocation
					+ " refers to a non-existent resource");
		}
		else
		{
			try
			{
				configURL = new java.net.URL(configLocation);
			} catch(java.net.MalformedURLException e)
			{
				throw new PrismsException("Configuration URL " + configLocation + " is malformed",
					e);
			}
		}
		org.dom4j.Element configEl;
		try
		{
			configEl = new org.dom4j.io.SAXReader().read(configURL).getRootElement();
		} catch(Exception e)
		{
			throw new PrismsException("Could not read client config file " + configLocation, e);
		}
		return configEl;
	}

	/**
	 * @see prisms.arch.ds.UserSource#getHashing()
	 */
	public Hashing getHashing()
	{
		Hashing ret = theHashing.clone();
		ret.randomlyFillSecondary(5);
		return ret;
	}

	PasswordData [] getPasswordData(DBUser user, boolean latest)
	{
		Statement stmt = null;
		ResultSet rs = null;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		String sql = "SELECT * FROM " + DBOWNER + "prisms_user_password WHERE pwdUser="
			+ user.getID() + " ORDER BY pwdTime DESC";
		java.util.ArrayList<PasswordData> data = new ArrayList<PasswordData>();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				if(latest && data.size() > 0)
					break;
				PasswordData newData = new PasswordData();
				newData.id = rs.getInt("id");
				newData.thePasswordTime = rs.getLong("pwdTime");
				Number exp = (Number) rs.getObject("pwdExpire");
				newData.thePasswordExpire = exp == null ? Long.MAX_VALUE : exp.longValue();
				newData.thePasswordHash = parsePwdData(rs.getString("pwdData"));
				data.add(newData);
			}
		} catch(SQLException e)
		{
			log.error("Could not get password data for user " + user.getName() + ": SQL=" + sql, e);
			return null;
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		return data.toArray(new PasswordData [data.size()]);
	}

	/**
	 * @see prisms.arch.ds.UserSource#getKey(prisms.arch.ds.User, prisms.arch.ds.Hashing)
	 */
	public long [] getKey(User user, Hashing hashing)
	{
		PasswordData [] password = getPasswordData((DBUser) user, true);
		if(password.length == 0)
			return null;
		return hashing.generateKey(password[0].thePasswordHash);
	}

	// ManageableUserSource methods now

	/**
	 * @see prisms.arch.ds.ManageableUserSource#getAllUsers()
	 */
	public User [] getAllUsers() throws PrismsException
	{
		ArrayList<DBUser> ret = new ArrayList<DBUser>();
		Statement stmt = null;
		ResultSet rs = null;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT id, userName FROM " + DBOWNER + "prisms_user");
			while(rs.next())
			{
				int id = rs.getInt(1);
				String name = rs.getString(2);
				DBUser user = theUsers.get(name + "/null");
				if(user == null)
					user = new DBUser(this, name, id);
				ret.add(user);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Error getting all users", e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		lock = theLock.writeLock();
		lock.lock();
		try
		{
			for(DBUser user : ret)
				if(!theUsers.containsKey(user.getName() + "/null"))
					theUsers.put(user.getName() + "/null", user);
		} finally
		{
			lock.unlock();
		}
		java.util.Collections.sort(ret, new java.util.Comparator<User>()
		{
			public int compare(User o1, User o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return ret.toArray(new DBUser [ret.size()]);
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#getAllApps()
	 */
	public PrismsApplication [] getAllApps() throws PrismsException
	{
		ArrayList<Integer> appIDs = new ArrayList<Integer>();
		ArrayList<String> appNames = new ArrayList<String>();
		Statement stmt = null;
		ResultSet rs = null;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT id, appName FROM " + DBOWNER + "prisms_application");
			while(rs.next())
			{
				appIDs.add(new Integer(rs.getInt(1)));
				appNames.add(rs.getString(2));
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Error getting all users", e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		PrismsApplication [] ret = new PrismsApplication [appIDs.size()];
		for(int a = 0; a < ret.length; a++)
		{
			ret[a] = theApps.get(appIDs.get(a));
			if(ret[a] == null)
				ret[a] = getApp(appNames.get(a));
		}
		java.util.Arrays.sort(ret, new java.util.Comparator<PrismsApplication>()
		{
			public int compare(PrismsApplication o1, PrismsApplication o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return ret;
	}

	/**
	 * Creates a user with the given ID
	 * 
	 * @param name The ID of the user to get
	 * @return An object representing the given user
	 */
	public User createUser(String name) throws PrismsException
	{
		if(getUser(name) != null)
			throw new IllegalArgumentException("User " + name + " already exists");
		int id;
		DBUser ret;
		String sql = null;
		Statement stmt = null;
		checkConnection();
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			id = getNextID("prisms_user", stmt);
			sql = "INSERT INTO " + DBOWNER + "prisms_user (id, userName) VALUES (" + id + ", "
				+ toSQL(name) + ")";
			stmt.execute(sql);
			ret = new DBUser(this, name, id);
			theUsers.put(name + "/null", ret);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not create user " + name + ": SQL=" + sql, e);
		} finally
		{
			lock.unlock();
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		return ret;
	}

	public void setUserAccess(User user, PrismsApplication app, boolean accessible)
		throws PrismsException
	{
		checkConnection();
		// if user access is true do an insert
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			DBUser db_user = (DBUser) user;
			int appId = theAppIDs.get(app.getName()).intValue();

			Statement stmt = null;
			ResultSet rs = null;

			try
			{
				stmt = thePRISMSConnection.createStatement();
				rs = stmt.executeQuery("SELECT assocUser FROM " + DBOWNER + "prisms_user_app_assoc"
					+ " where assocUser = " + db_user.getID() + " and assocApp =  " + appId);
				if(rs.next() == accessible)
					return;

				if(accessible)
					stmt.execute("INSERT INTO " + DBOWNER + "prisms_user_app_assoc"
						+ " (assocUser, assocApp, encryption, validationClass) VALUES ("
						+ db_user.getID() + ", " + appId + ", '"
						+ DBUtils.getBoolString(user.isEncryptionRequired()) + "', NULL)");
				else
					stmt.execute("DELETE FROM " + DBOWNER
						+ "prisms_user_app_assoc where assocUser = " + db_user.getID()
						+ " and assocApp =  " + appId);
			} catch(SQLException e)
			{
				throw new PrismsException("Could not update user " + user.getName() + " for app: "
					+ app.getName() + " to have access=" + accessible, e);
			} finally
			{
				if(rs != null)
					try
					{
						rs.close();
					} catch(SQLException e)
					{}
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			if(!accessible)
			{
				String cacheKey = user.getName() + "/" + app.getName();
				DBUser userTemp = theUsers.get(cacheKey);
				if(userTemp != null)
					theUsers.remove(cacheKey);
			}
		} finally
		{
			lock.unlock();
		}
	}

	public void setEncryptionRequired(User user, PrismsApplication app, boolean encrypted)
		throws PrismsException
	{
		checkConnection();
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			Statement stmt = null;
			ResultSet rs = null;
			try
			{
				stmt = thePRISMSConnection.createStatement();
				DBUser db_user = (DBUser) user;
				int appId = theAppIDs.get(app.getName()).intValue();
				rs = stmt.executeQuery("SELECT assocUser FROM " + DBOWNER + "prisms_user_app_assoc"
					+ " where assocUser = " + db_user.getID() + " and assocApp =  " + appId);
				if(!rs.next())
					throw new IllegalStateException("Could not set encryption -- user: "
						+ user.getName() + " does not have permission for access to application: "
						+ app.getName());
				stmt.executeUpdate("UPDATE " + DBOWNER + "prisms_user_app_assoc SET encryption = '"
					+ DBUtils.getBoolString(encrypted) + "' WHERE assocUser = "
					+ ((DBUser) user).getID() + " and assocApp = " + appId);
			} catch(SQLException e)
			{
				throw new PrismsException("Could not set password data for user " + user, e);
			} finally
			{
				if(rs != null)
					try
					{
						rs.close();
					} catch(SQLException e)
					{}
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}

			String cacheKey = user.getName() + "/" + app.getName();
			DBUser userTemp = theUsers.get(cacheKey);
			if(userTemp != null)
				userTemp.setEncryptionRequired(encrypted);
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see prisms.arch.ds.UserSource#setPassword(prisms.arch.ds.User, long[], boolean)
	 */
	public void setPassword(User user, long [] hash, boolean isAdmin) throws PrismsException
	{
		prisms.arch.ds.PasswordConstraints constraints = getPasswordConstraints();
		PasswordData [] password = getPasswordData((DBUser) user, false);
		for(int i = 0; i < constraints.getNumPreviousUnique() && i < password.length; i++)
		{
			long [] oldHash = password[i].thePasswordHash;
			if(oldHash == null)
				continue;
			if(oldHash.length != hash.length)
				continue;
			int h;
			for(h = 0; h < oldHash.length; h++)
				if(oldHash[h] != hash[h])
					break;
			if(h == oldHash.length)
			{
				String msg = "Password must not be the same as ";
				if(constraints.getNumPreviousUnique() == 1)
					throw new PrismsException(msg + "the current password");
				else if(constraints.getNumPreviousUnique() == 2)
					throw new PrismsException(msg + "either the current or the previous passwords");
				else
					throw new PrismsException(msg + "any of the previous "
						+ constraints.getNumPreviousUnique() + " passwords");
			}
		}
		long now = System.currentTimeMillis();
		if(!isAdmin && password.length > 0)
		{
			if(now > password[0].thePasswordTime
				&& (now - password[0].thePasswordTime) < constraints.getMinPasswordChangeInterval())
				throw new PrismsException("Password cannot be changed more than every "
					+ prisms.util.PrismsUtils.printTimeLength(constraints
						.getMinPasswordChangeInterval())
					+ "\nPassword can be changed at "
					+ prisms.util.PrismsUtils.print(password[0].thePasswordTime
						+ constraints.getMinPasswordChangeInterval()));
		}

		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		String sql = null;
		try
		{
			stmt = thePRISMSConnection.createStatement();
			sql = "INSERT INTO " + DBOWNER + "prisms_user_password (id, pwdUser, pwdData,"
				+ " pwdTime, pwdExpire) VALUES (" + getNextID("prisms_user_password", stmt) + ", "
				+ ((DBUser) user).getID() + ", " + DBUtils.toSQL(join(hash)) + ", " + now + ", ";
			if(constraints.getMaxPasswordDuration() > 0)
				sql += (now + constraints.getMaxPasswordDuration());
			else
				sql += "NULL";
			sql += ")";
			stmt.execute(sql);

			for(int p = password.length - 1; p >= 0 && p >= constraints.getNumPreviousUnique(); p--)
			{
				sql = "DELETE FROM " + DBOWNER + "prisms_user_password WHERE id=" + password[p].id;
				stmt.execute(sql);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not set password data for user " + user + ": SQL="
				+ sql, e);
		} finally
		{
			lock.unlock();
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
	}

	/**
	 * @see prisms.arch.ds.UserSource#getPasswordExpiration(prisms.arch.ds.User)
	 */
	public long getPasswordExpiration(User user) throws PrismsException
	{
		PasswordData [] password = getPasswordData((DBUser) user, true);
		if(password.length == 0)
			return Long.MAX_VALUE;
		return password[0].thePasswordExpire;
	}

	public void setPasswordExpiration(User user, long time) throws PrismsException
	{
		prisms.arch.ds.PasswordConstraints constraints = getPasswordConstraints();
		if(constraints.getMaxPasswordDuration() > 0
			&& time - System.currentTimeMillis() > constraints.getMaxPasswordDuration())
			throw new PrismsException("Password expiration cannot be set for more than "
				+ prisms.util.PrismsUtils.printTimeLength(constraints.getMaxPasswordDuration())
				+ " from current date");
		PasswordData [] password = getPasswordData((DBUser) user, true);
		if(password.length == 0)
			return;
		Lock lock = theLock.writeLock();
		lock.lock();
		Statement stmt = null;
		String sql;
		String toSet;
		if(time < Long.MAX_VALUE)
			toSet = "" + time;
		else
			toSet = "NULL";
		sql = "UPDATE " + DBOWNER + "prisms_user_password SET pwdExpire=" + toSet + " WHERE id="
			+ password[0].id;
		try
		{
			stmt = thePRISMSConnection.createStatement();
			stmt.executeUpdate(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not set password expiration for user " + user
				+ ": SQL=" + sql, e);
		} finally
		{
			lock.unlock();
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#deleteUser(prisms.arch.ds.User)
	 */
	public void deleteUser(User user) throws PrismsException
	{
		checkConnection();
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			Statement stmt = null;
			try
			{
				stmt = thePRISMSConnection.createStatement();
				stmt.execute("DELETE FROM " + DBOWNER + "prisms_user WHERE id = "
					+ ((DBUser) user).getID());
			} catch(SQLException e)
			{
				throw new PrismsException("Could not delete user " + user.getName(), e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			java.util.Iterator<DBUser> iter = theUsers.values().iterator();
			while(iter.hasNext())
				if(iter.next().equals(user))
					iter.remove();
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#rename(prisms.arch.ds.User, java.lang.String)
	 */
	public void rename(User user, String newName) throws PrismsException
	{
		checkConnection();
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			Statement stmt = null;
			try
			{
				stmt = thePRISMSConnection.createStatement();
				stmt.executeUpdate("UPDATE " + DBOWNER + "prisms_user SET userName = "
					+ toSQL(newName) + " WHERE id = " + ((DBUser) user).getID());
			} catch(SQLException e)
			{
				throw new PrismsException("Could not rename user " + user.getName(), e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			// Uncache the renamed users
			DBUser [] users = new DBUser [0];
			java.util.Iterator<DBUser> iter = theUsers.values().iterator();
			while(iter.hasNext())
			{
				DBUser next = iter.next();
				if(next.equals(user))
				{
					users = prisms.util.ArrayUtils.add(users, next);
					next.setName(newName);
					iter.remove();
				}
			}
			for(DBUser u : users)
				theUsers.put(u.getName(), u);
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see prisms.arch.ds.UserSource#getGroup(prisms.arch.PrismsApplication, java.lang.String)
	 */
	public UserGroup getGroup(PrismsApplication app, String groupName) throws PrismsException
	{
		int id;
		Statement stmt = null;
		ResultSet rs = null;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT id FROM " + DBOWNER
				+ "prisms_user_group WHERE groupApp = " + theAppIDs.get(app.getName())
				+ " AND groupName=" + toSQL(groupName));
			if(!rs.next())
				return null;
			id = rs.getInt(1);
		} catch(SQLException e)
		{
			throw new PrismsException("Error getting all users", e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		return getGroup(id);
	}

	/**
	 * @see prisms.arch.ds.UserSource#getGroups(prisms.arch.PrismsApplication)
	 */
	public UserGroup [] getGroups(PrismsApplication app) throws PrismsException
	{
		ArrayList<Integer> groupIDs = new ArrayList<Integer>();
		Statement stmt = null;
		ResultSet rs = null;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT id, groupName, groupDescrip FROM " + DBOWNER
				+ "prisms_user_group WHERE groupApp = " + theAppIDs.get(app.getName()));
			while(rs.next())
				groupIDs.add(new Integer(rs.getInt(1)));
		} catch(SQLException e)
		{
			throw new PrismsException("Error getting all users", e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		DBGroup [] ret = new DBGroup [groupIDs.size()];
		for(int g = 0; g < ret.length; g++)
			ret[g] = (DBGroup) getGroup(groupIDs.get(g).intValue());
		java.util.Arrays.sort(ret, new java.util.Comparator<DBGroup>()
		{
			public int compare(DBGroup o1, DBGroup o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return ret;
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#createGroup(java.lang.String,
	 *      prisms.arch.PrismsApplication)
	 */
	public UserGroup createGroup(String name, PrismsApplication app) throws PrismsException
	{
		int id;
		Statement stmt = null;
		DBGroup ret;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			id = getNextID("prisms_user_group", stmt);
			stmt.execute("INSERT INTO " + DBOWNER
				+ "prisms_user_group (id, groupName, groupApp) VALUES (" + id + ", " + toSQL(name)
				+ ", " + theAppIDs.get(app.getName()) + ")");
			ret = new DBGroup(this, name, app, id);
			ret.setPermissions(new SimplePermissions());
			theGroups.put(new Integer(id), ret);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not create user " + name, e);
		} finally
		{
			lock.unlock();
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		return ret;
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#deleteGroup(prisms.arch.ds.UserGroup)
	 */
	public void deleteGroup(UserGroup group) throws PrismsException
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			Statement stmt = null;
			try
			{
				stmt = thePRISMSConnection.createStatement();
				stmt.execute("DELETE FROM " + DBOWNER + "prisms_user_group WHERE id = "
					+ ((DBGroup) group).getID());
			} catch(SQLException e)
			{
				throw new PrismsException("Could not delete group " + group.getName(), e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			for(SimpleUser user : theUsers.values())
				user.removeFrom(group);
			theGroups.remove(new Integer(((DBGroup) group).getID()));
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#rename(prisms.arch.ds.UserGroup, java.lang.String)
	 */
	public void rename(UserGroup group, String newName) throws PrismsException
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		Statement stmt = null;
		try
		{
			stmt = thePRISMSConnection.createStatement();
			stmt.executeUpdate("UPDATE " + DBOWNER + "prisms_user_group SET groupName= "
				+ toSQL(newName) + " WHERE id = " + ((DBGroup) group).getID());
		} catch(SQLException e)
		{
			throw new PrismsException("Could not rename group " + group.getName(), e);
		} finally
		{
			lock.unlock();
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		((DBGroup) group).setName(newName);
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#setDescription(prisms.arch.ds.UserGroup,
	 *      java.lang.String)
	 */
	public void setDescription(UserGroup group, String descrip) throws PrismsException
	{
		if(!(group instanceof DBGroup))
			throw new IllegalStateException("Group " + group
				+ " did not come from this user source");
		Lock lock = theLock.writeLock();
		lock.lock();
		Statement stmt = null;
		try
		{
			stmt = thePRISMSConnection.createStatement();
			stmt.executeUpdate("UPDATE " + DBOWNER + "prisms_user_group SET groupDescrip= "
				+ toSQL(descrip) + " WHERE id = " + ((DBGroup) group).getID());
		} catch(SQLException e)
		{
			throw new PrismsException("Could not set properties of group " + group.getName(), e);
		} finally
		{
			lock.unlock();
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		((DBGroup) group).setDescription(descrip);
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#setDescription(Permission, String)
	 */
	public void setDescription(Permission permission, String descrip) throws PrismsException
	{
		if(!(permission instanceof DBPermission))
			throw new IllegalStateException("Permission " + permission
				+ " did not come from this user source");
		Lock lock = theLock.writeLock();
		lock.lock();
		Statement stmt = null;
		try
		{
			stmt = thePRISMSConnection.createStatement();
			stmt.executeUpdate("UPDATE " + DBOWNER + "prisms_permission SET pDescrip= "
				+ toSQL(descrip) + " WHERE id = " + ((DBPermission) permission).getID());
		} catch(SQLException e)
		{
			throw new PrismsException("Could not set properties of group " + permission.getName(),
				e);
		} finally
		{
			lock.unlock();
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		((DBPermission) permission).setDescrip(descrip);
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#addUserToGroup(prisms.arch.ds.User,
	 *      prisms.arch.ds.UserGroup)
	 */
	public void addUserToGroup(User user, UserGroup group) throws PrismsException
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			Statement stmt = null;
			try
			{
				stmt = thePRISMSConnection.createStatement();
				stmt.execute("INSERT INTO " + DBOWNER
					+ "prisms_user_group_assoc (assocUser, assocGroup) VALUES ("
					+ ((DBUser) user).getID() + ", " + ((DBGroup) group).getID() + ")");
			} catch(SQLException e)
			{
				throw new PrismsException("Could not add user " + user + " to group " + group, e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			for(SimpleUser user2 : theUsers.values())
				if(user.equals(user2))
					user2.addTo(group);
			((DBUser) user).addTo(group);
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#removeUserFromGroup(prisms.arch.ds.User,
	 *      prisms.arch.ds.UserGroup)
	 */
	public void removeUserFromGroup(User user, UserGroup group) throws PrismsException
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			Statement stmt = null;
			try
			{
				stmt = thePRISMSConnection.createStatement();
				stmt.execute("DELETE FROM " + DBOWNER
					+ "prisms_user_group_assoc WHERE assocUser = " + ((DBUser) user).getID()
					+ " AND assocGroup = " + ((DBGroup) group).getID());
			} catch(SQLException e)
			{
				throw new PrismsException("Could not remove user " + user + " from group " + group,
					e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			for(SimpleUser user2 : theUsers.values())
				if(user.equals(user2))
					user2.removeFrom(group);
			((DBUser) user).removeFrom(group);
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#createApplication(java.lang.String,
	 *      java.lang.String, java.lang.String, java.lang.String)
	 */
	public PrismsApplication createApplication(String name, String descrip, String configClass,
		String configXML) throws PrismsException
	{
		if(getApp(name) != null)
			throw new PrismsException("Application " + name + " already exists");
		int id;
		DBApplication ret;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			Statement stmt = null;
			try
			{
				stmt = thePRISMSConnection.createStatement();
				id = getNextID("prisms_application", stmt);
				stmt.execute("INSERT INTO " + DBOWNER
					+ "prisms_application (id, appName, appDescrip, appClass,"
					+ "configClass, configXML) VALUES (" + id + ", " + toSQL(name) + ", "
					+ toSQL(descrip) + ", " + toSQL(configClass) + ", " + toSQL(configXML) + ")");
			} catch(SQLException e)
			{
				throw new PrismsException("Could not create application " + name, e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			ret = new DBApplication();
			ret.setDataSource(this);
			ret.setName(name);
			theApps.put(new Integer(id), ret);
			theAppIDs.put(name, new Integer(id));
		} finally
		{
			lock.unlock();
		}
		return ret;
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#rename(prisms.arch.PrismsApplication,
	 *      java.lang.String)
	 */
	public void rename(PrismsApplication app, String newName) throws PrismsException
	{
		String oldName = app.getName();
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application object " + app
				+ " was not created by this user source");
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			int id = theAppIDs.get(app.getName()).intValue();
			Statement stmt = null;
			try
			{
				stmt = thePRISMSConnection.createStatement();
				stmt.executeUpdate("UPDATE " + DBOWNER + "prisms_application SET appName= "
					+ toSQL(newName) + " WHERE id = " + id);
			} catch(SQLException e)
			{
				throw new PrismsException("Could not rename application " + app.getName(), e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			theAppIDs.remove(app.getName());
			theAppIDs.put(newName, new Integer(id));
			theApps.remove(new Integer(id));
			theApps.put(new Integer(id), (DBApplication) app);
			if(theAppsConfigured.contains(oldName))
			{
				theAppsConfigured.remove(oldName);
				theAppsConfigured.add(newName);
			}
		} finally
		{
			lock.unlock();
		}
		app.setName(newName);
	}

	/**
	 * Sets the description, configClass, and configXML properties of the application
	 * 
	 * @param app The application to modify the properties of
	 * @throws PrismsException If an error occurs updating the application
	 */
	public void changeProperties(DBApplication app) throws PrismsException
	{
		Integer id = theAppIDs.get(app.getName());
		if(id == null)
			throw new PrismsException("Application " + app.getName() + " not recognized");
		checkConnection();
		Statement stmt = null;
		try
		{
			String configClass;
			if(app.getConfig() == null)
				configClass = null;
			else if(app.getConfig() instanceof PlaceholderAppConfig)
				configClass = ((PlaceholderAppConfig) app.getConfig()).getAppConfigClassName();
			else
				configClass = app.getConfig().getClass().getName();
			stmt = thePRISMSConnection.createStatement();
			stmt.executeUpdate("UPDATE " + DBOWNER + "prisms_application SET appDescrip="
				+ toSQL(app.getDescription()) + ", configClass=" + toSQL(configClass)
				+ ", configxml=" + toSQL(app.getConfigXML()) + " WHERE id=" + id);
		} catch(SQLException e)
		{
			throw new PrismsException(
				"Could not update properties of application " + app.getName(), e);
		}
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#getPermissions(prisms.arch.PrismsApplication)
	 */
	public Permission [] getPermissions(PrismsApplication app) throws PrismsException
	{
		ArrayList<DBPermission> ret = new ArrayList<DBPermission>();
		Statement stmt = null;
		ResultSet rs = null;
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			rs = stmt.executeQuery("SELECT id, pName, pDescrip FROM " + DBOWNER
				+ "prisms_permission WHERE pApp = " + theAppIDs.get(app.getName()));
			while(rs.next())
			{
				int id = rs.getInt(1);
				String name = rs.getString(2);
				String descrip = rs.getString(3);
				DBPermission permission = new DBPermission(name, descrip, app, id);
				ret.add(permission);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Error getting all permissions", e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		java.util.Collections.sort(ret, new java.util.Comparator<DBPermission>()
		{
			public int compare(DBPermission o1, DBPermission o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return ret.toArray(new DBPermission [ret.size()]);
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#createPermission(prisms.arch.PrismsApplication,
	 *      java.lang.String, java.lang.String)
	 */
	public Permission createPermission(PrismsApplication app, String name, String descrip)
		throws PrismsException
	{
		int id;
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			stmt = thePRISMSConnection.createStatement();
			id = getNextID("prisms_permission", stmt);
			stmt.execute("INSERT INTO " + DBOWNER
				+ "prisms_permission (id, pApp, pName, pDescrip) VALUES" + "(" + id + ", "
				+ theAppIDs.get(app.getName()) + ", " + toSQL(name) + ", " + toSQL(descrip) + ")");
		} catch(SQLException e)
		{
			throw new PrismsException("Could not create permission " + name, e);
		} finally
		{
			lock.unlock();
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		return new DBPermission(name, descrip, app, id);
	}

	public void deletePermission(Permission permission) throws PrismsException
	{
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			try
			{
				stmt = thePRISMSConnection.createStatement();
				stmt.execute("DELETE FROM " + DBOWNER + "prisms_permission WHERE id = "
					+ ((DBPermission) permission).getID());
			} catch(SQLException e)
			{
				throw new PrismsException("Could not delete permission " + permission.getName(), e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			for(SimpleUser user : theUsers.values())
				for(UserGroup group : user.getGroups())
					if(group.getPermissions().has(permission.getName()))
						((SimplePermissions) group.getPermissions()).removePermission(permission
							.getName());
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#addPermission(UserGroup, Permission)
	 */
	public void addPermission(UserGroup group, Permission permission) throws PrismsException
	{
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			try
			{
				stmt = thePRISMSConnection.createStatement();
				stmt.execute("INSERT INTO " + DBOWNER
					+ "prisms_group_permissions (assocGroup, assocPermission)" + " VALUES" + "("
					+ ((DBGroup) group).getID() + ", " + ((DBPermission) permission).getID() + ")");
			} catch(SQLException e)
			{
				throw new PrismsException("Could not add permission " + permission + " to group "
					+ group.getName(), e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			for(SimpleUser user : theUsers.values())
				for(UserGroup _group : user.getGroups())
					if(_group.equals(group) && _group.getPermissions() != group.getPermissions())
						((SimplePermissions) _group.getPermissions()).addPermission(permission);
			((SimplePermissions) group.getPermissions()).addPermission(permission);
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see prisms.arch.ds.ManageableUserSource#removePermission(UserGroup, Permission)
	 */
	public void removePermission(UserGroup group, Permission permission) throws PrismsException
	{
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			try
			{
				stmt = thePRISMSConnection.createStatement();
				stmt.execute("DELETE FROM " + DBOWNER
					+ "prisms_group_permissions WHERE assocGroup = " + ((DBGroup) group).getID()
					+ " AND assocPermission = " + ((DBPermission) permission).getID());
			} catch(SQLException e)
			{
				throw new PrismsException("Could not remove permission " + permission
					+ " from group " + group.getName(), e);
			} finally
			{
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			for(SimpleUser user : theUsers.values())
				for(UserGroup _group : user.getGroups())
					if(_group.equals(group) && _group.getPermissions() != group.getPermissions())
						((SimplePermissions) _group.getPermissions()).removePermission(permission
							.getName());
			((SimplePermissions) group.getPermissions()).removePermission(permission.getName());
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see prisms.arch.ds.UserSource#disconnect()
	 */
	public void disconnect()
	{
		for(PrismsApplication app : theApps.values())
		{
			try
			{
				app.destroy();
			} catch(Throwable e)
			{
				log.error("Exception destroying application " + app.getName(), e);
			}
		}
		theApps.clear();
		theAppIDs.clear();
		theHashing = null;
		theUsers.clear();
		if(thePRISMSConnection == null)
			return;
		thePersisterFactory.disconnect(thePRISMSConnection, theConfigEl);
	}

	void forceResetUsers() throws PrismsException
	{
		Statement stmt = null;
		ResultSet rs = null;
		boolean hasUsers = false;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			try
			{
				stmt = thePRISMSConnection.createStatement();
				rs = stmt.executeQuery("SELECT COUNT(*) FROM " + DBOWNER + "prisms_user");
				hasUsers = rs.next() && rs.getInt(1) > 0;

			} catch(SQLException e)
			{
				throw new IllegalStateException("No prisms_user table in database", e);
			} finally
			{
				if(rs != null)
					try
					{
						rs.close();
					} catch(SQLException e)
					{}
				if(stmt != null)
					try
					{
						stmt.close();
					} catch(SQLException e)
					{}
			}
			stmt = null;
			if(hasUsers)
			{
				log.warn("Resetting hashing data with users present"
					+ "--all user passwords will be reset");
				try
				{
					stmt = thePRISMSConnection.createStatement();
					stmt.executeUpdate("UPDATE " + DBOWNER + "prisms_user SET pwdData=NULL");
				} catch(SQLException e)
				{
					log.error("Could not reset user passwords", e);
				}
			}
		} finally
		{
			lock.unlock();
		}
		// Ensure that admin/admin (username/password) exists on the server so that the passwords
		// may be reset
		User admin = getUser("admin");
		if(admin == null)
			admin = createUser("admin");
		setPassword(admin, theHashing.partialHash("admin"), true);
	}

	private String join(long [] hash)
	{
		String ret = "";
		for(int h = 0; h < hash.length; h++)
			ret += hash[h] + (h < hash.length - 1 ? ":" : "");
		return ret;
	}

	private long [] parsePwdData(String joined)
	{
		if(joined == null)
			return new long [10];
		String [] split = joined.split(":");
		long [] ret = new long [split.length];
		for(int i = 0; i < ret.length; i++)
			ret[i] = Long.parseLong(split[i]);
		return ret;
	}

	int getNextID(String tableName, Statement stmt) throws SQLException
	{
		int id = 0;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery("SELECT id FROM " + DBOWNER + tableName + " ORDER BY id");
			while(rs.next())
			{
				int tempID = rs.getInt(1);
				if(id != tempID)
					break;
				id++;
			}
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
		return id;
	}

	/**
	 * Checks the database connection to ensure (if possible) that the database is accessible.
	 * 
	 * @throws IllegalStateException If the database is not accessible and the connection cannot be
	 *         renewed
	 */
	public void checkConnection() throws IllegalStateException
	{
		try
		{
			if(thePRISMSConnection == null || thePRISMSConnection.isClosed())
			{
				thePRISMSConnection = thePersisterFactory.getConnection(theConfigEl, null);
				DBOWNER = thePersisterFactory.getTablePrefix(thePRISMSConnection, theConfigEl
					.element("connection"), null);
			}
		} catch(SQLException e)
		{
			throw new IllegalStateException("Could not renew connection ", e);
		}
	}
}
