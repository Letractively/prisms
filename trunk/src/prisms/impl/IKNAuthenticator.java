/*
 * IKNAuthenticator.java Created Nov 2, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import prisms.arch.PrismsServer.PrismsRequest;

/**
 * Authenticates within the IKN architecture. Within IKN, no request may be received by the servlet
 * until it is pre-authenticated through AKO. The user name that the user is authenticated as is
 * passed to the servlet in a server variable (java request header). I am not 100% sure that this
 * cannot be spoofed from a very smart client in an environment where IKN does not exist, so this
 * authenticator should only be configured where IKN is used.
 */
public class IKNAuthenticator extends AutoCreateAuthenticator
{
	private java.util.ArrayList<String> theUserAttrs;

	@Override
	public void configure(prisms.arch.PrismsConfig config, prisms.arch.ds.UserSource userSource,
		prisms.arch.PrismsApplication[] apps)
	{
		super.configure(config, userSource, apps);
		theUserAttrs = new java.util.ArrayList<String>();
		for(String user : config.getAll("user-attr"))
			theUserAttrs.add(user);
	}

	public boolean recognized(PrismsRequest request)
	{
		for(String ua : theUserAttrs)
			if(request.httpRequest.getHeader(ua) != null)
				return true;
		return false;
	}

	@Override
	public String getUserName(PrismsRequest request)
	{
		String userName = null;
		for(String ua : theUserAttrs)
			if(request.httpRequest.getHeader(ua) != null)
			{
				userName = request.httpRequest.getHeader(ua);
				break;
			}
		if(userName == null)
			throw new IllegalStateException("No user attribute--request not recognized");
		return userName;
	}
}
