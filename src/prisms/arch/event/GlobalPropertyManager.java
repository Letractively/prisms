/*
 * GlobalPropertyManager.java Created Oct 20, 2010 by Andrew Butler, PSL
 */
package prisms.arch.event;

import org.apache.log4j.Logger;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;

/**
 * A property manager for a global property. This manager adds the ability to listen to events that
 * are relevant to the property (as given in the XML app config) and perform actions based on those
 * events. The default action is to globalize the event so that all sessions receive it.
 * 
 * In the XML element for this listener, there may be any number of &lt;changeEvent&gt; elements,
 * each of which may look like:
 * 
 * <pre>
 * &lt;changeEvent&gt;
 * 	&lt;name&gt;Event Name&lt;/name&gt;
 * 	&lt;eventProperty&gt;Event Property&lt;/eventProperty&gt;
 * 	&lt;valueType&gt;qualified.value.Type&lt;/valueType&gt;
 * 	&lt;path&gt;Reflection Path&lt;/path&gt;
 * &lt;/changeEvent&gt;
 * </pre>
 * 
 * where
 * <ul>
 * <li>"Event Name" is the name of the event to listen for that affects the property</li>
 * <li>"Event Property" (optional) is the property name in the event under which is the item that is
 * part of this listener's property</li>
 * <li>"qualified.value.Type" (optional) is the type of the event property</li>
 * <li>"Reflection Path" (optional) is the path to use to get from the event property to the value
 * to send to the
 * {@link #eventOccurred(PrismsApplication, PrismsSession, prisms.arch.event.PrismsEvent, Object)
 * eventOccurred} method. The path consists of the names of methods (suffixed by "()") and fields,
 * separated by dots, to evaluate in sequence that will return the value to send to the method. As
 * an example: "getParent().parent" would cause the method named "getParent" to be evaluated on the
 * event's property, and the "parent" field would be retrieved from the return value of that method.
 * The value of this field would be sent to the eventOccurred method as the last argument. The
 * valueType element must be present for a reflection path to be evaluated. The valueType refers to
 * the type of the property in the event, not the type that is sent to the eventOccurred method.</li>
 * </ul>
 * 
 * @param <T> The type of the property.
 */
public abstract class GlobalPropertyManager<T> extends prisms.arch.event.PropertyManager<T>
	implements prisms.arch.event.PrismsEventListener
{
	private static final Logger log = Logger.getLogger(GlobalPropertyManager.class);

	/**
	 * The user that caused the change represented by a global event. This property may be present
	 * in property change events and PRISMS events that are a result of a change to the managed
	 * property.
	 */
	public static final String CAUSE_USER = "causeUser";

	/**
	 * The application where a global change event was caused from. This property may be present in
	 * property change events and PRISMS events that are a result of a change to the managed
	 * property.
	 */
	public static final String CAUSE_APP = "causeApp";

	/** Represents a type of event that can affect the value of a global property */
	public static class ChangeEvent
	{
		private final String theEventName;

		private final String theEventProp;

		private final Class<?> thePropType;

		private final prisms.util.ReflectionPath<?> theReflectPath;

		private final boolean isRecursive;

		ChangeEvent(String eventName, String eventProp, boolean recursive)
		{
			this(eventName, eventProp, null, null, recursive);
		}

		ChangeEvent(String eventName, String eventProp, Class<?> type,
			prisms.util.ReflectionPath<?> reflectPath, boolean recursive)
		{
			theEventName = eventName;
			theEventProp = eventProp;
			thePropType = type;
			theReflectPath = reflectPath;
			isRecursive = recursive;
		}

		/** @return The name of this event */
		public String getEventName()
		{
			return theEventName;
		}

		/** @return The name of the property within the event where the changed value is stored */
		public String getEventProperty()
		{
			return theEventProp;
		}

		/** @return The type of the event property, if this is checked */
		public Class<?> getPropertyType()
		{
			return thePropType;
		}

		/**
		 * @return The reflection path to follow from the event property to a value of the manager's
		 *         property's type
		 */
		public prisms.util.ReflectionPath<?> getReflectPath()
		{
			return theReflectPath;
		}

		/**
		 * @return Whether events of this type represent a change to the event property's structure
		 *         or just the event property itself
		 */
		public boolean isRecursive()
		{
			return isRecursive;
		}

		static ChangeEvent compile(String eventName, String eventProp, Class<?> propType,
			String [] reflectPath, boolean recursive) throws SecurityException,
			NoSuchMethodException, NoSuchFieldException
		{
			return new ChangeEvent(eventName, eventProp, propType,
				prisms.util.ReflectionPath.compile(propType, reflectPath), recursive);
		}
	}

	private java.util.LinkedHashMap<String, ChangeEvent> theEventProperties;

	private String GLOBALIZED_PROPERTY;

	private boolean isConfigured;

	/**
	 * Creates a GlobalPropertyManager that must be configured to get its application and target
	 * property name
	 */
	public GlobalPropertyManager()
	{
		theEventProperties = new java.util.LinkedHashMap<String, ChangeEvent>();
		GLOBALIZED_PROPERTY = "PropertyGlobalizedEvent" + Integer.toHexString(hashCode());
	}

	/**
	 * Creates a GlobalPropertyManager with its application and target property name
	 * 
	 * @param app The application to manage the property in
	 * @param prop The name of the property to manage
	 */
	public GlobalPropertyManager(PrismsApplication app, prisms.arch.event.PrismsProperty<T> prop)
	{
		super(app, prop);
	}

	/** @return The event property that marks an event as dealt with by this manager */
	public String getGlobalizedProperty()
	{
		return GLOBALIZED_PROPERTY;
	}

	@Override
	public void configure(PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		super.configure(app, config);
		if(!isConfigured)
		{
			for(prisms.arch.PrismsConfig eventConfig : config.subConfigs("changeEvent"))
				registerEventListener(eventConfig);
			isConfigured = true;
		}
		for(String eventName : theEventProperties.keySet())
			app.addGlobalEventListener(eventName, this);
	}

	/**
	 * Registers a listener for an event that is relevant to this property
	 * 
	 * @param eventConfig The configuration specifying how the listener will behave
	 */
	protected void registerEventListener(prisms.arch.PrismsConfig eventConfig)
	{
		ChangeEvent evt;
		try
		{
			evt = parseChangeEvent(eventConfig);
		} catch(IllegalArgumentException e)
		{
			IllegalArgumentException toThrow = new IllegalArgumentException(
				"Could not parse change event \n" + eventConfig + "\nfor property " + getProperty()
					+ ": " + e.getMessage(), e.getCause());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}

		if(theEventProperties.get(evt.getEventName()) != null)
			log.error("Multiple event handlers registered for event " + evt.getEventName()
				+ " on global property " + getProperty().getName() + ". Using last entry.");
		theEventProperties.put(evt.getEventName(), evt);
	}

	/**
	 * @return All event types that this property manager responds to--typically those that may
	 *         affect this property's value
	 */
	public ChangeEvent [] getChangeEvents()
	{
		return theEventProperties.values().toArray(new ChangeEvent [theEventProperties.size()]);
	}

	public final void eventOccurred(PrismsSession session, prisms.arch.event.PrismsEvent evt)
	{
		if(!applies(evt))
			return;
		if(Boolean.TRUE.equals(evt.getProperty(GLOBALIZED_PROPERTY)))
			return;
		evt.setProperty(GLOBALIZED_PROPERTY, Boolean.TRUE);

		ChangeEvent changeEvent = theEventProperties.get(evt.name);
		if(changeEvent != null)
		{
			Object oProp = evt.getProperty(changeEvent.getEventProperty());
			if(changeEvent.getPropertyType() != null)
				if(!changeEvent.getPropertyType().isInstance(oProp))
				{
					log.error("Property " + changeEvent.getPropertyType() + " of event " + evt.name
						+ " on property " + getProperty().getName() + " is not an instance of "
						+ changeEvent.getPropertyType().getName());
					changeEvent = null;
				}
			if(changeEvent != null && changeEvent.getReflectPath() != null)
				try
				{
					oProp = ((prisms.util.ReflectionPath<Object>) changeEvent.getReflectPath())
						.follow(oProp);
				} catch(Exception e)
				{
					log.error("Could not evaluate reflection path for event " + evt.name
						+ " on property " + getProperty().getName(), e);
					changeEvent = null;
				}
			if(changeEvent != null)
			{
				PrismsApplication app;
				if(session != null)
					app = session.getApp();
				else
					app = (PrismsApplication) evt.getProperty("globalEventApp");
				prisms.util.ProgramTracker.TrackNode track = null;
				prisms.arch.PrismsTransaction trans = getEnv().getTransaction();
				if(trans != null)
					track = trans.getTracker().start(
						"PRISMS: Property " + getProperty() + " event " + evt.name
							+ " processed by property manager");
				try
				{
					eventOccurred(app, session, evt, oProp);
				} finally
				{
					if(track != null)
						trans.getTracker().end(track);
				}
			}
		}

		if(session != null)
		{
			if(!Boolean.TRUE.equals(evt
				.getProperty(prisms.arch.PrismsApplication.GLOBALIZED_EVENT_PROPERTY)))
				session.getApp().fireGlobally(session, evt, true);
			if(evt.getProperty(CAUSE_USER) == null)
				evt.setProperty(CAUSE_USER, session.getUser());
			if(evt.getProperty(CAUSE_APP) == null)
				evt.setProperty(CAUSE_APP, session.getApp());
			fireGlobalEvent(session.getApp(), evt.name, evt.getPropertyList());
		}
	}

	/**
	 * Notifies this manager that a relevant event has occurred
	 * 
	 * @param app The application in which the event is being fired. This may or may not be the
	 *        application in which the event occurred, but this method is guaranteed to only be
	 *        called once for each event, regardless of the number of sessions in the application.
	 * @param session The session in which the event is being fired. This may or may not be the
	 *        session in which the event occurred, but this method is guaranteed to only be called
	 *        once for each event, regardless of the number of sessions in the application. <b>THIS
	 *        ARGUMENT MAY BE NULL</b> if the event was fired globally and there are no sessions in
	 *        the application.
	 * @param evt The event that was fired
	 * @param eventValue The value of the property registered to the event from the app config XML
	 */
	protected abstract void eventOccurred(PrismsApplication app, PrismsSession session,
		prisms.arch.event.PrismsEvent evt, Object eventValue);

	/** Calls {@link prisms.arch.event.PropertyManager#changeValues(PrismsSession, PrismsPCE)} */
	void superChangeValues(PrismsSession session, final PrismsPCE<T> evt)
	{
		super.changeValues(session, evt);
	}

	@Override
	public void changeValues(PrismsSession session, final PrismsPCE<T> evt)
	{
		if(Boolean.TRUE.equals(evt.get(GLOBALIZED_PROPERTY)))
			return;
		evt.set(GLOBALIZED_PROPERTY, Boolean.TRUE);

		evt.getApp().runSessionTask(session, new prisms.arch.PrismsApplication.SessionTask()
		{
			public void run(PrismsSession s)
			{
				superChangeValues(s, evt);
			}
		}, false);
		T appData = getApplicationValue(evt.getApp());
		if(appData != null)
		{
			if(session != null && evt.get(CAUSE_USER) == null)
				evt.set(CAUSE_USER, session.getUser());
			if(evt.get(CAUSE_APP) == null)
				evt.set(CAUSE_APP, evt.getApp());
			globalAdjustValues(evt.getPropertyList());
		}
	}

	/**
	 * Allows subclasses to discriminate between events with the same name
	 * 
	 * @param evt the event to check
	 * @return Whether the event applies to this property manager's data set
	 */
	protected boolean applies(PrismsEvent evt)
	{
		return true;
	}

	/**
	 * Parses a change event from a configuration
	 * 
	 * @param config The event configuration
	 * @return The change event
	 * @throws IllegalArgumentException If the configuration cannot be parsed
	 */
	public static ChangeEvent parseChangeEvent(prisms.arch.PrismsConfig config)
		throws IllegalArgumentException
	{
		String eventName = config.get("name");
		if(eventName == null)
			throw new IllegalArgumentException("No event name specified.");

		String propName = config.get("eventProperty");
		if(propName == null)
			throw new IllegalArgumentException("Event property not specified.");
		ChangeEvent ep;
		String className = config.get("valueType");
		String pathStr = config.get("path");
		if(className != null)
		{
			Class<?> type;
			try
			{
				type = Class.forName(className);
			} catch(ClassNotFoundException e)
			{
				throw new IllegalArgumentException("No such type: " + className);
			}
			if(pathStr == null)
				ep = new ChangeEvent(eventName, propName, type, null, config.is("recursive", true));
			else
			{
				String [] pathSplit;
				if(pathStr.contains("."))
					pathSplit = pathStr.split("\\.");
				else
					pathSplit = new String [] {pathStr};
				for(int p = 0; p < pathSplit.length; p++)
					pathSplit[p] = pathSplit[p].trim();
				try
				{
					ep = ChangeEvent.compile(eventName, propName, type, pathSplit,
						config.is("recursive", true));
				} catch(Exception e)
				{
					throw new IllegalArgumentException("Could not compile path", e);
				}
			}
		}
		else if(pathStr != null)
		{
			throw new IllegalArgumentException(
				"In order to evaluate a reflection path on an event property,"
					+ " the valueType element must also be specified");
		}
		else
			ep = new ChangeEvent(eventName, propName, config.is("recursive", true));
		return ep;
	}
}
