/*
 * PlaceholderAppConfig.java Created Dec 4, 2009 by Andrew Butler, PSL
 */
package prisms.impl;

/**
 * A placeholder for an AppConfig subclass that cannot be identified. It may be meant for
 * configuration on another server. This class throws an error if application configuration is
 * attempted.
 */
public class PlaceholderAppConfig extends prisms.arch.AppConfig
{
	private final String theAppConfigClassName;

	/**
	 * Creates a placeholder for an AppConfig subclass
	 * 
	 * @param className The name of the subclass that cannot be identified
	 */
	public PlaceholderAppConfig(String className)
	{
		theAppConfigClassName = className;
	}

	/**
	 * @return The name of the subclass that this placeholder stands for
	 */
	public String getAppConfigClassName()
	{
		return theAppConfigClassName;
	}

	@Override
	public void configureApp(prisms.arch.PrismsApplication app, org.dom4j.Element config)
	{
		throw new IllegalStateException("Unrecognized AppConfig subclass: " + theAppConfigClassName);
	}

	@Override
	public void configureClient(prisms.arch.ClientConfig client, org.dom4j.Element config)
	{
		throw new IllegalStateException("Unrecognized AppConfig subclass: " + theAppConfigClassName);
	}

	public boolean equals(Object o)
	{
		return o instanceof PlaceholderAppConfig
			&& ((PlaceholderAppConfig) o).getAppConfigClassName().equals(theAppConfigClassName);
	}
}
