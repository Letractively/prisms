/*
 * AssignmentOperator.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents an assignment, either a straight assignment or an assignment operator, like += */
public class ParsedAssignmentOperator extends prisms.lang.ParseStruct
{
	private String theName;

	private boolean isPrefix;

	private prisms.lang.ParseStruct theVariable;

	private prisms.lang.ParseStruct theOperand;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theName = getStored("name").text;
		theVariable = parser.parseStructures(this, getStored("variable"))[0];
		prisms.lang.ParseMatch opMatch = getStored("operand");
		if(opMatch != null)
			theOperand = parser.parseStructures(this, opMatch)[0];
		else
			for(prisms.lang.ParseMatch m : match.getParsed())
			{
				if(m.config.getName().equals("pre-op"))
					isPrefix = false;
				else if(m.config.getName().equals("op"))
					isPrefix = true;
			}
	}

	/** @return The name of this operator */
	public String getName()
	{
		return theName;
	}

	/** @return Whether, if this operator is unary, the operator occurred before the variable */
	public boolean isPrefix()
	{
		return isPrefix;
	}

	/** @return The variable whose value will be assigned with this assignment */
	public prisms.lang.ParseStruct getVariable()
	{
		return theVariable;
	}

	/** @return The operand for the assignment operation */
	public prisms.lang.ParseStruct getOperand()
	{
		return theOperand;
	}
}
