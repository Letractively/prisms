/**
 * AllApplicationsList.java Created Jul 10, 2008 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;

import prisms.arch.PrismsApplication;

/**
 * Displays all applications to the user and allows the user certain options to manipulate them
 */
public class AllApplicationsList extends prisms.ui.list.SelectableList<PrismsApplication>
{
	private static final Logger log = Logger.getLogger(AllApplicationsList.class);

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#initPlugin(prisms.arch.PrismsSession,
	 *      org.dom4j.Element)
	 */
	@Override
	public void initPlugin(prisms.arch.PrismsSession session, org.dom4j.Element pluginEl)
	{
		super.initPlugin(session, pluginEl);
		setDisplaySelectedOnly(false);
		setSelectionMode(SelectionMode.SINGLE);
		setListData(session.getProperty(ManagerProperties.applications));
		if(session.getProperty(ManagerProperties.selectedApp) != null)
			setSelectedObjects(new PrismsApplication [] {session
				.getProperty(ManagerProperties.selectedApp)});
		session.addPropertyChangeListener(ManagerProperties.applications,
			new prisms.arch.event.PrismsPCL<PrismsApplication []>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication []> evt)
				{
					setListData(evt.getNewValue());
				}
			});
		session.addPropertyChangeListener(ManagerProperties.selectedApp,
			new prisms.arch.event.PrismsPCL<PrismsApplication>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication> evt)
				{
					if(evt.getNewValue() == null)
						setSelectedObjects(new PrismsApplication [0]);
					else
						setSelectedObjects(new PrismsApplication [] {evt.getNewValue()});
				}
			});
		session.addEventListener("appChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				for(int i = 0; i < getItemCount(); i++)
					if(getItem(i) instanceof prisms.ui.list.SelectableList<?>.ItemNode
						&& ((ItemNode) getItem(i)).getObject().equals(evt.getProperty("app")))
						((ItemNode) getItem(i)).check();
			}
		});
	}

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#processEvent(org.json.simple.JSONObject)
	 */
	@Override
	public void processEvent(org.json.simple.JSONObject evt)
	{
		if("setEditApp".equals(evt.get("method")))
		{
			if(evt.get("app") == null)
			{
				getSession().setProperty(ManagerProperties.selectedApp, null);
				return;
			}
			int i;
			for(i = 0; i < getItemCount(); i++)
				if(getItem(i).getID().equals(evt.get("app")))
				{
					getSession().setProperty(ManagerProperties.selectedApp,
						((ItemNode) getItem(i)).getObject());
					break;
				}
			if(i == getItemCount())
				log.error("No such app: " + evt.get("app"));
		}
		else
			super.processEvent(evt);
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
	 * @see prisms.ui.list.SelectableList#getTitle()
	 */
	@Override
	public String getTitle()
	{
		return "All Applications";
	}

	/**
	 * @see prisms.ui.list.SelectableList#getItemIcon(java.lang.Object)
	 */
	@Override
	public String getItemIcon(PrismsApplication obj)
	{
		return "manager/application";
	}

	/**
	 * @see prisms.ui.list.SelectableList#getItemName(java.lang.Object)
	 */
	@Override
	public String getItemName(PrismsApplication obj)
	{
		return obj.getName();
	}

	@Override
	public boolean canSelect(PrismsApplication a)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#doSelect(java.lang.Object)
	 */
	@Override
	public void doSelect(PrismsApplication a)
	{
		getSession().setProperty(ManagerProperties.selectedApp, a);
	}

	@Override
	public boolean canDeselect(PrismsApplication a)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#doDeselect(java.lang.Object)
	 */
	@Override
	public void doDeselect(PrismsApplication a)
	{
		getSession().setProperty(ManagerProperties.selectedApp, null);
	}
}
