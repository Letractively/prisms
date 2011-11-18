/*
 * ParsedConditional.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;
import prisms.lang.ParsedItem;

/** Represents a conditional expression of the form condition ? affirmative : negative */
public class ParsedConditional extends ParsedItem
{
	private ParsedItem theCondition;

	private ParsedItem theAffirmative;

	private ParsedItem theNegative;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theCondition = parser.parseStructures(this, getStored("condition"))[0];
		theAffirmative = parser.parseStructures(this, getStored("affirmative"))[0];
		theNegative = parser.parseStructures(this, getStored("negative"))[0];
	}

	/** @return The condition determining which expression is evaluated */
	public ParsedItem getCondition()
	{
		return theCondition;
	}

	/** @return The expression that is evaluated if the condition evaluates to true */
	public ParsedItem getAffirmative()
	{
		return theAffirmative;
	}

	/** @return The expression that is evaluated if the condition evaluates to false */
	public ParsedItem getNegative()
	{
		return theNegative;
	}

	@Override
	public EvaluationResult<Object> evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		EvaluationResult<?> condition = theCondition.evaluate(env, false, withValues);
		if(condition.isType() || condition.getPackageName() != null)
			throw new EvaluationException(condition.typeString()
				+ " cannot be resolved to a variable", this, theCondition.getMatch().index);
		if(!Boolean.TYPE.equals(condition.getType()))
			throw new EvaluationException("Type mismatch: cannot convert from "
				+ condition.typeString() + " to boolean", this, theCondition.getMatch().index);
		boolean condEval = withValues && ((Boolean) condition.getValue()).booleanValue();
		EvaluationResult<?> affirm = theAffirmative.evaluate(env, false, withValues && condEval);
		if(affirm.isType() || affirm.getPackageName() != null)
			throw new EvaluationException(
				affirm.typeString() + " cannot be resolved to a variable", this,
				theAffirmative.getMatch().index);
		EvaluationResult<?> negate = theNegative.evaluate(env, false, withValues && !condEval);
		if(negate.isType() || negate.getPackageName() != null)
			throw new EvaluationException(
				negate.typeString() + " cannot be resolved to a variable", this,
				theNegative.getMatch().index);
		Class<?> max = prisms.lang.PrismsLangUtils.getMaxType(affirm.getType(), negate.getType());
		if(max == null)
			throw new EvaluationException("Incompatible types in conditional expression: "
				+ affirm.typeString() + " and " + negate.typeString(), this,
				theAffirmative.getMatch().index);
		if(!withValues)
			return new EvaluationResult<Object>(max, null);
		else if(condEval)
			return new EvaluationResult<Object>(max, affirm.getValue());
		else
			return new EvaluationResult<Object>(max, negate.getValue());
	}
}
