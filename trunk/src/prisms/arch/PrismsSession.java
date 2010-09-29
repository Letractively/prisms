/**
 * PluginAppSession.java Created Aug 1, 2007 by Andrew Butler, PSL
 */
package prisms.arch;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.ds.User;
import prisms.arch.event.*;

/**
 * A PluginAppSession serves as the core of the plugin architecture and the remote session for the
 * remote communication architecture. It contains operations allowing plugins to communicate with
 * each other and with the client while requiring a minimum of knowledge about either the client or
 * the other plugins.
 */
public class PrismsSession
{
	/**
	 * Listens for events posted for the client
	 */
	public static interface EventListener
	{
		/**
		 * @param event The event that was posted
		 */
		void eventPosted(JSONObject event);
	}

	static final Logger log = Logger.getLogger(PrismsSession.class);

	private final PrismsApplication theApp;

	private final ClientConfig theClient;

	private final User theUser;

	private final long theCreationTime;

	private final java.util.concurrent.ConcurrentLinkedQueue<JSONObject> theOutgoingQueue;

	private long theLastCheckedTime;

	private final java.util.LinkedHashMap<String, AppPlugin> thePlugins;

	private final java.util.concurrent.ConcurrentHashMap<PrismsProperty<?>, Object> theProperties;

	private final java.util.Set<PrismsProperty<?>> thePropertyStack;

	@SuppressWarnings("rawtypes")
	private final ListenerManager<PrismsPCL> thePCLs;

	private final ListenerManager<PrismsEventListener> theELs;

	private final java.util.ArrayList<Runnable> theTaskList;

	private final java.util.concurrent.ConcurrentHashMap<Thread, JSONArray> theInvocations;

	private EventListener theListener;

	/**
	 * Creates a PluginAppSession
	 * 
	 * @param app The application that this session was created for
	 * @param client The client that this session was created for
	 * @param user The user to create the session for
	 */
	@SuppressWarnings("rawtypes")
	public PrismsSession(PrismsApplication app, ClientConfig client, User user)
	{
		theApp = app;
		theClient = client;
		theUser = user;
		theCreationTime = System.currentTimeMillis();
		theOutgoingQueue = new java.util.concurrent.ConcurrentLinkedQueue<JSONObject>();
		theLastCheckedTime = System.currentTimeMillis();
		thePlugins = new java.util.LinkedHashMap<String, AppPlugin>();
		theProperties = new java.util.concurrent.ConcurrentHashMap<PrismsProperty<?>, Object>();
		thePropertyStack = java.util.Collections
			.synchronizedSet(new java.util.HashSet<PrismsProperty<?>>());
		thePCLs = new ListenerManager<PrismsPCL>(PrismsPCL.class);
		theELs = new ListenerManager<PrismsEventListener>(PrismsEventListener.class);
		theTaskList = new java.util.ArrayList<Runnable>();
		theInvocations = new java.util.concurrent.ConcurrentHashMap<Thread, JSONArray>();
	}

	/**
	 * @param listener The listener to listen to this session for posted events to be sent to the
	 *        client
	 */
	public void setListener(EventListener listener)
	{
		theListener = listener;
	}

	/**
	 * @return The application that created this session
	 */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/**
	 * @return The client that configured this session
	 */
	public ClientConfig getClient()
	{
		return theClient;
	}

	/**
	 * @return The user of this session
	 */
	public User getUser()
	{
		return theUser;
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

	/**
	 * @return The time that this session was created
	 */
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
		synchronized(thePlugins)
		{
			return thePlugins.get(name);
		}
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

	/**
	 * Resets this session's counter so that it doesn't timeout
	 */
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
		JSONArray ret = new JSONArray();
		theInvocations.put(Thread.currentThread(), ret);
		try
		{
			process(event);
		} finally
		{
			theInvocations.remove(Thread.currentThread());
		}
		return ret;
	}

	/**
	 * Processes events from the remote client asynchronously
	 * 
	 * @param event The event to process
	 * @param finished A boolean array whose first element will be set to true when the event has
	 *        been processed
	 */
	public void processAsync(final JSONObject event, final boolean [] finished)
	{
		Runnable toRun = new Runnable()
		{
			public void run()
			{
				try
				{
					process(event);
				} finally
				{
					if(finished != null && finished.length > 0)
						finished[0] = true;
				}
			}
		};
		theApp.getWorker().run(toRun, new Worker.ErrorListener()
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
		theApp.runScheduledTasks();
		if(!theTaskList.isEmpty())
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
			plugin.processEvent(evt);
			renew();
		}

		if(!theTaskList.isEmpty())
			runTasks();
		theApp.runScheduledTasks();
	}

	/**
	 * Runs all tasks added with {@link #runEventually(Runnable)}
	 */
	public void runTasks()
	{
		Runnable [] tasks;
		synchronized(theTaskList)
		{
			tasks = theTaskList.toArray(new Runnable [theTaskList.size()]);
			theTaskList.clear();
		}
		Exception outerE = new Exception();
		for(Runnable task : tasks)
		{
			try
			{
				task.run();
			} catch(Throwable e)
			{
				e.setStackTrace(prisms.util.PrismsUtils.patchStackTraces(e.getStackTrace(),
					outerE.getStackTrace(), getClass().getName(), "_process"));
				log.error("Error Processing Task " + task, e);
				postOutgoingEvent(wrapError("Error Processing Task " + task, e));
			}
		}
	}

	JSONObject wrapError(String title, Throwable e)
	{
		JSONObject ret = new JSONObject();
		ret.put("method", "error");
		ret.put("code", PrismsServer.ErrorCode.ApplicationError.description);
		ret.put("title", title);
		ret.put("message", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
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

	/**
	 * Initializes this session for a client that has just connected or reconnected to it
	 */
	public void init()
	{
		clearOutgoingQueue();
		final boolean [] finished = new boolean [] {false};
		try
		{
			java.util.Map.Entry<String, AppPlugin> [] plugins;
			synchronized(thePlugins)
			{
				plugins = thePlugins.entrySet()
					.toArray(new java.util.Map.Entry [thePlugins.size()]);
			}
			long time = System.currentTimeMillis();
			for(java.util.Map.Entry<String, AppPlugin> p : plugins)
			{
				try
				{
					p.getValue().initClient();
					long newTime = System.currentTimeMillis();
					if(log.isDebugEnabled())
					{
						StringBuilder toPrint = new StringBuilder();
						toPrint.append("Initialized plugin ");
						toPrint.append(p.getKey());
						toPrint.append(" in ");
						toPrint.append(prisms.util.PrismsUtils.printTimeLength(newTime - time));
						log.debug(toPrint);
					}
				} catch(Throwable e)
				{
					log.error("Could not client-initialize plugin " + p.getKey(), e);
					JSONObject event = new JSONObject();
					event.put("method", "error");
					event.put("message",
						"Could not initialize plugin " + p.getKey() + ": " + e.getMessage());
					postOutgoingEvent(event);
				}
			}
		} finally
		{
			finished[0] = true;
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
		JSONArray serviceOut = theInvocations.get(Thread.currentThread());
		if(serviceOut != null)
		{
			serviceOut.add(evt);
			return;
		}
		else if(theClient.isService())
			return; // Events must be tied to an invocation if this session is for a web service
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
		synchronized(theTaskList)
		{
			theTaskList.add(task);
		}
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
			{
				if(p.equals(plugins.next()))
					plugins.remove();
			}
		}
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
	 * @param propName The name of the property to change
	 * @param propValue The new value for the property
	 * @param eventProps Event properties for the property change event that is fired. Must be in
	 *        the form of name, value, name, value, where name is a string.
	 */
	public <T> void setProperty(PrismsProperty<T> propName, T propValue, Object... eventProps)
	{
		// Generics *can* be defeated--check the type here
		if(propValue != null && !propName.getType().isInstance(propValue))
			throw new IllegalArgumentException("Cannot set an instance of "
				+ propValue.getClass().getName() + " for property " + propName + ", type "
				+ propName.getType().getName());
		if(eventProps.length % 2 != 0)
			throw new IllegalArgumentException("Event properties for property change event must be"
				+ " in the form of name, value, name, value, etc.--" + eventProps.length
				+ " arguments illegal");
		/* Many property sets can be going on at once, but only one for each property in a session
		 */
		synchronized(getPropertyLock(propName))
		{
			PrismsPCL<T> [] listeners;
			thePropertyStack.add(propName);
			if(propValue == null)
				theProperties.remove(propName);
			else
				theProperties.put(propName, propValue);
			listeners = getPropertyChangeListeners(propName);

			PrismsPCE<T> propEvt = new PrismsPCE<T>(this, propName,
				(T) theProperties.get(propName), propValue);
			for(int i = 0; i < eventProps.length; i += 2)
			{
				if(!(eventProps[i] instanceof String))
					throw new IllegalArgumentException("Event properties for property change event"
						+ " must be in the form of name, value, name, value, etc.--eventProps[" + i
						+ "] is not a string");
				propEvt.set((String) eventProps[i], eventProps[i + 1]);
			}
			for(PrismsPCL<T> l : listeners)
			{
				l.propertyChange(propEvt);
				/* If this property is changed as a result of the above PCL, stop this notification
				 */
				if(!thePropertyStack.contains(propName))
					break;
			}
			thePropertyStack.remove(propName);
		}
	}

	private java.util.HashMap<PrismsProperty<?>, Object> thePropertyLocks;

	private synchronized Object getPropertyLock(PrismsProperty<?> property)
	{
		if(thePropertyLocks == null)
			thePropertyLocks = new java.util.HashMap<PrismsProperty<?>, Object>();
		Object ret = thePropertyLocks.get(property);
		if(ret == null)
		{
			ret = new Object();
			thePropertyLocks.put(property, ret);
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
	public <T> void removePropertyChangeListener(PrismsProperty<T> propName, PrismsPCL<T> pcl)
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
		for(PrismsEventListener l : listeners)
			l.eventOccurred(event);
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

	/**
	 * Discards all outgoing events posted since the last process call
	 */
	public void clearOutgoingQueue()
	{
		theOutgoingQueue.clear();
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
	 * Called when the session is no longer accessible or needed
	 */
	public void destroy()
	{
		getApp().removeSession(this);
		fireEvent(new PrismsEvent("destroy"));
	}
}
