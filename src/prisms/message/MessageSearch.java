/*
 * MessageSearch.java Created Jan 27, 2011 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.arch.ds.User;
import prisms.message.Message.Priority;
import prisms.message.Recipient.Applicability;
import prisms.util.Search;

/** Represents a search for messages in a message source */
public abstract class MessageSearch extends Search
{
	/** The available types of searches */
	public static enum MessageSearchType implements SearchType
	{
		/** Searches for messages by ID */
		id() {
			public MessageSearch create(String search, SearchBuilder builder)
			{
				throw new IllegalStateException("Cannot create an ID search like this");
			}
		},
		/** Searches for messages by conversationID */
		conversation() {
			public MessageSearch create(String search, SearchBuilder builder)
			{
				throw new IllegalStateException("Cannot create a conversation search like this");
			}
		},
		/** Searches for successors to a message */
		successor() {
			public MessageSearch create(String search, SearchBuilder builder)
			{
				throw new IllegalStateException("Cannot create a successor search like this");
			}
		},
		/** Searches on subject */
		subject("subject:") {
			public SubjectSearch create(String search, SearchBuilder builder)
			{
				return new SubjectSearch(search);
			}
		},
		/** Searches on author */
		author("from:") {
			public AuthorSearch create(String search, SearchBuilder builder)
			{
				MsgSearchBuilder msb = (MsgSearchBuilder) builder;
				return new AuthorSearch(msb.getUser(), msb.getEnv() == null ? null : msb.getEnv()
					.getUserSource(), search);
			}
		},
		/** Searches on when (if ever) a message was sent */
		sent("time:") {
			public SentSearch create(String search, SearchBuilder builder)
			{
				return new SentSearch(search);
			}
		},
		/** Searches on priority */
		priority("priority:") {
			public PrioritySearch create(String search, SearchBuilder builder)
			{
				return new PrioritySearch(search);
			}
		},
		/** Searches on recipients */
		recipient("to:", "direct:", "cc:", "bcc:") {
			public RecipientSearch create(String search, SearchBuilder builder)
			{
				return new RecipientSearch(search);
			}
		},
		/** Searches on when a recipient read a message */
		readTime("readTime:", "firstReadTime:") {
			public ReadTimeSearch create(String search, SearchBuilder builder)
			{
				MsgSearchBuilder msb = (MsgSearchBuilder) builder;
				return new ReadTimeSearch(msb.getUser(), msb.getEnv() == null ? null : msb.getEnv()
					.getUserSource(), search);
			}
		},
		/** Searches on users' views of messages */
		view("view:") {
			public ViewSearch create(String search, SearchBuilder builder)
			{
				MsgSearchBuilder msb = (MsgSearchBuilder) builder;
				return new ViewSearch(msb.getUser(), msb.getEnv() == null ? null : msb.getEnv()
					.getUserSource(), search);
			}
		},
		/** Searches on content */
		content("content:") {
			public ContentSearch create(String search, SearchBuilder builder)
			{
				return new ContentSearch(search);
			}
		},
		/** Searches on attachment name, type, or size */
		attachment("attach:", "attachtype:", "attachsize:") {
			public AttachmentSearch create(String search, SearchBuilder builder)
			{
				return new AttachmentSearch(search);
			}
		},
		/** Searches on the space messages take up */
		size("size:") {
			public SizeSearch create(String search, SearchBuilder builder)
			{
				return new SizeSearch(search);
			}
		},
		/** Allows programmers to search for deleted messages as well */
		deleted() {
			public MessageSearch create(String search, SearchBuilder builder)
			{
				throw new IllegalStateException("Cannot create a deleted search like this");
			}
		};

		/** The headers that prefix a search of this type */
		public final java.util.List<String> headers;

		MessageSearchType(String... _headers)
		{
			java.util.ArrayList<String> heads = new java.util.ArrayList<String>();
			for(String header : _headers)
				heads.add(header.toLowerCase());
			headers = java.util.Collections.unmodifiableList(heads);
		}

		public String [] getHeaders()
		{
			return headers.toArray(new String [headers.size()]);
		}

		/**
		 * Checks the given search string against all this type's headers and returns the index of
		 * the header prefix used in the search string
		 * 
		 * @param srch The search string
		 * @return The header index used in the search string, or -1 if none of this type's headers
		 *         are prefixed to the search string
		 */
		protected int headerIndex(String srch)
		{
			srch = srch.trim();
			for(int h = 0; h < headers.size(); h++)
			{
				boolean hasHeader = true;
				for(int c = 0; c < headers.get(h).length(); c++)
				{
					char ch = srch.charAt(c);
					if(Character.isUpperCase(ch))
						ch = (char) (ch - 'A' + 'a');
					if(ch != headers.get(h).charAt(c))
					{
						hasHeader = false;
						break;
					}
				}
				if(hasHeader)
					return h;
			}
			return -1;
		}

		/**
		 * Cleans a search query, returning only the string to search for
		 * 
		 * @param srch The search query
		 * @return The cleaned search string
		 */
		protected String clean(String srch)
		{
			srch = srch.trim();
			for(String header : headers)
			{
				if(srch.length() < header.length())
					continue;
				boolean hasHeader = true;
				for(int c = 0; c < header.length(); c++)
				{
					char ch = srch.charAt(c);
					if(Character.isUpperCase(ch))
						ch = (char) (ch - 'A' + 'a');
					if(ch != header.charAt(c))
					{
						hasHeader = false;
						break;
					}
				}
				if(hasHeader)
				{
					srch = srch.substring(header.length());
					srch = srch.trim();
					break;
				}
			}
			if((srch.startsWith("\"") && srch.endsWith("\""))
				|| (srch.startsWith("'") && srch.endsWith("'")))
				srch = srch.substring(1, srch.length() - 1);
			return srch;
		}
	}

	/** A search for a messages whose IDs match a logical expression */
	public static class IDSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.id;

		/** The operator operating on the ID threshold */
		public final Operator operator;

		/** The ID threshold. May be null for a prepared search. */
		public final Long id;

		/**
		 * @param op The operator to operate on the ID threshold
		 * @param _id The ID threshold. May be null for a prepared search.
		 */
		public IDSearch(Operator op, Long _id)
		{
			operator = op;
			id = _id;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return type.headers.get(0) + operator + (id == null ? "?" : id);
		}

		@Override
		public boolean equals(Object obj)
		{
			return obj instanceof IDSearch && operator.equals(((IDSearch) obj).operator)
				&& id == ((IDSearch) obj).id;
		}
	}

	/** Searches for messages in a given conversation */
	public static class ConversationSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.conversation;

		/** The ID of the conversation to get messages for. May be null for a prepared search. */
		public final Long conversationID;

		/**
		 * @param _id The ID of the conversation to get messages for. May be null for a prepared
		 *        search.
		 */
		public ConversationSearch(Long _id)
		{
			conversationID = _id;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return type.headers.get(0) + (conversationID == null ? "?" : conversationID);
		}

		@Override
		public boolean equals(Object obj)
		{
			return obj instanceof ConversationSearch
				&& conversationID == ((ConversationSearch) obj).conversationID;
		}
	}

	/** Searches for successors of a given message */
	public static class SuccessorSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.successor;

		/** The message to search for successors to. May be null for a prepared search. */
		public final Message message;

		/**
		 * @param _message The message to search for successors to. May be null for a prepared
		 *        search.
		 */
		public SuccessorSearch(Message _message)
		{
			message = _message;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public SuccessorSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			return "successor:" + message;
		}
	}

	/** Searches for messages based on text in a field. */
	public static abstract class StringSearch extends MessageSearch
	{
		/** The search string that this message search is for */
		public final String search;

		/**
		 * @param srch The string to search for, or the entire subject search query (with header
		 *        prefix)
		 */
		protected StringSearch(String srch)
		{
			search = getType().clean(srch);
		}

		@Override
		public String toString()
		{
			String ret = getType().headers.get(0);
			if(search.indexOf(' ') >= 0)
				ret += "\"" + search + "\"";
			else
				ret += search;
			return ret;
		}
	}

	/** Searches for messages based on their subject text. Specified by "subject:..." */
	public static class SubjectSearch extends StringSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.subject;

		/**
		 * @param srch The string to search for, or the entire subject search query (with header
		 *        prefix)
		 */
		public SubjectSearch(String srch)
		{
			super(srch);
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public SubjectSearch clone()
		{
			return this; // Immutable
		}
	}

	/** Searches for messages based on their subject text. Specified by "from:..." */
	public static class AuthorSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.author;

		/** the user to search for */
		public final User user;

		/** The name of the user(s) to search for. May be null for a prepared search. */
		public final String userName;

		/**
		 * Creates an author search from a user-entered search string
		 * 
		 * @param u The user who is searching
		 * @param us The user source of the environment
		 * @param srch The user-entered search string
		 */
		public AuthorSearch(User u, prisms.arch.ds.UserSource us, String srch)
		{
			String oSearch = srch;
			srch = type.clean(srch);
			if("me".equals(srch))
			{
				user = u;
				userName = null;
			}
			else
			{
				userName = srch;
				if(us != null)
					try
					{
						if(us.getUser(userName) == null)
							throw new IllegalArgumentException("No such user named " + userName
								+ " in search " + oSearch);
					} catch(prisms.arch.PrismsException e)
					{
						throw new IllegalStateException("Could not check presence of user", e);
					}
				user = null;
			}
		}

		/** @param _user The author to search for. May be null for a prepared search. */
		public AuthorSearch(User _user)
		{
			user = _user;
			userName = null;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public AuthorSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			StringBuilder ret = new StringBuilder();
			ret.append(type.headers.get(0));
			if(userName != null)
				ret.append(userName);
			else if(user != null)
				ret.append(user);
			else
				ret.append('?');
			return ret.toString();
		}
	}

	/** Searches based on whether a message has been sent or not */
	public static class SentSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.sent;

		/** Whether the message has been sent, or null if unspecified */
		public final Boolean sent;

		/** The operator determining how to use the search date */
		public final Operator operator;

		/**
		 * When this message was sent. May be null for a prepared search (if {@link #sent} is null).
		 */
		public final SearchDate sentTime;

		/** @param srch The entire search query (with or without header) */
		public SentSearch(String srch)
		{
			srch = type.clean(srch);
			if(srch.equalsIgnoreCase("true"))
			{
				sent = Boolean.TRUE;
				operator = null;
				sentTime = null;
			}
			else if(srch.equalsIgnoreCase("false"))
			{
				sent = Boolean.FALSE;
				operator = null;
				sentTime = null;
			}
			else
			{
				StringBuilder sb = new StringBuilder(srch);
				operator = Operator.parse(sb, srch);
				sentTime = SearchDate.parse(sb, srch);
				sent = null;
			}
		}

		/** @param _sent Whether to search for messages that have been sent, or for drafts */
		public SentSearch(boolean _sent)
		{
			sent = Boolean.valueOf(_sent);
			operator = null;
			sentTime = null;
		}

		/**
		 * @param op The operator to determine how to use the search time
		 * @param time The send or creation time that will be the border of this search. May be null
		 *        for a prepared search.
		 */
		public SentSearch(Operator op, SearchDate time)
		{
			operator = op;
			sentTime = time;
			sent = null;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public SentSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			if(operator == null)
				return type.headers.get(0) + sent;
			else
				return type.headers.get(0) + operator + sentTime;
		}
	}

	/** Searches for messages whose priority match a logical expression */
	public static class PrioritySearch extends MessageSearch
	{
		/** The type of this search */
		public static MessageSearchType type = MessageSearchType.priority;

		/** The operator determining how to use the priority */
		public final Operator operator;

		/** The priority to compare with. May be null for a prepared search. */
		public final Priority priority;

		/**
		 * Creates a priority search with the given operator and priority
		 * 
		 * @param op The operator determining how to use the priority
		 * @param pri The priority to compare with. May be null for a prepared search.
		 */
		public PrioritySearch(Operator op, Priority pri)
		{
			operator = op;
			priority = pri;
		}

		/**
		 * Creates a priority search from a user-typed search string
		 * 
		 * @param srch The search string to compile
		 */
		public PrioritySearch(String srch)
		{
			srch = type.clean(srch);
			StringBuilder sb = new StringBuilder(srch);
			operator = Operator.parse(sb, srch);
			Priority p = null;
			for(Priority pri : Priority.values())
				if(pri.name().toLowerCase().startsWith(sb.toString().toLowerCase()))
				{
					p = pri;
					break;
				}
			if(p == null)
				throw new IllegalArgumentException("Unrecognized priority: " + sb.toString()
					+ " in search " + srch);
			priority = p;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public PrioritySearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			return "priority:" + operator + priority;
		}
	}

	/**
	 * Searches for messages based on their subject text. Specified by "To:...", "Direct:...",
	 * "Cc:...", or "Bcc:..."
	 */
	public static class RecipientSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.recipient;

		/**
		 * The user to search for. May be null for a prepared search (if {@link #userName} is null).
		 */
		public final User user;

		/** The name of the user(s) to search for */
		public final String userName;

		/** The applicability to the searched recipient */
		public final Applicability applicability;

		/** @param srch The recipient search query (with header prefix, required) */
		public RecipientSearch(String srch)
		{
			user = null;
			int header = type.headerIndex(srch);
			userName = type.clean(srch);
			String origSrch = srch;
			switch(header)
			{
			case 0:
				applicability = null;
				break;
			case 1:
				applicability = Applicability.DIRECT;
				break;
			case 2:
				applicability = Applicability.COPIED;
				break;
			case 3:
				applicability = Applicability.BLIND;
				break;
			default:
				throw new IllegalArgumentException(origSrch + " is not a valid recipient operator");
			}
		}

		/**
		 * @param aUserName The name of the user to search for
		 * @param app The applicability of the recipient to search for
		 * @param withDel Whether to include only deleted items or only active items (may be null to
		 *        include both)
		 */
		public RecipientSearch(String aUserName, Applicability app, Boolean withDel)
		{
			user = null;
			userName = aUserName;
			applicability = app;
		}

		/**
		 * @param _user The user to search for. May be null for a prepared search.
		 * @param app The applicability of the recipient to search for
		 */
		public RecipientSearch(User _user, Applicability app)
		{
			user = _user;
			userName = null;
			applicability = app;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public RecipientSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			int headerIdx;
			if(applicability == null)
				headerIdx = 0;
			else
				headerIdx = applicability.ordinal() + 1;
			return type.headers.get(headerIdx) + (user == null ? userName : user.getName());
		}
	}

	/** Searches by when the user read a message */
	public static class ReadTimeSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.readTime;

		/**
		 * The user to search the read times of. May be null for a prepared search (if
		 * {@link #userName} is also null).
		 */
		public final User user;

		/** The name of the user to search the read times of */
		public final String userName;

		/** The operator to use against the read time */
		public final Operator operator;

		/** The read time to search against. May be null for a prepared search. */
		public final SearchDate readTime;

		/**
		 * Determines whether this search is for the first read time (the first time the user viewed
		 * the message) or last read time (the most recent time the user viewed the message)
		 */
		public final boolean isLast;

		/**
		 * Creates a read time search from a user-entered search string
		 * 
		 * @param u The user who is searching
		 * @param us The user source of the environment
		 * @param srch The user-entered search string
		 */
		public ReadTimeSearch(User u, prisms.arch.ds.UserSource us, String srch)
		{
			String oSearch = srch;
			int idx = type.headerIndex(srch);
			srch = type.clean(srch);
			int opIdx = -1;
			for(Operator op : Operator.values())
			{
				int opIdx2 = srch.lastIndexOf(op.toString());
				if(opIdx2 >= 0 && (opIdx < 0 || opIdx2 > opIdx))
					opIdx = opIdx2;
			}
			if(opIdx < 0)
				throw new IllegalArgumentException("No operator specified for read time search: "
					+ oSearch);
			if(opIdx == 0)
			{
				user = u;
				userName = null;
			}
			else
			{
				userName = srch.substring(0, opIdx).trim();
				if(us != null)
					try
					{
						if(us.getUser(userName) == null)
							throw new IllegalArgumentException("No such user named " + userName
								+ " in search " + oSearch);
					} catch(prisms.arch.PrismsException e)
					{
						throw new IllegalStateException("Could not check presence of user", e);
					}
				user = null;
			}
			StringBuilder sb = new StringBuilder(srch.substring(opIdx));
			operator = Operator.parse(sb, srch);
			readTime = SearchDate.parse(sb, srch);
			isLast = idx > 0;
		}

		/**
		 * Creates a read time search
		 * 
		 * @param u The user whose view to search. May be null for a prepared search.
		 * @param op The operator to use against the read time
		 * @param time The read time to search against
		 * @param last Whether the given search time is for first or last read time
		 */
		public ReadTimeSearch(User u, Operator op, SearchDate time, boolean last)
		{
			user = u;
			userName = null;
			operator = op;
			readTime = time;
			isLast = last;
		}

		/**
		 * Creates a read time search
		 * 
		 * @param uName The name of the user whose view to search
		 * @param op The operator to use against the read time
		 * @param time The read time to search against
		 * @param last Whether the given search time is for first or last read time
		 */
		public ReadTimeSearch(String uName, Operator op, SearchDate time, boolean last)
		{
			userName = uName;
			user = null;
			operator = op;
			readTime = time;
			isLast = last;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public ReadTimeSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			StringBuilder ret = new StringBuilder();
			ret.append(isLast ? "lastReadTime:" : "readTime:");
			if(userName != null)
				ret.append(userName);
			else if(user != null)
				ret.append(user);
			else
				ret.append('?');
			ret.append(operator);
			if(readTime != null)
				ret.append(readTime);
			else
				ret.append('?');
			return ret.toString();
		}
	}

	/** Searches users' views of a message */
	public static class ViewSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.view;

		/**
		 * The user whose view to search for. May be null for a prepared search (if
		 * {@link #userName} is also null).
		 */
		public final User user;

		/** The name of the user(s) whose views to search for */
		public final String userName;

		/** Whether the message has been archived by the user */
		public final Boolean archived;

		/** Whether the message has been starred by the user */
		public final Boolean starred;

		/** Whether the message has been deleted by the user */
		public final Boolean deleted;

		/**
		 * Creates a view search from a user-entered search string
		 * 
		 * @param u The user who is searching
		 * @param us The user source of the environment
		 * @param srch The view search query (with header prefix, required)
		 */
		public ViewSearch(User u, prisms.arch.ds.UserSource us, String srch)
		{
			String oSearch = srch;
			srch = type.clean(srch);
			int idx = srch.lastIndexOf(':');
			boolean not = false;
			if(idx >= 0)
			{
				if(srch.length() > idx + 1 && srch.charAt(idx + 1) == '!')
				{
					not = true;
					idx++;
				}
				String action = srch.substring(idx + 1);
				if(action.equals("read") || action.equals("archived") || action.equals("starred")
					|| action.equals("deleted"))
				{
					userName = srch.substring(0, idx);
					if(us != null)
						try
						{
							if(us.getUser(userName) == null)
								throw new IllegalArgumentException("No such user named " + userName
									+ " in search " + oSearch);
						} catch(prisms.arch.PrismsException e)
						{
							throw new IllegalStateException("Could not check presence of user", e);
						}
					user = null;
					srch = action;
				}
				else
				{
					user = u;
					userName = null;
				}
			}
			else
			{
				user = u;
				userName = null;
			}

			StringBuilder sb = new StringBuilder(srch);
			if(sb.charAt(0) == '!')
			{
				not = true;
				sb.delete(0, 1);
			}
			if(srch.equals("archived"))
			{
				sb.delete(0, "archived".length());
				archived = Boolean.valueOf(!not);
				starred = null;
				deleted = null;
			}
			else if(srch.equals("starred"))
			{
				sb.delete(0, "starred".length());
				starred = Boolean.valueOf(!not);
				archived = null;
				deleted = null;
			}
			else if(srch.equals("deleted"))
			{
				sb.delete(0, "deleted".length());
				deleted = Boolean.valueOf(!not);
				archived = null;
				starred = null;
			}
			else
				throw new IllegalArgumentException("Unrecognized view search: " + srch);
		}

		/**
		 * Constructs a view search. Only one parameter may be searched for with each ViewSearch.
		 * User ExpressionSearch to form AND's or OR's of multiple view searches if desired.
		 * 
		 * @param _user The user whose view to search. May be null for a prepared search.
		 * @param _archived Whether to search for archived or unarchived messages.
		 * @param _starred Whether to search for starred or unstarred messages.
		 * @param _deleted Whether to search for deleted or undeleted messages
		 */
		public ViewSearch(User _user, Boolean _archived, Boolean _starred, Boolean _deleted)
		{
			this(_user, null, _archived, _starred, _deleted);
		}

		/**
		 * Constructs a view search. Only one parameter may be searched for with each ViewSearch.
		 * User ExpressionSearch to form AND's or OR's of multiple view searches if desired.
		 * 
		 * @param _userName The name of the user(s) whose view(s) to search.
		 * @param _archived Whether to search for archived or unarchived messages.
		 * @param _starred Whether to search for starred or unstarred messages.
		 * @param _deleted Whether to search for deleted or undeleted messages
		 */
		public ViewSearch(String _userName, Boolean _archived, Boolean _starred, Boolean _deleted)
		{
			this(null, _userName, _archived, _starred, _deleted);
		}

		private ViewSearch(User _user, String _userName, Boolean _archived, Boolean _starred,
			Boolean _deleted)
		{
			int count = 0;
			if(_archived != null)
				count++;
			if(_starred != null)
				count++;
			if(_deleted != null)
				count++;
			if(count == 0)
				throw new IllegalArgumentException("No search input. read, archived, starred,"
					+ "deleted, first read time, or last read time must be searched for.");
			if(count > 1)
				throw new IllegalArgumentException("Multiple search inputs. Only one of read,"
					+ " archived, starred, first read time, or last read time may be searched for."
					+ " Use expression search to combine multiple view searches.");
			user = _user;
			userName = _userName;
			archived = _archived;
			starred = _starred;
			deleted = _deleted;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public ViewSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			String ret;
			if(archived != null)
				ret = type.headers.get(0) + (archived.booleanValue() ? "" : "!") + "archived";
			else if(starred != null)
				ret = type.headers.get(0) + (starred.booleanValue() ? "" : "!") + "starred";
			else
				throw new IllegalStateException("No parameter selected for view search");
			if(user != null)
				ret += "(" + user.getName() + ")";
			else if(userName != null)
				ret += "(" + userName + ")";
			return ret;
		}
	}

	/** Searches for messages based on their content text. Specified by "content:...". */
	public static class ContentSearch extends StringSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.content;

		/**
		 * @param srch The string to search the content for, or the entire content search query
		 *        (with header prefix)
		 */
		public ContentSearch(String srch)
		{
			super(srch);
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public ContentSearch clone()
		{
			return this; // Immutable
		}
	}

	/**
	 * Searches for messages based on their subject text. Specified by "attach:..."
	 * "attachType:...", or "attachSize:..."
	 */
	public static class AttachmentSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.attachment;

		/**
		 * The name of the attachment to search for. This may be null if the search was not of type
		 * "attach:"
		 */
		public final String name;

		/**
		 * The type of the attachment to search for. This may be null if the search was not of type
		 * "attachType:"
		 */
		public final String attachType;

		/**
		 * The size of the attachment to search for. This may be null if the search was not of type
		 * "attachSize:"
		 */
		public final SizeSearch size;

		/** @param srch The attachment search query (with header prefix) */
		public AttachmentSearch(String srch)
		{
			String selected = null;
			srch = srch.trim();
			for(String header : type.headers)
			{
				boolean hasHeader = true;
				for(int c = 0; c < header.length(); c++)
				{
					char ch = srch.charAt(c);
					if(Character.isUpperCase(ch))
						ch = (char) (ch - 'A' + 'a');
					if(ch != header.charAt(c))
					{
						hasHeader = false;
						break;
					}
				}
				if(hasHeader)
				{
					selected = header;
					srch = srch.substring(header.length());
					srch = srch.trim();
					break;
				}
			}
			if(selected.equals(type.headers.get(0)))
			{
				name = type.clean(srch);
				attachType = null;
				size = null;
			}
			else if(selected.equals(type.headers.get(1)))
			{
				attachType = type.clean(srch);
				name = null;
				size = null;
			}
			else
			{
				size = new SizeSearch(srch);
				name = null;
				attachType = null;
			}
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public AttachmentSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			if(name != null)
				return type.headers.get(0) + name;
			else if(attachType != null)
				return type.headers.get(1) + attachType;
			else
				return size.toString(type.headers.get(2));
		}
	}

	/** Searches for messages based on the amount of storage they take up */
	public static class SizeSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.size;

		/** The operator to apply to the size search (<, >, <=, >=, =, !=) */
		public final Operator operator;

		/** The scalar size to search for */
		public final float size;

		/** The exponent on the unit (0=bytes, 3=KB, 6=MB, 9=GB) */
		public final int exp;

		/** @param srch The entire search query or the sizeoperatorunit string */
		public SizeSearch(String srch)
		{
			srch = type.clean(srch);
			StringBuilder sb = new StringBuilder(srch);
			operator = Operator.parse(sb, srch);
			int numberChars = 0;
			for(;; numberChars++)
			{
				if(numberChars >= sb.length())
					break;
				char ch = sb.charAt(numberChars);
				if((ch >= '0' && ch <= '9') || ch == '.')
					continue;
				else
					break;
			}
			if(numberChars == 0)
				throw new IllegalArgumentException("Illegal size expression: " + srch
					+ "--No number after the operator");
			size = Float.parseFloat(sb.substring(0, numberChars));
			sb.delete(0, numberChars);
			String unitS = sb.toString().toLowerCase();
			if(unitS.length() == 0)
				exp = 3;
			else if("gb".equals(unitS))
				exp = 9;
			else if("mb".equals(unitS))
				exp = 6;
			else if("kb".equals(unitS))
				exp = 3;
			else if("b".equals(unitS))
				exp = 0;
			else
				throw new IllegalArgumentException("Illegal size expression: " + srch + "--" + sb
					+ " is not a valid size unit");
		}

		/**
		 * @param op The operator for the search query
		 * @param sz The scalar size for the search
		 * @param ex The exponent for the size unit (0, 3, 6, or 9)
		 */
		public SizeSearch(Operator op, float sz, int ex)
		{
			operator = op;
			if(sz < 0 || (sz == 0 && ex == 1))
				throw new IllegalArgumentException("Size cannot be <=0");
			size = sz;
			if(ex % 3 == 0 && ex / 3 >= 0 && ex / 3 <= 3)
				exp = ex;
			else
				throw new IllegalArgumentException("Illegal exponent " + ex
					+ ": must be 0, 3, 6, or 9");
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public SizeSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			return toString(type.headers.get(0));
		}

		String toString(String header)
		{
			String ret = header;
			if(operator != Operator.EQ)
				ret += operator;
			ret += size;
			if(exp == 9)
				ret += "GB";
			else if(exp == 6)
				ret += "MB";
			else if(exp == 3)
				ret += "KB";
			else if(exp == 0)
				ret += "B";
			else
				throw new IllegalStateException("Unrecognized unit size: " + exp);
			return ret;
		}
	}

	/** Allows programmers to search for deleted messages as well */
	public static class DeletedSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.deleted;

		/** Whether to search for deleted messages (true), undeleted ones (false), or both (null) */
		public final Boolean deleted;

		/**
		 * @param del Whether to search for deleted messages (true), undeleted ones (false), or both
		 *        (null)
		 */
		public DeletedSearch(Boolean del)
		{
			deleted = del;
		}

		@Override
		public MessageSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return "deleted:" + (deleted == null ? "both" : deleted.toString());
		}

		@Override
		public boolean equals(Object obj)
		{
			return obj instanceof DeletedSearch
				&& prisms.util.ArrayUtils.equals(deleted, ((DeletedSearch) obj).deleted);
		}
	}

	/**
	 * A special extension of an expression search that expresses a ContentSearch and SubjectSearch
	 * OR'ed together with the same text as a single string
	 */
	public static class MsgExpressionSearch extends ExpressionSearch
	{
		/** @param _and Whether the expression is AND'ed or OR'ed */
		public MsgExpressionSearch(boolean _and)
		{
			super(_and);
		}

		@Override
		public MsgExpressionSearch or(Search srch)
		{
			return (MsgExpressionSearch) super.or(srch);
		}

		@Override
		public MsgExpressionSearch and(Search srch)
		{
			return (MsgExpressionSearch) super.and(srch);
		}

		@Override
		public MsgExpressionSearch addOps(Search... ops)
		{
			return (MsgExpressionSearch) super.addOps(ops);
		}

		@Override
		public String toString()
		{
			/* If one operand is a subject search and the other is a content search for the same
			 * search string, this is probably the result of simply searching for a string */
			if(!and && getOperandCount() == 2)
			{
				Search left = getOperand(0);
				Search right = getOperand(1);
				if((left instanceof SubjectSearch && right instanceof ContentSearch)
					|| (left instanceof ContentSearch && right instanceof SubjectSearch))
				{
					String leftSrch = ((StringSearch) left).search;
					String rightSrch = ((StringSearch) right).search;
					if(leftSrch.equals(rightSrch))
					{
						if(leftSrch.indexOf(' ') >= 0)
							return "\"" + leftSrch + "\"";
						else
							return leftSrch;
					}
				}
			}
			return super.toString();
		}
	}

	@Override
	public abstract MessageSearchType getType();

	@Override
	public MsgExpressionSearch or(Search srch)
	{
		return new MsgExpressionSearch(false).addOps(this, srch);
	}

	@Override
	public MsgExpressionSearch and(Search srch)
	{
		return new MsgExpressionSearch(true).addOps(this, srch);
	}

	/** Builds a message search from a string representation */
	public static class MsgSearchBuilder extends SearchBuilder
	{
		private prisms.arch.PrismsEnv theEnv;

		private User theUser;

		/**
		 * @param env The PRISMS environment to build the search in
		 * @param user The user who is building the search
		 */
		public MsgSearchBuilder(prisms.arch.PrismsEnv env, User user)
		{
			theEnv = env;
			theUser = user;
		}

		/** @return The PRISMS environment that the search is being built in */
		public prisms.arch.PrismsEnv getEnv()
		{
			return theEnv;
		}

		/** @return The user who built this search */
		public User getUser()
		{
			return theUser;
		}

		@Override
		public SearchType [] getAllTypes()
		{
			return MessageSearchType.values();
		}

		@Override
		public Search defaultSearch(StringBuilder sb)
		{
			int c = 0;
			for(; c < sb.length(); c++)
			{
				if(Character.isWhitespace(sb.charAt(c)))
					break;
				else if(sb.charAt(c) == '\"')
					c = goPastQuote(sb, c);
			}
			String srch = sb.substring(0, c);
			sb.delete(0, c + 1);
			MsgExpressionSearch ret = new MsgExpressionSearch(false).addOps(
				new SubjectSearch(srch), new ContentSearch(srch));
			return ret;
		}
	}

	/**
	 * Tests this class with a search string. Simply compiles the search and then prints its {
	 * {@link #toString()}. The method is a success for the given arguments if the printed response
	 * is logically identical to the input.
	 * 
	 * @param args Command-line arguments. Used to compile a search structure.
	 */
	public static void main(String [] args)
	{
		MsgSearchBuilder builder = new MsgSearchBuilder(null, null);
		java.util.Scanner scanner = new java.util.Scanner(System.in);
		String line;
		do
		{
			line = scanner.nextLine();
			Search search = builder.createSearch(line);
			System.out.println("Compiled " + search.toString());
		} while(true);
	}
}
