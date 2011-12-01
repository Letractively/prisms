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
		int pri = op.getInt("priority", 0);
		int min = 0, max = theOperators.size();
		while(max > min)
		{
			int mid = (min + max) / 2;
			if(mid == theOperators.size())
			{
				min = mid;
				break;
			}
			int testPri = theOperators.get(mid).getInt("priority", 0);
			if(pri < testPri)
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
			ParseMatch parseMatch = parseItem(str, index, null, -1, cmd, true);
			int nextIndex = index;
			if(parseMatch != null)
			{
				nextIndex += parseMatch.text.length();
				nextIndex = passWhiteSpace(str, nextIndex);
			}
			if(parseMatch == null
				|| (nextIndex < str.length() && str.charAt(nextIndex) != ';' && !isLastSemi(parseMatch)))
			{
				parseMatch = parseItem(str, index, null, -1, cmd, false);
				if(parseMatch == null)
					throw new ParseException("Syntax error", cmd, 0);
			}
			parseMatches = ArrayUtils.add(parseMatches, parseMatch);
			nextIndex = index + parseMatch.text.length();
			nextIndex = passWhiteSpace(str, nextIndex);
			if(nextIndex < str.length())
			{
				if(str.charAt(nextIndex) == ';' || isLastSemi(parseMatch))
					nextIndex++;
				else
					throw new ParseException("Syntax error", cmd, index);
			}
			index = nextIndex;
		}
		return parseMatches;
	}

	private static boolean isLastSemi(ParseMatch match)
	{
		while(match.getParsed() != null && match.getParsed().length > 0)
			match = match.getParsed()[match.getParsed().length - 1];
		return match.text.charAt(match.text.length() - 1) == ';';
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
		ParsedItem [] ret = new ParsedItem [matches.length];
		for(int i = 0; i < ret.length; i++)
		{
			ParseMatch implMatch = matches[i];
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
					else
						throw new ParseException("No implementation configured for " + implMatch.config, parent
							.getRoot().getFullCommand(), -1);
				}
			}
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
		return ret;
	}

	ParseMatch parseItem(StringBuilder sb, int index, ParseMatch preOp, int priority, String command,
		boolean completeOnly, String... types) throws IncompleteInputException
	{
		int start = index;
		ParseMatch ret = null;
		boolean foundOp = true;
		boolean withTypesHadPreOp = false;
		while(index < sb.length() && foundOp)
		{
			foundOp = false;
			if(ret != null)
				preOp = ret;
			for(PrismsConfig op : theOperators)
			{
				if(types.length == 0)
				{
					int opPri = op.getInt("priority", 0);
					if(opPri < 0 || ret != null && opPri <= priority)
						break;
				}
				else if(!ArrayUtils.contains(types, op.get("name")))
					continue;
				else
					withTypesHadPreOp |= hasPreOp(op);
				ParseMatch [] match = parseCheck(op, sb, index, preOp, command, completeOnly);
				if(match != null)
				{
					foundOp = true;
					int len = 0;
					for(ParseMatch m : match)
						len += m.text.length();
					ret = new ParseMatch(op, sb.substring(start, start + len), start, match);
					index = start + len;
					break;
				}
			}
		}
		if(ret == null && withTypesHadPreOp)
			return parseItem(sb, index, null, -1, command, completeOnly);
		return ret;
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
		boolean completeOnly) throws IncompleteInputException
	{
		return _parseCheck(opConfig, sb, index, preOp, opConfig.getInt("priority", 0), false, command, completeOnly);
	}

	ParseMatch [] _parseCheck(PrismsConfig opConfig, StringBuilder sb, int index, ParseMatch preOp, int priority,
		boolean optional, String command, boolean completeOnly) throws IncompleteInputException
	{
		boolean hasPreOpConfig = false;
		ParseMatch [] ret = null;
		for(int itemIdx = 0; itemIdx < opConfig.subConfigs().length; itemIdx++)
		{
			PrismsConfig item = opConfig.subConfigs()[itemIdx];
			if(item.getName().equals("name") || item.getName().equals("priority") || item.getName().equals("impl")
				|| item.getName().equals("min") || item.getName().equals("max"))
				continue;
			else if(item.getName().equals("pre-op"))
			{
				if(preOp == null)
				{
					if("option".equals(opConfig.getName()))
					{
						ParseMatch op = parseOp(sb, index, opConfig, itemIdx, priority, optional, command, completeOnly);
						if(op == null)
							return null;
						ret = prisms.util.ArrayUtils.add(ret, new ParseMatch(item, op.text, index,
							new ParseMatch [] {op}));
						index += op.text.length();
					}
					else
						return null;
				}
				else if(item.get("type") != null && !item.get("type").equals(preOp.config.get("name")))
					return null;
				else if(hasPreOpConfig)
				{
					log.error("Double pre-op configuration for config " + opConfig.get("name"));
					return null;
				}
				else
				{
					hasPreOpConfig = true;
					ret = ArrayUtils.add(ret, new ParseMatch(item, preOp.text, preOp.index, new ParseMatch [] {preOp}));
				}
				continue;
			}
			else if(preOp != null && !hasPreOpConfig)
				return null; // No pre-op registered. Can't be this one.

			String whiteSpace = null;
			if(!"option".equals(item.getName()) && !"select".equals(item.getName()))
			{
				int wsIdx = passWhiteSpace(sb, index);
				whiteSpace = wsIdx > index ? sb.substring(index, wsIdx) : null;
				if(whiteSpace != null)
					ret = prisms.util.ArrayUtils.add(ret, new ParseMatch(new PrismsConfig.DefaultPrismsConfig(
						"whitespace", null, null), whiteSpace, index, null));
				index = wsIdx;
			}

			if(item.getName().equals("whitespace"))
			{
				if("forbid".equals(item.get("type")))
				{
					if(whiteSpace != null)
						return null;
				}
				else if(whiteSpace == null)
				{
					if(!optional && index == sb.length())
						throw new IncompleteInputException("Incomplete input", command, index);
					return null;
				}
				else
				{
					ParseMatch wsMatch = ret[ret.length - 1];
					ret[ret.length - 1] = new ParseMatch(item, whiteSpace, wsMatch.index, null);
				}
				continue;
			}
			else if(item.getName().equals("literal"))
			{
				if(!completeOnly && !optional && index == sb.length())
					throw new IncompleteInputException("Incomplete input", command, index);
				String value = item.getValue();
				if(value != null && value.length() > 0)
				{
					for(int i = 0; i < value.length(); i++)
						if(index + i >= sb.length() || sb.charAt(index + i) != value.charAt(i))
							return null;
					ret = ArrayUtils.add(ret, new ParseMatch(item, sb.substring(index, index + value.length()), index,
						null));
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
						return null;
					ret = ArrayUtils.add(ret, new ParseMatch(item, sb.substring(index, index + match.end()), index,
						null));
					index += match.end();
				}
			}
			else if(item.getName().equals("charset"))
			{
				if(!completeOnly && !optional && index == sb.length())
					throw new IncompleteInputException("Incomplete input", command, index);
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
					return null;
				int end = index + match.end();
				for(PrismsConfig exclude : item.subConfigs("exclude"))
				{
					String escape = exclude.get("escape");
					String value = exclude.getValue();
					if(escape == null)
					{
						int idx = sb.indexOf(value, index);
						if(idx < end)
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
								break;
							}
						}
					}
				}
				if(item.subConfigs("match").length > 0)
				{
					String text = sb.substring(index, end);
					boolean found = false;
					for(PrismsConfig m : item.subConfigs("match"))
					{
						if(text.equals(m.getValue()))
							found = true;
						if(found)
							break;
					}
					if(!found)
						return null;
				}

				ret = ArrayUtils.add(ret, new ParseMatch(item, sb.substring(index, end), index, null));
				index = end;
			}
			else if(item.getName().equals("op"))
			{
				ParseMatch op = parseOp(sb, index, opConfig, itemIdx, priority, optional, command, completeOnly);
				if(op == null)
					return null;
				ret = prisms.util.ArrayUtils.add(ret, new ParseMatch(item, op.text, index, new ParseMatch [] {op}));
				index += op.text.length();
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
					res = _parseCheck(item, sb, index, hasPreOpConfig ? null : preOp, priority, true, command,
						completeOnly);
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
					total = prisms.util.ArrayUtils.addAll(total, res);
					for(ParseMatch m : res)
						index += m.text.length();
				} while(count < limit);
				if(count > limit || count < item.getInt("min", 0))
					return null;
				if(total == null)
					continue;
				ret = ArrayUtils.addAll(ret, total);
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
					res = _parseCheck(option, sb, index, preOp, priority, true, command, completeOnly);
					if(res != null)
						break;
				}
				if(res == null)
				{
					for(PrismsConfig option : item.subConfigs("option"))
					{
						res = _parseCheck(option, sb, index, preOp, priority, false, command, completeOnly);
						if(res != null)
							break;
					}
				}
				if(res == null)
				{
					if(!completeOnly && !optional && index == sb.length())
						throw new IncompleteInputException("Incomplete input", command, index);
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
		boolean optional, String command, boolean completeOnly) throws IncompleteInputException
	{
		if(!completeOnly && !optional && index == sb.length())
			throw new IncompleteInputException("Incomplete input", command, index);
		if(priority >= 0)
			for(int idx2 = itemIdx + 1; idx2 < opConfig.subConfigs().length; idx2++)
				if(opConfig.subConfigs()[idx2].getName().equals("literal"))
				{
					priority = -1;
					break;
				}

		String type = opConfig.subConfigs()[itemIdx].get("type");
		ParseMatch op;
		if(type == null)
			op = parseItem(sb, index, null, priority, command, completeOnly);
		else
			op = parseItem(sb, index, null, priority, command, completeOnly, type.split("\\|"));
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
		java.util.Scanner scanner = new java.util.Scanner(System.in);
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
