/**
 * EventGlobalizer.java Created Mar 5, 2009 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * Globalizes an event, firing it in every other session
 */
public class EventGlobalizer implements prisms.arch.event.ConfiguredPEL
{
	static int id = 0;

	private prisms.arch.PrismsSession theSession;

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
		if(evt.getProperty("globalEventID") != null
			|| Boolean.TRUE.equals(evt.getProperty("globalEvent")))
			return;
		evt.setProperty("globalEventID", new Integer(id));
		id++;
		theSession.getApp().runSessionTask(theSession,
			new prisms.arch.PrismsApplication.SessionTask()
			{
				public void run(prisms.arch.PrismsSession session)
				{
					session.fireEvent(evt);
				}
			}, true);
	}
}
