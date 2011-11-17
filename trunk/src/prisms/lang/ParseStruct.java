/*
 * ParseStruct.java Created Nov 10, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** A basic syntax structure representing the final output of the {@link PrismsParser} */
public abstract class ParseStruct
{
	private PrismsParser theParser;

	private ParseStruct theParent;

	private ParseMatch theMatch;

	private int theStart;

	/**
	 * Parses this structure type's data, including operands
	 * 
	 * @param parser The parser that is parsing this structure
	 * @param parent The parent structure
	 * @param match The parse match that this structure will represent
	 * @param start The index in the full command of the parse match
	 * @throws ParseException If parsing or syntactical validation fails
	 */
	public void setup(PrismsParser parser, ParseStruct parent, ParseMatch match, int start)
		throws ParseException
	{
		theParser = parser;
		theParent = parent;
		theMatch = match;
		theStart = start;
	}

	/** @return The parser that parsed this structure */
	public PrismsParser getParser()
	{
		return theParser;
	}

	/** @return This structure's parent */
	public ParseStruct getParent()
	{
		return theParent;
	}

	/** @return The root of this structure */
	public ParseStructRoot getRoot()
	{
		ParseStruct ret = this;
		while(ret != null && !(ret instanceof ParseStructRoot))
			ret = ret.theParent;
		return (ParseStructRoot) ret;
	}

	/** @return The parsed match that this structure was parsed from */
	public ParseMatch getMatch()
	{
		return theMatch;
	}

	/**
	 * @param name The name of the stored match to get
	 * @return The match within this structure stored as the given name
	 */
	public ParseMatch getStored(String name)
	{
		if(theMatch.getParsed() == null)
			return null;
		for(ParseMatch match : theMatch.getParsed())
			if(name.equals(match.config.get("storeAs")))
				return match;
		return null;
	}

	/** @return The start index within the full command of this structure's parsed text */
	public int getStart()
	{
		return theStart;
	}
}
