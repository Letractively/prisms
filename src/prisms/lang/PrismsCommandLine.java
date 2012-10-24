/*
 * Test.java Created Nov 22, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** Allows dynamic testing of the prisms.lang functionality */
public class PrismsCommandLine
{
	private PrismsParser theParser;

	private EvaluationEnvironment theEnv;

	private String theIncomplete;

	/** Creates a tester for the prisms lang functionality */
	public PrismsCommandLine()
	{
		theParser = new PrismsParser();
		try
		{
			theParser.configure(prisms.arch.PrismsConfig.fromXml(null,
				prisms.arch.PrismsConfig.getRootElement("Grammar.xml", prisms.arch.PrismsConfig.getLocation(PrismsParser.class))));
		} catch(java.io.IOException e)
		{
			e.printStackTrace();
			return;
		}
		theParser.validateConfig();
		theEnv = new DefaultEvaluationEnvironment();
		theIncomplete = "";
	}

	/**
	 * Sets the value of a variable in this environment
	 * 
	 * @param name The name of the variable to set
	 * @param type The type of the variable to set
	 * @param isFinal Whether the variable is to be declared as final
	 * @param value The value for the variable
	 */
	public <T> void setVariable(String name, Class<T> type, boolean isFinal, T value)
	{
		EvaluationEnvironment.Variable var = theEnv.getDeclaredVariable(name);
		try
		{
			if(var == null)
				theEnv.declareVariable(name, new Type(type), isFinal, null, 0);
			else if(var.isFinal())
				throw new IllegalArgumentException("Variable " + name + " already exists and is declared final");
			else
			{
				if(type != null && !var.getType().canAssignTo(type))
					throw new IllegalArgumentException("Variable " + name + " already exists and is typed " + var.getType()
						+ "--incompatible with " + type.getName());
				if(type == null && value != null && !var.getType().isAssignableFrom(value.getClass()))
					throw new IllegalArgumentException("Variable " + name + " already exists and is typed " + var.getType()
						+ "--incompatible with " + value.getClass().getName());
			}
			theEnv.setVariable(name, value, null, 0);
		} catch(EvaluationException e)
		{
			throw new IllegalStateException("Could not set variable " + name, e);
		}
	}

	/** @param name The name of the variable to remove from this environment */
	public void dropVariable(String name)
	{
		try
		{
			if(theEnv.getDeclaredVariable(name) != null)
				theEnv.dropVariable(name, null, 0);
		} catch(EvaluationException e)
		{
			throw new IllegalStateException("Could not drop variable " + name, e);
		}
	}

	/**
	 * Alters this environment so that methods which throw checked exceptions may be called without requiring a try/catch block
	 */
	public void setHandleAllExceptions()
	{
		theEnv.setHandledExceptionTypes(new Type [] {new Type(Throwable.class)});
	}

	/** @return The EvaluationEnvironment used by this tester */
	public EvaluationEnvironment getEnv()
	{
		return theEnv;
	}

	/**
	 * Parses and evaluates a line of text. If the line ends with an incomplete entity, the line will be stored, a 0-length array will be
	 * returned, and the line prepended to the next line for parsing.
	 * 
	 * @param line The line to parse and evaluate
	 * @return An array consisting of either {@link EvaluationResult}s or {@link EvaluationException}, one for each entity parse in the line
	 * @throws ParseException If the line fails to parse
	 */
	public Object [] readLine(String line) throws ParseException
	{
		ParsedItem [] structs;
		try
		{
			if(theIncomplete.length() > 0)
				line = theIncomplete + "\n" + line;
			ParseMatch [] matches = theParser.parseMatches(line);
			if(matches.length == 0 || !matches[matches.length - 1].isComplete())
			{
				theIncomplete = line;
				return new Object [0];
			}
			ParseStructRoot root = new ParseStructRoot(line);
			structs = theParser.parseStructures(root, matches);
			theIncomplete = "";
		} catch(ParseException e)
		{
			theIncomplete = "";
			throw e;
		}
		Object [] ret = new Object [structs.length];
		for(int i = 0; i < ret.length; i++)
		{
			try
			{
				structs[i].evaluate(theEnv.transact(), false, false);
				EvaluationResult type = structs[i].evaluate(theEnv, false, true);
				ret[i] = type;
				if(type != null && !Void.TYPE.equals(type.getType()) && !(structs[i] instanceof prisms.lang.types.ParsedPreviousAnswer))
					theEnv.addHistory(type.getType(), type.getValue());
			} catch(EvaluationException e)
			{
				ret[i] = e;
			}
		}
		return ret;
	}

	/**
	 * @return Whether this test has not parsed anything or the last call to {@link #readLine(String)} parsed a complete item or failed to
	 *         parse
	 */
	public boolean isComplete()
	{
		return theIncomplete.length() == 0;
	}

	/**
	 * Runs the PrismsCommandLine test, reading from the given stream
	 * 
	 * @param stream The stream to read input from
	 */
	public void start(java.io.Reader stream)
	{
		try (java.util.Scanner scanner = new java.util.Scanner(stream))
		{
			do
			{
				if(isComplete())
					System.out.print(">   ");
				else
					System.out.print("... ");
				String newLine = scanner.nextLine();
				if("exit".equals(newLine))
					break;
				Object [] results;
				try
				{
					results = readLine(newLine);
				} catch(ParseException e)
				{
					e.printStackTrace(System.out);
					continue;
				}
				for(Object res : results)
				{
					if(res instanceof EvaluationException)
						((EvaluationException) res).printStackTrace(System.out);
					else
					{
						EvaluationResult evRes = (EvaluationResult) res;
						if(evRes != null && !evRes.getType().canAssignTo(Void.TYPE))
							System.out.println("\t" + prisms.util.ArrayUtils.toString(evRes.getValue()));
					}
				}
			} while(true);
		}
	}

	/**
	 * A shorthand method that wraps the given input stream in a reader and calls {@link #start(java.io.Reader)}
	 * 
	 * @param input The stream to read input from
	 */
	public void start(java.io.InputStream input)
	{
		start(new java.io.InputStreamReader(input));
	}

	/**
	 * The test method
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		PrismsCommandLine test = new PrismsCommandLine();
		test.start(System.in);
	}
}
