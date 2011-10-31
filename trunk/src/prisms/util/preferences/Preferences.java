/*
 * Preferences.java Created Mar 18, 2008 by Andrew Butler, PSL
 */
package prisms.util.preferences;

import java.util.concurrent.locks.Lock;

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

	private java.util.concurrent.locks.ReentrantReadWriteLock theLock;

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
		theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
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
		Lock lock = theLock.readLock();
		lock.lock();
		PrefValue val;
		boolean isActive;
		try
		{
			val = thePrefs.get(pref);
			isActive = theActivePrefs.contains(pref);
		} finally
		{
			lock.unlock();
		}
		if(val == null)
			return null;
		if(pref.getDescription() != null)
			val.pref.setDescription(pref.getDescription());
		pref.setID(val.pref.getID());
		if(!isActive)
		{
			lock = theLock.writeLock();
			lock.lock();
			try
			{
				theActivePrefs.add(pref);
			} finally
			{
				lock.unlock();
			}
		}
		return (T) val.value;
	}

	/**
	 * @param domain The domain of the preference to get
	 * @param prefName The name of the preference to get
	 * @return The preference in this preferences object with the specified properties
	 */
	public Preference<?> getPreference(String domain, String prefName)
	{
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			for(Preference<?> pref : thePrefs.keySet())
				if(pref.getDomain().equals(domain) && pref.getName().equals(prefName))
					return pref;
		} finally
		{
			lock.unlock();
		}
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
		Lock lock = theLock.writeLock();
		lock.lock();
		T oldVal;
		try
		{
			PrefValue val = thePrefs.get(pref);
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
		} finally
		{
			lock.unlock();
		}
		PreferenceEvent evt = new PreferenceEvent(this, pref, oldVal, value, withRecord);
		for(Listener L : theListeners)
			L.prefChanged(evt);
	}

	/**
	 * @param pref The preference to check the activity of
	 * @return Whether the given preference is active
	 */
	public boolean isActive(Preference<?> pref)
	{
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			return theActivePrefs.contains(pref);
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * Sets whether a given preference is active
	 * 
	 * @param pref The preference to set
	 * @param active Whether the given preference should be active or not
	 */
	public void setActive(Preference<?> pref, boolean active)
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			if(!active)
				theActivePrefs.remove(pref);
			else if(pref.getDomain().equals(this))
				theActivePrefs.add(pref);
		} finally
		{
			lock.unlock();
		}
	}

	/** @return All domains contained in this set of preferences */
	public String [] getAllDomains()
	{
		java.util.ArrayList<String> ret = new java.util.ArrayList<String>();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			for(Preference<?> pref : thePrefs.keySet())
				if(!prisms.util.ArrayUtils.contains(ret, pref.getDomain()))
					ret.add(pref.getDomain());
		} finally
		{
			lock.unlock();
		}
		return ret.toArray(new String [ret.size()]);
	}

	/** @return All domains containing active preferences */
	public String [] getActiveDomains()
	{
		java.util.ArrayList<String> ret = new java.util.ArrayList<String>();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			for(Preference<?> pref : theActivePrefs)
				if(!prisms.util.ArrayUtils.contains(ret, pref.getDomain()))
					ret.add(pref.getDomain());
		} finally
		{
			lock.unlock();
		}
		return ret.toArray(new String [ret.size()]);
	}

	/**
	 * @param domain The domain to get preferences for
	 * @return The names of all preferences for the given domain in this set of preferences
	 */
	public Preference<?> [] getAllPreferences(String domain)
	{
		java.util.ArrayList<Preference<?>> ret = new java.util.ArrayList<Preference<?>>();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			for(Preference<?> pref : thePrefs.keySet())
				if(pref.getDomain().equals(domain))
					ret.add(pref);
		} finally
		{
			lock.unlock();
		}
		return ret.toArray(new Preference<?> [ret.size()]);
	}

	/**
	 * @param domain The domain to get preferences for
	 * @return All active preferences for the given domain in this set of preferences
	 */
	public Preference<?> [] getActivePreferences(String domain)
	{
		java.util.ArrayList<Preference<?>> ret = new java.util.ArrayList<Preference<?>>();
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			for(Preference<?> pref : theActivePrefs)
				if(pref.getDomain().equals(domain))
					ret.add(pref);
		} finally
		{
			lock.unlock();
		}
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
