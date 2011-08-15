/*
 * LoggerMonitor.java Created Aug 8, 2011 by Andrew Butler, PSL
 */
package log4j.app;

import prisms.logging.LogEntrySearch;
import prisms.util.Search;

/**
 * Ensures that users keep a default search and (if they are a log admin) an "All" search in their
 * search set
 */
public class LoggerMonitor implements prisms.arch.event.SessionMonitor
{
	/** The name of the default search */
	public static final String DEFAULT_SEARCH = "Default";

	/** The name of the search for all log entries */
	public static final String ALL_SEARCH = "All";

	public void register(final prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		session.setProperty(Log4jProperties.search, Log4jProperties.NO_SEARCH);
		checkSearches(session);
		session.addPropertyChangeListener(Log4jProperties.searches,
			new prisms.arch.event.PrismsPCL<NamedSearch []>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<NamedSearch []> evt)
				{
					checkSearches(session);
				}
			});
		final Runnable checker = new Runnable()
		{
			prisms.logging.AutoPurger thePurger;

			public void run()
			{
				prisms.logging.AutoPurger purger = session.getApp().getEnvironment().getLogger()
					.getAutoPurger();
				if(thePurger == null)
					thePurger = purger;
				else if(thePurger != purger)
				{
					thePurger = purger;
					session.fireEvent("autoPurgerChanged", "purger", purger);
				}
			}
		};
		session.getApp().scheduleRecurringTask(checker, 100);
		session.addEventListener("destroy", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.PrismsSession session2,
				prisms.arch.event.PrismsEvent evt)
			{
				session.getApp().stopRecurringTask(checker);
			}
		});
	}

	void checkSearches(prisms.arch.PrismsSession session)
	{
		prisms.arch.ds.IDGenerator.PrismsInstance inst = session.getApp().getEnvironment().getIDs()
			.getLocalInstance();
		NamedSearch realDef = null;
		if(inst != null)
			realDef = new NamedSearch(DEFAULT_SEARCH, new Search.ExpressionSearch(true).addOps(
				new LogEntrySearch.InstanceSearch(inst.location), new LogEntrySearch.LogTimeSearch(
					Search.Operator.GTE, new Search.SearchDate(inst.initTime))));
		NamedSearch [] searches = session.getProperty(Log4jProperties.searches);
		NamedSearch all = null;
		NamedSearch def = null;
		for(NamedSearch srch : searches)
		{
			if(srch.getName().equals(DEFAULT_SEARCH))
				def = srch;
			else if(srch.getName().equals(ALL_SEARCH))
				all = srch;
		}
		boolean changed = false;
		if(all == null)
		{
			changed = true;
			all = new NamedSearch(ALL_SEARCH, null);
			searches = prisms.util.ArrayUtils.add(searches, all, 0);
		}
		else if(all.getSearch() != null)
		{
			changed = true;
			searches = prisms.util.ArrayUtils.remove(searches, all);
			all = new NamedSearch(ALL_SEARCH, null);
			searches = prisms.util.ArrayUtils.add(searches, all, 0);
		}
		if(def == null)
		{
			changed = true;
			if(realDef == null)
				def = all;
			else
				def = realDef;
			searches = prisms.util.ArrayUtils.add(searches, def, 1);
		}
		else if(!realDef.getSearch().equals(def.getSearch()))
		{
			changed = true;
			searches = prisms.util.ArrayUtils.remove(searches, def);
			searches = prisms.util.ArrayUtils.add(searches, realDef, 1);
		}
		if(changed)
			session.setProperty(Log4jProperties.searches, searches);
	}
}
