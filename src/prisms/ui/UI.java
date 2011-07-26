/**
 * UI.java Created Oct 10, 2007 by Andrew Butler, PSL
 */
package prisms.ui;

import org.json.simple.JSONObject;

import prisms.arch.PrismsConfig;
import prisms.arch.PrismsSession;

/** A user interface plugin that allows plugins to get direct, generic-form input from the user. */
public interface UI extends prisms.arch.AppPlugin
{
	/** The types of listeners that this UI widget can handle */
	public static enum EventType
	{
		/** An error message to the user */
		ERROR(1),
		/** A warning message to the user */
		WARNING(2),
		/** An information message to the user */
		INFO(6),
		/** An option for the user to choose one of two simple options */
		CONFIRM(3),
		/** An option for the user to enter any text string */
		INPUT(4),
		/** An option for the user to choose between any number of options */
		SELECT(5),
		/** A notification to the user of the progress of a task */
		PROGRESS(7);

		final String display;

		final int priority;

		EventType(int pri)
		{
			display = toString().toLowerCase();
			priority = pri;
		}
	}

	/** An event object is a representation of a UI event to the user */
	public static class EventObject implements Comparable<EventObject>
	{
		/** The type of this event */
		public final EventType type;

		/** The message ID of this event */
		public final String id;

		final JSONObject event;

		/** The listener for this event object */
		public final Object listener;

		EventObject(EventType aType, String message, Object L, Object... options)
		{
			type = aType;
			id = prisms.util.PrismsUtils.getRandomString(16);
			listener = L;
			event = new JSONObject();
			event.put("plugin", "UI");
			event.put("messageID", id);
			event.put("method", type.display);
			event.put("message", prisms.util.PrismsUtils.encodeUnicode(message));
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

		/**
		 * @param title The title that should be displayed for this event
		 * @return This event object for more chained calls
		 */
		public EventObject setTitle(String title)
		{
			event.put("title", title);
			return this;
		}

		/**
		 * @param label The label that should be displayed for the OK button for this event
		 * @return This event object for more chained calls
		 */
		public EventObject setOkLabel(String label)
		{
			event.put("okLabel", label);
			return this;
		}

		/**
		 * @param label The label that should be displayed for the Cancel button for this event
		 * @return This event object for more chained calls
		 */
		public EventObject setCancelLabel(String label)
		{
			event.put("cancelLabel", label);
			return this;
		}

		/**
		 * Sets the title, OK label, and Cancel label for this event. Any of these that are null
		 * will be replaced by default settings.
		 * 
		 * @param title The title that should be displayed for this event
		 * @param okLabel The label that should be displayed for the OK button for this event
		 * @param cancelLabel The label that should be displayed for the Cancel button for this
		 *        event
		 * @return This event object for more chained calls
		 */
		public EventObject setAll(String title, String okLabel, String cancelLabel)
		{
			event.put("title", title);
			event.put("okLabel", okLabel);
			event.put("cancelLabel", cancelLabel);
			return this;
		}

		public int compareTo(EventObject o)
		{
			int ret = type.priority - o.type.priority;
			if(ret != 0)
				return ret;
			return id.hashCode() - o.id.hashCode();
		}
	}

	/**
	 * Informs the user of an error
	 * 
	 * @param message The error message to display
	 * @return The event object for the new error message event
	 */
	public EventObject error(String message);

	/**
	 * Informs the user of an error
	 * 
	 * @param message The error message to display
	 * @param L The listener to notify when the user acknowledges the message
	 * @return The event object for the new error message event
	 */
	public EventObject error(String message, AcknowledgeListener L);

	/**
	 * Warns the user
	 * 
	 * @param message The warning message to display
	 * @return The event object for the new warning message event
	 */
	public EventObject warn(String message);

	/**
	 * Warns the user
	 * 
	 * @param message The warning message to display
	 * @param L The listener to notify when the user acknowledges the message
	 * @return The event object for the new warning message event
	 */
	public EventObject warn(String message, AcknowledgeListener L);

	/**
	 * Informs the user
	 * 
	 * @param message The informational message to display
	 * @return The event object for the new information message event
	 */
	public EventObject info(String message);

	/**
	 * Informs the user
	 * 
	 * @param message The informational message to display
	 * @param L The listener to notify when the user acknowledges the message
	 * @return The event object for the new information message event
	 */
	public EventObject info(String message, AcknowledgeListener L);

	/**
	 * Asks the user for confirmation of a requested action
	 * 
	 * @param message The confirmation message to display to the user
	 * @param L The listener that will be informed of the user's choice
	 * @return The event object for the new confirmation message event
	 */
	public EventObject confirm(String message, ConfirmListener L);

	/**
	 * Asks the user for textual input
	 * 
	 * @param message The message to display to the user
	 * @param def The default value to show to the user
	 * @param L The listener that will be informed of the user's input
	 * @return The event object for the new input message event
	 */
	public EventObject input(String message, String def, InputListener L);

	/**
	 * Asks the user to select between a set of options
	 * 
	 * @param message The message to display to the user
	 * @param options The options that the user may choose from
	 * @param init The index of the default option
	 * @param L The listener that will be informed of the user's choice
	 * @return The event object for the new selection message event
	 */
	public EventObject select(String message, String [] options, int init, SelectListener L);

	/**
	 * Presents the progress of a task to the user
	 * 
	 * @param informer An object that allows the client to track the progress of the task and
	 *        possibly to cancel it
	 * @return The event object for the new progress message event
	 */
	public EventObject startTimedTask(ProgressInformer informer);

	/** @return Whether the active user interface dialog is a progress dialog */
	public boolean isProgressShowing();

	/**
	 * @return The EventObject for the dialog at the top of this UI's queue, or null if there is no
	 *         current dialog
	 */
	public EventObject getTopListener();

	/**
	 * @return All listeners stacked (or queued) for this UI widget, ordered by the order they would
	 *         appear to the user
	 */
	public EventObject [] getListeners();

	/** A listener to be informed when a user dismisses a UI message that requires no response */
	public static interface AcknowledgeListener
	{
		/** Called when the user dismisses the UI message */
		void acknowledged();
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

	/** A listener to be informed when the user finishes an input request */
	public static interface InputListener
	{
		/**
		 * Called when the user has finished giving input
		 * 
		 * @param input The input the user gave, or null if the user cancelled input
		 */
		void inputed(String input);
	}

	/** A listener to be informed when the user makes a choice for a selection request */
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

		/** @return Whether the task has finished */
		boolean isTaskDone();

		/** @return A dynamic description of the task's progress or status */
		String getTaskText();

		/** @return Whether the task can be cancelled at this point in its execution */
		boolean isCancelable();

		/**
		 * Called when the user chooses to cancel the task
		 * 
		 * @throws IllegalStateException If the task cannot be cancelled at the moment
		 */
		void cancel() throws IllegalStateException;
	}

	/** Implements a UI plugin for a normal human interface */
	public static class NormalUI implements UI
	{
		private prisms.arch.PrismsSession theSession;

		private java.util.Map<String, Object> theListeners;

		private java.util.Queue<EventObject> theEventQueue;

		private boolean isStack;

		/**
		 * Creates a UI plugin
		 * 
		 * @param session The session that this UI is for
		 */
		public NormalUI(prisms.arch.PrismsSession session)
		{
			theSession = session;
			theListeners = new java.util.concurrent.ConcurrentHashMap<String, Object>();
			theEventQueue = new java.util.concurrent.ConcurrentLinkedQueue<EventObject>();
			isStack = true;
		}

		/**
		 * Sets this UI's behavior when multiple response-required messages are given to the UI.
		 * Stack behavior (true, the default) means that when a new message is posted, it is
		 * displayed on top of older messages until the user has responded, then older messages will
		 * be re-displayed. Queue behavior (false) means that when a new message is posted, it will
		 * only be displayed after all other current response-required messges are responded to.
		 * 
		 * @param stack Whether to use stack- or queue-behavior for this UI
		 */
		public void setBehavior(boolean stack)
		{
			isStack = stack;
		}

		public void initPlugin(PrismsSession session, PrismsConfig config)
		{
		}

		public void initClient()
		{
			EventObject first = theEventQueue.peek();
			if(first != null)
			{
				if(first.listener instanceof ProgressInformer
					&& ((ProgressInformer) first.listener).isTaskDone())
				{
					removeEvent(first.id);
					initClient();
					return;
				}
			}
			if(first != null)
			{
				if(first.listener instanceof ProgressInformer)
				{
					ProgressInformer pi = (ProgressInformer) first.listener;
					first.event.put("message", pi.getTaskText());
					first.event.put("length", Integer.valueOf(pi.getTaskScale()));
					first.event.put("progress", Integer.valueOf(pi.getTaskProgress()));
					first.event.put("cancelable", Boolean.valueOf(pi.isCancelable()));
				}
				theSession.postOutgoingEvent(first.event);
			}
			else
			{
				JSONObject evt = new JSONObject();
				evt.put("plugin", "UI");
				evt.put("method", "close");
				theSession.postOutgoingEvent(evt);
			}
		}

		public void processEvent(JSONObject evt)
		{
			String messageID = (String) evt.get("messageID");
			Object listener = null;
			if(messageID != null)
			{
				listener = theListeners.get(messageID);
				if("eventReturned".equals(evt.get("method")))
					removeEvent(messageID);
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
						value = Boolean.valueOf(false);
					((ConfirmListener) listener).confirmed(((Boolean) value).booleanValue());
				}
				else if(listener instanceof InputListener)
				{
					if(value != null && !(value instanceof String))
						throw new IllegalArgumentException(
							"Input dialog requires string return value");
					((InputListener) listener).inputed(prisms.util.PrismsUtils
						.decodeUnicode((String) value));
				}
				else if(listener instanceof SelectListener)
				{
					if(value != null && !(value instanceof String))
						throw new IllegalArgumentException(
							"Select dialog requires string return value");
					((SelectListener) listener).selected(prisms.util.PrismsUtils
						.decodeUnicode((String) value));
				}
				else if(listener instanceof ProgressInformer)
					throw new IllegalArgumentException(
						"Cannot call eventReturned on a progress dialog");
				else if(listener instanceof AcknowledgeListener)
					((AcknowledgeListener) listener).acknowledged();
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
				throw new IllegalArgumentException("Unrecognized UI event: " + evt);
			initClient();
		}

		public EventObject error(String message)
		{
			return addEvent(new EventObject(EventType.ERROR, message, null));
		}

		public EventObject error(String message, AcknowledgeListener L)
		{
			return addEvent(new EventObject(EventType.ERROR, message, L));
		}

		public EventObject warn(String message)
		{
			return addEvent(new EventObject(EventType.WARNING, message, null));
		}

		public EventObject warn(String message, AcknowledgeListener L)
		{
			return addEvent(new EventObject(EventType.WARNING, message, L));
		}

		public EventObject info(String message)
		{
			return addEvent(new EventObject(EventType.INFO, message, null));
		}

		public EventObject info(String message, AcknowledgeListener L)
		{
			return addEvent(new EventObject(EventType.INFO, message, L));
		}

		public EventObject confirm(String message, ConfirmListener L)
		{
			return addEvent(new EventObject(EventType.CONFIRM, message, L));
		}

		public EventObject input(String message, String def, InputListener L)
		{
			if(def != null)
				def = prisms.util.PrismsUtils.encodeUnicode(def);
			return addEvent(new EventObject(EventType.INPUT, message, L, "init", def));
		}

		public EventObject select(String message, String [] options, int init, SelectListener L)
		{
			String [] copy = new String [options.length];
			for(int i = 0; i < options.length; i++)
				copy[i] = prisms.util.PrismsUtils.encodeUnicode(options[i]);
			return addEvent(new EventObject(EventType.SELECT, message, L, "options", copy,
				"initSelection", Integer.valueOf(init > 0 ? init : -1)));
		}

		public EventObject startTimedTask(ProgressInformer informer)
		{
			return addEvent(new EventObject(EventType.PROGRESS, informer.getTaskText(), informer,
				"length", Integer.valueOf(informer.getTaskScale()), "progress",
				Integer.valueOf(informer.getTaskProgress()), "cancelable", Boolean.valueOf(informer
					.isCancelable())));
		}

		public boolean isProgressShowing()
		{
			EventObject eo = theEventQueue.peek();
			return eo != null && eo.type == EventType.PROGRESS;
		}

		public EventObject getTopListener()
		{
			return theEventQueue.peek();
		}

		public EventObject [] getListeners()
		{
			return theEventQueue.toArray(new EventObject [0]);
		}

		private EventObject addEvent(EventObject obj)
		{
			for(EventObject eo : theEventQueue)
				if(eo.listener == obj.listener)
					return eo;
			if(obj.listener != null)
				theListeners.put(obj.id, obj.listener);

			EventObject oldFirst;
			if(theEventQueue.isEmpty())
				oldFirst = null;
			else
				oldFirst = theEventQueue.peek();
			theEventQueue.add(obj);
			if(isStack)
				while(!theEventQueue.isEmpty() && theEventQueue.peek() != obj)
					theEventQueue.add(theEventQueue.remove());
			if(theEventQueue.peek() != oldFirst)
				theSession.postOutgoingEvent(obj.event);
			return obj;
		}

		private void removeEvent(String messageID)
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
				theSession.postOutgoingEvent(theEventQueue.peek().event);
		}
	}

	/** Spoofs a UI plugin for a web service interface */
	public static class ServiceUI implements UI
	{
		public void initPlugin(PrismsSession session, PrismsConfig config)
		{
		}

		public void initClient()
		{
		}

		public void processEvent(JSONObject evt)
		{
		}

		public EventObject error(String message)
		{
			throw new IllegalStateException(message);
		}

		public EventObject error(String message, AcknowledgeListener L)
		{
			L.acknowledged();
			throw new IllegalStateException(message);
		}

		public EventObject warn(String message)
		{
			return new EventObject(EventType.WARNING, message, null);
		}

		public EventObject warn(String message, AcknowledgeListener L)
		{
			L.acknowledged();
			return new EventObject(EventType.WARNING, message, null);
		}

		public EventObject info(String message)
		{
			return new EventObject(EventType.INFO, message, null);
		}

		public EventObject info(String message, AcknowledgeListener L)
		{
			L.acknowledged();
			return new EventObject(EventType.INFO, message, null);
		}

		public EventObject confirm(String message, ConfirmListener L)
		{
			L.confirmed(true);
			return new EventObject(EventType.CONFIRM, message, null);
		}

		public EventObject input(String message, String def, InputListener L)
		{
			L.inputed(null);
			return new EventObject(EventType.INPUT, message, null);
		}

		public EventObject select(String message, String [] options, int init, SelectListener L)
		{
			L.selected(null);
			return new EventObject(EventType.SELECT, message, null);
		}

		public EventObject startTimedTask(ProgressInformer informer)
		{
			return new EventObject(EventType.PROGRESS, informer.getTaskText(), null);
		}

		public boolean isProgressShowing()
		{
			return false;
		}

		public EventObject getTopListener()
		{
			return null;
		}

		public EventObject [] getListeners()
		{
			return new EventObject [0];
		}
	}

	/** A simple, complete implementation of the {@link ProgressInformer} */
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

		/** @return Whether this task has been canceled */
		public boolean isCanceled()
		{
			return isCanceled;
		}

		/** @param text The text to display to the user detailing what this task is doing */
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

		/** @param progress The number of operations that this task has completed */
		public void setProgress(int progress)
		{
			theProgress = progress;
		}

		/** @param cancelable Whether this task can be canceled by the user */
		public void setCancelable(boolean cancelable)
		{
			isCancelable = cancelable;
		}

		/** Tells the UI that this task is finished */
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
				theApp.reloadSessions();
			try
			{
				theApp.setApplicationLock(null, 0, 0, null);
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not release application lock", e);
			}
		}

		private void setAppLock()
		{
			if(!isTaskDone())
				try
				{
					theApp.setApplicationLock(getTaskText(), getTaskScale(), getTaskProgress(),
						null);
				} catch(prisms.arch.PrismsException e)
				{
					throw new IllegalStateException("Could not set application lock", e);
				}
		}
	}
}
