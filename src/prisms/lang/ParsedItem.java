/*
 * ParseStruct.java Created Nov 10, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

import java.util.ArrayList;

/** A basic syntax structure representing the final output of the {@link PrismsParser} */
public abstract class ParsedItem
{
	private PrismsParser theParser;

	private ParsedItem theParent;

	private ParseMatch theMatch;

	/**
	 * Parses this structure type's data, including operands. Extensions must be aware that this method may be called with an incomplete
	 * match structure. Exceptions should NOT be thrown from this method for incomplete data. Instead, as much of the structure of this item
	 * should be parsed and stored as is present in the match and the rests should be left null.
	 * 
	 * @param parser The parser that is parsing this structure
	 * @param parent The parent structure
	 * @param match The parse match that this structure will represent
	 * @throws ParseException If parsing or syntactical validation fails
	 */
	public void setup(PrismsParser parser, ParsedItem parent, ParseMatch match) throws ParseException {
		theParser = parser;
		theParent = parent;
		theMatch = match;
	}

	/** @return The parser that parsed this structure */
	public PrismsParser getParser() {
		return theParser;
	}

	/** @return This structure's parent */
	public ParsedItem getParent() {
		return theParent;
	}

	/** @return The root of this structure */
	public ParseStructRoot getRoot() {
		ParsedItem ret = this;
		while(ret != null && !(ret instanceof ParseStructRoot))
			ret = ret.theParent;
		return (ParseStructRoot) ret;
	}

	/** @return The parsed match that this structure was parsed from */
	public ParseMatch getMatch() {
		return theMatch;
	}

	/**
	 * @param name The name of the stored match to get
	 * @return The match within this structure stored as the given name
	 */
	public ParseMatch getStored(String name) {
		for(ParseMatch match : matches())
			if(name.equals(match.config.get("storeAs")))
				return match;
		return null;
	}

	/**
	 * @param names The "storeAs" attributes to match against
	 * @return Parse match siblings under this item matching one of the given storeAs attributes
	 */
	public ParseMatch [] getAllStored(final String... names) {
		ArrayList<ParseMatch> ret = new ArrayList<>();
		for(ParseMatch match : matches()) {
			String storeAs = match.config.get("storeAs");
			if(storeAs != null && prisms.util.ArrayUtils.contains(names, storeAs))
				ret.add(match);
		}
		return ret.toArray(new ParseMatch[ret.size()]);
	}

	/**
	 * @param name The name of the stored match to get the index of
	 * @return The number of matches that have to be recursively traversed before arriving at the given stored match
	 */
	public int getDeepStoreIndex(String name) {
		return getDeepStoreIndex(theMatch, name);
	}

	private int getDeepStoreIndex(ParseMatch match, String name) {
		if(name.equals(match.config.get("storeAs")))
			return 0;
		if(match.getParsed() == null)
			return -1;
		int ret = 1;
		for(ParseMatch sub : match.getParsed())
		{
			int dsi = getDeepStoreIndex(sub, name);
			if(dsi > 0)
				return ret + dsi;
			ret += getDeepCount(sub);
		}
		return -1;
	}

	private int getDeepCount(ParseMatch match) {
		int ret = 1;
		if(match.getParsed() != null)
			for(ParseMatch sub : match.getParsed())
				ret += getDeepCount(sub);
		return ret;
	}

	/**
	 * @return An iterable to iterate through all matches in this ParsedItem. Matches from contained items are not returned. E.g. for a
	 *         method call, the content of the parameters to the call would not be included since they are not part of this item's
	 *         configuration.
	 */
	public Iterable<ParseMatch> matches() {
		return new Iterable<ParseMatch>() {
			@Override
			public java.util.Iterator<ParseMatch> iterator() {
				return new java.util.Iterator<ParseMatch>() {
					private ArrayList<ParseMatch> thePath;
					private prisms.util.IntList thePathChildIndex;
					private ParseMatch theReturn;

					{
						thePath = new ArrayList<>();
						thePathChildIndex = new prisms.util.IntList();
						theReturn = getMatch();
						thePath.add(theReturn);
						thePathChildIndex.add(0);
					}

					@Override
					public boolean hasNext() {
						if(theReturn != null)
							return true;
						theReturn = findNext();
						return theReturn != null;
					}

					@Override
					public ParseMatch next() {
						if(theReturn == null && !hasNext())
							throw new java.util.NoSuchElementException();
						ParseMatch ret = theReturn;
						theReturn = null;
						return ret;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}

					private ParseMatch findNext() {
						while(!thePath.isEmpty()) {
							int pathIdx = thePath.size() - 1;
							ParseMatch terminal = thePath.get(pathIdx);
							int childIndex = thePathChildIndex.get(pathIdx);
							if(terminal.getParsed() != null && childIndex < terminal.getParsed().length) {
								ParseMatch ret = terminal.getParsed()[childIndex];
								thePathChildIndex.set(pathIdx, childIndex + 1);
								// Return children only within the structure of this ParsedItem's configuration
								// e.g. Return all matches within a for loop structure, but don't return any structure for the arguments,
								// body, etc. TODO This doesn't work.
								// In certain cases, the config of the parent may be the same as that of the child (<op min="0">)
								if(terminal.config == ret.config
									|| prisms.util.ArrayUtils.contains(terminal.config.subConfigs(), ret.config)) {
									thePath.add(ret);
									thePathChildIndex.add(0);
									return ret;
								}
							} else {
								thePath.remove(pathIdx);
								thePathChildIndex.remove(pathIdx);
							}
						}
						return null;
					}
				};
			}
		};
	}

	/**
	 * @return All parsed items that this item is dependent on, in order of their appearance in the text. If this item's match is
	 *         incomplete, the returned array may end with one or more nulls in place of dependents that this item requires to evaluate
	 *         properly, but were not present in the text.
	 */
	public abstract ParsedItem [] getDependents();

	/**
	 * Replaces one dependent with a different value
	 * 
	 * @param dependent The dependent to replace
	 * @param toReplace The value to replace the given dependent with
	 * @throws IllegalArgumentException If the given dependent cannot be replaced or the given replacement cannot replace the dependent
	 */
	public abstract void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException;

	/**
	 * Validates or evaluates this expression. This method should never be called if this item's match is incomplete.
	 * 
	 * @param env The evaluation environment to execute in
	 * @param asType Whether the result should be a type if possible
	 * @param withValues Whether to evaluate the value of the expression, or simply validate it and return its type
	 * @return The result of the expression
	 * @throws EvaluationException If an error occurs evaluating the expression
	 */
	public abstract EvaluationResult evaluate(EvaluationEnvironment env, boolean asType, boolean withValues) throws EvaluationException;
}
