/**
 * ManageableUserSource.java Created Jun 25, 2008 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.AppConfig;
import prisms.arch.ClientConfig;
import prisms.arch.PrismsApplication;

/**
 * An extension of {@link UserSource} that allows an application to configure users, applications,
 * permissions, etc.
 */
public interface ManageableUserSource extends UserSource
{
	/**
	 * Retrieves all applications from this user source
	 * 
	 * @return All applications available from this user source
	 */
	PrismsApplication [] getAllApps();

	/**
	 * Retrieves all clients for an application in this user source
	 * 
	 * @param app The application to get clients for
	 * @return All clients available for the given application
	 */
	ClientConfig [] getAllClients(PrismsApplication app);

	/**
	 * Creates a new user in the user source
	 * 
	 * @param name The name for the new user
	 * @return The new user
	 */
	User createUser(String name);

	/**
	 * Deletes a user from this user source
	 * 
	 * @param user The user to delete
	 */
	void deleteUser(User user);

	/**
	 * Renames a user in this user source
	 * 
	 * @param user The user to rename
	 * @param newName The new name for the user
	 */
	void rename(User user, String newName);

	/**
	 * Sets a user's access to an application
	 * 
	 * @param user The user to configure the access of
	 * @param app The application to configure the user's access to
	 * @param accessible Whether the given user should be able to access the given application
	 */
	void setUserAccess(User user, PrismsApplication app, boolean accessible);

	/**
	 * Sets whether a user must use encryption to access an application
	 * 
	 * @param user The user to set the encryption requirements of
	 * @param app The application to set the user's encryption requirements to
	 * @param encrypted Whether the given user must use encryption to access the given application
	 */
	void setEncryptionRequired(User user, PrismsApplication app, boolean encrypted);

	/**
	 * Sets the password expiration time of a user
	 * 
	 * @param user The user to set the password expiration for
	 * @param time The password expiration time for the user
	 */
	void setPasswordExpiration(User user, long time);

	/**
	 * Creates a new user group
	 * 
	 * @param name The name for the new group
	 * @param app The application that the new group is for
	 * @return The new group
	 */
	UserGroup createGroup(String name, PrismsApplication app);

	/**
	 * Deletes a group
	 * 
	 * @param group The group to delete
	 */
	void deleteGroup(UserGroup group);

	/**
	 * Renames a group
	 * 
	 * @param group The group to rename
	 * @param newName The new name for the group
	 */
	void rename(UserGroup group, String newName);

	/**
	 * Sets the group's description
	 * 
	 * @param group The group to change the description of
	 * @param descrip The new description for the group
	 */
	void setDescription(UserGroup group, String descrip);

	/**
	 * Sets the permission's description
	 * 
	 * @param group The permission to change the description of
	 * @param descrip The new description for the permission
	 */
	void setDescription(Permission group, String descrip);

	/**
	 * Adds a user to a group, giving that user all the group's associated permissions
	 * 
	 * @param user The user to add to the group
	 * @param group The group to add the user to
	 */
	void addUserToGroup(User user, UserGroup group);

	/**
	 * Removes a user from a group, denying that user all the group's associated permissions
	 * 
	 * @param user The user to remove from the group
	 * @param group The group to remove the user from
	 */
	void removeUserFromGroup(User user, UserGroup group);

	/**
	 * Gets all permissions available within an application
	 * 
	 * @param app The application to get the permissions of
	 * @return All permissions available within the given application
	 */
	Permission [] getPermissions(PrismsApplication app);

	/**
	 * Creates a new permission in this user source
	 * 
	 * @param app The application to create the permission for
	 * @param name The name of the new permission (cannot be changed later with this interface)
	 * @param descrip The initial description for the permission
	 * @return The new permission
	 */
	Permission createPermission(PrismsApplication app, String name, String descrip);

	/**
	 * Deletes a permission from this user source
	 * 
	 * @param permission The permission to delete
	 */
	void deletePermission(Permission permission);

	/**
	 * Adds a permission to a group, giving the permission to all users associated with this group
	 * 
	 * @param group The group to add the permission to
	 * @param permission The permission to add to the group
	 */
	void addPermission(UserGroup group, Permission permission);

	/**
	 * Removes a permission from a group, denying the permission from all users associated with this
	 * group
	 * 
	 * @param group The group to remove the permission from
	 * @param permission The permission to remove from the group
	 */
	void removePermission(UserGroup group, Permission permission);

	/**
	 * Creates a new application in this user source
	 * 
	 * @param name The name of the application
	 * @param descrip The description of the application
	 * @param configClass The {@link AppConfig} implementation to use to configure the new
	 *        application
	 * @param configXML The location of the XML file to use to configure the new application
	 * @return The new application
	 */
	PrismsApplication createApplication(String name, String descrip, String configClass,
		String configXML);

	/**
	 * Renames an application in this user source
	 * 
	 * @param app The application to rename
	 * @param newName The new name for the application
	 */
	void rename(PrismsApplication app, String newName);

	/**
	 * Creates a new client for an application in this user source
	 * 
	 * @param app The application to create the new client for
	 * @param clientName The name of the new client
	 * @return The new client
	 */
	ClientConfig createClient(PrismsApplication app, String clientName);

	/**
	 * Deletes a client from this user source
	 * 
	 * @param client The client to delete
	 */
	void deleteClient(ClientConfig client);

	/**
	 * Adds an administrative group to an application, giving all users associated with the group
	 * permission to modify the application
	 * 
	 * @param app The application to add an administrative group to
	 * @param group The group to allow administrative priveleges on the application
	 */
	void addAdminGroup(PrismsApplication app, UserGroup group);

	/**
	 * Removes an administrative group from an application, denying all users associated with the
	 * group permission to modify the application
	 * 
	 * @param app The application to remove an adminstrative group from
	 * @param group The group to deny administrative priveleges on the application
	 */
	void removeAdminGroup(PrismsApplication app, UserGroup group);
}
