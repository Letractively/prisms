/*
 * UserPermissionDisplay.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;

import org.json.simple.JSONObject;

import prisms.arch.Permission;
import prisms.arch.PrismsSession;

/** Displays the description of the selected permission in the user editor */
public class UserPermissionDisplay implements prisms.arch.AppPlugin
{
	private PrismsSession theSession;

	private String theName;

	Permission thePermission;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		Permission perm = session.getProperty(ManagerProperties.userSelectedPermission);
		setPermission(perm);
		session.addPropertyChangeListener(ManagerProperties.userSelectedPermission,
			new prisms.arch.event.PrismsPCL<Permission>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<Permission> evt)
				{
					setPermission(evt.getNewValue());
				}

				@Override
				public String toString()
				{
					return "Manager User Permission Selection Updater";
				}
			});
		session.addEventListener("permissionChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				Permission perm2 = (Permission) evt.getProperty("permission");
				if(perm2.equals(thePermission))
					setPermission(perm2);
			}

			@Override
			public String toString()
			{
				return "Manager User Permission Content Updater";
			}
		});
	}

	public void initClient()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setData");
		JSONObject perm = null;
		if(thePermission != null)
		{
			perm = new JSONObject();
			perm.put("name", thePermission.getName());
			perm.put("descrip", thePermission.getDescrip());
		}
		evt.put("permission", perm);
		theSession.postOutgoingEvent(evt);
	}

	public void processEvent(JSONObject evt)
	{
		throw new IllegalStateException("Unrecognized " + theName + " event " + evt);
	}

	void setPermission(Permission perm)
	{
		thePermission = perm;
		initClient();
	}
}
