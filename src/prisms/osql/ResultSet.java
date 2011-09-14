/*
 * ResultSet.java Created Sep 15, 2010 by Andrew Butler, PSL
 */
package prisms.osql;

import java.sql.SQLException;

import org.apache.log4j.Logger;

/** Presents a more natural API to database query results */
public class ResultSet implements Iterable<ValueSet>
{
	static final Logger log = Logger.getLogger(ResultSet.class);

	private final Connection theConn;

	private java.sql.ResultSet theRS;

	private final Column<?> [] theColumns;

	private int theRowIndex;

	ResultSet(Connection conn, java.sql.ResultSet rs, Column<?> [] columns)
	{
		theConn = conn;
		theRS = rs;
		theColumns = columns;
		theRowIndex = -1;
	}

	/** @return The OSQL connection that generated these results */
	public Connection getConnection()
	{
		return theConn;
	}

	/** @return The JDBC result set that this result set wraps */
	public java.sql.ResultSet getRS()
	{
		return theRS;
	}

	/**
	 * @param column The column to test
	 * @return Whether the query that generated this result set included the given column
	 */
	public boolean has(Column<?> column)
	{
		return prisms.util.ArrayUtils.contains(theColumns, column);
	}

	/**
	 * @return All results (after the current cursor position) returned by the query
	 * @throws PrismsSqlException If the results cannot be retrieved or parsed
	 */
	public ValueSet [] getAll() throws PrismsSqlException
	{
		java.util.ArrayList<ValueSet> ret = new java.util.ArrayList<ValueSet>();
		while(next())
			ret.add(getRow());
		return ret.toArray(new ValueSet [ret.size()]);
	}

	/**
	 * @return Whether there is another row after the current row
	 * @throws PrismsSqlException If the information cannot be retrieved
	 */
	public boolean hasNext() throws PrismsSqlException
	{
		if(theRS == null)
			return false;
		try
		{
			return !theRS.isLast();
		} catch(SQLException e)
		{
			throw new PrismsSqlException("Could not check cursor position", e);
		}
	}

	/**
	 * Moves the cursor to the next available row
	 * 
	 * @return Whether there is a next row to move to
	 * @throws PrismsSqlException If an error occurs moving the cursor
	 */
	public boolean next() throws PrismsSqlException
	{
		if(theRS == null)
			return false;
		boolean ret;
		try
		{
			ret = theRS.next();
		} catch(SQLException e)
		{
			throw new PrismsSqlException(e.getMessage(), e);
		}
		theRowIndex++;
		return ret;
	}

	/**
	 * Moves the cursor to the previous row
	 * 
	 * @return Whether there is a previous row to move to
	 * @throws PrismsSqlException If an error occurs moving the cursor
	 */
	public boolean previous() throws PrismsSqlException
	{
		if(theRS == null)
			return false;
		try
		{
			if(!theRS.previous())
				return false;
		} catch(SQLException e)
		{
			throw new PrismsSqlException("Could not move cursor", e);
		}
		theRowIndex--;
		return true;
	}

	/** @return The index of the cursor's current row */
	public int rowIndex()
	{
		return theRowIndex;
	}

	/**
	 * Gets the value for a column in the current row
	 * 
	 * @param <T> The type of the column
	 * @param column The column to get the result for
	 * @return The query results for the given column on the current row
	 * @throws PrismsSqlException If the information cannot be retrieved or parsed
	 */
	public <T> T get(Column<T> column) throws PrismsSqlException
	{
		if(theRS == null)
			throw new PrismsSqlException("Result set has been closed");
		return column.getDataType().get(theRS, column.getName(), column);
	}

	/**
	 * @return All data on the current row
	 * @throws PrismsSqlException If the information cannot retrieved or parsed
	 */
	public ValueSet getRow() throws PrismsSqlException
	{
		if(theRS == null)
			throw new PrismsSqlException("Result set has been closed");
		ValueSet ret = new ValueSet(true);
		for(int c = 0; c < theColumns.length; c++)
		{
			Column<Object> column = (Column<Object>) theColumns[c];
			ret.set(column, column.getDataType().get(theRS, column.getName(), column));
		}
		return ret;
	}

	/**
	 * @return An iterator that iterates over this result set's rows (after the current cursor
	 *         position)
	 */
	public java.util.Iterator<ValueSet> iterator()
	{
		return new java.util.Iterator<ValueSet>()
		{
			public boolean hasNext()
			{
				try
				{
					return ResultSet.this.hasNext();
				} catch(PrismsSqlException e)
				{
					throw new IllegalStateException(e.getMessage(), e);
				}
			}

			public ValueSet next()
			{
				if(isClosed())
					throw new IllegalStateException("Result set has been closed");
				try
				{
					ResultSet.this.next();
					return getRow();
				} catch(PrismsSqlException e)
				{
					throw new IllegalStateException(e.getMessage(), e);
				} finally
				{
					if(!hasNext())
						try
						{
							close();
						} catch(PrismsSqlException e)
						{
							log.error("Connection error", e);
						}
				}
			}

			public void remove()
			{
				throw new UnsupportedOperationException(
					"Remove is not supported by a result set iterator");
			}
		};
	}

	/** @return Whether this result set has been closed */
	public boolean isClosed()
	{
		return theRS == null;
	}

	/**
	 * Closes this result set, disposing of all resources
	 * 
	 * @throws PrismsSqlException If an error occurs closing the result set
	 */
	public void close() throws PrismsSqlException
	{
		try
		{
			theRS.close();
		} catch(java.sql.SQLException e)
		{
			throw new PrismsSqlException(e.getMessage(), e);
		} finally
		{
			theRS = null;
		}
	}
}
