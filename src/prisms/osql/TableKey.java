/*
 * PrimaryKey.java Created Apr 8, 2010 by Andrew Butler, PSL
 */
package prisms.osql;

/**
 * A table key represents a set of columns in a table, typically ones with have a constraint on them
 */
public class TableKey implements Iterable<Column<?>>
{
	private Table theTable;

	private String theName;

	private Column<?> [] theColumns;

	TableKey(Table table, String name)
	{
		theTable = table;
		theName = name;
		theColumns = new Column [0];
	}

	/** @return The table whose content is governed by this key */
	public Table getTable()
	{
		return theTable;
	}

	/** @return The name of this foreign key */
	public String getName()
	{
		return theName;
	}

	/** @return The number of columns in this key */
	public int getColumnCount()
	{
		return theColumns.length;
	}

	void addColumn(Column<?> column)
	{
		theColumns = prisms.util.ArrayUtils.add(theColumns, column);
	}

	/**
	 * @param index The index of the column to get
	 * @return The column at the given index in this key
	 */
	public Column<?> getColumn(int index)
	{
		return theColumns[index];
	}

	public java.util.Iterator<Column<?>> iterator()
	{
		return new ColumnIterator(theColumns);
	}

	/**
	 * @param values The set of values to check against this key
	 * @return Whether the set of values matches the datatypes of this key's columns
	 */
	public boolean matches(Object... values)
	{
		if(theColumns.length != values.length)
			return false;
		for(int c = 0; c < theColumns.length; c++)
		{
			if(values[c] != null)
			{
				if(!theColumns[c].getDataType().getJavaType().isInstance(values[c]))
					return false;
			}
			else if(Boolean.FALSE.equals(theColumns[c].isNullable()))
				return false;
		}
		return true;
	}

	@Override
	public String toString()
	{
		StringBuilder msg = new StringBuilder();
		if(theName != null)
		{
			msg.append("table key ");
			msg.append(theName);
		}
		else
			msg.append("default primary key");
		msg.append(" of ");
		msg.append(theTable);
		return msg.toString();
	}

	/** Iterates over a list of columns */
	public static class ColumnIterator implements java.util.Iterator<Column<?>>
	{
		private final Column<?> [] theColumns;

		private int theIndex;

		/** @param cols The columns to iterate over */
		public ColumnIterator(Column<?> [] cols)
		{
			theColumns = cols;
		}

		public boolean hasNext()
		{
			return theIndex < theColumns.length;
		}

		public Column<?> next()
		{
			return theColumns[theIndex++];
		}

		public void remove()
		{
			throw new UnsupportedOperationException(
				"The remove method is not supported by a column iterator");
		}
	}
}
