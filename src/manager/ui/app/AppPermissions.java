/*
 * AppPermissions.java Created Jun 24, 2009 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;
import prisms.arch.Permission;
import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.arch.ds.ManageableUserSource;
import prisms.arch.ds.UserGroup;

/**
 * Shows all permissions for an application and allows the user to select which ones belong to the
 * selected group
 */
public class AppPermissions extends prisms.ui.list.SelectableList<Permission>
{
	private boolean SELECTABLE_FROM_LIST = false;

	PrismsApplication theApp;

	UserGroup theGroup;

	boolean theDataLock;

	@Override
	public void initPlugin(prisms.arch.PrismsSession session, org.dom4j.Element pluginEl)
	{
		super.initPlugin(session, pluginEl);
		setCompareByIdentity(false);
		setSelectionMode(SelectionMode.MULTIPLE);
		setDisplaySelectedOnly(false);
		setApp(session.getProperty(ManagerProperties.selectedApp));
		session.addPropertyChangeListener(ManagerProperties.selectedApp,
			new prisms.arch.event.PrismsPCL<PrismsApplication>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication> evt)
				{
					setApp(evt.getNewValue());
				}
			});
		setGroup(session.getProperty(ManagerProperties.selectedAppGroup));
		session.addPropertyChangeListener(ManagerProperties.selectedAppGroup,
			new prisms.arch.event.PrismsPCL<UserGroup>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<UserGroup> evt)
				{
					setGroup(evt.getNewValue());
				}
			});
		session.addEventListener("groupChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(evt.getProperty("group") == theGroup)
					setListParams();
			}
		});
		session.addEventListener("userPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
				{
					if(getSession().getUser().getName()
						.equals(((prisms.arch.ds.User) evt.getProperty("user")).getName()))
						setApp(theApp);
				}
			});
		session.addEventListener("groupPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
				{
					if(evt.getProperty("group") != theGroup)
						return;
					if(theDataLock)
						return;
					setGroup(theGroup);
				}
			});
	}

	String createNewPermissionName(String aTry)
	{
		if(!isUsed(aTry))
			return aTry;
		int i;
		for(i = 2; isUsed(aTry + " " + i); i++);
		return aTry + " " + i;
	}

	boolean isUsed(String permName)
	{
		for(int i = 0; i < getItemCount(); i++)
		{
			prisms.ui.list.DataListNode item = getItem(i);
			if(item instanceof prisms.ui.list.SelectableList<?>.ItemNode
				&& ((ItemNode) item).getObject().getName().equals(permName))
				return true;
		}
		return false;
	}

	void setApp(PrismsApplication app)
	{
		theApp = app;
		setListData(new Permission [0]);
		setItems(new prisms.ui.list.DataListNode [0]);
		setListParams();
		if(theApp == null)
			return;
		setListData(theApp.getPermissions());
		setGroup(theGroup);
	}

	void setGroup(UserGroup group)
	{
		theGroup = group;
		if(theGroup == null)
			setSelectedObjects(new Permission [0]);
		else
			setSelectedObjects(theGroup.getPermissions().getAllPermissions());
		setListParams();
	}

	@Override
	public synchronized void setSelection(prisms.ui.list.DataListNode[] nodes, boolean fromUser)
	{
		if(SELECTABLE_FROM_LIST || !fromUser)
			super.setSelection(nodes, fromUser);
		if(fromUser)
		{
			Permission selected = null;
			for(int i = 0; i < nodes.length; i++)
			{
				if(nodes[i] instanceof prisms.ui.list.SelectableList<?>.ItemNode)
				{
					if(selected != null)
					{
						selected = null;
						break;
					}
					selected = ((ItemNode) nodes[i]).getObject();
				}
				else if(nodes[i] instanceof prisms.ui.list.ActionListNode)
					((prisms.ui.list.ActionListNode) nodes[i]).userSetSelected(true);
			}
			getSession().setProperty(ManagerProperties.selectedAppPermission, selected);
		}
	}

	@Override
	public String getTitle()
	{
		return "Permissions" + (theGroup == null ? "" : " for " + theGroup.getName())
			+ (theApp == null ? "" : " in " + theApp.getName());
	}

	@Override
	public String getIcon()
	{
		return "manager/application";
	}

	@Override
	public boolean canDeselect(Permission item)
	{
		return theGroup != null
			&& theApp.getEnvironment().getUserSource() instanceof ManageableUserSource
			&& manager.app.ManagerUtils.canEdit(getSession().getPermissions(), theGroup);
	}

	@Override
	public boolean canSelect(Permission item)
	{
		return theGroup != null
			&& theApp.getEnvironment().getUserSource() instanceof ManageableUserSource
			&& manager.app.ManagerUtils.canEdit(getSession().getPermissions(), theGroup);
	}

	@Override
	public void doDeselect(Permission item)
	{
		theGroup.getPermissions().removePermission(item.getName());
		try
		{
			((ManageableUserSource) theApp.getEnvironment().getUserSource()).putGroup(theGroup);
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not remove permission from group", e);
		}
		theDataLock = true;
		try
		{
			getSession().fireEvent(
				new prisms.arch.event.PrismsEvent("groupPermissionsChanged", "group", theGroup));
		} finally
		{
			theDataLock = false;
		}
	}

	@Override
	public void doSelect(Permission item)
	{
		theGroup.getPermissions().addPermission(item);
		try
		{
			((ManageableUserSource) theApp.getEnvironment().getUserSource()).putGroup(theGroup);
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not add permission to group", e);
		}
		theDataLock = true;
		try
		{
			getSession().fireEvent(
				new prisms.arch.event.PrismsEvent("groupPermissionsChanged", "group", theGroup));
		} finally
		{
			theDataLock = false;
		}
	}

	@Override
	public String getItemName(Permission item)
	{
		return item.getName();
	}

	@Override
	public String getItemIcon(Permission item)
	{
		return "manager/permission";
	}
}
