/*
 * ConfigurablePersister.java Created Jul 20, 2011 by Andrew Butler, PSL
 */
package prisms.util.persisters;

/**
 * Depersists an instance of {@link ConfigurableResource} by instantiating it and configuring it
 * 
 * @param <T> The type of resource that this persister provides
 */
public class ConfigurablePersister<T extends ConfigurableResource> extends AbstractPersister<T>
{
	private T theValue;

	@Override
	public void configure(prisms.arch.PrismsConfig config, prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property)
	{
		if(theValue != null)
			return;
		super.configure(config, app, property);
		Class<? extends T> resClass;
		if(config.get("class") != null)
		{
			String className = config.get("class");
			Class<?> clazz;
			try
			{
				clazz = Class.forName(className);
			} catch(ClassNotFoundException e)
			{
				throw new IllegalStateException("Could not find class for configurable resource "
					+ property, e);
			}
			try
			{
				resClass = clazz.asSubclass(property.getType());
			} catch(ClassCastException e)
			{
				throw new IllegalStateException("Class given for configurable resource " + property
					+ " (" + className + ") is not an instance of " + property.getType().getName(),
					e);
			}
		}
		else
			resClass = property.getType();
		try
		{
			theValue = resClass.newInstance();
		} catch(Exception e)
		{
			throw new IllegalStateException("Could not instantiate class " + resClass.getName()
				+ " for configurable resource " + property, e);
		}
		try
		{
			theValue.configure(config, app);
		} catch(Throwable e)
		{
			throw new IllegalStateException(
				"Could not configure configurable resource " + property, e);
		}
	}

	public T getValue()
	{
		return theValue;
	}

	public <V extends T> void setValue(prisms.arch.PrismsSession session, V o,
		@SuppressWarnings("rawtypes") prisms.arch.event.PrismsPCE evt)
	{
	}

	public void valueChanged(prisms.arch.PrismsSession session, T fullValue, Object o,
		prisms.arch.event.PrismsEvent evt)
	{
	}

	public void reload()
	{
	}
}
