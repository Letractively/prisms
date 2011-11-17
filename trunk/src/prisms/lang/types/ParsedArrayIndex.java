/*
 * ParsedArrayIndex.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents an array index operation */
public class ParsedArrayIndex extends prisms.lang.ParseStruct
{
	private prisms.lang.ParseStruct theArray;

	private prisms.lang.ParseStruct theIndex;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theArray = parser.parseStructures(this, getStored("array"))[0];
		theIndex = parser.parseStructures(this, getStored("index"))[0];
	}

	/** @return The array that is being indexed */
	public prisms.lang.ParseStruct getArray()
	{
		return theArray;
	}

	/** @return The index that is being retrieved */
	public prisms.lang.ParseStruct getIndex()
	{
		return theIndex;
	}
}
