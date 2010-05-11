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
	private int theID;

	private String theDescription;

	private AppConfig theAppConfig;

	private String theConfigXML;

	private boolean isUserRestrictive;

	private boolean isDeleted;

	/**
	 * Creates a DBApplication
	 */
	public DBApplication()
	{
		theID = -1;
	}

	/**
	 * @return This application's database ID
	 */
	public int getID()
	{
		return theID;
	}

	/**
	 * @param id The database ID for this application
	 */
	public void setID(int id)
	{
		theID = id;
	}

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
	 * @throws prisms.arch.PrismsException If the XML file cannot be found
	 */
	public java.net.URL findConfigXML() throws prisms.arch.PrismsException
	{
		if(theConfigXML == null)
			return null;
		java.net.URL configURL;
		if(theConfigXML.startsWith("classpath://"))
		{
			configURL = prisms.arch.ds.UserSource.class.getResource(theConfigXML
				.substring("classpath:/".length()));
			if(configURL == null)
				throw new prisms.arch.PrismsException("Classpath configuration URL " + theConfigXML
					+ " refers to a non-existent resource");
		}
		else
		{
			try
			{
				configURL = new java.net.URL(theConfigXML);
			} catch(java.net.MalformedURLException e)
			{
				throw new prisms.arch.PrismsException("Configuration URL " + theConfigXML
					+ " is malformed", e);
			}
		}
		return configURL;
	}

	/**
	 * @return The root element of this application's XML resource
	 * @throws prisms.arch.PrismsException If the XML cannot be read and parsed
	 */
	public org.dom4j.Element parseConfigXML() throws prisms.arch.PrismsException
	{
		java.net.URL configURL = findConfigXML();

		org.dom4j.Element configEl;
		try
		{
			configEl = new org.dom4j.io.SAXReader().read(configURL).getRootElement();
		} catch(Exception e)
		{
			throw new prisms.arch.PrismsException("Could not read application config file "
				+ theConfigXML, e);
		}
		return configEl;
	}

	/**
	 * @return Whether this application restricts access on a user-by-user basis or allows users
	 *         openly
	 */
	public boolean isUserRestrictive()
	{
		return isUserRestrictive;
	}

	/**
	 * @param restrict Whether this application should restrict access on a user-by-user basis or
	 *        allows users openly
	 */
	public void setUserRestrictive(boolean restrict)
	{
		isUserRestrictive = restrict;
	}

	/**
	 * @return Whether this group is deleted
	 */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	public boolean equals(Object o)
	{
		return o instanceof DBApplication && ((DBApplication) o).theID == theID;
	}

	public int hashCode()
	{
		return theID;
	}

	public String toString()
	{
		return getName();
	}
}
