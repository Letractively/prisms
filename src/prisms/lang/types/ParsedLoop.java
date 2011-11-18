/*
 * ParsedLoop.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import java.util.ArrayList;

import prisms.lang.ParsedItem;

/** Represents a loop */
public class ParsedLoop extends ParsedItem<Object>
{
	private ParsedItem<?> [] theInits;

	private ParsedItem<Boolean> theCondition;

	private ParsedItem<?> [] theIncrements;

	private ParsedItem<?> [] theContents;

	private boolean isPreCondition;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		prisms.lang.ParseMatch conditionMatch = getStored("condition");
		if(conditionMatch != null)
			theCondition = parser.parseStructures(this, conditionMatch)[0];
		ArrayList<ParsedItem<?>> inits = new ArrayList<ParsedItem<?>>();
		ArrayList<ParsedItem<?>> incs = new ArrayList<ParsedItem<?>>();
		ArrayList<ParsedItem<?>> cont = new ArrayList<ParsedItem<?>>();

		boolean hasContent = false;
		for(prisms.lang.ParseMatch m : match.getParsed())
		{
			if("condition".equals(m.config.get("storeAs")))
				isPreCondition = !hasContent;
			else if("init".equals(m.config.get("storeAs")))
				inits.add(parser.parseStructures(this, m)[0]);
			else if("increment".equals(m.config.get("storeAs")))
				incs.add(parser.parseStructures(this, m)[0]);
			else if("content".equals(m.config.get("storeAs")))
			{
				hasContent = true;
				cont.add(parser.parseStructures(this, m)[0]);
			}
			else if("contentStart".equals(m.config.get("storeAs")))
				hasContent = true;
		}
		theInits = inits.toArray(new ParsedItem [inits.size()]);
		theIncrements = incs.toArray(new ParsedItem [incs.size()]);
		theContents = cont.toArray(new ParsedItem [cont.size()]);
	}

	/**
	 * @return Whether this loop's condition should be checked before the first execution of the
	 *         contents
	 */
	public boolean isPreCondition()
	{
		return isPreCondition;
	}

	/** @return The condition determining when the loop will stop executing */
	public ParsedItem<Boolean> getCondition()
	{
		return theCondition;
	}

	/** @return The set of statements to run before starting the loop */
	public ParsedItem<?> [] getInits()
	{
		return theInits;
	}

	/** @return The set of statements to run in between execution of the loop's contents */
	public ParsedItem<?> [] getIncrements()
	{
		return theIncrements;
	}

	/** @return The set of statements to run in the loop */
	public ParsedItem<?> [] getContents()
	{
		return theContents;
	}

	@Override
	public prisms.lang.EvaluationResult<Object> evaluate(prisms.lang.EvaluationEnvironment env,
		boolean asType, boolean withValues) throws prisms.lang.EvaluationException
	{
		// TODO Auto-generated method stub
	}
}
