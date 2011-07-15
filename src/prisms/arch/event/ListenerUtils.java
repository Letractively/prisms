/*
 * ListenerUtils.java Created Oct 21, 2010 by Andrew Butler, PSL
 */
package prisms.arch.event;

import prisms.arch.PrismsSession;
import prisms.util.ArrayUtils;

/** A few generally useful utilities for listeners */
public class ListenerUtils
{
	/**
	 * Monitors an array property and a singular property. If the array property's value is modified
	 * such that it no longer contains the value of the singular property, the singular property's
	 * value is set to null.
	 * 
	 * @param <T> The type of the property
	 * @param session The session to monitor
	 * @param arrayProp The array property to monitor
	 * @param elementProp The singular property to monitor
	 */
	public static <T> void monitorArrayElement(final PrismsSession session,
		final PrismsProperty<T []> arrayProp, final PrismsProperty<T> elementProp)
	{
		session.addPropertyChangeListener(arrayProp, new PrismsPCL<T []>()
		{
			public void propertyChange(PrismsPCE<T []> evt)
			{
				T sel = session.getProperty(elementProp);
				if(sel != null && !ArrayUtils.contains(evt.getNewValue(), sel))
					session.setProperty(elementProp, null);
			}

			@Override
			public String toString()
			{
				return session.getApp().getName() + " Array Element Enforcer: " + arrayProp + ": "
					+ elementProp;
			}
		});
	}

	/**
	 * Monitors the session for an event, at which the value of a property will be set to null if
	 * the value of a property in the event matches the value of the session property.
	 * 
	 * @param session The session to monitor
	 * @param eventName The name of the event to listen for
	 * @param eventProp The name of the property in the event that will contain the relevant value
	 * @param prop The property to set to null when its value is removed
	 */
	public static void monitorRemoveEvent(final PrismsSession session, final String eventName,
		final String eventProp, final PrismsProperty<?> prop)
	{
		session.addEventListener(eventName, new PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, PrismsEvent evt)
			{
				if(equal(session2.getProperty(prop), evt.getProperty(eventProp)))
					session2.setProperty(prop, null);
			}

			@Override
			public String toString()
			{
				return session.getApp().getName() + " Remove Event Enforcer: " + eventName + ": "
					+ prop;
			}
		});
	}

	/**
	 * Couples two properties--one a singular property, the other an array property. If the array
	 * property is changed, the singular property will be set to the first element of the array. If
	 * the singular property is changed, the array property will be set to an array containing only
	 * the singular element.
	 * 
	 * @param <T> The type of the property
	 * @param session The session to monitor
	 * @param arrayProp The array property to keep consistent with the singular property
	 * @param singProp The singular property to keep consistent with the array property
	 */
	public static <T> void coupleWithSingular(final PrismsSession session,
		final PrismsProperty<T []> arrayProp, final PrismsProperty<T> singProp)
	{
		session.addPropertyChangeListener(singProp, new PrismsPCL<T>()
		{
			public void propertyChange(PrismsPCE<T> evt)
			{
				T [] array = session.getProperty(arrayProp);
				if(array == null || array.length == 0)
				{
					if(evt.getNewValue() == null)
						return;
				}
				else if(array.length == 1
					&& (array[0] != null && array[0].equals(evt.getNewValue())))
					return;
				if(evt.getNewValue() == null)
					array = (T []) java.lang.reflect.Array.newInstance(singProp.getType(), 0);
				else
				{
					array = (T []) java.lang.reflect.Array.newInstance(singProp.getType(), 1);
					array[0] = evt.getNewValue();
				}
				session.setProperty(arrayProp, array);
			}

			@Override
			public String toString()
			{
				return session.getApp().getName() + " Array/Single Coupler: " + singProp + ": "
					+ arrayProp;
			}
		});
		session.addPropertyChangeListener(arrayProp, new PrismsPCL<T []>()
		{
			public void propertyChange(PrismsPCE<T []> evt)
			{
				T [] array = evt.getNewValue();
				if(array.length == 0)
				{
					if(session.getProperty(singProp) == null)
						return;
					session.setProperty(singProp, null);
				}
				else
				{
					if(array[0].equals(session.getProperty(singProp)))
						return;
					session.setProperty(singProp, array[0]);
				}
			}

			@Override
			public String toString()
			{
				return session.getApp().getName() + " Array/Single Coupler: " + arrayProp + ": "
					+ singProp;
			}
		});
	}

	/**
	 * Monitors a super-set property and ensures that when elements are removed from the super-set,
	 * those elements are not present in the sub-set.
	 * 
	 * @param <T> The type of the properties
	 * @param session The session to monitor
	 * @param superSetProp The super-set property
	 * @param subSetProp The sub-set property
	 */
	public static <T> void monitorSubset(final PrismsSession session,
		final PrismsProperty<T []> superSetProp, final PrismsProperty<T []> subSetProp)
	{
		session.addPropertyChangeListener(superSetProp, new PrismsPCL<T []>()
		{
			public void propertyChange(PrismsPCE<T []> evt)
			{
				T [] subSet = session.getProperty(subSetProp);
				final boolean [] modified = new boolean [] {false};
				subSet = ArrayUtils.adjust(subSet, evt.getNewValue(),
					new ArrayUtils.DifferenceListener<T, T>()
					{
						public boolean identity(T o1, T o2)
						{
							return o1 == o2;
						}

						public T added(T o, int idx, int retIdx)
						{
							return null;
						}

						public T removed(T o, int idx, int incMod, int retIdx)
						{
							modified[0] = true;
							return null;
						}

						public T set(T o1, int idx1, int incMod, T o2, int idx2, int retIdx)
						{
							return o1;
						}
					});
				if(modified[0])
					session.setProperty(subSetProp, subSet);
			}

			@Override
			public String toString()
			{
				return session.getApp().getName() + " Subset Enforcer: " + superSetProp + ":"
					+ subSetProp;
			}
		});
	}

	/**
	 * Makes a set of properties exclusive of each others. That is, when the value of one is set to
	 * a non-null, non zero-length value, the values of the rest of the properties will be set to
	 * null or a 0-length array of an appropriate type.
	 * 
	 * @param session The session to monitor
	 * @param props The properties to be exclusive of each other
	 */
	public static void makeExclusive(final PrismsSession session, final PrismsProperty<?>... props)
	{
		for(PrismsProperty<?> prop : props)
		{
			session.addPropertyChangeListener(prop, new PrismsPCL<Object>()
			{
				public void propertyChange(PrismsPCE<Object> evt)
				{
					if(evt.getNewValue() == null)
						return;
					if(evt.getNewValue() instanceof Object []
						&& ((Object []) evt.getNewValue()).length == 0)
						return;
					for(@SuppressWarnings("rawtypes")
					PrismsProperty prop2 : props)
						if(evt.getProperty() != prop2)
						{
							Object propVal;
							if(prop2.getType().isArray())
								propVal = java.lang.reflect.Array.newInstance(prop2.getType()
									.getComponentType(), 0);
							else
								propVal = null;
							session.setProperty(prop2, propVal);
						}
				}

				@Override
				public String toString()
				{
					return session.getApp().getName() + " Exclusivity Enforcer for "
						+ ArrayUtils.toString(props);
				}
			});
		}
	}

	static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? false : o1.equals(o2);
	}
}
