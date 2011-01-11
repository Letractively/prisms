/*
 * AutoCreateAuthenticator.java Created Nov 2, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.json.simple.JSONObject;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsAuthenticator;
import prisms.arch.PrismsException;
import prisms.arch.ds.User;
import prisms.arch.ds.UserSource;

/** An authenticator that auto-creates users when they are authenticated externally */
public abstract class AutoCreateAuthenticator implements PrismsAuthenticator
{
	private static final Logger log = Logger.getLogger(AutoCreateAuthenticator.class);

	private prisms.arch.PrismsApplication[] theApps;

	private UserSource theUserSource;

	private String theTemplateUserName;

	private User theTemplate;

	public void configure(Element configEl, UserSource userSource, PrismsApplication [] apps)
	{
		theApps = apps;
		theUserSource = userSource;
		theTemplateUserName = configEl.elementTextTrim("user-template");
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
	public abstract String getUserName(HttpServletRequest request);

	public User getUser(HttpServletRequest request) throws PrismsException
	{
		String userName = getUserName(request);
		User ret = theUserSource.getUser(userName);
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
					log.error("User template " + theTemplateUserName + " for authenticator "
						+ getClass().getName() + " does not exist. New user " + userName
						+ " can not" + " be authenticated.");
			}
		}
		prisms.arch.ds.ManageableUserSource mus = (prisms.arch.ds.ManageableUserSource) theUserSource;
		ret = mus.createUser(userName, new prisms.records2.RecordsTransaction(mus.getSystemUser()));
		ret.setName(userName);
		for(prisms.arch.PrismsApplication app : theApps)
			if(mus.canAccess(theTemplate, app))
				mus.setUserAccess(ret, app, true,
					new prisms.records2.RecordsTransaction(mus.getSystemUser()));
		for(prisms.arch.ds.UserGroup group : theTemplate.getGroups())
			ret.addTo(group);
		mus.putUser(ret, new prisms.records2.RecordsTransaction(mus.getSystemUser()));
		return ret;
	}

	public Object createAuthenticationInfo(HttpServletRequest request, User user)
		throws PrismsException
	{
		return null;
	}

	public Object getAuthenticatedData(HttpServletRequest request, Object authInfo, boolean secure)
		throws PrismsException
	{
		return request.getParameter("data");
	}

	public JSONObject requestLogin(HttpServletRequest request, Object authInfo)
	{
		throw new IllegalStateException("No custom login available for this Authenticator");
	}

	public String encrypt(HttpServletRequest request, String data, Object authInfo)
	{
		return data;
	}

	public boolean needsPasswordChange(HttpServletRequest request, Object authInfo)
		throws PrismsException
	{
		return false;
	}

	public JSONObject requestPasswordChange(HttpServletRequest request, Object authInfo)
		throws PrismsException
	{
		throw new IllegalStateException(
			"No custom password change available for this Authenticator");
	}

	public AuthenticationError changePassword(HttpServletRequest request, Object authInfo,
		JSONObject event)
	{
		throw new IllegalStateException(
			"No custom password change available for this Authenticator");
	}

	public void destroy(Object authInfo)
	{
	}
}
