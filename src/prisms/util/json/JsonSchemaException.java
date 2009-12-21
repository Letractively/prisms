/*
 * JsonSchemaException.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

/**
 * Thrown when a JSON structure does not match a schema
 */
public class JsonSchemaException extends Exception
{
	private final JsonElement theSchemaElement;

	private final Object theValue;

	/**
	 * @param msg The message of why the value did not match the schema
	 * @param schemaEl The schema element that was not matched
	 * @param jsonValue The value that did not match the schema
	 */
	public JsonSchemaException(String msg, JsonElement schemaEl, Object jsonValue)
	{
		super(msg);
		theSchemaElement = schemaEl;
		theValue = jsonValue;
	}

	/**
	 * @return The schema element that was not matched
	 */
	public JsonElement getSchemaElement()
	{
		return theSchemaElement;
	}

	/**
	 * @return The value that did not match the schema
	 */
	public Object getValue()
	{
		return theValue;
	}
}
