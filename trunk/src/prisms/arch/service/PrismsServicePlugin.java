/*
 * PrismsServicePlugin.java Created Dec 3, 2009 by Andrew Butler, PSL
 */
package prisms.arch.service;

import org.json.simple.JSONObject;

import prisms.arch.PrismsException;

/**
 * Allows external applications to use the PRISMS architecture without supporting multiple PRISMS
 * data sources
 */
public class PrismsServicePlugin implements prisms.arch.AppPlugin
{
	private prisms.arch.PrismsSession theSession;

	private String theName;

	private prisms.arch.ds.UserSource theSource;

	public void initPlugin(prisms.arch.PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		theSource = theSession.getApp().getEnvironment().getUserSource();
	}

	public void initClient()
	{
	}

	public void processEvent(JSONObject evt)
	{
		JSONObject ret = new JSONObject();
		ret.put("plugin", theName);
		ret.put("method", "returnValue");
		String method = (String) evt.get("method");
		if(method == null)
			throw new IllegalArgumentException("No method on event");
		try
		{
			if(method.equals("getPasswordConstraints"))
			{
				prisms.arch.ds.PasswordConstraints constraints = theSource.getPasswordConstraints();
				ret.put("constraints", PrismsSerializer.serializeConstraints(constraints));
			}
			else if(method.equals("getUser"))
			{
				prisms.arch.ds.User user = theSource.getUser((String) evt.get("userName"));
				if(user == null)
					ret.put("user", null);
				ret.put("user", PrismsSerializer.serializeUser(user));
			}
			else if(method.equals("canAccess"))
			{
				prisms.arch.ds.User user = theSource.getUser((String) evt.get("userName"));
				if(user == null)
					ret.put("canAccess", Boolean.FALSE);
				prisms.arch.PrismsApplication app = null;
				for(prisms.arch.PrismsApplication ap : theSession
					.getProperty(prisms.arch.event.PrismsProperties.applications))
					if(ap.getName().equals(evt.get("appName")))
					{
						app = ap;
						break;
					}
				if(app == null)
					ret.put("canAccess", Boolean.FALSE);
				ret.put("canAccess", new Boolean(theSource.canAccess(user, app)));
			}
			else if(method.equals("getAllUsers"))
				ret.put("users", PrismsSerializer.serializeUsers(theSource.getActiveUsers()));
			else if(method.equals("lockUser"))
			{
				ret = null;
				prisms.arch.ds.User user = theSource.getUser((String) evt.get("userName"));
				if(user != null)
					theSource.lockUser(user);
			}
			else if(method.equals("getPasswordExpiration"))
			{
				prisms.arch.ds.User user = theSource.getUser((String) evt.get("userName"));
				if(user == null)
					throw new PrismsException("No such user: " + evt.get("userName"));
				ret.put("expiration", new Long(theSource.getPasswordExpiration(user)));
			}
			else if(method.equals("setPassword"))
			{
				ret = null;
				prisms.arch.ds.User user = theSource.getUser((String) evt.get("userName"));
				if(user == null)
					throw new PrismsException("No such user: " + evt.get("userName"));
				java.util.List<Long> hashList = (java.util.List<Long>) evt.get("hash");
				long [] hash = new long [hashList.size()];
				for(int h = 0; h < hash.length; h++)
					hash[h] = hashList.get(h).longValue();
				theSource.setPassword(user, hash, ((Boolean) evt.get("admin")).booleanValue());
			}
			else if(method.equals("getHashing"))
				ret.put("hashing", theSource.getHashing().toJson());
			else if(method.equals("getKey"))
			{
				prisms.arch.ds.User user = theSource.getUser((String) evt.get("userName"));
				if(user == null)
					throw new PrismsException("No such user: " + evt.get("userName"));
				long [] key = theSource.getKey(user,
					prisms.arch.ds.Hashing.fromJson((JSONObject) evt.get("hashing")));
				org.json.simple.JSONArray jsonKey = new org.json.simple.JSONArray();
				for(int k = 0; k < key.length; k++)
					jsonKey.add(new Long(key[k]));
				ret.put("key", jsonKey);
			}
			else
				throw new IllegalArgumentException("Unrecognized " + theName + " method " + method);
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not process " + theName + " event " + evt, e);
		}

		if(ret != null)
			theSession.postOutgoingEvent(ret);
	}
}
