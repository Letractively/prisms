/*
 * CustomSchemaElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

import org.json.simple.JSONObject;

/**
 * An element that validates by a custom schema
 */
public class CustomSchemaElement extends DefaultJsonElement
{
	private String theSchemaName;

	private String theSchemaLocation;

	private JsonElement theSchemaEl;

	/**
	 * Creates a custom schema element
	 * 
	 * @param schemaName The name of the custom schema
	 * @param schemaLocation The location of the schema
	 */
	public CustomSchemaElement(String schemaName, String schemaLocation)
	{
		theSchemaName = schemaName;
		theSchemaLocation = schemaLocation;
	}

	/**
	 * @return The name of the custom schema
	 */
	public String getSchemaName()
	{
		return theSchemaName;
	}

	/**
	 * @return The location of the custom schema definition
	 */
	public String getSchemaLocation()
	{
		return theSchemaLocation;
	}

	@Override
	public void configure(JsonSchemaParser parser, JsonElement parent, String name,
		JSONObject schemaEl)
	{
		super.configure(parser, parent, name, schemaEl);
	}

	@Override
	public boolean doesValidate(Object jsonValue)
	{
		if(!super.doesValidate(jsonValue))
			return false;
		if(!(jsonValue instanceof JSONObject))
			return false;
		if(theSchemaEl == null)
			load();
		return theSchemaEl.doesValidate(jsonValue);
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(super.validate(jsonValue))
			return true;
		if(!(jsonValue instanceof JSONObject))
			throw new JsonSchemaException("Element must be a set", this, jsonValue);
		if(theSchemaEl == null)
			load();
		return theSchemaEl.validate(jsonValue);
	}

	/**
	 * Loads this custom schema
	 */
	protected void load()
	{
		theSchemaEl = getParser().getExternalSchema(theSchemaName, theSchemaLocation, this);
	}
}
