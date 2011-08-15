/*
 * Auto.java Created Aug 11, 2011 by Andrew Butler, PSL
 */
package log4j.ui;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.logging.AutoPurger;

/** Allows the user to view and edit the logging auto-purge configuration */
public class AutoPurgeEditor implements prisms.arch.AppPlugin
{
	static final Logger log = Logger.getLogger(AutoPurgeEditor.class);

	PrismsSession theSession;

	private String theName;

	AutoPurger theDisplayed;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		session.addEventListener("autoPurgeChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(theDisplayed != null)
					return;
				if(!(evt.getProperty("autoPurger") instanceof prisms.logging.AutoPurger))
					return;

				theSession.getUI().confirm(
					"Auto purge settings have been changed by another user"
						+ "\nWould you like to view the new settings?",
					new prisms.ui.UI.ConfirmListener()
					{
						public void confirmed(boolean confirm)
						{
							if(!confirm)
								return;
							sendAutoPurger();
						}
					});
			}
		});
	}

	public void initClient()
	{
		if(theDisplayed != null)
			sendAutoPurger();
	}

	public void processEvent(JSONObject evt)
	{
		if("display".equals(evt.get("method")))
			sendAutoPurger();
		else if("hide".equals(evt.get("method")))
			theDisplayed = null;
		else if("setAutoPurger".equals(evt.get("method")))
			setAutoPurger((JSONObject) evt.get("purger"));
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}

	void sendAutoPurger()
	{
		prisms.logging.PrismsLogger logger = theSession.getApp().getEnvironment().getLogger();
		prisms.logging.AutoPurger purger = logger.getAutoPurger();
		theDisplayed = purger;
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setAutoPurger");
		JSONObject jsonPurger = new JSONObject();
		evt.put("purger", jsonPurger);
		jsonPurger.put("enabled", Boolean.valueOf(theSession.getPermissions().has("Edit Purge")));
		jsonPurger.put("minSize", Integer.valueOf(logger.getMinConfiguredSize()));
		jsonPurger.put("maxSize", Integer.valueOf(logger.getMaxConfiguredSize()));
		jsonPurger.put("size", Integer.valueOf(purger.getMaxSize()));
		jsonPurger.put("minAge", Long.valueOf(logger.getMinConfiguredAge() / 1000));
		jsonPurger.put("minAgeDisplay",
			prisms.util.PrismsUtils.printTimeLength(logger.getMinConfiguredAge()));
		jsonPurger.put("maxAge", Long.valueOf(logger.getMaxConfiguredAge() / 1000));
		jsonPurger.put("maxAgeDisplay",
			prisms.util.PrismsUtils.printTimeLength(logger.getMaxConfiguredAge()));
		jsonPurger.put("age", Long.valueOf(purger.getMaxAge() / 1000));
		org.json.simple.JSONArray excludes = new org.json.simple.JSONArray();
		jsonPurger.put("excludes", excludes);
		for(String excl : purger.getExcludeLoggers())
		{
			JSONObject exclude = new JSONObject();
			exclude.put("name", excl);
			if(prisms.util.ArrayUtils.contains(logger.getPermanentExcludedLoggers(), excl))
				exclude.put("permanent", Boolean.TRUE);
			excludes.add(exclude);
		}
		theSession.postOutgoingEvent(evt);
		sendDSInfo();
	}

	void sendDSInfo()
	{
		long [] times;
		int [] size;
		try
		{
			times = theSession.getApp().getEnvironment().getLogger().getTimeRange();
			size = theSession.getApp().getEnvironment().getLogger().getTotalSize();
		} catch(prisms.arch.PrismsException e)
		{
			log.error("Could not get logging metadata", e);
			return;
		}
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setDSInfo");
		if(times != null)
			evt.put("oldestTime", prisms.util.PrismsUtils.print(times[0]));
		evt.put("entryCount", Integer.valueOf(size[0]));
		evt.put("totalSize", Integer.valueOf(size[1]));
		theSession.postOutgoingEvent(evt);
	}

	void setAutoPurger(JSONObject jsonPurger)
	{
		theDisplayed = null;
		if(!theSession.getPermissions().has("Edit Purge"))
		{
			theSession.getUI().error("You do not have permission to edit the auto purge settings");
			return;
		}
		final prisms.logging.AutoPurger purger = new prisms.logging.AutoPurger();
		purger.setMaxSize(((Number) jsonPurger.get("size")).intValue());
		purger.setMaxAge(((Number) jsonPurger.get("age")).longValue() * 1000);
		for(String logger : (java.util.List<String>) jsonPurger.get("excludes"))
			purger.excludeLogger(logger);

		int [] ids;
		try
		{
			ids = theSession.getApp().getEnvironment().getLogger().previewAutoPurge(purger);
		} catch(IllegalArgumentException e)
		{
			theSession.getUI().error(e.getMessage());
			return;
		}
		theSession.getUI().confirm(
			"Enacting these auto-purge settings will cause the deletion of " + ids.length
				+ " log entr" + (ids.length == 1 ? "y" : "ies")
				+ ".\nAre you sure these are the settings you want?",
			new prisms.ui.UI.ConfirmListener()
			{
				public void confirmed(boolean confirm)
				{
					if(!confirm)
						return;
					try
					{
						theSession.getApp().getEnvironment().getLogger().setAutoPurger(purger);
					} catch(prisms.arch.PrismsException e)
					{
						theSession.getUI().error("Could not change auto-purger: " + e.getMessage());
						log.error(e.getMessage(), e);
					}
				}
			});
	}
}
