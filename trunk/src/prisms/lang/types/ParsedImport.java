/*
 * ParsedImport.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents an import command */
public class ParsedImport extends prisms.lang.ParsedItem<Void>
{
	private boolean isStatic;

	private boolean isWildcard;

	private prisms.lang.ParsedItem<?> theType;

	private String theMethodName;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem<?> parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		isStatic = getStored("static") != null;
		isWildcard = getStored("wildcard") != null;
		theType = parser.parseStructures(this, getStored("type"))[0];
		if(isStatic)
		{
			if(!isWildcard)
			{
				if(theType instanceof ParsedMethod)
				{
					ParsedMethod method = (ParsedMethod) theType;
					if(method.isMethod())
						throw new prisms.lang.ParseException("The import "
							+ theType.getMatch().text + " cannot be resolved", getRoot()
							.getFullCommand(), theType.getMatch().index);
					theType = method.getContext();
					theMethodName = method.getName();
				}
				else if(theType instanceof ParsedIdentifier)
					throw new prisms.lang.ParseException("The import " + theType.getMatch().text
						+ " cannot be resolved", getRoot().getFullCommand(),
						theType.getMatch().index);
				else
					throw new prisms.lang.ParseException("Syntax error: name expected", getRoot()
						.getFullCommand(), theType.getMatch().index);
			}
		}
	}

	/**
	 * @return Either the type that was imported (for a non-static, non-wildcard import), the type
	 *         to which belong the method or methods that were imported with a static import, or the
	 *         package to import for a non-static wildcard import.
	 */
	public prisms.lang.ParsedItem<?> getType()
	{
		return theType;
	}

	/**
	 * @return The name of the method being imported. Will be null unless this is a static,
	 *         non-wildcard import
	 */
	public String getMethodName()
	{
		return theMethodName;
	}

	/** @return Whether this is a static (method-level) import */
	public boolean isStatic()
	{
		return isStatic;
	}

	/**
	 * @return Whether this is a wildcard import, meaning it could potentially import more than one
	 *         type or method
	 */
	public boolean isWildcard()
	{
		return isWildcard;
	}

	@Override
	public prisms.lang.EvaluationResult<Void> evaluate(prisms.lang.EvaluationEnvironment env,
		boolean asType, boolean withValues) throws prisms.lang.EvaluationException
	{
		// TODO Auto-generated method stub
	}
}
