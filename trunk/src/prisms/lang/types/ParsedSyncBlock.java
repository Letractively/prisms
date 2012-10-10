package prisms.lang.types;

import prisms.lang.ParsedItem;

/** Represents a synchronized block */
public class ParsedSyncBlock extends ParsedItem
{
	private ParsedItem theSyncItem;

	private ParsedStatementBlock theContents;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theSyncItem = parser.parseStructures(this, getStored("syncItem"))[0];
		theContents = (ParsedStatementBlock) parser.parseStructures(this, getStored("content"))[0];
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws prisms.lang.EvaluationException
	{
		prisms.lang.EvaluationResult syncItemRes = theSyncItem.evaluate(env, false, withValues);
		if(!syncItemRes.isValue())
			throw new prisms.lang.EvaluationException(syncItemRes.typeString() + " cannot be resolved to a variable", this,
				theSyncItem.getMatch().index);
		prisms.lang.EvaluationResult res;
		if(withValues)
			synchronized(syncItemRes.getValue())
			{
				res = theContents.evaluate(env, false, withValues);
			}
		else
			res = theContents.evaluate(env, false, withValues);
		return res;
	}

	/** @return The expression to get the item that will be synchronized on while the contents execute */
	public ParsedItem getSyncItem()
	{
		return theSyncItem;
	}

	/** @return The contents that will be executed while the sync item is synchronized */
	public ParsedStatementBlock getContents()
	{
		return theContents;
	}

	@Override
	public ParsedItem [] getDependents()
	{
		return new ParsedItem [] {theSyncItem, theContents};
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
		if(theSyncItem == dependent)
			theSyncItem = toReplace;
		else if(theContents == dependent)
		{
			if(toReplace instanceof ParsedStatementBlock)
				theContents = (ParsedStatementBlock) toReplace;
			else
				throw new IllegalArgumentException("Cannot replace the block of a synchronized statement with "
					+ toReplace.getClass().getSimpleName());
		}
		else
			throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public String toString()
	{
		return new StringBuilder("synchronized(").append(theSyncItem).append(")\n").append(theContents).toString();
	}
}
