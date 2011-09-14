/*
 * PrismsMessageException.java Created Jan 6, 2011 by Andrew Butler, PSL
 */
package prisms.message;

/** Thrown when an error occurs retrieving or storing PRISMS messages */
public class PrismsMessageException extends Exception
{
	/** @see Exception#Exception(String) */
	public PrismsMessageException(String message)
	{
		super(message);
	}

	/** @see Exception#Exception(String, Throwable) */
	public PrismsMessageException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
