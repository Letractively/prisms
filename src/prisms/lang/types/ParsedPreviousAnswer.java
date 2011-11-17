/*
 * ParsedPreviousAnswer.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Allows the results of previous calculations to be accessed dynamically */
public class ParsedPreviousAnswer extends prisms.lang.ParseStruct
{
	private int theIndex;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		prisms.lang.ParseMatch indexMatch = getStored("index");
		if(indexMatch != null)
			theIndex = Integer.parseInt(indexMatch.text);
	}

	/** @return The index (starting at 0) of the answer this refers to */
	public int getIndex()
	{
		return theIndex;
	}
}
