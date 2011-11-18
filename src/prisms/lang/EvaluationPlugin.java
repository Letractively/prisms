/*
 * EvaluationPlugin.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

import org.json.simple.JSONObject;

/**
 * Allows users to interpret scripts from a web interface **TODO** This plugin is not complete or
 * tested
 */
public class EvaluationPlugin implements prisms.arch.AppPlugin
{
	private prisms.arch.PrismsSession theSession;

	private String theName;

	private prisms.arch.PrismsConfig theConfig;

	private PrismsParser theParser;

	private PrismsEvaluator theEvaluator;

	private EvaluationEnvironment theEnvironment;

	private java.util.ArrayList<String> theExpressionHistory;

	private int thePreviousIndex;

	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		theConfig = config;
		theExpressionHistory = new java.util.ArrayList<String>();
		theParser = createParser();
		theEvaluator = createEvaluator();
		theEnvironment = createEnv();
		thePreviousIndex = -1;
	}

	public void initClient()
	{
		// TODO Auto-generated method stub

	}

	/** @return This plugin's session */
	public prisms.arch.PrismsSession getSession()
	{
		return theSession;
	}

	/** @return This plugin's name */
	public String getName()
	{
		return theName;
	}

	/** @return This plugin's configuration */
	public prisms.arch.PrismsConfig getConfig()
	{
		return theConfig;
	}

	/** @return The parser that this plugin should use */
	protected PrismsParser createParser()
	{
		PrismsParser parser = new PrismsParser();
		parser.configure(theConfig.subConfig("grammar"));
		return parser;
	}

	/** @return The parser that this plugin is using */
	public PrismsParser getParser()
	{
		return theParser;
	}

	/** @return The evaluator that this plugin should use */
	protected PrismsEvaluator createEvaluator()
	{
		return new DefaultJavaEvaluator();
	}

	/** @return The evaluator that this plugin is using */
	public PrismsEvaluator getEvaluator()
	{
		return theEvaluator;
	}

	/** @return The evaluation environment that this plugin should use */
	protected EvaluationEnvironment createEnv()
	{
		return new DefaultEvaluationEnvironment();
	}

	/** @return The evaluation environment that this plugin is using */
	public EvaluationEnvironment getEnv()
	{
		return theEnvironment;
	}

	public void processEvent(JSONObject evt)
	{
		if("evaluate".equals(evt.get("method")))
		{
			String expr = (String) evt.get("expression");
			if(expr.trim().length() == 0)
				return;
			prisms.ui.UI.DefaultProgressInformer pi = new prisms.ui.UI.DefaultProgressInformer();
			pi.setProgressText("Parsing Expression");
			theSession.getUI().startTimedTask(pi);
			try
			{
				ParsedItem [] structs;
				try
				{
					ParseMatch [] matches = theParser.parseMatches(expr);
					ParseStructRoot root = new ParseStructRoot(expr);
					structs = theParser.parseStructures(root, matches);
				} catch(IncompleteInputException e)
				{
					return;
				} catch(ParseException e)
				{
					thePreviousIndex = -1;
					theExpressionHistory.add(expr);
					JSONObject toSend = new JSONObject();
					toSend.put("plugin", theName);
					toSend.put("method", "error");
					toSend.put("message", e.getMessage());
					toSend.put("line", Integer.valueOf(e.getLine()));
					toSend.put("char", Integer.valueOf(e.getChar()));
					theSession.postOutgoingEvent(toSend);
					return;
				}
				thePreviousIndex = -1;
				theExpressionHistory.add(expr);
				if(structs.length == 1)
					pi.setProgressText("Evaluating Expression");
				for(int s = 0; s < structs.length; s++)
				{
					pi.setProgressText("Evaluating Expression " + (s + 1) + " of " + structs.length);
					try
					{
						prisms.lang.PrismsEvaluator.EvalResult type = theEvaluator.evaluate(
							structs[s], theEnvironment, false, true);
						if(type != null)
							theEnvironment.addHistory(type.getType(), type.getValue());
						JSONObject toSend = new JSONObject();
						toSend.put("plugin", theName);
						toSend.put("method", "setAnswer");
						toSend.put("answer", type == null ? null : String.valueOf(type.getValue()));
						theSession.postOutgoingEvent(toSend);
					} catch(EvaluationException e)
					{
						JSONObject toSend = new JSONObject();
						toSend.put("plugin", theName);
						toSend.put("method", "error");
						toSend.put("message", e.getMessage());
						toSend.put("line", Integer.valueOf(e.getLine()));
						toSend.put("char", Integer.valueOf(e.getChar()));
						theSession.postOutgoingEvent(toSend);
					}
				}
			} finally
			{
				pi.setDone();
			}
		}
		else if("getPrevious".equals(evt.get("method")))
		{
			if(thePreviousIndex < theExpressionHistory.size())
				thePreviousIndex++;
			if(thePreviousIndex < 0)
				return;
			JSONObject toSend = new JSONObject();
			toSend.put("plugin", theName);
			toSend.put("method", "setCurrent");
			toSend.put("previous", theExpressionHistory.get(thePreviousIndex));
			theSession.postOutgoingEvent(toSend);
		}
		else if("getNext".equals(evt.get("method")))
		{
			if(thePreviousIndex > 0)
				thePreviousIndex--;
			if(thePreviousIndex < 0)
				return;
			JSONObject toSend = new JSONObject();
			toSend.put("plugin", theName);
			toSend.put("method", "setCurrent");
			toSend.put("previous", theExpressionHistory.get(thePreviousIndex));
			theSession.postOutgoingEvent(toSend);
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}
}
