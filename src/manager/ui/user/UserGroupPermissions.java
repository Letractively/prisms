/**
 * UserGroupPermissions.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;

import org.dom4j.Element;

import prisms.arch.Permission;
import prisms.arch.PrismsSession;
import prisms.arch.ds.UserGroup;
import prisms.ui.list.SelectableList;

/**
 * Lists the permissions for the group selected in the user editor
 */
public class UserGroupPermissions extends SelectableList<Permission>
{
	UserGroup theGroup;

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#initPlugin(prisms.arch.PrismsSession,
	 *      org.dom4j.Element)
	 */
	@Override
	public void initPlugin(PrismsSession session, Element pluginEl)
	{
		super.initPlugin(session, pluginEl);
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
			});
		session.addEventListener("prismsUserChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(getSession().getUser().getName()
					.equals(((prisms.arch.ds.User) evt.getProperty("user")).getName()))
					initClient();// Refresh this tree to take new permissions changes into account
			}
		});
		session.addEventListener("groupPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
				{
					if(evt.getProperty("group") != theGroup)
						return;
					setGroup(theGroup);
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

	/**
	 * @see prisms.ui.list.SelectableList#getTitle()
	 */
	@Override
	public String getTitle()
	{
		return (theGroup == null ? "" : theGroup.getName() + " ") + "Permissions";
	}

	/**
	 * @see prisms.ui.list.SelectableList#getIcon()
	 */
	@Override
	public String getIcon()
	{
		return "manager/group";
	}

	/**
	 * @see prisms.ui.list.SelectableList#getItemName(java.lang.Object)
	 */
	@Override
	public String getItemName(Permission item)
	{
		return item.getName();
	}

	/**
	 * @see prisms.ui.list.SelectableList#getItemIcon(java.lang.Object)
	 */
	@Override
	public String getItemIcon(Permission item)
	{
		return "manager/permission";
	}

	/**
	 * @see prisms.ui.list.SelectableList#canSelect(java.lang.Object)
	 */
	@Override
	public boolean canSelect(Permission item)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#doSelect(java.lang.Object)
	 */
	@Override
	public void doSelect(Permission item)
	{
		getSession().setProperty(ManagerProperties.userSelectedPermission, item);
	}

	/**
	 * @see prisms.ui.list.SelectableList#canDeselect(java.lang.Object)
	 */
	@Override
	public boolean canDeselect(Permission item)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#doDeselect(java.lang.Object)
	 */
	@Override
	public void doDeselect(Permission item)
	{
		if(getSession().getProperty(ManagerProperties.userSelectedPermission) == item)
			getSession().setProperty(ManagerProperties.userSelectedPermission, null);
	}
}
