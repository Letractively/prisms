/**
 * ArrayInitializer.java Created Mar 11, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;

/**
 * Initializes an array to length 0 if it is null. This utility may be used as a property manager
 * (better for global properties) or a session monitor (for session properties).
 * 
 * @param <T> The type of the property
 */
public class ArrayPropertyInitializer<T> extends prisms.arch.event.PropertyManager<T> implements
	prisms.arch.event.SessionMonitor
{
	@Override
	public void configure(PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		super.configure(app, config);
		if(!getProperty().getType().isArray())
			throw new IllegalStateException(
				"An array property initializer can only be used on array types");
		T value = app.getGlobalProperty(getProperty());
		if(value == null)
			app.setGlobalProperty(getProperty(), zeroLengthArray(getProperty()));
	}

	@Override
	public T getApplicationValue(PrismsApplication app)
	{
		return null;
	}

	@Override
	public T getCorrectValue(PrismsSession session)
	{
		T value = session.getProperty(getProperty());
		if(value == null)
			return zeroLengthArray(getProperty());
		else
			return value;
	}

	@Override
	public <V extends T> boolean isValueCorrect(PrismsSession session, V val)
	{
		return val != null;
	}

	public void register(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		prisms.arch.event.PrismsProperty<T> prop = prisms.arch.event.PrismsProperty.get(
			config.get("property"), (Class<T>) Object [].class);
		if(session.getProperty(prop) == null)
			session.setProperty(prop, zeroLengthArray(prop));
	}

	T zeroLengthArray(prisms.arch.event.PrismsProperty<T> property)
	{
		return (T) java.lang.reflect.Array.newInstance(property.getType().getComponentType(), 0);
	}
}
