/**
 * PrismsProperty.java Created Mar 10, 2009 by Andrew Butler, PSL
 */
package prisms.arch.event;

/**
 * Represents a property in the PRISMS architecture. Properties can be retrieved, set, and listened
 * to.
 * 
 * @param <T> The type of the property
 */
public final class PrismsProperty<T>
{
	private static final java.util.Map<String, PrismsProperty<?>> theProperties;

	static
	{
		theProperties = new java.util.HashMap<String, PrismsProperty<?>>();
	}

	private final String theName;

	private final Class<T> theClass;

	private PrismsProperty(String name, Class<T> clazz)
	{
		theName = name;
		theClass = clazz;
	}

	/** @return The name of this property */
	public String getName()
	{
		return theName;
	}

	/** @return The type of this property */
	public Class<? extends T> getType()
	{
		return theClass;
	}

	@Override
	public String toString()
	{
		return theName;
	}

	/**
	 * Creates a new property
	 * 
	 * @param <T> The type of the property
	 * @param name The name of the property
	 * @param type The runtime type of the property
	 * @return A new property with the given name and type
	 */
	public static <T> PrismsProperty<T> create(String name, Class<T> type)
	{
		if(theProperties.containsKey(name))
			throw new IllegalArgumentException("Property " + name + " already exists");
		PrismsProperty<T> ret = new PrismsProperty<T>(name, type);
		theProperties.put(ret.getName(), ret);
		return ret;
	}

	/**
	 * Gets a property
	 * 
	 * @param <T> The type of the property
	 * @param <V> The subtype of the property to get
	 * @param name The name of the property to get
	 * @param type The runtime type of the property to get
	 * @return The pre-created property with the given name and type, if it exists.
	 * @throws IllegalArgumentException If the property does not exist or is not of the given type
	 */
	public static <T, V extends T> PrismsProperty<V> get(String name, Class<T> type)
	{
		PrismsProperty<?> ret = theProperties.get(name);
		if(ret == null)
			throw new IllegalArgumentException("No property named " + name + " has been declared");
		if(!type.isAssignableFrom(ret.getType()))
			throw new IllegalArgumentException("Property named " + name + ", type "
				+ ret.getType().getName() + " is not compatible with type " + type.getName());
		return (PrismsProperty<V>) ret;
	}
}
