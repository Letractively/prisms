/**
 * DBUserSource.java Created Jun 24, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import static prisms.util.DBUtils.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;

import org.apache.log4j.Logger;

import prisms.arch.*;
import prisms.arch.ds.*;
import prisms.util.ArrayUtils;
import prisms.util.DBUtils;

/**
 * A {@link prisms.arch.ds.ManageableUserSource} that obtains its information from a database
 */
public class DBUserSource implements prisms.arch.ds.ManageableUserSource
{
	static final Logger log = Logger.getLogger(DBUserSource.class);

	static class PasswordData
	{
		int id;

		long [] thePasswordHash;

		long thePasswordTime;

		long thePasswordExpire;
	}

	private java.util.concurrent.locks.ReentrantReadWriteLock theLock;

	private java.sql.Connection theConn;

	String DBOWNER;

	private org.dom4j.Element theConfigEl;

	private PersisterFactory thePersisterFactory;

	private String theAnonymousUserName;

	private Hashing theHashing;

	private DBUser [] theUserCache;

	private DBUser theAnonymousUser;

	private DBApplication [] theAppCache;

	private DBClientConfig [] theClientCache;

	private DBGroup [] theGroupCache;

	private DBPermission [] thePermissionCache;

	/**
	 * Creates a DBUserSource
	 */
	public DBUserSource()
	{
		theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
		theHashing = new Hashing();
		theAnonymousUserName = "anonymous";
	}

	public void configure(org.dom4j.Element configEl, PersisterFactory factory)
		throws PrismsException
	{
		theConfigEl = configEl;
		thePersisterFactory = factory;
		try
		{
			theConn = factory.getConnection(configEl.element("connection"), null);
		} catch(Exception e)
		{
			throw new PrismsException("Could not connect to PRISMS configuration database", e);
		}
		DBOWNER = factory.getTablePrefix(theConn, configEl.element("connection"), null);
		String anonUser = configEl.elementTextTrim("anonymous");
		if(anonUser != null)
			theAnonymousUserName = anonUser;
		// Set up hashing
		String sql = null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = theConn.createStatement();
			sql = "SELECT multiple, modulus FROM " + DBOWNER + "PRISMS_HASHING ORDER BY id";
			rs = stmt.executeQuery(sql);
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
				{
					log.error("Connection error", e);
				}
			}
			*/

			sql = null;
			try
			{
				stmt = theConn.createStatement();
				for(int i = 0; i < theHashing.getPrimaryMultiples().length; i++)
				{
					sql = "INSERT INTO " + DBOWNER
						+ "prisms_hashing (id, multiple, modulus) VALUES (" + i + ", "
						+ theHashing.getPrimaryMultiples()[i] + ", "
						+ theHashing.getPrimaryModulos()[i] + ")";
					stmt.execute(sql);
				}
			} catch(SQLException e)
			{
				throw new PrismsException("Cannot commit hashing values"
					+ "--passwords not persistable: SQL=" + sql, e);
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
			forceResetUsers();
		}
	}

	/**
	 * @return The database connection that this user source is using
	 */
	public java.sql.Connection getConnection()
	{
		return theConn;
	}

	/**
	 * @return The XML element used to configure this user source's database connection
	 */
	public org.dom4j.Element getConnectionConfig()
	{
		return theConfigEl.element("connection");
	}

	private void fillUserCache(Statement stmt) throws PrismsException
	{
		if(theAnonymousUserName != null)
			theAnonymousUser = new DBUser(this, theAnonymousUserName, -1);
		boolean killStatement = false;
		if(stmt == null)
		{
			killStatement = true;
			checkConnection();
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsException("Could not create statement", e);
			}
		}
		ResultSet rs = null;
		String sql = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		DBUser [] users;
		try
		{
			if(theUserCache != null)
				return; // Already filled before lock received
			if(theGroupCache == null)
				fillGroupCache(stmt);
			ArrayList<DBUser> userList = new ArrayList<DBUser>();
			sql = "SELECT * FROM " + DBOWNER + "prisms_user WHERE deleted=" + boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
				userList.add(new DBUser(this, rs.getString("userName"), rs.getInt("id")));
			rs.close();
			rs = null;
			users = userList.toArray(new DBUser [userList.size()]);

			ArrayList<Integer> userIDs = new ArrayList<Integer>();
			ArrayList<Integer> groupIDs = new ArrayList<Integer>();
			ArrayList<Integer> appIDs = new ArrayList<Integer>();
			sql = "SELECT assocUser, id, groupApp FROM " + DBOWNER
				+ "prisms_user_group_assoc INNER JOIN " + DBOWNER
				+ "prisms_user_group ON assocGroup=id WHERE deleted=" + boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				userIDs.add(new Integer(rs.getInt("assocUser")));
				groupIDs.add(new Integer(rs.getInt("id")));
				appIDs.add(new Integer(rs.getInt("groupApp")));
			}
			rs.close();
			rs = null;
			java.util.HashMap<Integer, DBApplication> apps = new java.util.HashMap<Integer, DBApplication>();
			for(int g = 0; g < groupIDs.size(); g++)
			{
				for(DBUser u : users)
				{
					if(u.getID() != userIDs.get(g).intValue())
						continue;
					DBApplication app = apps.get(appIDs.get(g));
					if(app == null)
					{
						app = getApplication(appIDs.get(g).intValue(), stmt);
						if(app == null)
						{
							log.error("Could not get application with ID " + appIDs.get(g));
							break;
						}
						apps.put(appIDs.get(g), app);
					}
					if(app.isDeleted())
						break;
					DBGroup group = getGroup(groupIDs.get(g).intValue(), app, stmt);
					if(group == null)
					{
						log.error("Could not get group with ID " + groupIDs.get(g));
						break;
					}
					if(group.isDeleted())
						break;
					u.addTo(group);
				}
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get PRISMS users: SQL=" + sql, e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(killStatement && stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		theUserCache = users;
	}

	private void fillGroupCache(Statement stmt) throws PrismsException
	{
		boolean killStatement = false;
		if(stmt == null)
		{
			killStatement = true;
			checkConnection();
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsException("Could not create statement", e);
			}
		}
		ResultSet rs = null;
		String sql = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		DBGroup [] groups;
		try
		{
			if(theGroupCache != null)
				return; // Already filled before lock received
			if(theAppCache == null)
				fillApplicationCache(stmt);
			if(thePermissionCache == null)
				fillPermissionCache(stmt);
			java.util.ArrayList<DBGroup> groupList = new ArrayList<DBGroup>();
			sql = "SELECT * FROM " + DBOWNER + "prisms_user_group WHERE deleted="
				+ boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				DBApplication app = getApplication(rs.getInt("groupApp"), stmt);
				if(app == null || app.isDeleted())
					continue;
				DBGroup group = new DBGroup(this, rs.getString("groupName"), app, rs.getInt("id"));
				group.setDescription(rs.getString("groupDescrip"));
				groupList.add(group);
			}
			rs.close();
			rs = null;
			groups = groupList.toArray(new DBGroup [groupList.size()]);

			ArrayList<Integer> groupIDs = new ArrayList<Integer>();
			ArrayList<Integer> permIDs = new ArrayList<Integer>();
			ArrayList<Integer> appIDs = new ArrayList<Integer>();
			sql = "SELECT assocGroup, id, pApp FROM " + DBOWNER
				+ "prisms_group_permissions INNER JOIN " + DBOWNER
				+ "prisms_permission ON assocPermission=id WHERE deleted=" + boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				groupIDs.add(new Integer(rs.getInt("assocGroup")));
				permIDs.add(new Integer(rs.getInt("id")));
				appIDs.add(new Integer(rs.getInt("pApp")));
			}
			rs.close();
			rs = null;
			java.util.HashMap<Integer, DBApplication> apps = new java.util.HashMap<Integer, DBApplication>();
			for(int p = 0; p < permIDs.size(); p++)
			{
				for(DBGroup g : groups)
				{
					if(g.getID() != groupIDs.get(p).intValue())
						continue;
					DBApplication app = apps.get(appIDs.get(p));
					if(app == null)
					{
						app = getApplication(appIDs.get(p).intValue(), stmt);
						if(app == null)
						{
							log.error("Could not get application with ID " + appIDs.get(p));
							break;
						}
						apps.put(appIDs.get(p), app);
					}
					if(app.isDeleted())
						break;
					DBPermission perm = getPermission(permIDs.get(p).intValue(), app, stmt);
					if(perm == null)
					{
						log.error("Could not get permission with ID " + permIDs.get(p));
						break;
					}
					if(perm.isDeleted())
						break;
					g.getPermissions().addPermission(perm);
				}
			}

			sql = "SELECT * FROM " + DBOWNER + "prisms_app_admin_group";
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				int appID = rs.getInt("adminApp");
				DBApplication app = getApplication(appID, stmt);
				if(app == null || app.isDeleted())
					continue;
				int groupID = rs.getInt("adminGroup");
				for(DBGroup group : groups)
				{
					if(group.getID() != groupID)
						continue;
					app.addAdminGroup(group);
				}
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get PRISMS user groups: SQL=" + sql, e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(killStatement && stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		theGroupCache = groups;
	}

	private void fillApplicationCache(Statement stmt) throws PrismsException
	{
		boolean killStatement = false;
		if(stmt == null)
		{
			killStatement = true;
			checkConnection();
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsException("Could not create statement", e);
			}
		}
		ResultSet rs = null;
		String sql = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		DBApplication [] apps;
		try
		{
			if(theAppCache != null)
				return; // Already filled before lock received
			checkConnection();
			java.util.ArrayList<DBApplication> appList = new ArrayList<DBApplication>();
			stmt = theConn.createStatement();
			sql = "SELECT * FROM " + DBOWNER + "prisms_application WHERE deleted="
				+ boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				DBApplication app = new DBApplication();
				appList.add(app);
				app.setDataSource(this);
				app.setID(rs.getInt("id"));
				app.setName(rs.getString("appName"));
				app.setDescription(rs.getString("appDescrip"));
				app.setConfigClass(rs.getString("configClass"));
				app.setConfigXML(rs.getString("configXML"));
				app.setUserRestrictive(boolFromSql(rs.getString("userRestrictive")));
			}
			rs.close();
			rs = null;
			apps = appList.toArray(new DBApplication [appList.size()]);
			// Don't add the admin groups here. We do that after we fill the group cache.
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get PRISMS applications: SQL=" + sql, e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(killStatement && stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		theAppCache = apps;
	}

	private void fillClientCache(Statement stmt) throws PrismsException
	{
		boolean killStatement = false;
		if(stmt == null)
		{
			killStatement = true;
			checkConnection();
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsException("Could not create statement", e);
			}
		}
		ResultSet rs = null;
		String sql = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		DBClientConfig [] clients;
		try
		{
			if(theClientCache != null)
				return; // Already filled before lock received
			if(theAppCache == null)
				fillApplicationCache(stmt);
			java.util.ArrayList<DBClientConfig> clientList = new ArrayList<DBClientConfig>();
			sql = "SELECT * FROM " + DBOWNER + "prisms_client_config WHERE deleted="
				+ boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				DBApplication app = getApplication(rs.getInt("configApp"), stmt);
				if(app == null || app.isDeleted())
					continue;
				DBClientConfig client = new DBClientConfig(rs.getInt("id"), app, rs
					.getString("configName"));
				client.setDescription(rs.getString("configDescrip"));
				client.setSerializerClass(rs.getString("configSerializer"));
				client.setConfigXML(rs.getString("configXML"));
				Number timeout = (Number) rs.getObject("sessionTimeout");
				client.setSessionTimeout(timeout == null ? -1 : timeout.longValue());
				client.setService(boolFromSql(rs.getString("isService")));
				client.setValidatorClass(rs.getString("validatorClass"));
				client.setAllowsAnonymous(boolFromSql(rs.getString("allowAnonymous")));
				clientList.add(client);
			}
			rs.close();
			rs = null;
			clients = clientList.toArray(new DBClientConfig [clientList.size()]);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get PRISMS client configurations: SQL=" + sql, e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(killStatement && stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		theClientCache = clients;
	}

	private void fillPermissionCache(Statement stmt) throws PrismsException
	{
		boolean killStatement = false;
		if(stmt == null)
		{
			killStatement = true;
			checkConnection();
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsException("Could not create statement", e);
			}
		}
		ResultSet rs = null;
		String sql = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		DBPermission [] perms;
		try
		{
			if(thePermissionCache != null)
				return; // Already filled before lock received
			if(theAppCache == null)
				fillApplicationCache(stmt);
			java.util.ArrayList<DBPermission> permList = new ArrayList<DBPermission>();
			sql = "SELECT * FROM " + DBOWNER + "prisms_permission WHERE deleted="
				+ boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				DBApplication app = getApplication(rs.getInt("pApp"), stmt);
				if(app == null || app.isDeleted())
					continue;
				DBPermission perm = new DBPermission(rs.getString("pName"), rs
					.getString("pDescrip"), app, rs.getInt("id"));
				permList.add(perm);
			}
			rs.close();
			rs = null;
			perms = permList.toArray(new DBPermission [permList.size()]);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get PRISMS permissions: SQL=" + sql, e);
		} finally
		{
			lock.unlock();
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(killStatement && stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		thePermissionCache = perms;
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
		String sql = null;
		try
		{
			stmt = theConn.createStatement();
			sql = "SELECT * FROM " + DBOWNER + "prisms_password_constraints";
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return ret;
			locked = boolFromSql(rs.getString("constraintsLocked"));
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
			throw new PrismsException("Could not retrieve password constraints: SQL=" + sql, e);
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

	public User getUser(String name) throws PrismsException
	{
		if(theUserCache == null)
			fillUserCache(null);
		if(name == null)
			return theAnonymousUser;
		for(DBUser user : theUserCache)
			if(user.getName().equals(name))
				return user;
		return null;
	}

	public UserGroup getGroup(PrismsApplication app, String groupName) throws PrismsException
	{
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application " + app + " was not created by this user source");
		int id;
		String sql = "SELECT id FROM " + DBOWNER + "prisms_user_group WHERE groupApp = "
			+ ((DBApplication) app).getID() + " AND groupName=" + toSQL(groupName);
		Statement stmt = null;
		ResultSet rs = null;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			id = rs.getInt(1);
			rs.close();
			rs = null;
			return dbGetGroup(id, (DBApplication) app, stmt);
		} catch(SQLException e)
		{
			throw new PrismsException("Error getting group " + groupName + " for app " + app
				+ ": SQL=" + sql, e);
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
	}

	public DBApplication getApp(String appName) throws PrismsException
	{
		if(theAppCache == null)
			fillApplicationCache(null);
		for(DBApplication app : theAppCache)
			if(app.getName().equals(appName))
				return app;
		return null;
	}

	public boolean canAccess(User user, PrismsApplication app) throws PrismsException
	{
		if(!(user instanceof DBUser))
			throw new PrismsException("User " + user + " was not created by this user source");
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application " + app + " was not created by this user source");
		if(!((DBApplication) app).isUserRestrictive())
			return true;
		if(user.equals(theAnonymousUser))
			return true;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_user_app_assoc WHERE assocUser="
			+ ((DBUser) user).getID() + " AND assocApp=" + ((DBApplication) app).getID();
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			return rs.next();
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get user-app association for user " + user
				+ ", app " + app + ": SQL=" + sql, e);
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
	}

	public DBClientConfig getClient(PrismsApplication app, String name) throws PrismsException
	{
		if(theClientCache == null)
			fillClientCache(null);
		for(DBClientConfig client : theClientCache)
			if(client.getApp() == app && client.getName().equals(name))
				return client;
		return null;
	}

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
			stmt = theConn.createStatement();
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

	public long [] getKey(User user, Hashing hashing)
	{
		PasswordData [] password = getPasswordData((DBUser) user, true);
		if(password.length == 0)
			return null;
		return hashing.generateKey(password[0].thePasswordHash);
	}

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
			stmt = theConn.createStatement();
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

	public long getPasswordExpiration(User user) throws PrismsException
	{
		PasswordData [] password = getPasswordData((DBUser) user, true);
		if(password.length == 0)
			return Long.MAX_VALUE;
		return password[0].thePasswordExpire;
	}

	public void lockUser(User user)
	{
		user.setLocked(true);
	}

	public DBClientConfig [] getAllClients(PrismsApplication app) throws PrismsException
	{
		if(theClientCache == null)
			fillClientCache(null);
		ArrayList<DBClientConfig> ret = new ArrayList<DBClientConfig>();
		for(DBClientConfig client : theClientCache)
			if(client.getApp() == app)
				ret.add(client);
		return ret.toArray(new DBClientConfig [ret.size()]);
	}

	public PrismsSession createSession(ClientConfig client, User user) throws PrismsException
	{
		if(!(client.getApp() instanceof DBApplication))
			throw new PrismsException("Application " + client.getApp()
				+ " was not created by this user source");
		if(!(client instanceof DBClientConfig))
			throw new PrismsException("Client " + client + " of application " + client.getApp()
				+ " was not created by this user source");
		if(!(user instanceof DBUser))
			throw new PrismsException("User " + user + " was not created by this user source");
		DBUser dbu = (DBUser) user;
		DBApplication app = (DBApplication) client.getApp();
		if(!app.isUserRestrictive())
		{
			String sql = null;
			checkConnection();
			Statement stmt = null;
			ResultSet rs = null;
			try
			{
				stmt = theConn.createStatement();
				sql = "SELECT * FROM " + DBOWNER + "prisms_user_app_assoc WHERE assocUser="
					+ dbu.getID() + " AND assocApp=" + app.getID();
				rs = stmt.executeQuery(sql);
				if(!rs.next())
					return null; // User does not have access to the application
			} catch(SQLException e)
			{
				throw new PrismsException("Could not query for user's application access: SQL="
					+ sql, e);
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
		if(!client.getApp().isConfigured())
			configure(client.getApp());
		if(!client.isConfigured())
			configure(client);
		PrismsSession ret = new PrismsSession(client.getApp(), client, user);
		client.getApp().configureSession(ret);
		AppConfig appConfig = app.getConfig();
		org.dom4j.Element configEl = getConfigXML(app.getConfigXML());
		try
		{
			appConfig.configureSession(ret, configEl);
		} catch(Throwable e)
		{
			throw new PrismsException("Could not configure session of application "
				+ client.getApp().getName(), e);
		}
		try
		{
			client.configure(ret);
		} catch(Throwable e)
		{
			throw new PrismsException("Could not configure client " + client.getName()
				+ " of application " + client.getApp().getName() + " for user " + user, e);
		}
		return ret;
	}

	private void configure(PrismsApplication app) throws PrismsException
	{
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application object " + app
				+ " was not created by this user source");
		DBApplication dbApp = (DBApplication) app;
		if(dbApp.isConfigured())
			return;
		synchronized(dbApp)
		{
			if(dbApp.isConfigured())
				return;
			if(dbApp.getConfig() == null)
				throw new PrismsException("Application " + app
					+ " does not have a valid configuration class");
			if(dbApp.getConfigXML() == null)
				throw new PrismsException("Application " + app
					+ " does not have a valid configuration XML");
			org.dom4j.Element configEl = dbApp.parseConfigXML();
			try
			{
				dbApp.getConfig().configureApp(app, configEl);
			} catch(Throwable e)
			{
				throw new PrismsException("Could not configure application " + app.getName(), e);
			}
			dbApp.setConfigured();
		}
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

	static org.dom4j.Element getConfigXML(String configLocation) throws PrismsException
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

	// ManageableUserSource methods now

	public DBUser [] getAllUsers() throws PrismsException
	{
		if(theUserCache == null)
			fillUserCache(null);
		DBUser [] users = theUserCache.clone();
		java.util.Arrays.sort(users, new java.util.Comparator<User>()
		{
			public int compare(User o1, User o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return users;
	}

	public DBGroup [] getGroups(PrismsApplication app) throws PrismsException
	{
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application " + app + " was not created by this user source");
		if(theGroupCache == null)
			fillGroupCache(null);
		ArrayList<DBGroup> groups = new ArrayList<DBGroup>();
		for(DBGroup group : theGroupCache)
			if(group.getApp() == app)
				groups.add(group);
		DBGroup [] ret = groups.toArray(new DBGroup [groups.size()]);
		java.util.Arrays.sort(ret, new java.util.Comparator<DBGroup>()
		{
			public int compare(DBGroup o1, DBGroup o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return ret;
	}

	public PrismsApplication [] getAllApps() throws PrismsException
	{
		if(theAppCache == null)
			fillApplicationCache(null);
		DBApplication [] apps = theAppCache.clone();
		java.util.Arrays.sort(apps, new java.util.Comparator<PrismsApplication>()
		{
			public int compare(PrismsApplication o1, PrismsApplication o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return apps;
	}

	public DBPermission [] getAllPermissions(PrismsApplication app) throws PrismsException
	{
		if(thePermissionCache == null)
			fillPermissionCache(null);
		ArrayList<DBPermission> ret = new ArrayList<DBPermission>();
		for(DBPermission perm : thePermissionCache)
			if(perm.getApp() == app)
				ret.add(perm);
		java.util.Collections.sort(ret, new java.util.Comparator<DBPermission>()
		{
			public int compare(DBPermission o1, DBPermission o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return ret.toArray(new DBPermission [ret.size()]);
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
			stmt = theConn.createStatement();
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

	public void setUserAccess(User user, PrismsApplication app, boolean accessible)
		throws PrismsException
	{
		if(!(user instanceof DBUser))
			throw new PrismsException("User " + user + " not created by this user source");
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application " + app + " not created by this user source");
		String sql = "SELECT * FROM " + DBOWNER + "prisms_user_app_assoc WHERE assocUser="
			+ ((DBUser) user).getID() + " AND assocApp=" + ((DBApplication) app).getID();
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			if(rs.next() == accessible)
				return;
			rs.close();
			rs = null;

			if(accessible)
				sql = "INSERT INTO " + DBOWNER
					+ "prisms_user_app_assoc (assocUser, assocApp) VALUES ("
					+ ((DBUser) user).getID() + ", " + ((DBApplication) app).getID() + ")";
			else
				sql = "DELETE FROM " + DBOWNER + "prisms_user_app_assoc WHERE assocUser="
					+ ((DBUser) user).getID() + " AND assocApp=" + ((DBApplication) app).getID();
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not set user accessibility: SQL=" + sql, e);
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
	}

	public User createUser(String name) throws PrismsException
	{
		if(getUser(name) != null)
			throw new PrismsException("User " + name + " already exists");
		if(name == null)
			throw new PrismsException("Cannot create user with no name");
		if(name.equals(theAnonymousUserName))
			throw new PrismsException("Cannot create user with the same name as the anonymous user");
		DBUser ret = new DBUser(this, name, -1);
		putUser(ret);
		return ret;
	}

	public void putUser(User user) throws PrismsException
	{
		if(!(user instanceof DBUser))
			throw new PrismsException("User " + user + " not created by this user source");
		if(user == theAnonymousUser)
			throw new PrismsException("Cannot modify the anonymous user");
		DBUser newUser = (DBUser) user;
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			checkConnection();
			stmt = theConn.createStatement();

			DBUser dbUser = newUser.getID() < 0 ? null : dbGetUser(newUser.getID(), stmt);
			if(dbUser == null)
			{
				dbInsertUser(newUser, stmt);
				theUserCache = ArrayUtils.add(theUserCache, newUser);
			}
			else
				dbUpdateUser(dbUser, newUser, stmt);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get create statement", e);
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

	public void deleteUser(User user) throws PrismsException
	{
		if(!(user instanceof DBUser))
			throw new PrismsException("User " + user + " not created by this user source");
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			checkConnection();
			Statement stmt = null;
			try
			{
				stmt = theConn.createStatement();
				dbRemoveUser((DBUser) user, stmt);
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
			theUserCache = ArrayUtils.remove(theUserCache, (DBUser) user);
		} finally
		{
			lock.unlock();
		}
	}

	public UserGroup createGroup(PrismsApplication app, String name) throws PrismsException
	{
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application " + app + " was not created by this user source");
		if(name == null)
			throw new PrismsException("Cannot create group with no name");
		if(getGroup(app, name) != null)
			throw new PrismsException("Group " + name + " already exists for application " + app);
		DBGroup group = new DBGroup(this, name, app, -1);
		putGroup(group);
		return group;
	}

	public void putGroup(UserGroup group) throws PrismsException
	{
		if(!(group instanceof DBGroup))
			throw new PrismsException("Group " + group + " was not created by this user source");
		DBGroup setGroup = (DBGroup) group;
		Lock lock = theLock.writeLock();
		lock.lock();
		Statement stmt = null;
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			DBGroup dbGroup = dbGetGroup(setGroup.getID(), (DBApplication) setGroup.getApp(), stmt);
			if(dbGroup == null)
			{
				dbInsertGroup(setGroup, stmt);
				theGroupCache = ArrayUtils.add(theGroupCache, setGroup);
			}
			else
				dbUpdateGroup(dbGroup, setGroup, stmt);
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
	}

	public void deleteGroup(UserGroup group) throws PrismsException
	{
		if(!(group instanceof DBGroup))
			throw new PrismsException("Group " + group + " was not created by this user source");
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			Statement stmt = null;
			try
			{
				checkConnection();
				stmt = theConn.createStatement();
				dbRemoveGroup((DBGroup) group, stmt);
				for(DBUser user : theUserCache)
					user.removeFrom(group);
				theGroupCache = ArrayUtils.remove(theGroupCache, (DBGroup) group);
				for(DBUser user : theUserCache)
					user.removeFrom(group);
				for(DBApplication app : theAppCache)
					app.removeAdminGroup(group);
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
		} finally
		{
			lock.unlock();
		}
	}

	public DBPermission createPermission(PrismsApplication app, String name, String descrip)
		throws PrismsException
	{
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application " + app + " was not created by this user source");
		if(name == null)
			throw new PrismsException("Cannot create permission with no name");
		for(DBPermission perm : thePermissionCache)
			if(perm.getApp() == app && perm.getName().equals(name))
				throw new PrismsException("Permission " + name + " for application " + app
					+ " already exists");
		DBPermission perm = new DBPermission(name, descrip, app, -1);
		putPermission(perm);
		return perm;
	}

	public void putPermission(Permission permission) throws PrismsException
	{
		if(!(permission instanceof DBPermission))
			throw new PrismsException("Permission " + permission
				+ " was not created by this user source");
		DBPermission setPerm = (DBPermission) permission;
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			DBPermission dbPerm = dbGetPermission(setPerm.getID(),
				(DBApplication) setPerm.getApp(), stmt);
			if(dbPerm == null)
			{
				dbInsertPermission(setPerm, stmt);
				thePermissionCache = ArrayUtils.add(thePermissionCache, setPerm);
			}
			else
				dbUpdatePermission(dbPerm, setPerm, stmt);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not create statement", e);
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

	public void deletePermission(Permission permission) throws PrismsException
	{
		if(!(permission instanceof DBPermission))
			throw new PrismsException("Permission " + permission
				+ " was not created by this user source");
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			dbRemovePermission((DBPermission) permission, stmt);
			thePermissionCache = ArrayUtils.remove(thePermissionCache, (DBPermission) permission);
			for(DBGroup group : theGroupCache)
				if(group.getApp() == permission.getApp())
					group.getPermissions().removePermission(permission.getName());
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete permission " + permission.getName(), e);
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

	public DBApplication createApplication(String name) throws PrismsException
	{
		if(name == null)
			throw new PrismsException("Cannot create an application without a name");
		if(getApp(name) != null)
			throw new PrismsException("Application " + name + " already exists");
		DBApplication app = new DBApplication();
		app.setName(name);
		putApplication(app);
		return app;
	}

	public void putApplication(PrismsApplication app) throws PrismsException
	{
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application " + app.getName()
				+ " was not created by this user source");
		DBApplication dba = (DBApplication) app;
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			DBApplication dbApp = dbGetApplication(dba.getID(), stmt);
			if(dbApp == null)
			{
				dbInsertApplication(dba, stmt);
				theAppCache = ArrayUtils.add(theAppCache, dba);
			}
			else
				dbUpdateApplication(dbApp, dba, stmt);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not update application " + app.getName(), e);
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

	public void deleteApplication(PrismsApplication app) throws PrismsException
	{
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application " + app + " was not created by this user source");
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			dbRemoveApplication((DBApplication) app, stmt);
			theAppCache = ArrayUtils.remove(theAppCache, (DBApplication) app);
			for(int c = 0; c < theClientCache.length; c++)
				if(theClientCache[c].getApp() == app)
				{
					theClientCache = ArrayUtils.remove(theClientCache, c);
					c--;
				}
			for(int p = 0; p < thePermissionCache.length; p++)
				if(thePermissionCache[p].getApp() == app)
				{
					thePermissionCache = ArrayUtils.remove(thePermissionCache, p);
					p--;
				}
			for(int g = 0; g < theGroupCache.length; g++)
				if(theGroupCache[g].getApp() == app)
				{
					theGroupCache = ArrayUtils.remove(theGroupCache, g);
					g--;
				}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete application " + app.getName(), e);
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

	public DBClientConfig createClient(PrismsApplication app, String name) throws PrismsException
	{
		if(!(app instanceof DBApplication))
			throw new PrismsException("Application " + app.getName()
				+ " was not created by this user source");
		if(getClient(app, name) != null)
			throw new PrismsException("Client configuration " + name + " for application " + app
				+ " already exists");
		if(name == null)
			throw new PrismsException("Cannot create a client configuration with no name");
		DBClientConfig client = new DBClientConfig(-1, app, name);
		putClient(client);
		return client;
	}

	public void putClient(ClientConfig client) throws PrismsException
	{
		if(!(client instanceof DBClientConfig))
			throw new PrismsException("Client config " + client.getName()
				+ " was not created by this user source");
		DBClientConfig dbcc = (DBClientConfig) client;
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			DBClientConfig dbClient = dbGetClient(dbcc.getID(), (DBApplication) dbcc.getApp(), stmt);
			if(dbClient == null)
			{
				dbInsertClient(dbcc, stmt);
				theClientCache = ArrayUtils.add(theClientCache, dbcc);
			}
			else
				dbUpdateClient(dbClient, dbcc, stmt);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not update client configuration " + client.getName(),
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
	}

	public void deleteClient(ClientConfig client) throws PrismsException
	{
		if(!(client instanceof DBClientConfig))
			throw new PrismsException("Client config " + client
				+ " was not created by this user source");
		Statement stmt = null;
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			dbRemoveClient((DBClientConfig) client, stmt);
			theClientCache = ArrayUtils.remove(theClientCache, (DBClientConfig) client);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete client configuration " + client.getName(),
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
	}

	/**
	 * @see prisms.arch.ds.UserSource#disconnect()
	 */
	public void disconnect()
	{
		for(PrismsApplication app : theAppCache)
		{
			try
			{
				app.destroy();
			} catch(Throwable e)
			{
				log.error("Exception destroying application " + app.getName(), e);
			}
		}
		theAnonymousUser = null;
		theUserCache = null;
		theGroupCache = null;
		thePermissionCache = null;
		theAppCache = null;
		theClientCache = null;
		theHashing = null;
		if(theConn == null)
			return;
		thePersisterFactory.disconnect(theConn, theConfigEl);
		theConn = null;
	}

	// Implementation methods here

	DBUser getUser(int id, Statement stmt) throws PrismsException
	{
		for(DBUser user : theUserCache)
			if(user.getID() == id)
				return user;
		return dbGetUser(id, stmt);
	}

	DBUser dbGetUser(int id, Statement stmt) throws PrismsException
	{
		if(id < 0)
			return null;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_user WHERE id=" + id;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			DBUser ret = new DBUser(this, rs.getString("userName"), id);
			ret.setDeleted(boolFromSql(rs.getString("deleted")));
			rs.close();
			rs = null;

			sql = "SELECT id, groupApp FROM " + DBOWNER + "prisms_user_group_assoc INNER JOIN "
				+ DBOWNER + "prisms_user_group ON assocGroup=id WHERE assocUser=" + id;
			rs = stmt.executeQuery(sql);
			ArrayList<Integer> groupIDs = new ArrayList<Integer>();
			ArrayList<Integer> appIDs = new ArrayList<Integer>();
			while(rs.next())
			{
				groupIDs.add(new Integer(rs.getInt(1)));
				appIDs.add(new Integer(rs.getInt(2)));
			}
			rs.close();
			rs = null;
			java.util.HashMap<Integer, DBApplication> apps = new java.util.HashMap<Integer, DBApplication>();
			for(int g = 0; g < groupIDs.size(); g++)
			{
				DBApplication app = apps.get(appIDs.get(g));
				if(app == null)
				{
					app = getApplication(appIDs.get(g).intValue(), stmt);
					if(app == null)
					{
						log.error("Could not get application for ID " + appIDs.get(g).intValue());
						continue;
					}
					apps.put(appIDs.get(g), app);
				}

				DBGroup group = getGroup(groupIDs.get(g).intValue(), app, stmt);
				if(group == null)
				{
					log.error("Could not get group for ID " + appIDs.get(g).intValue());
					continue;
				}
				ret.addTo(group);
			}
			return ret;
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get user for ID " + id + ": SQL=" + sql, e);
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

	void dbInsertUser(DBUser user, Statement stmt) throws PrismsException
	{
		user.setID(getNextID("prisms_user", stmt));
		String sql = "INSERT INTO " + DBOWNER + "prisms_user (id, userName, deleted) VALUES ("
			+ user.getID() + ", " + toSQL(user.getName()) + ", " + boolToSql(false) + ")";
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not insert user: SQL=" + sql, e);
		}
	}

	void dbUpdateUser(final DBUser dbUser, DBUser setUser, final Statement stmt)
		throws PrismsException
	{
		String update = "";
		if(!dbUser.getName().equals(setUser.getName()))
		{
			update += "userName=" + toSQL(setUser.getName()) + ", ";
			dbUser.setName(setUser.getName());
		}
		if(dbUser.isDeleted() != setUser.isDeleted())
		{
			update += "deleted=" + boolToSql(setUser.isDeleted()) + ", ";
			dbUser.setDeleted(setUser.isDeleted());
			if(setUser.isDeleted())
				theUserCache = ArrayUtils.remove(theUserCache, setUser);
			else
				theUserCache = ArrayUtils.add(theUserCache, setUser);
		}

		if(update.length() > 0)
		{
			update = update.substring(0, update.length() - 2);
			String sql = "UPDATE " + DBOWNER + "prisms_user SET " + update + " WHERE id="
				+ dbUser.getID();
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsException("Could not update user: SQL=" + sql, e);
			}
		}

		ArrayUtils.adjust(dbUser.getGroups(), setUser.getGroups(),
			new ArrayUtils.DifferenceListener<UserGroup, UserGroup>()
			{
				public boolean identity(UserGroup o1, UserGroup o2)
				{
					return equal(o1, o2);
				}

				public UserGroup added(UserGroup o, int idx, int retIdx)
				{
					if(!(o instanceof DBGroup))
					{
						log.error("Can't add group " + o + ": not correct implementation");
						return null;
					}
					String sql = "INSERT INTO " + DBOWNER
						+ "prisms_user_group_assoc (assocUser, assocGroup) VALUES ("
						+ dbUser.getID() + ", " + ((DBGroup) o).getID() + ")";
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not insert group membership: SQL=" + sql, e);
					}
					return o;
				}

				public UserGroup removed(UserGroup o, int idx, int incMod, int retIdx)
				{
					if(!(o instanceof DBGroup))
					{
						log.error("Can't remove group " + o + ": not correct implementation");
						return null;
					}
					String sql = "DELETE FROM " + DBOWNER
						+ "prisms_user_group_assoc WHERE assocUser=" + dbUser.getID()
						+ " AND assocGroup=" + ((DBGroup) o).getID();
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not remove group membership: SQL=" + sql, e);
					}
					return null;
				}

				public UserGroup set(UserGroup o1, int idx1, int incMod, UserGroup o2, int idx2,
					int retIdx)
				{
					return o1;
				}
			});
	}

	void dbRemoveUser(DBUser user, Statement stmt) throws PrismsException
	{
		String sql = "DELETE FROM " + DBOWNER + "prisms_user WHERE id=" + user.getID();
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete user: SQL=" + sql, e);
		}
	}

	DBGroup getGroup(int id, DBApplication app, Statement stmt) throws PrismsException
	{
		for(DBGroup group : theGroupCache)
			if(group.getID() == id)
				return group;
		return dbGetGroup(id, app, stmt);
	}

	DBGroup dbGetGroup(int id, DBApplication app, Statement stmt) throws PrismsException
	{
		if(id < 0)
			return null;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_user_group WHERE id=" + id;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			DBGroup ret = new DBGroup(this, rs.getString("groupName"), app, id);
			ret.setDescription(rs.getString("groupDescrip"));
			ret.setDeleted(boolFromSql(rs.getString("deleted")));
			rs.close();
			rs = null;

			sql = "SELECT id FROM " + DBOWNER + "prisms_permission INNER JOIN " + DBOWNER
				+ "prisms_group_permissions ON assocPermission=id WHERE assocGroup=" + id;
			rs = stmt.executeQuery(sql);
			ArrayList<Integer> permIDs = new ArrayList<Integer>();
			while(rs.next())
				permIDs.add(new Integer(rs.getInt(1)));
			for(Integer permID : permIDs)
			{
				DBPermission perm = dbGetPermission(permID.intValue(), app, stmt);
				if(perm == null)
				{
					log.error("Could not get permission with ID " + permID.intValue());
					continue;
				}
				ret.getPermissions().addPermission(perm);
			}
			return ret;
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get group for ID " + id + ": SQL=" + sql, e);
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

	void dbInsertGroup(DBGroup group, Statement stmt) throws PrismsException
	{
		group.setID(getNextID("prisms_user_group", stmt));
		String sql = "INSERT INTO " + DBOWNER
			+ "prisms_user_group (id, groupApp, groupName, groupDescrip, deleted) VALUES ("
			+ group.getID() + ", " + ((DBApplication) group.getApp()).getID() + ", "
			+ toSQL(group.getName()) + ", " + toSQL(group.getDescription()) + ", "
			+ boolToSql(false) + ")";
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not insert group: SQL=" + sql, e);
		}
	}

	void dbUpdateGroup(final DBGroup dbGroup, DBGroup setGroup, final Statement stmt)
		throws PrismsException
	{
		String update = "";
		if(!dbGroup.getName().equals(setGroup.getName()))
			update += "groupName=" + toSQL(setGroup.getName()) + ", ";
		if(!equal(dbGroup.getDescription(), setGroup.getDescription()))
			update += "groupDescrip=" + toSQL(setGroup.getName()) + ", ";
		if(dbGroup.isDeleted() != setGroup.isDeleted())
		{
			update += "deleted=" + boolToSql(setGroup.isDeleted()) + ", ";
			if(setGroup.isDeleted())
				theGroupCache = ArrayUtils.remove(theGroupCache, setGroup);
			else
				theGroupCache = ArrayUtils.add(theGroupCache, setGroup);
		}

		if(update.length() > 0)
		{
			update = update.substring(0, update.length() - 2);
			String sql = "UPDATE " + DBOWNER + "prisms_user_group SET " + update + " WHERE id="
				+ dbGroup.getID();
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsException("Could not update user: SQL=" + sql, e);
			}
		}

		ArrayUtils.adjust(dbGroup.getPermissions().getAllPermissions(), setGroup.getPermissions()
			.getAllPermissions(), new ArrayUtils.DifferenceListener<Permission, Permission>()
		{
			public boolean identity(Permission o1, Permission o2)
			{
				return equal(o1, o2);
			}

			public Permission added(Permission o, int idx, int retIdx)
			{
				if(!(o instanceof DBPermission))
				{
					log.error("Can't add permission " + o + ": not correct implementation");
					return null;
				}
				String sql = "INSERT INTO " + DBOWNER
					+ "prisms_group_permissions (assocGroup, assocPermission) VALUES ("
					+ dbGroup.getID() + ", " + ((DBPermission) o).getID() + ")";
				try
				{
					stmt.execute(sql);
				} catch(SQLException e)
				{
					log.error("Could not insert group permission: SQL=" + sql, e);
				}
				return o;
			}

			public Permission removed(Permission o, int idx, int incMod, int retIdx)
			{
				if(!(o instanceof DBPermission))
				{
					log.error("Can't remove permission " + o + ": not correct implementation");
					return null;
				}
				String sql = "DELETE FROM " + DBOWNER
					+ "prisms_group_permissions WHERE assocGroup=" + dbGroup.getID()
					+ " AND assocPermission=" + ((DBPermission) o).getID();
				try
				{
					stmt.execute(sql);
				} catch(SQLException e)
				{
					log.error("Could not remove group permission: SQL=" + sql, e);
				}
				return null;
			}

			public Permission set(Permission o1, int idx1, int incMod, Permission o2, int idx2,
				int retIdx)
			{
				return o1;
			}
		});
	}

	void dbRemoveGroup(DBGroup group, Statement stmt) throws PrismsException
	{
		String sql = "DELETE FROM " + DBOWNER + "prisms_user_group WHERE id=" + group.getID();
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete group: SQL=" + sql, e);
		}
	}

	DBPermission getPermission(int id, DBApplication app, Statement stmt) throws PrismsException
	{
		for(DBPermission perm : thePermissionCache)
			if(perm.getID() == id)
				return perm;
		return dbGetPermission(id, app, stmt);
	}

	DBPermission dbGetPermission(int id, DBApplication app, Statement stmt) throws PrismsException
	{
		if(id < 0)
			return null;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_permission WHERE id=" + id;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			DBPermission ret = new DBPermission(rs.getString("pName"), rs.getString("pDescrip"),
				app, id);
			ret.setDeleted(boolFromSql(rs.getString("deleted")));
			rs.close();
			rs = null;
			return ret;
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get permission for ID " + id + ": SQL=" + sql, e);
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

	void dbInsertPermission(DBPermission perm, Statement stmt) throws PrismsException
	{
		perm.setID(getNextID("prisms_permission", stmt));
		String sql = "INSERT INTO " + DBOWNER + "prisms_permission (id, pApp, pName, pDescrip)"
			+ " VALUES (" + perm.getID() + ", " + ((DBApplication) perm.getApp()).getID() + ", "
			+ toSQL(perm.getName()) + ", " + toSQL(perm.getDescrip()) + ")";
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not insert permission " + perm + ": SQL=" + sql, e);
		}
	}

	void dbUpdatePermission(DBPermission dbPerm, DBPermission setPerm, Statement stmt)
		throws PrismsException
	{
		String sql = "";
		if(!equal(dbPerm.getDescrip(), setPerm.getDescrip()))
			sql += "pDescrip=" + toSQL(setPerm.getDescrip()) + ", ";
		if(dbPerm.isDeleted() != setPerm.isDeleted())
		{
			sql += " deleted=" + boolToSql(setPerm.isDeleted());
			thePermissionCache = ArrayUtils.add(thePermissionCache, setPerm);
		}
		if(sql.length() > 0)
		{
			sql = "UPDATE " + DBOWNER + "prisms_permission SET "
				+ sql.substring(0, sql.length() - 2) + " WHERE id=" + dbPerm.getID();
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsException("Could not update permission " + dbPerm + ": SQL=" + sql,
					e);
			}
		}
	}

	void dbRemovePermission(DBPermission perm, Statement stmt) throws PrismsException
	{
		String sql = "DELETE FROM " + DBOWNER + "prisms_permission WHERE id=" + perm.getID();
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete permission: SQL=" + sql, e);
		}
	}

	DBApplication getApplication(int id, Statement stmt) throws PrismsException
	{
		for(DBApplication app : theAppCache)
			if(app.getID() == id)
				return app;
		return dbGetApplication(id, stmt);
	}

	DBApplication dbGetApplication(int id, Statement stmt) throws PrismsException
	{
		if(id < 0)
			return null;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_application WHERE id=" + id;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			DBApplication ret = new DBApplication();
			ret.setDataSource(this);
			ret.setID(id);
			ret.setName(rs.getString("appName"));
			ret.setDescription(rs.getString("appDescrip"));
			ret.setConfigClass(rs.getString("configClass"));
			ret.setConfigXML(rs.getString("configXML"));
			ret.setUserRestrictive(boolFromSql(rs.getString("userRestrictive")));
			ret.setDeleted(boolFromSql(rs.getString("deleted")));
			rs.close();
			rs = null;

			sql = "SELECT id, groupApp FROM " + DBOWNER + "prisms_app_admin_group INNER JOIN "
				+ DBOWNER + "prisms_user_group ON adminGroup=id WHERE adminApp=" + id;
			rs = stmt.executeQuery(sql);
			ArrayList<Integer> groupIDs = new ArrayList<Integer>();
			ArrayList<Integer> appIDs = new ArrayList<Integer>();
			while(rs.next())
			{
				groupIDs.add(new Integer(rs.getInt(1)));
				appIDs.add(new Integer(rs.getInt(2)));
			}
			rs.close();
			rs = null;
			java.util.HashMap<Integer, DBApplication> apps = new java.util.HashMap<Integer, DBApplication>();
			for(int g = 0; g < groupIDs.size(); g++)
			{
				DBApplication app = apps.get(appIDs.get(g));
				if(app == null)
				{
					app = getApplication(appIDs.get(g).intValue(), stmt);
					if(app == null)
					{
						log.error("Could not get application for ID " + appIDs.get(g).intValue());
						continue;
					}
					apps.put(appIDs.get(g), app);
				}

				DBGroup group = getGroup(groupIDs.get(g).intValue(), app, stmt);
				if(group == null)
				{
					log.error("Could not get group for ID " + appIDs.get(g).intValue());
					continue;
				}
				ret.addAdminGroup(group);
			}
			return ret;
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get application for ID " + id + ": SQL=" + sql, e);
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

	void dbInsertApplication(DBApplication app, Statement stmt) throws PrismsException
	{
		app.setID(getNextID("prisms_application", stmt));
		String sql = "INSERT INTO " + DBOWNER + "prisms_application (id, appName, appDescrip,"
			+ " configClass, configXML, userRestrictive, deleted) VALUES (" + app.getID() + ", "
			+ toSQL(app.getName()) + ", " + toSQL(app.getDescription()) + ", ";
		sql += toSQL((app.getConfig() == null || app.getConfig().getClass().equals(AppConfig.class))
			? null : app.getConfig().getClass().getName())
			+ ", ";
		sql += toSQL(app.getConfigXML()) + ", ";
		sql += boolToSql(app.isUserRestrictive()) + ", ";
		sql += boolToSql(app.isDeleted()) + ")";
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not insert application " + app + ": SQL=" + sql, e);
		}
	}

	void dbUpdateApplication(final DBApplication dbApp, DBApplication setApp, final Statement stmt)
		throws PrismsException
	{
		String update = "";
		if(!dbApp.getName().equals(setApp.getName()))
			update += "appName=" + toSQL(setApp.getName()) + ", ";
		if(!equal(dbApp.getDescription(), setApp.getDescription()))
			update += "appDescrip=" + toSQL(setApp.getName()) + ", ";
		if(!equal(dbApp.getConfig(), setApp.getConfig()))
		{
			if(setApp.getConfig() instanceof PlaceholderAppConfig)
				update += "configClass="
					+ toSQL(((PlaceholderAppConfig) setApp.getConfig()).getAppConfigClassName())
					+ ", ";
			else if(setApp.getConfig().getClass().equals(AppConfig.class))
				update += "configClass=" + toSQL(null) + ", ";
			else
				update += "configClass=" + toSQL(setApp.getConfig().getClass().getName()) + ", ";
		}
		if(!equal(dbApp.getConfigXML(), setApp.getConfigXML()))
			update += "configXML=" + toSQL(setApp.getConfigXML()) + ", ";
		if(dbApp.isUserRestrictive() != setApp.isUserRestrictive())
			update += "userRestrictive=" + boolToSql(setApp.isUserRestrictive()) + ", ";
		if(dbApp.isDeleted() != setApp.isDeleted())
		{
			update += "deleted=" + boolToSql(setApp.isDeleted()) + ", ";
			if(setApp.isDeleted())
				theAppCache = ArrayUtils.remove(theAppCache, setApp);
			else
				theAppCache = ArrayUtils.add(theAppCache, setApp);
		}

		if(update.length() > 0)
		{
			update = update.substring(0, update.length() - 2);
			String sql = "UPDATE " + DBOWNER + "prisms_application SET " + update + " WHERE id="
				+ dbApp.getID();
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsException("Could not update application: SQL=" + sql, e);
			}
		}

		ArrayUtils.adjust(dbApp.getAdminGroups(), setApp.getAdminGroups(),
			new ArrayUtils.DifferenceListener<UserGroup, UserGroup>()
			{
				public boolean identity(UserGroup o1, UserGroup o2)
				{
					return equal(o1, o2);
				}

				public UserGroup added(UserGroup o, int idx, int retIdx)
				{
					if(!(o instanceof DBGroup))
					{
						log.error("Can't add admin group " + o + ": not correct implementation");
						return null;
					}
					String sql = "INSERT INTO " + DBOWNER
						+ "prisms_app_admin_group (adminApp, adminGroup) VALUES (" + dbApp.getID()
						+ ", " + ((DBGroup) o).getID() + ")";
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not insert admin group: SQL=" + sql, e);
					}
					return o;
				}

				public UserGroup removed(UserGroup o, int idx, int incMod, int retIdx)
				{
					if(!(o instanceof DBGroup))
					{
						log.error("Can't remove admin group " + o + ": not correct implementation");
						return null;
					}
					String sql = "DELETE FROM " + DBOWNER
						+ "prisms_app_admin_group WHERE adminApp=" + dbApp.getID()
						+ " AND adminGroup=" + ((DBGroup) o).getID();
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not remove admin group: SQL=" + sql, e);
					}
					return null;
				}

				public UserGroup set(UserGroup o1, int idx1, int incMod, UserGroup o2, int idx2,
					int retIdx)
				{
					return o1;
				}
			});
	}

	void dbRemoveApplication(DBApplication app, Statement stmt) throws PrismsException
	{
		String sql = "DELETE FROM " + DBOWNER + "prisms_application WHERE id=" + app.getID();
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete application: SQL=" + sql, e);
		}
	}

	DBClientConfig dbGetClient(int id, DBApplication app, Statement stmt) throws PrismsException
	{
		if(id < 0)
			return null;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_client_config WHERE id=" + id;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			DBClientConfig ret = new DBClientConfig(id, app, rs.getString("configName"));
			ret.setDescription(rs.getString("configDescrip"));
			ret.setSerializerClass(rs.getString("configSerializer"));
			ret.setConfigXML(rs.getString("configXML"));
			ret.setValidatorClass(rs.getString("validatorClass"));
			ret.setSessionTimeout(rs.getLong("sessionTimeout"));
			ret.setAllowsAnonymous(boolFromSql(rs.getString("allowAnonymous")));
			ret.setService(boolFromSql(rs.getString("isService")));
			ret.setDeleted(boolFromSql(rs.getString("deleted")));
			rs.close();
			rs = null;
			return ret;
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get application for ID " + id + ": SQL=" + sql, e);
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

	void dbInsertClient(DBClientConfig client, Statement stmt) throws PrismsException
	{
		client.setID(getNextID("prisms_client_config", stmt));
		String sql = "INSERT INTO " + DBOWNER + "prisms_client_config (id, configApp, configName,"
			+ " configDescrip, configSerializer, configXML, validatorClass, isService,"
			+ " sessionTimeout, allowAnonymous, deleted) VALUES (" + client.getID() + ", "
			+ ((DBApplication) client.getApp()).getID() + ", " + toSQL(client.getName()) + ", "
			+ toSQL(client.getDescription());
		if(client.getSerializer() == null)
			sql += ", " + toSQL(null);
		else if(client.getSerializer() instanceof PlaceholderSerializer)
			sql += ", "
				+ toSQL(((PlaceholderSerializer) client.getSerializer()).getSerializerClassName());
		else
			sql += ", " + toSQL(client.getSerializer().getClass().getName());
		sql += ", " + toSQL(client.getConfigXML());
		if(client.getValidator() == null)
			sql += ", " + toSQL(null);
		else if(client.getValidator() instanceof PlaceholderValidator)
			sql += ", "
				+ toSQL(((PlaceholderValidator) client.getValidator()).getValidatorClassName());
		else
			sql += ", " + toSQL(client.getValidator().getClass().getName());
		sql += ", " + boolToSql(client.isService());
		sql += ", " + client.getSessionTimeout();
		sql += ", " + boolToSql(client.allowsAnonymous());
		sql += ", " + boolToSql(false) + ")";
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not insert client: SQL=" + sql, e);
		}
	}

	void dbUpdateClient(DBClientConfig dbClient, DBClientConfig setClient, Statement stmt)
		throws PrismsException
	{
		String msg = "Updating client " + dbClient.getName() + ": ";
		String update = "";
		if(!dbClient.getName().equals(setClient.getName()))
		{
			msg += "name changed to " + setClient.getName() + ", ";
			update += "appName=" + toSQL(setClient.getName()) + ", ";
		}
		if(!equal(dbClient.getDescription(), setClient.getDescription()))
		{
			msg += "description changed from " + dbClient.getDescription() + " to "
				+ setClient.getDescription() + ", ";
			update += "appDescrip=" + toSQL(setClient.getName()) + ", ";
		}
		if(!equal(dbClient.getSerializer(), setClient.getSerializer()))
		{
			msg += "serializer class changed to ";
			if(setClient.getSerializer() instanceof PlaceholderSerializer)
			{
				msg += ((PlaceholderSerializer) setClient.getSerializer()).getSerializerClassName();
				update += "configSerializer="
					+ toSQL(((PlaceholderSerializer) setClient.getSerializer())
						.getSerializerClassName()) + ", ";
			}
			else if(setClient.getSerializer().getClass().equals(JsonSerializer.class))
			{
				msg += "default";
				update += "configSerializer=" + toSQL(null) + ", ";
			}
			else
			{
				msg += setClient.getSerializer().getClass().getName();
				update += "configSerializer="
					+ toSQL(setClient.getSerializer().getClass().getName()) + ", ";
			}
			msg += ", ";
		}
		if(!equal(dbClient.getValidator(), setClient.getValidator()))
		{
			msg += "validator class changed to ";
			if(setClient.getValidator() instanceof PlaceholderValidator)
			{
				msg += ((PlaceholderValidator) setClient.getValidator()).getValidatorClassName();
				update += "validatorClass="
					+ toSQL(((PlaceholderValidator) setClient.getValidator())
						.getValidatorClassName()) + ", ";
			}
			else
			{
				msg += "none";
				update += "validatorClass=" + toSQL(setClient.getValidator().getClass().getName())
					+ ", ";
			}
			msg += ", ";
		}
		if(!equal(dbClient.getConfigXML(), setClient.getConfigXML()))
		{
			msg += "configXML changed from " + dbClient.getConfigXML() + " to "
				+ setClient.getConfigXML() + ", ";
			update += "configXML=" + toSQL(setClient.getConfigXML()) + ", ";
		}
		if(dbClient.isService() != setClient.isService())
		{
			msg += "set to "
				+ (setClient.isService() ? "method-invocation service" : "event-driven service")
				+ ", ";
			update += "isService=" + boolToSql(setClient.isService()) + ", ";
		}
		if(dbClient.getSessionTimeout() != setClient.getSessionTimeout())
		{
			msg += "session timeout set to "
				+ (setClient.getSessionTimeout() < 0 ? "none" : prisms.util.PrismsUtils
					.printTimeLength(setClient.getSessionTimeout())) + ", ";
			update += "sessionTimeout=" + setClient.getSessionTimeout();
		}
		if(dbClient.allowsAnonymous() != setClient.allowsAnonymous())
		{
			msg += "anonymous access " + (setClient.allowsAnonymous() ? "allowed" : "forbidden")
				+ ", ";
			update += "allowAnonymous=" + boolToSql(setClient.allowsAnonymous()) + ", ";
		}
		if(dbClient.isDeleted() != setClient.isDeleted())
		{
			msg += "restored from deletion, ";
			update += "deleted=" + boolToSql(setClient.isDeleted()) + ", ";
			if(setClient.isDeleted())
				theClientCache = ArrayUtils.remove(theClientCache, setClient);
			else
				theClientCache = ArrayUtils.add(theClientCache, setClient);
		}

		if(update.length() > 0)
		{
			log.info(msg.substring(0, msg.length() - 2));
			update = update.substring(0, update.length() - 2);
			String sql = "UPDATE " + DBOWNER + "prisms_client_config SET " + update + " WHERE id="
				+ dbClient.getID();
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsException("Could not update client: SQL=" + sql, e);
			}
		}
	}

	void dbRemoveClient(DBClientConfig client, Statement stmt) throws PrismsException
	{
		String sql = "DELETE FROM " + DBOWNER + "prisms_client_config WHERE id=" + client.getID();
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete group: SQL=" + sql, e);
		}
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
				stmt = theConn.createStatement();
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
					stmt = theConn.createStatement();
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

	int getNextID(String tableName, Statement stmt) throws PrismsException
	{
		String sql = "SELECT id FROM " + DBOWNER + tableName + " ORDER BY id";
		int id = 0;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				int tempID = rs.getInt(1);
				if(id != tempID)
					break;
				id++;
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get next " + tableName + " id: SQL=" + sql, e);
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

	static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
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
			if(theConn == null || theConn.isClosed())
			{
				theConn = thePersisterFactory.getConnection(theConfigEl, null);
				DBOWNER = thePersisterFactory.getTablePrefix(theConn, theConfigEl
					.element("connection"), null);
			}
		} catch(SQLException e)
		{
			throw new IllegalStateException("Could not renew connection ", e);
		}
	}
}
