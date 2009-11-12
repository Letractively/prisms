/**
 * UserSpecificPersister.java Created Jul 8, 2008 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.Persister;

/**
 * An interface for persisters that have information that varies by user
 * 
 * @param <T> The type of object to persist
 */
public interface UserSpecificPersister<T> extends Persister<java.util.Map<String, T>>
{
	/**
	 * Creates a user-specific data set by user
	 * 
	 * @param user The user to create the data set for
	 * @return The new user-specific data set
	 */
	T create(prisms.arch.ds.User user);
}
