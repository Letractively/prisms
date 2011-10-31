/*
 * UserSource.java Created Oct 31, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsException;

/** Provides access to a set of users */
public interface UserSource
{
	/** Represents a password set for a user */
	public static class Password
	{
		/** The primary-hash of the password value. See {@link Hashing#partialHash(String)} */
		public final long [] hash;

		/** The time at which the password was set */
		public final long setTime;

		/**
		 * The time at which the password will or has expired. May be -1 for a password that does
		 * not expire
		 */
		public final long expire;

		/**
		 * Creates a password
		 * 
		 * @param h The hashed password
		 * @param time The time the password was set
		 * @param ex The expire time for the password
		 */
		public Password(long [] h, long time, long ex)
		{
			hash = h;
			setTime = time;
			expire = ex;
		}
	}

	/** Status of an application in the PRISMS environment */
	public static class ApplicationStatus
	{
		/** The application lock applying to the application */
		public final PrismsApplication.ApplicationLock lock;

		/** The ID of the most recent command to reload the application's properties */
		public final int reloadPropsCommand;

		/** The ID of the most recent command to reload the application's sessions */
		public final int reloadSessionsCommand;

		/**
		 * @param aLock The lock for the application
		 * @param reloadProps The command to reload application properties
		 * @param reloadSessions The command to reload application sessions
		 */
		public ApplicationStatus(PrismsApplication.ApplicationLock aLock, int reloadProps,
			int reloadSessions)
		{
			lock = aLock;
			reloadPropsCommand = reloadProps;
			reloadSessionsCommand = reloadSessions;
		}
	}

	/**
	 * Sets this user source's data source
	 * 
	 * @param configEl The configuration to configure this data source with
	 * @param env The PRISMS environment that this user source will be used in
	 * @param apps All applications configured in the PRISMS environment
	 * @param initHashing The hashing values to use for an initial installation--may be null
	 * @throws PrismsException If the user source could not be configured correctly
	 */
	void configure(prisms.arch.PrismsConfig configEl, prisms.arch.PrismsEnv env,
		PrismsApplication [] apps, Hashing initHashing) throws PrismsException;

	/**
	 * @param app The application to get the lock for
	 * @return The lock on the application, or null if the application is not locked
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	ApplicationStatus getApplicationStatus(PrismsApplication app) throws PrismsException;

	/**
	 * Sets or releases the lock on an application
	 * 
	 * @param app The application to lock or unlock
	 * @param lock The lock to set for the application
	 * @throws PrismsException If an error occurs setting the lock
	 */
	void setApplicationLock(PrismsApplication app, PrismsApplication.ApplicationLock lock)
		throws PrismsException;

	/**
	 * Causes the given application to reload its properties on all other servers in the enterprise
	 * 
	 * @param app The application to reload the properties of
	 * @throws PrismsException If an error occurs propagating the command
	 */
	void reloadProperties(PrismsApplication app) throws PrismsException;

	/**
	 * Causes the given application to reload its sessions on all other servers in the enterprise
	 * 
	 * @param app The application to reload the sessions of
	 * @throws PrismsException If an error occurs propagating the command
	 */
	void reloadSessions(PrismsApplication app) throws PrismsException;

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
	 * @return The system user that can be used in record-keeping when an operation is not caused
	 *         directly by an actual user
	 * @throws PrismsException If an error occurs getting the system user
	 */
	User getSystemUser() throws PrismsException;

	/**
	 * @param user The user requesting access to the application
	 * @param app The application the user is requesting access to
	 * @return Whether the user has permission to access the given application
	 * @throws PrismsException If an error occurs accessing the data
	 */
	boolean canAccess(User user, PrismsApplication app) throws PrismsException;

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
	Password getPassword(User user) throws PrismsException;

	/**
	 * Gets all a user's stored passwords, sorted from the most recently set. The first element of
	 * the return value should be the user's current password.
	 * 
	 * @param user The user to get the passwords for
	 * @param hashing The hashing to use to interpret the passwords from the raw data
	 * @return All a user's passwords
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	Password [] getOldPasswords(User user) throws PrismsException;

	/**
	 * Sets a user's password
	 * 
	 * @param user The user to set the password for
	 * @param hash The hashed password information
	 * @param isAdmin Whether this password change is being performed by an admin user
	 * @return The password set as a result of this call
	 * @throws PrismsException If an error occurs writing the data
	 */
	Password setPassword(User user, long [] hash, boolean isAdmin) throws PrismsException;

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
