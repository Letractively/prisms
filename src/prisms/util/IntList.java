/*
 * IntList.java Created Aug 310, 2010 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * <p>
 * Acts like an {@link java.util.ArrayList} but for primitive int values.
 * </p>
 * 
 * <p>
 * This class also has a sorted option that will cause the list to sort itself and maintain the
 * sorted order (ascending). If this option is set, calls to {@link #add(int, int)} will disregard
 * the index parameter to maintain correct order. Calls to {@link #set(int, int)} will behave the
 * same as successive calls to {@link #remove(int)} and {@link #add(int)}.
 * </p>
 * 
 * <p>
 * This class also has a unique option that will cause the list to contain at most one instance of
 * any value. If this option is set, calls to {@link #add(int)}, {@link #add(int, int)},
 * {@link #addAll(int...)}, etc. will not always add the values given if the value(s) already exist.
 * The unique feature has better performance if used with the sorted feature, but both features may
 * be used independently.
 * </p>
 * 
 * <p>
 * This class is NOT thread-safe. If an instance of this class is accessed by multiple threads and
 * may be modified by one or more of them, it MUST be synchronized externally.
 * </p>
 */
public class IntList implements Iterable<Integer>, Cloneable
{
	private int [] theValue;

	private int theSize;

	private boolean isSorted;

	private boolean isUnique;

	private boolean isSealed;

	/** Creates a list with a capacity of 5 */
	public IntList()
	{
		this(5);
	}

	/**
	 * Creates an int list with the option of having the list sorted and/or unique-constrained
	 * initially
	 * 
	 * @param sorted Whether the list should be sorted
	 * @param unique Whether the list should eliminate duplicate values
	 */
	public IntList(boolean sorted, boolean unique)
	{
		this(5);
		isSorted = sorted;
		isUnique = unique;
	}

	/**
	 * Creates a list with a set capacity
	 * 
	 * @param size The initial capacity of the list
	 */
	public IntList(int size)
	{
		theValue = new int [size];
	}

	/**
	 * Creates a list with a set of values
	 * 
	 * @param values The values for the list
	 */
	public IntList(int [] values)
	{
		theValue = values;
		theSize = values.length;
	}

	/** @return Whether the elements in this list are sorted */
	public boolean isSorted()
	{
		return isSorted;
	}

	/**
	 * Sets whether this list should keep itself sorted or not. If set to true, this method will
	 * sort the current value set.
	 * 
	 * @param sorted Whether the elements in this list should be sorted or not
	 */
	public void setSorted(boolean sorted)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(sorted && !isSorted)
			java.util.Arrays.sort(theValue, 0, theSize);
		isSorted = sorted;
	}

	/** @return Whether this list eliminates duplicate values */
	public boolean isUnique()
	{
		return isUnique;
	}

	/**
	 * Sets whether this list should accept duplicate values. If set to true, this method will
	 * eliminate duplicate values that may exist in the current set.
	 * 
	 * @param unique Whether this list should eliminate duplicate values
	 */
	public void setUnique(boolean unique)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(unique && !isUnique)
		{
			if(isSorted)
				for(int i = 0; i < theSize - 1; i++)
					while(i < theSize - 1 && theValue[i + 1] == theValue[i])
						remove(i + 1);
			else
				for(int i = 0; i < theSize - 1; i++)
				{
					int idx = lastIndexOf(theValue[i]);
					while(idx != i)
					{
						remove(idx);
						idx = lastIndexOf(theValue[i]);
					}
				}
		}
		isUnique = unique;
	}

	/** @return The number of elements in the list */
	public int size()
	{
		return theSize;
	}

	/** @return Whether this list is empty of elements */
	public boolean isEmpty()
	{
		return theSize == 0;
	}

	/** Clears this list, setting its size to 0 */
	public void clear()
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		theSize = 0;
	}

	/** Seals this list so that it cannot be modified. This cannot be undone. */
	public void seal()
	{
		trimToSize();
		isSealed = true;
	}

	/**
	 * Gets the value in the list at the given index
	 * 
	 * @param index The index of the value to get
	 * @return The value at the given index
	 */
	public int get(int index)
	{
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		return theValue[index];
	}

	/**
	 * Adds a value to this list.
	 * 
	 * <p>
	 * If this list is sorted, the value will be inserted at the index where it belongs; otherwise
	 * the value will be added to the end of the list.
	 * </p>
	 * 
	 * <p>
	 * If this list is unique, the value will not be added if it already exists in the list
	 * </p>
	 * 
	 * @param value The value to add to the list
	 * @return Whether the value was added. This will only be false if this list is unique and the
	 *         value already exists.
	 */
	public boolean add(int value)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		ensureCapacity(theSize + 1);
		if(isSorted)
		{
			int index = indexFor(value);
			if(isUnique && theValue[index] == value)
				return false;
			for(int i = theSize; i > index; i--)
				theValue[i] = theValue[i - 1];
			theValue[index] = value;
			theSize++;
		}
		else if(!isUnique || indexOf(value) < 0)
			theValue[theSize++] = value;
		return true;
	}

	/**
	 * Performs a binary search to find the location where the given value would belong in this
	 * list. If there already exist more than one instance of the given value, the result will be
	 * the index of one of these, but the exact index of the result is undetermined if more than one
	 * instance exists.
	 * 
	 * @param value The value to find the index for
	 * @return The index at which the given value would be added into this array from an
	 *         {@link #add(int) add} operation.
	 * @throws IllegalStateException If this list is not sorted
	 */
	public int indexFor(int value)
	{
		if(!isSorted)
			throw new IllegalStateException("The indexFor method is only meaningful for a"
				+ " sorted list");
		if(theSize == 0)
			return 0;
		int min = 0, max = theSize - 1;
		while(min < max - 1)
		{
			int mid = (min + max) >>> 1;
			int diff = theValue[mid] - value;
			if(diff > 0)
				max = mid;
			else if(diff < 0)
				min = mid;
			else
				return mid;
		}
		return max;
	}

	/**
	 * Adds a value to this list at the given index.
	 * 
	 * <p>
	 * If this list is sorted, the index parameter will be ignored and the value will be inserted at
	 * the index where it belongs.
	 * </p>
	 * 
	 * <p>
	 * If this list is unique, the value will not be added if it already exists in the list.
	 * <p>
	 * 
	 * @param index The index to add the value at
	 * @param value The value to add to the list
	 * @return Whether the value was added. This will only be false if this list is unique and the
	 *         value already exists.
	 */
	public boolean add(int index, int value)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(isSorted)
			return add(value);
		else if(!isUnique || indexOf(value) < 0)
		{
			if(index < 0 || index > theSize)
				throw new ArrayIndexOutOfBoundsException(index);
			ensureCapacity(theSize + 1);
			for(int i = theSize; i > index; i--)
				theValue[i] = theValue[i - 1];
			theValue[index] = value;
			theSize++;
			return true;
		}
		else
			return false;
	}

	/**
	 * Adds an array of values to the end of this list.
	 * 
	 * <p>
	 * If this list is sorted, all values will be inserted into the indexes where they belong;
	 * otherwise the values will be added to the end of this list.
	 * </p>
	 * 
	 * <p>
	 * If this list is unique, each value will only be added if it does not already exist in the
	 * list, and values that appear multiple times in the given set will be added once.
	 * </p>
	 * 
	 * @param value The values to add
	 * @return The number of values added to this list
	 */
	public int addAll(int... value)
	{
		return addAll(value, 0, value.length);
	}

	/**
	 * Adds all elements of the given array within the given range.
	 * 
	 * <p>
	 * If this list is sorted, all values will be inserted into the indexes where they belong;
	 * otherwise the values will be added to the end of this list.
	 * </p>
	 * 
	 * <p>
	 * If this list is unique, each value will only be added if it does not already exist in the
	 * list, and values that appear multiple times in the given set will be added once.
	 * </p>
	 * 
	 * @param value The array with the values to add
	 * @param start The starting index (inclusive) of the values in the array to add
	 * @param end The end index (exclusive) of the value in the array to add
	 * @return The number of values added to this list
	 */
	public int addAll(int [] value, int start, int end)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(start >= value.length)
			return 0;
		if(end > value.length)
			end = value.length;

		if(isSorted)
		{
			java.util.Arrays.sort(value, start, end);
			int i1 = 0, i2 = start;
			int ret;
			if(isUnique)
			{
				ret = 0;
				while(i2 < end)
				{
					while(i1 < theSize && theValue[i1] < value[i2])
						i1++;
					if(i2 < end && theValue[i1] != value[i2]
						&& (i2 == 0 || value[i2] != value[i2 - 1]))
						ret++;
					i2++;
				}
			}
			else
				ret = end - start;
			if(ret == 0)
				return 0;
			ensureCapacity(theSize + ret);

			i1 = theSize - 1;
			i2 = end - 1;
			int i = theSize + ret - 1;
			theSize += ret;
			int ret2 = ret;
			while(i >= 0 && ret2 > 0 && (i1 >= 0 || i2 >= start))
			{
				if(i1 < 0)
				{
					if(!isUnique || i == theSize - 1 || theValue[i + 1] != value[i2])
					{
						theValue[i--] = value[i2];
						ret2--;
					}
					i2--;
				}
				else if(i2 < start || theValue[i1] >= value[i2])
					theValue[i--] = theValue[i1--];
				else
				{
					if(!isUnique || i == theSize - 1 || theValue[i + 1] != value[i2])
					{
						theValue[i--] = value[i2];
						ret2--;
					}
					i2--;
				}
			}
			return ret;
		}
		else
		{
			ensureCapacity(theSize + end - start);
			for(int i = start; i < end; i++)
			{
				if(!isUnique || !contains(value[i]))
					theValue[theSize + i - start] = value[i];
				else
				{
					i--;
					start++;
				}
			}
			theSize += end - start;
			return end - start;
		}
	}

	/**
	 * Adds a list of values to the end of this list
	 * 
	 * <p>
	 * If this list is sorted, all values will be inserted into the indexes where they belong;
	 * otherwise the values will be added to the end of this list
	 * </p>
	 * 
	 * <p>
	 * If this list is unique, each value will only be added if it does not already exist in the
	 * list, and values that appear multiple times in the given set will be added once.
	 * </p>
	 * 
	 * @param list The list of values to add
	 * @return The number of values added to this list
	 */
	public int addAll(IntList list)
	{
		return addAll(list.theValue, 0, list.theSize);
	}

	/**
	 * <p>
	 * Replaces a value in this list with another value.
	 * </p>
	 * 
	 * <p>
	 * If this list is sorted, the value at the given index will be removed and the new value will
	 * be inserted into the index where it belongs.
	 * </p>
	 * 
	 * <p>
	 * If this list is unique, the value at the given index will be removed and the new value will
	 * replace it ONLY if the value does not exist elsewhere in the list.
	 * </p>
	 * 
	 * @param index The index of the value to replace
	 * @param value The value to replace the old value with
	 * @return The old value at the given index
	 */
	public int set(int index, int value)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		int ret = theValue[index];
		if(isUnique)
		{
			theValue[index] = value + 1;
			if(indexOf(value) >= 0)
			{
				remove(index);
				return ret;
			}
		}
		if(isSorted)
		{
			int newIndex = indexFor(value);
			for(int i = index; i < newIndex; i++)
				theValue[i] = theValue[i + 1]; // If newIndex>index
			for(int i = index; i > newIndex; i--)
				theValue[i] = theValue[i - 1]; // If newIndex<index
			theValue[newIndex] = value;
		}
		else
			theValue[index] = value;
		return ret;
	}

	/**
	 * Removes a value from this list
	 * 
	 * @param index The index of the value to remove
	 * @return The value that was removed
	 */
	public int remove(int index)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(index < 0 || index >= theSize)
			throw new ArrayIndexOutOfBoundsException(index);
		int ret = theValue[index];
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
	public boolean removeValue(int value)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
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
	public int removeAll(int value)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
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
	 * Removes all values in this list that are present in the given list
	 * 
	 * @param list The list whose values to remove from this list
	 * @return The number of elements removed from this list as a result of this call
	 */
	public int removeAll(IntList list)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		int ret = 0;
		for(int i = 0; i < theSize; i++)
			if(list.contains(theValue[i]))
			{
				remove(i);
				i--;
				ret++;
			}
		return ret;
	}

	/**
	 * Switches the positions of two values
	 * 
	 * @param idx1 The index of the first value to switch
	 * @param idx2 The index of the second value to switch
	 */
	public void swap(int idx1, int idx2)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(isSorted)
			throw new IllegalStateException("Cannot perform a move operation on a sorted list");
		if(idx1 < 0 || idx1 >= theSize)
			throw new ArrayIndexOutOfBoundsException(idx1);
		if(idx2 < 0 || idx2 >= theSize)
			throw new ArrayIndexOutOfBoundsException(idx2);
		int temp = theValue[idx1];
		theValue[idx1] = theValue[idx2];
		theValue[idx2] = temp;
	}

	public java.util.ListIterator<Integer> iterator()
	{
		return new IntListIterator(toArray());
	}

	/**
	 * Adds all elements of an array that are not present in this list.
	 * 
	 * <p>
	 * If this list is sorted, the given list will be sorted and each value will be inserted at the
	 * index where it belongs (assuming it is not already present in the list)
	 * </p>
	 * 
	 * @param list The list to add new values from
	 * @return The number of values added to this list
	 */
	public int or(int... list)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(isUnique)
			return addAll(list, 0, list.length);
		else
		{
			isUnique = true;
			try
			{
				return addAll(list, 0, list.length);
			} finally
			{
				isUnique = false;
			}
		}
	}

	/**
	 * Adds all elements of a new list that are not present in this list.
	 * 
	 * <p>
	 * If this list is sorted, the given list will be sorted and each value will be inserted at the
	 * index where it belongs (assuming it is not already present in the list)
	 * </p>
	 * 
	 * @param list The list to add new values from
	 * @return The number of values added to this list
	 */
	public int or(IntList list)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		if(isUnique)
			return addAll(list.theValue, 0, list.theSize);
		else
		{
			isUnique = true;
			try
			{
				return addAll(list.theValue, 0, list.theSize);
			} finally
			{
				isUnique = false;
			}
		}
	}

	/**
	 * Removes all elements of this list that are not present in the given list
	 * 
	 * @param list The list to keep elements from
	 * @return The number of elements removed from this lists
	 */
	public int and(IntList list)
	{
		if(isSealed)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		int ret = 0;
		for(int i = 0; i < theSize; i++)
		{
			int j;
			for(j = 0; j < list.theSize; j++)
				if(theValue[i] == list.theValue[j])
					break;
			if(j == list.theSize)
			{
				remove(i);
				ret++;
			}
		}
		return ret;
	}

	/**
	 * Determines if this list contains a given value
	 * 
	 * @param value The value to find
	 * @return Whether this list contains the given value
	 */
	public boolean contains(int value)
	{
		return indexOf(value) >= 0;
	}

	/**
	 * Counts the number of times a value is represented in this list
	 * 
	 * @param value The value to count
	 * @return The number of times the value appears in this list
	 */
	public int instanceCount(int value)
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
	public int indexOf(int value)
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
	public int lastIndexOf(int value)
	{
		for(int i = theSize - 1; i >= 0; i--)
			if(theValue[i] == value)
				return i;
		return -1;
	}

	/** @return The list of values currently in this list */
	public int [] toArray()
	{
		int [] ret = new int [theSize];
		System.arraycopy(theValue, 0, ret, 0, theSize);
		return ret;
	}

	/**
	 * Similary to {@link #toArray()} but creates an array of {@link Integer} wrappers
	 * 
	 * @return The list of values currently in this list
	 */
	public Integer [] toObjectArray()
	{
		Integer [] ret = new Integer [theSize];
		for(int i = 0; i < ret.length; i++)
			ret[i] = Integer.valueOf(theValue[i]);
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
	public void arrayCopy(int srcPos, int [] dest, int destPos, int length)
	{
		int i = srcPos;
		int j = destPos;
		for(int k = 0; k < length; i++, j++, k++)
			dest[j] = theValue[i];
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append('[');
		for(int i = 0; i < theSize; i++)
		{
			if(i > 0)
				ret.append(", ");
			ret.append(theValue[i]);
		}
		ret.append(']');
		return ret.toString();
	}

	@Override
	public IntList clone()
	{
		IntList ret;
		try
		{
			ret = (IntList) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("Clone not supported", e);
		}
		ret.theValue = theValue.clone();
		ret.isSealed = false;
		return ret;
	}

	/** Trims this list so that it wastes no space and its capacity is equal to its size */
	public void trimToSize()
	{
		if(theValue.length == theSize)
			return;
		int [] oldData = theValue;
		theValue = new int [theSize];
		System.arraycopy(oldData, 0, theValue, 0, theSize);
	}

	/**
	 * Ensures that this list's capacity is at list the given value
	 * 
	 * @param minCapacity The minimum capacity for the list
	 */
	public void ensureCapacity(int minCapacity)
	{
		if(isSealed && minCapacity > theSize)
			throw new IllegalStateException("This list has been sealed and cannot be modified");
		int oldCapacity = theValue.length;
		if(minCapacity > oldCapacity)
		{
			int oldData[] = theValue;
			int newCapacity = (oldCapacity * 3) / 2 + 1;
			if(newCapacity < minCapacity)
				newCapacity = minCapacity;
			theValue = new int [newCapacity];
			System.arraycopy(oldData, 0, theValue, 0, theSize);
		}
	}

	private class IntListIterator implements java.util.ListIterator<Integer>
	{
		private int [] theContent;

		private int theIndex;

		private boolean lastRemoved;

		IntListIterator(int [] content)
		{
			theContent = content;
		}

		public boolean hasNext()
		{
			return theIndex < theContent.length;
		}

		public Integer next()
		{
			Integer ret = Integer.valueOf(theContent[theIndex]);
			theIndex++;
			lastRemoved = false;
			return ret;
		}

		public boolean hasPrevious()
		{
			return theIndex > 0;
		}

		public Integer previous()
		{
			Integer ret = Integer.valueOf(theContent[theIndex - 1]);
			theIndex--;
			lastRemoved = false;
			return ret;
		}

		public int nextIndex()
		{
			return theIndex;
		}

		public int previousIndex()
		{
			return theIndex - 1;
		}

		public void remove()
		{
			if(lastRemoved)
				throw new IllegalStateException(
					"remove() can only be called once with each call to" + " next() or previous()");
			if(get(theIndex) != theContent[theIndex])
				throw new java.util.ConcurrentModificationException(
					"list has been modified apart from this iterator");
			IntList.this.remove(theIndex);
			int [] newContent = new int [theContent.length - 1];
			System.arraycopy(theContent, 0, newContent, 0, theIndex);
			System.arraycopy(theContent, theIndex + 1, newContent, theIndex, newContent.length
				- theIndex);
			theContent = newContent;
			lastRemoved = true;
		}

		public void set(Integer e)
		{
			if(lastRemoved)
				throw new IllegalStateException("set() cannot be called after remove()");
			if(get(theIndex) != theContent[theIndex])
				throw new java.util.ConcurrentModificationException(
					"List has been modified apart from this iterator");
			theContent[theIndex] = e.intValue();
			IntList.this.set(theIndex, e.intValue());
		}

		public void add(Integer e)
		{
			if(get(theIndex) != theContent[theIndex])
				throw new java.util.ConcurrentModificationException(
					"List has been modified apart from this iterator");
			IntList.this.add(theIndex, e.intValue());
			int [] newContent = new int [theContent.length + 1];
			System.arraycopy(theContent, 0, newContent, 0, theIndex);
			System.arraycopy(theContent, theIndex, newContent, theIndex + 1, theContent.length
				- theIndex);
			newContent[theIndex] = e.intValue();
			theIndex++;
			theContent = newContent;
			lastRemoved = false;
		}
	}
}
