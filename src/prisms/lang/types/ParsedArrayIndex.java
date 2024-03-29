/*
 * ParsedArrayIndex.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.*;

/** Represents an array index operation */
public class ParsedArrayIndex extends Assignable
{
	private ParsedItem theArray;

	private ParsedItem theIndex;

	@Override
	public void setup(PrismsParser parser, ParsedItem parent, ParseMatch match) throws ParseException
	{
		super.setup(parser, parent, match);
		theArray = parser.parseStructures(this, getStored("array"))[0];
		theIndex = parser.parseStructures(this, getStored("index"))[0];
	}

	/** @return The array that is being indexed */
	public prisms.lang.ParsedItem getArray()
	{
		return theArray;
	}

	/** @return The index that is being retrieved */
	public prisms.lang.ParsedItem getIndex()
	{
		return theIndex;
	}

	@Override
	public ParsedItem [] getDependents()
	{
		return new ParsedItem [] {theArray, theIndex};
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException {
		if(dependent == theArray) {
			theArray = toReplace;
		}
		else if(dependent == theIndex)
			theIndex = toReplace;
		else
			throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public EvaluationResult evaluate(EvaluationEnvironment env, boolean asType, boolean withValues)
		throws EvaluationException
	{
		EvaluationResult array = theArray.evaluate(env, false, withValues);
		if(!array.isValue())
			throw new EvaluationException(array.typeString() + " cannot be resolved to a variable", this,
				theArray.getMatch().index);
		if(!array.getType().isArray())
			throw new EvaluationException("The type of the expression must resolve to an array but it resolved to "
				+ array.typeString(), this, theArray.getMatch().index);
		EvaluationResult index = theIndex.evaluate(env, false, withValues);
		if(!index.isValue())
			throw new EvaluationException(index.typeString() + " cannot be resolved to a variable", this,
				theIndex.getMatch().index);
		if(!index.isIntType() || Long.TYPE.equals(index.getType().getBaseType()))
			throw new EvaluationException("Type mismatch: cannot convert from " + index + " to int", this,
				theIndex.getMatch().index);
		return new EvaluationResult(array.getType().getComponentType(), withValues ? java.lang.reflect.Array.get(
			array.getValue(), ((Number) index.getValue()).intValue()) : null);
	}

	@Override
	public EvaluationResult getValue(EvaluationEnvironment env, ParsedAssignmentOperator assign)
		throws EvaluationException
	{
		return evaluate(env, false, true);
	}

	@Override
	public void assign(EvaluationResult value, EvaluationEnvironment env, ParsedAssignmentOperator assign)
		throws EvaluationException
	{
		int index = ((Number) theIndex.evaluate(env, false, true).getValue()).intValue();
		EvaluationResult res = theArray.evaluate(env, false, true);
		java.lang.reflect.Array.set(res.getValue(), index, value.getValue());
	}

	@Override
	public String toString()
	{
		return theArray + "[" + theIndex + "]";
	}
}
