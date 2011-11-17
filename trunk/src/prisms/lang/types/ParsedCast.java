/*
 * ParsedCast.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents a cast from one type to another */
public class ParsedCast extends prisms.lang.ParseStruct
{
	private prisms.lang.ParseStruct theType;

	private prisms.lang.ParseStruct theValue;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theType = parser.parseStructures(this, getStored("type"))[0];
		theValue = parser.parseStructures(this, getStored("value"))[0];
	}

	/** @return The type that the value is being cast to */
	public prisms.lang.ParseStruct getType()
	{
		return theType;
	}

	/** @return The value that is being type-cast */
	public prisms.lang.ParseStruct getValue()
	{
		return theValue;
	}

	@Override
	public String toString()
	{
		return "(" + theType + ") " + theValue;
	}
}
