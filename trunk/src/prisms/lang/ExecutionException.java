package prisms.lang;

/**
 * Instances of this class are thrown when the code being executed is syntactically and semantically correct, but an
 * exception is thrown at run time by the executing code itself
 */
public class ExecutionException extends EvaluationException
{
	private final Type theType;

	/**
	 * @param type The type of the exception thrown
	 * @param cause The exception thrown
	 * @param struct The code where the exception was thrown
	 * @param index The location in the code where the exception was thrown
	 */
	public ExecutionException(Type type, Throwable cause, ParsedItem struct, int index)
	{
		super(cause.getMessage(), cause, struct, index);
		theType = type;
	}

	/** @return The type of the exception thrown */
	public Type getType()
	{
		return theType;
	}
}
