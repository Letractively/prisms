/*
 * Column.java Created Apr 8, 2010 by Andrew Butler, PSL
 */
package prisms.osql;

/**
 * Represents a column of data in a table in the database
 * 
 * @param <T> The java type of the column's data
 */
public class Column<T> implements prisms.util.Sorter.Field
{
	private final Table theTable;

	private final String theName;

	private final DataType<T> theDataType;

	private final int theSize;

	private final int theDigits;

	private final Boolean isNullable;

	/**
	 * Creates a column
	 * 
	 * @param table The table that this column belongs to
	 * @param name The name of the column
	 * @param dataType The data type of the column
	 * @param size The maximum size of the column
	 * @param digits The decimal digits in the column (or -1 if not applicable)
	 * @param nullable The nullability of the column
	 */
	public Column(Table table, String name, DataType<T> dataType, int size, int digits,
		Boolean nullable)
	{
		theTable = table;
		theName = name;
		theDataType = dataType;
		theSize = size;
		theDigits = digits;
		isNullable = nullable;
	}

	/** @return The table that this column belongs to */
	public Table getTable()
	{
		return theTable;
	}

	/** @return The name of this column */
	public String getName()
	{
		return theName;
	}

	/** @return The data type of this column */
	public DataType<T> getDataType()
	{
		return theDataType;
	}

	/** @return The maximum size of the data that can be stored in this column */
	public int getSize()
	{
		return theSize;
	}

	/** @return The number of decimal digits on this column's numeric type */
	public int getDecimalDigits()
	{
		return theDigits;
	}

	/**
	 * @return Whether null can be inserted into this column. It is possible that the nullability
	 *         cannot be derived from the database connection, in which case this will be null.
	 */
	public Boolean isNullable()
	{
		return isNullable;
	}

	/**
	 * Prints an SQL representation of this column
	 * 
	 * @param ret The string builder to print the SQL to
	 */
	public void toSQL(StringBuilder ret)
	{
		theTable.toSQL(ret);
		ret.append('.');
		ret.append(theName);
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append(theTable.toString()).append('.').append(theName).append(' ')
			.append(theDataType.getSqlName());
		if(theSize > 0)
		{
			ret.append('(').append(theSize);
			if(theDigits > 0)
				ret.append('.').append(theDigits);
			ret.append(')');
		}
		return ret.toString();
	}

	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof Column))
			return false;
		Column<?> c = (Column<?>) o;
		return c.theTable.equals(theTable) && c.theName.equals(theName);
	}

	@Override
	public int hashCode()
	{
		return theTable.hashCode() * 13 + theName.hashCode();
	}
}
