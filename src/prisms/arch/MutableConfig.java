package prisms.arch;

import org.dom4j.Element;

/** A modifiable version of PrismsConfig */
public class MutableConfig extends PrismsConfig
{
	/** Listens to changes in a configuration */
	public static interface ConfigListener
	{
		/** @param config The configuration that was added */
		void configAdded(MutableConfig config);

		/** @param config The configuration that was removed */
		void configRemoved(MutableConfig config);

		/**
		 * @param config The configuration whose value was modified
		 * @param previousValue The previous value of the configuration
		 */
		void configChanged(MutableConfig config, String previousValue);
	}

	private MutableConfig theParent;

	private String theName;

	private String theValue;

	private MutableConfig [] theSubConfigs;

	private ConfigListener [] theListeners;

	private ConfigListener theSubConfigListener;

	/**
	 * Creates a blank config with just a name
	 * 
	 * @param name The name for the configuration
	 */
	public MutableConfig(String name)
	{
		theName = name;
		theSubConfigs = new MutableConfig [0];
		theListeners = new ConfigListener [0];
		theSubConfigListener = new ConfigListener()
		{
			@Override
			public void configAdded(MutableConfig config)
			{
				MutableConfig.this.configAdded(config);
			}

			@Override
			public void configRemoved(MutableConfig config)
			{
				MutableConfig.this.configRemoved(config);
			}

			@Override
			public void configChanged(MutableConfig config, String previousValue)
			{
				MutableConfig.this.configChanged(config, previousValue);
			}
		};
	}

	/**
	 * Creates a blank config with just a name and a parent
	 * 
	 * @param parent The parent for this config
	 * @param name The name for the configuration
	 */
	public MutableConfig(MutableConfig parent, String name) {
		this(name);
		theParent = parent;
	}

	/**
	 * Creates a modifiable version of an existing PrismsConfig
	 * 
	 * @param parent The configuration that this config is a sub-config for. Null for a top-level configuration.
	 * @param config The configuration to duplicate as modifiable
	 */
	public MutableConfig(MutableConfig parent, PrismsConfig config)
	{
		theParent = parent;
		theName = config.getName();
		theValue = config.getValue();
		theListeners = new ConfigListener [0];
		PrismsConfig [] subs = config.subConfigs();
		theSubConfigs = new MutableConfig [subs.length];
		for(int i = 0; i < subs.length; i++)
		{
			theSubConfigs[i] = new MutableConfig(this, subs[i]);
			theSubConfigs[i].addListener(theSubConfigListener);
		}
	}

	/** @return The configuration that this config is a sub-config for. Null if this is a top-level configuration. */
	public MutableConfig getParent()
	{
		return theParent;
	}

	void setParent(MutableConfig config)
	{
		theParent = config;
	}

	ConfigListener getSubConfigListener()
	{
		return theSubConfigListener;
	}

	/** @return The path to this configuration from the top-level config, including the top-level config. '/'-separated. */
	public String getPath()
	{
		java.util.ArrayList<MutableConfig> configPath = new java.util.ArrayList<>();
		MutableConfig config = this;
		while(config != null)
		{
			configPath.add(config);
			config = config.getParent();
		}
		StringBuilder ret = new StringBuilder();
		for(int i = configPath.size() - 1; i >= 0; i--)
		{
			ret.append(configPath.get(i).getName());
			if(i > 0)
				ret.append('/');
		}
		return ret.toString();
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
	 * @return This config, for chaining
	 */
	public MutableConfig set(String key, String value) {
		MutableConfig config = subConfig(key);
		if(value == null) {
			if(config != null)
				removeSubConfig(config);
		} else {
			if(config == null) {
				config = new MutableConfig(key);
				addSubConfig(config);
			}
			config.setValue(value);
		}
		return this;
	}

	/**
	 * @param value The value for this configuration
	 * @return This config, for chaining
	 */
	public MutableConfig setValue(String value) {
		String preValue = theValue;
		theValue = value;
		configChanged(this, preValue);
		return this;
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
	 * Retrieves the first sub configuration with the given name, or creates a new sub configuration with the given name if none exists
	 * already
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
		prisms.util.ArrayUtils.adjust(theSubConfigs, subs, new prisms.util.ArrayUtils.DifferenceListener<MutableConfig, MutableConfig>()
			{
			@Override
			public boolean identity(MutableConfig o1, MutableConfig o2)
			{
				return o1 == o2;
			}

			@Override
			public MutableConfig added(MutableConfig o, int mIdx, int retIdx)
			{
				o.setParent(MutableConfig.this);
				o.addListener(getSubConfigListener());
				return null;
			}

			@Override
			public MutableConfig removed(MutableConfig o, int oIdx, int incMod, int retIdx)
			{
				if(o.getParent() == MutableConfig.this)
					o.setParent(null);
				o.removeListener(getSubConfigListener());
				return null;
			}

			@Override
			public MutableConfig set(MutableConfig o1, int idx1, int incMod, MutableConfig o2, int idx2, int retIdx)
			{
				return null;
			}
			});
		theSubConfigs = subs;
	}

	/**
	 * @param sub The sub configuration to add to this configuration
	 * @return The added config, for chaining
	 */
	public MutableConfig addSubConfig(MutableConfig sub)
	{
		theSubConfigs = prisms.util.ArrayUtils.add(theSubConfigs, sub);
		sub.theParent = this;
		sub.addListener(theSubConfigListener);
		return sub;
	}

	/** @param sub The sub configuration to remove from this configuration */
	public void removeSubConfig(MutableConfig sub)
	{
		theSubConfigs = prisms.util.ArrayUtils.remove(theSubConfigs, sub);
		if(sub.theParent == this)
			sub.theParent = null;
		sub.removeListener(theSubConfigListener);
	}

	/** @param listener The listener to be notified when this configuration or any of its children change */
	public void addListener(ConfigListener listener)
	{
		if(listener != null)
			theListeners = prisms.util.ArrayUtils.add(theListeners, listener);
	}

	/** @param listener The listener to stop notification for */
	public void removeListener(ConfigListener listener)
	{
		theListeners = prisms.util.ArrayUtils.remove(theListeners, listener);
	}

	void configAdded(MutableConfig config)
	{
		for(ConfigListener listener : theListeners)
			listener.configAdded(config);
	}

	void configRemoved(MutableConfig config)
	{
		for(ConfigListener listener : theListeners)
			listener.configRemoved(config);
	}

	void configChanged(MutableConfig config, String previousValue)
	{
		for(ConfigListener listener : theListeners)
			listener.configChanged(config, previousValue);
	}

	@Override
	public MutableConfig clone()
	{
		final MutableConfig ret = (MutableConfig) super.clone();
		ret.theSubConfigs = new MutableConfig [theSubConfigs.length];
		ret.theListeners = new ConfigListener [0];
		ret.theSubConfigListener = new ConfigListener()
		{
			@Override
			public void configAdded(MutableConfig config)
			{
				ret.configAdded(config);
			}

			@Override
			public void configRemoved(MutableConfig config)
			{
				ret.configRemoved(config);
			}

			@Override
			public void configChanged(MutableConfig config, String previousValue)
			{
				ret.configChanged(config, previousValue);
			}
		};
		for(int i = 0; i < theSubConfigs.length; i++)
		{
			ret.theSubConfigs[i] = theSubConfigs[i].clone();
			ret.theSubConfigs[i].addListener(ret.theSubConfigListener);
		}
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
			if(attrs.get(sub.theName)[0] == 1 && sub.theSubConfigs.length == 0 && sub.theValue != null && sub.theValue.indexOf('\n') < 0)
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
