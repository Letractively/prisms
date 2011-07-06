/*
 * Log4jProperties.java Created Apr 17, 2009 by Andrew Butler, PSL
 */
package log4j.app;

import org.apache.log4j.Logger;

import prisms.arch.event.PrismsProperty;

/** Contains all properties used by the Log4j Configuration application */
public class Log4jProperties
{
	private Log4jProperties()
	{
	}

	/** The logger that the user has selected to edit */
	public static PrismsProperty<Logger> selectedLogger = PrismsProperty.create("selectedLogger",
		Logger.class);
}
