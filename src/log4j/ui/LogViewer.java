/*
 * LogViewer.java Created Aug 8, 2011 by Andrew Butler, PSL
 */
package log4j.ui;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsException;
import prisms.arch.PrismsSession;
import prisms.logging.LogEntry;
import prisms.logging.PrismsLogger.LogField;
import prisms.util.Search;

/** Sends log entries to the client for display to the user */
public class LogViewer implements prisms.arch.AppPlugin
{
	static final Logger log = Logger.getLogger(LogViewer.class);

	prisms.util.preferences.Preference<Integer> PAGE_PREF;

	private PrismsSession theSession;

	private String theName;

	private prisms.util.SearchableAPI.PreparedSearch<LogField> theCheckSearch;

	private boolean isRunning;

	private long theLastCheckTime;

	private int theStart;

	int thePageSize;

	private prisms.util.IntList theSnapshot;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		PAGE_PREF = new prisms.util.preferences.Preference<Integer>(theName, "Entries Per Page",
			prisms.util.preferences.Preference.Type.NONEG_INT, Integer.class, true);
		Integer val = theSession.getPreferences().get(PAGE_PREF);
		if(val == null)
		{
			thePageSize = 250;
			theSession.getPreferences().set(PAGE_PREF, Integer.valueOf(thePageSize));
		}
		else
			thePageSize = val.intValue();
		session.addEventListener("preferencesChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(!(evt instanceof prisms.util.preferences.PreferenceEvent))
					return;
				prisms.util.preferences.PreferenceEvent pEvt = (prisms.util.preferences.PreferenceEvent) evt;
				if(!pEvt.getPreference().equals(PAGE_PREF))
					return;
				thePageSize = ((Integer) pEvt.getNewValue()).intValue();
				resend();
			}
		});
		final Runnable checker = new Runnable()
		{
			public void run()
			{
				check();
			}
		};
		session.getApp().scheduleRecurringTask(checker, 1000);
		session.addEventListener("destroy", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				getSession().getApp().stopRecurringTask(checker);
			}
		});
		session.addPropertyChangeListener(log4j.app.Log4jProperties.search,
			new prisms.arch.event.PrismsPCL<Search>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<Search> evt)
				{
					reset();
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
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "clear");
		theSession.postOutgoingEvent(evt);
		resend();
	}

	public void processEvent(JSONObject evt)
	{
		if("check".equals(evt.get("method")))
			check();
		else if("checkBack".equals(evt.get("method")))
		{
			if(isRunning)
			{
				JSONObject evt2 = new JSONObject();
				evt2.put("plugin", theName);
				evt2.put("method", "checkBack");
				theSession.postOutgoingEvent(evt2);
			}
		}
		else if("previous".equals(evt.get("method")))
		{
			if(theSnapshot == null)
				return;
			theStart -= thePageSize;
			if(theStart < 0)
				theStart = 0;
			resend();
		}
		else if("next".equals(evt.get("method")))
		{
			if(theSnapshot == null)
				return;
			theStart += thePageSize;
			while(theStart >= theSnapshot.size())
				theStart -= thePageSize;
			resend();
		}
		else if("clear".equals(evt.get("method")))
			clear();
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}

	void clear()
	{
		theSession.setProperty(log4j.app.Log4jProperties.search, null);
	}

	void reset()
	{
		isRunning = false;
		theCheckSearch = null;
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "clear");
		theSession.postOutgoingEvent(evt);
		research();
	}

	/** Performs a new search */
	void research()
	{
		isRunning = false;
		Search search = theSession.getProperty(log4j.app.Log4jProperties.search);
		if(search == log4j.app.Log4jProperties.NO_SEARCH)
			return;
		if(!theSession.getPermissions().has("View All Logs"))
		{
			if(search == null)
				search = new prisms.logging.LogEntrySearch.LogUserSearch(theSession.getUser(),
					false);
			search = new Search.ExpressionSearch(true).addOps(search,
				new prisms.logging.LogEntrySearch.LogUserSearch(theSession.getUser(), false));
		}
		prisms.logging.PrismsLogger logger = theSession.getApp().getEnvironment().getLogger();
		final boolean [] finished = new boolean [1];
		prisms.ui.UI.ProgressInformer pi = new prisms.ui.UI.ProgressInformer()
		{
			public int getTaskScale()
			{
				return 0;
			}

			public int getTaskProgress()
			{
				return 0;
			}

			public boolean isTaskDone()
			{
				return finished[0];
			}

			public String getTaskText()
			{
				return "Searching PRISMS logs";
			}

			public boolean isCancelable()
			{
				return false;
			}

			public void cancel() throws IllegalStateException
			{
			}
		};
		try
		{
			theSession.getUI().startTimedTask(pi);
			theLastCheckTime = System.currentTimeMillis();
			prisms.util.Sorter<prisms.logging.PrismsLogger.LogField> sorter = null;
			long [] ids;
			try
			{
				ids = logger.search(search, sorter);
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not search logs: " + search, e);
			}
			int [] intIDs = new int [ids.length];
			for(int i = 0; i < ids.length; i++)
				intIDs[i] = (int) ids[i];
			theSnapshot = new prisms.util.IntList(intIDs);
			theStart = 0;
			resend();

			Search checkSearch;
			if(search == null)
				checkSearch = new prisms.logging.LogEntrySearch.LogTimeSearch(Search.Operator.GTE,
					null);
			else
				checkSearch = new Search.ExpressionSearch(true).addOps(search,
					new prisms.logging.LogEntrySearch.LogTimeSearch(Search.Operator.GTE, null));
			try
			{
				theCheckSearch = logger.prepare(checkSearch, sorter);
			} catch(PrismsException e)
			{
				log.error("Could not prepare search updater", e);
				theCheckSearch = null;
			}
		} finally
		{
			finished[0] = true;
		}
	}

	void resend()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "clear");
		theSession.postOutgoingEvent(evt);

		if(theSnapshot == null)
			return;
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setCount");
		evt.put("count", Integer.valueOf(theSnapshot.size()));
		evt.put("start", Integer.valueOf(theStart));
		evt.put("page", Integer.valueOf(thePageSize));
		theSession.postOutgoingEvent(evt);

		prisms.logging.PrismsLogger logger = theSession.getApp().getEnvironment().getLogger();
		final long [] ids;
		if(theSnapshot.size() - theStart < thePageSize)
			ids = new long [theSnapshot.size() - theStart];
		else
			ids = new long [thePageSize];
		theSnapshot.arrayCopy(theStart, ids, 0, ids.length);
		if(ids.length < 150 || thePageSize < 150)
		{
			LogEntry [] entries;
			try
			{
				entries = logger.getItems(ids);
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not get log entries", e);
			}
			JSONArray jsonEntries = new JSONArray();
			for(LogEntry entry : entries)
			{
				JSONObject jsonEntry = toJson(entry);
				if(jsonEntry != null)
					jsonEntries.add(jsonEntry);
			}
			evt = new JSONObject();
			evt.put("plugin", theName);
			evt.put("method", "addEntries");
			evt.put("entries", jsonEntries);
			theSession.postOutgoingEvent(evt);
		}
		else
		{
			isRunning = true;
			Runnable getter = new Runnable()
			{
				public void run()
				{
					sendEntriesProgressive(ids);
				}
			};
			theSession.getApp().getEnvironment().getWorker()
				.run(getter, new prisms.arch.Worker.ErrorListener()
				{
					public void error(Error error)
					{
						log.error("Getting entries failed", error);
					}

					public void runtime(RuntimeException ex)
					{
						log.error("Getting entries failed", ex);
					}
				});
			evt = new JSONObject();
			evt.put("plugin", theName);
			evt.put("method", "checkBack");
			theSession.postOutgoingEvent(evt);
		}
	}

	/** Checks for new log entries that match the current search */
	void check()
	{
		prisms.util.SearchableAPI.PreparedSearch<LogField> checkSearch = theCheckSearch;
		if(checkSearch == null)
			return;
		prisms.logging.PrismsLogger logger = theSession.getApp().getEnvironment().getLogger();
		long preCheck = System.currentTimeMillis();
		long lastCheck = theLastCheckTime;
		long [] ids;
		try
		{
			ids = logger.execute(theCheckSearch, Long.valueOf(lastCheck));
		} catch(PrismsException e)
		{
			throw new IllegalStateException("Could not search for updated logs: ", e);
		}
		if(ids.length == 0)
			return;
		theLastCheckTime = preCheck;
		int [] intIDs = new int [ids.length];
		for(int i = 0; i < ids.length; i++)
			intIDs[i] = (int) ids[i];
		theSnapshot.addAll(intIDs, 0, intIDs.length, 0);
		LogEntry [] entries;
		try
		{
			entries = logger.getItems(ids);
		} catch(PrismsException e)
		{
			throw new IllegalStateException("Could not get log entries", e);
		}
		if(theCheckSearch != checkSearch)
			return;
		JSONArray jsonEntries = new JSONArray();
		for(LogEntry entry : entries)
		{
			JSONObject jsonEntry = toJson(entry);
			if(jsonEntry != null)
				jsonEntries.add(jsonEntry);
		}
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "addNewEntries");
		evt.put("entries", jsonEntries);
		if(theCheckSearch != checkSearch)
			return;
		theSession.postOutgoingEvent(evt);

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setCount");
		evt.put("count", Integer.valueOf(theSnapshot.size()));
		evt.put("start", Integer.valueOf(theStart));
		evt.put("page", Integer.valueOf(thePageSize));
		theSession.postOutgoingEvent(evt);
	}

	/** Used to send entries in chunks instead of all at once */
	void sendEntriesProgressive(long [] ids)
	{
		try
		{
			prisms.logging.PrismsLogger logger = theSession.getApp().getEnvironment().getLogger();
			long [] subIDs = new long [100];
			for(int i = 0; i < ids.length; i += subIDs.length)
			{
				if(!isRunning)
					return;
				if(subIDs.length > ids.length - i)
					subIDs = new long [ids.length - i];
				System.arraycopy(ids, i, subIDs, 0, subIDs.length);
				LogEntry [] entries;
				try
				{
					entries = logger.getItems(subIDs);
				} catch(PrismsException e)
				{
					log.error("Could not get log entries", e);
					return;
				}
				if(!isRunning)
					return;
				JSONArray jsonEntries = new JSONArray();
				for(LogEntry entry : entries)
				{
					JSONObject jsonEntry = toJson(entry);
					if(jsonEntry != null)
						jsonEntries.add(jsonEntry);
				}
				JSONObject evt = new JSONObject();
				evt.put("plugin", theName);
				evt.put("method", "addEntries");
				evt.put("entries", jsonEntries);
				theSession.postOutgoingEvent(evt);
				if(!isRunning)
					return;
			}
		} finally
		{
			isRunning = false;
		}
	}

	JSONObject toJson(LogEntry entry)
	{
		if(entry == null)
			return null;
		JSONObject jsonEntry = new JSONObject();
		jsonEntry.put("id", Integer.toHexString(entry.getID()));
		boolean dup = entry.getDuplicateRef() >= 0;
		if(dup)
			jsonEntry.put("duplicate", Integer.valueOf(entry.getDuplicateRef()));
		jsonEntry.put("instance", entry.getInstanceLocation());
		jsonEntry.put("app", entry.getApp());
		jsonEntry.put("client", entry.getClient());
		if(entry.getUser() != null)
			jsonEntry.put("user", entry.getUser().getName());
		jsonEntry.put("session", entry.getSessionID());
		jsonEntry.put("level", entry.getLevel().toString());
		if(entry.getLevel().equals(org.apache.log4j.Level.FATAL)
			|| entry.getLevel().equals(org.apache.log4j.Level.ERROR))
			jsonEntry.put("levelColor", "#ff0000");
		else if(entry.getLevel().equals(org.apache.log4j.Level.WARN))
			jsonEntry.put("levelColor", "#c0c000");
		else if(entry.getLevel().equals(org.apache.log4j.Level.INFO))
			jsonEntry.put("levelColor", "#0000ff");
		else
			jsonEntry.put("levelColor", "#000000");
		jsonEntry.put("logger", entry.getLoggerName());
		jsonEntry.put("time",
			prisms.util.PrismsUtils.TimePrecision.MILLIS.print(entry.getLogTime(), false));
		jsonEntry.put("tracking", entry.getTrackingData());
		jsonEntry.put("message", entry.getMessage());
		jsonEntry.put("stackTrace", entry.getStackTrace());
		return jsonEntry;
	}
}
