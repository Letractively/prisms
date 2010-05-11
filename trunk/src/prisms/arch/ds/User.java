/**
 * User.java Created Oct 10, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.PrismsApplication;

/**
 * Represents a user of a {@link PrismsApplication}. Allows the application to restrict or deny
 * access based on a user's credentials and verifiability.
 */
public class User
{
	private boolean isAdmin;

	private final UserSource theSource;

	private String theName;

	private UserPermissions thePermissions;

	private java.util.ArrayList<UserGroup> theGroups;

	private boolean isLocked;

	/**
	 * Creates a user
	 * 
	 * @param src The UserSource that this user is from
	 * @param name The name of the user
	 */
	public User(UserSource src, String name)
	{
		theSource = src;
		theName = name;
		thePermissions = new UserPermissions(this);
		theGroups = new java.util.ArrayList<UserGroup>();
	}

	/**
	 * @return This user's source
	 */
	public UserSource getSource()
	{
		return theSource;
	}

	/**
	 * @return Whether this user is an admin (governs his permission changing passwords)
	 */
	public boolean isAdmin()
	{
		return isAdmin;
	}

	/**
	 * @param admin Whether this user is an admin or not
	 */
	protected void setAdmin(boolean admin)
	{
		isAdmin = admin;
	}

	/**
	 * @return This user's name
	 */
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
		theName = name;
	}

	/**
	 * @return The groups this user belongs to
	 */
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
			theGroups.add(group);
	}

	/**
	 * Removes this user from a group
	 * 
	 * @param group The group to remove this user from
	 */
	public void removeFrom(UserGroup group)
	{
		theGroups.remove(group);
	}

	/**
	 * @param app The application to get permissions for
	 * @return This user's permissions
	 */
	public Permissions getPermissions(PrismsApplication app)
	{
		return thePermissions.forApp(app);
	}

	/**
	 * @return Whether this user is locked from creating new sessions
	 */
	public boolean isLocked()
	{
		return isLocked;
	}

	/**
	 * @param locked Whether this user should be locked from creating new sessions
	 */
	public void setLocked(boolean locked)
	{
		isLocked = locked;
	}

	public String toString()
	{
		return theName;
	}

	public boolean equals(Object o)
	{
		return o instanceof User && ((User) o).getName().equals(theName);
	}

	public int hashCode()
	{
		int ret = 0;
		ret += theName.hashCode();
		return ret;
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

		public prisms.arch.ds.Permission getPermission(String capability)
		{
			prisms.arch.ds.Permission perm = null;
			for(UserGroup group : theUser.getGroups())
			{
				perm = group.getPermissions().getPermission(capability);
				if(perm != null)
					break;
			}
			return perm;
		}

		public prisms.arch.ds.Permission[] getAllPermissions()
		{
			java.util.ArrayList<prisms.arch.ds.Permission> ret;
			ret = new java.util.ArrayList<prisms.arch.ds.Permission>();
			for(UserGroup group : theUser.getGroups())
				for(prisms.arch.ds.Permission perm : group.getPermissions().getAllPermissions())
					ret.add(perm);
			return ret.toArray(new prisms.arch.ds.Permission [ret.size()]);
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
			prisms.arch.ds.Permission perm = null;
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
			java.util.ArrayList<prisms.arch.ds.Permission> ret;
			ret = new java.util.ArrayList<prisms.arch.ds.Permission>();
			for(UserGroup group : theRoot.theUser.getGroups())
			{
				if(group.getApp() != theApp)
					continue;
				for(prisms.arch.ds.Permission perm : group.getPermissions().getAllPermissions())
					ret.add(perm);
			}
			return ret.toArray(new prisms.arch.ds.Permission [ret.size()]);
		}
	}
}
