/**
 * ManagerUtils.java Created Aug 4, 2008 by Andrew Butler, PSL
 */
package manager.app;

import prisms.arch.Permission;
import prisms.arch.PrismsException;
import prisms.arch.ds.Permissions;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;
import prisms.records2.RecordsTransaction;
import prisms.records2.SyncRecord;

/** A utility class for determining a user's permissions to edit manager objects */
public class ManagerUtils
{
	private ManagerUtils()
	{
	}

	/**
	 * Creates a transaction to be used for persisting changes caused by events
	 * 
	 * @param session The session in which the event occurred
	 * @param evt The event that occurred
	 * @param userSource The user source for the PRISMS environment
	 * @return The transaction to use for the change
	 */
	public static RecordsTransaction getTransaction(prisms.arch.PrismsSession session,
		prisms.arch.event.PrismsPCE<?> evt, prisms.arch.ds.UserSource userSource)
	{
		if(evt.get("no-prisms-persist") != null)
			return null;
		RecordsTransaction ret = (RecordsTransaction) evt.get("transaction");
		if(ret != null)
			return ret;
		User user = (User) evt.get("trans-user");
		if(user == null && session != null)
			user = session.getUser();
		if(user == null && userSource instanceof prisms.arch.ds.ManageableUserSource)
			try
			{
				user = ((prisms.arch.ds.ManageableUserSource) userSource).getSystemUser();
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not get system user", e);
			}
		if(user == null)
			throw new IllegalStateException("PRISMS data must be changed from inside a session");
		SyncRecord record = (SyncRecord) evt.get("syncRecord");
		boolean shouldRecord = true;
		if(Boolean.TRUE.equals(evt.get("no-persist")))
			shouldRecord = false;
		if(record != null)
			ret = new RecordsTransaction(user, record);
		else
			ret = new RecordsTransaction(user, shouldRecord);
		return ret;
	}

	/**
	 * Creates a transaction to be used for persisting changes caused by events
	 * 
	 * @param session The session in which the event occurred
	 * @param evt The event that occurred
	 * @param userSource The user source for the PRISMS environment
	 * @return The transaction to use for the change
	 */
	public static RecordsTransaction getTransaction(prisms.arch.PrismsSession session,
		prisms.arch.event.PrismsEvent evt, prisms.arch.ds.UserSource userSource)
	{
		if(evt.getProperty("no-prisms-persist") != null)
			return null;
		RecordsTransaction ret = (RecordsTransaction) evt.getProperty("transaction");
		if(ret != null)
			return ret;
		User user = (User) evt.getProperty("trans-user");
		if(user == null && session != null)
			user = session.getUser();
		if(user == null && userSource instanceof prisms.arch.ds.ManageableUserSource)
			try
			{
				user = ((prisms.arch.ds.ManageableUserSource) userSource).getSystemUser();
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not get system user", e);
			}
		if(user == null)
			throw new IllegalStateException("PRISMS data must be changed from inside a session");
		SyncRecord record = (SyncRecord) evt.getProperty("syncRecord");
		boolean shouldRecord = true;
		if(Boolean.TRUE.equals(evt.getProperty("db-persisted")))
			shouldRecord = false;
		if(Boolean.TRUE.equals(evt.getProperty("no-persist")))
			shouldRecord = false;
		evt.setProperty("db-persisted", Boolean.TRUE);
		if(record != null)
			ret = new RecordsTransaction(user, record);
		else
			ret = new RecordsTransaction(user, shouldRecord);
		return ret;
	}

	/**
	 * Checks for the existence of a name in a custom data set
	 */
	public static interface NameChecker
	{
		/**
		 * @param name The name to check
		 * @return Whether the name exists in the data set
		 */
		boolean nameExists(String name);
	}

	/**
	 * Creates a new, unique name for a new item to be inserted into a set of items
	 * 
	 * @param startName The starting name for the item (will be used if no other item with this name
	 *        exists)
	 * @param checker The checker to determine what names already exist in the set of items
	 * @return The new name for the new item
	 */
	public static String newName(String startName, NameChecker checker)
	{
		String ret = "New User";
		if(!checker.nameExists(ret))
			return ret;
		int count = 1;
		while(checker.nameExists(ret + "(" + count + ")"))
			count++;
		return ret + "(" + count + ")";
	}

	/**
	 * Generates a new, unique name for an item that is a copy of an existing named item
	 * 
	 * @param nameToCopy The name of the item that is being copied
	 * @param checker The checker to determine what names already exist in the set of items
	 * @return The new name for the copied item
	 */
	public static String getCopyName(String nameToCopy, NameChecker checker)
	{
		String startName = nameToCopy;
		int copyNum = 0;
		if(nameToCopy.endsWith(" (copy)"))
		{
			startName = nameToCopy.substring(0, nameToCopy.length() - " (copy)".length());
			copyNum = 1;
		}
		else
		{
			java.util.regex.Pattern pattern = java.util.regex.Pattern
				.compile("(.*) \\(copy (\\d*)\\)");
			java.util.regex.Matcher match = pattern.matcher(nameToCopy);
			if(match.find())
			{
				startName = match.group(1);
				copyNum = Integer.parseInt(match.group(2));
			}
		}
		String name;
		if(copyNum == 0)
		{
			name = startName + " (copy)";
			if(!checker.nameExists(name))
				return name;
			copyNum++;
		}
		for(copyNum++; checker.nameExists(startName + " (copy " + copyNum + ")"); copyNum++);
		return startName + " (copy " + copyNum + ")";
	}

	/**
	 * Gets the management level of a user--the lower the number, the more users a user can manage.
	 * 
	 * @param perms The permissions of the user to get the management level for
	 * @return The management level of the user whose permissions are given
	 */
	public static int getManagementLevel(Permissions perms)
	{
		int level = 0;
		for(Permission p : perms.getAllPermissions())
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
	 * @param perms The permissions of the user to determine admin for
	 * @return Whether the user whose permissions are given is an admin user or not
	 */
	public static boolean isAdmin(Permissions perms)
	{
		for(Permission p : perms.getAllPermissions())
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
	 * @param perms The permissions of the user trying to edit the given user
	 * @param user2Perms The permissions of the user to be edited
	 * @return Whether the user with the first given permissions can modify the user with the second
	 */
	public static boolean canEdit(Permissions perms, Permissions user2Perms)
	{
		if(isAdmin(perms))
			return true;
		if(isAdmin(user2Perms))
			return false;

		return getManagementLevel(perms) > getManagementLevel(user2Perms);
	}

	/**
	 * Determines whether a user can edit a group
	 * 
	 * @param perms The permissions of the user trying to edit the group
	 * @param toEdit The group to be edited
	 * @return Whether the user can modify the group
	 */
	public static boolean canEdit(Permissions perms, UserGroup toEdit)
	{
		if(isAdmin(perms))
			return true;
		if(isAdmin(toEdit))
			return false;
		return getManagementLevel(perms) > getManagementLevel(toEdit);
	}

	/**
	 * Determines whether a user can edit a permission
	 * 
	 * @param perms The permissions of the user trying to edit the permission
	 * @param toEdit The permission to be edited
	 * @return Whether the user can modify the permission
	 */
	public static boolean canEdit(Permissions perms, Permission toEdit)
	{
		if(isAdmin(perms))
			return true;
		if(isAdmin(toEdit))
			return false;
		return getManagementLevel(perms) > getManagementLevel(toEdit);
	}
}
