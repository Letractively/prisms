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
 * @param <T> The type of the manager
 */
public abstract class PersistingPropertyManager<T> extends
	prisms.arch.event.GlobalPropertyManager<T>
{
	private Persister<T> thePersister;

	private boolean hasLoaded;

	@Override
	public void configure(prisms.arch.PrismsApplication app, org.dom4j.Element configEl)
	{
		super.configure(app, configEl);
		if(thePersister == null)
		{
			Persister<T> persister;
			org.dom4j.Element persisterEl = configEl.element("persister");
			if(persisterEl != null)
				persister = app.getEnvironment().getPersisterFactory()
					.create(persisterEl, app, getProperty());
			else
				persister = null;
			setPersister(persister);
		}
	}

	@Override
	protected void eventOccurred(PrismsSession session, PrismsEvent evt, Object eventValue)
	{
		changeValue(session, session.getProperty(getProperty()), eventValue, evt);
	}

	@Override
	public void propertiesSet(prisms.arch.PrismsApplication app)
	{
		super.propertiesSet(app);
		if(thePersister != null && !hasLoaded)
		{
			hasLoaded = true;
			setValue(thePersister.link(getGlobalValue()));
		}
	}

	@Override
	public void changeValues(prisms.arch.PrismsSession session, prisms.arch.event.PrismsPCE<T> evt)
	{
		super.changeValues(session, evt);
		if(Boolean.TRUE.equals(evt.get("prismsPersisted")))
			return;
		evt.set("prismsPersisted", Boolean.TRUE);
		saveData(session, evt);
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
		if(thePersister != null)
			thePersister.valueChanged(session, fullValue, o, evt);
	}

	/**
	 * @return this managers persister
	 */
	public Persister<T> getPersister()
	{
		return thePersister;
	}

	/**
	 * Sets this manager's persister. Called from
	 * {@link #configure(prisms.arch.PrismsApplication, org.dom4j.Element)}
	 * 
	 * @param persister The persister being set for this manager
	 */
	public void setPersister(Persister<T> persister)
	{
		thePersister = persister;
		if(thePersister == null)
			return;
		setValue(thePersister.getValue());
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
		thePersister.setValue(session, getGlobalValue(), evt);
	}
}
