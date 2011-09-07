/*
 * ListPersister.java Created Jul 23, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;

import prisms.arch.Persister;
import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsProperty;
import prisms.util.ArrayUtils;
import prisms.util.ProgramTracker.TrackNode;

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

		boolean isLocked;

		ListElementContainer(T dbValue, T availableValue)
		{
			theDBValue = dbValue;
			theAvailableValue = availableValue;
		}

		void queue(T value)
		{
			synchronized(ListPersister.this)
			{
				for(int i = 0; i < theUpdateQueue.length; i++)
					if(equivalent(theUpdateQueue[i], value))
						return; // Already queued
				theUpdateQueue = ArrayUtils.add(theUpdateQueue, value);
			}
		}
	}

	private PrismsApplication theApp;

	private prisms.arch.PrismsConfig theConfig;

	private PrismsProperty<T []> theProperty;

	String DBOWNER;

	java.util.ArrayList<ListElementContainer> theElements;

	T [] theUpdateQueue;

	int dataLock;

	public void configure(prisms.arch.PrismsConfig config, PrismsApplication app,
		PrismsProperty<T []> property)
	{
		if(theElements != null)
			return; // Already configured
		theApp = app;
		theConfig = config;
		theProperty = property;
		theElements = new java.util.ArrayList<ListElementContainer>();
		theUpdateQueue = (T []) java.lang.reflect.Array.newInstance(property.getType()
			.getComponentType(), 0);

		for(T value : depersist())
			theElements.add(new ListElementContainer(clone(value), value));
	}

	/** @return The application that this persister is persisting a property for */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/** @return The configuration that intitialized this persister */
	public prisms.arch.PrismsConfig getConfigEl()
	{
		return theConfig;
	}

	/** @return The property that this persister is persisting */
	public PrismsProperty<T []> getProperty()
	{
		return theProperty;
	}

	public synchronized T [] getValue()
	{
		T [] ret = (T []) java.lang.reflect.Array.newInstance(theProperty.getType()
			.getComponentType(), theElements.size());
		for(int a = 0; a < ret.length; a++)
			ret[a] = theElements.get(a).theAvailableValue;
		return ret;
	}

	public T [] link(T [] value)
	{
		return value;
	}

	public synchronized void setValue(final PrismsSession session, Object [] value,
		@SuppressWarnings("rawtypes") prisms.arch.event.PrismsPCE evt)
	{
		final prisms.arch.event.PrismsPCE<T []> fEvt = evt;
		dataLock++;
		final int currentLock = dataLock;
		ListElementContainer [] dbEls = theElements
			.toArray(new ListPersister.ListElementContainer [theElements.size()]);
		ArrayUtils.adjust(dbEls, (T []) value,
			new ArrayUtils.DifferenceListener<ListElementContainer, T>()
			{
				public boolean identity(ListElementContainer o1, T o2)
				{
					return equivalent(o1.theDBValue, o2);
				}

				public ListElementContainer added(T o, int index, int retIdx)
				{
					if(currentLock != dataLock)
						return null;
					TrackNode track = null;
					prisms.arch.PrismsTransaction trans = getApp().getEnvironment()
						.getTransaction();
					if(trans != null)
						track = trans.getTracker().start(
							"PRISMS: Adding value " + o + " in persister "
								+ prisms.util.PrismsUtils.taskToString(this));
					T dbItem;
					try
					{
						dbItem = add(session, o, fEvt);
					} finally
					{
						if(track != null)
							trans.getTracker().end(track);
					}
					if(currentLock != dataLock)
						return null;
					if(dbItem != null)
					{
						ListElementContainer ret = new ListElementContainer(dbItem, o);
						theElements.add(retIdx, ret);
						return ret;
					}
					else
						return null;
				}

				public ListElementContainer removed(ListElementContainer o, int index, int incMod,
					int retIdx)
				{
					if(currentLock != dataLock)
						return null;
					TrackNode track = null;
					prisms.arch.PrismsTransaction trans = getApp().getEnvironment()
						.getTransaction();
					if(trans != null)
						track = trans.getTracker().start(
							"PRISMS: Removing value " + o + " from persister "
								+ prisms.util.PrismsUtils.taskToString(this));
					synchronized(o.theDBValue)
					{
						try
						{
							remove(session, o.theDBValue, fEvt);
						} finally
						{
							if(track != null)
								trans.getTracker().end(track);
						}
					}
					if(currentLock != dataLock)
						return null;
					theElements.remove(incMod);
					return null;
				}

				public ListElementContainer set(ListElementContainer o1, int idx1, int incMod,
					T o2, int idx2, int retIdx)
				{
					if(currentLock != dataLock)
						return null;
					// update(o1.theDBValue, o2);
					if(incMod != retIdx)
					{
						ListElementContainer lec = theElements.remove(incMod);
						/* I don't know how it can be that retIdx>=theElements.size() since this
						 * method is protected from modifications both within this thread and
						 * outside it, but it seems it's happening, so I'll protect against the
						 * exception it causes. */
						if(retIdx < theElements.size())
							theElements.add(retIdx, lec);
						else
							theElements.add(lec);
					}
					return o1;
				}
			});
	}

	private boolean doingQueue;

	public synchronized void valueChanged(PrismsSession session, T [] fullValue, Object o,
		prisms.arch.event.PrismsEvent evt)
	{
		for(ListElementContainer el : theElements)
			if(equivalent(el.theDBValue, (T) o))
			{
				/* If this item is already being updated, there's no reason to do multiple updates
				 * at the same time, so we'll queue the item up to be updated once more after the
				 * current update finishes. This is so if the updating has passed the data that
				 * changed with this invocation, it will be reflected in the database. This allows
				 * repeated calls to this persister to be ignored, with update only being called
				 * twice. */
				if(el.isLocked)
					el.queue((T) o);
				else
					synchronized(el.theDBValue)
					{
						boolean preLocked = el.isLocked;
						el.isLocked = true;
						TrackNode track = null;
						prisms.arch.PrismsTransaction trans = getApp().getEnvironment()
							.getTransaction();
						if(trans != null)
							track = trans.getTracker().start(
								"PRISMS: Value " + el.theDBValue + " changed in persister "
									+ prisms.util.PrismsUtils.taskToString(this));
						try
						{
							update(session, el.theDBValue, (T) o, evt);
						} finally
						{
							el.isLocked = preLocked;
							if(track != null)
								trans.getTracker().end(track);
						}
					}
				break;
			}
		if(!doingQueue)
		{
			doingQueue = true;
			try
			{
				while(theUpdateQueue.length > 0)
				{
					T toUpdate = theUpdateQueue[0];
					theUpdateQueue = ArrayUtils.remove(theUpdateQueue, 0);
					if(toUpdate != o)
						valueChanged(session, fullValue, toUpdate, evt);
				}
			} finally
			{
				doingQueue = false;
			}
		}
	}

	public synchronized void reload()
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
	 * its argument. The return value to this method will never be returned from the
	 * {@link #getValue()} method. It will be passed as the first argument to the
	 * {@link #equivalent(Object, Object)} and
	 * {@link #update(PrismsSession, Object, Object, prisms.arch.event.PrismsEvent)} methods.
	 * 
	 * @param toClone The available object to clone for this persister's cache
	 * @return An object independent of but identical to <code>toClone</code> for internal use.
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
	 * @param session The session that caused the change
	 * @param newValue The value to persist
	 * @param evt The event that represents the change
	 * @return An independent but identical representation of <code>newValue</code> for this
	 *         persister's cache
	 */
	protected abstract T add(PrismsSession session, T newValue,
		prisms.arch.event.PrismsPCE<T []> evt);

	/**
	 * Removes a value from persistent storage
	 * 
	 * @param session The session that caused the change
	 * @param removed The databased object that should be removed
	 * @param evt The event that represents the change
	 */
	protected abstract void remove(PrismsSession session, T removed,
		prisms.arch.event.PrismsPCE<T []> evt);

	/**
	 * Updates any possible changes to an object into persistent storage
	 * 
	 * @param session The session that caused the change
	 * @param dbValue The cloned, cached value representing the value currently in persistence
	 * @param availableValue The value with the same identity as <code>dbValue</code> that may have
	 *        been modified and may need to be updated in persistent storage
	 * @param evt The event that represents the change
	 */
	protected abstract void update(PrismsSession session, T dbValue, T availableValue,
		prisms.arch.event.PrismsEvent evt);
}
