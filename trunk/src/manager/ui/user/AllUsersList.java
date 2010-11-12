/**
 * AllUsersList.java Created Jul 10, 2008 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;

import prisms.arch.PrismsException;
import prisms.arch.PrismsSession;
import prisms.arch.ds.ManageableUserSource;
import prisms.arch.ds.User;
import prisms.arch.event.PrismsProperties;
import prisms.ui.list.NodeAction;
import prisms.util.ArrayUtils;

/** Displays the list of all users to the user and allows user creation/deletion/selection */
public class AllUsersList extends prisms.ui.list.SelectableList<User>
{
	static final Logger log = Logger.getLogger(AllUsersList.class);

	private javax.swing.Action DELETE_USER_ACTION;

	private javax.swing.Action CLONE_USER_ACTION;

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
				if(!manager.app.ManagerUtils.canEdit(getSession().getPermissions(),
					user.getPermissions(getSession().getApp())))
					throw new IllegalArgumentException("User " + getSession().getUser()
						+ " does not have permission to delete a user");
				prisms.ui.UI.ConfirmListener cl = new prisms.ui.UI.ConfirmListener()
				{
					public void confirmed(boolean confirm)
					{
						if(!confirm)
							return;
						ManageableUserSource source = (ManageableUserSource) getSession().getApp()
							.getEnvironment().getUserSource();
						User [] users = getSession().getProperty(PrismsProperties.users);
						try
						{
							source.deleteUser(user);
						} catch(prisms.arch.PrismsException e)
						{
							throw new IllegalStateException("Could not delete user", e);
						}
						users = prisms.util.ArrayUtils.remove(users, user);
						getSession().setProperty(PrismsProperties.users, users);
					}
				};
				prisms.ui.UI ui = (prisms.ui.UI) getSession().getPlugin("UI");
				if(ui == null)
					cl.confirmed(true);
				else
					ui.confirm("Are you sure you want to delete user " + user.getName(), cl);
			}
		};
		CLONE_USER_ACTION = new javax.swing.AbstractAction("Clone")
		{
			public void actionPerformed(java.awt.event.ActionEvent evt)
			{
				if(!getSession().getPermissions().has("createUser"))
					throw new IllegalStateException("User " + getSession().getUser()
						+ " does not have permission to create a user");
				ManageableUserSource source = (ManageableUserSource) getSession().getApp()
					.getEnvironment().getUserSource();
				User user = ((ItemNode) evt.getSource()).getObject();
				final User [] users = getSession().getProperty(PrismsProperties.users);
				User newUser;
				try
				{
					newUser = source.createUser(manager.app.ManagerUtils.getCopyName(
						user.getName(), new manager.app.ManagerUtils.NameChecker()
						{
							public boolean nameExists(String name)
							{
								return userExists(name, users);
							}
						}));
				} catch(prisms.arch.PrismsException e)
				{
					throw new IllegalStateException("Could not create user", e);
				}
				for(prisms.arch.PrismsApplication app : getSession().getProperty(
					prisms.arch.event.PrismsProperties.applications))
				{
					try
					{
						if(source.canAccess(user, app))
							source.setUserAccess(newUser, app, true);
					} catch(PrismsException e)
					{
						log.error("Could not give new user \"" + newUser.getName()
							+ "\" access to application " + app.getName(), e);
					}
				}
				for(prisms.arch.ds.UserGroup group : user.getGroups())
					newUser.addTo(group);
				getSession().setProperty(PrismsProperties.users,
					prisms.util.ArrayUtils.add(users, newUser));
				doSelect(newUser);
			}
		};
		setSelectionMode(SelectionMode.SINGLE);
		setListData(session.getProperty(PrismsProperties.users));
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
		session.addPropertyChangeListener(PrismsProperties.users,
			new prisms.arch.event.PrismsPCL<User []>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<User []> evt)
				{
					setListData(evt.getNewValue());
					if(!prisms.util.ArrayUtils.contains(evt.getNewValue(), getSession()
						.getProperty(ManagerProperties.selectedUser)))
						getSession().setProperty(ManagerProperties.selectedUser, null);
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
		session.addEventListener("prismsUserChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
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
				public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
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
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(!getSession().getPermissions().has("createUser"))
					throw new IllegalStateException("User " + getSession().getUser()
						+ " does not have permission to create a user");
				ManageableUserSource source = (ManageableUserSource) getSession().getApp()
					.getEnvironment().getUserSource();
				final User [] users = getSession().getProperty(PrismsProperties.users);
				User newUser;
				try
				{
					newUser = source.createUser(manager.app.ManagerUtils.newName("New User",
						new manager.app.ManagerUtils.NameChecker()
						{
							public boolean nameExists(String name)
							{
								return userExists(name, users);
							}
						}));
				} catch(prisms.arch.PrismsException e)
				{
					throw new IllegalStateException("Could not create user", e);
				}
				getSession().setProperty(PrismsProperties.users,
					prisms.util.ArrayUtils.add(users, newUser));
				doSelect(newUser);
			}
		});
	}

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

	boolean userExists(String name, User [] users)
	{
		for(int u = 0; u < users.length; u++)
			if(users[u].getName().equals(name))
				return true;
		return false;
	}

	@Override
	public ItemNode createObjectNode(User a)
	{
		ItemNode ret = super.createObjectNode(a);
		if(manager.app.ManagerUtils.canEdit(getSession().getPermissions(),
			a.getPermissions(getSession().getApp()))
			&& !getSession().getUser().equals(a))
			ret.addAction(DELETE_USER_ACTION);
		if(manager.app.ManagerUtils.canEdit(getSession().getPermissions(),
			a.getPermissions(getSession().getApp())))
			ret.addAction(CLONE_USER_ACTION);
		return ret;
	}

	@Override
	public NodeAction [] getActions()
	{
		NodeAction [] ret = super.getActions();
		if(getSession().getApp().getEnvironment().getUserSource() instanceof ManageableUserSource)
		{
			if(getSession().getPermissions().has("createUser"))
			{
				ret = prisms.util.ArrayUtils
					.add(ret, new NodeAction("Restore Deleted User", false));
				// ret = prisms.util.ArrayUtils.add(ret, new NodeAction("Purge Deleted User",
				// false));
			}
		}
		return ret;
	}

	@Override
	public void doAction(String action)
	{
		if("Restore Deleted User".equals(action) || "Purge Deleted User".equals(action))
		{
			final boolean restore = "Restore Deleted User".equals(action);
			if(!getSession().getPermissions().has("createUser"))
			{
				if(restore)
					throw new IllegalArgumentException(
						"You do not have permission to restore deleted users");
				else
					throw new IllegalArgumentException(
						"You do not have permission to purge deleted users");
			}
			final ManageableUserSource mus = (ManageableUserSource) getSession().getApp()
				.getEnvironment().getUserSource();
			User [] allUsers;
			try
			{
				allUsers = mus.getAllUsers();
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not get all users", e);
			}
			final java.util.ArrayList<User> deletedUsers = new java.util.ArrayList<User>();
			for(User u : allUsers)
			{
				if(!getSession().getApp().getEnvironment().getUserSource().getIDs()
					.belongs(u.getID()))
					continue;
				if(prisms.util.ArrayUtils.contains(
					getSession().getProperty(PrismsProperties.users), u))
					continue;
				deletedUsers.add(u);
			}
			prisms.ui.UI ui = (prisms.ui.UI) getSession().getPlugin("UI");
			if(deletedUsers.size() == 0)
			{
				if(restore)
					ui.info("No deleted users to restore");
				else
					ui.info("No deleted users to purge");
				return;
			}
			final String [] options = new String [deletedUsers.size()];
			for(int i = 0; i < options.length; i++)
			{
				String name = deletedUsers.get(i).getName();
				for(int j = 2; prisms.util.ArrayUtils.contains(options, name); j++)
					name = deletedUsers.get(i).getName() + " (" + j + ")";
				options[i] = name;
			}
			ui.select("Which user would you like to restore?", options, 0,
				new prisms.ui.UI.SelectListener()
				{
					public void selected(String option)
					{
						if(option == null)
							return;
						int idx = prisms.util.ArrayUtils.indexOf(options, option);
						User user = deletedUsers.get(idx);
						if(restore)
						{
							user.setDeleted(false);
							getSession().setProperty(
								PrismsProperties.users,
								ArrayUtils.add(getSession().getProperty(PrismsProperties.users),
									user));
						}
						// else
						// mus.purgeUser(user);
					}
				});
		}
		else
			super.doAction(action);
	}

	@Override
	public String getIcon()
	{
		return "manager/user";
	}

	@Override
	public String getTitle()
	{
		return "All Users";
	}

	@Override
	public String getItemIcon(User obj)
	{
		return "manager/user";
	}

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

	@Override
	public void doDeselect(User a)
	{
		getSession().setProperty(ManagerProperties.selectedUser, null);
	}
}
