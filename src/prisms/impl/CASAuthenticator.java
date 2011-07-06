/*
 * CASAuthenticator.java Created Nov 2, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import prisms.arch.PrismsServer.PrismsRequest;

/**
 * Authenticates from an external CAS server. Note that this authenticator does not make any CAS
 * calls, but merely uses the information that has already been ascertained from configured server
 * listeners from the web.xml.
 */
public class CASAuthenticator extends AutoCreateAuthenticator
{
	org.jasig.cas.client.validation.Assertion getAssertion(
		javax.servlet.http.HttpServletRequest request)
	{
		return (org.jasig.cas.client.validation.Assertion) request.getSession().getAttribute(
			org.jasig.cas.client.util.AbstractCasFilter.CONST_CAS_ASSERTION);
	}

	public boolean recognized(PrismsRequest request)
	{
		return getAssertion(request.httpRequest) != null
			&& request.httpRequest.getRemoteUser() != null;
	}

	@Override
	public String getUserName(PrismsRequest request)
	{
		return request.httpRequest.getRemoteUser();
	}
}
