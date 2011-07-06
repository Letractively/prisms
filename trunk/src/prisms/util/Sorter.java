/*
 * Sorter.java Created Nov 13, 2009 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * Sorts data from an API
 * 
 * @param <T> The type of field in this sorter
 */
public class Sorter<T extends Sorter.Field>
{
	/** A field to sort on */
	public static interface Field
	{
	}

	/**
	 * A comparator that sorts items using a sorter
	 * 
	 * @param <I> The type of item to compare
	 * @param <F> The type of field that the sorter is for
	 */
	public static abstract class SorterComparator<I, F extends Sorter.Field> implements
		java.util.Comparator<I>
	{
		private final Sorter<F> theSorter;

		/** @param sorter The sorter to use to sort the items */
		public SorterComparator(Sorter<F> sorter)
		{
			theSorter = sorter;
		}

		public int compare(I o1, I o2)
		{
			for(int s = 0; s < theSorter.getSortCount(); s++)
			{
				int ret = compare(o1, o2, theSorter.getField(s));
				if(ret != 0)
				{
					if(!theSorter.isAscending(s))
						ret = -ret;
					return ret;
				}
			}
			return 0;
		}

		/**
		 * Compares a single field of two items
		 * 
		 * @param o1 The first item to compare
		 * @param o2 The second item to compare
		 * @param field The field to compare the two items on
		 * @return The comparison of the field on the given items
		 */
		public abstract int compare(I o1, I o2, F field);
	}

	private Field [] theFields;

	private boolean [] theAscendings;

	/** Creates a sorter */
	public Sorter()
	{
		theFields = new Field [0];
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
		int idx = ArrayUtils.indexOf(theFields, field);
		if(idx >= 0)
		{
			theFields = ArrayUtils.move(theFields, idx, 0);
			theAscendings = (boolean []) ArrayUtils.moveP(theAscendings, idx, 0);
			theAscendings[0] = ascending;
		}
		else
		{
			theFields = ArrayUtils.add(theFields, field, 0);
			theAscendings = (boolean []) ArrayUtils.addP(theAscendings, Boolean.valueOf(ascending),
				0);
		}
	}

	/** Clears this sorter */
	public synchronized void clear()
	{
		theFields = new Field [0];
		theAscendings = new boolean [0];
	}

	/** @return The number of fields that will be sorted with this sorter */
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
		return (T) theFields[sortIdx];
	}

	/**
	 * @param sortIdx The index of the field to get the ascending value of
	 * @return Whether the field at the given index should be sorted ascending or descending
	 */
	public boolean isAscending(int sortIdx)
	{
		return theAscendings[sortIdx];
	}

	/**
	 * Removes a sort field from this sorter
	 * 
	 * @param sortIdx The index of the field to remove
	 */
	public void removeSort(int sortIdx)
	{
		theFields = ArrayUtils.remove(theFields, sortIdx);
		theAscendings = (boolean []) ArrayUtils.removeP(theAscendings, sortIdx);
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder("ORDER BY ");
		for(int f = 0; f < theFields.length; f++)
		{
			ret.append(theFields[f]);
			if(!theAscendings[f])
				ret.append(" DESC");
			if(f < theFields.length - 1)
				ret.append(", ");
		}
		return ret.toString();
	}
}
