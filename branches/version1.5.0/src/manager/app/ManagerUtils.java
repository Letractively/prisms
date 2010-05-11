/**
 * ManagerUtils.java Created Aug 4, 2008 by Andrew Butler, PSL
 */
package manager.app;

import prisms.arch.ds.Permission;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;

/**
 * A utility class for determining a user's permissions to edit manager objects
 */
public class ManagerUtils
{
	private ManagerUtils()
	{
	}

	/**
	 * Gets the management level of a user--the lower the number, the more users a user can manage.
	 * 
	 * @param user The user to get the management level of
	 * @return The user's management level
	 */
	public static int getManagementLevel(User user)
	{
		int level = 0;
		for(Permission p : user.getPermissions().getAllPermissions())
		{
			int levelTemp = getManagementLevel(p);
			if(levelTemp > level)
				level = levelTemp;
		}
		return level;
	}

	/**
	 * Determines whether a user is an "admin" user or not. An admin user can edit almost anything
	 * in the Manager application without needing specific permission or management levels.
	 * 
	 * @param user The user to determine whether he/she is an admin
	 * @return Whether the user is an admin user or not
	 */
	public static boolean isAdmin(User user)
	{
		for(Permission p : user.getPermissions().getAllPermissions())
			if(isAdmin(p))
				return true;
		return false;
	}

	/**
	 * Gets the management level of a group--the lower the number, the more users those users
	 * assigned to the group can manage.
	 * 
	 * @param group The group to get the management level of
	 * @return The group's management level
	 */
	public static int getManagementLevel(UserGroup group)
	{
		if(group.getName().startsWith("userManager"))
			return Integer.parseInt(group.getName().substring("userManager".length()));
		else
			return 0;
	}

	/**
	 * Gets the management level of a permission--the lower the number, the more users those users
	 * with the permission can manage.
	 * 
	 * @param p The permission to get the management level of
	 * @return The permission's management level
	 */
	public static int getManagementLevel(Permission p)
	{
		if(p.getName().startsWith("userManager"))
			return Integer.parseInt(p.getName().substring("userManager".length()));
		else
			return 0;
	}

	/**
	 * Determines whether a group is an "admin" group or not. Users in an admin group can edit
	 * almost anything in the Manager application without needing specific permission or management
	 * levels.
	 * 
	 * @param group The group to determine whether users in the group are admin
	 * @return Whether the group is an admin group or not
	 */
	public static boolean isAdmin(UserGroup group)
	{
		for(Permission p : group.getPermissions().getAllPermissions())
			if(isAdmin(p))
				return true;
		return false;
	}

	/**
	 * Determines whether a permission is an "admin" permission or not. Users with an admin
	 * permission can edit almost anything in the Manager application without needing specific
	 * permission or management levels.
	 * 
	 * @param p The permission to determine whether users with the permission are admin
	 * @return Whether the permission is an admin permission or not
	 */
	public static boolean isAdmin(Permission p)
	{
		return p.getName().equals("userAdmin");
	}

	/**
	 * Determines whether a user can edit another user
	 * 
	 * @param manager The manager user (the one accessing the application)
	 * @param toEdit The user to be edited
	 * @return Whether the first user can modify the second
	 */
	public static boolean canEdit(User manager, User toEdit)
	{
		if(isAdmin(manager))
			return true;
		if(isAdmin(toEdit))
			return false;

		return getManagementLevel(manager) > getManagementLevel(toEdit);
	}

	/**
	 * Determines whether a user can edit a group
	 * 
	 * @param manager The manager user (the one accessing the application)
	 * @param toEdit The group to be edited
	 * @return Whether the user can modify the group
	 */
	public static boolean canEdit(User manager, UserGroup toEdit)
	{
		if(isAdmin(manager))
			return true;
		if(isAdmin(toEdit))
			return false;
		return getManagementLevel(manager) > getManagementLevel(toEdit);
	}

	/**
	 * Determines whether a user can edit an application
	 * 
	 * @param manager The manager user (the one accessing the application)
	 * @param app The application to be edited
	 * @return Whether the user can modify the application
	 */
	public static boolean canEdit(User manager, prisms.arch.PrismsApplication app)
	{
		if(isAdmin(manager))
			return true;
		UserGroup [] userGroups = manager.getGroups();
		UserGroup [] adminGroups = app.getAdminGroups();
		return prisms.util.ArrayUtils.mergeExclusive(UserGroup.class, userGroups, adminGroups).length > 0;
	}

	/**
	 * Determines whether a user can edit a permission
	 * 
	 * @param manager The manager user (the one accessing the application)
	 * @param toEdit The permission to be edited
	 * @return Whether the user can modify the permission
	 */
	public static boolean canEdit(User manager, Permission toEdit)
	{
		if(isAdmin(manager))
			return true;
		if(isAdmin(toEdit))
			return false;
		return getManagementLevel(manager) > getManagementLevel(toEdit);
	}
}
