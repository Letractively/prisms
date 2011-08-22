/*
 * InspectorUtils.java Created Jul 25, 2011 by Andrew Butler, PSL
 */
package prisms.util;

import prisms.arch.event.DataInspector.ChangeListener;
import prisms.arch.event.GlobalPropertyManager.ChangeEvent;

/**
 * A utility to make {@link prisms.arch.event.DataInspector}s easier to implement. An InspectorUtils
 * can be used for a property if the only events that can affect the value of the property are the
 * property changed event on that property and PRISMS events for which the value that was changed
 * exists in a property of the event.
 */
public class InspectorUtils
{
	private static class Listener
	{
		final prisms.arch.PrismsApplication app;

		final prisms.arch.PrismsSession session;

		final ChangeListener listener;

		prisms.arch.event.PrismsPCL<Object> pcl;

		prisms.arch.event.PrismsEventListener[] els;

		Runnable autoUpdater;

		Listener(prisms.arch.PrismsApplication a, prisms.arch.PrismsSession s, ChangeListener list)
		{
			app = a;
			session = s;
			listener = list;
		}
	}

	final prisms.arch.event.PrismsProperty<?> theProperty;

	private ChangeEvent [] theChangeEvents;

	private final java.util.ArrayList<Listener> theListeners;

	private long theUpdateInterval;

	/**
	 * Creates an inspector utility
	 * 
	 * @param property The property of the inspector that this util will aid
	 * @param pclConfig The configuration of the property change listener that this util's inspector
	 *        was created to inspect
	 * @param inspectorConfig The configuration of the util's inspector
	 */
	public InspectorUtils(prisms.arch.event.PrismsProperty<?> property,
		prisms.arch.PrismsConfig pclConfig, prisms.arch.PrismsConfig inspectorConfig)
	{
		theListeners = new java.util.ArrayList<Listener>();
		theProperty = property;
		java.util.ArrayList<ChangeEvent> evts = new java.util.ArrayList<ChangeEvent>();
		for(prisms.arch.PrismsConfig evtConfig : pclConfig.subConfigs("changeEvent"))
			evts.add(prisms.arch.event.GlobalPropertyManager.parseChangeEvent(evtConfig));
		for(prisms.arch.PrismsConfig evtConfig : inspectorConfig.subConfigs("changeEvent"))
			evts.add(prisms.arch.event.GlobalPropertyManager.parseChangeEvent(evtConfig));
		theChangeEvents = evts.toArray(new ChangeEvent [evts.size()]);
	}

	/**
	 * @param interval The interval over which this inspector should refresh the content of its
	 *        property. The refresh task is run as a scheduled task within the application being
	 *        inspected.
	 */
	public void setUpdateInterval(long interval)
	{
		theUpdateInterval = interval;
	}

	/**
	 * Registers a listener that will be notified whenever the session value of a property changes
	 * 
	 * @param session The session to listen to changes in
	 * @param cl The change listener to register
	 */
	public void registerSessionListener(final prisms.arch.PrismsSession session,
		final ChangeListener cl)
	{
		Listener ret = new Listener(session.getApp(), session, cl);
		ret.pcl = new prisms.arch.event.PrismsPCL<Object>()
		{
			public void propertyChange(prisms.arch.event.PrismsPCE<Object> evt)
			{
				cl.propertyChanged(evt.getNewValue());
				if(evt.getNewValue() != null && !evt.getNewValue().getClass().isArray()
					&& evt.getOldValue().equals(evt.getNewValue()))
					cl.valueChanged(evt.getNewValue(), true);
			}
		};
		session.addPropertyChangeListener(theProperty, ret.pcl);
		ret.els = new prisms.arch.event.PrismsEventListener [theChangeEvents.length];
		for(int e = 0; e < ret.els.length; e++)
		{
			final ChangeEvent cEvt = theChangeEvents[e];
			ret.els[e] = new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.PrismsSession session2,
					prisms.arch.event.PrismsEvent evt)
				{
					changeEvent(cEvt, evt, cl);
				}
			};
			session.addEventListener(cEvt.getEventName(), ret.els[e]);
		}
		if(theUpdateInterval > 0)
		{
			ret.autoUpdater = new Runnable()
			{
				public void run()
				{
					cl.propertyChanged(session.getProperty(theProperty));
				}
			};
			session.getApp().scheduleRecurringTask(ret.autoUpdater, theUpdateInterval);
		}
		synchronized(theListeners)
		{
			theListeners.add(ret);
		}
	}

	/**
	 * Un-registers a listener to no longer be notified when the session value of a property changes
	 * 
	 * @param cl The change listener to un-register
	 */
	public void deregisterSessionListener(ChangeListener cl)
	{
		Listener list = null;
		synchronized(theListeners)
		{
			for(int i = 0; i < theListeners.size(); i++)
				if(theListeners.get(i).listener == cl)
				{
					list = theListeners.get(i);
					theListeners.remove(i);
					break;
				}
		}
		if(list == null)
			return;
		list.session.removePropertyChangeListener(theProperty, list.pcl);
		for(int e = 0; e < list.els.length; e++)
			list.session.removeEventListener(theChangeEvents[e].getEventName(), list.els[e]);
		if(list.autoUpdater != null)
			list.app.stopRecurringTask(list.autoUpdater);
	}

	/**
	 * Registers a listener that will be notified whenever the application value of a property
	 * changes
	 * 
	 * @param app The application to listen to changes in
	 * @param cl The change listener to register
	 */
	public void registerGlobalListener(final prisms.arch.PrismsApplication app,
		final ChangeListener cl)
	{
		Listener ret = new Listener(app, null, cl);
		ret.pcl = new prisms.arch.event.PrismsPCL<Object>()
		{
			public void propertyChange(prisms.arch.event.PrismsPCE<Object> evt)
			{
				cl.propertyChanged(evt.getNewValue());
				if(evt.getNewValue() != null && !evt.getNewValue().getClass().isArray()
					&& evt.getOldValue().equals(evt.getNewValue()))
					cl.valueChanged(evt.getNewValue(), true);
			}
		};
		app.addGlobalPropertyChangeListener(theProperty, ret.pcl);
		ret.els = new prisms.arch.event.PrismsEventListener [theChangeEvents.length];
		for(int e = 0; e < ret.els.length; e++)
		{
			final ChangeEvent cEvt = theChangeEvents[e];
			ret.els[e] = new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.PrismsSession session2,
					prisms.arch.event.PrismsEvent evt)
				{
					changeEvent(cEvt, evt, cl);
				}
			};
			app.addGlobalEventListener(cEvt.getEventName(), ret.els[e]);
		}
		if(theUpdateInterval > 0)
		{
			ret.autoUpdater = new Runnable()
			{
				public void run()
				{
					cl.propertyChanged(app.getGlobalProperty(theProperty));
				}
			};
			app.scheduleRecurringTask(ret.autoUpdater, theUpdateInterval);
		}
		synchronized(theListeners)
		{
			theListeners.add(ret);
		}
	}

	/**
	 * Un-registers a listener to no longer be notified when the application value of a property
	 * changes
	 * 
	 * @param cl The change listener to un-register
	 */
	public void deregisterGlobalListener(ChangeListener cl)
	{
		Listener list = null;
		synchronized(theListeners)
		{
			for(int i = 0; i < theListeners.size(); i++)
				if(theListeners.get(i).listener == cl)
				{
					list = theListeners.get(i);
					theListeners.remove(i);
					break;
				}
		}
		if(list == null)
			return;
		list.app.removeGlobalPropertyChangeListener(theProperty, list.pcl);
		for(int e = 0; e < list.els.length; e++)
			list.app.removeGlobalEventListener(theChangeEvents[e].getEventName(), list.els[e]);
		if(list.autoUpdater != null)
			list.app.stopRecurringTask(list.autoUpdater);
	}

	void changeEvent(ChangeEvent cEvt, prisms.arch.event.PrismsEvent evt, ChangeListener cl)
	{
		Object evtProp = evt.getProperty(cEvt.getEventProperty());
		if(cEvt.getPropertyType() != null && !(cEvt.getPropertyType().isInstance(evtProp)))
			return;
		if(cEvt.getReflectPath() != null)
			evtProp = ((ReflectionPath<Object>) cEvt.getReflectPath()).follow(evtProp);
		cl.valueChanged(evtProp, cEvt.isRecursive());
	}
}
