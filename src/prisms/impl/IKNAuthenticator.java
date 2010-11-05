/*
 * IKNAuthenticator.java Created Nov 2, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import javax.servlet.http.HttpServletRequest;

import org.dom4j.Element;

import prisms.arch.ds.UserSource;

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

	public void configure(Element configEl, UserSource userSource,
		prisms.arch.PrismsApplication[] apps)
	{
		super.configure(configEl, userSource, apps);
		theUserAttrs = new java.util.ArrayList<String>();
		for(Element uaEl : (java.util.List<Element>) configEl.elements("user-attr"))
			theUserAttrs.add(uaEl.getTextTrim());
	}

	public boolean recognized(HttpServletRequest request)
	{
		for(String ua : theUserAttrs)
			if(request.getHeader(ua) != null)
				return true;
		return false;
	}

	public String getUserName(HttpServletRequest request)
	{
		String userName = null;
		for(String ua : theUserAttrs)
			if(request.getHeader(ua) != null)
			{
				userName = request.getHeader(ua);
				break;
			}
		if(userName == null)
			throw new IllegalStateException("No user attribute--request not recognized");
		return userName;
	}
}
