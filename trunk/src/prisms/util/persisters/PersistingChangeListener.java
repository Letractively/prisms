/**
 * OOMChangeListener.java Created Mar 27, 2008 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;

import prisms.arch.PrismsSession;

/**
 * Registered with the application by {@link PersistingPropertyManager} to listen for changes to an
 * element in a managed property set
 * 
 * @param <T> The type of property to persist
 */
public class PersistingChangeListener<T> implements prisms.arch.event.ConfiguredPEL
{
	private static final Logger log = Logger.getLogger(PersistingChangeListener.class);

	private PrismsSession theSession;

	private Class<T> theType;

	private prisms.arch.event.PrismsProperty<T> theProperty;

	private String theEventProperty;

	/**
	 * @see prisms.arch.event.ConfiguredPEL#configure(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void configure(PrismsSession session, org.dom4j.Element configEl)
	{
		theSession = session;
		try
		{
			theType = (Class<T>) Class.forName(configEl.elementText("type"));
		} catch(Throwable e)
		{
			throw new IllegalStateException("Could not instantiate type "
				+ configEl.elementText("type"), e);
		}
		theProperty = prisms.arch.event.PrismsProperty.get(configEl.elementText("persistProperty"),
			theType);
		theEventProperty = configEl.elementText("eventProperty");
	}

	/**
	 * @return The session that this listener listens to
	 */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/**
	 * @return The property that this listener persistes
	 */
	public prisms.arch.event.PrismsProperty<T> getProperty()
	{
		return theProperty;
	}

	/**
	 * @return The name of the property to get in the events
	 */
	public String getEventProperty()
	{
		return theEventProperty;
	}

	/**
	 * @see prisms.arch.event.PrismsEventListener#eventOccurred(prisms.arch.event.PrismsEvent)
	 */
	public void eventOccurred(prisms.arch.event.PrismsEvent evt)
	{
		if(evt.getProperty("persisted") != null
			&& ((Boolean) evt.getProperty("persisted")).booleanValue())
			return;
		evt.setProperty("persisted", new Boolean(true));

		Object oo = evt.getProperty(theEventProperty);
		if(oo == null)
		{
			log.warn("Change event " + evt.name + "does not have specified event property: "
				+ theEventProperty);
			return;
		}
		prisms.arch.event.PropertyManager<?> [] propMgrs = theSession.getApp().getManagers(
			theProperty);
		for(int pm = 0; pm < propMgrs.length; pm++)
			if(propMgrs[pm] instanceof PersistingPropertyManager<?>)
				((PersistingPropertyManager<T>) propMgrs[pm]).changeValue(theSession, theSession
					.getProperty(theProperty), oo);
	}
}
