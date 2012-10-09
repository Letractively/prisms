/*
 * ParseStructRoot.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** The root of a parsed structure */
public class ParseStructRoot extends ParsedItem
{
	private String theFullCommand;

	/** @param fullCommand The full command that was parsed */
	public ParseStructRoot(String fullCommand)
	{
		theFullCommand = fullCommand;
	}

	/** @return The full command that was parsed */
	public String getFullCommand()
	{
		return theFullCommand;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		return new prisms.lang.ParsedItem [0];
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException {
		throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public EvaluationResult evaluate(EvaluationEnvironment env, boolean asType, boolean withValues)
		throws EvaluationException
	{
		return null;
	}
}
