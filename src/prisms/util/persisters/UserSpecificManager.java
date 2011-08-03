/*
 * UserSpecificManager.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.PrismsSession;

/**
 * Allows a user-specific property to be stored globally
 * 
 * @param <T> The type of property to manage
 */
public class UserSpecificManager<T> extends prisms.arch.event.PropertyManager<T>
{
	private UserSpecificPersister<T> thePersister;

	/** Creates a user-specific property manager */
	public UserSpecificManager()
	{
	}

	@Override
	public void configure(prisms.arch.PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		super.configure(app, config);
		if(thePersister == null)
		{
			prisms.arch.PrismsConfig persisterEl = config.subConfig("persister");
			if(persisterEl != null)
				thePersister = createUSPersister(persisterEl, app.getEnvironment(), getProperty());
		}
	}

	/** @return The persister that persists this manager's property */
	public UserSpecificPersister<T> getPersister()
	{
		return thePersister;
	}

	@Override
	public T getApplicationValue(prisms.arch.PrismsApplication app)
	{
		return null;
	}

	@Override
	public T getCorrectValue(PrismsSession session)
	{
		return thePersister.getValue(session);
	}

	@Override
	public <V extends T> boolean isValueCorrect(PrismsSession session, V val)
	{
		return getCorrectValue(session) == val;
	}

	@Override
	public void changeValues(PrismsSession session, prisms.arch.event.PrismsPCE<T> evt)
	{
		super.changeValues(session, evt);
		thePersister.setValue(session, session.getProperty(getProperty()), evt);
	}

	/**
	 * Creates a persister from a configuration
	 * 
	 * @param <T> The type of persister to create
	 * @param persisterEl The configuration representing a persister
	 * @param env The PRISMS environment to create the persister in
	 * @param property The name of the property to be persisted by the new persister
	 * @return A configured persister
	 */
	public static <T> UserSpecificPersister<T> createUSPersister(
		prisms.arch.PrismsConfig persisterEl, prisms.arch.PrismsEnv env,
		prisms.arch.event.PrismsProperty<T> property)
	{
		UserSpecificPersister<T> ret;
		try
		{
			String className = persisterEl.get("class");
			if(className == null)
				throw new IllegalStateException("No class element in persister element "
					+ persisterEl);
			Class<? extends UserSpecificPersister<T>> clazz = (Class<? extends UserSpecificPersister<T>>) Class
				.forName(className);
			if(clazz == null)
				throw new IllegalStateException("Persister class " + className + " not found");
			ret = clazz.newInstance();
		} catch(Throwable e)
		{
			if(e instanceof IllegalStateException)
				throw (IllegalStateException) e;
			else
				throw new IllegalArgumentException("Could not create persister for property "
					+ property, e);
		}
		ret.configure(persisterEl, env, property);
		return ret;
	}
}
