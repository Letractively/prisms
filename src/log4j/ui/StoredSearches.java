/*
 * StoredSearches.java Created Aug 8, 2011 by Andrew Butler, PSL
 */
package log4j.ui;

import log4j.app.NamedSearch;

import org.json.simple.JSONObject;

import prisms.arch.AppPlugin;
import prisms.arch.PrismsConfig;
import prisms.arch.PrismsSession;
import prisms.util.Search;

/** Allows the user to view and create stored searches */
public class StoredSearches implements AppPlugin
{
	private PrismsSession theSession;

	private String theName;

	public void initPlugin(PrismsSession session, PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		session.addPropertyChangeListener(log4j.app.Log4jProperties.searches,
			new prisms.arch.event.PrismsPCL<NamedSearch []>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<NamedSearch []> evt)
				{
					initClient();
				}
			});
		session.addPropertyChangeListener(log4j.app.Log4jProperties.search,
			new prisms.arch.event.PrismsPCL<Search>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<Search> evt)
				{
					initClient();
				}
			});
	}

	/** @return The session that is using this plugin */
	public PrismsSession getSession()
	{
		return theSession;
	}

	public void initClient()
	{
		NamedSearch [] searches = theSession.getProperty(log4j.app.Log4jProperties.searches);
		Search srch = theSession.getProperty(log4j.app.Log4jProperties.search);
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setSearches");
		org.json.simple.JSONArray jSearches = new org.json.simple.JSONArray();
		evt.put("searches", jSearches);
		for(NamedSearch ns : searches)
		{
			JSONObject jSearch = new JSONObject();
			jSearches.add(jSearch);
			jSearch.put("name", ns.getName());
			if(ns.getSearch() == srch)
				jSearch.put("selected", Boolean.TRUE);
		}
		evt.put("isSearched", Boolean.valueOf(srch != log4j.app.Log4jProperties.NO_SEARCH));
		theSession.postOutgoingEvent(evt);
	}

	public void processEvent(JSONObject evt)
	{
		if("doSearch".equals(evt.get("method")))
		{
			NamedSearch [] searches = theSession.getProperty(log4j.app.Log4jProperties.searches);
			for(NamedSearch search : searches)
				if(search.getName().equals(evt.get("search")))
				{
					theSession.setProperty(log4j.app.Log4jProperties.search, search.getSearch());
					break;
				}
		}
		else if("forgetSearch".equals(evt.get("method")))
		{
			Search srch = theSession.getProperty(log4j.app.Log4jProperties.search);
			final NamedSearch [] searches = theSession
				.getProperty(log4j.app.Log4jProperties.searches);
			int s;
			for(s = 0; s < searches.length; s++)
			{
				if(searches[s].getSearch() == srch)
				{
					if(searches[s].getName().equals(log4j.app.LoggerMonitor.ALL_SEARCH)
						|| searches[s].getName().equals(log4j.app.LoggerMonitor.DEFAULT_SEARCH))
					{
						theSession.getUI().error(
							"Search \"" + searches[s].getName() + "\" is not deletable");
						return;
					}
					break;
				}
			}
			if(s == searches.length)
			{
				theSession.getUI().error("The current search is not saved");
				return;
			}
			final int fS = s;
			theSession.getUI().confirm(
				"Are you sure you want to forget search \"" + searches[s] + "\"?",
				new prisms.ui.UI.ConfirmListener()
				{
					public void confirmed(boolean confirm)
					{
						if(!confirm)
							return;
						getSession().setProperty(log4j.app.Log4jProperties.searches,
							prisms.util.ArrayUtils.remove(searches, fS));
					}
				});
		}
		else if("rememberSearch".equals(evt.get("method")))
		{
			final Search srch = theSession.getProperty(log4j.app.Log4jProperties.search);
			if(srch == log4j.app.Log4jProperties.NO_SEARCH)
			{
				theSession.getUI().error("No search has been made");
				return;
			}
			theSession.getUI().input("What name would you like to remember the new search as?",
				null, new prisms.ui.UI.InputListener()
				{
					public void inputed(String input)
					{
						if(input == null)
							return;

						boolean found = false;
						NamedSearch [] searches = getSession().getProperty(
							log4j.app.Log4jProperties.searches);
						for(NamedSearch search : searches)
							if(search.getName().equals(input))
							{
								found = true;
								break;
							}
						if(found)
							getSession().getUI().error(
								"A search named \"" + input + "\" already exists");
						else
						{
							searches = prisms.util.ArrayUtils.add(searches, new NamedSearch(input,
								srch));
							getSession().setProperty(log4j.app.Log4jProperties.searches, searches);
						}
					}
				});
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}
}
