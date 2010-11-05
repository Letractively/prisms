/*
 * PrismsEnv.java Created Oct 27, 2010 by Andrew Butler, PSL
 */
package prisms.arch;

import prisms.arch.ds.UserSource;
import prisms.arch.event.PropertyManager;

/** Represents an environment in which the PRISMS architecture is accessed */
public class PrismsEnv
{
	private final UserSource theUserSource;

	private final PersisterFactory thePersisterFactory;

	private PrismsApplication theManagerApp;

	private final java.util.Map<String, PropertyManager<?> []> theGlobalManagers;

	private final java.util.Map<String, org.dom4j.Element[]> theGMConfigs;

	private boolean isConfigured;

	/**
	 * Creates an environment
	 * 
	 * @param userSource The user source that is being used in the environment
	 * @param factory The persister factory that is being used in the environment
	 */
	public PrismsEnv(UserSource userSource, PersisterFactory factory)
	{
		theUserSource = userSource;
		thePersisterFactory = factory;
		theGlobalManagers = new java.util.HashMap<String, PropertyManager<?> []>();
		theGMConfigs = new java.util.HashMap<String, org.dom4j.Element[]>();
	}

	/** @return The user source that is being used in this environment */
	public UserSource getUserSource()
	{
		return theUserSource;
	}

	/** @return The persister factory that is being used in this environment */
	public PersisterFactory getPersisterFactory()
	{
		return thePersisterFactory;
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
		if(isConfigured)
			throw new IllegalStateException("Cannot set the manager application after the"
				+ " environment has been configured");
		theManagerApp = managerApp;
	}

	/**
	 * Adds a global property manager to this environment
	 * 
	 * @param name The name of the manager to be referenced later
	 * @param manager The manager to add
	 * @param configXML The configuration XML to use to configure the manager for each application
	 *        that will use it
	 */
	public void addGlobalManager(String name, PropertyManager<?> manager,
		org.dom4j.Element configXML)
	{
		if(isConfigured)
			throw new IllegalStateException("Cannot add global managers after the"
				+ " environment has been configured");
		PropertyManager<?> [] managers = theGlobalManagers.get(name);
		org.dom4j.Element[] configs = theGMConfigs.get(name);
		if(managers == null)
		{
			managers = new PropertyManager [] {manager};
			configs = new org.dom4j.Element [] {configXML};
		}
		else
		{
			managers = prisms.util.ArrayUtils.add(managers, manager);
			configs = prisms.util.ArrayUtils.add(configs, configXML);
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
	 * @return The XML configuration for the global property manager with the given name
	 */
	public org.dom4j.Element[] getManagerConfigs(String name)
	{
		return theGMConfigs.get(name);
	}

	/** @return Whether this environment has finished being configured */
	public boolean isConfigured()
	{
		return isConfigured;
	}

	/** Marks this environment as completely configured and immutable */
	public void setConfigured()
	{
		isConfigured = true;
	}
}
