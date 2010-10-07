/**
 * DBUser.java Created Jun 25, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

/**
 * A database implementation of a user
 */
public class DBUser extends prisms.arch.ds.User
{
	private boolean isDeleted;

	/**
	 * Creates a user
	 * 
	 * @param source The user source that this user is for
	 * @param name The name for the user
	 * @param id The database ID for the user
	 */
	public DBUser(prisms.arch.ds.UserSource source, String name, int id)
	{
		super(source, name, id);
	}

	/**
	 * @return Whether this user is deleted
	 */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}
}
