/*
 * AppConfig.java Created Oct 24, 2007 by Andrew Butler, PSL
 */
package prisms.arch;

import org.apache.log4j.Logger;

import prisms.arch.event.PrismsEventListener;
import prisms.arch.event.PropertyManager;
import prisms.arch.event.SessionMonitor;

/** Configures a {@link PrismsApplication} and its clients and sessions */
public class AppConfig
{
	private static Logger log = Logger.getLogger(AppConfig.class);

	/** Creates the configurator */
	public AppConfig()
	{
	}

	/**
	 * Configures a PluginApplication using an XML config file
	 * 
	 * @param app The application to configure
	 * @param config The XML configuration
	 * @param withInitializers false if this method should not configure intitializers for the
	 *        application; true if this method should only configure intitializers; null if this
	 *        method should configure all listeners whether initializers or not
	 */
	public void configureApp(PrismsApplication app, PrismsConfig config, Boolean withInitializers)
	{
		synchronized(app)
		{
			if(app.isConfigured())
				return;
			prisms.util.TrackerSet.TrackConfig[] trackConfigs = null;
			PrismsConfig tracks = config.subConfig("tracking");
			if(tracks != null)
				trackConfigs = prisms.util.TrackerSet.parseTrackConfigs(tracks);
			if(trackConfigs == null)
				trackConfigs = app.getEnvironment().getTrackConfigs();
			if(trackConfigs != null)
				configureTracking(app, trackConfigs);
			java.util.Collection<PropertyManager<?>> newMgrs = new java.util.ArrayList<PropertyManager<?>>();
			for(PrismsConfig propConfig : config.subConfigs("properties/property"))
				try
				{
					if(testInitializer(propConfig, withInitializers))
						addPropertyManager(app, propConfig, withInitializers, newMgrs);
				} catch(RuntimeException e)
				{
					if(!propConfig.is("optional", false))
						throw e;
					else
						log.error("Could not add property manager: " + propConfig, e);
				}
			for(PropertyManager<?> pm : newMgrs)
			{
				try
				{
					pm.propertiesSet(app);
				} catch(Throwable e)
				{
					log.error("Properties set failed: ", e);
				}
			}
			for(PrismsConfig evtConfig : config.subConfigs("events/event"))
				try
				{
					if(testInitializer(evtConfig, withInitializers))
						addEventListener(app, null, evtConfig);
				} catch(Exception e)
				{
					log.error("Could not add event listener: " + evtConfig, e);
				}
			for(PrismsConfig monConfig : config.subConfigs("monitors/monitor"))
				try
				{
					if(testInitializer(monConfig, withInitializers))
						addMonitor(app, null, monConfig);
				} catch(Exception e)
				{
					log.error("Could not add monitor: " + monConfig, e);
				}
		}
	}

	private static boolean testInitializer(PrismsConfig config, Boolean withInitializers)
	{
		if(withInitializers == null)
			return true;
		return config.is("initializer", false) == withInitializers.booleanValue();
	}

	/**
	 * @param client The client to configure
	 * @param config The configuration XML
	 */
	public void configureClient(ClientConfig client, PrismsConfig config)
	{
		for(PrismsConfig evtConfig : config.subConfigs("events/event"))
			try
			{
				addEventListener(null, client, evtConfig);
			} catch(Exception e)
			{
				log.error("Could not add event listener: " + evtConfig, e);
			}
		for(PrismsConfig monConfig : config.subConfigs("monitors/monitor"))
			try
			{
				addMonitor(null, client, monConfig);
			} catch(Exception e)
			{
				log.error("Could not add monitor: " + monConfig, e);
			}
		for(PrismsConfig pluginConfig : config.subConfigs("plugins/plugin"))
			try
			{
				addPlugin(client, pluginConfig);
			} catch(Exception e)
			{
				log.error("Could not add plugin: " + pluginConfig, e);
			}
	}

	/**
	 * Configures a new session for an application. This method is empty but may be overridden by
	 * subclasses. Typically, initialization of sessions is done by the
	 * {@link PrismsApplication#configureSession(PrismsSession)} and
	 * {@link ClientConfig#configure(PrismsSession)} methods.
	 * 
	 * @param session The new session to configure
	 */
	public void configureSession(PrismsSession session)
	{
	}

	/**
	 * Configures an application's tracking
	 * 
	 * @param app The application to configure
	 * @param trackConfigs The track configs to configure the application's tracking with
	 */
	public void configureTracking(PrismsApplication app,
		prisms.util.TrackerSet.TrackConfig[] trackConfigs)
	{
		app.getTrackSet().addTrackConfigs(trackConfigs);
	}

	/**
	 * Tells this config to register a property manager using the configuration properties
	 * 
	 * @param app The application to add the plugin type to
	 * @param propConfig The configuration for the property manager
	 * @param withInitializers false if this method should not configure intitializers for the
	 *        application; true if this method should only configure intitializers; null if this
	 *        method should configure all listeners whether initializers or not
	 * @param newMgrs The collection to add newly configured property managers to
	 * @throws IllegalArgumentException If the property manager cannot be registered as specified
	 */
	public void addPropertyManager(PrismsApplication app, PrismsConfig propConfig,
		Boolean withInitializers, java.util.Collection<PropertyManager<?>> newMgrs)
		throws IllegalArgumentException
	{
		log.debug("Adding property manager:\n" + propConfig);
		String globalName = propConfig.get("globalRef");
		if(globalName != null)
		{
			PropertyManager<?> [] mgrs = app.getEnvironment().getManagers(globalName);
			PrismsConfig [] configs = app.getEnvironment().getManagerConfigs(globalName);

			if(mgrs == null)
			{
				log.error("No global property managers named " + globalName);
				return;
			}
			for(int m = 0; m < mgrs.length; m++)
				if(testInitializer(configs[m], withInitializers))
				{
					mgrs[m].configure(app, configs[m]);
					app.addManager(mgrs[m]);
					newMgrs.add(mgrs[m]);
				}

			PrismsConfig [] els = app.getEnvironment().getEventConfigs(globalName);
			if(els != null)
				for(PrismsConfig e : els)
					if(testInitializer(e, withInitializers))
						addEventListener(app, null, e);
			PrismsConfig [] monEls = app.getEnvironment().getMonitorConfigs(globalName);
			if(monEls != null)
				for(PrismsConfig m : monEls)
					if(testInitializer(m, withInitializers))
						addMonitor(app, null, m);
		}
		else
		{
			PropertyManager<?> mgr;
			String mgrType = propConfig.get("type");
			try
			{
				mgr = (PropertyManager<?>) Class.forName(mgrType).newInstance();
			} catch(Throwable e)
			{
				log.error("Could not instantiate manager type " + mgrType, e);
				return;
			}
			mgr.configure(app, propConfig);
			app.addManager(mgr);
			newMgrs.add(mgr);
		}
	}

	/**
	 * Adds to the application or client a prototype for an event listener that will be added to
	 * each session in the application or client
	 * 
	 * @param app The application, optional
	 * @param client The client, optional
	 * @param evtConfig The configuration describing the event listener
	 * @throws IllegalArgumentException If the event listener prototype cannot be created or added
	 */
	public void addEventListener(PrismsApplication app, ClientConfig client, PrismsConfig evtConfig)
		throws IllegalArgumentException
	{
		String lstnrClass = evtConfig.get("class");
		log.debug("Adding event listener:\n" + evtConfig);
		Class<? extends PrismsEventListener> pelClass;
		try
		{
			pelClass = (Class<? extends PrismsEventListener>) Class.forName(lstnrClass);
		} catch(Throwable e)
		{
			log.error("Could not get event listener type " + lstnrClass + ": " + evtConfig, e);
			return;
		}
		if(!PrismsEventListener.class.isAssignableFrom(pelClass))
		{
			log.error("type " + lstnrClass + " is not an Event Listener: " + evtConfig);
			return;
		}
		String name = evtConfig.get("name");
		if(app != null)
			app.addEventListenerType(name, pelClass, evtConfig);
		if(client != null)
			client.addEventListenerType(name, pelClass, evtConfig);
	}

	/**
	 * Adds to the application or client a prototype for a session monitor that will be added to
	 * each session in the application or client
	 * 
	 * @param app The application, optional
	 * @param client The client, optional
	 * @param monitorConfig The configuration describing the monitor
	 * @throws IllegalArgumentException If the monitor prototype cannot be created or added
	 */
	public void addMonitor(PrismsApplication app, ClientConfig client, PrismsConfig monitorConfig)
		throws IllegalArgumentException
	{
		String monitorClassStr = monitorConfig.get("class");
		log.debug("Adding session monitor:\n" + monitorConfig);
		Class<? extends SessionMonitor> monitorClass;
		try
		{
			monitorClass = (Class<? extends SessionMonitor>) Class.forName(monitorClassStr);
		} catch(Throwable e)
		{
			log.error("Could not get monitor type " + monitorClassStr + ": " + monitorConfig, e);
			return;
		}
		if(!SessionMonitor.class.isAssignableFrom(monitorClass))
		{
			log.error("type " + monitorClassStr + " is not a Monitor: " + monitorConfig);
			return;
		}
		if(app != null)
			app.addMonitorType(monitorClass, monitorConfig);
		if(client != null)
			client.addMonitorType(monitorClass, monitorConfig);
	}

	/**
	 * Adds a template for a plugin to the client
	 * 
	 * @param client The client to add the plugin to
	 * @param pluginConfig The plugin configuration
	 * @throws IllegalArgumentException If the plugin cannot be registered as specified
	 */
	public void addPlugin(ClientConfig client, PrismsConfig pluginConfig)
		throws IllegalArgumentException
	{
		String name = pluginConfig.get("name");
		if(name == null)
		{
			log.error("No name specified for plugin: " + pluginConfig);
			return;
		}
		Class<? extends AppPlugin> type;
		try
		{
			type = pluginConfig.getClass("class", AppPlugin.class);
		} catch(ClassNotFoundException e)
		{
			log.error("Class " + pluginConfig.get("class") + " not found for plugin " + name, e);
			return;
		} catch(ClassCastException e)
		{
			log.error("Class " + pluginConfig.get("class") + " is not a plugin type for plugin "
				+ name, e);
			return;
		}
		if(type == null)
		{
			log.error("No class specified for plugin: " + pluginConfig);
			return;
		}
		client.addPluginType(name, type, pluginConfig);
	}
}
