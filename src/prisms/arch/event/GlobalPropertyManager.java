/*
 * GlobalPropertyManager.java Created Oct 20, 2010 by Andrew Butler, PSL
 */
package prisms.arch.event;

import org.apache.log4j.Logger;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.util.DualKey;

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
 * {@link #eventOccurred(prisms.arch.PrismsSession, prisms.arch.event.PrismsEvent, Object)
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
	public static final String CAUSE_APP = "causeUser";

	private static class EventProperty
	{
		final String eventProp;

		final Class<?> propType;

		final java.lang.reflect.AccessibleObject[] theReflectPath;

		EventProperty(String prop)
		{
			this(prop, null, null);
		}

		EventProperty(String prop, Class<?> type, java.lang.reflect.AccessibleObject[] reflectPath)
		{
			eventProp = prop;
			propType = type;
			theReflectPath = reflectPath;
		}

		static EventProperty compile(String propName, Class<?> type, String [] reflectPath)
			throws SecurityException, NoSuchMethodException, NoSuchFieldException
		{
			Class<?> type_i = type;
			java.lang.reflect.AccessibleObject[] realPath = new java.lang.reflect.AccessibleObject [reflectPath.length];
			for(int i = 0; i < reflectPath.length; i++)
			{
				if(reflectPath[i].endsWith("()"))
				{
					String methodName = reflectPath[i].substring(0, reflectPath[i].length() - 2)
						.trim();
					java.lang.reflect.Method method = type_i.getMethod(methodName);
					realPath[i] = method;
					type_i = method.getReturnType();
				}
				else
				{
					java.lang.reflect.Field field = type_i.getField(reflectPath[i]);
					realPath[i] = field;
					type_i = field.getType();
				}
			}
			return new EventProperty(propName, type, realPath);
		}
	}

	private java.util.HashMap<DualKey<PrismsApplication, String>, EventProperty> theEventProperties;

	private String GLOBALIZED_PROPERTY;

	/**
	 * Creates a GlobalPropertyManager that must be configured to get its application and target
	 * property name
	 */
	public GlobalPropertyManager()
	{
		theEventProperties = new java.util.HashMap<DualKey<PrismsApplication, String>, EventProperty>();
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
	public void configure(PrismsApplication app, org.dom4j.Element configEl)
	{
		super.configure(app, configEl);
		java.util.List<org.dom4j.Element> eventEls = configEl.elements("changeEvent");
		for(org.dom4j.Element eventEl : eventEls)
			registerEventListener(app, eventEl);
	}

	/**
	 * Registers a listener for an event that is relevant to this property
	 * 
	 * @param app The application to register the listener in
	 * @param eventEl The element specifying how the listener will behave
	 */
	protected void registerEventListener(prisms.arch.PrismsApplication app,
		org.dom4j.Element eventEl)
	{
		String eventName = eventEl.elementTextTrim("name");
		if(eventName == null)
			throw new IllegalArgumentException("Cannot listen for change event on property "
				+ getProperty() + ". No event name specified");

		String propName = eventEl.elementTextTrim("eventProperty");
		if(propName != null)
		{
			EventProperty ep;
			String className = eventEl.elementTextTrim("valueType");
			String pathStr = eventEl.elementTextTrim("path");
			if(className != null)
			{
				try
				{
					Class<?> type = Class.forName(className);
					if(pathStr == null)
						ep = new EventProperty(propName, type, null);
					else
					{
						String [] pathSplit;
						if(pathStr.contains("."))
							pathSplit = pathStr.split("\\.");
						else
							pathSplit = new String [] {pathStr};
						for(int p = 0; p < pathSplit.length; p++)
							pathSplit[p] = pathSplit[p].trim();
						ep = EventProperty.compile(propName, type, pathSplit);
					}
				} catch(Throwable e)
				{
					log.error("Could not compile reflection path for event " + eventName
						+ " on global property " + getProperty().getName(), e);
					ep = null;
				}
			}
			else if(pathStr != null)
			{
				log.error("In order to evaluate a reflection path on an event property,"
					+ " the valueType element must also be specified");
				ep = null;
			}
			else
				ep = new EventProperty(propName);
			if(ep != null)
			{
				DualKey<PrismsApplication, String> key = new DualKey<PrismsApplication, String>(
					app, eventName);
				if(theEventProperties.get(key) != null)
					log.error("Multiple event handlers registered for event " + eventName
						+ " on global property " + getProperty().getName() + " for application "
						+ app.getName() + ". Using last entry.");
				theEventProperties.put(key, ep);
			}
		}

		app.addGlobalEventListener(eventName, this);
	}

	public final void eventOccurred(PrismsSession session, prisms.arch.event.PrismsEvent evt)
	{
		if(Boolean.TRUE.equals(evt.getProperty(GLOBALIZED_PROPERTY)))
			return;
		evt.setProperty(GLOBALIZED_PROPERTY, Boolean.TRUE);

		EventProperty prop = theEventProperties.get(new DualKey<PrismsApplication, String>(session
			.getApp(), evt.name));
		if(prop != null)
		{
			Object oProp = evt.getProperty(prop.eventProp);
			if(prop.propType != null)
				if(!prop.propType.isInstance(oProp))
				{
					log.error("Property " + prop.eventProp + " of event " + evt.name
						+ " on property " + getProperty().getName() + " is not an instance of "
						+ prop.propType.getName());
					prop = null;
				}
			if(prop != null && prop.theReflectPath != null)
				try
				{
					for(java.lang.reflect.AccessibleObject ao : prop.theReflectPath)
					{
						if(ao instanceof java.lang.reflect.Method)
							oProp = ((java.lang.reflect.Method) ao).invoke(oProp);
						else if(ao instanceof java.lang.reflect.Field)
							oProp = ((java.lang.reflect.Field) ao).get(oProp);
						else
							throw new IllegalStateException();
					}
				} catch(Exception e)
				{
					log.error("Could not evaluate reflection path for event " + evt.name
						+ " on property " + getProperty().getName(), e);
					prop = null;
				}
			if(prop != null)
				eventOccurred(session, evt, oProp);
		}

		if(!Boolean.TRUE.equals(evt
			.getProperty(prisms.arch.PrismsApplication.GLOBALIZED_EVENT_PROPERTY)))
			session.getApp().fireGlobally(session, evt);
		if(evt.getProperty(CAUSE_USER) == null)
			evt.setProperty(CAUSE_USER, session.getUser());
		if(evt.getProperty(CAUSE_APP) == null)
			evt.setProperty(CAUSE_APP, session.getApp());
		fireGlobalEvent(session.getApp(), evt.name, evt.getPropertyList());
	}

	/**
	 * Notifies this manager that a relevant event has occurred
	 * 
	 * @param session The session in which the event is being fired. This may or may not be the
	 *        session in which the event occurred, but this method is guaranteed to only be called
	 *        once for each event, regardless of the number of sessions in the application.
	 * @param evt The event that was fired
	 * @param eventValue The value of the property registered to the event from the app config XML
	 */
	protected abstract void eventOccurred(prisms.arch.PrismsSession session,
		prisms.arch.event.PrismsEvent evt, Object eventValue);

	/** Calls {@link prisms.arch.event.PropertyManager#changeValues(PrismsSession, PrismsPCE)} */
	void superChangeValues(PrismsSession session, final PrismsPCE<T> evt)
	{
		super.changeValues(session, evt);
	}

	@Override
	public void changeValues(PrismsSession session, final PrismsPCE<T> evt)
	{
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
			globalAdjustValues(evt.getApp(), session, evt.getPropertyList());
		}
	}
}
