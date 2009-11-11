/**
 * AppPersister.java Created Jul 10, 2008 by Andrew Butler, PSL
 */
package manager.persisters;

import prisms.arch.ds.User;

/**
 * Retrieves all users from the user source
 */
public class UserPersister extends prisms.util.persisters.AbstractPersister<User []>
{
	/**
	 * @see prisms.arch.Persister#getValue()
	 */
	public User [] getValue()
	{
		User [] users = getApp().getDataSource().getAllUsers();
		return users;
	}

	/**
	 * @see prisms.arch.Persister#setValue(java.lang.Object)
	 */
	public void setValue(User [] o)
	{
		// Changes should not be made directly to a user, but rather through the manager,
		// so persistence is not handled here
	}

	/**
	 * @see prisms.arch.Persister#valueChanged(java.lang.Object, java.lang.Object)
	 */
	public void valueChanged(User [] fullValue, Object o)
	{
		// Changes should not be made directly to a user, but rather through the manager,
		// so persistence is not handled here
	}

	public void reload()
	{
		// No cache to clear
	}
}
