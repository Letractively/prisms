/*
 * StatusPlugin.java Created Apr 28, 2009 by Andrew Butler, PSL
 */
package prisms.ui;

import org.json.simple.JSONObject;

import prisms.arch.AppPlugin;
import prisms.arch.PrismsSession;
import prisms.util.PrismsUtils;

/**
 * A very simple plugin that listens for "sendStatusUpdate" and "sendStatusError" events and posts
 * their messages to the client
 */
public class StatusPlugin implements AppPlugin
{
	PrismsSession theSession;

	String theName;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = "Status";
		theSession.addEventListener("sendStatusUpdate", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				sendStatusUpdate((String) evt.getProperty("message"));
			}

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Status Update Displayer";
			}
		});

		theSession.addEventListener("sendStatusError", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				sendStatusError((String) evt.getProperty("message"));
			}

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Status Error Displayer";
			}
		});
	}

	/** @return The session that is using this plugin */
	public PrismsSession getSession()
	{
		return theSession;
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

	public void initClient()
	{
	}

	public void processEvent(JSONObject evt)
	{
		throw new IllegalArgumentException("No talking to the " + theName + " plugin!");
	}
}
