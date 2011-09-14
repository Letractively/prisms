/*
 * PrismsSqlException.java Created Apr 8, 2010 by Andrew Butler, PSL
 */
package prisms.osql;

/**
 * An exception thrown from classes in the prisms.osql package, typically in response to invalid
 * data given to the PRISMS OSQL API or a native SQL exception.
 */
public class PrismsSqlException extends Exception
{
	/** @param reason The message as to what caused the exception */
	public PrismsSqlException(String reason)
	{
		super(reason);
	}

	/**
	 * @param reason The message as to what cause the exception
	 * @param cause The thrown exception that caused this exception
	 */
	public PrismsSqlException(String reason, Throwable cause)
	{
		super(reason, cause);
	}
}
