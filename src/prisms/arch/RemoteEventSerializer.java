/**
 * RemoveEventSerializer.java Created Jul 31, 2007 by Andrew Butler, PSL
 */
package prisms.arch;

/**
 * Allows server-client communication by serializing events to a string that can be sent via HTTP
 */
public interface RemoteEventSerializer
{
	/**
	 * Serializes a set of server events to be sent to the client
	 * 
	 * @param events The events to serialize
	 * @return A deserializable string representing the events
	 * @throws java.io.NotSerializableException If the events cannot be serialized
	 */
	String serialize(org.json.simple.JSONArray events) throws java.io.NotSerializableException;

	/**
	 * Deserializes a set of events from the client to be interpreted by the server
	 * 
	 * @param evtString The deserializable string representing the events to deserialize
	 * @return The events from the client
	 * @throws java.io.InvalidObjectException If the string cannot be deserialized
	 */
	org.json.simple.JSONArray deserialize(String evtString) throws java.io.InvalidObjectException;
}
