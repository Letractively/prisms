/*
 * Preferences.java Created Mar 18, 2008 by Andrew Butler, PSL
 */
package prisms.util.preferences;

/** A set of preferences for a user */
public class Preferences implements prisms.util.persisters.OwnedObject
{
	/**
	 * An association between a preference and a value. Used internally to keep track of
	 * preferences.
	 */
	public static class PrefValue
	{
		/** The preference */
		public final Preference<?> pref;

		/** The value */
		public Object value;

		PrefValue(Preference<?> p, Object v)
		{
			pref = p;
			value = v;
		}
	}

	private prisms.arch.PrismsApplication theApp;

	private prisms.arch.ds.User theOwner;

	private java.util.Map<Preference<?>, PrefValue> thePrefs;

	private Listener [] theListeners;

	private java.util.LinkedHashSet<Preference<?>> theActivePrefs;

	/**
	 * Creates the preferences set
	 * 
	 * @param app The application that these preferences apply to
	 * @param user The user whose preferences this is to represent
	 */
	public Preferences(prisms.arch.PrismsApplication app, prisms.arch.ds.User user)
	{
		theApp = app;
		theOwner = user;
		thePrefs = new java.util.TreeMap<Preference<?>, PrefValue>();
		theActivePrefs = new java.util.LinkedHashSet<Preference<?>>();
		theListeners = new Listener [0];
	}

	/** @return The application that these preferences apply to */
	public prisms.arch.PrismsApplication getApp()
	{
		return theApp;
	}

	/** @return The user whose preferences this represents */
	public prisms.arch.ds.User getOwner()
	{
		return theOwner;
	}

	public boolean isPublic()
	{
		return false;
	}

	/**
	 * Adds a listener to listen for changes to this preferences
	 * 
	 * @param L The listener to add
	 */
	public void addListener(Listener L)
	{
		theListeners = prisms.util.ArrayUtils.add(theListeners, L);
	}

	/**
	 * Removes a listener from listening for changes to this preferences
	 * 
	 * @param L The listener to remove
	 */
	public void removeListener(Listener L)
	{
		theListeners = prisms.util.ArrayUtils.remove(theListeners, L);
	}

	/**
	 * Gets a preference
	 * 
	 * @param <T> The type of the preference
	 * @param pref The preference to get the value of
	 * @return The value of the specified preference
	 */
	public <T> T get(Preference<T> pref)
	{
		PrefValue val = thePrefs.get(pref);
		if(val == null)
			return null;
		val.pref.setDescription(pref.getDescription());
		pref.setID(val.pref.getID());
		theActivePrefs.add(pref);
		return (T) val.value;
	}

	/**
	 * @param domain The domain of the preference to get
	 * @param prefName The name of the preference to get
	 * @return The preference in this preferences object with the specified properties
	 */
	public Preference<?> getPreference(String domain, String prefName)
	{
		for(Preference<?> pref : thePrefs.keySet())
			if(pref.getDomain().equals(domain) && pref.getName().equals(prefName))
				return pref;
		return null;
	}

	/**
	 * Sets a preference
	 * 
	 * @param <T> The type of the preference
	 * @param <V> The type of the value being set
	 * @param pref The preference to set the value of
	 * @param value The value to set for the preference
	 */
	public <T, V extends T> void set(Preference<T> pref, V value)
	{
		set(pref, value, true);
	}

	<T, V extends T> void set(Preference<T> pref, V value, boolean withRecord)
	{
		if(value != null)
			pref.getType().validate(value);
		PrefValue val = thePrefs.get(pref);
		T oldVal;
		if(val == null)
		{
			oldVal = null;
			if(value == null)
				return;
			thePrefs.put(pref, new PrefValue(pref, value));
		}
		else
		{
			oldVal = (T) val.value;
			if(value == null)
				thePrefs.remove(pref);
			val.value = value;
		}
		PreferenceEvent evt = new PreferenceEvent(this, pref, oldVal, value, withRecord);
		for(Listener L : theListeners)
			L.prefChanged(evt);
	}

	/** @return All domains contained in this set of preferences */
	public String [] getAllDomains()
	{
		String [] ret = new String [0];
		for(Preference<?> pref : thePrefs.keySet())
			if(!prisms.util.ArrayUtils.contains(ret, pref.getDomain()))
				ret = prisms.util.ArrayUtils.add(ret, pref.getDomain());
		return ret;
	}

	/** @return All domains containing active preferences */
	public String [] getActiveDomains()
	{
		String [] ret = new String [0];
		for(Preference<?> pref : theActivePrefs)
			if(!prisms.util.ArrayUtils.contains(ret, pref.getDomain()))
				ret = prisms.util.ArrayUtils.add(ret, pref.getDomain());
		return ret;
	}

	/**
	 * @param domain The domain to get preferences for
	 * @return The names of all preferences for the given domain in this set of preferences
	 */
	public Preference<?> [] getAllPreferences(String domain)
	{
		java.util.ArrayList<Preference<?>> ret = new java.util.ArrayList<Preference<?>>();
		for(Preference<?> pref : thePrefs.keySet())
			if(pref.getDomain().equals(domain))
				ret.add(pref);
		return ret.toArray(new Preference<?> [ret.size()]);
	}

	/**
	 * @param domain The domain to get preferences for
	 * @return All active preferences for the given domain in this set of preferences
	 */
	public Preference<?> [] getActivePreferences(String domain)
	{
		java.util.ArrayList<Preference<?>> ret = new java.util.ArrayList<Preference<?>>();
		for(Preference<?> pref : theActivePrefs)
			if(pref.getDomain().equals(domain))
				ret.add(pref);
		return ret.toArray(new Preference<?> [ret.size()]);
	}

	/** A listener to listen for changes to a Preferences set */
	public static interface Listener
	{
		/**
		 * Called when a particular preference changes
		 * 
		 * @param <T> The type of the preference being changed
		 * @param evt The change event
		 */
		<T> void prefChanged(PreferenceEvent evt);
	}
}
