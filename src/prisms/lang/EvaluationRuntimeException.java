/*
 * EvaluationRuntimeException.java Created Nov 18, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/**
 * Thrown when the evaluation of an expression throws an exception. Instances of this class are NOT
 * thrown when the syntax or semantics of the command are wrong, but only when the evaluation itself
 * throws an exception.
 */
public class EvaluationRuntimeException extends EvaluationException
{
	/** @see EvaluationException#EvaluationException(String, ParsedItem, int) */
	public EvaluationRuntimeException(String message, ParsedItem struct, int index)
	{
		super(message, struct, index);
	}

	/** @see EvaluationException#EvaluationException(String, Throwable, ParsedItem, int) */
	public EvaluationRuntimeException(String message, Throwable cause, ParsedItem struct, int index)
	{
		super(message, cause, struct, index);
	}
}
