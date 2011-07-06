/*
 * ObjectBag.java Created Jul 23, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/** Stores objects by a type and ID */
public class ObjectBag
{
	private static final class BagKey extends prisms.util.DualKey<String, Long>
	{
		public BagKey(String key1, long key2)
		{
			super(key1, Long.valueOf(key2));
		}

		@Override
		public String toString()
		{
			return getKey1() + "(" + getKey2() + ")";
		}
	}

	private java.util.HashMap<BagKey, Object> theItems;

	/** Creates an ObjectBag */
	public ObjectBag()
	{
		theItems = new java.util.HashMap<BagKey, Object>();
	}

	/**
	 * @param type The type of the object
	 * @param id The identifier of the object
	 * @return Whether this bag contains the given object
	 */
	public boolean contains(String type, long id)
	{
		return theItems.containsKey(new BagKey(type, id));
	}

	/**
	 * @param type The type of the object
	 * @param id The identifier of the object
	 * @return The identified object, if contained in this bag, or null if this bag does not contain
	 *         the object
	 */
	public Object get(String type, long id)
	{
		return theItems.get(new BagKey(type, id));
	}

	/**
	 * Adds an object to the bag
	 * 
	 * @param type The object's type
	 * @param id The object's identifier
	 * @param value The object to store
	 */
	public void add(String type, long id, Object value)
	{
		if(value == null)
			value = this;
		theItems.put(new BagKey(type, id), value);
	}

	/** Clears this cache */
	public void clear()
	{
		theItems.clear();
	}
}
