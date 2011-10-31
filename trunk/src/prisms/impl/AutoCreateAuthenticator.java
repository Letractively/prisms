/*
 * AutoCreateAuthenticator.java Created Nov 2, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.*;
import prisms.arch.PrismsServer.PrismsRequest;
import prisms.arch.ds.User;
import prisms.arch.ds.UserSource;

/** An authenticator that auto-creates users when they are authenticated externally */
public abstract class AutoCreateAuthenticator implements PrismsAuthenticator
{
	private static final Logger userLog = Logger.getLogger("prisms.users");

	/**
	 * A {@link prisms.arch.PrismsAuthenticator.SessionAuthenticator} for auto-create authenticators
	 */
	protected static class AutoCreateSessionAuthenticator implements SessionAuthenticator
	{
		public RequestAuthenticator getRequestAuthenticator(PrismsRequest request)
			throws PrismsException
		{
			return new AutoCreateRequestAuthenticator(request);
		}

		public JSONObject requestLogin(PrismsRequest request)
		{
			return null;
		}

		public boolean needsPasswordChange() throws PrismsException
		{
			return false;
		}

		public JSONObject requestPasswordChange() throws PrismsException
		{
			throw new IllegalStateException(
				"No custom password change available for this Authenticator");
		}

		public AuthenticationError changePassword(JSONObject event)
		{
			throw new IllegalStateException(
				"No custom password change available for this Authenticator");
		}

		public long recheck()
		{
			return -1;
		}

		public void destroy()
		{
		}
	}

	/**
	 * A {@link prisms.arch.PrismsAuthenticator.RequestAuthenticator} for auto-create authentication
	 */
	protected static class AutoCreateRequestAuthenticator implements RequestAuthenticator
	{
		private PrismsRequest theRequest;

		/** @param req The request to provide authentication data for */
		protected AutoCreateRequestAuthenticator(PrismsRequest req)
		{
			theRequest = req;
		}

		public boolean isError()
		{
			return false;
		}

		public String getError()
		{
			return null;
		}

		public boolean shouldReattempt()
		{
			return false;
		}

		public String getData()
		{
			return theRequest.getParameter("data");
		}

		public String encrypt(javax.servlet.http.HttpServletResponse resp, String data)
		{
			return data;
		}
	}

	private prisms.arch.PrismsApplication[] theApps;

	private UserSource theUserSource;

	private String theTemplateUserName;

	private User theTemplate;

	public void configure(PrismsConfig config, UserSource userSource, PrismsApplication [] apps)
	{
		theApps = apps;
		theUserSource = userSource;
		theTemplateUserName = config.get("user-template");
		if(theTemplateUserName != null)
			try
			{
				theTemplate = theUserSource.getUser(theTemplateUserName);
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not get template user", e);
			}
	}

	/** @return The user source that this authenticator uses */
	public UserSource getUserSource()
	{
		return theUserSource;
	}

	/** @return The applications that may be accessed through this authenticator */
	public PrismsApplication [] getApps()
	{
		return theApps;
	}

	/**
	 * @param request The request through which PRISMS is being accessed
	 * @return The name of the user that is trying to access PRISMS through the given request
	 */
	public abstract String getUserName(PrismsRequest request);

	public User getUser(PrismsRequest request) throws PrismsException
	{
		String userName = getUserName(request);
		User ret = theUserSource.getUser(userName);
		if(ret != null)
			return ret;
		synchronized(this)
		{
			ret = theUserSource.getUser(userName);
			if(ret != null)
				return ret;
			if(!(theUserSource instanceof prisms.arch.ds.ManageableUserSource))
				throw new PrismsException("User source is not manageable--cannot create user for "
					+ userName);
			if(theTemplate == null)
			{
				if(theTemplateUserName == null)
					throw new PrismsException("No user template set--cannot set properties of user");
				else
				{
					try
					{
						theTemplate = theUserSource.getUser(theTemplateUserName);
					} catch(PrismsException e)
					{
						throw new IllegalStateException("Could not get template user", e);
					}
					if(theTemplate == null)
						throw new PrismsException("User template " + theTemplateUserName
							+ " for authenticator " + getClass().getName()
							+ " does not exist. New user " + userName + " can not"
							+ " be authenticated.");
				}
			}
			if(!theUserSource.canAccess(theTemplate, request.getApp()))
				throw new PrismsException("User " + userName
					+ " does not have access to application " + request.getApp());

			String className = getClass().getName();
			if(className.indexOf('.') >= 0)
				className = className.substring(className.lastIndexOf('.') + 1);
			prisms.arch.ds.ManageableUserSource mus = (prisms.arch.ds.ManageableUserSource) theUserSource;
			for(User u : mus.getAllUsers())
				if(u.getName().equals(userName))
				{
					userLog.info(className + ": Auto-re-creating user \"" + userName
						+ "\" based on template \"" + theTemplate.getName() + "\"");
					ret = u;
					break;
				}
			if(ret == null)
			{
				userLog.info(className + ": Auto-creating user \"" + userName
					+ "\" based on template \"" + theTemplate.getName() + "\"");
				ret = mus.createUser(userName,
					new prisms.records.RecordsTransaction(mus.getSystemUser()));
				ret.setName(userName);
			}
			else
				ret.setDeleted(false);
			manager.app.ManagerUtils.copy(theTemplate, ret, mus, theApps);
			mus.putUser(ret, new prisms.records.RecordsTransaction(mus.getSystemUser()));
			return ret;
		}
	}

	public SessionAuthenticator createSessionAuthenticator(PrismsRequest request, User user)
		throws PrismsException
	{
		return new AutoCreateSessionAuthenticator();
	}
}
