/**
 * UserApplications.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;
import prisms.arch.PrismsApplication;

/** A list of applications that a user might access */
public class UserApplications extends prisms.ui.list.SelectableList<PrismsApplication>
{
	@Override
	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
		setSelectionMode(SelectionMode.SINGLE);
		setDisplaySelectedOnly(false);
		PrismsApplication [] apps = session
			.getProperty(prisms.arch.event.PrismsProperties.applications);
		setListData(apps);
		session.addPropertyChangeListener(prisms.arch.event.PrismsProperties.applications,
			new prisms.arch.event.PrismsPCL<PrismsApplication []>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication []> evt)
				{
					setListData(evt.getNewValue());
				}
			});
		PrismsApplication app = session.getProperty(ManagerProperties.userApplication);
		setSelectedObjects(new PrismsApplication [] {app});
		session.addPropertyChangeListener(ManagerProperties.userApplication,
			new prisms.arch.event.PrismsPCL<PrismsApplication>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication> evt)
				{
					setSelectedObjects(new PrismsApplication [] {evt.getNewValue()});
				}
			});
		session.addEventListener("appChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.PrismsSession session2,
				prisms.arch.event.PrismsEvent evt)
			{
				for(int i = 0; i < getItemCount(); i++)
					if(getItem(i) instanceof prisms.ui.list.SelectableList<?>.ItemNode
						&& ((ItemNode) getItem(i)).getObject().equals(evt.getProperty("app")))
						((ItemNode) getItem(i)).check();
			}
		});
		session.addEventListener("prismsUserChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.PrismsSession session2,
				prisms.arch.event.PrismsEvent evt)
			{
				if(getSession().getUser().getName()
					.equals(((prisms.arch.ds.User) evt.getProperty("user")).getName()))
					initClient();// Refresh this tree to take new permissions changes into account
			}
		});
	}

	@Override
	public String getTitle()
	{
		return "Applications";
	}

	@Override
	public String getIcon()
	{
		return "manager/application";
	}

	@Override
	public String getItemName(PrismsApplication item)
	{
		return item.getName();
	}

	@Override
	public String getItemIcon(PrismsApplication item)
	{
		return "manager/application";
	}

	@Override
	public boolean canSelect(PrismsApplication item)
	{
		return true;
	}

	@Override
	public void doSelect(PrismsApplication item)
	{
		getSession().setProperty(ManagerProperties.userApplication, item);
	}

	@Override
	public boolean canDeselect(PrismsApplication item)
	{
		return true;
	}

	@Override
	public void doDeselect(PrismsApplication item)
	{
		if(getSession().getProperty(ManagerProperties.userApplication) == item)
			getSession().setProperty(ManagerProperties.userApplication, null);
	}
}
