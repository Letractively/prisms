package prisms.lang.types;

import prisms.lang.EvaluationException;
import prisms.lang.ParsedItem;

/** Represents a block of statements */
public class ParsedStatementBlock extends ParsedItem
{
	private prisms.lang.ParsedItem[] theContents;

	/**
	 * Default constructor. Used when {@link #setup(prisms.lang.PrismsParser, ParsedItem, prisms.lang.ParseMatch)} will
	 * be called later
	 */
	public ParsedStatementBlock()
	{
	}

	/**
	 * Pre-setup constructor. Used so that {@link #setup(prisms.lang.PrismsParser, ParsedItem, prisms.lang.ParseMatch)}
	 * does not need to be called subsequently.
	 * 
	 * @param parser The parser that parsed this structure's contents
	 * @param parent The parent structure
	 * @param match The match that this structure is to identify with
	 * @param contents The content statements of the block
	 * @see ParsedItem#setup(prisms.lang.PrismsParser, ParsedItem, prisms.lang.ParseMatch)
	 */
	public ParsedStatementBlock(prisms.lang.PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match,
		ParsedItem... contents)
	{
		try
		{
			super.setup(parser, parent, match);
		} catch(prisms.lang.ParseException e)
		{
			throw new IllegalStateException("ParseException should not come from super class", e);
		}
		theContents = contents;
	}

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		java.util.ArrayList<ParsedItem> contents = new java.util.ArrayList<ParsedItem>();
		for(prisms.lang.ParseMatch m : match.getParsed())
			if("content".equals(m.config.get("storeAs")))
				contents.add(parser.parseStructures(this, m)[0]);
		theContents = contents.toArray(new ParsedItem [contents.size()]);
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws EvaluationException
	{
		prisms.lang.EvaluationEnvironment scoped = env.scope(true);
		for(ParsedItem content : theContents)
		{
			if(content instanceof ParsedAssignmentOperator)
			{}
			else if(content instanceof ParsedDeclaration)
			{}
			else if(content instanceof ParsedMethod)
			{
				ParsedMethod method = (ParsedMethod) content;
				if(!method.isMethod())
					throw new EvaluationException("Content expressions in a loop must be"
						+ " declarations, assignments or method calls", this, content.getMatch().index);
			}
			else if(content instanceof ParsedConstructor)
			{}
			else if(content instanceof ParsedLoop)
			{}
			else if(content instanceof ParsedIfStatement)
			{}
			else if(content instanceof ParsedKeyword)
			{
				String word = ((ParsedKeyword) content).getName();
				if(word.equals("continue"))
					return new prisms.lang.EvaluationResult(prisms.lang.EvaluationResult.ControlType.CONTINUE, null,
						content);
				if(word.equals("break"))
					return new prisms.lang.EvaluationResult(prisms.lang.EvaluationResult.ControlType.BREAK, null,
						content);
			}
			else if(content instanceof ParsedReturn)
			{
				if(withValues)
					return content.evaluate(scoped, false, withValues);
			}
			else if(content instanceof ParsedThrow)
			{}
			else
				throw new EvaluationException("Content expressions in a loop must be"
					+ " declarations, assignments or method calls", this, content.getMatch().index);
			content.evaluate(scoped, false, withValues);
		}
		return null;
	}

	/** @return The contents of this statement block */
	public prisms.lang.ParsedItem[] getContents()
	{
		return theContents;
	}
}
