/*
 * ParseStructRoot.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** The root of a parsed structure */
public class ParseStructRoot extends ParseStruct
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
}
