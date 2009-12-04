/*
 * AppPermissions.java Created Jun 24, 2009 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;
import prisms.arch.PrismsApplication;
import prisms.arch.ds.ManageableUserSource;
import prisms.arch.ds.Permission;
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
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(evt.getProperty("group") == theGroup)
					setListParams();
			}
		});
		session.addEventListener("userPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					if(getSession().getUser().getName().equals(
						((prisms.arch.ds.User) evt.getProperty("user")).getName()))
						setApp(theApp);
				}
			});
		session.addEventListener("groupPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					if(evt.getProperty("group") != theGroup)
						return;
					if(theDataLock)
						return;
					setGroup(theGroup);
				}
			});
		session.addEventListener("appPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					if(evt.getProperty("app") != theApp)
						return;
					Permission [] permissions = (Permission []) evt.getProperty("permissions");
					if(!prisms.util.ArrayUtils.contains(permissions, getSession().getProperty(
						ManagerProperties.selectedAppPermission)))
						getSession().setProperty(ManagerProperties.selectedAppPermission, null);
					setApp(theApp);
				}
			});
		session.addEventListener("createNewPermission", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				final prisms.ui.UI ui = (prisms.ui.UI) getSession().getPlugin("UI");
				if(ui == null)
					throw new IllegalStateException("No UI--can't query user for permission name");
				final prisms.ui.UI.InputListener[] il = new prisms.ui.UI.InputListener [1];
				il[0] = new prisms.ui.UI.InputListener()
				{
					public void inputed(String input)
					{
						if(input == null)
							return;
						if(isUsed(input))
						{
							ui.input("Application " + theApp.getName()
								+ " already has a permission named " + input
								+ ". Choose a different name.", input, il[0]);
							return;
						}
						ManageableUserSource mus = (ManageableUserSource) getSession().getApp()
							.getDataSource();
						try
						{
							mus.createPermission(theApp, input, input);
						} catch(prisms.arch.PrismsException e)
						{
							throw new IllegalStateException("Could not create permission", e);
						}
						try
						{
							getSession().fireEvent(
								new prisms.arch.event.PrismsEvent("appPermissionsChanged", "app",
									theApp, "permissions", mus.getPermissions(theApp)));
						} catch(prisms.arch.PrismsException e)
						{
							throw new IllegalStateException(
								"Could not get permissions for application " + theApp, e);
						}
					}
				};
				ui.input("Enter a name for the new permission (this cannot be changed later).",
					createNewPermissionName("New " + theApp.getName() + " Permission"), il[0]);
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
			if(item instanceof prisms.ui.list.SelectableList.ItemNode
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
		if(theApp == null || !(theApp.getDataSource() instanceof ManageableUserSource))
			return;
		try
		{
			setListData(((ManageableUserSource) theApp.getDataSource()).getPermissions(theApp));
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not get permissions for application " + theApp,
				e);
		}
		if(manager.app.ManagerUtils.canEdit(getSession().getUser(), theApp))
		{
			prisms.ui.list.ActionListNode action;
			action = new prisms.ui.list.ActionListNode(this, "createNewPermission");
			action.setText("*Create Permission*");
			action.setIcon("manager/permission");
			addNode(action, 0);
		}
		setGroup(theGroup);
	}

	void setGroup(UserGroup group)
	{
		theGroup = group;
		if(theGroup == null || !(theApp.getDataSource() instanceof ManageableUserSource))
			setSelectedObjects(new Permission [0]);
		else
			setSelectedObjects(theGroup.getPermissions().getAllPermissions());
		setListParams();
	}

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#setSelection(prisms.ui.list.DataListNode[], boolean)
	 */
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
				if(nodes[i] instanceof prisms.ui.list.SelectableList.ItemNode)
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

	/**
	 * @see prisms.ui.list.SelectableList#createObjectNode(java.lang.Object)
	 */
	@Override
	public ItemNode createObjectNode(Permission item)
	{
		ItemNode ret = super.createObjectNode(item);
		if(manager.app.ManagerUtils.canEdit(getSession().getUser(), theApp))
			ret.addAction(new javax.swing.AbstractAction("Delete")
			{
				public void actionPerformed(java.awt.event.ActionEvent evt)
				{
					final ItemNode node = (ItemNode) evt.getSource();
					prisms.ui.UI ui = (prisms.ui.UI) getSession().getPlugin("UI");
					prisms.ui.UI.ConfirmListener cl = new prisms.ui.UI.ConfirmListener()
					{
						public void confirmed(boolean confirm)
						{
							if(!confirm)
								return;
							ManageableUserSource mus = (ManageableUserSource) getSession().getApp()
								.getDataSource();

							prisms.arch.ds.User[] users = getSession().getProperty(
								ManagerProperties.users);
							users = users.clone();
							for(int u = 0; u < users.length; u++)
							{
								try
								{
									users[u] = mus.getUser(users[u], node.getObject().getApp());
								} catch(prisms.arch.PrismsException e)
								{
									throw new IllegalStateException("Could not get PRISMS user", e);
								}
								if(users[u] == null
									|| !users[u].getPermissions().has(node.getObject().getName()))
								{
									users = prisms.util.ArrayUtils.remove(users, u);
									u--;
								}
							}
							UserGroup [] groups;
							try
							{
								groups = mus.getGroups(theApp);
							} catch(prisms.arch.PrismsException e)
							{
								throw new IllegalStateException("Could not get PRISMS groups", e);
							}
							for(int g = 0; g < groups.length; g++)
								if(!groups[g].getPermissions().has(node.getObject().getName()))
								{
									groups = prisms.util.ArrayUtils.remove(groups, g);
									g--;
								}
							try
							{
								mus.deletePermission(node.getObject());
							} catch(prisms.arch.PrismsException e)
							{
								throw new IllegalStateException(
									"Could not delete permission for application " + theApp, e);
							}
							for(int g = 0; g < groups.length; g++)
								getSession().fireEvent(
									new prisms.arch.event.PrismsEvent("groupPermissionsChanged",
										"group", groups[g]));
							for(int u = 0; u < users.length; u++)
								getSession().fireEvent(
									new prisms.arch.event.PrismsEvent("userPermissionsChanged",
										"user", users[u]));
							try
							{
								getSession().fireEvent(
									new prisms.arch.event.PrismsEvent("appPermissionsChanged",
										"app", theApp, "permissions", mus.getPermissions(theApp)));
							} catch(prisms.arch.PrismsException e)
							{
								throw new IllegalStateException(
									"Could not get permissions for application " + theApp, e);
							}
						}
					};
					if(ui != null)
						ui.confirm("Are you sure you want to delete permission "
							+ node.getObject().getName() + " from application " + theApp.getName()
							+ "?", cl);
					else
						cl.confirmed(true);
				}
			});
		return ret;
	}

	/**
	 * @see prisms.ui.list.SelectableList#getTitle()
	 */
	@Override
	public String getTitle()
	{
		return "Permissions" + (theGroup == null ? "" : " for " + theGroup.getName())
			+ (theApp == null ? "" : " in " + theApp.getName());
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
	 * @see prisms.ui.list.SelectableList#canDeselect(java.lang.Object)
	 */
	@Override
	public boolean canDeselect(Permission item)
	{
		return theGroup != null && theApp.getDataSource() instanceof ManageableUserSource
			&& manager.app.ManagerUtils.canEdit(getSession().getUser(), theGroup);
	}

	/**
	 * @see prisms.ui.list.SelectableList#canSelect(java.lang.Object)
	 */
	@Override
	public boolean canSelect(Permission item)
	{
		return theGroup != null && theApp.getDataSource() instanceof ManageableUserSource
			&& manager.app.ManagerUtils.canEdit(getSession().getUser(), theGroup);
	}

	/**
	 * @see prisms.ui.list.SelectableList#doDeselect(java.lang.Object)
	 */
	@Override
	public void doDeselect(Permission item)
	{
		try
		{
			((ManageableUserSource) theApp.getDataSource()).removePermission(theGroup, item);
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

	/**
	 * @see prisms.ui.list.SelectableList#doSelect(java.lang.Object)
	 */
	@Override
	public void doSelect(Permission item)
	{
		try
		{
			((ManageableUserSource) theApp.getDataSource()).addPermission(theGroup, item);
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
}
