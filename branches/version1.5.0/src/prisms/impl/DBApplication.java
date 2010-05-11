/**
 * DBApplication.java Created Oct 2, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import prisms.arch.AppConfig;

/**
 * An internal extension of the application class
 */
public class DBApplication extends prisms.arch.PrismsApplication
{
	private String theDescription;

	private AppConfig theAppConfig;

	private String theConfigXML;

	/**
	 * @return A description of this application
	 */
	public String getDescription()
	{
		return theDescription;
	}

	/**
	 * @return The configuration class for this application
	 */
	public AppConfig getConfig()
	{
		return theAppConfig;
	}

	/**
	 * @return The configuration XML location for this application
	 */
	public String getConfigXML()
	{
		return theConfigXML;
	}

	/**
	 * @param descrip The description for this application
	 */
	public void setDescription(String descrip)
	{
		theDescription = descrip;
	}

	/**
	 * @param configClassName The name of the configuration class for this application
	 */
	public void setConfigClass(String configClassName)
	{
		if(configClassName == null)
		{
			theAppConfig = new AppConfig();
			return;
		}
		try
		{
			theAppConfig = Class.forName(configClassName).asSubclass(AppConfig.class).newInstance();
		} catch(Throwable e)
		{
			theAppConfig = new PlaceholderAppConfig(configClassName);
			return;
		}
	}

	/**
	 * @param configXML The configuration XML location for this application
	 */
	public void setConfigXML(String configXML)
	{
		theConfigXML = configXML;
	}

	/**
	 * @return A URL to this application's configuration XML resource
	 */
	public java.net.URL findConfigXML()
	{
		if(theConfigXML == null)
			return null;
		java.net.URL configURL;
		if(theConfigXML.startsWith("classpath://"))
		{
			configURL = prisms.arch.ds.UserSource.class.getResource(theConfigXML
				.substring("classpath:/".length()));
			if(configURL == null)
				throw new IllegalArgumentException("Classpath configuration URL " + theConfigXML
					+ " refers to a non-existent resource");
		}
		else
		{
			try
			{
				configURL = new java.net.URL(theConfigXML);
			} catch(java.net.MalformedURLException e)
			{
				throw new IllegalArgumentException("Configuration URL " + theConfigXML
					+ " is malformed", e);
			}
		}
		return configURL;
	}

	/**
	 * @return The root element of this application's XML resource
	 */
	public org.dom4j.Element parseConfigXML()
	{
		java.net.URL configURL = findConfigXML();
		if(configURL == null)
			return null;

		org.dom4j.Element configEl;
		try
		{
			configEl = new org.dom4j.io.SAXReader().read(configURL).getRootElement();
		} catch(Exception e)
		{
			throw new IllegalStateException("Could not read application config file "
				+ theConfigXML, e);
		}
		return configEl;
	}

	public String toString()
	{
		return getName();
	}
}
