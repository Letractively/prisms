/*
 * ParsedConstructor.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationException;
import prisms.lang.Type;

/** Represents a constructor */
public class ParsedConstructor extends prisms.lang.ParsedItem
{
	private prisms.lang.ParsedItem theType;

	private prisms.lang.ParsedItem[] theArguments;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theType = parser.parseStructures(this, getStored("type"))[0];
		java.util.ArrayList<prisms.lang.ParsedItem> args = new java.util.ArrayList<prisms.lang.ParsedItem>();
		for(prisms.lang.ParseMatch m : match.getParsed())
			if(m.config.getName().equals("op") && !"type".equals(m.config.get("storeAs")))
				args.add(parser.parseStructures(this, m)[0]);
		theArguments = args.toArray(new prisms.lang.ParsedItem [args.size()]);
	}

	/** @return The type that this constructor is to instantiate */
	public prisms.lang.ParsedItem getType()
	{
		return theType;
	}

	/** @return The arguments to this constructor */
	public prisms.lang.ParsedItem[] getArguments()
	{
		return theArguments;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append("new ").append(theType.toString()).append('(');
		boolean first = true;
		for(prisms.lang.ParsedItem arg : theArguments)
		{
			if(first)
				first = false;
			else
				ret.append(", ");
			ret.append(arg.toString());
		}
		ret.append(')');
		return ret.toString();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws EvaluationException
	{
		prisms.lang.EvaluationResult type = theType.evaluate(env, true, false);
		if(!type.isType())
			throw new EvaluationException(type.typeString() + " cannot be resolved to a type", this,
				theType.getMatch().index);
		java.lang.reflect.Constructor[] constructors;
		if(env.usePublicOnly())
			constructors = type.getType().getBaseType().getConstructors();
		else
			constructors = type.getType().getBaseType().getDeclaredConstructors();
		prisms.lang.EvaluationResult[] argRes = new prisms.lang.EvaluationResult [theArguments.length];
		for(int i = 0; i < argRes.length; i++)
			argRes[i] = theArguments[i].evaluate(env, false, withValues);

		java.lang.reflect.Constructor goodTarget = null;
		java.lang.reflect.Constructor badTarget = null;
		for(java.lang.reflect.Constructor c : constructors)
		{
			java.lang.reflect.Type[] _paramTypes = c.getGenericParameterTypes();
			Type [] paramTypes = new Type [_paramTypes.length];
			for(int p = 0; p < paramTypes.length; p++)
				paramTypes[p] = type.getType().resolve(_paramTypes[p], c.getDeclaringClass());
			if(paramTypes.length > argRes.length + 1)
				continue;
			boolean bad = false;
			int p;
			for(p = 0; !bad && p < paramTypes.length - 1; p++)
			{
				if(!paramTypes[p].isAssignable(argRes[p].getType()))
					bad = true;
			}
			if(bad)
				continue;
			java.lang.reflect.Constructor target = null;
			if(paramTypes.length == argRes.length
				&& (paramTypes.length == 0 || paramTypes[p].isAssignable(argRes[p].getType())))
				target = c;
			else if(c.isVarArgs())
			{
				Type varArgType = paramTypes[paramTypes.length - 1].getComponentType();
				for(; !bad && p < argRes.length; p++)
					if(!varArgType.isAssignable(argRes[p].getType()))
						bad = true;
				if(!bad)
					target = c;
			}
			if(target == null)
				continue;
			if(env.usePublicOnly() && (target.getModifiers() & java.lang.reflect.Modifier.PUBLIC) == 0)
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
				args[i] = argRes[i].getValue();
			if(!goodTarget.isVarArgs())
			{
				if(args.length > 0)
					args[args.length - 1] = argRes[args.length - 1].getValue();
			}
			else
			{
				Object varArgs = java.lang.reflect.Array.newInstance(paramTypes[args.length - 1].getComponentType(),
					theArguments.length - paramTypes.length + 1);
				args[args.length - 1] = varArgs;
				for(int i = paramTypes.length - 1; i < theArguments.length; i++)
					java.lang.reflect.Array.set(varArgs, i - paramTypes.length + 1, argRes[i].getValue());
			}
			try
			{
				return new prisms.lang.EvaluationResult(type.getType(), withValues ? goodTarget.newInstance(args)
					: null);
			} catch(java.lang.reflect.InvocationTargetException e)
			{
				throw new prisms.lang.ExecutionException(e.getMessage(), e, this, getStored("type").index);
			} catch(Exception e)
			{
				throw new EvaluationException("Could not invoke constructor of class "
					+ type.getType().getBaseType().getName(), e, this, getStored("type").index);
			}
		}
		else if(badTarget != null)
		{
			StringBuilder msg = new StringBuilder();
			msg.append("new ").append(type.getType().getBaseType().getName()).append('(');
			Class<?> [] paramTypes = badTarget.getParameterTypes();
			int p;
			for(p = 0; p < paramTypes.length - 1; p++)
			{
				msg.append(prisms.lang.Type.typeString(paramTypes[p]));
				msg.append(", ");
			}
			if(badTarget.isVarArgs())
				msg.append(prisms.lang.Type.typeString(paramTypes[p].getComponentType())).append("...");
			else
				msg.append(prisms.lang.Type.typeString(paramTypes[p]));
			msg.append(')');
			if(env.usePublicOnly() && (badTarget.getModifiers() & java.lang.reflect.Modifier.PUBLIC) == 0)
				throw new EvaluationException("The constructor " + msg + " is not visible", this,
					getStored("type").index);
			throw new EvaluationException("The constructor " + msg + " is undefined", this, getStored("type").index);
		}
		else
		{
			StringBuilder msg = new StringBuilder();
			msg.append("new ").append(type.getType().getBaseType().getName()).append('(');
			int p;
			for(p = 0; p < argRes.length; p++)
			{
				if(p > 0)
					msg.append(", ");
				msg.append(argRes[p].typeString());
			}
			msg.append(')');
			throw new EvaluationException("The constructor " + msg + " is undefined", this, getStored("type").index);
		}
	}
}
