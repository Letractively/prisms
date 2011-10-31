/*
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
import prisms.arch.PrismsApplication.ApplicationLock;
import prisms.arch.ds.*;
import prisms.arch.ds.Transactor.TransactionOperation;
import prisms.impl.PrismsChangeTypes.GroupChange;
import prisms.impl.PrismsChangeTypes.UserChange;
import prisms.records.RecordsTransaction;
import prisms.util.ArrayUtils;
import prisms.util.DBUtils;
import prisms.util.LongList;

/** A {@link prisms.arch.ds.ManageableUserSource} that obtains its information from a database */
public class DBUserSource implements ScalableUserSource
{
	static final Logger log = Logger.getLogger(DBUserSource.class);

	static final Logger userLog = Logger.getLogger("prisms.users");

	static class PasswordData
	{
		int id;

		long [] thePasswordHash;

		long thePasswordTime;

		long thePasswordExpire;
	}

	/** Determines a minimum time that passwords are kept around */
	public static final long PASSWORD_KEEP_TIME = 5L * 60 * 1000;

	Transactor<PrismsException> theTransactor;

	private PrismsEnv theEnv;

	private prisms.records.DBRecordKeeper theKeeper;

	private final java.util.HashMap<String, PrismsApplication> theApps;

	ArrayList<UserSetListener> theListeners;

	IDGenerator theIDs;

	private String theAnonymousUserName;

	private Hashing theHashing;

	User [] theUserCache;

	private User theAnonymousUser;

	private User theSystemUser;

	UserGroup [] theGroupCache;

	/** Creates a DBUserSource */
	public DBUserSource()
	{
		theHashing = new Hashing();
		theAnonymousUserName = "anonymous";
		theApps = new java.util.HashMap<String, PrismsApplication>();
		theListeners = new ArrayList<UserSetListener>();
	}

	public prisms.records.DBRecordKeeper getRecordKeeper()
	{
		return theKeeper;
	}

	public void configure(PrismsConfig config, PrismsEnv env, PrismsApplication [] apps,
		Hashing initHashing) throws PrismsException
	{
		PrismsConfig connEl = config.subConfig("connection");
		theTransactor = env.getConnectionFactory().getConnection(connEl, null,
			new Transactor.Thrower<PrismsException>()
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
		theEnv = env;
		for(PrismsApplication app : apps)
			theApps.put(app.getName(), app);
		theIDs = env.getIDs();
		if(theIDs.isShared())
			theKeeper = new prisms.records.ScaledRecordKeeper("PRISMS", connEl,
				theEnv.getConnectionFactory(), theIDs);
		else
			theKeeper = new prisms.records.DBRecordKeeper("PRISMS", connEl,
				theEnv.getConnectionFactory(), theIDs);
		theKeeper.setPersister(new PrismsSyncImpl(this, theApps.values().toArray(
			new PrismsApplication [theApps.size()])));
		if(theKeeper instanceof prisms.records.ScaledRecordKeeper)
			((prisms.records.ScaledRecordKeeper) theKeeper).setScaleImpl((PrismsSyncImpl) theKeeper
				.getPersister());
		String anonUser = config.get("anonymous");
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
			sql = "SELECT multiple, modulus FROM " + theTransactor.getTablePrefix()
				+ "PRISMS_HASHING ORDER BY id";
			rs = stmt.executeQuery(sql);
			java.util.ArrayList<long []> hashing = new java.util.ArrayList<long []>();
			while(rs.next())
				hashing.add(new long [] {rs.getInt(1), rs.getInt(2)});

			long [] mults = new long [hashing.size()];
			long [] mods = new long [hashing.size()];
			for(int h = 0; h < mults.length; h++)
			{
				mults[h] = hashing.get(h)[0];
				mods[h] = hashing.get(h)[1];
			}
			theHashing.setPrimaryHashing(mults, mods);
			if(theHashing.getPrimaryMultiples() == null
				|| theHashing.getPrimaryMultiples().length == 0)
			{
				if(initHashing != null)
					theHashing = initHashing;
				else
					theHashing.randomlyFillPrimary(10);

				sql = null;
				try
				{
					for(int i = 0; i < theHashing.getPrimaryMultiples().length; i++)
					{
						sql = "INSERT INTO " + theTransactor.getTablePrefix()
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

	private void fillUserCache(Statement stmt) throws PrismsException
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
		User [] users;
		try
		{
			if(theUserCache != null)
				return; // Already filled before lock received
			if(theGroupCache == null)
				fillGroupCache(stmt);
			ArrayList<User> userList = new ArrayList<User>();
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_user WHERE deleted="
				+ boolToSql(false) + " AND id>=" + IDGenerator.getMinID(theIDs.getCenterID())
				+ " AND id<=" + IDGenerator.getMaxID(theIDs.getCenterID()) + " ORDER BY id";
			rs = stmt.executeQuery(sql);
			java.util.HashSet<User> readOnlyUsers = new java.util.HashSet<User>();
			while(rs.next())
			{
				User user = new User(this, rs.getString("userName"), rs.getLong("id"));
				user.setLocked(boolFromSql(rs.getString("isLocked")));
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
			LongList groupIDs = new LongList();
			ArrayList<String> appNames = new ArrayList<String>();
			sql = "SELECT assocUser, id, groupApp FROM " + theTransactor.getTablePrefix()
				+ "prisms_user_group_assoc INNER JOIN " + theTransactor.getTablePrefix()
				+ "prisms_user_group ON assocGroup=id WHERE deleted=" + boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				userIDs.add(rs.getLong("assocUser"));
				groupIDs.add(rs.getLong("id"));
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
					UserGroup group = getGroup(groupIDs.get(g), app, stmt);
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
		UserGroup [] groups;
		try
		{
			if(theGroupCache != null)
				return; // Already filled before lock received
			java.util.ArrayList<UserGroup> groupList = new ArrayList<UserGroup>();
			sql = "SELECT * FROM " + theTransactor.getTablePrefix()
				+ "prisms_user_group WHERE deleted=" + boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				PrismsApplication app = theApps.get(rs.getString("groupApp"));
				UserGroup group = new UserGroup(this, rs.getString("groupName"), app,
					rs.getLong("id"));
				group.setDescription(rs.getString("groupDescrip"));
				groupList.add(group);
			}
			rs.close();
			rs = null;
			groups = groupList.toArray(new UserGroup [groupList.size()]);

			ArrayList<Long> groupIDs = new ArrayList<Long>();
			ArrayList<String> appNames = new ArrayList<String>();
			ArrayList<String> permNames = new ArrayList<String>();
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_group_permissions";
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				groupIDs.add(Long.valueOf(rs.getLong("assocGroup")));
				appNames.add(rs.getString("pApp"));
				permNames.add(rs.getString("assocPermission"));
			}
			rs.close();
			rs = null;
			for(int p = 0; p < permNames.size(); p++)
			{
				for(UserGroup g : groups)
				{
					if(g.getID() != groupIDs.get(p).longValue())
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

	public ApplicationStatus getApplicationStatus(PrismsApplication app) throws PrismsException
	{
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
			+ "prisms_application_status WHERE application=" + toSQL(app.getName());
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return new ApplicationStatus(null, -1, -1);
			java.sql.Timestamp time = rs.getTimestamp("lastUpdate");
			if(time.getTime() < System.currentTimeMillis() - 2L * 60 * 60 * 1000)
				return new ApplicationStatus(null, -1, -1);
			String lockMessage = rs.getString("lockMessage");
			PrismsApplication.ApplicationLock lock;
			if(lockMessage == null || time.getTime() < System.currentTimeMillis() - 5000)
				lock = null;
			else
				lock = new PrismsApplication.ApplicationLock(lockMessage, rs.getInt("lockScale"),
					rs.getInt("lockProgress"), null);
			Number rProp = (Number) rs.getObject("reloadProperties");
			Number rSess = (Number) rs.getObject("reloadSessions");
			return new ApplicationStatus(lock, rProp == null ? -1 : rProp.intValue(), rSess == null
				? -1 : rSess.intValue());
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get lock for application " + app + ": SQL=" + sql,
				e);
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

	public void setApplicationLock(PrismsApplication app, ApplicationLock lock)
		throws PrismsException
	{
		String sql = "SELECT lockScale FROM " + theTransactor.getTablePrefix()
			+ "prisms_application_status WHERE application=" + toSQL(app.getName());
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			boolean update = rs.next();
			rs.close();
			rs = null;
			String msg = lock == null ? null : lock.getMessage();
			int scale = lock == null ? -1 : lock.getScale();
			int progress = lock == null ? -1 : lock.getProgress();
			String sqlDate = formatDate(System.currentTimeMillis(),
				isOracle(theTransactor.getConnection()));
			String updateSql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_application_status SET lockMessage=" + toSQL(msg) + ", lockScale="
				+ scale + ", lockProgress=" + progress + ", lastUpdate=" + sqlDate
				+ " WHERE application=" + toSQL(app.getName());
			String insertSql = "INSERT INTO " + theTransactor.getTablePrefix()
				+ "prisms_application_status"
				+ " (application, lockMessage, lockScale, lockProgress," + " lastUpdate) VALUES ("
				+ toSQL(app.getName()) + ", " + toSQL(msg) + ", " + scale + ", " + progress + ", "
				+ sqlDate + ")";
			if(update)
				sql = updateSql;
			else
				sql = insertSql;
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				if(!update)
					sql = updateSql;
				else
					throw e;
				stmt.executeUpdate(sql);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not set lock for application " + app + ": SQL=" + sql,
				e);
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

	public void reloadProperties(PrismsApplication app) throws PrismsException
	{
		String sql = "SELECT lockScale FROM " + theTransactor.getTablePrefix()
			+ "prisms_application_status WHERE application=" + toSQL(app.getName());
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			boolean update = rs.next();
			rs.close();
			rs = null;
			int commandID = (int) (Math.random() * Integer.MAX_VALUE);
			String updateSql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_application_status SET reloadProperties=" + commandID
				+ " WHERE application=" + toSQL(app.getName());
			String insertSql = "INSERT INTO " + theTransactor.getTablePrefix()
				+ "prisms_application_lock" + " (application, reloadProperties) VALUES ("
				+ toSQL(app.getName()) + ", " + commandID + ")";
			if(update)
				sql = updateSql;
			else
				sql = insertSql;
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				if(!update)
					sql = updateSql;
				else
					throw e;
				stmt.executeUpdate(sql);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not reload properties for application " + app
				+ ": SQL=" + sql, e);
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

	public void reloadSessions(PrismsApplication app) throws PrismsException
	{
		String sql = "SELECT lockScale FROM " + theTransactor.getTablePrefix()
			+ "prisms_application_status WHERE application=" + toSQL(app.getName());
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			boolean update = rs.next();
			rs.close();
			rs = null;
			int commandID = (int) (Math.random() * Integer.MAX_VALUE);
			String updateSql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_application_status SET reloadSessions=" + commandID
				+ " WHERE application=" + toSQL(app.getName());
			String insertSql = "INSERT INTO " + theTransactor.getTablePrefix()
				+ "prisms_application_lock" + " (application, reloadSessions) VALUES ("
				+ toSQL(app.getName()) + ", " + commandID + ")";
			if(update)
				sql = updateSql;
			else
				sql = insertSql;
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				if(!update)
					sql = updateSql;
				else
					throw e;
				stmt.executeUpdate(sql);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not reload properties for application " + app
				+ ": SQL=" + sql, e);
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
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_password_constraints";
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
			sql = "SELECT constraintsLocked FROM " + theTransactor.getTablePrefix()
				+ "prisms_password_constraints";
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				sql = "UPDATE " + theTransactor.getTablePrefix()
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
				sql = "INSERT INTO " + theTransactor.getTablePrefix()
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
		return theSystemUser;
	}

	private boolean addingAnonymous;

	public User getUser(String name) throws PrismsException
	{
		if(theUserCache == null)
			fillUserCache(null);
		if(name == null || name.equals(theAnonymousUserName))
		{
			if(theAnonymousUser == null)
			{
				theAnonymousUser = new User(this, theAnonymousUserName, IDGenerator.getMaxID(theIDs
					.getCenterID()) - 1);
				theAnonymousUser.setReadOnly(true);
				addingAnonymous = true;
				try
				{
					putUser(theAnonymousUser, new RecordsTransaction(getSystemUser()));
				} finally
				{
					addingAnonymous = false;
				}
				theUserCache = ArrayUtils.remove(theUserCache, theAnonymousUser);
			}
			return theAnonymousUser;
		}
		else if(name.equals("System"))
			return getSystemUser();
		for(User user : theUserCache)
			if(user.getName().equals(name))
				return user;
		return null;
	}

	public User getUser(long id) throws PrismsException
	{
		if(theUserCache == null)
			fillUserCache(null);
		if(id == IDGenerator.getMaxID(theIDs.getCenterID()))
			return theSystemUser;
		if(id == IDGenerator.getMaxID(theIDs.getCenterID()) - 1)
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
			sql = "SELECT * FROM " + theTransactor.getTablePrefix()
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
			sql = "SELECT * FROM " + theTransactor.getTablePrefix()
				+ "prisms_user_password WHERE pwdUser=" + user.getID() + " ORDER BY pwdTime DESC";
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

	public Password getPassword(User user) throws PrismsException
	{
		PasswordData [] password = getPasswordData(user, true, null);
		if(password == null || password.length == 0)
			return null;
		return new Password(password[0].thePasswordHash, password[0].thePasswordTime,
			password[0].thePasswordExpire);
	}

	public Password [] getOldPasswords(User user) throws PrismsException
	{
		PasswordData [] password = getPasswordData(user, false, null);
		if(password == null || password.length == 0)
			return null;
		Password [] ret = new Password [password.length];
		for(int p = 0; p < ret.length; p++)
			ret[p] = new Password(password[p].thePasswordHash, password[p].thePasswordTime,
				password[p].thePasswordExpire);
		return ret;
	}

	public Object getDBValue(prisms.records.ChangeRecord record) throws PrismsException
	{
		if(record.type.changeType == null || record.type.changeType.getObjectType() == null)
			return null;
		if(record.type.subjectType instanceof prisms.records.PrismsChange)
			return prisms.records.RecordUtils.getDBValue(record, theKeeper.getNamespace(),
				theTransactor, theKeeper.getPersister());
		String sql;
		if(record.type.subjectType instanceof PrismsSubjectType)
		{
			switch((PrismsSubjectType) record.type.subjectType)
			{
			case user:
				User user = (User) record.majorSubject;
				sql = " FROM " + theTransactor.getTablePrefix() + "prisms_user WHERE id="
					+ user.getID();
				switch((PrismsChangeTypes.UserChange) record.type.changeType)
				{
				case name:
					return DBUtils.fromSQL(theTransactor.getDBItem(null, "SELECT userName" + sql,
						String.class));
				case locked:
					return Boolean.valueOf(boolFromSql(theTransactor.getDBItem(null,
						"SELECT isLocked" + sql, String.class)));
				case admin:
					return Boolean.valueOf(boolFromSql(theTransactor.getDBItem(null,
						"SELECT isAdmin" + sql, String.class)));
				case readOnly:
					return Boolean.valueOf(boolFromSql(theTransactor.getDBItem(null,
						"SELECT isReadOnly" + sql, String.class)));
				case appAccess:
					return null;
				case group:
					return null;
				}
				throw new PrismsException("Unrecognized prisms change type: "
					+ record.type.changeType);
			case group:
				UserGroup group = (UserGroup) record.majorSubject;
				sql = " FROM " + theTransactor.getTablePrefix() + "prisms_user_group WHERE id="
					+ group.getID();
				switch((PrismsChangeTypes.GroupChange) record.type.changeType)
				{
				case name:
					return DBUtils.fromSQL(theTransactor.getDBItem(null, "SELECT groupName" + sql,
						String.class));
				case descrip:
					return DBUtils.fromSQL(theTransactor.getDBItem(null, "SELECT groupDescrip"
						+ sql, String.class));
				case permission:
					return null;
				}
			}
			throw new PrismsException("Unrecognized PRISMS subject type: "
				+ record.type.subjectType);
		}
		else
			throw new PrismsException("Unrecognized subject type: " + record.type.subjectType);
	}

	public Password setPassword(final User user, final long [] hash, final boolean isAdmin)
		throws PrismsException
	{
		Object ret;
		ret = theTransactor.performTransaction(new TransactionOperation<PrismsException>()
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
				long exp;
				if(constraints.getMaxPasswordDuration() > 0)
					exp = now + constraints.getMaxPasswordDuration();
				else
					exp = -1;
				try
				{
					if(!user.equals(getSystemUser()))
						userLog.info("User " + user + "'s password changed");
					sql = "INSERT INTO "
						+ theTransactor.getTablePrefix()
						+ "prisms_user_password (id, pwdUser, pwdData,"
						+ " pwdTime, pwdExpire) VALUES ("
						+ theIDs.getNextIntID(stmt, "prisms_user_password",
							theTransactor.getTablePrefix(), "id", null) + ", " + user.getID()
						+ ", " + DBUtils.toSQL(join(hash)) + ", " + now + ", ";
					if(exp >= 0)
						sql += exp;
					else
						sql += "NULL";
					sql += ")";
					stmt.execute(sql);

					for(int p = password.length - 1; p >= 0
						&& p >= constraints.getNumPreviousUnique(); p--)
					{
						if(now - password[p].thePasswordTime < PASSWORD_KEEP_TIME)
							continue;
						sql = "DELETE FROM " + theTransactor.getTablePrefix()
							+ "prisms_user_password WHERE id=" + password[p].id;
						stmt.execute(sql);
					}
				} catch(SQLException e)
				{
					throw new PrismsException("Could not set password data for user " + user
						+ ": SQL=" + sql, e);
				}
				return new Password(hash, now, exp);
			}
		}, "Could not set password for user " + user);
		return (Password) ret;
	}

	public long getPasswordExpiration(User user) throws PrismsException
	{
		PasswordData [] password = getPasswordData(user, true, null);
		if(password == null || password.length == 0)
			return Long.MAX_VALUE;
		return password[0].thePasswordExpire;
	}

	public void lockUser(User user)
	{
		if(user.isAdmin() || user.equals(theAnonymousUser) || user.equals(theSystemUser))
			return; // Can't lock anonymous, the system user, or an admin user
		user.setLocked(true);
		try
		{
			putUser(user, new RecordsTransaction(user));
		} catch(PrismsException e)
		{
			log.error("Could not lock user " + user, e);
		}
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

	public UserGroup [] getGroups(PrismsApplication app) throws PrismsException
	{
		if(theGroupCache == null)
			fillGroupCache(null);
		ArrayList<UserGroup> groups = new ArrayList<UserGroup>();
		for(UserGroup group : theGroupCache)
			if(group.getApp() == app)
				groups.add(group);
		UserGroup [] ret = groups.toArray(new UserGroup [groups.size()]);
		java.util.Arrays.sort(ret, new java.util.Comparator<UserGroup>()
		{
			public int compare(UserGroup o1, UserGroup o2)
			{
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		return ret;
	}

	public UserGroup getGroup(long id) throws PrismsException
	{
		if(theGroupCache == null)
			fillGroupCache(null);
		for(UserGroup g : theGroupCache)
			if(g.getID() == id)
				return g;
		Statement stmt = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			return dbGetGroup(id, stmt);
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
				userLog.info("User " + user + "'s password expiration set to "
					+ prisms.util.PrismsUtils.print(time));
				sql = "UPDATE " + theTransactor.getTablePrefix()
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
		prisms.records.ChangeType changeType, int add, Object majorSubject, Object minorSubject,
		Object previousValue, Object data1, Object data2) throws PrismsException
	{
		if(trans == null || !trans.shouldRecord() || theKeeper == null)
			return;
		prisms.records.ChangeRecord record;
		try
		{
			record = theKeeper.persist(trans, subjectType, changeType, add, majorSubject,
				minorSubject, previousValue, data1, data2);
		} catch(prisms.records.PrismsRecordException e)
		{
			throw new PrismsException("Could not persist change record", e);
		}
		if(trans.getRecord() != null)
		{
			try
			{
				theKeeper.associate(record, trans.getRecord(), false);
			} catch(prisms.records.PrismsRecordException e)
			{
				log.error("Could not associate change record with sync record", e);
			}
		}
	}

	public void setUserAccess(final User user, final PrismsApplication app,
		final boolean accessible, final RecordsTransaction trans) throws PrismsException
	{
		if(trans != null && trans.isMemoryOnly())
		{
			for(UserSetListener listener : theListeners.toArray(new UserSetListener [theListeners
				.size()]))
				listener.userChanged(user);
			return;
		}
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
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
						userLog.info("User " + user + " granted access to " + app.getName());
						addModification(trans, PrismsSubjectType.user, UserChange.appAccess, 1,
							user, app, null, null, null);
						sql = "INSERT INTO " + theTransactor.getTablePrefix()
							+ "prisms_user_app_assoc (assocUser, assocApp) VALUES ("
							+ (user).getID() + ", " + toSQL(app.getName()) + ")";
					}
					else
					{
						userLog.info("User " + user + " denied access to " + app.getName());
						addModification(trans, PrismsSubjectType.user, UserChange.appAccess, -1,
							user, app, null, null, null);
						sql = "DELETE FROM " + theTransactor.getTablePrefix()
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
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_user ORDER BY id";
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
				user.setLocked(boolFromSql(rs.getString("isLocked")));
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
			LongList groupIDs = new LongList();
			ArrayList<String> appNames = new ArrayList<String>();
			sql = "SELECT assocUser, id, groupApp FROM " + theTransactor.getTablePrefix()
				+ "prisms_user_group_assoc INNER JOIN " + theTransactor.getTablePrefix()
				+ "prisms_user_group ON assocGroup=id WHERE deleted=" + boolToSql(false);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				userIDs.add(rs.getLong("assocUser"));
				groupIDs.add(rs.getLong("id"));
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
					UserGroup group = getGroup(groupIDs.get(g), app, stmt);
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
		if(trans != null && trans.isMemoryOnly())
		{
			User cacheUser = null;
			if(user.equals(theSystemUser))
				cacheUser = theSystemUser;
			else if(user.equals(theAnonymousUser))
				cacheUser = theAnonymousUser;
			else
			{
				int idx = ArrayUtils.indexOf(theUserCache, user);
				if(idx >= 0)
					cacheUser = theUserCache[idx];
			}
			if(cacheUser != null)
			{
				if(user != cacheUser)
					dbUpdateUser(cacheUser, user, null, trans);
				else
					for(UserSetListener listener : theListeners
						.toArray(new UserSetListener [theListeners.size()]))
						listener.userChanged(user);
			}
			else
			{
				theUserCache = ArrayUtils.add(theUserCache, user);
				for(UserSetListener listener : theListeners
					.toArray(new UserSetListener [theListeners.size()]))
					listener.userSetChanged(theUserCache.clone());
			}
			return;
		}
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
					boolean authChange = dbUpdateUser(dbUser, user, stmt, trans);
					if(theIDs.belongs(user.getID()))
					{
						if(recreate)
						{
							for(UserSetListener listener : theListeners
								.toArray(new UserSetListener [theListeners.size()]))
								listener.userSetChanged(theUserCache.clone());
						}
						else
							for(UserSetListener listener : theListeners
								.toArray(new UserSetListener [theListeners.size()]))
							{
								listener.userChanged(user);
								if(authChange)
									listener.userAuthorityChanged(user);
							}
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
				if(trans != null && !trans.isMemoryOnly())
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

	public UserGroup createGroup(final PrismsApplication app, final String name,
		RecordsTransaction trans) throws PrismsException
	{
		if(name == null)
			throw new PrismsException("Cannot create group with no name");
		UserGroup ret = new UserGroup(this, name, app, -1);
		putGroup(ret, trans);
		return ret;
	}

	public void putGroup(final UserGroup group, final RecordsTransaction trans)
		throws PrismsException
	{
		if(trans != null && trans.isMemoryOnly())
		{
			UserGroup cacheGroup = null;
			int idx = ArrayUtils.indexOf(theGroupCache, group);
			if(idx >= 0)
				cacheGroup = theGroupCache[idx];
			if(cacheGroup != null)
			{
				if(group != cacheGroup)
					dbUpdateGroup(cacheGroup, group, null, trans);
				else
					for(UserSetListener listener : theListeners
						.toArray(new UserSetListener [theListeners.size()]))
						listener.groupChanged(group);
			}
			else
			{
				theGroupCache = ArrayUtils.add(theGroupCache, group);
				for(UserSetListener listener : theListeners
					.toArray(new UserSetListener [theListeners.size()]))
					listener.groupSetChanged(group.getApp(), getGroups(group.getApp()));
			}
			return;
		}
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				UserGroup dbGroup = group.getID() < 0 ? null : dbGetGroup(group.getID(), stmt);
				if(dbGroup == null)
				{
					dbInsertGroup(group, stmt, trans);
					if(theIDs.belongs(group.getID()))
					{
						theGroupCache = ArrayUtils.add(theGroupCache, group);
						for(UserSetListener listener : theListeners
							.toArray(new UserSetListener [theListeners.size()]))
							listener.groupSetChanged(group.getApp(), getGroups(group.getApp()));
					}
				}
				else
				{
					boolean recreate = dbGroup.isDeleted() != group.isDeleted();
					boolean authChange = dbUpdateGroup(dbGroup, group, stmt, trans);
					if(authChange)
						for(User user : theUserCache)
						{
							if(ArrayUtils.contains(user.getGroups(), group))
								for(UserSetListener listener : theListeners
									.toArray(new UserSetListener [theListeners.size()]))
									listener.userAuthorityChanged(user);
						}

					if(theIDs.belongs(group.getID()))
					{
						if(recreate)
						{
							for(UserSetListener listener : theListeners
								.toArray(new UserSetListener [theListeners.size()]))
								listener.groupSetChanged(group.getApp(), getGroups(group.getApp()));
						}
						else
							for(UserSetListener listener : theListeners
								.toArray(new UserSetListener [theListeners.size()]))
								listener.groupChanged(group);
					}
				}
				return null;
			}
		}, "Could not add/modify user group " + group);
	}

	public void deleteGroup(final UserGroup group, final RecordsTransaction trans)
		throws PrismsException
	{
		theTransactor.performTransaction(new TransactionOperation<PrismsException>()
		{
			public Object run(Statement stmt) throws PrismsException
			{
				if(trans != null && !trans.isMemoryOnly())
					dbRemoveGroup(group, stmt, trans);
				for(User user : theUserCache)
				{
					if(ArrayUtils.contains(user.getGroups(), group))
					{
						user.removeFrom(group);
						putUser(user, trans);
					}
				}

				int idx = ArrayUtils.indexOf(theGroupCache, group);
				if(idx >= 0)
				{
					theGroupCache = ArrayUtils.remove(theGroupCache, group);
					for(UserSetListener listener : theListeners
						.toArray(new UserSetListener [theListeners.size()]))
						listener.groupSetChanged(group.getApp(), getGroups(group.getApp()));
				}
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
		theKeeper.disconnect();
		theTransactor.release();
	}

	// Implementation methods here

	User dbGetUser(long id, Statement stmt) throws PrismsException
	{
		if(id == IDGenerator.getMaxID(theIDs.getCenterID()) - 1)
			return theAnonymousUser;
		if(id < 0)
			return null;
		String sql = null;
		boolean closeAfter = false;
		ResultSet rs = null;
		try
		{
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_user WHERE id=" + id;
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
			ret.setLocked(boolFromSql(rs.getString("isLocked")));
			ret.setAdmin(boolFromSql(rs.getString("isAdmin")));
			ret.setDeleted(boolFromSql(rs.getString("deleted")));
			rs.close();
			rs = null;

			sql = "SELECT id, groupApp FROM " + theTransactor.getTablePrefix()
				+ "prisms_user_group_assoc INNER JOIN " + theTransactor.getTablePrefix()
				+ "prisms_user_group ON assocGroup=id WHERE assocUser=" + id;
			rs = stmt.executeQuery(sql);
			ArrayList<Long> groupIDs = new ArrayList<Long>();
			ArrayList<String> appNames = new ArrayList<String>();
			while(rs.next())
			{
				groupIDs.add(Long.valueOf(rs.getLong(1)));
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

				UserGroup group = getGroup(groupIDs.get(g).longValue(), app, stmt);
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
			user.setID(theIDs.getNextID("prisms_user", "id", stmt, theTransactor.getTablePrefix(),
				null));
		String sql = null;
		try
		{
			sql = "INSERT INTO " + theTransactor.getTablePrefix()
				+ "prisms_user (id, userName, isAdmin, isReadOnly, isLocked, deleted)"
				+ " VALUES (" + user.getID() + ", " + toSQL(user.getName()) + ", "
				+ boolToSql(user.isAdmin()) + ", " + boolToSql(user.isReadOnly()) + ", "
				+ boolToSql(user.isLocked()) + ", " + boolToSql(false) + ")";
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not insert user: SQL=" + sql, e);
		}
		userLog.info("User " + user + ", ID " + user.getID() + " created");
	}

	boolean dbUpdateUser(final User dbUser, User setUser, final Statement stmt,
		final RecordsTransaction trans) throws PrismsException
	{
		final StringBuilder status = new StringBuilder("User ").append(dbUser).append(", ID ")
			.append(dbUser.getID()).append(" modified:\n");
		final boolean [] authChange = new boolean [] {false};
		String update = "";
		if(dbUser.isDeleted() != setUser.isDeleted())
		{
			update += "deleted=" + boolToSql(setUser.isDeleted()) + ", ";
			addModification(trans, PrismsSubjectType.user, null, setUser.isDeleted() ? -1 : 1,
				setUser, null, null, null, null);
			status.append("User ").append(setUser.isDeleted() ? "deleted" : "re-created")
				.append('\n');
			dbUser.setDeleted(setUser.isDeleted());
			if(theIDs.belongs(dbUser.getID()))
			{
				if(setUser.isDeleted())
					theUserCache = ArrayUtils.remove(theUserCache, setUser);
				else
					theUserCache = ArrayUtils.add(theUserCache, setUser);
			}
		}

		ArrayUtils.adjust(dbUser.getGroups(), setUser.getGroups(),
			new ArrayUtils.DifferenceListenerE<UserGroup, UserGroup, PrismsException>()
			{
				public boolean identity(UserGroup o1, UserGroup o2)
				{
					return equal(o1, o2);
				}

				public UserGroup added(UserGroup o, int idx, int retIdx) throws PrismsException
				{
					status.append("User added to group ").append(o).append(", ID ")
						.append(o.getID()).append('\n');
					String sql = null;
					try
					{
						sql = "INSERT INTO " + theTransactor.getTablePrefix()
							+ "prisms_user_group_assoc (assocUser, assocGroup) VALUES ("
							+ dbUser.getID() + ", " + o.getID() + ")";
						if(stmt != null)
							stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not insert group membership: SQL=" + sql, e);
					}
					dbUser.addTo(o);
					addModification(trans, PrismsSubjectType.user, UserChange.group, 1, dbUser, o,
						null, null, null);
					authChange[0] = true;
					return o;
				}

				public UserGroup removed(UserGroup o, int idx, int incMod, int retIdx)
					throws PrismsException
				{
					status.append("User removed from group ").append(o).append(", ID ")
						.append(o.getID()).append('\n');
					String sql = null;
					try
					{
						sql = "DELETE FROM " + theTransactor.getTablePrefix()
							+ "prisms_user_group_assoc WHERE assocUser=" + dbUser.getID()
							+ " AND assocGroup=" + o.getID();
						if(stmt != null)
							stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not remove group membership: SQL=" + sql, e);
					}
					dbUser.removeFrom(o);
					addModification(trans, PrismsSubjectType.user, UserChange.group, -1, dbUser, o,
						null, null, null);
					authChange[0] = true;
					return null;
				}

				public UserGroup set(UserGroup o1, int idx1, int incMod, UserGroup o2, int idx2,
					int retIdx)
				{
					return o1;
				}
			});

		if(!dbUser.getName().equals(setUser.getName()))
		{
			status.append("Name changed to " + setUser.getName()).append('\n');
			update += "userName=" + toSQL(setUser.getName()) + ", ";
			addModification(trans, PrismsSubjectType.user, UserChange.name, 0, setUser, null,
				dbUser.getName(), null, null);
			dbUser.setName(setUser.getName());
		}
		if(dbUser.isAdmin() != setUser.isAdmin())
		{
			status.append("Admin priveleges ").append(setUser.isAdmin() ? "granted" : "revoked")
				.append('\n');
			update += "isAdmin=" + boolToSql(setUser.isAdmin()) + ", ";
			addModification(trans, PrismsSubjectType.user, UserChange.admin, 0, setUser, null,
				Boolean.valueOf(dbUser.isAdmin()), null, null);
			boolean ro = dbUser.isReadOnly();
			dbUser.setReadOnly(false);
			try
			{
				dbUser.setAdmin(setUser.isAdmin());
			} finally
			{
				dbUser.setReadOnly(ro);
			}
			authChange[0] = true;
		}
		if(dbUser.isLocked() != setUser.isLocked())
		{
			status.append("User ").append(setUser.isLocked() ? "locked" : "unlocked").append('\n');
			update += "isLocked=" + boolToSql(setUser.isLocked()) + ", ";
			addModification(trans, PrismsSubjectType.user, UserChange.locked, 0, setUser, null,
				Boolean.valueOf(dbUser.isLocked()), null, null);
			dbUser.setLocked(setUser.isLocked());
		}
		if(dbUser.isReadOnly() != setUser.isReadOnly())
		{
			status.append("User made ").append(setUser.isReadOnly() ? "read-only" : "modifiable")
				.append('\n');
			update += "isReadOnly=" + boolToSql(setUser.isReadOnly()) + ", ";
			addModification(trans, PrismsSubjectType.user, UserChange.readOnly, 0, setUser, null,
				Boolean.valueOf(dbUser.isReadOnly()), null, null);
			dbUser.setReadOnly(setUser.isReadOnly());
		}

		if(update.length() > 0)
		{
			update = update.substring(0, update.length() - 2);
			String sql = null;
			try
			{
				sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_user SET " + update
					+ " WHERE id=" + dbUser.getID();
				if(stmt != null)
					stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsException("Could not update user: SQL=" + sql, e);
			}
		}

		if(update.length() > 0 || authChange[0])
		{
			status.delete(status.length() - 1, status.length());
			userLog.info(status.toString());
		}
		return authChange[0];
	}

	void dbRemoveUser(User user, Statement stmt, RecordsTransaction trans) throws PrismsException
	{
		userLog.info("User " + user + ", ID " + user.getID() + " deleted");
		// String sql = "DELETE FROM " + theTransactor.getTablePrefix() + "prisms_user WHERE id=" +
		// user.getID();
		String sql = null;
		try
		{
			sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_user SET deleted="
				+ boolToSql(true) + " WHERE id=" + user.getID();
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete user: SQL=" + sql, e);
		}
		addModification(trans, PrismsSubjectType.user, null, -1, user, null, null, null, null);
	}

	void purgeUser(User user, Statement stmt) throws PrismsException
	{
		userLog.info("User " + user + ", ID " + user.getID() + " purged");
		String sql = "DELETE FROM " + theTransactor.getTablePrefix() + "prisms_user WHERE id="
			+ user.getID();
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not purge user: SQL=" + sql, e);
		}
	}

	private UserGroup getGroup(long id, PrismsApplication app, Statement stmt)
		throws PrismsException
	{
		for(UserGroup group : theGroupCache)
			if(group.getID() == id)
				return group;
		return dbGetGroup(id, stmt);
	}

	UserGroup dbGetGroup(long id, Statement stmt) throws PrismsException
	{
		if(id < 0)
			return null;
		String sql = null;
		ResultSet rs = null;
		try
		{
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_user_group WHERE id="
				+ id;
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			PrismsApplication app = theApps.get(rs.getString("groupApp"));
			if(app == null)
				throw new PrismsException("No such application: " + rs.getString("groupApp"));
			UserGroup ret = new UserGroup(this, rs.getString("groupName"), app, id);
			ret.setDescription(rs.getString("groupDescrip"));
			ret.setDeleted(boolFromSql(rs.getString("deleted")));
			rs.close();
			rs = null;

			sql = "SELECT assocPermission FROM " + theTransactor.getTablePrefix()
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

	void dbInsertGroup(UserGroup group, Statement stmt, RecordsTransaction trans)
		throws PrismsException
	{
		String sql = null;
		try
		{
			if(group.getID() < 0)
				group.setID(theIDs.getNextID("prisms_user_group", "id", stmt,
					theTransactor.getTablePrefix(), null));
			sql = "INSERT INTO " + theTransactor.getTablePrefix()
				+ "prisms_user_group (id, groupApp, groupName, groupDescrip, deleted) VALUES ("
				+ group.getID() + ", " + toSQL(group.getApp().getName()) + ", "
				+ toSQL(group.getName()) + ", " + toSQL(group.getDescription()) + ", "
				+ boolToSql(false) + ")";
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not insert group: SQL=" + sql, e);
		}
		userLog.info("Group " + group + ", ID " + group.getID() + " created");
		addModification(trans, PrismsSubjectType.group, null, 1, group, null, null, group.getApp(),
			null);
	}

	boolean dbUpdateGroup(final UserGroup dbGroup, UserGroup setGroup, final Statement stmt,
		final RecordsTransaction trans) throws PrismsException
	{
		final StringBuilder status = new StringBuilder("Group ").append(dbGroup).append(", ID ")
			.append(dbGroup.getID()).append(" modified:\n");
		final boolean [] authChange = new boolean [] {false};
		String update = "";
		if(dbGroup.isDeleted() != setGroup.isDeleted())
		{
			status.append("Group ").append(setGroup.isDeleted() ? "deleted" : "re-created")
				.append('\n');
			update += "deleted=" + boolToSql(setGroup.isDeleted()) + ", ";
			if(setGroup.isDeleted())
				theGroupCache = ArrayUtils.remove(theGroupCache, setGroup);
			else
				theGroupCache = ArrayUtils.add(theGroupCache, setGroup);
			addModification(trans, PrismsSubjectType.group, null, setGroup.isDeleted() ? -1 : 1,
				dbGroup, null, null, dbGroup.getApp(), null);
		}
		if(!dbGroup.getName().equals(setGroup.getName()))
		{
			status.append("Name changed to ").append(setGroup.getName()).append('\n');
			update += "groupName=" + toSQL(setGroup.getName()) + ", ";
			addModification(trans, PrismsSubjectType.group, GroupChange.name, 0, dbGroup, null,
				dbGroup.getName(), dbGroup.getApp(), null);
		}
		if(!equal(dbGroup.getDescription(), setGroup.getDescription()))
		{
			status.append("Description changed to ").append(setGroup.getDescription()).append('\n');
			update += "groupDescrip=" + toSQL(setGroup.getDescription()) + ", ";
			addModification(trans, PrismsSubjectType.group, GroupChange.descrip, 0, dbGroup, null,
				dbGroup.getDescription(), dbGroup.getApp(), null);
		}

		if(update.length() > 0)
		{
			update = update.substring(0, update.length() - 2);
			String sql = null;
			try
			{
				sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_user_group SET "
					+ update + " WHERE id=" + dbGroup.getID();
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
				status.append("Permission ").append(o.getName()).append(" added\n");
				String sql = null;
				try
				{
					sql = "INSERT INTO " + theTransactor.getTablePrefix()
						+ "prisms_group_permissions (assocGroup, pApp, assocPermission) VALUES ("
						+ dbGroup.getID() + ", " + toSQL(o.getApp().getName()) + ", "
						+ toSQL(o.getName()) + ")";
					stmt.execute(sql);
				} catch(SQLException e)
				{
					log.error("Could not insert group permission: SQL=" + sql, e);
				}
				try
				{
					addModification(trans, PrismsSubjectType.group, GroupChange.permission, 1,
						dbGroup, o, null, dbGroup.getApp(), null);
				} catch(PrismsException e)
				{
					log.error("Could not add modification for group permission", e);
				}
				authChange[0] = true;
				return o;
			}

			public Permission removed(Permission o, int idx, int incMod, int retIdx)
			{
				status.append("Permission ").append(o.getName()).append(" removed\n");
				String sql = null;
				try
				{
					sql = "DELETE FROM " + theTransactor.getTablePrefix()
						+ "prisms_group_permissions WHERE assocGroup=" + dbGroup.getID()
						+ " AND pApp=" + toSQL(o.getApp().getName()) + " AND assocPermission="
						+ toSQL(o.getName());
					stmt.execute(sql);
				} catch(SQLException e)
				{
					log.error("Could not remove group permission: SQL=" + sql, e);
				}
				try
				{
					addModification(trans, PrismsSubjectType.group, GroupChange.permission, -1,
						dbGroup, o, null, dbGroup.getApp(), null);
				} catch(PrismsException e)
				{
					log.error("Could not add modification for group permission", e);
				}
				authChange[0] = true;
				return null;
			}

			public Permission set(Permission o1, int idx1, int incMod, Permission o2, int idx2,
				int retIdx)
			{
				return o1;
			}
		});
		if(update.length() > 0 || authChange[0])
		{
			status.delete(status.length() - 1, status.length());
			userLog.info(status.toString());
		}
		return authChange[0];
	}

	void dbRemoveGroup(UserGroup group, Statement stmt, RecordsTransaction trans)
		throws PrismsException
	{
		userLog.info("Group " + group + ", ID " + group.getID() + " deleted");
		String sql = null;
		try
		{
			sql = "UPDATE" + theTransactor.getTablePrefix() + "prisms_user_group SET deleted="
				+ boolToSql(true) + " WHERE id=" + group.getID();
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not delete group: SQL=" + sql, e);
		}
		addModification(trans, PrismsSubjectType.group, null, -1, group, null, null,
			group.getApp(), null);
	}

	void purgeGroup(UserGroup group, Statement stmt) throws PrismsException
	{
		userLog.info("Group " + group + ", ID " + group.getID() + " purged");
		String sql = "DELETE FROM " + theTransactor.getTablePrefix()
			+ "prisms_user_group WHERE id=" + group.getID();
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not purge group: SQL=" + sql, e);
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
		StringBuilder ret = new StringBuilder();
		for(int h = 0; h < hash.length; h++)
			ret.append(hash[h] + (h < hash.length - 1 ? ":" : ""));
		return ret.toString();
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
