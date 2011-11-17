/*
 * ParsedUnaryOp.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.ParseException;

/** An operation on a single operand */
public class ParsedUnaryOp extends prisms.lang.ParseStruct
{
	private String theName;

	private boolean isPrefix;

	private prisms.lang.ParseStruct theOperand;

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
			{
				isPrefix = false;
				theOperand = parser.parseStructures(this, m)[0];
			}
			else if(m.config.getName().equals("op"))
			{
				isPrefix = true;
				theOperand = parser.parseStructures(this, m)[0];
			}
		}
		if(theOperand == null)
			throw new ParseException("No operand for configured unary operation: "
				+ getMatch().config, getRoot().getFullCommand(), getStart());
	}

	/** @return The name of the operation */
	public String getName()
	{
		return theName;
	}

	/** @return The operand of the operation */
	public prisms.lang.ParseStruct getOp()
	{
		return theOperand;
	}

	/** @return Whether this operator occurred before or after its operand */
	public boolean isPrefix()
	{
		return isPrefix;
	}

	@Override
	public String toString()
	{
		if(isPrefix)
			return theName + theOperand.toString();
		else
			return theOperand.toString() + theName;
	}
}
