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
	 * Parses this structure type's data, including operands. Extensions must be aware that this method may be called
	 * with an incomplete match structure. Exceptions should NOT be thrown from this method for incomplete data.
	 * Instead, as much of the structure of this item should be parsed and stored as is present in the match and the
	 * rests should be left null.
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
	 * @return All parsed items that this item is dependent on, in order of their appearance in the text. If this item's
	 *         match is incomplete, the returned array may end with one or more nulls in place of dependents that this
	 *         item requires to evaluate properly, but were not present in the text.
	 */
	public abstract ParsedItem [] getDependents();

	/**
	 * Validates or evaluates this expression. This method should never be called if this item's match is incomplete.
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
