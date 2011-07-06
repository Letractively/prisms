/*
 * CertificateSerializer.java Created Jun 27, 2011 by Andrew Butler, PSL
 */
package prisms.ui;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.util.PrismsUtils;

/**
 * Serializes an X.509 certificate chain into JSON to be interpreted on the client by a Certificate
 * Viewer
 */
public class CertificateSerializer
{
	private static final Logger log = Logger.getLogger(CertificateSerializer.class);

	private static final java.util.regex.Pattern PRINCIPAL_PATTERN = java.util.regex.Pattern
		.compile("[A-Z]*=([^,]*)");

	private static final char [] HEX_CHARS = new char [] {'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

	private static final int MAX_DETAIL_CHARS = 20;

	/**
	 * Serializes an X.509 certificate chain
	 * 
	 * @param certs The certificate chain (beginning with the terminus)
	 * @param url The URL that the certificates were provided by. Impacts the status--may be null.
	 * @return A JSON object that represents the terminal certificate (the rest of the chain is
	 *         recursively included in the parent attribute)
	 */
	public static JSONObject serialize(java.security.cert.X509Certificate[] certs, String url)
	{
		JSONObject ret = serialize(certs[0], url);
		JSONObject child = ret;
		for(int c = 1; c < certs.length; c++)
		{
			JSONObject parent = serialize(certs[c], null);
			child.put("parent", parent);
			child = parent;
		}
		return ret;
	}

	static JSONObject serialize(java.security.cert.X509Certificate cert, String url)
	{
		JSONObject ret = new JSONObject();
		String subject = getCertSubjectName(cert, false);
		ret.put("subject", subject != null ? PrismsUtils.encodeUnicode(subject)
			: "Could not parse certificate subject");
		String issuer = getCertSubjectName(cert, true);
		if(issuer == null)
			ret.put("issuer", "Could not parse certificate issuer");
		else if(issuer.equals(subject))
			ret.put("issuer", "Self-signed");
		else
			ret.put("issuer", PrismsUtils.encodeUnicode(issuer));
		ret.put("validFrom", print(cert.getNotBefore().getTime()));
		ret.put("validTo", print(cert.getNotAfter().getTime()));
		String status = getCertStatus(cert, url);
		ret.put("status", status);
		if(!status.equals("OK"))
			ret.put("iconError", Boolean.TRUE);

		org.json.simple.JSONArray details = new org.json.simple.JSONArray();
		ret.put("details", details);
		details.add(detail("Version", "V" + cert.getVersion(), null));
		java.math.BigInteger sni = cert.getSerialNumber();
		java.math.BigInteger sixteen = new java.math.BigInteger("16");
		StringBuilder sb = new StringBuilder();
		while(sni.compareTo(java.math.BigInteger.ZERO) > 0)
		{
			sb.insert(0, HEX_CHARS[sni.mod(sixteen).intValue()]);
			sni = sni.divide(sixteen);
			sb.insert(0, HEX_CHARS[sni.mod(sixteen).intValue()]);
			sni = sni.divide(sixteen);
			if(sni.compareTo(java.math.BigInteger.ZERO) > 0)
				sb.insert(0, ' ');
		}
		details.add(detail("Serial number", abbrev(sb.toString()), sb.toString()));
		sb.setLength(0);
		details.add(detail("Signature algorithm", PrismsUtils.encodeUnicode(cert.getSigAlgName()),
			null));
		details.add(detail("Subject", abbrev(getCertSubjectFull(cert, false, false)),
			getCertSubjectFull(cert, false, true)));
		String alts = getCertAlternates(cert, false, false);
		if(alts != null)
			details.add(detail("Subject alternates", abbrev(alts),
				getCertAlternates(cert, false, true)));
		details.add(detail("Issuer", abbrev(getCertSubjectFull(cert, true, false)),
			getCertSubjectFull(cert, true, true)));
		details.add(detail("Valid from", print(cert.getNotBefore().getTime()), null));
		details.add(detail("Valid to", print(cert.getNotAfter().getTime()), null));

		byte [] pk = cert.getPublicKey().getEncoded();
		for(int b = 0; b < pk.length; b++)
		{
			sb.append(HEX_CHARS[(pk[b] >>> 4) & 0xf]);
			sb.append(HEX_CHARS[pk[b] & 0xf]);
			sb.append(' ');
		}
		details.add(detail("Public key", abbrev(sb.toString()), sb.toString()));
		return ret;
	}

	static String print(long time)
	{
		return PrismsUtils.TimePrecision.DAYS.print(time, false);
	}

	/**
	 * Gets the simple subject or issuer name of a certificate
	 * 
	 * @param cert The certificate to get the name from
	 * @param issuer Whether to get the issuer name or the subject name
	 * @return The simple subject name or issuer name for the certificate
	 */
	public static String getCertSubjectName(java.security.cert.X509Certificate cert, boolean issuer)
	{
		javax.security.auth.x500.X500Principal principal;
		if(issuer)
			principal = cert.getIssuerX500Principal();
		else
			principal = cert.getSubjectX500Principal();
		String subjectName = null;
		if(principal.getName().length() > 0)
			subjectName = principal.getName();
		else
			try
			{
				java.util.Collection<java.util.List<?>> alts;
				if(issuer)
					alts = cert.getIssuerAlternativeNames();
				else
					alts = cert.getSubjectAlternativeNames();
				for(java.util.List<?> L : alts)
					for(Object o : L)
					{
						if(!(o instanceof String))
							continue;
						subjectName = (String) o;
						break;
					}
			} catch(java.security.cert.CertificateParsingException e)
			{
				log.error("Could not parse alternative names", e);
			}
		if(subjectName == null)
			return null;
		java.util.regex.Matcher match = PRINCIPAL_PATTERN.matcher(subjectName);
		if(match.find() && match.start() == 0)
			subjectName = match.group(1);
		return PrismsUtils.encodeUnicode(subjectName);
	}

	/**
	 * Gets the full, comma-separated subject or issuer name for a certificate
	 * 
	 * @param cert The certificate to get the name from
	 * @param issuer Whether to get the issuer name or subject name
	 * @param withKeys Whether to include the keys (CN=, O=, etc.) in the name
	 * @return The full subject or issuer name
	 */
	public static String getCertSubjectFull(java.security.cert.X509Certificate cert,
		boolean issuer, boolean withKeys)
	{
		javax.security.auth.x500.X500Principal principal;
		if(issuer)
			principal = cert.getIssuerX500Principal();
		else
			principal = cert.getSubjectX500Principal();
		String subjectName = null;
		if(principal.getName().length() > 0)
			subjectName = principal.getName();
		else
			try
			{
				java.util.Collection<java.util.List<?>> alts;
				if(issuer)
					alts = cert.getIssuerAlternativeNames();
				else
					alts = cert.getSubjectAlternativeNames();
				for(java.util.List<?> L : alts)
					for(Object o : L)
					{
						if(!(o instanceof String))
							continue;
						subjectName = (String) o;
						break;
					}
			} catch(java.security.cert.CertificateParsingException e)
			{
				log.error("Could not parse alternative names", e);
			}
		if(subjectName == null)
			return null;
		java.util.regex.Matcher match = PRINCIPAL_PATTERN.matcher(subjectName);
		if(!match.find())
			return PrismsUtils.encodeUnicode(subjectName);
		StringBuilder ret = new StringBuilder();
		if(withKeys)
			ret.append(match.group());
		else
			ret.append(match.group(1));
		while(match.find())
		{
			if(withKeys)
			{
				ret.append('\n');
				ret.append(match.group());
			}
			else
			{
				ret.append(", ");
				ret.append(match.group(1));
			}
		}
		return PrismsUtils.encodeUnicode(ret.toString());
	}

	/**
	 * Gets a list of alternate names from a certificate
	 * 
	 * @param cert The certificate to get the list of names from
	 * @param issuer Whether to get the issuer name or subject name
	 * @param withKeys Whether to include the keys in the name
	 * @return The alternate issuer- or subject-names from the certificate
	 */
	public static String getCertAlternates(java.security.cert.X509Certificate cert, boolean issuer,
		boolean withKeys)
	{
		java.util.Collection<java.util.List<?>> alts;
		try
		{
			if(issuer)
				alts = cert.getIssuerAlternativeNames();
			else
				alts = cert.getSubjectAlternativeNames();
		} catch(java.security.cert.CertificateParsingException e)
		{
			log.error("Could not parse alternative names", e);
			return null;
		}
		if(alts == null)
			return null;
		StringBuilder ret = new StringBuilder();
		for(java.util.List<?> L : alts)
			for(Object o : L)
			{
				if(!(o instanceof String))
					continue;
				String value = (String) o;
				if(ret.length() > 0)
				{
					if(withKeys)
						ret.append('\n');
					else
						ret.append(", ");
				}
				java.util.regex.Matcher match = PRINCIPAL_PATTERN.matcher(value);
				if(!match.find())
				{
					ret.append(value);
					continue;
				}
				if(withKeys)
				{
					ret.append(match.group());
					while(match.find())
					{
						ret.append(", ");
						ret.append(match.group());
					}
				}
				else
					ret.append(match.group(1));
			}
		return PrismsUtils.encodeUnicode(ret.toString());
	}

	/**
	 * Gets the status of a certificate
	 * 
	 * @param cert The certificate to get the status for
	 * @param url The URL that the certificate is for. May be null.
	 * @return The status of the certificate: "OK" unless something is wrong with the certificate as
	 *         applied to the given URL.
	 */
	public static String getCertStatus(java.security.cert.X509Certificate cert, String url)
	{
		long now = System.currentTimeMillis();
		if(now < cert.getNotBefore().getTime())
			return "This certificate is not valid until " + print(cert.getNotBefore().getTime());
		if(now > cert.getNotAfter().getTime())
			return "This certificate expired on " + print(cert.getNotAfter().getTime());
		if(url == null)
			return "OK";

		java.net.URL netUrl;
		try
		{
			netUrl = new java.net.URL(url);
		} catch(java.net.MalformedURLException e)
		{
			log.error("Could not parse url: " + url, e);
			return "Could not parse URL";
		}
		String host = netUrl.getHost();

		String subjectName;
		if(cert.getSubjectX500Principal().getName().length() > 0)
		{
			subjectName = cert.getSubjectX500Principal().getName();
			java.util.regex.Matcher match = PRINCIPAL_PATTERN.matcher(subjectName);
			if(match.find() && match.start() == 0)
				subjectName = match.group(1);
			if(subjectName.equals(host))
				return "OK";
		}
		java.util.Collection<java.util.List<?>> alts;
		try
		{
			alts = cert.getSubjectAlternativeNames();
		} catch(java.security.cert.CertificateParsingException e)
		{
			log.error("Could not parse alternative names", e);
			alts = null;
		}
		if(alts != null)
			for(java.util.List<?> L : alts)
				for(Object o : L)
				{
					if(!(o instanceof String))
						continue;
					subjectName = (String) o;
					java.util.regex.Matcher match = PRINCIPAL_PATTERN.matcher(subjectName);
					if(match.find() && match.start() == 0)
						subjectName = match.group(1);
					if(subjectName.equals(host))
						return "OK";
				}
		return "This certificate is not valid for its host: " + host;
	}

	static String abbrev(String value)
	{
		if(value.length() < MAX_DETAIL_CHARS)
			return value;
		return value.substring(0, MAX_DETAIL_CHARS - 2) + PrismsUtils.encodeUnicode("\u2026");
	}

	static JSONObject detail(String name, String value, String descrip)
	{
		JSONObject ret = new JSONObject();
		ret.put("name", name);
		ret.put("value", value);
		if(descrip != null)
			ret.put("descrip", descrip);
		else
			ret.put("descrip", value);
		return ret;
	}
}
