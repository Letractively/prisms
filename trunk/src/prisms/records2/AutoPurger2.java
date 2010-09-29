/*
 * AutoPurger.java Created Dec 1, 2009 by Andrew Butler, PSL
 */
package prisms.records2;

import static prisms.util.DBUtils.toSQL;

import java.sql.SQLException;

import org.apache.log4j.Logger;

import prisms.util.ArrayUtils;
import prisms.util.DBUtils;

/**
 * Determines what changes will be purged automatically. This purger uses 4 parameters:
 * <ul>
 * <li><b>Entry Count</b> How many changes this purger will keep in the database. If the number of
 * entries in the data source exceeds this value, the oldest entries NOT matching any exclusions
 * will be purged</li>
 * <li><b>Age</b> How old a change can be. Any entries older than this value and NOT matching any
 * exclusions will be purged</li>
 * <li><b>Excluded Users</b> Changes caused by any of these users will NEVER be purged</li>
 * <li><b>Excluded Types</b> Changes of any of these types will NEVER be purged</li>
 * </ul>
 */
public class AutoPurger2
{
	private static final Logger log = Logger.getLogger(AutoPurger2.class);

	private int theEntryCount;

	private long theAge;

	private RecordUser [] theExcludeUsers;

	private RecordType [] theExcludeTypes;

	/**
	 * Creates an AutoPurger that purges nothing
	 */
	public AutoPurger2()
	{
		theEntryCount = -1;
		theAge = -1;
		theExcludeUsers = new RecordUser [0];
		theExcludeTypes = new RecordType [0];
	}

	/**
	 * @return The maximum number of modifications that this purger will tolerate in the data source
	 */
	public int getEntryCount()
	{
		return theEntryCount;
	}

	/**
	 * @param entryCount The maximum number of modifications that this purger should tolerate in the
	 *        data source before purging
	 */
	public void setEntryCount(int entryCount)
	{
		theEntryCount = entryCount;
	}

	/**
	 * @return The age, in milliseconds at which a modification becomes eligible to be automatically
	 *         purged by this purger
	 */
	public long getAge()
	{
		return theAge;
	}

	/**
	 * @param age The age, in milliseconds at which this purger should purge modifications
	 */
	public void setAge(long age)
	{
		theAge = age;
	}

	/**
	 * @return The users whose modifications (those they cause) will not be purged
	 */
	public RecordUser [] getExcludeUsers()
	{
		return theExcludeUsers;
	}

	/**
	 * Adds a user to this purger, causing that user's modifications (those they cause) to cease
	 * being purged
	 * 
	 * @param user The user to exclude from purge
	 */
	public void addExcludeUser(RecordUser user)
	{
		if(!ArrayUtils.containsP(theExcludeUsers, user))
			theExcludeUsers = ArrayUtils.add(theExcludeUsers, user);
	}

	/**
	 * Removes a user from this purger, causing that user's modifications (those they cause) to
	 * resume being purged
	 * 
	 * @param user The user to include in the purge
	 */
	public void removeExcludeUser(RecordUser user)
	{
		int idx = ArrayUtils.indexOf(theExcludeUsers, user);
		theExcludeUsers = ArrayUtils.remove(theExcludeUsers, idx);
	}

	/**
	 * @return The types of modifications that this purger will not purge
	 */
	public RecordType [] getExcludeTypes()
	{
		return theExcludeTypes;
	}

	/**
	 * @param type Adds a type to those whose modifications will not be purged by this purger
	 */
	public void addExcludeType(RecordType type)
	{
		theExcludeTypes = ArrayUtils.add(theExcludeTypes, type);
	}

	/**
	 * @param type Removes a type from those whose modifications will not be purged by this purger
	 */
	public void removeExcludeType(RecordType type)
	{
		theExcludeTypes = ArrayUtils.remove(theExcludeTypes, type);
	}

	/**
	 * @param rk The record keeper to purge from
	 * @param stmt The statement to use to find which modifications would be purged
	 * @param modTable The name of the table where the modifications are stored
	 * @param timeColumn The name of the column storing the time in the modification table
	 * @param userColumn The name of the column storing the responsible user ID in the modification
	 *        table
	 * @param subjectTypeColumn The name of the column storing the subject type of the modification
	 *        in the table
	 * @param changeTypeColumn The name of the column storing the change type of the modification in
	 *        the table
	 * @param additivityColumn The name of the column storing the additivity of the modification in
	 *        the table
	 * @return The number of rows that would be purged if
	 *         {@link #doPurge(DBRecordKeeper, java.sql.Statement, String, String, String, String, String, String)}
	 *         were called.
	 * @throws PrismsRecordException If an error occurs during the purge
	 */
	public int previewRowsDeleted(DBRecordKeeper rk, java.sql.Statement stmt, String modTable,
		String timeColumn, String userColumn, String subjectTypeColumn, String changeTypeColumn,
		String additivityColumn) throws PrismsRecordException
	{
		return getPurgeIDs(rk, stmt, modTable, timeColumn, userColumn, subjectTypeColumn,
			changeTypeColumn, additivityColumn).length;
	}

	/**
	 * Performs the configured purging operation
	 * 
	 * @param rk The record keeper to purge from
	 * @param stmt The statement to use to purge the undesired modifications
	 * @param modTable The name of the table where the modifications are stored
	 * @param timeColumn The name of the column storing the time in the modification table
	 * @param userColumn The name of the column storing the responsible user ID in the modification
	 *        table
	 * @param subjectTypeColumn The name of the column storing the subject type of the modification
	 *        in the table
	 * @param changeTypeColumn The name of the column storing the change type of the modification in
	 *        the table
	 * @param additivityColumn The name of the column storing the additivity of the modification in
	 *        the table
	 * @throws PrismsRecordException If an error occurs during the purge
	 */
	public void doPurge(DBRecordKeeper rk, java.sql.Statement stmt, String modTable,
		String timeColumn, String userColumn, String subjectTypeColumn, String changeTypeColumn,
		String additivityColumn) throws PrismsRecordException
	{
		long [] ids = getPurgeIDs(rk, stmt, modTable, timeColumn, userColumn, subjectTypeColumn,
			changeTypeColumn, additivityColumn);
		if(ids.length == 0)
			return;
		ChangeRecord [] records = rk.getChanges(ids);
		for(ChangeRecord record : records)
			rk.purge(record, stmt);
	}

	/**
	 * Gets the IDs of modifications to purge
	 * 
	 * @param rk The record keeper to purge from
	 * @param stmt The statement to use to purge the undesired modifications
	 * @param modTable The name of the table where the modifications are stored
	 * @param timeColumn The name of the column storing the time in the modification table
	 * @param userColumn The name of the column storing the responsible user ID in the modification
	 *        table
	 * @param subjectTypeColumn The name of the column storing the subject type of the modification
	 *        in the table
	 * @param changeTypeColumn The name of the column storing the change type of the modification in
	 *        the table
	 * @param additivityColumn The name of the column storing the additivity of the modification in
	 *        the table
	 * @return The IDs of modifications to purge with this AutoPurger
	 * @throws PrismsRecordException If an error occurs selecting the modifications to purge
	 */
	protected long [] getPurgeIDs(DBRecordKeeper rk, java.sql.Statement stmt, String modTable,
		String timeColumn, String userColumn, String subjectTypeColumn, String changeTypeColumn,
		String additivityColumn) throws PrismsRecordException
	{
		if(theAge < 0 && theEntryCount < 0) // Nothing to purge
			return new long [0];
		int totalCount = 0;
		java.sql.ResultSet rs = null;
		String sql;
		if(theEntryCount >= 0)
		{
			sql = "SELECT COUNT(*) FROM " + modTable + " WHERE recordNS="
				+ DBUtils.toSQL(rk.getNamespace());
			try
			{
				rs = stmt.executeQuery(sql);
				if(!rs.next())
					throw new PrismsRecordException("Could not select total change count");
				totalCount = rs.getInt(1);
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not select total change count", e);
			}
			if(totalCount < theEntryCount)
				return new long [0];
		}
		// Create the WHERE clause to exclude our users and types
		String exclude;
		if(theExcludeUsers.length > 0 || theExcludeTypes.length > 0)
		{
			exclude = "";
			if(theExcludeUsers.length > 0)
			{
				exclude += userColumn + " NOT IN (";
				for(int u = 0; u < theExcludeUsers.length; u++)
				{
					exclude += theExcludeUsers[u];
					if(u < theExcludeUsers.length - 1)
						exclude += ", ";
				}
				exclude += ")";
			}
			if(theExcludeTypes.length > 0)
			{
				if(theExcludeUsers.length > 0)
					exclude += " AND (";
				exclude += subjectTypeColumn + ", " + changeTypeColumn + ", " + additivityColumn
					+ ") NOT IN (";
				for(int t = 0; t < theExcludeTypes.length; t++)
				{
					exclude += "(" + toSQL(theExcludeTypes[t].subjectType.name()) + ", ";
					exclude += (theExcludeTypes[t].changeType == null ? "NULL"
						: toSQL(theExcludeTypes[t].changeType.name()));
					exclude += ", "
						+ (theExcludeTypes[t].additivity < 0 ? "-"
							: (theExcludeTypes[t].additivity > 0 ? "+" : "0"));
					exclude += prisms.util.DBUtils.toSQL(theExcludeTypes[t].toString());
					exclude += ")";
					if(t < theExcludeTypes.length - 1)
						exclude += ", ";
				}
				exclude += ")";
			}
		}
		else
			exclude = null;

		long purgeTime = Record2Utils.getPurgeSafeTime(rk.getCenters());
		long age = System.currentTimeMillis() - purgeTime;
		if(theAge > age)
			age = theAge;
		java.util.ArrayList<Long> notSafeIDs = new java.util.ArrayList<Long>();
		// Count deletions by age first
		if(age >= 0)
		{
			sql = "SELECT id FROM " + modTable + " WHERE " + timeColumn + " <= ";
			sql += rk.formatDate(System.currentTimeMillis() - age);
			sql += " AND recordNS=" + DBUtils.toSQL(rk.getNamespace());
			if(exclude != null)
				sql += " AND " + exclude;
			sql += " ORDER BY " + timeColumn + " DESC";
			try
			{
				rs = stmt.executeQuery(sql);
				while(rs.next())
					notSafeIDs.add(new Long(rs.getLong(1)));
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not get auto-purge IDs: SQL=" + sql, e);
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
				rs = null;
			}
			if(theEntryCount >= 0)
				while(notSafeIDs.size() > totalCount - theEntryCount)
					notSafeIDs.remove(notSafeIDs.size() - 1);
		}
		else if(theEntryCount >= 0)
		{
			sql = "SELECT id FROM " + modTable + " WHERE recordNS="
				+ DBUtils.toSQL(rk.getNamespace()) + " ORDER BY " + timeColumn + " DESC";
			try
			{
				rs = stmt.executeQuery(sql);
				int count = 0;
				while(rs.next())
				{
					if(count > theEntryCount)
						notSafeIDs.add(new Long(rs.getLong(1)));
					else
						count++;
				}
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not get auto-purge IDs: SQL=" + sql, e);
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
				rs = null;
			}
		}
		long [] ret = new long [notSafeIDs.size()];
		for(int i = 0; i < ret.length; i++)
			ret[i] = notSafeIDs.get(i).longValue();
		return ret;
	}

	public boolean equals(Object o)
	{
		if(!(o instanceof AutoPurger2))
			return false;
		AutoPurger2 ap = (AutoPurger2) o;
		return ap.theAge == theAge && ap.theEntryCount == theEntryCount
			&& ArrayUtils.equalsUnordered(ap.theExcludeTypes, theExcludeTypes)
			&& ArrayUtils.equalsUnordered(ap.theExcludeUsers, theExcludeUsers);
	}
}
