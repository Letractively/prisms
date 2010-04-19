/**
 * JsonSerializer.java Created Jul 31, 2007 by Andrew Butler, PSL
 */
package prisms.arch;

import java.io.NotSerializableException;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A very simple serializer that writes and reads JSON, performing a simple check on sent objects
 */
public class JsonSerializer implements RemoteEventSerializer
{
	private static final Logger log = Logger.getLogger(JsonSerializer.class);

	/**
	 * @see prisms.arch.RemoteEventSerializer#deserialize(java.lang.String)
	 */
	public JSONObject deserialize(String evtString) throws java.io.InvalidObjectException
	{
		String replaced = evtString.replaceAll("undefined", "null");
		if(replaced != evtString)
		{
			log.warn("undefined found in JSON string");
			evtString = replaced;
		}
		Object json;
		try
		{
			json = org.json.simple.JSONValue.parse(evtString);
		} catch(Throwable e)
		{
			log.error("Could not deserialize event string: " + evtString);
			throw new java.io.InvalidObjectException("Could not deserialize event string \""
				+ evtString + "\": " + e.getMessage());
		}
		JSONObject ret;
		if(json instanceof JSONObject)
			ret = (JSONObject) json;
		else
			throw new java.io.InvalidObjectException("Could not deserialize event from "
				+ evtString);
		return ret;
	}

	/**
	 * @see prisms.arch.RemoteEventSerializer#serialize(org.json.simple.JSONArray)
	 */
	public String serialize(JSONArray evt) throws NotSerializableException
	{
		validate(evt);
		return evt.toString();
	}

	/**
	 * Validates a JSON-serializable object to ensure that it can be serialized and deserialized by
	 * either the server or the client correctly.
	 * 
	 * @param o The object to be tested for JSON-serializability
	 * @return The value to substitute for the object, or null if the object can be sent as JSON
	 * @throws NotSerializableException If the object cannot be serialized correctly
	 */
	public static Object validate(Object o) throws NotSerializableException
	{
		if(o == null || o instanceof Boolean || o instanceof Byte || o instanceof Short
			|| o instanceof Integer || o instanceof Long || o instanceof String)
			return null;
		if(o instanceof Float)
		{
			if(!((Float) o).isInfinite())
				return null;
			if(((Float) o).floatValue() < 0)
				return "-Inf";
			else
				return "Inf";
		}
		if(o instanceof Double)
		{
			if(!((Double) o).isInfinite())
				return null;
			if(((Double) o).floatValue() < 0)
				return "-Inf";
			else
				return "Inf";
		}
		if(o instanceof JSONArray)
		{
			for(int i = 0; i < ((JSONArray) o).size(); i++)
			{
				Object newVal = validate(((JSONArray) o).get(i));
				if(newVal != null)
					((JSONArray) o).set(i, newVal);
			}
			return null;
		}
		if(o instanceof JSONObject)
		{
			for(java.util.Map.Entry<Object, Object> entry : ((java.util.Map<Object, Object>) o)
				.entrySet())
			{
				if(!(entry.getKey() instanceof String))
					throw new NotSerializableException("All keys in a JSONObject must be strings");
				Object newVal = validate(entry.getValue());
				if(newVal != null)
					entry.setValue(newVal);
			}
			return null;
		}
		throw new NotSerializableException("All JSON-serializable objects must be primitive or of"
			+ " type string, JSONObject, or JSONArray--not " + o.getClass().getName());
	}
}
