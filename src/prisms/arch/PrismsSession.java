/*
 * PrismsSession.java Created Aug 1, 2007 by Andrew Butler, PSL
 */
package prisms.arch;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.ds.User;
import prisms.arch.event.*;
import prisms.util.ProgramTracker.TrackNode;

/**
 * A PrismsSession serves as the core of the plugin architecture and the remote session for the
 * remote communication architecture. It contains operations allowing plugins to communicate with
 * each other and with the client while requiring a minimum of knowledge about either the client or
 * the other plugins.
 */
public class PrismsSession
{
	/** Listens for events posted for the client */
	public static interface EventListener
	{
		/** @param event The event that was posted */
		void eventPosted(JSONObject event);
	}

	/** Metadata describing a session */
	public static class SessionMetadata
	{
		private final String theID;

		private final PrismsAuthenticator theAuth;

		private final String theRemoteAddr;

		private final String theRemoteHost;

		private final prisms.util.ClientEnvironment theCE;

		private final PrismsServer.ClientGovernor theGovernor;

		/**
		 * Creates session metadata
		 * 
		 * @param id The ID of the session
		 * @param auth The authenticator that granted access to the application
		 * @param remoteAddr The remote address that is accessing this session
		 * @param remoteHost The remote host that is accessing this session
		 * @param ce The client environment detailing the browser and OS of the client, if available
		 * @param gov Allows access to the activity level of the client
		 * @see javax.servlet.ServletRequest#getRemoteAddr()
		 * @see javax.servlet.ServletRequest#getRemoteHost()
		 */
		public SessionMetadata(String id, PrismsAuthenticator auth, String remoteAddr,
			String remoteHost, prisms.util.ClientEnvironment ce, PrismsServer.ClientGovernor gov)
		{
			theID = id;
			theAuth = auth;
			theRemoteAddr = remoteAddr;
			theRemoteHost = remoteHost;
			theCE = ce;
			theGovernor = gov;
		}

		/**
		 * @return The session's ID. This value does not necessarily uniquely identify a particular
		 *         {@link PrismsSession}, but rather is a field of the request used by the server to
		 *         address a group of sessions.
		 */
		public String getID()
		{
			return theID;
		}

		/** @return The authenticator that granted access to the application */
		public PrismsAuthenticator getAuth()
		{
			return theAuth;
		}

		/**
		 * @return The remote IP that is accessing this session
		 * @see javax.servlet.ServletRequest#getRemoteAddr()
		 */
		public String getRemoteAddr()
		{
			return theRemoteAddr;
		}

		/**
		 * @return The remote host that is accessing this session
		 * @see javax.servlet.ServletRequest#getRemoteHost()
		 */
		public String getRemoteHost()
		{
			return theRemoteHost;
		}

		/** @return The client's browser and OS versions, if available */
		public prisms.util.ClientEnvironment getClientEnv()
		{
			return theCE;
		}

		/**
		 * @return The governor that keeps track of and enforces maximums on client access to the
		 *         server. May be null.
		 */
		public PrismsServer.ClientGovernor getGovernor()
		{
			return theGovernor;
		}
	}

	static final Logger log = Logger.getLogger(PrismsSession.class);

	private final PrismsApplication theApp;

	private final ClientConfig theClient;

	private final User theUser;

	private final SessionMetadata theMetadata;

	private final long theCreationTime;

	private prisms.ui.UI theUI;

	private prisms.ui.PreferencesEditor thePrefEditor;

	private prisms.util.preferences.Preferences thePreferences;

	private prisms.ui.StatusPlugin theStatus;

	private final java.util.concurrent.ConcurrentLinkedQueue<JSONObject> theOutgoingQueue;

	private long theLastCheckedTime;

	private boolean isKilled;

	private final java.util.LinkedHashMap<String, AppPlugin> thePlugins;

	private final java.util.LinkedHashMap<String, AppPlugin> theStandardPlugins;

	private final ConcurrentHashMap<PrismsProperty<?>, Object> theProperties;

	private final ConcurrentHashMap<PrismsProperty<?>, PrismsApplication.PrismsPropertyLock> thePropertyLocks;

	private final ConcurrentHashMap<PrismsProperty<?>, PrismsSession> thePropertyStack;

	private final PropertySetActionQueue thePSAQueue;

	@SuppressWarnings("rawtypes")
	private final ListenerManager<PrismsPCL> thePCLs;

	private final ListenerManager<PrismsEventListener> theELs;

	private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> theTaskList;

	private EventListener theListener;

	private java.util.concurrent.ConcurrentHashMap<String, boolean []> theRunningTasks;

	private final prisms.util.TrackerSet theTrackSet;

	private long theAsyncWait;

	/**
	 * Creates a PluginAppSession
	 * 
	 * @param app The application that this session was created for
	 * @param client The client that this session was created for
	 * @param user The user to create the session for
	 * @param md The metadata associated with this session
	 */
	@SuppressWarnings("rawtypes")
	public PrismsSession(PrismsApplication app, ClientConfig client, User user, SessionMetadata md)
	{
		theApp = app;
		theClient = client;
		theUser = user;
		theMetadata = md;
		theCreationTime = System.currentTimeMillis();
		theOutgoingQueue = new java.util.concurrent.ConcurrentLinkedQueue<JSONObject>();
		theLastCheckedTime = System.currentTimeMillis();
		thePlugins = new java.util.LinkedHashMap<String, AppPlugin>();
		theStandardPlugins = new java.util.LinkedHashMap<String, AppPlugin>();
		theProperties = new ConcurrentHashMap<PrismsProperty<?>, Object>();
		thePropertyLocks = new ConcurrentHashMap<PrismsProperty<?>, PrismsApplication.PrismsPropertyLock>();
		thePropertyStack = new ConcurrentHashMap<PrismsProperty<?>, PrismsSession>();
		thePSAQueue = new PropertySetActionQueue();
		thePCLs = new ListenerManager<PrismsPCL>(PrismsPCL.class);
		theELs = new ListenerManager<PrismsEventListener>(PrismsEventListener.class);
		theTaskList = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
		theRunningTasks = new ConcurrentHashMap<String, boolean []>();
		theTrackSet = new prisms.util.TrackerSet("Session: " + client + "/" + user, app
			.getTrackSet().getConfigs());
		theTrackSet.setConfigured();

		if(client.isService())
			theUI = new prisms.ui.UI.ServiceUI();
		else
			theUI = new prisms.ui.UI.NormalUI(this);
		theStandardPlugins.put("UI", theUI);
		thePrefEditor = new prisms.ui.PreferencesEditor();
		thePrefEditor.initPlugin(this, null);
		theStandardPlugins.put("Preferences", thePrefEditor);
		theStatus = new prisms.ui.StatusPlugin();
		theStatus.initPlugin(this, null);
		theStandardPlugins.put("Status", theStatus);

		theAsyncWait = 100;
	}

	/**
	 * @param listener The listener to listen to this session for posted events to be sent to the
	 *        client
	 */
	public void setListener(EventListener listener)
	{
		theListener = listener;
	}

	/** @return The application that created this session */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/** @return The client that configured this session */
	public ClientConfig getClient()
	{
		return theClient;
	}

	/** @return The user of this session */
	public User getUser()
	{
		return theUser;
	}

	/**
	 * @return The metadata associated with this session. This data may not be unique to this
	 *         session. In particular, the ID associated with the metadata does not uniquely
	 *         identify this particular session.
	 */
	public SessionMetadata getMetadata()
	{
		return theMetadata;
	}

	/** @return The UI plugin for this session */
	public prisms.ui.UI getUI()
	{
		return theUI;
	}

	/** @param ui The UI plugin that this session should use */
	public void setUI(prisms.ui.UI ui)
	{
		if(ui == null)
			throw new NullPointerException();
		theUI = ui;
	}

	/** @return The user preferences associated with this session */
	public prisms.util.preferences.Preferences getPreferences()
	{
		if(thePreferences == null)
		{
			thePreferences = getProperty(PrismsProperties.preferences);
			if(thePreferences == null)
			{
				log.warn("No preference persister configured for " + theApp
					+ ": preferences will not be persisted");
				thePreferences = new prisms.util.preferences.Preferences(theApp, theUser);
			}
			thePreferences.addListener(new prisms.util.preferences.Preferences.Listener()
			{
				public <T> void prefChanged(prisms.util.preferences.PreferenceEvent evt)
				{
					fireEvent(evt);
				}
			});
		}
		return thePreferences;
	}

	/** @return The status plugin to use for this session */
	public prisms.ui.StatusPlugin getStatus()
	{
		return theStatus;
	}

	/**
	 * A convenience method for getting the PRISMS transaction for the current thread
	 * 
	 * @return The PRISMS transaction for the current thread
	 */
	public PrismsTransaction getTransaction()
	{
		return theApp.getEnvironment().getTransaction();
	}

	/** @return The track set that keeps track of performance for this session */
	public prisms.util.TrackerSet getTrackSet()
	{
		return theTrackSet;
	}

	/**
	 * A convenience method for getting this session's user's permissions for this session's
	 * application.
	 * 
	 * @return This session's permissions according to its user and application
	 */
	public prisms.arch.ds.Permissions getPermissions()
	{
		return theUser.getPermissions(theApp);
	}

	/** @return The time that this session was created */
	public long getCreationTime()
	{
		return theCreationTime;
	}

	/**
	 * @param name The name of the plugin to get
	 * @return This session's plugin with the given name
	 */
	public AppPlugin getPlugin(String name)
	{
		AppPlugin ret = theStandardPlugins.get(name);
		if(ret == null)
			synchronized(thePlugins)
			{
				return thePlugins.get(name);
			}
		return ret;
	}

	/**
	 * Removes all outgoing events marked for the given plugin
	 * 
	 * @param pluginName The name of the plugin to remove the outgoing events for
	 */
	public void removeOutgoingEvents(String pluginName)
	{
		java.util.Iterator<JSONObject> queueIter = theOutgoingQueue.iterator();
		while(queueIter.hasNext())
		{
			JSONObject evt = queueIter.next();
			if(pluginName.equals(evt.get("plugin")))
				queueIter.remove();
		}
	}

	/** Resets this session's counter so that it doesn't timeout */
	public void renew()
	{
		theLastCheckedTime = System.currentTimeMillis();
	}

	/**
	 * Processes an event synchronously. If this method is used instead of
	 * {@link #processAsync(JSONObject, boolean [])}, events posted from this thread will go into
	 * the return array instead of the outgoing list.
	 * 
	 * @param event The event to process
	 * @return All events posted to this session as a result of the service call
	 */
	public JSONArray processSync(JSONObject event)
	{
		PrismsTransaction trans = theApp.getEnvironment().transact(this,
			PrismsTransaction.Stage.processEvent);
		trans.setSynchronous();
		JSONArray ret;
		try
		{
			process(event);
		} finally
		{
			ret = theApp.getEnvironment().finish(trans);
		}
		return ret;
	}

	/**
	 * Processes events from the remote client asynchronously
	 * 
	 * @param event The event to process
	 * @param finished A boolean array whose first element will be set to true when the event has
	 *        been processed
	 * @return The events posted to this session that need to be sent to the client, whether or not
	 *         they are a result of this particular call
	 */
	public JSONArray processAsync(final JSONObject event, boolean [] finished)
	{
		if(finished == null)
			finished = new boolean [1];
		final boolean [] fFinished = finished;
		PrismsTransaction trans = getTransaction();
		if(event.get("plugin") == null && "getEvents".equals(event.get("method"))
			&& event.containsKey("taskID"))
		{
			prisms.util.ProgramTracker.TrackNode track = prisms.util.PrismsUtils.track(trans,
				"Check Running Tasks");
			try
			{
				java.util.Iterator<boolean []> values = theRunningTasks.values().iterator();
				while(values.hasNext())
				{
					if(values.next()[0])
						values.remove();
				}

				String taskID = (String) event.get("taskID");
				finished = theRunningTasks.get(taskID);
				JSONArray ret = getEvents();
				if(finished != null && !finished[0] && !getUI().isProgressShowing())
				{
					JSONObject getEvents = new JSONObject();
					getEvents.put("method", "getEvents");
					getEvents.put("taskID", taskID);
					ret.add(getEvents);
				}
				return ret;
			} finally
			{
				prisms.util.PrismsUtils.end(trans, track);
				fFinished[0] = true;
			}
		}
		Runnable toRun = new Runnable()
		{
			public void run()
			{
				PrismsTransaction trans2 = getApp().getEnvironment().transact(PrismsSession.this,
					PrismsTransaction.Stage.processEvent);
				try
				{
					process(event);
				} finally
				{
					fFinished[0] = true;
					getApp().getEnvironment().finish(trans2);
				}
			}
		};
		theApp.getEnvironment().getWorker().run(toRun, new Worker.ErrorListener()
		{
			public void error(Error e)
			{
				postOutgoingEvent(wrapError("Background task error", e));
			}

			public void runtime(RuntimeException e)
			{
				postOutgoingEvent(wrapError("Background task runtime exception", e));
			}
		});
		if(theAsyncWait > 0)
		{
			/*
			 * This code checks often to see if the event has been processed. If the processing
			 * isn't finished after theAsyncWait, this method returns, leaving the final results of
			 * the event on the queue to be retrieved at the next client poll or user action. This
			 * allows progress bars to be shown to the user quickly while a long operation
			 * progresses, as well as releasing the server thread to be reused by other requests.
			 */
			prisms.util.ProgramTracker.TrackNode track = prisms.util.PrismsUtils.track(trans,
				"Wait For Async Results");
			long waitInc = theAsyncWait / 5;
			if(waitInc < 50)
			{
				waitInc = 50;
				if(waitInc > theAsyncWait)
					waitInc = theAsyncWait;
			}
			long now = System.currentTimeMillis();
			do
			{
				try
				{
					Thread.sleep(waitInc);
				} catch(InterruptedException e)
				{}
			} while(!finished[0] && System.currentTimeMillis() - now < theAsyncWait);
			prisms.util.PrismsUtils.end(trans, track);
		}
		if(!finished[0])
		{
			String taskID = Integer.toHexString(toRun.hashCode());
			JSONObject getEvents = new JSONObject();
			getEvents.put("method", "getEvents");
			getEvents.put("taskID", taskID);
			if(!finished[0] && !getUI().isProgressShowing())
			{
				theRunningTasks.put(taskID, finished);
				JSONArray ret = getEvents();
				ret.add(getEvents);
				return ret;
			}
		}
		return getEvents();
	}

	void process(JSONObject event)
	{
		try
		{
			_process(event);
		} catch(prisms.util.CancelException e)
		{
			log.info(e.getMessage(), e);
		} catch(Error e)
		{
			log.error("Session event error", e);
			postOutgoingEvent(wrapError("Session event error", e));
		} catch(RuntimeException e)
		{
			log.error("Session event runtime exception", e);
			postOutgoingEvent(wrapError("Session event runtime exception", e));
		}
	}

	void _process(JSONObject evt)
	{
		runTasks();
		if(evt == null)
			return;
		Object pName = evt.get("plugin");
		if(pName == null)
			this.processEvent(evt);
		else
		{
			if(!(pName instanceof String))
				throw new IllegalArgumentException("Illegal plugin value: " + pName);
			AppPlugin plugin = getPlugin((String) pName);
			if(plugin == null)
				throw new IllegalArgumentException("No such plugin: " + pName);
			PrismsTransaction trans = getTransaction();
			TrackNode track = prisms.util.PrismsUtils.track(trans, "PRISMS: Plugin " + pName + "."
				+ evt.get("method") + "()");
			try
			{
				plugin.processEvent(evt);
			} finally
			{
				prisms.util.PrismsUtils.end(trans, track);
			}
			renew();
		}

		runTasks();
	}

	/** Runs all tasks added with {@link #runEventually(Runnable)} */
	public void runTasks()
	{
		PrismsTransaction trans = getTransaction();
		boolean hasTasks = false;
		do
		{
			hasTasks = false;
			if(!theTaskList.isEmpty())
			{
				hasTasks = true;
				Runnable [] tasks = new Runnable [theTaskList.size()];
				java.util.Iterator<Runnable> iter = theTaskList.iterator();
				for(int i = 0; i < tasks.length && iter.hasNext(); i++)
				{
					tasks[i] = iter.next();
					iter.remove();
				}
				if(tasks.length > 0 && tasks[0] != null)
				{
					TrackNode totalTrack = prisms.util.PrismsUtils.track(trans, "Session Tasks");
					try
					{
						Exception outerE = new Exception();
						for(Runnable task : tasks)
						{
							/* If a task was removed between the time when the tasks array was created and when
							 * the item would have been reached in the iteration, the tasks array may not be
							 * full. */
							if(task == null)
								break;
							TrackNode track = prisms.util.PrismsUtils.track(trans, task);
							try
							{
								task.run();
							} catch(Throwable e)
							{
								e.setStackTrace(prisms.util.PrismsUtils.patchStackTraces(
									e.getStackTrace(), outerE.getStackTrace(),
									getClass().getName(), "_process"));
								log.error("Error Processing Task " + task, e);
								postOutgoingEvent(wrapError("Error Processing Task " + task, e));
							} finally
							{
								prisms.util.PrismsUtils.end(trans, track);
							}
						}
					} finally
					{
						prisms.util.PrismsUtils.end(trans, totalTrack);
					}
				}
			}
			hasTasks |= theApp.runPropertySetActions();
			hasTasks |= theApp.runScheduledTasks();
			PropertySetActionQueue.PropertySetAction<Object> [] actions = thePSAQueue.getActions();
			if(actions.length > 0)
			{
				TrackNode totalTrack = prisms.util.PrismsUtils.track(trans,
					"Queued Property Set Actions");
				try
				{
					for(PropertySetActionQueue.PropertySetAction<Object> action : actions)
						_setProperty(trans, action.property, action.oldValue, action.value,
							action.eventProps);
				} finally
				{
					prisms.util.PrismsUtils.end(trans, totalTrack);
				}
			}
			hasTasks |= actions.length > 0;
		} while(hasTasks);
	}

	JSONObject wrapError(String title, Throwable e)
	{
		JSONObject ret = new JSONObject();
		ret.put("method", "error");
		ret.put("code", PrismsServer.ErrorCode.ApplicationError.description);
		ret.put("title", title);
		String message = e.getMessage();
		if(message == null || message.length() == 0)
			message = e.getClass().getName();
		else if(message.length() <= 5)
			message = e.getClass().getName() + ": " + message;
		ret.put("message", message);
		if(e instanceof PrismsDetailException)
			ret.put("params", ((PrismsDetailException) e).getParams());
		return ret;
	}

	/**
	 * @return The set of events posted to this session from the server since the last getEvents
	 *         call
	 */
	public org.json.simple.JSONArray getEvents()
	{
		org.json.simple.JSONArray events = new org.json.simple.JSONArray();
		java.util.Iterator<JSONObject> iter = theOutgoingQueue.iterator();
		while(iter.hasNext())
		{
			events.add(iter.next());
			iter.remove();
		}
		return events;
	}

	/** Initializes this session for a client that has just connected or reconnected to it */
	public void init()
	{
		getPreferences();
		clearOutgoingQueue();
		final boolean [] finished = new boolean [] {false};
		final PrismsTransaction trans = getApp().getEnvironment().transact(PrismsSession.this,
			PrismsTransaction.Stage.initSession);
		try
		{
			TrackNode track;
			for(java.util.Map.Entry<String, AppPlugin> p : theStandardPlugins.entrySet())
			{
				track = trans.getTracker().start("PRISMS:Initializing " + p.getKey());
				try
				{
					p.getValue().initClient();
				} catch(Throwable e)
				{
					log.error("Could not client-initialize " + p.getKey(), e);
					JSONObject event = new JSONObject();
					event.put("method", "error");
					event.put("message",
						"Could not initialize " + p.getKey() + ": " + e.getMessage());
					postOutgoingEvent(event);
				} finally
				{
					trans.getTracker().end(track);
				}
			}

			java.util.Map.Entry<String, AppPlugin> [] plugins;
			synchronized(thePlugins)
			{
				plugins = thePlugins.entrySet()
					.toArray(new java.util.Map.Entry [thePlugins.size()]);
			}
			for(java.util.Map.Entry<String, AppPlugin> p : plugins)
			{
				track = trans.getTracker().start("PRISMS:Initializing plugin " + p.getKey());
				try
				{
					long time = System.currentTimeMillis();
					p.getValue().initClient();
					long newTime = System.currentTimeMillis();
					if(log.isDebugEnabled())
					{
						StringBuilder toPrint = new StringBuilder();
						toPrint.append("Initialized plugin ");
						toPrint.append(p.getKey());
						toPrint.append(" in ");
						toPrint.append(prisms.util.PrismsUtils.printTimeLength(newTime - time));
						log.debug(toPrint.toString());
					}
				} catch(Throwable e)
				{
					log.error("Could not client-initialize plugin " + p.getKey(), e);
					JSONObject event = new JSONObject();
					event.put("method", "error");
					event.put("message",
						"Could not initialize plugin " + p.getKey() + ": " + e.getMessage());
					postOutgoingEvent(event);
				} finally
				{
					trans.getTracker().end(track);
				}
			}
		} finally
		{
			finished[0] = true;
			getApp().getEnvironment().finish(trans);
		}
		renew();
	}

	/**
	 * Called when this session and not an individual plugin should process an event
	 * 
	 * @param evt The event to process
	 */
	public void processEvent(JSONObject evt)
	{
		if("init".equals(evt.get("method")))
			clearOutgoingQueue();
		else if("addPlugin".equals(evt.get("method")))
		{
			String pluginName = (String) evt.get("pluginToAdd");
			AppPlugin plugin = getPlugin(pluginName);
			if(plugin != null)
				plugin.initClient();
			else
				log.error("Client requested plugin " + pluginName + " does not exist!");
			renew();
		}
		else if("getEvents".equals(evt.get("method")))
			return;
		else if("renew".equals(evt.get("method")))
			renew();
		else
			throw new IllegalArgumentException("Each event must specify a plugin: " + evt);
	}

	/**
	 * Posts an event to send to the client
	 * 
	 * @param evt The event object to send
	 */
	public void postOutgoingEvent(JSONObject evt)
	{
		try
		{
			prisms.arch.JsonSerializer.validate(evt);
		} catch(java.io.NotSerializableException e)
		{
			throw new IllegalArgumentException("Could not serialize event", e);
		}
		PrismsTransaction trans = theApp.getEnvironment().getTransaction();
		if(trans != null && trans.getSession() == this && trans.isSynchronous())
		{
			trans.respond(evt);
			return;
		}
		else if(theClient.isService())
			return; /* Events must be tied to a synchronous transaction if this session is for a web service */
		theOutgoingQueue.add(evt);
		if(theListener != null)
			theListener.eventPosted(evt);
	}

	/**
	 * Schedules a task to be run at the next opportunity
	 * 
	 * @param task The task to run
	 */
	public void runEventually(Runnable task)
	{
		theTaskList.add(task);
	}

	/**
	 * Registers a plugin with this session so that it can receive remote events from the client
	 * 
	 * @param name The name of the plugin
	 * @param p The plugin to register
	 */
	public void addPlugin(String name, AppPlugin p)
	{
		synchronized(thePlugins)
		{
			thePlugins.put(name, p);
		}
	}

	/**
	 * Removes a plugin from this session by name
	 * 
	 * @param name The name of the plugin to remove
	 */
	public void removePlugin(String name)
	{
		synchronized(thePlugins)
		{
			thePlugins.remove(name);
		}
	}

	/**
	 * Removes a plugin from this session
	 * 
	 * @param p The plugin to remove
	 */
	public void removePlugin(AppPlugin p)
	{
		if(p == null)
			return;
		synchronized(thePlugins)
		{
			java.util.Iterator<AppPlugin> plugins = thePlugins.values().iterator();
			while(plugins.hasNext())
				if(p.equals(plugins.next()))
					plugins.remove();
		}
	}

	/** @return All properties for which values are set in this session */
	public PrismsProperty<?> [] getAllProperties()
	{
		return theProperties.keySet().toArray(new PrismsProperty [theProperties.size()]);
	}

	/**
	 * @param <T> The type of property to get
	 * @param property The name of the property to get the value of
	 * @return The current value of the given property
	 */
	public <T> T getProperty(PrismsProperty<T> property)
	{
		return (T) theProperties.get(property);
	}

	/**
	 * Sets the value of a property, notifying all listeners registered for the property
	 * 
	 * @param <T> The type of property to set
	 * @param property The property to change
	 * @param propValue The new value for the property
	 * @param eventProps Event properties for the property change event that is fired. Must be in
	 *        the form of name, value, name, value, where name is a string.
	 */
	public <T> void setProperty(PrismsProperty<T> property, T propValue, Object... eventProps)
	{
		PrismsTransaction trans = getTransaction();
		if(trans == null || trans.getSession() != this)
			thePSAQueue.add(property, (T) theProperties.get(property), propValue, null, eventProps);
		else
			_setProperty(trans, property, (T) theProperties.get(property), propValue, eventProps);
	}

	private <T> void _setProperty(PrismsTransaction trans, PrismsProperty<T> property, T oldValue,
		T propValue, Object... eventProps)
	{
		// Generics *can* be defeated--check the type here
		if(propValue != null && !property.getType().isInstance(propValue))
			throw new IllegalArgumentException("Cannot set an instance of "
				+ propValue.getClass().getName() + " for property " + property + ", type "
				+ property.getType().getName());
		if(eventProps.length % 2 != 0)
			throw new IllegalArgumentException("Event properties for property change event must be"
				+ " in the form of name, value, name, value, etc.--" + eventProps.length
				+ " arguments illegal");
		TrackNode [] tracks = new TrackNode [3];
		tracks[0] = prisms.util.PrismsUtils.track(trans, "Session.setProperty");
		tracks[1] = prisms.util.PrismsUtils.track(trans, "Property " + property.getName());
		try
		{
			/* Many property sets can be going on at once, but only one for each property in a session */
			synchronized(getPropertyLock(property))
			{
				PrismsPCE<T> propEvt = new PrismsPCE<T>(getApp(), this, property, oldValue,
					propValue);
				PrismsPCL<T> [] listeners;
				thePropertyStack.put(property, this);
				if(propValue == null)
					theProperties.remove(property);
				else
					theProperties.put(property, propValue);
				listeners = getPropertyChangeListeners(property);

				for(int i = 0; i < eventProps.length; i += 2)
				{
					if(!(eventProps[i] instanceof String))
						throw new IllegalArgumentException("Event properties for property change"
							+ " event must be in the form of name, value, name, value, etc."
							+ "--eventProps[" + i + "] is not a string");
					propEvt.set((String) eventProps[i], eventProps[i + 1]);
				}
				for(PrismsPCL<T> l : listeners)
				{
					tracks[2] = prisms.util.PrismsUtils.track(trans, l);
					try
					{
						l.propertyChange(propEvt);
					} finally
					{
						prisms.util.PrismsUtils.end(trans, tracks[2]);
					}
					/* If this property is changed as a result of the above PCL, stop this notification */
					if(!thePropertyStack.containsKey(property))
					{
						propEvt.set("canceled", Boolean.TRUE);
						break;
					}
				}
				thePropertyStack.remove(property);
			}
		} finally
		{
			prisms.util.PrismsUtils.end(trans, tracks[1]);
			prisms.util.PrismsUtils.end(trans, tracks[0]);
		}
	}

	private Object getPropertyLock(PrismsProperty<?> property)
	{
		PrismsApplication.PrismsPropertyLock ret = thePropertyLocks.get(property);
		if(ret == null)
		{
			synchronized(thePropertyLocks)
			{
				ret = thePropertyLocks.get(property);
				if(ret == null)
				{
					ret = new PrismsApplication.PrismsPropertyLock(property, theApp, this);
					thePropertyLocks.put(property, ret);
				}
			}
		}
		return ret;
	}

	/**
	 * @param <T> The type of property to get listeners for
	 * @param prop The name of the property to get the listeners for
	 * @return All property change listeners that will be notified when the given property changes
	 */
	public <T> PrismsPCL<T> [] getPropertyChangeListeners(PrismsProperty<T> prop)
	{
		return thePCLs.getListeners(prop);
	}

	/**
	 * Adds a property change listener to listen for changes to all properties
	 * 
	 * @param pcl The listener to be notified when any property changes
	 */
	public void addPropertyChangeListener(PrismsPCL<Object> pcl)
	{
		thePCLs.addListener(pcl);
	}

	/**
	 * Adds a PropertyChangeListener to listen for changes to a specific property
	 * 
	 * @param <T> The type of property to listen for
	 * @param propName The name of the property to listen to
	 * @param pcl The listener to be notified when the property changes
	 */
	public <T> void addPropertyChangeListener(PrismsProperty<T> propName, PrismsPCL<? super T> pcl)
	{
		thePCLs.addListener(propName, pcl);
	}

	/**
	 * This method removes the given listener from <b>EVERY</b> property it is registered for,
	 * including the general case. The {@link Object#equals(Object)} method is used for comparison.
	 * 
	 * @param pcl The PropertyChangeListener to remove
	 */
	public void removePropertyChangeListener(PrismsPCL<?> pcl)
	{
		thePCLs.removeListener(pcl);
	}

	/**
	 * Removes a PropertyChangeListener from a single property
	 * 
	 * @param <T> The type of property to remove the listener for
	 * @param propName The name of the property to remove the listener from
	 * @param pcl The PropertyChangeListener to remove
	 */
	public <T> void removePropertyChangeListener(PrismsProperty<T> propName,
		PrismsPCL<? super T> pcl)
	{
		thePCLs.removeListener(propName, pcl);
	}

	/**
	 * Fires an event, notifying all listeners registered for the event type
	 * 
	 * @param event The event to fire
	 */
	public void fireEvent(PrismsEvent event)
	{
		PrismsEventListener [] listeners = getEventListeners(event.name);
		PrismsTransaction trans = getTransaction();
		for(PrismsEventListener l : listeners)
		{
			TrackNode [] tracks = new TrackNode [3];
			tracks[0] = prisms.util.PrismsUtils.track(trans, "Event Listener");
			tracks[1] = prisms.util.PrismsUtils.track(trans, "Event " + event.name);
			tracks[2] = prisms.util.PrismsUtils.track(trans, l);
			try
			{
				l.eventOccurred(this, event);
			} finally
			{
				for(int i = tracks.length - 1; i >= 0; i--)
					prisms.util.PrismsUtils.end(trans, tracks[i]);
			}
		}
	}

	/**
	 * Fires an event with implicit event creation
	 * 
	 * @param eventName The name of the event to fire
	 * @param args Alternating name, value pairs of all the named properties for the event
	 */
	public void fireEvent(String eventName, Object... args)
	{
		fireEvent(new PrismsEvent(eventName, args));
	}

	/**
	 * @param eventName The name of the event to get the listeners for
	 * @return All PluginEventListeners that will be notified when the given event is fired
	 */
	public PrismsEventListener [] getEventListeners(String eventName)
	{
		return theELs.getListeners(eventName);
	}

	/**
	 * Adds a PrismsEventListener to listen for all events
	 * 
	 * @param el The listener to be notified when any event fires
	 */
	public void addEventListener(PrismsEventListener el)
	{
		theELs.addListener(el);
	}

	/**
	 * Adds a PrismsEventListener to listen for a specific type of event
	 * 
	 * @param eventName The name of the event to listen for
	 * @param el The listener to be notified when an event of the given type is fired
	 */
	public void addEventListener(String eventName, PrismsEventListener el)
	{
		theELs.addListener(eventName, el);
	}

	/**
	 * This method removes the given listener from <b>EVERY</b> event type it is registered for,
	 * including the general case. The {@link Object#equals(Object)} method is used for comparison.
	 * 
	 * @param el The PrismsEventListener to remove
	 */
	public void removeEventListener(PrismsEventListener el)
	{
		theELs.removeListener(el);
	}

	/**
	 * Removes a PrismsEventListener from a single event type
	 * 
	 * @param eventName The name of the event type to remove the listener from
	 * @param el The PropertyChangeListener to remove
	 */
	public void removeEventListener(String eventName, PrismsEventListener el)
	{
		theELs.removeListener(eventName, el);
	}

	/** Discards all outgoing events posted since the last process call */
	public void clearOutgoingQueue()
	{
		theOutgoingQueue.clear();
	}

	/** @return The last time the user or client interacted with this session */
	public long getLastAccess()
	{
		return theLastCheckedTime;
	}

	/**
	 * Expires on a simple timeout. This can always be overridden to be more complex.
	 * 
	 * @return The amount of time until this session expires and should be destroyed. If < 0, then
	 *         the session is already expired
	 */
	public long untilExpires()
	{
		return theClient.getSessionTimeout() - System.currentTimeMillis() + theLastCheckedTime;
	}

	/**
	 * Kills this session
	 * 
	 * @param manager The manager application. If this argument is not the manager application, this
	 *        method will throw an exception
	 */
	public void kill(PrismsApplication manager)
	{
		if(!theApp.getEnvironment().isManager(manager))
			throw new IllegalArgumentException("Only the manager application may kill sessions");
		isKilled = true;
	}

	/** @return Whether this session has been killed */
	public boolean isKilled()
	{
		return isKilled;
	}

	/** Called when the session is no longer accessible or needed */
	public void destroy()
	{
		getApp().removeSession(this);
		fireEvent("destroy");
	}

	@Override
	public String toString()
	{
		return theApp.getName() + "/" + theClient.getName() + " session #"
			+ Integer.toHexString(hashCode()) + " for " + theUser.getName();
	}
}
