/*
 * CertificateAuthenticator.java Created Nov 2, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import org.apache.log4j.Logger;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsConfig;
import prisms.arch.PrismsServer.PrismsRequest;
import prisms.arch.ds.UserSource;

/**
 * Authenticates behind an architecture that queries the client for certificates and installs them
 * in the request
 */
public class CertificateAuthenticator extends AutoCreateAuthenticator
{
	private static final Logger log = Logger.getLogger(CertificateAuthenticator.class);

	private java.util.regex.Pattern theCertPattern;

	private int theMatchGroup;

	@Override
	public void configure(PrismsConfig config, UserSource userSource, PrismsApplication [] apps)
	{
		super.configure(config, userSource, apps);
		try
		{
			theCertPattern = java.util.regex.Pattern.compile(config.get("pattern"));
		} catch(java.util.regex.PatternSyntaxException e)
		{
			throw new IllegalArgumentException(config.get("pattern") + " is not valid", e);
		}
		theMatchGroup = config.getInt("match-group", 1);
	}

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
		return getUserName(request) != null;
	}

	@Override
	public String getUserName(PrismsRequest request)
	{
		java.security.cert.X509Certificate cert = getCertificate(request.httpRequest);
		if(cert == null)
			return null;

		java.util.regex.Matcher match;
		String subject = prisms.ui.CertificateSerializer.getCertSubjectName(cert, false);
		match = theCertPattern.matcher(subject);
		if(match.matches())
		{
			if(theMatchGroup <= match.groupCount())
				return match.group(theMatchGroup);
			else
				return match.group();
		}
		try
		{
			java.util.Collection<java.util.List<?>> alts = cert.getSubjectAlternativeNames();
			if(alts != null)
				for(java.util.List<?> L : alts)
					for(Object obj : L)
					{
						if(obj instanceof String)
						{
							match = theCertPattern.matcher((String) obj);
							if(match.matches())
							{
								if(theMatchGroup <= match.groupCount())
									return match.group(theMatchGroup);
								else
									return match.group();
							}
						}
					}
		} catch(java.security.cert.CertificateParsingException e)
		{
			log.error("Could not parse alternative names of certificate", e);
		}
		return null;
	}
}
