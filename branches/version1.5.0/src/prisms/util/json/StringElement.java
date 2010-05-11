/*
 * StringElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

/**
 * Represents an element that must be a string
 */
public class StringElement extends DefaultJsonElement
{
	@Override
	public float doesValidate(Object jsonValue)
	{
		float ret = super.doesValidate(jsonValue);
		if(ret < 1)
			return ret;
		if(jsonValue == null)
			return 1;
		return jsonValue instanceof String ? 1 : 0;
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(super.validate(jsonValue))
			return true;
		if(jsonValue == null)
			return true;
		if(jsonValue instanceof String)
			return true;
		throw new JsonSchemaException("Value must be a string", this, jsonValue);
	}
}
