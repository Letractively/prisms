/*
 * MessagePlugin.java Created Oct 1, 2010 by Andrew Butler, PSL
 */
package prisms.message;

import java.util.Calendar;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.arch.ds.User;
import prisms.util.Search;
import prisms.util.SearchableAPI.PreparedSearch;
import prisms.util.Sorter.Field;
import prisms.util.preferences.Preference;

/** Allows management of a user's messages from a user interface */
public class MessagePlugin implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(MessagePlugin.class);

	/**
	 * This message managerf allows customized links to be displayed in a message. When a user
	 * clicks on a custom link, the {@link LinkListener#linkClicked(Link)} method is called on this
	 * manager's link listener with the link they clicked on, allowing that listener to, for
	 * instance, display internal data that the link represents. This allows messages to be more
	 * interactive with the rest of the aplication.
	 */
	public static class Link
	{
		/** The text that is displayed in the link's UI representation */
		public final String display;

		/**
		 * The String that represents the action to be performed when the link is clicked. This
		 * value is completely determined by the creator of the link, though JSON is a good
		 * recommendation.
		 */
		public final String action;

		/**
		 * Creates a link
		 * 
		 * @param disp The display for the link
		 * @param act The action for the link
		 */
		public Link(String disp, String act)
		{
			display = disp;
			action = act;
		}

		@Override
		public String toString()
		{
			java.io.StringWriter writer = new java.io.StringWriter();
			prisms.util.json.JsonStreamWriter jsw = new prisms.util.json.JsonStreamWriter(writer);
			try
			{
				jsw.startObject();
				jsw.startProperty("@prisms-link");
				jsw.writeString(action);
				jsw.startProperty("display");
				jsw.startProperty(display);
				jsw.endObject();
			} catch(java.io.IOException e)
			{
				throw new IllegalStateException("IOException from StringWriter?!!!", e);
			}
			return writer.toString();
		}
	}

	/** Listens for clicks on links */
	public static interface LinkListener
	{
		/**
		 * Called when the user clicks on a link
		 * 
		 * @param link The link the user clicked on
		 */
		void linkClicked(Link link);
	}

	static final Preference<Integer> theCountPref;

	static int STAR = 0;

	static int FROM = 1;

	static int SUBJECT = 2;

	static int ATTACH = 3;

	static int TIME = 4;

	static java.text.SimpleDateFormat SAME_YEAR_FORMAT;

	static java.text.SimpleDateFormat CROSS_YEAR_FORMAT;

	static
	{
		SAME_YEAR_FORMAT = new java.text.SimpleDateFormat("mmm dd");
		CROSS_YEAR_FORMAT = new java.text.SimpleDateFormat("ddmmmyyyy");
		theCountPref = new Preference<Integer>("Messaging", "Displayed Messages",
			Preference.Type.NONEG_INT, Integer.class, true);
		theCountPref.setDescription("The number of messages to display at a time");
	}

	private PrismsSession theSession;

	private String theName;

	private LinkListener theListener;

	private MessageManager theManager;

	private prisms.ui.SortTableStructure theTable;

	int theStart;

	int theCount;

	int theInboxCount;

	int theDraftCount;

	long [] theSnapshot;

	long theSnapshotTime;

	prisms.util.LongList theSelectedIndices;

	private PreparedSearch<Field> theInboxSearch;

	private PreparedSearch<Field> theSentSearch;

	private PreparedSearch<Field> theDraftsSearch;

	private PreparedSearch<Field> theAllMailSearch;

	private PreparedSearch<Field> theTrashSearch;

	private PreparedSearch<Field> theCurrentSearch;

	private PreparedSearch<Field> theInboxUnreadSearch;

	private ConversationHolder [] theCurrentView;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		String mgrPropName = config.get("manager");
		if(mgrPropName == null)
			throw new IllegalStateException("No manager configured for message plugin " + theName);
		prisms.arch.event.PrismsProperty<? extends MessageManager> mgrProp;
		mgrProp = prisms.arch.event.PrismsProperty.get(mgrPropName, MessageManager.class);
		theManager = session.getProperty(mgrProp);

		theCurrentView = new ConversationHolder [0];

		theStart = 1;
		prisms.util.preferences.Preferences prefs = theSession.getPreferences();
		if(prefs.get(theCountPref) == null)
			prefs.set(theCountPref, Integer.valueOf(10));
		theCount = prefs.get(theCountPref).intValue();
		session.addEventListener("preferencesChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(!(evt instanceof prisms.util.preferences.PreferenceEvent))
					return;
				prisms.util.preferences.PreferenceEvent pEvt = (prisms.util.preferences.PreferenceEvent) evt;
				if(pEvt.getPreference().equals(theCountPref))
				{
					theStart = 1;
					theCount = ((Integer) pEvt.getNewValue()).intValue();
					refresh(false);
				}
			}

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Messaging Preference Applier";
			}
		});

		theTable = new prisms.ui.SortTableStructure(TIME + 1);
		theTable.setColumn(STAR, "", false);
		theTable.setColumn(FROM, "", false);
		theTable.setColumn(SUBJECT, "", false);
		theTable.setColumn(ATTACH, "", false);
		theTable.setColumn(TIME, "", false);
		theSelectedIndices = new prisms.util.LongList();

		MessageSearch.ViewSearch noDelSearch = new MessageSearch.ViewSearch(theSession.getUser(),
			null, null, Boolean.FALSE);
		try
		{
			theAllMailSearch = theManager.views().prepare(
				new MessageSearch.SentSearch(true).and(noDelSearch), null);
			theInboxSearch = theManager
				.views()
				.prepare(
					new MessageSearch.ViewSearch(theSession.getUser(), Boolean.FALSE, null, null)
						.and(noDelSearch),
					null);
			theSentSearch = theManager.views().prepare(
				new MessageSearch.AuthorSearch(theSession.getUser()).and(noDelSearch), null);
			theDraftsSearch = theManager.views().prepare(
				new MessageSearch.AuthorSearch(theSession.getUser()).and(
					new MessageSearch.SentSearch(false)).and(noDelSearch), null);
			theTrashSearch = theManager.views().prepare(
				new MessageSearch.SentSearch(true).and(new MessageSearch.ViewSearch(theSession
					.getUser(), null, null, Boolean.TRUE)), null);
			theInboxUnreadSearch = theManager.views().prepare(
				new MessageSearch.ViewSearch(theSession.getUser(), Boolean.FALSE, null, null).and(
					noDelSearch).and(
					new MessageSearch.ReadTimeSearch(theSession.getUser(), Search.Operator.GTE,
						new Search.SearchDate(0), false)), null);
		} catch(PrismsMessageException e)
		{
			throw new IllegalStateException("Could not prepare searches for messaging", e);
		}

		theCurrentSearch = theInboxSearch;

		final Runnable checkTask = new Runnable()
		{
			public void run()
			{
				checkMessageCount();
				checkForNewMessages();
			}

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Messaging Timed Updater";
			}
		};
		theSession.getApp().scheduleRecurringTask(checkTask, 60000);
		theSession.addEventListener("destroy", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				getSession().getApp().stopRecurringTask(checkTask);
			}
		});
	}

	public void initClient()
	{
		refresh(false);
		checkMessageCount();
	}

	public void processEvent(JSONObject evt)
	{
		if("goToBox".equals(evt.get("method")))
		{
			PreparedSearch<Field> newSearch;
			if("inbox".equals(evt.get("box")))
				newSearch = theInboxSearch;
			else if("sent".equals(evt.get("box")))
				newSearch = theSentSearch;
			else if("drafts".equals(evt.get("box")))
				newSearch = theDraftsSearch;
			else if("all".equals(evt.get("box")))
				newSearch = theAllMailSearch;
			else if("trash".equals(evt.get("box")))
				newSearch = theTrashSearch;
			else
				throw new IllegalArgumentException("Unrecognized box name: " + evt.get("box"));
			if(newSearch != theCurrentSearch)
			{
				theStart = 1;
				refresh(false);
			}
		}
		else if("search".equals(evt.get("method")))
		{
			// TODO
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}

	/** @return The session that this plugin belongs to */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/** @return This plugin's name */
	public String getName()
	{
		return theName;
	}

	/** @return The message manager that this plugin gets messages from */
	public MessageManager getManager()
	{
		return theManager;
	}

	/** @return The listener listening to link clicks in this manager */
	public LinkListener getLinkListener()
	{
		return theListener;
	}

	/** @param listener The listener to listen to link clicks in this manager */
	public void setLinkListener(LinkListener listener)
	{
		theListener = listener;
	}

	void checkMessageCount()
	{
		if(theSession == null || theManager == null)
			return;
		int in, drafts;
		try
		{
			in = theManager.execute(theInboxUnreadSearch).length;
			drafts = theManager.execute(theDraftsSearch).length;
		} catch(PrismsMessageException e)
		{
			log.error("Could not get message count", e);
			return;
		}
		if(in != theInboxCount || drafts != theDraftCount)
		{
			theInboxCount = in;
			theDraftCount = drafts;
			JSONObject evt = new JSONObject();
			evt.put("plugin", theName);
			evt.put("method", "setMessageCount");
			evt.put("inboxCount", Integer.valueOf(theInboxCount));
			evt.put("draftCount", Integer.valueOf(theDraftCount));
			theSession.postOutgoingEvent(evt);
		}
	}

	/** Checks for new messages */
	protected void checkForNewMessages()
	{
		if(theSession == null || theManager == null)
			return;
		// boolean newMessages = theSnapshotTime <= 0;
		// if(!newMessages)
		// {
		// Search search = new MessageSearch.MsgExpressionSearch(true).addOps(theCurrentSearch,
		// new MessageSearch.SentSearch(MessageSearch.Operator.GTE,
		// new MessageSearch.SearchDate(theSnapshotTime)));
		// try
		// {
		// newMessages = theManager.getConversations(search).length > 0;
		// } catch(PrismsMessageException e)
		// {
		// log.error("Could not query for new messages", e);
		// return;
		// }
		// }
		// if(newMessages)
		// refresh(false);
	}

	/**
	 * Refreshes the client's view of the messages
	 * 
	 * @param show Whether to tab to the message view
	 */
	protected synchronized void refresh(boolean show)
	{
		prisms.ui.UI.DefaultProgressInformer pi = new prisms.ui.UI.DefaultProgressInformer();
		pi.setProgressText("Querying messages");
		getSession().getUI().startTimedTask(pi);
		try
		{
			theSnapshotTime = System.currentTimeMillis();
			try
			{
				theSnapshot = theManager.views().execute(theCurrentSearch);
			} catch(PrismsMessageException e)
			{
				throw new IllegalStateException("Could not query messages for search "
					+ theCurrentSearch.getSearch(), e);
			}
			String baseText;
			try
			{
				theSnapshot = theManager.views().execute(theCurrentSearch);

				baseText = pi.getTaskText();
				pi.setProgressText(baseText + " (" + theSnapshot.length + " conversations total)");
			} catch(PrismsMessageException e)
			{
				theSnapshot = new long [0];
				throw new IllegalStateException("Could not get messages", e);
			}
			if(theStart - 1 > theSnapshot.length)
				theStart = 1;
			java.util.Iterator<Long> selIter = theSelectedIndices.iterator();
			while(selIter.hasNext())
				if(!prisms.util.ArrayUtils.containsP(theSnapshot, selIter.next()))
					selIter.remove();
			int count = theCount;
			if(count > theSnapshot.length - theStart + 1)
				count = theSnapshot.length - theStart + 1;
			pi.setProgressText(baseText + " (displaying " + count + " of " + theSnapshot.length
				+ " conversations)");
			sendDisplay(true, show);
		} finally
		{
			pi.setDone();
		}
	}

	/**
	 * Sends the message display to the client
	 * 
	 * @param refresh Whether to refresh the cached view or just send the cached view
	 * @param show Whether to tab to the message view
	 */
	protected void sendDisplay(boolean refresh, boolean show)
	{
		if(refresh)
		{
			long [] fs = getFilteredSnapshot();
			if(fs.length == 0)
				theCurrentView = new ConversationHolder [0];
			else
				try
				{
					theCurrentView = theManager.getConversations(fs);
				} catch(PrismsMessageException e)
				{
					throw new IllegalStateException("Could not get messages", e);
				}
		}
		theTable.setRowCount(theCurrentView.length);
		for(int i = 0; i < theCurrentView.length; i++)
		{
			ConversationHolder c = theCurrentView[i];
			JSONObject starLink = prisms.util.PrismsUtils.rEventProps("type", "starClicked",
				"conversation", Long.toHexString(c.getConversation().getID()));
			theTable
				.row(i)
				.cell(STAR)
				.set(null, starLink,
					c.getConversation().isStarred() ? "message/starred" : "message/unstarred");
			theTable.row(i).cell(FROM).setLabel(getFromLabel(c));
			theTable.row(i).cell(SUBJECT).setLabel(getSubjectLabel(c));
			boolean attach = false;
			for(MessageView msg : c)
				if(msg.getMessage().getAttachmentCount() > 0)
				{
					attach = true;
					break;
				}
			if(attach)
				theTable.row(i).cell(ATTACH).set(null, null, null);
			else
				theTable.row(i).cell(ATTACH).set(null, null, "message/attachment");
			theTable.row(i).cell(TIME).setLabel(getTimeLabel(c));
		}
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setMessages");
		evt.put("data", theTable.serialize(theStart, theStart + theCurrentView.length - 1,
			theCount, theSnapshot == null ? 0 : theSnapshot.length));
		evt.put("show", Boolean.valueOf(show));
		theSession.postOutgoingEvent(evt);

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setMessageCount");
		evt.put("inboxCount", Integer.valueOf(theInboxCount));
		evt.put("draftCount", Integer.valueOf(theDraftCount));
		theSession.postOutgoingEvent(evt);
	}

	private long [] getFilteredSnapshot()
	{
		if(theSnapshot == null)
			return new long [0];
		if(theStart < 1)
			theStart = 1;
		if(theStart > theSnapshot.length)
			return new long [0];
		int length = theCount;
		if(length > theSnapshot.length - theStart + 1)
			length = theSnapshot.length - theStart + 1;
		long [] ret = new long [length];
		System.arraycopy(theSnapshot, theStart - 1, ret, 0, length);
		return ret;
	}

	private String getFromLabel(ConversationHolder c)
	{
		java.util.HashSet<User> users = new java.util.HashSet<User>();
		MessageView first = c.getFirstMessage();
		String ret = getUserString(first.getMessage().getAuthor());
		users.add(first.getMessage().getAuthor());
		boolean addingNames = true;
		int namesAdded = 1;
		StringBuilder suffix = new StringBuilder();
		for(MessageView m : c)
		{
			if(users.contains(m.getMessage().getAuthor()))
				continue;
			users.add(m.getMessage().getAuthor());

			if(!addingNames)
				continue;
			String name = getUserString(m.getMessage().getAuthor());
			if(ret.length() + suffix.length() + name.length() + 5 > 40)
			{
				addingNames = false;
				continue;
			}
			namesAdded++;
			suffix.insert(0, ", ");
			suffix.insert(0, name);
		}

		if(!addingNames)
		{
			suffix.insert(0, "...");
			suffix.append(", ");
			suffix.append(users.size() - namesAdded);
			suffix.append(" others");
		}
		suffix.append(" (");
		suffix.append(c.getMessageCount());
		suffix.append(")");
		return ret + suffix;
	}

	private String getUserString(User user)
	{
		// TODO
		return user.getName();
	}

	private String getSubjectLabel(ConversationHolder c)
	{
		MessageView first = c.getFirstMessage();
		String ret = first.getMessage().getSubject();
		if(ret.length() > 50)
			ret = ret.substring(0, 48) + "\u2026";
		else if(ret.length() < 35)
		{
			String preview;
			try
			{
				preview = c.getLastMessage().getMessage().getContent(47 - ret.length() + 1);
				if(preview.length() > 47 - ret.length())
					preview = preview.substring(0, 50 - ret.length() - 2) + "\u2026";
				ret += " - " + preview;
			} catch(PrismsMessageException e)
			{
				log.error("Could not preview message " + ret, e);
			}
		}
		return ret;
	}

	private String getTimeLabel(ConversationHolder c)
	{
		long time = c.getLastMessage().getMessage().getTime();
		long now = System.currentTimeMillis();
		long diff = now - time;
		if(diff < 60000)
			return "Seconds ago";
		else if(diff < 60 * 60000)
			return Math.round(diff * 1.0f / 60000) + " minutes ago";
		else if(diff < 24 * 60 * 60000)
			return Math.round(diff * 1.0f / 60 / 60000) + " hours ago";
		else
		{
			Calendar timeCal = Calendar.getInstance();
			timeCal.setTimeInMillis(time);
			Calendar nowCal = Calendar.getInstance();
			nowCal.setTimeInMillis(now);
			if(timeCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR))
				return SAME_YEAR_FORMAT.format(timeCal.getTime());
			else
				return CROSS_YEAR_FORMAT.format(timeCal.getTime());
		}
	}

	private static java.util.regex.Matcher linkMatcher(String messageContent)
	{
		return java.util.regex.Pattern.compile("\\{@prisms-link:").matcher(messageContent);
	}

	/**
	 * @param messageContent The content of the message to search for links
	 * @return The indices of the start of each link in the message
	 */
	public static int [] findLinks(String messageContent)
	{
		java.util.regex.Matcher matcher = linkMatcher(messageContent);
		prisms.util.IntList ret = new prisms.util.IntList();
		while(matcher.find())
			ret.add(matcher.start());
		return ret.toArray();
	}

	/**
	 * @param messageContent The content of the message to search for links
	 * @return An array of alternating Strings and Links that represent how the message will be
	 *         displayed to the user
	 */
	public static Object [] parseForLinks(String messageContent)
	{
		java.util.ArrayList<Object> ret = new java.util.ArrayList<Object>();
		java.io.StringReader reader = new java.io.StringReader(messageContent);
		prisms.util.json.SAJParser.DefaultHandler handler;
		handler = new prisms.util.json.SAJParser.DefaultHandler();
		java.util.regex.Matcher matcher = linkMatcher(messageContent);
		int lastEnd = 0;
		while(matcher.find())
		{
			if(matcher.start() != lastEnd)
				ret.add(messageContent.substring(lastEnd, matcher.start()));
			try
			{
				reader.skip(matcher.start() - lastEnd);
				new prisms.util.json.SAJParser().parse(reader, handler);
			} catch(java.io.IOException e)
			{
				throw new IllegalStateException("IOException from a StringReader?!!");
			} catch(prisms.util.json.SAJParser.ParseException e)
			{
				if(matcher.start() != lastEnd)
					ret.remove(ret.size() - 1);
				try
				{
					reader.skip(lastEnd - matcher.start());
				} catch(java.io.IOException e1)
				{
					throw new IllegalStateException("IOException from a StringReader?!!");
				}
				continue;
			}
			org.json.simple.JSONObject json = (org.json.simple.JSONObject) handler.finalValue();
			ret.add(new Link((String) json.get("@prisms-link"), (String) json.get("display")));
			lastEnd += handler.getState().getIndex();
		}
		if(lastEnd != messageContent.length())
			ret.add(messageContent.substring(lastEnd));
		return ret.toArray();
	}
}
