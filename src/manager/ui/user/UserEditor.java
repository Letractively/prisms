/**
 * UserEditor.java Created Jul 21, 2008 by Andrew Butler, PSL
 */
package manager.ui.user;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.arch.ds.User;

/** Allows the user to edit a user's name, password, etc. */
public class UserEditor extends Object implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(UserEditor.class);

	private static final long MAX_CHANGE_PASSWORD_REQUEST_WAIT = 5L * 60 * 1000;

	private PrismsSession theSession;

	private String theName;

	User theUser;

	private prisms.arch.ds.Hashing theValidationParams;

	private long theValidationRequestTime;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		theUser = theSession.getProperty(ManagerProperties.selectedUser);
		session.addPropertyChangeListener(ManagerProperties.selectedUser,
			new prisms.arch.event.PrismsPCL<User>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<User> evt)
				{
					setUser(evt.getNewValue());
				}

				@Override
				public String toString()
				{
					return "Manager User Editor Selection Updater";
				}
			});
		session.addEventListener("prismsUserChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				User user = (User) evt.getProperty("user");
				if(theUser != null && theUser.equals(user))
					initClient();
			}

			@Override
			public String toString()
			{
				return "Manager User Editor Viewability Updater";
			}
		});
	}

	public void initClient()
	{
		boolean visible = theUser != null;
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setVisible");
		evt.put("visible", Boolean.valueOf(visible));
		theSession.postOutgoingEvent(evt);
		if(!visible)
			return;

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setEnabled");
		evt.put("enabled", Boolean.valueOf(isEditable()));
		theSession.postOutgoingEvent(evt);

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setReadOnly");
		evt.put("readOnly", Boolean.valueOf(theUser.isReadOnly()));
		theSession.postOutgoingEvent(evt);

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setValue");
		JSONObject val = new JSONObject();
		val.put("name", theUser.getName());
		long expiration;
		try
		{
			expiration = theSession.getApp().getEnvironment().getUserSource()
				.getPasswordExpiration(theUser);
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not get user's password expiration", e);
		}
		if(expiration < Long.MAX_VALUE)
			val.put("passwordExpiration", Long.valueOf(expiration));
		else
			val.put("passwordExpiration", null);
		val.put("locked", Boolean.valueOf(theUser.isLocked()));
		evt.put("value", val);
		theSession.postOutgoingEvent(evt);
	}

	public void processEvent(JSONObject evt)
	{
		if("nameChanged".equals(evt.get("method")))
		{
			assertEditable();
			String newName = (String) evt.get("name");
			log.info("User " + theSession.getUser() + " changing name of user " + theUser + " to "
				+ newName);
			if(newName == null)
				throw new IllegalArgumentException("No name to set");
			theUser.setName(newName);
			theSession.fireEvent(new prisms.arch.event.PrismsEvent("prismsUserChanged", "user",
				theUser));
		}
		else if("passwordExpirationChanged".equals(evt.get("method")))
		{
			assertEditable();
			if(!evt.containsKey("expiration"))
				throw new IllegalArgumentException("No expiration to set");
			Number newExpire = (Number) evt.get("expiration");
			prisms.arch.ds.ManageableUserSource source;
			source = (prisms.arch.ds.ManageableUserSource) theSession.getApp().getEnvironment()
				.getUserSource();
			try
			{
				if(newExpire == null)
					source.setPasswordExpiration(theUser, Long.MAX_VALUE);
				else
					source.setPasswordExpiration(theUser, newExpire.longValue());
			} catch(prisms.arch.PrismsException e)
			{
				log.error("Could not set password expiration", e);
				theSession.getUI().error("Could not set password expiration: " + e.getMessage());
				return;
			}
			log.info("User " + theSession.getUser() + " changing expiration of user " + theUser
				+ " to " + newExpire);
			theSession.fireEvent(new prisms.arch.event.PrismsEvent("prismsUserChanged", "user",
				theUser));
		}
		else if("changePassword".equals(evt.get("method")))
		{
			assertEditable();
			changePassword();
		}
		else if("doChangePassword".equals(evt.get("method")))
		{
			org.json.simple.JSONArray jsonHash = (org.json.simple.JSONArray) evt
				.get("passwordHash");
			if(jsonHash == null)
				throw new IllegalArgumentException("No passwordHash to change password with");
			long [] hash = new long [jsonHash.size()];
			for(int h = 0; h < hash.length; h++)
				hash[h] = ((Number) jsonHash.get(h)).longValue();
			log.info("User " + theSession.getUser() + " changing password of user " + theUser);
			doChangePassword(hash);
		}
		else if("setLocked".equals(evt.get("method")))
		{
			assertEditable();
			theUser.setLocked(((Boolean) evt.get("locked")).booleanValue());
			if(theUser.isLocked())
				log.info("User " + theSession.getUser() + " locked user " + theUser);
			else
				log.info("User " + theSession.getUser() + " unlocked user " + theUser);
			theSession.fireEvent(new prisms.arch.event.PrismsEvent("prismsUserChanged", "user",
				theUser));
		}
		else
			throw new IllegalArgumentException("Unrecognized event " + evt);
	}

	void setUser(User user)
	{
		if(theUser == null && user == null)
			return;
		theUser = user;
		initClient();
	}

	boolean isEditable()
	{
		if(theUser == null)
			return false;
		return manager.app.ManagerUtils.canEdit(theSession.getPermissions(),
			theUser.getPermissions(theSession.getApp()));
	}

	void assertEditable()
	{
		if(theUser == null)
			throw new IllegalArgumentException("No user selected to edit");
		if(theUser.isReadOnly())
			throw new IllegalArgumentException("User " + theUser + " is read-only");
		if(!manager.app.ManagerUtils.canEdit(theSession.getPermissions(),
			theUser.getPermissions(theSession.getApp())))
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to edit user " + theUser);
	}

	void changePassword()
	{
		assertEditable();
		if(!(theSession.getApp().getEnvironment().getUserSource() instanceof prisms.arch.ds.ManageableUserSource))
			throw new IllegalStateException("Cannot change password: user source is not manageable");
		theValidationRequestTime = System.currentTimeMillis();
		prisms.arch.ds.Hashing hashing;
		prisms.arch.ds.PasswordConstraints constraints;
		try
		{
			hashing = theSession.getApp().getEnvironment().getUserSource().getHashing();
			constraints = theSession.getApp().getEnvironment().getUserSource()
				.getPasswordConstraints();
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not get PRISMS hashing or password constraints",
				e);
		}
		theValidationParams = hashing;
		JSONObject event = new JSONObject();
		event.put("plugin", theName);
		event.put("method", "changePassword");
		event.put("hashing", hashing.toJson());
		event.put("constraints", prisms.util.PrismsUtils.serializeConstraints(constraints));
		theSession.postOutgoingEvent(event);
	}

	void doChangePassword(long [] hash)
	{
		assertEditable();
		if(theUser == null)
			throw new IllegalStateException("No user to change password for");
		if(theValidationParams == null)
			throw new IllegalStateException("No change password requested");
		if(theValidationRequestTime < System.currentTimeMillis() - MAX_CHANGE_PASSWORD_REQUEST_WAIT)
		{
			theSession.getUI().error("Change password request expired.  Please try again.");
			changePassword();
			return;
		}
		try
		{
			theSession
				.getApp()
				.getEnvironment()
				.getUserSource()
				.setPassword(theUser, hash,
					!theUser.getName().equals(theSession.getUser().getName()));
		} catch(prisms.arch.PrismsException e)
		{
			log.error("Could not set user password", e);
			theSession.getUI().error("Could not set user password: " + e.getMessage());
		}
	}
}
