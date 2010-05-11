/**
 * ClientConfig.java Created Aug 26, 2008 by Andrew Butler, PSL
 */
package prisms.arch;

import org.dom4j.Element;

import prisms.arch.event.PrismsEventListener;
import prisms.arch.event.SessionMonitor;

/**
 * Represents a client configuration for creation of sessions for a class of client
 */
public interface ClientConfig
{
	/**
	 * @return The application that this configuration is for
	 */
	PrismsApplication getApp();

	/**
	 * @return The name of this configuration
	 */
	String getName();

	/**
	 * @return A description of this configuration
	 */
	String getDescription();

	/**
	 * @return The event serializer that sessions of this client config will use
	 */
	RemoteEventSerializer getSerializer();

	/**
	 * @return The amount of inactive time until a session of this client should be expired
	 */
	long getSessionTimeout();

	/**
	 * @return Whether this client serves as a web service in a request-response paradigm rather
	 *         than an event-driven one.
	 */
	boolean isService();

	/**
	 * @return Whether this client allows anonymous access without authentication
	 */
	boolean allowsAnonymous();

	/**
	 * @return A validator that allows this client to configure user access in an
	 *         implementation-specific way
	 */
	Validator getValidator();

	/**
	 * Adds an event listener type to add to sessions created (in the future) with this client
	 * config
	 * 
	 * @param eventName The name of the event to listen for
	 * @param type The listener class to instantiate
	 * @param configEl The XML element to configure the instantiated listener
	 */
	void addEventListenerType(String eventName, Class<? extends PrismsEventListener> type,
		Element configEl);

	/**
	 * Adds a monitor type to add to sessions created (in the future) with this client config
	 * 
	 * @param type The monitor class to instantiate
	 * @param configEl The XML element to configure the instantiated monitor
	 */
	void addMonitorType(Class<? extends SessionMonitor> type, Element configEl);

	/**
	 * Adds a plugin type to add to sessions created (in the future) with this client config
	 * 
	 * @param pluginName The name of the plugin
	 * @param type The plugin class to instantiate
	 * @param configEl The XML element to configure the instantiated plugin
	 */
	void addPluginType(String pluginName, Class<? extends AppPlugin> type, Element configEl);

	/**
	 * Adds event listeners, monitors, and plugins to the session
	 * 
	 * @param session The session to configure
	 */
	void configure(PrismsSession session);

	/**
	 * @return Whether this client has been configured or not
	 */
	boolean isConfigured();

	/**
	 * Notifies this client config that it has been configured
	 */
	void setConfigured();
}
