/*
 * ParsedImport.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import java.lang.reflect.Modifier;

import prisms.lang.EvaluationException;

/** Represents an import command */
public class ParsedImport extends prisms.lang.ParsedItem
{
	private boolean isStatic;

	private boolean isWildcard;

	private prisms.lang.ParsedItem theType;

	private String theMethodName;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		isStatic = getStored("static") != null;
		isWildcard = getStored("wildcard") != null;
		theType = parser.parseStructures(this, getStored("type"))[0];
		if(isStatic)
		{
			if(!isWildcard)
			{
				if(theType instanceof ParsedMethod)
				{
					ParsedMethod method = (ParsedMethod) theType;
					if(method.isMethod())
						throw new prisms.lang.ParseException("The import " + theType.getMatch().text
							+ " cannot be resolved", getRoot().getFullCommand(), theType.getMatch().index);
					theType = method.getContext();
					theMethodName = method.getName();
				}
				else if(!getMatch().isComplete())
				{}
				else if(theType instanceof ParsedIdentifier)
					throw new prisms.lang.ParseException("The import " + theType.getMatch().text
						+ " cannot be resolved", getRoot().getFullCommand(), theType.getMatch().index);
				else
					throw new prisms.lang.ParseException("Syntax error: name expected", getRoot().getFullCommand(),
						theType.getMatch().index);
			}
		}
	}

	/**
	 * @return Either the type that was imported (for a non-static, non-wildcard import), the type to which belong the
	 *         method or methods that were imported with a static import, or the package to import for a non-static
	 *         wildcard import.
	 */
	public prisms.lang.ParsedItem getType()
	{
		return theType;
	}

	/** @return The name of the method being imported. Will be null unless this is a static, non-wildcard import */
	public String getMethodName()
	{
		return theMethodName;
	}

	/** @return Whether this is a static (method-level) import */
	public boolean isStatic()
	{
		return isStatic;
	}

	/** @return Whether this is a wildcard import, meaning it could potentially import more than one type or method */
	public boolean isWildcard()
	{
		return isWildcard;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		return new prisms.lang.ParsedItem [] {theType};
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws EvaluationException
	{
		prisms.lang.EvaluationResult typeEval = theType.evaluate(env, true, false);
		if(isStatic)
		{
			if(!typeEval.isType())
				throw new EvaluationException("The static import " + theType.getMatch().text + "." + theMethodName
					+ " cannot be resolved", this, theType.getMatch().index);
			if(isWildcard)
			{
				if(!typeEval.isType())
					throw new EvaluationException("The static import " + theType.getMatch().text
						+ ".* cannot be resolved", this, theType.getMatch().index);
				for(java.lang.reflect.Method m : typeEval.getType().getBaseType().getDeclaredMethods())
				{
					if((m.getModifiers() & Modifier.STATIC) == 0)
						continue;
					env.addImportMethod(typeEval.getType().getBaseType(), m.getName());
				}
				for(java.lang.reflect.Field f : typeEval.getType().getBaseType().getDeclaredFields())
				{
					if((f.getModifiers() & Modifier.STATIC) == 0)
						continue;
					env.addImportMethod(typeEval.getType().getBaseType(), f.getName());
				}
				return null;
			}
			else
			{
				if(!typeEval.isType())
					throw new EvaluationException("The static import " + theType.getMatch().text + "." + theMethodName
						+ " cannot be resolved", this, theType.getMatch().index);
				boolean found = false;
				for(java.lang.reflect.Method m : typeEval.getType().getBaseType().getDeclaredMethods())
				{
					if((m.getModifiers() & Modifier.STATIC) == 0)
						continue;
					if(!m.getName().equals(theMethodName))
						continue;
					found = true;
					break;
				}
				if(!found)
				{
					for(java.lang.reflect.Field f : typeEval.getType().getBaseType().getDeclaredFields())
					{
						if((f.getModifiers() & Modifier.STATIC) == 0)
							continue;
						if(!f.getName().equals(theMethodName))
							continue;
						found = true;
						break;
					}
				}
				if(!found)
					throw new EvaluationException("The static import " + theType.getMatch().text + "." + theMethodName
						+ " cannot be resolved", this, theType.getMatch().index);
				env.addImportMethod(typeEval.getType().getBaseType(), theMethodName);
				return null;
			}
		}
		else if(isWildcard)
		{
			if(typeEval.getPackageName() == null)
				throw new EvaluationException("The type import " + theType.getMatch().text + ".* cannot be resolved",
					this, theType.getMatch().index);
			env.addImportPackage(typeEval.getPackageName());
			return null;
		}
		else
		{
			if(!typeEval.isType())
				throw new EvaluationException("The type import " + theType.getMatch().text + " cannot be resolved",
					this, theType.getMatch().index);
			env.addImportType(typeEval.getType().getBaseType());
			return null;
		}
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append("import ");
		if(isStatic)
			ret.append("static ");
		ret.append(theType);
		if(isWildcard)
			ret.append(".*");
		else if(theMethodName != null)
			ret.append('.').append(theMethodName);
		return ret.toString();
	}
}
