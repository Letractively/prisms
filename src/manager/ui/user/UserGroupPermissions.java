/*
 * UserGroupPermissions.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;
import prisms.arch.Permission;
import prisms.arch.PrismsSession;
import prisms.arch.ds.UserGroup;
import prisms.ui.list.SelectableList;

/** Lists the permissions for the group selected in the user editor */
public class UserGroupPermissions extends SelectableList<Permission>
{
	UserGroup theGroup;

	@Override
	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
		setSelectionMode(SelectionMode.SINGLE);
		setDisplaySelectedOnly(false);
		UserGroup group = session.getProperty(ManagerProperties.userSelectedGroup);
		setGroup(group);
		session.addPropertyChangeListener(ManagerProperties.userSelectedGroup,
			new prisms.arch.event.PrismsPCL<UserGroup>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<UserGroup> evt)
				{
					setGroup(evt.getNewValue());
				}

				@Override
				public String toString()
				{
					return "Manager User Group Permissions Selection Updater";
				}
			});
		session.addEventListener("prismsUserChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(getSession().getUser().getName()
					.equals(((prisms.arch.ds.User) evt.getProperty("user")).getName()))
					initClient();// Refresh this tree to take new permissions changes into account
			}

			@Override
			public String toString()
			{
				return "Manager User Group Permissions Viewability Updater";
			}
		});
		session.addEventListener("groupChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(evt.getProperty("group") != theGroup)
					return;
				setGroup(theGroup);
			}

			@Override
			public String toString()
			{
				return "Manager User Group Permissions Content Updater";
			}
		});
	}

	void setGroup(UserGroup group)
	{
		theGroup = group;
		setListParams();
		setListData(theGroup == null ? new Permission [0] : theGroup.getPermissions()
			.getAllPermissions());
	}

	@Override
	public String getTitle()
	{
		return (theGroup == null ? "" : theGroup.getName() + " ") + "Permissions";
	}

	@Override
	public String getIcon()
	{
		return "manager/group";
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

	@Override
	public boolean canSelect(Permission item)
	{
		return true;
	}

	@Override
	public void doSelect(Permission item)
	{
		getSession().setProperty(ManagerProperties.userSelectedPermission, item);
	}

	@Override
	public boolean canDeselect(Permission item)
	{
		return true;
	}

	@Override
	public void doDeselect(Permission item)
	{
		if(getSession().getProperty(ManagerProperties.userSelectedPermission) == item)
			getSession().setProperty(ManagerProperties.userSelectedPermission, null);
	}
}
