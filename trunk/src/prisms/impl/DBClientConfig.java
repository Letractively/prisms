/**
 * DBClientConfig.java Created Aug 26, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.dom4j.Element;

import prisms.arch.*;
import prisms.arch.event.PrismsEventListener;
import prisms.arch.event.SessionMonitor;

/**
 * A complete implementation of ClientConfig
 */
public class DBClientConfig implements ClientConfig
{
	private static final Logger log = Logger.getLogger(DBClientConfig.class);

	static class EventListenerType
	{
		final String theEventName;

		final Class<? extends PrismsEventListener> theListenerType;

		final org.dom4j.Element theConfigEl;

		EventListenerType(String eventName, Class<? extends PrismsEventListener> listenerType,
			Element configEl)
		{
			theEventName = eventName;
			theListenerType = listenerType;
			theConfigEl = configEl;
		}
	}

	static class MonitorType
	{
		final Class<? extends SessionMonitor> theMonitorType;

		final Element theConfigEl;

		MonitorType(Class<? extends SessionMonitor> monitorType, Element configEl)
		{
			theMonitorType = monitorType;
			theConfigEl = configEl;
		}
	}

	static class PluginType
	{
		final String thePluginName;

		final Class<? extends AppPlugin> thePluginType;

		final Element theConfigEl;

		PluginType(String eventName, Class<? extends AppPlugin> listenerType, Element configEl)
		{
			thePluginName = eventName;
			thePluginType = listenerType;
			theConfigEl = configEl;
		}
	}

	private int theID;

	private PrismsApplication theApp;

	private String theName;

	private String theDescrip;

	private long theTimeout;

	private String theSerializerClass;

	private RemoteEventSerializer theSerializer;

	private boolean isService;

	private boolean allowsAnonymous;

	private String theValidatorClass;

	private Validator theValidator;

	private ArrayList<EventListenerType> theEventTypes;

	private ArrayList<MonitorType> theMonitorTypes;

	private java.util.HashMap<String, PluginType> thePluginTypes;

	private boolean isConfigured;

	private boolean isDeleted;

	/**
	 * Stores a URL location to the XML
	 */
	private String theConfigXML;

	/**
	 * Creates a DBClientConfig
	 * 
	 * @param id The database ID of the config
	 * @param app The application that this client config is for
	 * @param name This client config's name
	 */
	public DBClientConfig(int id, PrismsApplication app, String name)
	{
		theID = id;
		theApp = app;
		theName = name;
		theTimeout = -1;
		theEventTypes = new ArrayList<EventListenerType>();
		theMonitorTypes = new ArrayList<MonitorType>();
		thePluginTypes = new java.util.HashMap<String, PluginType>();
	}

	/**
	 * @return This client config's database ID
	 */
	public int getID()
	{
		return theID;
	}

	/**
	 * @param id The database ID for this client config
	 */
	public void setID(int id)
	{
		theID = id;
	}

	/**
	 * @see prisms.arch.ClientConfig#getApp()
	 */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/**
	 * @see prisms.arch.ClientConfig#getName()
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * @param name The name for this client config
	 */
	public void setName(String name)
	{
		theName = name;
	}

	/**
	 * @see prisms.arch.ClientConfig#getDescription()
	 */
	public String getDescription()
	{
		return theDescrip;
	}

	/**
	 * @param descrip The description for this client config
	 */
	public void setDescription(String descrip)
	{
		theDescrip = descrip;
	}

	public long getSessionTimeout()
	{
		return theTimeout;
	}

	/**
	 * @param timeout The inactivity interval after which sessions of this client should be expired
	 */
	public void setSessionTimeout(long timeout)
	{
		theTimeout = timeout;
	}

	public boolean isService()
	{
		return isService;
	}

	/**
	 * @param service Whether this client serves as a web service in a request-response paradigm
	 *        rather than an event-driven one.
	 */
	public void setService(boolean service)
	{
		isService = service;
	}

	public RemoteEventSerializer getSerializer()
	{
		RemoteEventSerializer ret = theSerializer;
		if(ret != null)
			return ret;
		String className = theSerializerClass;
		if(className == null)
			return null;
		else
		{
			try
			{
				ret = Class.forName(className).asSubclass(RemoteEventSerializer.class)
					.newInstance();
			} catch(Throwable e)
			{
				ret = new PlaceholderSerializer(className);
			}
			theSerializer = ret;
		}
		return ret;
	}

	/**
	 * @param serializerClass The class name of the serializer that this client shall use
	 */
	public void setSerializerClass(String serializerClass)
	{
		theSerializerClass = serializerClass;
		theSerializer = null;
	}

	public boolean allowsAnonymous()
	{
		return allowsAnonymous;
	}

	/**
	 * @param allowed Whether this client should allow users to connect to this client anonymously
	 *        without authentication
	 */
	public void setAllowsAnonymous(boolean allowed)
	{
		allowsAnonymous = allowed;
	}

	public Validator getValidator()
	{
		Validator ret = theValidator;
		if(ret != null)
			return ret;
		String config = theValidatorClass;
		if(config != null)
		{
			try
			{
				ret = Class.forName(theValidatorClass).asSubclass(Validator.class).newInstance();
			} catch(Throwable e)
			{
				ret = new PlaceholderValidator(theValidatorClass);
			}
		}
		theValidator = ret;
		return ret;
	}

	/**
	 * @param validatorClass The class of the validator to allow this client to determine user
	 *        access in an implementation-specific way
	 */
	public void setValidatorClass(String validatorClass)
	{
		theValidatorClass = validatorClass;
		theValidator = null;
	}

	/**
	 * @return The path to the configuration XML that this client config will use to configure a
	 *         session
	 */
	public String getConfigXML()
	{
		return theConfigXML;
	}

	/**
	 * 
	 * @param configXML The path to the configuration XML that this client config should user to
	 *        configure a session
	 */
	public void setConfigXML(String configXML)
	{
		theConfigXML = configXML;
	}

	public boolean isConfigured()
	{
		return isConfigured;
	}

	public void setConfigured()
	{
		isConfigured = true;
	}

	/**
	 * @see prisms.arch.ClientConfig#addEventListenerType(java.lang.String, java.lang.Class,
	 *      org.dom4j.Element)
	 */
	public void addEventListenerType(String eventName, Class<? extends PrismsEventListener> type,
		Element configEl)
	{
		theEventTypes.add(new EventListenerType(eventName, type, configEl));
	}

	/**
	 * @see prisms.arch.ClientConfig#addMonitorType(java.lang.Class, org.dom4j.Element)
	 */
	public void addMonitorType(Class<? extends SessionMonitor> type, Element configEl)
	{
		theMonitorTypes.add(new MonitorType(type, configEl));
	}

	/**
	 * @see prisms.arch.ClientConfig#addPluginType(java.lang.String, java.lang.Class,
	 *      org.dom4j.Element)
	 */
	public void addPluginType(String pluginName, Class<? extends AppPlugin> type, Element configEl)
	{
		thePluginTypes.put(pluginName, new PluginType(pluginName, type, configEl));
	}

	/**
	 * @see prisms.arch.ClientConfig#configure(prisms.arch.PrismsSession)
	 */
	public void configure(PrismsSession session)
	{
		for(EventListenerType elt : theEventTypes)
		{
			log.debug("Adding event listener:\n" + elt.theConfigEl.asXML());
			PrismsEventListener pel;
			try
			{
				pel = elt.theListenerType.newInstance();
			} catch(Exception e)
			{
				log.error("Could not instantiate event listener " + elt.theListenerType.getName(),
					e);
				return;
			}
			if(pel instanceof prisms.arch.event.ConfiguredPEL)
				((prisms.arch.event.ConfiguredPEL) pel).configure(session, elt.theConfigEl);
			session.addEventListener(elt.theEventName, pel);
		}
		for(MonitorType mt : theMonitorTypes)
		{
			log.debug("Adding session monitor:\n" + mt.theConfigEl.asXML());
			SessionMonitor sm;
			try
			{
				sm = mt.theMonitorType.newInstance();
			} catch(Exception e)
			{
				log
					.error("Could not instantiate session monitor " + mt.theMonitorType.getName(),
						e);
				return;
			}
			sm.register(session, mt.theConfigEl);
		}
		for(PluginType pt : thePluginTypes.values())
		{
			AppPlugin plugin;
			try
			{
				plugin = pt.thePluginType.newInstance();
			} catch(Exception e)
			{
				log.error("Could not instantiate plugin " + pt.thePluginType.getName(), e);
				continue;
			}
			try
			{
				plugin.initPlugin(session, pt.theConfigEl);
				session.removeOutgoingEvents(pt.thePluginName);
			} catch(Exception e)
			{
				log.error("Could not initialize plugin " + pt.thePluginName, e);
			}
			session.addPlugin(pt.thePluginName, plugin);
		}
	}

	/**
	 * @return Whether this group is deleted
	 */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	public boolean equals(Object o)
	{
		return o instanceof DBClientConfig && ((DBClientConfig) o).theID == theID;
	}

	public int hashCode()
	{
		return theID;
	}
}
