/*
 * ParsedBinaryOp.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.ParseException;

/** Represents an operation on two operands */
public class ParsedBinaryOp extends prisms.lang.ParseStruct
{
	private String theName;

	private prisms.lang.ParseStruct theOp1;

	private prisms.lang.ParseStruct theOp2;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws ParseException
	{
		super.setup(parser, parent, match, start);
		try
		{
			theName = getStored("name").text;
		} catch(NullPointerException e)
		{
			throw new ParseException("No name for configured binary operation: "
				+ getMatch().config, getRoot().getFullCommand(), getStart());
		}
		for(prisms.lang.ParseMatch m : match.getParsed())
		{
			if(m.config.getName().equals("pre-op"))
				theOp1 = parser.parseStructures(this, m)[0];
			else if(m.config.getName().equals("op"))
			{
				theOp2 = parser.parseStructures(this, m)[0];
				break;
			}
		}
		if(theOp1 == null)
			throw new ParseException("No pre-op for configured binary operation: "
				+ getMatch().config, getRoot().getFullCommand(), getStart());
		if(theOp2 == null)
			throw new ParseException("No op for configured binary operation: " + getMatch().config,
				getRoot().getFullCommand(), getStart());
	}

	/** @return The name of the operation */
	public String getName()
	{
		return theName;
	}

	/** @return The first operand of the operation */
	public prisms.lang.ParseStruct getOp1()
	{
		return theOp1;
	}

	/** @return The second operand of the operation */
	public prisms.lang.ParseStruct getOp2()
	{
		return theOp2;
	}

	@Override
	public String toString()
	{
		return theOp1.toString() + theName + theOp2.toString();
	}
}
