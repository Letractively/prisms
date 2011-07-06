/*
 * CertificateAuthenticator.java Created Nov 2, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import org.apache.log4j.Logger;

import prisms.arch.PrismsServer.PrismsRequest;

/**
 * Authenticates behind an architecture that queries the client for certificates and installs them
 * in the request
 */
public class CertificateAuthenticator extends AutoCreateAuthenticator
{
	private static final Logger log = Logger.getLogger(CertificateAuthenticator.class);

	private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern
		.compile("(.*)@([a-zA-Z\\.]*)");

	java.security.cert.X509Certificate getCertificate(javax.servlet.http.HttpServletRequest request)
	{
		java.security.cert.X509Certificate[] certs = (java.security.cert.X509Certificate[]) request
			.getAttribute("javax.servlet.request.X509Certificate");
		if(certs == null || certs.length == 0)
			return null;
		return certs[0];
	}

	public boolean recognized(PrismsRequest request)
	{
		return getCertificate(request.httpRequest) != null;
	}

	@Override
	public String getUserName(PrismsRequest request)
	{
		java.security.cert.X509Certificate cert = getCertificate(request.httpRequest);

		java.util.regex.Matcher match;
		String subject = prisms.ui.CertificateSerializer.getCertSubjectName(cert, false);
		match = EMAIL_PATTERN.matcher(subject);
		if(match.matches())
			return match.group(1);
		try
		{
			for(java.util.List<?> L : cert.getSubjectAlternativeNames())
				for(Object obj : L)
				{
					if(obj instanceof String)
					{
						match = EMAIL_PATTERN.matcher((String) obj);
						if(match.matches())
							return match.group(1);
					}
				}
		} catch(java.security.cert.CertificateParsingException e)
		{
			log.error("Could not parse alternative names of certificate", e);
		}
		return subject;
	}
}
