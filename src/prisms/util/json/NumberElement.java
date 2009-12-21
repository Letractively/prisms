/*
 * StringElement.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.util.json;

/**
 * Represents an element that must be a string
 */
public class NumberElement extends DefaultJsonElement
{
	@Override
	public boolean doesValidate(Object jsonValue)
	{
		if(!super.doesValidate(jsonValue))
			return false;
		if(jsonValue == null)
			return true;
		return jsonValue instanceof Number;
	}

	@Override
	public boolean validate(Object jsonValue) throws JsonSchemaException
	{
		if(super.validate(jsonValue))
			return true;
		if(jsonValue == null)
			return true;
		if(jsonValue instanceof Number)
			return true;
		throw new JsonSchemaException("Value must be a number", this, jsonValue);
	}
}
