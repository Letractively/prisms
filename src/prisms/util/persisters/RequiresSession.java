/*
 * RequiresSession.java Created Dec 14, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

/**
 * To be tagged on to a persister. This interface specifies that the persister requires a session
 * for modifications.
 */
public interface RequiresSession
{
	/**
	 * This method will be called with a non-null session immediately before invocations to the
	 * {@link prisms.arch.Persister#setValue(Object)} or
	 * {@link prisms.arch.Persister#valueChanged(Object, Object)} methods.
	 * 
	 * @param session The session that is about to affect a change
	 */
	public void setSession(prisms.arch.PrismsSession session);
}
