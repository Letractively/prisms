/*
 * PermissionEditor.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.Permission;
import prisms.arch.PrismsSession;
import prisms.arch.ds.ManageableUserSource;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;

/** Allows the user to view a permission in an application */
public class PermissionEditor implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(PermissionEditor.class);

	PrismsSession theSession;

	private String theName;

	Permission thePermission;

	UserGroup theGroup;

	boolean theDataLock;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
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
		session.addPropertyChangeListener(ManagerProperties.selectedAppGroup,
			new prisms.arch.event.PrismsPCL<UserGroup>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<UserGroup> evt)
				{
					setPermissionGroup(thePermission, evt.getNewValue());
				}

				@Override
				public String toString()
				{
					return "Manager Group Permission Selection Updater";
				}
			});
		session.addEventListener("groupChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(theDataLock)
					return;
				UserGroup group2 = (UserGroup) evt.getProperty("group");
				if(group2.equals(theGroup))
					setPermissionGroup(thePermission, group2);
			}

			@Override
			public String toString()
			{
				return "Manager Group Permission Edit Updater";
			}
		});
	}

	public void initClient()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setEnabled");
		evt.put(
			"enabled",
			Boolean.valueOf(thePermission != null
				&& manager.app.ManagerUtils.canEdit(theSession.getPermissions(), thePermission)));
		theSession.postOutgoingEvent(evt);
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setGroupCheckEnabled");
		evt.put(
			"enabled",
			Boolean.valueOf(thePermission != null && theGroup != null
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
				group.put("selected",
					Boolean.valueOf(theGroup.getPermissions().has(thePermission.getName())));
			}
			data.put("group", group);
		}
		evt.put("data", data);
		theSession.postOutgoingEvent(evt);
	}

	public void processEvent(JSONObject evt)
	{
		if("membershipChanged".equals(evt.get("method")))
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
		prisms.arch.ds.UserSource us = theSession.getApp().getEnvironment().getUserSource();
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
		User [] users = theSession.getProperty(prisms.arch.event.PrismsProperties.users);
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
			((ManageableUserSource) us).putGroup(theGroup, new prisms.records.RecordsTransaction(
				theSession.getUser()));
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not modify group-permission relationship", e);
		}
		theDataLock = true;
		try
		{
			theSession.fireEvent("groupChanged", "group", theGroup);
		} finally
		{
			theDataLock = false;
		}
	}
}
