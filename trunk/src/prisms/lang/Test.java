/*
 * Test.java Created Nov 22, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** Allows dynamic testing of the prisms.lang functionality */
public class Test
{
	/**
	 * The test method
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		PrismsParser parser = new PrismsParser();
		try
		{
			parser.configure(prisms.arch.PrismsConfig.fromXml(
				null,
				prisms.arch.PrismsConfig.getRootElement("Grammar.xml",
					prisms.arch.PrismsConfig.getLocation(PrismsParser.class))));
		} catch(java.io.IOException e)
		{
			e.printStackTrace();
			return;
		}
		EvaluationEnvironment valEnv = new DefaultEvaluationEnvironment();
		EvaluationEnvironment rtEnv = new DefaultEvaluationEnvironment();
		java.util.Scanner scanner = new java.util.Scanner(System.in);
		String line = null;
		boolean complete = true;
		do
		{
			if(complete)
				System.out.print(">   ");
			else
				System.out.print("... ");
			String newLine = scanner.nextLine();
			if("exit".equals(newLine))
				break;
			if(complete)
				line = newLine;
			else
				line += "\n" + newLine;
			ParsedItem [] structs;
			try
			{
				ParseMatch [] matches = parser.parseMatches(line);
				ParseStructRoot root = new ParseStructRoot(line);
				structs = parser.parseStructures(root, matches);
				complete = true;
			} catch(IncompleteInputException e)
			{
				complete = false;
				continue;
			} catch(ParseException e)
			{
				complete = true;
				e.printStackTrace(System.out);
				continue;
			}
			for(ParsedItem s : structs)
			{
				try
				{
					s.evaluate(valEnv, false, false);
					EvaluationResult type = s.evaluate(rtEnv, false, true);
					if(type != null && !Void.TYPE.equals(type.getType()))
					{
						System.out.println("\t" + prisms.util.ArrayUtils.toString(type.getValue()));
						if(!(s instanceof prisms.lang.types.ParsedPreviousAnswer))
						{
							valEnv.addHistory(type.getType(), type.getValue());
							rtEnv.addHistory(type.getType(), type.getValue());
						}
					}
				} catch(EvaluationException e)
				{
					e.printStackTrace(System.out);
				}
			}
		} while(true);
	}
}
