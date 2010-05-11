/**
 * StatusPlugin.java Created Apr 28, 2009 by Andrew Butler, PSL
 */
package prisms.ui;

import org.json.simple.JSONObject;

import prisms.arch.AppPlugin;
import prisms.arch.PrismsSession;
import prisms.util.PrismsUtils;

/**
 * A very simple plugin that listens for "sendStatusUpdate" events and posts their messages to the
 * client
 */
public class StatusPlugin implements AppPlugin
{
	PrismsSession theSession;

	String theName;

	/**
	 * @see prisms.arch.AppPlugin#initPlugin(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		theSession.addEventListener("sendStatusUpdate", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				sendStatusUpdate((String) evt.getProperty("message"));
			}
		});

		theSession.addEventListener("sendStatusError", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				sendStatusError((String) evt.getProperty("message"));
			}
		});
	}

	/**
	 * Sends a status update to the client
	 * 
	 * @param message The message to show the client
	 */
	public void sendStatusUpdate(String message)
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "showStatus");
		evt.put("message", PrismsUtils.encodeUnicode(message));
		theSession.postOutgoingEvent(evt);
	}

	/**
	 * Sends a status update to the client
	 * 
	 * @param message The message to show the client
	 */
	public void sendStatusError(String message)
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "showError");
		evt.put("message", PrismsUtils.encodeUnicode(message));
		theSession.postOutgoingEvent(evt);
	}

	/**
	 * @see prisms.arch.AppPlugin#initClient()
	 */
	public void initClient()
	{
	}

	/**
	 * @see prisms.arch.AppPlugin#processEvent(org.json.simple.JSONObject)
	 */
	public void processEvent(JSONObject evt)
	{
		throw new IllegalArgumentException("No talking to the " + theName + " plugin!");
	}
}
