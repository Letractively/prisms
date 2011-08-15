/*
 * PreferencesEditor.java Created Mar 19, 2008 by Andrew Butler, PSL
 */
package prisms.ui;

import java.awt.Color;

import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.util.preferences.Preference;

/** A plugin allowing the user to change preferences registered as user-modifiable by other plugins */
public class PreferencesEditor implements prisms.arch.AppPlugin
{
	/** The name of the preferences editor plugin */
	public static final String NAME = "Preferences";

	private PrismsSession theSession;

	boolean theDataLock;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;

		theSession.addEventListener("preferencesChanged",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
				{
					if(theDataLock || !(evt instanceof prisms.util.preferences.PreferenceEvent))
						return;
					prisms.util.preferences.PreferenceEvent pEvt = (prisms.util.preferences.PreferenceEvent) evt;
					if(!pEvt.getPreference().isDisplayed())
						return;
					JSONObject postEvt = new JSONObject();
					postEvt.put("plugin", NAME);
					postEvt.put("method", "set");
					postEvt.put("domain", pEvt.getPreference().getDomain());
					postEvt.put("prefName", pEvt.getPreference().getName());
					postEvt.put("type", pEvt.getPreference().getType().name());
					Object value = pEvt.getNewValue();
					switch(pEvt.getPreference().getType())
					{
					case ENUM:
						JSONObject enumPref = new JSONObject();
						enumPref.put("value", value.toString());
						org.json.simple.JSONArray options = new org.json.simple.JSONArray();
						enumPref.put("options", options);
						for(Enum<?> option : ((Enum<?>) value).getDeclaringClass()
							.getEnumConstants())
							options.add(option.toString());
						postEvt.put("value", enumPref);
						break;
					case COLOR:
						postEvt.put("value", prisms.util.ColorUtils.toHTML((Color) value));
						break;
					default:
						postEvt.put("value", value);
					}
					session2.postOutgoingEvent(postEvt);
				}
			});
	}

	public void initClient()
	{
		if(theSession.getClient().isService())
			return;
		prisms.util.preferences.Preferences prefs = theSession.getPreferences();
		JSONObject evt = new JSONObject();
		evt.put("plugin", NAME);
		evt.put("method", "setVisible");
		evt.put("visible", Boolean.valueOf(prefs.getActiveDomains().length > 0));
		theSession.postOutgoingEvent(evt);
	}

	public void processEvent(JSONObject evt)
	{
		prisms.util.preferences.Preferences prefs = theSession.getPreferences();
		if("editPreferences".equals(evt.get("method")))
		{
			JSONObject prefObj = serialize(prefs);
			JSONObject evt2 = new JSONObject();
			evt2.put("plugin", NAME);
			evt2.put("method", "show");
			evt2.put("data", prefObj);
			theSession.postOutgoingEvent(evt2);
		}
		else if("dataChanged".equals(evt.get("method")))
		{
			String domain = (String) evt.get("domain");
			String prefName = (String) evt.get("prefName");
			Preference<?> pref = prefs.getPreference(domain, prefName);
			Object oldVal = prefs.get(pref);
			Object value = evt.get("value");
			switch(pref.getType())
			{
			case ENUM:
				for(Enum<?> option : ((Enum<?>) oldVal).getDeclaringClass().getEnumConstants())
					if(option.toString().equals(value))
					{
						value = option;
						break;
					}
				break;
			case COLOR:
				value = prisms.util.ColorUtils.fromHTML((String) value);
				break;
			case INT:
			case NONEG_INT:
				value = Integer.valueOf(((Number) value).intValue());
				break;
			case FLOAT:
			case NONEG_FLOAT:
			case PROPORTION:
				value = Float.valueOf(((Number) value).floatValue());
				break;
			default:
			}
			if(domain == null)
				throw new IllegalArgumentException("No domain/prefName couple");
			theDataLock = true;
			try
			{
				prefs.set((Preference<Object>) pref, value);
			} finally
			{
				theDataLock = false;
			}
		}
	}

	/**
	 * Serializes a preferences object for serialization or remote transmission
	 * 
	 * @param prefs The preferences to serialize
	 * @return A JSON object representing all the preferences in the given object
	 */
	public JSONObject serialize(prisms.util.preferences.Preferences prefs)
	{
		JSONObject ret = new JSONObject();
		String [] domains = prefs.getActiveDomains();
		for(String domain : domains)
		{
			org.json.simple.JSONArray domainArray = new org.json.simple.JSONArray();
			Preference<?> [] domainPrefs = prefs.getActivePreferences(domain);
			for(Preference<?> pref : domainPrefs)
			{
				if(!pref.isDisplayed())
					continue;
				JSONObject prefObject = new JSONObject();
				prefObject.put("name", pref.getName());
				prefObject.put("descrip", pref.getDescription());
				prefObject.put("type", pref.getType().name());
				Object value = prefs.get(pref);
				switch(pref.getType())
				{
				case ENUM:
					JSONObject enumPref = new JSONObject();
					enumPref.put("value", value.toString());
					org.json.simple.JSONArray options = new org.json.simple.JSONArray();
					enumPref.put("options", options);
					for(Enum<?> option : ((Enum<?>) value).getDeclaringClass().getEnumConstants())
						options.add(option.toString());
					prefObject.put("value", enumPref);
					break;
				case COLOR:
					prefObject.put("value", prisms.util.ColorUtils.toHTML((Color) value));
					break;
				default:
					prefObject.put("value", value);
				}
				domainArray.add(prefObject);
			}
			if(domainArray.size() > 0)
				ret.put(domain, domainArray);
		}
		return ret;
	}
}
