/**
 * PropertySerializer.java Created Oct 16, 2007 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.PrismsApplication;

/**
 * Converts a property to a serializable JSON-compatible object
 * 
 * @param <T> The type of property to serialize
 */
public interface PropertySerializer<T>
{
	/**
	 * Serializes a property value
	 * 
	 * @param obj The value to serialize
	 * @return The serialized value
	 */
	Object serialize(T obj);

	/**
	 * Deserializes a property value
	 * 
	 * @param json The JSON object representing the value to deserialize
	 * @param app The application requesting the deserialization
	 * @return The deserialized property value
	 */
	T deserialize(Object json, PrismsApplication app);

	/**
	 * Allows the serializer to link its deserialized value up with other deserialized properties
	 * found in the application after all properties have been deserialized
	 * 
	 * @param value The value of this serializer's property
	 * @param app The application to use as a source of properties to link
	 * @return The value for the property (since the reference may be to a new object)
	 */
	T link(T value, PrismsApplication app);
}
