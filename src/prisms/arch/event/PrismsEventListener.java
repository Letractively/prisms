/**
 * PrismsEventListener.java Created Aug 1, 2007 by Andrew Butler, PSL
 */
package prisms.arch.event;

import prisms.arch.PrismsSession;

/** Listens for a {@link PrismsEvent} to occur in a {@link PrismsSession} */
public interface PrismsEventListener
{
	/**
	 * Called by the session when the listened-for event occurs
	 * 
	 * @param session The session that the event is being fired in currently
	 * @param evt The event that was fired in the session
	 */
	void eventOccurred(PrismsSession session, PrismsEvent evt);
}
