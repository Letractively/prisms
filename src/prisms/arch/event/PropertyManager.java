/**
 * PropertyManager.java Created Oct 25, 2007 by Andrew Butler, PSL
 */
package prisms.arch.event;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;

/**
 * Manages a property between {@link PrismsSession}s in a {@link PrismsApplication}
 * 
 * @param <T> the type of this manager
 */
public abstract class PropertyManager<T> implements PrismsPCL<T>
{
	private PrismsApplication theApp;

	private PrismsProperty<T> theProperty;

	volatile boolean theDataLock;

	/**
	 * Creates a PropertyManager that must be configured to get its application and target property
	 * name
	 */
	public PropertyManager()
	{
	}

	/**
	 * Creates a PropertyManager with its application and target property name
	 * 
	 * @param app The application to manage the property in
	 * @param prop The name of the property to manage
	 */
	public PropertyManager(PrismsApplication app, PrismsProperty<T> prop)
	{
		theApp = app;
		theProperty = prop;
	}

	/**
	 * Configures this PropertyManager
	 * 
	 * @param app The application to manage the property in
	 * @param configEl The XML element with potential subclass-specific settings
	 */
	public void configure(PrismsApplication app, org.dom4j.Element configEl)
	{
		theApp = app;
		String propField = configEl.elementTextTrim("field");
		if(propField != null)
		{
			int lastDot = propField.lastIndexOf('.');
			try
			{
				Class<?> clazz = Class.forName(propField.substring(0, lastDot));
				theProperty = (PrismsProperty<T>) clazz.getField(propField.substring(lastDot + 1))
					.get(null);
			} catch(Throwable e)
			{
				throw new IllegalArgumentException(
					"Could not configure property manager--field configured incorrectly", e);
			}
		}
		else
		{
			String propName = configEl.elementText("name");
			String className = configEl.elementText("type");
			try
			{
				theProperty = PrismsProperty.get(propName, (Class<T>) Class.forName(className));
			} catch(Exception e)
			{
				throw new IllegalArgumentException(
					"Could not configure property manager--name/type configured incorrectly", e);
			}
		}
	}

	/**
	 * @return The application that this PropertyManager manages a property in
	 */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/**
	 * @return The name of the property that this manager manages
	 */
	public PrismsProperty<T> getProperty()
	{
		return theProperty;
	}

	/**
	 * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
	 */
	public void propertyChange(PrismsPCE<T> evt)
	{
		if(theDataLock)
			return;
		changeValues((PrismsSession) evt.getSource());
	}

	/**
	 * Checks the value of the property being set in a session and takes action, if necessary, to
	 * ensure that all sessions keep the proper value
	 * 
	 * @param session The session where the property has been changed
	 */
	public synchronized void changeValues(PrismsSession session)
	{
		theApp.runSessionTask(session, new PrismsApplication.SessionTask()
		{
			public void run(PrismsSession s)
			{
				if(!isValueCorrect(s, s.getProperty(getProperty())))
				{
					theDataLock = true;
					try
					{
						s.setProperty(getProperty(), getCorrectValue(s));
					} finally
					{
						theDataLock = false;
					}
				}
			}
		}, false);
		T appData = getApplicationValue();
		if(appData != null)
			theApp.fireGlobalPropertyChange(getProperty(), this, appData);
	}

	/**
	 * Called by the AppConfig after all of the registered properties for an application are
	 * deserialized and set. This is intended to be used for linking--so that property elements
	 * containing elements of other properties may be properly linked. By default, this does
	 * nothing.
	 */
	public void propertiesSet()
	{
	}

	/**
	 * @return All data for this manager's property that is available to the application. This may
	 *         return null if no data is available to the application as a whole--that is, if the
	 *         managed property's value is completely session-specific
	 */
	public abstract T getApplicationValue();

	/**
	 * @param <V> The type of value to set
	 * @param session The session to check
	 * @param val The value to check
	 * @return Whether the given value is correct for the given session
	 */
	public abstract <V extends T> boolean isValueCorrect(PrismsSession session, V val);

	/**
	 * Called if a session is found to have an incorrect value
	 * 
	 * @param session The session to get the correct property value for
	 * @return The correct value to set for the session's value of this manager's property
	 * @see #isValueCorrect(PrismsSession, Object)
	 */
	public abstract T getCorrectValue(PrismsSession session);
}
