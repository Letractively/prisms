/*
 * PrismsRecordException.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/**
 * Thrown when an error occurs with PRISMS record keeping or synchronization
 */
public class PrismsRecordException extends Exception
{
	/**
	 * @see Exception#Exception(String)
	 */
	public PrismsRecordException(String message)
	{
		super(message);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @see Exception#Exception(String, Throwable)
	 */
	public PrismsRecordException(String message, Throwable cause)
	{
		super(message, cause);
		// TODO Auto-generated constructor stub
	}
}
