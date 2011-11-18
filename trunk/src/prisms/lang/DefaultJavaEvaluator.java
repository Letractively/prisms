/*
 * DefaultJavaEvaluator.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;

import prisms.lang.types.*;

/** Evaluates expressions as java */
public class DefaultJavaEvaluator implements PrismsEvaluator
{
	private boolean isOnlyPublic;

	/** Creates an evaluator */
	public DefaultJavaEvaluator()
	{
		isOnlyPublic = true;
	}

	public EvalResult evaluate(ParsedItem struct, EvaluationEnvironment env, boolean asType,
		final boolean withValues) throws EvaluationException
	{
		if(struct instanceof ParsedBoolean)
			return new EvalResult(Boolean.TYPE,
				Boolean.valueOf(((ParsedBoolean) struct).getValue()));
		else if(struct instanceof ParsedChar)
			return new EvalResult(Character.TYPE, Character.valueOf(((ParsedChar) struct)
				.getValue()));
		else if(struct instanceof ParsedNumber)
		{
			Number value = ((prisms.lang.types.ParsedNumber) struct).getValue();
			if(value instanceof Double)
				return new EvalResult(Double.TYPE, value);
			else if(value instanceof Float)
				return new EvalResult(Float.TYPE, value);
			else if(value instanceof Long)
				return new EvalResult(Long.TYPE, value);
			else if(value instanceof Integer)
				return new EvalResult(Integer.TYPE, value);
			else
				throw new EvaluationException("Unrecognized number type: "
					+ value.getClass().getName(), struct, struct.getMatch().index);
		}
		else if(struct instanceof ParsedString)
			return new EvalResult(String.class, ((ParsedString) struct).getValue());
		else if(struct instanceof ParsedIdentifier)
		{
			ParsedIdentifier id = (ParsedIdentifier) struct;
			String name = id.getName();
			if(!asType)
			{
				Class<?> type = env.getVariableType(name);
				if(type != null)
					return new EvalResult(type, withValues ? env.getVariable(name, struct,
						id.getStored("name").index) : null);
			}
			if("boolean".equals(name))
				return new EvalResult(Boolean.TYPE);
			else if("char".equals(name))
				return new EvalResult(Character.TYPE);
			else if("double".equals(name))
				return new EvalResult(Double.TYPE);
			else if("float".equals(name))
				return new EvalResult(Float.TYPE);
			else if("long".equals(name))
				return new EvalResult(Long.TYPE);
			else if("int".equals(name))
				return new EvalResult(Integer.TYPE);
			else if("short".equals(name))
				return new EvalResult(Short.TYPE);
			else if("byte".equals(name))
				return new EvalResult(Byte.TYPE);
			Class<?> clazz;
			try
			{
				clazz = Class.forName(name);
			} catch(ClassNotFoundException e)
			{
				clazz = null;
			}
			if(clazz != null)
				return new EvalResult(clazz);
			clazz = env.getImportType(name);
			if(clazz != null)
				return new EvalResult(clazz);
			try
			{
				clazz = Class.forName("java.lang." + name);
			} catch(ClassNotFoundException e)
			{
				clazz = null;
			}
			if(clazz != null)
				return new EvalResult(clazz);
			Package [] pkgs = Package.getPackages();
			for(Package pkg : pkgs)
				if(pkg.getName().equals(name) || pkg.getName().startsWith(name + "."))
					return new EvalResult(id.getName());
			Class<?> importType = env.getImportMethodType(name);
			if(importType != null)
			{
				for(java.lang.reflect.Field f : importType.getDeclaredFields())
				{
					if(!name.equals(f.getName()))
						continue;
					if((f.getModifiers() & Modifier.STATIC) == 0)
						continue;
					if(isOnlyPublic && (f.getModifiers() & Modifier.PUBLIC) == 0)
						continue;
					try
					{
						return new EvalResult(f.getType(), withValues ? f.get(null) : null);
					} catch(Exception e)
					{
						throw new EvaluationException("Could not access field " + name
							+ " on type " + EvalResult.typeString(importType), e, struct,
							id.getStored("name").index);
					}
				}
			}
			throw new EvaluationException(name + " cannot be resolved to a "
				+ (asType ? "type" : "variable"), struct, id.getMatch().index);
		}
		else if(struct instanceof ParsedParenthetic)
			return evaluate(((ParsedParenthetic) struct).getContent(), env, asType, withValues);
		else if(struct instanceof ParsedPreviousAnswer)
		{
			if(env.getHistoryCount() == 0)
				throw new EvaluationException("No previous results available", struct,
					struct.getMatch().index);
			else if(env.getHistoryCount() <= ((ParsedPreviousAnswer) struct).getIndex())
				throw new EvaluationException("Only " + env.getHistoryCount()
					+ " previous result(s) are available", struct, struct.getMatch().index);
			else
			{
				int index = ((ParsedPreviousAnswer) struct).getIndex();
				return new EvalResult(env.getHistoryType(index), withValues ? env.getHistory(index)
					: null);
			}
		}
		else if(struct instanceof ParsedArrayIndex)
		{
			ParsedArrayIndex idx = (ParsedArrayIndex) struct;
			EvalResult array = evaluate(idx.getArray(), env, false, withValues);
			if(array.isType() || array.getPackageName() != null)
				throw new EvaluationException(array.typeString()
					+ " cannot be resolved to a variable", struct, idx.getArray().getMatch().index);
			if(!array.getType().isArray())
				throw new EvaluationException(
					"The type of the expression must resolve to an array but it resolved to "
						+ array.typeString(), struct, idx.getArray().getMatch().index);
			EvalResult index = evaluate(idx.getIndex(), env, false, withValues);
			if(index.isType() || index.getPackageName() != null)
				throw new EvaluationException(index.typeString()
					+ " cannot be resolved to a variable", struct, idx.getIndex().getMatch().index);
			if(!index.isIntType() || Long.TYPE.equals(index.getType()))
				throw new EvaluationException("Type mismatch: cannot convert from "
					+ index.typeString() + " to int", struct, idx.getIndex().getMatch().index);
			return new EvalResult(array.getType().getComponentType(), withValues ? Array.get(
				array.getValue(), ((Number) index.getValue()).intValue()) : null);
		}
		else if(struct instanceof ParsedConditional)
		{
			ParsedConditional cond = (ParsedConditional) struct;
			EvalResult condition = evaluate(cond.getCondition(), env, false, withValues);
			if(condition.isType() || condition.getPackageName() != null)
				throw new EvaluationException(condition.typeString()
					+ " cannot be resolved to a variable", struct,
					cond.getCondition().getMatch().index);
			if(!Boolean.TYPE.equals(condition.getType()))
				throw new EvaluationException("Type mismatch: cannot convert from "
					+ condition.typeString() + " to boolean", struct, cond.getCondition()
					.getMatch().index);
			boolean condEval = withValues && ((Boolean) condition.getValue()).booleanValue();
			EvalResult affirm = evaluate(cond.getAffirmative(), env, false, withValues && condEval);
			if(affirm.isType() || affirm.getPackageName() != null)
				throw new EvaluationException(affirm.typeString()
					+ " cannot be resolved to a variable", struct,
					cond.getAffirmative().getMatch().index);
			EvalResult negate = evaluate(cond.getNegative(), env, false, withValues && !condEval);
			if(negate.isType() || negate.getPackageName() != null)
				throw new EvaluationException(negate.typeString()
					+ " cannot be resolved to a variable", struct,
					cond.getNegative().getMatch().index);
			Class<?> max = getMaxType(affirm.getType(), negate.getType());
			if(max == null)
				throw new EvaluationException("Incompatible types in conditional expression: "
					+ affirm.typeString() + " and " + negate.typeString(), cond, cond
					.getAffirmative().getMatch().index);
			if(!withValues)
				return new EvalResult(max, null);
			else if(condEval)
				return new EvalResult(max, affirm.getValue());
			else
				return new EvalResult(max, negate.getValue());
		}
		else if(struct instanceof ParsedArrayInitializer)
		{
			ParsedArrayInitializer ai = (ParsedArrayInitializer) struct;
			EvalResult typeEval = evaluate(ai.getType(), env, true, false);
			if(!typeEval.isType())
				throw new EvaluationException("Unrecognized type " + ai.getType().getMatch().text,
					ai, ai.getType().getMatch().index);
			Class<?> type = typeEval.getType();
			for(int i = ai.getDimension() - 1; i >= 0; i--)
				type = Array.newInstance(type, 0).getClass();
			if(!withValues)
				return new EvalResult(type, null);
			Object ret = null;
			if(ai.getSizes().length > 0)
			{
				final int [] sizes = new int [ai.getSizes().length];
				for(int i = 0; i < sizes.length; i++)
				{
					EvalResult sizeEval = evaluate(ai.getSizes()[i], env, false, withValues);
					if(sizeEval.isType() || sizeEval.getPackageName() != null)
						throw new EvaluationException(sizeEval.typeString()
							+ " cannot be resolved to a variable", struct,
							ai.getSizes()[i].getMatch().index);
					if(!sizeEval.isIntType() || Long.TYPE.equals(sizeEval.getType()))
						throw new EvaluationException("Type mismatch: " + sizeEval.typeString()
							+ " cannot be cast to int", struct, ai.getSizes()[i].getMatch().index);
					sizes[i] = ((Number) sizeEval.getValue()).intValue();
				}
				type = type.getComponentType();
				ret = Array.newInstance(type, sizes[0]);
				class ArrayFiller
				{
					public void fillArray(Object array, Class<?> componentType, int dimIdx)
					{
						if(dimIdx == sizes.length)
							return;
						int len = Array.getLength(array);
						for(int i = 0; i < len; i++)
						{
							Object element = Array.newInstance(componentType, sizes[dimIdx]);
							Array.set(array, i, element);
							fillArray(element, componentType.getComponentType(), dimIdx);
						}
					}
				}
				new ArrayFiller().fillArray(ret, type.getComponentType(), 1);
			}
			else
			{
				type = type.getComponentType();
				Object [] elements = new Object [ai.getElements().length];
				for(int i = 0; i < elements.length; i++)
				{
					EvalResult elEval = evaluate(ai.getElements()[i], env, false, withValues);
					if(elEval.isType() || elEval.getPackageName() != null)
						throw new EvaluationException(elEval.typeString()
							+ " cannot be resolved to a variable", struct,
							ai.getSizes()[i].getMatch().index);
					if(!canAssign(type, elEval.getType()))
						throw new EvaluationException("Type mismatch: cannot convert from "
							+ elEval.typeString() + " to " + EvalResult.typeString(type), struct,
							ai.getElements()[i].getMatch().index);
					elements[i] = elEval.getValue();
				}
				ret = Array.newInstance(type, elements.length);
				for(int i = 0; i < elements.length; i++)
					Array.set(ret, i, elements[i]);
			}

			return new EvalResult(ret.getClass(), ret);
		}
		else if(struct instanceof ParsedUnaryOp)
		{
			ParsedUnaryOp op = (ParsedUnaryOp) struct;
			EvalResult res = evaluate(op.getOp(), env, false, withValues);
			if(res.isType())
				throw new EvaluationException("The operator " + op.getName()
					+ " is not defined for type java.lang.Class", struct,
					op.getStored("name").index);
			if(res.getType() == null)
				throw new EvaluationException(res.getFirstVar()
					+ " cannot be resolved to a variable", struct, op.getOp().getMatch().index);
			if("+".equals(op.getName()))
			{
				if(!res.getType().isPrimitive() || Boolean.TYPE.equals(res.getType()))
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for type " + res.typeString(), struct,
						op.getStored("name").index);
				return res;
			}
			else if("-".equals(op.getName()))
			{
				if(Double.TYPE.equals(res.getType()))
					return new EvalResult(Double.TYPE, withValues ? Double.valueOf(-((Number) res
						.getValue()).doubleValue()) : null);
				else if(Float.TYPE.equals(res.getType()))
					return new EvalResult(Float.TYPE, withValues ? Float.valueOf(-((Number) res
						.getValue()).floatValue()) : null);
				else if(Long.TYPE.equals(res.getType()))
					return new EvalResult(Long.TYPE, withValues ? Long.valueOf(-((Number) res
						.getValue()).longValue()) : null);
				else if(Integer.TYPE.equals(res.getType()))
					return new EvalResult(Integer.TYPE, withValues ? Integer.valueOf(-((Number) res
						.getValue()).intValue()) : null);
				else if(Character.TYPE.equals(res.getType()))
					return new EvalResult(Integer.TYPE, withValues
						? Integer.valueOf(-((Character) res.getValue()).charValue()) : null);
				else if(Short.TYPE.equals(res.getType()))
					return new EvalResult(Integer.TYPE, withValues ? Integer.valueOf(-((Number) res
						.getValue()).shortValue()) : null);
				else if(Byte.TYPE.equals(res.getType()))
					return new EvalResult(Integer.TYPE, withValues ? Integer.valueOf(-((Number) res
						.getValue()).byteValue()) : null);
				else
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for type " + res.typeString(), struct,
						op.getStored("name").index);
			}
			else if("!".equals(op.getName()))
			{
				if(Boolean.TYPE.equals(res.getType()))
					return new EvalResult(Boolean.TYPE, withValues
						? Boolean.valueOf(!((Boolean) res.getValue()).booleanValue()) : null);
				else
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for type " + res.typeString(), struct,
						op.getStored("name").index);
			}
			else if("~".equals(op.getName()))
			{
				if(Long.TYPE.equals(res.getType()))
					return new EvalResult(Long.TYPE, withValues ? Long.valueOf(~((Long) res
						.getValue()).longValue()) : null);
				else if(Integer.TYPE.equals(res.getType()))
					return new EvalResult(Integer.TYPE, withValues
						? Integer.valueOf(~((Integer) res.getValue()).intValue()) : null);
				else if(Short.TYPE.equals(res.getType()))
					return new EvalResult(Integer.TYPE, withValues ? Integer.valueOf(~((Short) res
						.getValue()).shortValue()) : null);
				else if(Byte.TYPE.equals(res.getType()))
					return new EvalResult(Integer.TYPE, withValues ? Integer.valueOf(~((Byte) res
						.getValue()).byteValue()) : null);
				else
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for type " + res.typeString(), struct,
						op.getStored("name").index);
			}
			else
				throw new EvaluationException("Unary operator " + op.getName() + " not recognized",
					struct, op.getStored("name").index);
		}
		else if(struct instanceof ParsedBinaryOp)
		{
			ParsedBinaryOp op = (ParsedBinaryOp) struct;
			EvalResult res1 = evaluate(op.getOp1(), env, false, withValues);
			EvalResult res2 = evaluate(op.getOp2(), env, false, withValues);
			if(res1.isType() || res1.getPackageName() != null)
				throw new EvaluationException(res1.getFirstVar()
					+ " cannot be resolved to a variable", struct, op.getOp1().getMatch().index);
			if(res2.isType() || res2.getPackageName() != null)
				throw new EvaluationException(res2.getFirstVar()
					+ " cannot be resolved to a variable", struct, op.getOp2().getMatch().index);

			if("+".equals(op.getName()) || "-".equals(op.getName()) || "*".equals(op.getName())
				|| "/".equals(op.getName()) || "%".equals(op.getName()))
			{
				if("+".equals(op.getName())
					&& (String.class.equals(res1.getType()) || String.class.equals(res2.getType())))
				{
					StringBuilder value = null;
					if(withValues)
						value = new StringBuilder().append(res1.getValue()).append(res2.getValue());
					return new EvalResult(String.class, withValues ? value.toString() : null);
				}
				if(!res1.getType().isPrimitive() || Boolean.TYPE.equals(res1.getType())
					|| !res2.getType().isPrimitive() || Boolean.TYPE.equals(res2.getType()))
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for types " + res1.typeString() + ", "
						+ res2.typeString(), struct, op.getStored("name").index);
				Class<?> max = getMaxType(res1.getType(), res2.getType());
				Number op1, op2;
				if(res1.getValue() instanceof Character)
					op1 = withValues ? Integer.valueOf(((Character) res1.getValue()).charValue())
						: null;
				else
					op1 = (Number) res1.getValue();
				if(res2.getValue() instanceof Character)
					op2 = Integer.valueOf(((Character) res2.getValue()).charValue());
				else
					op2 = (Number) res2.getValue();
				Object res;
				if(withValues)
				{
					if(max.equals(Double.TYPE))
						res = Double.valueOf(mathF(op.getName(), op1, op2));
					else if(max.equals(Float.TYPE))
						res = Float.valueOf((float) mathF(op.getName(), op1, op2));
					else if(max.equals(Long.TYPE))
						res = Long.valueOf(mathI(max, op.getName(), op1, op2));
					else if(max.equals(Integer.TYPE))
						res = Integer.valueOf((int) mathI(max, op.getName(), op1, op2));
					else if(max.equals(Character.TYPE))
						res = Integer.valueOf((int) mathI(max, op.getName(), op1, op2));
					else if(max.equals(Short.TYPE))
						res = Short.valueOf((short) mathI(max, op.getName(), op1, op2));
					else if(max.equals(Byte.TYPE))
						res = Byte.valueOf((byte) mathI(max, op.getName(), op1, op2));
					else
						throw new EvaluationException("The operator " + op.getName()
							+ " is not defined for type " + EvalResult.typeString(max), struct,
							op.getStored("name").index);
				}
				else
					res = null;
				return new EvalResult(max, res);
			}
			else if("<".equals(op.getName()) || "<=".equals(op.getName())
				|| ">".equals(op.getName()) || ">=".equals(op.getName()))
			{
				if(!res1.getType().isPrimitive() || Boolean.TYPE.equals(res1.getType())
					|| !res2.getType().isPrimitive() || Boolean.TYPE.equals(res2.getType()))
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for types " + res1.typeString() + ", "
						+ res2.typeString(), struct, op.getStored("name").index);
				Class<?> max = getMaxType(res1.getType(), res2.getType());
				Number op1, op2;
				if(res1.getValue() instanceof Character)
					op1 = withValues ? Integer.valueOf(((Character) res1.getValue()).charValue())
						: null;
				else
					op1 = (Number) res1.getValue();
				if(res2.getValue() instanceof Character)
					op2 = Integer.valueOf(((Character) res2.getValue()).charValue());
				else
					op2 = (Number) res2.getValue();
				if(!withValues)
					return new EvalResult(Boolean.TYPE, null);
				else if(max.equals(Double.TYPE) || max.equals(Float.TYPE))
					return new EvalResult(Boolean.TYPE, Boolean.valueOf(compareF(op.getName(), op1,
						op2)));
				else
					return new EvalResult(Boolean.TYPE, Boolean.valueOf(compareI(op.getName(), op1,
						op2)));
			}
			else if("==".equals(op.getName()) || "!=".equals(op.getName()))
			{
				boolean ret;
				if(Boolean.TYPE.equals(res1.getType()) && Boolean.TYPE.equals(res2.getType()))
					ret = withValues
						? ((Boolean) res1.getValue()).booleanValue() == ((Boolean) res2.getValue())
							.booleanValue() : false;
				else if(Boolean.TYPE.equals(res1.getType()) || Boolean.TYPE.equals(res2.getType()))
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for types " + res1.typeString() + ", "
						+ res2.typeString(), struct, op.getStored("name").index);
				else if(Character.TYPE.equals(res1.getType())
					&& Character.TYPE.equals(res2.getType()))
					ret = withValues
						? ((Character) res1.getValue()).charValue() == ((Character) res2.getValue())
							.charValue() : false;
				else if(Character.TYPE.equals(res1.getType()) && res2.getType().isPrimitive())
					ret = withValues ? ((Character) res1.getValue()).charValue() == ((Number) res2
						.getValue()).doubleValue() : false;
				else if(Character.TYPE.equals(res2.getType()) && res1.getType().isPrimitive())
					ret = withValues ? ((Character) res2.getValue()).charValue() == ((Number) res1
						.getValue()).doubleValue() : false;
				else if(res1.getType().isPrimitive() && res2.getType().isPrimitive())
					ret = withValues ? ((Number) res1.getValue()).doubleValue() == ((Number) res2
						.getValue()).doubleValue() : false;
				else if(res1.getType().isPrimitive() || res2.getType().isPrimitive())
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for types " + res1.typeString() + ", "
						+ res2.typeString(), struct, op.getStored("name").index);
				else
					ret = withValues ? res1.getValue() == res2.getValue() : false;
				if("!=".equals(op.getName()))
					ret = !ret;
				return new EvalResult(Boolean.TYPE, withValues ? null : Boolean.valueOf(ret));
			}
			else if("||".equals(op.getName()))
			{
				if(Boolean.TYPE.equals(res1.getType()) && Boolean.TYPE.equals(res2.getType()))
					return new EvalResult(Boolean.TYPE, withValues
						? Boolean.valueOf(((Boolean) res1.getValue()).booleanValue()
							|| ((Boolean) res2.getValue()).booleanValue()) : null);
				throw new EvaluationException("The operator " + op.getName()
					+ " is not defined for types " + res1.typeString() + ", " + res2.typeString(),
					struct, op.getStored("name").index);
			}
			else if("&&".equals(op.getName()))
			{
				if(Boolean.TYPE.equals(res1.getType()) && Boolean.TYPE.equals(res2.getType()))
					return new EvalResult(Boolean.TYPE, withValues
						? Boolean.valueOf(((Boolean) res1.getValue()).booleanValue()
							&& ((Boolean) res2.getValue()).booleanValue()) : null);
				throw new EvaluationException("The operator " + op.getName()
					+ " is not defined for types " + res1.typeString() + ", " + res2.typeString(),
					struct, op.getStored("name").index);
			}
			else if("|".equals(op.getName()) || "&".equals(op.getName())
				|| "^".equals(op.getName()))
			{
				if(Boolean.TYPE.equals(res1.getType()) && Boolean.TYPE.equals(res2.getType()))
				{
					if(!withValues)
						return new EvalResult(Boolean.TYPE, null);
					else if("|".equals(op.getName()))
						return new EvalResult(Boolean.TYPE, Boolean.valueOf(((Boolean) res1
							.getValue()).booleanValue()
							| ((Boolean) res2.getValue()).booleanValue()));
					else if("&".equals(op.getName()))
						return new EvalResult(Boolean.TYPE, Boolean.valueOf(((Boolean) res1
							.getValue()).booleanValue()
							& ((Boolean) res2.getValue()).booleanValue()));
					else
						return new EvalResult(Boolean.TYPE, Boolean.valueOf(((Boolean) res1
							.getValue()).booleanValue()
							^ ((Boolean) res2.getValue()).booleanValue()));
				}
				else if(res1.isIntType() && res2.isIntType())
				{
					Class<?> max = getMaxType(res1.getType(), res2.getType());
					if(!withValues)
						return new EvalResult(max, null);
					long val;
					if("|".equals(op.getName()))
						val = ((Number) res1.getValue()).longValue()
							| ((Number) res2.getValue()).longValue();
					else if("&".equals(op.getName()))
						val = ((Number) res1.getValue()).longValue()
							& ((Number) res2.getValue()).longValue();
					else
						val = ((Number) res1.getValue()).longValue()
							^ ((Number) res2.getValue()).longValue();
					if(Long.TYPE.equals(max))
						return new EvalResult(max, Long.valueOf(val));
					else if(Integer.TYPE.equals(max))
						return new EvalResult(max, Integer.valueOf((int) val));
					else if(Short.TYPE.equals(max))
						return new EvalResult(max, Short.valueOf((short) val));
					else if(Byte.TYPE.equals(max))
						return new EvalResult(max, Byte.valueOf((byte) val));
					else
						throw new EvaluationException("The operator " + op.getName()
							+ " is not defined for types " + res1.typeString() + ", "
							+ res2.typeString(), struct, op.getStored("name").index);
				}
				else
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for types " + res1.typeString() + ", "
						+ res2.typeString(), struct, op.getStored("name").index);
			}
			else if("<<".equals(op.getName()) || ">>".equals(op.getName())
				|| ">>>".equals(op.getName()))
			{
				Class<?> max = getMaxType(res1.getType(), res2.getType());
				if(!withValues)
					return new EvalResult(max, null);
				long val;
				if("<<".equals(op.getName()))
					val = ((Number) res1.getValue()).longValue() << ((Number) res2.getValue())
						.longValue();
				else if(">>".equals(op.getName()))
					val = ((Number) res1.getValue()).longValue() >> ((Number) res2.getValue())
						.longValue();
				else
					val = ((Number) res1.getValue()).longValue() >>> ((Number) res2.getValue())
						.longValue();
				if(Long.TYPE.equals(max))
					return new EvalResult(max, Long.valueOf(val));
				else if(Integer.TYPE.equals(max))
					return new EvalResult(max, Integer.valueOf((int) val));
				else if(Short.TYPE.equals(max))
					return new EvalResult(max, Short.valueOf((short) val));
				else if(Byte.TYPE.equals(max))
					return new EvalResult(max, Byte.valueOf((byte) val));
				else
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for types " + res1.typeString() + ", "
						+ res2.typeString(), struct, op.getStored("name").index);
			}
			else
				throw new EvaluationException(
					"Binary operator " + op.getName() + " not recognized", struct,
					op.getStored("name").index);
		}
		else if(struct instanceof ParsedAssignmentOperator)
		{
			ParsedAssignmentOperator op = (ParsedAssignmentOperator) struct;
			EvalResult type = evaluate(op.getVariable(), env, false,
				withValues && !"=".equals(op.getName()));
			EvalResult opType = op.getOperand() == null ? null : evaluate(op.getOperand(), env,
				false, withValues);
			if(opType != null)
			{
				if(opType.getPackageName() != null)
					throw new EvaluationException(opType.getFirstVar()
						+ " cannot be resolved to a variable", op,
						op.getVariable().getMatch().index);
				if(opType.isType())
					throw new EvaluationException(opType.getType().getName()
						+ " cannot be resolved to a variable", op,
						op.getVariable().getMatch().index);
			}

			EvalResult ctxType = null;
			java.lang.reflect.Field field = null;
			EvalResult arrType = null;
			ParsedItem var = op.getVariable();
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
					type = new EvalResult(c);
				}
				if(!"=".equals(op.getName()))
					throw new EvaluationException("Syntax error: " + dec.getName()
						+ " has not been assigned", struct, op.getStored("name").index);
			}
			else if(var instanceof ParsedMethod)
			{
				ParsedMethod method = (ParsedMethod) var;
				if(method.isMethod())
					throw new EvaluationException("Invalid argument for operator " + op.getName(),
						struct, op.getVariable().getMatch().index);
				if(method.getContext() == null)
					ctxType = new EvalResult(env.getImportMethodType(method.getName()));
				else
					ctxType = evaluate(method.getContext(), env, false, withValues);
				boolean isStatic = ctxType.isType();
				if(method.getName().equals("length") && ctxType.getType().isArray())
					throw new EvaluationException(
						"The final field array.length cannot be assigned", struct,
						method.getStored("name").index);
				try
				{
					field = ctxType.getType().getField(method.getName());
				} catch(Exception e)
				{
					throw new EvaluationException("Could not access field " + method.getName()
						+ " on type " + ctxType.typeString(), e, struct,
						struct.getStored("name").index);
				}
				if(field == null)
					throw new EvaluationException(ctxType.typeString() + "." + method.getName()
						+ " cannot be resolved or is not a field", struct,
						struct.getStored("name").index);
				if(isOnlyPublic && (field.getModifiers() & Modifier.PUBLIC) == 0)
					throw new EvaluationException(ctxType.typeString() + "." + method.getName()
						+ " is not visible", struct, struct.getStored("name").index);
				if(isStatic && (field.getModifiers() & Modifier.STATIC) == 0)
					throw new EvaluationException(
						"Cannot make a static reference to non-static field " + method.getName()
							+ " from the type " + ctxType.typeString() + "." + method.getName()
							+ " is not static", struct, struct.getStored("name").index);
				if((field.getModifiers() & Modifier.FINAL) != 0)
					throw new EvaluationException("The final field " + ctxType.typeString() + "."
						+ method.getName() + " cannot be assigned", struct,
						method.getStored("name").index);
			}
			else
				throw new EvaluationException("Invalid argument for assignment operator "
					+ op.getName(), struct, op.getVariable().getMatch().index);

			Object ret;
			Object toSet;
			if("=".equals(op.getName()))
			{
				if(!canAssign(type.getType(), opType.getType()))
					throw new EvaluationException("Type mismatch: Cannot convert from "
						+ opType.getType().getName() + " to " + type.typeString(), op, op
						.getOperand().getMatch().index);
				ret = toSet = withValues ? opType.getValue() : null;
			}
			else if("++".equals(op.getName()) || "--".equals(op.getName()))
			{
				ret = type.getValue();
				int adjust = 1;
				if("--".equals(op.getName()))
					adjust = -adjust;
				boolean pre = op.isPrefix();
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
						throw new EvaluationException("The operator " + op.getName()
							+ " is not defined for type " + type.typeString(), struct,
							op.getStored("name").index);
				}
				else
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for type " + type.typeString(), struct,
						op.getStored("name").index);
				if(pre)
					ret = toSet;
			}
			else if("+=".equals(op.getName()) || "-=".equals(op.getName())
				|| "*=".equals(op.getName()) || "/=".equals(op.getName())
				|| "%".equals(op.getName()))
			{
				if(type.getType().isPrimitive() && !Boolean.TYPE.equals(type.getType())
					&& opType.getType().isPrimitive() && !Boolean.TYPE.equals(opType.getType()))
				{
					ret = type.getValue();
					Number op1, op2;
					if(type.getValue() instanceof Character)
						op1 = withValues ? Integer.valueOf(((Character) type.getValue())
							.charValue()) : null;
					else
						op1 = (Number) type.getValue();
					if(opType.getValue() instanceof Character)
						op2 = Integer.valueOf(((Character) opType.getValue()).charValue());
					else
						op2 = (Number) opType.getValue();
					if(withValues)
					{
						if(type.getType().equals(Double.TYPE))
							ret = Double.valueOf(mathF(op.getName(), op1, op2));
						else if(type.getType().equals(Float.TYPE))
							ret = Float.valueOf((float) mathF(op.getName(), op1, op2));
						else if(type.getType().equals(Long.TYPE))
							ret = Long.valueOf(mathI(type.getType(), op.getName(), op1, op2));
						else if(type.getType().equals(Integer.TYPE))
							ret = Integer.valueOf((int) mathI(type.getType(), op.getName(), op1,
								op2));
						else if(type.getType().equals(Character.TYPE))
							ret = Integer.valueOf((int) mathI(type.getType(), op.getName(), op1,
								op2));
						else if(type.getType().equals(Short.TYPE))
							ret = Short.valueOf((short) mathI(type.getType(), op.getName(), op1,
								op2));
						else if(type.getType().equals(Byte.TYPE))
							ret = Byte
								.valueOf((byte) mathI(type.getType(), op.getName(), op1, op2));
						else
							throw new EvaluationException("The operator " + op.getName()
								+ " is not defined for type " + type.typeString(), struct,
								op.getStored("name").index);
					}
					else
						ret = null;
					toSet = ret;
				}
				else
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for type " + type.typeString(), struct,
						op.getStored("name").index);
			}
			else if("|=".equals(op.getName()) || "&=".equals(op.getName())
				|| "^=".equals(op.getName()))
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
						if("|=".equals(op.getName()))
							val = op1.longValue() | op2.longValue();
						else if("&=".equals(op.getName()))
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
							throw new EvaluationException("The operator " + op.getName()
								+ " is not defined for types " + type.typeString() + ", "
								+ opType.typeString(), struct, op.getStored("name").index);
					}
					else
						ret = toSet = null;
				}
				else if(Boolean.TYPE.equals(type.getType())
					&& Boolean.TYPE.equals(opType.getType()))
				{
					boolean val;
					if("|=".equals(op.getName()))
						val = ((Boolean) type.getValue()).booleanValue()
							| ((Boolean) opType.getValue()).booleanValue();
					else if("&=".equals(op.getName()))
						val = ((Boolean) type.getValue()).booleanValue()
							& ((Boolean) opType.getValue()).booleanValue();
					else
						val = ((Boolean) type.getValue()).booleanValue()
							^ ((Boolean) opType.getValue()).booleanValue();
					ret = toSet = Boolean.valueOf(val);
				}
				else
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for type " + type.typeString(), struct,
						op.getStored("name").index);
			}
			else if("<<=".equals(op.getName()) || ">>=".equals(op.getName())
				|| ">>>=".equals(op.getName()))
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
						if("<<=".equals(op.getName()))
						{
							if(Long.TYPE.equals(type.getType()))
								ret = toSet = Long.valueOf(op1.longValue() << op2.longValue());
							else if(Integer.TYPE.equals(type.getType()))
								ret = toSet = Integer.valueOf(op1.intValue() << op2.intValue());
							else if(Short.TYPE.equals(type.getType()))
								ret = toSet = Short.valueOf((short) (op1.shortValue() << op2
									.shortValue()));
							else if(Byte.TYPE.equals(type.getType()))
								ret = toSet = Byte.valueOf((byte) (op1.byteValue() << op2
									.byteValue()));
							else if(Character.TYPE.equals(type.getType()))
								ret = toSet = Character.valueOf((char) (op1.shortValue() << op2
									.shortValue()));
							else
								throw new EvaluationException("The operator " + op.getName()
									+ " is not defined for types " + type.typeString() + ", "
									+ opType.typeString(), struct, op.getStored("name").index);
						}
						else if(">>=".equals(op.getName()))
						{
							if(Long.TYPE.equals(type.getType()))
								ret = toSet = Long.valueOf(op1.longValue() >> op2.longValue());
							else if(Integer.TYPE.equals(type.getType()))
								ret = toSet = Integer.valueOf(op1.intValue() >> op2.intValue());
							else if(Short.TYPE.equals(type.getType()))
								ret = toSet = Short.valueOf((short) (op1.shortValue() >> op2
									.shortValue()));
							else if(Byte.TYPE.equals(type.getType()))
								ret = toSet = Byte.valueOf((byte) (op1.byteValue() >> op2
									.byteValue()));
							else if(Character.TYPE.equals(type.getType()))
								ret = toSet = Character.valueOf((char) (op1.shortValue() >> op2
									.shortValue()));
							else
								throw new EvaluationException("The operator " + op.getName()
									+ " is not defined for types " + type.typeString() + ", "
									+ opType.typeString(), struct, op.getStored("name").index);
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
								ret = toSet = Byte.valueOf((byte) (op1.byteValue() >>> op2
									.byteValue()));
							else if(Character.TYPE.equals(type.getType()))
								ret = toSet = Character.valueOf((char) (op1.shortValue() >>> op2
									.shortValue()));
							else
								throw new EvaluationException("The operator " + op.getName()
									+ " is not defined for types " + type.typeString() + ", "
									+ opType.typeString(), struct, op.getStored("name").index);
						}
					}
					else
						ret = toSet = null;
				}
				else
					throw new EvaluationException("The operator " + op.getName()
						+ " is not defined for type " + type.typeString(), struct,
						op.getStored("name").index);
			}
			else
				throw new EvaluationException("Assignment operator " + op.getName()
					+ " not recognized", struct, op.getStored("name").index);

			if(!withValues)
				return type;
			if(var instanceof ParsedIdentifier)
				env.setVariable(((ParsedIdentifier) var).getName(), toSet, op,
					op.getStored("name").index);
			else if(var instanceof ParsedArrayIndex)
			{
				int index = ((Number) evaluate(((ParsedArrayIndex) var).getIndex(), env, false,
					true).getValue()).intValue();
				Array.set(arrType.getValue(), index, toSet);
			}
			else if(var instanceof ParsedDeclaration)
			{
				ParsedDeclaration dec = (ParsedDeclaration) var;
				env.declareVariable(dec.getName(), type.getType(), dec.isFinal(), dec,
					dec.getStored("name").index);
				env.setVariable(dec.getName(), toSet, op, op.getStored("name").index);
			}
			else
				try
				{
					field.set(ctxType.getValue(), toSet);
				} catch(Exception e)
				{
					throw new EvaluationException("Assignment to field " + field.getName()
						+ " of class " + field.getDeclaringClass().getName() + " failed", e, op,
						op.getStored("name").index);
				}
			return new EvalResult(type.getType(), ret);
		}
		else if(struct instanceof ParsedMethod)
		{
			ParsedMethod method = (ParsedMethod) struct;
			EvalResult ctxType;
			if(method.getContext() == null)
			{
				Class<?> c = env.getImportMethodType(method.getName());
				if(c == null)
					throw new EvaluationException((method.isMethod() ? "Method " : "Field ")
						+ method.getName() + " unrecognized", method,
						method.getStored("name").index);
				ctxType = new EvalResult(c);
			}
			else
				ctxType = evaluate(method.getContext(), env, false, withValues);
			if(ctxType == null)
				throw new EvaluationException("No value for context to "
					+ (method.isMethod() ? "method " : "field ") + method.getName(), method, method
					.getContext().getMatch().index);
			boolean isStatic = ctxType.isType();
			if(!method.isMethod())
			{
				if(ctxType.getPackageName() != null)
				{
					// Could be a class name or a more specific package name
					String name = ctxType.getPackageName() + "." + method.getName();
					java.lang.Class<?> clazz;
					try
					{
						clazz = Class.forName(name);
					} catch(ClassNotFoundException e)
					{
						clazz = null;
					}
					if(clazz != null)
						return new EvalResult(clazz);
					Package [] pkgs = Package.getPackages();
					for(Package pkg : pkgs)
						if(pkg.getName().equals(name) || pkg.getName().startsWith(name + "."))
							return new EvalResult(name);
					throw new EvaluationException(ctxType.getFirstVar()
						+ " cannot be resolved to a variable", struct, method.getContext()
						.getMatch().index);
				}
				if(!ctxType.isType() && ctxType.getType().isPrimitive())
					throw new EvaluationException("The primitive type "
						+ ctxType.getType().getName() + " does not have a field "
						+ method.getName(), method, method.getContext().getMatch().index
						+ method.getContext().getMatch().text.length());
				if(method.getName().equals("length") && ctxType.getType().isArray())
					return new EvalResult(Integer.class, withValues ? Integer.valueOf(Array
						.getLength(ctxType.getValue())) : null);
				else if(method.getName().equals("class") && ctxType.isType())
					return new EvalResult(Class.class, ctxType.getType());
				java.lang.reflect.Field field;
				try
				{
					field = ctxType.getType().getField(method.getName());
				} catch(Exception e)
				{
					throw new EvaluationException("Could not access field " + method.getName()
						+ " on type " + ctxType.typeString(), e, struct,
						struct.getStored("name").index);
				}
				if(field == null)
					throw new EvaluationException(ctxType.typeString() + "." + method.getName()
						+ " cannot be resolved or is not a field", struct,
						struct.getStored("name").index);
				if(isOnlyPublic && (field.getModifiers() & Modifier.PUBLIC) == 0)
					throw new EvaluationException(ctxType.typeString() + "." + method.getName()
						+ " is not visible", struct, struct.getStored("name").index);
				if(isStatic && (field.getModifiers() & Modifier.STATIC) == 0)
					throw new EvaluationException(
						"Cannot make a static reference to non-static field " + method.getName()
							+ " from the type " + ctxType.typeString() + "." + method.getName()
							+ " is not static", struct, struct.getStored("name").index);
				try
				{
					return new EvalResult(field.getType(), withValues ? field.get(ctxType
						.getValue()) : null);
				} catch(Exception e)
				{
					throw new EvaluationException("Retrieval of field " + field.getName()
						+ " of type " + field.getDeclaringClass().getName() + " failed", e, method,
						method.getStored("name").index);
				}
			}
			else
			{
				if(ctxType.getPackageName() != null)
					throw new EvaluationException(ctxType.getFirstVar()
						+ " cannot be resolved to a variable", struct, method.getContext()
						.getMatch().index);
				EvalResult [] argTypes = new EvalResult [method.getArguments().length];
				for(int i = 0; i < argTypes.length; i++)
				{
					argTypes[i] = evaluate(method.getArguments()[i], env, false, withValues);
					if(argTypes[i].getPackageName() != null || argTypes[i].isType())
						throw new EvaluationException(argTypes[i].getFirstVar()
							+ " cannot be resolved to a variable", method,
							method.getArguments()[i].getMatch().index);
				}
				if(!ctxType.isType() && ctxType.getType().isPrimitive())
				{
					StringBuilder msg = new StringBuilder();
					msg.append(method.getName()).append('(');
					int p;
					for(p = 0; p < argTypes.length; p++)
					{
						if(p > 0)
							msg.append(", ");
						msg.append(argTypes[p].typeString());
					}
					msg.append(')');
					throw new EvaluationException("Cannot invoke " + msg + " on primitive type "
						+ ctxType.getType().getName(), method, method.getContext().getMatch().index
						+ method.getContext().getMatch().text.length());
				}
				if("getClass".equals(method.getName()))
				{
					if(isStatic)
						throw new EvaluationException(
							"Cannot access the non-static getClass() method from a static context",
							method, method.getStored("name").index);
					try
					{
						return new EvalResult(Class.class, withValues ? ctxType.getValue()
							.getClass() : null);
					} catch(NullPointerException e)
					{
						throw new EvaluationException("Argument to getClass() is null", e, method,
							method.getStored("dot").index);
					}
				}
				java.lang.reflect.Method[] methods = ctxType.getType().getMethods();
				if(!isOnlyPublic)
					methods = prisms.util.ArrayUtils.mergeInclusive(java.lang.reflect.Method.class,
						methods, ctxType.getType().getDeclaredMethods());
				java.lang.reflect.Method goodTarget = null;
				java.lang.reflect.Method badTarget = null;
				for(java.lang.reflect.Method m : methods)
				{
					if(!m.getName().equals(method.getName()))
						continue;
					Class<?> [] paramTypes = m.getParameterTypes();
					if(paramTypes.length > argTypes.length + 1)
						continue;
					boolean bad = false;
					int p;
					for(p = 0; !bad && p < paramTypes.length - 1; p++)
					{
						if(!canAssign(paramTypes[p], argTypes[p].getType()))
							bad = true;
					}
					if(bad)
						continue;
					java.lang.reflect.Method target = null;
					if(paramTypes.length == argTypes.length
						&& (paramTypes.length == 0 || canAssign(paramTypes[p],
							argTypes[p].getType())))
						target = m;
					else if(m.isVarArgs())
					{
						Class<?> varArgType = paramTypes[paramTypes.length - 1].getComponentType();
						for(; !bad && p < argTypes.length; p++)
							if(!canAssign(varArgType, argTypes[p].getType()))
								bad = true;
						if(!bad)
							target = m;
					}
					if(target == null)
						continue;
					if(isOnlyPublic && (target.getModifiers() & Modifier.PUBLIC) == 0)
						badTarget = target;
					else if(isStatic && (target.getModifiers() & Modifier.STATIC) == 0)
						badTarget = target;
					else
					{
						goodTarget = target;
						break;
					}
				}
				if(goodTarget != null)
				{
					Class<?> [] paramTypes = goodTarget.getParameterTypes();
					Object [] args = new Object [paramTypes.length];
					for(int i = 0; i < args.length - 1; i++)
						args[i] = argTypes[i].getValue();
					if(!goodTarget.isVarArgs())
					{
						if(args.length > 0)
							args[args.length - 1] = argTypes[args.length - 1].getValue();
					}
					else
					{
						Object varArgs = Array.newInstance(
							paramTypes[args.length - 1].getComponentType(),
							method.getArguments().length - paramTypes.length + 1);
						args[args.length - 1] = varArgs;
						for(int i = paramTypes.length - 1; i < method.getArguments().length; i++)
							Array.set(varArgs, i - paramTypes.length + 1, argTypes[i].getValue());
					}
					try
					{
						return new EvalResult(goodTarget.getReturnType(), withValues
							? goodTarget.invoke(ctxType.getValue(), args) : null);
					} catch(java.lang.reflect.InvocationTargetException e)
					{
						throw new EvaluationException(e.getMessage(), e, method,
							method.getStored("name").index);
					} catch(Exception e)
					{
						throw new EvaluationException("Could not invoke method " + method.getName()
							+ " of class " + ctxType.typeString(), e, method,
							method.getStored("name").index);
					}
				}
				else if(badTarget != null)
				{
					StringBuilder msg = new StringBuilder();
					msg.append(method.getName()).append('(');
					Class<?> [] paramTypes = badTarget.getParameterTypes();
					int p;
					for(p = 0; p < paramTypes.length - 1; p++)
					{
						msg.append(EvalResult.typeString(paramTypes[p]));
						msg.append(", ");
					}
					if(badTarget.isVarArgs())
						msg.append(EvalResult.typeString(paramTypes[p].getComponentType())).append(
							"...");
					else
						msg.append(EvalResult.typeString(paramTypes[p]));
					msg.append(')');
					if(isOnlyPublic && (badTarget.getModifiers() & Modifier.PUBLIC) == 0)
						throw new EvaluationException("The method " + msg + " from the type "
							+ ctxType.typeString() + "." + method.getName() + " is not visible",
							struct, struct.getStored("name").index);
					if(isStatic && (badTarget.getModifiers() & Modifier.STATIC) == 0)
						throw new EvaluationException(
							"Cannot make a static reference to the non-static method " + msg
								+ " from the type " + ctxType.typeString(), struct,
							struct.getStored("name").index);
					throw new EvaluationException("The method " + msg
						+ " is undefined for the type " + ctxType.typeString(), struct,
						struct.getStored("name").index);
				}
				else
					throw new EvaluationException("The method " + method.getName()
						+ " is undefined for the type " + ctxType.typeString(), struct,
						struct.getStored("name").index);
			}
		}
		else if(struct instanceof ParsedConstructor)
		{
			ParsedConstructor con = (ParsedConstructor) struct;
			EvalResult type = evaluate(con.getType(), env, true, false);
			if(type.getPackageName() != null)
				throw new EvaluationException(type.getPackageName()
					+ " cannot be resolved to a type", struct, con.getType().getMatch().index);
			else if(!type.isType())
				throw new EvaluationException(type.getPackageName()
					+ " cannot be resolved to a type", struct, con.getType().getMatch().index);
			@SuppressWarnings("rawtypes")
			java.lang.reflect.Constructor[] constructors;
			if(isOnlyPublic)
				constructors = type.getType().getConstructors();
			else
				constructors = type.getType().getDeclaredConstructors();
			EvalResult [] argTypes = new EvalResult [con.getArguments().length];
			for(int i = 0; i < argTypes.length; i++)
				argTypes[i] = evaluate(con.getArguments()[i], env, false, withValues);

			@SuppressWarnings("rawtypes")
			java.lang.reflect.Constructor goodTarget = null;
			@SuppressWarnings("rawtypes")
			java.lang.reflect.Constructor badTarget = null;
			for(@SuppressWarnings("rawtypes")
			java.lang.reflect.Constructor c : constructors)
			{
				Class<?> [] paramTypes = c.getParameterTypes();
				if(paramTypes.length > argTypes.length + 1)
					continue;
				boolean bad = false;
				int p;
				for(p = 0; !bad && p < paramTypes.length - 1; p++)
				{
					if(!canAssign(paramTypes[p], argTypes[p].getType()))
						bad = true;
				}
				if(bad)
					continue;
				@SuppressWarnings("rawtypes")
				java.lang.reflect.Constructor target = null;
				if(paramTypes.length == argTypes.length
					&& (paramTypes.length == 0 || canAssign(paramTypes[p], argTypes[p].getType())))
					target = c;
				else if(c.isVarArgs())
				{
					Class<?> varArgType = paramTypes[paramTypes.length - 1].getComponentType();
					for(; !bad && p < argTypes.length; p++)
						if(!canAssign(varArgType, argTypes[p].getType()))
							bad = true;
					if(!bad)
						target = c;
				}
				if(target == null)
					continue;
				if(isOnlyPublic && (target.getModifiers() & Modifier.PUBLIC) == 0)
					badTarget = target;
				else
				{
					goodTarget = target;
					break;
				}
			}
			if(goodTarget != null)
			{
				Class<?> [] paramTypes = goodTarget.getParameterTypes();
				Object [] args = new Object [paramTypes.length];
				for(int i = 0; i < args.length - 1; i++)
					args[i] = argTypes[i].getValue();
				if(!goodTarget.isVarArgs())
				{
					if(args.length > 0)
						args[args.length - 1] = argTypes[args.length - 1].getValue();
				}
				else
				{
					Object varArgs = Array.newInstance(
						paramTypes[args.length - 1].getComponentType(), con.getArguments().length
							- paramTypes.length + 1);
					args[args.length - 1] = varArgs;
					for(int i = paramTypes.length - 1; i < con.getArguments().length; i++)
						Array.set(varArgs, i - paramTypes.length + 1, argTypes[i].getValue());
				}
				try
				{
					return new EvalResult(goodTarget.getDeclaringClass(), withValues
						? goodTarget.newInstance(args) : null);
				} catch(java.lang.reflect.InvocationTargetException e)
				{
					throw new EvaluationException(e.getMessage(), e, con,
						con.getStored("type").index);
				} catch(Exception e)
				{
					throw new EvaluationException("Could not invoke constructor of class "
						+ type.getType().getName(), e, con, con.getStored("type").index);
				}
			}
			else if(badTarget != null)
			{
				StringBuilder msg = new StringBuilder();
				msg.append("new ").append(type.getType().getName()).append('(');
				Class<?> [] paramTypes = badTarget.getParameterTypes();
				int p;
				for(p = 0; p < paramTypes.length - 1; p++)
				{
					msg.append(EvalResult.typeString(paramTypes[p]));
					msg.append(", ");
				}
				if(badTarget.isVarArgs())
					msg.append(EvalResult.typeString(paramTypes[p].getComponentType())).append(
						"...");
				else
					msg.append(EvalResult.typeString(paramTypes[p]));
				msg.append(')');
				if(isOnlyPublic && (badTarget.getModifiers() & Modifier.PUBLIC) == 0)
					throw new EvaluationException("The constructor " + msg + " is not visible",
						struct, struct.getStored("name").index);
				throw new EvaluationException("The constructor " + msg + " is undefined", struct,
					struct.getStored("name").index);
			}
			else
			{
				StringBuilder msg = new StringBuilder();
				msg.append("new ").append(type.getType().getName()).append('(');
				int p;
				for(p = 0; p < argTypes.length; p++)
				{
					if(p > 0)
						msg.append(", ");
					msg.append(argTypes[p].typeString());
				}
				msg.append(')');
				throw new EvaluationException("The constructor " + msg + " is undefined", struct,
					struct.getStored("name").index);
			}
		}
		else if(struct instanceof ParsedDeclaration)
		{
			ParsedDeclaration dec = (ParsedDeclaration) struct;
			EvalResult typeEval = evaluate(dec.getType(), env, true, false);
			if(!typeEval.isType())
				throw new EvaluationException("Unrecognized type " + dec.getType().getMatch().text,
					dec, dec.getType().getMatch().index);
			Class<?> type = typeEval.getType();
			for(int i = 0; i < dec.getArrayDimension(); i++)
				type = Array.newInstance(type, 0).getClass();
			if(withValues)
				env.declareVariable(dec.getName(), typeEval.getType(), dec.isFinal(), struct,
					struct.getMatch().index);
			return null;
		}
		else if(struct instanceof ParsedCast)
		{
			ParsedCast cast = (ParsedCast) struct;
			EvalResult typeEval = evaluate(cast.getType(), env, true, false);
			if(!typeEval.isType())
				throw new EvaluationException(
					"Unrecognized type " + cast.getType().getMatch().text, cast, cast.getType()
						.getMatch().index);
			try
			{
				return new EvalResult(typeEval.getType(), withValues ? typeEval.getType().cast(
					evaluate(cast.getValue(), env, false, withValues)) : null);
			} catch(ClassCastException e)
			{
				throw new EvaluationException(e.getMessage(), e, cast, cast.getStored("type").index);
			}
		}
		else if(struct instanceof ParsedImport)
		{
			ParsedImport imp = (ParsedImport) struct;
			EvalResult typeEval = evaluate(imp.getType(), env, true, false);
			if(imp.isStatic())
			{
				if(!typeEval.isType())
					throw new EvaluationException("The static import "
						+ imp.getType().getMatch().text + "." + imp.getMethodName()
						+ " cannot be resolved", imp, imp.getType().getMatch().index);
				if(imp.isWildcard())
				{
					if(!typeEval.isType())
						throw new EvaluationException("The static import "
							+ imp.getType().getMatch().text + ".* cannot be resolved", imp, imp
							.getType().getMatch().index);
					for(java.lang.reflect.Method m : typeEval.getType().getDeclaredMethods())
					{
						if((m.getModifiers() & Modifier.STATIC) != 0)
							continue;
						env.addImportMethod(typeEval.getType(), m.getName());
					}
					for(java.lang.reflect.Field f : typeEval.getType().getDeclaredFields())
					{
						if((f.getModifiers() & Modifier.STATIC) != 0)
							continue;
						env.addImportMethod(typeEval.getType(), f.getName());
					}
					return null;
				}
				else
				{
					if(!typeEval.isType())
						throw new EvaluationException("The static import "
							+ imp.getType().getMatch().text + "." + imp.getMethodName()
							+ " cannot be resolved", imp, imp.getType().getMatch().index);
					boolean found = false;
					for(java.lang.reflect.Method m : typeEval.getType().getDeclaredMethods())
					{
						if((m.getModifiers() & Modifier.STATIC) == 0)
							continue;
						if(!m.getName().equals(imp.getMethodName()))
							continue;
						found = true;
						break;
					}
					if(!found)
					{
						for(java.lang.reflect.Field f : typeEval.getType().getDeclaredFields())
						{
							if((f.getModifiers() & Modifier.STATIC) == 0)
								continue;
							if(!f.getName().equals(imp.getMethodName()))
								continue;
							found = true;
							break;
						}
					}
					if(!found)
						throw new EvaluationException("The static import "
							+ imp.getType().getMatch().text + "." + imp.getMethodName()
							+ " cannot be resolved", imp, imp.getType().getMatch().index);
					env.addImportMethod(typeEval.getType(), imp.getMethodName());
					return null;
				}
			}
			else if(imp.isWildcard())
			{
				if(typeEval.getPackageName() == null)
					throw new EvaluationException("The type import "
						+ imp.getType().getMatch().text + ".* cannot be resolved", imp, imp
						.getType().getMatch().index);
				env.addImportPackage(typeEval.getPackageName());
				return null;
			}
			else
			{
				if(!typeEval.isType())
					throw new EvaluationException("The type import "
						+ imp.getType().getMatch().text + " cannot be resolved", imp, imp.getType()
						.getMatch().index);
				env.addImportType(typeEval.getType());
				return null;
			}
		}
		else if(struct instanceof ParsedLoop)
		{
			ParsedLoop loop = (ParsedLoop) struct;
			EvaluationEnvironment scoped = env.scope(false);
			for(ParsedItem init : loop.getInits())
			{
				if(init instanceof ParsedAssignmentOperator)
				{}
				else if(init instanceof ParsedDeclaration)
				{}
				else if(init instanceof ParsedMethod)
				{
					ParsedMethod method = (ParsedMethod) init;
					if(!method.isMethod())
						throw new EvaluationException("Initial expressions in a loop must be"
							+ " declarations, assignments or method calls", loop,
							init.getMatch().index);
				}
				else if(init instanceof ParsedConstructor)
				{}
				else
					throw new EvaluationException("Initial expressions in a loop must be"
						+ " declarations, assignments or method calls", loop, init.getMatch().index);
				evaluate(init, scoped, false, withValues);
			}
			ParsedItem condition = loop.getCondition();
			EvalResult condRes = evaluate(condition, scoped, false,
				withValues && loop.isPreCondition());
			if(condRes.isType() || condRes.getPackageName() != null)
				throw new EvaluationException(condRes.typeString()
					+ " cannot be resolved to a variable", struct, condition.getMatch().index);
			if(!Boolean.TYPE.equals(condRes.getType()))
				throw new EvaluationException("Type mismatch: cannot convert from "
					+ condRes.typeString() + " to boolean", struct, condition.getMatch().index);
			if(withValues && loop.isPreCondition()
				&& !((Boolean) condRes.getValue()).booleanValue())
				return null;

			do
			{
				for(ParsedItem content : loop.getContents())
				{
					if(content instanceof ParsedAssignmentOperator)
					{}
					else if(content instanceof ParsedDeclaration)
					{}
					else if(content instanceof ParsedMethod)
					{
						ParsedMethod method = (ParsedMethod) content;
						if(!method.isMethod())
							throw new EvaluationException("Content expressions in a loop must be"
								+ " declarations, assignments or method calls", loop,
								content.getMatch().index);
					}
					else if(content instanceof ParsedConstructor)
					{}
					else if(content instanceof ParsedLoop)
					{}
					else if(content instanceof ParsedIfStatement)
					{}
					else
						throw new EvaluationException("Content expressions in a loop must be"
							+ " declarations, assignments or method calls", loop,
							content.getMatch().index);
					evaluate(content, scoped, false, withValues);
				}
				for(ParsedItem inc : loop.getIncrements())
				{
					if(inc instanceof ParsedAssignmentOperator)
					{}
					else if(inc instanceof ParsedMethod)
					{
						ParsedMethod method = (ParsedMethod) inc;
						if(!method.isMethod())
							throw new EvaluationException("Increment expressions in a loop must be"
								+ " assignments or method calls", loop, inc.getMatch().index);
					}
					else if(inc instanceof ParsedConstructor)
					{}
					else
						throw new EvaluationException("Increment expressions in a loop must be"
							+ " assignments or method calls", loop, inc.getMatch().index);
					evaluate(inc, scoped, false, withValues);
				}
				if(withValues)
					condRes = evaluate(condition, env, false, true);
			} while(withValues && ((Boolean) condRes.getValue()).booleanValue());
			return null;
		}
		else if(struct instanceof ParsedIfStatement)
		{
			ParsedIfStatement ifStmt = (ParsedIfStatement) struct;
			boolean hit = false;
			for(int i = 0; i < ifStmt.getConditions().length && (!hit || !withValues); i++)
			{
				EvaluationEnvironment scoped = env.scope(false);
				ParsedItem condition = ifStmt.getConditions()[i];
				EvalResult condRes = evaluate(condition, scoped, false, withValues);
				if(condRes.isType() || condRes.getPackageName() != null)
					throw new EvaluationException(condRes.typeString()
						+ " cannot be resolved to a variable", struct, condition.getMatch().index);
				if(!Boolean.TYPE.equals(condRes.getType()))
					throw new EvaluationException("Type mismatch: cannot convert from "
						+ condRes.typeString() + " to boolean", struct, condition.getMatch().index);
				hit = !withValues || ((Boolean) condRes.getValue()).booleanValue();
				if(hit)
				{
					for(ParsedItem content : ifStmt.getContents(i))
					{
						if(content instanceof ParsedAssignmentOperator)
						{}
						else if(content instanceof ParsedDeclaration)
						{}
						else if(content instanceof ParsedMethod)
						{
							ParsedMethod method = (ParsedMethod) content;
							if(!method.isMethod())
								throw new EvaluationException(
									"Content expressions in a block must be"
										+ " declarations, assignments or method calls", ifStmt,
									content.getMatch().index);
						}
						else if(content instanceof ParsedConstructor)
						{}
						else if(content instanceof ParsedLoop)
						{}
						else if(content instanceof ParsedIfStatement)
						{}
						else
							throw new EvaluationException("Content expressions in a block must be"
								+ " declarations, assignments or method calls", ifStmt,
								content.getMatch().index);
						evaluate(content, scoped, false, withValues);
					}
				}
			}
			if(ifStmt.hasTerminal() && (!withValues || !hit))
			{
				EvaluationEnvironment scoped = env.scope(false);
				for(ParsedItem content : ifStmt.getContents(ifStmt.getConditions().length))
				{
					if(content instanceof ParsedAssignmentOperator)
					{}
					else if(content instanceof ParsedDeclaration)
					{}
					else if(content instanceof ParsedMethod)
					{
						ParsedMethod method = (ParsedMethod) content;
						if(!method.isMethod())
							throw new EvaluationException("Content expressions in a block must be"
								+ " declarations, assignments or method calls", ifStmt,
								content.getMatch().index);
					}
					else if(content instanceof ParsedConstructor)
					{}
					else if(content instanceof ParsedLoop)
					{}
					else if(content instanceof ParsedIfStatement)
					{}
					else
						throw new EvaluationException("Content expressions in a loop must be"
							+ " declarations, assignments or method calls", ifStmt,
							content.getMatch().index);
					evaluate(content, scoped, false, withValues);
				}
			}
			return null;
		}
		else
			throw new EvaluationException("Unrecognized parsed structure type: "
				+ struct.getClass().getName(), struct, struct.getMatch().index);
	}

	/**
	 * @param t1 The type of the variable to assign to
	 * @param t2 The type of the value to assign
	 * @return Whether t1 is the same or a super class of t2
	 */
	public static boolean canAssign(Class<?> t1, Class<?> t2)
	{
		if(t1.isAssignableFrom(t2))
			return true;
		if(!t1.isPrimitive() || !t2.isPrimitive())
			return false;
		if(Boolean.TYPE.equals(t1) || Character.TYPE.equals(t1) || Boolean.TYPE.equals(t2))
			return false;
		if(Double.TYPE.equals(t1))
			return true; // Can assign any number type or char to double
		if(Double.TYPE.equals(t2))
			return false; // Can't assign double to any lesser type
		if(Float.TYPE.equals(t1))
			return true;
		if(Float.TYPE.equals(t2))
			return false;
		if(Long.TYPE.equals(t1))
			return true;
		if(Long.TYPE.equals(t2))
			return false;
		if(Integer.TYPE.equals(t1))
			return true;
		if(Integer.TYPE.equals(t2))
			return false;
		if(Short.TYPE.equals(t1) || Character.TYPE.equals(t1))
			return true;
		return !(Short.TYPE.equals(t2) || Character.TYPE.equals(t2));
	}

	/**
	 * @param t1 One type
	 * @param t2 The other type
	 * @return A type that is the same or a super class of both t1 and t2, or null if no such class
	 *         exists
	 */
	public static Class<?> getMaxType(Class<?> t1, Class<?> t2)
	{
		if(t1.isAssignableFrom(t2))
			return t1;
		if(t2.isAssignableFrom(t1))
			return t2;
		if(!t1.isPrimitive() || !t2.isPrimitive())
			return null;
		if(Boolean.TYPE.equals(t1) || Boolean.TYPE.equals(t2))
			return null;
		if(Double.TYPE.equals(t1) || Double.TYPE.equals(t2))
			return Double.TYPE;
		if(Float.TYPE.equals(t1) || Float.TYPE.equals(t2))
			return Float.TYPE;
		if(Long.TYPE.equals(t1) || Long.TYPE.equals(t2))
			return Long.TYPE;
		if(Integer.TYPE.equals(t1) || Integer.TYPE.equals(t2))
			return Integer.TYPE;
		if(Character.TYPE.equals(t1) || Character.TYPE.equals(t2))
			return Character.TYPE;
		if(Short.TYPE.equals(t1) || Short.TYPE.equals(t2))
			return Short.TYPE;
		return Byte.TYPE;
	}

	private static double mathF(String op, Number op1, Number op2)
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

	private static long mathI(Class<?> type, String op, Number op1, Number op2)
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

	private static boolean compareF(String op, Number op1, Number op2)
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

	private static boolean compareI(String op, Number op1, Number op2)
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

	/**
	 * Tests the evaluator by reading in from System.in and printing out the toString()
	 * representation of the evaluated value of each structure parsed
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		prisms.arch.PrismsServer.initLog4j(prisms.arch.PrismsServer.class.getResource("log4j.xml"));
		PrismsParser parser = new PrismsParser();
		try
		{
			parser.configure(prisms.arch.PrismsConfig.fromXml(
				null,
				prisms.arch.PrismsConfig.getRootElement("Grammar.xml",
					prisms.arch.PrismsConfig.getLocation(PrismsParser.class))));
		} catch(java.io.IOException e)
		{
			e.printStackTrace();
			return;
		}
		DefaultJavaEvaluator eval = new DefaultJavaEvaluator();
		EvaluationEnvironment env = new DefaultEvaluationEnvironment();
		java.util.Scanner scanner = new java.util.Scanner(System.in);
		String line = null;
		boolean complete = true;
		do
		{
			if(complete)
				System.out.print(">   ");
			else
				System.out.print("... ");
			String newLine = scanner.nextLine();
			if("exit".equals(newLine))
				break;
			if(complete)
				line = newLine;
			else
				line += "\n" + newLine;
			ParsedItem [] structs;
			try
			{
				ParseMatch [] matches = parser.parseMatches(line);
				ParseStructRoot root = new ParseStructRoot(line);
				structs = parser.parseStructures(root, matches);
				complete = true;
			} catch(IncompleteInputException e)
			{
				complete = false;
				continue;
			} catch(ParseException e)
			{
				complete = true;
				e.printStackTrace(System.out);
				continue;
			}
			for(ParsedItem s : structs)
			{
				try
				{
					eval.evaluate(s, env, false, false);
					EvalResult type = eval.evaluate(s, env, false, true);
					if(type != null && !Void.TYPE.equals(type.getType()))
					{
						System.out.println("\t" + prisms.util.ArrayUtils.toString(type.getValue()));
						env.addHistory(type.getType(), type.getValue());
					}
				} catch(EvaluationException e)
				{
					e.printStackTrace(System.out);
				}
			}
		} while(true);
	}
}
