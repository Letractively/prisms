/**
 * SerialPersister.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.event.PropertyDataSource;

/**
 * A persister that writes serialized data to a file. This is a quickly-implementable stop-gap
 * solution that works well in development for small amounts of data. In deployment, flat file use
 * by a web application is sloppy; and re-writing all of a property's data for each change may
 * cripple performance.
 * 
 * @param <T> The type of value to persist
 */
public class SerialPersister<T> extends AbstractPersister<T>
{
	private PropertySerializer<T> theSerializer;

	private PropertyDataSource thePDS;

	private prisms.arch.event.PrismsProperty<T> theProperty;

	@Override
	public void configure(org.dom4j.Element configEl, prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property)
	{
		super.configure(configEl, app, property);
		theProperty = property;
		String serStr = configEl.elementTextTrim("serializer");
		if(serStr != null)
		{
			try
			{
				theSerializer = (PropertySerializer<T>) Class.forName(serStr).newInstance();
			} catch(Throwable e)
			{
				throw new IllegalArgumentException("Could not instantiate property serializer "
					+ serStr, e);
			}
		}
		else
			theSerializer = null;
		thePDS = app.getServer().getPersisterFactory().parseDS(configEl.element("datasource"));
	}

	/**
	 * @see prisms.arch.Persister#getValue()
	 */
	public T getValue()
	{
		return theSerializer.deserialize(thePDS.getData(theProperty.getName()), getApp());
	}

	/**
	 * @see prisms.arch.Persister#link(java.lang.Object)
	 */
	@Override
	public T link(T value)
	{
		value = super.link(value);
		return theSerializer.link(value, getApp());
	}

	/**
	 * @see prisms.arch.Persister#setValue(java.lang.Object)
	 */
	public <V extends T> void setValue(V o)
	{
		thePDS.saveData(theProperty.getName(), theSerializer.serialize(o));
	}

	/**
	 * @see prisms.arch.Persister#valueChanged(java.lang.Object, java.lang.Object)
	 */
	public void valueChanged(T fullValue, Object o)
	{
		thePDS.saveData(theProperty.getName(), theSerializer.serialize(fullValue));
	}

	public void reload()
	{
		// No cache to clear
	}
}
