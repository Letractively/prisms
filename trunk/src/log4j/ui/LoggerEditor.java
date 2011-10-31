/*
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
	private static final Logger log = Logger.getLogger(LoggerEditor.class);

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

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Logger Editor Selection Updater";
			}
		});
	}

	/** @return The session that is using this plugin */
	public PrismsSession getSession()
	{
		return theSession;
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

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setEnabled");
		evt.put("enabled", Boolean.valueOf(isEnabled()));
		theSession.postOutgoingEvent(evt);
		sendLogger();
	}

	public void processEvent(JSONObject evt)
	{
		if("levelChanged".equals(evt.get("method")))
		{
			if(theSelectedLogger == null)
				throw new IllegalStateException("No logger to set the level for");
			if(!isEnabled())
				throw new IllegalArgumentException("You do not have permission to modify loggers");
			String levelName = (String) evt.get("level");
			Level level = levelName == null ? null : Level.toLevel(levelName);
			if(level == null && levelName != null)
				throw new IllegalArgumentException("Unrecognized logger level " + levelName);
			try
			{
				theSession.getApp().getEnvironment().getLogger()
					.addLoggerConfig(theSelectedLogger.getName(), theSelectedLogger.getLevel());
				theSelectedLogger.setLevel(level);
			} catch(prisms.arch.PrismsException e)
			{
				log.error(
					"Could not persist configuration of logger " + theSelectedLogger.getName(), e);
			} catch(IllegalArgumentException e)
			{
				theSession.getUI().error(e.getMessage());
				return;
			}
			theSession.setProperty(selectedLogger, theSelectedLogger);
		}
		else if("printMessage".equals(evt.get("method")))
		{
			if(theSelectedLogger == null)
				throw new IllegalStateException("No logger to print a message to");
			String msg = (String) evt.get("message");
			msg = prisms.util.PrismsUtils.replaceAll(msg, "\\\n", "\n");
			msg = prisms.util.PrismsUtils.replaceAll(msg, "\\\\t", "\t");
			theSelectedLogger.log(theSelectedLogger.getEffectiveLevel(),
				"From Log4j Configuration Utility: " + msg);
		}
		else if("printException".equals(evt.get("method")))
		{
			if(theSelectedLogger == null)
				throw new IllegalStateException("No logger to print a message to");
			theSelectedLogger.log(theSelectedLogger.getEffectiveLevel(),
				"Text Exception From Log4j Configuration Utility", new Exception("Test"));
		}
		else if("printAttachment".equals(evt.get("method")))
		{
			if(theSelectedLogger == null)
				throw new IllegalStateException("No logger to print a message to");
			String location = theSession.getApp().getEnvironment().getLogger().getExposedDir();
			if(location == null)
			{
				theSession.getUI().error("No exposed directory");
				return;
			}
			String testFile;
			try
			{
				String name = "Test_File";
				testFile = name + ".txt";
				java.io.File tf = new java.io.File(location + testFile);
				for(int i = 2; tf.exists(); i++)
				{
					testFile = name + "(" + i + ").txt";
					tf = new java.io.File(location + testFile);
				}

				java.io.FileWriter writer = new java.io.FileWriter(tf);
				writer.write("This is a test file from Log4j configuration");
				writer.close();
			} catch(java.io.IOException e)
			{
				theSession.getUI().error("Could not create exposed file: " + e);
				return;
			}
			theSelectedLogger.log(theSelectedLogger.getEffectiveLevel(),
				"From Log4j Configuration Utility: " + evt.get("message") + " Check out "
					+ location + testFile);
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event " + evt);
	}

	boolean isEnabled()
	{
		return theSession.getPermissions().has("Edit Loggers");
	}

	void setLogger(Logger logger)
	{
		theSelectedLogger = logger;
		sendLogger();
	}

	void sendLogger()
	{
		JSONObject logger = null;
		if(theSelectedLogger != null)
		{
			logger = new JSONObject();
			logger.put("name", theSelectedLogger.getName());
			if(theSelectedLogger.getLevel() == null)
				logger.put("level", null);
			else
				logger.put("level", theSelectedLogger.getLevel().toString());
			logger.put("effectiveLevel", theSelectedLogger.getEffectiveLevel().toString());
		}
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setLogger");
		evt.put("logger", logger);
		theSession.postOutgoingEvent(evt);
	}
}
