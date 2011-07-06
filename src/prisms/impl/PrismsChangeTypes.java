/*
 * PrismsChangeTypes.java Created Dec 3, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

/** All {@link prisms.records.ChangeType}s for PRISMS data */
public class PrismsChangeTypes
{
	/** All change types against users */
	public enum UserChange implements prisms.records.ChangeType
	{
		/** Changing a user's name */
		name(null, String.class, false),
		/** Locking or unlocking a user */
		locked(null, Boolean.class, false),
		/** Changing a user's admin status */
		admin(null, Boolean.class, false),
		/** Changing whether a user is read-only */
		readOnly(null, Boolean.class, false),
		/** Granting or revoking a user's permission to access an application */
		appAccess(prisms.arch.PrismsApplication.class, null, false),
		/** Adding or removing a user from a group */
		group(prisms.arch.ds.UserGroup.class, null, false);

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
			case locked:
				if(add != 0)
					return null;
				return "User Locked or Unlocked";
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
			case group:
				if(add > 0)
					return "User Added To Group";
				else if(add < 0)
					return "User Removed From Group";
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
			case locked:
				if(add != 0)
					return null;
				return "User " + major + " locked or unlocked";
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
			case group:
				if(add > 0)
					return "User " + major + " added to group " + minor;
				else if(add < 0)
					return "User " + major + " removed from group " + minor;
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
			case locked:
				if(add != 0)
					return null;
				return "User " + major
					+ (((Boolean) after).booleanValue() ? " locked" : " unlocked");
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
			case group:
				return toString(add, major, minor);
			}
			throw new IllegalStateException("Unrecognized user change " + this);
		}
	}

	/** All change types against users */
	public enum GroupChange implements prisms.records.ChangeType
	{
		/** Changing a group's name */
		name(null, String.class, false),
		/** Changing a user's admin status */
		descrip(null, String.class, false),
		/** Granting or revoking a permission for a group */
		permission(prisms.arch.Permission.class, null, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		GroupChange(Class<?> minorType, Class<?> objectType, boolean id)
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
				return "Group Name Changed";
			case descrip:
				if(add != 0)
					return null;
				return "Group Description Changed";
			case permission:
				if(add > 0)
					return "Group Permission Granted";
				else if(add < 0)
					return "Group Permission Revoked";
				else
					return null;

			}
			throw new IllegalStateException("Unrecognized group change " + this);
		}

		public String toString(int add, Object major, Object minor)
		{
			switch(this)
			{
			case name:
				if(add != 0)
					return null;
				return "Group " + major + "'s name changed";
			case descrip:
				if(add != 0)
					return null;
				return "Group " + major + "'s description changed";
			case permission:
				if(add > 0)
					return "Group " + major + " granted permission " + minor;
				else if(add < 0)
					return "Group " + major + " revoked permission " + minor;
				else
					return null;
			}
			throw new IllegalStateException("Unrecognized group change " + this);
		}

		public String toString(int add, Object major, Object minor, Object before, Object after)
		{
			switch(this)
			{
			case name:
				if(add != 0)
					return null;
				return "Group " + major + "'s name changed from " + before + " to " + after;
			case descrip:
				if(add != 0)
					return null;
				return "Group " + major + "'s description changed from " + before + " to " + after;
			case permission:
				return toString(add, major, minor);
			}
			throw new IllegalStateException("Unrecognized user change " + this);
		}
	}
}
