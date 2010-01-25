/*
 * ConstantElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

import org.json.simple.JSONObject;

/**
 * Represents an element that must have a constant value
 */
public class ConstantElement implements JsonElement
{
	private JsonSchemaParser theParser;

	private JsonElement theParent;

	private String theName;

	private Object theValue;

	/**
	 * @param value The value for this constant
	 */
	public ConstantElement(Object value)
	{
		theValue = value;
	}

	public void configure(JsonSchemaParser parser, JsonElement parent, String elementName,
		JSONObject schemaEl)
	{
		theParser = parser;
		theParent = parent;
		theName = elementName;
	}

	/**
	 * @return The value for this constant
	 */
	public Object getValue()
	{
		return theValue;
	}

	public JsonSchemaParser getParser()
	{
		return theParser;
	}

	public JsonElement getParent()
	{
		return theParent;
	}

	public String getName()
	{
		return theName;
	}

	public boolean doesValidate(Object jsonValue)
	{
		if(theValue == null)
			return jsonValue == null;
		return theValue.equals(jsonValue);
	}

	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(theValue == null ? jsonValue == null : theValue.equals(jsonValue))
			return true;
		throw new JsonSchemaException(theValue + " expected", this, jsonValue);
	}
}