/*
 * AssignmentOperator.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;
import prisms.lang.ParsedItem;

/** Represents an assignment, either a straight assignment or an assignment operator, like += */
public class ParsedAssignmentOperator extends prisms.lang.ParsedItem
{
	private String theName;

	private boolean isPrefix;

	private prisms.lang.ParsedItem theVariable;

	private prisms.lang.ParsedItem theOperand;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
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
	public EvaluationResult<Object> evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws EvaluationException
	{
		EvaluationResult<?> type = theVariable.evaluate(env, false,
			withValues && !"=".equals(theName));
		EvaluationResult<?> opType = theOperand == null ? null : theOperand.evaluate(env, false,
			withValues);
		if(opType != null)
		{
			if(opType.getPackageName() != null)
				throw new EvaluationException(opType.getFirstVar()
					+ " cannot be resolved to a variable", this, theVariable.getMatch().index);
			if(opType.isType())
				throw new EvaluationException(opType.getType().getName()
					+ " cannot be resolved to a variable", this, theVariable.getMatch().index);
		}

		EvaluationResult<?> ctxType = null;
		java.lang.reflect.Field field = null;
		EvaluationResult<?> arrType = null;
		ParsedItem var = theVariable;
		while(var instanceof ParsedParenthetic)
			var = ((ParsedParenthetic) var).getContent();
		if(var instanceof ParsedIdentifier)
		{}
		else if(var instanceof ParsedArrayIndex)
		{
			ParsedArrayIndex idx = (ParsedArrayIndex) var;
			arrType = evaluate(idx.getArray(), env, false, withValues);
		}
		else if(var instanceof ParsedDeclaration)
		{
			ParsedDeclaration dec = (ParsedDeclaration) var;
			type = evaluate(dec.getType(), env, true, withValues);
			if(dec.getArrayDimension() > 0)
			{
				Class<?> c = type.getType();
				for(int i = 0; i < dec.getArrayDimension(); i++)
					c = Array.newInstance(c, 0).getClass();
				type = new EvaluationResult<?>(c);
			}
			if(!"=".equals(theName))
				throw new EvaluationException("Syntax error: " + dec.getName()
					+ " has not been assigned", this, getStored("name").index);
		}
		else if(var instanceof ParsedMethod)
		{
			ParsedMethod method = (ParsedMethod) var;
			if(method.isMethod())
				throw new EvaluationException("Invalid argument for operator " + theName, this, op
					.getVariable().getMatch().index);
			if(method.getContext() == null)
				ctxType = new EvaluationResult<?>(env.getImportMethodType(method.getName()));
			else
				ctxType = evaluate(method.getContext(), env, false, withValues);
			boolean isStatic = ctxType.isType();
			if(method.getName().equals("length") && ctxType.getType().isArray())
				throw new EvaluationException("The final field array.length cannot be assigned",
					this, method.getStored("name").index);
			try
			{
				field = ctxType.getType().getField(method.getName());
			} catch(Exception e)
			{
				throw new EvaluationException("Could not access field " + method.getName()
					+ " on type " + ctxType.typeString(), e, this, this.getStored("name").index);
			}
			if(field == null)
				throw new EvaluationException(ctxType.typeString() + "." + method.getName()
					+ " cannot be resolved or is not a field", this, this.getStored("name").index);
			if(env.usePublicOnly() && (field.getModifiers() & Modifier.PUBLIC) == 0)
				throw new EvaluationException(ctxType.typeString() + "." + method.getName()
					+ " is not visible", this, this.getStored("name").index);
			if(isStatic && (field.getModifiers() & Modifier.STATIC) == 0)
				throw new EvaluationException("Cannot make a static reference to non-static field "
					+ method.getName() + " from the type " + ctxType.typeString() + "."
					+ method.getName() + " is not static", this, this.getStored("name").index);
			if((field.getModifiers() & Modifier.FINAL) != 0)
				throw new EvaluationException("The final field " + ctxType.typeString() + "."
					+ method.getName() + " cannot be assigned", this,
					method.getStored("name").index);
		}
		else
			throw new EvaluationException("Invalid argument for assignment operator " + theName,
				this, theVariable.getMatch().index);

		Object ret;
		Object toSet;
		if("=".equals(theName))
		{
			if(!prisms.lang.PrismsLangUtils.canAssign(type.getType(), opType.getType()))
				throw new EvaluationException("Type mismatch: Cannot convert from "
					+ opType.getType().getName() + " to " + type.typeString(), this,
					theOperand.getMatch().index);
			ret = toSet = withValues ? opType.getValue() : null;
		}
		else if("++".equals(theName) || "--".equals(theName))
		{
			ret = type.getValue();
			int adjust = 1;
			if("--".equals(theName))
				adjust = -adjust;
			if(Character.TYPE.equals(type))
				toSet = withValues ? Character
					.valueOf((char) (((Character) ret).charValue() + adjust)) : null;
			else if(type.getType().isPrimitive() && !Boolean.TYPE.equals(type.getType()))
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
					throw new EvaluationException("The operator " + theName
						+ " is not defined for type " + type.typeString(), this,
						getStored("name").index);
			}
			else
				throw new EvaluationException("The operator " + theName
					+ " is not defined for type " + type.typeString(), this,
					getStored("name").index);
			if(isPrefix)
				ret = toSet;
		}
		else if("+=".equals(theName) || "-=".equals(theName) || "*=".equals(theName)
			|| "/=".equals(theName) || "%".equals(theName))
		{
			if(type.getType().isPrimitive() && !Boolean.TYPE.equals(type.getType())
				&& opType.getType().isPrimitive() && !Boolean.TYPE.equals(opType.getType()))
			{
				ret = type.getValue();
				Number op1, op2;
				if(type.getValue() instanceof Character)
					op1 = withValues ? Integer.valueOf(((Character) type.getValue()).charValue())
						: null;
				else
					op1 = (Number) type.getValue();
				if(opType.getValue() instanceof Character)
					op2 = Integer.valueOf(((Character) opType.getValue()).charValue());
				else
					op2 = (Number) opType.getValue();
				if(withValues)
				{
					if(type.getType().equals(Double.TYPE))
						ret = Double.valueOf(ParsedBinaryOp.mathF(theName, op1, op2));
					else if(type.getType().equals(Float.TYPE))
						ret = Float.valueOf((float) ParsedBinaryOp.mathF(theName, op1, op2));
					else if(type.getType().equals(Long.TYPE))
						ret = Long.valueOf(ParsedBinaryOp.mathI(type.getType(), theName, op1, op2));
					else if(type.getType().equals(Integer.TYPE))
						ret = Integer.valueOf((int) ParsedBinaryOp.mathI(type.getType(), theName,
							op1, op2));
					else if(type.getType().equals(Character.TYPE))
						ret = Integer.valueOf((int) ParsedBinaryOp.mathI(type.getType(), theName,
							op1, op2));
					else if(type.getType().equals(Short.TYPE))
						ret = Short.valueOf((short) ParsedBinaryOp.mathI(type.getType(), theName,
							op1, op2));
					else if(type.getType().equals(Byte.TYPE))
						ret = Byte.valueOf((byte) ParsedBinaryOp.mathI(type.getType(), theName,
							op1, op2));
					else
						throw new EvaluationException("The operator " + theName
							+ " is not defined for type " + type.typeString(), this,
							getStored("name").index);
				}
				else
					ret = null;
				toSet = ret;
			}
			else
				throw new EvaluationException("The operator " + theName
					+ " is not defined for type " + type.typeString(), this,
					getStored("name").index);
		}
		else if("|=".equals(theName) || "&=".equals(theName) || "^=".equals(theName))
		{
			if((type.isIntType() || Character.TYPE.equals(type.getType()))
				&& (opType.isIntType() || Character.TYPE.equals(opType.getType())))
			{
				if(withValues)
				{
					Number op1 = Character.TYPE.equals(type.getType()) ? Integer
						.valueOf(((Character) type.getValue()).charValue()) : (Number) type
						.getValue();
					Number op2 = Character.TYPE.equals(opType.getType()) ? Integer
						.valueOf(((Character) opType.getValue()).charValue()) : (Number) opType
						.getValue();
					long val;
					if("|=".equals(theName))
						val = op1.longValue() | op2.longValue();
					else if("&=".equals(theName))
						val = op1.longValue() & op2.longValue();
					else
						val = op1.longValue() ^ op2.longValue();
					if(Long.TYPE.equals(type.getType()))
						ret = toSet = Long.valueOf(val);
					else if(Integer.TYPE.equals(type.getType()))
						ret = toSet = Integer.valueOf((int) val);
					else if(Short.TYPE.equals(type.getType()))
						ret = toSet = Short.valueOf((short) val);
					else if(Byte.TYPE.equals(type.getType()))
						ret = toSet = Byte.valueOf((byte) val);
					else if(Character.TYPE.equals(type.getType()))
						ret = toSet = Character.valueOf((char) val);
					else
						throw new EvaluationException("The operator " + theName
							+ " is not defined for types " + type.typeString() + ", "
							+ opType.typeString(), this, getStored("name").index);
				}
				else
					ret = toSet = null;
			}
			else if(Boolean.TYPE.equals(type.getType()) && Boolean.TYPE.equals(opType.getType()))
			{
				boolean val;
				if("|=".equals(theName))
					val = ((Boolean) type.getValue()).booleanValue()
						| ((Boolean) opType.getValue()).booleanValue();
				else if("&=".equals(theName))
					val = ((Boolean) type.getValue()).booleanValue()
						& ((Boolean) opType.getValue()).booleanValue();
				else
					val = ((Boolean) type.getValue()).booleanValue()
						^ ((Boolean) opType.getValue()).booleanValue();
				ret = toSet = Boolean.valueOf(val);
			}
			else
				throw new EvaluationException("The operator " + theName
					+ " is not defined for type " + type.typeString(), this,
					getStored("name").index);
		}
		else if("<<=".equals(theName) || ">>=".equals(theName) || ">>>=".equals(theName))
		{
			if((type.isIntType() || Character.TYPE.equals(type.getType()))
				&& (opType.isIntType() || Character.TYPE.equals(opType.getType())))
			{
				if(withValues)
				{
					Number op1 = Character.TYPE.equals(type.getType()) ? Integer
						.valueOf(((Character) type.getValue()).charValue()) : (Number) type
						.getValue();
					Number op2 = Character.TYPE.equals(opType.getType()) ? Integer
						.valueOf(((Character) opType.getValue()).charValue()) : (Number) opType
						.getValue();
					if("<<=".equals(theName))
					{
						if(Long.TYPE.equals(type.getType()))
							ret = toSet = Long.valueOf(op1.longValue() << op2.longValue());
						else if(Integer.TYPE.equals(type.getType()))
							ret = toSet = Integer.valueOf(op1.intValue() << op2.intValue());
						else if(Short.TYPE.equals(type.getType()))
							ret = toSet = Short.valueOf((short) (op1.shortValue() << op2
								.shortValue()));
						else if(Byte.TYPE.equals(type.getType()))
							ret = toSet = Byte.valueOf((byte) (op1.byteValue() << op2.byteValue()));
						else if(Character.TYPE.equals(type.getType()))
							ret = toSet = Character.valueOf((char) (op1.shortValue() << op2
								.shortValue()));
						else
							throw new EvaluationException("The operator " + theName
								+ " is not defined for types " + type.typeString() + ", "
								+ opType.typeString(), this, getStored("name").index);
					}
					else if(">>=".equals(theName))
					{
						if(Long.TYPE.equals(type.getType()))
							ret = toSet = Long.valueOf(op1.longValue() >> op2.longValue());
						else if(Integer.TYPE.equals(type.getType()))
							ret = toSet = Integer.valueOf(op1.intValue() >> op2.intValue());
						else if(Short.TYPE.equals(type.getType()))
							ret = toSet = Short.valueOf((short) (op1.shortValue() >> op2
								.shortValue()));
						else if(Byte.TYPE.equals(type.getType()))
							ret = toSet = Byte.valueOf((byte) (op1.byteValue() >> op2.byteValue()));
						else if(Character.TYPE.equals(type.getType()))
							ret = toSet = Character.valueOf((char) (op1.shortValue() >> op2
								.shortValue()));
						else
							throw new EvaluationException("The operator " + theName
								+ " is not defined for types " + type.typeString() + ", "
								+ opType.typeString(), this, getStored("name").index);
					}
					else
					{
						if(Long.TYPE.equals(type.getType()))
							ret = toSet = Long.valueOf(op1.longValue() >>> op2.longValue());
						else if(Integer.TYPE.equals(type.getType()))
							ret = toSet = Integer.valueOf(op1.intValue() >>> op2.intValue());
						else if(Short.TYPE.equals(type.getType()))
							ret = toSet = Short.valueOf((short) (op1.shortValue() >>> op2
								.shortValue()));
						else if(Byte.TYPE.equals(type.getType()))
							ret = toSet = Byte
								.valueOf((byte) (op1.byteValue() >>> op2.byteValue()));
						else if(Character.TYPE.equals(type.getType()))
							ret = toSet = Character.valueOf((char) (op1.shortValue() >>> op2
								.shortValue()));
						else
							throw new EvaluationException("The operator " + theName
								+ " is not defined for types " + type.typeString() + ", "
								+ opType.typeString(), this, getStored("name").index);
					}
				}
				else
					ret = toSet = null;
			}
			else
				throw new EvaluationException("The operator " + theName
					+ " is not defined for type " + type.typeString(), this,
					getStored("name").index);
		}
		else
			throw new EvaluationException("Assignment operator " + theName + " not recognized",
				this, getStored("name").index);

		if(!withValues)
			return type;
		if(var instanceof ParsedIdentifier)
			env.setVariable(((ParsedIdentifier) var).getName(), toSet, op, getStored("name").index);
		else if(var instanceof ParsedArrayIndex)
		{
			int index = ((Number) evaluate(((ParsedArrayIndex) var).getIndex(), env, false, true)
				.getValue()).intValue();
			java.lang.reflect.Array.set(arrType.getValue(), index, toSet);
		}
		else if(var instanceof ParsedDeclaration)
		{
			ParsedDeclaration dec = (ParsedDeclaration) var;
			env.declareVariable(dec.getName(), type.getType(), dec.isFinal(), dec,
				dec.getStored("name").index);
			env.setVariable(dec.getName(), toSet, op, getStored("name").index);
		}
		else
			try
			{
				field.set(ctxType.getValue(), toSet);
			} catch(Exception e)
			{
				throw new EvaluationException("Assignment to field " + field.getName()
					+ " of class " + field.getDeclaringClass().getName() + " failed", e, op,
					getStored("name").index);
			}
		return new EvaluationResult<Object>(type.getType(), ret);
	}
}
