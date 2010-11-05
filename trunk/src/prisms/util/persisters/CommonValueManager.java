/**
 * CommonValueManager.java Created Oct 30, 2007 by Andrew Butler, PSL
 */
package prisms.util.persisters;

/**
 * Assures that a common value is kept between all sessions. Any session may change the value in all
 * sessions
 * 
 * @param <T> The type of property to manage
 */
public class CommonValueManager<T> extends PersistingPropertyManager<T>
{
	private T theValue;

	/** Creates a CommonValueManager */
	public CommonValueManager()
	{
	}

	@Override
	public <V extends T> void setValue(V value)
	{
		theValue = value;
	}

	@Override
	public void propertyChange(prisms.arch.event.PrismsPCE<T> evt)
	{
		theValue = evt.getNewValue();
		super.propertyChange(evt);
	}

	@Override
	public T getGlobalValue()
	{
		return theValue;
	}

	public T getApplicationValue(prisms.arch.PrismsApplication app)
	{
		return theValue;
	}

	@Override
	public T getCorrectValue(prisms.arch.PrismsSession session)
	{
		return theValue;
	}

	@Override
	public boolean isValueCorrect(prisms.arch.PrismsSession session, Object val)
	{
		return prisms.util.ArrayUtils.equals(theValue, val);
	}
}
