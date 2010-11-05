/*
 * PropertySorter.java Created Dec 8, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsEvent;

/**
 * Sorts a list property by a configured comparator or the property values' natural order
 * 
 * @param <T> The type of elements in the property to sort
 */
public class PropertySorter<T> extends prisms.arch.event.GlobalPropertyManager<T []>
{
	private java.util.Comparator<T> theComparator;

	@Override
	public void configure(prisms.arch.PrismsApplication app, org.dom4j.Element configEl)
	{
		super.configure(app, configEl);
		if(theComparator == null)
		{
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
		}
	}

	@Override
	protected void eventOccurred(PrismsSession session, PrismsEvent evt, Object eventValue)
	{
		if(!isValueCorrect(session, session.getProperty(getProperty())))
			session.setProperty(getProperty(), getCorrectValue(session));
	}

	@Override
	public T [] getApplicationValue(prisms.arch.PrismsApplication app)
	{
		return null;
	}

	@Override
	public T [] getCorrectValue(prisms.arch.PrismsSession session)
	{
		T [] ret = session.getProperty(getProperty());
		if(ret == null)
			return ret;
		ret = ret.clone();
		java.util.Arrays.sort(ret, theComparator);
		return ret;
	}

	@Override
	public boolean isValueCorrect(prisms.arch.PrismsSession session, Object [] val)
	{
		if(val == null)
			return true;
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
