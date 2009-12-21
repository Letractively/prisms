/*
 * JsonObjectElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

import java.util.Map;

import org.json.simple.JSONObject;

/**
 * Represents an element that must be a json object with certain fields
 */
public class JsonObjectElement extends DefaultJsonElement
{
	private static enum OnExtraEl
	{
		IGNORE, WARN, ERROR;

		static OnExtraEl byName(String name)
		{
			if(name == null)
				return WARN;
			for(OnExtraEl el : values())
				if(el.name().equalsIgnoreCase(name))
					return el;
			throw new IllegalArgumentException("Illegal extra element value: " + name);
		}
	}

	private Map<String, JsonElement> theChildren;

	private OnExtraEl theExtraEl;

	@Override
	public void configure(JsonSchemaParser parser, JsonElement parent, String name,
		JSONObject schemaEl)
	{
		super.configure(parser, parent, name, schemaEl);
		theChildren = new java.util.HashMap<String, JsonElement>();
		JSONObject constraints = getConstraints();
		if(constraints != null)
			theExtraEl = OnExtraEl.byName((String) constraints.get("allowExtras"));
		else
			theExtraEl = OnExtraEl.WARN;
		for(Map.Entry<String, Object> entry : ((Map<String, Object>) schemaEl).entrySet())
			theChildren.put(entry.getKey(), parser.parseSchema(this, entry.getKey(), entry
				.getValue()));
	}

	@Override
	public boolean doesValidate(Object jsonValue)
	{
		if(!super.doesValidate(jsonValue))
			return false;
		if(jsonValue == null)
			return true;
		if(!(jsonValue instanceof JSONObject))
			return false;
		JSONObject json = (JSONObject) jsonValue;
		for(Map.Entry<String, JsonElement> entry : theChildren.entrySet())
			if(!entry.getValue().doesValidate(json.get(entry.getKey())))
				return false;
		if(theExtraEl == OnExtraEl.ERROR)
			for(Map.Entry<String, Object> entry : ((Map<String, Object>) jsonValue).entrySet())
			{
				if(theChildren.get(entry.getKey()) == null)
					return false;
			}
		return true;
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(super.validate(jsonValue))
			return true;
		if(jsonValue == null)
			return true;
		if(!(jsonValue instanceof JSONObject))
			throw new JsonSchemaException("Element must be a JSON object", this, jsonValue);
		JSONObject json = (JSONObject) jsonValue;
		for(Map.Entry<String, JsonElement> entry : theChildren.entrySet())
			entry.getValue().validate(json.get(entry.getKey()));
		for(Map.Entry<String, Object> entry : ((Map<String, Object>) jsonValue).entrySet())
		{
			if(theChildren.get(entry.getKey()) == null)
			{
				switch(theExtraEl)
				{
				case ERROR:
					throw new JsonSchemaException("Extra element " + entry.getKey()
						+ " in JSON object", this, jsonValue);
				case WARN:
					JsonSchemaParser.log.warn("Extra element " + entry.getKey()
						+ " in JSON object " + getPathString());
					break;
				case IGNORE:
					break;
				}
			}
		}
		return true;
	}

}
