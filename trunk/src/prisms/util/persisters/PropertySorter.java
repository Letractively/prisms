/*
 * PropertySorter.java Created Dec 8, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

/**
 * Sorts a list property by a configured comparator or the property values' natural order
 * 
 * @param <T> The type of elements in the property to sort
 */
public class PropertySorter<T> extends prisms.arch.event.PropertyManager<T []>
{
	private java.util.Comparator<T> theComparator;

	@Override
	public void configure(prisms.arch.PrismsApplication app, org.dom4j.Element configEl)
	{
		super.configure(app, configEl);
		String compClass = configEl.elementTextTrim("comparator");
		if(compClass != null)
			try
			{
				theComparator = Class.forName(compClass).asSubclass(java.util.Comparator.class)
					.newInstance();
			} catch(Throwable e)
			{
				throw new IllegalArgumentException("Could not instantiate comparator class "
					+ compClass);
			}
		java.util.List<org.dom4j.Element> eventEls = configEl.elements("changeEvent");
		for(org.dom4j.Element eventEl : eventEls)
		{
			String eventName = eventEl.elementTextTrim("name");
			if(eventName == null)
				throw new IllegalArgumentException("Cannot listen for change event on property "
					+ getProperty() + ". No event name specified");
			String propName = eventEl.elementTextTrim("eventProperty");
			if(propName == null)
				throw new IllegalArgumentException("Cannot listen for change event on property "
					+ getProperty() + ". No eventProperty specified");
			// Put the property name and type in here so the event listener can find this property
			// manager
			eventEl.addElement("persistProperty").setText(getProperty().getName());
			eventEl.addElement("type").setText(getProperty().getType().getName());
			app.addEventListenerType(eventName, SortChangeListener.class, eventEl);
		}
	}

	@Override
	public T [] getApplicationValue()
	{
		return null;
	}

	@Override
	public T [] getCorrectValue(prisms.arch.PrismsSession session)
	{
		T [] ret = session.getProperty(getProperty());
		ret = ret.clone();
		java.util.Arrays.sort(ret, theComparator);
		return ret;
	}

	@Override
	public boolean isValueCorrect(prisms.arch.PrismsSession session, Object [] val)
	{
		if(theComparator != null)
		{
			for(int i = 0; i < val.length - 1; i++)
				if(theComparator.compare((T) val[i], (T) val[i + 1]) > 0)
					return false;
		}
		else
		{
			for(int i = 0; i < val.length - 1; i++)
				if(((Comparable<T>) val[i]).compareTo((T) val[i + 1]) > 0)
					return false;
		}
		return true;
	}
}
