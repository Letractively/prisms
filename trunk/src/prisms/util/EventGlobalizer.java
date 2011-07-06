/**
 * EventGlobalizer.java Created Mar 5, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import prisms.arch.PrismsSession;

/** Globalizes an event, firing it in every other session */
public class EventGlobalizer implements prisms.arch.event.ConfiguredPEL
{
	static int id = 0;

	private prisms.arch.PrismsSession theSession;

	public void configure(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
	}

	public void eventOccurred(PrismsSession session, final prisms.arch.event.PrismsEvent evt)
	{
		if(evt.getProperty("globalEventID") != null
			|| Boolean.TRUE.equals(evt.getProperty("globalEvent")))
			return;
		evt.setProperty("globalEventID", Integer.valueOf(id));
		id++;
		theSession.getApp().runSessionTask(theSession,
			new prisms.arch.PrismsApplication.SessionTask()
			{
				public void run(prisms.arch.PrismsSession session2)
				{
					session2.fireEvent(evt);
				}
			}, true);
	}
}
