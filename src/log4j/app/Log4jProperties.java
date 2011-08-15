/*
 * Log4jProperties.java Created Apr 17, 2009 by Andrew Butler, PSL
 */
package log4j.app;

import org.apache.log4j.Logger;

import prisms.arch.event.PrismsProperty;

/** Contains all properties used by the Log4j Configuration application */
public class Log4jProperties
{
	/** The constant to set in {@link Log4jProperties#search} to clear the log viewer */
	public static prisms.util.Search NO_SEARCH = new prisms.util.Search()
	{
		@Override
		public SearchType getType()
		{
			return null;
		}

		@Override
		public String toString()
		{
			return "No Search";
		}
	};

	private Log4jProperties()
	{
	}

	/** The logger that the user has selected to edit */
	public static PrismsProperty<Logger> selectedLogger = PrismsProperty.create("selectedLogger",
		Logger.class);

	/** The log searches stored by name for the user */
	public static PrismsProperty<NamedSearch []> searches = PrismsProperty.create(
		"logger/searches", NamedSearch [].class);

	/** The log search to display log entries of */
	public static PrismsProperty<prisms.util.Search> search = PrismsProperty.create(
		"logger/search", prisms.util.Search.class);
}
