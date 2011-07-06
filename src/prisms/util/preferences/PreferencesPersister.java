/**
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
import prisms.util.DBUtils;
import prisms.util.DualKey;

/** Persists preferences needed by PRISMS to a database */
public class PreferencesPersister implements
	prisms.util.persisters.UserSpecificPersister<Preferences>
{
	private static final Logger log = Logger.getLogger(PreferencesPersister.class);

	Transactor<SQLException> theTransactor;

	private java.util.concurrent.ConcurrentHashMap<DualKey<PrismsApplication, User>, Preferences> thePrefs;

	public void configure(PrismsConfig config, PrismsEnv env,
		PrismsProperty<? super Preferences> property)
	{
		theTransactor = env.getConnectionFactory().getConnection(config, null, null);
		thePrefs = new java.util.concurrent.ConcurrentHashMap<DualKey<PrismsApplication, User>, Preferences>();
	}

	@SuppressWarnings("rawtypes")
	public Preferences getValue(PrismsSession session)
	{
		DualKey<PrismsApplication, User> key = new DualKey<PrismsApplication, User>(
			session.getApp(), session.getUser());
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
			String sql = "SELECT pDomain, pName, pType, pDisplayed, pValue FROM "
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
					ret.set(new Preference(domain, propName, type, type.getType(), displayed),
						value);
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
				public void domainRemoved(Preferences p, String domain)
				{
					removeDomain(p.getApp(), p.getOwner(), domain);
				}

				public <T, V extends T> void prefChanged(Preferences p, Preference<T> pref, V value)
				{
					setValue(p.getApp(), p.getOwner(), pref, value);
				}
			});
			thePrefs.put(key, ret);
			return ret;
		}
	}

	public <V extends Preferences> void setValue(PrismsSession session, V value,
		PrismsPCE<? extends Preferences> evt)
	{
		// Don't commit the world--this persister persists with each change
	}

	void setValue(final PrismsApplication app, final User user, final Preference<?> pref,
		final Object value)
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
								stmt.execute("INSERT INTO " + theTransactor.getTablePrefix()
									+ "prisms_preference (pApp, pUser, pDomain, pName, pType,"
									+ " pDisplayed, pValue) VALUES ("
									+ DBUtils.toSQL(app.getName()) + ", "
									+ DBUtils.toSQL(user.getName()) + ", "
									+ DBUtils.toSQL(pref.getDomain()) + ", "
									+ DBUtils.toSQL(pref.getName()) + ", "
									+ DBUtils.toSQL(pref.getType().name()) + ", "
									+ DBUtils.boolToSql(pref.isDisplayed()) + ", "
									+ DBUtils.toSQL(valueS) + ")");
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
					return null;
				}
			}, "Could not set preference " + pref);
		} catch(SQLException e)
		{
			log.error("Could not persist preference " + pref + " of user " + user.getName(), e);
		}
	}

	void removeDomain(final PrismsApplication app, final User user, final String domain)
	{
		final String sql = "DELETE FROM " + theTransactor.getTablePrefix()
			+ "prisms_preference WHERE pUser = " + DBUtils.toSQL(user.getName())
			+ " AND pDomain = " + DBUtils.toSQL(domain) + " AND pApp="
			+ DBUtils.toSQL(app.getName());
		try
		{
			theTransactor.performTransaction(new Transactor.TransactionOperation<SQLException>()
			{
				public Object run(Statement stmt) throws SQLException
				{
					stmt.execute(sql);
					return null;
				}
			}, "Could not remove preference domain " + domain);
		} catch(SQLException e)
		{
			log.error(
				"Could not remove preference domain " + domain + " for user " + user.getName()
					+ ": SQL=" + sql, e);
		}
	}
}
