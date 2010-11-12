/**
 * PersisterUtils.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import java.sql.Connection;

import org.apache.log4j.Logger;
import org.dom4j.Element;

import prisms.arch.Persister;
import prisms.arch.event.PropertyDataSource;

/** A utility class to help with persistence */
public class DefaultPersisterFactory implements prisms.arch.PersisterFactory
{
	private static final Logger log = Logger.getLogger(DefaultPersisterFactory.class);

	private java.util.HashMap<String, Connection> theNamedConnections;

	private java.util.HashMap<String, Element> theNamedConnEls;

	/** Creates this persister factory */
	public DefaultPersisterFactory()
	{
		theNamedConnections = new java.util.HashMap<String, Connection>();
		theNamedConnEls = new java.util.HashMap<String, Element>();
	}

	public void configure(Element configEl)
	{
		for(Element connEl : (java.util.List<Element>) configEl.elements("connection"))
		{
			org.dom4j.Attribute attr = connEl.attribute("name");
			String [] names = null;
			if(attr != null)
			{
				names = prisms.util.ArrayUtils.add(names, attr.getValue());
				connEl.remove(attr);
			}
			java.util.List<Element> nameEls = connEl.elements("name");
			for(Element nameEl : nameEls)
				names = prisms.util.ArrayUtils.add(names, nameEl.getTextTrim());

			if(names.length == 0)
				throw new IllegalArgumentException("No name for connection: " + connEl.asXML());
			for(String name : names)
				theNamedConnEls.put(name, connEl);
		}
	}

	public <T> Persister<T> create(Element persisterEl, prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property)
	{
		Persister<T> ret;
		try
		{
			ret = (Persister<T>) Class.forName(persisterEl.elementTextTrim("class")).newInstance();
		} catch(Throwable e)
		{
			log.error("Could not create persister for property " + property, e);
			return null;
		}
		ret.configure(persisterEl, app, property);
		return ret;
	}

	public PropertyDataSource parseDS(Element el)
	{
		PropertyDataSource ds;
		try
		{
			ds = (PropertyDataSource) Class.forName(el.elementTextTrim("class")).newInstance();
		} catch(Throwable e)
		{
			log.error("Could not instantiate data source " + el.elementTextTrim("class"), e);
			return null;
		}
		ds.configure(el);
		return ds;
	}

	public Connection getConnection(Element el)
	{
		if(el == null)
		{
			log.error("No PRISMS configuration element was available!");
			throw new IllegalStateException("No PRISMS configuration element was available!");
		}

		String ref = el.attributeValue("ref");
		if(ref != null)
		{
			Connection ret = theNamedConnections.get(ref);
			if(ret == null)
			{
				Element namedConnEl = getConnectionElement(ref);
				if(namedConnEl != null)
				{
					ret = getConnection(namedConnEl);
					if(ret != null)
						theNamedConnections.put(ref, ret);
				}
			}
			if(ret != null)
				return ret;
		}

		String url = el.elementText("url");
		if(url != null)
		{
			String driver = el.elementText("driver");
			try
			{
				if(driver != null)
					Class.forName(driver);
				String user = el.elementText("username");
				String pwd = el.elementText("password");
				if(user == null)
				{
					log.debug("Connecting to database at " + url);
					return java.sql.DriverManager.getConnection(url);
				}
				else
				{
					log.debug("Connecting to database at " + url + " as " + user);
					return java.sql.DriverManager.getConnection(url, user, pwd);
				}
			} catch(Throwable e)
			{
				throw new IllegalStateException("Could not instantiate SQL Connection: ", e);
			}
		}
		throw new IllegalStateException("No suitable connection!");
	}

	/**
	 * @param connName The name of the connection to get the configuration element for
	 * @return The configuration element for the named connection
	 */
	public Element getConnectionElement(String connName)
	{
		return theNamedConnEls.get(connName);
	}

	/**
	 * @param connEl The connection element given to a configured plugin or application--may just be
	 *        a reference to a stored connection element
	 * @return The connection element that actually configures the SQL connection
	 */
	public Element getReferredConnEl(Element connEl)
	{
		Element ret = connEl;
		do
		{
			connEl = ret;
			String ref = connEl.attributeValue("ref");
			if(ref != null)
				ret = getConnectionElement(ref);
			else
				ret = null;
		} while(ret != null);
		return connEl;
	}

	public String getTablePrefix(Connection conn, Element connEl)
	{
		String ref = connEl.attributeValue("ref");
		if(ref != null)
		{
			Element namedEl = getConnectionElement(ref);
			if(namedEl != null)
				return getTablePrefix(conn, namedEl);
		}
		String prefix = connEl.elementTextTrim("prefix");
		if(prefix != null)
			return prefix;
		return "";
	}

	public void disconnect(Connection conn, Element connEl)
	{
		if(connEl == null)
		{
			log.error("No PRISMS configuration element was available!");
			try
			{
				conn.close();
			} catch(java.sql.SQLException e)
			{
				log.error("Could not close connection", e);
			}
		}
		String ref = connEl.attributeValue("ref");
		if(ref != null)
		{
			Connection ret = theNamedConnections.get(ref);
			if(ret != null)
			{
				/* This connection is managed by the persister factory--we'll close it when
				 * we're good and ready (see the destroy method)*/
				return;
			}
		}

		boolean disconnect = connEl.elementTextTrim("url") != null;
		if(!disconnect)
			return;

		boolean closed;
		try
		{
			closed = conn.isClosed();
		} catch(java.sql.SQLException e)
		{
			log.error("Connection error", e);
			closed = true;
		}
		String name = connEl.attributeValue("name");
		if(name == null)
			name = connEl.elementTextTrim("name");
		if(closed)
		{
			log.error("Connection " + (name == null ? connEl.asXML() : name) + " is already closed");
		}
		if(conn.getClass().getName().contains("hsql") && connEl.element("noshutdown") == null)
		{
			// hsql connection--use shutdown command
			try
			{
				java.sql.Statement stmt = conn.createStatement();
				stmt.execute("SHUTDOWN");
			} catch(java.sql.SQLException e)
			{
				log.error("Could not execute HSQL shutdown statement", e);
			}
		}
		try
		{
			conn.close();
		} catch(java.sql.SQLException e)
		{
			throw new IllegalStateException("Connection error", e);
		}
	}

	public void destroy()
	{
		for(String connName : theNamedConnections.keySet())
		{
			Connection conn = theNamedConnections.get(connName);
			disconnect(conn, theNamedConnEls.get(connName));
		}
		theNamedConnections.clear();
		theNamedConnEls.clear();
	}
}
