/**
 * InitialValueManager.java Created Oct 30, 2007 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;
import org.dom4j.Element;

import prisms.arch.Persister;
import prisms.arch.PrismsSession;
import prisms.arch.event.PropertyManager;

/**
 * Ensures that each session is initialized with a common value. The session can modify the value
 * later without affecting other sessions
 * 
 * @param <T> The type of property to manage
 */
public class InitialValueManager<T> extends PropertyManager<T>
{
	private static final Logger log = Logger.getLogger(InitialValueManager.class);

	private T theValue;

	private java.util.Set<Integer> theInitializedSessions;

	/**
	 * Creates an InitialValueManager
	 */
	public InitialValueManager()
	{
		theInitializedSessions = new java.util.HashSet<Integer>();
	}

	/**
	 * Creates an initialized InitialValueManager
	 * 
	 * @param app The application that this manager is for
	 * @param property The property that this manager is to set initially
	 * @param value The value to set as the initial value in all sessions
	 */
	public InitialValueManager(prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property, T value)
	{
		super(app, property);
		theInitializedSessions = new java.util.HashSet<Integer>();
		theValue = value;
	}

	@Override
	public void configure(prisms.arch.PrismsApplication app, Element configEl)
	{
		super.configure(app, configEl);
		if(theValue != null)
			return;
		Persister<T> persister = app.getEnvironment().getPersisterFactory()
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
	}

	/**
	 * Sets the value to set for the initial value in each session
	 * 
	 * @param <V> The type of value to set
	 * @param value The value to set
	 */
	public <V extends T> void setValue(V value)
	{
		theValue = value;
	}

	@Override
	public synchronized void propertyChange(prisms.arch.event.PrismsPCE<T> evt)
	{
		// Do nothing
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
	public <V extends T> boolean isValueCorrect(PrismsSession session, V val)
	{
		if(!theInitializedSessions.contains(new Integer(session.hashCode())))
		{
			theInitializedSessions.add(new Integer(session.hashCode()));
			return false;
		}
		return true;
	}
}
