/*
 * ParsedLoop.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import java.util.ArrayList;

import prisms.lang.ParseStruct;

/** Represents a loop */
public class ParsedLoop extends ParseStruct
{
	private ParseStruct [] theInits;

	private ParseStruct theCondition;

	private ParseStruct [] theIncrements;

	private ParseStruct [] theContents;

	private boolean isPreCondition;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		prisms.lang.ParseMatch conditionMatch = getStored("condition");
		if(conditionMatch != null)
			theCondition = parser.parseStructures(this, conditionMatch)[0];
		ArrayList<ParseStruct> inits = new ArrayList<ParseStruct>();
		ArrayList<ParseStruct> incs = new ArrayList<ParseStruct>();
		ArrayList<ParseStruct> cont = new ArrayList<ParseStruct>();

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
		theInits = inits.toArray(new ParseStruct [inits.size()]);
		theIncrements = incs.toArray(new ParseStruct [incs.size()]);
		theContents = cont.toArray(new ParseStruct [cont.size()]);
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
	public ParseStruct getCondition()
	{
		return theCondition;
	}

	/** @return The set of statements to run before starting the loop */
	public ParseStruct [] getInits()
	{
		return theInits;
	}

	/** @return The set of statements to run in between execution of the loop's contents */
	public ParseStruct [] getIncrements()
	{
		return theIncrements;
	}

	/** @return The set of statements to run in the loop */
	public ParseStruct [] getContents()
	{
		return theContents;
	}
}
