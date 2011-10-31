/*
 * ClientConfig.java Created Aug 26, 2008 by Andrew Butler, PSL
 */
package prisms.arch;

import java.util.ArrayList;

import org.apache.log4j.Logger;

import prisms.arch.event.PrismsEventListener;
import prisms.arch.event.SessionMonitor;

/** Represents a client configuration for creation of sessions for a class of client */
public class ClientConfig
{
	private static final Logger log = Logger.getLogger(ClientConfig.class);

	static class EventListenerType
	{
		final String theEventName;

		final Class<? extends PrismsEventListener> theListenerType;

		final PrismsConfig theConfig;

		EventListenerType(String eventName, Class<? extends PrismsEventListener> listenerType,
			PrismsConfig config)
		{
			theEventName = eventName;
			theListenerType = listenerType;
			theConfig = config;
		}
	}

	static class MonitorType
	{
		final Class<? extends SessionMonitor> theMonitorType;

		final PrismsConfig theConfig;

		MonitorType(Class<? extends SessionMonitor> monitorType, PrismsConfig config)
		{
			theMonitorType = monitorType;
			theConfig = config;
		}
	}

	static class PluginType
	{
		final String thePluginName;

		final Class<? extends AppPlugin> thePluginType;

		final PrismsConfig theConfig;

		PluginType(String eventName, Class<? extends AppPlugin> listenerType, PrismsConfig config)
		{
			thePluginName = eventName;
			thePluginType = listenerType;
			theConfig = config;
		}
	}

	private final PrismsApplication theApp;

	private final String theName;

	private final String theDescrip;

	private final boolean isService;

	private final boolean isCommonSession;

	private final boolean allowsAnonymous;

	private long theTimeout;

	private RemoteEventSerializer theSerializer;

	private ArrayList<EventListenerType> theEventTypes;

	private ArrayList<MonitorType> theMonitorTypes;

	private java.util.HashMap<String, PluginType> thePluginTypes;

	private Object theConfigurator;

	private boolean isConfigured;

	/**
	 * Creates a ClientConfig
	 * 
	 * @param app The application that this client config is for
	 * @param name This client config's name
	 * @param descrip A description for this client config
	 * @param service Whether this client config is for a web service as opposed to a UI client
	 * @param commonSession Whether this client config uses a common session per user for all
	 *        clients accessing it
	 * @param allowAnonymous Whether this client config allows anonymous users
	 * @param configurator The object that is responsible for configuring this client
	 */
	public ClientConfig(PrismsApplication app, String name, String descrip, boolean service,
		boolean commonSession, boolean allowAnonymous, Object configurator)
	{
		theApp = app;
		theName = name;
		theDescrip = descrip;
		isService = service;
		isCommonSession = commonSession;
		allowsAnonymous = allowAnonymous;
		theTimeout = -1;
		theConfigurator = configurator;
		theEventTypes = new ArrayList<EventListenerType>();
		theMonitorTypes = new ArrayList<MonitorType>();
		thePluginTypes = new java.util.HashMap<String, PluginType>();
	}

	/** @return The application that this configuration is for */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/** @return Whether this client has been configured or not */
	public boolean isConfigured()
	{
		return isConfigured;
	}

	/**
	 * Marks this client as configured
	 * 
	 * @param config The object that is responsible for configuring this client. This must be the
	 *        same object as the configurator that was passed to the constructor.
	 */
	public void setConfigured(Object config)
	{
		if(theConfigurator == config)
		{
			theConfigurator = null;
			isConfigured = true;
		}
		else
			throw new IllegalArgumentException("Configurator is not correct");
	}

	/** @return The name of this configuration */
	public String getName()
	{
		return theName;
	}

	/** @return A description of this configuration */
	public String getDescription()
	{
		return theDescrip;
	}

	/**
	 * @return Whether this client serves as a web service in a request-response paradigm rather
	 *         than an event-driven one.
	 */
	public boolean isService()
	{
		return isService;
	}

	/**
	 * @return Whether this client configuration should only have a single session per user shared
	 *         by all clients accessing the client config through each user
	 */
	public boolean isCommonSession()
	{
		return isCommonSession;
	}

	/** @return Whether this client allows anonymous access without authentication */
	public boolean allowsAnonymous()
	{
		return allowsAnonymous;
	}

	/** @return The amount of inactive time until a session of this client should be expired */
	public long getSessionTimeout()
	{
		return theTimeout;
	}

	/** @param timeout The inactivity interval after which sessions of this client should be expired */
	public void setSessionTimeout(long timeout)
	{
		if(isConfigured)
			throw new IllegalStateException("Session timeout cannot be set after the client"
				+ " has been completely configured");
		theTimeout = timeout;
	}

	/** @return The event serializer that sessions of this client config will use */
	public RemoteEventSerializer getSerializer()
	{
		return theSerializer;
	}

	/** @param serializer The object that will serialize events going to the client */
	public void setSerializer(RemoteEventSerializer serializer)
	{
		if(isConfigured)
			throw new IllegalStateException("Event serializer cannot be set after the client"
				+ " has been completely configured");
		theSerializer = serializer;
	}

	/**
	 * Adds an event listener type to add to sessions created (in the future) with this client
	 * config
	 * 
	 * @param eventName The name of the event to listen for
	 * @param type The listener class to instantiate
	 * @param config The configuration to configure the instantiated listener
	 */
	public void addEventListenerType(String eventName, Class<? extends PrismsEventListener> type,
		PrismsConfig config)
	{
		if(isConfigured)
			throw new IllegalStateException("Event listener types cannot be added after the client"
				+ " has been completely configured");
		theEventTypes.add(new EventListenerType(eventName, type, config));
	}

	/**
	 * Adds a monitor type to add to sessions created (in the future) with this client config
	 * 
	 * @param type The monitor class to instantiate
	 * @param config The configuration to configure the instantiated monitor
	 */
	public void addMonitorType(Class<? extends SessionMonitor> type, PrismsConfig config)
	{
		if(isConfigured)
			throw new IllegalStateException("Monitor types cannot be added after the client"
				+ " has been completely configured");
		theMonitorTypes.add(new MonitorType(type, config));
	}

	/**
	 * Adds a plugin type to add to sessions created (in the future) with this client config
	 * 
	 * @param pluginName The name of the plugin
	 * @param type The plugin class to instantiate
	 * @param config The configuration for the instantiated plugin
	 */
	public void addPluginType(String pluginName, Class<? extends AppPlugin> type,
		PrismsConfig config)
	{
		if(isConfigured)
			throw new IllegalStateException("Plugin types cannot be added after the client"
				+ " has been completely configured");
		thePluginTypes.put(pluginName, new PluginType(pluginName, type, config));
	}

	/**
	 * Adds event listeners, monitors, and plugins to the session
	 * 
	 * @param session The session to configure
	 */
	public void configure(PrismsSession session)
	{
		PrismsTransaction trans = theApp.getEnvironment().getTransaction();
		prisms.util.ProgramTracker.TrackNode track = prisms.util.PrismsUtils.track(trans,
			"Add Event Listener");
		prisms.util.ProgramTracker.TrackNode track2;
		try
		{
			for(EventListenerType elt : theEventTypes)
			{
				log.debug("Adding event listener:\n" + elt.theConfig);
				PrismsEventListener pel;
				try
				{
					pel = elt.theListenerType.newInstance();
				} catch(Exception e)
				{
					log.error(
						"Could not instantiate event listener " + elt.theListenerType.getName(), e);
					return;
				}
				if(pel instanceof prisms.arch.event.ConfiguredPEL)
				{
					track2 = prisms.util.PrismsUtils.track(trans, pel);
					try
					{
						((prisms.arch.event.ConfiguredPEL) pel).configure(session, elt.theConfig);
					} finally
					{
						prisms.util.PrismsUtils.end(trans, track2);
					}
				}
				session.addEventListener(elt.theEventName, pel);
			}
		} finally
		{
			prisms.util.PrismsUtils.end(trans, track);
		}
		track = prisms.util.PrismsUtils.track(trans, "Add Session Monitor");
		try
		{
			for(MonitorType mt : theMonitorTypes)
			{
				log.debug("Adding session monitor:\n" + mt.theConfig);
				SessionMonitor sm;
				try
				{
					sm = mt.theMonitorType.newInstance();
				} catch(Exception e)
				{
					log.error(
						"Could not instantiate session monitor " + mt.theMonitorType.getName(), e);
					return;
				}
				track2 = prisms.util.PrismsUtils.track(trans, sm);
				try
				{
					sm.register(session, mt.theConfig);
				} finally
				{
					prisms.util.PrismsUtils.end(trans, track2);
				}
			}
		} finally
		{
			prisms.util.PrismsUtils.end(trans, track);
		}
		track = prisms.util.PrismsUtils.track(trans, "Add Plugin");
		try
		{
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
				track2 = prisms.util.PrismsUtils.track(trans, pt.thePluginName);
				try
				{
					plugin.initPlugin(session, pt.theConfig);
					session.removeOutgoingEvents(pt.thePluginName);
				} catch(Exception e)
				{
					log.error("Could not initialize plugin " + pt.thePluginName, e);
				} finally
				{
					prisms.util.PrismsUtils.end(trans, track2);
				}
				session.addPlugin(pt.thePluginName, plugin);
			}
		} finally
		{
			prisms.util.PrismsUtils.end(trans, track);
		}
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof ClientConfig && ((ClientConfig) o).theApp.equals(theApp)
			&& ((ClientConfig) o).theName.equals(theName);
	}

	@Override
	public int hashCode()
	{
		return theApp.hashCode() * 13 + theName.hashCode();
	}

	@Override
	public String toString()
	{
		return theApp.getName() + "/" + theName;
	}
}
