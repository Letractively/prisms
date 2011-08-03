/*
 * PreferenceEvent.java Created Mar 18, 2008 by Andrew Butler, PSL
 */
package prisms.util.preferences;

import prisms.arch.event.PrismsEvent;

/** An event fired when the preferences change */
public class PreferenceEvent extends PrismsEvent
{
	private final Preferences thePrefs;

	private Preference<?> thePreference;

	private final Object theOldValue;

	private final Object theNewValue;

	private final boolean isWithRecord;

	/**
	 * Creates a PreferenceEvent
	 * 
	 * @param prefs The preferences set that this event is for
	 * @param pref The preference that was changed
	 * @param old The previous value of the preference before this event
	 * @param value The new value for the preference
	 * @param withRecord Whether this event should be persisted for scaling
	 */
	public PreferenceEvent(Preferences prefs, Preference<?> pref, Object old, Object value,
		boolean withRecord)
	{
		super("preferencesChanged");
		thePrefs = prefs;
		thePreference = pref;
		theOldValue = old;
		theNewValue = value;
		isWithRecord = withRecord;
	}

	/** @return The preferences set that this event is for */
	public Preferences getPrefSet()
	{
		return thePrefs;
	}

	/** @return The preference that was changed */
	public Preference<?> getPreference()
	{
		return thePreference;
	}

	/** @return The previous value of the preference before this event */
	public Object getOldValue()
	{
		return theOldValue;
	}

	/** @return The new value of this event's preference */
	public Object getNewValue()
	{
		return theNewValue;
	}

	/** @return Whether this event should be persisted for scaling in an enterprise */
	public boolean isWithRecord()
	{
		return isWithRecord;
	}
}
