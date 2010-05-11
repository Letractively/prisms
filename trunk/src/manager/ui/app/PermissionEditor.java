/**
 * UserGroupAssoc.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.arch.ds.*;

/**
 * Allows the user to manage the association of a user with a group in an application
 */
public class PermissionEditor implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(PermissionEditor.class);

	PrismsSession theSession;

	private String theName;

	Permission thePermission;

	UserGroup theGroup;

	boolean theDataLock;

	/**
	 * @see prisms.arch.AppPlugin#initPlugin(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		Permission p = session.getProperty(ManagerProperties.selectedAppPermission);
		UserGroup group = session.getProperty(ManagerProperties.selectedAppGroup);
		setPermissionGroup(p, group);
		session.addPropertyChangeListener(ManagerProperties.selectedAppPermission,
			new prisms.arch.event.PrismsPCL<Permission>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<Permission> evt)
				{
					setPermissionGroup(evt.getNewValue(), theGroup);
				}
			});
		session.addEventListener("permissionChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(theDataLock)
					return;
				Permission p2 = (Permission) evt.getProperty("permission");
				if(p2.equals(thePermission))
					setPermissionGroup(p2, theGroup);
			}
		});
		session.addEventListener("userPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					if(theSession.getUser().getName().equals(
						((User) evt.getProperty("user")).getName()))
						setPermissionGroup(thePermission, theGroup);
				}
			});
		session.addPropertyChangeListener(ManagerProperties.selectedAppGroup,
			new prisms.arch.event.PrismsPCL<UserGroup>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<UserGroup> evt)
				{
					setPermissionGroup(thePermission, evt.getNewValue());
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
					setPermissionGroup(thePermission, group2);
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
		evt.put("enabled", new Boolean(thePermission != null
			&& manager.app.ManagerUtils.canEdit(theSession.getPermissions(), thePermission)));
		theSession.postOutgoingEvent(evt);
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setGroupCheckEnabled");
		evt.put("enabled", new Boolean(thePermission != null && theGroup != null
			&& manager.app.ManagerUtils.canEdit(theSession.getPermissions(), theGroup)));
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setData");
		JSONObject data = null;
		if(thePermission != null)
		{
			data = new JSONObject();
			data.put("name", thePermission.getName());
			data.put("description", thePermission.getDescrip());
			JSONObject group = null;
			if(theGroup != null)
			{
				group = new JSONObject();
				group.put("name", theGroup.getName());
				group.put("selected", new Boolean(theGroup.getPermissions().has(
					thePermission.getName())));
			}
			data.put("group", group);
		}
		evt.put("data", data);
		theSession.postOutgoingEvent(evt);
	}

	/**
	 * @see prisms.arch.AppPlugin#processEvent(org.json.simple.JSONObject)
	 */
	public void processEvent(JSONObject evt)
	{
		if("descripChanged".equals(evt.get("method")))
		{
			if(thePermission == null
				|| !manager.app.ManagerUtils.canEdit(theSession.getPermissions(), thePermission))
				throw new IllegalArgumentException(theSession.getUser()
					+ " does not have permission to change permission " + thePermission.getName());
			String newDescrip = (String) evt.get("descrip");
			if(newDescrip == null)
				throw new IllegalArgumentException("No description to set");
			log
				.info("User " + theSession.getUser() + " changing description of permission "
					+ thePermission + " in application " + thePermission.getApp() + " to "
					+ newDescrip);
			prisms.arch.ds.ManageableUserSource source;
			source = (prisms.arch.ds.ManageableUserSource) theSession.getApp().getDataSource();
			thePermission.setDescrip(newDescrip);
			try
			{
				source.putPermission(thePermission);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify permission", e);
			}
			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("permissionChanged",
					"permission", thePermission));
			} finally
			{
				theDataLock = false;
			}
		}
		else if("membershipChanged".equals(evt.get("method")))
		{
			membershipChanged(((Boolean) evt.get("isMember")).booleanValue());
			if(theGroup.getPermissions().has(thePermission.getName()))
				log.info("User " + theSession.getUser() + " granted permission " + thePermission
					+ " to group " + theGroup + " in application " + theGroup.getApp());
		}
		else
			throw new IllegalStateException("Unrecognized " + theName + " event " + evt);
	}

	void setPermissionGroup(Permission permission, UserGroup group)
	{
		thePermission = permission;
		theGroup = group;
		initClient();
	}

	void membershipChanged(boolean isMember)
	{
		if(thePermission == null || theGroup == null)
			return;
		if(isMember == theGroup.getPermissions().has(thePermission.getName()))
			return;
		prisms.arch.ds.UserSource us = theSession.getApp().getDataSource();
		if(!(us instanceof ManageableUserSource))
			throw new IllegalStateException(
				"Cannot modify group permission membership--user source is not manageable");
		if(!manager.app.ManagerUtils.canEdit(theSession.getPermissions(), theGroup))
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " cannot modify group " + theGroup.getName());
		if(isMember && theGroup.getApp() == theSession.getApp()
			&& theGroup.getName().equals("userAdmin"))
			throw new IllegalStateException(
				"The userAdmin group cannot be modified through this interface");
		User [] users = theSession.getProperty(ManagerProperties.users);
		users = users.clone();
		for(int u = 0; u < users.length; u++)
		{
			if(!prisms.util.ArrayUtils.contains(users[u].getGroups(), theGroup))
			{
				users = prisms.util.ArrayUtils.remove(users, u);
				u--;
			}
		}
		try
		{
			if(isMember)
				theGroup.getPermissions().addPermission(thePermission);
			else
				theGroup.getPermissions().removePermission(thePermission.getName());
			((ManageableUserSource) us).putGroup(theGroup);
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not modify group-permission relationship", e);
		}
		theDataLock = true;
		try
		{
			theSession.fireEvent(new prisms.arch.event.PrismsEvent("groupPermissionsChanged",
				"group", theGroup));
			for(int u = 0; u < users.length; u++)
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("userPermissionsChanged",
					"user", users[u]));
		} finally
		{
			theDataLock = false;
		}
	}
}
