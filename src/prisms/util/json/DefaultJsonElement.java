/*
 * DefaultJsonElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

import org.json.simple.JSONObject;

/**
 * Contains fields and methods to cover some of the basic pieces of JSON validation
 */
public abstract class DefaultJsonElement implements JsonElement
{
	private JsonSchemaParser theParser;

	private JsonElement theParent;

	private String theName;

	private JSONObject theConstraints;

	private boolean isNullable;

	public void configure(JsonSchemaParser parser, JsonElement parent, String name,
		JSONObject schemaEl)
	{
		theParser = parser;
		theParent = parent;
		theName = name;
		theConstraints = (JSONObject) schemaEl.get("schemaConstraints");
		isNullable = Boolean.TRUE.equals(schemaEl.get("nullable"));
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

	/**
	 * @return The schema constraints contained in this element's source
	 */
	public JSONObject getConstraints()
	{
		return theConstraints;
	}

	/**
	 * @return Whether this element's value may be null or missing
	 */
	public boolean isNullable()
	{
		return isNullable;
	}

	public boolean doesValidate(Object jsonValue)
	{
		if(jsonValue == null && !isNullable)
			return false;
		return true;
	}

	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(jsonValue == null)
		{
			if(!isNullable)
				throw new JsonSchemaException("Null or missing value not allowed for JSON element "
					+ this, this, jsonValue);
			else
				return true;
		}
		return false;
	}

	/**
	 * @return A string detailing the location of this element within the schema
	 */
	public String getPathString()
	{
		StringBuilder ret = new StringBuilder();
		JsonElement el = this;
		while(el != null)
		{
			ret.insert(0, el.getName() + '/');			
			el = el.getParent();
		}
		ret.deleteCharAt(ret.length() - 1);
		return ret.toString();
	}
}
