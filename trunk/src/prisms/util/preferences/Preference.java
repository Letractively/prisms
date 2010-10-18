/*
 * Preference.java Created Aug 13, 2009 by Andrew Butler, PSL
 */
package prisms.util.preferences;

/**
 * Represents a property that is configurable per user.
 * 
 * @param <T> The type of the value that this preference represents
 */
public class Preference<T> implements Comparable<Preference<?>>
{
	/**
	 * The preference type
	 */
	public static enum Type
	{
		/**
		 * A simple boolean-type preference
		 */
		BOOLEAN(Boolean.class) {
			@Override
			public Object deserialize(String serialized)
			{
				return Boolean.valueOf(serialized);
			}
		},
		/**
		 * A simple integer-type preference
		 */
		INT(Integer.class) {
			@Override
			public Object deserialize(String serialized)
			{
				return new Integer(serialized);
			}
		},
		/**
		 * An integer-type preference that cannot be negative
		 */
		NONEG_INT(Integer.class) {
			@Override
			public Object deserialize(String serialized)
			{
				return new Integer(serialized);
			}

			@Override
			public void validate(Object value)
			{
				super.validate(value);
				if(((Integer) value).intValue() < 0)
					throw new IllegalArgumentException(
						"Cannot set a negative value for this preference");
			}
		},
		/**
		 * A simple floating point-type preference
		 */
		FLOAT(Float.class) {
			@Override
			public Object deserialize(String serialized)
			{
				return new Float(serialized);
			}
		},
		/**
		 * A floating point-type preference that cannot be negative
		 */
		NONEG_FLOAT(Float.class) {
			@Override
			public Object deserialize(String serialized)
			{
				return new Float(serialized);
			}

			/**
			 * @see prisms.util.preferences.Preference.Type#validate(java.lang.Object)
			 */
			@Override
			public void validate(Object value)
			{
				super.validate(value);
				if(((Float) value).floatValue() < 0)
					throw new IllegalArgumentException(
						"Cannot set a negative value for this preference");
			}
		},
		/**
		 * A simple string-type preference
		 */
		STRING(String.class) {
			@Override
			public Object deserialize(String serialized)
			{
				return serialized;
			}
		},
		/**
		 * A preference that represents a choice between several possible values
		 */
		ENUM(Enum.class) {
			@Override
			@SuppressWarnings("rawtypes")
			public Object deserialize(String serialized)
			{
				try
				{
					int divIdx = serialized.indexOf(':');
					Class enumClass = Class.forName(serialized.substring(0, divIdx));
					return Enum.valueOf(enumClass, serialized.substring(divIdx + 1));
				} catch(Throwable e)
				{
					throw new IllegalArgumentException("Could not parse property: " + serialized, e);
				}
			}

			@Override
			public String serialize(Object o)
			{
				return o.getClass().getName() + ":" + ((Enum<?>) o).name();
			}
		},
		/**
		 * A color-type preference
		 */
		COLOR(java.awt.Color.class) {
			@Override
			public Object deserialize(String serialized)
			{
				return prisms.util.JsonUtils.fromHTML(serialized);
			}

			@Override
			public String serialize(Object o)
			{
				return prisms.util.JsonUtils.toHTML((java.awt.Color) o);
			}
		},
		/**
		 * A proportion-type preference, representing a value between 0 and 1
		 */
		PROPORTION(Float.class) {
			@Override
			public Object deserialize(String serialized)
			{
				return new Float(serialized);
			}

			/**
			 * @see prisms.util.preferences.Preference.Type#validate(java.lang.Object)
			 */
			@Override
			public void validate(Object value)
			{
				super.validate(value);
				if(((Float) value).floatValue() < 0 || ((Float) value).floatValue() > 1)
					throw new IllegalArgumentException(
						"The value for this preference must be between 0 and 1");
			}
		},
		/**
		 * An arbitrary value that cannot be edited directly but can be set and retrieved
		 * programmatically.
		 */
		ARBITRARY(Object.class) {
			@Override
			public Object deserialize(String serialized)
			{
				return org.json.simple.JSONValue.parse(serialized);
			}

			@Override
			public void validate(Object value)
			{
				try
				{
					prisms.arch.JsonSerializer.validate(value);
				} catch(java.io.NotSerializableException e)
				{
					throw new IllegalArgumentException(
						"Arbitrary values must be JSON-serializable", e);
				}
			}
		};

		private final Class<?> theType;

		Type(Class<?> type)
		{
			theType = type;
		}

		/**
		 * @return The java type that a preference of this type can hold
		 */
		public Class<?> getType()
		{
			return theType;
		}

		/**
		 * Serializes the value for persistence
		 * 
		 * @param value The value to serialize
		 * @return The serialized value
		 */
		public String serialize(Object value)
		{
			return value.toString();
		}

		/**
		 * Deserializes the value from persistence
		 * 
		 * @param serialized The serialized value
		 * @return The deserialized value
		 */
		public abstract Object deserialize(String serialized);

		/**
		 * Validates a preference value when it is set
		 * 
		 * @param value The new value for a preference of this type
		 */
		public void validate(Object value)
		{
			if(!theType.isInstance(value))
				throw new IllegalArgumentException(value.getClass().getName() + " " + value
					+ " is not an instance of " + theType);
		}
	}

	private final Type theType;

	private final String theDomain;

	private final String theName;

	private final boolean isDisplayed;

	/**
	 * Creates a preference
	 * 
	 * @param domain The domain of the preference
	 * @param name The name of the preference
	 * @param type The type of the preference
	 * @param javaType The java sub-type of the preference
	 * @param displayed Whether this preference is to be directly editable by the user or not
	 */
	public Preference(String domain, String name, Type type, Class<T> javaType, boolean displayed)
	{
		theType = type;
		theDomain = domain;
		theName = name;
		if(!theType.getType().isAssignableFrom(javaType))
			throw new IllegalArgumentException("Preference type " + type
				+ " is incompatible with java type " + javaType + ": java type must be "
				+ type.getType().getName());
		isDisplayed = displayed;
		if(displayed && type == Type.ARBITRARY)
			throw new IllegalArgumentException(
				"Cannot create a displayed preference of arbitrary type");
	}

	/**
	 * @return This preference's domain
	 */
	public String getDomain()
	{
		return theDomain;
	}

	/**
	 * @return This preference's name
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * @return This preference's type
	 */
	public Type getType()
	{
		return theType;
	}

	/**
	 * @return Whether this preference can be directly edited by the user
	 */
	public boolean isDisplayed()
	{
		return isDisplayed;
	}

	public int hashCode()
	{
		return theDomain.hashCode() * 31 + theName.hashCode() * 17;
	}

	public boolean equals(Object o)
	{
		if(!(o instanceof Preference<?>))
			return false;
		Preference<?> pref = (Preference<?>) o;
		return theDomain.equals(pref.theDomain) && theName.equals(pref.theName);
	}

	public int compareTo(Preference<?> pref)
	{
		int ret = theDomain.compareToIgnoreCase(pref.theDomain);
		if(ret != 0)
			return ret;
		ret = theType.ordinal() - pref.theType.ordinal();
		if(ret != 0)
			return ret;
		ret = theName.compareToIgnoreCase(pref.theName);
		return ret;
	}

	public String toString()
	{
		return "Preference " + theDomain + "/" + theName + "(type " + theType + ")";
	}
}
