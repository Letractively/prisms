/**
 * ReadOnlyManager.java Created Oct 25, 2007 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;
import org.dom4j.Element;

import prisms.arch.Persister;
import prisms.arch.PrismsSession;

/**
 * A PropertyManager that throws an exception if a session attempts to change the value of the
 * managed property directly
 * 
 * @param <T> The type of property to manage
 */
public class ReadOnlyManager<T> extends prisms.arch.event.GlobalPropertyManager<T>
{
	private static final Logger log = Logger.getLogger(ReadOnlyManager.class);

	private T theValue;

	/**
	 * Creates a ReadOnlyManager that must be configured to get its application and target property
	 * name
	 */
	public ReadOnlyManager()
	{
	}

	/**
	 * Creates a ReadOnlyManager with its application and target property name
	 * 
	 * @param app The application to manage the property in
	 * @param property The property to manage
	 * @param value The value for this manager that will be enforced on all sessions
	 */
	public ReadOnlyManager(prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property, T value)
	{
		super(app, property);
		theValue = value;
	}

	@Override
	public void configure(prisms.arch.PrismsApplication app, Element configEl)
	{
		super.configure(app, configEl);
		if(theValue != null)
			return;
		Element persisterEl = configEl.element("persister");
		Persister<T> persister;

		if(persisterEl == null)
			persister = null;
		else
			persister = app.getEnvironment().getPersisterFactory()
				.create(configEl.element("persister"), app, getProperty());

		if(persister != null)
		{
			try
			{
				theValue = persister.getValue();
			} catch(Throwable e)
			{
				log.error("Could not deserialize property " + getProperty(), e);
				return;
			}
		}
		else
		{
			Class<? extends T> propType = getProperty().getType();
			try
			{
				theValue = propType.newInstance();
			} catch(Exception e)
			{
				throw new IllegalStateException("ReadOnlyManager does not have initial value"
					+ " and can't instantiate one.");
			}
		}
	}

	@Override
	public synchronized void propertyChange(prisms.arch.event.PrismsPCE<T> evt)
	{
		boolean isCorrect;
		if(evt.getSession() != null)
			isCorrect = isValueCorrect(evt.getSession(), evt.getNewValue());
		else
			isCorrect = prisms.util.ArrayUtils.equals(evt.getNewValue(), evt.getApp()
				.getGlobalProperty(getProperty()));
		if(!isCorrect)
			throw new IllegalArgumentException("Cannot change the value of the read-only property "
				+ getProperty());
	}

	@Override
	public T getApplicationValue(prisms.arch.PrismsApplication app)
	{
		return theValue;
	}

	/**
	 * @see prisms.arch.event.PropertyManager#getCorrectValue(prisms.arch.PrismsSession)
	 */
	@Override
	public T getCorrectValue(PrismsSession session)
	{
		return theValue;
	}

	/**
	 * @see prisms.arch.event.PropertyManager#isValueCorrect(prisms.arch.PrismsSession,
	 *      java.lang.Object)
	 */
	@Override
	public boolean isValueCorrect(PrismsSession session, Object val)
	{
		return val == theValue;
	}

	@Override
	protected void eventOccurred(PrismsSession session, prisms.arch.event.PrismsEvent evt,
		Object eventValue)
	{
	}
}
