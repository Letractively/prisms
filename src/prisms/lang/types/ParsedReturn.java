package prisms.lang.types;

import prisms.lang.ParsedItem;

/** Represents a return statement */
public class ParsedReturn extends prisms.lang.ParsedItem
{
	private ParsedItem theValue;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		prisms.lang.ParseMatch m = getStored("value");
		if(m != null)
			theValue = parser.parseStructures(this, m)[0];
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws prisms.lang.EvaluationException
	{
		if(theValue == null)
			return null;
		if(theValue instanceof ParsedStatementBlock)
			throw new prisms.lang.EvaluationException("Syntax error", this, theValue.getMatch().index);
		prisms.lang.EvaluationResult ret = theValue.evaluate(env, false, withValues);
		if(ret.getPackageName() != null || ret.isType())
			throw new prisms.lang.EvaluationException(ret.getFirstVar() + " cannot be resolved to a variable", theValue,
				theValue.getMatch().index);
		if(env.getReturnType() == null)
			throw new prisms.lang.EvaluationException("Cannot return a value except from within a function or method", this,
				getMatch().index);
		if(Void.TYPE.equals(env.getReturnType().getBaseType()))
			throw new prisms.lang.EvaluationException("Void methods cannot return a value", this, getMatch().index);
		if(!env.getReturnType().isAssignable(ret.getType()))
			throw new prisms.lang.EvaluationException("Type mismatch: cannot convert from " + ret.getType() + " to " + env.getReturnType(),
				theValue, theValue.getMatch().index);
		return new prisms.lang.EvaluationResult(prisms.lang.EvaluationResult.ControlType.RETURN, ret.getValue(), this);
	}

	/** @return The value that this return statement will evaluate and return */
	public ParsedItem getValue()
	{
		return theValue;
	}

	@Override
	public ParsedItem [] getDependents()
	{
		return new ParsedItem [] {theValue};
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
		if(theValue == dependent)
			theValue = toReplace;
		else
			throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public String toString()
	{
		if(theValue != null)
			return "return " + theValue;
		else
			return "return";
	}
}
