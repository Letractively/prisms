/*
 * DBIDGenerator.java Created Nov 15, 2010 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

import prisms.arch.PrismsException;
import prisms.util.DBUtils;

/** The database implementation of an ID generator */
public class DBIDGenerator extends IDGenerator
{
	private static final Logger log = Logger.getLogger(DBIDGenerator.class);

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
	public DBIDGenerator(java.sql.Connection conn, String prefix) throws PrismsException
	{
		theConn = conn;
		thePrefix = prefix;
		theCenterID = -1;
		doStartup();
	}

	@Override
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

	@Override
	public boolean isOracle()
	{
		if(isOracle == null)
			isOracle = new Boolean(DBUtils.isOracle(theConn));
		return isOracle.booleanValue();
	}

	@Override
	public boolean isNewInstall()
	{
		return isNewInstall;
	}

	@Override
	public boolean belongs(long objectID)
	{
		return getCenterID(objectID) == theCenterID;
	}

	@Override
	public long getInstallDate() throws PrismsException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT centerID, installDate FROM " + thePrefix + "prisms_installation";
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
			throw new PrismsException("Could not query PRISMS installation: SQL=" + sql, e);
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

			sql = "DELETE FROM " + thePrefix + "prisms_auto_increment";
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not install PRISMS: SQL=" + sql, e);
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

	@Override
	public long getNextID(String table, String column, Statement extStmt, String extPrefix,
		String where) throws PrismsException
	{
		boolean closeStmt = true;
		Statement stmt = null;
		try
		{
			if(extStmt != null && extStmt.getConnection() == theConn)
			{
				stmt = extStmt;
				closeStmt = false;
			}
			else
				stmt = theConn.createStatement();
			return getNextID(stmt, table, column, extStmt, extPrefix, where);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not create statement to get next ID", e);
		} finally
		{
			try
			{
				if(closeStmt && stmt != null)
					stmt.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
	}

	@Override
	public synchronized long getNextID(Statement prismsStmt, String table, String column,
		Statement extStmt, String extPrefix, String where) throws PrismsException
	{
		if(extStmt == null)
		{
			extStmt = prismsStmt;
			extPrefix = thePrefix;
		}
		ResultSet rs = null;
		String sql = null;
		try
		{
			final long centerMin = getMinID(theCenterID);
			final long centerMax = getMaxID(theCenterID);

			sql = "SELECT DISTINCT nextID FROM " + thePrefix
				+ "prisms_auto_increment WHERE tableName=" + DBUtils.toSQL(table)
				+ " AND whereClause" + (where == null ? " IS NULL" : "=" + DBUtils.toSQL(where));
			rs = prismsStmt.executeQuery(sql);

			long ret;
			if(rs.next())
				ret = rs.getLong(1);
			else
				ret = -1;
			rs.close();
			rs = null;
			if(ret < centerMin || ret > centerMax)
				ret = -1;
			if(ret < 0)
			{
				sql = "SELECT MAX(" + column + ") FROM " + extPrefix + table + " WHERE " + column
					+ ">=" + centerMin + " AND " + column + " <=" + centerMax;
				if(where != null)
					sql += " AND " + where;
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
				rs.close();
				rs = null;
				if(ret > centerMax)
				{
					/* It may be the case that the maximum ID and a few others adjacent to it are
					 * used as standard values, with a large gap between them and the dynamic ID
					 * set. If this is the case, we'll search for a large gap below the IDs
					 * adjacent to the max ID. */
					sql = "SELECT " + column + " FROM " + extPrefix + table + " WHERE " + column
						+ ">=" + centerMin + " AND " + column + "<=" + centerMax + " ORDER by "
						+ column + " DESC";
					rs = extStmt.executeQuery(sql);
					long next = 0;
					long maxNext = centerMax + 1;
					while(rs.next())
					{
						next = rs.getLong(1);
						maxNext--;
						if(next != maxNext)
							break;
					}
					rs.close();
					rs = null;
					if(next == maxNext) // The higher end is used, but not the lower end
						ret = centerMin;
					else if(maxNext - next < 10) // Not a big enough gap
						throw new PrismsException("All " + table + " ids are used!");
					else
						ret = next;
				}
				// update the db
				sql = "INSERT INTO " + thePrefix + "prisms_auto_increment (tableName, whereClause,"
					+ " nextID) VALUES(" + DBUtils.toSQL(table) + ", " + DBUtils.toSQL(where)
					+ ", " + ret + ")";
				prismsStmt.execute(sql);
			}
			sql = null;
			long nextTry = nextAvailableID(extStmt, extPrefix + table, column, ret + 1, where);
			if(nextTry > centerMax)
				nextTry = nextAvailableID(extStmt, extPrefix + table, column, centerMin, where);
			if(nextTry == ret || nextTry > centerMax)
				throw new PrismsException("All " + table + " ids are used!");

			sql = "UPDATE " + thePrefix + "prisms_auto_increment SET nextID = " + nextTry
				+ " WHERE tableName = " + DBUtils.toSQL(table) + " AND whereClause"
				+ (where == null ? " IS NULL" : "=" + DBUtils.toSQL(where));
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

	private static long nextAvailableID(Statement stmt, String table, String column, long start,
		String where) throws PrismsException
	{
		String sql = "SELECT DISTINCT " + column + " FROM " + table + " WHERE " + column + ">="
			+ start;
		if(where != null)
			sql += " AND " + where;
		sql += " ORDER BY " + column;
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
	 * Gets the maximum length of data for a field
	 * 
	 * @param tableName The name of the table
	 * @param fieldName The name of the field to get the length for
	 * @return The maximum length that data in the given field can be
	 * @throws PrismsException If an error occurs retrieving the information
	 */
	@Override
	public int getFieldSize(String tableName, String fieldName) throws PrismsException
	{
		return getFieldSize(theConn, thePrefix + tableName, fieldName);
	}
}
