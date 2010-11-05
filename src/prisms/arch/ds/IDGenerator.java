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
 * <b>*</b>When a new PRISMS installation is created there is a 1/{@link #ID_RANGE ID Range}
 * chance that it will clash with another given PRISMS installation. The probability of there being
 * a clash among <code>n</code> PRISMS installations is:
 * 
 * <pre>
 *         {@link #ID_RANGE ID Range}!
 * ----------------------------
 * {@link #ID_RANGE ID Range}^<code>n</code> * ({@link #ID_RANGE ID Range} - <code>n</code>)!
 * </pre>
 * 
 * For values of n much less than the square root of {@link #ID_RANGE ID Range}, the formula
 * <code>n</code>(<code>n</code>-1)/2/ {@link #ID_RANGE ID Range} approximates this well.
 * For perspective, 1,000 installations have a 0.05% chance of a clash; 4,484 installations have a
 * 1% chance; 10,000 have a 5% chance. 100,000 installations have a 0.67% chance of NOT encountering
 * a clash. Practically, coordination with thousands of centers may encounter problems, but for
 * smaller scales, this can be assumed to be safe.
 * </p>
 */
public class IDGenerator
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

	private final java.sql.Connection theConn;

	private final String thePrefix;

	private Boolean isOracle;

	private int theCenterID;

	private boolean isNewInstall;

	/**
	 * Creates an ID generator for a given connection
	 * 
	 * @param conn The connection to generate IDs for
	 * @param prefix The prefix to insert before
	 * @throws PrismsException If the ID generator cannot be configured
	 */
	public IDGenerator(java.sql.Connection conn, String prefix) throws PrismsException
	{
		theConn = conn;
		thePrefix = prefix;
		theCenterID = -1;
		doStartup();
	}

	/** @return The ID of this PRISMS installation */
	public int getCenterID()
	{
		return theCenterID;
	}

	/**
	 * Peforms initial functions to set up this data source
	 * 
	 * @throws PrismsException If an error occurs getting the setup data
	 */
	protected void doStartup() throws PrismsException
	{
		if(getInstallDate() < 0)
		{
			isNewInstall = true;
			theCenterID = (int) (Math.random() * ID_RANGE);
			install();
		}
	}

	/** @return Whether this ID generator's connection is to an oracle database */
	public boolean isOracle()
	{
		if(isOracle == null)
			isOracle = new Boolean(DBUtils.isOracle(theConn));
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
		return getCenterID(objectID) == theCenterID;
	}

	/**
	 * @return The date when this set of records was installed
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	public long getInstallDate() throws PrismsException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT * FROM " + thePrefix + "prisms_installation";
		try
		{
			stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return -1;
			theCenterID = rs.getInt("centerID");
			return rs.getTimestamp("installDate").getTime();
		} catch(SQLException e)
		{
			throw new PrismsException("Could not query PRISMS installation", e);
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
		String sql = "INSERT INTO " + thePrefix
			+ "prisms_installation (centerID, installDate) VALUES (" + theCenterID + ", "
			+ DBUtils.formatDate(System.currentTimeMillis(), isOracle()) + ")";
		try
		{
			stmt = theConn.createStatement();
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not install PRISMS", e);
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
	 * Gets the next ID for the given table within this center and namespace. Like
	 * {@link #getNextID(Statement, String, String, String, Statement)} except that this method
	 * creates the statement required for the first argument.
	 * 
	 * @param table The name of the table to get the next ID for (including any applicable prefix)
	 * @param column The ID column of the table
	 * @param extStmt The active statement pointing to the database where the actual implementation
	 *        data resides. If this is null it will be assumed that the implementation data resides
	 *        in the same database as the PRISMS records data.
	 * @return The next ID that should be used for an entry in the table
	 * @throws PrismsException If an error occurs deriving the data
	 */
	public long getNextID(String table, String column, Statement extStmt) throws PrismsException
	{
		Statement stmt = null;
		try
		{
			stmt = theConn.createStatement();
			return getNextID(stmt, thePrefix, table, column, extStmt);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not create statement to get next ID", e);
		} finally
		{
			try
			{
				if(stmt != null)
					stmt.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
	}

	/**
	 * Gets the next ID for the given table within this center and namespace
	 * 
	 * @param prismsStmt The active statement pointing to the PRISMS records database. Cannot be
	 *        null or closed.
	 * @param prefix The prefix to use before the table name in the SQL
	 * @param table The name of the table to get the next ID for (including any applicable prefix)
	 * @param column The ID column of the table
	 * @param extStmt The active statement pointing to the database where the actual implementation
	 *        data resides. If this is null it will be assumed that the implementation data resides
	 *        in the same database as the PRISMS records data and will use the prismsStmt for
	 *        implementation-specific queries.
	 * @return The next ID that should be used for an entry in the table
	 * @throws PrismsException If an error occurs deriving the data
	 */
	public synchronized long getNextID(Statement prismsStmt, String prefix, String table,
		String column, Statement extStmt) throws PrismsException
	{
		if(extStmt == null)
			extStmt = prismsStmt;
		ResultSet rs = null;
		String sql = null;
		try
		{
			long centerMin = ((long) theCenterID) * ID_RANGE;
			long centerMax = centerMin + ID_RANGE - 1;

			sql = "SELECT DISTINCT nextID FROM " + thePrefix
				+ "prisms_auto_increment WHERE tableName=" + DBUtils.toSQL(table);
			rs = prismsStmt.executeQuery(sql);

			long ret;
			if(rs.next())
				ret = rs.getLong(1);
			else
				ret = -1;
			rs.close();
			if(ret < centerMin || ret > centerMax)
				ret = -1;
			if(ret < 0)
			{
				sql = "SELECT MAX(" + column + ") FROM " + prefix + table + " WHERE " + column
					+ ">=" + centerMin + " AND " + column + " <=" + centerMax;
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
				if(ret > centerMax)
					throw new PrismsException("All " + table + " ids are used!");
				// update the db
				sql = "INSERT INTO " + thePrefix + "prisms_auto_increment (tableName,"
					+ " nextID) VALUES(" + DBUtils.toSQL(table) + ", " + centerMin + ")";
				prismsStmt.execute(sql);
			}
			sql = null;
			long nextTry = nextAvailableID(extStmt, prefix + table, column, ret + 1);
			if(nextTry > centerMax)
				nextTry = nextAvailableID(extStmt, prefix + table, column, centerMin);
			if(nextTry == ret || nextTry > centerMax)
				throw new PrismsException("All " + table + " ids are used!");

			sql = "UPDATE " + thePrefix + "prisms_auto_increment SET nextID = " + nextTry
				+ " WHERE tableName = " + DBUtils.toSQL(table);
			prismsStmt.executeUpdate(sql);
			return ret;
		} catch(SQLException e)
		{
			throw new PrismsException("Could not get next ID: SQL=" + sql, e);
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
	}

	private long nextAvailableID(Statement stmt, String table, String column, long start)
		throws PrismsException
	{
		String sql = "SELECT DISTINCT " + column + " FROM " + table + " WHERE " + column + ">="
			+ start + " ORDER BY " + column;
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
	 * @param tableName The table to get the next ID for
	 * @param column The ID column in the table
	 * @return The next ID to use for an entry in the table
	 * @throws SQLException If an error occurs retrieving the data
	 */
	public static int getNextIntID(Statement stmt, String tableName, String column)
		throws SQLException
	{
		int id = 0;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery("SELECT DISTINCT " + column + " FROM " + tableName
				+ " ORDER BY " + column);
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
	public int getFieldSize(String tableName, String fieldName) throws PrismsException
	{
		return getFieldSize(theConn, thePrefix + tableName, fieldName);
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
}
