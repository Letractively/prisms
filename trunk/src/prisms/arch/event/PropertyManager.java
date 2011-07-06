/**
 * PropertyManager.java Created Oct 25, 2007 by Andrew Butler, PSL
 */
package prisms.arch.event;

import org.apache.log4j.Logger;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;

/**
 * Manages a property between {@link PrismsSession}s in a {@link PrismsApplication}
 * 
 * @param <T> the type of this manager
 */
public abstract class PropertyManager<T> implements PrismsPCL<T>
{
	private static final Logger log = Logger.getLogger(PropertyManager.class);

	private PrismsProperty<T> theProperty;

	private prisms.arch.PrismsEnv theEnv;

	private java.util.ArrayList<PrismsApplication> theApps;

	private DataInspector theInspector;

	/**
	 * Creates a PropertyManager that must be configured to get its application and target property
	 * name
	 */
	public PropertyManager()
	{
		theApps = new java.util.ArrayList<PrismsApplication>();
	}

	/**
	 * Creates a PropertyManager with its application and target property name
	 * 
	 * @param app The application to manage the property in
	 * @param prop The name of the property to manage
	 */
	public PropertyManager(PrismsApplication app, PrismsProperty<T> prop)
	{
		this();
		theProperty = prop;
		theApps.add(app);
	}

	/**
	 * Configures this PropertyManager
	 * 
	 * @param app The application to manage the property in
	 * @param config The configuration with potential subclass-specific settings
	 */
	public synchronized void configure(PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		if(theEnv == null)
			theEnv = app.getEnvironment();
		synchronized(theApps)
		{
			theApps.add(app);
		}
		if(theProperty == null)
		{
			String propField = config.get("field");
			if(propField != null)
			{
				int lastDot = propField.lastIndexOf('.');
				try
				{
					Class<?> clazz = Class.forName(propField.substring(0, lastDot));
					theProperty = (PrismsProperty<T>) clazz.getField(
						propField.substring(lastDot + 1)).get(null);
				} catch(Throwable e)
				{
					throw new IllegalArgumentException(
						"Could not configure property manager--field configured incorrectly", e);
				}
			}
			else
			{
				String propName = config.get("name");
				try
				{
					theProperty = PrismsProperty.get(propName,
						(Class<T>) config.getClass("type", Object.class));
				} catch(Exception e)
				{
					throw new IllegalArgumentException(
						"Could not configure property manager--name/type configured incorrectly", e);
				}
			}
		}
		prisms.arch.PrismsConfig inspectorConfig = config.subConfig("inspector");
		if(theInspector == null && inspectorConfig != null)
		{
			try
			{
				theInspector = inspectorConfig.getClass("class", DataInspector.class).newInstance();
				theInspector.configure(theProperty, inspectorConfig);
			} catch(Exception e)
			{
				log.error("Could not instantiate inspector for configuration " + inspectorConfig, e);
			}
		}
	}

	/** @return The property that this manager manages */
	public PrismsProperty<T> getProperty()
	{
		return theProperty;
	}

	/** @return The PRISMS environment that this manager is set in */
	public prisms.arch.PrismsEnv getEnv()
	{
		return theEnv;
	}

	/**
	 * @return This property manager's inspector that allows a manager to inspect the value(s) in
	 *         this property
	 */
	public DataInspector getInspector()
	{
		return theInspector;
	}

	public void propertyChange(PrismsPCE<T> evt)
	{
		changeValues(evt.getSession(), evt);
	}

	/**
	 * Checks the value of the property being set in a session and takes action, if necessary, to
	 * ensure that all sessions keep the proper value
	 * 
	 * @param session The session where the property has been changed
	 * @param evt The event that represents the change
	 */
	public void changeValues(PrismsSession session, PrismsPCE<T> evt)
	{
		if(session == null)
			return;
		if(Boolean.TRUE.equals(evt.get("canceled")))
			return;
		checkValue(session, session.getProperty(getProperty()), evt);
	}

	/**
	 * Ensures that the session's property is correct according to this manager
	 * 
	 * @param session The session to set the property of
	 * @param value The session's current value of this manager's property
	 * @param evt The event that caused this check
	 */
	protected void checkValue(PrismsSession session, T value, PrismsPCE<T> evt)
	{
		if(!isValueCorrect(session, value))
			session.setProperty(getProperty(), getCorrectValue(session));
	}

	/**
	 * Adjusts the values of this property in every session of every application which this manager
	 * manages
	 * 
	 * @param eventProps The properties of the event to fire
	 */
	protected void globalAdjustValues(Object... eventProps)
	{
		for(PrismsApplication app : theApps)
		{
			app.setGlobalProperty(theProperty, getApplicationValue(app), eventProps);
			// app.runSessionTask(null, new PrismsApplication.SessionTask()
			// {
			// public void run(PrismsSession session2)
			// {
			// if(!isValueCorrect(session2, session2.getProperty(getProperty())))
			// session2.setProperty(getProperty(), getCorrectValue(session2));
			// }
			// }, false);
		}
	}

	/**
	 * Fires an event in every application that this manager manages except the application given
	 * 
	 * @param app The application to not fire the event in
	 * @param name The name of the event to fire
	 * @param eventProps The properties of the event to fire
	 */
	protected void fireGlobalEvent(PrismsApplication app, String name, Object... eventProps)
	{
		for(PrismsApplication app2 : theApps)
			if(app2 != app)
				app2.fireGlobally(null, new PrismsEvent(name, eventProps));
	}

	/**
	 * Called by the AppConfig after all of the registered properties for an application are
	 * deserialized and set. This is intended to be used for linking--so that property elements
	 * containing elements of other properties may be properly linked. By default, this does
	 * nothing.
	 * 
	 * @param app The application whose properties have been set
	 */
	public void propertiesSet(PrismsApplication app)
	{
	}

	/**
	 * @param app The application to get the values for
	 * @return All data for this manager's property that is available to the application. This may
	 *         return null if no data is available to the application as a whole--that is, if the
	 *         managed property's value is completely session-specific
	 */
	public abstract T getApplicationValue(PrismsApplication app);

	/**
	 * @param <V> The type of value to set
	 * @param session The session to check
	 * @param val The value to check
	 * @return Whether the given value is correct for the given session
	 */
	public abstract <V extends T> boolean isValueCorrect(PrismsSession session, V val);

	/**
	 * Called if a session is found to have an incorrect value
	 * 
	 * @param session The session to get the correct property value for
	 * @return The correct value to set for the session's value of this manager's property
	 * @see #isValueCorrect(PrismsSession, Object)
	 */
	public abstract T getCorrectValue(PrismsSession session);

	@Override
	public String toString()
	{
		String className = getClass().getName();
		int idx = className.lastIndexOf('.');
		return className.substring(idx + 1) + " manager for property " + theProperty;
	}
}
