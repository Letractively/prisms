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

	java.util.ArrayList<Search> theSearchPaths;

	int thePathIndex;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		theSearchPaths = new java.util.ArrayList<Search>();
		session.addPropertyChangeListener(log4j.app.Log4jProperties.search,
			new prisms.arch.event.PrismsPCL<Search>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<Search> evt)
				{
					if(evt.getNewValue() == log4j.app.Log4jProperties.NO_SEARCH)
					{
						theSearchPaths.clear();
						thePathIndex = 0;
					}
					else
					{
						boolean found = false;
						for(int i = 0; i < theSearchPaths.size(); i++)
							if(prisms.util.ArrayUtils.equals(theSearchPaths.get(i),
								evt.getNewValue()))
							{
								found = true;
								thePathIndex = i;
								break;
							}
						if(!found)
						{
							while(thePathIndex < theSearchPaths.size() - 1)
								theSearchPaths.remove(theSearchPaths.size() - 1);
							theSearchPaths.add(evt.getNewValue());
							thePathIndex = theSearchPaths.size() - 1;
						}
					}
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

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setCookieCrumbs");
		org.json.simple.JSONArray preSearches = new org.json.simple.JSONArray();
		evt.put("crumbs", preSearches);
		for(int s = 0; s < theSearchPaths.size(); s++)
		{
			JSONObject crumb = new JSONObject();
			preSearches.add(crumb);
			Search srch = theSearchPaths.get(s);
			if(s == thePathIndex)
				crumb.put("selected", Boolean.TRUE);
			String str = srch == null ? "*" : srch.toString();
			crumb.put("descrip", str);
			if(str.length() > 12)
				str = str.substring(0, 10) + "\\u2026";
			crumb.put("text", str);
		}
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
		else if("prevSearch".equals(evt.get("method")))
		{
			int idx = ((Number) evt.get("index")).intValue();
			if(idx < 0 || idx >= theSearchPaths.size())
			{
				theSession.getUI().error("Cookie crumb index invalid: " + idx);
				return;
			}
			theSession.setProperty(log4j.app.Log4jProperties.search, theSearchPaths.get(idx));
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}
}
