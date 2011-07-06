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
	public void configure(prisms.arch.PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		super.configure(app, config);
		if(theComparator == null)
		{
			String compClass = config.get("comparator");
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
	protected void eventOccurred(prisms.arch.PrismsApplication app, PrismsSession session,
		PrismsEvent evt, Object eventValue)
	{
		T [] value;
		if(session != null)
		{
			value = session.getProperty(getProperty());
			if(!isValueCorrect(session, value))
				session.setProperty(getProperty(), getCorrectValue(value), "prismsPersisted",
					evt.getProperty("prismsPersisted"));
		}
		else
		{
			value = app.getGlobalProperty(getProperty());
			if(!isValueCorrect(null, value))
				app.setGlobalProperty(getProperty(), getCorrectValue(value), "prismsPersisted",
					evt.getProperty("prismsPersisted"));
		}
	}

	@Override
	public T [] getApplicationValue(prisms.arch.PrismsApplication app)
	{
		return null;
	}

	@Override
	protected void checkValue(PrismsSession session, T [] value,
		prisms.arch.event.PrismsPCE<T []> evt)
	{
		if(!isValueCorrect(session, value))
			session.setProperty(getProperty(), getCorrectValue(session), "prismsPersisted",
				evt.get("prismsPersisted"));
	}

	@Override
	public T [] getCorrectValue(prisms.arch.PrismsSession session)
	{
		T [] ret = session.getProperty(getProperty());
		return getCorrectValue(ret);
	}

	T [] getCorrectValue(T [] origValue)
	{
		if(origValue == null)
			return origValue;
		origValue = origValue.clone();
		java.util.Arrays.sort(origValue, theComparator);
		return origValue;
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
