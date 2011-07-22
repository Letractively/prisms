/**
 * InitialValueManager.java Created Oct 30, 2007 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;

import prisms.arch.Persister;
import prisms.arch.PrismsSession;
import prisms.arch.event.PropertyManager;

/**
 * Ensures that each session is initialized with a common value. The session can modify the value
 * later without affecting other sessions
 * 
 * @param <T> The type of property to manage
 */
public class InitialValueManager<T> extends PropertyManager<T>
{
	private static final Logger log = Logger.getLogger(InitialValueManager.class);

	private T theValue;

	private java.util.Set<Integer> theInitializedSessions;

	/** Creates an InitialValueManager */
	public InitialValueManager()
	{
		theInitializedSessions = new java.util.HashSet<Integer>();
	}

	/**
	 * Creates an initialized InitialValueManager
	 * 
	 * @param app The application that this manager is for
	 * @param property The property that this manager is to set initially
	 * @param value The value to set as the initial value in all sessions
	 */
	public InitialValueManager(prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property, T value)
	{
		super(app, property);
		theInitializedSessions = new java.util.HashSet<Integer>();
		theValue = value;
	}

	@Override
	public void configure(prisms.arch.PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		super.configure(app, config);
		if(theValue != null)
			return;
		Persister<T> persister = null;
		prisms.arch.PrismsConfig persisterEl = config.subConfig("persister");
		if(persisterEl != null)
			persister = PersistingPropertyManager.createPersister(persisterEl, app, getProperty());
		if(persister != null)
		{
			try
			{
				theValue = persister.getValue();
			} catch(Throwable e)
			{
				log.error("Could not deserialize property " + getProperty(), e);
				return;
			}
		}
		else if("true".equals(config.get("initNull")))
			theValue = null;
		else if(getProperty().getType().isArray())
			theValue = (T) java.lang.reflect.Array.newInstance(getProperty().getType()
				.getComponentType(), 0);
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

	/**
	 * Sets the value to set for the initial value in each session
	 * 
	 * @param <V> The type of value to set
	 * @param value The value to set
	 */
	public <V extends T> void setValue(V value)
	{
		theValue = value;
	}

	@Override
	public synchronized void propertyChange(prisms.arch.event.PrismsPCE<T> evt)
	{
		// Do nothing
	}

	@Override
	public T getApplicationValue(prisms.arch.PrismsApplication app)
	{
		// Just an initial value--this doesn't count as an application-wide value
		return null;
	}

	@Override
	public T getCorrectValue(PrismsSession session)
	{
		return theValue;
	}

	@Override
	public <V extends T> boolean isValueCorrect(PrismsSession session, V val)
	{
		if(theValue == null)
			return true;
		if(!theInitializedSessions.contains(Integer.valueOf(session.hashCode())))
		{
			theInitializedSessions.add(Integer.valueOf(session.hashCode()));
			return false;
		}
		return true;
	}
}
