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
	private String thePluginName;

	private prisms.util.PrismsServiceConnector theConnector;

	private java.util.HashMap<String, DBApplication> theApps;

	private java.util.HashMap<String, DBClientConfig> theClients;

	private java.util.HashSet<String> theAppsConfigured;

	private java.util.concurrent.locks.ReentrantReadWriteLock theLock;

	public void configure(org.dom4j.Element configEl, PersisterFactory factory)
		throws PrismsException
	{
		String url = configEl.elementTextTrim("serviceURL");
		if(url == null)
			throw new PrismsException("No serviceURL!");
		String appName = configEl.elementTextTrim("appName");
		if(appName == null)
			throw new PrismsException("No appName!");
		String serviceName = configEl.elementTextTrim("serviceName");
		if(serviceName == null)
			throw new PrismsException("No serviceName!");
		thePluginName = configEl.elementTextTrim("pluginName");
		if(thePluginName == null)
			throw new PrismsException("No pluginName!");
		String userName = configEl.elementTextTrim("user");
		theConnector = new prisms.util.PrismsServiceConnector(url, appName, serviceName, userName);
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

	public PasswordConstraints getPasswordConstraints() throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult(thePluginName, "getPasswordConstraints");
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		return PrismsSerializer.deserializeConstraints((JSONObject) res.get("constraints"));
	}

	public User getUser(String name) throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult(thePluginName, "getUser", "userName", name);
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return PrismsSerializer.deserializeUser((JSONObject) res.get("user"), this);
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize user from PRISMS service: "
				+ res.get("user"), e);
		}
	}

	public boolean canAccess(User serverUser, PrismsApplication app) throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult(thePluginName, "canAccess", "userName", serverUser
				.getName(), "appName", app.getName());
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		return "true".equalsIgnoreCase((String) res.get("canAccess"));
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
			res = theConnector.getResult(thePluginName, "getAllUsers");
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
			res = theConnector.getResult(thePluginName, "getPasswordExpiration", "userName", user
				.getName());
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

	public void setPassword(User user, long [] hash, boolean admin) throws PrismsException
	{
		JSONArray jsonHash = new JSONArray();
		for(int h = 0; h < hash.length; h++)
			jsonHash.add(new Long(hash[h]));
		try
		{
			theConnector.callProcedure(thePluginName, "setPassword", true, "userName", user
				.getName(), "hash", jsonHash, "admin", new Boolean(admin));
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

	private void configure(PrismsApplication app) throws PrismsException
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
		ret.setSessionTimeout(((Number) clientConfig.get("sessionTimeout")).longValue());
		org.dom4j.Element configEl = getConfigXML(configXML);
		dbApp.getConfig().configureClient(ret, configEl);
		lock = theLock.writeLock();
		lock.lock();
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

	public PrismsSession createSession(ClientConfig client, User user) throws PrismsException
	{
		if(!(client instanceof DBClientConfig))
			throw new IllegalArgumentException("Client " + client.getName()
				+ " not created with this user source");
		DBClientConfig dbCC = (DBClientConfig) client;
		if(!(client.getApp() instanceof DBApplication))
			throw new IllegalArgumentException("Application " + client.getApp().getName()
				+ " not creates with this user source");
		DBApplication dbApp = (DBApplication) client.getApp();

		if(!canAccess(user, client.getApp()))
			throw new PrismsException("User " + user + " is not permitted to access application "
				+ client.getApp().getName());
		if(!client.getApp().isConfigured())
			configure(client.getApp());
		PrismsSession ret = new PrismsSession(client.getApp(), client, user);
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
			res = theConnector.getResult(thePluginName, "getGroup", "appName", app.getName(),
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

	public Hashing getHashing() throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult(thePluginName, "getHashing");
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			return Hashing.fromJson((JSONObject) res.get("hashing"));
		} catch(RuntimeException e)
		{
			throw new PrismsException("Could not deserialize hashing from PRISMS service: "
				+ res.get("user"), e);
		}
	}

	public long [] getKey(User user, Hashing hashing) throws PrismsException
	{
		JSONObject res;
		try
		{
			res = theConnector.getResult(thePluginName, "getKey", "userName", user.getName(),
				"hashing", hashing.toJson());
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		try
		{
			JSONArray jsonKey = (JSONArray) res.get("key");
			long [] ret = new long [jsonKey.size()];
			for(int i = 0; i < ret.length; i++)
				ret[i] = ((Number) jsonKey.get(i)).longValue();
			return ret;
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
