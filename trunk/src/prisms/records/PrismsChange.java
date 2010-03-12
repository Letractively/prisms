/*
 * PrismsChanges.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import prisms.util.PrismsUtils;

/**
 * The changes that can happen internal to the PRISMS synchronization architecture. Changes of these
 * types are internal and should not be synchronized to another center.
 */
public enum PrismsChange implements SubjectType
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

	PrismsChange(Class<?> majorType, Class<?> md1, Class<?> md2,
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

		public String toString(int add)
		{
			switch(this)
			{
			case name:
				return "Center Name Changed";
			case url:
				return "Center Server URL Changed";
			case serverUserName:
				return "Center Server User Name Changed";
			case serverPassword:
				return "Center Server Password Changed";
			case syncFrequency:
				return "Center Sync Frequency Changed";
			case clientUser:
				return "Center Client User Changed";
			case changeSaveTime:
				return "Center Change Save Time Changed";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int add, Object major, Object minor)
		{
			switch(this)
			{
			case name:
				return "Center " + major + "'s name changed";
			case url:
				return "Center " + major + "'s server URL changed";
			case serverUserName:
				return "Center " + major + "'s server user name changed";
			case serverPassword:
				return "Center " + major + "'s server password changed";
			case syncFrequency:
				return "Center " + major + "'s server sync frequency changed";
			case clientUser:
				return "Center " + major + "'s client user changed";
			case changeSaveTime:
				return "Center " + major + "'s change save time changed";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int add, Object major, Object minor, Object before, Object after)
		{
			String ret = toString(add, major, minor);
			switch(this)
			{
			case name:
			case url:
			case serverUserName:
			case clientUser:
				return ret + " from " + before + " to " + after;
			case serverPassword:
				return ret; // Don't print the password out
			case syncFrequency:
			case changeSaveTime:
				long bef = ((Number) before).longValue();
				long aft = ((Number) before).longValue();
				return ret + " from " + (bef < 0 ? "none" : PrismsUtils.printTimeLength(bef))
					+ " to " + (aft < 0 ? "none" : PrismsUtils.printTimeLength(aft));
			}
			throw new IllegalStateException("Unrecognized change type " + name());
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

		public String toString(int add)
		{
			switch(this)
			{
			case entryCount:
				return "Auto-Purge Entry Count Changed";
			case age:
				return "Auto-Purge Age Changed";
			case excludeType:
				if(add > 0)
					return "Auto-Purge Type Excluded";
				else
					return "Auto-Purge Type Included";
			case excludeUser:
				if(add > 0)
					return "Auto-Purge User Excluded";
				else
					return "Auto-Purge User Included";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int add, Object major, Object minor)
		{
			return toString(add);
		}

		public String toString(int add, Object major, Object minor, Object before, Object after)
		{
			switch(this)
			{
			case entryCount:
				int bec = ((Number) before).intValue();
				int aec = ((Number) after).intValue();
				return toString(add, major, minor) + " from " + (bec < 0 ? "none" : "" + bec)
					+ " to " + (aec < 0 ? "none" : "" + aec);
			case age:
				long bAge = ((Number) before).longValue();
				long aAge = ((Number) after).longValue();
				return toString(add, major, minor) + " from "
					+ (bAge < 0 ? "none" : PrismsUtils.print(bAge)) + " to "
					+ (aAge < 0 ? "none" : PrismsUtils.print(aAge));
			case excludeType:
				if(add > 0)
					return "Type '" + before + "' excluded from auto-purge";
				else
					return "Type '" + before + "' re-included in auto-purge";
			case excludeUser:
				if(add > 0)
					return "User " + minor + " excluded from auto-purge";
				else
					return "User " + minor + " re-included in auto-purge";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}
	}
}
