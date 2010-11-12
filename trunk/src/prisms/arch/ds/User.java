/**
 * User.java Created Oct 10, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.Permission;
import prisms.arch.PrismsApplication;

/**
 * Represents a user of a {@link PrismsApplication}. Allows the application to restrict or deny
 * access based on a user's credentials and verifiability.
 */
public class User implements Cloneable
{
	private long theID;

	private boolean isAdmin;

	private final UserSource theSource;

	private String theName;

	private UserPermissions thePermissions;

	private java.util.ArrayList<UserGroup> theGroups;

	private boolean isReadOnly;

	private boolean isLocked;

	private boolean isDeleted;

	/**
	 * Creates a user
	 * 
	 * @param src The UserSource that this user is from
	 * @param name The name of the user
	 * @param id The storage ID for the user
	 */
	public User(UserSource src, String name, long id)
	{
		theSource = src;
		theName = name;
		theID = id;
		thePermissions = new UserPermissions(this);
		theGroups = new java.util.ArrayList<UserGroup>();
	}

	/** @return This user's source */
	public UserSource getSource()
	{
		return theSource;
	}

	/** @return This user's storage ID */
	public long getID()
	{
		return theID;
	}

	/**
	 * Sets the storage ID for this user. This method is left public for custom {@link UserSource}
	 * implementations, but this method should never be used except within such an implementation.
	 * In particular, this should never be modified within an application.
	 * 
	 * @param id The storage ID for this user
	 */
	public void setID(long id)
	{
		theID = id;
	}

	/** @return Whether this user is an admin (governs his permission changing passwords) */
	public boolean isAdmin()
	{
		return isAdmin;
	}

	/** @param admin Whether this user is an admin or not */
	public void setAdmin(boolean admin)
	{
		if(isReadOnly)
			throw new IllegalStateException("User " + theName + " is read-only");
		isAdmin = admin;
	}

	/** @return This user's name */
	public String getName()
	{
		return theName;
	}

	/**
	 * Sets this user's name
	 * 
	 * @param name The name for this user
	 */
	public void setName(String name)
	{
		if(isReadOnly)
			throw new IllegalStateException("User " + theName + " is read-only");
		theName = name;
	}

	/** @return The groups this user belongs to */
	public UserGroup [] getGroups()
	{
		return theGroups.toArray(new UserGroup [theGroups.size()]);
	}

	/**
	 * Adds this user to a group, associating the group's priveleges with this user
	 * 
	 * @param group The group to add this user to
	 */
	public void addTo(UserGroup group)
	{
		if(!theGroups.contains(group))
		{
			if(isReadOnly)
				throw new IllegalStateException("User " + theName + " is read-only");
			theGroups.add(group);
		}
	}

	/**
	 * Removes this user from a group
	 * 
	 * @param group The group to remove this user from
	 */
	public void removeFrom(UserGroup group)
	{
		if(theGroups.contains(group))
		{
			if(isReadOnly)
				throw new IllegalStateException("User " + theName + " is read-only");
			theGroups.remove(group);
		}
	}

	/**
	 * @param app The application to get permissions for
	 * @return This user's permissions
	 */
	public Permissions getPermissions(PrismsApplication app)
	{
		return thePermissions.forApp(app);
	}

	/** @return Whether this user is locked from creating new sessions */
	public boolean isLocked()
	{
		return isLocked;
	}

	/** @param locked Whether this user should be locked from creating new sessions */
	public void setLocked(boolean locked)
	{
		isLocked = locked;
	}

	/** @return Whether this user is read-only and cannot be modified */
	public boolean isReadOnly()
	{
		return isReadOnly;
	}

	/**
	 * @param readOnly Whether this user should be read-only so that it cannot be changed through
	 *        the manager
	 */
	public void setReadOnly(boolean readOnly)
	{
		isReadOnly = readOnly;
	}

	/**
	 * @return Whether this user has been removed from the active set. Deleted users may be
	 *         preserved in the database for record-keeping purposes, but they are not reflected in
	 *         the manager or allowed to log in to applications.
	 */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	/** @param deleted Whether this user has been removed from the active set */
	public void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	@Override
	public User clone()
	{
		User ret;
		try
		{
			ret = (User) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("Clone not supported", e);
		}
		ret.theID = -1;
		ret.isReadOnly = false;
		ret.isLocked = false;
		ret.theGroups = new java.util.ArrayList<UserGroup>();
		ret.theGroups.addAll(theGroups);
		ret.thePermissions = new UserPermissions(ret);
		return ret;
	}

	@Override
	public String toString()
	{
		return theName;
	}

	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof User))
			return false;
		User u = (User) o;
		if(u.theID == -1 || theID == -1)
			return theName.equals(u.theName);
		else
			return theID == u.theID;
	}

	@Override
	public int hashCode()
	{
		return ((int) theID) ^ ((int) (theID >>> 32));
	}

	private static class UserPermissions implements Permissions
	{
		final User theUser;

		private java.util.Map<String, UserAppPermissions> theAppPermissions;

		UserPermissions(User user)
		{
			theUser = user;
			theAppPermissions = new java.util.HashMap<String, UserAppPermissions>();
		}

		public boolean has(String capability)
		{
			for(UserGroup group : theUser.getGroups())
				if(group.getPermissions().has(capability))
					return true;
			return false;
		}

		public prisms.arch.Permission getPermission(String capability)
		{
			prisms.arch.Permission perm = null;
			for(UserGroup group : theUser.getGroups())
			{
				perm = group.getPermissions().getPermission(capability);
				if(perm != null)
					break;
			}
			return perm;
		}

		public prisms.arch.Permission[] getAllPermissions()
		{
			java.util.ArrayList<prisms.arch.Permission> ret;
			ret = new java.util.ArrayList<prisms.arch.Permission>();
			for(UserGroup group : theUser.getGroups())
				for(prisms.arch.Permission perm : group.getPermissions().getAllPermissions())
					ret.add(perm);
			return ret.toArray(new prisms.arch.Permission [ret.size()]);
		}

		public Permissions forApp(PrismsApplication app)
		{
			UserAppPermissions ret = theAppPermissions.get(app.getName());
			if(ret != null)
				return ret;
			synchronized(this)
			{
				ret = theAppPermissions.get(app.getName());
				if(ret != null)
					return ret;
				ret = new UserAppPermissions(this, app);
				theAppPermissions.put(app.getName(), ret);
			}
			return ret;
		}
	}

	private static class UserAppPermissions implements Permissions
	{
		private final UserPermissions theRoot;

		private final PrismsApplication theApp;

		UserAppPermissions(UserPermissions root, PrismsApplication app)
		{
			theRoot = root;
			theApp = app;
		}

		public boolean has(String capability)
		{
			for(UserGroup group : theRoot.theUser.getGroups())
			{
				if(group.getApp() != theApp)
					continue;
				if(group.getPermissions().has(capability))
					return true;
			}
			return false;
		}

		public Permission getPermission(String capability)
		{
			prisms.arch.Permission perm = null;
			for(UserGroup group : theRoot.theUser.getGroups())
			{
				if(group.getApp() != theApp)
					continue;
				perm = group.getPermissions().getPermission(capability);
				if(perm != null)
					break;
			}
			return perm;
		}

		public Permission [] getAllPermissions()
		{
			java.util.ArrayList<prisms.arch.Permission> ret;
			ret = new java.util.ArrayList<prisms.arch.Permission>();
			for(UserGroup group : theRoot.theUser.getGroups())
			{
				if(group.getApp() != theApp)
					continue;
				for(prisms.arch.Permission perm : group.getPermissions().getAllPermissions())
					ret.add(perm);
			}
			return ret.toArray(new prisms.arch.Permission [ret.size()]);
		}
	}
}
