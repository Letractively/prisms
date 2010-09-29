/**
 * UserSpecific.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.PrismsSession;

/**
 * Allows a user-specific property to be stored globally. The persister for this class must return a
 * value of type java.util.Map<prisms.arch.ds.User, java.util.Map<String, Object>>
 * 
 * @param <T> The type of property to manage
 */
public class UserSpecific<T extends OwnedObject> extends prisms.arch.event.PropertyManager<T>
{
	private UserSpecificPersister<T> thePersister;

	private java.util.Map<String, T> theValues;

	/**
	 * Creates a user-specific property manager
	 */
	public UserSpecific()
	{
		theValues = new java.util.HashMap<String, T>();
	}

	/**
	 * @see prisms.arch.event.PropertyManager#configure(prisms.arch.PrismsApplication,
	 *      org.dom4j.Element)
	 */
	@Override
	public void configure(prisms.arch.PrismsApplication app, org.dom4j.Element configEl)
	{
		super.configure(app, configEl);
		org.dom4j.Element persisterEl = configEl.element("persister");
		if(persisterEl != null)
		{
			// Gets around a java compile error. THIS IS SAFE.
			Object persister = app.getServer().getPersisterFactory()
				.create(persisterEl, app, getProperty());
			thePersister = (UserSpecificPersister<T>) persister;
		}
		if(thePersister != null)
			theValues = thePersister.getValue();
	}

	/**
	 * @see prisms.arch.event.PropertyManager#getApplicationValue()
	 */
	@Override
	public T getApplicationValue()
	{
		return null;
	}

	/**
	 * @see prisms.arch.event.PropertyManager#getCorrectValue(prisms.arch.PrismsSession)
	 */
	@Override
	public T getCorrectValue(PrismsSession session)
	{
		T ret = theValues.get(session.getUser().getName());
		if(ret == null)
		{
			ret = thePersister.create(session.getUser());
			theValues.put(session.getUser().getName(), ret);
		}
		return ret;
	}

	/**
	 * @see prisms.arch.event.PropertyManager#isValueCorrect(prisms.arch.PrismsSession,
	 *      java.lang.Object)
	 */
	@Override
	public <V extends T> boolean isValueCorrect(PrismsSession session, V val)
	{
		return getCorrectValue(session) == val;
	}

	@Override
	public void changeValues(PrismsSession session, prisms.arch.event.PrismsPCE<T> evt)
	{
		super.changeValues(session, evt);
		thePersister.setValue(theValues, evt);
	}

	/**
	 * @see prisms.arch.event.PropertyManager#propertiesSet()
	 */
	@Override
	public void propertiesSet()
	{
		super.propertiesSet();
		theValues = thePersister.link(theValues);
	}
}
