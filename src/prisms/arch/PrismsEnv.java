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
	private prisms.arch.ds.IDGenerator theIDs;

	private UserSource theUserSource;

	private ConnectionFactory theConnectionFactory;

	private Worker theWorker;

	private PrismsApplication theManagerApp;

	private final java.util.Map<String, String> theVariables;

	private final java.util.Map<String, PropertyManager<?> []> theGlobalManagers;

	private final java.util.Map<String, PrismsConfig []> theGMConfigs;

	private final prisms.util.ResourcePool<PrismsTransaction> theTransactionPool;

	private final java.util.concurrent.ConcurrentHashMap<Thread, PrismsTransaction> theActiveTransactions;

	private TrackerSet.TrackConfig[] theTrackConfigs;

	private prisms.util.ProgramTracker.PrintConfig theDefaultPrintConfig;

	private boolean isSealed;

	/** Creates an environment */
	public PrismsEnv()
	{
		theVariables = new java.util.LinkedHashMap<String, String>();
		theGlobalManagers = new java.util.HashMap<String, PropertyManager<?> []>();
		theGMConfigs = new java.util.HashMap<String, PrismsConfig []>();
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
		theDefaultPrintConfig = new prisms.util.ProgramTracker.PrintConfig();
		theDefaultPrintConfig.setOverallDisplayThreshold(1500);
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
	public prisms.util.ProgramTracker.PrintConfig getDefaultPrintConfig()
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
	 * @param name The name of the manager to be referenced later
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
	 * @param name The name of the manger to get
	 * @return The global property manager with the given name
	 */
	public PropertyManager<?> [] getManagers(String name)
	{
		return theGlobalManagers.get(name);
	}

	/**
	 * @param name The name of the manger to get the configuration for
	 * @return The configuration for the global property manager with the given name
	 */
	public PrismsConfig [] getManagerConfigs(String name)
	{
		return theGMConfigs.get(name);
	}

	PrismsTransaction transact(PrismsSession session, PrismsTransaction.Stage stage)
	{
		PrismsTransaction ret = theActiveTransactions.get(Thread.currentThread());
		if(ret != null)
			return ret;
		try
		{
			ret = theTransactionPool.getResource(true);
		} catch(prisms.util.ResourcePool.ResourceCreationException e)
		{
			// This exception is not thrown from this implementation
			throw new IllegalStateException("Should not be thrown from here!", e);
		}
		ret.init(session, stage);
		theActiveTransactions.put(Thread.currentThread(), ret);
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

	org.json.simple.JSONArray finish(PrismsTransaction trans)
	{
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
			if(trans.getThread() == null || !trans.getThread().isAlive())
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
