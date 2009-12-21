/*
 * JsonSchemaParser.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

/**
 * Parses a JSON schema for validation
 */
public class JsonSchemaParser
{
	/**
	 * Used by the JSON schema API for logging
	 */
	public static final Logger log = Logger.getLogger(JsonSchemaParser.class);

	/**
	 * Parses a JSON schema
	 * 
	 * @param parent The parent element of the schema, or null if called from the root
	 * @param name The name of the schema element
	 * @param schemaEl The JSON-source of the schema element
	 * @return The schema element to validate with
	 */
	public JsonElement parseSchema(JsonElement parent, String name, Object schemaEl)
	{
		JsonElement ret;
		if(schemaEl instanceof JSONObject)
		{
			JSONObject jsonSchema = (JSONObject) schemaEl;
			String typeName = (String) jsonSchema.get("valueType");
			if(typeName == null)
				ret = new JsonObjectElement();
			else if(typeName.equals("set"))
				ret = new SetElement();
			else if(typeName.equals("oneOf"))
				ret = new EnumElement();
			else if(typeName.equals("string"))
				ret = new StringElement();
			else if(typeName.equals("boolean"))
				return new BooleanElement();
			else if(typeName.equals("number"))
				return new NumberElement();
			else
				throw new IllegalStateException("Unrecognized valueType: " + typeName);
		}
		else if(schemaEl == null || schemaEl instanceof Boolean || schemaEl instanceof String
			|| schemaEl instanceof Number)
			ret = new ConstantElement(schemaEl);
		else
			throw new IllegalStateException("Unrecognized schema element type: "
				+ schemaEl.getClass().getName());
		ret.configure(this, parent, name, schemaEl instanceof JSONObject ? (JSONObject) schemaEl
			: null);
		return ret;
	}
}
