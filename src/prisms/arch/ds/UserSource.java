/**
 * UserSource.java Created Oct 31, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.PrismsException;

/** Provides access to a set of users */
public interface UserSource
{
	/**
	 * Sets this user source's data source
	 * 
	 * @param configEl The XML element to configure this data source with
	 * @param env The PRISMS environment that this user source will be used in
	 * @param apps All applications configured in the PRISMS environment
	 * @throws PrismsException If the user source could not be configured correctly
	 */
	void configure(org.dom4j.Element configEl, prisms.arch.PrismsEnv env,
		prisms.arch.PrismsApplication[] apps) throws PrismsException;

	/** @return The ID generator used to configure IDs in this PRISMS environment */
	prisms.arch.ds.IDGenerator getIDs();

	/**
	 * @return The PRISMS user source's password constraints
	 * @throws PrismsException If an error occurs getting the data
	 */
	PasswordConstraints getPasswordConstraints() throws PrismsException;

	/**
	 * Gets a user based on his/her user name
	 * 
	 * @param name The name of the user to get
	 * @return The user with the specified name, or null if no such user exists
	 * @throws PrismsException If an error occurs getting the data
	 */
	User getUser(String name) throws PrismsException;

	/**
	 * Gets a user based on his/her id
	 * 
	 * @param id The name of the id to get
	 * @return The user with the specified id, or null if no such user exists
	 * @throws PrismsException If an error occurs getting the data
	 */
	User getUser(long id) throws PrismsException;

	/**
	 * @param user The user requesting access to the application
	 * @param app The application the user is requesting access to
	 * @return Whether the user has permission to access the given application
	 * @throws PrismsException If an error occurs accessing the data
	 */
	boolean canAccess(User user, prisms.arch.PrismsApplication app) throws PrismsException;

	/**
	 * Gets a set of password hashing data to generate an encryption key from
	 * 
	 * @return A partially randomized set of hashing data.
	 * @throws PrismsException If an error occurs getting the data
	 */
	Hashing getHashing() throws PrismsException;

	/**
	 * Generates an encryption key for a user from a set of hashing data
	 * 
	 * @param user The user to get an encryption key for
	 * @param hashing The hashing data to generate the key from
	 * @return An encryption key
	 * @throws PrismsException If an error occurs getting the data
	 */
	long [] getKey(User user, Hashing hashing) throws PrismsException;

	/**
	 * Sets a user's password
	 * 
	 * @param user The user to set the password for
	 * @param hash The hashed password information
	 * @param isAdmin Whether this password change is being performed by an admin user
	 * @throws PrismsException If an error occurs writing the data
	 */
	void setPassword(User user, long [] hash, boolean isAdmin) throws PrismsException;

	/**
	 * Checks to see when a user's password expires and must be reset
	 * 
	 * @param user The user whose password to check for expiration
	 * @return The given user's password expiration time
	 * @throws PrismsException If an error occurs getting the data
	 */
	long getPasswordExpiration(User user) throws PrismsException;

	/**
	 * Locks a user so that all current sessions using this login are disabled and no more may be
	 * created until the user is unlocked.
	 * 
	 * @param user The user to lock
	 * @throws PrismsException If an error occurs writing the data
	 */
	void lockUser(User user) throws PrismsException;

	/**
	 * Retrieves all users from this user source that are local and visible
	 * 
	 * @return All users available from this user source
	 * @throws PrismsException If an error occurs getting the data
	 */
	User [] getActiveUsers() throws PrismsException;

	/**
	 * Allows this user source to veto the otherwise inevitable accessto the given client by the
	 * given user. This should be an extremely fast operation.
	 * 
	 * @param user The user that is attempting to access the client
	 * @param config The client config that the user is attempting to access
	 * @throws PrismsException If the given user should not be allowed to access the given client.
	 *         The message in the exception will be returned to the user to explain why access was
	 *         denied, so this message should be as descriptive as possible.
	 */
	void assertAccessible(User user, prisms.arch.ClientConfig config) throws PrismsException;

	/** Disposes of this data source's resources */
	void disconnect();
}
