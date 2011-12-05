package prisms.lang.types;

/** Represents an operation by which a user may undo a variable declaration */
public class ParsedDrop extends prisms.lang.ParsedItem
{
	private String theName;

	private ParsedType [] theParamTypes;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theName = getStored("name").text;
		if(getStored("function") != null)
		{
			theParamTypes = new ParsedType [0];
			for(prisms.lang.ParseMatch m : match.getParsed())
				if("parameter".equals(m.config.get("storeAs")))
					theParamTypes = prisms.util.ArrayUtils.add(theParamTypes,
						(ParsedType) parser.parseStructures(this, m)[0]);
		}
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		if(theParamTypes == null)
		{
			env.dropVariable(theName, this, getStored("name").index);
			return null;
		}
		else
		{
			for(ParsedFunctionDeclaration func : env.getDeclaredFunctions())
			{
				if(!func.getName().equals(theName))
					continue;
				if(func.getParameters().length != theParamTypes.length)
					continue;
				for(int i = 0; i < theParamTypes.length; i++)
					if(!func.getParameters()[i].getType().evaluate(env, true, withValues)
						.equals(theParamTypes[i].evaluate(env, true, withValues)))
						continue;
				env.dropFunction(func, this, getStored("name").index);
				return null;
			}
			throw new prisms.lang.EvaluationException("No such function " + this, this, getStored("name").index);
		}
	}

	/** @return The name of the variable or function to drop */
	public String getName()
	{
		return theName;
	}

	/** @return The parameter types of the function to drop, or null if this drop is for a variable */
	public ParsedType [] getParameterTypes()
	{
		return theParamTypes;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		if(theParamTypes == null)
			return new prisms.lang.ParsedItem [0];
		return theParamTypes;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append("drop ").append(theName);
		if(theParamTypes != null)
		{
			ret.append('(');
			for(int i = 0; i < theParamTypes.length; i++)
			{
				if(i > 0)
					ret.append(", ");
				ret.append(theParamTypes[i]);
			}
			ret.append(')');
		}
		return ret.toString();
	}
}
