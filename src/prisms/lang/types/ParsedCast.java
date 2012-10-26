/*
 * ParsedCast.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.ParsedItem;

/** Represents a cast from one type to another */
public class ParsedCast extends prisms.lang.ParsedItem
{
	private prisms.lang.ParsedItem theType;

	private prisms.lang.ParsedItem theValue;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theType = parser.parseStructures(this, getStored("type"))[0];
		theValue = parser.parseStructures(this, getStored("value"))[0];
	}

	/** @return The type that the value is being cast to */
	public prisms.lang.ParsedItem getType()
	{
		return theType;
	}

	/** @return The value that is being type-cast */
	public prisms.lang.ParsedItem getValue()
	{
		return theValue;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		return new prisms.lang.ParsedItem [] {theType, theValue};
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
		if(theType == dependent)
			theType = toReplace;
		else if(theValue == dependent)
			theValue = toReplace;
		else
			throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public String toString()
	{
		return "(" + theType + ") " + theValue;
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws prisms.lang.EvaluationException
	{
		prisms.lang.EvaluationResult typeEval = theType.evaluate(env, true, false);
		if(!typeEval.isType())
			throw new prisms.lang.EvaluationException("Unrecognized type " + theType.getMatch().text, this, theType.getMatch().index);
		prisms.lang.EvaluationResult toCast = theValue.evaluate(env, false, withValues);
		if(toCast.getPackageName() != null || toCast.isType())
			throw new prisms.lang.EvaluationException(toCast.getFirstVar() + " cannot be resolved to a variable", theValue,
				theValue.getMatch().index);
		if(typeEval.getType().getCommonType(toCast.getType()) == null)
			throw new prisms.lang.EvaluationException("Cannot cast from " + toCast.getType() + " to " + typeEval.getType(), this,
				theType.getMatch().index);
		if(withValues)
			try
			{
				return new prisms.lang.EvaluationResult(typeEval.getType(), typeEval.getType().cast(toCast.getValue()));
			} catch(IllegalArgumentException e)
			{
				throw new prisms.lang.ExecutionException(new prisms.lang.Type(ClassCastException.class), new ClassCastException(
					(toCast.getValue() == null ? "null" : prisms.lang.Type.typeString(toCast.getValue().getClass()))
						+ " cannot be cast to " + typeEval.getType()), this, getStored("type").index);
			}
		else
			return new prisms.lang.EvaluationResult(typeEval.getType(), null);
	}
}
