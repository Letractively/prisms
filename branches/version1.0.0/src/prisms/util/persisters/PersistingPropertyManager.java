/**
 * PersistingPropertyManager.java Created Feb 24, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.Persister;

/**
 * A property manager that persists its data using a {@link Persister}
 * 
 * @param <T> The type of the manager
 */
public abstract class PersistingPropertyManager<T> extends prisms.arch.event.PropertyManager<T>
{
	private Persister<T> thePersister;

	/**
	 * @see prisms.arch.event.PropertyManager#configure(prisms.arch.PrismsApplication, org.dom4j.Element)
	 */
	@Override
	public void configure(prisms.arch.PrismsApplication app, org.dom4j.Element configEl)
	{
		super.configure(app, configEl);
		Persister<T> persister;
		org.dom4j.Element persisterEl = configEl.element("persister");
		if(persisterEl != null)
			persister = app.getServer().getPersisterFactory().create(persisterEl, app,
				getProperty());
		else
			persister = null;
		setPersister(persister);
		org.dom4j.Element eventEl = configEl.element("changeEvent");
		if(eventEl != null)
		{
			String eventName = eventEl.elementTextTrim("name");
			if(eventName == null)
				throw new IllegalArgumentException("Cannot listen for change event on property "
					+ getProperty() + ". No event name specified");
			String propName = eventEl.elementTextTrim("eventProperty");
			if(propName == null)
				throw new IllegalArgumentException("Cannot listen for change event on property "
					+ getProperty() + ". No eventProperty specified");
			eventEl.addElement("persistProperty").setText(getProperty().getName());
			eventEl.addElement("type").setText(getProperty().getType().getName());
			app.addEventListenerType(eventName, PersistingChangeListener.class, eventEl);
		}
	}

	@Override
	public void propertiesSet()
	{
		super.propertiesSet();
		if(thePersister != null)
			setValue(thePersister.link(getApplicationValue()));
	}

	/**
	 * @see prisms.arch.event.PropertyManager#changeValues(prisms.arch.PrismsSession)
	 */
	@Override
	public void changeValues(prisms.arch.PrismsSession session)
	{
		super.changeValues(session);
		saveData();
	}

	/**
	 * Changes a piece of the persisted value
	 * 
	 * @param fullValue The full persisted value
	 * @param o The piece of the value that changed
	 */
	public synchronized void changeValue(T fullValue, Object o)
	{
		if(thePersister != null)
			thePersister.valueChanged(fullValue, o);
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

	/**
	 * Called to set the initial value of this manager. NOT called when the property changes.
	 * 
	 * @param <V> The type of value to set
	 * @param value The property value to set for this manager
	 */
	public abstract <V extends T> void setValue(V value);

	/**
	 * Called to save the current set of data
	 */
	public void saveData()
	{
		if(thePersister == null)
			return;
		thePersister.setValue(getApplicationValue());
	}
}
