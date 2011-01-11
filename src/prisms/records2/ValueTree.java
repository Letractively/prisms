/*
 * LCCInput.java Created Dec 1, 2010 by Andrew Butler, PSL
 */
package prisms.records2;

import prisms.util.ArrayUtils;

/**
 * Simply holds any kind of data in a hierarchy
 * 
 * @param <T> The type of data this tree holds
 */
public class ValueTree<T>
{
	/**
	 * Facilitates conversion from one kind of value tree to another
	 * 
	 * @param <T> The type of value tree to convert
	 * @param <C> The type of value tree to create
	 */
	public interface Converter<T, C>
	{
		/**
		 * Converts a value from one type to another
		 * 
		 * @param value The value to convert
		 * @return The converted value
		 */
		C convert(T value);
	}

	private T theValue;

	private ValueTree<T> [] theChildren;

	/** Creates an empty tree */
	public ValueTree()
	{
		this(null);
	}

	/**
	 * Creates a tree with an initial value
	 * 
	 * @param value The initial value for the tree
	 */
	public ValueTree(T value)
	{
		theValue = value;
		theChildren = new ValueTree [0];
	}

	/** @return The root value of the tree */
	public T getValue()
	{
		return theValue;
	}

	/** @param value The root value for the tree */
	public void setValue(T value)
	{
		theValue = value;
	}

	/** @return This tree's children */
	public ValueTree<T> [] getChildren()
	{
		return theChildren;
	}

	/** @param child The tree to add as a child to this tree */
	public void addChild(ValueTree<T> child)
	{
		theChildren = ArrayUtils.add(theChildren, child);
	}

	/**
	 * Converts this tree to another of the same type
	 * 
	 * @param <C> The type of the tree to create
	 * @param converter The converter to convert values for the tree
	 * @return The new tree
	 */
	public <C> ValueTree<C> convert(Converter<T, C> converter)
	{
		ValueTree<C> ret = new ValueTree<C>();
		ret.theValue = converter.convert(theValue);
		ret.theChildren = new ValueTree [theChildren.length];
		for(int c = 0; c < theChildren.length; c++)
			ret.theChildren[c] = theChildren[c].convert(converter);
		return ret;
	}
}
