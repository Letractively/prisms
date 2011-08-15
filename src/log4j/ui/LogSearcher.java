/*
 * LogViewer.java Created Aug 4, 2011 by Andrew Butler, PSL
 */
package log4j.ui;

import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.logging.LogEntrySearch;
import prisms.util.Search;

/** Allows the user to search the logs */
public class LogSearcher implements prisms.arch.AppPlugin
{
	private PrismsSession theSession;

	private String theName;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		session.addPropertyChangeListener(log4j.app.Log4jProperties.search,
			new prisms.arch.event.PrismsPCL<Search>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<Search> evt)
				{
					sendSearch();
				}
			});
	}

	public void initClient()
	{
		sendSearch();
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setTooltip");
		StringBuilder sb = new StringBuilder();
		sb.append("Available search keys:    ");
		for(LogEntrySearch.LogEntrySearchType type : LogEntrySearch.LogEntrySearchType.values())
			for(String header : type.headers)
				sb.append(header).append("    ");
		evt.put("tooltip", sb.toString());
		theSession.postOutgoingEvent(evt);
	}

	void sendSearch()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setSearch");
		Search search = theSession.getProperty(log4j.app.Log4jProperties.search);
		if(search == log4j.app.Log4jProperties.NO_SEARCH)
			evt.put("search", "");
		else if(search == null)
			evt.put("search", "*");
		else
			evt.put("search", search.toString());
		theSession.postOutgoingEvent(evt);
	}

	public void processEvent(JSONObject evt)
	{
		if("search".equals(evt.get("method")))
		{
			String searchStr = (String) evt.get("search");
			if(searchStr.length() == 0)
			{
				theSession.setProperty(log4j.app.Log4jProperties.search,
					log4j.app.Log4jProperties.NO_SEARCH);
				return;
			}
			LogEntrySearch.LogEntrySearchBuilder builder = new LogEntrySearch.LogEntrySearchBuilder(
				theSession.getApp().getEnvironment());
			prisms.util.Search search;
			try
			{
				search = builder.createSearch(searchStr);
			} catch(IllegalArgumentException e)
			{
				theSession.getUI().error(e.getMessage());
				return;
			}
			theSession.setProperty(log4j.app.Log4jProperties.search, search);
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}
}
