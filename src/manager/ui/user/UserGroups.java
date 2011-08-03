/*
 * UserGroups.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;
import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;
import prisms.ui.list.SelectableList;

/** Lists the groups that a user can belong to for a specific application */
public class UserGroups extends SelectableList<UserGroup>
{
	User theUser;

	PrismsApplication theApp;

	@Override
	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
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

				@Override
				public String toString()
				{
					return "Manager User Groups User Selection Updater";
				}
			});
		session.addEventListener("prismsUserChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				User user2 = (User) evt.getProperty("user");
				if(getSession().getUser().equals(user2))
					initClient();// Refresh this tree to take new permissions changes into account
				if(user2.equals(theUser))
					setUserApp(user2, theApp);
			}

			@Override
			public String toString()
			{
				return "Manager User Groups Viewability Updater";
			}
		});
		session.addPropertyChangeListener(ManagerProperties.userApplication,
			new prisms.arch.event.PrismsPCL<PrismsApplication>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication> evt)
				{
					setUserApp(theUser, evt.getNewValue());
				}

				@Override
				public String toString()
				{
					return "Manager User Groups App Selection Updater";
				}
			});
		session.addEventListener("appChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				PrismsApplication app2 = (PrismsApplication) evt.getProperty("app");
				if(app2.equals(theApp))
					setUserApp(theUser, app2);
			}

			@Override
			public String toString()
			{
				return "Manager User Groups App Updater";
			}
		});
		session.addEventListener("appGroupsChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(theApp == evt.getProperty("app"))
					setListData((UserGroup []) evt.getProperty("groups"));
			}

			@Override
			public String toString()
			{
				return "Manager User Groups Content Updater";
			}
		});
		session.addEventListener("groupChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				UserGroup group = (UserGroup) evt.getProperty("group");
				for(int i = 0; i < getItemCount(); i++)
					if(getItem(i) instanceof prisms.ui.list.SelectableList<?>.ItemNode
						&& ((ItemNode) getItem(i)).getObject().equals(group))
					{
						((ItemNode) getItem(i)).check();
						break;
					}
			}

			@Override
			public String toString()
			{
				return "Manager User Groups Group Updater";
			}
		});
	}

	void setUserApp(User user, PrismsApplication app)
	{
		prisms.arch.ds.ManageableUserSource src = (prisms.arch.ds.ManageableUserSource) getSession()
			.getApp().getEnvironment().getUserSource();
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
			getSession().setProperty(ManagerProperties.userSelectedGroup, selected);
		}
	}

	@Override
	public String getTitle()
	{
		return (theApp == null ? "" : theApp.getName() + " ") + "Groups"
			+ (theUser == null ? "" : " for user " + theUser.getName());
	}

	@Override
	public String getIcon()
	{
		return "manager/application";
	}

	@Override
	public String getItemName(UserGroup item)
	{
		return item.getName();
	}

	@Override
	public String getItemIcon(UserGroup item)
	{
		return "manager/group";
	}

	@Override
	public boolean canSelect(UserGroup item)
	{
		return true;
	}

	@Override
	public void doSelect(UserGroup item)
	{
		getSession().setProperty(ManagerProperties.userSelectedGroup, item);
	}

	@Override
	public boolean canDeselect(UserGroup item)
	{
		return true;
	}

	@Override
	public void doDeselect(UserGroup item)
	{
		if(getSession().getProperty(ManagerProperties.userSelectedGroup) == item)
			getSession().setProperty(ManagerProperties.userSelectedGroup, null);
	}
}
