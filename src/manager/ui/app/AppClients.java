/**
 * AppClients.java Created Oct 1, 2008 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;
import prisms.arch.ClientConfig;
import prisms.arch.PrismsApplication;

/** Allows the user to view and manage all clients for an application */
public class AppClients extends prisms.ui.list.SelectableList<ClientConfig>
{
	PrismsApplication theApp;

	@Override
	public void initPlugin(prisms.arch.PrismsSession session, org.dom4j.Element pluginEl)
	{
		super.initPlugin(session, pluginEl);
		setDisplaySelectedOnly(false);
		setSelectionMode(SelectionMode.SINGLE);
		theApp = getSession().getProperty(ManagerProperties.selectedApp);
		if(theApp != null)
			setListData(theApp.getClients());
		session.addPropertyChangeListener(ManagerProperties.selectedApp,
			new prisms.arch.event.PrismsPCL<PrismsApplication>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication> evt)
				{
					PrismsApplication app = evt.getNewValue();
					theApp = app;
					if(app != null)
						setListData(app.getClients());
					else
						setListData(new ClientConfig [0]);
					setListParams();
				}
			});
	}

	@Override
	public boolean canSelect(ClientConfig a)
	{
		return true;
	}

	@Override
	public boolean canDeselect(ClientConfig a)
	{
		return true;
	}

	@Override
	public void doSelect(ClientConfig a)
	{
		getSession().setProperty(ManagerProperties.selectedClient, a);
	}

	@Override
	public void doDeselect(ClientConfig a)
	{
		if(a != null && a.equals(getSession().getProperty(ManagerProperties.selectedClient)))
			getSession().setProperty(ManagerProperties.selectedClient, null);
	}

	@Override
	public String getIcon()
	{
		return "manager/application";
	}

	@Override
	public String getItemIcon(ClientConfig obj)
	{
		return "manager/client";
	}

	@Override
	public String getItemName(ClientConfig obj)
	{
		return obj.getName();
	}

	@Override
	public String getTitle()
	{
		return (theApp == null ? "" : theApp.getName() + " ") + "Clients";
	}
}
