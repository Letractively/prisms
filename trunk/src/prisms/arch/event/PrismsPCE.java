/**
 * PrismsPCE.java Created Mar 10, 2009 by Andrew Butler, PSL
 */
package prisms.arch.event;

import prisms.util.ArrayUtils;

/**
 * A substitute for {@link java.beans.PropertyChangeEvent} that represents a change to a typed
 * property
 * 
 * @param <T> The type of the property that this event represents a change to
 */
public class PrismsPCE<T>
{
	private final Object theSource;

	private final PrismsProperty<T> theProperty;

	private final T theOldValue;

	private final T theNewValue;

	private String [] thePropertyNames;

	private Object [] thePropertyValues;

	/**
	 * Creates a property change event
	 * 
	 * @param source The source that fired the event
	 * @param property The property that changed
	 * @param oldValue The value of the property before the change
	 * @param newValue The new (current) value of the property
	 */
	public PrismsPCE(Object source, PrismsProperty<T> property, T oldValue, T newValue)
	{
		theSource = source;
		theProperty = property;
		theOldValue = oldValue;
		theNewValue = newValue;
	}

	/**
	 * @return The source that fired the event. Normally, this will be the session on which the
	 *         event was originally fired. If the event was fired globally (via
	 *         {@link prisms.arch.PrismsApplication#fireGlobalPropertyChange(PrismsProperty, PropertyManager, Object)}
	 *         ), this will be either the property manager passed to the method or the application
	 *         itself if the given manager was null.
	 */
	public Object getSource()
	{
		return theSource;
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
}
