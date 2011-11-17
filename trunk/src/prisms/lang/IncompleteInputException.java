/*
 * IncompleteInputException.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** Thrown when parsing cannot be completed because the input runs out */
public class IncompleteInputException extends ParseException
{
	/** @see ParseException#ParseException(String, String, int) */
	public IncompleteInputException(String message, String command, int index)
	{
		super(message, command, index);
	}

}
