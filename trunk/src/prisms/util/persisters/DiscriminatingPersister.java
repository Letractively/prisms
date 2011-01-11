/*
 * DiscriminatingPersister.java Created Dec 17, 2010 by Andrew Butler, PSL
 */
package prisms.util.persisters;

/**
 * A persister that can discriminate what events apply to its data set
 * 
 * @param <T> The type of item to persist
 */
public interface DiscriminatingPersister<T> extends prisms.arch.Persister<T>
{
	/**
	 * @param evt The event to check
	 * @return Whether the event applies to this persister's data set
	 * @see prisms.arch.event.GlobalPropertyManager#applies(prisms.arch.event.PrismsEvent)
	 */
	boolean applies(prisms.arch.event.PrismsEvent evt);
}
