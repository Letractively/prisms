/*
 * PropertySetActionQueue.java Created Oct 13, 2011 by Andrew Butler, PSL
 */
package prisms.arch;

import prisms.arch.event.PrismsProperty;

/**
 * Stores attempts to set a property in a PRISMS session or application from an environment where
 * directly setting the property and calling listeners would be dangerous and prone to deadlock.
 * This class stores the attempt so it can be executed later from a safer context.
 */
public class PropertySetActionQueue
{
	/**
	 * An attempt to set a property
	 * 
	 * @param <T> The type of the property. Object is used to satisfy generic methods.
	 */
	public static class PropertySetAction<T>
	{
		/** The property to set */
		public final PrismsProperty<T> property;

		/** The value of the property before this action */
		public final T oldValue;

		/** The value to set */
		public final T value;

		/** The session that attempted to set the property. May be null. */
		public final PrismsSession session;

		/** The properties to set in the property change event that fires */
		public final Object [] eventProps;

		PropertySetAction(PrismsProperty<T> prop, T oldVal, T val, PrismsSession ses,
			Object [] evtProps)
		{
			property = prop;
			oldValue = oldVal;
			value = val;
			session = ses;
			eventProps = evtProps;
		}
	}

	private java.util.LinkedHashMap<PrismsProperty<?>, PropertySetAction<Object>> thePSAs;

	/** Creates the queue */
	public PropertySetActionQueue()
	{
		thePSAs = new java.util.LinkedHashMap<PrismsProperty<?>, PropertySetAction<Object>>();
	}

	/**
	 * Adds a property set attempt to this queue
	 * 
	 * @param prop The property to set
	 * @param oldVal The value of the property before the property set action
	 * @param val The value to set for the property
	 * @param ses The session that attempted to set the application property. May be null.
	 * @param evtProps The properties to set in the property change event that fires
	 */
	public synchronized <T> void add(PrismsProperty<T> prop, T oldVal, T val, PrismsSession ses,
		Object [] evtProps)
	{
		PropertySetAction<Object> lastAction = thePSAs.get(prop);
		if(lastAction != null)
			oldVal = (T) lastAction.oldValue;
		PropertySetAction<Object> action = new PropertySetAction<Object>(
			(PrismsProperty<Object>) prop, oldVal, val, ses, evtProps);
		thePSAs.put(prop, action);
	}

	/**
	 * @return All property set actionst that have been queued in this queue since the last call to
	 *         this method
	 */
	public synchronized PropertySetAction<Object> [] getActions()
	{
		PropertySetAction<Object> [] ret;
		ret = thePSAs.values().toArray(new PropertySetAction [thePSAs.size()]);
		thePSAs.clear();
		return ret;
	}
}
