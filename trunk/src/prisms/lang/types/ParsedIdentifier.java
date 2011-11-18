/*
 * ParsedIdentifier.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.*;
import prisms.lang.PrismsEvaluator.EvalResult;

/** A simple identifier */
public class ParsedIdentifier extends Assignable
{
	private String theName;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theName = getStored("name").text;
	}

	/** @return This identifier's name */
	public String getName()
	{
		return theName;
	}

	@Override
	public String toString()
	{
		return theName;
	}

	@Override
	public prisms.lang.EvaluationResult<?> evaluate(prisms.lang.EvaluationEnvironment env,
		boolean asType, boolean withValues) throws prisms.lang.EvaluationException
	{
		if(!asType)
		{
			Class<Object> type = (Class<Object>) env.getVariableType(theName);
			if(type != null)
				return new EvaluationResult<Object>(type, withValues ? env.getVariable(theName,
					this, getStored("name").index) : null);
		}
		if("boolean".equals(theName))
			return new EvaluationResult<Boolean>(Boolean.TYPE);
		else if("char".equals(theName))
			return new EvaluationResult<Character>(Character.TYPE);
		else if("double".equals(theName))
			return new EvaluationResult<Double>(Double.TYPE);
		else if("float".equals(theName))
			return new EvaluationResult<Float>(Float.TYPE);
		else if("long".equals(theName))
			return new EvaluationResult<Long>(Long.TYPE);
		else if("int".equals(theName))
			return new EvaluationResult<Integer>(Integer.TYPE);
		else if("short".equals(theName))
			return new EvaluationResult<Short>(Short.TYPE);
		else if("byte".equals(theName))
			return new EvaluationResult<Byte>(Byte.TYPE);
		Class<?> clazz;
		try
		{
			clazz = Class.forName(theName);
		} catch(ClassNotFoundException e)
		{
			clazz = null;
		}
		if(clazz != null)
			return new EvaluationResult<Object>((Class<Object>) clazz);
		clazz = env.getImportType(theName);
		if(clazz != null)
			return new EvaluationResult<Object>((Class<Object>) clazz);
		try
		{
			clazz = Class.forName("java.lang." + theName);
		} catch(ClassNotFoundException e)
		{
			clazz = null;
		}
		if(clazz != null)
			return new EvaluationResult<Object>((Class<Object>) clazz);
		Package [] pkgs = Package.getPackages();
		for(Package pkg : pkgs)
			if(pkg.getName().equals(theName) || pkg.getName().startsWith(theName + "."))
				return new EvaluationResult<Object>(theName);
		Class<?> importType = env.getImportMethodType(theName);
		if(importType != null)
		{
			for(java.lang.reflect.Field f : importType.getDeclaredFields())
			{
				if(!theName.equals(f.getName()))
					continue;
				if((f.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0)
					continue;
				if(env.usePublicOnly()
					&& (f.getModifiers() & java.lang.reflect.Modifier.PUBLIC) == 0)
					continue;
				try
				{
					return new EvaluationResult<Object>(f.getType(), withValues ? f.get(null)
						: null);
				} catch(Exception e)
				{
					throw new EvaluationException("Could not access field " + theName + " on type "
						+ EvalResult.typeString(importType), e, this, getStored("name").index);
				}
			}
		}
		throw new EvaluationException(theName + " cannot be resolved to a "
			+ (asType ? "type" : "variable"), this, getMatch().index);
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
		env.setVariable(theName, value, assign, assign.getStored("name").index);
	}
}
