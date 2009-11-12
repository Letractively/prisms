/**
 * AppConfig.java Created Oct 24, 2007 by Andrew Butler, PSL
 */
package prisms.arch;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.dom4j.Element;

import prisms.arch.event.PrismsEventListener;
import prisms.arch.event.PropertyManager;
import prisms.arch.event.SessionMonitor;

/**
 * Configures a {@link PrismsApplication}
 */
public class AppConfig
{
	private static Logger log = Logger.getLogger(AppConfig.class);

	/**
	 * Creates the configurator
	 */
	public AppConfig()
	{
	}

	/**
	 * Configures a PluginApplication using an XML config file
	 * 
	 * @param app The application to configure
	 * @param config The XML configuration
	 */
	public void configureApp(PrismsApplication app, Element config)
	{
		synchronized(app)
		{
			if(app.isConfigured())
				return;
			String versionString = config.elementTextTrim("version");
			if(versionString != null)
			{
				String [] split = versionString.split("\\.");
				int [] version = new int [split.length];
				for(int v = 0; version != null && v < version.length; v++)
				{
					try
					{
						version[v] = Integer.parseInt(split[v]);
					} catch(Exception e)
					{
						log.error("Invalid character in version: " + versionString, e);
						version = null;
					}
				}
				if(version != null)
					app.setVersion(version);
			}
			String dateString = config.elementTextTrim("modified");
			if(dateString != null)
			{
				try
				{
					app.setModifiedDate(new java.text.SimpleDateFormat("ddMMMyyyy").parse(
						dateString).getTime());
				} catch(Exception e)
				{
					log.error("Invalid modified date in XML: " + dateString
						+ "--must be in form ddMMMyyyy", e);
				}
			}
			Element workerEl = config.element("worker");
			configureWorker(app, workerEl);
			Element propertiesEl = config.element("properties");
			if(propertiesEl != null)
			{
				Iterator<Element> elIter = propertiesEl.elementIterator("property");
				while(elIter != null && elIter.hasNext())
				{
					Element next = elIter.next();
					try
					{
						addPropertyManager(app, next);
					} catch(Exception e)
					{
						log.error("Could not add property manager: " + next.asXML(), e);
					}
				}
			}
			for(PropertyManager<?> pm : app.getManagers())
			{
				try
				{
					pm.propertiesSet();
				} catch(Throwable e)
				{
					log.error("Properties set failed: ", e);
				}
			}
			Element eventsEl = config.element("events");
			if(eventsEl != null)
			{
				Iterator<Element> elIter = eventsEl.elementIterator("event");
				while(elIter.hasNext())
				{
					Element next = elIter.next();
					try
					{
						addEventListener(app, null, next);
					} catch(Exception e)
					{
						log.error("Could not add event listener: " + next.asXML(), e);
					}
				}
			}
			Element monitorsEl = config.element("monitors");
			if(monitorsEl != null)
			{
				Iterator<Element> elIter = monitorsEl.elementIterator("monitor");
				while(elIter.hasNext())
				{
					Element next = elIter.next();
					try
					{
						addMonitor(app, null, next);
					} catch(Exception e)
					{
						log.error("Could not add monitor: " + next.asXML(), e);
					}
				}
			}
			app.setConfigured();
		}
	}

	/**
	 * @param client The client to configure
	 * @param config The configuration XML
	 */
	public void configureClient(ClientConfig client, Element config)
	{
		Element eventsEl = config.element("events");
		if(eventsEl != null)
		{
			Iterator<Element> elIter = eventsEl.elementIterator("event");
			while(elIter.hasNext())
			{
				Element next = elIter.next();
				try
				{
					addEventListener(null, client, next);
				} catch(Exception e)
				{
					log.error("Could not add event listener: " + next.asXML(), e);
				}
			}
		}
		Element monitorsEl = config.element("monitors");
		if(monitorsEl != null)
		{
			Iterator<Element> elIter = monitorsEl.elementIterator("monitor");
			while(elIter.hasNext())
			{
				Element next = elIter.next();
				try
				{
					addMonitor(null, client, next);
				} catch(Exception e)
				{
					log.error("Could not add monitor: " + next.asXML(), e);
				}
			}
		}
		Element pluginsEl = config.element("plugins");
		if(pluginsEl != null)
		{
			Iterator<Element> elIter = pluginsEl.elementIterator("plugin");
			while(elIter.hasNext())
			{
				Element next = elIter.next();
				try
				{
					addPlugin(client, next);
				} catch(Exception e)
				{
					log.error("Could not add plugin: " + next.asXML(), e);
				}
			}
		}
	}

	/**
	 * Configures a new session for an application
	 * 
	 * @param session The new session to configure
	 * @param config The client configuration element
	 */
	public void configureSession(PrismsSession session, Element config)
	{
	}

	/**
	 * Configures an application's worker for running background tasks
	 * 
	 * @param app The application to set the worker for
	 * @param el The XML element representing the worker to configure
	 */
	public void configureWorker(PrismsApplication app, Element el)
	{
		if(el == null || "threadpool".equals(el.elementText("type")))
			app.setWorker(new prisms.impl.ThreadPoolWorker());
		else
			throw new IllegalArgumentException("Unrecognized worker type in worker element "
				+ el.asXML() + "\nCannot configure application without worker");
	}

	/**
	 * Tells this config to register a property manager using the element properties
	 * 
	 * @param app The application to add the plugin type to
	 * @param propEl The element to configure the property manager
	 * @throws IllegalArgumentException If the property manager cannot be registered as specified
	 */
	public void addPropertyManager(PrismsApplication app, Element propEl)
		throws IllegalArgumentException
	{
		String mgrType = propEl.attributeValue("type");
		log.debug("Adding property manager:\n" + propEl.asXML());
		PropertyManager<?> mgr;
		try
		{
			mgr = (PropertyManager<?>) Class.forName(mgrType).newInstance();
		} catch(Throwable e)
		{
			log.error("Could not instantiate manager type " + mgrType, e);
			return;
		}
		try
		{
			mgr.configure(app, propEl);
		} catch(Exception e)
		{
			log.error("Could not configure manager type " + mgrType + ": " + propEl.asXML(), e);
			return;
		}
		app.addManager(mgr);
	}

	/**
	 * Adds to the application or client a prototype for an event listener that will be added to
	 * each session in the application or client
	 * 
	 * @param app The application, optional
	 * @param client The client, optional
	 * @param evtEl The XML element describing the event listener
	 * @throws IllegalArgumentException If the event listener prototype cannot be created or added
	 */
	public void addEventListener(PrismsApplication app, ClientConfig client, Element evtEl)
		throws IllegalArgumentException
	{
		String lstnrClass = evtEl.elementTextTrim("class");
		log.debug("Adding event listener:\n" + evtEl.asXML());
		Class<? extends PrismsEventListener> pelClass;
		try
		{
			pelClass = (Class<? extends PrismsEventListener>) Class.forName(lstnrClass);
		} catch(Throwable e)
		{
			log.error("Could not get event listener type " + lstnrClass + ": " + evtEl.asXML(), e);
			return;
		}
		if(!PrismsEventListener.class.isAssignableFrom(pelClass))
		{
			log.error("type " + lstnrClass + " is not an Event Listener: " + evtEl.asXML());
			return;
		}
		String name = evtEl.elementTextTrim("name");
		if(app != null)
			app.addEventListenerType(name, pelClass, evtEl);
		if(client != null)
			client.addEventListenerType(name, pelClass, evtEl);
	}

	/**
	 * Adds to the application or client a prototype for a session monitor that will be added to
	 * each session in the application or client
	 * 
	 * @param app The application, optional
	 * @param client The client, optional
	 * @param monitorEl The XML element describing the monitor
	 * @throws IllegalArgumentException If the monitor prototype cannot be created or added
	 */
	public void addMonitor(PrismsApplication app, ClientConfig client, Element monitorEl)
		throws IllegalArgumentException
	{
		String monitorClassStr = monitorEl.elementTextTrim("class");
		log.debug("Adding session monitor:\n" + monitorEl.asXML());
		Class<? extends SessionMonitor> monitorClass;
		try
		{
			monitorClass = (Class<? extends SessionMonitor>) Class.forName(monitorClassStr);
		} catch(Throwable e)
		{
			log
				.error("Could not get monitor type " + monitorClassStr + ": " + monitorEl.asXML(),
					e);
			return;
		}
		if(!SessionMonitor.class.isAssignableFrom(monitorClass))
		{
			log.error("type " + monitorClassStr + " is not a Monitor: " + monitorEl.asXML());
			return;
		}
		if(app != null)
			app.addMonitorType(monitorClass, monitorEl);
		if(client != null)
			client.addMonitorType(monitorClass, monitorEl);
	}

	/**
	 * Adds a template for a plugin to the client
	 * 
	 * @param client The client to add the plugin to
	 * @param pluginEl The plugin element
	 * @throws IllegalArgumentException If the plugin cannot be registered as specified
	 */
	public void addPlugin(ClientConfig client, Element pluginEl) throws IllegalArgumentException
	{
		String name = pluginEl.elementTextTrim("name");
		String clazz = pluginEl.elementTextTrim("class");
		log.debug("Adding plugin " + name + " type " + clazz);
		if(name == null && clazz == null)
		{
			log.error("No name or class specified for plugin: " + pluginEl.asXML());
			return;
		}
		else if(name == null)
		{
			log.error("No name specified for plugin of class " + clazz + ": " + pluginEl.asXML());
			return;
		}
		if(clazz == null)
		{
			log.error("No class specified for plugin " + name + ": " + pluginEl.asXML());
			return;
		}
		Class<? extends AppPlugin> type;
		try
		{
			type = (Class<? extends AppPlugin>) Class.forName(clazz);
		} catch(Throwable e)
		{
			log.error("Could not instantiate plugin " + clazz + ": " + pluginEl.asXML(), e);
			return;
		}
		client.addPluginType(name, type, pluginEl);
	}
}
