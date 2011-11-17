/*
 * ParsedIfStatement.java Created Nov 17, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.ParseStruct;

/** Represents an if/else if/.../else structure */
public class ParsedIfStatement extends ParseStruct
{
	private ParseStruct [] theConditions;

	private ParseStruct [][] theContents;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		java.util.ArrayList<ParseStruct> conditions = new java.util.ArrayList<ParseStruct>();
		java.util.ArrayList<ParseStruct> contents = new java.util.ArrayList<ParseStruct>();
		for(prisms.lang.ParseMatch m : match.getParsed())
		{
			if("condition".equals(m.config.get("storeAs")))
			{
				if(!conditions.isEmpty())
				{
					theContents = prisms.util.ArrayUtils.add(theContents,
						contents.toArray(new ParseStruct [contents.size()]));
					contents.clear();
				}
				conditions.add(parser.parseStructures(this, m)[0]);
			}
			else if("terminal".equals(m.config.get("storeAs")))
			{
				theContents = prisms.util.ArrayUtils.add(theContents,
					contents.toArray(new ParseStruct [contents.size()]));
				contents.clear();
			}
			else if("content".equals(m.config.get("storeAs")))
				contents.add(parser.parseStructures(this, m)[0]);
		}
		theConditions = conditions.toArray(new ParseStruct [conditions.size()]);
		theContents = prisms.util.ArrayUtils.add(theContents,
			contents.toArray(new ParseStruct [contents.size()]));
	}

	/** @return The conditions in this if statement as if/else if/else if/... */
	public ParseStruct [] getConditions()
	{
		return theConditions;
	}

	/** @return Whether this if statement has a terminal block (an else without an if) */
	public boolean hasTerminal()
	{
		return theContents.length > theConditions.length;
	}

	/**
	 * @param condition The index of the condition to get the contents for, or the length of the
	 *        conditions array to get the contents of the terminal block
	 * @return The contents of the condition or terminal block specified
	 */
	public ParseStruct [] getContents(int condition)
	{
		return theContents[condition];
	}
}
