/*
 * ParsedArrayIndex.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationEnvironment;
import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;

/** Represents an array index operation */
public class ParsedArrayIndex extends Assignable
{
	private prisms.lang.ParsedItem theArray;

	private prisms.lang.ParsedItem theIndex;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
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
	public EvaluationResult<?> evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		EvaluationResult<?> array = theArray.evaluate(env, false, withValues);
		if(array.isType() || array.getPackageName() != null)
			throw new EvaluationException(array.typeString() + " cannot be resolved to a variable",
				this, theArray.getMatch().index);
		if(!array.getType().isArray())
			throw new EvaluationException(
				"The type of the expression must resolve to an array but it resolved to "
					+ array.typeString(), this, theArray.getMatch().index);
		EvaluationResult<?> index = theIndex.evaluate(env, false, withValues);
		if(index.isType() || index.getPackageName() != null)
			throw new EvaluationException(index.typeString() + " cannot be resolved to a variable",
				this, theIndex.getMatch().index);
		if(!index.isIntType() || Long.TYPE.equals(index.getType()))
			throw new EvaluationException("Type mismatch: cannot convert from "
				+ index.typeString() + " to int", this, theIndex.getMatch().index);
		return new EvaluationResult<Object>(array.getType().getComponentType(), withValues
			? java.lang.reflect.Array.get(array.getValue(), ((Number) index.getValue()).intValue())
			: null);
	}

	@Override
	public EvaluationResult<?> getValue(EvaluationEnvironment env, ParsedAssignmentOperator assign)
		throws EvaluationException
	{
		return evaluate(env, false, true);
	}

	@Override
	public void assign(Object value, EvaluationEnvironment env, ParsedAssignmentOperator assign)
		throws EvaluationException
	{
		int index = ((Number) theIndex.evaluate(env, false, true).getValue()).intValue();
		Class<?> type = theArray.evaluate(env, false, false).getType();
		java.lang.reflect.Array.set(type, index, value);
	}
}
