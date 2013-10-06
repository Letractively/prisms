/* PrismsParser.java Created Nov 10, 2011 by Andrew Butler, PSL */
package prisms.lang;

import java.io.Reader;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import prisms.arch.PrismsConfig;
import prisms.util.ArrayUtils;
import prisms.util.DualKey;
import prisms.util.PrismsUtils;

/** Parses syntactical structures from text without regard to semantics using an XML-encoded grammar file */
public class PrismsParser {
	private static final Logger log = Logger.getLogger(PrismsParser.class);

	/** Specifies that the parser is looking only for completely parsed statements */
	static final int COMPLETE_ONLY = 2;

	/** Specifies that incomplete statements may be returned unless the missing piece is optional */
	static final int COMPLETE_OPTIONAL = 1;

	/** Specifies that the parser will accept incomplete statements */
	static final int COMPLETE_ANY = 0;

	/** Specifies that an error may the thrown from parsing code if the parsing state knows what is wrong with the parsed text */
	static final int COMPLETE_ERROR = -1;

	class ParseSessionCache {
		private ParseMatch none = new ParseMatch(null, null, 0, null, false, "NO MATCH!");

		Map<DualKey<Integer, String>, ParseMatch> theFound;

		java.util.Set<DualKey<Integer, String>> theFinding;

		ParseSessionCache() {
			theFound = new java.util.HashMap<>();
			theFinding = new java.util.HashSet<>();
		}

		void addFinding(int position, String type) {
			theFinding.add(new DualKey<>(Integer.valueOf(position), type));
		}

		void addFound(int position, String type, ParseMatch found) {
			if(found == null)
				found = none;
			theFound.put(new DualKey<>(Integer.valueOf(position), type), found);
			if(type == null && found != none)
				theFound.put(new DualKey<>(Integer.valueOf(position), found.config.get("name")), found);
		}

		boolean isFound(int position, String type) {
			return theFound.containsKey(new DualKey<>(Integer.valueOf(position), type));
		}

		ParseMatch getFound(int position, String type) {
			ParseMatch ret = theFound.get(new DualKey<>(Integer.valueOf(position), type));
			if(ret == none)
				return null;
			return ret;
		}

		boolean isFinding(int position, String type) {
			return theFinding.contains(new DualKey<>(Integer.valueOf(position), type));
		}

		void stopFinding(int position, String type) {
			theFinding.remove(new DualKey<>(Integer.valueOf(position), type));
		}
	}

	private java.util.List<PrismsConfig> theOperators;

	private Map<String, PrismsConfig> theOpsByName;

	private String [] theIgnorables;

	private java.util.List<String> theTerminators;

	private boolean isValidated;

	/** Creates a parser */
	public PrismsParser() {
		theOperators = new java.util.ArrayList<>();
		theOpsByName = new java.util.HashMap<>();
		theIgnorables = new String[0];
		theTerminators = new java.util.ArrayList<>();
		addTerminator("\n");
		addTerminator(";");
	}

	/** @param terminator The terminator string to accept as a boundary between successive matches */
	public void addTerminator(String terminator) {
		theTerminators.add(terminator);
	}

	/** @param terminator The terminator string to not accept for a boundary between successive matchess */
	public void removeTerminator(String terminator) {
		theTerminators.remove(terminator);
	}

	/** Clears this perser's terminators so that a clean set can be added */
	public void clearTerminators() {
		theTerminators.clear();
	}

	/** @return All terminator strings that are accepted by this parser as boundaries between successive matches */
	public String [] getTerminators() {
		return theTerminators.toArray(new String[theTerminators.size()]);
	}

	/**
	 * Configures the parser
	 * 
	 * @param config The configuration to configure the types of structures the parser can parse
	 */
	public void configure(prisms.arch.PrismsConfig config) {
		for(PrismsConfig entity : config.subConfigs())
			insertOperator(entity);
	}

	void insertOperator(PrismsConfig op) {
		validateOperator(op);
		int pri = op.getInt("order", 0);
		int min = 0, max = theOperators.size();
		while(max > min) {
			int mid = (min + max) / 2;
			if(mid == theOperators.size()) {
				min = mid;
				break;
			}
			int testPri = theOperators.get(mid).getInt("order", 0);
			if(pri > testPri)
				min = mid + 1;
			else
				max = mid;
		}
		theOperators.add(min, op);
		theOpsByName.put(op.get("name"), op);
		if(op.is("ignorable", false))
			theIgnorables = ArrayUtils.add(theIgnorables, op.get("name"));
	}

	void validateOperator(PrismsConfig op) throws IllegalArgumentException {
		if(op.get("name") == null)
			throw new IllegalArgumentException("No name in " + op.getName() + ":\n" + op);
		for(PrismsConfig sub : op.subConfigs()) {
			switch (sub.getName()) {
			case "name":
				if(theOpsByName.containsKey(sub.getValue()))
					throw new IllegalArgumentException("Duplicate operators named \"" + sub.getValue() + "\"");
				break;
			case "order":
			case "priority":
			case "ignorews":
			case "ignorable":
				break;
			case "impl":
				try {
					Class.forName(sub.getValue());
				} catch(Exception e) {
					throw new IllegalArgumentException(op.getName() + " \"" + op.get("name") + "\"'s implementation cannot be found: "
						+ sub.getValue(), e);
				}
				break;
			default:
				try {
					validateParseStructure(sub);
				} catch(IllegalArgumentException e) {
					throw new IllegalArgumentException(op.getName() + " \"" + op.get("name") + "\":", e);
				}
			}
		}
	}

	static void validateParseStructure(PrismsConfig config) throws IllegalArgumentException {
		switch (config.getName()) {
		case "literal":
			if(config.get("pattern") != null) {
				if(config.get("pattern").length() == 0)
					throw new IllegalArgumentException("zero-length pattern in literal");
				if(config.getValue() != null && config.getValue().length() > 0)
					throw new IllegalArgumentException("pattern and value both have content for literal");
			} else if(config.getValue() == null || config.getValue().length() == 0)
				throw new IllegalArgumentException("No pattern or value for literal");
			break;
		case "charset":
			if(config.get("pattern") == null)
				throw new IllegalArgumentException("charset has no pattern");
			else {
				try {
					Pattern.compile(config.get("pattern"));
				} catch(Exception e) {
					throw new IllegalArgumentException("Could not compile charset pattern \"" + config.get("pattern") + "\"", e);
				}
			}
			break;
		case "whitespace":
			if(config.get("type") != null && !"forbid".equals(config.get("type")))
				throw new IllegalArgumentException("Invalid \"type\" attribute for whitespace: " + config.get("type"));
			break;
		case "option":
			for(PrismsConfig sub : config.subConfigs()) {
				switch (sub.getName()) {
				case "min":
				case "max":
					break;
				default:
					validateParseStructure(sub);
				}
			}
			break;
		case "select":
			if(config.subConfigs().length == 0)
				throw new IllegalArgumentException("No options for select");
			if(config.subConfigs().length != config.subConfigs("option").length)
				throw new IllegalArgumentException("select may only have option contents");
			for(PrismsConfig sub : config.subConfigs())
				validateParseStructure(sub);
			break;
		case "op":
			for(PrismsConfig sub : config.subConfigs()) {
				switch (sub.getName()) {
				case "type":
				case "storeAs":
					break;
				default:
					validateParseStructure(sub);
				}
			}
		}
	}

	/**
	 * Checks this parser's configuration for errors
	 * 
	 * @throws IllegalArgumentException If an error is found in the configuration
	 */
	public void validateConfig() throws IllegalArgumentException {
		if(isValidated)
			return;
		for(PrismsConfig op : theOperators) {
			try {
				checkReferences(op);
			} catch(IllegalArgumentException e) {
				throw new IllegalArgumentException(op.getName() + " \"" + op.get("name") + "\":", e);
			}
		}
		java.util.Collections.sort(theOperators, new java.util.Comparator<PrismsConfig>() {
			@Override
			public int compare(PrismsConfig o1, PrismsConfig o2) {
				return o2.getInt("priority", 0) - o1.getInt("priority", 0);
			}
		});
		isValidated = true;
	}

	void checkReferences(PrismsConfig op) {
		if("op".equals(op.getName()) && op.get("type") != null) {
			String [] types = op.get("type").split("\\|");
			for(String type : types) {
				if(!theOpsByName.containsKey(type.trim()))
					throw new IllegalArgumentException("Type \"" + type + "\" not recognized");
			}
		}
		for(PrismsConfig sub : op.subConfigs())
			checkReferences(sub);
	}

	/**
	 * Parses crude structure matches from text
	 * 
	 * @param cmd The command to parse
	 * @return The structure matches parsed
	 * @throws ParseException If parsing fails
	 */
	public synchronized ParseMatch [] parseMatches(String cmd) throws ParseException {
		validateConfig();
		if(cmd.length() == 0)
			throw new ParseException("No input given", cmd, 0);
		int index = 0;
		StringBuilder str = new StringBuilder(cmd);
		ParseMatch [] parseMatches = new ParseMatch[0];
		while(index < str.length()) {
			ParseMatch parseMatch = parseMatch(str, index, new ParseSessionCache());
			if(parseMatch == null)
				throw new ParseException("Syntax error", cmd, index);
			index += parseMatch.text.length();
			if(parseMatch.getError() != null) {
				if(parseMatch.isComplete() || index < str.length()) {
					ParseMatch op = parseMatch;
					String name = null;
					while(op.getParsed() != null && op.getParsed().length > 0) {
						if(op.config.get("name") != null)
							name = op.config.get("name");
						op = op.getParsed()[op.getParsed().length - 1];
					}
					throw new ParseException(name + ": " + parseMatch.getError(), cmd, parseMatch.getErrorMatch().index);
				}
				parseMatches = ArrayUtils.add(parseMatches, parseMatch);
				break;
			}

			ParseMatch terminator;
			if(index < str.length()) {
				terminator = parseTerminator(str, index);
				if(terminator == null)
					throw new ParseException("Terminator expected", cmd, index);
				index += terminator.text.length();
				ParseMatch [] ignores = null;
				if(index < str.length())
					ignores = parseIgnorables(str, index, new ParseSessionCache(), true);
				ParseMatch [] subs = parseMatch.getParsed();
				String text = parseMatch.text + terminator.text;
				subs = ArrayUtils.add(subs, terminator);
				if(ignores != null) {
					subs = ArrayUtils.addAll(subs, ignores);
					for(ParseMatch ignore : ignores) {
						text += ignore.text;
						index += ignore.text.length();
					}
				}
				parseMatch = new ParseMatch(parseMatch.config, text, parseMatch.index, subs, true, null);
			}
			parseMatches = ArrayUtils.add(parseMatches, parseMatch);
		}
		return parseMatches;
	}

	/**
	 * Parses logical syntactic structures from crude matches
	 * 
	 * @param parent The parent parse structure
	 * @param matches The matches parsed from the command
	 * @return The syntax structures parsed from the matches
	 * @throws ParseException If parsing fails
	 */
	public synchronized ParsedItem [] parseStructures(ParsedItem parent, ParseMatch... matches) throws ParseException {
		if(parent == null)
			throw new IllegalArgumentException("Parent required for parsed structures.  Use " + ParseStructRoot.class.getSimpleName() + ".");
		ParsedItem [] ret = new ParsedItem[matches.length];
		for(int i = 0; i < ret.length; i++) {
			ParseMatch implMatch = matches[i];
			if(implMatch == null)
				continue;
			Class<? extends ParsedItem> implClass = null;
			while(implClass == null) {
				try {
					implClass = implMatch.config.getClass("impl", ParsedItem.class);
				} catch(ClassNotFoundException e) {
					throw new ParseException("Implementation not found for " + implMatch.config, e, parent.getRoot().getFullCommand(), -1);
				} catch(ClassCastException e) {
					throw new ParseException("Implementation not an instance of ParseStruct for " + implMatch.config, e, parent.getRoot()
						.getFullCommand(), -1);
				}
				if(implClass == null) {
					if(implMatch.getParsed() != null && implMatch.getParsed().length == 1)
						implMatch = implMatch.getParsed()[0];
					else if("entity".equals(implMatch.config.getName()) || "operator".equals(implMatch.config.getName()))
						throw new ParseException("No implementation configured for " + implMatch.config, parent.getRoot().getFullCommand(),
							-1);
					else
						break;
				}
			}
			if(implClass != null) {
				try {
					ret[i] = implClass.newInstance();
				} catch(InstantiationException e) {
					throw new ParseException("Could not instantiate implementation for " + matches[i].config, e, parent.getRoot()
						.getFullCommand(), -1);
				} catch(IllegalAccessException e) {
					throw new ParseException("Could not instantiate implementation for " + matches[i].config, e, parent.getRoot()
						.getFullCommand(), -1);
				}
				ret[i].setup(this, parent, implMatch);
			}
		}
		return ret;
	}

	ParseMatch parseMatch(StringBuilder sb, int index, ParseSessionCache cache) {
		return getBestMatch(sb, index, cache, false);
	}

	ParseMatch getBestMatch(StringBuilder sb, int index, ParseSessionCache cache, boolean useCache, String... types) {
		ParseMatch ret = null;
		boolean betterMatch = true;
		boolean firstRound = true;
		while(betterMatch) {
			betterMatch = false;
			if(types.length > 0) {
				for(String type : types) {
					if(cache.isFinding(index, type))
						continue;
					ParseMatch match;
					if(useCache && cache.isFound(index, type))
					{
						if(firstRound)
							match = cache.getFound(index, type);
						else
							continue;
					}
					else {
						cache.addFinding(index, type);
						try {
							match = parseTypedMatch(sb, index, cache, theOpsByName.get(type));
						} finally {
							cache.stopFinding(index, type);
						}
						if(match != null && match.isComplete()) {
							ParseMatch cached = cache.getFound(index, type);
							if(isBetter(cached, match))
								cache.addFound(index, type, match);
						}
					}
					if(match != null && (ret == null || !ret.isComplete() || match.isComplete()) && isBetter(ret, match)) {
						betterMatch = true;
						ret = match;
					}
				}
			} else {
				if(useCache && firstRound && cache.isFound(index, null))
					return cache.getFound(index, null);
				if(cache.isFinding(index, null))
					return cache.getFound(index, null);
				cache.addFinding(index, null);
				try {
					ParseMatch bestComplete = cache.getFound(index, null);
					for(PrismsConfig op : theOperators) {
						if(op.getInt("priority", 0) < 0)
							break;
						String name = op.get("name");
						if(cache.isFinding(index, name))
							continue;
						ParseMatch match;
						if(useCache && firstRound && cache.isFound(index, name))
							match = cache.getFound(index, name);
						else {
							cache.addFinding(index, name);
							try {
								match = parseTypedMatch(sb, index, cache, op);
							} finally {
								cache.stopFinding(index, name);
							}
						}
						if(match != null && match.isComplete() && isBetter(bestComplete, match)) {
							bestComplete = match;
							cache.addFound(index, null, match);
						}
						if(match != null && isBetter(ret, match)) {
							betterMatch = true;
							ret = match;
						}
					}
				} finally {
					cache.stopFinding(index, null);
				}
			}
			firstRound = false;
		}
		return ret;
	}

	/** @return Whether o2 is better than o1 */
	static boolean isBetter(ParseMatch o1, ParseMatch o2) {
		if(o2 == null)
			return false;
		if(o1 == null)
			return true;
		int len1 = nonErrorLength(o1);
		int len2 = nonErrorLength(o2);
		if(len1 == len2)
			return !o1.isComplete() && o2.isComplete();
		return len2 > len1;
	}

	static int nonErrorLength(ParseMatch match) {
		if(match.config.getName().equals("whitespace") || match.isThisError())
			return 0;
		else if(match.getError() == null)
			return match.text.length();
		else if(match.getParsed() == null || match.getParsed().length == 0)
			return 0;
		else {
			int ret = 0;
			for(ParseMatch sub : match.getParsed()) {
				ret += nonErrorLength(sub);
				if(sub.getError() != null)
					break;
			}
			return ret;
		}
	}

	ParseMatch parseTypedMatch(StringBuilder sb, int index, ParseSessionCache cache, PrismsConfig op) {
		final int startIndex = index;
		ParseMatch [] subMatches = new ParseMatch[0];

		/* These variables serve the purpose of keeping incomplete optional content around after it's parsed in case it really is the best
		 * match. */
		ParseMatch badOption = null;
		boolean badOptionOld = true;

		ParseMatch [] ignores = null;
		for(PrismsConfig sub : op.subConfigs()) {
			if(badOption != null && badOptionOld)
				badOption = null;
			ignores = null;
			ParseMatch match;
			switch (sub.getName()) {
			case "name":
			case "order":
			case "priority":
			case "impl":
			case "min":
			case "max":
			case "ignorews":
			case "ignorable":
				continue;
			case "literal":
				badOptionOld = true;
				ignores = parseIgnorables(sb, index, cache, !sub.is("ignorews", false));
				if(ignores != null)
					for(ParseMatch ig : ignores)
						index += ig.text.length();
				match = parseLiteral(sb, index, sub);
				break;
			case "charset":
				badOptionOld = true;
				ignores = parseIgnorables(sb, index, cache, !sub.is("ignorews", false));
				if(ignores != null)
					for(ParseMatch ig : ignores)
						index += ig.text.length();
				match = parseCharset(sb, index, sub);
				break;
			case "whitespace":
				badOptionOld = true;
				match = parseWhiteSpace(sb, index);
				if(sub.get("type") != null) // forbid
				{
					if(match != null)
						match = new ParseMatch(sub, match.text, match.index, null, true, "White space unexpected");
					else
						continue;
				} else if(match == null)
					match = new ParseMatch(sub, "", index, null, false, "White space expected");
				break;
			case "option":
			case "forbid":
				badOptionOld = true;
				int min,
				max;
				if(sub.getName().equals("forbid")) {
					min = 0;
					max = 1;
				} else {
					min = sub.getInt("min", 0);
					// If a min is declared and no max, the max is infinite. If no min is declared, then the default max is 1.
					max = sub.getInt("max", sub.get("min") != null ? -1 : 1);
				}
				int count = 0;
				match = null;
				int preOptionIndex = index;
				ParseMatch [] optionMatches = null;
				while(max < 0 || count < max) {
					match = parseTypedMatch(sb, index, cache, sub);
					if(match == null || !match.isComplete() || match.getError() != null) {
						if(match != null && badOptionOld) {
							badOption = match;
							badOptionOld = false;
						}
						break;
					}
					optionMatches = ArrayUtils.add(optionMatches, match);
					index += match.text.length();
					count++;
				}
				if(count < min) {
					if(!badOptionOld) {
						if(optionMatches != null)
							for(ParseMatch optMatch : optionMatches)
								subMatches = ArrayUtils.addAll(optMatch.getParsed());
						match = badOption;
					} else {
						String name = sub.get("storeAs");
						if(name == null)
							name = "option";
						match = new ParseMatch(sub, "", index, null, false, "At least " + min + " \"" + name + "\" occurrence"
							+ (min > 1 ? "s" : "") + " expected");
					}
					break; // handle this outside the switch
				}
				if(sub.getName().equals("forbid") && optionMatches != null) {
					match = new ParseMatch(sub, sb.substring(preOptionIndex, index), preOptionIndex, optionMatches, true,
						"Forbidden content present");
					break;
				}
				// For option, add the content and continue the loop
				if(optionMatches != null)
					for(ParseMatch optMatch : optionMatches)
						subMatches = ArrayUtils.addAll(subMatches, optMatch.getParsed());
				continue;
			case "select":
				badOptionOld = true;
				match = null;
				for(PrismsConfig option : sub.subConfigs()) {
					ParseMatch optionMatch = parseTypedMatch(sb, index, cache, option);
					if(optionMatch == null)
						continue;
					if(optionMatch.isComplete()) {
						match = optionMatch;
						break;
					} else if(match == null || optionMatch.text.length() > match.text.length())
						match = optionMatch;
				}
				if(match != null) {
					subMatches = ArrayUtils.addAll(subMatches, match.getParsed());
					for(ParseMatch optMatch : match.getParsed())
						index += optMatch.text.length();
					if(!match.isComplete()) {
						match = subMatches[subMatches.length - 1];
						index -= match.text.length();
						subMatches = ArrayUtils.remove(subMatches, subMatches.length - 1);
					} else
						continue;
				}
				break;
			case "op":
				badOptionOld = true;
				ignores = parseIgnorables(sb, index, cache, !sub.is("ignorews", false));
				if(ignores != null)
					for(ParseMatch ig : ignores)
						index += ig.text.length();
				String [] types;
				if(sub.get("type") != null)
					types = sub.get("type").split("\\|");
				else
					types = new String[0];
				match = getBestMatch(sb, index, cache, true, types);
				if(match != null)
					match = new ParseMatch(sub, match.text, index, new ParseMatch[] {match}, true, null);
				break;
			default:
				throw new IllegalStateException("Unrecognized configuration: \"" + sub.getName() + "\" in " + op.getName()
					+ (op.get("name") == null ? "" : " " + op.get("name")) + ":\n" + op);
			}
			if(match == null || (!match.isComplete() && match.text.length() == 0)) {
				if(badOption != null)
					match = badOption;
			}
			if(match == null || (!match.isComplete() && match.text.length() == 0 && subMatches.length == 0))
				return null;
			if(ignores != null)
				subMatches = ArrayUtils.addAll(subMatches, ignores);
			subMatches = ArrayUtils.add(subMatches, match);
			index = match.index + match.text.length();
			if(!match.isComplete())
				return new ParseMatch(op, sb.substring(startIndex, index), startIndex, subMatches, true, null);
		}
		/*ParseMatch terminator=parseTerminator(sb, index);
		if(terminator!=null){
			subMatches=ArrayUtils.add(subMatches, terminator);
			index+=terminator.text.length();
		}*/
		return new ParseMatch(op, sb.substring(startIndex, index), startIndex, subMatches, true, null);
	}

	private ParseMatch parseTerminator(StringBuilder sb, int index) {
		final int startIndex = index;
		boolean firstRound = true;
		do {
			if(!firstRound)
				index++; // Pass white space character and try again
			firstRound = false;
			for(String term : theTerminators) {
				if(index + term.length() > sb.length())
					continue;
				boolean matches = true;
				for(int i = 0; matches && i < term.length(); i++)
					if(term.charAt(i) != sb.charAt(index + i))
						matches = false;
				if(matches)
					return new ParseMatch(new PrismsConfig.DefaultPrismsConfig("config", null, null), sb.substring(startIndex,
						index + term.length()), startIndex, null, true, null);
			}
		} while(index < sb.length() && Character.isWhitespace(sb.charAt(index)));
		return null;
	}

	private ParseMatch [] parseIgnorables(StringBuilder sb, int index, ParseSessionCache cache, boolean withWS) {
		ParseMatch [] ret = null;
		ParseMatch match;
		do {
			match = null;
			match = getBestMatch(sb, index, cache, true, theIgnorables);
			if(match == null && withWS)
				match = parseWhiteSpace(sb, index);
			if(match != null) {
				ret = ArrayUtils.add(ret, match);
				index += match.text.length();
			}
		} while(match != null);
		return ret;
	}

	private ParseMatch parseWhiteSpace(StringBuilder sb, int index) {
		int start = index;
		while(index < sb.length() && Character.isWhitespace(sb.charAt(index)))
			index++;
		if(index > start)
			return new ParseMatch(new PrismsConfig.DefaultPrismsConfig("whitespace", null, null), sb.substring(start, index), start, null,
				true, null);
		else
			return null;
	}

	private ParseMatch parseLiteral(StringBuilder sb, int index, PrismsConfig item) {
		String value = item.getValue();
		if(value != null)
			value = prisms.util.PrismsUtils.decodeUnicode(value);
		if(index == sb.length()) {
			if(value != null && value.length() > 0)
				return new ParseMatch(item, "", index, null, false, item.getValue() + " expected");
			else
				return new ParseMatch(item, "", index, null, false, "Sequence matching " + item.get("pattern") + " expected");
		}
		if(value != null && value.length() > 0) {
			for(int i = 0; i < value.length(); i++)
				if(index + i >= sb.length() || sb.charAt(index + i) != value.charAt(i))
					return new ParseMatch(item, "", index, null, false, item.getValue() + " expected");
			return new ParseMatch(item, sb.substring(index, index + value.length()), index, null, true, null);
		} else {
			String pattern = PrismsUtils.decodeUnicode(item.get("pattern"));
			java.util.regex.Matcher match = Pattern.compile(pattern, Pattern.DOTALL).matcher(sb.substring(index));
			if(!match.find() || match.start() > 0)
				return new ParseMatch(item, "", index, null, false, "Sequence matching " + item.get("pattern") + " expected");
			return new ParseMatch(item, sb.substring(index, index + match.end()), index, null, true, null);
		}
	}

	private ParseMatch parseCharset(StringBuilder sb, int index, PrismsConfig item) {
		if(index == sb.length())
			return new ParseMatch(item, "", index, null, false, "Sequence matching " + item.get("pattern") + " expected");
		String pattern = PrismsUtils.decodeUnicode(item.get("pattern"));
		Pattern pat = Pattern.compile(pattern, Pattern.DOTALL);
		java.util.regex.Matcher match = pat.matcher(sb.substring(index));
		if(!match.find() || match.start() > 0)
			return new ParseMatch(item, "", index, null, false, "Sequence matching " + pattern + " expected");
		int end = index + match.end();
		for(PrismsConfig exclude : item.subConfigs("exclude")) {
			String escape = exclude.get("escape");
			String value = PrismsUtils.decodeUnicode(exclude.getValue());
			if(escape == null) {
				int idx = sb.indexOf(value, index);
				if(idx >= 0 && idx < end)
					end = idx;
			} else {
				escape = PrismsUtils.decodeUnicode(escape);
				for(int i = index; i < end; i++) {
					if(startsWith(sb, i, escape))
						i += escape.length() - 1;
					else if(startsWith(sb, i, value)) {
						end = i;
						if(!pat.matcher(sb.substring(index, end)).matches())
							return new ParseMatch(item, sb.substring(index, end), index, null, true, value + " not expected");
						break;
					}
				}
			}
		}
		if(item.subConfigs("match").length > 0) {
			String text = sb.substring(index, end);
			boolean found = false;
			int maxLen = 0;
			String maxMatch = null;
			boolean incomplete = false;
			for(PrismsConfig m : item.subConfigs("match")) {
				String mValue = PrismsUtils.decodeUnicode(m.getValue());
				if(text.equals(mValue))
					found = true;
				if(found)
					break;
				if(mValue == null)
					throw new IllegalStateException("Match must have content: " + item);
				int i;
				for(i = 0; i < text.length() && i < mValue.length(); i++) {
					if(text.charAt(i) != mValue.length()) {
						if(i > maxLen) {
							maxLen = i;
							maxMatch = mValue;
						}
						break;
					}
				}
				if(i == text.length())
					incomplete = true;
			}
			if(!found) {
				PrismsConfig [] matches = item.subConfigs("match");
				StringBuilder msg = new StringBuilder();
				if(matches.length == 1)
					msg.append(matches[0].getValue());
				else {
					msg.append("One of ");
					for(int m = 0; m < matches.length; m++) {
						if(m > 0)
							msg.append(", ");
						msg.append(matches[m].getValue());
					}
				}
				msg.append(" expected");
				return new ParseMatch(item, maxMatch == null ? "" : maxMatch, index, null, incomplete, msg.toString());
			}
		}

		return new ParseMatch(item, sb.substring(index, end), index, null, true, null);
	}

	private static boolean startsWith(StringBuilder sb, int index, String seq) {
		for(int i = 0; index + i < sb.length() && i < seq.length(); i++)
			if(sb.charAt(index + i) != seq.charAt(i))
				return false;
		return true;
	}

	/**
	 * A unit test for the parser--attempts to parse and evaluate the content of UnitTest.txt
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args) {
		prisms.arch.PrismsServer.initLog4j(prisms.arch.PrismsServer.class.getResource("log4j.xml"));
		PrismsParser parser = new PrismsParser();
		System.out.println("Configuring parser...");
		try {
			parser.configure(PrismsConfig.fromXml(null,
				PrismsConfig.getRootElement("Grammar.xml", PrismsConfig.getLocation(PrismsParser.class))));
		} catch(java.io.IOException e) {
			log.error("Could not load/parse grammar config", e);
			return;
		}
		System.out.println("Finished configuring parser");
		parser.validateConfig();
		System.out.println("Parser configuration validated");
		EvaluationEnvironment env = new DefaultEvaluationEnvironment();
		StringBuilder line = new StringBuilder();
		try (Reader reader = new java.io.InputStreamReader(PrismsParser.class.getResourceAsStream("UnitTest.txt"))) {
			int read = reader.read();
			while(read >= 0) {
				if(read == '\r') {
					read = reader.read();
					continue;
				}
				line.append((char) read);
				if(read == '\n') {
					if(line.length() == 1) {
						line.setLength(0);
						read = reader.read();
						continue;
					}
					// Parse line, output result if any
					ParseMatch [] matches = parser.parseMatches(line.toString());
					for(ParseMatch match : matches) {
						if(!match.isComplete())
							break;
						ParsedItem [] items = parser.parseStructures(new ParseStructRoot(line.toString()), matches);

						for(ParsedItem item : items) {
							item.evaluate(env.transact(), false, false);
							EvaluationResult result = item.evaluate(env, false, true);
							if(result != null && !Void.TYPE.equals(result.getType())) {
								System.out.println(ArrayUtils.toString(result.getValue()));
								if(!(item instanceof prisms.lang.types.ParsedPreviousAnswer))
									env.addHistory(result.getType(), result.getValue());
							}
						}

						line.delete(0, match.text.length());
					}
				}
				read = reader.read();
			}
			System.out.println("UnitTest completed successfully");
		} catch(java.io.IOException e) {
			log.error("Could not read UnitTest.txt", e);
		} catch(ParseException e) {
			log.error("UnitTest parsing failed on \"" + line + "\"", e);
		} catch(EvaluationException e) {
			log.error("UnitTest evaluation failed", e);
		}
	}
}
