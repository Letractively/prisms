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
import prisms.impl.PrismsChangeTypes.UserChange;
import prisms.records2.RecordsTransaction;
import prisms.util.*;
import prisms.util.DBUtils.TransactionOperation;

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

	DBUtils.Transactor<PrismsException> theTransactor;

	private PrismsEnv theEnv;

	private prisms.records2.DBRecordKeeper theKeeper;

	private final java.util.HashMap<String, PrismsApplication> theApps;

	ArrayList<UserSetListener> theListeners;

	IDGenerator theIDs;

	private String theAnonymousUserName;

	private Hashing theHashing;

	User [] theUserCache;

	private User theAnonymousUser;

	private User theSystemUser;

	DBGroup [] theGroupCache;

	/** Creates a DBUserSource */
	public DBUserSource()
	{
		theHashing = new Hashing();
		theAnonymousUserName = "anonymous";
		theApps = new java.util.HashMap<String, PrismsApplication>();
		theListeners = new ArrayList<UserSetListener>();
	}

	public prisms.records2.DBRecordKeeper getRecordKeeper()
	{
		return theKeeper;
	}

	public void configure(Element configEl, PrismsEnv env, PrismsApplication [] apps)
		throws PrismsException
	{
		Element connEl = configEl.element("connection");
		theTransactor = new DBUtils.Transactor<PrismsException>(log, env.getPersisterFactory(),
			connEl)
		{
			@Override
			public void error(String message) throws PrismsException
			{
				throw new PrismsException(message);
			}

			@Override
			public void error(String message, Throwable cause) throws PrismsException
			{
				throw new PrismsException(message, cause);
			}
		};
		theEnv = env;
		for(PrismsApplication app : apps)
			theApps.put(app.getName(), app);
		theIDs = new DBIDGenerator(theTransactor.getConnection(), theTransactor.getDbOwner());
		theKeeper = new prisms.records2.DBRecordKeeper("PRISMS", connEl,
			theEnv.getPersisterFactory(), theIDs);
		theKeeper.setPersister(new PrismsSyncImpl(this, theApps.values().toArray(
			new PrismsApplication [theApps.size()])));
		String anonUser = configEl.elementTextTrim("anonymous");
		if(anonUser != null)
			theAnonymousUserName = anonUser;
		else
			theAnonymousUserName = "anonymous";
		// Set up hashing
		String sql = null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "SELECT multiple, modulus FROM " + theTransactor.getDbOwner()
				+ "PRISMS_HASHING ORDER BY id";
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
			if(theHashing.getPrimaryMultiples() == null
				|| theHashing.getPrimaryMultiples().length < 10)
			{
				theHashing.randomlyFillPrimary(10);

				sql = null;
				try
				{
					for(int i = 0; i < theHashing.getPrimaryMultiples().length; i++)
					{
						sql = "INSERT INTO " + theTransactor.getDbOwner()
							+ "prisms_hashing (id, multiple, modulus) VALUES (" + i + ", "
							+ theHashing.getPrimaryMultiples()[i] + ", "
							+ theHashing.getPrimaryModulos()[i] + ")";
						stmt.execute(sql);
					}
				} catch(SQLException e)
				{
					throw new PrismsException("Cannot commit hashing values"
						+ "--passwords not persistable: SQL=" + sql, e);
				}
			}
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

	private boolean addingAnonymous;

	private void fillUserCache(Statement stmt) throws PrismsException
	{
		theAnonymousUser = new User(this, theAnonymousUserName, IDGenerator.getMaxID(theIDs
			.getCenterID()) - 1);
		theAnonymousUser.setReadOnly(true);
		boolean killStatement = false;
		if(stmt == null)
		{
			killStatement = true;
			try
			{
				stmt = theTransactor.getConnection().createStatement();
			} catch(SQLException e)
			{
				throw new PrismsException("Could not create statement", e);
			}
		}
		ResultSet rs = null;
		String sql = null;
		Lock lock = theTransactor.getLock().writeLock();
		lock.lock();
		User [] users;
		try
		{
			if(theUserCache != null)
				return; // Already filled before lock received
			if(theGroupCache == null)
				fillGroupCache(stmt);
			ArrayList<User> userList = new ArrayList<User>();
			sql = "SELECT * FROM " + theTransactor.getDbOwner() + "prisms_user WHERE deleted="
				+ boolToSql(false) + " AND id>=" + IDGenerator.getMinID(theIDs.getCenterID())
				+ " AND id<=" + IDGenerator.getMaxID(theIDs.getCenterID()) + " ORDER BY id";
			rs = stmt.executeQuery(sql);
			java.util.HashSet<User> readOnlyUsers = new java.util.HashSet<User>();
			while(rs.next())
			{
				User user = new User(this, rs.getString("userName"), rs.getLong("id"));
				if(boolFromSql(rs.getString("isAdmin")))
					user.setAdmin(true);
				if(boolFromSql(rs.getString("isReadOnly")))
					readOnlyUsers.add(user);
				userList.add(user);
			}
			rs.close();
			rs = null;
			users = userList.toArray(new User [userList.size()]);

			LongList userIDs = new LongList();
			IntList groupIDs = new IntList();
			ArrayList<String> appNames = new ArrayList<String>();
			sql = "SELECT assocUser, id, groupApp FROM " + theTransactor.getDbOwner()
				+ "prisms_user_group_assoc INNER JOIN " + theTransactor.getDbOwner()
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
				for(User u : users)
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
			for(User u : readOnlyUsers)
				u.setReadOnly(true);
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

		long sysID = IDGenerator.getMaxID(theIDs.getCenterID());
		for(int u = 0; u < theUserCache.length; u++)
			if(theUserCache[u].getID() == sysID)
			{
				theSystemUser = theUserCache[u];
				theUserCache = ArrayUtils.remove(theUserCache, u);
				break;
			}

		if(!ArrayUtils.contains(theUserCache, theAnonymousUser))
		{
			addingAnonymous = true;
			try
			{
				putUser(theAnonymousUser, new RecordsTransaction(getSystemUser()));
			} finally
			{
				addingAnonymous = false;
			}
		}
		theUserCache = ArrayUtils.remove(theUserCache, theAnonymousUser);
	}

	private void fillGroupCache(Statement stmt) throws PrismsException
	{
		boolean killStatement = false;
		if(stmt == null)
		{
			killStatement = true;
			try
			{
				stmt = theTransactor.getConnection().createStatement();
			} catch(SQLException e)
			{
				throw new PrismsException("Could not create statement", e);
			}
		}
		ResultSet rs = null;
		String sql = null;
		Lock lock = theTransactor.getLock().writeLock();
		lock.lock();
		DBGroup [] groups;
		try
		{
			if(theGroupCache != null)
				return; // Already filled before lock received
			java.util.ArrayList<DBGroup> groupList = new ArrayList<DBGroup>();
			sql = "SELECT * FROM " + theTransactor.getDbOwner()
				+ "prisms_user_group WHERE deleted=" + boolToSql(false);
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
			sql = "SELECT * FROM " + theTransactor.getDbOwner() + "prisms_group_permissions";
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
		Statement stmt = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			return getPasswordConstraints(stmt);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not create statement", e);
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

	PasswordConstraints getPasswordConstraints(Statement stmt) throws PrismsException
	{
		prisms.arch.ds.PasswordConstraints ret = new prisms.arch.ds.PasswordConstraints();
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
			sql = "SELECT * FROM " + theTransactor.getDbOwner() + "prisms_password_constraints";
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
		Statement stmt = null;
		ResultSet rs = null;
		String sql = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "SELECT constraintsLocked FROM " + theTransactor.getDbOwner()
				+ "prisms_password_constraints";
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				sql = "UPDATE " + theTransactor.getDbOwner()
					+ "prisms_password_constraints SET constraintsLocked="
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
				sql = "INSERT INTO " + theTransactor.getDbOwner()
					+ "prisms_password_constraints (constraintsLocked,"
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

	private boolean addingSystem;

	public User getSystemUser() throws PrismsException
	{
		if(theSystemUser != null)
			return theSystemUser;
		if(theUserCache == null)
			fillUserCache(null);
		if(theSystemUser != null)
			return theSystemUser;
		long sysID = IDGenerator.getMaxID(theIDs.getCenterID());
		theSystemUser = new User(this, "System", sysID);
		theSystemUser.setAdmin(false);
		theSystemUser.setReadOnly(true);
		addingSystem = true;
		try
		{
			putUser(theSystemUser, new RecordsTransaction(theSystemUser));
		} finally
		{
			addingSystem = false;
		}
		theUserCache = ArrayUtils.remove(theUserCache, theSystemUser);
		return theSystemUser;
	}

	public User getUser(String name) throws PrismsException
	{
		if(theUserCache == null)
			fillUserCache(null);
		if(name == null)
			return theAnonymousUser;
		for(User user : theUserCache)
			if(user.getName().equals(name))
				return user;
		return null;
	}

	public User getUser(long id) throws PrismsException
	{
		if(theUserCache == null)
			fillUserCache(null);
		if(getSystemUser().getID() == id)
			return getSystemUser();
		if(theAnonymousUser.getID() == id)
			return theAnonymousUser;
		for(User user : theUserCache)
			if(user.getID() == id)
				return user;
		return dbGetUser(id, null);
	}

	public boolean canAccess(User user, PrismsApplication app) throws PrismsException
	{
		if(user.equals(theAnonymousUser))
			return true;
		String sql = null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			sql = "SELECT * FROM " + theTransactor.getDbOwner()
				+ "prisms_user_app_assoc WHERE assocUser=" + user.getID() + " AND assocApp="
				+ toSQL(app.getName());
			stmt = theTransactor.getConnection().createStatement();
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

	PasswordData [] getPasswordData(User user, boolean latest, Statement stmt)
		throws PrismsException
	{
		ResultSet rs = null;
		Lock lock = theTransactor.getLock().readLock();
		lock.lock();
		String sql = null;
		boolean closeStmt = false;
		java.util.ArrayList<PasswordData> data = new ArrayList<PasswordData>();
		try
		{
			if(stmt == null)
			{
				closeStmt = true;
				stmt = theTransactor.getConnection().createStatement();
			}
			sql = "SELECT * FROM " + theTransactor.getDbOwner()
				+ "prisms_user_password WHERE pwdUser=" + user.getID() + " ORDER BY pwdTime DESC";
			stmt = theTransactor.getConnection().createStatement();
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
			if(stmt != null && closeStmt)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{}
		}
		return data.toArray(new PasswordData [data.size()]);
	}

	public long [] getKey(User user, Hashing hashing) throws PrismsException
	{
		PasswordData [] password = getPasswordData(user, true, null);
		if(password.length == 0)
			return null;
		return hashing.generateKey(password[0].thePasswordHash);
	}

	public void setPassword(final User user, final long [] hash, final boolean isAdmin)
		throws PrismsException
	{
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				prisms.arch.ds.PasswordConstraints constraints = getPasswordConstraints(stmt);
				PasswordData [] password = getPasswordData(user, false, stmt);
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
							throw new PrismsException(msg
								+ "either the current or the previous passwords");
						else
							throw new PrismsException(msg + "any of the previous "
								+ constraints.getNumPreviousUnique() + " passwords");
					}
				}
				long now = System.currentTimeMillis();
				if(!isAdmin && password.length > 0)
				{
					if(now > password[0].thePasswordTime
						&& (now - password[0].thePasswordTime) < constraints
							.getMinPasswordChangeInterval())
						throw new PrismsException("Password cannot be changed more than every "
							+ prisms.util.PrismsUtils.printTimeLength(constraints
								.getMinPasswordChangeInterval())
							+ "\nPassword can be changed at "
							+ prisms.util.PrismsUtils.print(password[0].thePasswordTime
								+ constraints.getMinPasswordChangeInterval()));
				}

				String sql = null;
				try
				{
					sql = "INSERT INTO "
						+ theTransactor.getDbOwner()
						+ "prisms_user_password (id, pwdUser, pwdData,"
						+ " pwdTime, pwdExpire) VALUES ("
						+ IDGenerator.getNextIntID(stmt, theTransactor.getDbOwner()
							+ "prisms_user_password", "id", null) + ", " + user.getID() + ", "
						+ DBUtils.toSQL(join(hash)) + ", " + now + ", ";
					if(constraints.getMaxPasswordDuration() > 0)
						sql += (now + constraints.getMaxPasswordDuration());
					else
						sql += "NULL";
					sql += ")";
					stmt.execute(sql);

					for(int p = password.length - 1; p >= 0
						&& p >= constraints.getNumPreviousUnique(); p--)
					{
						sql = "DELETE FROM " + theTransactor.getDbOwner()
							+ "prisms_user_password WHERE id=" + password[p].id;
						stmt.execute(sql);
					}
				} catch(SQLException e)
				{
					throw new PrismsException("Could not set password data for user " + user
						+ ": SQL=" + sql, e);
				}
				return null;
			}
		}, "Could not set password for user " + user);
	}

	public long getPasswordExpiration(User user) throws PrismsException
	{
		PasswordData [] password = getPasswordData(user, true, null);
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

	public User [] getActiveUsers() throws PrismsException
	{
		if(theUserCache == null)
			fillUserCache(null);
		User [] users = theUserCache.clone();
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

	public void setPasswordExpiration(final User user, final long time) throws PrismsException
	{
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				prisms.arch.ds.PasswordConstraints constraints = getPasswordConstraints();
				if(constraints.getMaxPasswordDuration() > 0
					&& time - System.currentTimeMillis() > constraints.getMaxPasswordDuration())
					throw new PrismsException("Password expiration cannot be set for more than "
						+ prisms.util.PrismsUtils.printTimeLength(constraints
							.getMaxPasswordDuration()) + " from current date");
				PasswordData [] password = getPasswordData(user, true, stmt);
				if(password.length == 0)
					return null;
				String sql;
				String toSet;
				if(time < Long.MAX_VALUE)
					toSet = "" + time;
				else
					toSet = "NULL";
				sql = "UPDATE " + theTransactor.getDbOwner()
					+ "prisms_user_password SET pwdExpire=" + toSet + " WHERE id=" + password[0].id;
				try
				{
					stmt.executeUpdate(sql);
				} catch(SQLException e)
				{
					throw new PrismsException("Could not set password expiration for user " + user
						+ ": SQL=" + sql, e);
				}
				return null;
			}
		}, "Could not set password expiration for user " + user);
	}

	void addModification(RecordsTransaction trans, PrismsSubjectType subjectType,
		prisms.records2.ChangeType changeType, int add, Object majorSubject, Object minorSubject,
		Object previousValue, Object data1, Object data2) throws PrismsException
	{
		if(trans == null || theKeeper == null)
			return;
		if(!trans.shouldPersist())
			return;
		prisms.records2.ChangeRecord record;
		try
		{
			record = theKeeper.persist(trans.getUser(), subjectType, changeType, add, majorSubject,
				minorSubject, previousValue, data1, data2);
		} catch(prisms.records2.PrismsRecordException e)
		{
			throw new PrismsException("Could not persist change record", e);
		}
		if(trans.getRecord() != null)
		{
			try
			{
				theKeeper.associate(record, trans.getRecord(), false);
			} catch(prisms.records2.PrismsRecordException e)
			{
				log.error("Could not associate change record with sync record", e);
			}
		}
	}

	public void setUserAccess(final User user, final PrismsApplication app,
		final boolean accessible, final RecordsTransaction trans) throws PrismsException
	{
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				String sql = "SELECT * FROM " + theTransactor.getDbOwner()
					+ "prisms_user_app_assoc WHERE assocUser=" + (user).getID() + " AND assocApp="
					+ toSQL(app.getName());
				ResultSet rs = null;
				try
				{
					rs = stmt.executeQuery(sql);
					if(rs.next() == accessible)
						return null;
					rs.close();
					rs = null;

					if(accessible)
					{
						addModification(trans, PrismsSubjectType.user, UserChange.appAccess, 1,
							user, app, null, null, null);
						sql = "INSERT INTO " + theTransactor.getDbOwner()
							+ "prisms_user_app_assoc (assocUser, assocApp) VALUES ("
							+ (user).getID() + ", " + toSQL(app.getName()) + ")";
					}
					else
					{
						addModification(trans, PrismsSubjectType.user, UserChange.appAccess, -1,
							user, app, null, null, null);
						sql = "DELETE FROM " + theTransactor.getDbOwner()
							+ "prisms_user_app_assoc WHERE assocUser=" + (user).getID()
							+ " AND assocApp=" + toSQL(app.getName());
					}
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
				}
				for(UserSetListener listener : theListeners
					.toArray(new UserSetListener [theListeners.size()]))
					listener.userChanged(user);
				return null;
			}
		}, "Could not set user " + user + "'s access to " + app);
	}

	public User [] getAllUsers() throws PrismsException
	{
		if(theUserCache == null)
			fillUserCache(null);
		Statement stmt = null;
		ResultSet rs = null;
		String sql = null;
		Lock lock = theTransactor.getLock().writeLock();
		lock.lock();
		User [] users;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			ArrayList<User> userList = new ArrayList<User>();
			sql = "SELECT * FROM " + theTransactor.getDbOwner() + "prisms_user ORDER BY id";
			rs = stmt.executeQuery(sql);
			java.util.HashSet<User> readOnlyUsers = new java.util.HashSet<User>();
			while(rs.next())
			{
				User user = null;
				long id = rs.getLong("id");
				for(User u : theUserCache)
					if(u.getID() == id)
					{
						user = u;
						break;
					}
				if(user != null)
				{
					userList.add(user);
					continue;
				}
				user = new User(this, rs.getString("userName"), id);
				if(boolFromSql(rs.getString("isAdmin")))
					user.setAdmin(true);
				if(boolFromSql(rs.getString("isReadOnly")))
					readOnlyUsers.add(user);
				if(boolFromSql(rs.getString("deleted")))
					user.setDeleted(true);
				userList.add(user);
			}
			rs.close();
			rs = null;
			users = userList.toArray(new User [userList.size()]);

			LongList userIDs = new LongList();
			IntList groupIDs = new IntList();
			ArrayList<String> appNames = new ArrayList<String>();
			sql = "SELECT assocUser, id, groupApp FROM " + theTransactor.getDbOwner()
				+ "prisms_user_group_assoc INNER JOIN " + theTransactor.getDbOwner()
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
				for(User u : users)
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
			for(User u : readOnlyUsers)
				u.setReadOnly(true);
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
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		return users;
	}

	public User createUser(String name, RecordsTransaction trans) throws PrismsException
	{
		if(getUser(name) != null)
			throw new PrismsException("User " + name + " already exists");
		if(name == null)
			throw new PrismsException("Cannot create user with no name");
		if(name.equals(theAnonymousUserName))
			throw new PrismsException("Cannot create user with the same name as the anonymous user");
		User ret = new User(this, name, -1);
		putUser(ret, trans);
		return ret;
	}

	public void putUser(final User user, final RecordsTransaction trans) throws PrismsException
	{
		if(user.equals(theSystemUser) && !addingSystem)
			throw new PrismsException("Cannot modify the system user");
		if(user.equals(theAnonymousUser) && !addingAnonymous)
			throw new PrismsException("Cannot modify the anonymous user");
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				User dbUser = user.getID() < 0 ? null : dbGetUser(user.getID(), stmt);
				if(dbUser == null)
				{
					dbInsertUser(user, stmt);
					addModification(trans, PrismsSubjectType.user, null, 1, user, null, null, null,
						null);
					if(theIDs.belongs(user.getID()))
					{
						theUserCache = ArrayUtils.add(theUserCache, user);
						for(UserSetListener listener : theListeners
							.toArray(new UserSetListener [theListeners.size()]))
							listener.userSetChanged(theUserCache.clone());
					}
				}
				else
				{
					boolean recreate = dbUser.isDeleted() != user.isDeleted();
					dbUpdateUser(dbUser, user, stmt, trans);
					if(theIDs.belongs(user.getID()))
					{
						if(recreate)
						{
							for(UserSetListener listener : theListeners
								.toArray(new UserSetListener [theListeners.size()]))
								listener.userSetChanged(theUserCache);
						}
						else
							for(UserSetListener listener : theListeners
								.toArray(new UserSetListener [theListeners.size()]))
								listener.userChanged(user);
					}
				}
				return null;
			}
		}, "Could not add/modify user " + user);
	}

	public void deleteUser(final User user, final RecordsTransaction trans) throws PrismsException
	{
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				dbRemoveUser(user, stmt, trans);
				int idx = ArrayUtils.indexOf(theUserCache, user);
				if(idx >= 0)
				{
					theUserCache = ArrayUtils.remove(theUserCache, idx);
					for(UserSetListener listener : theListeners
						.toArray(new UserSetListener [theListeners.size()]))
						listener.userSetChanged(theUserCache.clone());
				}
				return null;
			}
		}, "Could not delete user " + user);
	}

	public UserGroup createGroup(final PrismsApplication app, final String name)
		throws PrismsException
	{
		if(name == null)
			throw new PrismsException("Cannot create group with no name");
		return (UserGroup) theTransactor.performTransaction(
			new TransactionOperation<PrismsException>()
			{
				public Object run(Statement stmt) throws PrismsException
				{
					if(hasGroup(app, name, stmt))
						throw new PrismsException("Group " + name
							+ " already exists for application " + app);
					DBGroup group = new DBGroup(DBUserSource.this, name, app, -1);
					putGroup(group, stmt);
					return group;
				}
			}, "Could not create group " + name);
	}

	boolean hasGroup(PrismsApplication app, String groupName, Statement stmt)
		throws PrismsException
	{
		String sql = null;
		ResultSet rs = null;
		try
		{
			sql = "SELECT id FROM " + theTransactor.getDbOwner()
				+ "prisms_user_group WHERE groupApp = " + toSQL(app.getName()) + " AND groupName="
				+ toSQL(groupName);
			rs = stmt.executeQuery(sql);
			return rs.next();
		} catch(SQLException e)
		{
			throw new PrismsException("Error getting group " + groupName + " for app " + app
				+ ": SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{}
		}
	}

	public void putGroup(final UserGroup group) throws PrismsException
	{
		if(!(group instanceof DBGroup))
			throw new PrismsException("Group " + group + " was not created by this user source");
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				DBGroup setGroup = (DBGroup) group;
				putGroup(setGroup, stmt);
				return null;
			}
		}, "Could not add/modify user group " + group);
	}

	void putGroup(DBGroup group, Statement stmt) throws PrismsException
	{
		DBGroup dbGroup = dbGetGroup(group.getID(), group.getApp(), stmt);
		if(dbGroup == null)
		{
			dbInsertGroup(group, stmt);
			theGroupCache = ArrayUtils.add(theGroupCache, group);
		}
		else
			dbUpdateGroup(dbGroup, group, stmt);
	}

	public void deleteGroup(final UserGroup group) throws PrismsException
	{
		if(!(group instanceof DBGroup))
			throw new PrismsException("Group " + group + " was not created by this user source");
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				dbRemoveGroup((DBGroup) group, stmt);
				for(User user : theUserCache)
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
				return null;
			}
		}, "Could not delete group " + group);
	}

	public void disconnect()
	{
		theAnonymousUser = null;
		theUserCache = null;
		theGroupCache = null;
		theHashing = null;
		if(theTransactor == null)
			return;
		try
		{
			theTransactor.disconnect();
		} catch(PrismsException e)
		{
			log.error("Could not disconnect from PRISMS data source", e);
		}
		theTransactor = null;
	}

	// Implementation methods here

	User dbGetUser(long id, Statement stmt) throws PrismsException
	{
		if(id == theAnonymousUser.getID())
			return theAnonymousUser;
		if(id < 0)
			return null;
		String sql = null;
		boolean closeAfter = false;
		ResultSet rs = null;
		try
		{
			sql = "SELECT * FROM " + theTransactor.getDbOwner() + "prisms_user WHERE id=" + id;
			if(stmt == null)
			{
				closeAfter = true;
				stmt = theTransactor.getConnection().createStatement();
			}
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			boolean readOnly = boolFromSql(rs.getString("isReadOnly"));
			User ret = new User(this, rs.getString("userName"), id);
			ret.setAdmin(boolFromSql(rs.getString("isAdmin")));
			ret.setDeleted(boolFromSql(rs.getString("deleted")));
			rs.close();
			rs = null;

			sql = "SELECT id, groupApp FROM " + theTransactor.getDbOwner()
				+ "prisms_user_group_assoc INNER JOIN " + theTransactor.getDbOwner()
				+ "prisms_user_group ON assocGroup=id WHERE assocUser=" + id;
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
			ret.setReadOnly(readOnly);
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
			if(closeAfter && stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	void dbInsertUser(User user, Statement stmt) throws PrismsException
	{
		if(user.getID() < 0)
			user.setID(theIDs.getNextID(stmt, "prisms_user", "id", null, null, null));
		String sql = null;
		try
		{
			sql = "INSERT INTO " + theTransactor.getDbOwner()
				+ "prisms_user (id, userName, isAdmin, isReadOnly," + " deleted)" + " VALUES ("
				+ user.getID() + ", " + toSQL(user.getName()) + ", " + boolToSql(user.isAdmin())
				+ ", " + boolToSql(user.isReadOnly()) + ", " + boolToSql(false) + ")";
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not insert user: SQL=" + sql, e);
		}
	}

	void dbUpdateUser(final User dbUser, User setUser, final Statement stmt,
		RecordsTransaction trans) throws PrismsException
	{
		String update = "";
		if(!dbUser.getName().equals(setUser.getName()))
		{
			update += "userName=" + toSQL(setUser.getName()) + ", ";
			addModification(trans, PrismsSubjectType.user, UserChange.name, 0, setUser, null,
				dbUser.getName(), null, null);
			dbUser.setName(setUser.getName());
		}
		if(dbUser.isAdmin() != setUser.isAdmin())
		{
			update += "isAdmin=" + boolToSql(setUser.isAdmin()) + ", ";
			addModification(trans, PrismsSubjectType.user, UserChange.admin, 0, setUser, null,
				Boolean.valueOf(dbUser.isAdmin()), null, null);
			dbUser.setAdmin(setUser.isAdmin());
		}
		if(dbUser.isReadOnly() != setUser.isReadOnly())
		{
			update += "isReadOnly=" + boolToSql(setUser.isReadOnly()) + ", ";
			addModification(trans, PrismsSubjectType.user, UserChange.readOnly, 0, setUser, null,
				Boolean.valueOf(dbUser.isReadOnly()), null, null);
			dbUser.setReadOnly(setUser.isReadOnly());
		}
		if(dbUser.isDeleted() != setUser.isDeleted())
		{
			update += "deleted=" + boolToSql(setUser.isDeleted()) + ", ";
			addModification(trans, PrismsSubjectType.user, null, setUser.isDeleted() ? -1 : 1,
				setUser, null, null, null, null);
			dbUser.setDeleted(setUser.isDeleted());
			if(theIDs.belongs(dbUser.getID()))
			{
				if(setUser.isDeleted())
					theUserCache = ArrayUtils.remove(theUserCache, setUser);
				else
					theUserCache = ArrayUtils.add(theUserCache, setUser);
			}
		}

		if(update.length() > 0)
		{
			update = update.substring(0, update.length() - 2);
			String sql = null;
			try
			{
				sql = "UPDATE " + theTransactor.getDbOwner() + "prisms_user SET " + update
					+ " WHERE id=" + dbUser.getID();
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
					String sql = null;
					try
					{
						sql = "INSERT INTO " + theTransactor.getDbOwner()
							+ "prisms_user_group_assoc (assocUser, assocGroup) VALUES ("
							+ dbUser.getID() + ", " + ((DBGroup) o).getID() + ")";
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
					String sql = null;
					try
					{
						sql = "DELETE FROM " + theTransactor.getDbOwner()
							+ "prisms_user_group_assoc WHERE assocUser=" + dbUser.getID()
							+ " AND assocGroup=" + ((DBGroup) o).getID();
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

	void dbRemoveUser(User user, Statement stmt, RecordsTransaction trans) throws PrismsException
	{
		// String sql = "DELETE FROM " + theTransactor.getDbOwner() + "prisms_user WHERE id=" +
		// user.getID();
		String sql = null;
		try
		{
			sql = "UPDATE " + theTransactor.getDbOwner() + "prisms_user SET deleted="
				+ boolToSql(true) + " WHERE id=" + user.getID();
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete user: SQL=" + sql, e);
		}
		addModification(trans, PrismsSubjectType.user, null, -1, user, null, null, null, null);
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
		String sql = null;
		ResultSet rs = null;
		try
		{
			sql = "SELECT * FROM " + theTransactor.getDbOwner() + "prisms_user_group WHERE id="
				+ id;
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			DBGroup ret = new DBGroup(this, rs.getString("groupName"), app, id);
			ret.setDescription(rs.getString("groupDescrip"));
			ret.setDeleted(boolFromSql(rs.getString("deleted")));
			rs.close();
			rs = null;

			sql = "SELECT assocPermission FROM " + theTransactor.getDbOwner()
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
			group.setID(IDGenerator.getNextIntID(stmt, theTransactor.getDbOwner()
				+ "prisms_user_group", "id", null));
			sql = "INSERT INTO " + theTransactor.getDbOwner()
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
			String sql = null;
			try
			{
				sql = "UPDATE " + theTransactor.getDbOwner() + "prisms_user_group SET " + update
					+ " WHERE id=" + dbGroup.getID();
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
				String sql = null;
				try
				{
					sql = "INSERT INTO " + theTransactor.getDbOwner()
						+ "prisms_group_permissions (assocGroup, pApp, assocPermission) VALUES ("
						+ dbGroup.getID() + ", " + toSQL(o.getApp().getName()) + ", "
						+ toSQL(o.getName()) + ")";
					stmt.execute(sql);
				} catch(SQLException e)
				{
					log.error("Could not insert group permission: SQL=" + sql, e);
				}
				return o;
			}

			public Permission removed(Permission o, int idx, int incMod, int retIdx)
			{
				String sql = null;
				try
				{
					sql = "DELETE FROM " + theTransactor.getDbOwner()
						+ "prisms_group_permissions WHERE assocGroup=" + dbGroup.getID()
						+ " AND pApp=" + toSQL(o.getApp().getName()) + " AND assocPermission="
						+ toSQL(o.getName());
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
		String sql = null;
		try
		{
			sql = "DELETE FROM " + theTransactor.getDbOwner() + "prisms_user_group WHERE id="
				+ group.getID();
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete group: SQL=" + sql, e);
		}
	}

	/**
	 * Creates a database hash string from a series of hash values
	 * 
	 * @param hash The primary hashing of the password
	 * @return The value to store in the database for the user's password data
	 */
	public static String join(long [] hash)
	{
		String ret = "";
		for(int h = 0; h < hash.length; h++)
			ret += hash[h] + (h < hash.length - 1 ? ":" : "");
		return ret;
	}

	/**
	 * Parses a user's password data from the database
	 * 
	 * @param joined The user's databased password data
	 * @return The primary hash of the user's password
	 */
	public static long [] parsePwdData(String joined)
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
}
