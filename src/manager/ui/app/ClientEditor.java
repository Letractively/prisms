/**
 * ApplicationEditor.java Created Oct 1, 2008 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.json.simple.JSONObject;

import prisms.arch.AppPlugin;
import prisms.arch.ClientConfig;
import prisms.arch.PrismsSession;
import prisms.arch.ds.User;
import prisms.impl.DBClientConfig;

;

/**
 * Allows the user to edit properties of an application
 */
public class ClientEditor implements AppPlugin
{
	private static final Logger log = Logger.getLogger(ClientEditor.class);

	PrismsSession theSession;

	private String theName;

	ClientConfig theClient;

	boolean theDataLock;

	/**
	 * @see prisms.arch.AppPlugin#initPlugin(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void initPlugin(PrismsSession session, Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		theClient = theSession.getProperty(ManagerProperties.selectedClient);
		session.addPropertyChangeListener(ManagerProperties.selectedClient,
			new prisms.arch.event.PrismsPCL<ClientConfig>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<ClientConfig> evt)
				{
					setClient(evt.getNewValue());
				}
			});
		session.addEventListener("clientChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				if(theDataLock)
					return;
				ClientConfig app = (ClientConfig) evt.getProperty("client");
				if(theClient != null && theClient.equals(app))
					initClient();
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
						setClient(theClient);
					}
				}
			});
	}

	/**
	 * @see prisms.arch.AppPlugin#initClient()
	 */
	public void initClient()
	{
		boolean visible = theClient != null;
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
		val.put("name", theClient.getName());
		if(theClient instanceof DBClientConfig)
		{
			DBClientConfig client = (DBClientConfig) theClient;
			val.put("descrip", client.getDescription());
			if(client.getSerializer() != null)
				val.put("serializerClass", client.getSerializer().getClass().getName());
			else
				val.put("serializerClass", null);
			if(client.getValidator() != null)
				val.put("validatorClass", client.getValidator().getClass().getName());
			else
				val.put("validatorClass", null);
			String configXML = client.getConfigXML();
			val.put("configXML", configXML);
			val.put("isService", new Boolean(client.isService()));
			val.put("sessionTimeout", new Long(client.getSessionTimeout() / 1000));
			val.put("allowAnonymous", new Boolean(client.allowsAnonymous()));
		}
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
			log.info("User " + theSession.getUser() + " changing name of client " + theClient
				+ " to " + newName);
			prisms.arch.ds.ManageableUserSource source;
			source = (prisms.arch.ds.ManageableUserSource) theSession.getApp().getDataSource();
			((DBClientConfig) theClient).setName(newName);
			try
			{
				source.putClient(theClient);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify application", e);
			}
			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("clientChanged", "client",
					theClient));
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
			log.info("User " + theSession.getUser() + " changing description of client "
				+ theClient + " to " + newDescrip);
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();
			((DBClientConfig) theClient).setDescription(newDescrip);
			try
			{
				source.putClient(theClient);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify client", e);
			}
			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("clientChanged", "client",
					theClient));
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
			log.info("User " + theSession.getUser() + " changing configuration XML of client "
				+ theClient + " to " + configXML);
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();

			((DBClientConfig) theClient).setConfigXML(configXML);

			try
			{
				source.putClient(theClient);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify application", e);
			}
			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("clientChanged", "client",
					theClient));
			} finally
			{
				theDataLock = false;
			}
		}
		else if("serializerClassChanged".equals(evt.get("method")))
		{
			assertEditable();
			String configClass = (String) evt.get("serializerClass");
			log.info("User " + theSession.getUser() + " changing serializer class of client "
				+ theClient + " to " + configClass);
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();
			((DBClientConfig) theClient).setSerializerClass(configClass);
			try
			{
				source.putClient(theClient);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify client", e);
			}

			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("clientChanged", "client",
					theClient));
			} finally
			{
				theDataLock = false;
			}
		}
		else if("validatorClassChanged".equals(evt.get("method")))
		{
			assertEditable();
			String configClass = (String) evt.get("validatorClass");
			log.info("User " + theSession.getUser() + " changing validation class of client "
				+ theClient + " to " + configClass);
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();
			((DBClientConfig) theClient).setValidatorClass(configClass);
			try
			{
				source.putClient(theClient);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify client", e);
			}

			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("clientChanged", "client",
					theClient));
			} finally
			{
				theDataLock = false;
			}
		}
		else if("setService".equals(evt.get("method")))
		{
			assertEditable();
			((DBClientConfig) theClient).setService(((Boolean) evt.get("service")).booleanValue());
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();
			try
			{
				source.putClient(theClient);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify client", e);
			}
			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("clientChanged", "client",
					theClient));
			} finally
			{
				theDataLock = false;
			}
		}
		else if("setTimeout".equals(evt.get("method")))
		{
			assertEditable();
			long timeout = evt.get("timeout") != null
				? ((Number) evt.get("timeout")).longValue() * 1000 : -1;
			((DBClientConfig) theClient).setSessionTimeout(timeout);
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();
			try
			{
				source.putClient(theClient);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify client", e);
			}
			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("clientChanged", "client",
					theClient));
			} finally
			{
				theDataLock = false;
			}
		}
		else if("setAllowAnonymous".equals(evt.get("method")))
		{
			assertEditable();
			((DBClientConfig) theClient).setAllowsAnonymous(((Boolean) evt.get("allowed"))
				.booleanValue());
			prisms.impl.DBUserSource source;
			source = (prisms.impl.DBUserSource) theSession.getApp().getDataSource();
			try
			{
				source.putClient(theClient);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not modify client", e);
			}
			theDataLock = true;
			try
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("clientChanged", "client",
					theClient));
			} finally
			{
				theDataLock = false;
			}
		}
		else
			throw new IllegalArgumentException("Unrecognized event " + evt);
	}

	void setClient(ClientConfig client)
	{
		if(theClient == null && client == null)
			return;
		theClient = client;
		initClient();
	}

	boolean isEditable()
	{
		if(theClient == null)
			return false;
		return manager.app.ManagerUtils.canEdit(theSession.getUser(), theSession.getApp(),
			theClient.getApp());
	}

	void assertEditable()
	{
		if(theClient == null)
			throw new IllegalArgumentException("No client selected to edit");
		if(!manager.app.ManagerUtils.canEdit(theSession.getUser(), theSession.getApp(), theClient
			.getApp()))
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to edit client " + theClient.getName());
	}
}
