/*
 * ParsedDeclaration.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents a typed, parsed declaration */
public class ParsedDeclaration extends prisms.lang.ParseStruct
{
	private prisms.lang.ParseStruct theType;

	private String theName;

	private boolean isFinal;

	private int theArrayDimension;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theType = parser.parseStructures(this, getStored("type"))[0];
		theName = getStored("name").text;
		isFinal = getStored("final") != null;
		for(prisms.lang.ParseMatch m : match.getParsed())
			if("array".equals(m.config.get("storeAs")))
				theArrayDimension++;
	}

	/** @return The name of the declared variable */
	public String getName()
	{
		return theName;
	}

	/** @return Whether the variable in this declaration is marked as final */
	public boolean isFinal()
	{
		return isFinal;
	}

	/** @return The type of this declaration */
	public prisms.lang.ParseStruct getType()
	{
		return theType;
	}

	/** @return The dimension of this array declaration, or 0 if this declaration is not an array */
	public int getArrayDimension()
	{
		return theArrayDimension;
	}

	@Override
	public String toString()
	{
		return theType + " " + theName;
	}
}
