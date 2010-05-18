/**
 * AllUsersList.java Created Jul 10, 2008 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;

import prisms.arch.ds.User;

/**
 * Displays the list of all users to the user and allows user creation/deletion/selection
 */
public class AllUsersList extends prisms.ui.list.SelectableList<User>
{
	private static final Logger log = Logger.getLogger(AllUsersList.class);

	private javax.swing.Action DELETE_USER_ACTION;

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#initPlugin(prisms.arch.PrismsSession,
	 *      org.dom4j.Element)
	 */
	@Override
	public void initPlugin(prisms.arch.PrismsSession session, org.dom4j.Element pluginEl)
	{
		super.initPlugin(session, pluginEl);
		setDisplaySelectedOnly(false);
		DELETE_USER_ACTION = new javax.swing.AbstractAction("Delete")
		{
			public void actionPerformed(java.awt.event.ActionEvent evt)
			{
				final User user = ((ItemNode) evt.getSource()).getObject();
				if(user.equals(getSession().getUser()))
					throw new IllegalArgumentException("User " + user
						+ " cannot delete him/herself");
				if(!getSession().getPermissions().has("deleteUser"))
					throw new IllegalArgumentException("User " + getSession().getUser()
						+ " does not have permission to delete a user");
				prisms.ui.UI.ConfirmListener cl = new prisms.ui.UI.ConfirmListener()
				{
					public void confirmed(boolean confirm)
					{
						if(!confirm)
							return;
						prisms.arch.ds.ManageableUserSource source;
						source = (prisms.arch.ds.ManageableUserSource) getSession().getApp()
							.getDataSource();
						User [] users = getSession().getProperty(ManagerProperties.users);
						try
						{
							source.deleteUser(user);
						} catch(prisms.arch.PrismsException e)
						{
							throw new IllegalStateException("Could not delete user", e);
						}
						users = prisms.util.ArrayUtils.remove(users, user);
						getSession().setProperty(ManagerProperties.users, users);
					}
				};
				prisms.ui.UI ui = (prisms.ui.UI) getSession().getPlugin("UI");
				if(ui == null)
					cl.confirmed(true);
				else
					ui.confirm("Are you sure you want to delete user " + user.getName(), cl);
			}
		};
		setSelectionMode(SelectionMode.SINGLE);
		setListData(session.getProperty(ManagerProperties.users));
		if(getSession().getPermissions().has("createUser"))
		{
			prisms.ui.list.ActionListNode action = new prisms.ui.list.ActionListNode(this,
				"createNewUser");
			action.setText("*Create User*");
			action.setIcon("manager/user");
			addNode(action, 0);
		}
		if(session.getProperty(ManagerProperties.selectedUser) != null)
			setSelectedObjects(new User [] {session.getProperty(ManagerProperties.selectedUser)});
		session.addPropertyChangeListener(ManagerProperties.users,
			new prisms.arch.event.PrismsPCL<User []>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<User []> evt)
				{
					setListData(evt.getNewValue());
				}
			});
		session.addPropertyChangeListener(ManagerProperties.selectedUser,
			new prisms.arch.event.PrismsPCL<User>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<User> evt)
				{
					if(evt.getNewValue() == null)
						setSelectedObjects(new User [0]);
					else
						setSelectedObjects(new User [] {evt.getNewValue()});
				}
			});
		session.addEventListener("userChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				User user = (User) evt.getProperty("user");
				if(getSession().getUser().getName().equals(user.getName()))
					initClient();// Refresh this tree to take new permissions changes into account
				for(int i = 0; i < getItemCount(); i++)
					if(getItem(i) instanceof prisms.ui.list.SelectableList<?>.ItemNode
						&& ((ItemNode) getItem(i)).getObject().equals(user))
					{
						((ItemNode) getItem(i)).check();
						break;
					}
			}
		});
		session.addEventListener("userPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					if(getSession().getPermissions().has("createUser") && getItemCount() == 0
						|| !(getItem(0) instanceof prisms.ui.list.ActionListNode))
					{
						prisms.ui.list.ActionListNode action = new prisms.ui.list.ActionListNode(
							AllUsersList.this, "createNewUser");
						action.setText("*Create User*");
						action.setIcon("manager/user");
						addNode(action, 0);
					}
					else if(!getSession().getPermissions().has("createUser") && getItemCount() > 0
						&& getItem(0) instanceof prisms.ui.list.ActionListNode)
						removeNode(0);
				}
			});
		session.addEventListener("createNewUser", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(!getSession().getPermissions().has("createUser"))
					throw new IllegalStateException("User " + getSession().getUser()
						+ " does not have permission to create a user");
				prisms.arch.ds.ManageableUserSource source;
				source = (prisms.arch.ds.ManageableUserSource) getSession().getApp()
					.getDataSource();
				User [] users = getSession().getProperty(ManagerProperties.users);
				User newUser;
				try
				{
					newUser = source.createUser(newUserName(users));
				} catch(prisms.arch.PrismsException e)
				{
					throw new IllegalStateException("Could not create user", e);
				}
				getSession().setProperty(ManagerProperties.users,
					prisms.util.ArrayUtils.add(users, newUser));
				doSelect(newUser);
			}
		});
	}

	/**
	 * @see prisms.ui.list.DataListMgrPlugin#processEvent(org.json.simple.JSONObject)
	 */
	@Override
	public void processEvent(org.json.simple.JSONObject evt)
	{
		if("setEditUser".equals(evt.get("method")))
		{
			if(evt.get("user") == null)
			{
				getSession().setProperty(ManagerProperties.selectedUser, null);
				return;
			}
			int i;
			for(i = 0; i < getItemCount(); i++)
				if(getItem(i).getID().equals(evt.get("user")))
				{
					getSession().setProperty(ManagerProperties.selectedUser,
						((ItemNode) getItem(i)).getObject());
					break;
				}
			if(i == getItemCount())
				log.error("No such user: " + evt.get("user"));
		}
		else
			super.processEvent(evt);
	}

	String newUserName(User [] users)
	{
		String ret = "New User";
		if(!userExists(ret, users))
			return ret;
		int count = 1;
		while(userExists(ret + "(" + count + ")", users))
			count++;
		return ret + "(" + count + ")";
	}

	private boolean userExists(String name, User [] users)
	{
		for(int u = 0; u < users.length; u++)
			if(users[u].getName().equals(name))
				return true;
		return false;
	}

	/**
	 * @see prisms.ui.list.SelectableList#createObjectNode(java.lang.Object)
	 */
	@Override
	public ItemNode createObjectNode(User a)
	{
		ItemNode ret = super.createObjectNode(a);
		if(getSession().getPermissions().has("deleteUser") && !getSession().getUser().equals(a))
			ret.addAction(DELETE_USER_ACTION);
		return ret;
	}

	/**
	 * @see prisms.ui.list.SelectableList#getIcon()
	 */
	@Override
	public String getIcon()
	{
		return "manager/user";
	}

	/**
	 * @see prisms.ui.list.SelectableList#getTitle()
	 */
	@Override
	public String getTitle()
	{
		return "All Users";
	}

	/**
	 * @see prisms.ui.list.SelectableList#getItemIcon(java.lang.Object)
	 */
	@Override
	public String getItemIcon(User obj)
	{
		return "manager/user";
	}

	/**
	 * @see prisms.ui.list.SelectableList#getItemName(java.lang.Object)
	 */
	@Override
	public String getItemName(User obj)
	{
		return obj.getName();
	}

	@Override
	public boolean canSelect(User a)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#doSelect(java.lang.Object)
	 */
	@Override
	public void doSelect(User a)
	{
		getSession().setProperty(ManagerProperties.selectedUser, a);
	}

	@Override
	public boolean canDeselect(User a)
	{
		return true;
	}

	/**
	 * @see prisms.ui.list.SelectableList#doDeselect(java.lang.Object)
	 */
	@Override
	public void doDeselect(User a)
	{
		getSession().setProperty(ManagerProperties.selectedUser, null);
	}
}
