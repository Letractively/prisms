/*
 * SortChangeListener.java Created Dec 8, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;

/**
 * Alerts a {@link PropertySorter} that an element in the property has changed so that the sorter
 * can keep the property sorted even when an element changes
 * 
 * @param <T> The type of the element in the property to sort
 */
public class SortChangeListener<T> extends PersistingChangeListener<T []>
{
	private static final Logger log = Logger.getLogger(SortChangeListener.class);

	/**
	 * @see prisms.arch.event.PrismsEventListener#eventOccurred(prisms.arch.event.PrismsEvent)
	 */
	public void eventOccurred(prisms.arch.event.PrismsEvent evt)
	{
		if(evt.getProperty("sorted") != null
			&& ((Boolean) evt.getProperty("sorted")).booleanValue())
			return;
		evt.setProperty("sorted", new Boolean(true));

		Object oo = evt.getProperty(getEventProperty());
		if(oo == null)
		{
			log.warn("Change event " + evt.name + "does not have specified event property: "
				+ getEventProperty());
			return;
		}
		prisms.arch.event.PropertyManager<?> [] propMgrs = getSession().getApp().getManagers(
			getProperty());
		for(int pm = 0; pm < propMgrs.length; pm++)
			if(propMgrs[pm] instanceof PropertySorter<?>)
			{
				if(!((PropertySorter<T>) propMgrs[pm]).isValueCorrect(getSession(), getSession()
					.getProperty(getProperty())))
					getSession().setProperty(getProperty(),
						((PropertySorter<T>) propMgrs[pm]).getCorrectValue(getSession()));
			}
	}
}
