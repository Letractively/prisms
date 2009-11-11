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

/**
 * Allows the user to manage the association of a user with an application
 */
public class UserAppAssocEditor implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(UserAppAssocEditor.class);

	private PrismsSession theSession;

	private String theName;

	User theUser;

	PrismsApplication theApp;

	User theAppUser;

	/**
	 * @see prisms.arch.AppPlugin#initPlugin(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
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
			&& theApp != null
			&& !(theApp == theSession.getApp() && theUser.getName().equals(
				theSession.getUser().getName()))
			&& manager.app.ManagerUtils.canEdit(theSession.getUser(), theUser)));
		theSession.postOutgoingEvent(evt);
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setData");
		evt.put("user", theUser == null ? null : theUser.getName());
		evt.put("app", theApp == null ? null : theApp.getName());
		evt.put("accessible", new Boolean(theAppUser != null));
		evt.put("encrypted", new Boolean(theAppUser != null && theAppUser.isEncryptionRequired()));
		theSession.postOutgoingEvent(evt);
	}

	/**
	 * @see prisms.arch.AppPlugin#processEvent(org.json.simple.JSONObject)
	 */
	public void processEvent(JSONObject evt)
	{
		if("accessChanged".equals(evt.get("method")))
		{
			changeAccess(((Boolean) evt.get("accessible")).booleanValue());
			if(theAppUser != null)
				log.info("User " + theSession.getUser() + " granted application " + theApp
					+ " access to user " + theUser);
			else
				log.info("User " + theSession.getUser() + " denied application " + theApp
					+ " access from user " + theUser);
		}
		else if("encryptedChanged".equals(evt.get("method")))
		{
			changeEncrypted(((Boolean) evt.get("encrypted")).booleanValue());
			log.info("User " + theSession.getUser() + " set encryption requirement on application "
				+ theApp + " for user " + theUser + " to " + theAppUser.isEncryptionRequired());
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}

	void setUserApp(User user, PrismsApplication app)
	{
		theUser = user;
		theApp = app;
		if(user == null || app == null)
			theAppUser = null;
		else if(user.getApp() != null && user.getApp().equals(app))
			theAppUser = user;
		else
			theAppUser = theSession.getApp().getDataSource().getUser(user, app);
		initClient();
	}

	void changeAccess(boolean accessible)
	{
		if(!manager.app.ManagerUtils.canEdit(theSession.getUser(), theUser))
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to modify user " + theUser
				+ "'s access to application " + theApp.getName());
		if(accessible == (theAppUser != null))
			return;
		if(theSession.getUser().getName().equals(theUser.getName())
			&& theApp == theSession.getApp())
			throw new IllegalArgumentException("A user cannot disallow his/her own access"
				+ " to the manager application");
		prisms.arch.ds.UserSource us = theSession.getApp().getDataSource();
		if(!(us instanceof ManageableUserSource))
			throw new IllegalStateException(
				"Cannot modify user access--user source is not manageable");
		if(accessible)
		{
			((ManageableUserSource) us).setUserAccess(theUser, theApp, accessible);
			theAppUser = us.getUser(theUser, theApp);
		}
		else
		{
			((ManageableUserSource) us).setUserAccess(theUser, theApp, accessible);
			theAppUser = null;
		}
		theSession.fireEvent(new prisms.arch.event.PrismsEvent("appChanged", "app", theApp));
		theSession.fireEvent(new prisms.arch.event.PrismsEvent("userChanged", "user", theUser));
	}

	void changeEncrypted(boolean encrypted)
	{
		if(theAppUser == null)
			throw new IllegalStateException(
				"Cannot modify encryption requirements--user cannot access application");
		if(!manager.app.ManagerUtils.canEdit(theSession.getUser(), theUser))
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to modify user " + theUser
				+ "'s encryption requirements for application " + theApp.getName());
		if(encrypted == theAppUser.isEncryptionRequired())
			return;
		prisms.arch.ds.UserSource us = theSession.getApp().getDataSource();
		if(!(us instanceof ManageableUserSource))
			throw new IllegalStateException(
				"Cannot modify user access--user source is not manageable");
		((ManageableUserSource) us).setEncryptionRequired(theUser, theApp, encrypted);
		theSession.fireEvent(new prisms.arch.event.PrismsEvent("userChanged", "user", theUser));
	}
}
