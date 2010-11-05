/**
 * PrismsPCE.java Created Mar 10, 2009 by Andrew Butler, PSL
 */
package prisms.arch.event;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.util.ArrayUtils;

/**
 * A substitute for {@link java.beans.PropertyChangeEvent} that represents a change to a typed
 * property
 * 
 * @param <T> The type of the property that this event represents a change to
 */
public class PrismsPCE<T>
{
	private final PrismsApplication theApp;

	private final PrismsSession theSession;

	private final PrismsProperty<T> theProperty;

	private final T theOldValue;

	private final T theNewValue;

	private String [] thePropertyNames;

	private Object [] thePropertyValues;

	/**
	 * Creates a property change event
	 * 
	 * @param app The application that the event is to be fired in
	 * @param session The session that fired the event. May be null
	 * @param property The property that changed
	 * @param oldValue The value of the property before the change
	 * @param newValue The new (current) value of the property
	 */
	public PrismsPCE(PrismsApplication app, PrismsSession session, PrismsProperty<T> property,
		T oldValue, T newValue)
	{
		theApp = app;
		theSession = session;
		theProperty = property;
		theOldValue = oldValue;
		theNewValue = newValue;
	}

	/** @return The application that this event was fired in */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/** @return The session that caused the change. May be null. */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/**
	 * @return The property that changed
	 */
	public PrismsProperty<T> getProperty()
	{
		return theProperty;
	}

	/**
	 * @return The value of the property before the change
	 */
	public T getOldValue()
	{
		return theOldValue;
	}

	/**
	 * @return The new (current) value of the property
	 */
	public T getNewValue()
	{
		return theNewValue;
	}

	/**
	 * Gets the value of a property for this listener
	 * 
	 * @param propertyName The name of the property to get
	 * @return The value associated with the property or none if the property has not been set
	 */
	public Object get(String propertyName)
	{
		if(thePropertyNames == null)
			return null;
		int idx = ArrayUtils.indexOf(thePropertyNames, propertyName);
		if(idx < 0)
			return null;
		return thePropertyValues[idx];
	}

	/**
	 * Sets the value of a property for this listener
	 * 
	 * @param propertyName The name of the property to set
	 * @param value The value to associated with the property
	 */
	public void set(String propertyName, Object value)
	{
		if(value == null)
		{ // Delete the property
			if(thePropertyNames == null)
				return;
			int idx = ArrayUtils.indexOf(thePropertyNames, propertyName);
			if(idx < 0)
				return;
			if(thePropertyNames.length == 1)
			{
				thePropertyNames = null;
				thePropertyValues = null;
			}
			else
			{
				thePropertyNames = ArrayUtils.remove(thePropertyNames, idx);
				thePropertyValues = ArrayUtils.remove(thePropertyValues, idx);
			}
		}
		else if(thePropertyNames == null)
		{
			thePropertyNames = new String [] {propertyName};
			thePropertyValues = new Object [] {value};
		}
		else
		{
			int idx = ArrayUtils.indexOf(thePropertyNames, propertyName);
			if(idx < 0)
			{
				thePropertyNames = ArrayUtils.add(thePropertyNames, propertyName);
				thePropertyValues = ArrayUtils.add(thePropertyValues, value);
			}
			else
				thePropertyValues[idx] = value;
		}
	}

	/** @return All properties set in this event in the form [name, value, name, value...] */
	public Object [] getPropertyList()
	{
		if(thePropertyNames == null)
			return new Object [0];
		Object [] ret = new Object [thePropertyNames.length * 2];
		for(int i = 0; i < thePropertyNames.length; i++)
		{
			ret[i * 2] = thePropertyNames[i];
			ret[i * 2 + 1] = thePropertyValues[i];
		}
		return ret;
	}
}
