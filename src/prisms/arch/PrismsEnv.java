/*
 * PrismsEnv.java Created Oct 27, 2010 by Andrew Butler, PSL
 */
package prisms.arch;

import prisms.arch.ds.UserSource;
import prisms.arch.event.PropertyManager;
import prisms.util.TrackerSet;

/** Represents an environment in which the PRISMS architecture is accessed */
public class PrismsEnv implements prisms.util.Sealable
{
	/**
	 * An extension of {@link prisms.util.ProgramTracker.PrintConfig} with extra data used by
	 * transactions to determine how or if the data is printed
	 */
	public static class GlobalPrintConfig extends prisms.util.ProgramTracker.PrintConfig
	{
		private long thePrintThreshold;

		private long theDebugThreshold;

		private long theInfoThreshold;

		private long theWarnThreshold;

		private long theErrorThreshold;

		/** Creates a print config */
		public GlobalPrintConfig()
		{
			thePrintThreshold = 0;
			theDebugThreshold = Long.MAX_VALUE;
			theInfoThreshold = Long.MAX_VALUE;
			theWarnThreshold = Long.MAX_VALUE;
			theErrorThreshold = Long.MAX_VALUE;
		}

		/**
		 * @return The threshold below which no tracking data will be printed to System.out or the
		 *         log
		 */
		public long getPrintThreshold()
		{
			return thePrintThreshold;
		}

		/**
		 * @return The threshold above which tracking data will be written to the log as debug or
		 *         higher
		 */
		public long getDebugThreshold()
		{
			return theDebugThreshold;
		}

		/**
		 * @return The threshold above which tracking data will be written to the log as info or
		 *         higher
		 */
		public long getInfoThreshold()
		{
			return theInfoThreshold;
		}

		/**
		 * @return The threshold above which tracking data will be written to the log as warning or
		 *         higher
		 */
		public long getWarningThreshold()
		{
			return theWarnThreshold;
		}

		/** @return The threshold above which tracking data will be written to the log as error */
		public long getErrorThreshold()
		{
			return theErrorThreshold;
		}

		/** @param thresh The threshold below which no tracking data will be printed anywhere */
		public void setPrintThreshold(long thresh)
		{
			thePrintThreshold = thresh;
			if(thresh > theDebugThreshold)
				theDebugThreshold = thresh;
			if(thresh > theInfoThreshold)
				theInfoThreshold = thresh;
			if(thresh > theWarnThreshold)
				theWarnThreshold = thresh;
			if(thresh >= theErrorThreshold)
				theErrorThreshold = thresh;
		}

		/**
		 * @param thresh The threshold above which tracking data will be printed to the log as debug
		 *        or higher
		 */
		public void setDebugThreshold(long thresh)
		{
			theDebugThreshold = thresh;
			if(thresh < thePrintThreshold)
				thePrintThreshold = thresh;
			if(thresh > theInfoThreshold)
				theInfoThreshold = thresh;
			if(thresh > theWarnThreshold)
				theWarnThreshold = thresh;
			if(thresh > theErrorThreshold)
				theErrorThreshold = thresh;
		}

		/**
		 * @param thresh The threshold above which tracking data will be printed to the log as info
		 *        or higher
		 */
		public void setInfoThreshold(long thresh)
		{
			theInfoThreshold = thresh;
			if(thresh < thePrintThreshold)
				thePrintThreshold = thresh;
			if(thresh < theDebugThreshold)
				theDebugThreshold = thresh;
			if(thresh > theWarnThreshold)
				theWarnThreshold = thresh;
			if(thresh > theErrorThreshold)
				theErrorThreshold = thresh;
		}

		/**
		 * @param thresh The threshold above which tracking data will be printed to the log as
		 *        warning or higher
		 */
		public void setWarningThreshold(long thresh)
		{
			theWarnThreshold = thresh;
			if(thresh < thePrintThreshold)
				thePrintThreshold = thresh;
			if(thresh < theDebugThreshold)
				theDebugThreshold = thresh;
			if(thresh < theInfoThreshold)
				theInfoThreshold = thresh;
			if(thresh > theErrorThreshold)
				theErrorThreshold = thresh;
		}

		/**
		 * @param thresh The threshold above which tracking data will be printed to the log as error
		 */
		public void setErrorThreshold(long thresh)
		{
			theErrorThreshold = thresh;
			if(thresh < thePrintThreshold)
				thePrintThreshold = thresh;
			if(thresh < theDebugThreshold)
				theDebugThreshold = thresh;
			if(thresh < theInfoThreshold)
				theInfoThreshold = thresh;
			if(thresh < theWarnThreshold)
				theWarnThreshold = thresh;
		}
	}

	private prisms.arch.ds.IDGenerator theIDs;

	private UserSource theUserSource;

	private ConnectionFactory theConnectionFactory;

	private Worker theWorker;

	private prisms.logging.PrismsLogger theLogger;

	private PrismsApplication theManagerApp;

	private final java.util.Map<String, String> theVariables;

	private final java.util.Map<String, PropertyManager<?> []> theGlobalManagers;

	private final java.util.Map<String, PrismsConfig []> theGMConfigs;

	private final java.util.Map<String, PrismsConfig []> theGEventConfigs;

	private final java.util.Map<String, PrismsConfig []> theGMonitorConfigs;

	private final prisms.util.ResourcePool<PrismsTransaction> theTransactionPool;

	private final java.util.concurrent.ConcurrentHashMap<Thread, PrismsTransaction> theActiveTransactions;

	private TrackerSet.TrackConfig[] theTrackConfigs;

	private GlobalPrintConfig theDefaultPrintConfig;

	private boolean isSealed;

	/** Creates an environment */
	public PrismsEnv()
	{
		theLogger = new prisms.logging.PrismsLogger(this);
		theVariables = new java.util.LinkedHashMap<String, String>();
		theGlobalManagers = new java.util.HashMap<String, PropertyManager<?> []>();
		theGMConfigs = new java.util.HashMap<String, PrismsConfig []>();
		theGEventConfigs = new java.util.HashMap<String, PrismsConfig []>();
		theGMonitorConfigs = new java.util.HashMap<String, PrismsConfig []>();
		theTransactionPool = new prisms.util.ResourcePool<PrismsTransaction>(
			new prisms.util.ResourcePool.ResourceCreator<PrismsTransaction>()
			{
				public PrismsTransaction createResource()
					throws prisms.util.ResourcePool.ResourceCreationException
				{
					return new PrismsTransaction(getDefaultPrintConfig());
				}

				public void destroyResource(PrismsTransaction resource)
				{
				}
			}, Integer.MAX_VALUE);
		theActiveTransactions = new java.util.concurrent.ConcurrentHashMap<Thread, PrismsTransaction>();
		theDefaultPrintConfig = new GlobalPrintConfig();
		theDefaultPrintConfig.setPrintThreshold(1500);
		theDefaultPrintConfig.setTaskDisplayThreshold(100);
		theDefaultPrintConfig.setAccentThreshold(8);
	}

	/* The following methods are for initialization of the environment. These methods cannot be
	 * called after the environment has been configured. They are called during the initialization
	 * of the PRISMS architecture. */

	void setIDs(prisms.arch.ds.IDGenerator ids)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot set the ID generator after the"
				+ " environment has been configured");
		theIDs = ids;
	}

	void setUserSource(UserSource userSource)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot set the user source after the"
				+ " environment has been configured");
		theUserSource = userSource;
	}

	void setConnectionFactory(ConnectionFactory factory)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot set the connection factory after the"
				+ " environment has been configured");
		theConnectionFactory = factory;
	}

	void setWorker(Worker worker)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot set the worker after the"
				+ " environment has been configured");
		theWorker = worker;
	}

	void setTrackConfigs(prisms.util.TrackerSet.TrackConfig[] trackConfigs)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot set the tracking configuration after the"
				+ " environment has been configured");
		theTrackConfigs = trackConfigs;
	}

	void setVariable(String name, String value)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot set environment variables after the"
				+ " environment has been configured");
		String oldVal = theVariables.get(name);
		if(value == null ? oldVal == null : value.equals(oldVal))
			return; // No change
		if(oldVal != null)
			throw new IllegalStateException("Environment variable " + name
				+ " has already been set to " + theVariables.get(name)
				+ " and will not be changed to " + value);
		theVariables.put(name, value);
	}

	/* Public methods */

	/**
	 * @param name The name of the environment variable to get the value of
	 * @return The value of the given variable in this environment
	 */
	public String getVariable(String name)
	{
		return theVariables.get(name);
	}

	/** @return The names of all variables that have been set in this environment */
	public String [] getAllVariableNames()
	{
		return theVariables.keySet().toArray(new String [theVariables.size()]);
	}

	/** @return The logger that keeps track of log entries for this PRISMS environment */
	public prisms.logging.PrismsLogger getLogger()
	{
		return theLogger;
	}

	/** @return The ID generator used to configure IDs in this PRISMS environment */
	public prisms.arch.ds.IDGenerator getIDs()
	{
		return theIDs;
	}

	/** @return The user source that is being used in this environment */
	public UserSource getUserSource()
	{
		return theUserSource;
	}

	/** @return The connection factory that is being used in this environment */
	public ConnectionFactory getConnectionFactory()
	{
		return theConnectionFactory;
	}

	/** @return The worker that this PRISMS environment will use for asynchronous processing */
	public Worker getWorker()
	{
		return theWorker;
	}

	/** @return Default tracking configs used for tracking unless overridden in an application */
	public TrackerSet.TrackConfig[] getTrackConfigs()
	{
		return theTrackConfigs == null ? null : theTrackConfigs.clone();
	}

	/** @return The default print configuration that PRISMS will use to print tracking data */
	public GlobalPrintConfig getDefaultPrintConfig()
	{
		return theDefaultPrintConfig;
	}

	/** @return Whether a manager application has been configured for this environment yet */
	public boolean hasManager()
	{
		return theManagerApp != null;
	}

	/**
	 * @param app The application to test
	 * @return Whether the given application is the manager application in this PRISMS environment
	 */
	public boolean isManager(PrismsApplication app)
	{
		return theManagerApp == app;
	}

	void setManagerApp(PrismsApplication managerApp)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot set the manager application after the"
				+ " environment has been configured");
		theManagerApp = managerApp;
	}

	/**
	 * Adds a global property manager to this environment
	 * 
	 * @param name The name of the listener set to be referenced later
	 * @param manager The manager to add
	 * @param config The configuration to use to configure the manager for each application that
	 *        will use it
	 */
	public void addGlobalManager(String name, PropertyManager<?> manager, PrismsConfig config)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot add global managers after the"
				+ " environment has been configured");
		PropertyManager<?> [] managers = theGlobalManagers.get(name);
		PrismsConfig [] configs = theGMConfigs.get(name);
		if(managers == null)
		{
			managers = new PropertyManager [] {manager};
			configs = new PrismsConfig [] {config};
		}
		else
		{
			managers = prisms.util.ArrayUtils.add(managers, manager);
			configs = prisms.util.ArrayUtils.add(configs, config);
		}
		theGlobalManagers.put(name, managers);
		theGMConfigs.put(name, configs);
	}

	/**
	 * Adds a global event listener to this environment
	 * 
	 * @param name The name of the listener set to be referenced later
	 * @param config The configuration to use to configure the listener for each application that
	 *        will use it
	 */
	public void addGlobalEventListener(String name, PrismsConfig config)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot add global event listener after the"
				+ " environment has been configured");
		PrismsConfig [] configs = theGEventConfigs.get(name);
		if(configs == null)
			configs = new PrismsConfig [] {config};
		else
			configs = prisms.util.ArrayUtils.add(configs, config);
		theGEventConfigs.put(name, configs);
	}

	/**
	 * Adds a global session monitor to this environment
	 * 
	 * @param name The name of the listener set to be referenced later
	 * @param config The configuration to use to configure the monitor for each application that
	 *        will use it
	 */
	public void addGlobalMonitorListener(String name, PrismsConfig config)
	{
		if(isSealed)
			throw new IllegalStateException("Cannot add global session monitor after the"
				+ " environment has been configured");
		PrismsConfig [] configs = theGMonitorConfigs.get(name);
		if(configs == null)
			configs = new PrismsConfig [] {config};
		else
			configs = prisms.util.ArrayUtils.add(configs, config);
		theGMonitorConfigs.put(name, configs);
	}

	/**
	 * @param name The name of the listener set to get property managers of
	 * @return The global property managers from the given listener set
	 */
	public PropertyManager<?> [] getManagers(String name)
	{
		return theGlobalManagers.get(name);
	}

	/**
	 * @param name The name of the listener set to get the property manager configurations for
	 * @return The configurations for the global property managers from the given listener set
	 */
	public PrismsConfig [] getManagerConfigs(String name)
	{
		return theGMConfigs.get(name);
	}

	/**
	 * @param name The name of the event listener to get the event configurations for
	 * @return The configurations for the global event listeners from the given listener set
	 */
	public PrismsConfig [] getEventConfigs(String name)
	{
		return theGEventConfigs.get(name);
	}

	/**
	 * @param name The name of the listener set to get the monitor configuration for
	 * @return The configurations for the global session monitors from the given listener set
	 */
	public PrismsConfig [] getMonitorConfigs(String name)
	{
		return theGMonitorConfigs.get(name);
	}

	/**
	 * Initiates a transaction if one does not already exist for the current thread. Code after this
	 * method MUST be enclosed in a try/finally with a call to {@link #finish(PrismsTransaction)} in
	 * the finally block.
	 * 
	 * @param session The session that the transaction is for
	 * @param stage The stage of the transaction
	 * @return A transaction to use in this PRISMS environment
	 */
	public PrismsTransaction transact(PrismsSession session, PrismsTransaction.Stage stage)
	{
		Thread ct = Thread.currentThread();
		PrismsTransaction ret = theActiveTransactions.get(ct);
		if(ret != null)
		{
			ret.theDuplicateStartCount++;
			return ret;
		}
		try
		{
			ret = theTransactionPool.getResource(true);
		} catch(prisms.util.ResourcePool.ResourceCreationException e)
		{
			// This exception is not thrown from this implementation
			throw new IllegalStateException("Should not be thrown from here!", e);
		}
		ret.init(session, stage);
		theActiveTransactions.put(ct, ret);
		return ret;
	}

	/**
	 * Initiates a transaction if one does not already exist for the current thread. Code after this
	 * method MUST be enclosed in a try/finally with a call to {@link #finish(PrismsTransaction)} in
	 * the finally block.
	 * 
	 * @param app The application that the transaction is for
	 * @return A transaction to use in this PRISMS environment
	 */
	public PrismsTransaction transact(PrismsApplication app)
	{
		Thread ct = Thread.currentThread();
		PrismsTransaction ret = theActiveTransactions.get(ct);
		if(ret != null)
		{
			ret.theDuplicateStartCount++;
			return ret;
		}
		try
		{
			ret = theTransactionPool.getResource(true);
		} catch(prisms.util.ResourcePool.ResourceCreationException e)
		{
			// This exception is not thrown from this implementation
			throw new IllegalStateException("Should not be thrown from here!", e);
		}
		ret.init(app, PrismsTransaction.Stage.external);
		theActiveTransactions.put(ct, ret);
		return ret;
	}

	/**
	 * Gets the transaction associated with the current thread
	 * 
	 * @return The current thread's transaction
	 */
	public PrismsTransaction getTransaction()
	{
		return theActiveTransactions.get(Thread.currentThread());
	}

	/**
	 * Finishes a transaction
	 * 
	 * @param trans The transaction to finish
	 * @return The events that have been posted to the transaction
	 */
	public org.json.simple.JSONArray finish(PrismsTransaction trans)
	{
		if(trans.theDuplicateStartCount > 0)
		{
			trans.theDuplicateStartCount--;
			return trans.getEvents();
		}
		org.json.simple.JSONArray ret = trans.finish();
		theActiveTransactions.remove(Thread.currentThread());
		theTransactionPool.releaseResource(trans);
		return ret;
	}

	/** @return All transactions that are active in this environment */
	public PrismsTransaction [] getActiveTransactions()
	{
		java.util.Iterator<PrismsTransaction> iter = theActiveTransactions.values().iterator();
		while(iter.hasNext())
		{
			PrismsTransaction trans = iter.next();
			if(trans == null)
				continue;
			Thread thread = trans.getThread();
			if(thread == null)
				continue;
			if(!thread.isAlive())
			{
				if(!trans.isFinished())
					trans.clear();
				iter.remove();
				theTransactionPool.releaseResource(trans);
			}
		}
		return theActiveTransactions.values().toArray(new PrismsTransaction [0]);
	}

	/** @return Whether this environment has finished being configured */
	public boolean isSealed()
	{
		return isSealed;
	}

	/** Marks this environment as completely configured and immutable */
	public void seal()
	{
		isSealed = true;
	}
}
