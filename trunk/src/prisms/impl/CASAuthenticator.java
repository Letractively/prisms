/*
 * CASAuthenticator.java Created Nov 2, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import javax.servlet.http.HttpServletRequest;

import org.dom4j.Element;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.UserSource;

/**
 * Authenticates from an external CAS server. Note that this authenticator does not make any CAS
 * calls, but merely uses the information that has already been ascertained from configured server
 * listeners from the web.xml.
 */
public class CASAuthenticator extends AutoCreateAuthenticator
{
	public void configure(Element configEl, UserSource userSource, PrismsApplication [] apps)
	{
		super.configure(configEl, userSource, apps);
	}

	org.jasig.cas.client.validation.Assertion getAssertion(HttpServletRequest request)
	{
		return (org.jasig.cas.client.validation.Assertion) request.getSession().getAttribute(
			org.jasig.cas.client.util.AbstractCasFilter.CONST_CAS_ASSERTION);
	}

	public boolean recognized(HttpServletRequest request)
	{
		return getAssertion(request) != null && request.getRemoteUser() != null;
	}

	public String getUserName(HttpServletRequest request)
	{
		return request.getRemoteUser();
	}
}
