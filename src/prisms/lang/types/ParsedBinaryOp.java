/*
 * ParsedBinaryOp.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;
import prisms.lang.ParseException;
import prisms.lang.ParsedItem;

/** Represents an operation on two operands */
public class ParsedBinaryOp extends prisms.lang.ParsedItem
{
	private String theName;

	private prisms.lang.ParsedItem theOp1;

	private prisms.lang.ParsedItem theOp2;

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
		for(prisms.lang.ParseMatch m : match.getParsed())
		{
			if(m.config.getName().equals("op"))
			{
				if(theOp1 == null)
					theOp1 = parser.parseStructures(this, m)[0];
				else
				{
					theOp2 = parser.parseStructures(this, m)[0];
					break;
				}
			}
		}
		if(theOp1 == null)
			throw new ParseException("No op for configured binary operation: " + getMatch().config, getRoot().getFullCommand(),
				getMatch().index);
		if(getMatch().isComplete() && theOp2 == null)
			throw new ParseException("Only one op for configured binary operation: " + getMatch().config, getRoot().getFullCommand(),
				getMatch().index);
	}

	/** @return The name of the operation */
	public String getName()
	{
		return theName;
	}

	/** @return The first operand of the operation */
	public prisms.lang.ParsedItem getOp1()
	{
		return theOp1;
	}

	/** @return The second operand of the operation */
	public prisms.lang.ParsedItem getOp2()
	{
		return theOp2;
	}

	@Override
	public ParsedItem [] getDependents()
	{
		return new ParsedItem [] {theOp1, theOp2};
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
		if(theOp1 == dependent)
			theOp1 = toReplace;
		else if(theOp2 == dependent)
			theOp2 = toReplace;
		else
			throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public String toString()
	{
		return theOp1.toString() + theName + theOp2.toString();
	}

	@Override
	public EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues) throws EvaluationException
	{
		EvaluationResult res1 = theOp1.evaluate(env, false, withValues);
		EvaluationResult res2 = theOp2.evaluate(env, false, withValues);
		if(res1.isType() || res1.getPackageName() != null)
			throw new EvaluationException(res1.getFirstVar() + " cannot be resolved to a variable", this, getOp1().getMatch().index);
		if(res1.getControl() != null)
			throw new EvaluationException("Syntax error: misplaced construct", this, getStored("name").index);
		if(res2.isType() || res2.getPackageName() != null)
			throw new EvaluationException(res2.getFirstVar() + " cannot be resolved to a variable", this, getOp2().getMatch().index);
		if(res2.getControl() != null)
			throw new EvaluationException("Syntax error: misplaced construct", this, getStored("name").index);

		if("+".equals(theName) || "-".equals(theName) || "*".equals(theName) || "/".equals(theName) || "%".equals(theName))
		{
			if("+".equals(theName)
				&& (String.class.equals(res1.getType().getBaseType()) || String.class.equals(res2.getType().getBaseType())))
			{
				StringBuilder value = null;
				if(withValues)
					value = new StringBuilder().append(res1.getValue()).append(res2.getValue());
				return new EvaluationResult(new prisms.lang.Type(String.class), withValues ? value.toString() : null);
			}
			if(!res1.getType().isPrimitive() || Boolean.TYPE.equals(res1.getType().getBaseType()) || !res2.getType().isPrimitive()
				|| Boolean.TYPE.equals(res2.getType().getBaseType()))
				throw new EvaluationException("The operator " + theName + " is not defined for types " + res1 + ", " + res2, this,
					getStored("name").index);
			prisms.lang.Type max = res1.getType().getCommonType(res2.getType());
			Number op1, op2;
			if(res1.getValue() instanceof Character)
				op1 = withValues ? Integer.valueOf(((Character) res1.getValue()).charValue()) : null;
			else
				op1 = (Number) res1.getValue();
			if(res2.getValue() instanceof Character)
				op2 = Integer.valueOf(((Character) res2.getValue()).charValue());
			else
				op2 = (Number) res2.getValue();
			Object res;
			if(withValues)
			{
				if(max.getBaseType().equals(Double.TYPE))
					res = Double.valueOf(mathF(theName, op1, op2));
				else if(max.getBaseType().equals(Float.TYPE))
					res = Float.valueOf((float) mathF(theName, op1, op2));
				else if(max.getBaseType().equals(Long.TYPE))
					res = Long.valueOf(mathI(max.getBaseType(), theName, op1, op2));
				else if(max.getBaseType().equals(Integer.TYPE))
					res = Integer.valueOf((int) mathI(max.getBaseType(), theName, op1, op2));
				else if(max.getBaseType().equals(Character.TYPE))
					res = Integer.valueOf((int) mathI(max.getBaseType(), theName, op1, op2));
				else if(max.getBaseType().equals(Short.TYPE))
					res = Short.valueOf((short) mathI(max.getBaseType(), theName, op1, op2));
				else if(max.getBaseType().equals(Byte.TYPE))
					res = Byte.valueOf((byte) mathI(max.getBaseType(), theName, op1, op2));
				else
					throw new EvaluationException("The operator " + theName + " is not defined for type " + max, this,
						getStored("name").index);
			}
			else
				res = null;
			return new EvaluationResult(max, res);
		}
		else if("<".equals(theName) || "<=".equals(theName) || ">".equals(theName) || ">=".equals(theName))
		{
			if(!res1.getType().isPrimitive() || Boolean.TYPE.equals(res1.getType().getBaseType()) || !res2.getType().isPrimitive()
				|| Boolean.TYPE.equals(res2.getType().getBaseType()))
				throw new EvaluationException("The operator " + theName + " is not defined for types " + res1 + ", " + res2, this,
					getStored("name").index);
			prisms.lang.Type max = res1.getType().getCommonType(res2.getType());
			Number op1, op2;
			if(res1.getValue() instanceof Character)
				op1 = withValues ? Integer.valueOf(((Character) res1.getValue()).charValue()) : null;
			else
				op1 = (Number) res1.getValue();
			if(res2.getValue() instanceof Character)
				op2 = Integer.valueOf(((Character) res2.getValue()).charValue());
			else
				op2 = (Number) res2.getValue();
			if(!withValues)
				return new EvaluationResult(new prisms.lang.Type(Boolean.TYPE), (Boolean) null);
			else if(max.equals(Double.TYPE) || max.equals(Float.TYPE))
				return new EvaluationResult(new prisms.lang.Type(Boolean.TYPE), Boolean.valueOf(compareF(theName, op1, op2)));
			else
				return new EvaluationResult(new prisms.lang.Type(Boolean.TYPE), Boolean.valueOf(compareI(theName, op1, op2)));
		}
		else if("==".equals(theName) || "!=".equals(theName))
		{
			boolean ret;
			if(Boolean.TYPE.equals(res1.getType().getBaseType()) && Boolean.TYPE.equals(res2.getType().getBaseType()))
				ret = withValues ? ((Boolean) res1.getValue()).booleanValue() == ((Boolean) res2.getValue()).booleanValue() : false;
			else if(Boolean.TYPE.equals(res1.getType().getBaseType()) || Boolean.TYPE.equals(res2.getType().getBaseType()))
				throw new EvaluationException("The operator " + theName + " is not defined for types " + res1 + ", " + res2, this,
					getStored("name").index);
			else if(Character.TYPE.equals(res1.getType().getBaseType()) && Character.TYPE.equals(res2.getType().getBaseType()))
				ret = withValues ? ((Character) res1.getValue()).charValue() == ((Character) res2.getValue()).charValue() : false;
			else if(Character.TYPE.equals(res1.getType().getBaseType()) && res2.getType().isPrimitive())
				ret = withValues ? ((Character) res1.getValue()).charValue() == ((Number) res2.getValue()).doubleValue() : false;
			else if(Character.TYPE.equals(res2.getType().getBaseType()) && res1.getType().isPrimitive())
				ret = withValues ? ((Character) res2.getValue()).charValue() == ((Number) res1.getValue()).doubleValue() : false;
			else if(res1.getType().isPrimitive() && res2.getType().isPrimitive())
				ret = withValues ? ((Number) res1.getValue()).doubleValue() == ((Number) res2.getValue()).doubleValue() : false;
			else if(res1.getType().isPrimitive() || res2.getType().isPrimitive())
				throw new EvaluationException("The operator " + theName + " is not defined for types " + res1.typeString() + ", "
					+ res2.typeString(), this, getStored("name").index);
			else
				ret = withValues ? res1.getValue() == res2.getValue() : false;
			if("!=".equals(theName))
				ret = !ret;
			return new EvaluationResult(new prisms.lang.Type(Boolean.TYPE), withValues ? null : Boolean.valueOf(ret));
		}
		else if("||".equals(theName))
		{
			if(Boolean.TYPE.equals(res1.getType().getBaseType()) && Boolean.TYPE.equals(res2.getType().getBaseType()))
				return new EvaluationResult(res1.getType(), withValues ? Boolean.valueOf(((Boolean) res1.getValue()).booleanValue()
					|| ((Boolean) res2.getValue()).booleanValue()) : null);
			throw new EvaluationException("The operator " + theName + " is not defined for types " + res1.typeString() + ", "
				+ res2.typeString(), this, getStored("name").index);
		}
		else if("&&".equals(theName))
		{
			if(Boolean.TYPE.equals(res1.getType().getBaseType()) && Boolean.TYPE.equals(res2.getType().getBaseType()))
				return new EvaluationResult(res1.getType(), withValues ? Boolean.valueOf(((Boolean) res1.getValue()).booleanValue()
					&& ((Boolean) res2.getValue()).booleanValue()) : null);
			throw new EvaluationException("The operator " + theName + " is not defined for types " + res1 + ", " + res2, this,
				getStored("name").index);
		}
		else if("|".equals(theName) || "&".equals(theName) || "^".equals(theName))
		{
			if(Boolean.TYPE.equals(res1.getType().getBaseType()) && Boolean.TYPE.equals(res2.getType()))
			{
				if(!withValues)
					return new EvaluationResult(res1.getType(), (Boolean) null);
				else if("|".equals(theName))
					return new EvaluationResult(res1.getType(), Boolean.valueOf(((Boolean) res1.getValue()).booleanValue()
						| ((Boolean) res2.getValue()).booleanValue()));
				else if("&".equals(theName))
					return new EvaluationResult(res1.getType(), Boolean.valueOf(((Boolean) res1.getValue()).booleanValue()
						& ((Boolean) res2.getValue()).booleanValue()));
				else
					return new EvaluationResult(res1.getType(), Boolean.valueOf(((Boolean) res1.getValue()).booleanValue()
						^ ((Boolean) res2.getValue()).booleanValue()));
			}
			else if(res1.isIntType() && res2.isIntType())
			{
				prisms.lang.Type max = res1.getType().getCommonType(res2.getType());
				if(!withValues)
					return new EvaluationResult(max, null);
				long val;
				if("|".equals(theName))
					val = ((Number) res1.getValue()).longValue() | ((Number) res2.getValue()).longValue();
				else if("&".equals(theName))
					val = ((Number) res1.getValue()).longValue() & ((Number) res2.getValue()).longValue();
				else
					val = ((Number) res1.getValue()).longValue() ^ ((Number) res2.getValue()).longValue();
				if(Long.TYPE.equals(max.getBaseType()))
					return new EvaluationResult(max, Long.valueOf(val));
				else if(Integer.TYPE.equals(max.getBaseType()))
					return new EvaluationResult(max, Integer.valueOf((int) val));
				else if(Character.TYPE.equals(max.getBaseType()))
					return new EvaluationResult(max, Character.valueOf((char) val));
				else if(Short.TYPE.equals(max.getBaseType()))
					return new EvaluationResult(max, Short.valueOf((short) val));
				else if(Byte.TYPE.equals(max.getBaseType()))
					return new EvaluationResult(max, Byte.valueOf((byte) val));
				else
					throw new EvaluationException("The operator " + theName + " is not defined for types " + res1 + ", " + res2, this,
						getStored("name").index);
			}
			else
				throw new EvaluationException("The operator " + theName + " is not defined for types " + res1.typeString() + ", "
					+ res2.typeString(), this, getStored("name").index);
		}
		else if("<<".equals(theName) || ">>".equals(theName) || ">>>".equals(theName))
		{
			prisms.lang.Type max = res1.getType().getCommonType(res2.getType());
			if(!withValues)
				return new EvaluationResult(max, null);
			long val;
			if("<<".equals(theName))
				val = ((Number) res1.getValue()).longValue() << ((Number) res2.getValue()).longValue();
			else if(">>".equals(theName))
				val = ((Number) res1.getValue()).longValue() >> ((Number) res2.getValue()).longValue();
			else
				val = ((Number) res1.getValue()).longValue() >>> ((Number) res2.getValue()).longValue();
			if(Long.TYPE.equals(max.getBaseType()))
				return new EvaluationResult(max, Long.valueOf(val));
			else if(Integer.TYPE.equals(max.getBaseType()))
				return new EvaluationResult(max, Integer.valueOf((int) val));
			else if(Character.TYPE.equals(max.getBaseType()))
				return new EvaluationResult(max, Character.valueOf((char) val));
			else if(Short.TYPE.equals(max.getBaseType()))
				return new EvaluationResult(max, Short.valueOf((short) val));
			else if(Byte.TYPE.equals(max.getBaseType()))
				return new EvaluationResult(max, Byte.valueOf((byte) val));
			else
				throw new EvaluationException("The operator " + theName + " is not defined for types " + res1 + ", " + res2, this,
					getStored("name").index);
		}
		else
			throw new EvaluationException("Binary operator " + theName + " not recognized", this, getStored("name").index);
	}

	static double mathF(String op, Number op1, Number op2)
	{
		if(op.startsWith("+"))
			return op1.doubleValue() + op2.doubleValue();
		else if(op.startsWith("-"))
			return op1.doubleValue() - op2.doubleValue();
		else if(op.startsWith("*"))
			return op1.doubleValue() * op2.doubleValue();
		else if(op.startsWith("/"))
			return op1.doubleValue() / op2.doubleValue();
		else if(op.startsWith("%"))
			return op1.doubleValue() % op2.doubleValue();
		else
			throw new IllegalArgumentException("Unrecognized operator: " + op);
	}

	static long mathI(Class<?> type, String op, Number op1, Number op2)
	{
		if(type.equals(Long.TYPE))
		{
			if(op.startsWith("+"))
				return op1.longValue() + op2.longValue();
			else if(op.startsWith("-"))
				return op1.longValue() - op2.longValue();
			else if(op.startsWith("*"))
				return op1.longValue() * op2.longValue();
			else if(op.startsWith("/"))
				return op1.longValue() / op2.longValue();
			else if(op.startsWith("%"))
				return op1.longValue() % op2.longValue();
			else
				throw new IllegalArgumentException("Unrecognized operator: " + op);
		}
		else if(type.equals(Integer.TYPE))
		{
			if(op.startsWith("+"))
				return op1.intValue() + op2.intValue();
			else if(op.startsWith("-"))
				return op1.intValue() - op2.intValue();
			else if(op.startsWith("*"))
				return op1.intValue() * op2.intValue();
			else if(op.startsWith("/"))
				return op1.intValue() / op2.intValue();
			else if(op.startsWith("%"))
				return op1.intValue() % op2.intValue();
			else
				throw new IllegalArgumentException("Unrecognized operator: " + op);
		}
		else if(type.equals(Short.TYPE))
		{
			if(op.startsWith("+"))
				return op1.shortValue() + op2.shortValue();
			else if(op.startsWith("-"))
				return op1.shortValue() - op2.shortValue();
			else if(op.startsWith("*"))
				return op1.shortValue() * op2.shortValue();
			else if(op.startsWith("/"))
				return op1.shortValue() / op2.shortValue();
			else if(op.startsWith("%"))
				return op1.shortValue() % op2.shortValue();
			else
				throw new IllegalArgumentException("Unrecognized operator: " + op);
		}
		else if(type.equals(Byte.TYPE))
		{
			if(op.startsWith("+"))
				return op1.byteValue() + op2.byteValue();
			else if(op.startsWith("-"))
				return op1.byteValue() - op2.byteValue();
			else if(op.startsWith("*"))
				return op1.byteValue() * op2.byteValue();
			else if(op.startsWith("/"))
				return op1.byteValue() / op2.byteValue();
			else if(op.startsWith("%"))
				return op1.byteValue() % op2.byteValue();
			else
				throw new IllegalArgumentException("Unrecognized operator: " + op);
		}
		else if(type.equals(Character.TYPE))
		{
			if(op.startsWith("+"))
				return op1.shortValue() + op2.shortValue();
			else if(op.startsWith("-"))
				return op1.shortValue() - op2.shortValue();
			else if(op.startsWith("*"))
				return op1.shortValue() * op2.shortValue();
			else if(op.startsWith("/"))
				return op1.shortValue() / op2.shortValue();
			else if(op.startsWith("%"))
				return op1.shortValue() % op2.shortValue();
			else
				throw new IllegalArgumentException("Unrecognized operator: " + op);
		}
		else
			throw new IllegalStateException("Unrecognized integer type: " + type.getName());
	}

	static boolean compareF(String op, Number op1, Number op2)
	{
		if("<".equals(op))
			return op1.doubleValue() < op2.doubleValue();
		else if("<=".equals(op))
			return op1.doubleValue() <= op2.doubleValue();
		else if(">".equals(op))
			return op1.doubleValue() > op2.doubleValue();
		else if(">=".equals(op))
			return op1.doubleValue() >= op2.doubleValue();
		else
			throw new IllegalStateException("Unrecognized compare op: " + op);
	}

	static boolean compareI(String op, Number op1, Number op2)
	{
		if("<".equals(op))
			return op1.longValue() < op2.longValue();
		else if("<=".equals(op))
			return op1.longValue() <= op2.longValue();
		else if(">".equals(op))
			return op1.longValue() > op2.longValue();
		else if(">=".equals(op))
			return op1.longValue() >= op2.longValue();
		else
			throw new IllegalStateException("Unrecognized compare op: " + op);
	}
}
