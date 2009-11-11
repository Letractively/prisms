/**
 * User.java Created Oct 10, 2007 by Andrew Butler, PSL
 */
package prisms.impl;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.Permissions;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;
import prisms.arch.ds.UserSource;

/**
 * Represents a user of a {@link PrismsApplication}. Allows the application to restrict or deny
 * access based on a user's credentials and verifiability.
 */
public class SimpleUser implements User
{
	private final UserSource theSource;

	private String theName;

	private final PrismsApplication theApp;

	private boolean isEncryptionRequired;

	private prisms.arch.Validator theValidator;

	private Permissions thePermissions;

	private java.util.ArrayList<UserGroup> theGroups;

	private final SimpleUser theRootUser;

	private boolean isLocked;

	/**
	 * Creates a user
	 * 
	 * @param src The UserSource that this user is from
	 * @param name The name of the user
	 * @param app The application that this user is for
	 */
	private SimpleUser(SimpleUser rootUser, UserSource src, String name, PrismsApplication app)
	{
		theSource = src;
		theName = name;
		theApp = app;
		thePermissions = new UserPermissions(this);
		theGroups = new java.util.ArrayList<UserGroup>();
		theRootUser = rootUser;
	}

	/**
	 * Creates a user not attached to a particular application
	 * 
	 * @param src The user source that the user came from
	 * @param name The name of the user
	 */
	public SimpleUser(UserSource src, String name)
	{
		this(null, src, name, null);
	}

	/**
	 * Creates a user for a particular application
	 * 
	 * @param rootUser The non-attached root user
	 * @param app The application that this user is for
	 */
	public SimpleUser(SimpleUser rootUser, PrismsApplication app)
	{
		this(rootUser, rootUser.getSource(), rootUser.getName(), app);
	}

	/**
	 * @return The non-application-attached user that is the root of this user, or null if this user
	 *         is the root
	 */
	public SimpleUser getRootUser()
	{
		return theRootUser;
	}

	/**
	 * @return This user's source
	 */
	public UserSource getSource()
	{
		return theSource;
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

	public PrismsApplication getApp()
	{
		return theApp;
	}

	public boolean isEncryptionRequired()
	{
		return isEncryptionRequired;
	}

	/**
	 * @param e Whether this application user requires encryption for its sessions
	 */
	public void setEncryptionRequired(boolean e)
	{
		isEncryptionRequired = e;
	}

	public prisms.arch.Validator getValidator()
	{
		return theValidator;
	}

	/**
	 * @param validator The validator that this app user requires for validating its sessions
	 */
	public void setValidator(prisms.arch.Validator validator)
	{
		theValidator = validator;
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
	 * @return This user's permissions
	 */
	public Permissions getPermissions()
	{
		return thePermissions;
	}

	public boolean isLocked()
	{
		if(theRootUser != null)
			return theRootUser.isLocked();
		else
			return isLocked;
	}

	public void setLocked(boolean locked)
	{
		if(theRootUser != null)
			theRootUser.setLocked(locked);
		else
			isLocked = locked;
	}

	public String toString()
	{
		return theName;
	}

	public boolean equals(Object o)
	{
		return o instanceof User && ((User) o).getName().equals(theName)
			&& ((User) o).getApp() == theApp;
	}

	public int hashCode()
	{
		int ret = 0;
		if(theApp != null)
			ret += theApp.getName().hashCode() * 17;
		ret += theName.hashCode();
		return ret;
	}

	/*
	 	public SimpleUser clone()
		{
			final SimpleUser ret;
			try
			{
				ret = (SimpleUser) super.clone();
			} catch(CloneNotSupportedException e)
			{
				throw new IllegalStateException("Clone not supported", e);
			}
			ret.theGroups = (java.util.ArrayList<UserGroup>) theGroups.clone();
			ret.thePermissions = new UserPermissions(ret);
			return ret;
		}
		*/

	private static class UserPermissions implements Permissions
	{
		private final User theUser;

		UserPermissions(User user)
		{
			theUser = user;
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
				if(theUser != null)
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
	}
}
