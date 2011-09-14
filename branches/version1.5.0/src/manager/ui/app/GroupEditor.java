/**
 * ApplicationEditor.java Created Oct 1, 2008 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;

/**
 * Allows the user to edit properties of an application
 */
public class GroupEditor implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(GroupEditor.class);

	prisms.arch.PrismsSession theSession;

	private String theName;

	UserGroup theGroup;

	boolean theDataLock;

	/**
	 * @see prisms.arch.AppPlugin#initPlugin(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void initPlugin(prisms.arch.PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		prisms.arch.ds.UserSource us = session.getApp().getDataSource();
		if(!(us instanceof prisms.arch.ds.ManageableUserSource))
			log.warn("User source is not manageable");
		theGroup = theSession.getProperty(ManagerProperties.selectedAppGroup);
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
				if(theDataLock)
					return;
				setGroup((UserGroup) evt.getProperty("group"));
			}
		});
		session.addEventListener("userPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					if(theSession.getUser().getName().equals(
						((User) evt.getProperty("user")).getName()))
					{
						setGroup(theGroup);
					}
				}
			});
	}

	/**
	 * @see prisms.arch.AppPlugin#initClient()
	 */
	public void initClient()
	{
		boolean visible = theGroup != null;
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setVisible");
		evt.put("visible", new Boolean(visible));
		theSession.postOutgoingEvent(evt);
		if(!visible)
			return;
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setEnabled");
		evt.put("enabled", new Boolean(isEditable()));
		theSession.postOutgoingEvent(evt);
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setValue");
		JSONObject val = new JSONObject();
		val.put("name", theGroup.getName());
		val.put("descrip", theGroup.getDescription());
		evt.put("value", val);
		theSession.postOutgoingEvent(evt);
	}

	/**
	 * @see prisms.arch.AppPlugin#processEvent(org.json.simple.JSONObject)
	 */
	public void processEvent(JSONObject evt)
	{
		if("nameChanged".equals(evt.get("method")))
		{
			assertEditable();
			String newName = (String) evt.get("name");
			if(newName == null)
				throw new IllegalArgumentException("No name to set");
			log.info("User " + theSession.getUser() + " changing name of group " + theGroup
				+ " in application " + theGroup.getApp() + " to " + newName);
			prisms.arch.ds.ManageableUserSource source;
			source = (prisms.arch.ds.ManageableUserSource) theSession.getApp().getDataSource();
			try
			{
				source.rename(theGroup, newName);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify group", e);
			}
			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("groupChanged", "group",
					theGroup));
			} finally
			{
				theDataLock = false;
			}
		}
		else if("descripChanged".equals(evt.get("method")))
		{
			assertEditable();
			String newDescrip = (String) evt.get("descrip");
			if(newDescrip == null)
				throw new IllegalArgumentException("No description to set");
			log.info("User " + theSession.getUser() + " changing description of group " + theGroup
				+ " in application " + theGroup.getApp() + " to " + newDescrip);
			prisms.arch.ds.ManageableUserSource source;
			source = (prisms.arch.ds.ManageableUserSource) theSession.getApp().getDataSource();
			try
			{
				source.setDescription(theGroup, newDescrip);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify group", e);
			}
			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("groupChanged", "group",
					theGroup));
			} finally
			{
				theDataLock = false;
			}
		}
		else
			throw new IllegalArgumentException("Unrecognized event " + evt);
	}

	void setGroup(UserGroup group)
	{
		if(theGroup == null && group == null)
			return;
		theGroup = group;
		initClient();
	}

	boolean isEditable()
	{
		return theGroup != null && manager.app.ManagerUtils.canEdit(theSession.getUser(), theGroup);
	}

	void assertEditable()
	{
		if(theGroup == null)
			throw new IllegalArgumentException("No group selected to edit");
		if(!manager.app.ManagerUtils.canEdit(theSession.getUser(), theGroup))
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to edit group " + theGroup.getName());
	}
}