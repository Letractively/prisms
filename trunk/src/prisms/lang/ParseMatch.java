/*
 * ParseMatch.java Created Nov 11, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

import prisms.arch.PrismsConfig;

/** Represents a piece of text that matches an operator or entity configuration */
public class ParseMatch implements Iterable<ParseMatch>
{
	/** The configuration that the parsed text matches */
	public final PrismsConfig config;

	/** The section of text that matched this match's configuration */
	public final String text;

	/** The index within the full command where this match was made */
	public final int index;

	private final ParseMatch [] parsed;

	private boolean isComplete;

	private String theError;

	ParseMatch(PrismsConfig cnfg, String txt, int idx, ParseMatch [] _parsed, boolean complete, String error)
	{
		config = cnfg;
		text = txt;
		index = idx;
		parsed = _parsed;
		isComplete = complete;
		theError = error;
	}

	/** @return Details on the match */
	public ParseMatch [] getParsed()
	{
		return parsed;
	}

	/** @return Whether this match parsed completely. Matches may be returned incompletely parsed. */
	public boolean isComplete()
	{
		if(!isComplete)
			return false;
		if(parsed == null || parsed.length == 0)
			return true;
		return parsed[parsed.length - 1].isComplete();
	}

	/** @return Whether this parse match (and not one of its children) is the source of an error */
	public boolean isThisError() {
		return theError != null;
	}

	/** @return The error message that prevented this match from being made, or null if the match was successful */
	public String getError()
	{
		if(theError != null)
			return theError;
		if(parsed == null || parsed.length == 0)
			return null;
		for(ParseMatch sub : parsed) {
			String err = sub.getError();
			if(err != null)
				return err;
		}
		return null;
	}

	/** @return The match in this structure that contains this match's error */
	public ParseMatch getErrorMatch()
	{
		if(theError != null)
			return this;
		if(parsed == null || parsed.length == 0)
			return null;
		for(ParseMatch sub : parsed) {
			ParseMatch err = sub.getErrorMatch();
			if(err != null)
				return err;
		}
		return null;
	}

	/** Implements a depth-first iteration over this match's structure. The first match returned will be {@code this}. */
	@Override
	public java.util.Iterator<ParseMatch> iterator()
	{
		return new java.util.Iterator<ParseMatch>()
			{
			private boolean hasReturnedSelf;

			private int theIterIndex;

			private java.util.Iterator<ParseMatch> theChildIter;

			private boolean calledNext = true;

			@Override
			public boolean hasNext()
			{
				if(!calledNext)
					return true;
				if(!hasReturnedSelf)
				{
					calledNext = false;
					return true;
				}
				if(getParsed() == null)
					return false;
				boolean ret = theChildIter != null && theChildIter.hasNext();
				while(!ret && theIterIndex < getParsed().length)
				{
					theChildIter = getParsed()[theIterIndex].iterator();
					theIterIndex++;
					ret = theChildIter != null && theChildIter.hasNext();
				}
				if(ret)
					calledNext = false;
				return ret;
			}

			@Override
			public ParseMatch next()
			{
				calledNext = true;
				if(!hasReturnedSelf)
				{
					hasReturnedSelf = true;
					return ParseMatch.this;
				}
				if(theChildIter == null)
					throw new java.util.NoSuchElementException();
				return theChildIter.next();
			}

			@Override
			public void remove()
			{
				throw new UnsupportedOperationException();
			}
			};
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
