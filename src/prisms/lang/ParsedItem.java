/*
 * ParseStruct.java Created Nov 10, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** A basic syntax structure representing the final output of the {@link PrismsParser} */
public abstract class ParsedItem
{
	private PrismsParser theParser;

	private ParsedItem theParent;

	private ParseMatch theMatch;

	/**
	 * Parses this structure type's data, including operands
	 * 
	 * @param parser The parser that is parsing this structure
	 * @param parent The parent structure
	 * @param match The parse match that this structure will represent
	 * @throws ParseException If parsing or syntactical validation fails
	 */
	public void setup(PrismsParser parser, ParsedItem parent, ParseMatch match) throws ParseException
	{
		theParser = parser;
		theParent = parent;
		theMatch = match;
	}

	/** @return The parser that parsed this structure */
	public PrismsParser getParser()
	{
		return theParser;
	}

	/** @return This structure's parent */
	public ParsedItem getParent()
	{
		return theParent;
	}

	/** @return The root of this structure */
	public ParseStructRoot getRoot()
	{
		ParsedItem ret = this;
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
		for(ParseMatch match : theMatch.getParsed())
			if(match.getParsed() != null)
				for(ParseMatch subMatch : match.getParsed())
					if(name.equals(subMatch.config.get("storeAs")))
						return subMatch;
		return null;
	}

	/**
	 * Validates or evaluates this expression
	 * 
	 * @param env The evaluation environment to execute in
	 * @param asType Whether the result should be a type if possible
	 * @param withValues Whether to evaluate the value of the expression, or simply validate it and return its type
	 * @return The result of the expression
	 * @throws EvaluationException If an error occurs evaluating the expression
	 */
	public abstract EvaluationResult evaluate(EvaluationEnvironment env, boolean asType, boolean withValues)
		throws EvaluationException;
}
