/*
 * ParsedParenthetic.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationEnvironment;
import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;

/** Represents a parenthetic expression */
public class ParsedParenthetic extends Assignable
{
	private prisms.lang.ParsedItem theContent;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theContent = parser.parseStructures(this, getStored("content"))[0];
	}

	/** @return The content of this parenthetical */
	public prisms.lang.ParsedItem getContent()
	{
		return theContent;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		return new prisms.lang.ParsedItem [] {theContent};
	}

	@Override
	public String toString()
	{
		return "(" + theContent + ")";
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		return theContent.evaluate(env, asType, withValues);
	}

	@Override
	public EvaluationResult getValue(EvaluationEnvironment env, ParsedAssignmentOperator assign)
		throws EvaluationException
	{
		if(!(theContent instanceof Assignable))
			throw new EvaluationException("Invalid argument for assignment operator " + assign.getName(), this,
				theContent.getMatch().index);
		return ((Assignable) theContent).getValue(env, assign);
	}

	@Override
	public void assign(EvaluationResult value, EvaluationEnvironment env, ParsedAssignmentOperator assign)
		throws EvaluationException
	{
		if(!(theContent instanceof Assignable))
			throw new EvaluationException("Invalid argument for assignment operator " + assign.getName(), this,
				theContent.getMatch().index);
		((Assignable) theContent).assign(value, env, assign);
	}
}
