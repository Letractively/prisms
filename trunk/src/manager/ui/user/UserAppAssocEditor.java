/**
 * UserAppAssocEditor.java Created Feb 23, 2009 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.arch.ds.ManageableUserSource;
import prisms.arch.ds.User;

/** Allows the user to manage the association of a user with an application */
public class UserAppAssocEditor implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(UserAppAssocEditor.class);

	private PrismsSession theSession;

	private String theName;

	User theUser;

	PrismsApplication theApp;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
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
					return "Manager User/App Assoc User Changer";
				}
			});
		session.addEventListener("prismsUserChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				User user2 = (User) evt.getProperty("user");
				if(user2.equals(theUser))
					setUserApp(user2, theApp);
			}

			@Override
			public String toString()
			{
				return "Manager User/App Assoc Viewability Updater";
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
					return "Manager User/App Assoc App Changer";
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
				return "Manager User/App Assoc App Updater";
			}
		});
	}

	public void initClient()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setEnabled");
		evt.put("enabled", Boolean.valueOf(isEnabled()));
		theSession.postOutgoingEvent(evt);
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setData");
		evt.put("user", theUser == null ? null : theUser.getName());
		evt.put("app", theApp == null ? null : theApp.getName());
		evt.put("accessible", Boolean.valueOf(isAccessible()));
		theSession.postOutgoingEvent(evt);
	}

	private boolean isEnabled()
	{
		if(theUser == null || theApp == null)
			return false;
		if(theUser.isReadOnly())
			return false;
		if(theApp == theSession.getApp()
			&& theUser.getName().equals(theSession.getUser().getName()))
			return false;
		if(!manager.app.ManagerUtils.canEdit(theSession.getPermissions(),
			theUser.getPermissions(theSession.getApp())))
			return false;
		return true;
	}

	public void processEvent(JSONObject evt)
	{
		if("accessChanged".equals(evt.get("method")))
		{
			changeAccess(((Boolean) evt.get("accessible")).booleanValue());
			if(isAccessible())
				log.info("User " + theSession.getUser() + " granted application " + theApp
					+ " access to user " + theUser);
			else
				log.info("User " + theSession.getUser() + " denied application " + theApp
					+ " access from user " + theUser);
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}

	boolean isAccessible()
	{
		if(theUser == null || theApp == null)
			return false;
		try
		{
			return theSession.getApp().getEnvironment().getUserSource().canAccess(theUser, theApp);
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not determine application accessibility", e);
		}
	}

	void setUserApp(User user, PrismsApplication app)
	{
		theUser = user;
		theApp = app;
		initClient();
	}

	void changeAccess(boolean accessible)
	{
		if(!manager.app.ManagerUtils.canEdit(theSession.getPermissions(),
			theUser.getPermissions(theSession.getApp())))
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to modify user " + theUser
				+ "'s access to application " + theApp.getName());
		if(accessible == isAccessible())
			return;
		if(theUser.isReadOnly())
			throw new IllegalStateException("User " + theUser + " is read-only");
		if(theSession.getUser().getName().equals(theUser.getName())
			&& theApp == theSession.getApp())
			throw new IllegalArgumentException("A user cannot disallow his/her own access"
				+ " to the manager application");
		prisms.arch.ds.UserSource us = theSession.getApp().getEnvironment().getUserSource();
		if(!(us instanceof ManageableUserSource))
			throw new IllegalStateException(
				"Cannot modify user access--user source is not manageable");
		try
		{
			((ManageableUserSource) us).setUserAccess(theUser, theApp, accessible,
				new prisms.records.RecordsTransaction(theSession.getUser()));
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not set user-application accessibility", e);
		}
		theSession.fireEvent(new prisms.arch.event.PrismsEvent("appChanged", "app", theApp));
		theSession
			.fireEvent(new prisms.arch.event.PrismsEvent("prismsUserChanged", "user", theUser));
	}
}
