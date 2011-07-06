/**
 * Preferences.java Created Mar 18, 2008 by Andrew Butler, PSL
 */
package prisms.util.preferences;

/** A set of preferences for a user */
public class Preferences implements prisms.util.persisters.OwnedObject
{
	private prisms.arch.PrismsApplication theApp;

	private prisms.arch.ds.User theOwner;

	private java.util.Map<Preference<?>, Object> thePrefs;

	private Listener [] theListeners;

	private java.util.HashSet<Preference<?>> theActivePrefs;

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
		thePrefs = new java.util.TreeMap<Preference<?>, Object>();
		theActivePrefs = new java.util.HashSet<Preference<?>>();
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
		T ret = (T) thePrefs.get(pref);
		if(ret != null)
			theActivePrefs.add(pref);
		return ret;
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
		if(value != null)
			theActivePrefs.add(pref);
		if(value != null)
			pref.getType().validate(value);
		thePrefs.put(pref, value);
		for(Listener L : theListeners)
			L.prefChanged(this, pref, value);
	}

	/**
	 * Removes a preference
	 * 
	 * @param pref The preference to remove
	 */
	public void remove(Preference<?> pref)
	{
		thePrefs.remove(pref);
		for(Listener L : theListeners)
			L.prefChanged(this, pref, null);
	}

	/**
	 * Removes an entire domain of preferences
	 * 
	 * @param domain The name of the domain to remove
	 */
	public void removeDomain(String domain)
	{
		java.util.Iterator<Preference<?>> keys = thePrefs.keySet().iterator();
		while(keys.hasNext())
		{
			Preference<?> pref = keys.next();
			if(pref.getDomain().equals(domain))
				keys.remove();
		}
		for(Listener L : theListeners)
			L.domainRemoved(this, domain);
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
		 * @param <V> The type of the value being set
		 * @param prefs The preferences set that was modified
		 * @param pref The preference that was modified
		 * @param value The new value for the preference
		 */
		<T, V extends T> void prefChanged(Preferences prefs, Preference<T> pref, V value);

		/**
		 * Called when a domain is removed
		 * 
		 * @param prefs The preferences set that was modified
		 * @param domain The domain that was removed
		 */
		void domainRemoved(Preferences prefs, String domain);
	}
}
