/*
 * PreferenceEvent.java Created Mar 18, 2008 by Andrew Butler, PSL
 */
package prisms.util.preferences;

import prisms.arch.event.PrismsEvent;

/** An event fired when the preferences change */
public class PreferenceEvent extends PrismsEvent
{
	/** The type of event that occurred */
	public static enum Type
	{
		/** The event type when a preference is introduced or changed */
		CHANGED,
		/** The event type when a preference is removed */
		REMOVED;
	}

	private final Type theType;

	private Preference<?> thePreference;

	private final Object theValue;

	/**
	 * Creates a PreferenceEvent
	 * 
	 * @param type The type of the event
	 * @param pref The preference that was changed
	 * @param value The new value for the preference
	 */
	public PreferenceEvent(Type type, Preference<?> pref, Object value)
	{
		super("preferencesChanged");
		theType = type;
		thePreference = pref;
		theValue = value;
	}

	/** @return This event's type */
	public Type getType()
	{
		return theType;
	}

	/** @return The preference that was changed */
	public Preference<?> getPreference()
	{
		return thePreference;
	}

	/** @return The new value of this event's preference */
	public Object getValue()
	{
		return theValue;
	}
}
