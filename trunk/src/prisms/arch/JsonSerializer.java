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
	public JSONArray deserialize(String evtString) throws java.io.InvalidObjectException
	{
		String replaced = evtString.replaceAll("undefined", "null");
		if(replaced != evtString)
			log.warn("undefined found in JSON string");
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
		JSONArray ret;
		if(json instanceof JSONArray)
			ret = (JSONArray) json;
		else if(json instanceof JSONObject)
		{
			ret = new JSONArray();
			ret.add(json);
		}
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
	 * @throws NotSerializableException If the object cannot be serialized correctly
	 */
	public static void validate(Object o) throws NotSerializableException
	{
		if(o == null || o instanceof Boolean || o instanceof Byte || o instanceof Short
			|| o instanceof Integer || o instanceof Long || o instanceof Float
			|| o instanceof Double || o instanceof String)
			return;
		if(o instanceof JSONArray)
		{
			for(Object el : (JSONArray) o)
				validate(el);
			return;
		}
		if(o instanceof JSONObject)
		{
			for(java.util.Map.Entry<Object, Object> entry : ((java.util.Map<Object, Object>) o)
				.entrySet())
			{
				if(!(entry.getKey() instanceof String))
					throw new NotSerializableException("All keys in a JSONObject must be strings");
				validate(entry.getValue());
			}
			return;
		}
		throw new NotSerializableException("All JSON-serializable objects must be primitive or of"
			+ " type string, JSONObject, or JSONArray--not " + o.getClass().getName());
	}
}
