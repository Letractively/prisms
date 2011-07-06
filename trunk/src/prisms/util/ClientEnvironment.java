/*
 * Browser.java Created May 10, 2011 by Andrew Butler, PSL
 */
package prisms.util;

/** Represents a client that has sent a request to a server */
public class ClientEnvironment
{
	/** The name of the Google Chrome browser */
	public static final String CHROME = "Chrome";

	/** The name of the Mozilla Firefox browser */
	public static final String FIREFOX = "Firefox";

	/** The name of the Internet Explorer browser */
	public static final String IE = "Internet Explorer";

	/** The name of the Apple Safari browser */
	public static final String SAFARI = "Safari";

	/** The name of the Opera browser */
	public static final String OPERA = "Opera";

	/** The name of the browser or HTTP client that sent the request. May be "Unknown". */
	public final String browserName;

	/** The version of the browser or client--may be null if unknown */
	public final String browserVersion;

	/** The name of the operating system on the client. May be "Unknown". */
	public final String osName;

	/** The version of the operating system on the client--may be null if unknown */
	public final String osVersion;

	/**
	 * Creates a client environment
	 * 
	 * @param browser The name of the browser or HTTP client being used
	 * @param version The version of the browser or HTTP client being used
	 * @param os The name of the operating system on the client
	 * @param osV The version of the operating system on the client
	 */
	public ClientEnvironment(String browser, String version, String os, String osV)
	{
		browserName = browser;
		browserVersion = version;
		osName = os;
		osVersion = osV;
	}

	/**
	 * Parses a client environment from the User-Agent header from an HTTP request
	 * 
	 * @param userAgent The User-Agent header from an HTTP request
	 * @return The client environment represented by that header
	 */
	public static ClientEnvironment getClientEnv(String userAgent)
	{
		if(userAgent == null)
			return new ClientEnvironment("Unknown", null, "Unknown", null);
		String [] bv = getBrowser(userAgent);
		String [] os = getOS(userAgent);
		return new ClientEnvironment(bv[0], bv[1], os[0], os[1]);
	}

	/**
	 * Parses a browser name/version from the user-agent string
	 * 
	 * @param userAgent The User-Agent string from an HTTP request
	 * @return The browser name/version represented in the user-agent header
	 */
	public static String [] getBrowser(String userAgent)
	{
		String [] ret;
		String browser;
		String version;
		int idx;
		ret = getSimpleBrowser(userAgent, "Chrome", "Firefox");
		if(ret != null)
			return ret;
		ret = getVersionBrowser(userAgent, "Safari", "Version");
		if(ret != null)
			return ret;
		ret = getVersionBrowser(userAgent, "Opera", "Version");
		if(ret != null)
			return ret;
		idx = userAgent.indexOf("MSIE");
		if(idx >= 0)
		{
			browser = IE;
			String compat = getVersion(userAgent, idx + "MSIE".length());
			idx = userAgent.indexOf("Trident");
			if(idx >= 0)
			{
				idx += "Trident".length();
				String tridentV = getVersion(userAgent, idx);
				version = (firstNum(tridentV) + 4) + ".0";
				if(!compat.equals(version))
					version += " (compat mode " + firstNum(compat) + ")";
			}
			else
				version = compat;
			return new String [] {browser, version};
		}
		ret = getSimpleBrowser(userAgent, "PRISMS-Service", "-PRISMS WS Connector", "1X", "amaya",
			"AmigaVoyager", "Arachne", "APT-HTTP", "-apt-get", "Arora", "Amiga-AWeb", "bluefish",
			"BrowseX", "Camino", "Check&Get", "Chimera", "Contiki", "curl", "-cURL", "Democracy",
			"Dillo", "Doczilla", "edbrowse", "Emacs-W3", "Epiphany", "Shredder", "Fennec",
			"Superswan", "Firebird", "Phoenix", "Flock", "Galeon", "Iceweasel", "Iceape", "IceCat",
			"Avant", "-GreenBrowser", "HTTPClient", "HttpClient", "IBrowse", "iCab", "ipd", "ICE",
			"Kazehakase", "K-Meleon", "Links", "Lobo", "Lynx", "Midori", "Mosaic", "SeaMonkey",
			"SlimBrowser", "muCommander", "NetPositive", "Netscape", "NetSurf", "Netsurf",
			"OmniWeb", "Acorn", "Oregano", "HP Web PrintSmart", "prism", "Prism", "Bison",
			"-Proxomitron", "retawq", "SIS", "-Spectrum Internet Suite", "Spicebird", "Songbird",
			"Strata", "Sylera", "W3CLineMode", "WebCapture", "WebTV", "w3m", "Wget", "newmath",
			"-Maxima");
		if(ret != null)
			return ret;
		ret = getVersionBrowser(userAgent, "Mozilla", "Sun");
		if(ret != null)
		{
			ret[0] = "HotJava";
			return ret;
		}
		ret = getVersionBrowser(userAgent, "Konqueror", "KHTML");
		if(ret != null)
			return ret;
		ret = getVersionBrowser(userAgent, "pango", "Gecko");
		if(ret != null)
		{
			ret[0] = "One Laptop per Child";
			return ret;
		}
		ret = getSimpleBrowser(userAgent, "Mozilla");
		if(ret != null)
			return ret;
		int i;
		for(i = 0; i < userAgent.length(); i++)
		{
			char c = userAgent.charAt(i);
			if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-')
				continue;
			else
				break;
		}
		if(i == userAgent.length())
			return new String [] {"Unknown", null};
		else
			return getSimpleBrowser(userAgent, userAgent.substring(0, i));
	}

	private static String [] getSimpleBrowser(String userAgent, String... browsers)
	{
		for(int b = 0; b < browsers.length; b++)
		{
			if(browsers[b].charAt(0) == '-')
				continue;
			int idx = userAgent.indexOf(browsers[b]);
			if(idx >= 0)
			{
				String browser = browsers[b];
				if(b < browsers.length - 1 && browsers[b + 1].charAt(0) == '-')
					browser = browsers[b + 1].substring(1);
				return new String [] {browser, getVersion(userAgent, idx + browsers[b].length())};
			}
		}
		return null;
	}

	private static String [] getVersionBrowser(String userAgent, String browser, String vString)
	{
		int idx = userAgent.indexOf(browser);
		if(idx >= 0)
		{
			String version;
			int vIdx = userAgent.indexOf(vString);
			if(vIdx >= 0)
				version = getVersion(userAgent, vIdx + vString.length());
			else
				version = getVersion(userAgent, idx + browser.length());
			return new String [] {browser, version};
		}
		return null;
	}

	/**
	 * Parses an OS name/version from the user-agent string
	 * 
	 * @param userAgent The User-Agent string from an HTTP request
	 * @return The OS name/version represented in the user-agent header
	 */
	public static String [] getOS(String userAgent)
	{
		String [] ret;
		ret = getSimpleBrowser(userAgent, "Windows NT", "WinNT", "WIN_NT");
		if(ret != null)
		{
			ret[0] = "Windows";
			if(ret[1] != null && ret[1].charAt(0) == '3' || ret[1].charAt(0) == '4')
				ret[1] = "NT " + ret[1];
			else if("5.0".equals(ret[1]))
				ret[1] = "2000";
			else if("5.1".equals(ret[1]))
				ret[1] = "XP";
			else if("5.2".equals(ret[1]))
			{
				if(userAgent.contains("64"))
					ret[1] = "XP";
				else
					ret[1] = "Server 2003";
			}
			else if("6.0".equals(ret[1]))
				ret[1] = "Vista";
			else if("6.1".equals(ret[1]))
				ret[1] = "7";
			else
				ret[1] = null;

			if(userAgent.contains("CYGWIN"))
				ret[0] = "Cygwin on " + ret[0];
			return ret;
		}
		else if(userAgent.contains("Windows 95") || userAgent.contains("Win95"))
			return new String [] {"Windows", "95"};
		else if(userAgent.contains("Windows 98") || userAgent.contains("Win98"))
			return new String [] {"Windows", "98"};
		else if(userAgent.contains("Win 9x 4.90"))
			return new String [] {"Windows", "ME"};
		else if(userAgent.contains("WindowsCE"))
			return new String [] {"Windows", "CE"};
		else if(userAgent.contains("Win3.11"))
			return new String [] {"Windows", "3.11"};

		ret = getSimpleBrowser(userAgent, "Linux", "ubuntu", "-Ubuntu", "Ubuntu", "Windows");
		if(ret != null)
			return ret;
		ret = getVersionBrowser(userAgent, "Android", "rv");
		if(ret != null)
			return ret;
		if(userAgent.contains("Mac OS X"))
			return new String [] {"Mac OS", "X"};
		else if(userAgent.contains("PPC") || userAgent.contains("PowerPC"))
			return new String [] {"Mac", "PowerPC"};
		else if(userAgent.contains("68K"))
			return new String [] {"Mac", "68K"};
		else if(userAgent.contains("Macintosh"))
			return new String [] {"Mac OS", null};
		else if(userAgent.contains("Commodore 64"))
			return new String [] {"Commodore 64", null};
		return new String [] {"Unknown", null};
	}

	private static String getVersion(String userAgent, int start)
	{
		int i;
		boolean hadWS = false;
		boolean endWS = false;
		for(i = start; i < userAgent.length()
			&& (userAgent.charAt(i) < '0' || userAgent.charAt(i) > '9'); i++)
		{
			if(Character.isWhitespace(userAgent.charAt(i)) || userAgent.charAt(i) == ';')
			{
				if(endWS)
					return null;
				if(!hadWS)
					hadWS = true;
			}
			else if(hadWS)
				endWS = true;
		}
		StringBuilder v = new StringBuilder();
		for(; i < userAgent.length() && !Character.isWhitespace(userAgent.charAt(i))
			&& userAgent.charAt(i) != ';'; i++)
			v.append(userAgent.charAt(i));
		if(v.length() > 0 && (v.charAt(v.length() - 1) == ')' || v.charAt(v.length() - 1) == ';'))
			v.setLength(v.length() - 1);
		if(v.length() == 0)
			return null;
		return v.toString();
	}

	private static int firstNum(String v)
	{
		int ret = 0;
		for(int i = 0; i < v.length() && v.charAt(i) >= '0' && v.charAt(i) <= '9'; i++)
			ret = ret * 10 + v.charAt(i) - '0';
		return ret;
	}
}
