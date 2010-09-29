/*
 * JsonSerialWriter.java Created Jul 29, 2010 by Andrew Butler, PSL
 */
package prisms.util.json;

import java.io.IOException;

/**
 * Creates JSON-formatted data in a serial method. This allows a stream to be written more
 * efficiently than compiling large JSON documents and then serializing them to a string to write.
 */
public interface JsonSerialWriter
{
	/**
	 * Starts a JSON object
	 * 
	 * @throws IOException If an error occurs creating the data
	 */
	public void startObject() throws IOException;

	/**
	 * Starts a property within an object
	 * 
	 * @param name The name of the property
	 * @throws IOException If an error occurs creating the data
	 */
	public void startProperty(String name) throws IOException;

	/**
	 * Ends an object
	 * 
	 * @throws IOException If an error occurs creating the data
	 */
	public void endObject() throws IOException;

	/**
	 * Starts a JSON array (writes the '[')
	 * 
	 * @throws IOException If an error occurs creating the data
	 */
	public void startArray() throws IOException;

	/**
	 * Ends an array
	 * 
	 * @throws IOException If an error occurs creating the data
	 */
	public void endArray() throws IOException;

	/**
	 * Writes a string value
	 * 
	 * @param value The string to write
	 * @throws IOException If an error occurs creating the data
	 */
	public void writeString(String value) throws IOException;

	/**
	 * Writes a number value
	 * 
	 * @param value The number to write
	 * @throws IOException If an error occurs creating the data
	 */
	public void writeNumber(Number value) throws IOException;

	/**
	 * Writes a boolean value
	 * 
	 * @param value The boolean to write
	 * @throws IOException If an error occurs creating the data
	 */
	public void writeBoolean(boolean value) throws IOException;

	/**
	 * Writes a null value
	 * 
	 * @throws IOException If an error occurs creating the data
	 */
	public void writeNull() throws IOException;
}
