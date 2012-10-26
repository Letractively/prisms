package prisms.lang.types;

import prisms.lang.ParsedItem;
import prisms.lang.PrismsParser;

/** Represents a commented text block */
public class ParsedComment extends ParsedItem
{
	private String theContent;

	@Override
	public void setup(PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theContent = getStored("content").text;
	}

	/** @return The comment's content */
	public String getContent()
	{
		return theContent;
	}

	@Override
	public ParsedItem [] getDependents()
	{
		return new ParsedItem [0];
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws prisms.lang.EvaluationException
	{
		return null;
	}
}
