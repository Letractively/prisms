/**
 * SessionMonitor.java Created Feb 20, 2008 by Andrew Butler, PSL
 */
package prisms.arch.event;

import prisms.arch.PrismsSession;

/**
 * A SessionMonitor monitors a session for implementation-specific changes (events) and performs
 * appropriate actions. This generic mechanism allows easy insertion of business logic into the
 * application.
 */
public interface SessionMonitor
{
	/**
	 * Registers the monitor with the session. This method adds all necessary listeners and performs
	 * all necessary setup work on the session.
	 * 
	 * @param session The session to set up and monitor
	 * @param configEl The XML element to configure this monitor with
	 */
	void register(PrismsSession session, org.dom4j.Element configEl);
}
