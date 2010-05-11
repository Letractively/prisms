/**
 * UserGroups.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;

import org.dom4j.Element;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;
import prisms.ui.list.SelectableList;

/**
 * Lists the groups that a user can belong to for a specific application
 */
public class UserGroups extends SelectableList<UserGroup>
{
	User theUser;

	PrismsApplication theApp;

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#initPlugin(prisms.arch.PrismsSession,
	 *      org.dom4j.Element)
	 */
	@Override
	public void initPlugin(PrismsSession session, Element pluginEl)
	{
		super.initPlugin(session, pluginEl);
		setSelectionMode(SelectionMode.MULTIPLE);
		setDisplaySelectedOnly(false);
		User user = session.getProperty(ManagerProperties.selectedUser);
		PrismsApplication app = session.getProperty(ManagerProperties.userApplication);
		setUserApp(user, app);
		session.addPropertyChangeListener(ManagerProperties.selectedUser,
			new prisms.arch.event.PrismsPCL<User>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<User> evt)
				{
					setUserApp(evt.getNewValue(), theApp);
				}
			});
		session.addEventListener("userChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				User user2 = (User) evt.getProperty("user");
				if(user2.equals(theUser))
					setUserApp(user2, theApp);
			}
		});
		session.addPropertyChangeListener(ManagerProperties.userApplication,
			new prisms.arch.event.PrismsPCL<PrismsApplication>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication> evt)
				{
					setUserApp(theUser, evt.getNewValue());
				}
			});
		session.addEventListener("appChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				PrismsApplication app2 = (PrismsApplication) evt.getProperty("app");
				if(app2.equals(theApp))
					setUserApp(theUser, app2);
			}
		});
		session.addEventListener("appGroupsChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(theApp == evt.getProperty("app"))
					setListData((UserGroup []) evt.getProperty("groups"));
			}
		});
		session.addEventListener("userGroupsChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(theUser.equals(evt.getProperty("user")))
					setUserApp(theUser, theApp);
			}
		});
		session.addEventListener("userPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					if(getSession().getUser().getName().equals(
						((prisms.arch.ds.User) evt.getProperty("user")).getName()))
						initClient();// Refresh this tree to take new permissions changes into
					// account
				}
			});
		session.addEventListener("groupChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				UserGroup group = (UserGroup) evt.getProperty("group");
				for(int i = 0; i < getItemCount(); i++)
					if(getItem(i) instanceof prisms.ui.list.SelectableList.ItemNode
						&& ((ItemNode) getItem(i)).getObject().equals(group))
					{
						((ItemNode) getItem(i)).check();
						break;
					}
			}
		});
	}

	void setUserApp(User user, PrismsApplication app)
	{
		prisms.arch.ds.ManageableUserSource src = (prisms.arch.ds.ManageableUserSource) getSession()
			.getApp().getDataSource();
		theUser = user;
		theApp = app;
		setListParams();
		if(theApp == null)
		{
			setListData(new UserGroup [0]);
			return;
		}
		else
			try
			{
				setListData(src.getGroups(theApp));
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not get PRISMS groups", e);
			}
		if(theUser != null)
		{
			UserGroup [] groups = new UserGroup [0];
			for(UserGroup g : theUser.getGroups())
				if(g.getApp().equals(theApp))
					groups = prisms.util.ArrayUtils.add(groups, g);
			setSelectedObjects(groups);
		}
		else
			setSelectedObjects(new UserGroup [0]);
	}

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#setSelection(prisms.ui.list.DataListNode[], boolean)
	 */
	@Override
	public synchronized void setSelection(prisms.ui.list.DataListNode[] nodes, boolean fromUser)
	{
		if(!fromUser)
			super.setSelection(nodes, fromUser);
		if(fromUser)
		{
			UserGroup selected = null;
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
			getSession().setProperty(ManagerProperties.userSelectedGroup, selected);
		}
	}

	/**
	 * @see prisms.ui.list.SelectableList#getTitle()
	 */
	@Override
	public String getTitle()
	{
		return (theApp == null ? "" : theApp.getName() + " ") + "Groups"
			+ (theUser == null ? "" : " for user " + theUser.getName());
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
	 * @see prisms.ui.list.SelectableList#getItemName(java.lang.Object)
	 */
	@Override
	public String getItemName(UserGroup item)
	{
		return item.getName();
	}

	/**
	 * @see prisms.ui.list.SelectableList#getItemIcon(java.lang.Object)
	 */
	@Override
	public String getItemIcon(UserGroup item)
	{
		return "manager/group";
	}

	/**
	 * @see prisms.ui.list.SelectableList#canSelect(java.lang.Object)
	 */
	@Override
	public boolean canSelect(UserGroup item)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#doSelect(java.lang.Object)
	 */
	@Override
	public void doSelect(UserGroup item)
	{
		getSession().setProperty(ManagerProperties.userSelectedGroup, item);
	}

	/**
	 * @see prisms.ui.list.SelectableList#canDeselect(java.lang.Object)
	 */
	@Override
	public boolean canDeselect(UserGroup item)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#doDeselect(java.lang.Object)
	 */
	@Override
	public void doDeselect(UserGroup item)
	{
		if(getSession().getProperty(ManagerProperties.userSelectedGroup) == item)
			getSession().setProperty(ManagerProperties.userSelectedGroup, null);
	}
}
