/*
 * ServiceUserSource.java Created Dec 3, 2009 by Andrew Butler, PSL
 */
package prisms.arch.service;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.*;
import prisms.arch.ds.*;

/** A user source that connects to the PRISMS web service for its data */
public class ServiceUserSource implements UserSource
{
	private String thePluginName;

	private prisms.util.PrismsServiceConnector theConnector;

	public void configure(org.dom4j.Element configEl, PrismsEnv env, PrismsApplication [] apps)
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
	}

	public IDGenerator getIDs()
	{
		// TODO Figure out how to do this through a service
		return null;
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
			res = theConnector.getResult(thePluginName, "canAccess", "userName",
				serverUser.getName(), "appName", app.getName());
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
		}
		return "true".equalsIgnoreCase((String) res.get("canAccess"));
	}

	public void assertAccessible(User user, ClientConfig config) throws PrismsException
	{
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
			res = theConnector.getResult(thePluginName, "getPasswordExpiration", "userName",
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

	public void setPassword(User user, long [] hash, boolean admin) throws PrismsException
	{
		JSONArray jsonHash = new JSONArray();
		for(int h = 0; h < hash.length; h++)
			jsonHash.add(new Long(hash[h]));
		try
		{
			theConnector.callProcedure(thePluginName, "setPassword", true, "userName",
				user.getName(), "hash", jsonHash, "admin", new Boolean(admin));
		} catch(java.io.IOException e)
		{
			throw new PrismsException("Could not communicate with PRISMS server", e);
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
