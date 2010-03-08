/*
 * HistorySorter.java Created Nov 13, 2009 by Andrew Butler, PSL
 */
package prisms.records;

/**
 * Sorts database modifications
 */
public class HistorySorter
{
	/**
	 * Fields on which the history may be sorted
	 */
	public static enum Field
	{
		/**
		 * Sort on the change time
		 */
		TIME("changeTime"),
		/**
		 * Sort on the change type
		 */
		DOMAIN("changeDomain"),
		/**
		 * Sort on the user that made the change
		 */
		USER("changeUser"),
		/**
		 * Sort on the field that was changed
		 */
		FIELD("field");

		private final String theDBValue;

		Field(String dbValue)
		{
			theDBValue = dbValue;
		}

		public String toString()
		{
			return theDBValue;
		}
	}

	private Field [] theFields;

	private boolean [] theAscendings;

	/**
	 * Creates a sorter
	 */
	public HistorySorter()
	{
		theFields = new Field [0];
		theAscendings = new boolean [0];
	}

	/**
	 * Adds a field to sort by
	 * 
	 * @param field The field to sort by
	 * @param ascending Whether to sort on the field ascending or descending
	 */
	public synchronized void addSort(Field field, boolean ascending)
	{
		int idx = prisms.util.ArrayUtils.indexOf(theFields, field);
		if(idx >= 0)
		{
			theFields = prisms.util.ArrayUtils.move(theFields, idx, 0);
			theAscendings = (boolean []) prisms.util.ArrayUtils.moveP(theAscendings, idx, 0);
			theAscendings[0] = ascending;
		}
		else
		{
			theFields = prisms.util.ArrayUtils.add(theFields, field, 0);
			theAscendings = (boolean []) prisms.util.ArrayUtils.addP(theAscendings, new Boolean(
				ascending), 0);
		}
	}

	/**
	 * Clears this sorter
	 */
	public synchronized void clear()
	{
		theFields = new Field [0];
		theAscendings = new boolean [0];
	}

	/**
	 * @return The number of fields that should be sorted with this sorter
	 */
	public int getSortCount()
	{
		return theFields.length;
	}

	/**
	 * @param sortIdx The index of the field to get
	 * @return The sorted field at the given index
	 */
	public Field getField(int sortIdx)
	{
		return theFields[sortIdx];
	}

	/**
	 * @param sortIdx The index of the field to get the ascending value of
	 * @return Whether the field at the given index should be sorted ascending or descending
	 */
	public boolean isAscending(int sortIdx)
	{
		return theAscendings[sortIdx];
	}
}
