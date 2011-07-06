/**
 * LoggerEditor.java Created Apr 17, 2009 by Andrew Butler, PSL
 */
package log4j.ui;

import static log4j.app.Log4jProperties.selectedLogger;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;

/** Allows the user to edit a Logger */
public class LoggerEditor implements prisms.arch.AppPlugin
{
	private PrismsSession theSession;

	private String theName;

	private Logger theSelectedLogger;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		Logger selected = session.getProperty(selectedLogger);
		setLogger(selected);
		session.addPropertyChangeListener(selectedLogger, new prisms.arch.event.PrismsPCL<Logger>()
		{
			public void propertyChange(prisms.arch.event.PrismsPCE<Logger> evt)
			{
				setLogger(evt.getNewValue());
			}
		});
	}

	public void initClient()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setLevels");
		JSONArray levels = new JSONArray();
		// levels.add(Level.TRACE.toString());
		levels.add(Level.DEBUG.toString());
		levels.add(Level.INFO.toString());
		levels.add(Level.WARN.toString());
		levels.add(Level.ERROR.toString());
		levels.add(Level.FATAL.toString());
		evt.put("levels", levels);
		theSession.postOutgoingEvent(evt);
		sendLogger();
	}

	public void processEvent(JSONObject evt)
	{
		if("levelChanged".equals(evt.get("method")))
		{
			if(theSelectedLogger == null)
				throw new IllegalStateException("No logger to set the level for");
			String level = (String) evt.get("level");
			if(level == null)
				theSelectedLogger.setLevel(null);
			// else if(Level.TRACE.toString().equals(level))
			// theSelectedLogger.setLevel(Level.TRACE);
			else if(Level.DEBUG.toString().equals(level))
				theSelectedLogger.setLevel(Level.DEBUG);
			else if(Level.INFO.toString().equals(level))
				theSelectedLogger.setLevel(Level.INFO);
			else if(Level.WARN.toString().equals(level))
				theSelectedLogger.setLevel(Level.WARN);
			else if(Level.ERROR.toString().equals(level))
				theSelectedLogger.setLevel(Level.ERROR);
			else if(Level.FATAL.toString().equals(level))
				theSelectedLogger.setLevel(Level.FATAL);
			else
				throw new IllegalArgumentException("Unrecognized logger level " + level);
			theSession.setProperty(selectedLogger, theSelectedLogger);
		}
		else if("additivityChanged".equals(evt.get("method")))
		{
			if(theSelectedLogger == null)
				throw new IllegalStateException("No logger to set the additivity for");
			theSelectedLogger.setAdditivity(((Boolean) evt.get("additivity")).booleanValue());
			theSession.setProperty(selectedLogger, theSelectedLogger);
		}
		else if("printMessage".equals(evt.get("method")))
		{
			if(theSelectedLogger == null)
				throw new IllegalStateException("No logger to print a message to");
			theSelectedLogger.log(theSelectedLogger.getEffectiveLevel(),
				"From Log4j Configuration Utility: " + evt.get("message"));
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event " + evt);
	}

	void setLogger(Logger log)
	{
		theSelectedLogger = log;
		sendLogger();
	}

	void sendLogger()
	{
		JSONObject log = null;
		if(theSelectedLogger != null)
		{
			log = new JSONObject();
			log.put("name", theSelectedLogger.getName());
			if(theSelectedLogger.getLevel() == null)
				log.put("level", null);
			else
				log.put("level", theSelectedLogger.getLevel().toString());
			log.put("effectiveLevel", theSelectedLogger.getEffectiveLevel().toString());
			log.put("additivity", Boolean.valueOf(theSelectedLogger.getAdditivity()));
		}
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setLogger");
		evt.put("logger", log);
		theSession.postOutgoingEvent(evt);
	}
}
