package prisms.lang.types;

import prisms.lang.EvaluationResult;
import prisms.lang.ParsedItem;

/** Represents a switch/case statement */
public class ParsedSwitch extends ParsedItem
{
	private ParsedItem theVariable;

	private ParsedItem [] theCases;

	private ParsedItem [][] theCaseBlocks;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theVariable = parser.parseStructures(this, getStored("variable"))[0];
		java.util.ArrayList<ParsedItem> caseStmts = new java.util.ArrayList<ParsedItem>();
		boolean hasDefault = false;
		theCases = new ParsedItem [0];
		theCaseBlocks = new ParsedItem [0] [];
		for(prisms.lang.ParseMatch m : matches())
		{
			if("case".equals(m.config.get("storeAs")) || "default".equals(m.config.get("storeAs")))
			{
				if(theCases.length > 0)
					theCaseBlocks = prisms.util.ArrayUtils.add(theCaseBlocks, caseStmts.toArray(new ParsedItem [caseStmts.size()]));
				caseStmts.clear();
				if("case".equals(m.config.get("storeAs")))
					theCases = prisms.util.ArrayUtils.add(theCases, parser.parseStructures(this, m)[0]);
				else if(hasDefault)
					throw new prisms.lang.ParseException("Only one default statement allowed in a switch", getRoot().getFullCommand(),
						m.index);
				else
				{
					theCases = prisms.util.ArrayUtils.add(theCases, null);
					hasDefault = true;
				}
			}
			else if(theCases.length > 0 && m.config.getName().equals("op"))
				caseStmts.add(parser.parseStructures(this, m)[0]);
		}
		if(theCases.length > theCaseBlocks.length)
			theCaseBlocks = prisms.util.ArrayUtils.add(theCaseBlocks, caseStmts.toArray(new ParsedItem [caseStmts.size()]));
	}

	@Override
	public ParsedItem [] getDependents()
	{
		java.util.ArrayList<ParsedItem> ret = new java.util.ArrayList<ParsedItem>();
		ret.add(theVariable);
		for(int c = 0; c < theCases.length; c++)
		{
			if(theCases[c] != null)
				ret.add(theCases[c]);
			for(ParsedItem pi : theCaseBlocks[c])
				ret.add(pi);
		}
		return ret.toArray(new ParsedItem [ret.size()]);
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
		if(theVariable == dependent)
		{
			theVariable = toReplace;
			return;
		}
		for(int i = 0; i < theCases.length; i++)
		{
			if(theCases[i] == dependent)
			{
				theCases[i] = toReplace;
				return;
			}
			for(int j = 0; j < theCaseBlocks[i].length; j++)
			{
				if(theCaseBlocks[i][j] == dependent)
				{
					theCaseBlocks[i][j] = toReplace;
					return;
				}
			}
		}
		throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws prisms.lang.EvaluationException
		{
		EvaluationResult var = theVariable.evaluate(env, false, withValues);
		if(var.getPackageName() != null || var.isType())
			throw new prisms.lang.EvaluationException(var.getFirstVar() + " cannot be resolved to a variable", this,
				theVariable.getMatch().index);
		if(withValues && var.getValue() == null)
			throw new prisms.lang.ExecutionException(new prisms.lang.Type(NullPointerException.class), new NullPointerException(
				"Variable in switch statement may not be null"), this, theVariable.getMatch().index);

		Enum<?> [] consts = null;
		if(var.getType().canAssignTo(Enum.class))
			consts = (Enum<?> []) var.getType().getBaseType().getEnumConstants();
		else if(var.getType().canAssignTo(Integer.TYPE) || var.getType().canAssignTo(String.class))
		{}
		else
			throw new prisms.lang.EvaluationException("Only int-convertible types, strings and enums may be used in switch statements",
				this, theVariable.getMatch().index);

		prisms.lang.EvaluationEnvironment scoped = env.scope(true);
		boolean match = false;
		for(int c = 0; c < theCases.length; c++)
		{
			if(theCases[c] == null) // Default case
				continue;
			ParsedItem case_c = theCases[c];
			while(case_c instanceof ParsedParenthetic)
				case_c = ((ParsedParenthetic) case_c).getContent();
			if(!match || !withValues)
			{
				if(consts != null)
				{
					if(case_c instanceof ParsedIdentifier)
					{
						ParsedIdentifier id = (ParsedIdentifier) case_c;
						boolean found = false;
						for(Enum<?> con : consts)
							if(con.name().equals(id.getName()))
							{
								found = true;
								match = con == var.getValue();
								if(withValues)
									break;
							}
						if(!found)
							throw new prisms.lang.EvaluationException("No such constant " + id.getName() + " in enum " + var.getType(),
								theCases[c], theCases[c].getMatch().index);
					}
					else if(case_c instanceof ParsedMethod)
					{
						ParsedMethod m = (ParsedMethod) theCases[c];
						if(m.isMethod())
							throw new prisms.lang.EvaluationException("The value in a case statement must be a constant", case_c,
								case_c.getMatch().index);
						EvaluationResult caseTypeRes = ((ParsedMethod) case_c).getContext().evaluate(env, true, true);
						if(!caseTypeRes.isType())
							throw new prisms.lang.EvaluationException("The value in a case statement must be a constant", case_c,
								case_c.getMatch().index);
						if(!var.getType().isAssignable(caseTypeRes.getType()))
							throw new prisms.lang.EvaluationException("The value in an enum case statement must be one of the enum values",
								case_c, case_c.getMatch().index);
						boolean found = false;
						for(Enum<?> con : consts)
							if(con.name().equals(m.getName()))
							{
								found = true;
								match = con == var.getValue();
								if(withValues)
									break;
							}
						if(!found)
							throw new prisms.lang.EvaluationException("No such constant " + m.getName() + " in enum " + var.getType(),
								case_c, case_c.getMatch().index);
					}
					else
						throw new prisms.lang.EvaluationException("The value in a case statement must be a constant", this,
							case_c.getMatch().index);
				}
				else if(case_c instanceof ParsedString || case_c instanceof ParsedNumber || case_c instanceof ParsedChar)
				{
					EvaluationResult caseRes = case_c.evaluate(env, false, withValues);
					if(!var.getType().isAssignable(caseRes.getType()))
						throw new prisms.lang.EvaluationException("type mismatch: cannot convert from " + caseRes.getType() + " to "
							+ var.getType(), case_c, case_c.getMatch().index);
					match = var.getValue().equals(caseRes.getValue());
				}
				else if(case_c instanceof ParsedNull)
					throw new prisms.lang.EvaluationException("Null not allowed for case statement", this, case_c.getMatch().index);
				else if(case_c instanceof ParsedBoolean)
				{
					throw new prisms.lang.EvaluationException("type mismatch: cannot convert from boolean to " + var.getType(), case_c,
						case_c.getMatch().index);
				}
				else
					throw new prisms.lang.EvaluationException("The value in a case statement must be a constant", this,
						case_c.getMatch().index);
			}
			if(match || !withValues)
			{
				for(ParsedItem pi : theCaseBlocks[c])
				{
					EvaluationResult res = ParsedStatementBlock.executeJavaStatement(pi, scoped, withValues);
					if(withValues && res != null && res.getControl() != null)
						switch(res.getControl())
						{
						case RETURN:
							return res;
						case BREAK:
							return null;
						case CONTINUE:
							throw new prisms.lang.EvaluationException("continue outside of loop", pi, pi.getMatch().index);
						}
				}
			}
		}
		if(!match || !withValues)
		{
			for(int c = 0; c < theCases.length; c++)
			{
				if(theCases[c] != null)
					continue;
				for(ParsedItem pi : theCaseBlocks[c])
				{
					EvaluationResult res = ParsedStatementBlock.executeJavaStatement(pi, scoped, withValues);
					if(withValues && res != null && res.getControl() != null)
						switch(res.getControl())
						{
						case RETURN:
							return res;
						case BREAK:
							return null;
						case CONTINUE:
							return res;
						}
				}
				break;
			}
		}
		return null;
		}
}
