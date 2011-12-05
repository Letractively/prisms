/*
 * AssignmentOperator.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;

/** Represents an assignment, either a straight assignment or an assignment operator, like += */
public class ParsedAssignmentOperator extends prisms.lang.ParsedItem
{
	private String theName;

	private boolean isPrefix;

	private prisms.lang.ParsedItem theVariable;

	private prisms.lang.ParsedItem theOperand;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
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
	public prisms.lang.ParsedItem getVariable()
	{
		return theVariable;
	}

	/** @return The operand for the assignment operation */
	public prisms.lang.ParsedItem getOperand()
	{
		return theOperand;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		if(theOperand == null)
			return new prisms.lang.ParsedItem [] {theVariable};
		else
			return new prisms.lang.ParsedItem [] {theVariable, theOperand};
	}

	@Override
	public EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws EvaluationException
	{
		if(!(theVariable instanceof Assignable))
			throw new EvaluationException("The left-hand side of an assignment must be a variable", this,
				theVariable.getMatch().index);
		Assignable a = (Assignable) theVariable;

		EvaluationResult opType = theOperand == null ? null : theOperand.evaluate(env, false, withValues);
		if(opType != null)
		{
			if(opType.getPackageName() != null)
				throw new EvaluationException(opType.getFirstVar() + " cannot be resolved to a variable", this,
					theVariable.getMatch().index);
			if(opType.isType())
				throw new EvaluationException(opType.getType() + " cannot be resolved to a variable", this,
					theVariable.getMatch().index);
			if(opType.getControl() != null)
				throw new EvaluationException("Syntax error: misplaced construct", this, getStored("name").index);
		}

		EvaluationResult preRes;
		if(!withValues || theName.equals("="))
			preRes = a.evaluate(env, false, false);
		else
			preRes = a.getValue(env, this);

		Object ret;
		Object toSet;
		if("=".equals(theName))
		{
			if(preRes != null && !preRes.getType().isAssignable(opType.getType()))
				throw new EvaluationException("Type mismatch: Cannot convert from " + opType.getType() + " to "
					+ preRes.typeString(), this, theOperand.getMatch().index);
			ret = toSet = withValues ? opType.getValue() : null;
		}
		else if("++".equals(theName) || "--".equals(theName))
		{
			ret = preRes.getValue();
			int adjust = 1;
			if("--".equals(theName))
				adjust = -adjust;
			if(Character.TYPE.equals(preRes))
				toSet = withValues ? Character.valueOf((char) (((Character) ret).charValue() + adjust)) : null;
			else if(preRes.getType().isPrimitive() && !Boolean.TYPE.equals(preRes.getType().getBaseType()))
			{
				Number num = (Number) ret;
				if(!withValues)
					toSet = null;
				else if(ret instanceof Double)
					toSet = Double.valueOf(num.doubleValue() + adjust);
				else if(ret instanceof Float)
					toSet = Float.valueOf(num.floatValue() + adjust);
				else if(ret instanceof Long)
					toSet = Long.valueOf(num.longValue() + adjust);
				else if(ret instanceof Integer)
					toSet = Integer.valueOf(num.intValue() + adjust);
				else if(ret instanceof Short)
					toSet = Short.valueOf((short) (num.shortValue() + adjust));
				else if(ret instanceof Byte)
					toSet = withValues ? Byte.valueOf((byte) (num.byteValue() + adjust)) : null;
				else
					throw new EvaluationException("The operator " + theName + " is not defined for type " + preRes,
						this, getStored("name").index);
			}
			else
				throw new EvaluationException("The operator " + theName + " is not defined for type "
					+ preRes.typeString(), this, getStored("name").index);
			if(isPrefix)
				ret = toSet;
		}
		else if("+=".equals(theName) || "-=".equals(theName) || "*=".equals(theName) || "/=".equals(theName)
			|| "%".equals(theName))
		{
			if(preRes.getType().isPrimitive() && !Boolean.TYPE.equals(preRes.getType().getBaseType())
				&& opType.getType().isPrimitive() && !Boolean.TYPE.equals(opType.getType().getBaseType()))
			{
				ret = preRes.getValue();
				Number op1, op2;
				if(preRes.getValue() instanceof Character)
					op1 = withValues ? Integer.valueOf(((Character) preRes.getValue()).charValue()) : null;
				else
					op1 = (Number) preRes.getValue();
				if(opType.getValue() instanceof Character)
					op2 = Integer.valueOf(((Character) opType.getValue()).charValue());
				else
					op2 = (Number) opType.getValue();
				if(withValues)
				{
					if(preRes.getType().getBaseType().equals(Double.TYPE))
						ret = Double.valueOf(ParsedBinaryOp.mathF(theName, op1, op2));
					else if(preRes.getType().getBaseType().equals(Float.TYPE))
						ret = Float.valueOf((float) ParsedBinaryOp.mathF(theName, op1, op2));
					else if(preRes.getType().getBaseType().equals(Long.TYPE))
						ret = Long.valueOf(ParsedBinaryOp.mathI(preRes.getType().getBaseType(), theName, op1, op2));
					else if(preRes.getType().getBaseType().equals(Integer.TYPE))
						ret = Integer.valueOf((int) ParsedBinaryOp.mathI(preRes.getType().getBaseType(), theName, op1,
							op2));
					else if(preRes.getType().getBaseType().equals(Character.TYPE))
						ret = Integer.valueOf((int) ParsedBinaryOp.mathI(preRes.getType().getBaseType(), theName, op1,
							op2));
					else if(preRes.getType().getBaseType().equals(Short.TYPE))
						ret = Short.valueOf((short) ParsedBinaryOp.mathI(preRes.getType().getBaseType(), theName, op1,
							op2));
					else if(preRes.getType().getBaseType().equals(Byte.TYPE))
						ret = Byte.valueOf((byte) ParsedBinaryOp.mathI(preRes.getType().getBaseType(), theName, op1,
							op2));
					else
						throw new EvaluationException("The operator " + theName + " is not defined for type "
							+ preRes.typeString(), this, getStored("name").index);
				}
				else
					ret = null;
				toSet = ret;
			}
			else
				throw new EvaluationException("The operator " + theName + " is not defined for type "
					+ preRes.typeString(), this, getStored("name").index);
		}
		else if("|=".equals(theName) || "&=".equals(theName) || "^=".equals(theName))
		{
			if(preRes.isIntType() && opType.isIntType())
			{
				if(withValues)
				{
					Number op1 = Character.TYPE.equals(preRes.getType().getBaseType()) ? Integer
						.valueOf(((Character) preRes.getValue()).charValue()) : (Number) preRes.getValue();
					Number op2 = Character.TYPE.equals(opType.getType().getBaseType()) ? Integer
						.valueOf(((Character) opType.getValue()).charValue()) : (Number) opType.getValue();
					long val;
					if("|=".equals(theName))
						val = op1.longValue() | op2.longValue();
					else if("&=".equals(theName))
						val = op1.longValue() & op2.longValue();
					else
						val = op1.longValue() ^ op2.longValue();
					if(Long.TYPE.equals(preRes.getType().getBaseType()))
						ret = toSet = Long.valueOf(val);
					else if(Integer.TYPE.equals(preRes.getType().getBaseType()))
						ret = toSet = Integer.valueOf((int) val);
					else if(Short.TYPE.equals(preRes.getType().getBaseType()))
						ret = toSet = Short.valueOf((short) val);
					else if(Byte.TYPE.equals(preRes.getType().getBaseType()))
						ret = toSet = Byte.valueOf((byte) val);
					else if(Character.TYPE.equals(preRes.getType().getBaseType()))
						ret = toSet = Character.valueOf((char) val);
					else
						throw new EvaluationException("The operator " + theName + " is not defined for types " + preRes
							+ ", " + opType, this, getStored("name").index);
				}
				else
					ret = toSet = null;
			}
			else if(Boolean.TYPE.equals(preRes.getType().getBaseType())
				&& Boolean.TYPE.equals(opType.getType().getBaseType()))
			{
				boolean val;
				if("|=".equals(theName))
					val = ((Boolean) preRes.getValue()).booleanValue() | ((Boolean) opType.getValue()).booleanValue();
				else if("&=".equals(theName))
					val = ((Boolean) preRes.getValue()).booleanValue() & ((Boolean) opType.getValue()).booleanValue();
				else
					val = ((Boolean) preRes.getValue()).booleanValue() ^ ((Boolean) opType.getValue()).booleanValue();
				ret = toSet = Boolean.valueOf(val);
			}
			else
				throw new EvaluationException("The operator " + theName + " is not defined for type " + preRes, this,
					getStored("name").index);
		}
		else if("<<=".equals(theName) || ">>=".equals(theName) || ">>>=".equals(theName))
		{
			if(preRes.isIntType() && opType.isIntType())
			{
				if(withValues)
				{
					Number op1 = Character.TYPE.equals(preRes.getType().getBaseType()) ? Integer
						.valueOf(((Character) preRes.getValue()).charValue()) : (Number) preRes.getValue();
					Number op2 = Character.TYPE.equals(opType.getType().getBaseType()) ? Integer
						.valueOf(((Character) opType.getValue()).charValue()) : (Number) opType.getValue();
					if("<<=".equals(theName))
					{
						if(Long.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Long.valueOf(op1.longValue() << op2.longValue());
						else if(Integer.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Integer.valueOf(op1.intValue() << op2.intValue());
						else if(Short.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Short.valueOf((short) (op1.shortValue() << op2.shortValue()));
						else if(Byte.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Byte.valueOf((byte) (op1.byteValue() << op2.byteValue()));
						else if(Character.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Character.valueOf((char) (op1.shortValue() << op2.shortValue()));
						else
							throw new EvaluationException("The operator " + theName + " is not defined for types "
								+ preRes + ", " + opType, this, getStored("name").index);
					}
					else if(">>=".equals(theName))
					{
						if(Long.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Long.valueOf(op1.longValue() >> op2.longValue());
						else if(Integer.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Integer.valueOf(op1.intValue() >> op2.intValue());
						else if(Short.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Short.valueOf((short) (op1.shortValue() >> op2.shortValue()));
						else if(Byte.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Byte.valueOf((byte) (op1.byteValue() >> op2.byteValue()));
						else if(Character.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Character.valueOf((char) (op1.shortValue() >> op2.shortValue()));
						else
							throw new EvaluationException("The operator " + theName + " is not defined for types "
								+ preRes + ", " + opType, this, getStored("name").index);
					}
					else
					{
						if(Long.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Long.valueOf(op1.longValue() >>> op2.longValue());
						else if(Integer.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Integer.valueOf(op1.intValue() >>> op2.intValue());
						else if(Short.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Short.valueOf((short) (op1.shortValue() >>> op2.shortValue()));
						else if(Byte.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Byte.valueOf((byte) (op1.byteValue() >>> op2.byteValue()));
						else if(Character.TYPE.equals(preRes.getType().getBaseType()))
							ret = toSet = Character.valueOf((char) (op1.shortValue() >>> op2.shortValue()));
						else
							throw new EvaluationException("The operator " + theName + " is not defined for types "
								+ preRes + ", " + opType, this, getStored("name").index);
					}
				}
				else
					ret = toSet = null;
			}
			else
				throw new EvaluationException("The operator " + theName + " is not defined for type " + preRes, this,
					getStored("name").index);
		}
		else
			throw new EvaluationException("Assignment operator " + theName + " not recognized", this,
				getStored("name").index);

		if(!withValues)
			return preRes;
		if(preRes == null)
			preRes = opType;
		a.assign(new EvaluationResult(preRes.getType(), toSet), env, this);
		return new EvaluationResult(preRes.getType(), ret);
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		if(theOperand == null && isPrefix)
			ret.append(theName);
		ret.append(theVariable);
		if(theOperand != null || !isPrefix)
			ret.append(theName);
		if(theOperand != null)
			ret.append(theOperand);
		return ret.toString();
	}
}
