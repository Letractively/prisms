/*
 * ParsedDeclaration.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationEnvironment;
import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;

/** Represents a typed, parsed declaration */
public class ParsedDeclaration extends Assignable
{
	private prisms.lang.ParsedItem theType;

	private ParsedType [] theParamTypes;

	private String theName;

	private boolean isFinal;

	private int theArrayDimension;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theType = parser.parseStructures(this, getStored("type"))[0];
		theName = getStored("name").text;
		isFinal = getStored("final") != null;
		theParamTypes = new ParsedType [0];
		for(prisms.lang.ParseMatch m : match.getParsed())
		{
			if("array".equals(m.config.get("storeAs")))
				theArrayDimension++;
			else if("paramType".equals(m.config.get("storeAs")))
				theParamTypes = prisms.util.ArrayUtils.add(theParamTypes,
					(ParsedType) parser.parseStructures(this, m)[0]);
		}
	}

	/** @return The name of the declared variable */
	public String getName()
	{
		return theName;
	}

	/** @return Whether the variable in this declaration is marked as final */
	public boolean isFinal()
	{
		return isFinal;
	}

	/** @return The type of this declaration */
	public prisms.lang.ParsedItem getType()
	{
		return theType;
	}

	/** @return The dimension of this array declaration, or 0 if this declaration is not an array */
	public int getArrayDimension()
	{
		return theArrayDimension;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		prisms.lang.ParsedItem[] ret = new prisms.lang.ParsedItem [theParamTypes.length + 1];
		ret[0] = theType;
		System.arraycopy(theParamTypes, 0, ret, 1, theParamTypes.length);
		return ret;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append(theType);
		if(theParamTypes.length > 0)
		{
			ret.append('<');
			for(int p = 0; p < theParamTypes.length; p++)
			{
				if(p > 0)
					ret.append(", ");
				ret.append(theParamTypes[p]);
			}
			ret.append('>');
		}
		ret.append(' ').append(theName);
		return ret.toString();
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws EvaluationException
	{
		EvaluationResult res = theType.evaluate(env, true, true);
		if(!res.isType())
			throw new EvaluationException(theType.getMatch().text + " cannot be resolved to a type", this,
				theType.getMatch().index);
		if(theParamTypes.length > 0)
		{
			prisms.lang.Type[] ptTypes = new prisms.lang.Type [theParamTypes.length];
			for(int p = 0; p < ptTypes.length; p++)
				ptTypes[p] = theParamTypes[p].evaluate(env, true, true).getType();
			if(ptTypes.length > 0 && res.getType().getBaseType().getTypeParameters().length == 0)
			{
				String args = prisms.util.ArrayUtils.toString(ptTypes);
				args = args.substring(1, args.length() - 1);
				int index = theType.getMatch().index + theType.getMatch().text.length();
				throw new prisms.lang.EvaluationException("The type " + res.getType()
					+ " is not generic; it cannot be parameterized with arguments <" + args + ">", theType, index);
			}
			if(ptTypes.length > 0 && ptTypes.length != res.getType().getBaseType().getTypeParameters().length)
			{
				String type = res.getType().getBaseType().getName() + "<";
				for(java.lang.reflect.Type t : res.getType().getBaseType().getTypeParameters())
					type += t + ", ";
				type = type.substring(0, type.length() - 2);
				type += ">";
				String args = prisms.util.ArrayUtils.toString(ptTypes);
				args = args.substring(1, args.length() - 1);
				int index = theType.getMatch().index + theType.getMatch().text.length();
				throw new prisms.lang.EvaluationException("Incorrect number of arguments for type " + type
					+ "; it cannot be parameterized with arguments <" + args + ">", theType, index);
			}
			res = new EvaluationResult(new prisms.lang.Type(res.getType().getBaseType(), ptTypes));
		}
		if(theArrayDimension > 0)
		{
			prisms.lang.Type t = res.getType();
			for(int i = 0; i < theArrayDimension; i++)
				t = t.getArrayType();
			res = new EvaluationResult(t);
		}
		env.declareVariable(theName, res.getType(), isFinal, this, getStored("name").index);
		return null;
	}

	@Override
	public EvaluationResult getValue(EvaluationEnvironment env, ParsedAssignmentOperator assign)
		throws EvaluationException
	{
		evaluate(env, false, false);
		throw new EvaluationException("Syntax error: " + theName + " has not been assigned", this,
			getStored("name").index);
	}

	@Override
	public void assign(EvaluationResult value, EvaluationEnvironment env, ParsedAssignmentOperator assign)
		throws EvaluationException
	{
		EvaluationResult res = theType.evaluate(env, true, true);
		if(!res.isType())
			throw new EvaluationException(theType.getMatch().text + " cannot be resolved to a type", this,
				theType.getMatch().index);
		if(theArrayDimension > 0)
		{
			prisms.lang.Type t = res.getType();
			for(int i = 0; i < theArrayDimension; i++)
				t = t.getArrayType();
			res = new EvaluationResult(t);
		}

		if(!res.getType().isAssignable(value.getType()))
			throw new EvaluationException("Type mismatch: Cannot convert from " + value.getType() + " to " + res, this,
				assign.getOperand().getMatch().index);
		env.setVariable(theName, value.getValue(), assign, assign.getStored("name").index);
	}
}
