/*
 * PreferencesPersister.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.util.preferences;

import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

import prisms.arch.*;
import prisms.arch.ds.Transactor;
import prisms.arch.ds.User;
import prisms.arch.event.PrismsPCE;
import prisms.arch.event.PrismsProperty;
import prisms.records.PrismsRecordException;
import prisms.util.DBUtils;
import prisms.util.DualKey;

/** Persists preferences needed by PRISMS to a database */
public class PreferencesPersister implements
	prisms.util.persisters.UserSpecificPersister<Preferences>
{
	static final Logger log = Logger.getLogger(PreferencesPersister.class);

	private PrismsEnv theEnv;

	Transactor<SQLException> theTransactor;

	private java.util.concurrent.ConcurrentHashMap<DualKey<PrismsApplication, User>, Preferences> thePrefs;

	prisms.records.ScaledRecordKeeper theScaler;

	public void configure(PrismsConfig config, PrismsEnv env,
		PrismsProperty<? super Preferences> property)
	{
		theEnv = env;
		if(theTransactor == null)
			theTransactor = env.getConnectionFactory().getConnection(config, null, null);
		if(thePrefs == null)
			thePrefs = new java.util.concurrent.ConcurrentHashMap<DualKey<PrismsApplication, User>, Preferences>();
		if(theScaler == null && theTransactor.getConnectionConfig().is("shared", false))
		{
			theScaler = new prisms.records.ScaledRecordKeeper("PRISMS Prefs", config,
				env.getConnectionFactory(), env.getIDs());
			PreferencesScaleImpl impl = new PreferencesScaleImpl();
			impl.setPrefPersister(this);
			theScaler.setPersister(impl);
			theScaler.setScaleImpl(impl);
			prisms.records.AutoPurger purger = new prisms.records.AutoPurger();
			purger.setAge(30L * 60 * 1000);
			try
			{
				theScaler.setAutoPurger(purger, new prisms.records.RecordsTransaction());
			} catch(PrismsRecordException e)
			{
				log.error("Could not set preference auto-purger", e);
			}
			theScaler.setCheckInterval(config.getTime("check-time", 5000));
		}
	}

	/** @return The environment in which this preferences persister is used */
	public PrismsEnv getEnv()
	{
		return theEnv;
	}

	public Preferences getValue(PrismsSession session)
	{
		return getValue(session.getApp(), session.getUser());
	}

	/**
	 * Gets the preferences for a user's view of an application
	 * 
	 * @param app The application to get the preferences for
	 * @param user The user to get the preferences for
	 * @return The Preferences for the user's view of the application
	 */
	@SuppressWarnings("rawtypes")
	public Preferences getValue(PrismsApplication app, User user)
	{
		if(theScaler != null)
		{
			if(((PreferencesScaleImpl) theScaler.getPersister()).addApp(app))
				app.scheduleRecurringTask(new Runnable()
				{
					public void run()
					{
						theScaler.checkChanges(false);
					}

					@Override
					public String toString()
					{
						return "Preferences Scaling Checker";
					}
				}, 2000);
		}
		DualKey<PrismsApplication, User> key = new DualKey<PrismsApplication, User>(app, user);
		Preferences ret = thePrefs.get(key);
		if(ret != null)
			return ret;
		synchronized(this)
		{
			ret = thePrefs.get(key);
			if(ret != null)
				return ret;

			java.sql.Statement stmt = null;
			java.sql.ResultSet rs = null;
			String sql = "SELECT id, pDomain, pName, pType, pDisplayed, pValue FROM "
				+ theTransactor.getTablePrefix() + "prisms_preference WHERE pApp="
				+ DBUtils.toSQL(key.getKey1().getName()) + " AND pUser="
				+ DBUtils.toSQL(key.getKey2().getName());
			ret = new Preferences(key.getKey1(), key.getKey2());
			try
			{
				stmt = theTransactor.getConnection().createStatement();
				rs = stmt.executeQuery(sql);
				while(rs.next())
				{
					String domain = DBUtils.fromSQL(rs.getString("pDomain"));
					String propName = DBUtils.fromSQL(rs.getString("pName"));
					Preference.Type type;
					try
					{
						type = Preference.Type.valueOf(DBUtils.fromSQL(rs.getString("pType")));
					} catch(IllegalArgumentException e)
					{
						log.error("Could not retrieve type of preference " + domain + "/"
							+ propName + " for " + key.getKey1() + "/" + key.getKey2(), e);
						continue;
					}
					boolean displayed = prisms.util.DBUtils.boolFromSql(rs.getString("pDisplayed"));
					String valueS = DBUtils.fromSQL(rs.getString("pValue"));
					Object value;
					try
					{
						value = type.deserialize(valueS);
					} catch(Exception e)
					{
						log.error("Could not deserialize preference " + domain + "/" + propName
							+ " type " + type + " value=" + valueS, e);
						continue;
					}
					Preference pref = new Preference(domain, propName, type, type.getType(),
						displayed);
					pref.setID(rs.getLong("id"));
					ret.set(pref, value);
				}
			} catch(Exception e)
			{
				log.error("Could not retrieve preferences: SQL=" + sql, e);
				return ret;
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
			ret.addListener(new Preferences.Listener()
			{
				public <T> void prefChanged(PreferenceEvent evt)
				{
					if(evt.isWithRecord())
						setValue(evt.getPrefSet().getApp(), evt.getPrefSet().getOwner(),
							evt.getPreference(), evt.getOldValue(), evt.getNewValue());
				}
			});
			thePrefs.put(key, ret);
			return ret;
		}
	}

	/**
	 * @param <T> The type of the preference
	 * @param prefs The preference set that the preference belongs to
	 * @param pref The preference to get the current value of
	 * @return The value of the preference in the database right now
	 */
	public <T> T getDBValue(Preferences prefs, Preference<T> pref)
	{
		java.sql.Statement stmt = null;
		java.sql.ResultSet rs = null;
		String sql = "SELECT pValue FROM " + theTransactor.getTablePrefix()
			+ "prisms_preference WHERE id=" + pref.getID();
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			return (T) pref.getType().deserialize(rs.getString("pValue"));
		} catch(Exception e)
		{
			log.error("Could not retrieve preferences: SQL=" + sql, e);
			return prefs.get(pref);
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

	public <V extends Preferences> void setValue(PrismsSession session, V value,
		PrismsPCE<? extends Preferences> evt)
	{
		// Don't commit the world--this persister persists with each change
	}

	void setValue(final PrismsApplication app, final User user, final Preference<?> pref,
		final Object oldValue, final Object value)
	{
		final String whereClause = "WHERE pUser = " + DBUtils.toSQL(user.getName())
			+ " AND pDomain = " + DBUtils.toSQL(pref.getDomain()) + " AND pName = "
			+ DBUtils.toSQL(pref.getName()) + " AND pApp=" + DBUtils.toSQL(app.getName());
		try
		{
			theTransactor.performTransaction(new Transactor.TransactionOperation<SQLException>()
			{
				public Object run(Statement stmt) throws SQLException
				{
					if(value == null)
						stmt.execute("DELETE FROM " + theTransactor.getTablePrefix()
							+ "prisms_preference " + whereClause);
					else
					{
						java.sql.ResultSet rs = null;
						try
						{
							String valueS = pref.getType().serialize(value);
							rs = stmt.executeQuery("SELECT COUNT(*) FROM "
								+ theTransactor.getTablePrefix() + "prisms_preference "
								+ whereClause);
							if(rs.next() && rs.getInt(1) > 0)
								stmt.executeUpdate("UPDATE " + theTransactor.getTablePrefix()
									+ "prisms_preference SET pValue = " + DBUtils.toSQL(valueS)
									+ " " + whereClause);
							else
							{
								long id;
								try
								{
									id = getEnv().getIDs().getNextID("prisms_preference", "id",
										stmt, theTransactor.getTablePrefix(), null);
								} catch(prisms.arch.PrismsException e)
								{
									throw new IllegalStateException(
										"Could not get next preference ID", e);
								}
								stmt.execute("INSERT INTO " + theTransactor.getTablePrefix()
									+ "prisms_preference (id, pApp, pUser, pDomain, pName, pType,"
									+ " pDisplayed, pValue) VALUES (" + id + ", "
									+ DBUtils.toSQL(app.getName()) + ", "
									+ DBUtils.toSQL(user.getName()) + ", "
									+ DBUtils.toSQL(pref.getDomain()) + ", "
									+ DBUtils.toSQL(pref.getName()) + ", "
									+ DBUtils.toSQL(pref.getType().name()) + ", "
									+ DBUtils.boolToSql(pref.isDisplayed()) + ", "
									+ DBUtils.toSQL(valueS) + ")");
							}
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
					if(theScaler != null)
						try
						{
							theScaler.persist(new prisms.records.RecordsTransaction(user),
								PreferenceSubjectType.Preference,
								PreferenceSubjectType.PreferenceChange.Value, 0, pref, null,
								oldValue, app, user);
						} catch(PrismsRecordException e)
						{
							log.error("Could not persist preference change", e);
						}
					return null;
				}
			}, "Could not set preference " + pref);
		} catch(SQLException e)
		{
			log.error("Could not persist preference " + pref + " of user " + user.getName(), e);
		}
	}
}
