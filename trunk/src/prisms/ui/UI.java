/**
 * UI.java Created Oct 10, 2007 by Andrew Butler, PSL
 */
package prisms.ui;

import org.json.simple.JSONObject;

/**
 * A user interface plugin that allows plugins to get direct, generic-form input from the user.
 */
public class UI implements prisms.arch.AppPlugin
{
	private prisms.arch.PrismsSession theSession;

	String theName;

	private java.util.Map<String, Object> theListeners;

	private java.util.TreeSet<EventObject> theEventQueue;

	/**
	 * Creates a UI object
	 */
	public UI()
	{
		theListeners = new java.util.HashMap<String, Object>();
		theEventQueue = new java.util.TreeSet<EventObject>();
	}

	public void initPlugin(prisms.arch.PrismsSession session, org.dom4j.Element configEl)
	{
		theSession = session;
		theName = configEl.elementText("name");
	}

	public void initClient()
	{
		EventObject first;
		synchronized(this)
		{
			if(!theEventQueue.isEmpty())
			{
				first = theEventQueue.first();
				if(first.listener instanceof ProgressInformer
					&& ((ProgressInformer) first.listener).isTaskDone())
				{
					removeEvent(first.id);
					initClient();
					return;
				}
			}
			else
				first = null;
		}
		if(first != null)
		{
			if(first.listener instanceof ProgressInformer)
			{
				ProgressInformer pi = (ProgressInformer) first.listener;
				first.event.put("message", pi.getTaskText());
				first.event.put("length", new Integer(pi.getTaskScale()));
				first.event.put("progress", new Integer(pi.getTaskProgress()));
				first.event.put("cancelable", new Boolean(pi.isCancelable()));
			}
			theSession.postOutgoingEvent(first.event);
		}
		else
		{
			JSONObject evt = new JSONObject();
			evt.put("plugin", theName);
			evt.put("method", "close");
			theSession.postOutgoingEvent(evt);
		}
	}

	public void processEvent(JSONObject evt)
	{
		String messageID = (String) evt.get("messageID");
		Object listener;
		if(messageID != null)
		{
			synchronized(this)
			{
				listener = theListeners.get(messageID);
				if("eventReturned".equals(evt.get("method")))
					removeEvent(messageID);
			}
		}
		else if("refresh".equals(evt.get("method")))
		{
			initClient();
			return;
		}
		else
			throw new IllegalArgumentException("messageID expected");

		if("eventReturned".equals(evt.get("method")))
		{
			Object value = evt.get("value");
			if(listener instanceof ConfirmListener)
			{
				if(!Boolean.TRUE.equals(value))
					value = new Boolean(false);
				((ConfirmListener) listener).confirmed(((Boolean) value).booleanValue());
			}
			else if(listener instanceof InputListener)
			{
				if(value != null && !(value instanceof String))
					throw new IllegalArgumentException("Input dialog requires string return value");
				((InputListener) listener).inputed((String) value);
			}
			else if(listener instanceof SelectListener)
			{
				if(value != null && !(value instanceof String))
					throw new IllegalArgumentException("Select dialog requires string return value");
				((SelectListener) listener).selected((String) value);
			}
			else if(listener instanceof ProgressInformer)
				throw new IllegalArgumentException("Cannot call eventReturned on a progress dialog");
			else if(listener == null)
			{}
			else
				throw new IllegalStateException("Unrecognized listener type: "
					+ listener.getClass().getName());
		}
		else if("cancel".equals(evt.get("method")))
		{
			if(listener instanceof ProgressInformer)
			{
				try
				{
					((ProgressInformer) listener).cancel();
				} catch(IllegalStateException e)
				{
					error(e.getMessage());
				}
			}
			else if(listener instanceof ConfirmListener)
			{
				removeEvent(messageID);
				((ConfirmListener) listener).confirmed(false);
			}
			else if(listener instanceof InputListener)
			{
				removeEvent(messageID);
				((InputListener) listener).inputed(null);
			}
			else if(listener instanceof SelectListener)
			{
				removeEvent(messageID);
				((SelectListener) listener).selected(null);
			}
			else if(listener == null)
			{}
			else
				throw new IllegalStateException("Unrecognized listener type for cancel: "
					+ listener.getClass().getName());
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
		initClient();
	}

	/**
	 * Informs the user of an error
	 * 
	 * @param message The error message to display
	 */
	public void error(String message)
	{
		addEvent(new EventObject("error", message, null));
	}

	/**
	 * Warns the user
	 * 
	 * @param message The warning message to display
	 */
	public void warn(String message)
	{
		addEvent(new EventObject("warning", message, null));
	}

	/**
	 * Informs the user
	 * 
	 * @param message The informational message to display
	 */
	public void info(String message)
	{
		addEvent(new EventObject("info", message, null));
	}

	/**
	 * Asks the user for confirmation of a requested action
	 * 
	 * @param message The confirmation message to display to the user
	 * @param L The listener that will be informed of the user's choice
	 */
	public void confirm(String message, ConfirmListener L)
	{
		addEvent(new EventObject("confirm", message, L));
	}

	/**
	 * Asks the user for textual input
	 * 
	 * @param message The message to display to the user
	 * @param def The default value to show to the user
	 * @param L The listener that will be informed of the user's input
	 */
	public void input(String message, String def, InputListener L)
	{
		addEvent(new EventObject("input", message, L, "init", def));
	}

	/**
	 * Asks the user to select between a set of options
	 * 
	 * @param message The message to display to the user
	 * @param options The options that the user may choose from
	 * @param init The index of the default option
	 * @param L The listener that will be informed of the user's choice
	 */
	public void select(String message, String [] options, int init, SelectListener L)
	{
		addEvent(new EventObject("select", message, L, "options", options, "initSelection",
			new Integer(init > 0 ? init : -1)));
	}

	/**
	 * Presents the progress of a task to the user
	 * 
	 * @param informer An object that allows the client to track the progress of the task and
	 *        possibly to cancel it
	 */
	public void startTimedTask(ProgressInformer informer)
	{
		addEvent(new EventObject("progress", informer.getTaskText(), informer, "length",
			new Integer(informer.getTaskScale()), "progress", new Integer(
				informer.getTaskProgress()), "cancelable", new Boolean(informer.isCancelable())));
	}

	String createMessageID()
	{
		String ret;
		do
		{
			ret = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
		} while(theListeners.containsKey(ret));
		return ret;
	}

	private class EventObject implements Comparable<EventObject>
	{
		final String id;

		final int priority;

		final JSONObject event;

		final Object listener;

		EventObject(String type, String message, Object L, Object... options)
		{
			id = createMessageID();
			if("error".equals(type))
				priority = 1;
			else if("warning".equals(type))
				priority = 2;
			else if("confirm".equals(type))
				priority = 3;
			else if("input".equals(type))
				priority = 4;
			else if("select".equals(type))
				priority = 5;
			else if("info".equals(type))
				priority = 6;
			else if("progress".equals(type))
				priority = 7;
			else
				throw new IllegalStateException("Unexpected event type: " + type);
			listener = L;
			event = new JSONObject();
			event.put("plugin", theName);
			event.put("messageID", id);
			event.put("method", type);
			event.put("message", message);
			for(int arg = 0; arg < options.length; arg += 2)
			{
				Object val = options[arg + 1];
				if(val instanceof Object [])
				{
					org.json.simple.JSONArray array = new org.json.simple.JSONArray();
					for(Object val_i : (Object []) val)
						array.add(val_i);
					val = array;
				}
				event.put(options[arg], val);
			}
		}

		public int compareTo(EventObject o)
		{
			int ret = priority - o.priority;
			if(ret != 0)
				return ret;
			return id.hashCode() - o.id.hashCode();
		}
	}

	private synchronized void addEvent(EventObject obj)
	{
		for(EventObject eo : theEventQueue)
			if(eo.listener == obj.listener)
				return;
		if(obj.listener != null)
			theListeners.put(obj.id, obj.listener);

		EventObject oldFirst;
		if(theEventQueue.isEmpty())
			oldFirst = null;
		else
			oldFirst = theEventQueue.first();
		theEventQueue.add(obj);
		if(theEventQueue.first() != oldFirst)
			theSession.postOutgoingEvent(obj.event);
	}

	private synchronized void removeEvent(String messageID)
	{
		theListeners.remove(messageID);
		java.util.Iterator<EventObject> iter = theEventQueue.iterator();
		while(iter.hasNext())
		{
			EventObject evtObj = iter.next();
			if(messageID.equals(evtObj.id))
			{
				iter.remove();
				break;
			}
		}
		if(!theEventQueue.isEmpty())
			theSession.postOutgoingEvent(theEventQueue.first().event);
	}

	/**
	 * A listener to be informed when the user confirms or declines to confirm a confirmation
	 * request
	 */
	public static interface ConfirmListener
	{
		/**
		 * Called when the user has made a choice to confirm or decline from a confirmation request
		 * 
		 * @param confirm Whether the user confirmed
		 */
		void confirmed(boolean confirm);
	}

	/**
	 * A listener to be informed when the user finishes an input request
	 */
	public static interface InputListener
	{
		/**
		 * Called when the user has finished giving input
		 * 
		 * @param input The input the user gave, or null if the user cancelled input
		 */
		void inputed(String input);
	}

	/**
	 * A listener to be informed when the user makes a choice for a selection request
	 */
	public static interface SelectListener
	{
		/**
		 * Called when the user has made a selection
		 * 
		 * @param option The option that the user selected, or null if the user cancelled selection
		 */
		void selected(String option);
	}

	/**
	 * Informs the client of the progress of a task and allows the client the opportunity to cancel
	 * the task
	 */
	public static interface ProgressInformer
	{
		/**
		 * @return An abstract measure of the total time a task will take, or 0 if this task's
		 *         progress cannot be reported quantitatively
		 */
		int getTaskScale();

		/**
		 * @return The amount of the task that has been completed, where the result of getTaskScale
		 *         would indicate that the task is complete
		 */
		int getTaskProgress();

		/**
		 * @return Whether the task has finished
		 */
		boolean isTaskDone();

		/**
		 * @return A dynamic description of the task's progress or status
		 */
		String getTaskText();

		/**
		 * @return Whether the task can be cancelled at this point in its execution
		 */
		boolean isCancelable();

		/**
		 * Called when the user chooses to cancel the task
		 * 
		 * @throws IllegalStateException If the task cannot be cancelled at the moment
		 */
		void cancel() throws IllegalStateException;
	}

	/**
	 * A simple, complete implementation of the {@link ProgressInformer}
	 */
	public static class DefaultProgressInformer implements ProgressInformer
	{
		private String theProgressText;

		private int theProgressScale;

		private int theProgress;

		private boolean isDone;

		private boolean isCancelable;

		private boolean isCanceled;

		public String getTaskText()
		{
			return theProgressText;
		}

		public int getTaskScale()
		{
			return theProgressScale;
		}

		public int getTaskProgress()
		{
			return theProgress;
		}

		public boolean isTaskDone()
		{
			return isDone;
		}

		public boolean isCancelable()
		{
			return isCancelable;
		}

		public void cancel()
		{
			if(isCancelable)
				isCanceled = true;
		}

		/**
		 * @return Whether this task has been canceled
		 */
		public boolean isCanceled()
		{
			return isCanceled;
		}

		/**
		 * @param text The text to display to the user detailing what this task is doing
		 */
		public void setProgressText(String text)
		{
			theProgressText = text;
		}

		/**
		 * @param scale The number of operations that need to be completed before this task finishes
		 */
		public void setProgressScale(int scale)
		{
			theProgressScale = scale;
		}

		/**
		 * @param progress The number of operations that this task has completed
		 */
		public void setProgress(int progress)
		{
			theProgress = progress;
		}

		/**
		 * @param cancelable Whether this task can be canceled by the user
		 */
		public void setCancelable(boolean cancelable)
		{
			isCancelable = cancelable;
		}

		/**
		 * Tells the UI that this task is finished
		 */
		public void setDone()
		{
			isDone = true;
		}
	}

	/**
	 * A progress informer implementation that locks an application, informing the user of the
	 * progress of the operation and unlocking the app when the task is complete.
	 */
	public static class AppLockProgress extends DefaultProgressInformer
	{
		private final prisms.arch.PrismsApplication theApp;

		private boolean postReload;

		/** @param app The application to lock for this progress */
		public AppLockProgress(prisms.arch.PrismsApplication app)
		{
			theApp = app;
		}

		/**
		 * @param reload Whether the application's sessions should be reloaded after this progress
		 *        completes
		 */
		public void setPostReload(boolean reload)
		{
			postReload = reload;
		}

		@Override
		public void setProgressText(String text)
		{
			super.setProgressText(text);
			setAppLock();
		}

		@Override
		public void setProgressScale(int scale)
		{
			super.setProgressScale(scale);
			setAppLock();
		}

		@Override
		public void setProgress(int progress)
		{
			super.setProgress(progress);
			setAppLock();
		}

		@Override
		public void setDone()
		{
			super.setDone();
			if(postReload)
				theApp.reloadAll();
			theApp.setApplicationLock(null, 0, 0, null);
		}

		private void setAppLock()
		{
			if(!isTaskDone())
				theApp.setApplicationLock(getTaskText(), getTaskScale(), getTaskProgress(), null);
		}
	}
}
