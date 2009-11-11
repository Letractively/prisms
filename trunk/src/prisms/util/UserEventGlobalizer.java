/*
 * UserEventGlobalizer.java Created Aug 6, 2009 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * Globalizes events that are specific to a user. When an event occurs, this listener broadcasts it
 * to every other session in the application open to the same user name.
 */
public class UserEventGlobalizer implements prisms.arch.event.ConfiguredPEL
{
	static int id = 0;

	prisms.arch.PrismsSession theSession;

	/**
	 * @see prisms.arch.event.ConfiguredPEL#configure(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void configure(prisms.arch.PrismsSession session, org.dom4j.Element configEl)
	{
		theSession = session;
	}

	/**
	 * @see prisms.arch.event.PrismsEventListener#eventOccurred(prisms.arch.event.PrismsEvent)
	 */
	public void eventOccurred(final prisms.arch.event.PrismsEvent evt)
	{
		if(evt.getProperty("globalEventID") != null)
			return;
		evt.setProperty("globalEventID", new Integer(id));
		id++;
		theSession.getApp().runSessionTask(theSession,
			new prisms.arch.PrismsApplication.SessionTask()
			{
				public void run(prisms.arch.PrismsSession session)
				{
					if(session.getUser().equals(theSession.getUser()))
						session.fireEvent(evt);
				}
			}, true);
	}
}
