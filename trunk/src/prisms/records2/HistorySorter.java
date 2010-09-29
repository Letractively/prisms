/*
 * HistorySorter.java Created Nov 13, 2009 by Andrew Butler, PSL
 */
package prisms.records2;

/**
 * Sorts database modifications
 * 
 * @param <T> The type of field in this history sorter
 */
public class HistorySorter<T extends HistorySorter.Field>
{
	/**
	 * A field to sort on
	 */
	public static interface Field
	{
	}

	private T [] theFields;

	private boolean [] theAscendings;

	/**
	 * Creates a sorter
	 */
	public HistorySorter()
	{
		theFields = (T []) new Field [0];
		theAscendings = new boolean [0];
	}

	/**
	 * Adds a field to sort by. Entries will be sorted by the most recently added sort criterion.
	 * Within like categories of that criterion, entries will be sorted according to previously
	 * added criteria.
	 * 
	 * @param field The field to sort by
	 * @param ascending Whether to sort on the field ascending or descending
	 */
	public synchronized void addSort(T field, boolean ascending)
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
		theFields = (T []) new Field [0];
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
	public T getField(int sortIdx)
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
