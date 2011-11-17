/*
 * ParsedParenthetic.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents a parenthetic expression */
public class ParsedParenthetic extends prisms.lang.ParseStruct
{
	private prisms.lang.ParseStruct theContent;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theContent = parser.parseStructures(this, getStored("content"))[0];
	}

	/** @return The content of this parenthetical */
	public prisms.lang.ParseStruct getContent()
	{
		return theContent;
	}

	@Override
	public String toString()
	{
		return "(" + theContent + ")";
	}
}
