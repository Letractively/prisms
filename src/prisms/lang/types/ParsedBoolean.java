/*
 * ParsedBoolean.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents a boolean value */
public class ParsedBoolean extends prisms.lang.ParsedItem
{
	private boolean theValue;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theValue = getStored("value").text.equalsIgnoreCase("true");
	}

	/** @return The value of this boolean */
	public boolean getValue()
	{
		return theValue;
	}

	@Override
	public String toString()
	{
		return String.valueOf(theValue);
	}

	@Override
	public prisms.lang.EvaluationResult<Boolean> evaluate(prisms.lang.EvaluationEnvironment env,
		boolean asType, boolean withValues) throws prisms.lang.EvaluationException
	{
		return new prisms.lang.EvaluationResult<Boolean>(Boolean.TYPE, Boolean.valueOf(theValue));
	}
}
