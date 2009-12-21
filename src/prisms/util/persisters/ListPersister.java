/*
 * DBPersister.java Created Jul 23, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;
import org.dom4j.Element;

import prisms.arch.PrismsApplication;
import prisms.arch.Persister;
import prisms.arch.event.PrismsProperty;
import prisms.util.ArrayUtils;

/**
 * Persists a property consisting of an array of elements
 * 
 * @param <T> The type of element in the list to persist
 */
public abstract class ListPersister<T> implements Persister<T []>
{
	static final Logger log = Logger.getLogger(ListPersister.class);

	private class ListElementContainer
	{
		T theDBValue;

		T theAvailableValue;

		ListElementContainer(T dbValue, T availableValue)
		{
			theDBValue = dbValue;
			theAvailableValue = availableValue;
		}
	}

	private PrismsApplication theApp;

	private Element theConfigEl;

	private PrismsProperty<T []> theProperty;

	String DBOWNER;

	java.util.ArrayList<ListElementContainer> theElements;

	/**
	 * @see prisms.arch.Persister#configure(org.dom4j.Element, prisms.arch.PrismsApplication,
	 *      prisms.arch.event.PrismsProperty)
	 */
	public void configure(Element configEl, PrismsApplication app, PrismsProperty<T []> property)
	{
		theApp = app;
		theConfigEl = configEl;
		theProperty = property;
		theElements = new java.util.ArrayList<ListElementContainer>();
		reload();
	}

	/**
	 * @return The application that this persister is persisting a property for
	 */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/**
	 * @return The XML configuration that intitialized this persister
	 */
	public Element getConfigEl()
	{
		return theConfigEl;
	}

	/**
	 * @return The property that this persister is persisting
	 */
	public PrismsProperty<T []> getProperty()
	{
		return theProperty;
	}

	/**
	 * @see prisms.arch.Persister#getValue()
	 */
	public synchronized T [] getValue()
	{
		T [] ret = (T []) java.lang.reflect.Array.newInstance(theProperty.getType()
			.getComponentType(), theElements.size());
		for(int a = 0; a < ret.length; a++)
			ret[a] = theElements.get(a).theAvailableValue;
		return ret;
	}

	/**
	 * @see prisms.arch.Persister#link(java.lang.Object)
	 */
	public T [] link(T [] value)
	{
		return value;
	}

	/**
	 * @see prisms.arch.Persister#setValue(java.lang.Object)
	 */
	public synchronized void setValue(Object [] value)
	{
		ListElementContainer [] dbEls = theElements
			.toArray(new ListPersister.ListElementContainer [theElements.size()]);
		ArrayUtils.adjust(dbEls, (T []) value,
			new ArrayUtils.DifferenceListener<ListElementContainer, T>()
			{
				/**
				 * @see ArrayUtils.DifferenceListener#identity(java.lang.Object, java.lang.Object)
				 */
				public boolean identity(ListElementContainer o1, T o2)
				{
					return equivalent(o1.theDBValue, o2);
				}

				public ListElementContainer set(ListElementContainer o1, int idx1, int incMod,
					T o2, int idx2, int retIdx)
				{
					// update(o1.theDBValue, o2);
					if(incMod != retIdx)
					{
						ListElementContainer lec = theElements.remove(incMod);
						theElements.add(retIdx, lec);
					}
					return o1;
				}

				/**
				 * @see ArrayUtils.DifferenceListener#added(java.lang.Object, int, int)
				 */
				public ListElementContainer added(T o, int index, int retIdx)
				{
					T dbArea = add(o);
					if(dbArea != null)
					{
						ListElementContainer ret = new ListElementContainer(dbArea, o);
						theElements.add(retIdx, ret);
						return ret;
					}
					else
						return null;
				}

				public ListElementContainer removed(ListElementContainer o, int index, int incMod,
					int retIdx)
				{
					remove(o.theDBValue);
					theElements.remove(incMod);
					return null;
				}
			});
	}

	/**
	 * @see prisms.arch.Persister#valueChanged(java.lang.Object, java.lang.Object)
	 */
	public synchronized void valueChanged(T [] fullValue, Object o)
	{
		for(ListElementContainer el : theElements)
			if(equivalent(el.theDBValue, (T) o))
			{
				update(el.theDBValue, (T) o);
				break;
			}
	}

	/**
	 * @see prisms.arch.Persister#reload()
	 */
	public void reload()
	{
		theElements.clear();
		for(T value : depersist())
			theElements.add(new ListElementContainer(clone(value), value));
	}

	/**
	 * Determines whether two objects are equivalent
	 * 
	 * @param po The persisted (cloned) object to compare
	 * @param avo The available (original) object to compare
	 * @return Whether the two objects have the same identity (and hence should be updated)
	 */
	protected abstract boolean equivalent(T po, T avo);

	/**
	 * Clones an available object to keep a cache used to compare with the set. If the persister
	 * implementation does not require keeping a cached version, then this method may simply return
	 * its argument.
	 * 
	 * @param toClone The available object to clone for this persister's cache
	 * @return An object independent of but identical to <code>toClone</code>
	 */
	protected abstract T clone(T toClone);

	/**
	 * Reads the persistent data source and returns the list of objects persisted there
	 * 
	 * @return The list of objects persisted to permanent storage
	 */
	protected abstract T [] depersist();

	/**
	 * Adds a new value to the set of persisted data
	 * 
	 * @param newValue The value to persist
	 * @return An indepentent but identical representation of <code>newValue</code> for this
	 *         persister's cache
	 */
	protected abstract T add(T newValue);

	/**
	 * Removes a value from persistent storage
	 * 
	 * @param removed The databased object that should be removed
	 */
	protected abstract void remove(T removed);

	/**
	 * Updates any possible changes to an object into persistent storage
	 * 
	 * @param dbValue The cloned, cached value representing the value currently in persistence
	 * @param availableValue The value with the same identity as <code>dbValue</code> that may have
	 *        been modified and may need to be updated in persistent storage
	 */
	protected abstract void update(T dbValue, T availableValue);
}
