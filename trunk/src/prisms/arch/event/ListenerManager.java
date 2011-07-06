/**
 * ListenerManager.java Created Feb 11, 2008 by Andrew Butler, PSL
 */
package prisms.arch.event;

import java.lang.reflect.Array;

/**
 * Manages a set of listeners
 * 
 * @param <L> The type of the listener to manage
 */
public class ListenerManager<L>
{
	/** The key for listeners to all properties or all events */
	private static final String ALL_KEY = "All" + prisms.util.PrismsUtils.getRandomString(8);

	private Class<L> theType;

	private java.util.concurrent.ConcurrentHashMap<Object, L []> theListeners;

	/**
	 * Creates a ListenerManager for a given type of listener
	 * 
	 * @param type The type of listener to manage
	 */
	public ListenerManager(Class<L> type)
	{
		theType = type;
		theListeners = new java.util.concurrent.ConcurrentHashMap<Object, L []>();
	}

	/**
	 * @param property The name of the listeners to get
	 * @return All listeners registered with the given name
	 */
	public L [] getListeners(Object property)
	{
		L [] specificLs;
		L [] generalLs;
		specificLs = theListeners.get(property);
		generalLs = theListeners.get(ALL_KEY);
		L [] ret;
		if(specificLs == null && generalLs == null)
			ret = (L []) Array.newInstance(theType, 0);
		else if(specificLs == null)
			ret = generalLs.clone();
		else if(generalLs == null)
			ret = specificLs.clone();
		else
		{
			ret = (L []) Array.newInstance(theType, specificLs.length + generalLs.length);
			System.arraycopy(specificLs, 0, ret, 0, specificLs.length);
			System.arraycopy(generalLs, 0, ret, specificLs.length, generalLs.length);
		}
		if(ret == null)
			ret = (L []) Array.newInstance(theType, 0);
		return ret;
	}

	/**
	 * Adds a listener to be notified when any other listeners are
	 * 
	 * @param lstnr The listener to register
	 */
	public void addListener(L lstnr)
	{
		addListener(ALL_KEY, lstnr);
	}

	/**
	 * Adds a listener under the given name
	 * 
	 * @param property The name to register the listener under
	 * @param lstnr The listener to register
	 */
	public void addListener(Object property, L lstnr)
	{
		if(property == null)
			throw new IllegalArgumentException("Property name cannot be null");
		if(lstnr == null)
			return;
		L [] lstnrs = theListeners.get(property);
		if(lstnrs == null)
		{
			lstnrs = (L []) Array.newInstance(theType, 1);
			lstnrs[0] = lstnr;
		}
		else
		{
			L [] newLs = (L []) Array.newInstance(theType, lstnrs.length + 1);
			System.arraycopy(lstnrs, 0, newLs, 0, lstnrs.length);
			newLs[lstnrs.length] = lstnr;
			lstnrs = newLs;
		}
		theListeners.put(property, lstnrs);
	}

	/**
	 * Removes all occurrences of a listener
	 * 
	 * @param lstnr The listener to remove
	 */
	public void removeListener(L lstnr)
	{
		if(lstnr == null)
			return;
		java.util.Map.Entry<String, L []> [] allLs;
		allLs = theListeners.entrySet().toArray(new java.util.Map.Entry [0]);
		for(java.util.Map.Entry<String, L []> entry : allLs)
		{
			L [] lstnrs = entry.getValue();
			for(int i = 0; lstnrs != null && i < lstnrs.length; i++)
			{
				if(lstnr.equals(lstnrs[i]))
				{
					L [] newLs;
					if(lstnrs.length == 1)
						newLs = null;
					else
					{
						newLs = (L []) Array.newInstance(theType, lstnrs.length - 1);
						System.arraycopy(lstnrs, 0, newLs, 0, i);
						System.arraycopy(lstnrs, i + 1, newLs, i, newLs.length - i);
					}
					lstnrs = newLs;
				}
			}
			if(lstnrs == null)
				theListeners.remove(entry.getKey());
			else
				entry.setValue(lstnrs);
		}
	}

	/**
	 * Removes a listener under a given name
	 * 
	 * @param property The name to remove the listener from
	 * @param lstnr The listener to remove
	 */
	public void removeListener(Object property, L lstnr)
	{
		if(lstnr == null)
			return;
		L [] lstnrs = theListeners.get(property);
		for(int i = 0; lstnrs != null && i < lstnrs.length; i++)
		{
			if(lstnr.equals(lstnrs[i]))
			{
				if(lstnrs.length == 1)
					lstnrs = null;
				else
				{
					L [] newLs = (L []) Array.newInstance(theType, lstnrs.length - 1);
					System.arraycopy(lstnrs, 0, newLs, 0, i);
					System.arraycopy(lstnrs, i + 1, newLs, i, newLs.length - i);
					lstnrs = newLs;
				}
			}
		}
		if(lstnrs == null)
			theListeners.remove(property);
		else
			theListeners.put(property, lstnrs);
	}
}
