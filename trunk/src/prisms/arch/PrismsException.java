/*
 * PrismsException.java Created Dec 3, 2009 by Andrew Butler, PSL
 */
package prisms.arch;

/** Thrown whenever a PRISMS-specific problem occurs */
public class PrismsException extends java.io.IOException
{
	/** @see java.io.IOException#IOException(String) */
	public PrismsException(String s)
	{
		super(s);
	}

	/** @see Exception#Exception(String, Throwable) */
	public PrismsException(String s, Throwable cause)
	{
		this(s);
		initCause(cause);
	}
}
