/**
 * UserActionAlerter.java Created Feb 19, 2009 by Andrew Butler, PSL
 */
package prisms.ui;

/**
 * Listens to the "getUserActions" event generated by some tree and list nodes, adds a configured
 * menu item (action), and fires a configured event when the action is selected
 */
public class UserActionAlerter implements prisms.arch.event.SessionMonitor
{
	prisms.arch.PrismsSession theSession;

	private String [] thePlugins;

	String theActionName;

	boolean requiresListeners;

	private String theEventName;

	private String [] theEventPropertiesIn;

	private String [] theEventPropertiesOut;

	private Class<?> [] theEventPropertyClasses;

	private boolean [] theEventPropertyRequired;

	public void register(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		thePlugins = config.getAll("activatePlugins/plugin");
		if(thePlugins.length == 0)
			throw new IllegalArgumentException("No plugins configured: " + config);
		for(int p = 0; p < thePlugins.length; p++)
			if(thePlugins[p].length() == 0)
				throw new IllegalArgumentException("Plugin element is empty: " + config);
		theActionName = config.get("actionname");
		if(theActionName == null)
			throw new IllegalArgumentException("No actionname configured: " + config);
		requiresListeners = !Boolean.FALSE.equals(config.is("requireListeners"));
		theEventName = config.get("eventname");
		if(theEventName == null)
			throw new IllegalArgumentException("No eventname configured: " + config);

		prisms.arch.PrismsConfig[] propConfigs = config.subConfigs("properties/property");
		theEventPropertiesIn = new String [propConfigs.length];
		theEventPropertiesOut = new String [propConfigs.length];
		theEventPropertyClasses = new Class [propConfigs.length];
		theEventPropertyRequired = new boolean [propConfigs.length];
		for(int p = 0; p < propConfigs.length; p++)
		{
			theEventPropertiesIn[p] = propConfigs[p].get("name");
			theEventPropertiesOut[p] = propConfigs[p].get("newname");
			if(theEventPropertiesIn[p] == null || theEventPropertiesIn[p].length() == 0)
				throw new IllegalArgumentException("event property not properly configured: "
					+ config);
			try
			{
				theEventPropertyClasses[p] = propConfigs[p].getClass("class", Object.class);
			} catch(ClassNotFoundException e)
			{
				throw new IllegalArgumentException("Class " + propConfigs[p].get("class")
					+ " not found for property " + theEventPropertiesIn[p] + ": " + config, e);
			} catch(NoClassDefFoundError e)
			{
				throw new IllegalArgumentException("Class " + propConfigs[p].get("class")
					+ " not found for property " + theEventPropertiesIn[p] + ": " + config, e);
			}
			theEventPropertyRequired[p] = !Boolean.FALSE.equals(propConfigs[p].is("required"));
		}

		session.addEventListener("getUserActions", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.PrismsSession session2,
				prisms.arch.event.PrismsEvent evt)
			{
				final prisms.arch.event.PrismsEvent toFire = getEventToFire(evt);
				if(toFire == null)
					return;
				evt.setProperty("actions", prisms.util.ArrayUtils.add(
					(javax.swing.Action[]) evt.getProperty("actions"),
					new javax.swing.AbstractAction(theActionName)
					{
						public void actionPerformed(java.awt.event.ActionEvent actionEvent)
						{
							UserActionAlerter.this.actionPerformed(toFire, actionEvent);
						}

						@Override
						public boolean isEnabled()
						{
							/* We want only specific listeners for this event. If the event has no
							 * listeners, we don't want to display the action because it won't do
							 * anything. */
							if(requiresListeners
								&& !prisms.util.PrismsUtils.hasEventListeners(theSession,
									toFire.name))
								return false;
							return true;
						}
					}));
			}
		});
	}

	/** @return This alerter's session */
	public prisms.arch.PrismsSession getSession()
	{
		return theSession;
	}

	/**
	 * This method takes a "getUserActions" event and returns an event to fire in the case that the
	 * user clicks on the action generated. If the method returns null, no action will be displayed.
	 * 
	 * @param evt The getUserActions event to get an action for
	 * @return The PrismsEvent that should be fired if the user chooses the action generated by this
	 *         alerter, or null if no action should be presented for this getUserActions event.
	 */
	public prisms.arch.event.PrismsEvent getEventToFire(prisms.arch.event.PrismsEvent evt)
	{
		String plugin = (String) evt.getProperty("plugin");
		if(!prisms.util.ArrayUtils.contains(thePlugins, plugin))
			return null;
		Object [] props = new Object [theEventPropertiesIn.length * 2];
		if(theEventPropertiesIn.length > 0)
		{
			for(int p = 0; p < theEventPropertiesIn.length; p++)
			{
				Object prop = evt.getProperty(theEventPropertiesIn[p]);
				if(theEventPropertyRequired[p] && prop == null)
					return null;
				if(prop != null && theEventPropertyClasses[p] != null
					&& !theEventPropertyClasses[p].isInstance(prop))
					return null;
				props[p * 2] = theEventPropertiesOut[p];
				props[p * 2 + 1] = prop;
			}
		}
		return new prisms.arch.event.PrismsEvent(theEventName, props);
	}

	/**
	 * Called when the user chooses this alerter's action
	 * 
	 * @param toFire The PrismsEvent to be fired
	 * @param actionEvent The action event that was fired when the user chose the action
	 */
	public void actionPerformed(prisms.arch.event.PrismsEvent toFire,
		java.awt.event.ActionEvent actionEvent)
	{
		theSession.fireEvent(toFire);
	}

	@Override
	public String toString()
	{
		String ret;
		if(theEventPropertiesIn.length > 0)
		{
			ret = "Action Alerter " + theEventPropertiesIn[0];
			if(theEventPropertyClasses[0] != null)
				ret += "(" + theEventPropertyClasses[0].getName() + ")";
		}
		else if(getClass() == UserActionAlerter.class)
			ret = "Action Alerter";
		else
			ret = "Custom Action Alerter";
		ret += "->" + theEventName;
		return ret;
	}
}
