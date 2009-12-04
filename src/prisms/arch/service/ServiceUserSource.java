/*
 * ServiceUserSource.java Created Dec 3, 2009 by Andrew Butler, PSL
 */
package prisms.arch.service;

import java.util.concurrent.locks.Lock;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.*;
import prisms.arch.ds.*;
import prisms.impl.DBApplication;
import prisms.impl.DBClientConfig;

/**
 * A user source that connects to the PRISMS web service for its data
 */
public class ServiceUserSource implements UserSource
{
	private static final String thePluginName = "PRISMS Service";

	private prisms.util.PrismsServiceConnector theConnector;

	private java.util.HashMap<String, DBApplication> theApps;

	private java.util.HashMap<String, DBClientConfig> theClients;

	private java.util.HashSet<String> theAppsConfigured;

	private java.util.concurrent.locks.ReentrantReadWriteLock theLock;

	public void configure(org.dom4j.Element configEl, PersisterFactory factory)
		throws PrismsException
	{
		theConnector = new prisms.util.PrismsServiceConnector(configEl
			.elementTextTrim("serviceURL"), configEl.elementTextTrim("appName"), configEl
			.elementTextTrim("serviceName"), configEl.elementTextTrim("user"));
		String pwd = configEl.elementTextTrim("password");
		if(pwd != null)
			theConnector.setPassword(pwd);
		try
		{
			theConnector.init();
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not initialize communication with PRISMS server", e);
		}
		theApps = new java.util.HashMap<String, DBApplication>();
		theClients = new java.util.HashMap<String, DBClientConfig>();
		theAppsConfigured = new java.util.HashSet<String>();
		theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
	}

	public User getUser(String name) throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult("PRISMS Service", "getUser", "userName", name);
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return PrismsSerializer.deserializeUser((JSONObject) res.get("user"), this, null);
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize user from PRISMS service: "
				+ res.get("user"), e);
		}
	}

	public User getUser(User serverUser, PrismsApplication app) throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult("PRISMS Service", "getUser", "userName", serverUser
				.getName(), "appName", app.getName());
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return PrismsSerializer.deserializeUser((JSONObject) res.get("user"), this, app);
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize user from PRISMS service: "
				+ res.get("user"), e);
		}
	}

	public void lockUser(User user) throws PrismsException
	{
		user.setLocked(true);
		try
		{
			theConnector.callProcedure(thePluginName, "lockUser", true, "user", user.getName());
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate locked user to PRISMS service", e);
		}
	}

	public User [] getAllUsers() throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult("PRISMS Service", "getAllUsers");
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return PrismsSerializer.deserializeUsers((JSONArray) res.get("users"), this);
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize users from PRISMS service: "
				+ res.get("users"), e);
		}
	}

	public long getPasswordExpiration(User user) throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult("PRISMS Service", "getPasswordExpiration", "userName",
				user.getName());
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return ((Number) res.get("expiration")).longValue();
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize expiration from PRISMS service: "
				+ res.get("user"), e);
		}
	}

	public void setPassword(User user, long [] hash) throws PrismsException
	{
		JSONArray jsonHash = new JSONArray();
		for(int h = 0; h < hash.length; h++)
			jsonHash.add(new Long(hash[h]));
		try
		{
			theConnector.callProcedure("PRISMS Service", "setPassword", true, "userName", user
				.getName(), "hash", jsonHash);
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
	}

	public PrismsApplication getApp(String name) throws PrismsException
	{
		DBApplication ret;
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			ret = theApps.get(name);
		} finally
		{
			lock.unlock();
		}
		if(ret != null)
			return ret;
		JSONObject res;
		try
		{
			res = theConnector.getResult(thePluginName, "getAppConfig", "appName", name);
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			JSONObject appConfig = (JSONObject) res.get("appConfig");
			String descrip = (String) appConfig.get("description");
			String configClassStr = (String) appConfig.get("configClass");
			String configXML = (String) appConfig.get("configXML");
			ret = new DBApplication();
			ret.setDataSource(this);
			ret.setName(name);
			ret.setDescription(descrip);
			ret.setConfigXML(configXML);
			ret.setConfigClass(configClassStr);
		} catch(Throwable e)
		{
			throw new PrismsException(
				"Could not deserialize application configuration from PRISMS service", e);
		}
		lock = theLock.writeLock();
		lock.lock();
		try
		{
			theApps.put(name, ret);
		} finally
		{
			lock.unlock();
		}
		return ret;
	}

	private void configure(PrismsApplication app)
	{
		if(!(app instanceof DBApplication))
			throw new IllegalArgumentException("Application object " + app
				+ " was not created by this user source");
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			if(theAppsConfigured.contains(app.getName()))
				return;
		} finally
		{
			lock.unlock();
		}
		DBApplication dbApp = (DBApplication) app;
		if(dbApp.getConfig() == null)
			throw new IllegalStateException("Application " + app
				+ " does not have a valid configuration class");
		if(dbApp.getConfigXML() == null)
			throw new IllegalStateException("Application " + app
				+ " does not have a valid configuration XML");
		org.dom4j.Element configEl = dbApp.parseConfigXML();
		if(theAppsConfigured.contains(app.getName()))
			return;
		try
		{
			dbApp.getConfig().configureApp(app, configEl);
		} catch(Throwable e)
		{
			throw new IllegalStateException("Could not configure application " + app.getName(), e);
		}
		lock = theLock.writeLock();
		lock.lock();
		try
		{
			theAppsConfigured.add(app.getName());
		} catch(Throwable e)
		{
			throw new IllegalStateException("Could not configure application " + app.getName(), e);
		} finally
		{
			lock.unlock();
		}
		app.setConfigured();
	}

	public ClientConfig getClient(PrismsApplication app, String name) throws PrismsException
	{
		DBApplication dbApp;
		if(app instanceof DBApplication)
			dbApp = (DBApplication) app;
		else
			throw new IllegalArgumentException("Application " + app.getName()
				+ " was not created by this user source");
		DBClientConfig ret;
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			ret = theClients.get(app.getName() + "/" + name);
		} finally
		{
			lock.unlock();
		}
		if(ret != null)
			return ret;

		JSONObject res;
		try
		{
			res = theConnector.getResult(thePluginName, "getClient", "appName", app.getName(),
				"clientName", name);
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		JSONObject clientConfig = (JSONObject) res.get("clientConfig");
		String descrip = (String) clientConfig.get("description");
		String serializerStr = (String) clientConfig.get("serializer");
		String configXML = (String) clientConfig.get("configXML");

		ret = new DBClientConfig(-1, app, name);
		ret.setDescription(descrip);
		ret.setSerializerClass(serializerStr);
		ret.setConfigXML(configXML);
		org.dom4j.Element configEl = getConfigXML(configXML);
		dbApp.getConfig().configureClient(ret, configEl);
		lock = theLock.writeLock();
		try
		{
			theClients.put(app.getName() + "/" + name, ret);
		} finally
		{
			lock.unlock();
		}
		return ret;
	}

	org.dom4j.Element getConfigXML(String configLocation)
	{
		java.net.URL configURL;
		if(configLocation.startsWith("classpath://"))
		{
			configURL = prisms.arch.ds.UserSource.class.getResource(configLocation
				.substring("classpath:/".length()));
			if(configURL == null)
				throw new IllegalArgumentException("Classpath configuration URL " + configLocation
					+ " refers to a non-existent resource");
		}
		else
		{
			try
			{
				configURL = new java.net.URL(configLocation);
			} catch(java.net.MalformedURLException e)
			{
				throw new IllegalArgumentException("Configuration URL " + configLocation
					+ " is malformed", e);
			}
		}
		org.dom4j.Element configEl;
		try
		{
			configEl = new org.dom4j.io.SAXReader().read(configURL).getRootElement();
		} catch(Exception e)
		{
			throw new IllegalStateException("Could not read client config file " + configLocation,
				e);
		}
		return configEl;
	}

	public PrismsSession createSession(ClientConfig client, User user, boolean asService)
		throws PrismsException
	{
		if(!(client instanceof DBClientConfig))
			throw new IllegalArgumentException("Client " + client.getName()
				+ " not created with this user source");
		DBClientConfig dbCC = (DBClientConfig) client;
		if(!(client.getApp() instanceof DBApplication))
			throw new IllegalArgumentException("Application " + client.getApp().getName()
				+ " not creates with this user source");
		DBApplication dbApp = (DBApplication) client.getApp();

		if(user.getApp() == null || !user.getApp().getName().equals(client.getApp().getName()))
		{
			User appUser = getUser(user, client.getApp());
			if(appUser == null)
				throw new PrismsException("User " + user
					+ " is not permitted to access application " + client.getApp().getName());
			user = appUser;
		}
		if(!client.getApp().isConfigured())
			configure(client.getApp());
		PrismsSession ret = new PrismsSession(client.getApp(), client, user, asService);
		try
		{
			client.getApp().configureSession(ret);
			client.configure(ret);
		} catch(Throwable e)
		{
			throw new PrismsException("Could not configure client " + client.getName()
				+ " of application " + client.getApp().getName() + " for user " + user, e);
		}

		String config = dbCC.getConfigXML();
		org.dom4j.Element configEl = getConfigXML(config);
		try
		{
			dbApp.getConfig().configureSession(ret, configEl);
		} catch(RuntimeException e)
		{
			throw e;
		} catch(Throwable e)
		{
			throw new IllegalStateException("Could not configure session of application "
				+ client.getApp().getName(), e);
		}
		return ret;
	}

	public UserGroup getGroup(PrismsApplication app, String groupName) throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult("PRISMS Service", "getGroup", "appName", app.getName(),
				"groupName", groupName);
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return PrismsSerializer.deserializeGroup((JSONObject) res.get("group"), this, app);
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize user from PRISMS service: "
				+ res.get("user"), e);
		}
	}

	public UserGroup [] getGroups(PrismsApplication app) throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult("PRISMS Service", "getGroups");
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return PrismsSerializer.deserializeGroups((JSONArray) res.get("groups"), this, app);
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize users from PRISMS service: "
				+ res.get("users"), e);
		}
	}

	public Hashing getHashing() throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult("PRISMS Service", "getHashing");
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return Hashing.fromJson((JSONObject) res.get("key"));
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize hashing from PRISMS service: "
				+ res.get("user"), e);
		}
	}

	public String getKey(User user, Hashing hashing) throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult("PRISMS Service", "getKey", "userName", user.getName(),
				"hashing", hashing.toJson());
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return (String) res.get("key");
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize key from PRISMS service: "
				+ res.get("user"), e);
		}
	}

	public void disconnect()
	{
		// Nothing to do here
	}
}
