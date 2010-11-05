/*
 * IISAuthenticator.java Created Nov 2, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import javax.servlet.http.HttpServletRequest;

/** Authenticates behind an IIS architecture that installs certificates in the request */
public class IISAuthenticator extends AutoCreateAuthenticator
{
	java.security.cert.X509Certificate getCertificate(HttpServletRequest request)
	{
		java.security.cert.X509Certificate[] certs = (java.security.cert.X509Certificate[]) request
			.getAttribute("javax.servlet.request.X509Certificate");
		if(certs == null || certs.length == 0)
			return null;
		return certs[0];
	}

	public boolean recognized(HttpServletRequest request)
	{
		return getCertificate(request) != null;
	}

	@Override
	public String getUserName(HttpServletRequest request)
	{
		return getCertificate(request).getSubjectX500Principal().getName();
	}
}
