/**
 * ApplicationEditor.java Created Oct 1, 2008 by Andrew Butler, PSL
 */
package manager.ui.app;

import manager.app.ManagerProperties;

import org.json.simple.JSONObject;

import prisms.arch.ClientConfig;

/** Allows the user to view the properties of a client configuration */
public class ClientEditor implements prisms.arch.AppPlugin
{
	prisms.arch.PrismsSession theSession;

	private String theName;

	ClientConfig theClient;

	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		theClient = theSession.getProperty(ManagerProperties.selectedClient);
		session.addPropertyChangeListener(ManagerProperties.selectedClient,
			new prisms.arch.event.PrismsPCL<ClientConfig>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<ClientConfig> evt)
				{
					setClient(evt.getNewValue());
				}

				@Override
				public String toString()
				{
					return "Manager Client Editor Updater";
				}
			});
		session.addPropertyChangeListener(ManagerProperties.selectedApp,
			new prisms.arch.event.PrismsPCL<prisms.arch.PrismsApplication>()
			{
				public void propertyChange(
					prisms.arch.event.PrismsPCE<prisms.arch.PrismsApplication> evt)
				{
					if(theClient != null && evt.getNewValue() != theClient.getApp())
						setClient(null);
				}

				@Override
				public String toString()
				{
					return "Manager Client Editor Clearer";
				}
			});
	}

	public void initClient()
	{
		send(false);
	}

	public void processEvent(JSONObject evt)
	{
		throw new IllegalArgumentException("Unrecognized " + theName + " event " + evt);
	}

	void setClient(ClientConfig client)
	{
		if(theClient == client)
			return;
		theClient = client;
		send(true);
	}

	void send(boolean show)
	{
		boolean visible = theClient != null;
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setVisible");
		evt.put("visible", Boolean.valueOf(visible));
		if(show && visible)
			evt.put("show", Boolean.TRUE);
		theSession.postOutgoingEvent(evt);
		if(!visible)
			return;
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setValue");
		JSONObject val = new JSONObject();
		val.put("name", theClient.getName());
		val.put("descrip", theClient.getDescription());
		val.put("isService", Boolean.valueOf(theClient.isService()));
		val.put("sessionTimeout",
			prisms.util.PrismsUtils.printTimeLength(theClient.getSessionTimeout()));
		val.put("allowAnonymous", Boolean.valueOf(theClient.allowsAnonymous()));
		evt.put("value", val);
		theSession.postOutgoingEvent(evt);
	}
}
