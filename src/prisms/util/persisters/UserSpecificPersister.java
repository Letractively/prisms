/*
 * UserSpecificPersister.java Created Jul 8, 2008 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.PrismsSession;

/**
 * An interface for persisters that have information that varies by user
 * 
 * @param <T> The type of object to persist
 */
public interface UserSpecificPersister<T>
{
	/**
	 * Configures this persister
	 * 
	 * @param config The configuration for this persister
	 * @param env The PRISMS environment to use configure this persister
	 * @param property The propert that this persister will persist values for
	 */
	void configure(prisms.arch.PrismsConfig config, prisms.arch.PrismsEnv env,
		prisms.arch.event.PrismsProperty<? super T> property);

	/**
	 * Gets or creates a user-specific data set by user
	 * 
	 * @param session The session to create the data set for
	 * @return The new user-specific data set
	 */
	T getValue(PrismsSession session);

	/**
	 * 
	 * @param <V> The actual type of the value
	 * @param session The session that the value has changed in
	 * @param value The new value to persist
	 * @param evt The event that caused the change
	 */
	<V extends T> void setValue(PrismsSession session, V value,
		prisms.arch.event.PrismsPCE<? extends T> evt);
}
