/*
 * ParsedIfStatement.java Created Nov 17, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.ParsedItem;

/** Represents an if/else if/.../else structure */
public class ParsedIfStatement extends ParsedItem<Object>
{
	private ParsedItem<Boolean> [] theConditions;

	private ParsedItem<?> [][] theContents;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem<?> parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		java.util.ArrayList<ParsedItem<Boolean>> conditions = new java.util.ArrayList<ParsedItem<Boolean>>();
		java.util.ArrayList<ParsedItem<?>> contents = new java.util.ArrayList<ParsedItem<?>>();
		for(prisms.lang.ParseMatch m : match.getParsed())
		{
			if("condition".equals(m.config.get("storeAs")))
			{
				if(!conditions.isEmpty())
				{
					theContents = prisms.util.ArrayUtils.add(theContents,
						contents.toArray(new ParsedItem [contents.size()]));
					contents.clear();
				}
				conditions.add(parser.parseStructures(this, m)[0]);
			}
			else if("terminal".equals(m.config.get("storeAs")))
			{
				theContents = prisms.util.ArrayUtils.add(theContents,
					contents.toArray(new ParsedItem [contents.size()]));
				contents.clear();
			}
			else if("content".equals(m.config.get("storeAs")))
				contents.add(parser.parseStructures(this, m)[0]);
		}
		theConditions = conditions.toArray(new ParsedItem [conditions.size()]);
		theContents = prisms.util.ArrayUtils.add(theContents,
			contents.toArray(new ParsedItem [contents.size()]));
	}

	/** @return The conditions in this if statement as if/else if/else if/... */
	public ParsedItem<?> [] getConditions()
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
	public ParsedItem<?> [] getContents(int condition)
	{
		return theContents[condition];
	}

	@Override
	public prisms.lang.EvaluationResult<Object> evaluate(prisms.lang.EvaluationEnvironment env,
		boolean asType, boolean withValues) throws prisms.lang.EvaluationException
	{
		// TODO Auto-generated method stub
	}
}
