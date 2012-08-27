/*
 * EvaluationException.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/**
 * Thrown from {@link ParsedItem#evaluate(EvaluationEnvironment, boolean, boolean)} if evaluation of a parsed structure
 * fails
 */
public class EvaluationException extends Exception
{
	private ParsedItem theStruct;

	private int theIndex;

	private int theLine;

	private int theChar;

	/**
	 * Creates an evaluation exception without a cause
	 * 
	 * @param message The message as to what went wrong with the evaluation
	 * @param struct The parsed structure where the evaluation failed
	 * @param index The index within the full command where the problem occurred
	 */
	public EvaluationException(String message, ParsedItem struct, int index)
	{
		super(message);
		theStruct = struct;
		theIndex = index;
		if(struct != null)
		{
			String command = struct.getRoot().getFullCommand();
			for(int c = 0; c < index; c++)
			{
				if(command.charAt(c) == '\n')
				{
					theLine++;
					theChar = 0;
				}
				else if(command.charAt(c) >= ' ')
					theChar++;
			}
		}
	}

	/**
	 * Creates an evaluation exception with a cause
	 * 
	 * @param message The message as to what went wrong with the evaluation
	 * @param cause The exception that caused this evaluation exception
	 * @param struct The parsed structure where the evaluation failed
	 * @param index The index within the full command where the problem occurred
	 */
	public EvaluationException(String message, Throwable cause, ParsedItem struct, int index)
	{
		super(message, cause);
		theStruct = struct;
		theIndex = index;
		if(struct != null)
		{
			String command = struct.getRoot().getFullCommand();
			for(int c = 0; c < index; c++)
			{
				if(command.charAt(c) == '\n')
				{
					theLine++;
					theChar = 0;
				}
				else if(command.charAt(c) >= ' ')
					theChar++;
			}
		}
	}

	/** @return The parsed structure where the evaluation failed */
	public ParsedItem getStruct()
	{
		return theStruct;
	}

	/** @return The index within the full command where the problem occurred */
	public int getIndex()
	{
		return theIndex;
	}

	/** @return The line number where evaluation failed */
	public int getLine()
	{
		return theLine;
	}

	/** @return The character number on the line where evaluation failed */
	public int getChar()
	{
		return theChar;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append(super.toString());
		if(theIndex >= 0)
		{
			ret.append(" Line ").append(theLine + 1).append(", char ").append(theChar + 1).append("\n\t");
			int line = 0;
			int c;
			String command = theStruct.getRoot().getFullCommand();
			for(c = 0; line < theLine; c++)
				if(command.charAt(c) == '\n')
					line++;
			for(; c < command.length() && command.charAt(c) != '\n'; c++)
				ret.append(command.charAt(c));
			ret.append("\n\t");
			for(int i = 0; i < theChar; i++)
				ret.append(' ');
			ret.append('^');
		}
		return ret.toString();
	}
}
