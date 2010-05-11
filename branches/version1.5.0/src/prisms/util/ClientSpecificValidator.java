/*
 * ClientSpecificValidator.java Created Jul 14, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import prisms.arch.ClientConfig;
import prisms.arch.PrismsApplication;

/**
 * A validator implementation that simply checks the session's client against a list of allowed or a
 * list of forbidden clients. This allows a particular user to be allowed access to only a specific
 * client or set of clients within an application.
 */
public abstract class ClientSpecificValidator implements prisms.arch.Validator
{
	private final String [] theAllowedClients;

	private final String [] theForbiddenClients;

	/**
	 * Creates a ClientSpecificValidator
	 * 
	 * @param allowedClients The list of clients that a user is allowed to access--overrides
	 *        forbiddenClients
	 * @param forbiddenClients The list of clients that a user is forbidden to access--used only if
	 *        allowedClients is null
	 */
	protected ClientSpecificValidator(String [] allowedClients, String [] forbiddenClients)
	{
		theAllowedClients = allowedClients;
		theForbiddenClients = forbiddenClients;
	}

	/**
	 * @see prisms.arch.Validator#getValidationInfo(prisms.arch.ds.User, PrismsApplication,
	 *      ClientConfig, javax.servlet.http.HttpServletRequest)
	 */
	public org.json.simple.JSONObject getValidationInfo(prisms.arch.ds.User user,
		PrismsApplication app, ClientConfig client, javax.servlet.http.HttpServletRequest req)
	{
		return null;
	}

	/**
	 * @see prisms.arch.Validator#validate(prisms.arch.ds.User, PrismsApplication, ClientConfig,
	 *      javax.servlet.http.HttpServletRequest, org.json.simple.JSONObject)
	 */
	public boolean validate(prisms.arch.ds.User user, PrismsApplication app, ClientConfig client,
		javax.servlet.http.HttpServletRequest request, org.json.simple.JSONObject data)
		throws java.io.IOException
	{
		if(theAllowedClients == null)
		{
			if(ArrayUtils.contains(theForbiddenClients, client.getName()))
				throw new IllegalArgumentException("User " + user
					+ " is forbidden to access client " + client.getName() + " of application "
					+ app.getName());
		}
		else
		{
			if(!ArrayUtils.contains(theAllowedClients, client.getName()))
				throw new IllegalArgumentException("User " + user
					+ " is not allowed to access client " + client.getName() + " of application "
					+ app.getName());
		}
		return true;
	}
}
