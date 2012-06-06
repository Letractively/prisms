package prisms.arch;

import org.dom4j.Element;

/** A modifiable version of PrismsConfig */
public class MutableConfig extends PrismsConfig
{
	private String theName;

	private String theValue;

	private MutableConfig [] theSubConfigs;

	/**
	 * Creates a blank config with just a name
	 * 
	 * @param name The name for the configuration
	 */
	public MutableConfig(String name)
	{
		theName = name;
		theSubConfigs = new MutableConfig [0];
	}

	/**
	 * Creates a modifiable version of an existing PrismsConfig
	 * 
	 * @param config The configuration to duplicate as modifiable
	 */
	public MutableConfig(PrismsConfig config)
	{
		theName = config.getName();
		theValue = config.getValue();
		PrismsConfig [] subs = config.subConfigs();
		theSubConfigs = new MutableConfig [subs.length];
		for(int i = 0; i < subs.length; i++)
			theSubConfigs[i] = new MutableConfig(subs[i]);
	}

	@Override
	public String getName()
	{
		return theName;
	}

	/** @param name The name for this configuration */
	public void setName(String name)
	{
		theName = name;
	}

	@Override
	public String getValue()
	{
		return theValue;
	}

	/**
	 * @param key The name of the sub-configuration to store the value in
	 * @param value The value to store for the given sub-configuration
	 */
	public void set(String key, String value)
	{
		getOrCreate(key).setValue(value);
	}

	/** @param value The value for this configuration */
	public void setValue(String value)
	{
		theValue = value;
	}

	@Override
	public MutableConfig [] subConfigs()
	{
		return theSubConfigs.clone();
	}

	@Override
	public MutableConfig subConfig(String type, String... props)
	{
		return (MutableConfig) super.subConfig(type, props);
	}

	@Override
	public MutableConfig [] subConfigs(String type, String... props)
	{
		return (MutableConfig []) super.subConfigs(type, props);
	}

	/**
	 * Retrieves the first sub configuration with the given name, or creates a new sub configuration with the given name
	 * if none exists already
	 * 
	 * @param type The name of the configuration to get or create
	 * @return The retrieved or created configuration
	 */
	public MutableConfig getOrCreate(String type)
	{
		MutableConfig ret = subConfig(type);
		if(ret == null)
		{
			ret = new MutableConfig(type);
			addSubConfig(ret);
		}
		return ret;
	}

	@Override
	protected MutableConfig [] createConfigArray(int size)
	{
		return new MutableConfig [size];
	}

	/** @param subs The sub configurations for this configuration */
	public void setSubConfigs(MutableConfig [] subs)
	{
		theSubConfigs = subs;
	}

	/**
	 * @param sub The sub configuration to add to this configuration
	 * @return The added config, for chaining
	 */
	public MutableConfig addSubConfig(MutableConfig sub)
	{
		theSubConfigs = prisms.util.ArrayUtils.add(theSubConfigs, sub);
		return sub;
	}

	/** @param sub The sub configuration to remove from this configuration */
	public void removeSubConfig(MutableConfig sub)
	{
		theSubConfigs = prisms.util.ArrayUtils.remove(theSubConfigs, sub);
	}

	@Override
	public MutableConfig clone()
	{
		MutableConfig ret = (MutableConfig) super.clone();
		ret.theSubConfigs = new MutableConfig [theSubConfigs.length];
		for(int i = 0; i < theSubConfigs.length; i++)
			ret.theSubConfigs[i] = theSubConfigs[i].clone();
		return ret;
	}

	/**
	 * Writes this configuration to an XML element
	 * 
	 * @param df The document factory with which to create the element
	 * @return The XML element representing this configuration
	 */
	public Element toXML(org.dom4j.DocumentFactory df)
	{
		Element ret = df.createElement(theName);
		if(theValue != null)
			ret.setText(theValue);
		java.util.HashMap<String, int []> attrs = new java.util.HashMap<String, int []>();
		for(MutableConfig sub : theSubConfigs)
		{
			int [] count = attrs.get(sub.theName);
			if(count == null)
			{
				count = new int [1];
				attrs.put(sub.theName, count);
			}
			count[0]++;
		}
		for(MutableConfig sub : theSubConfigs)
		{
			if(attrs.get(sub.theName)[0] == 1 && sub.theSubConfigs.length == 0 && sub.theValue != null
				&& sub.theValue.length() < 16)
				ret.addAttribute(sub.theName, sub.theValue);
			else
				ret.add(sub.toXML(df));
		}
		return ret;
	}

	/**
	 * @param config The configuration to write as XML
	 * @param out The stream to write the configuration to
	 * @throws java.io.IOException If an error occurs writing the XML document
	 */
	public static void writeAsXml(MutableConfig config, java.io.OutputStream out) throws java.io.IOException
	{
		org.dom4j.DocumentFactory df = org.dom4j.DocumentFactory.getInstance();
		Element root = config.toXML(df);
		org.dom4j.Document doc = df.createDocument(root);
		org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
		format.setIndent("\t");
		org.dom4j.io.XMLWriter writer;
		writer = new org.dom4j.io.XMLWriter(out, format);
		writer.write(doc);
		writer.flush();
	}
}
