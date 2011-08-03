/*
 * ReadOnlyManager.java Created Oct 25, 2007 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;

import prisms.arch.PrismsSession;

/**
 * A PropertyManager that throws an exception if a session attempts to change the value of the
 * managed property directly
 * 
 * @param <T> The type of property to manage
 */
public class ReadOnlyManager<T> extends prisms.arch.event.GlobalPropertyManager<T>
{
	private static final Logger log = Logger.getLogger(ReadOnlyManager.class);

	private T theValue;

	/**
	 * Creates a ReadOnlyManager that must be configured to get its application and target property
	 * name
	 */
	public ReadOnlyManager()
	{
	}

	/**
	 * Creates a ReadOnlyManager with its application and target property name
	 * 
	 * @param app The application to manage the property in
	 * @param property The property to manage
	 * @param value The value for this manager that will be enforced on all sessions
	 */
	public ReadOnlyManager(prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property, T value)
	{
		super(app, property);
		theValue = value;
	}

	@Override
	public void configure(prisms.arch.PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		super.configure(app, config);
		if(theValue != null)
			return;
		prisms.arch.PrismsConfig persisterEl = config.subConfig("persister");
		prisms.arch.Persister<T> persister;

		if(persisterEl == null)
			persister = null;
		else
			persister = PersistingPropertyManager.createPersister(persisterEl, app, getProperty());

		if(persister != null)
		{
			try
			{
				theValue = persister.getValue();
			} catch(Throwable e)
			{
				log.error("Could not depersist property " + getProperty(), e);
				return;
			}
		}
		else
		{
			Class<? extends T> propType;
			if(config.get("class") != null)
			{
				try
				{
					propType = Class.forName(config.get("class")).asSubclass(
						getProperty().getType());
				} catch(ClassNotFoundException e)
				{
					throw new IllegalStateException("Class configured for property "
						+ getProperty() + " (" + config.get("class") + ") cannot be found", e);
				} catch(ClassCastException e)
				{
					throw new IllegalStateException("Class configured for property "
						+ getProperty() + " (" + config.get("class") + ") is not a subtype of "
						+ getProperty().getType(), e);
				}
			}
			else
				propType = getProperty().getType();
			try
			{
				theValue = propType.newInstance();
			} catch(Exception e)
			{
				throw new IllegalStateException("ReadOnlyManager does not have initial value"
					+ " and can't instantiate one.", e);
			}
			if(theValue instanceof ConfigurableResource)
				((ConfigurableResource) theValue).configure(config, app);
		}
	}

	@Override
	public synchronized void propertyChange(prisms.arch.event.PrismsPCE<T> evt)
	{
		boolean isCorrect;
		if(evt.getSession() != null)
			isCorrect = isValueCorrect(evt.getSession(), evt.getNewValue());
		else
			isCorrect = prisms.util.ArrayUtils.equals(evt.getNewValue(), evt.getApp()
				.getGlobalProperty(getProperty()));
		if(!isCorrect)
			throw new IllegalArgumentException("Cannot change the value of the read-only property "
				+ getProperty());
	}

	@Override
	public T getApplicationValue(prisms.arch.PrismsApplication app)
	{
		return theValue;
	}

	@Override
	public T getCorrectValue(PrismsSession session)
	{
		return theValue;
	}

	@Override
	public boolean isValueCorrect(PrismsSession session, Object val)
	{
		return val == theValue;
	}

	@Override
	protected void eventOccurred(prisms.arch.PrismsApplication app, PrismsSession session,
		prisms.arch.event.PrismsEvent evt, Object eventValue)
	{
	}
}
