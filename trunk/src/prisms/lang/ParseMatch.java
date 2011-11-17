/*
 * ParseMatch.java Created Nov 11, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

import prisms.arch.PrismsConfig;

/** Represents a piece of text that matches an operator or entity configuration */
public class ParseMatch
{
	/** The configuration that the parsed text matches */
	public final PrismsConfig config;

	/** The section of text that matched this match's configuration */
	public final String text;

	/** The index within the full command where this match was made */
	public final int index;

	private final ParseMatch [] parsed;

	ParseMatch(PrismsConfig cnfg, String txt, int idx, ParseMatch [] _parsed)
	{
		config = cnfg;
		text = txt;
		index = idx;
		parsed = _parsed;
	}

	/** @return Details on the match */
	public ParseMatch [] getParsed()
	{
		return parsed;
	}

	@Override
	public String toString()
	{
		if(parsed == null)
			return text;
		else
		{
			StringBuilder ret = new StringBuilder();
			if(parsed.length > 1)
				ret.append('(');
			for(ParseMatch m : parsed)
				ret.append(m.toString());
			if(parsed.length > 1)
				ret.append(')');
			return ret.toString();
		}
	}
}
