/*
 * ParsedConstructor.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/**
 * Represents a construactor
 * 
 * @param <T> The type to be constructed
 */
public class ParsedConstructor<T> extends prisms.lang.ParsedItem<T>
{
	private prisms.lang.ParsedItem<T> theType;

	private prisms.lang.ParsedItem<?> [] theArguments;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem<?> parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theType = parser.parseStructures(this, getStored("type"))[0];
		java.util.ArrayList<prisms.lang.ParsedItem<?>> args = new java.util.ArrayList<prisms.lang.ParsedItem<?>>();
		for(prisms.lang.ParseMatch m : match.getParsed())
			if(m.config.getName().equals("op") && !"type".equals(m.config.get("storeAs")))
				args.add(parser.parseStructures(this, m)[0]);
		theArguments = args.toArray(new prisms.lang.ParsedItem [args.size()]);
	}

	/** @return The type that this constructor is to instantiate */
	public prisms.lang.ParsedItem<T> getType()
	{
		return theType;
	}

	/** @return The arguments to this constructor */
	public prisms.lang.ParsedItem<?> [] getArguments()
	{
		return theArguments;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append("new ").append(theType.toString()).append('(');
		boolean first = true;
		for(prisms.lang.ParsedItem<?> arg : theArguments)
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

	@Override
	public prisms.lang.EvaluationResult<T> evaluate(prisms.lang.EvaluationEnvironment env,
		boolean asType, boolean withValues) throws prisms.lang.EvaluationException
	{
		// TODO Auto-generated method stub
	}
}
