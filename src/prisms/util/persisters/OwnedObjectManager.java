/**
 * OwnedObjectManager.java Created Oct 11, 2007 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import java.lang.reflect.Array;
import java.util.ArrayList;

import prisms.arch.PrismsSession;
import prisms.arch.ds.User;
import prisms.util.ArrayUtils;

/**
 * Allows {link OwnedObject}s to be conditionally shared between users and sessions
 * 
 * @param <T> The type of value to manage. Although I can't seem to tell the compiler this, this
 *        MUST be a subtype of OwnedObject []
 */
public class OwnedObjectManager<T> extends PersistingPropertyManager<T>
{
	private Class<? extends OwnedObject> theType;

	private OwnedObject [] thePublicList;

	private OwnedObject [] thePrivateList;

	/**
	 * Creates an OwnedObjectManager
	 */
	public OwnedObjectManager()
	{
	}

	/**
	 * @return The set of public objects being managed
	 */
	public OwnedObject [] getPublicSet()
	{
		return thePublicList.clone();
	}

	/**
	 * @return The set of private objects being managed
	 */
	public OwnedObject [] getPrivateSet()
	{
		return thePrivateList.clone();
	}

	public T getApplicationValue()
	{
		return (T) prisms.util.ArrayUtils.mergeInclusiveP(theType, thePublicList, thePrivateList);
	}

	public void configure(prisms.arch.PrismsApplication app, org.dom4j.Element configEl)
	{
		try
		{
			theType = (Class<? extends OwnedObject>) Class
				.forName(configEl.elementTextTrim("type"));
		} catch(Throwable e)
		{
			throw new IllegalArgumentException("Cannot get type class "
				+ configEl.elementTextTrim("type"), e);
		}
		thePublicList = (OwnedObject []) Array.newInstance(theType, 0);
		thePrivateList = (OwnedObject []) Array.newInstance(theType, 0);
		super.configure(app, configEl);
	}

	public <V extends T> void setValue(V value)
	{
		Object objVal = value;
		if(objVal == null)
			objVal = new OwnedObject [0];
		if(!(objVal instanceof OwnedObject []))
			throw new IllegalStateException("OwnedObject array expected, " + objVal.getClass()
				+ " received");
		if(objVal != null)
		{
			ArrayList<OwnedObject> publicList = new ArrayList<OwnedObject>();
			ArrayList<OwnedObject> privateList = new ArrayList<OwnedObject>();
			for(OwnedObject o : (OwnedObject []) objVal)
			{
				if(o.isPublic())
					publicList.add(o);
				else
					privateList.add(o);
			}
			if(!ArrayUtils.equals(publicList, thePublicList)
				|| !ArrayUtils.equals(privateList, thePrivateList))
			{
				thePublicList = publicList.toArray((OwnedObject []) Array.newInstance(theType,
					publicList.size()));
				thePrivateList = privateList.toArray((OwnedObject []) Array.newInstance(theType,
					privateList.size()));
			}
		}
	}

	public boolean isValueCorrect(PrismsSession session, Object o)
	{
		int i;
		OwnedObject [] set = (OwnedObject []) o;
		if(set == null)
			return false;
		for(i = 0; i < thePublicList.length; i++)
		{
			OwnedObject ownedObject = thePublicList[i];
			if(!ArrayUtils.contains(set, ownedObject))
				return false;
		}
		for(i = 0; i < thePrivateList.length; i++)
		{
			OwnedObject ownedObject = thePrivateList[i];
			if(ArrayUtils.contains(set, ownedObject))
			{
				if(!ownedObject.getOwner().equals(session.getUser()))
					return false;
			}
			else if(ownedObject.getOwner().equals(session.getUser()))
				return false;
		}
		for(i = 0; i < set.length; i++)
		{
			if(ArrayUtils.contains(thePublicList, set[i]))
				continue;
			if(ArrayUtils.contains(thePrivateList, set[i])
				&& set[i].getOwner().equals(session.getUser()))
				continue;
			return false;
		}
		return true;
	}

	public T getCorrectValue(PrismsSession session)
	{
		ArrayList<OwnedObject> ret = new ArrayList<OwnedObject>();
		for(OwnedObject m : thePublicList)
			ret.add(m);
		for(OwnedObject m : thePrivateList)
			if(session.getUser().equals(m.getOwner()))
				ret.add(m);
		return (T) ret.toArray((OwnedObject []) Array.newInstance(theType, ret.size()));
	}

	void updatePublicPrivate()
	{
		int i;
		for(i = 0; i < thePublicList.length; i++)
		{
			OwnedObject ownedObject = thePublicList[i];
			if(!ownedObject.isPublic())
			{
				thePublicList = ArrayUtils.remove(thePublicList, i);
				thePrivateList = ArrayUtils.add(thePrivateList, ownedObject);
				i--;
			}
		}
		for(i = 0; i < thePrivateList.length; i++)
		{
			OwnedObject ownedObject = thePrivateList[i];
			if(ownedObject.isPublic())
			{
				thePrivateList = ArrayUtils.remove(thePrivateList, i);
				thePublicList = ArrayUtils.add(thePublicList, ownedObject);
				i--;
			}
		}
	}

	/**
	 * @see prisms.arch.event.PropertyManager#changeValues(prisms.arch.PrismsSession)
	 */
	@Override
	public void changeValues(PrismsSession session)
	{
		int i;
		updatePublicPrivate();
		if(session != null)
		{
			OwnedObject [] set = (OwnedObject []) session.getProperty(getProperty());
			User user = session.getUser();
			for(i = 0; i < thePublicList.length; i++)
			{
				OwnedObject ownedObject = thePublicList[i];
				if(!ArrayUtils.contains(set, ownedObject) && ownedObject.getOwner().equals(user))
				{
					thePublicList = ArrayUtils.remove(thePublicList, i);
					i--;
				}
			}
			for(i = 0; i < thePrivateList.length; i++)
			{
				OwnedObject ownedObject = thePrivateList[i];
				if(!ArrayUtils.contains(set, ownedObject) && ownedObject.getOwner().equals(user))
				{
					thePrivateList = ArrayUtils.remove(thePrivateList, i);
					i--;
				}
			}
			for(i = 0; i < set.length; i++)
			{
				if(set[i].isPublic())
				{
					if(!ArrayUtils.contains(thePublicList, set[i]))
						thePublicList = ArrayUtils.add(thePublicList, set[i]);
				}
				else
				{
					if(!ArrayUtils.contains(thePrivateList, set[i]))
						thePrivateList = ArrayUtils.add(thePrivateList, set[i]);
				}
			}
		}
		super.changeValues(session);
		saveData();
	}

	/**
	 * @see prisms.util.persisters.PersistingPropertyManager#changeValue(java.lang.Object, java.lang.Object)
	 */
	@Override
	public void changeValue(T fullValue, Object o)
	{
		if((((OwnedObject) o).isPublic() && ArrayUtils.contains(thePrivateList, (OwnedObject) o))
			|| (!((OwnedObject) o).isPublic() && ArrayUtils
				.contains(thePublicList, (OwnedObject) o)))
			changeValues(null);
		else
			super.changeValue(fullValue, o);
	}
}
