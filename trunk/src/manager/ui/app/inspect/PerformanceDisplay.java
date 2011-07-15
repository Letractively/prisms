/*
 * PerformanceDisplay.java Created May 31, 2011 by Andrew Butler, PSL
 */
package manager.ui.app.inspect;

import org.json.simple.JSONObject;

/** Allows the performance data to be invisible initially and allow the user to dismiss the view */
public class PerformanceDisplay implements prisms.arch.AppPlugin
{
	private prisms.arch.PrismsSession theSession;

	private String theName;

	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		session.addPropertyChangeListener(manager.app.ManagerProperties.performanceData,
			new prisms.arch.event.PrismsPCL<prisms.util.ProgramTracker>()
			{
				public void propertyChange(
					prisms.arch.event.PrismsPCE<prisms.util.ProgramTracker> evt)
				{
					initClient();
				}

				@Override
				public String toString()
				{
					return "Manager Performance Data Display";
				}
			});
	}

	public void initClient()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setVisible");
		evt.put("visible", Boolean.valueOf(theSession
			.getProperty(manager.app.ManagerProperties.performanceData) != null));
		theSession.postOutgoingEvent(evt);
	}

	public void processEvent(JSONObject evt)
	{
		if("close".equals(evt.get("method")))
			theSession.setProperty(manager.app.ManagerProperties.performanceData, null);
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: "
				+ evt.get("method"));
	}
}
