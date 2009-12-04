/**
 * ApplicationEditor.java Created Oct 1, 2008 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.json.simple.JSONObject;

import prisms.arch.AppPlugin;
import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.arch.ds.User;
import prisms.impl.DBApplication;

/**
 * Allows the user to edit properties of an application
 */
public class ApplicationEditor implements AppPlugin
{
	private static final Logger log = Logger.getLogger(ApplicationEditor.class);

	PrismsSession theSession;

	private String theName;

	private prisms.arch.ds.ManageableUserSource theUserSource;

	PrismsApplication theApp;

	private User theAppUser;

	private prisms.ui.UI theUI;

	boolean theDataLock;

	/**
	 * @see prisms.arch.AppPlugin#initPlugin(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void initPlugin(PrismsSession session, Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		prisms.arch.ds.UserSource us = session.getApp().getDataSource();
		if(!(us instanceof prisms.arch.ds.ManageableUserSource))
			log.warn("User source is not manageable");
		else
			theUserSource = (prisms.arch.ds.ManageableUserSource) us;
		theApp = theSession.getProperty(ManagerProperties.selectedApp);
		try
		{
			if(theApp != null && theUserSource != null)
				theAppUser = theUserSource.getUser(session.getUser(), theApp);
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not get application user", e);
		}
		session.addPropertyChangeListener(ManagerProperties.selectedApp,
			new prisms.arch.event.PrismsPCL<PrismsApplication>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication> evt)
				{
					setApp(evt.getNewValue());
				}
			});
		session.addEventListener("appChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(theDataLock)
					return;
				PrismsApplication app = (PrismsApplication) evt.getProperty("app");
				if(theApp != null && theApp.equals(app))
					initClient();
			}
		});
		theUI = (prisms.ui.UI) session.getPlugin("UI");
		session.addEventListener("userPermissionsChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					if(theSession.getUser().getName().equals(
						((User) evt.getProperty("user")).getName()))
					{
						setApp(theApp);
					}
				}
			});
	}

	/**
	 * @see prisms.arch.AppPlugin#initClient()
	 */
	public void initClient()
	{
		boolean visible = theApp != null;
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
		val.put("name", theApp.getName());
		JSONObject evt2 = null;
		if(theApp instanceof DBApplication)
		{
			val.put("descrip", ((DBApplication) theApp).getDescription());
			if(((DBApplication) theApp).getConfig() instanceof prisms.impl.PlaceholderAppConfig)
				val.put("configClass", ((prisms.impl.PlaceholderAppConfig) ((DBApplication) theApp)
					.getConfig()).getAppConfigClassName());
			else if(((DBApplication) theApp).getConfig() != null)
				val.put("configClass", ((DBApplication) theApp).getConfig().getClass().getName());
			else
				val.put("configClass", null);
			String configXML = ((DBApplication) theApp).getConfigXML();
			val.put("configXML", configXML);

			if(configXML != null)
			{
				evt2 = new JSONObject();
				evt2.put("plugin", theName);
				evt2.put("method", "setConfigXmlValid");
				boolean valid = true;
				try
				{
					((DBApplication) theApp).findConfigXML();
				} catch(Exception e)
				{
					valid = false;
				}
				if(valid)
				{
					try
					{
						((DBApplication) theApp).parseConfigXML();
					} catch(Exception e)
					{
						valid = false;
					}
				}
				evt2.put("valid", new Boolean(valid));
			}
		}
		evt.put("value", val);
		theSession.postOutgoingEvent(evt);
		if(evt2 != null)
			theSession.postOutgoingEvent(evt2);
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
			log.info("User " + theSession.getUser() + " changing name of application " + theApp
				+ " to " + newName);
			prisms.arch.ds.ManageableUserSource source;
			source = (prisms.arch.ds.ManageableUserSource) theSession.getApp().getDataSource();
			try
			{
				source.rename(theApp, newName);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify application", e);
			}
			theDataLock = true;
			try
			{
				theSession
					.fireEvent(new prisms.arch.event.PrismsEvent("appChanged", "app", theApp));
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
			log.info("User " + theSession.getUser() + " changing description of application "
				+ theApp + " to " + newDescrip);
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();
			((DBApplication) theApp).setDescription(newDescrip);
			try
			{
				source.changeProperties((DBApplication) theApp);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify application", e);
			}
			theDataLock = true;
			try
			{
				theSession
					.fireEvent(new prisms.arch.event.PrismsEvent("appChanged", "app", theApp));
			} finally
			{
				theDataLock = false;
			}
		}
		else if("configClassChanged".equals(evt.get("method")))
		{
			assertEditable();
			String configClass = (String) evt.get("configClass");
			if(configClass == null)
				throw new IllegalArgumentException("No config class to set");
			log.info("User " + theSession.getUser()
				+ " changing configuration class of application " + theApp + " to " + configClass);
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();
			((DBApplication) theApp).setConfigClass(configClass);
			try
			{
				source.changeProperties((DBApplication) theApp);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify application", e);
			}

			JSONObject evt2 = new JSONObject();
			evt2.put("plugin", theName);
			evt2.put("method", "setConfigClassValid");
			boolean error = false;
			Class<?> firstClass = null;
			try
			{
				firstClass = Class.forName(configClass);
			} catch(Exception e)
			{
				evt2.put("valid", new Boolean(false));
				theUI.error(configClass + " is not a valid java class or is not in the classpath");
				theSession.postOutgoingEvent(evt2);
				error = true;
			}
			if(firstClass != null)
			{
				try
				{
					firstClass.asSubclass(prisms.arch.AppConfig.class);
				} catch(Exception e)
				{
					evt2.put("valid", new Boolean(false));
					theUI.error(configClass + " is not an implementation of "
						+ prisms.arch.AppConfig.class.getName());
					theSession.postOutgoingEvent(evt2);
					error = true;
				}
			}
			if(!error)
			{
				evt2.put("valid", new Boolean(true));
				theSession.postOutgoingEvent(evt2);
			}
			theDataLock = true;
			try
			{
				theSession
					.fireEvent(new prisms.arch.event.PrismsEvent("appChanged", "app", theApp));
			} finally
			{
				theDataLock = false;
			}
		}
		else if("configXmlChanged".equals(evt.get("method")))
		{
			assertEditable();
			String configXML = (String) evt.get("configXML");
			if(configXML == null)
				throw new IllegalArgumentException("No config XML to set");
			log.info("User " + theSession.getUser() + " changing configuration XML of application "
				+ theApp + " to " + configXML);
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();

			JSONObject evt2 = new JSONObject();
			evt2.put("plugin", theName);
			evt2.put("method", "setConfigXmlValid");
			evt2.put("valid", new Boolean(true));
			((DBApplication) theApp).setConfigXML(configXML);
			try
			{
				((DBApplication) theApp).findConfigXML();
			} catch(Exception e)
			{
				evt2.put("valid", new Boolean(false));
				theUI.error(configXML + " points to a non-existent resource");
			}
			if(((Boolean) evt2.get("valid")).booleanValue())
			{
				try
				{
					((DBApplication) theApp).parseConfigXML();
				} catch(Exception e)
				{
					evt2.put("valid", new Boolean(false));
					theUI.error(configXML + " points to a resource that cannot be parsed as XML");
				}
			}

			theSession.postOutgoingEvent(evt2);
			try
			{
				source.changeProperties((DBApplication) theApp);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify application", e);
			}
			theDataLock = true;
			try
			{
				theSession
					.fireEvent(new prisms.arch.event.PrismsEvent("appChanged", "app", theApp));
			} finally
			{
				theDataLock = false;
			}
		}
		else
			throw new IllegalArgumentException("Unrecognized event " + evt);
	}

	void setApp(PrismsApplication app)
	{
		if(theApp == null && app == null)
			return;
		theApp = app;
		try
		{
			if(theApp != null && theUserSource != null)
				theAppUser = theUserSource.getUser(theSession.getUser(), theApp);
		} catch(prisms.arch.PrismsException e)
		{
			throw new IllegalStateException("Could not application user", e);
		}
		initClient();
	}

	boolean isEditable()
	{
		if(theApp == null || theAppUser != null)
			return false;
		return theApp != null && manager.app.ManagerUtils.canEdit(theAppUser, theApp);
	}

	void assertEditable()
	{
		if(theApp == null)
			throw new IllegalArgumentException("No application selected to edit");
		if(!manager.app.ManagerUtils.canEdit(theAppUser, theApp))
			throw new IllegalArgumentException("User " + theAppUser
				+ " does not have permission to edit application " + theApp.getName());
	}
}
