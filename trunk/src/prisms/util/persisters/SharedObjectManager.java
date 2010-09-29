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

	@Override
	public T getApplicationValue()
	{
		return (T) theGlobalValue;
	}

	@Override
	public <V extends T> boolean isValueCorrect(PrismsSession session, V val)
	{
		if(val == null)
			return false;
		final prisms.arch.ds.User user = session.getUser();
		final boolean [] ret = new boolean [] {true};
		ArrayUtils.adjust(theGlobalValue, (SharedObject []) val,
			new ArrayUtils.DifferenceListener<SharedObject, SharedObject>()
			{
				public boolean identity(SharedObject o1, SharedObject o2)
				{
					return o1.equals(o2);
				}

				public SharedObject added(SharedObject o, int mIdx, int retIdx)
				{
					if(o.getShareKey().isShared())
						ret[0] = false;// The session has a value that is not in the global value
					return null;
				}

				public SharedObject removed(SharedObject o, int oIdx, int incMod, int retIdx)
				{
					if(o.getShareKey().canView(user))
						ret[0] = false; // The session is missing a value that the user should be
										// able to see
					return null;
				}

				public SharedObject set(SharedObject o1, int idx1, int incMod, SharedObject o2,
					int idx2, int retIdx)
				{
					if(o1.getShareKey().isShared() && !o1.getShareKey().canView(user))
						ret[0] = false;// The session has a value that the user shouldn't be able to
										// see
					return null;
				}
			});
		return ret[0];
	}

	@Override
	public T getCorrectValue(PrismsSession session)
	{
		final prisms.arch.ds.User user = session.getUser();
		SharedObject [] sv = (SharedObject []) session.getProperty(getProperty());
		if(sv == null)
			sv = (SharedObject []) java.lang.reflect.Array.newInstance(theGlobalValue.getClass()
				.getComponentType(), 0);
		return (T) ArrayUtils.adjust(sv, theGlobalValue,
			new ArrayUtils.DifferenceListener<SharedObject, SharedObject>()
			{
				public boolean identity(SharedObject o1, SharedObject o2)
				{
					return o1.equals(o2);
				}

				public SharedObject added(SharedObject o, int mIdx, int retIdx)
				{
					if(o.getShareKey().isShared() && o.getShareKey().canView(user))
						return o;
					return null;
				}

				public SharedObject removed(SharedObject o, int oIdx, int incMod, int retIdx)
				{
					if(!o.getShareKey().isShared())
						return o;
					return null;
				}

				public SharedObject set(SharedObject o1, int idx1, int incMod, SharedObject o2,
					int idx2, int retIdx)
				{
					if(o1.getShareKey().isShared() && !o2.getShareKey().canView(user))
						return null;
					return o1;
				}
			});
	}

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

	@Override
	public void changeValues(PrismsSession session, prisms.arch.event.PrismsPCE<T> evt)
	{
		updateKeys();
		if(session != null)
		{
			final prisms.arch.ds.User user = session.getUser();
			final ShareKey [][] keys = new ShareKey [] [] {theKeys};
			theGlobalValue = ArrayUtils.adjust(theGlobalValue,
				(SharedObject []) session.getProperty(getProperty()),
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
						if(!o.getShareKey().isShared() || !o.getShareKey().canView(user))
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
		if(evt != null)
			super.changeValues(session, evt);
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

	@Override
	public void changeValue(PrismsSession session, T fullValue, Object o,
		prisms.arch.event.PrismsEvent evt)
	{
		SharedObject so = (SharedObject) o;
		int idx = ArrayUtils.indexOf(theGlobalValue, o);
		if(idx < 0)
		{
			log.warn("Element " + o + " not found in global value");
			return;
		}
		super.changeValue(session, fullValue, o, evt);
		if(!so.getShareKey().equals(theKeys[idx]))
		{
			theKeys[idx] = so.getShareKey().clone();
			changeValues(null, null);
		}
	}
}
