/*
 * ManagerMonitor.java Created May 18, 2010 by Andrew Butler, PSL
 */
package manager.app;

import org.apache.log4j.Logger;

/**
 * The ManagerMonitor checks every 15 seconds for changes to the user set in the data source. This
 * may be modified outside the manager application in the case of a trusted server where users are
 * auto-created.
 */
public class ManagerMonitor implements prisms.arch.event.SessionMonitor
{
	static final Logger log = Logger.getLogger(ManagerMonitor.class);

	public void register(final prisms.arch.PrismsSession session, org.dom4j.Element configEl)
	{
		session.getApp().scheduleRecurringTask(new Runnable()
		{
			public void run()
			{
				prisms.arch.ds.User[] users;
				try
				{
					users = session.getApp().getDataSource().getAllUsers();
				} catch(prisms.arch.PrismsException e)
				{
					log.error("Could not get users", e);
					return;
				}
				if(!prisms.util.ArrayUtils.equalsUnordered(users, session
					.getProperty(ManagerProperties.users)))
					session.setProperty(ManagerProperties.users, users);
			}
		}, 15000);
	}
}
