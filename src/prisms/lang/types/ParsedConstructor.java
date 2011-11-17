/*
 * ParsedConstructor.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents a construactor */
public class ParsedConstructor extends prisms.lang.ParseStruct
{
	private prisms.lang.ParseStruct theType;

	private prisms.lang.ParseStruct[] theArguments;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theType = parser.parseStructures(this, getStored("type"))[0];
		java.util.ArrayList<prisms.lang.ParseStruct> args = new java.util.ArrayList<prisms.lang.ParseStruct>();
		for(prisms.lang.ParseMatch m : match.getParsed())
			if(m.config.getName().equals("op") && !"type".equals(m.config.get("storeAs")))
				args.add(parser.parseStructures(this, m)[0]);
		theArguments = args.toArray(new prisms.lang.ParseStruct [args.size()]);
	}

	/** @return The type that this constructor is to instantiate */
	public prisms.lang.ParseStruct getType()
	{
		return theType;
	}

	/** @return The arguments to this constructor */
	public prisms.lang.ParseStruct[] getArguments()
	{
		return theArguments;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append("new ").append(theType.toString()).append('(');
		boolean first = true;
		for(prisms.lang.ParseStruct arg : theArguments)
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
}
