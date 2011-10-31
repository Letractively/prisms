/*
 * PrismsConfig.java Created Jun 15, 2011 by Andrew Butler, PSL
 */
package prisms.arch;

import org.apache.log4j.Logger;

/**
 * <p>
 * A PrismsConfig is a hierarchical configuration structure containing attributes that can be used
 * to setup and determine the behavior of various PRISMS functions.
 * </p>
 * <p>
 * For all of the get functions, the key may be used to navigate beyond the immediate children of
 * the config using a notation similar to XPath. '/' characters may be used to navigate beyond one
 * level deep.
 * </p>
 */
public abstract class PrismsConfig
{
	private static final Logger log = Logger.getLogger(PrismsConfig.class);

	private static class DefaultPrismsConfig extends PrismsConfig
	{
		private final String theName;

		private final String theValue;

		private final PrismsConfig [] theElements;

		DefaultPrismsConfig(String name, String value, PrismsConfig [] els)
		{
			theName = name;
			theValue = value;
			if(els == null)
				els = new PrismsConfig [0];
			theElements = els;
		}

		@Override
		public String getName()
		{
			return theName;
		}

		@Override
		public String getValue()
		{
			return theValue;
		}

		@Override
		public PrismsConfig [] subConfigs()
		{
			return theElements.clone();
		}
	}

	/** Effectively merges one or more configs to inherit attributes in succession */
	public static class MergedConfig extends PrismsConfig
	{
		private final PrismsConfig [] theMerged;

		/**
		 * Creates a merged config
		 * 
		 * @param configs The configurations to merge
		 */
		public MergedConfig(PrismsConfig... configs)
		{
			if(configs.length == 0)
				throw new IllegalArgumentException(
					"Merged configs must contain at least one config");
			theMerged = configs;
		}

		/** @return The configs that were merged to create this config */
		public PrismsConfig [] getMerged()
		{
			return theMerged.clone();
		}

		@Override
		public String getName()
		{
			return theMerged[0].getName();
		}

		@Override
		public String getValue()
		{
			return theMerged[0].getValue();
		}

		@Override
		public PrismsConfig [] subConfigs()
		{
			int size = 0;
			PrismsConfig [][] unmerged = new PrismsConfig [theMerged.length] [];
			for(int m = 0; m < theMerged.length; m++)
			{
				unmerged[m] = theMerged[m].subConfigs();
				size += unmerged[m].length;
			}
			PrismsConfig [] ret = new PrismsConfig [size];
			size = 0;
			for(int m = 0; m < unmerged.length; m++)
			{
				System.arraycopy(unmerged[m], 0, ret, size, unmerged[m].length);
				size += unmerged[m].length;
			}
			return ret;
		}

		@Override
		public PrismsConfig subConfig(String type, String... props)
		{
			PrismsConfig ret = null;
			for(PrismsConfig m : theMerged)
			{
				ret = m.subConfig(type, props);
				if(ret != null)
					break;
			}
			return ret;
		}

		@Override
		public PrismsConfig [] subConfigs(String type, String... props)
		{
			int size = 0;
			PrismsConfig [][] unmerged = new PrismsConfig [theMerged.length] [];
			for(int m = 0; m < theMerged.length; m++)
			{
				unmerged[m] = theMerged[m].subConfigs(type, props);
				size += unmerged[m].length;
			}
			PrismsConfig [] ret = new PrismsConfig [size];
			size = 0;
			for(int m = 0; m < unmerged.length; m++)
			{
				System.arraycopy(unmerged[m], 0, ret, size, unmerged[m].length);
				size += unmerged[m].length;
			}
			return ret;
		}

		@Override
		public String get(String key)
		{
			String ret = null;
			for(PrismsConfig m : theMerged)
			{
				ret = m.get(key);
				if(ret != null)
					break;
			}
			return ret;
		}

		@Override
		public String [] getAll(String key)
		{
			int size = 0;
			String [][] unmerged = new String [theMerged.length] [];
			for(int m = 0; m < theMerged.length; m++)
			{
				unmerged[m] = theMerged[m].getAll(key);
				size += unmerged[m].length;
			}
			String [] ret = new String [size];
			size = 0;
			for(int m = 0; m < unmerged.length; m++)
			{
				System.arraycopy(unmerged[m], 0, ret, size, unmerged[m].length);
				size += unmerged[m].length;
			}
			return ret;
		}
	}

	/** @return The name of this configuration */
	public abstract String getName();

	/** @return The base value of this configuration */
	public abstract String getValue();

	/** @return All of this configuration's sub-configurations */
	public abstract PrismsConfig [] subConfigs();

	/**
	 * @param type The type of the config to get
	 * @param props The properties that a config must match to be returned by this method
	 * @return The first configuration that would be returned from
	 *         {@link #subConfigs(String, String...)}, or null if no such sub-configuration exists
	 */
	public PrismsConfig subConfig(String type, String... props)
	{
		int index = type.indexOf('/');
		if(index >= 0)
		{
			PrismsConfig pathEl = subConfig(type.substring(0, index));
			type = type.substring(index + 1);
			if(pathEl == null)
				return null;
			return pathEl.subConfig(type);
		}

		for(PrismsConfig config : subConfigs())
			if(config.getName().equals(type))
			{
				boolean propMatch = true;
				for(int i = 0; i + 2 <= props.length; i += 2)
				{
					String value = config.get(props[i]);
					if(value == null ? props[i + 1] != null : !value.equals(props[i + 1]))
					{
						propMatch = false;
						break;
					}
				}
				if(propMatch)
					return config;
			}
		return null;
	}

	/**
	 * Gets a sub-configurations of a given type and potentially selected by a set of attributes
	 * 
	 * @param type The type of the configs to get
	 * @param props The properties (name, value, name, value...) that a config must match to be
	 *        returned by this method
	 * @return All configuration in this config's children (or descendants) that match the given
	 *         type and properties
	 */
	public PrismsConfig [] subConfigs(String type, String... props)
	{
		int index = type.indexOf('/');
		if(index >= 0)
		{
			PrismsConfig [] pathEls = subConfigs(type.substring(0, index));
			type = type.substring(index + 1);
			PrismsConfig [][] ret = new PrismsConfig [pathEls.length] [];
			for(int p = 0; p < pathEls.length; p++)
				ret[p] = pathEls[p].subConfigs(type, props);
			return prisms.util.ArrayUtils.mergeInclusive(PrismsConfig.class, ret);
		}

		java.util.ArrayList<PrismsConfig> ret = new java.util.ArrayList<PrismsConfig>();
		for(PrismsConfig config : subConfigs())
			if(config.getName().equals(type))
			{
				boolean propMatch = true;
				for(int i = 0; i + 2 <= props.length; i += 2)
				{
					String value = config.get(props[i]);
					if(value == null ? props[i + 1] != null : !value.equals(props[i + 1]))
					{
						propMatch = false;
						break;
					}
				}
				if(propMatch)
					ret.add(config);
			}
		return ret.toArray(new PrismsConfig [ret.size()]);
	}

	/**
	 * Gets all attribute values from this configuration (may have been represented by an actual XML
	 * attribute or an element in XML) that match a given name.
	 * 
	 * @param key The name of the attribute to get the values of
	 * @return The values of the given attribute in this config
	 */
	public String [] getAll(String key)
	{
		PrismsConfig [] configs = subConfigs(key);
		String [] ret = new String [configs.length];
		for(int i = 0; i < configs.length; i++)
			ret[i] = configs[i].getValue();
		return ret;
	}

	/**
	 * Gets a single attribute value from this config
	 * 
	 * @param key The name of the attribute to get the value of
	 * @return The first value that would be returned from {@link #getAll(String)}, or null if no
	 *         values would be returned
	 */
	public String get(String key)
	{
		PrismsConfig config = subConfig(key);
		return config == null ? null : config.getValue();
	}

	/**
	 * Parses an int from an attribute of this config
	 * 
	 * @param key The name of the attribute to get the value of
	 * @param def The value to return if the attribute is missing from the config
	 * @return The integer parsed from the given attribute of this config, or the given default
	 *         value if the attribute is missing
	 */
	public int getInt(String key, int def)
	{
		String ret = get(key);
		if(ret == null)
			return def;
		try
		{
			return Integer.parseInt(ret);
		} catch(NumberFormatException e)
		{
			throw new IllegalArgumentException("Value of property " + key + " (" + ret
				+ ") is not an integer", e);
		}
	}

	/**
	 * Parses a float from an attribute of this config
	 * 
	 * @param key The name of the attribute to get the value of
	 * @param def The value to return if the attribute is missing from the config
	 * @return The float parsed from the given attribute of this config, or the given default value
	 *         if the attribute is missing
	 */
	public float getFloat(String key, float def)
	{
		String ret = get(key);
		if(ret == null)
			return def;
		try
		{
			return Float.parseFloat(ret);
		} catch(NumberFormatException e)
		{
			throw new IllegalArgumentException("Value of property " + key + " (" + ret
				+ ") is not a float", e);
		}
	}

	/**
	 * Parses a boolean from an attribute of this config
	 * 
	 * @param key The name of the attribute to get the value of
	 * @param def The value to return if the given key is missing from this config
	 * @return The boolean parsed from the given attribute of this config, or <code>def</code> if
	 *         the attribute is missing
	 */
	public boolean is(String key, boolean def)
	{
		String ret = get(key);
		if(ret == null)
			return def;
		if("true".equalsIgnoreCase(ret))
			return true;
		else if("false".equalsIgnoreCase(ret))
			return false;
		else
			throw new IllegalArgumentException("Value of property " + key + " (" + ret
				+ ") is not a boolean");
	}

	/**
	 * Parses a time from an attribute of this config. This method uses
	 * {@link prisms.util.PrismsUtils#parseEnglishTime(String)} to parse the time.
	 * 
	 * @param key The name of the attribute to get the value of
	 * @return The time parsed from the given attribute of this config, or -1 if the attribute is
	 *         missing
	 */
	public long getTime(String key)
	{
		String ret = get(key);
		if(ret == null)
			return -1;
		return prisms.util.PrismsUtils.parseEnglishTime(ret);
	}

	/**
	 * Parses a time interval from an attribute of this config. This method uses
	 * {@link prisms.util.PrismsUtils#parseEnglishTime(String)} to parse the time.
	 * 
	 * @param key The name of the attribute to get the value of
	 * @param def The default value to return if the given key does not exist in this config
	 * @return The time interval parsed from the given attribute of this config, or <code>def</code>
	 *         if the attribute is missing
	 */
	public long getTime(String key, long def)
	{
		String ret = get(key);
		if(ret == null)
			return def;
		return prisms.util.PrismsUtils.parseEnglishTime(ret);
	}

	/**
	 * Parses a class from an attribute of this config.
	 * 
	 * @param <T> The type of the class to return
	 * @param key The name of the attribute to get the value of
	 * @param superClass The super-class to cast the value to
	 * @return The class parsed from the fully-qualified name in the value of the given attribute,
	 *         or null if the attribute is missing
	 * @throws ClassNotFoundException If the class named in the attribute cannot be found in the
	 *         classpath
	 * @throws ClassCastException If the class named in the attribute is not a subclass of the given
	 *         <code>superClass</code>
	 */
	public <T> Class<? extends T> getClass(String key, Class<T> superClass)
		throws ClassNotFoundException, ClassCastException
	{
		String className = get(key);
		if(className == null)
			return null;
		Class<?> ret = Class.forName(className);
		if(superClass == null)
			return (Class<? extends T>) ret;
		else
			return ret.asSubclass(superClass);
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		toString(ret, 0);
		return ret.toString();
	}

	/**
	 * Prints this config to a string builder
	 * 
	 * @param ret The string builder to write this config to
	 * @param indent The amount to indent the text
	 */
	public void toString(StringBuilder ret, int indent)
	{
		for(int i = 0; i < indent; i++)
			ret.append('\t');
		ret.append('<').append(getName());
		PrismsConfig [] subConfigs = subConfigs();
		String [] values = new String [subConfigs.length];
		int withChildren = 0;
		for(int c = 0; c < subConfigs.length; c++)
		{
			values[c] = subConfigs[c].getValue();
			if(subConfigs[c].subConfigs().length == 0
				&& (values[c] != null && values[c].length() > 0))
			{
				ret.append(' ').append(subConfigs[c].getName()).append('=').append('"');
				ret.append(values[c]).append('"');
				subConfigs[c] = null;
			}
			else
				withChildren++;
		}
		if(withChildren == 0 && getValue() == null)
		{
			ret.append(' ').append('/').append('>');
		}
		else
		{
			ret.append('>');
			if(getValue() != null)
				ret.append(getValue());
			if(withChildren > 0)
				ret.append('\n');
			for(int c = 0; c < subConfigs.length; c++)
			{
				if(subConfigs[c] == null)
					continue;
				subConfigs[c].toString(ret, indent + 1);
				ret.append('\n');
			}
			if(withChildren > 0)
				for(int i = 0; i < indent; i++)
					ret.append('\t');
			ret.append('<').append('/').append(getName()).append('>');
		}
	}

	/**
	 * Parses a PrismsConfig from a pre-parsed or dynamically-generated XML element
	 * 
	 * @param env The PRISMS environment to get environment variables from
	 * @param xml The XML element to parse the PrismsConfig from
	 * @return The parsed PrismsConfig
	 */
	public static PrismsConfig fromXml(PrismsEnv env, org.dom4j.Element xml)
	{
		PrismsConfig [] fx = fromXml(env, xml, null);
		if(fx.length == 0)
			return null;
		else if(fx.length == 1)
			return fx[0];
		else
			return create(env, "none", null, fx);
	}

	/**
	 * Parses a PrismsConfig from XML found at a location
	 * 
	 * @param env The PRISMS environment to get environment variables from
	 * @param location The location of the XML file
	 * @param relative The locations that the given location might be relative to (see
	 *        {@link #getRootElement(String, String...)}
	 * @return The PrismsConfig parsed from the XML at the given location
	 * @throws java.io.IOException If the XML cannot be found or parsed into a configuration
	 */
	public static PrismsConfig fromXml(PrismsEnv env, String location, String... relative)
		throws java.io.IOException
	{
		org.dom4j.Element root = getRootElement(location, relative);
		if(root == null)
		{
			StringBuilder msg = new StringBuilder();
			msg.append("Could not resolve location: ");
			msg.append(location);
			for(String rel : relative)
			{
				msg.append('/');
				msg.append(rel);
			}
			throw new java.io.IOException(msg.toString());
		}
		PrismsConfig [] fx = fromXml(env, root, location, relative);
		if(fx.length == 0)
			return null;
		else if(fx.length == 1)
			return fx[0];
		else
			return create(env, "none", null, fx);
	}

	/**
	 * Retrieves and parses XML for a given location
	 * 
	 * @param location The location of the XML file to parse
	 * @param relative The locations to which the location may be relative
	 * @return The root element of the given XMl file
	 * @throws java.io.IOException If an error occurs finding, reading, or parsing the file
	 */
	public static org.dom4j.Element getRootElement(String location, String... relative)
		throws java.io.IOException
	{
		String newLocation = resolve(location, relative);
		if(newLocation == null)
			return null;
		java.net.URL configURL;
		if(newLocation.startsWith("classpath:/"))
		{ // Classpath resource
			configURL = PrismsConfig.class
				.getResource(newLocation.substring("classpath:/".length()));
			if(configURL == null)
			{
				throw new java.io.FileNotFoundException("Classpath configuration URL "
					+ newLocation + " refers to a non-existent resource");
			}
		}
		else if(newLocation.contains(":/"))
			configURL = new java.net.URL(newLocation); // Absolute resource
		else
			throw new java.io.IOException("Location " + newLocation + " is invalid");

		org.dom4j.Element configEl;
		try
		{
			configEl = new org.dom4j.io.SAXReader().read(configURL).getRootElement();
		} catch(org.dom4j.DocumentException e)
		{
			throw new javax.imageio.IIOException("Could not read XML file " + location, e);
		}
		return configEl;
	}

	/**
	 * Gets the location of a class to use in resolving relative paths with
	 * {@link #getRootElement(String, String...)}
	 * 
	 * @param clazz The class to get the location of
	 * @return The location of the class file
	 */
	public static String getLocation(Class<?> clazz)
	{
		return "classpath://" + clazz.getName().replaceAll("\\.", "/") + ".class";
	}

	private static String resolve(String location, String... relative) throws java.io.IOException
	{
		if(location.contains(":/"))
			return location;
		else if(relative.length > 0)
		{
			String resolvedRel = resolve(relative[0], prisms.util.ArrayUtils.remove(relative, 0));
			int protocolIdx = resolvedRel.indexOf(":/");
			if(protocolIdx >= 0)
			{
				if(location.startsWith("/"))
					return resolvedRel.substring(0, protocolIdx) + ":/" + location;
				String newLocation = location;
				do
				{
					int lastSlash = resolvedRel.lastIndexOf("/");
					resolvedRel = resolvedRel.substring(0, lastSlash);
					if(newLocation.startsWith("../"))
						newLocation = newLocation.substring(3);
				} while(newLocation.startsWith("../"));
				if(!resolvedRel.contains(":/"))
				{
					throw new java.io.IOException("Location " + location + " relative to "
						+ prisms.util.ArrayUtils.toString(relative) + " is invalid");
				}
				return resolvedRel + "/" + newLocation;
			}
			else
				return null;
		}
		else
		{
			throw new java.io.IOException("Location " + location + " is invalid");
		}
	}

	private static final java.util.regex.Pattern ENV_VAR_NAME_PATTERN = java.util.regex.Pattern
		.compile("[a-zA-Z_\\-\\.]*");

	private static final java.util.regex.Pattern ENV_VAR_REF_PATTERN = java.util.regex.Pattern
		.compile("\\$\\{([a-zA-Z_\\-\\.]*)\\}");

	private static PrismsConfig [] fromXml(PrismsEnv env, org.dom4j.Element xml, String location,
		String... relative)
	{
		String ifExp = xml.attributeValue("if");
		if(ifExp != null && !evaluate(ifExp, env))
			return new PrismsConfig [0];

		if("variable".equals(xml.getName()))
		{
			if(env.isSealed())
			{
				log.error("Environment variable reference in a configured environment");
				return new PrismsConfig [0];
			}
			String name = xml.attributeValue("name");
			if(name == null)
			{
				log.error("No name attribute for variable setting: " + xml.asXML());
				return new PrismsConfig [0];
			}
			if(!ENV_VAR_NAME_PATTERN.matcher(name).matches())
			{
				log.error("Invalid config variable name: " + name);
				return new PrismsConfig [0];
			}
			String val = xml.attributeValue("value");
			if(val == null)
			{
				log.error("No value attribute for variable setting: " + xml.asXML());
				return new PrismsConfig [0];
			}
			if(val.equals("" + null))
				val = null;
			else
				val = replace(val, env);
			env.setVariable(name, val);
			return new PrismsConfig [0];
		}
		else if("switch".equals(xml.getName()))
		{
			if(xml.attributeValue("variable") == null)
				throw new IllegalStateException("No variable declaration for switch statement: "
					+ xml.asXML());
			String var = env.getVariable(xml.attributeValue("variable"));
			if(var == null)
				var = "null";
			for(org.dom4j.Element caseEl : (java.util.List<org.dom4j.Element>) xml.elements("case"))
			{
				String val = caseEl.attributeValue("value");
				if(val == null)
				{
					log.error("No value attribute in switch case: " + xml.asXML());
					continue;
				}
				boolean found = replace(val, env).equals(var);
				if(!found)
				{
					String [] vals = val.split(",");
					for(String v : vals)
						if(replace(v.trim(), env).equals(var))
						{
							found = true;
							break;
						}
				}
				if(!found)
					continue;
				String error = caseEl.attributeValue("error");
				if(error != null)
					throw new IllegalStateException(replace(error, env));
				String warn = caseEl.attributeValue("warning");
				if(warn != null)
				{
					log.warn(replace(warn, env));
					return new PrismsConfig [0];
				}
				java.util.ArrayList<PrismsConfig> ret = new java.util.ArrayList<PrismsConfig>();
				for(org.dom4j.Attribute toAdd : (java.util.List<org.dom4j.Attribute>) caseEl
					.attributes())
				{
					if(!"value".equals(toAdd.getName()) && !"warning".equals(toAdd.getName()))
						ret.add(create(env, toAdd.getName(), replace(toAdd.getValue(), env)));
				}
				for(org.dom4j.Element toAdd : (java.util.List<org.dom4j.Element>) caseEl.elements())
					for(PrismsConfig c : fromXml(env, toAdd, location, relative))
						ret.add(c);
				return ret.toArray(new PrismsConfig [ret.size()]);
			}

			// No case statement. Check for default.
			org.dom4j.Element defCase = xml.element("default");
			if(defCase != null)
			{
				String error = defCase.attributeValue("error");
				if(error != null)
					throw new IllegalStateException(replace(error, env));
				String warn = defCase.attributeValue("warning");
				if(warn != null)
				{
					log.warn(replace(warn, env));
					return new PrismsConfig [0];
				}
				java.util.ArrayList<PrismsConfig> ret = new java.util.ArrayList<PrismsConfig>();
				for(org.dom4j.Attribute toAdd : (java.util.List<org.dom4j.Attribute>) defCase
					.attributes())
				{
					if(!"value".equals(toAdd.getName()))
						ret.add(create(env, toAdd.getName(), replace(toAdd.getValue(), env)));
				}
				for(org.dom4j.Element toAdd : (java.util.List<org.dom4j.Element>) defCase
					.elements())
					for(PrismsConfig c : fromXml(env, toAdd, location, relative))
						ret.add(c);
				return ret.toArray(new PrismsConfig [ret.size()]);
			}
			else
			{
				log.warn("No case found for value " + var + " in switch on "
					+ xml.attributeValue("variable"));
				return new PrismsConfig [0];
			}
		}
		else if("import".equals(xml.getName()))
		{
			String loc = xml.getTextTrim();
			if(loc == null || loc.length() == 0)
			{
				log.error("No file location in content of import element: " + xml.asXML());
				return new PrismsConfig [0];
			}
			loc = replace(loc, env);
			String [] rel2 = relative;
			if(location != null)
				rel2 = prisms.util.ArrayUtils.add(relative, location);
			PrismsConfig referred;
			try
			{
				referred = fromXml(env, loc, rel2);
			} catch(java.io.IOException e)
			{
				String msg = "File referred to from import (" + loc + ") could not be found";
				if(!"false".equalsIgnoreCase(xml.attributeValue("required")))
					throw new IllegalStateException(msg, e);
				log.error(msg, e);
				return new PrismsConfig [0];
			}
			if(referred == null)
				return new PrismsConfig [0];
			else if("true".equalsIgnoreCase(xml.attributeValue("keep-root")))
				return new PrismsConfig [] {referred};
			else
				return referred.subConfigs();
		}
		else
		{
			String value = xml.getTextTrim();
			if(value.length() == 0)
				value = null;
			else
				value = replace(value, env);
			java.util.List<org.dom4j.Element> children = xml.elements();
			java.util.List<org.dom4j.Attribute> atts = xml.attributes();
			int attSize = atts.size();
			if(ifExp != null)
				attSize--;
			if(children.size() == 0 && attSize == 0)
				return new PrismsConfig [] {new DefaultPrismsConfig(xml.getName(), value, null)};
			java.util.ArrayList<PrismsConfig> childConfigs = new java.util.ArrayList<PrismsConfig>();
			if(attSize > 0)
				for(org.dom4j.Attribute att : atts)
					if(!att.getName().equals("if"))
						childConfigs.add(new DefaultPrismsConfig(att.getName(), replace(
							att.getValue(), env), null));
			for(org.dom4j.Element child : children)
				for(PrismsConfig toAdd : fromXml(env, child, location, relative))
					childConfigs.add(toAdd);
			return new PrismsConfig [] {create(env, xml.getName(), value,
				childConfigs.toArray(new PrismsConfig [childConfigs.size()]))};
		}
	}

	/**
	 * Replaces references to environment variables with their values as set in the given
	 * environment. A reference to an environment variable has the form "${variable.name}".
	 * 
	 * @param value The string to replace environment variables in
	 * @param env The PRISMS environment containing the environment variables to replace
	 * @return The replaced value
	 */
	public static String replace(String value, PrismsEnv env)
	{
		if(value == null || env == null)
			return value;
		value = value.trim();
		java.util.regex.Matcher matcher = ENV_VAR_REF_PATTERN.matcher(value);
		if(!matcher.find())
			return value;
		int lastEnd = 0;
		StringBuilder ret = new StringBuilder();
		do
		{
			if(lastEnd < matcher.start())
				ret.append(value.substring(lastEnd, matcher.start()));
			String varValue = env.getVariable(matcher.group(1));
			if(varValue != null)
				ret.append(varValue);
			lastEnd = matcher.end();
		} while(matcher.find());
		if(lastEnd < value.length())
			ret.append(value.substring(lastEnd, value.length()));
		return ret.toString();
	}

	private static final java.util.regex.Pattern VERSION_PATTERN = java.util.regex.Pattern
		.compile("[a-zA-Z]*[\\._]?(?:(\\d+)[\\._])*(\\d+)([a-zA-Z0-9]+)?");

	private enum Op
	{
		AND("&&"), OR("||"), EQUAL2("=="), EQUAL1("="), NOT_EQUAL("!="), LESS("<"), LESS_EQUAL("<="), GREATER(
			">"), GREATER_EQUAL(">=");

		final String name;

		Op(String nm)
		{
			name = nm;
		}

		static Op get(final String s, final int i)
		{
			for(Op op : values())
			{
				if(op.name.length() > s.length() - i)
					continue;
				int j;
				for(j = 0; j < op.name.length(); j++)
					if(op.name.charAt(j) != s.charAt(i + j))
						break;
				if(j == op.name.length())
					return op;
			}
			return null;
		}
	}

	/**
	 * Evaluates a condition expression
	 * 
	 * @param ifStr <p>
	 *        The expression to evaluate. This may be a binary expression with comparison operator
	 *        ("=" (or "=="), "!=", "&gt;", "&lt;", "&gt;=", "&lt;="), usually comparing an
	 *        environment variable to a constant value. If an equivalence operator is used, the
	 *        values on either side of the operator will be compared for equality. If one of the
	 *        "numerical" operators (&lt;, &gt;, etc.) is used, the values will be compared using
	 *        the {@link #compareVersions(String, String)} method.
	 *        </p>
	 *        <p>
	 *        The expression may also be only a reference to an environment variable. If this is the
	 *        case, this method will return true unless the variable's value is null or "false",
	 *        case-insensitive.
	 *        </p>
	 *        <p>
	 *        Multiple expressions may be combined using and (&&) and or (||) operations.
	 *        Parenthetical expressions are also supported, in addition to the unary not ("!")
	 *        operator.
	 *        </p>
	 * @param env The PRISMS environment to get environment variables from
	 * @return Whether the expression evaluates to true
	 */
	public static boolean evaluate(String ifStr, PrismsEnv env)
	{
		ifStr = ifStr.trim();

		Op op = null;
		String expr1 = null;
		String expr2 = null;
		if(ifStr.charAt(0) == '(')
		{
			int closeIdx = findClose(ifStr);
			if(closeIdx < 0)
			{
				log.error("No closing parenthesis: if statement cannot be evaluated: " + ifStr);
				return false;
			}
			expr1 = ifStr.substring(1, closeIdx);
			ifStr = ifStr.substring(closeIdx + 1).trim();
			if(ifStr.length() == 0)
				return evaluate(expr1, env);
			op = Op.get(ifStr, 0);
			if(op == null)
			{
				log.error("Parenthetical expressions must be alone or joined by an operator:"
					+ " if statement cannot be evaluated: (" + expr1 + ")" + ifStr);
				return false;
			}
			expr2 = ifStr.substring(op.name.length());
		}
		else if(ifStr.charAt(0) == '!')
			return !evaluate(ifStr.substring(1), env);
		else
			for(int idx = 0; idx < ifStr.length(); idx++)
			{
				op = Op.get(ifStr, idx);
				if(op == null)
					continue;
				expr1 = ifStr.substring(0, idx);
				expr2 = ifStr.substring(idx + op.name.length());
				break;
			}
		if(op != null)
		{
			switch(op)
			{
			case AND:
			case OR:
				break;
			default:
				expr1 = replace(expr1, env);
				expr2 = replace(expr2, env);
			}
			switch(op)
			{
			case AND:
				return evaluate(expr1, env) && evaluate(expr2, env);
			case OR:
				return evaluate(expr1, env) || evaluate(expr2, env);
			case EQUAL1:
			case EQUAL2:
				if(VERSION_PATTERN.matcher(expr1).matches()
					&& VERSION_PATTERN.matcher(expr2).matches())
					return compareVersions(expr1, expr2) == 0;
				else
					return expr1.equals(expr2);
			case NOT_EQUAL:
				if(VERSION_PATTERN.matcher(expr1).matches()
					&& VERSION_PATTERN.matcher(expr2).matches())
					return compareVersions(expr1, expr2) != 0;
				else
					return !expr1.equals(expr2);
			case LESS:
				return compareVersions(expr1, expr2) < 0;
			case LESS_EQUAL:
				return compareVersions(expr1, expr2) <= 0;
			case GREATER:
				return compareVersions(expr1, expr2) > 0;
			case GREATER_EQUAL:
				return compareVersions(expr1, expr2) >= 0;
			}
			throw new IllegalStateException("Unrecognized operator: " + op);
		}
		else
		{
			java.util.regex.Matcher matcher = ENV_VAR_REF_PATTERN.matcher(ifStr);
			if(matcher.matches())
			{
				String var = env.getVariable(matcher.group(1));
				if(var == null)
					return false;
				return !"false".equalsIgnoreCase(var);
			}
			log.error("if statement cannot be evaluated: " + ifStr);
			return false;
		}
	}

	private static int findClose(String s)
	{
		int d = 0;
		for(int c = 1; c < s.length(); c++)
		{
			if(s.charAt(c) == ')')
			{
				if(d == 0)
					return c;
				else
					d--;
			}
			else if(s.charAt(c) == '(')
				d++;
		}
		return -1;
	}

	/**
	 * <p>
	 * Compares two version strings. Version strings may be composed of any number of integers,
	 * separated by either a '.' or a '_' and may be preceded by a letter string (e.g. "v") that may
	 * have a separator ('.' or '_') between it and the rest of the version, and terminated by an
	 * alphanumeric string (e.g. "alpha", "a" or "RC1").
	 * </p>
	 * <p>
	 * If both strings have a prefix, these are compared ignoring case and if different, that value
	 * is returned. If only one version has a prefix, the other's prefix is ignored. Then each pair
	 * of integers in the version strings is compared. The first one of these pairs that is
	 * different will determine the return value. If the integer components of the versions are
	 * equivalent, the alphanumeric end-tag will be used. If both versions have tags, the tags will
	 * be compared alphanumerically, ignoring case. If one of the versions has a tag and the other
	 * one does not, the one without the tag will be deemed more recent. This is inspired by alpha,
	 * beta, and release-candidate tags. E.g. 3.0.0 &gt; 3.0.0RC2 &gt; 3.0.0b &gt; 3.0.0a
	 * </p>
	 * 
	 * @param v1 The first version string to compare
	 * @param v2 The second version string to compare
	 * @return 1 if the first version is newer, -1 if the first version is older, 0 if the versions
	 *         are the same
	 */
	public static int compareVersions(String v1, String v2)
	{
		if(v1 == null && v2 == null)
			return 0;
		if(v1 == null)
			return -1;
		if(v2 == null)
			return 1;
		v1 = v1.trim();
		v2 = v2.trim();
		if(v1.equalsIgnoreCase(v2))
			return 0;

		if(v1.length() == 0)
			return -1;
		else if(v2.length() == 0)
			return 1;

		final String o1 = v1, o2 = v2;
		int idx1, idx2;

		for(idx1 = 0; idx1 < v1.length() && (v1.charAt(idx1) < '0' || v1.charAt(idx1) > '9'); idx1++);
		for(idx2 = 0; idx2 < v2.length() && (v2.charAt(idx2) < '0' || v2.charAt(idx2) > '9'); idx2++);
		if(idx1 >= 0 && idx2 >= 0)
		{
			int ret = v1.substring(0, idx1).compareToIgnoreCase(v2.substring(0, idx2));
			if(ret != 0)
				return ret;
			v1 = v1.substring(idx1);
			v2 = v2.substring(idx2);
			idx1 = sepIndex(v1);
			if(idx1 == 0)
			{
				v1 = v1.substring(1);
				idx1 = sepIndex(v1);
			}
			idx2 = sepIndex(v2);
			if(idx2 == 0)
			{
				v2 = v2.substring(1);
				idx2 = sepIndex(v2);
			}
		}
		else
		{
			idx1 = sepIndex(v1);
			idx2 = sepIndex(v2);
		}
		while(idx1 >= 0 && idx2 >= 0)
		{
			int num1, num2;
			try
			{
				num1 = Integer.parseInt(v1.substring(0, idx1));
			} catch(NumberFormatException e)
			{
				try
				{
					num2 = Integer.parseInt(v2.substring(0, idx2));
				} catch(NumberFormatException e2)
				{
					log.error("Versions in comparison (" + o1 + ", " + o2
						+ ") are not version numbers");
					return 0;
				}
				log.error("Version in comparison (" + o1 + ") is not a version number");
				return -1;
			}
			try
			{
				num2 = Integer.parseInt(v2.substring(0, idx2));
			} catch(NumberFormatException e)
			{
				log.error("Version in comparison (" + o2 + ") is not a version number");
				return 1;
			}
			// Compare version numbers
			if(num1 != num2)
				return num1 - num2;
			v1 = v1.substring(idx1 + 1);
			v2 = v2.substring(idx2 + 1);
			idx1 = sepIndex(v1);
			idx2 = sepIndex(v2);
		}
		if(idx1 >= 0)
			return 1; // First version has more numbers left--it's more recent
		else if(idx2 >= 0)
			return -1; // Second version has more numbers left--it's more recent

		// Last element in the version--may start with a number
		for(idx1 = 0; idx1 < v1.length() && v1.charAt(idx1) >= '0' && v1.charAt(idx1) <= '9'; idx1++);
		for(idx2 = 0; idx2 < v2.length() && v2.charAt(idx2) >= '0' && v2.charAt(idx2) <= '9'; idx2++);

		if(idx1 > 0)
		{ // All numbers in the remainder of v1
			if(idx2 > 0)
			{ // Compare last version numbers
				int num1, num2;
				try
				{
					num1 = Integer.parseInt(v1.substring(0, idx1));
				} catch(NumberFormatException e)
				{
					try
					{
						num2 = Integer.parseInt(v2.substring(0, idx2));
					} catch(NumberFormatException e2)
					{
						log.error("Versions in comparison (" + o1 + ", " + o2
							+ ") are not version numbers");
						return 0;
					}
					log.error("Version in comparison (" + o1 + ") is not a version number");
					return -1;
				}
				try
				{
					num2 = Integer.parseInt(v2.substring(0, idx2));
				} catch(NumberFormatException e)
				{
					log.error("Version in comparison (" + o2 + ") is not a version number");
					return 1;
				}
				if(num1 != num2)
					return num1 - num2;
				v1 = v1.substring(idx1);
				v2 = v2.substring(idx2);
			}
			else
				return 1; // First version has more numbers left--it's more recent
		}
		else if(idx2 > 0)
			return -1; // Second version has more numbers left--it's more recent

		// All that's left now is the alphanumeric end tag, if anything
		if(v1.length() == 0)
		{
			if(v2.length() == 0)
				return 0;
			else
				return 1; // Second version has an alpha or other mod on it--it is older
		}
		else if(v2.length() == 0)
			return -1; // First version has an alpha or other mod on it--it is older

		// Both versions have an end tag
		return v1.compareToIgnoreCase(v2);
	}

	private static int sepIndex(String v)
	{
		int dotIdx = v.indexOf('.');
		int undIdx = v.indexOf('_');
		if(dotIdx < 0)
			return undIdx;
		else if(undIdx < 0)
			return dotIdx;
		else if(dotIdx < undIdx)
			return dotIdx;
		else
			return undIdx;
	}

	/**
	 * Creates a PrismsConfig
	 * 
	 * @param env The PRISMS environment to use to replace referred environment variables with
	 * @param name The name for the config
	 * @param value The base value for the config
	 * @param children The subconfigs for the new PrismsConfig
	 * @return The new PrismsConfig
	 */
	public static PrismsConfig create(PrismsEnv env, String name, String value,
		PrismsConfig... children)
	{
		return new DefaultPrismsConfig(name, replace(value, env), children);
	}

	/**
	 * Tests this class by allowing the user to type XML in the console and have it interpreted
	 * on-the-fly
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		PrismsServer.initLog4j(PrismsServer.class.getResource("log4j.xml"));
		PrismsEnv env = new PrismsEnv();
		java.util.Scanner scanner = new java.util.Scanner(System.in);
		String read = scanner.nextLine();
		StringBuilder buffer = new StringBuilder();
		while(!"exit".equals(read) && !"quit".equals(read))
		{
			if(read.length() == 0)
			{}
			else if("clear".equals(read))
				buffer.setLength(0);
			else
			{
				buffer.append(read);
				if(read.endsWith(";"))
				{
					buffer.setLength(buffer.length() - 1);
					org.dom4j.Element root = null;
					try
					{
						root = new org.dom4j.io.SAXReader().read(
							new java.io.StringReader(buffer.toString())).getRootElement();
					} catch(org.dom4j.DocumentException e)
					{
						System.out.println("Could not parse XML: " + e);
					}
					if(root != null)
					{
						buffer.setLength(0);
						PrismsConfig compiled = fromXml(env, root);
						System.out.println("\nCompiled: " + compiled + "\n");
					}
				}
			}
			read = scanner.nextLine();
		}
	}
}
