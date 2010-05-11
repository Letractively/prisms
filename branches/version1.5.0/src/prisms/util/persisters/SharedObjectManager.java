/*
 * SharedObjectManager.java Created Jun 26, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;

import prisms.arch.PrismsSession;
import prisms.util.ArrayUtils;

/**
 * Allows {@link SharedObject}s to be conditionally shared between users and sessions
 * 
 * @param <T> The type of value to manage. Although I can't seem to tell the compiler this, this
 *        MUST be a subtype of SharedObject []
 */
public class SharedObjectManager<T> extends PersistingPropertyManager<T>
{
	static final Logger log = Logger.getLogger(SharedObjectManager.class);

	private SharedObject [] theGlobalValue;

	private ShareKey [] theKeys;

	/**
	 * @see prisms.arch.event.PropertyManager#getApplicationValue()
	 */
	@Override
	public T getApplicationValue()
	{
		return (T) theGlobalValue;
	}

	/**
	 * @see prisms.arch.event.PropertyManager#isValueCorrect(prisms.arch.PrismsSession, java.lang.Object)
	 */
	@Override
	public <V extends T> boolean isValueCorrect(PrismsSession session, V val)
	{
		if(val == null)
			return false;
		final prisms.arch.ds.User user = session.getUser();
		SharedObject [] gv = theGlobalValue;
		boolean [] used = new boolean [gv.length];
		SharedObject [] soVal = (SharedObject []) val;
		for(int v = 0; v < soVal.length; v++)
		{
			int gvIdx;
			for(gvIdx = 0; gvIdx < gv.length && gv[gvIdx] != soVal[v]; gvIdx++);
			if(gvIdx == gv.length)
				return false; // The session has a value that is not in the global value
			if(used[gvIdx])
				return false; // The session has the same value twice
			used[gvIdx] = true;
			if(!soVal[v].getShareKey().canView(user))
				return false; // The session has a value that the user shouldn't be able to see
		}
		for(int v = 0; v < gv.length; v++)
			if(!used[v] && gv[v].getShareKey().canView(user))
				return false; // The session is missing a value that the user should be able to see
		return true;
	}

	/**
	 * @see prisms.arch.event.PropertyManager#getCorrectValue(prisms.arch.PrismsSession)
	 */
	@Override
	public T getCorrectValue(PrismsSession session)
	{
		final prisms.arch.ds.User user = session.getUser();
		java.util.ArrayList<SharedObject> ret = new java.util.ArrayList<SharedObject>();
		for(SharedObject o : theGlobalValue)
			if(o.getShareKey().canView(user))
				ret.add(o);
		return (T) ret.toArray((SharedObject []) java.lang.reflect.Array.newInstance(theGlobalValue
			.getClass().getComponentType(), ret.size()));
	}

	/**
	 * @see prisms.util.persisters.PersistingPropertyManager#setValue(java.lang.Object)
	 */
	@Override
	public <V extends T> void setValue(V value)
	{
		if(value == null)
			throw new NullPointerException("Cannot set a null value for the SharedObjectManager");
		theGlobalValue = (SharedObject []) value;
		theKeys = new ShareKey [theGlobalValue.length];
		for(int i = 0; i < theGlobalValue.length; i++)
			theKeys[i] = theGlobalValue[i].getShareKey().clone();
	}

	/**
	 * @see prisms.arch.event.PropertyManager#changeValues(prisms.arch.PrismsSession)
	 */
	@Override
	public void changeValues(PrismsSession session)
	{
		updateKeys();
		if(session != null)
		{
			final prisms.arch.ds.User user = session.getUser();
			final ShareKey [][] keys = new ShareKey [] [] {theKeys};
			theGlobalValue = ArrayUtils.adjust(theGlobalValue, (SharedObject []) session
				.getProperty(getProperty()),
				new ArrayUtils.DifferenceListener<SharedObject, SharedObject>()
				{
					public boolean identity(SharedObject o1, SharedObject o2)
					{
						return o1 == o2;
					}

					public SharedObject added(SharedObject o, int idx, int retIdx)
					{
						keys[0] = ArrayUtils.add(keys[0], o.getShareKey().clone());
						return o; // Anyone can create any shared object
					}

					public SharedObject removed(SharedObject o, int idx, int incMod, int retIdx)
					{
						if(!o.getShareKey().canView(user))
							return o; // User can't view it so of course it's not in their session
						else if(o.getShareKey().canEdit(user))
						{
							keys[0] = ArrayUtils.remove(keys[0], incMod);
							return null; // User has deleted the object
						}
						else
						{
							log.error("User " + user + " attempted to delete shared object " + o
								+ " without edit permissions");
							return o;
						}
					}

					public SharedObject set(SharedObject o1, int idx1, int incMod, SharedObject o2,
						int idx2, int retIdx)
					{
						return o1;
					}
				});
			theKeys = keys[0];
		}
		super.changeValues(session);
	}

	private boolean updateKeys()
	{
		boolean ret = false;
		for(int i = 0; i < theGlobalValue.length; i++)
			if(!theGlobalValue[i].getShareKey().equals(theKeys[i]))
			{
				ret = true;
				theKeys[i] = theGlobalValue[i].getShareKey().clone();
			}
		return ret;
	}

	/**
	 * @see prisms.util.persisters.PersistingPropertyManager#changeValue(PrismsSession, java.lang.Object, java.lang.Object)
	 */
	@Override
	public void changeValue(PrismsSession session, T fullValue, Object o)
	{
		SharedObject so = (SharedObject) o;
		int idx = ArrayUtils.indexOf(theGlobalValue, o);
		if(idx < 0)
		{
			log.warn("Element " + o + " not found in global value");
			return;
		}
		if(!so.getShareKey().equals(theKeys[idx]))
		{
			theKeys[idx] = so.getShareKey().clone();
			super.changeValues(null);
		}
		else
			super.changeValue(session, fullValue, o);
	}
}
