/*
 * PreferenceSubjectType.java Created Jul 29, 2011 by Andrew Butler, PSL
 */
package prisms.util.preferences;

/** Subject types for changes to preferences */
public enum PreferenceSubjectType implements prisms.records.SubjectType
{
	/** The only subject type--preference */
	Preference(Preference.class, prisms.arch.PrismsApplication.class, prisms.arch.ds.User.class,
		PreferenceChange.class);

	private final Class<?> theMajorType;

	private final Class<?> theMDType1;

	private final Class<?> theMDType2;

	private final Class<? extends Enum<? extends prisms.records.ChangeType>> theChangeClass;

	PreferenceSubjectType(Class<?> majorType, Class<?> md1, Class<?> md2,
		Class<? extends Enum<? extends prisms.records.ChangeType>> fieldsClass)
	{
		theMajorType = majorType;
		theMDType1 = md1;
		theMDType2 = md2;
		theChangeClass = fieldsClass;
	}

	public Class<?> getMajorType()
	{
		return theMajorType;
	}

	public Class<?> getMetadataType1()
	{
		return theMDType1;
	}

	public Class<?> getMetadataType2()
	{
		return theMDType2;
	}

	public Class<? extends Enum<? extends prisms.records.ChangeType>> getChangeTypes()
	{
		return theChangeClass;
	}

	/** Change types for preferences */
	public static enum PreferenceChange implements prisms.records.ChangeType
	{
		/** The only change type--the value of a preference changed */
		Value(null, Object.class, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		PreferenceChange(Class<?> minorType, Class<?> objectType, boolean id)
		{
			theMinorType = minorType;
			theObjectType = objectType;
			isIdentifiable = id;
		}

		public Class<?> getMinorType()
		{
			return theMinorType;
		}

		public Class<?> getObjectType()
		{
			return theObjectType;
		}

		public boolean isObjectIdentifiable()
		{
			return isIdentifiable;
		}

		public String toString(int additivity)
		{
			switch(this)
			{
			case Value:
				return "Preference value changed";
			}
			throw new IllegalStateException("Unrecognized preference change " + this);
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject)
		{
			switch(this)
			{
			case Value:
				return "Value of " + majorSubject + " changed";
			}
			throw new IllegalStateException("Unrecognized preference change " + this);
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject,
			Object before, Object after)
		{
			switch(this)
			{
			case Value:
				return "Value of " + majorSubject + " changed from " + before + " to " + after;
			}
			throw new IllegalStateException("Unrecognized preference change " + this);
		}
	}
}
