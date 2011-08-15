/*
 * SerializablePropertyPersister.java Created Aug 8, 2011 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import java.util.Map.Entry;

import org.apache.log4j.Logger;

import prisms.arch.PrismsSession;
import prisms.util.preferences.Preference;

/**
 * A persister that persists a serializable property using the PRISMS preferences architecture
 * 
 * @param <T> The component type of the property
 */
public class SerializablePropertyPersister<T> implements UserSpecificPersister<T []>
{
	private static final Logger log = Logger.getLogger(SerializablePropertyPersister.class);

	/**
	 * Serializes properties for a {@link SerializablePropertyPersister}
	 * 
	 * @param <T> The type of value to serialize
	 */
	public static interface PropertySerializer<T>
	{
		/**
		 * @param config The configuration for the persister
		 * @param env The PRISMS environment
		 * @param property The property to persist
		 */
		void configure(prisms.arch.PrismsConfig config, prisms.arch.PrismsEnv env,
			prisms.arch.event.PrismsProperty<? super T []> property);

		/**
		 * @param property The value of the property to get the name of
		 * @return The name of the given property
		 */
		String getName(T property);

		/**
		 * @param property The value of the property to serialize
		 * @return The serialized property value
		 */
		String serialize(T property);

		/**
		 * @param name The name of the property
		 * @param serialized The serialized property value
		 * @return The deserialized property value
		 */
		T deserialize(String name, String serialized);
	}

	private prisms.arch.PrismsEnv theEnv;

	private prisms.arch.event.PrismsProperty<? super T []> theProperty;

	PropertySerializer<T> theSerializer;

	private java.util.concurrent.ConcurrentHashMap<prisms.arch.ds.User, T []> theValues;

	public void configure(prisms.arch.PrismsConfig config, prisms.arch.PrismsEnv env,
		prisms.arch.event.PrismsProperty<? super T []> property)
	{
		theValues = new java.util.concurrent.ConcurrentHashMap<prisms.arch.ds.User, T []>();
		theEnv = env;
		theProperty = property;
		try
		{
			theSerializer = config.getClass("serializer", PropertySerializer.class).newInstance();
			theSerializer.configure(config, env, property);
		} catch(Exception e)
		{
			throw new IllegalStateException(
				"Could not instantiate and configure property serializer", e);
		}
	}

	/** @return The PRISMS environment that this persister is in */
	public prisms.arch.PrismsEnv getEnv()
	{
		return theEnv;
	}

	/** @return The property that this persister persists the value of */
	public prisms.arch.event.PrismsProperty<? super T []> getProperty()
	{
		return theProperty;
	}

	public T [] getValue(prisms.arch.PrismsSession session)
	{
		T [] ret2 = theValues.get(session.getUser());
		if(ret2 != null)
			return ret2;
		java.util.LinkedHashMap<String, String> props = getProps(session);
		java.util.ArrayList<T> ret = new java.util.ArrayList<T>();
		for(java.util.Map.Entry<String, String> entry : props.entrySet())
		{
			try
			{
				ret.add(theSerializer.deserialize(entry.getKey(), entry.getValue()));
			} catch(RuntimeException e)
			{
				log.error("Error deserializing property value: " + entry.getKey(), e);
			}
		}
		ret2 = ret.toArray((T []) java.lang.reflect.Array.newInstance(theProperty.getType()
			.getComponentType(), ret.size()));
		theValues.put(session.getUser(), ret2);
		return ret2;
	}

	java.util.LinkedHashMap<String, String> getProps(PrismsSession session)
	{
		Preference<String> [] prefs = (Preference<String> []) session.getPreferences()
			.getAllPreferences(theProperty.getName());
		java.util.Arrays.sort(prefs, new java.util.Comparator<Preference<?>>()
		{
			public int compare(Preference<?> p1, Preference<?> p2)
			{
				return p1.getName().compareToIgnoreCase(p2.getName());
			}
		});
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("prop:(.*)(#\\d+)?");
		java.util.LinkedHashMap<String, String> props = new java.util.LinkedHashMap<String, String>();
		for(Preference<String> pref : prefs)
		{
			java.util.regex.Matcher match = pattern.matcher(pref.getName());
			if(!match.matches())
				continue;
			String name = match.group(1);
			if(match.group(2) != null)
				continue;
			String value = session.getPreferences().get(pref);
			for(int i = 1; true; i++)
			{
				String prefName = "prop:" + name + "#" + i;
				Preference<String> pref2 = new Preference<String>(theProperty.getName(), prefName,
					Preference.Type.STRING, String.class, false);
				String val = session.getPreferences().get(pref2);
				if(val != null)
					value += val;
				else
					break;
				i++;
			}
			props.put(name, value);
		}
		return props;
	}

	public synchronized void setValue(final PrismsSession session, Object [] value,
		@SuppressWarnings("rawtypes") prisms.arch.event.PrismsPCE evt)
	{
		Entry<String, String> [] entries = getProps(session).entrySet().toArray(new Entry [0]);
		T [] newVals = prisms.util.ArrayUtils.adjust((T []) value, entries,
			new prisms.util.ArrayUtils.DifferenceListener<T, Entry<String, String>>()
			{
				public boolean identity(T o1, Entry<String, String> o2)
				{
					return theSerializer.getName(o1).equals(o2.getKey());
				}

				public T added(Entry<String, String> o, int mIdx, int retIdx)
				{
					remove(session, o.getKey());
					return null;
				}

				public T removed(T o, int oIdx, int incMod, int retIdx)
				{
					add(session, o);
					return o;
				}

				public T set(T o1, int idx1, int incMod, Entry<String, String> o2, int idx2,
					int retIdx)
				{
					String ser = theSerializer.serialize(o1);
					if(!ser.equals(o2.getValue()))
						update(session, o2.getKey(), ser);
					return null;
				}
			});
		theValues.put(session.getUser(), newVals);
	}

	void add(PrismsSession session, T value)
	{
		update(session, theSerializer.getName(value), theSerializer.serialize(value));
	}

	void remove(PrismsSession session, String name)
	{
		session.getPreferences().set(
			new Preference<String>(theProperty.getName(), name, Preference.Type.STRING,
				String.class, false), null);
		for(int i = 0; true; i++)
		{
			String propName = "prop:" + name + "#" + i;
			Preference<String> pref = new Preference<String>(theProperty.getName(), propName,
				Preference.Type.STRING, String.class, false);
			if(session.getPreferences().get(pref) != null)
				session.getPreferences().set(pref, null);
			else
				break;
		}
	}

	void update(PrismsSession session, String name, String serialized)
	{
		int i;
		for(i = 0; i * 1024 < serialized.length(); i++)
		{
			String prefName = "prop:" + name;
			if(i > 0)
				prefName += "#" + i;
			Preference<String> pref = new Preference<String>(theProperty.getName(), prefName,
				Preference.Type.STRING, String.class, false);
			String val_i;
			if(serialized.length() < (i + 1) * 1024)
				val_i = serialized.substring(i, serialized.length());
			else
				val_i = serialized.substring(i * 1024, (i + 1) * 1024);
			session.getPreferences().set(pref, val_i);
		}
		while(true)
		{
			String prefName = "prop:" + name + "#" + i;
			Preference<String> pref = new Preference<String>(theProperty.getName(), prefName,
				Preference.Type.STRING, String.class, false);
			if(session.getPreferences().get(pref) != null)
				session.getPreferences().set(pref, null);
			else
				break;
			i++;
		}
	}
}
