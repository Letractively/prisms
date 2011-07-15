/*
 * ManagerMonitor.java Created Apr 27, 2011 by Andrew Butler, PSL
 */
package manager.app;

/** Keeps some manager properties in sync */
public class ManagerMonitor implements prisms.arch.event.SessionMonitor
{
	public void register(final prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		session.addPropertyChangeListener(ManagerProperties.selectedApp,
			new prisms.arch.event.PrismsPCL<prisms.arch.PrismsApplication>()
			{
				public void propertyChange(
					prisms.arch.event.PrismsPCE<prisms.arch.PrismsApplication> evt)
				{ // Set selectedClient to null when the selectedApp property changes
					prisms.arch.ClientConfig client = session
						.getProperty(ManagerProperties.selectedClient);
					if(client != null && client.getApp() != evt.getNewValue())
						session.setProperty(ManagerProperties.selectedClient, null);
				}

				@Override
				public String toString()
				{
					return "Manager AppChange Client Clearer";
				}
			});
		session.addPropertyChangeListener(ManagerProperties.selectedClient,
			new prisms.arch.event.PrismsPCL<prisms.arch.ClientConfig>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<prisms.arch.ClientConfig> evt)
				{
					if(evt.getNewValue() != null
						&& evt.getNewValue().getApp() != session
							.getProperty(ManagerProperties.selectedApp))
						session.setProperty(ManagerProperties.selectedApp, evt.getNewValue()
							.getApp());
				}

				@Override
				public String toString()
				{
					return "Manager ClientChange App Setter";
				}
			});
		session.addPropertyChangeListener(ManagerProperties.userApplication,
			new prisms.arch.event.PrismsPCL<prisms.arch.PrismsApplication>()
			{
				public void propertyChange(
					prisms.arch.event.PrismsPCE<prisms.arch.PrismsApplication> evt)
				{
					session.setProperty(ManagerProperties.userSelectedGroup, null);
				}

				@Override
				public String toString()
				{
					return "Manager UserAppChange Group Clearer";
				}
			});
		session.addPropertyChangeListener(ManagerProperties.userSelectedGroup,
			new prisms.arch.event.PrismsPCL<prisms.arch.ds.UserGroup>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<prisms.arch.ds.UserGroup> evt)
				{
					session.setProperty(ManagerProperties.userSelectedPermission, null);
				}

				@Override
				public String toString()
				{
					return "Manager UserGroup Change Permission Clearer";
				}
			});
	}
}
