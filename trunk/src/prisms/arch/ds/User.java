/**
 * User.java Created Oct 10, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.PrismsApplication;
import prisms.arch.Validator;

/**
 * Represents a user of a {@link PrismsApplication}. Allows the application to restrict or deny
 * access based on a user's credentials and verifiability.
 */
public interface User
{
	/**
	 * @return This user's source
	 */
	UserSource getSource();

	/**
	 * @return Whether this user is an admin (governs his permission changing passwords)
	 */
	boolean isAdmin();

	/**
	 * @return This user's name
	 */
	String getName();

	/**
	 * @return The application this user was created for, or null if this user is
	 *         application-independent
	 */
	PrismsApplication getApp();

	/**
	 * @return The groups this user belongs to
	 */
	UserGroup [] getGroups();

	/**
	 * @return This user's permissions
	 */
	Permissions getPermissions();

	/**
	 * @return Whether sessions of this application user must use encryption to transfer data. Only
	 *         applies to application users (where {@link #getApp()}!=null).
	 */
	boolean isEncryptionRequired();

	/**
	 * @return A validator required to validate sessons of this application user. Only applies to
	 *         application users (where {@link #getApp()}!=null).
	 */
	Validator getValidator();

	/**
	 * @return Whether this user is locked from creating new sessions
	 */
	boolean isLocked();

	/**
	 * @param locked Whether this user should be locked from creating new sessions
	 */
	void setLocked(boolean locked);
}
