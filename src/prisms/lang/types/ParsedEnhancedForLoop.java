package prisms.lang.types;

import prisms.lang.ParsedItem;
import prisms.lang.Type;

/** Represents an enhanced for loop */
public class ParsedEnhancedForLoop extends ParsedItem
{
	private ParsedDeclaration theVariable;

	private ParsedItem theIterable;

	private ParsedStatementBlock theContents;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		ParsedItem variable = parser.parseStructures(this, getStored("variable"))[0];
		if(!(variable instanceof ParsedDeclaration))
			throw new prisms.lang.ParseException("Enhanced for loop must begin with a declaration", getRoot().getFullCommand(),
				variable.getMatch().index);
		theVariable = (ParsedDeclaration) variable;
		theIterable = parser.parseStructures(this, getStored("iterable"))[0];
		ParsedItem content = parser.parseStructures(this, getStored("content"))[0];
		if(content instanceof ParsedStatementBlock)
			theContents = (ParsedStatementBlock) content;
		else
			theContents = new ParsedStatementBlock(parser, this, content.getMatch(), content);
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws prisms.lang.EvaluationException
	{
		prisms.lang.EvaluationEnvironment scoped = env.scope(true);
		theVariable.evaluate(scoped, false, withValues);
		final prisms.lang.EvaluationResult iterRes = theIterable.evaluate(scoped, false, withValues);
		if(withValues)
		{
			if(!iterRes.isValue())
				throw new prisms.lang.EvaluationException(iterRes.typeString() + " cannot be resolved to a variable", this,
					theIterable.getMatch().index);
			Type instanceType;
			if(Iterable.class.isAssignableFrom(iterRes.getType().getBaseType()))
				instanceType = iterRes.getType().resolve(Iterable.class.getTypeParameters()[0], Iterable.class, null,
					new java.lang.reflect.Type [0], new Type [0]);
			else if(iterRes.getType().isArray())
				instanceType = iterRes.getType().getComponentType();
			else
				throw new prisms.lang.EvaluationException("Can only iterate over an array or an instanceof java.lang.Iterable", this,
					theIterable.getMatch().index);

			Type type = theVariable.evaluateType(env);
			if(!type.isAssignable(instanceType))
				throw new prisms.lang.EvaluationException("Type mismatch: cannot convert from " + instanceType + " to " + type,
					theIterable, theIterable.getMatch().index);

			java.util.Iterator<?> iterator;
			if(iterRes.getValue() instanceof Iterable)
				iterator = ((Iterable<?>) iterRes.getValue()).iterator();
			else
			{
				class ArrayIterator implements java.util.Iterator<Object>
				{
					private int theIndex;

					private int theLength = java.lang.reflect.Array.getLength(iterRes.getValue());

					@Override
					public boolean hasNext()
					{
						return theIndex < theLength;
					}

					@Override
					public Object next()
					{
						return java.lang.reflect.Array.get(iterRes.getValue(), theIndex++);
					}

					@Override
					public void remove()
					{
						throw new UnsupportedOperationException();
					}
				}
				iterator = new ArrayIterator();
			}
			iterLoop: while(iterator.hasNext())
			{
				if(env.isCanceled())
					throw new prisms.lang.EvaluationException("User canceled execution", this, getMatch().index);
				Object value = iterator.next();
				scoped.setVariable(theVariable.getName(), value, theVariable, theVariable.getStored("name").index);
				prisms.lang.EvaluationResult res = theContents.evaluate(scoped, false, withValues);
				if(res != null && res.getControl() != null)
				{
					switch(res.getControl())
					{
					case RETURN:
						return res;
					case CONTINUE:
						continue;
					case BREAK:
						break iterLoop;
					}
				}
			}
		}
		else
			theContents.evaluate(scoped, asType, withValues);
		return null;
	}

	/** @return The variable that each instance the iterator returns will be assigned to */
	public ParsedDeclaration getVariable()
	{
		return theVariable;
	}

	/** @return The iterator to iterate over in the loop */
	public ParsedItem getIterable()
	{
		return theIterable;
	}

	/** @return The statements to be executed for each item in the iterator */
	public ParsedStatementBlock getContents()
	{
		return theContents;
	}

	@Override
	public ParsedItem [] getDependents()
	{
		return new ParsedItem [] {theVariable, theIterable, theContents};
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException
	{
		if(theVariable == dependent)
		{
			if(toReplace instanceof ParsedDeclaration)
				theVariable = (ParsedDeclaration) toReplace;
			else
				throw new IllegalArgumentException("Cannot replace the variable declaration of an enhanced for loop with "
					+ toReplace.getClass().getSimpleName());
		}
		else if(theIterable == dependent)
			theIterable = toReplace;
		else if(theContents == dependent)
		{
			if(toReplace instanceof ParsedStatementBlock)
				theContents = (ParsedStatementBlock) toReplace;
			else
				throw new IllegalArgumentException("Cannot replace the contents of a for loop with " + toReplace.getClass().getSimpleName());
		}
		else
			throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public String toString()
	{
		return new StringBuilder("for(").append(theVariable).append(" : ").append(theIterable).append('\n').append(theContents).toString();
	}
}
