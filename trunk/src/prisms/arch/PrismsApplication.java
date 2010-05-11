/**
 * PluginApplication.java Created Aug 2, 2007 by Andrew Butler, PSL
 */
package prisms.arch;

import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.dom4j.Element;

import prisms.arch.ds.UserGroup;
import prisms.arch.ds.UserSource;
import prisms.arch.event.*;

/**
 * PluginApplication represents a type of application from which sessions may be instantiated that
 * interact with clients. A PluginAppSession is to its PluginApplication what an object is to its
 * class.
 */
public class PrismsApplication
{
	private static Logger log = Logger.getLogger(PrismsApplication.class);

	/**
	 * A task to be run on a session
	 */
	public static interface SessionTask
	{
		/**
		 * Runs the task
		 * 
		 * @param session The session to run the task on
		 */
		void run(PrismsSession session);
	}

	private static class EventListenerType
	{
		final String theEventName;

		final Class<? extends PrismsEventListener> theListenerType;

		final Element theConfigEl;

		EventListenerType(String eventName, Class<? extends PrismsEventListener> listenerType,
			Element configEl)
		{
			theEventName = eventName;
			theListenerType = listenerType;
			theConfigEl = configEl;
		}
	}

	private static class MonitorType
	{
		final Class<? extends SessionMonitor> theMonitorType;

		final Element theConfigEl;

		MonitorType(Class<? extends SessionMonitor> monitorType, Element configEl)
		{
			theMonitorType = monitorType;
			theConfigEl = configEl;
		}
	}

	private PrismsServer theServer;

	private UserSource theDataSource;

	private String theName;

	private int [] theVersion;

	private long theModifiedDate;

	private boolean isConfigured;

	private ArrayList<UserGroup> theAdminGroups;

	private ArrayList<PrismsSession> theSessions;

	private java.util.Collection<PropertyManager<?>> theManagers;

	private java.util.Collection<EventListenerType> theELTypes;

	private java.util.Collection<MonitorType> theMonitorTypes;

	private ArrayList<ScheduledTask> theOneTimeTasks;

	private ArrayList<ScheduledTask> theRecurringTasks;

	private ArrayList<Runnable> theDestroyTasks;

	private java.util.HashSet<PrismsProperty<?>> thePropertyStack;

	@SuppressWarnings("unchecked")
	private ListenerManager<PrismsPCL> thePCLs;

	private java.util.concurrent.locks.ReentrantLock thePropLock;

	private Worker theWorker;

	private String theAppLock;

	private PrismsSession theLocker;

	private long theReloadTime;

	private long theServiceReloadTime;

	private String theReloadMessage;

	/**
	 * Creates a PluginApplication
	 */
	@SuppressWarnings("unchecked")
	public PrismsApplication()
	{
		theAdminGroups = new ArrayList<UserGroup>();
		theSessions = new ArrayList<PrismsSession>();
		theManagers = new ArrayList<PropertyManager<?>>();
		theELTypes = new java.util.ArrayList<EventListenerType>();
		theMonitorTypes = new java.util.ArrayList<MonitorType>();
		theOneTimeTasks = new ArrayList<ScheduledTask>();
		theRecurringTasks = new ArrayList<ScheduledTask>();
		theDestroyTasks = new ArrayList<Runnable>();
		thePCLs = new ListenerManager<PrismsPCL>(PrismsPCL.class);
		thePropLock = new java.util.concurrent.locks.ReentrantLock();
		thePropertyStack = new java.util.HashSet<PrismsProperty<?>>();
	}

	/**
	 * @return The server that this application was created for
	 */
	public PrismsServer getServer()
	{
		return theServer;
	}

	/**
	 * @param server The server that this application was created for
	 */
	public void setServer(PrismsServer server)
	{
		theServer = server;
	}

	/**
	 * @return Whether this application has been fully configured
	 */
	public boolean isConfigured()
	{
		return isConfigured;
	}

	/**
	 * Marks this application as fully configured
	 */
	public void setConfigured()
	{
		isConfigured = true;
	}

	/**
	 * @return This application's version
	 */
	public int [] getVersion()
	{
		return theVersion;
	}

	/**
	 * @return This application's version as a string
	 */
	public String getVersionString()
	{
		if(theVersion == null)
			return "Unknown";
		StringBuilder ret = new StringBuilder();
		for(int v = 0; v < theVersion.length; v++)
		{
			ret.append(theVersion[v]);
			if(v < theVersion.length - 1)
				ret.append('.');
		}
		return ret.toString();
	}

	/**
	 * Sets this application's version
	 * 
	 * @param version The version for this application
	 */
	public void setVersion(int [] version)
	{
		theVersion = version;
	}

	/**
	 * @return The time when this application was last developed
	 */
	public long getModifiedDate()
	{
		return theModifiedDate;
	}

	/**
	 * @return The time when this application was last developed, as a string
	 */
	public String getModifiedDateString()
	{
		if(theModifiedDate <= 0)
			return "Unknown";
		else
		{
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("ddMMMyyyy");
			sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
			return sdf.format(new java.util.Date(theModifiedDate));
		}
	}

	/**
	 * Sets this application's modification date
	 * 
	 * @param modified the tiime when this application was last developed
	 */
	public void setModifiedDate(long modified)
	{
		theModifiedDate = modified;
	}

	/**
	 * @return The data source that created this application
	 */
	public UserSource getDataSource()
	{
		return theDataSource;
	}

	/**
	 * @param ds The data source that created this application
	 */
	public void setDataSource(UserSource ds)
	{
		theDataSource = ds;
	}

	/**
	 * @return The name of this application
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * @param name The name for this application
	 */
	public void setName(String name)
	{
		theName = name;
	}

	/**
	 * @return Groups whose users can administrate this application
	 */
	public UserGroup [] getAdminGroups()
	{
		synchronized(theAdminGroups)
		{
			return theAdminGroups.toArray(new UserGroup [theAdminGroups.size()]);
		}
	}

	/**
	 * Gives a group permission to administrate this application
	 * 
	 * @param group The group to give administration priveleges to
	 */
	public void addAdminGroup(UserGroup group)
	{
		synchronized(theAdminGroups)
		{
			theAdminGroups.add(group);
		}
	}

	/**
	 * Removes the permission of a group to administrate this application
	 * 
	 * @param group The group to remove from administration priveleges
	 */
	public void removeAdminGroup(UserGroup group)
	{
		synchronized(theAdminGroups)
		{
			theAdminGroups.remove(group);
		}
	}

	/**
	 * Tells this application to manage a property, ensuring that all sessions in this application
	 * always have access to the appropriate value
	 * 
	 * @param <T> The type of the manager to add
	 * @param manager The manager for a property
	 */
	public <T> void addManager(PropertyManager<T> manager)
	{
		synchronized(theManagers)
		{
			theManagers.add(manager);
		}
		for(PrismsSession s : getSessions())
			s.addPropertyChangeListener(manager.getProperty(), manager);
	}

	/**
	 * @return All property managers registered with this application
	 */
	public PropertyManager<?> [] getManagers()
	{
		return theManagers.toArray(new PropertyManager [theManagers.size()]);
	}

	/**
	 * @param <T> The type of the manager to add
	 * @param property The property to get the managers for
	 * @return All PropertyManagers registered to manage the value of the given property
	 */
	public <T> PropertyManager<T> [] getManagers(PrismsProperty<T> property)
	{
		PropertyManager<T> [] ret = new PropertyManager [0];
		for(PropertyManager<?> pm : theManagers)
			if(pm.getProperty().equals(property))
				ret = prisms.util.ArrayUtils.add(ret, (PropertyManager<T>) pm);
		return ret;
	}

	/**
	 * Adds a template for an event listener, which will be added to each session as it is created
	 * 
	 * @param eventName The name of the event to listener
	 * @param listenerType The class of the listener to create for each session
	 * @param configEl The element used to configure new instances of the listener type
	 */
	public void addEventListenerType(String eventName,
		Class<? extends PrismsEventListener> listenerType, Element configEl)
	{
		theELTypes.add(new EventListenerType(eventName, listenerType, configEl));
	}

	/**
	 * Adds a template for a session monitor, which will be added to each session as it is created
	 * 
	 * @param monitorType The class of the monitor to create for each session
	 * @param configEl The configuration element to configure the monitor
	 */
	public void addMonitorType(Class<? extends SessionMonitor> monitorType,
		org.dom4j.Element configEl)
	{
		theMonitorTypes.add(new MonitorType(monitorType, configEl));
	}

	/**
	 * Searches this application's property managers for the application value of a property. This
	 * may be called from application-level objects such as property managers and serializers. This
	 * should <b>NEVER</b> be called from session-level objects such as plugin instances. These
	 * should rely on the session-specific values.
	 * 
	 * @param <T> The type of property to get
	 * @param propName The name of the property to get the application value of
	 * @return The non-null value of the property as determined by this application's property
	 *         managers; or null if no managers exist or if the property's value is not
	 *         application-governed.
	 */
	public <T> T getGlobalProperty(PrismsProperty<T> propName)
	{
		T ret = null;
		for(PropertyManager<?> manager : theManagers)
		{
			if(manager.getProperty() != null && manager.getProperty().equals(propName))
			{
				if(ret == null)
					ret = ((PropertyManager<T>) manager).getApplicationValue();
				else
				{
					T temp = ((PropertyManager<T>) manager).getApplicationValue();
					if(temp != null && !ret.equals(temp))
						throw new IllegalStateException("Managers have two different values for"
							+ " property " + propName);
				}
			}
		}
		return ret;
	}

	/**
	 * Sets the application value of a property
	 * 
	 * @param <T> The type of the property to set
	 * @param prop The property to set the value of
	 * @param value The value of the property to set
	 */
	public <T> void setGlobalProperty(PrismsProperty<T> prop, T value)
	{
		PrismsPCE<T> propEvt = new PrismsPCE<T>(this, prop, null, value);
		PrismsPCL<T> [] listeners = getGlobalPropertyChangeListeners(prop);
		thePropLock.lock();
		PropertyManager<T> manager = null;
		try
		{
			for(PropertyManager<?> mgr : theManagers)
			{
				if(mgr != manager && mgr.getProperty().equals(prop))
				{
					PropertyManager<T> propMgr = (PropertyManager<T>) mgr;
					if(manager == null && propMgr.getApplicationValue() != null)
						manager = propMgr;
					propMgr.propertyChange(propEvt);
				}
			}
			value = getGlobalProperty(prop);
			thePropertyStack.add(prop);
			try
			{
				for(PrismsPCL<T> l : listeners)
				{
					l.propertyChange(propEvt);
					/*
					 * If this property is changed as a result of the above PCL, stop this notification
					 */
					if(!thePropertyStack.contains(prop))
						break;
				}
				for(PrismsSession session : getSessions())
				{
					T sessionValue = session.getProperty(prop);
					if(manager == null && !prisms.util.ArrayUtils.equals(sessionValue, value))
						session.setProperty(prop, sessionValue);
					else if(manager != null
						&& !manager.isValueCorrect(session, session.getProperty(prop)))
						session.setProperty(prop, manager.getCorrectValue(session));
				}
			} finally
			{
				thePropertyStack.remove(prop);
			}
		} finally
		{
			thePropLock.unlock();
		}
	}

	/**
	 * Signals that data for a globally managed property is changed. This is called when the data
	 * available to the application is changed, not just that available to a particular session.
	 * This method does not change the data in a session--it merely fires the appropriate property
	 * change listeners.
	 * 
	 * @param <T> The type of property that changed
	 * @param prop The property whose value is changed
	 * @param manager The manager of the changed property
	 * @param value The new global value of the property
	 */
	public <T> void fireGlobalPropertyChange(PrismsProperty<T> prop, PropertyManager<T> manager,
		T value)
	{
		PrismsPCE<T> propEvt = new PrismsPCE<T>(manager != null ? manager : this, prop, null, value);
		PrismsPCL<T> [] listeners = getGlobalPropertyChangeListeners(prop);
		thePropLock.lock();
		try
		{
			thePropertyStack.add(prop);
			for(PrismsPCL<T> l : listeners)
			{
				l.propertyChange(propEvt);
				/*
				 * If this property is changed as a result of the above PCL, stop this notification
				 */
				if(!thePropertyStack.contains(prop))
					break;
			}
			thePropertyStack.remove(prop);
		} finally
		{
			thePropLock.unlock();
		}
	}

	/**
	 * Registers to listen for a global property change. This may be used by managers of other
	 * properties whose value depends on the value of other proprties. This should <b>NEVER</b> be
	 * called by a session-level object such as a plugin instance. Instead those objects should
	 * listen to the session-specific values of the properties.
	 * 
	 * @param <T> The type of property to listen for
	 * @param property The property to listen for
	 * @param pcl The listener to notify when the property changes
	 */
	public <T> void addGlobalPropertyChangeListener(PrismsProperty<T> property, PrismsPCL<T> pcl)
	{
		thePCLs.addListener(property, pcl);
	}

	/**
	 * Unregisters a listener for a property
	 * 
	 * @param <T> The type of property to remove the listener for
	 * @param property The property that is currently being listened for
	 * @param pcl The listener to unregister
	 */
	public <T> void removeGlobalPropertyChangeListener(PrismsProperty<T> property, PrismsPCL<T> pcl)
	{
		thePCLs.removeListener(property, pcl);
	}

	/**
	 * @param <T> The type of property to get the listeners for
	 * @param propName The property name to get the listeners for
	 * @return All listeners listening to the given property
	 */
	public <T> PrismsPCL<T> [] getGlobalPropertyChangeListeners(PrismsProperty<T> propName)
	{
		return thePCLs.getListeners(propName);
	}

	/**
	 * Runs the given task for all sessions being served by this application
	 * 
	 * @param session The session requesting the task be run
	 * @param task The task to run
	 * @param excludeSession Whether the given session should be excluded from the task
	 */
	public void runSessionTask(PrismsSession session, final SessionTask task, boolean excludeSession)
	{
		for(PrismsSession session_i : getSessions())
		{
			if(!isOpen(session_i))
				continue;
			if(session_i == session)
			{
				if(!excludeSession)
					task.run(session);
			}
			else
			{
				final PrismsSession s = session_i;
				final Exception e = new Exception();
				s.runEventually(new Runnable()
				{
					public void run()
					{
						try
						{
							task.run(s);
						} catch(RuntimeException e2)
						{
							e2.setStackTrace(prisms.util.PrismsUtils.patchStackTraces(e2
								.getStackTrace(), e.getStackTrace(), getClass().getName(), "run"));
							throw e2;
						}
					}
				});
			}
		}
	}

	/**
	 * Fires a plugin event for all sessions in an application
	 * 
	 * @param session The session where the event originated
	 * @param toFire The event to fire
	 */
	public void fireGlobally(PrismsSession session, final PrismsEvent toFire)
	{
		toFire.setProperty("globalEvent", Boolean.TRUE);
		runSessionTask(session, new SessionTask()
		{
			public void run(PrismsSession s)
			{
				s.fireEvent(toFire);
			}
		}, false);
	}

	/**
	 * Configures a new session created for this application
	 * 
	 * @param session The session to configure
	 */
	public void configureSession(PrismsSession session)
	{
		addPropertyManagers(session);
		addEventListeners(session);
		addSessionMonitors(session);
		synchronized(theSessions)
		{
			theSessions.add(session);
		}
	}

	/**
	 * Schedules a task to run at a specific time. The task will be run at or after the specified
	 * time at the next time this application is called.
	 * 
	 * @param task The task to run
	 * @param execTime The time to execute the task after
	 */
	public void scheduleOneTimeTask(Runnable task, long execTime)
	{
		synchronized(theOneTimeTasks)
		{
			theOneTimeTasks.add(new ScheduledTask(task, execTime, 0));
		}
	}

	/**
	 * Schedules a task to run at a given frequency. The task will be run at the given frequency at
	 * the highest, depending on when and how often this application is called.
	 * 
	 * @param task The task to run
	 * @param freq The minimum frequency to run the task at
	 */
	public void scheduleRecurringTask(Runnable task, long freq)
	{
		synchronized(theRecurringTasks)
		{
			theRecurringTasks.add(new ScheduledTask(task, System.currentTimeMillis(), freq));
		}
	}

	/**
	 * Locks this application from user interaction or unlocks it
	 * 
	 * @param message The message that should be displayed to users who try to access the
	 *        application, or null if the application is to be unlocked
	 * @param locker The session that locked the application--the application will not be locked
	 *        against this session
	 */
	public void setApplicationLock(String message, PrismsSession locker)
	{
		theLocker = locker;
		theAppLock = message;
	}

	/**
	 * @return The message that should be displayed to users who try to access the application if it
	 *         is locked, or null if the application is not locked
	 */
	public String getApplicationLock()
	{
		return theAppLock;
	}

	/**
	 * Causes all sessions of this application to expire. The user will be given a message on what
	 * caused the need to reload.
	 * 
	 * @param message The message to display to the user
	 * @param includeServices Whether services should be reloaded also
	 */
	public void reloadAll(String message, boolean includeServices)
	{
		theReloadTime = System.currentTimeMillis();
		if(includeServices)
			theServiceReloadTime = theReloadTime;
		theReloadMessage = message;
	}

	/**
	 * Reloads the persistent properties in this application from their respective data sources
	 */
	@SuppressWarnings("unchecked")
	public void reloadProperties()
	{
		for(PropertyManager manager : theManagers)
		{
			if(manager instanceof prisms.util.persisters.PersistingPropertyManager)
			{
				final prisms.util.persisters.PersistingPropertyManager ppm;
				ppm = (prisms.util.persisters.PersistingPropertyManager) manager;
				if(ppm.getPersister() == null)
					continue;
				ppm.getPersister().reload();
				ppm.setValue(ppm.getPersister().getValue());
				runSessionTask(null, new SessionTask()
				{
					public void run(PrismsSession session)
					{
						session.setProperty(ppm.getProperty(), ppm.getCorrectValue(session));
					}
				}, false);
			}
		}
	}

	/**
	 * Allows the application to fire events directly to the session
	 * 
	 * @param session The session to fire events to
	 */
	public void putApplicationEvents(PrismsSession session)
	{
		String appLock = theAppLock;
		if(session.getCreationTime() < theReloadTime)
		{
			if(!session.getClient().isService() || session.getCreationTime() < theServiceReloadTime)
				session.postOutgoingEvent(prisms.util.PrismsUtils.rEventProps("method", "restart",
					"message", theReloadMessage));
		}
		else if(session != theLocker && appLock != null)
			session.postOutgoingEvent(prisms.util.PrismsUtils.rEventProps("method", "appLocked",
				"message", appLock));
	}

	/**
	 * Checks to see whether a session is still active in the application
	 * 
	 * @param session The session to check
	 * @return Whether the session should still be accessible
	 */
	public boolean isOpen(PrismsSession session)
	{
		if(session.getCreationTime() < theReloadTime)
		{
			if(!session.getClient().isService() || session.getCreationTime() < theServiceReloadTime)
				return false;
		}
		return true;
	}

	/**
	 * Runs tasks that are scheduled with this application to be run
	 */
	protected void runScheduledTasks()
	{
		long currentTime = System.currentTimeMillis();
		ScheduledTask [] tasks = new ScheduledTask [0];
		synchronized(theOneTimeTasks)
		{
			java.util.Iterator<ScheduledTask> it = theOneTimeTasks.iterator();
			while(it.hasNext())
			{
				ScheduledTask task = it.next();
				if(task.shouldRun(currentTime))
				{
					tasks = prisms.util.ArrayUtils.add(tasks, task);
					it.remove();
				}
			}
			tasks = prisms.util.ArrayUtils.mergeInclusive(ScheduledTask.class, tasks,
				theRecurringTasks.toArray(new ScheduledTask [0]));
		}
		for(ScheduledTask t : tasks)
			t.run(currentTime);
	}

	/**
	 * Adds all registered property managers to the session
	 * 
	 * @param session The session to add the property managers to
	 */
	@SuppressWarnings("unchecked")
	protected void addPropertyManagers(PrismsSession session)
	{
		PropertyManager [] mgrs;
		synchronized(theManagers)
		{
			mgrs = theManagers.toArray(new PropertyManager [0]);
		}
		for(PropertyManager mgr : mgrs)
		{
			try
			{
				if(!mgr.isValueCorrect(session, session.getProperty(mgr.getProperty())))
					session.setProperty(mgr.getProperty(), mgr.getCorrectValue(session));
				session.addPropertyChangeListener(mgr.getProperty(), mgr);
			} catch(RuntimeException e)
			{
				log.error("Could not add property manager " + mgr + " for property "
					+ mgr.getProperty() + " to session", e);
				throw e;
			}
		}
	}

	/**
	 * Adds instances of all registered event listener types to the session
	 * 
	 * @param session The session to add the event listeners to
	 */
	protected void addEventListeners(PrismsSession session)
	{
		for(EventListenerType elt : theELTypes)
		{
			PrismsEventListener pel;
			try
			{
				pel = elt.theListenerType.newInstance();
			} catch(Exception e)
			{
				log.error("Could not instantiate event listener " + elt.theEventName + ", type "
					+ elt.theListenerType.getName(), e);
				continue;
			}
			if(pel instanceof ConfiguredPEL)
			{
				try
				{
					((ConfiguredPEL) pel).configure(session, elt.theConfigEl);
				} catch(Throwable e)
				{
					log.error("Could not configure listener " + elt.theEventName
						+ " for application " + theName, e);
					continue;
				}
			}
			if(elt.theEventName == null)
				session.addEventListener(pel);
			else
				session.addEventListener(elt.theEventName, pel);
		}
	}

	/**
	 * Adds instances of all registered session monitor types to the session
	 * 
	 * @param session The session to add the monitors to
	 */
	protected void addSessionMonitors(PrismsSession session)
	{
		for(MonitorType mt : theMonitorTypes)
		{
			SessionMonitor sm;
			try
			{
				sm = mt.theMonitorType.newInstance();
			} catch(Exception e)
			{
				log.error("Could not instantiate session monitor " + mt.theMonitorType, e);
				continue;
			}
			sm.register(session, mt.theConfigEl);
		}
	}

	/**
	 * Removes the session from this application's list of active sessions
	 * 
	 * @param session The session to remove
	 */
	void removeSession(PrismsSession session)
	{
		synchronized(theSessions)
		{
			theSessions.remove(session);
		}
	}

	/**
	 * @return The list of active sessions that this application serves
	 */
	private PrismsSession [] getSessions()
	{
		synchronized(theSessions)
		{
			return theSessions.toArray(new PrismsSession [theSessions.size()]);
		}
	}

	/**
	 * Schedules a task to be run when the application is destroyed
	 * 
	 * @param task The task to run
	 */
	public void addDestroyTask(Runnable task)
	{
		synchronized(theDestroyTasks)
		{
			theDestroyTasks.add(task);
		}
	}

	/**
	 * Called when the application is no longer accessible or needed
	 */
	public void destroy()
	{
		synchronized(theSessions)
		{
			PrismsSession [] sessions = theSessions.toArray(new PrismsSession [theSessions.size()]);
			sessions = new PrismsSession [0];
			for(PrismsSession s : sessions)
				s.destroy();
		}
		synchronized(theDestroyTasks)
		{
			Runnable [] tasks = theDestroyTasks.toArray(new Runnable [0]);
			theDestroyTasks.clear();
			for(Runnable r : tasks)
			{
				try
				{
					r.run();
				} catch(Throwable e)
				{
					log.error("Error running destroy task " + r, e);
				}
			}
		}
		if(theWorker != null)
			theWorker.close();
	}

	/**
	 * @return This application's worker for running background tasks
	 */
	public Worker getWorker()
	{
		return theWorker;
	}

	/**
	 * Sets the worker that does background tasks for this application
	 * 
	 * @param w The worker to set for the application
	 */
	public void setWorker(Worker w)
	{
		Worker oldWorker = theWorker;
		theWorker = w;
		if(oldWorker != null)
			oldWorker.close();
	}

	private static class ScheduledTask
	{
		private final Runnable theTask;

		private long theTime;

		private final long theFrequency;

		ScheduledTask(Runnable task, long time, long freq)
		{
			theTask = task;
			theTime = time;
			theFrequency = freq;
		}

		boolean shouldRun(long currentTime)
		{
			return currentTime >= theTime;
		}

		boolean run(long currentTime)
		{
			if(currentTime < theTime)
				return false;
			if(theFrequency > 0)
				theTime = currentTime + theFrequency;
			theTask.run();
			return true;
		}
	}
}
