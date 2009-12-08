/**
 * PersisterUtils.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import org.apache.log4j.Logger;
import org.dom4j.Element;

import prisms.arch.Persister;
import prisms.arch.PrismsServer;
import prisms.arch.event.PropertyDataSource;

/**
 * A utility class to help with persistence
 */
public class DefaultPersisterFactory implements prisms.arch.PersisterFactory
{
	private static final Logger log = Logger.getLogger(DefaultPersisterFactory.class);

	private java.util.HashMap<String, java.sql.Connection> theNamedConnections;

	private java.util.HashMap<String, Element> theNamedConnEls;

	/**
	 * Creates this persister factory
	 */
	public DefaultPersisterFactory()
	{
		theNamedConnections = new java.util.HashMap<String, java.sql.Connection>();
		theNamedConnEls = new java.util.HashMap<String, Element>();
	}

	public void configure(PrismsServer server, Element configEl)
	{
		for(Element connEl : (java.util.List<Element>) configEl.elements("connection"))
		{
			String name = connEl.attributeValue("name");
			if(name == null)
				name = connEl.elementTextTrim("name");
			if(name == null)
				throw new IllegalArgumentException("No name for connection: " + connEl.asXML());
			theNamedConnections.put(name, getConnection(connEl, server.getUserSource()));
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

	public java.sql.Connection getConnection(Element el, prisms.arch.ds.UserSource userSource)
	{
		String name = el.attributeValue("name");
		if(name == null)
			name = el.elementTextTrim("name");
		if(name != null)
		{
			java.sql.Connection ret = theNamedConnections.get(name);
			if(ret != null)
				return ret;
		}
		if(el == null)
		{
			log.error("No PRISMS configuration element was available!");
			throw new IllegalStateException("No PRISMS configuration element was available!");
		}

		if("true".equalsIgnoreCase(el.elementText("usePrismsConnection")))
		{
			if(userSource instanceof prisms.impl.DBUserSource)
			{
				((prisms.impl.DBUserSource) userSource).checkConnection();
				return ((prisms.impl.DBUserSource) userSource).getConnection();
			}
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
					return java.sql.DriverManager.getConnection(url);
				else
					return java.sql.DriverManager.getConnection(url, user, pwd);
			} catch(Throwable e)
			{
				log.error("Could not instantiate SQL Connection: ", e);
				el.remove(el.element("url"));
			}
		}
		throw new IllegalStateException("No suitable connection!");
	}

	public String getTablePrefix(java.sql.Connection conn, org.dom4j.Element connEl,
		prisms.arch.ds.UserSource userSource)
	{
		if("true".equalsIgnoreCase(connEl.elementText("usePrismsConnection")))
		{
			if(userSource instanceof prisms.impl.DBUserSource)
			{
				prisms.impl.DBUserSource dbus = (prisms.impl.DBUserSource) userSource;
				return getTablePrefix(dbus.getConnection(), dbus.getConnectionConfig(), null);
			}
		}
		return "";
	}

	public void disconnect(java.sql.Connection conn, org.dom4j.Element connEl)
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
		String name = connEl.attributeValue("name");
		if(name == null)
			name = connEl.elementTextTrim("name");
		if(name != null)
		{
			java.sql.Connection ret = theNamedConnections.get(name);
			if(ret != null)
			{
				/* This connection is managed by the persister factory--we'll close it when
				 * we're good and ready (see the destroy method)*/
				return;
			}
		}

		if("true".equalsIgnoreCase(connEl.elementText("SCS")))
		{
			log.info("no disconnect for a [dcgs] database...");
			return;
		}

		boolean disconnect = connEl.elementTextTrim("url") != null;
		if(!disconnect)
			return;

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
			disconnect(theNamedConnections.get(connName), theNamedConnEls.get(connName));
		theNamedConnections.clear();
		theNamedConnEls.clear();
	}
}
