/*
 * PrismsParser.java Created Nov 10, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

import org.apache.log4j.Logger;

import prisms.arch.PrismsConfig;
import prisms.util.ArrayUtils;

/** Parses syntactical structures from text without regard to semantics using an XML-encoded grammar file */
public class PrismsParser
{
	private static final Logger log = Logger.getLogger(PrismsParser.class);

	/** Specifies that the parser is looking only for completely parsed statements */
	static final int COMPLETE_ONLY = 2;

	/** Specifies that incomplete statements may be returned unless the missing piece is optional */
	static final int COMPLETE_OPTIONAL = 1;

	/** Specifies that the parser will accept incomplete statements */
	static final int COMPLETE_ANY = 0;

	/**
	 * Specifies that an error may the thrown from parsing code if the parsing state knows what is wrong with the parsed
	 * text
	 */
	static final int COMPLETE_ERROR = -1;

	private java.util.List<PrismsConfig> theOperators;

	/** Creates a parser */
	public PrismsParser()
	{
		theOperators = new java.util.ArrayList<PrismsConfig>();
	}

	/**
	 * Configures the parser
	 * 
	 * @param config The configuration to configure the types of structures the parser can parse
	 */
	public void configure(prisms.arch.PrismsConfig config)
	{
		for(PrismsConfig entity : config.subConfigs())
			insertOperator(entity);
	}

	void insertOperator(PrismsConfig op)
	{
		int pri = op.getInt("order", 0);
		int min = 0, max = theOperators.size();
		while(max > min)
		{
			int mid = (min + max) / 2;
			if(mid == theOperators.size())
			{
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
	}

	/**
	 * Parses crude structure matches from text
	 * 
	 * @param cmd The command to parse
	 * @return The structure matches parsed
	 * @throws ParseException If parsing fails
	 */
	public synchronized ParseMatch [] parseMatches(String cmd) throws ParseException
	{
		if(cmd.length() == 0)
			throw new ParseException("No input given", cmd, 0);
		int index = 0;
		StringBuilder str = new StringBuilder(cmd);
		ParseMatch [] parseMatches = null;
		while(index < str.length())
		{
			ParseMatch parseMatch = parseItem(str, index, -1, cmd, COMPLETE_ONLY);
			int nextIndex = index;
			if(parseMatch != null)
			{
				nextIndex += parseMatch.text.length();
				nextIndex = passWhiteSpace(str, nextIndex);
			}
			if(parseMatch == null
				|| (nextIndex < str.length() && str.charAt(nextIndex) != ';' && !isLastSemi(parseMatch)))
			{
				parseMatch = parseItem(str, index, -1, cmd, COMPLETE_OPTIONAL);
				if(parseMatch != null)
				{
					nextIndex = index + parseMatch.text.length();
					nextIndex = passWhiteSpace(str, nextIndex);
				}
				if(parseMatch == null || nextIndex < str.length() && str.charAt(nextIndex) != ';'
					&& !isLastSemi(parseMatch))
				{
					parseMatch = parseItem(str, index, -1, cmd, COMPLETE_ANY);
					if(parseMatch == null)
					{
						parseItem(str, index, -1, cmd, COMPLETE_ERROR);
						throw new ParseException("Syntax error", cmd, 0);
					}
				}
			}
			parseMatches = ArrayUtils.add(parseMatches, parseMatch);
			nextIndex = index + parseMatch.text.length();
			nextIndex = passWhiteSpace(str, nextIndex);
			if(nextIndex < str.length())
			{
				if(str.charAt(nextIndex) == ';')
					nextIndex++;
				else if(isLastSemi(parseMatch)) {
				}
				else
				{
					parseItem(str, index, -1, cmd, COMPLETE_ERROR);
					throw new ParseException("Syntax error", cmd, nextIndex);
				}
			}
			index = nextIndex;
		}
		return parseMatches;
	}

	private static boolean isLastSemi(ParseMatch match)
	{
		while(match.getParsed() != null && match.getParsed().length > 0)
			match = match.getParsed()[match.getParsed().length - 1];
		return match.text.length() > 0 && match.text.charAt(match.text.length() - 1) == ';';
	}

	/**
	 * Parses logical syntactic structures from crude matches
	 * 
	 * @param parent The parent parse structure
	 * @param matches The matches parsed from the command
	 * @return The syntax structures parsed from the matches
	 * @throws ParseException If parsing fails
	 */
	public synchronized ParsedItem [] parseStructures(ParsedItem parent, ParseMatch... matches) throws ParseException
	{
		if(parent == null)
			throw new IllegalArgumentException("Parent required for parsed structures.  Use " + ParseStructRoot.class.getSimpleName() + ".");
		ParsedItem [] ret = new ParsedItem [matches.length];
		for(int i = 0; i < ret.length; i++)
		{
			ParseMatch implMatch = matches[i];
			if(implMatch == null)
				continue;
			Class<? extends ParsedItem> implClass = null;
			while(implClass == null)
			{
				try
				{
					implClass = implMatch.config.getClass("impl", ParsedItem.class);
				} catch(ClassNotFoundException e)
				{
					throw new ParseException("Implementation not found for " + implMatch.config, e, parent.getRoot()
						.getFullCommand(), -1);
				} catch(ClassCastException e)
				{
					throw new ParseException("Implementation not an instance of ParseStruct for " + implMatch.config,
						e, parent.getRoot().getFullCommand(), -1);
				}
				if(implClass == null)
				{
					if(implMatch.getParsed() != null && implMatch.getParsed().length == 1)
						implMatch = implMatch.getParsed()[0];
					else if("entity".equals(implMatch.config.getName())
						|| "operator".equals(implMatch.config.getName()))
						throw new ParseException("No implementation configured for " + implMatch.config, parent
							.getRoot().getFullCommand(), -1);
					else
						break;
				}
			}
			if(implClass != null)
			{
				try
				{
					ret[i] = implClass.newInstance();
				} catch(InstantiationException e)
				{
					throw new ParseException("Could not instantiate implementation for " + matches[i].config, e, parent
						.getRoot().getFullCommand(), -1);
				} catch(IllegalAccessException e)
				{
					throw new ParseException("Could not instantiate implementation for " + matches[i].config, e, parent
						.getRoot().getFullCommand(), -1);
				}
				ret[i].setup(this, parent, implMatch);
			}
		}
		return ret;
	}

	ParseMatch parseItem(StringBuilder sb, int index, int priority, String command, int completeness, String... types)
		throws ParseException
	{
		boolean withError = completeness == COMPLETE_ERROR;
		int origIndex = index;
		if(withError)
			completeness = COMPLETE_ANY;
		ParseMatch ret = null;
		boolean foundOp = true;
		final boolean [] withTypesHadPreOp = new boolean [1];
		ParseMatch preOp = null;
		while(index < sb.length() && foundOp)
		{
			foundOp = false;
			if(ret != null)
			{
				preOp = ret;
				ret = null;
			}
			ret = parseNextMatch(sb, index, priority, command, completeness, types, preOp, withTypesHadPreOp);
			for(PrismsConfig op : theOperators)
			{
				if(types.length == 0)
				{
					if(op.get("priority") != null
						&& (op.getInt("priority", 0) < 0 || op.getInt("priority", 0) < priority))
						continue;
				}
				else if(!ArrayUtils.contains(types, op.get("name")))
					continue;
				else
					withTypesHadPreOp[0] |= hasPreOp(op);
				ParseMatch temp = parseNextMatch(op, sb, index, preOp, command, completeness);
				// temp can't be incomplete if there is text after the match
				if(temp != null && !temp.isComplete() && index + temp.text.length() < sb.length())
					temp = null;
				// temp must have some real content--can't just be a wrapped pre-op and/or whitespace
				if(temp != null)
				{
					boolean hasContent = false;
					for(ParseMatch m : temp.getParsed())
					{
						if("pre-op".equals(m.config.get("name")))
						{}
						else if("whitespace".equals(m.config.get("name")))
						{}
						else
						{
							hasContent = true;
							break;
						}
					}
					if(!hasContent)
						temp = null;
				}
				if(temp != null && temp.text.length() > 0)
				{
					if(ret == null)
						ret = temp;
					else if(temp.text.length() > ret.text.length())
						ret = temp;
					else if(temp.text.length() == ret.text.length() && !ret.isComplete() && temp.isComplete())
						ret = temp;
				}
			}
			if(ret != null)
			{
				foundOp = ret.isComplete();
				index = ret.index + ret.text.length();
				if(types.length > 0)
					return ret;
			}
		}
		if(withError && ret != null)
		{ // For a better error message
			index = origIndex;
			java.util.ArrayList<PrismsConfig> ops = new java.util.ArrayList<PrismsConfig>();
			while(ret != null)
			{
				ops.add(ret.config);
				boolean found = false;
				for(int p = 0; p < ret.getParsed().length; p++)
					if(ret.getParsed()[p].config.getName().equals("pre-op"))
					{
						ret = ret.getParsed()[p].getParsed()[0];
						found = true;
						break;
					}
				if(!found)
					ret = null;
			}
			java.util.Collections.reverse(ops);
			for(PrismsConfig op : ops)
			{
				ret = parseNextMatch(op, sb, index, ret, command, COMPLETE_ERROR);
				index = ret.index + ret.text.length();
			}
		}
		if(ret == null)
			ret = preOp;
		if(ret == null && withTypesHadPreOp[0])
		{
			do
			{
				String [] allTypes = new String [0];
				ret = parseNextMatch(sb, index, priority, command, completeness, allTypes, preOp, withTypesHadPreOp);
				if(ret != null)
				{
					index = ret.index + ret.text.length();
					preOp = ret;
					ret = null;
					for(PrismsConfig op : theOperators)
					{
						if(!ArrayUtils.contains(types, op.get("name")))
							continue;
						ret = parseNextMatch(op, sb, index, preOp, command, completeness);
						if(ret != null)
						{
							index = ret.index + ret.text.length();
							return ret;
						}
					}
				}
			} while(ret != null && index < sb.length() && !ArrayUtils.contains(types, ret.config.get("name")));
		}
		return ret;
	}

	private ParseMatch parseNextMatch(StringBuilder sb, int index, int priority, String command, int completeness,
		String [] types, ParseMatch preOp, boolean [] withTypesHadPreOp) throws ParseException
	{
		ParseMatch ret = null;
		for(PrismsConfig op : theOperators)
		{
			if(types.length == 0)
			{
				if(op.get("priority") != null && (op.getInt("priority", 0) < 0 || op.getInt("priority", 0) < priority))
					continue;
			}
			else if(!ArrayUtils.contains(types, op.get("name")))
				continue;
			else
				withTypesHadPreOp[0] |= hasPreOp(op);
			ParseMatch temp = parseNextMatch(op, sb, index, preOp, command, completeness);
			// temp can't be incomplete if there is text after the match
			if(temp != null && !temp.isComplete() && index + temp.text.length() < sb.length())
				temp = null;
			// temp must have some real content--can't just be a wrapped pre-op and/or whitespace
			if(temp != null)
			{
				boolean hasContent = false;
				for(ParseMatch m : temp.getParsed())
				{
					if("pre-op".equals(m.config.get("name")))
					{}
					else if("whitespace".equals(m.config.get("name")))
					{}
					else
					{
						hasContent = true;
						break;
					}
				}
				if(!hasContent)
					temp = null;
			}
			if(temp != null && temp.text.length() > 0)
			{
				if(ret == null)
					ret = temp;
				else if(temp.text.length() > ret.text.length())
					ret = temp;
				else if(temp.text.length() == ret.text.length() && !ret.isComplete() && temp.isComplete())
					ret = temp;
			}
		}
		return ret;
	}

	private ParseMatch parseNextMatch(PrismsConfig op, StringBuilder sb, int index, ParseMatch preOp, String command,
		int completeness) throws ParseException
	{
		ParseMatch [] match = parseCheck(op, sb, index, preOp, command, completeness);
		if(match != null)
		{
			int len = 0;
			for(ParseMatch m : match)
				len += m.text.length();
			index = match[0].index;
			return new ParseMatch(op, sb.substring(index, index + len), index, match, true);
		}
		else
			return null;
	}

	private static boolean hasPreOp(PrismsConfig config)
	{
		for(PrismsConfig subConfig : config.subConfigs())
		{
			if(subConfig.getName().equals("pre-op"))
				return true;
			else if((subConfig.getName().equals("option") || subConfig.equals("select")) && hasPreOp(subConfig))
				return true;
		}
		return false;
	}

	ParseMatch [] parseCheck(PrismsConfig opConfig, StringBuilder sb, int index, ParseMatch preOp, String command,
		int completeness) throws ParseException
	{
		return _parseCheck(opConfig, sb, index, preOp, opConfig.getInt("priority", 0), false, command, completeness);
	}

	ParseMatch [] _parseCheck(PrismsConfig opConfig, StringBuilder sb, int index, ParseMatch preOp, int priority,
		boolean optional, String command, int completeness) throws ParseException
	{
		boolean hasPreOpConfig = false;
		ParseMatch [] ret = null;
		for(int itemIdx = 0; itemIdx < opConfig.subConfigs().length; itemIdx++)
		{
			PrismsConfig item = opConfig.subConfigs()[itemIdx];
			if(item.getName().equals("name") || item.getName().equals("priority") || item.getName().equals("order")
				|| item.getName().equals("impl") || item.getName().equals("min") || item.getName().equals("max"))
				continue;
			else if(item.getName().equals("pre-op"))
			{
				if(preOp == null)
				{
					if("option".equals(opConfig.getName()))
					{
						ParseMatch op = parseOp(sb, index, opConfig, itemIdx, priority, optional, command,
							completeness, ret);
						if(op == null)
							return null;
						ret = prisms.util.ArrayUtils.add(ret, new ParseMatch(item, op.text, index,
							new ParseMatch [] {op}, true));
						index += op.text.length();
					}
					else
						return null;
				}
				else if(item.get("type") != null)
				{
					if(!ArrayUtils.contains(item.get("type").split("\\|"), preOp.config.get("name")))
						return null;
				}
				if(hasPreOpConfig)
				{
					log.error("Double pre-op configuration for config " + opConfig.get("name"));
					return null;
				}
				else
				{
					hasPreOpConfig = true;
					ret = ArrayUtils.add(ret, new ParseMatch(item, preOp.text, preOp.index, new ParseMatch [] {preOp},
						true));
					preOp = null;
				}
				continue;
			}
			else if(preOp != null && !hasPreOpConfig)
				return null; // No pre-op registered. Can't be this one.

			String whiteSpace = null;
			if(!"option".equals(item.getName()) && !"select".equals(item.getName()) && !"forbid".equals(item.getName()))
			{
				int wsIdx = passWhiteSpace(sb, index);
				whiteSpace = wsIdx > index ? sb.substring(index, wsIdx) : null;
				if(whiteSpace != null)
					ret = prisms.util.ArrayUtils.add(ret, new ParseMatch(new PrismsConfig.DefaultPrismsConfig(
						"whitespace", null, null), whiteSpace, index, null, true));
				index = wsIdx;
			}

			if(item.getName().equals("forbid"))
			{
				if(_parseCheck(item, sb, index, preOp, priority, true, command, completeness) != null)
					return null;
			}
			else if(item.getName().equals("option"))
			{
				if(priority >= 0)
					for(int idx2 = itemIdx + 1; idx2 < opConfig.subConfigs().length; idx2++)
						if(hasLiteral(opConfig.subConfigs()[idx2]))
						{
							priority = -1;
							break;
						}

				ParseMatch [] res;
				int limit;
				if(item.getInt("min", -1) >= 0 || item.getInt("max", -1) >= 0)
					limit = item.getInt("max", Integer.MAX_VALUE);
				else
					limit = 1;
				ParseMatch [] total = null;
				int count = 0;
				do
				{
					res = _parseCheck(item, sb, index, preOp, priority, true, command, completeness);
					if(res == null)
						break;
					count++;
					boolean newPreOpConfig = false;
					for(ParseMatch m : res)
						if(m.config.getName().equals("pre-op"))
						{
							newPreOpConfig = true;
							break;
						}
					if(hasPreOpConfig && newPreOpConfig)
					{
						log.error("Double pre-op configuration for config " + opConfig.get("name"));
						return null;
					}
					int len = 0;
					for(ParseMatch m : res)
						len += m.text.length();
					if(len == 0)
						break;
					total = prisms.util.ArrayUtils.addAll(total, res);
					index += len;
					if(!total[total.length - 1].isComplete())
						break;
				} while(count < limit);
				if(count > limit || count < item.getInt("min", 0))
					return null;
				if(total == null)
					continue;
				ret = ArrayUtils.addAll(ret, total);
				if(!total[total.length - 1].isComplete())
					return ret;
			}
			else if(item.getName().equals("select"))
			{
				if(priority >= 0)
					for(int idx2 = itemIdx + 1; idx2 < opConfig.subConfigs().length; idx2++)
						if(opConfig.subConfigs()[idx2].getName().equals("literal"))
						{
							priority = -1;
							break;
						}

				ParseMatch [] res = null;
				for(PrismsConfig option : item.subConfigs("option"))
				{
					res = _parseCheck(option, sb, index, preOp, priority, true, command, completeness);
					if(res != null)
						break;
				}
				if(res == null)
				{
					for(PrismsConfig option : item.subConfigs("option"))
					{
						res = _parseCheck(option, sb, index, preOp, priority, false, command, completeness);
						if(res != null)
							break;
					}
				}
				if(res == null)
				{
					if((completeness <= COMPLETE_ANY || (completeness <= COMPLETE_OPTIONAL && !optional)))
					{
						if(ret == null)
							return null;
						ret = ArrayUtils.add(ret, new ParseMatch(item, "", index, null, false));
						return ret;
					}
					else
						return null;
				}
				boolean newPreOpConfig = false;
				for(ParseMatch m : res)
					if(m.config.getName().equals("pre-op"))
					{
						newPreOpConfig = true;
						break;
					}
				if(hasPreOpConfig && newPreOpConfig)
				{
					log.error("Double pre-op configuration for config " + opConfig.get("name"));
					return null;
				}
				ret = ArrayUtils.addAll(ret, res);
				for(ParseMatch m : res)
					index += m.text.length();
				if(!res[res.length - 1].isComplete())
					return ret;
			}
			else if(preOp != null)
				return null; // No pre-op registered, can't take a pre-op
			else if(item.getName().equals("whitespace"))
			{
				if("forbid".equals(item.get("type")))
				{
					if(whiteSpace != null)
					{
						if(completeness <= COMPLETE_ERROR)
							throw new ParseException("White space unexpected", command, index);
						else
							return null;
					}
				}
				else if(whiteSpace != null)
				{
					ParseMatch wsMatch = ret[ret.length - 1];
					ret[ret.length - 1] = new ParseMatch(item, whiteSpace, wsMatch.index, null, true);
				}
				else if(index == sb.length())
				{ // If this is the end of the input, we can treat that as white space
					ParseMatch wsMatch = ret[ret.length - 1];
					ret[ret.length - 1] = new ParseMatch(item, "", wsMatch.index, null, true);
				}
				else
				{
					if(completeness <= COMPLETE_ERROR)
						throw new ParseException("White space expected", command, index);
					else if((completeness <= COMPLETE_OPTIONAL && !optional) || completeness <= COMPLETE_ANY)
					{
						if(ret == null)
							return null;
						ret = ArrayUtils.add(ret, new ParseMatch(item, "", index, null, false));
						return ret;
					}
					else
						return null;
				}
				continue;
			}
			else if(item.getName().equals("literal"))
			{
				String value = item.getValue();
				if(index == sb.length())
				{
					if(ret == null || ret.length == 0)
						return null;
					else if(completeness <= COMPLETE_ERROR)
					{
						if(value != null && value.length() > 0)
							throw new ParseException(item.getValue() + " expected", command, index);
						else
							throw new ParseException("Pattern matching " + item.get("pattern") + " expected", command,
								index);
					}
					else if(completeness <= COMPLETE_ANY || (completeness <= COMPLETE_OPTIONAL && !optional))
					{
						ret = ArrayUtils.add(ret, new ParseMatch(item, "", index, null, false));
						break;
					}
					else
						return null;
				}
				if(value != null && value.length() > 0)
				{
					for(int i = 0; i < value.length(); i++)
						if(index + i >= sb.length() || sb.charAt(index + i) != value.charAt(i))
						{
							if(completeness <= COMPLETE_ERROR)
								throw new ParseException(item.getValue() + " expected", command, index);
							else if(i > 0
								&& ((completeness <= COMPLETE_OPTIONAL && !optional) || completeness <= COMPLETE_ANY))
							{
								ret = ArrayUtils.add(ret, new ParseMatch(item, "", index, null, false));
								return ret;
							}
							else
								return null;
						}
					ret = ArrayUtils.add(ret, new ParseMatch(item, sb.substring(index, index + value.length()), index,
						null, true));
					index += value.length();
				}
				else
				{
					String pattern = item.get("pattern");
					if(pattern == null)
					{
						log.error("No value or pattern for literal in " + opConfig);
						return null;
					}
					java.util.regex.Matcher match;
					try
					{
						match = java.util.regex.Pattern.compile(pattern).matcher(sb.substring(index));
					} catch(java.util.regex.PatternSyntaxException e)
					{
						log.error("Pattern " + pattern + " in config " + opConfig + " has an error: " + e);
						return null;
					}
					if(!match.find() || match.start() > 0)
					{
						if(completeness <= COMPLETE_ERROR)
							throw new ParseException("Pattern matching " + pattern + " expected", command, index);
						else if((completeness <= COMPLETE_OPTIONAL && !optional) || completeness <= COMPLETE_ANY)
						{
							ret = ArrayUtils.add(ret, new ParseMatch(item, "", index, null, false));
							return ret;
						}
						else
							return null;
					}
					ret = ArrayUtils.add(ret, new ParseMatch(item, sb.substring(index, index + match.end()), index,
						null, true));
					index += match.end();
				}
			}
			else if(item.getName().equals("charset"))
			{
				if(index == sb.length())
				{
					if(ret == null || ret.length == 0)
						return null;
					else if(completeness <= COMPLETE_ERROR)
					{
						throw new ParseException("Sequence matching " + item.get("pattern") + " expected", command,
							index);
					}
					else if(completeness <= COMPLETE_ANY || (completeness <= COMPLETE_OPTIONAL && !optional))
					{
						ret = ArrayUtils.add(ret, new ParseMatch(item, "", index, null, false));
						return ret;
					}
					else
						return null;
				}
				String pattern = item.get("pattern");
				if(pattern == null)
				{
					log.error("No value or pattern for literal in " + opConfig);
					return null;
				}
				java.util.regex.Pattern pat;
				try
				{
					pat = java.util.regex.Pattern.compile(pattern);
				} catch(java.util.regex.PatternSyntaxException e)
				{
					log.error("Pattern " + pattern + " in config " + opConfig + " has an error: " + e);
					return null;
				}
				java.util.regex.Matcher match = pat.matcher(sb.substring(index));
				if(!match.find() || match.start() > 0)
				{
					if(completeness <= COMPLETE_ERROR)
						throw new ParseException("Sequence matching " + pattern + " expected", command, index);
					else if((completeness <= COMPLETE_OPTIONAL && !optional) || completeness <= COMPLETE_ANY)
					{
						ret = ArrayUtils.add(ret, new ParseMatch(item, "", index, null, false));
						return ret;
					}
					else
						return null;
				}
				int end = index + match.end();
				for(PrismsConfig exclude : item.subConfigs("exclude"))
				{
					String escape = exclude.get("escape");
					String value = exclude.getValue();
					if(escape == null)
					{
						int idx = sb.indexOf(value, index);
						if(idx >= 0 && idx < end)
							end = idx;
					}
					else
					{
						for(int i = index; i < end; i++)
						{
							if(startsWith(sb, i, escape))
								i += escape.length() - 1;
							else if(startsWith(sb, i, value))
							{
								end = i;
								if(!pat.matcher(sb.substring(index, end)).matches())
								{
									if(completeness <= COMPLETE_ERROR)
										throw new ParseException(value + " not expected", command, index);
									else if((completeness <= COMPLETE_OPTIONAL && !optional)
										|| completeness <= COMPLETE_ANY)
									{
										ret = ArrayUtils.add(ret, new ParseMatch(item, sb.substring(index, end), index,
											null, false));
										return ret;
									}
									else
										return null;
								}
								break;
							}
						}
					}
				}
				if(item.subConfigs("match").length > 0)
				{
					String text = sb.substring(index, end);
					boolean found = false;
					int maxLen = 0;
					String maxMatch = null;
					for(PrismsConfig m : item.subConfigs("match"))
					{
						if(text.equals(m.getValue()))
							found = true;
						if(found)
							break;
						if(m.getValue() == null)
							throw new IllegalStateException("Match must have content: " + item);
						for(int i = 0; i < text.length() && i < m.getValue().length(); i++)
						{
							if(text.charAt(i) != m.getValue().length())
							{
								if(i > maxLen)
								{
									maxLen = i;
									maxMatch = m.getValue();
								}
								break;
							}
						}
					}
					if(!found)
					{
						if(completeness <= COMPLETE_ERROR)
						{
							PrismsConfig [] matches = item.subConfigs("match");
							StringBuilder msg = new StringBuilder();
							if(matches.length == 1)
								msg.append(matches[0].getValue());
							else
							{
								msg.append("One of ");
								for(int m = 0; m < matches.length; m++)
								{
									if(m > 0)
										msg.append(", ");
									msg.append(matches[m].getValue());
								}
							}
							msg.append(" expected");
							throw new ParseException(msg.toString(), command, index);
						}
						else if(maxMatch != null
							&& ((completeness <= COMPLETE_OPTIONAL && !optional) || completeness <= COMPLETE_ANY))
						{
							ret = ArrayUtils.add(ret, new ParseMatch(item, maxMatch, index, null, false));
							return ret;
						}
						else
							return null;
					}
				}

				ret = ArrayUtils.add(ret, new ParseMatch(item, sb.substring(index, end), index, null, true));
				index = end;
			}
			else if(item.getName().equals("op"))
			{
				ParseMatch op = parseOp(sb, index, opConfig, itemIdx, priority, optional, command, completeness, ret);
				if(op == null)
					return null;
				ret = prisms.util.ArrayUtils.add(ret,
					new ParseMatch(item, op.text, index, new ParseMatch [] {op}, true));
				index += op.text.length();
				if(!op.isComplete())
					return ret;
			}
			else
			{
				log.error("Unrecognized config named " + item.getName() + " in config " + opConfig);
				return null;
			}
		}
		return ret;
	}

	private int passWhiteSpace(StringBuilder sb, int index)
	{
		while(index < sb.length() && Character.isWhitespace(sb.charAt(index)))
			index++;
		return index;
	}

	private boolean startsWith(StringBuilder sb, int index, String seq)
	{
		for(int i = 0; index + i < sb.length() && i < seq.length(); i++)
			if(sb.charAt(index + i) != seq.charAt(i))
				return false;
		return true;
	}

	private boolean hasLiteral(PrismsConfig config)
	{
		if(config.getName().equals("literal"))
			return true;
		else if(config.getName().equals("select"))
		{
			boolean ret = true;
			for(PrismsConfig option : config.subConfigs("option"))
				for(PrismsConfig c : option.subConfigs())
					ret &= hasLiteral(c);
			if(ret)
				return true;
		}
		return false;
	}

	private ParseMatch parseOp(StringBuilder sb, int index, PrismsConfig opConfig, int itemIdx, int priority,
		boolean optional, String command, int completeness, ParseMatch [] ret) throws ParseException
	{
		String type = opConfig.subConfigs()[itemIdx].get("type");
		if(index == sb.length())
		{
			if(ret == null || ret.length == 0)
				return null;
			else if(completeness <= COMPLETE_ERROR)
			{
				if(type != null)
				{
					String [] types = type.split("\\|");
					StringBuilder msg = new StringBuilder();
					for(int i = 0; i < types.length; i++)
					{
						if(i == types.length - 1)
							msg.append(" or ");
						else if(i > 0)
							msg.append(", ");
						msg.append(types[i]);
					}
					msg.append(" expected");
					throw new ParseException(msg.toString(), command, index);
				}
				else
					throw new ParseException("operand expected", command, index);
			}
			else if(completeness <= COMPLETE_ANY || (completeness <= COMPLETE_OPTIONAL && !optional))
				return new ParseMatch(opConfig.subConfigs()[itemIdx], "", index, null, false);
		}
		if(priority >= 0)
			for(int idx2 = itemIdx + 1; idx2 < opConfig.subConfigs().length; idx2++)
				if(opConfig.subConfigs()[idx2].getName().equals("literal"))
				{
					priority = -1;
					break;
				}

		ParseMatch op;
		if(type == null)
		{
			op = parseItem(sb, index, priority, command, completeness);
			if(op == null && completeness <= COMPLETE_ERROR)
			{
				String stored = opConfig.subConfigs()[itemIdx].get("storeAs");
				if(stored != null)
					throw new ParseException(stored + " expected", command, index);
				else
					throw new ParseException("Unrecognized content", command, index);
			}
		}
		else
		{
			op = parseItem(sb, index, priority, command, completeness, type.split("\\|"));
			if(op == null && completeness <= COMPLETE_ERROR)
			{
				String stored = opConfig.subConfigs()[itemIdx].get("storeAs");
				String [] types = type.split("\\|");
				StringBuilder msg = new StringBuilder();
				for(int t = 0; t < types.length; t++)
				{
					if(t > 0)
					{
						if(t == types.length - 1)
							msg.append(" or ");
						else
							msg.append(", ");
					}
					msg.append(types[t]);
				}
				msg.append(" expected");
				if(stored != null)
					msg.append(" for ").append(stored);
				throw new ParseException(msg.toString(), command, index);
			}
		}
		return op;
	}

	/**
	 * Tests the parser by reading in from System.in and printing out the toString() representation of the structures
	 * parsed
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		prisms.arch.PrismsServer.initLog4j(prisms.arch.PrismsServer.class.getResource("log4j.xml"));
		PrismsParser parser = new PrismsParser();
		try
		{
			parser.configure(PrismsConfig.fromXml(null,
				PrismsConfig.getRootElement("Grammar.xml", PrismsConfig.getLocation(PrismsParser.class))));
		} catch(java.io.IOException e)
		{
			log.error("Could not load/parse grammar config", e);
			return;
		}
		try (java.util.Scanner scanner = new java.util.Scanner(System.in))
		{
			String line = scanner.nextLine();
			while(!"exit".equals(line))
			{
				try
				{
					ParseMatch [] matches = parser.parseMatches(line);
					ParseStructRoot root = new ParseStructRoot(line);
					ParsedItem [] structs = parser.parseStructures(root, matches);
					System.out.println(ArrayUtils.toString(structs));
				} catch(ParseException e)
				{
					log.error("Could not parse line", e);
				}
				line = scanner.nextLine();
			}
		}
	}
}
