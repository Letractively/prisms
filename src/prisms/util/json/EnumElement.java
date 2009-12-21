/*
 * EnumElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

import org.json.simple.JSONObject;

/**
 * Validates an element that could take one of several types of values
 */
public class EnumElement extends ContainerJsonElement
{
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
		if(jsonValue == null)
			return true;
		for(JsonElement el : getChildren())
			if(el.doesValidate(jsonValue))
				return true;
		return false;
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(super.validate(jsonValue))
			return true;
		if(jsonValue == null)
			return true;
		for(JsonElement el : getChildren())
			if(el.doesValidate(jsonValue))
				return true;
		throw new JsonSchemaException("Element does not match any schema option", this, jsonValue);
	}
}
