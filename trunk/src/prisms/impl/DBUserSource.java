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
import org.dom4j.Element;

import prisms.arch.*;
import prisms.arch.ds.*;
import prisms.util.*;

/** A {@link prisms.arch.ds.ManageableUserSource} that obtains its information from a database */
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

	private PrismsEnv theEnv;

	private final java.util.HashMap<String, PrismsApplication> theApps;

	private ArrayList<UserSetListener> theListeners;

	private IDGenerator theIDs;

	private String theAnonymousUserName;

	private Hashing theHashing;

	private DBUser [] theUserCache;

	private DBUser theAnonymousUser;

	private DBGroup [] theGroupCache;

	/** Creates a DBUserSource */
	public DBUserSource()
	{
		theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
		theHashing = new Hashing();
		theAnonymousUserName = "anonymous";
		theApps = new java.util.HashMap<String, PrismsApplication>();
		theListeners = new ArrayList<UserSetListener>();
	}

	public void configure(Element configEl, PrismsEnv env, PrismsApplication [] apps)
		throws PrismsException
	{
		theConfigEl = configEl;
		theEnv = env;
		for(PrismsApplication app : apps)
			theApps.put(app.getName(), app);
		try
		{
			theConn = env.getPersisterFactory().getConnection(configEl.element("connection"), null);
		} catch(Exception e)
		{
			throw new PrismsException("Could not connect to PRISMS configuration database", e);
		}
		DBOWNER = env.getPersisterFactory().getTablePrefix(theConn, configEl.element("connection"),
			null);
		theIDs = env.getPersisterFactory().getIDGenerator(configEl.element("connection"), null);
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
		}
	}

	public void addListener(UserSetListener listener)
	{
		synchronized(theListeners)
		{
			theListeners.add(listener);
		}
	}

	public void removeListener(UserSetListener listener)
	{
		synchronized(theListeners)
		{
			theListeners.remove(listener);
		}
	}

	public IDGenerator getIDs()
	{
		return theIDs;
	}

	/** @return The database connection that this user source is using */
	public java.sql.Connection getConnection()
	{
		return theConn;
	}

	/** @return The XML element used to configure this user source's database connection */
	public Element getConnectionConfig()
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
				userList.add(new DBUser(this, rs.getString("userName"), rs.getLong("id")));
			rs.close();
			rs = null;
			users = userList.toArray(new DBUser [userList.size()]);

			LongList userIDs = new LongList();
			IntList groupIDs = new IntList();
			ArrayList<String> appNames = new ArrayList<String>();
			sql = "SELECT assocUser, id, groupApp FROM " + DBOWNER
				+ "prisms_user_group_assoc INNER JOIN " + DBOWNER
				+ "prisms_user_group ON assocGroup=id WHERE deleted=" + boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				userIDs.add(rs.getLong("assocUser"));
				groupIDs.add(rs.getInt("id"));
				appNames.add(rs.getString("groupApp"));
			}
			rs.close();
			rs = null;
			for(int g = 0; g < groupIDs.size(); g++)
			{
				for(DBUser u : users)
				{
					if(u.getID() != userIDs.get(g))
						continue;
					PrismsApplication app = theApps.get(appNames.get(g));
					if(app == null)
					{
						log.error("No such application named " + appNames.get(g));
						break;
					}
					DBGroup group = getGroup(groupIDs.get(g), app, stmt);
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
			java.util.ArrayList<DBGroup> groupList = new ArrayList<DBGroup>();
			sql = "SELECT * FROM " + DBOWNER + "prisms_user_group WHERE deleted="
				+ boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				PrismsApplication app = theApps.get(rs.getString("groupApp"));
				DBGroup group = new DBGroup(this, rs.getString("groupName"), app, rs.getInt("id"));
				group.setDescription(rs.getString("groupDescrip"));
				groupList.add(group);
			}
			rs.close();
			rs = null;
			groups = groupList.toArray(new DBGroup [groupList.size()]);

			ArrayList<Integer> groupIDs = new ArrayList<Integer>();
			ArrayList<String> appNames = new ArrayList<String>();
			ArrayList<String> permNames = new ArrayList<String>();
			sql = "SELECT * FROM " + DBOWNER + "prisms_group_permissions";
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				groupIDs.add(new Integer(rs.getInt("assocGroup")));
				appNames.add(rs.getString("pApp"));
				permNames.add(rs.getString("assocPermission"));
			}
			rs.close();
			rs = null;
			for(int p = 0; p < permNames.size(); p++)
			{
				for(DBGroup g : groups)
				{
					if(g.getID() != groupIDs.get(p).intValue())
						continue;
					PrismsApplication app = theApps.get(appNames.get(p));
					if(app == null)
					{
						log.error("No such application named " + appNames.get(p));
						break;
					}
					Permission perm = app.getPermission(permNames.get(p));
					if(perm == null)
					{
						log.error("No such permission named " + permNames.get(p)
							+ " for application " + app.getName());
						break;
					}
					g.getPermissions().addPermission(perm);
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

	public void setPasswordConstraints(prisms.arch.ds.PasswordConstraints constraints)
		throws PrismsException
	{
		checkConnection();
		Statement stmt = null;
		ResultSet rs = null;
		String sql = null;
		try
		{
			stmt = theConn.createStatement();
			sql = "SELECT constraintsLocked FROM " + DBOWNER + "prisms_password_constraints";
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				sql = "UPDATE " + DBOWNER + "prisms_password_constraints SET constraintsLocked="
					+ boolToSql(constraints.isLocked()) + ", minCharLength="
					+ constraints.getMinCharacterLength() + ", minUpperCase="
					+ constraints.getMinUpperCase() + ", minLowerCase="
					+ constraints.getMinLowerCase() + ", minDigits=" + constraints.getMinDigits()
					+ ", minSpecialChars=" + constraints.getMinSpecialChars()
					+ ", maxPasswordDuration=" + constraints.getMaxPasswordDuration()
					+ ", numPreviousUnique=" + constraints.getNumPreviousUnique()
					+ ", minChangeInterval=" + constraints.getMinPasswordChangeInterval();
			}
			else
			{
				sql = "INSERT INTO " + DBOWNER + "prisms_password_constraints (constraintsLocked,"
					+ " minCharLength, minUpperCase, minLowerCase, minDigits, minSpecialChars,"
					+ " maxPasswordDuration, numPreviousUnique, minChangeInterval) VALUES ("
					+ boolToSql(constraints.isLocked()) + ", "
					+ constraints.getMinCharacterLength() + ", " + constraints.getMinUpperCase()
					+ ", " + constraints.getMinLowerCase() + ", " + constraints.getMinDigits()
					+ ", " + constraints.getMinSpecialChars() + ", "
					+ constraints.getMaxPasswordDuration() + ", "
					+ constraints.getNumPreviousUnique() + ", "
					+ constraints.getMinPasswordChangeInterval() + ")";
			}
			rs.close();
			rs = null;
			stmt.executeUpdate(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not set password constraints: SQL=" + sql, e);
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

	public boolean canAccess(User user, PrismsApplication app) throws PrismsException
	{
		if(!(user instanceof DBUser))
			throw new PrismsException("User " + user + " was not created by this user source");
		if(user.equals(theAnonymousUser))
			return true;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_user_app_assoc WHERE assocUser="
			+ ((DBUser) user).getID() + " AND assocApp=" + toSQL(app.getName());
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

	public void assertAccessible(User user, ClientConfig config) throws PrismsException
	{
	}

	public Hashing getHashing()
	{
		Hashing ret = theHashing.clone();
		ret.randomlyFillSecondary(5);
		return ret;
	}

	private PasswordData [] getPasswordData(DBUser user, boolean latest)
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
				+ " pwdTime, pwdExpire) VALUES ("
				+ IDGenerator.getNextIntID(stmt, DBOWNER + "prisms_user_password", "id") + ", "
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
		for(UserSetListener listener : theListeners.toArray(new UserSetListener [theListeners
			.size()]))
			listener.userChanged(user);
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
		String sql = "SELECT * FROM " + DBOWNER + "prisms_user_app_assoc WHERE assocUser="
			+ ((DBUser) user).getID() + " AND assocApp=" + toSQL(app.getName());
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
					+ ((DBUser) user).getID() + ", " + toSQL(app.getName()) + ")";
			else
				sql = "DELETE FROM " + DBOWNER + "prisms_user_app_assoc WHERE assocUser="
					+ ((DBUser) user).getID() + " AND assocApp=" + toSQL(app.getName());
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
		for(UserSetListener listener : theListeners.toArray(new UserSetListener [theListeners
			.size()]))
			listener.userChanged(user);
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
				for(UserSetListener listener : theListeners
					.toArray(new UserSetListener [theListeners.size()]))
					listener.userSetChanged(theUserCache.clone());
			}
			else
			{
				dbUpdateUser(dbUser, newUser, stmt);
				for(UserSetListener listener : theListeners
					.toArray(new UserSetListener [theListeners.size()]))
					listener.userChanged(newUser);
			}
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
			for(UserSetListener listener : theListeners.toArray(new UserSetListener [theListeners
				.size()]))
				listener.userSetChanged(theUserCache.clone());
		} finally
		{
			lock.unlock();
		}
	}

	public UserGroup createGroup(PrismsApplication app, String name) throws PrismsException
	{
		if(name == null)
			throw new PrismsException("Cannot create group with no name");
		if(hasGroup(app, name))
			throw new PrismsException("Group " + name + " already exists for application " + app);
		DBGroup group = new DBGroup(this, name, app, -1);
		putGroup(group);
		return group;
	}

	private boolean hasGroup(PrismsApplication app, String groupName) throws PrismsException
	{
		String sql = "SELECT id FROM " + DBOWNER + "prisms_user_group WHERE groupApp = "
			+ toSQL(app.getName()) + " AND groupName=" + toSQL(groupName);
		Statement stmt = null;
		ResultSet rs = null;
		checkConnection();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			return rs.next();
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
			DBGroup dbGroup = dbGetGroup(setGroup.getID(), setGroup.getApp(), stmt);
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
				{
					if(ArrayUtils.contains(user.getGroups(), group))
					{
						user.removeFrom(group);
						for(UserSetListener listener : theListeners
							.toArray(new UserSetListener [theListeners.size()]))
							listener.userChanged(user);
					}
				}
				theGroupCache = ArrayUtils.remove(theGroupCache, (DBGroup) group);
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

	/** @see prisms.arch.ds.UserSource#disconnect() */
	public void disconnect()
	{
		theAnonymousUser = null;
		theUserCache = null;
		theGroupCache = null;
		theHashing = null;
		if(theConn == null)
			return;
		theEnv.getPersisterFactory().disconnect(theConn, theConfigEl);
		theConn = null;
	}

	// Implementation methods here

	private DBUser dbGetUser(long id, Statement stmt) throws PrismsException
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
			ArrayList<String> appNames = new ArrayList<String>();
			while(rs.next())
			{
				groupIDs.add(new Integer(rs.getInt(1)));
				appNames.add(rs.getString(2));
			}
			rs.close();
			rs = null;
			for(int g = 0; g < groupIDs.size(); g++)
			{
				PrismsApplication app = theApps.get(appNames.get(g));
				if(app == null)
				{
					log.error("No such application named " + appNames.get(g));
					continue;
				}

				DBGroup group = getGroup(groupIDs.get(g).intValue(), app, stmt);
				if(group == null)
				{
					log.error("Could not get group for ID " + groupIDs.get(g));
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

	private void dbInsertUser(DBUser user, Statement stmt) throws PrismsException
	{
		user.setID(theIDs.getNextID(stmt, DBOWNER, "prisms_user", "id", null));
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

	private void dbUpdateUser(final DBUser dbUser, DBUser setUser, final Statement stmt)
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

	private void dbRemoveUser(DBUser user, Statement stmt) throws PrismsException
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

	private DBGroup getGroup(int id, PrismsApplication app, Statement stmt) throws PrismsException
	{
		for(DBGroup group : theGroupCache)
			if(group.getID() == id)
				return group;
		return dbGetGroup(id, app, stmt);
	}

	private DBGroup dbGetGroup(int id, PrismsApplication app, Statement stmt)
		throws PrismsException
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

			sql = "SELECT assocPermission FROM " + DBOWNER
				+ "prisms_group_permissions WHERE assocGroup=" + id + " AND pApp="
				+ toSQL(app.getName());
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				String pName = rs.getString(1);
				Permission perm = app.getPermission(pName);
				if(perm == null)
				{
					log.error("No such permission named " + pName + " in application "
						+ app.getName());
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

	private void dbInsertGroup(DBGroup group, Statement stmt) throws PrismsException
	{
		String sql = null;
		try
		{
			group.setID(IDGenerator.getNextIntID(stmt, "prisms_user_group", "id"));
			sql = "INSERT INTO " + DBOWNER
				+ "prisms_user_group (id, groupApp, groupName, groupDescrip, deleted) VALUES ("
				+ group.getID() + ", " + toSQL(group.getApp().getName()) + ", "
				+ toSQL(group.getName()) + ", " + toSQL(group.getDescription()) + ", "
				+ boolToSql(false) + ")";
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not insert group: SQL=" + sql, e);
		}
	}

	private void dbUpdateGroup(final DBGroup dbGroup, DBGroup setGroup, final Statement stmt)
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
				String sql = "INSERT INTO " + DBOWNER
					+ "prisms_group_permissions (assocGroup, pApp, assocPermission) VALUES ("
					+ dbGroup.getID() + ", " + toSQL(o.getApp().getName()) + ", "
					+ toSQL(o.getName()) + ")";
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
				String sql = "DELETE FROM " + DBOWNER
					+ "prisms_group_permissions WHERE assocGroup=" + dbGroup.getID() + " AND pApp="
					+ toSQL(o.getApp().getName()) + " AND assocPermission=" + toSQL(o.getName());
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

	private void dbRemoveGroup(DBGroup group, Statement stmt) throws PrismsException
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
				theConn = theEnv.getPersisterFactory().getConnection(theConfigEl, null);
				DBOWNER = theEnv.getPersisterFactory().getTablePrefix(theConn,
					theConfigEl.element("connection"), null);
			}
		} catch(SQLException e)
		{
			throw new IllegalStateException("Could not renew connection ", e);
		}
	}
}
