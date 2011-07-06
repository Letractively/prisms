/**
 * PersistingPropertyManager.java Created Feb 24, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.Persister;
import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsEvent;

/**
 * A property manager that persists its data using a {@link Persister}
 * 
 * @param <T> The type of the property that this manager manages
 */
public abstract class PersistingPropertyManager<T> extends
	prisms.arch.event.GlobalPropertyManager<T>
{
	private Persister<T> thePersister;

	private boolean hasLoaded;

	private boolean theDataLock;

	@Override
	public void configure(prisms.arch.PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		super.configure(app, config);
		if(thePersister == null)
		{
			Persister<T> persister;
			prisms.arch.PrismsConfig persisterEl = config.subConfig("persister");
			if(persisterEl != null)
				persister = createPersister(persisterEl, app, getProperty());
			else
				persister = null;
			setPersister(persister);
		}
	}

	@Override
	protected void eventOccurred(prisms.arch.PrismsApplication app, PrismsSession session,
		PrismsEvent evt, Object eventValue)
	{
		T value;
		if(session == null)
			value = app.getGlobalProperty(getProperty());
		else
			value = session.getProperty(getProperty());
		changeValue(session, value, eventValue, evt);
	}

	@Override
	public void propertiesSet(prisms.arch.PrismsApplication app)
	{
		super.propertiesSet(app);
		if(thePersister != null && !hasLoaded)
		{
			theDataLock = true;
			try
			{
				setValue(thePersister.link(getGlobalValue()));
			} finally
			{
				theDataLock = false;
			}
		}
		hasLoaded = true;
	}

	@Override
	public void changeValues(prisms.arch.PrismsSession session, prisms.arch.event.PrismsPCE<T> evt)
	{
		if(!theDataLock && !Boolean.TRUE.equals(evt.get("prismsPersisted")))
		{
			evt.set("prismsPersisted", Boolean.TRUE);
			saveData(session, evt);
		}
		super.changeValues(session, evt);
	}

	/**
	 * Changes a piece of the persisted value
	 * 
	 * @param session The session that caused the change
	 * @param fullValue The full persisted value
	 * @param o The piece of the value that changed
	 * @param evt The event that represents the change
	 */
	public synchronized void changeValue(PrismsSession session, T fullValue, Object o,
		prisms.arch.event.PrismsEvent evt)
	{
		if(thePersister != null && !Boolean.TRUE.equals(evt.getProperty("prismsPersisted")))
		{
			prisms.util.ProgramTracker.TrackNode track = null;
			prisms.arch.PrismsTransaction trans = getEnv().getTransaction();
			if(trans != null)
				track = trans.getTracker().start(
					"PRISMS: Persisting new data portion (" + evt.name + ") for property "
						+ getProperty());
			try
			{
				thePersister.valueChanged(session, fullValue, o, evt);
			} finally
			{
				if(track != null)
					trans.getTracker().end(track);
			}
		}
	}

	/** @return this managers persister */
	public Persister<T> getPersister()
	{
		return thePersister;
	}

	/**
	 * Sets this manager's persister. Called from
	 * {@link #configure(prisms.arch.PrismsApplication, prisms.arch.PrismsConfig)}
	 * 
	 * @param persister The persister being set for this manager
	 */
	public void setPersister(Persister<T> persister)
	{
		thePersister = persister;
		if(thePersister == null)
			return;
		theDataLock = true;
		try
		{
			setValue(thePersister.getValue());
		} finally
		{
			theDataLock = false;
		}
	}

	/** @return All values that should be persisted for this property */
	public abstract T getGlobalValue();

	/**
	 * Called to set the initial value of this manager. NOT called when the property changes.
	 * 
	 * @param <V> The type of value to set
	 * @param value The property value to set for this manager
	 */
	public abstract <V extends T> void setValue(V value);

	/**
	 * Called to save the current set of data
	 * 
	 * @param session The session that caused the change
	 * @param evt The event that represents the change
	 */
	public void saveData(PrismsSession session, prisms.arch.event.PrismsPCE<T> evt)
	{
		if(thePersister == null)
			return;
		prisms.util.ProgramTracker.TrackNode track = null;
		prisms.arch.PrismsTransaction trans = getEnv().getTransaction();
		if(trans != null)
			track = trans.getTracker().start(
				"PRISMS: Persisting new data for property " + getProperty());
		try
		{
			thePersister.setValue(session, getGlobalValue(), evt);
		} finally
		{
			if(track != null)
				trans.getTracker().end(track);
		}
	}

	@Override
	protected boolean applies(PrismsEvent evt)
	{
		return !(thePersister instanceof DiscriminatingPersister)
			|| ((DiscriminatingPersister<T>) thePersister).applies(evt);
	}

	/**
	 * Creates a persister from a configuration
	 * 
	 * @param <T> The type of persister to create
	 * @param persisterEl The configuration representing a persister
	 * @param app The application to create the persister for
	 * @param property The name of the property to be persisted by the new persister
	 * @return A configured persister
	 */
	public static <T> Persister<T> createPersister(prisms.arch.PrismsConfig persisterEl,
		prisms.arch.PrismsApplication app, prisms.arch.event.PrismsProperty<T> property)
	{
		Persister<T> ret;
		try
		{
			String className = persisterEl.get("class");
			if(className == null)
				throw new IllegalStateException("No class element in persister element "
					+ persisterEl);
			Class<? extends Persister<T>> clazz = (Class<? extends Persister<T>>) Class
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
		ret.configure(persisterEl, app, property);
		return ret;
	}
}
