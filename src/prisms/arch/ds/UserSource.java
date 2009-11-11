/**
 * UserSource.java Created Oct 31, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.ClientConfig;
import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.arch.PersisterFactory;

/**
 * Provides access to a set of users
 */
public interface UserSource
{
	/**
	 * Sets this user source's data source
	 * 
	 * @param configEl The XML element to configure this data source with
	 * @param factory The PersisterFactory to use to get connection information
	 */
	void configure(org.dom4j.Element configEl, PersisterFactory factory);

	/**
	 * Gets a user based on his/her id
	 * 
	 * @param name The id of the user to get
	 * @return The user with the specified id, or null if no such user exists
	 */
	User getUser(String name);

	/**
	 * Gets a specialized user for a given app with the correct permissions settings
	 * 
	 * @param serverUser The validated user on the server
	 * @param app The application to get the user for
	 * @return A user with the same name as <code>serverUser</code>, but with permissions
	 *         specialized for the given application
	 */
	User getUser(User serverUser, PrismsApplication app);

	/**
	 * Retrieves all users from this user source
	 * 
	 * @return All users available from this user source
	 */
	User [] getAllUsers();

	/**
	 * Checks to see when a user's password expires and must be reset
	 * 
	 * @param user The user whose password to check for expiration
	 * @return The given user's password expiration time
	 */
	long getPasswordExpiration(User user);

	/**
	 * Sets a user's password
	 * 
	 * @param user The user to set the password for
	 * @param hash The hashed password information
	 */
	void setPassword(User user, long [] hash);

	/**
	 * @param name The name of the application to get
	 * @return The stored application with the given name
	 */
	PrismsApplication getApp(String name);

	/**
	 * @param app The application for the configuration to retrieve
	 * @param name The name of the client configuration to retrieve
	 * @return The client configuration of the given application and name
	 */
	ClientConfig getClient(PrismsApplication app, String name);

	/**
	 * Creates a new session for an application
	 * 
	 * @param client The client configuration to create the session for
	 * @param user The user to create the session for
	 * @param asService Whether the new session is to be creates as an M2M client as opposed to as a
	 *        user interface client
	 * @return The new session to use
	 */
	PrismsSession createSession(ClientConfig client, User user, boolean asService);

	/**
	 * Retrieves a user group
	 * 
	 * @param app The application that the group applies to
	 * @param groupName The name of the group to get
	 * @return The specified group, or null if no such group exists
	 */
	UserGroup getGroup(PrismsApplication app, String groupName);

	/**
	 * Retrieves all user groups that apply to an application
	 * 
	 * @param app The application to get the groups of
	 * @return All groups in this user source for the given application
	 */
	UserGroup [] getGroups(PrismsApplication app);

	/**
	 * Gets a set of password hashing data to generate an encryption key from
	 * 
	 * @return A partially randomized set of hashing data.
	 */
	Hashing getHashing();

	/**
	 * Generates an encryption key for a user from a set of hashing data
	 * 
	 * @param user The user to get an encryption key for
	 * @param hashing The hashing data to generate the key from
	 * @return An encryption key
	 */
	String getKey(User user, Hashing hashing);

	/**
	 * Disposes of this data source's resources
	 */
	void disconnect();
}
