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

	private java.util.Map<String, String> theSchemaRoots;

	private java.util.Map<String, Object> theStoredSchemas;

	/**
	 * Creates a schema parser
	 */
	public JsonSchemaParser()
	{
		theSchemaRoots = new java.util.HashMap<String, String>();
		theStoredSchemas = new java.util.HashMap<String, Object>();
	}

	/**
	 * Adds a schema to this parser
	 * 
	 * @param name The name of the schema
	 * @param schemaRoot The root URL to use to search for .json files under the additional schema
	 */
	public void addSchema(String name, String schemaRoot)
	{
		theSchemaRoots.put(name, schemaRoot);
	}

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
		JsonElement ret = createElementFor(schemaEl);
		ret.configure(this, parent, name, schemaEl instanceof JSONObject ? (JSONObject) schemaEl
			: null);
		return ret;
	}

	/**
	 * Creates an unconfigured JsonElement for the given schema object
	 * 
	 * @param schemaEl The schema object to create an element for
	 * @return The JsonElement to parse the given schema type
	 */
	public JsonElement createElementFor(Object schemaEl)
	{
		if(schemaEl instanceof JSONObject)
		{
			JSONObject jsonSchema = (JSONObject) schemaEl;
			String typeName = (String) jsonSchema.get("valueType");
			if(typeName == null)
				return new JsonObjectElement();
			else if(typeName.equals("set"))
				return new SetElement();
			else if(typeName.equals("oneOf"))
				return new EnumElement();
			else if(typeName.equals("string"))
				return new StringElement();
			else if(typeName.equals("boolean"))
				return new BooleanElement();
			else if(typeName.equals("number"))
				return new NumberElement();
			else
				return createElementForType(typeName);
		}
		else if(schemaEl == null || schemaEl instanceof Boolean || schemaEl instanceof String
			|| schemaEl instanceof Number)
			return new ConstantElement(schemaEl);
		else
			throw new IllegalStateException("Unrecognized schema element type: "
				+ schemaEl.getClass().getName());
	}

	/**
	 * @param type The type of schema to get
	 * @return The schema for the given type
	 */
	public JsonElement createElementForType(String type)
	{
		int idx = type.indexOf('/');
		if(idx < 0)
			throw new IllegalStateException("No schema root to find schema type " + type);
		String schemaName = type.substring(0, idx);
		String schemaRoot = theSchemaRoots.get(schemaName);
		if(schemaRoot == null)
			throw new IllegalStateException("Unrecognized schema: " + schemaName);
		return new CustomSchemaElement(type, schemaRoot + type + ".json");
	}

	/**
	 * @param schemaName The name of the schema to load
	 * @param schemaLocation The location of the schema to load
	 * @return The schema at the given location
	 */
	public JsonElement getExternalSchema(String schemaName, String schemaLocation)
	{
		Object ret = theStoredSchemas.get(schemaName);
		if(ret != null)
			return createElementFor(ret);
		try
		{
			ret = org.json.simple.JSONValue.parse(new java.io.InputStreamReader(new java.net.URL(
				schemaLocation).openStream()));
		} catch(Throwable e)
		{
			throw new IllegalStateException("Could not find schema " + schemaName + " at "
				+ schemaLocation);
		}
		return createElementFor(ret);
	}
}
