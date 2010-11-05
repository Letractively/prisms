/*
 * UserEventGlobalizer.java Created Aug 6, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import prisms.arch.PrismsSession;

/**
 * Globalizes events that are specific to a user. When an event occurs, this listener broadcasts it
 * to every other session in the application open to the same user name.
 */
public class UserEventGlobalizer implements prisms.arch.event.ConfiguredPEL
{
	static int id = 0;

	prisms.arch.PrismsSession theSession;

	public void configure(prisms.arch.PrismsSession session, org.dom4j.Element configEl)
	{
		theSession = session;
	}

	public void eventOccurred(PrismsSession session, final prisms.arch.event.PrismsEvent evt)
	{
		if(evt.getProperty("globalEventID") != null)
			return;
		evt.setProperty("globalEventID", new Integer(id));
		id++;
		theSession.getApp().runSessionTask(theSession,
			new prisms.arch.PrismsApplication.SessionTask()
			{
				public void run(prisms.arch.PrismsSession session2)
				{
					if(session2.getUser().equals(theSession.getUser()))
						session2.fireEvent(evt);
				}
			}, true);
	}
}
