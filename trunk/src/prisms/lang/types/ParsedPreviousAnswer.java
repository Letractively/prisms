/*
 * ParsedPreviousAnswer.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Allows the results of previous calculations to be accessed dynamically */
public class ParsedPreviousAnswer extends prisms.lang.ParsedItem
{
	private int theIndex;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
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
	public prisms.lang.EvaluationResult<Object> evaluate(prisms.lang.EvaluationEnvironment env,
		boolean asType, boolean withValues) throws prisms.lang.EvaluationException
	{
		if(env.getHistoryCount() == 0)
			throw new prisms.lang.EvaluationException("No previous results available", this,
				getMatch().index);
		else if(env.getHistoryCount() <= this.getIndex())
			throw new prisms.lang.EvaluationException("Only " + env.getHistoryCount()
				+ " previous result(s) are available", this, getMatch().index);
		else
		{
			int index = getIndex();
			return new prisms.lang.EvaluationResult<Object>(env.getHistoryType(index), withValues
				? env.getHistory(index) : null);
		}
	}
}
