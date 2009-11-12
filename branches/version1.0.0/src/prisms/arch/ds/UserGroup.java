/**
 * UserGroup.java Created Dec 19, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.PrismsApplication;

/**
 * A group that a user can belong to that gives the user permissions
 */
public interface UserGroup
{
	/**
	 * @return This user's source
	 */
	public UserSource getSource();

	/**
	 * @return This user's name
	 */
	public String getName();

	/**
	 * @return The name of the application that this group applies to
	 */
	public PrismsApplication getApp();

	/**
	 * @return The group's description
	 */
	public String getDescription();

	/**
	 * @return This user's permissions
	 */
	public Permissions getPermissions();
}
