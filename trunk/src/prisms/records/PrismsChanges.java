/*
 * PrismsChanges.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/**
 * The changes that can happen internal to the PRISMS synchronization architecture. Changes of these
 * types are internal and should not be synchronized to another center.
 */
public enum PrismsChanges implements SubjectType
{
	/**
	 * Modification of a field in a PRISMS center
	 */
	center(PrismsCenter.class, null, null, CenterChange.class),
	/**
	 * Modification of a field in the auto-purge
	 */
	autoPurge(AutoPurger.class, null, null, AutoPurgeChange.class);

	private final Class<?> theMajorType;

	private final Class<?> theMDType1;

	private final Class<?> theMDType2;

	private final Class<? extends Enum<? extends ChangeType>> theChangeClass;

	PrismsChanges(Class<?> majorType, Class<?> md1, Class<?> md2,
		Class<? extends Enum<? extends ChangeType>> fieldsClass)
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

	public Class<? extends Enum<? extends ChangeType>> getChangeTypes()
	{
		return theChangeClass;
	}

	/**
	 * Names of all fields that may change in a {@link PrismsCenter}
	 */
	public static enum CenterChange implements ChangeType
	{
		/**
		 * @see PrismsCenter#getName()
		 * @see PrismsCenter#setName(String)
		 */
		name(null, String.class, false),
		/**
		 * @see PrismsCenter#getServerURL()
		 * @see PrismsCenter#setServerURL(String)
		 */
		url(null, String.class, false),
		/**
		 * @see PrismsCenter#getServerUserName()
		 * @see PrismsCenter#setServerUserName(String)
		 */
		serverUserName(null, String.class, false),
		/**
		 * @see PrismsCenter#getServerPassword()
		 * @see PrismsCenter#setServerPassword(String)
		 */
		serverPassword(null, String.class, false),
		/**
		 * @see PrismsCenter#getServerSyncFrequency()
		 * @see PrismsCenter#setServerSyncFrequency(long)
		 */
		syncFrequency(null, Long.class, false),
		/**
		 * @see PrismsCenter#getClientUser()
		 * @see PrismsCenter#setClientUser(RecordUser)
		 */
		clientUser(null, RecordUser.class, true),
		/**
		 * @see PrismsCenter#getChangeSaveTime()
		 * @see PrismsCenter#setChangeSaveTime(long)
		 */
		changeSaveTime(null, Long.class, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		CenterChange(Class<?> minorType, Class<?> objectType, boolean id)
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
	}

	/**
	 * Names of all fields that may change in the auto-purge feature of the database
	 */
	public static enum AutoPurgeChange implements ChangeType
	{
		/**
		 * @see AutoPurger#getEntryCount()
		 * @see AutoPurger#setEntryCount(int)
		 */
		entryCount(null, Integer.class, false),
		/**
		 * @see AutoPurger#getAge()
		 * @see AutoPurger#setAge(long)
		 */
		age(null, Long.class, false),
		/**
		 * The exclusion or inclusion of a user from auto-purge
		 */
		excludeUser(RecordUser.class, null, false),
		/**
		 * The exclusion or inclusion of a type from auto-purge
		 */
		excludeType(null, String.class, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		AutoPurgeChange(Class<?> minorType, Class<?> objectType, boolean id)
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
	}
}
