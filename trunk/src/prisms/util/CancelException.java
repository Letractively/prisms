/**
 * CancelException.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * To be thrown from executing code when the user cancels an operation
 */
public class CancelException extends RuntimeException
{
	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public CancelException()
	{
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public CancelException(String message)
	{
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException(Throwable)
	 */
	public CancelException(Throwable cause)
	{
		super(cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public CancelException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
