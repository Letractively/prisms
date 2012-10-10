package prisms.lang.types;

import prisms.lang.ParsedItem;

/** Represents a throw statement */
public class ParsedThrow extends prisms.lang.ParsedItem
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
		if(!ret.getType().canAssignTo(Throwable.class))
			throw new prisms.lang.EvaluationException("No exception of type " + ret.getType()
				+ " may be thrown; an exception type must be a subclass of Throwable", theValue, theValue.getMatch().index);
		if(!env.canHandle(ret.getType()))
			throw new prisms.lang.EvaluationException("Unhandled exception type " + ret.getType(), theValue, theValue.getMatch().index);
		if(!withValues)
			return null;
		Throwable toThrow = (Throwable) ret.getValue();
		if(toThrow == null)
			toThrow = new NullPointerException();
		throw new prisms.lang.ExecutionException(ret.getType(), toThrow, this, this.getMatch().index);
	}

	/** @return The Throwable that this throw statement will evaluate and throw */
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
		return "throw " + theValue;
	}
}
