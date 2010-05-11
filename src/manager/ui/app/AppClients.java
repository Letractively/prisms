/**
 * AppClients.java Created Oct 1, 2008 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;
import manager.app.ManagerUtils;

import org.apache.log4j.Logger;

import prisms.arch.ClientConfig;
import prisms.arch.PrismsApplication;
import prisms.ui.list.NodeAction;
import prisms.ui.list.SelectableList;

/**
 * Allows the user to view and manage all clients for an application
 */
public class AppClients extends SelectableList<ClientConfig>
{
	private static final Logger log = Logger.getLogger(AppClients.class);

	private javax.swing.Action DELETE_USER_ACTION;

	prisms.arch.ds.ManageableUserSource theUserSource;

	PrismsApplication theApp;

	prisms.ui.UI theUI;

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#initPlugin(prisms.arch.PrismsSession,
	 *      org.dom4j.Element)
	 */
	@Override
	public void initPlugin(prisms.arch.PrismsSession session, org.dom4j.Element pluginEl)
	{
		DELETE_USER_ACTION = new javax.swing.AbstractAction("Delete")
		{
			public void actionPerformed(java.awt.event.ActionEvent evt)
			{
				if(theUserSource == null)
					throw new IllegalStateException("Data source is not manageable"
						+ "--cannot manage clients");
				if(!ManagerUtils.canEdit(getSession().getUser(), getSession().getApp(), theApp))
					throw new IllegalArgumentException("User " + getSession().getUser()
						+ " does not have permission to delete a client of application "
						+ theApp.getName());
				final ClientConfig client = ((ItemNode) evt.getSource()).getObject();
				final prisms.ui.UI.ConfirmListener action = new prisms.ui.UI.ConfirmListener()
				{
					public void confirmed(boolean confirmed)
					{
						try
						{
							theUserSource.deleteClient(client);
						} catch(prisms.arch.PrismsException e)
						{
							throw new IllegalStateException(
								"Could not delete client configuration", e);
						}
						getSession().getApp().fireGlobally(
							getSession(),
							new prisms.arch.event.PrismsEvent("appChanged", prisms.util.PrismsUtils
								.eventProps("app", theApp)));
					}
				};
				if(theUI == null)
					action.confirmed(true);
				else
					theUI.confirm("Are you sure you want to delete client " + client.getName()
						+ " of application " + theApp.getName(), action);
			}
		};
		super.initPlugin(session, pluginEl);
		setDisplaySelectedOnly(false);
		setSelectionMode(SelectionMode.SINGLE);
		prisms.arch.ds.UserSource us = session.getApp().getDataSource();
		if(!(us instanceof prisms.arch.ds.ManageableUserSource))
			log.warn("User source is not manageable");
		else
			theUserSource = (prisms.arch.ds.ManageableUserSource) us;
		theApp = getSession().getProperty(ManagerProperties.selectedApp);
		if(theApp != null && theUserSource != null)
			try
			{
				setListData(theUserSource.getAllClients(theApp));
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not get application clients", e);
			}
		session.addPropertyChangeListener(ManagerProperties.selectedApp,
			new prisms.arch.event.PrismsPCL<PrismsApplication>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication> evt)
				{
					PrismsApplication app = evt.getNewValue();
					theApp = app;
					if(theUserSource != null && app != null)
						try
						{
							setListData(theUserSource.getAllClients(app));
						} catch(prisms.arch.PrismsException e)
						{
							throw new IllegalStateException("Could not get application clients", e);
						}
					else
						setListData(new ClientConfig [0]);
					setListParams();
				}
			});
		session.addEventListener("appChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				PrismsApplication app = (PrismsApplication) evt.getProperty("app");
				if(theApp != null && theApp.equals(app) && theUserSource != null)
				{
					try
					{
						setListData(theUserSource.getAllClients(app));
					} catch(prisms.arch.PrismsException e)
					{
						throw new IllegalStateException("Could not get application clients", e);
					}
					initClient();
				}
			}
		});
		session.addEventListener("userChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(getSession().getUser().getName().equals(
					((prisms.arch.ds.User) evt.getProperty("user")).getName()))
					initClient();// Refresh this tree to take new permissions changes into account
			}
		});
		theUI = (prisms.ui.UI) session.getPlugin("UI");
	}

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#getActions()
	 */
	@Override
	public NodeAction [] getActions()
	{
		NodeAction [] ret = super.getActions();

		if(theApp != null
			&& ManagerUtils.canEdit(getSession().getUser(), getSession().getApp(), theApp))
			ret = prisms.util.ArrayUtils.add(ret, new NodeAction("New Client", false));
		return ret;
	}

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#doAction(java.lang.String)
	 */
	@Override
	public void doAction(String action)
	{
		if("New Client".equals(action))
		{
			if(theUserSource == null || theApp == null)
				throw new IllegalStateException(
					"User source not manageable or application not selected");
			if(!ManagerUtils.canEdit(getSession().getUser(), getSession().getApp(), theApp))
				throw new IllegalArgumentException("User " + getSession().getUser()
					+ " does not have permission to create new clients for application "
					+ theApp.getName());

			String name;
			try
			{
				name = newClientName(theUserSource.getAllClients(theApp));
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not get application clients", e);
			}
			ClientConfig newClient;
			try
			{
				newClient = theUserSource.createClient(theApp, name);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not create client", e);
			}
			getSession().getApp().fireGlobally(getSession(),
				new prisms.arch.event.PrismsEvent("appChanged", "app", theApp));
			doSelect(newClient);
		}
		else
			super.doAction(action);
	}

	private String newClientName(ClientConfig [] clients)
	{
		String ret = "New Client";
		if(!clientExists(ret, clients))
			return ret;
		int count = 1;
		while(clientExists(ret + "(" + count + ")", clients))
			count++;
		return ret + "(" + count + ")";
	}

	private boolean clientExists(String name, ClientConfig [] clients)
	{
		for(int u = 0; u < clients.length; u++)
			if(clients[u].getName().equals(name))
				return true;
		return false;
	}

	/**
	 * @see prisms.ui.list.SelectableList#createObjectNode(java.lang.Object)
	 */
	@Override
	public ItemNode createObjectNode(ClientConfig a)
	{
		ItemNode ret = super.createObjectNode(a);
		if(ManagerUtils.canEdit(getSession().getUser(), getSession().getApp(), theApp))
			ret.addAction(DELETE_USER_ACTION);
		return ret;
	}

	/**
	 * @see prisms.ui.list.SelectableList#canSelect(java.lang.Object)
	 */
	@Override
	public boolean canSelect(ClientConfig a)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#canDeselect(java.lang.Object)
	 */
	@Override
	public boolean canDeselect(ClientConfig a)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#doSelect(java.lang.Object)
	 */
	@Override
	public void doSelect(ClientConfig a)
	{
		getSession().setProperty(ManagerProperties.selectedClient, a);
	}

	/**
	 * @see prisms.ui.list.SelectableList#doDeselect(java.lang.Object)
	 */
	@Override
	public void doDeselect(ClientConfig a)
	{
		if(a != null && a.equals(getSession().getProperty(ManagerProperties.selectedClient)))
			getSession().setProperty(ManagerProperties.selectedClient, null);
	}

	/**
	 * @see prisms.ui.list.SelectableList#getIcon()
	 */
	@Override
	public String getIcon()
	{
		return "manager/application";
	}

	/**
	 * @see prisms.ui.list.SelectableList#getItemIcon(java.lang.Object)
	 */
	@Override
	public String getItemIcon(ClientConfig obj)
	{
		return "manager/client";
	}

	/**
	 * @see prisms.ui.list.SelectableList#getItemName(java.lang.Object)
	 */
	@Override
	public String getItemName(ClientConfig obj)
	{
		return obj.getName();
	}

	/**
	 * @see prisms.ui.list.SelectableList#getTitle()
	 */
	@Override
	public String getTitle()
	{
		return (theApp == null ? "" : theApp.getName() + " ") + "Clients";
	}
}
