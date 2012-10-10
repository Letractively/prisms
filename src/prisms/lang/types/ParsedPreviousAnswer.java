/*
 * ParsedPreviousAnswer.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.ParsedItem;

/** Allows the results of previous calculations to be accessed dynamically */
public class ParsedPreviousAnswer extends ParsedItem
{
	private int theIndex;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		prisms.lang.ParseMatch indexMatch = getStored("index");
		if(indexMatch != null)
			theIndex = Integer.parseInt(indexMatch.text);
	}

	/** @return The index (starting at 0) of the answer this refers to */
	public int getIndex()
	{
		return theIndex;
	}

	@Override
	public ParsedItem [] getDependents()
	{
		return new ParsedItem [0];
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
		throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws prisms.lang.EvaluationException
	{
		if(env.getHistoryCount() == 0)
			throw new prisms.lang.EvaluationException("No previous results available", this, getMatch().index);
		else if(env.getHistoryCount() <= this.getIndex())
			throw new prisms.lang.EvaluationException("Only " + env.getHistoryCount() + " previous result(s) are available", this,
				getMatch().index);
		else
		{
			int index = getIndex();
			return new prisms.lang.EvaluationResult(env.getHistoryType(index), withValues ? env.getHistory(index) : null);
		}
	}

	@Override
	public String toString()
	{
		if(theIndex > 0)
			return "%" + theIndex;
		else
			return "%";
	}
}
