/*
 * ParsedMethod.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.ParseMatch;

/**
 * Represents one of:
 * <ul>
 * <li><b>A function:</b> An operation with no context, in the form of fn(arg1, arg2...)</li>
 * <li><b>A field:</b> A property out of a context, in the form of ctx.fieldName</li>
 * <li><b>A method:</b> An operation with a context, in the form of ctx.fn(arg1, arg2...)</li>
 * </ul>
 */
public class ParsedMethod extends prisms.lang.ParseStruct
{
	private String theName;

	private boolean isMethod;

	private prisms.lang.ParseStruct theContext;

	private prisms.lang.ParseStruct[] theArguments;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theName = getStored("name").text;
		isMethod = getStored("method") != null;
		ParseMatch miMatch = null;
		if(!getStored("name").config.getName().equals("pre-op"))
		{
			for(ParseMatch m : match.getParsed())
			{
				if(m.config.getName().equals("pre-op"))
					miMatch = m;
				else if(m.config.getName().equals("op"))
					break;
			}
		}
		if(miMatch != null)
			theContext = parser.parseStructures(this, miMatch)[0];
		else
			isMethod = true;
		java.util.ArrayList<ParseMatch> opMatches = new java.util.ArrayList<ParseMatch>();
		for(ParseMatch m : match.getParsed())
			if(m.config.getName().equals("op") && !"name".equals(m.config.get("storeAs")))
				opMatches.add(m);
		theArguments = parser.parseStructures(this,
			opMatches.toArray(new ParseMatch [opMatches.size()]));
	}

	/** @return The name of this field or method */
	public String getName()
	{
		return theName;
	}

	/** @return Whether this represents a method or a field */
	public boolean isMethod()
	{
		return isMethod;
	}

	/** @return The instance on which this field or method was invoked */
	public prisms.lang.ParseStruct getContext()
	{
		return theContext;
	}

	/** @return The arguments to this method */
	public prisms.lang.ParseStruct[] getArguments()
	{
		return theArguments;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		if(theContext != null)
			ret.append(theContext.toString()).append('.');
		ret.append(theName);
		if(isMethod)
		{
			ret.append('(');
			for(int i = 0; i < theArguments.length; i++)
			{
				if(i > 0)
					ret.append(", ");
				ret.append(theArguments[i].toString());
			}
			ret.append(')');
		}
		return ret.toString();
	}
}
