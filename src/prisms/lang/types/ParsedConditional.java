/*
 * ParsedConditional.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.ParseStruct;

/** Represents a conditional expression of the form condition ? affirmative : negative */
public class ParsedConditional extends ParseStruct
{
	private ParseStruct theCondition;

	private ParseStruct theAffirmative;

	private ParseStruct theNegative;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theCondition = parser.parseStructures(this, getStored("condition"))[0];
		theAffirmative = parser.parseStructures(this, getStored("affirmative"))[0];
		theNegative = parser.parseStructures(this, getStored("negative"))[0];
	}

	/** @return The condition determining which expression is evaluated */
	public ParseStruct getCondition()
	{
		return theCondition;
	}

	/** @return The expression that is evaluated if the condition evaluates to true */
	public ParseStruct getAffirmative()
	{
		return theAffirmative;
	}

	/** @return The expression that is evaluated if the condition evaluates to false */
	public ParseStruct getNegative()
	{
		return theNegative;
	}
}
