/*
 * IDGenerator.java Created Oct 26, 2010 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
public abstract class IDGenerator
{
	private static final Logger log = Logger.getLogger(IDGenerator.class);

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

	/** @return The ID of this PRISMS installation */
	public abstract int getCenterID();

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

	/** @return Whether this ID generator's connection is to an oracle database */
	public abstract boolean isOracle();

	/** @return Whether this IDGenerator had to install itself when it was loaded */
	public abstract boolean isNewInstall();

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
	 * @return The date when this set of records was installed
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	public abstract long getInstallDate() throws PrismsException;

	/**
	 * Gets the next ID for the given table within this center and namespace. Like
	 * {@link #getNextID(Statement, String, String, Statement, String, String)} except that this
	 * method creates the statement required for the first argument.
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
	public abstract long getNextID(String table, String column, Statement extStmt,
		String extPrefix, String where) throws PrismsException;

	/**
	 * Gets the next ID for the given table within this center and namespace
	 * 
	 * @param prismsStmt The active statement pointing to the PRISMS records database. Cannot be
	 *        null or closed.
	 * @param table The name of the table to get the next ID for (including any applicable prefix)
	 * @param column The ID column of the table
	 * @param extStmt The active statement pointing to the database where the actual implementation
	 *        data resides. If this is null it will be assumed that the implementation data resides
	 *        in the same database as the PRISMS records data and will use the prismsStmt for
	 *        implementation-specific queries.
	 * @param extPrefix The prefix that should be used to access tables in the external database
	 * @param where The where clause that should be used to get the next ID
	 * @return The next ID that should be used for an entry in the table
	 * @throws PrismsException If an error occurs deriving the data
	 */
	public abstract long getNextID(Statement prismsStmt, String table, String column,
		Statement extStmt, String extPrefix, String where) throws PrismsException;

	/**
	 * Gets the next ID for a table whose value is not dependent on the center
	 * 
	 * @param stmt The statement pointing to the given table
	 * @param tableName The table to get the next ID for. This must be appended with any necessary
	 *        prefixes.
	 * @param column The ID column in the table
	 * @param where The where clause that should be used to get the next ID
	 * @return The next ID to use for an entry in the table
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	public static int getNextIntID(Statement stmt, String tableName, String column, String where)
		throws PrismsException
	{
		int id = 0;
		String sql = "SELECT DISTINCT " + column + " FROM " + tableName + " ORDER BY " + column;
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
			throw new PrismsException("Could not generate next ID: SQL=" + sql, e);
		} finally
		{
			try
			{
				if(rs != null)
					rs.close();
			} catch(SQLException e)
			{
				e.printStackTrace();
				log.error("Connection error", e);
			}
		}
		return id;
	}

	/**
	 * Gets the maximum length of data for a field
	 * 
	 * @param tableName The name of the table
	 * @param fieldName The name of the field to get the length for
	 * @return The maximum length that data in the given field can be
	 * @throws PrismsException If an error occurs retrieving the information
	 */
	public abstract int getFieldSize(String tableName, String fieldName) throws PrismsException;

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
}
