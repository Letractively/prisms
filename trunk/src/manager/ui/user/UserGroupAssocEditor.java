/**
 * UserGroupAssoc.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.arch.ds.ManageableUserSource;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;

/**
 * Allows the user to manage the association of a user with a group in an application
 */
public class UserGroupAssocEditor implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(UserGroupAssocEditor.class);

	PrismsSession theSession;

	private String theName;

	User theUser;

	UserGroup theGroup;

	boolean theDataLock;

	/**
	 * @see prisms.arch.AppPlugin#initPlugin(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		User user = session.getProperty(ManagerProperties.selectedUser);
		UserGroup group = session.getProperty(ManagerProperties.userSelectedGroup);
		setUserGroup(user, group);
		session.addPropertyChangeListener(ManagerProperties.selectedUser,
			new prisms.arch.event.PrismsPCL<User>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<User> evt)
				{
					setUserGroup(evt.getNewValue(), theGroup);
				}
			});
		session.addEventListener("userChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(theDataLock)
					return;
				User user2 = (User) evt.getProperty("user");
				if(user2.equals(theUser))
					setUserGroup(user2, theGroup);
			}
		});
		session.addEventListener("userPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					if(theSession.getUser().getName().equals(
						((User) evt.getProperty("user")).getName()))
						setUserGroup(theUser, theGroup);
				}
			});
		session.addPropertyChangeListener(ManagerProperties.userSelectedGroup,
			new prisms.arch.event.PrismsPCL<UserGroup>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<UserGroup> evt)
				{
					setUserGroup(theUser, evt.getNewValue());
				}
			});
		session.addEventListener("groupChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(theDataLock)
					return;
				UserGroup group2 = (UserGroup) evt.getProperty("group");
				if(group2.equals(theGroup))
					setUserGroup(theUser, group2);
			}
		});
	}

	/**
	 * @see prisms.arch.AppPlugin#initClient()
	 */
	public void initClient()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setEnabled");
		evt.put("enabled", new Boolean(theUser != null
			&& theGroup != null
			&& !(theGroup.getApp() == theSession.getApp()
				&& theUser.getName().equals(theSession.getUser().getName()) && theGroup.getName()
				.equals("userAdmin"))
			&& manager.app.ManagerUtils.canEdit(theSession.getPermissions(), theUser
				.getPermissions(theSession.getApp()))));
		theSession.postOutgoingEvent(evt);
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setData");
		evt.put("user", theUser == null ? null : theUser.getName());
		evt.put("app", theGroup == null ? null : theGroup.getApp().getName());
		JSONObject group = null;
		if(theGroup != null)
		{
			group = new JSONObject();
			group.put("name", theGroup.getName());
			group.put("descrip", theGroup.getDescription());
			group.put("selected", new Boolean(userHasGroup()));
		}
		evt.put("group", group);
		theSession.postOutgoingEvent(evt);
	}

	boolean userHasGroup()
	{
		if(theUser == null)
			return false;
		return prisms.util.ArrayUtils.contains(theUser.getGroups(), theGroup);
	}

	/**
	 * @see prisms.arch.AppPlugin#processEvent(org.json.simple.JSONObject)
	 */
	public void processEvent(JSONObject evt)
	{
		if("membershipChanged".equals(evt.get("method")))
		{
			membershipChanged(((Boolean) evt.get("isMember")).booleanValue());
			if(userHasGroup())
				log.info("User " + theSession.getUser() + " granted membership in group "
					+ theGroup + " of application " + theGroup.getApp() + " to user " + theUser);
			else
				log.info("User " + theSession.getUser() + " revoked membership in group "
					+ theGroup + " of application " + theGroup.getApp() + " from user " + theUser);
		}
		else
			throw new IllegalStateException("Unrecognized " + theName + " event " + evt);
	}

	void setUserGroup(User user, UserGroup group)
	{
		theUser = user;
		theGroup = group;
		initClient();
	}

	void membershipChanged(boolean isMember)
	{
		if(theUser == null || theGroup == null)
			return;
		if(isMember == prisms.util.ArrayUtils.contains(theUser.getGroups(), theGroup))
			return;
		prisms.arch.ds.UserSource us = theSession.getApp().getDataSource();
		if(!(us instanceof ManageableUserSource))
			throw new IllegalStateException(
				"Cannot modify user group membership--user source is not manageable");
		if(!manager.app.ManagerUtils.canEdit(theSession.getPermissions(), theUser
			.getPermissions(theSession.getApp())))
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " cannot modify user " + theUser.getName() + "'s permissions");
		if(isMember && theGroup.getApp() == theSession.getApp()
			&& theUser.getName().equals(theSession.getUser().getName())
			&& theGroup.getName().equals("userAdmin"))
			throw new IllegalStateException("A user cannot remove himself from the userAdmin group");
		if(isMember)
			theUser.addTo(theGroup);
		else
			theUser.removeFrom(theGroup);
		try
		{
			((ManageableUserSource) us).putUser(theUser);
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not edit group membership", e);
		}
		theDataLock = true;
		try
		{
			theSession.fireEvent(new prisms.arch.event.PrismsEvent("userGroupsChanged", "user",
				theUser));
			theSession.fireEvent(new prisms.arch.event.PrismsEvent("userPermissionsChanged",
				"user", theUser));
		} finally
		{
			theDataLock = false;
		}
	}
}
