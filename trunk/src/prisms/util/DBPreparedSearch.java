/*
 * DBPreparedSearch.java Created Feb 23, 2011 by Andrew Butler, PSL
 */
package prisms.util;

import java.sql.SQLException;
import java.util.Collection;

import org.apache.log4j.Logger;

import prisms.arch.ds.Transactor;

/**
 * Implements most of the functionality needed for a {@link SearchableAPI.PreparedSearch} for a
 * database implementation
 * 
 * @param <S> The sub-type of search that this implementation knows how to handle
 * @param <F> The type of sorter field that the API can sort on
 * @param <E> The type of exception that the API can throw
 */
public abstract class DBPreparedSearch<S extends Search, F extends Sorter.Field, E extends Throwable>
	extends AbstractPreparedSearch<S, F>
{
	private static final Logger log = Logger.getLogger(DBPreparedSearch.class);

	private final Transactor<E> theTransactor;

	private int theConnectionID;

	private final String theSQL;

	private final int [] theParamTypes;

	private java.sql.PreparedStatement thePS;

	private IntList theTempTypes;

	/**
	 * Creates a databased prepared search
	 * 
	 * @param transactor The transactor to administrate the connection
	 * @param sql The SQL to use to prepare the statement
	 * @param srch The search that this prepared search is for
	 * @param sorter The sorter that this prepared search is for
	 * @param searchType The sub-type of search that this implementation knows how to handle
	 * @throws E If the search cannot be prepared
	 */
	protected DBPreparedSearch(Transactor<E> transactor, String sql, Search srch, Sorter<F> sorter,
		Class<S> searchType) throws E
	{
		super(srch, sorter, searchType);

		theParamTypes = theTempTypes.toArray();
		theTempTypes.clear();
		theTempTypes = null;

		theTransactor = transactor;
		theSQL = sql;
		theConnectionID = -1;
		checkPrepare();
	}

	@Override
	protected Search compileParamTypes(Search search, Collection<Class<?>> types, int limit)
	{
		if(theParamTypes != null)
			return super.compileParamTypes(search, types, limit);
		if(theTempTypes == null)
			theTempTypes = new IntList();
		int oldSize = types.size();
		Search ret = super.compileParamTypes(search, types, limit);
		if(!(search instanceof prisms.util.Search.CompoundSearch) && types.size() > oldSize)
		{
			if(theSearchType.isInstance(search))
				addSqlTypes(theSearchType.cast(search), theTempTypes);
			else if(search != null)
				throw new IllegalArgumentException("Unrecognized search type: "
					+ search.getClass().getName());
		}
		return ret;
	}

	/**
	 * Adds SQL types for the missing parameters in the given search
	 * 
	 * @param search The search to add the missing parameter SQL types of
	 * @param types The list of types to add the missing parameter types to
	 */
	protected abstract void addSqlTypes(S search, IntList types);

	private void checkPrepare() throws E
	{
		int cid = theTransactor.getConnectionID();
		if(cid == theConnectionID)
			return;

		try
		{
			thePS = theTransactor.getConnection().prepareStatement(theSQL);
		} catch(SQLException e)
		{
			theTransactor.getThrower().error("Could not prepare search: SQL=" + theSQL, e);
		}
	}

	/**
	 * Executes the search
	 * 
	 * @param params The parameters to fill in the search
	 * @return The IDs of all items in the API that met this search's criteria
	 * @throws E If the search fails
	 */
	protected synchronized long [] execute(Object... params) throws E
	{
		checkPrepare();
		if(params.length != theParamTypes.length)
			theTransactor.getThrower().error(
				"Prepared search expected " + theParamTypes.length + " parameters, but received "
					+ params.length);
		LongList ret = new LongList();
		java.sql.ResultSet rs = null;
		try
		{
			for(int p = 0; p < params.length; p++)
				setParameter(theParamTypes[p], params[p], p);

			rs = thePS.executeQuery();
			while(rs.next())
				ret.add(rs.getLong(1));
		} catch(SQLException e)
		{
			theTransactor.getThrower().error("Could not execute prepared search: SQL=" + theSQL, e);
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
			try
			{
				thePS.clearParameters();
			} catch(SQLException e)
			{
				log.error("Could not clear prepared statement parameters", e);
			}
		}
		return ret.toArray();
	}

	/**
	 * Releases this search's resources. If this search is used after this method is called, its
	 * resources will be automatically recreated.
	 */
	protected void dispose()
	{
		try
		{
			thePS.close();
		} catch(SQLException e)
		{
			log.error("Connection error", e);
		} catch(Error e)
		{
			// Keep getting these from an HSQL bug--silence
			if(!e.getMessage().contains("compilation"))
				log.error("Error", e);
		}
		thePS = null;
		theConnectionID = -1;
	}

	@Override
	protected void finalize() throws Throwable
	{
		dispose();
		super.finalize();
	}

	/**
	 * Sets a parameter in the prepared statement
	 * 
	 * @param type The SQL type of the parameter
	 * @param param The value of the parameter to set
	 * @param index The index of the parameter to set
	 * @throws E If the parameter cannot be set at the given index, for example, because its type is
	 *         not valid for that index
	 */
	protected void setParameter(int type, Object param, int index) throws E
	{
		try
		{
			if(param == null)
			{
				thePS.setNull(index + 1, type);
				return;
			}
			param = isCompatible(type, param);
			if(param == null)
			{
				theTransactor.getThrower().error(
					"Parameter at index " + index + " is not compatible with data type "
						+ getParameterType(index).getName());
				throw new IllegalStateException("Thrower failed to throw exception");
			}
			index++;
			if(param instanceof Long)
				thePS.setLong(index, ((Long) param).longValue());
			else if(param instanceof Integer)
				thePS.setInt(index, ((Integer) param).intValue());
			else if(param instanceof Short)
				thePS.setShort(index, ((Short) param).shortValue());
			else if(param instanceof Byte)
				thePS.setByte(index, ((Byte) param).byteValue());
			else if(param instanceof Float)
				thePS.setFloat(index, ((Float) param).floatValue());
			else if(param instanceof Double)
				thePS.setDouble(index, ((Double) param).doubleValue());
			else if(param instanceof java.math.BigDecimal)
				thePS.setBigDecimal(index, (java.math.BigDecimal) param);
			else if(param instanceof Boolean)
				thePS.setBoolean(index, ((Boolean) param).booleanValue());
			else if(param instanceof String)
				thePS.setString(index, (String) param);
			else if(param instanceof java.io.InputStream)
				DBUtils.setBlob(thePS, index, type, (java.io.InputStream) param);
			else if(param instanceof java.io.Reader)
				DBUtils.setClob(thePS, index, type, (java.io.Reader) param);
			else if(param instanceof java.sql.Date)
				thePS.setDate(index, (java.sql.Date) param);
			else if(param instanceof java.sql.Timestamp)
			{
				try
				{
					thePS.setTimestamp(index, (java.sql.Timestamp) param);
				} catch(IllegalArgumentException e)
				{
					// Looks like HSQL bug. Try again
					try
					{
						thePS.setTimestamp(index, (java.sql.Timestamp) param);
					} catch(IllegalArgumentException e2)
					{
						// Failed. Throw error
						theTransactor.getThrower().error("Could not set timestamp", e2);
					}
				}
			}
			else
				throw new IllegalArgumentException("Unrecognized parameter type: "
					+ param.getClass());
		} catch(SQLException e)
		{
			theTransactor.getThrower()
				.error(
					"Could not set parameter of SQL type " + type + "(default "
						+ getParameterType(index - 1).getName() + ") with value " + param
						+ " (type " + (param == null ? "null" : param.getClass().getName())
						+ ") at index " + index, e);
		}
	}

	/**
	 * Converts a parameter into a value that can be sent to the database directly
	 * 
	 * @param sqlType The SQL type to convert the parameter to (see {@link java.sql.Types})
	 * @param param The value to convert
	 * @return The converted parameter value, or null if the parameter is invalid for the given SQL
	 *         type
	 */
	public Object isCompatible(int sqlType, Object param)
	{
		switch(sqlType)
		{
		case java.sql.Types.ARRAY:
		case java.sql.Types.DATALINK:
		case java.sql.Types.JAVA_OBJECT:
		case java.sql.Types.STRUCT:
			throw new IllegalStateException("SQL type (" + sqlType + ") not supported");
		case java.sql.Types.BIGINT:
		case java.sql.Types.DECIMAL:
			if(param instanceof java.math.BigDecimal || param instanceof Long
				|| param instanceof Integer)
				return param;
			else if(param instanceof java.math.BigInteger)
				return new java.math.BigDecimal((java.math.BigInteger) param);
			else if(param instanceof Number)
				return Integer.valueOf(((Number) param).intValue());
			else
				return null;
		case java.sql.Types.BINARY:
		case java.sql.Types.BLOB:
		case java.sql.Types.LONGVARBINARY:
		case java.sql.Types.VARBINARY:
			if(param instanceof java.io.InputStream)
				return param;
			else
				return null;
		case java.sql.Types.BIT:
		case java.sql.Types.BOOLEAN:
			if(param instanceof Boolean)
				return param;
			else
				return null;
		case java.sql.Types.CHAR:
			if(param instanceof Boolean)
				return DBUtils.boolToSql(((Boolean) param).booleanValue());
			else if(param instanceof String)
				return param;
			else if(param instanceof Character)
				return "" + ((Character) param).charValue();
			else
				return null;
		case java.sql.Types.VARCHAR:
			if(param instanceof CharSequence)
				return ((CharSequence) param).toString();
			else
				return null;
		case java.sql.Types.CLOB:
		case java.sql.Types.LONGVARCHAR:
			if(param instanceof java.io.Reader)
				return param;
			else
				return null;
		case java.sql.Types.DATE:
		case java.sql.Types.TIME:
			if(param instanceof Long)
				return new java.sql.Date(((Long) param).longValue());
			else if(param instanceof java.util.Date)
				return new java.sql.Date(((java.util.Date) param).getTime());
			else if(param instanceof java.util.Calendar)
				return new java.sql.Date(((java.util.Calendar) param).getTimeInMillis());
			else if(param instanceof java.sql.Date)
				return param;
			else
				return null;
		case java.sql.Types.TIMESTAMP:
			if(param instanceof Long)
				return new java.sql.Timestamp(((Long) param).longValue());
			else if(param instanceof java.util.Date)
				return new java.sql.Timestamp(((java.util.Date) param).getTime());
			else if(param instanceof java.util.Calendar)
				return new java.sql.Timestamp(((java.util.Calendar) param).getTimeInMillis());
			else if(param instanceof java.sql.Timestamp)
				return param;
			else
				return null;
		case java.sql.Types.DOUBLE:
		case java.sql.Types.NUMERIC:
		case java.sql.Types.REAL:
			if(param instanceof Number)
				return param;
			else
				return null;
		case java.sql.Types.FLOAT:
			if(param instanceof Double)
				return Float.valueOf(((Double) param).floatValue());
			else if(param instanceof Number)
				return param;
			else
				return null;
		case java.sql.Types.INTEGER:
			if(param instanceof Integer || param instanceof Short || param instanceof Byte)
				return param;
			else
				return null;
		case java.sql.Types.SMALLINT:
			if(param instanceof Short || param instanceof Byte)
				return param;
			else
				return null;
		case java.sql.Types.TINYINT:
			if(param instanceof Byte)
				return param;
			else
				return null;
		default:
			throw new IllegalStateException("Unrecognized SQL type (" + sqlType + ")");
		}
	}
}
