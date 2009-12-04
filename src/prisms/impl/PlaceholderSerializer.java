/*
 * PlaceholderSerializer.java Created Dec 4, 2009 by Andrew Butler, PSL
 */
package prisms.impl;

/**
 * A placeholder for a {@link prisms.arch.RemoteEventSerializer} subclass that cannot be identified.
 * It may be meant for configuration on another server. This class throws an error if serialization
 * or deserialization is attempted.
 */
public class PlaceholderSerializer implements prisms.arch.RemoteEventSerializer
{
	private final String theSerializerClassName;

	/**
	 * Creates a {@link prisms.arch.RemoteEventSerializer} placeholder
	 * 
	 * @param className The name of the unidentifiable subclass that this instance represents
	 */
	public PlaceholderSerializer(String className)
	{
		theSerializerClassName = className;
	}

	/**
	 * @return The name of the unidentifiable subclass that this instance represents
	 */
	public String getSerializerClassName()
	{
		return theSerializerClassName;
	}

	public org.json.simple.JSONArray deserialize(String evtString)
	{
		throw new IllegalStateException("Unrecognized RemoteEventSerializer implementation: "
			+ theSerializerClassName);
	}

	public String serialize(org.json.simple.JSONArray events)
	{
		throw new IllegalStateException("Unrecognized RemoteEventSerializer implementation: "
			+ theSerializerClassName);
	}

}
