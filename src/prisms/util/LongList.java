/*
 * LongList.java Created Aug 3, 2010 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * Acts like an {@link java.util.ArrayList} but for primitive long values
 */
public class LongList
{
	private long [] theValue;

	private int theSize;

	/**
	 * Creates a list with a capacity of 5
	 */
	public LongList()
	{
		this(5);
	}

	/**
	 * Creates a list with a set capacity
	 * 
	 * @param size The initial capacity of the list
	 */
	public LongList(int size)
	{
		theValue = new long [size];
	}

	/**
	 * Creates a list with a set of values
	 * 
	 * @param values The values for the list
	 */
	public LongList(long [] values)
	{
		theValue = values;
		theSize = values.length;
	}

	/**
	 * @return The number of elements in the list
	 */
	public int size()
	{
		return theSize;
	}

	/**
	 * @return Whether this list is empty of elements
	 */
	public boolean isEmpty()
	{
		return theSize == 0;
	}

	/**
	 * Clears this list, setting its size to 0
	 */
	public void clear()
	{
		theSize = 0;
	}

	/**
	 * Gets the value in the list at the given index
	 * 
	 * @param index The index of the value to get
	 * @return The value at the given index
	 */
	public long get(int index)
	{
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		return theValue[index];
	}

	/**
	 * Adds a value to the end of this list
	 * 
	 * @param value The value to add to the list
	 */
	public void add(long value)
	{
		ensureCapacity(theSize + 1);
		theValue[theSize++] = value;
	}

	/**
	 * Adds a value to this list at the given index
	 * 
	 * @param index The index to add the value at
	 * @param value The value to add to the list
	 */
	public void add(int index, long value)
	{
		if(index < 0 || index > theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		ensureCapacity(theSize + 1);
		for(int i = theSize; i > index; i--)
			theValue[i] = theValue[i - 1];
		theValue[index] = value;
		theSize++;
	}

	/**
	 * Adds an array of values to the end of this list
	 * 
	 * @param value The values to add
	 */
	public void addAll(long [] value)
	{
		ensureCapacity(theSize + value.length);
		for(int i = 0; i < value.length; i++)
			theValue[theSize + i] = value[i];
		theSize += value.length;
	}

	/**
	 * Adds a list of values to the end of this list
	 * 
	 * @param list The list of values to add
	 */
	public void addAll(LongList list)
	{
		ensureCapacity(theSize + list.theSize);
		for(int i = 0; i < list.theSize; i++)
			theValue[theSize + i] = list.theValue[i];
		theSize += list.theSize;
	}

	/**
	 * Replaces a value in this list with another value
	 * 
	 * @param index The index of the value to replace
	 * @param value The value to replace the old value with
	 * @return The old value at the given index
	 */
	public long set(int index, long value)
	{
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		long ret = theValue[index];
		theValue[index] = value;
		return ret;
	}

	/**
	 * Removes a value from this list
	 * 
	 * @param index The index of the value to remove
	 * @return The value that was removed
	 */
	public long remove(int index)
	{
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		long ret = theValue[index];
		for(int i = index; i < theSize - 1; i++)
			theValue[i] = theValue[i + 1];
		theSize--;
		return ret;
	}

	/**
	 * Removes a value from this list
	 * 
	 * @param value The value to remove
	 * @return Whether the value was found and removed
	 */
	public boolean remove(long value)
	{
		for(int i = 0; i < theSize; i++)
			if(theValue[i] == value)
			{
				remove(i);
				return true;
			}
		return false;
	}

	/**
	 * Removes all instances of the given value from this list
	 * 
	 * @param value The value to remove
	 * @return The number of times the value was removed
	 */
	public int removeAll(long value)
	{
		int ret = 0;
		for(int i = 0; i < theSize; i++)
			if(theValue[i] == value)
			{
				remove(i);
				i--;
				ret++;
			}
		return ret;
	}

	/**
	 * Adds all elements of a new list that are not present in this list
	 * 
	 * @param list The list to add new values from
	 */
	public void or(LongList list)
	{
		int size = theSize;
		for(int i = 0; i < list.size(); i++)
		{
			long value = list.get(i);
			int j;
			for(j = 0; j < size; j++)
				if(theValue[j] == value)
					break;
			if(j == size)
				add(value);
		}
	}

	/**
	 * Removes all elements of this list that are not present in the given list
	 * 
	 * @param list The list to keep elements from
	 */
	public void and(LongList list)
	{
		for(int i = 0; i < theSize; i++)
		{
			int j;
			for(j = 0; j < list.theSize; j++)
				if(theValue[i] == list.theValue[j])
					break;
			if(j == list.theSize)
				remove(i);
		}
	}

	/**
	 * Determines if this list contains a given value
	 * 
	 * @param value The value to find
	 * @return Whether this list contains the given value
	 */
	public boolean contains(long value)
	{
		return indexOf(value) >= 0;
	}

	/**
	 * Counts the number of times a value is represented in this list
	 * 
	 * @param value The value to count
	 * @return The number of times the value appears in this list
	 */
	public int instanceCount(long value)
	{
		int ret = 0;
		for(int i = 0; i < theSize; i++)
			if(theValue[i] == value)
				ret++;
		return ret;
	}

	/**
	 * Finds a value in this list
	 * 
	 * @param value The value to find
	 * @return The first index whose value is the given value
	 */
	public int indexOf(long value)
	{
		for(int i = 0; i < theSize; i++)
			if(theValue[i] == value)
				return i;
		return -1;
	}

	/**
	 * Finds a value in this list
	 * 
	 * @param value The value to find
	 * @return The last index whose value is the given value
	 */
	public int lastIndexOf(long value)
	{
		for(int i = theSize - 1; i >= 0; i--)
			if(theValue[i] == value)
				return i;
		return -1;
	}

	/**
	 * @return The list of values currently in this list
	 */
	public long [] toArray()
	{
		long [] ret = new long [theSize];
		System.arraycopy(theValue, 0, ret, 0, theSize);
		return ret;
	}

	/**
	 * Similary to {@link #toArray()} but creates an array of {@link Long} wrappers
	 * 
	 * @return The list of values currently in this list
	 */
	public Long [] toObjectArray()
	{
		Long [] ret = new Long [theSize];
		for(int i = 0; i < ret.length; i++)
			ret[i] = new Long(theValue[i]);
		return ret;
	}

	/**
	 * Copies a subset of this list's data into an array
	 * 
	 * @param srcPos The index in this list to start copying from
	 * @param dest The array to copy the data into
	 * @param destPos The index in the destination array to start copying to
	 * @param length The number of items to copy
	 */
	public void arrayCopy(int srcPos, long [] dest, int destPos, int length)
	{
		int i = srcPos;
		int j = destPos;
		for(int k = 0; k < length; i++, j++, k++)
			dest[j] = theValue[i];
	}

	/**
	 * Trims this list so that it wastes no space and its capacity is equal to its size
	 */
	public void trimToSize()
	{
		if(theValue.length == theSize)
			return;
		long [] oldData = theValue;
		theValue = new long [theSize];
		System.arraycopy(oldData, 0, theValue, 0, theSize);
	}

	/**
	 * Ensures that this list's capacity is at list the given value
	 * 
	 * @param minCapacity The minimum capacity for the list
	 */
	public void ensureCapacity(int minCapacity)
	{
		int oldCapacity = theValue.length;
		if(minCapacity > oldCapacity)
		{
			long oldData[] = theValue;
			int newCapacity = (oldCapacity * 3) / 2 + 1;
			if(newCapacity < minCapacity)
				newCapacity = minCapacity;
			theValue = new long [newCapacity];
			System.arraycopy(oldData, 0, theValue, 0, theSize);
		}
	}
}
