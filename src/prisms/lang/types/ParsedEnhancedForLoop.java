package prisms.lang.types;

/** Represents an enhanced for loop */
public class ParsedEnhancedForLoop extends prisms.lang.ParsedItem
{
	private ParsedDeclaration theVariable;

	private prisms.lang.ParsedItem theIterable;

	private ParsedStatementBlock theContents;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		prisms.lang.ParsedItem variable = parser.parseStructures(this, getStored("variable"))[0];
		if(!(variable instanceof ParsedDeclaration))
			throw new prisms.lang.ParseException("Enhanced for loop must begin with a declaration", getRoot()
				.getFullCommand(), variable.getMatch().index);
		theVariable = (ParsedDeclaration) variable;
		theIterable = parser.parseStructures(this, getStored("iterable"))[0];
		prisms.lang.ParsedItem content = parser.parseStructures(this, getStored("content"))[0];
		if(content instanceof ParsedStatementBlock)
			theContents = (ParsedStatementBlock) content;
		else
			theContents = new ParsedStatementBlock(parser, this, content.getMatch(), content);
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		prisms.lang.EvaluationEnvironment scoped = env.scope(true);
		theVariable.evaluate(scoped, false, withValues);
		final prisms.lang.EvaluationResult iterRes = theIterable.evaluate(scoped, false, withValues);
		if(withValues)
		{
			if(!iterRes.isValue())
				throw new prisms.lang.EvaluationException(iterRes.typeString() + " cannot be resolved to a variable",
					this, theIterable.getMatch().index);
			prisms.lang.Type instanceType;
			if(Iterable.class.isAssignableFrom(iterRes.getType().getBaseType()))
				instanceType = iterRes.getType().resolve(Iterable.class.getTypeParameters()[0], Iterable.class);
			else if(iterRes.getType().isArray())
				instanceType = iterRes.getType().getComponentType();
			else
				throw new prisms.lang.EvaluationException(
					"Can only iterate over an array or an instanceof java.lang.Iterable", this,
					theIterable.getMatch().index);

			prisms.lang.EvaluationResult type = theVariable.getType().evaluate(env, true, false);
			if(!type.getType().isAssignable(instanceType))
				throw new prisms.lang.EvaluationException("Type mismatch: cannot convert from " + instanceType + " to "
					+ type.typeString(), theIterable, theIterable.getMatch().index);

			java.util.Iterator<?> iterator;
			if(iterRes.getValue() instanceof Iterable)
				iterator = ((Iterable<?>) iterRes.getValue()).iterator();
			else
			{
				class ArrayIterator implements java.util.Iterator<Object>
				{
					private int theIndex;

					private int theLength = java.lang.reflect.Array.getLength(iterRes.getValue());

					public boolean hasNext()
					{
						return theIndex < theLength;
					}

					public Object next()
					{
						return java.lang.reflect.Array.get(iterRes.getValue(), theIndex++);
					}

					public void remove()
					{
						throw new UnsupportedOperationException();
					}
				}
				iterator = new ArrayIterator();
			}
			iterLoop: while(iterator.hasNext())
			{
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
	public prisms.lang.ParsedItem getIterable()
	{
		return theIterable;
	}

	/** @return The statements to be executed for each item in the iterator */
	public ParsedStatementBlock getContents()
	{
		return theContents;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		return new prisms.lang.ParsedItem [] {theVariable, theIterable, theContents};
	}

	@Override
	public String toString()
	{
		return new StringBuilder("for(").append(theVariable).append(" : ").append(theIterable).append('\n')
			.append(theContents).toString();
	}
}
