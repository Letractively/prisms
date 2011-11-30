package prisms.lang;

/**
 * Instances of this class are thrown when the code being executed is syntactically and semantically correct, but an
 * exception is thrown at run time by the executing code itself
 */
public class ExecutionException extends EvaluationException
{
	/** @see EvaluationException#EvaluationException(String, Throwable, ParsedItem, int) */
	public ExecutionException(String message, Throwable cause, ParsedItem struct, int index)
	{
		super(message, cause, struct, index);
	}
}
