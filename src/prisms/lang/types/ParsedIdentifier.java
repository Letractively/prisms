/*
 * ParsedIdentifier.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** A simple identifier */
public class ParsedIdentifier extends prisms.lang.ParseStruct
{
	private String theName;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theName = getStored("name").text;
	}

	/** @return This identifier's name */
	public String getName()
	{
		return theName;
	}

	@Override
	public String toString()
	{
		return theName;
	}
}
