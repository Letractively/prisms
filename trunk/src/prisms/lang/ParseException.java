/*
 * ParseException.java Created Nov 10, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/**
 * Thrown by {@link PrismsParser} or implementations of {@link ParsedItem#setup(PrismsParser, ParsedItem, ParseMatch)
 * ParseStruct.setup()} in response to syntax errors in text to be parsed
 */
public class ParseException extends Exception
{
	private final String theCommand;

	private final int theIndex;

	private int theLine;

	private int theChar;

	/**
	 * Creates a parse exception with no cause
	 * 
	 * @param message The message as to why parsing failed
	 * @param command The command whose parsing faildd
	 * @param index The index of the section of text where parsing failed
	 */
	public ParseException(String message, String command, int index)
	{
		super(message);
		theCommand = command;
		theIndex = index;
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

	/**
	 * Creates a parse exception with a cause
	 * 
	 * @param message The message as to why parsing failed
	 * @param cause The cause of the parsing failure
	 * @param command The command whose parsing faildd
	 * @param index The index of the section of text where parsing failed
	 */
	public ParseException(String message, Throwable cause, String command, int index)
	{
		super(message, cause);
		theCommand = command;
		theIndex = index;
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

	/** @return The command that failed to parse */
	public String getCommand()
	{
		return theCommand;
	}

	/** @return The index of the section of text where parsing failed */
	public int getIndex()
	{
		return theIndex;
	}

	/** @return The line number where parsing failed */
	public int getLine()
	{
		return theLine;
	}

	/** @return The character number on the line where parsing failed */
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
			for(c = 0; line < theLine; c++)
				if(theCommand.charAt(c) == '\n')
					line++;
			for(; c < theCommand.length() && theCommand.charAt(c) != '\n'; c++)
				ret.append(theCommand.charAt(c));
			ret.append("\n\t");
			for(int i = 0; i < theChar; i++)
				ret.append(' ');
			ret.append('^');
		}
		return ret.toString();
	}
}
