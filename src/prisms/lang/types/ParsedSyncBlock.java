package prisms.lang.types;

/** Represents a synchronized block */
public class ParsedSyncBlock extends prisms.lang.ParsedItem
{
	private prisms.lang.ParsedItem theSyncItem;

	private ParsedStatementBlock theContents;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theSyncItem = parser.parseStructures(this, getStored("syncItem"))[0];
		theContents = (ParsedStatementBlock) parser.parseStructures(this, getStored("content"))[0];
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		prisms.lang.EvaluationResult syncItemRes = theSyncItem.evaluate(env, false, withValues);
		if(!syncItemRes.isValue())
			throw new prisms.lang.EvaluationException(syncItemRes.typeString() + " cannot be resolved to a variable",
				this, theSyncItem.getMatch().index);
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
	public prisms.lang.ParsedItem getSyncItem()
	{
		return theSyncItem;
	}

	/** @return The contents that will be executed while the sync item is synchronized */
	public ParsedStatementBlock getContents()
	{
		return theContents;
	}
}
