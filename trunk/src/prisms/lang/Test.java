/*
 * Test.java Created Nov 22, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** Allows dynamic testing of the prisms.lang functionality */
public class Test
{
	private PrismsParser theParser;

	private EvaluationEnvironment theEnv;

	private String theIncomplete;

	/** Creates a tester for the prisms lang functionality */
	public Test()
	{
		theParser = new PrismsParser();
		try
		{
			theParser.configure(prisms.arch.PrismsConfig.fromXml(
				null,
				prisms.arch.PrismsConfig.getRootElement("Grammar.xml",
					prisms.arch.PrismsConfig.getLocation(PrismsParser.class))));
		} catch(java.io.IOException e)
		{
			e.printStackTrace();
			return;
		}
		theEnv = new DefaultEvaluationEnvironment();
		theIncomplete = "";
	}

	/** @return The EvaluationEnvironment used by this tester */
	public EvaluationEnvironment getEnv()
	{
		return theEnv;
	}

	/**
	 * Parses and evaluates a line of text. If the line ends with an incomplete entity, the line will be stored, a
	 * 0-length array will be returned, and the line prepended to the next line for parsing.
	 * 
	 * @param line The line to parse and evaluate
	 * @return An array consisting of either {@link EvaluationResult}s or {@link EvaluationException}, one for each
	 *         entity parse in the line
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
				if(type != null && !Void.TYPE.equals(type.getType())
					&& !(structs[i] instanceof prisms.lang.types.ParsedPreviousAnswer))
					theEnv.addHistory(type.getType(), type.getValue());
			} catch(EvaluationException e)
			{
				ret[i] = e;
			}
		}
		return ret;
	}

	/**
	 * @return Whether this test has not parsed anything or the last call to {@link #readLine(String)} parsed a complete
	 *         item or failed to parse
	 */
	public boolean isComplete()
	{
		return theIncomplete.length() == 0;
	}

	/**
	 * The test method
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		Test test = new Test();

		try (java.util.Scanner scanner = new java.util.Scanner(System.in))
		{
			do
			{
				if(test.isComplete())
					System.out.print(">   ");
				else
					System.out.print("... ");
				String newLine = scanner.nextLine();
				if("exit".equals(newLine))
					break;
				Object [] results;
				try
				{
					results = test.readLine(newLine);
				} catch(ParseException e)
				{
					e.printStackTrace(System.out);
					continue;
				}
				for(Object res : results)
				{
					if(res instanceof EvaluationException)
					{
						((EvaluationException) res).printStackTrace(System.out);
					}
					else
					{
						EvaluationResult evRes = (EvaluationResult) res;
						if(evRes != null && !Void.TYPE.equals(evRes.getType()))
							System.out.println("\t" + prisms.util.ArrayUtils.toString(evRes.getValue()));
					}
				}
			} while(true);
		}
	}
}
