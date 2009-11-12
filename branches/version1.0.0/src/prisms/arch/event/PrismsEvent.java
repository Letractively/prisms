/**
 * PrismsEvent.java Created Aug 1, 2007 by Andrew Butler, PSL
 */
package prisms.arch.event;

/**
 * A PrismsEvent may be fired using a PluginAppSession to communicate between plugins
 */
public class PrismsEvent
{
	/**
	 * The name of this event
	 */
	public final String name;

	private java.util.HashMap<String, Object> theProperties;

	/**
	 * Creates a PrismsEvent with no properties. This is the same as calling either of the other
	 * constructors using a null properties argument. The reason for this constructor is that
	 * <code>PrismsEvent("name", null)</code> is ambiguous.
	 * 
	 * @param aName The name for the event
	 */
	public PrismsEvent(String aName)
	{
		this(aName, (Object []) null);
	}

	/**
	 * Creates a PrismsEvent using a name and a map of properties
	 * 
	 * @param aName The name for the event
	 * @param evtProps The map of properties for this event
	 */
	public PrismsEvent(String aName, java.util.Map<String, Object> evtProps)
	{
		name = aName.intern();
		if(evtProps == null || evtProps.isEmpty())
			theProperties = null;
		else
		{
			theProperties = new java.util.HashMap<String, Object>();
			theProperties.putAll(evtProps);
		}
	}

	/**
	 * Creates a PrismsEvent using a name and an alternating array of string, object, string,
	 * object... to initialize the properties
	 * 
	 * @param aName The name for the event
	 * @param evtProps An array whose elements alternate string, object, string, object... This sets
	 *        the properties of the event in a way that is much easier to write inline
	 */
	public PrismsEvent(String aName, Object... evtProps)
	{
		name = aName;
		if(evtProps == null || evtProps.length == 0)
			return;
		if(evtProps.length % 2 != 0)
			throw new IllegalArgumentException("An even number of arguments are required, not "
				+ evtProps.length);
		theProperties = new java.util.HashMap<String, Object>();
		for(int i = 0; i < evtProps.length; i += 2)
		{
			if(!(evtProps[i] instanceof String))
				throw new IllegalArgumentException("Every other object must be a string, not "
					+ evtProps[i]);
			theProperties.put((String) evtProps[i], evtProps[i + 1]);
		}
	}

	/**
	 * @param propName The name of the property to get
	 * @return The value of the property registered to this event with the given name
	 */
	public Object getProperty(String propName)
	{
		if(theProperties == null)
			return null;
		else
			return theProperties.get(propName);
	}

	/**
	 * @param propName The name of the property to set
	 * @param propValue The value of the property to set for the given name in this event
	 */
	public void setProperty(String propName, Object propValue)
	{
		if(theProperties == null && propValue != null)
			theProperties = new java.util.HashMap<String, Object>();
		if(theProperties != null)
			theProperties.put(propName, propValue);
	}
}
