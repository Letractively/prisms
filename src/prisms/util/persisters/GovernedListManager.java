/*
 * SharedObjectManager.java Created Jun 26, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.apache.log4j.Logger;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsPCE;
import prisms.util.ArrayUtils;

/**
 * Governs a set of items, distributing to the appropriate sessions of different applications
 * 
 * @param <T> The type of item that is governed by this manager
 */
public class GovernedListManager<T> extends PersistingPropertyManager<T []>
{
	static final Logger log = Logger.getLogger(GovernedListManager.class);

	private static final String GOVERNED = "eventGoverned";

	private T [] theGlobalValue;

	private Governor<? super T> theGovernor;

	@Override
	public void configure(PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		super.configure(app, config);
		if(theGovernor == null)
		{
			prisms.arch.PrismsConfig govEl = config.subConfig("governor");
			if(govEl == null)
				throw new IllegalStateException(
					"No governor specified for governed manager of property " + getProperty());
			@SuppressWarnings("rawtypes")
			Class<? extends Governor> clazz;
			try
			{
				clazz = govEl.getClass("class", Governor.class);
			} catch(ClassCastException e)
			{
				throw new IllegalStateException("Class " + govEl.get("class")
					+ " is not a Governor implementation for governed property " + getProperty(), e);
			} catch(Exception e)
			{
				throw new IllegalStateException("Cannot find governor class " + govEl.get("class")
					+ " for managing governed property " + getProperty(), e);
			}
			if(clazz == null)
				throw new IllegalStateException(
					"No governor specified for governed manager of property " + getProperty());
			try
			{
				theGovernor = clazz.newInstance();
			} catch(Exception e)
			{
				throw new IllegalStateException("Could not instantiate governor "
					+ govEl.get("class") + " for governed property " + getProperty(), e);
			}
			theGovernor.configure(govEl);
		}
	}

	/** @return The governor that determines who can view and edit which elements */
	public Governor<? super T> getGovernor()
	{
		return theGovernor;
	}

	@Override
	public T [] getGlobalValue()
	{
		return theGlobalValue;
	}

	@Override
	public T [] getApplicationValue(PrismsApplication app)
	{
		java.util.ArrayList<T> ret = new java.util.ArrayList<T>();
		for(T val : theGlobalValue)
			if(theGovernor.canView(app, null, val))
				ret.add(val);
		return ret.toArray((T []) java.lang.reflect.Array.newInstance(getProperty().getType()
			.getComponentType(), ret.size()));
	}

	@Override
	public boolean isValueCorrect(final PrismsSession session, Object [] val)
	{
		if(val == null)
			return false;
		final boolean [] ret = new boolean [] {true};
		ArrayUtils.adjust(theGlobalValue, (T []) val, new ArrayUtils.DifferenceListener<T, T>()
		{
			public boolean identity(T o1, T o2)
			{
				return o1.equals(o2);
			}

			public T added(T o, int mIdx, int retIdx)
			{
				if(getGovernor().isShared(o))
					ret[0] = false;// The session has a value that is not in the global value
				return null;
			}

			public T removed(T o, int oIdx, int incMod, int retIdx)
			{
				if(getGovernor().canView(session.getApp(), session.getUser(), o))
					ret[0] = false; // The session is missing a value that the user should be
									// able to see
				return null;
			}

			public T set(T o1, int idx1, int incMod, T o2, int idx2, int retIdx)
			{
				if(getGovernor().isShared(o1)
					&& !getGovernor().canView(session.getApp(), session.getUser(), o1))
					ret[0] = false;// The session has a value that the user shouldn't be able to
									// see
				return null;
			}
		});
		return ret[0];
	}

	@Override
	public T [] getCorrectValue(final PrismsSession session)
	{
		T [] sv = session.getProperty(getProperty());
		if(sv == null)
			sv = (T []) java.lang.reflect.Array.newInstance(getProperty().getType()
				.getComponentType(), 0);
		return ArrayUtils.adjust(sv, theGlobalValue, new ArrayUtils.DifferenceListener<T, T>()
		{
			public boolean identity(T o1, T o2)
			{
				return o1.equals(o2);
			}

			public T added(T o, int mIdx, int retIdx)
			{
				if(getGovernor().isShared(o)
					&& getGovernor().canView(session.getApp(), session.getUser(), o))
					return o;
				return null;
			}

			public T removed(T o, int oIdx, int incMod, int retIdx)
			{
				if(!getGovernor().isShared(o))
					return o;
				return null;
			}

			public T set(T o1, int idx1, int incMod, T o2, int idx2, int retIdx)
			{
				if(getGovernor().isShared(o1)
					&& !getGovernor().canView(session.getApp(), session.getUser(), o1))
					return null;
				return o1;
			}
		});
	}

	@Override
	public void setValue(Object [] value)
	{
		if(value == null)
			throw new NullPointerException("Cannot set a null value for the "
				+ getClass().getName());
		theGlobalValue = (T []) value;
	}

	@Override
	public void changeValues(final PrismsSession session, PrismsPCE<T []> evt)
	{
		if(!Boolean.TRUE.equals(evt.get(GOVERNED)))
		{
			evt.set(GOVERNED, Boolean.TRUE);
			if(session != null)
			{
				theGlobalValue = ArrayUtils.adjust(theGlobalValue, evt.getNewValue(),
					new ArrayUtils.DifferenceListener<T, T>()
					{
						public boolean identity(T o1, T o2)
						{
							return o1 == o2;
						}

						public T added(T o, int idx, int retIdx)
						{
							if(getGovernor().isShared(o)
								&& !getGovernor().canEdit(session.getApp(), session.getUser(), o))
								throw new IllegalStateException("User " + session.getUser()
									+ " is not allowed to create value " + o + " in application "
									+ session.getApp());
							return o;
						}

						public T removed(T o, int idx, int incMod, int retIdx)
						{
							if(!getGovernor().isShared(o)
								|| !getGovernor().canView(session.getApp(), session.getUser(), o))
								return o; // User can't view it so of course it's not in their
											// session
							else if(getGovernor().canEdit(session.getApp(), session.getUser(), o))
								return null; // User has deleted the object
							else
							{
								log.error("User " + session.getUser()
									+ " is not allowed to delete value " + o + " in application "
									+ session.getApp());
								return o;
							}
						}

						public T set(T o1, int idx1, int incMod, T o2, int idx2, int retIdx)
						{
							return o1;
						}
					});
			}
			else
			{
				theGlobalValue = ArrayUtils.adjust(theGlobalValue, evt.getNewValue(),
					new ArrayUtils.DifferenceListener<T, T>()
					{
						public boolean identity(T o1, T o2)
						{
							return o1 == o2;
						}

						public T added(T o, int idx, int retIdx)
						{
							return o;
						}

						public T removed(T o, int idx, int incMod, int retIdx)
						{
							return null;
						}

						public T set(T o1, int idx1, int incMod, T o2, int idx2, int retIdx)
						{
							return o1;
						}
					});
			}
		}
		super.changeValues(session, evt);
	}

	@Override
	protected void eventOccurred(PrismsApplication app, PrismsSession session,
		prisms.arch.event.PrismsEvent evt, Object eventValue)
	{
		if(!Boolean.TRUE.equals(evt.getProperty(GOVERNED)))
		{
			evt.setProperty(GOVERNED, Boolean.TRUE);
			if(session != null
				&& !Boolean.TRUE.equals(evt
					.getProperty(PrismsApplication.GLOBALIZED_EVENT_PROPERTY)))
			{
				if(!getGovernor().canView(app, session.getUser(), (T) eventValue))
					throw new IllegalArgumentException("User " + session.getUser()
						+ " does not have access to item " + eventValue + " in application " + app);
				if(!getGovernor().canEdit(app, session.getUser(), (T) eventValue))
					throw new IllegalArgumentException("User " + session.getUser()
						+ " does not have permission to edit item " + eventValue
						+ " in application " + app);
			}
			int idx = ArrayUtils.indexOf(theGlobalValue, eventValue);
			if(idx < 0)
			{
				if(getGovernor().isShared((T) eventValue))
					log.warn("Element " + eventValue
						+ " not found in global value--cannot persist modification");
			}
			else
				globalAdjustValues(evt.getPropertyList());
		}
		super.eventOccurred(app, session, evt, eventValue);
	}
}
