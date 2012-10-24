/*
 * ParsedUnaryOp.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;
import prisms.lang.ParseException;
import prisms.lang.ParsedItem;
import prisms.lang.Type;

/** An operation on a single operand */
public class ParsedUnaryOp extends prisms.lang.ParsedItem
{
	private String theName;

	private boolean isPrefix;

	private prisms.lang.ParsedItem theOperand;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match) throws ParseException
	{
		super.setup(parser, parent, match);
		try
		{
			theName = getStored("name").text;
		} catch(NullPointerException e)
		{
			throw new ParseException("No name for configured binary operation: " + getMatch().config, getRoot().getFullCommand(),
				getMatch().index);
		}
		isPrefix = getDeepStoreIndex("op") < getDeepStoreIndex("name");
		for(prisms.lang.ParseMatch m : match.getParsed())
			if(m.config.getName().equals("op"))
				theOperand = parser.parseStructures(this, m)[0];
		if(getMatch().isComplete() && theOperand == null)
			throw new ParseException("No operand for configured unary operation: " + getMatch().config, getRoot().getFullCommand(),
				getMatch().index);
	}

	/** @return The name of the operation */
	public String getName()
	{
		return theName;
	}

	/** @return The operand of the operation */
	public prisms.lang.ParsedItem getOp()
	{
		return theOperand;
	}

	/** @return Whether this operator occurred before or after its operand */
	public boolean isPrefix()
	{
		return isPrefix;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		return new prisms.lang.ParsedItem [] {theOperand};
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
		if(theOperand == dependent)
			theOperand = toReplace;
		else
			throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public String toString()
	{
		if(isPrefix)
			return theName + theOperand.toString();
		else
			return theOperand.toString() + theName;
	}

	@Override
	public EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues) throws EvaluationException
	{
		EvaluationResult res = theOperand.evaluate(env, false, withValues);
		if(res.isType())
			throw new EvaluationException("The operator " + theName + " is not defined for type java.lang.Class", this,
				getStored("name").index);
		if(res.getType() == null)
			throw new EvaluationException(res.getFirstVar() + " cannot be resolved to a variable", this, theOperand.getMatch().index);
		if("+".equals(theName))
		{
			if(!res.getType().isPrimitive() || Boolean.TYPE.equals(res.getType().getBaseType()))
				throw new EvaluationException("The operator " + theName + " is not defined for type " + res.typeString(), this,
					getStored("name").index);
			return res;
		}
		else if("-".equals(theName))
		{
			if(Double.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Double.TYPE), withValues ? Double.valueOf(-((Number) res.getValue()).doubleValue())
					: null);
			else if(Float.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Float.TYPE), withValues ? Float.valueOf(-((Number) res.getValue()).floatValue())
					: null);
			else if(Long.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Long.TYPE), withValues ? Long.valueOf(-((Number) res.getValue()).longValue()) : null);
			else if(Integer.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Integer.TYPE), withValues ? Integer.valueOf(-((Number) res.getValue()).intValue())
					: null);
			else if(Character.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Integer.TYPE), withValues ? Integer.valueOf(-((Character) res.getValue()).charValue())
					: null);
			else if(Short.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Integer.TYPE), withValues ? Integer.valueOf(-((Number) res.getValue()).shortValue())
					: null);
			else if(Byte.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Integer.TYPE), withValues ? Integer.valueOf(-((Number) res.getValue()).byteValue())
					: null);
			else
				throw new EvaluationException("The operator " + theName + " is not defined for type " + res.typeString(), this,
					getStored("name").index);
		}
		else if("!".equals(theName))
		{
			if(Boolean.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Boolean.TYPE), withValues
					? Boolean.valueOf(!((Boolean) res.getValue()).booleanValue()) : null);
			else
				throw new EvaluationException("The operator " + theName + " is not defined for type " + res.typeString(), this,
					getStored("name").index);
		}
		else if("~".equals(theName))
		{
			if(Long.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Long.TYPE), withValues ? Long.valueOf(~((Long) res.getValue()).longValue()) : null);
			else if(Integer.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Integer.TYPE), withValues ? Integer.valueOf(~((Integer) res.getValue()).intValue())
					: null);
			else if(Short.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Integer.TYPE), withValues ? Integer.valueOf(~((Short) res.getValue()).shortValue())
					: null);
			else if(Byte.TYPE.equals(res.getType().getBaseType()))
				return new EvaluationResult(new Type(Integer.TYPE), withValues ? Integer.valueOf(~((Byte) res.getValue()).byteValue())
					: null);
			else
				throw new EvaluationException("The operator " + theName + " is not defined for type " + res.typeString(), this,
					getStored("name").index);
		}
		else
			throw new EvaluationException("Unary operator " + theName + " not recognized", this, getStored("name").index);
	}
}
