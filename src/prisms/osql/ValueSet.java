/*
 * ValueSet.java Created Oct 18, 2010 by Andrew Butler, PSL
 */
package prisms.osql;

import prisms.osql.ValueSet.ColumnValue;

/** Represents a set of values for an insert or update, one per column */
public class ValueSet implements Iterable<ColumnValue>
{
	static final Object NULL = new Object();

	final java.util.LinkedHashMap<Column<?>, Object> theValues;

	final boolean isExclusive;

	/**
	 * Creates a value set
	 * 
	 * @param exclusive Whether to throw an exception if a value is requested for a column for which
	 *        a value has not been set
	 */
	public ValueSet(boolean exclusive)
	{
		theValues = new java.util.LinkedHashMap<Column<?>, Object>();
		isExclusive = exclusive;
	}

	/** @return The number of parameters in this value set */
	public int size()
	{
		return theValues.size();
	}

	/** @return All columns that have values set in this value set */
	public Column<?> [] getColumns()
	{
		return theValues.keySet().toArray(new Column [theValues.size()]);
	}

	/**
	 * Sets a value for this value set
	 * 
	 * @param <T> The data type of the column
	 * @param column The column to set the value for
	 * @param value The value for the column
	 * @throws ClassCastException If the given value does not match the given column's data type
	 */
	public <T> void set(Column<T> column, T value)
	{
		if(value != null)
			theValues.put(column, column.getDataType().getJavaType().cast(value));
		else
			theValues.put(column, NULL);
	}

	/**
	 * Removes a value from this value set
	 * 
	 * @param <T> The data type of the column
	 * @param column The column to remove the value for
	 * @return The value previously set for the column
	 */
	public <T> T remove(Column<T> column)
	{
		Object ret = theValues.remove(column);
		if(ret == NULL)
			return null;
		return (T) ret;
	}

	/**
	 * Gets the value set for a column in this value set
	 * 
	 * @param <T> The data type of the column
	 * @param column The column to get the value of
	 * @return The value set for the column in this value set
	 */
	public <T> T get(Column<T> column)
	{
		Object ret = theValues.get(column);
		if(ret == NULL)
			return null;
		else if(ret == null && isExclusive)
			throw new IllegalArgumentException("Column " + column + " not requested in query");
		return (T) ret;
	}

	public java.util.Iterator<ColumnValue> iterator()
	{
		return new java.util.Iterator<ColumnValue>()
		{
			private java.util.Iterator<java.util.Map.Entry<Column<?>, Object>> theValueIter = theValues
				.entrySet().iterator();

			public boolean hasNext()
			{
				return theValueIter.hasNext();
			}

			public ColumnValue next()
			{
				return new ColumnValue(theValueIter.next().getKey());
			}

			public void remove()
			{
				theValueIter.remove();
			}
		};
	}

	/**
	 * @return Whether all the data in this value set can be written to SQL without requiring a
	 *         prepared statement
	 */
	public boolean isStringable()
	{
		for(java.util.Map.Entry<Column<?>, Object> entry : theValues.entrySet())
			if(!((Column<Object>) entry.getKey()).getDataType().isStringable(entry.getValue(),
				(Column<Object>) entry.getKey()))
				return false;
		return true;
	}

	/**
	 * The type of structure that is returned from {@link ValueSet#iterator()}
	 * {@link java.util.Iterator#next() .next()}
	 */
	public class ColumnValue
	{
		private final Column<?> theColumn;

		ColumnValue(Column<?> column)
		{
			theColumn = column;
		}

		/** @return The column that this value is for */
		public Column<?> getColumn()
		{
			return theColumn;
		}

		/** @return The value to be set for the column */
		public Object getValue()
		{
			Object ret = theValues.get(theColumn);
			if(ret == NULL)
				return null;
			else
				return ret;
		}
	}

	/**
	 * Checks whether another column exists in this result set with the same name as the given
	 * column
	 * 
	 * @param c The column to check uniqueness of
	 * @return Whether the column's name is unique in this result set
	 */
	public boolean isNameUnique(Column<?> c)
	{
		for(Column<?> c2 : theValues.keySet())
			if(c2 != c && c2.getName().equalsIgnoreCase(c.getName()))
				return false;
		return true;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		boolean first = true;
		for(ColumnValue cv : this)
		{
			if(!first)
				ret.append(", ");
			first = false;
			if(isNameUnique(cv.getColumn()))
				ret.append(cv.getColumn().getName());
			else
				cv.getColumn().toSQL(ret);
			ret.append('=').append(cv.getValue());
		}
		return ret.toString();
	}
}
