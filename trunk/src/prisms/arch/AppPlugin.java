/*
 * AppPlugin.java Created Aug 1, 2007 by Andrew Butler, PSL
 */
package prisms.arch;

/** A plugin to a {@link PrismsSession}. Oversees a certain part of an application. */
public interface AppPlugin
{
	/**
	 * Initializes the plugin. This method is called when the plugin is first created.
	 * 
	 * @param session The session that this plugin belongs to
	 * @param config The configuration for the plugin
	 */
	void initPlugin(PrismsSession session, PrismsConfig config);

	/**
	 * Called to initialize a client with its complete state. This method should fire events to
	 * ensure that the client accurately represents the state of this plugin.
	 */
	void initClient();

	/**
	 * Processes an event from the client
	 * 
	 * @param evt The client event to process
	 */
	void processEvent(org.json.simple.JSONObject evt);
}
