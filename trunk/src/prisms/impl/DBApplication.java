/**
 * DBApplication.java Created Oct 2, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

/**
 * An internal extension of the application class
 */
public class DBApplication extends prisms.arch.PrismsApplication
{
	private String theDescription;

	private Class<? extends prisms.arch.AppConfig> theConfigClass;

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
	public Class<? extends prisms.arch.AppConfig> getConfigClass()
	{
		return theConfigClass;
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
	 * @param configClass The configuration class for this application
	 */
	public void setConfigClass(Class<? extends prisms.arch.AppConfig> configClass)
	{
		theConfigClass = configClass;
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
