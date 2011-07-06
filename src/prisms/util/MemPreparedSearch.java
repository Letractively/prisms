/*
 * MemPreparedSearch.java Created Mar 1, 2011 by Andrew Butler, PSL
 */
package prisms.util;

import java.util.BitSet;

/**
 * Implements most of the functionality needed for a {@link SearchableAPI.PreparedSearch} for an
 * in-memory implementation
 * 
 * @param <T> The type of item that this search is for
 * @param <S> The sub-type of search that this implementation knows how to handle
 * @param <F> The type of sorter field that the API can sort on
 */
public abstract class MemPreparedSearch<T, S extends Search, F extends Sorter.Field> extends
	AbstractPreparedSearch<S, F>
{
	private final java.util.Comparator<T> theCompare;

	/** Allows implementations to carry state over a search */
	public static interface MatchState extends Cloneable
	{
		/** @return An independent copy of this state */
		MatchState clone();

		boolean equals(Object o);
	}

	/**
	 * Creates a prepared search
	 * 
	 * @param search The search to prepare
	 * @param sorter The sorter to sort the results after search
	 * @param searchType The sub-type of search that this implementation knows how to handle
	 */
	public MemPreparedSearch(Search search, Sorter<F> sorter, Class<S> searchType)
	{
		super(search, sorter, searchType);
		theCompare = new Sorter.SorterComparator<T, F>(sorter)
		{
			@Override
			public int compare(T o1, T o2, F field)
			{
				return MemPreparedSearch.this.compare(o1, o2, field);
			}
		};
	}

	/**
	 * Executes this search on a set of items
	 * 
	 * @param allItems All available items to search on
	 * @param params The parameters to fill in the missing values in the search
	 * @return All items that match the given search with the given parameters
	 */
	public T [] execute(T [] allItems, Object... params)
	{
		if(params.length != getParameterCount())
			throw new IllegalArgumentException("Expected " + getParameterCount()
				+ " parameters but received " + params.length);
		BitSet matches = new BitSet();
		matches.flip(0, allItems.length);
		matches = matches(allItems, matches, getSearch(), createState(), params,
			new java.util.ArrayList<Class<?>>());
		T [] ret = (T []) java.lang.reflect.Array.newInstance(allItems.getClass()
			.getComponentType(), matches.cardinality());
		int t = 0;
		for(int i = matches.nextSetBit(0); i >= 0; i = matches.nextSetBit(i + 1))
			ret[t++] = allItems[i];
		java.util.Arrays.sort(ret, theCompare);
		return ret;
	}

	/**
	 * Checks a set of items for matches on a search
	 * 
	 * @param items The full set of items
	 * @param filter The filter to determine which items in the set to test
	 * @param search The search to test against the items
	 * @param state The implementation's search state
	 * @param params The params passed to {@link #execute(Object[], Object...)}
	 * @param types A list of types needed for internal processing
	 * @return Whether the given item matches the given search
	 */
	protected BitSet matches(T [] items, BitSet filter, Search search, MatchState state,
		Object [] params, java.util.Collection<Class<?>> types)
	{
		if(search == null)
			return filter;
		if(search instanceof Search.NotSearch)
		{
			BitSet ret = new BitSet();
			ret.and(filter);
			matches(items, ret, ((Search.NotSearch) search).getOperand(), state, params, types);
			ret.xor(filter);
			return ret;
		}
		else if(search instanceof Search.ExpressionSearch)
		{
			Search.ExpressionSearch exp = (Search.ExpressionSearch) search;
			if(exp.getOperandCount() == 0)
				return filter;
			else if(exp.getOperandCount() == 1)
			{
				filter = matches(items, filter, exp.getOperand(0), state, params, types);
				return filter;
			}
			if(exp.and)
			{
				for(Search op : exp)
				{
					if(filter.isEmpty())
						break;
					filter = matches(items, filter, op, state, params, types);
				}
				return filter;
			}
			else
			{
				MatchState backup = state.clone();
				BitSet ret = matches(items, filter, exp.getOperand(0), state, params, types);
				int filterCar = filter.cardinality();
				for(int i = 1; i < exp.getOperandCount(); i++)
				{
					if(ret.cardinality() == filterCar)
						break;
					if(!state.equals(backup))
					{
						if(i == exp.getOperandCount() - 1)
							state = backup;
						else
							state = backup.clone();
					}
					ret.or(matches(items, filter, exp.getOperand(i), state, params, types));
				}
				return ret;
			}
		}
		else if(theSearchType.isInstance(search))
		{
			S srch = theSearchType.cast(search);
			int oldSize = types.size();
			addParamTypes(srch, types);
			Object [] subParams = new Object [types.size() - oldSize];
			System.arraycopy(params, oldSize, subParams, 0, subParams.length);
			return matches(items, filter, srch, state, subParams);
		}
		else
			throw new IllegalArgumentException("Unrecognized search type: "
				+ search.getClass().getName());
	}

	/** @return A customized search state for a new search */
	protected abstract MatchState createState();

	/**
	 * Tests a set of items against a search
	 * 
	 * @param items The full set of items
	 * @param filter The filter determining which items to test
	 * @param search The search to test against the item
	 * @param state The match state for the test
	 * @param params The parameters for the missing values in the given search
	 * @return Whether the given item matches the search
	 */
	protected abstract BitSet matches(T [] items, BitSet filter, S search, MatchState state,
		Object [] params);

	/**
	 * Compares a single field of two items
	 * 
	 * @param o1 The first item to compare
	 * @param o2 The second item to compare
	 * @param field The field to compare the two items on
	 * @return The comparison of the field on the given items
	 */
	public abstract int compare(T o1, T o2, F field);
}
