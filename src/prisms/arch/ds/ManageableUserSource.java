/**
 * ManageableUserSource.java Created Jun 25, 2008 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.ClientConfig;
import prisms.arch.PrismsApplication;
import prisms.arch.PrismsException;

/**
 * An extension of {@link UserSource} that allows an application to configure users, applications,
 * permissions, etc.
 */
public interface ManageableUserSource extends UserSource
{
	/**
	 * Retrieves all user groups that apply to an application
	 * 
	 * @param app The application to get the groups of
	 * @return All groups in this user source for the given application
	 * @throws PrismsException If an error occurs getting the data
	 */
	UserGroup [] getGroups(PrismsApplication app) throws PrismsException;

	/**
	 * Retrieves all applications from this user source
	 * 
	 * @return All applications available from this user source
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	PrismsApplication [] getAllApps() throws PrismsException;

	/**
	 * Retrieves all clients for an application in this user source
	 * 
	 * @param app The application to get clients for
	 * @return All clients available for the given application
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	ClientConfig [] getAllClients(PrismsApplication app) throws PrismsException;

	/**
	 * Gets all permissions available within an application
	 * 
	 * @param app The application to get the permissions of
	 * @return All permissions available within the given application
	 * @throws PrismsException If an error occurs retrieving the data
	 * @throws PrismsException If an error occurs accessing the data
	 */
	Permission [] getAllPermissions(PrismsApplication app) throws PrismsException;

	/**
	 * Creates a new user in the user source
	 * 
	 * @param name The name for the new user
	 * @return The new user
	 * @throws PrismsException If an error occurs accessing the data
	 */
	User createUser(String name) throws PrismsException;

	/**
	 * Modifies a user. This method is a catch-all for any kind of legal modification to a user,
	 * including name, group membership, etc.
	 * 
	 * @param user The user to modify
	 * @throws PrismsException If an error occurs modifying the user
	 */
	void putUser(User user) throws PrismsException;

	/**
	 * Deletes a user from this user source
	 * 
	 * @param user The user to delete
	 * @throws PrismsException If an error occurs accessing the data
	 */
	void deleteUser(User user) throws PrismsException;

	/**
	 * Sets the password expiration time of a user
	 * 
	 * @param user The user to set the password expiration for
	 * @param time The password expiration time for the user
	 * @throws PrismsException If an error occurs accessing the data
	 */
	void setPasswordExpiration(User user, long time) throws PrismsException;

	/**
	 * Controls whether a user can access an application
	 * 
	 * @param user The user to determine access for
	 * @param app The application to determine access for
	 * @param accessible Whether the user should be able to access the application
	 * @throws PrismsException If an error occurs setting the data
	 */
	void setUserAccess(User user, PrismsApplication app, boolean accessible) throws PrismsException;

	/**
	 * Creates a new user group
	 * 
	 * @param app The application that the new group is for
	 * @param name The name for the new group
	 * @return The new group
	 * @throws PrismsException If an error occurs accessing the data
	 */
	UserGroup createGroup(PrismsApplication app, String name) throws PrismsException;

	/**
	 * Modifies a group. This method is a catch-all for any kind of legal modification to a user,
	 * including name, description, permissions, etc.
	 * 
	 * @param group The group to modify
	 * @throws PrismsException If an error occurs modifying the group
	 */
	void putGroup(UserGroup group) throws PrismsException;

	/**
	 * Deletes a group
	 * 
	 * @param group The group to delete
	 * @throws PrismsException If an error occurs accessing the data
	 */
	void deleteGroup(UserGroup group) throws PrismsException;

	/**
	 * Creates a new permission in this user source
	 * 
	 * @param app The application to create the permission for
	 * @param name The name of the new permission (cannot be changed later with this interface)
	 * @param descrip The initial description for the permission
	 * @return The new permission
	 * @throws PrismsException If an error occurs accessing the data
	 */
	Permission createPermission(PrismsApplication app, String name, String descrip)
		throws PrismsException;

	/**
	 * Changes certain features of a permission. This method cannot change a permissions name.
	 * 
	 * @param perm The permission to modify
	 * @throws PrismsException If an error occurs accessing the data
	 */
	void putPermission(Permission perm) throws PrismsException;

	/**
	 * Deletes a permission from this user source
	 * 
	 * @param permission The permission to delete
	 * @throws PrismsException If an error occurs accessing the data
	 */
	void deletePermission(Permission permission) throws PrismsException;

	/**
	 * Creates a new application in this user source
	 * 
	 * @param name The name of the application
	 * @return The new application
	 * @throws PrismsException If an error occurs accessing the data
	 */
	PrismsApplication createApplication(String name) throws PrismsException;

	/**
	 * Modifies an application. This method is a catch-all for any legal modification to an
	 * application, including name, description, configuration, and admin groups.
	 * 
	 * @param app The application to modify
	 * @throws PrismsException If an error occurs modifying the application
	 */
	void putApplication(PrismsApplication app) throws PrismsException;

	/**
	 * Deletes an application from this user source
	 * 
	 * @param app The application to delete
	 * @throws PrismsException If an error occurs accessing the data
	 */
	void deleteApplication(PrismsApplication app) throws PrismsException;

	/**
	 * Creates a new client for an application in this user source
	 * 
	 * @param app The application to create the new client for
	 * @param clientName The name of the new client
	 * @return The new client
	 * @throws PrismsException If an error occurs accessing the data
	 */
	ClientConfig createClient(PrismsApplication app, String clientName) throws PrismsException;

	/**
	 * Modifies an client configuration. This method is a catch-all for any legal modification to an
	 * application, including name, description, and configuration data
	 * 
	 * @param client The client config to modify
	 * @throws PrismsException If an error occurs modifying the client config
	 */
	void putClient(ClientConfig client) throws PrismsException;

	/**
	 * Deletes a client from this user source
	 * 
	 * @param client The client to delete
	 * @throws PrismsException If an error occurs accessing the data
	 */
	void deleteClient(ClientConfig client) throws PrismsException;
}
