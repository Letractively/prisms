/*
 * SetElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Validates an element that is a set of other elements
 */
public class SetElement extends DefaultJsonElement
{
	private JsonElement theChild;

	private int theMinSize;

	private int theMaxSize;

	@Override
	public void configure(JsonSchemaParser parser, JsonElement parent, String name,
		JSONObject schemaEl)
	{
		super.configure(parser, parent, name, schemaEl);
		theChild = parser.parseSchema(this, "[element]", schemaEl.get("values"));
		theMinSize = -1;
		theMaxSize = -1;
		JSONObject constraints = getConstraints();
		if(constraints != null)
		{
			if(constraints.get("min") != null)
				theMinSize = ((Number) constraints.get("min")).intValue();
			if(constraints.get("max") != null)
				theMaxSize = ((Number) constraints.get("max")).intValue();
		}
	}

	@Override
	public boolean doesValidate(Object jsonValue)
	{
		if(!super.doesValidate(jsonValue))
			return false;
		if(!(jsonValue instanceof JSONArray))
			return false;
		JSONArray set = (JSONArray) jsonValue;
		for(Object el : set)
			if(!theChild.doesValidate(el))
				return false;
		return true;
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(super.doesValidate(jsonValue))
			return true;
		if(!(jsonValue instanceof JSONArray))
			throw new JsonSchemaException("Element must be a set", this, jsonValue);
		JSONArray set = (JSONArray) jsonValue;
		if(theMinSize >= 0 && set.size() < theMinSize)
			throw new JsonSchemaException("Set must have at least " + theMinSize + " elements",
				this, jsonValue);
		if(theMaxSize >= 0 && set.size() > theMaxSize)
			throw new JsonSchemaException("Set must have at most " + theMaxSize + " elements",
				this, jsonValue);
		for(Object el : set)
			theChild.validate(el);
		return true;
	}
}
