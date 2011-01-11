/*
 * PrismsChangeTypes.java Created Dec 3, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

/** All {@link prisms.records2.ChangeType}s for PRISMS data */
public class PrismsChangeTypes
{
	/** All change types against users */
	public enum UserChange implements prisms.records2.ChangeType
	{
		/** Changing a user's name */
		name(null, String.class, false),
		/** Changing a user's admin status */
		admin(null, Boolean.class, false),
		/** Changing whether a user is read-only */
		readOnly(null, Boolean.class, false),
		/** Granting or revoking a user's permission to access an application */
		appAccess(prisms.arch.PrismsApplication.class, null, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		UserChange(Class<?> minorType, Class<?> objectType, boolean id)
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
				if(add != 0)
					return null;
				return "User Name Changed";
			case admin:
				if(add != 0)
					return null;
				return "User Admin Changed";
			case readOnly:
				if(add != 0)
					return null;
				return "User Read-Only Changed";
			case appAccess:
				if(add > 0)
					return "User Granted Application Access";
				else if(add < 0)
					return "User Denied Application Access";
				else
					return null;

			}
			throw new IllegalStateException("Unrecognized user change " + this);
		}

		public String toString(int add, Object major, Object minor)
		{
			switch(this)
			{
			case name:
				if(add != 0)
					return null;
				return "User " + major + "'s name changed";
			case admin:
				if(add != 0)
					return null;
				return "User " + major + "'s admin status changed";
			case readOnly:
				if(add != 0)
					return null;
				return "User " + major + " made read-only or writable";
			case appAccess:
				if(add > 0)
					return "User " + major + " granted access to application " + minor;
				else if(add < 0)
					return "User " + major + " denied access to application " + minor;
				else
					return null;
			}
			throw new IllegalStateException("Unrecognized user change " + this);
		}

		public String toString(int add, Object major, Object minor, Object before, Object after)
		{
			switch(this)
			{
			case name:
				if(add != 0)
					return null;
				return "User " + major + "'s name changed from " + before + " to " + after;
			case admin:
				if(add != 0)
					return null;
				return "User " + major + " made "
					+ (((Boolean) after).booleanValue() ? "admin" : "non-admin");
			case readOnly:
				if(add != 0)
					return null;
				return "User " + major + " made "
					+ (((Boolean) after).booleanValue() ? "read-only" : "modifiable");
			case appAccess:
				if(add > 0)
					return "User " + major + " granted access to application " + minor;
				else if(add < 0)
					return "User " + major + " denied access to application " + minor;
				else
					return null;
			}
			throw new IllegalStateException("Unrecognized user change " + this);
		}
	}
}
