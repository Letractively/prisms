/*
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
	 * @param config The configuration to configure this monitor with
	 */
	void register(PrismsSession session, prisms.arch.PrismsConfig config);
}
