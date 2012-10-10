/*
 * ParsedLoop.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import java.util.ArrayList;

import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;
import prisms.lang.ParsedItem;

/** Represents a loop */
public class ParsedLoop extends ParsedItem
{
	private ParsedItem [] theInits;

	private ParsedItem theCondition;

	private ParsedItem [] theIncrements;

	private ParsedStatementBlock theContents;

	private boolean isPreCondition;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		prisms.lang.ParseMatch conditionMatch = getStored("condition");
		if(conditionMatch != null)
			theCondition = parser.parseStructures(this, conditionMatch)[0];
		ArrayList<ParsedItem> inits = new ArrayList<ParsedItem>();
		ArrayList<ParsedItem> incs = new ArrayList<ParsedItem>();

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
				hasContent = true;
		}
		theInits = inits.toArray(new ParsedItem [inits.size()]);
		theIncrements = incs.toArray(new ParsedItem [incs.size()]);
		ParsedItem contentItem = parser.parseStructures(this, getStored("content"))[0];
		if(contentItem instanceof ParsedStatementBlock)
			theContents = (ParsedStatementBlock) contentItem;
		else if(contentItem != null)
			theContents = new ParsedStatementBlock(parser, this, contentItem.getMatch(), contentItem);
		else if(getMatch().isComplete())
			theContents = new ParsedStatementBlock(parser, this, getStored("terminal"));
	}

	/** @return Whether this loop's condition should be checked before the first execution of the contents */
	public boolean isPreCondition()
	{
		return isPreCondition;
	}

	/** @return The condition determining when the loop will stop executing */
	public ParsedItem getCondition()
	{
		return theCondition;
	}

	/** @return The set of statements to run before starting the loop */
	public ParsedItem [] getInits()
	{
		return theInits;
	}

	/** @return The set of statements to run in between execution of the loop's contents */
	public ParsedItem [] getIncrements()
	{
		return theIncrements;
	}

	/** @return The set of statements to run in the loop */
	public ParsedStatementBlock getContents()
	{
		return theContents;
	}

	@Override
	public ParsedItem [] getDependents()
	{
		String name = getStored("name").text;
		ArrayList<ParsedItem> ret = new ArrayList<ParsedItem>();
		if(!"do".equals(name))
		{
			for(int i = 0; i < theInits.length; i++)
				ret.add(theInits[i]);
			ret.add(theCondition);
			for(int i = 0; i < theIncrements.length; i++)
				ret.add(theIncrements[i]);
		}
		ret.add(theContents);
		if("do".equals(name))
			ret.add(theCondition);
		return ret.toArray(new prisms.lang.ParsedItem [ret.size()]);
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
		if(theCondition == dependent)
		{
			theCondition = toReplace;
			return;
		}
		if(theContents == dependent)
		{
			if(toReplace instanceof ParsedStatementBlock)
			{
				theContents = (ParsedStatementBlock) toReplace;
				return;
			}
			else
				throw new IllegalArgumentException("Cannot replace content block of loop with " + toReplace.getClass().getSimpleName());
		}
		for(int i = 0; i < theInits.length; i++)
			if(theInits[i] == dependent)
			{
				theInits[i] = toReplace;
				return;
			}
		for(int i = 0; i < theIncrements.length; i++)
			if(theIncrements[i] == dependent)
			{
				theIncrements[i] = toReplace;
				return;
			}
		throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues) throws EvaluationException
	{
		prisms.lang.EvaluationEnvironment scoped = env.scope(true);
		for(ParsedItem init : theInits)
		{
			if(init instanceof ParsedAssignmentOperator)
			{}
			else if(init instanceof ParsedDeclaration)
			{}
			else if(init instanceof ParsedMethod)
			{
				ParsedMethod method = (ParsedMethod) init;
				if(!method.isMethod())
					throw new EvaluationException("Initial expressions in a loop must be" + " declarations, assignments or method calls",
						this, init.getMatch().index);
			}
			else if(init instanceof ParsedConstructor)
			{}
			else
				throw new EvaluationException("Initial expressions in a loop must be" + " declarations, assignments or method calls", this,
					init.getMatch().index);
			init.evaluate(scoped, false, withValues);
		}
		ParsedItem condition = theCondition;
		EvaluationResult condRes = condition.evaluate(scoped, false, withValues && isPreCondition);
		if(condRes.isType() || condRes.getPackageName() != null)
			throw new EvaluationException(condRes.typeString() + " cannot be resolved to a variable", this, condition.getMatch().index);
		if(!Boolean.TYPE.equals(condRes.getType().getBaseType()))
			throw new EvaluationException("Type mismatch: cannot convert from " + condRes.typeString() + " to boolean", this,
				condition.getMatch().index);
		if(withValues && isPreCondition && !((Boolean) condRes.getValue()).booleanValue())
			return null;

		do
		{
			if(env.isCanceled())
				throw new prisms.lang.EvaluationException("User canceled execution", this, getMatch().index);
			EvaluationResult res = theContents.evaluate(scoped, false, withValues);
			if(res != null && withValues && res.getControl() == EvaluationResult.ControlType.RETURN)
				return res;
			for(ParsedItem inc : theIncrements)
			{
				if(inc instanceof ParsedAssignmentOperator)
				{}
				else if(inc instanceof ParsedMethod)
				{
					ParsedMethod method = (ParsedMethod) inc;
					if(!method.isMethod())
						throw new EvaluationException("Increment expressions in a loop must be" + " assignments or method calls", this,
							inc.getMatch().index);
				}
				else if(inc instanceof ParsedConstructor)
				{}
				else
					throw new EvaluationException("Increment expressions in a loop must be" + " assignments or method calls", this,
						inc.getMatch().index);
				inc.evaluate(scoped, false, withValues);
			}
			if(res != null && withValues && res.getControl() != null)
			{
				switch(res.getControl())
				{
				case RETURN: // Already checked this case above
				case BREAK:
					break;
				case CONTINUE:
					continue;
				}
			}
			if(withValues)
				condRes = condition.evaluate(scoped, false, true);
		} while(withValues && ((Boolean) condRes.getValue()).booleanValue());
		return null;
	}

	@Override
	public String toString()
	{
		String name = getStored("name").text;
		StringBuilder ret = new StringBuilder();
		ret.append(name);
		if("for".equals(name))
		{
			ret.append('(');
			for(int i = 0; i < theInits.length; i++)
			{
				if(i > 0)
					ret.append(", ");
				ret.append(theInits[i]);
			}
			ret.append(';');
			ret.append(theCondition);
			ret.append(';');
			for(int i = 0; i < theIncrements.length; i++)
			{
				if(i > 0)
					ret.append(", ");
				ret.append(theIncrements[i]);
			}
			ret.append(')');
		}
		else if("while".equals(name))
			ret.append('(').append(theCondition).append(')');
		ret.append('\n');
		ret.append(theContents);
		if("do".equals(name))
			ret.append("while)").append(theCondition).append(");");
		return ret.toString();
	}
}
