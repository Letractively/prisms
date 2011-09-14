/*
 * MessageSearch.java Created Jan 27, 2011 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.arch.ds.User;
import prisms.message.MessageHeader.Priority;
import prisms.message.Receipt.Applicability;
import prisms.util.Search;

/** Represents a search for messages in a message source */
public abstract class MessageSearch extends Search
{
	/** The available types of searches */
	public static enum MessageSearchType implements SearchType
	{
		/** Searches for successors to a message */
		successor() {
			public MessageSearch create(String search, SearchBuilder builder)
			{
				throw new IllegalStateException("Cannot create successor search like this");
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
				return new AuthorSearch(search);
			}
		},
		/** Searches on when (if ever) a message was sent */
		sent("time:") {
			public SentSearch create(String search, SearchBuilder builder)
			{
				return new SentSearch(search);
			}
		},
		/** Searches on recipients */
		recipient("to:", "direct:", "cc:", "bcc:") {
			public RecipientSearch create(String search, SearchBuilder builder)
			{
				return new RecipientSearch(search);
			}
		},
		/** Searches on users' views of messages */
		view("view:", "readTime:", "firstReadTime:") {
			public ViewSearch create(String search, SearchBuilder builder)
			{
				return new ViewSearch(search);
			}
		},
		/** Searches on priority */
		priority("priority:") {
			public PrioritySearch create(String search, SearchBuilder builder)
			{
				return new PrioritySearch(search);
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

	/** Searches for successors of a given message */
	public static class SuccessorSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.successor;

		/** The message to search for successors to */
		public final MessageHeader message;

		/** @param _message The message to search for successors to */
		public SuccessorSearch(MessageHeader _message)
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

		/** The name of the user(s) to search for */
		public final String userName;

		/**
		 * @param srch The name of the author to search for or the entire author search query (with
		 *        header prefix)
		 */
		public AuthorSearch(String srch)
		{
			userName = type.clean(srch);
			user = null;
		}

		/** @param _user The author to search for */
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
			return type.headers.get(0) + userName;
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

		/** When this message was sent */
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
		 * @param time The send or creation time that will be the border of this search
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

	/**
	 * Searches for messages based on their subject text. Specified by "To:...", "Direct:...",
	 * "Cc:...", or "Bcc:..."
	 */
	public static class RecipientSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.recipient;

		/** The user to search for */
		public final User user;

		/** The name of the user(s) to search for */
		public final String userName;

		/** The applicability to the searched recipient */
		public final Applicability applicability;

		/** Whether to include only deleted items or only active items, or null to include both */
		public final Boolean withDeleted;

		/** @param srch The recipient search query (with header prefix, required) */
		public RecipientSearch(String srch)
		{
			withDeleted = null;
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
			withDeleted = withDel;
		}

		/**
		 * @param _user The user to search for
		 * @param app The applicability of the recipient to search for
		 * @param withDel Whether to include only deleted items or only active items (may be null to
		 *        include both)
		 */
		public RecipientSearch(User _user, Applicability app, Boolean withDel)
		{
			user = _user;
			userName = null;
			applicability = app;
			withDeleted = withDel;
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

	/** Searches users' views of a message */
	public static class ViewSearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.view;

		/** The user whose view to search for */
		public final User user;

		/** The name of the user(s) whose views to search for */
		public final String userName;

		/** Whether the message has been read by the viewer */
		public final Boolean read;

		/** Whether the message has been archived by the user */
		public final Boolean archived;

		/** Whether the message has been starred by the user */
		public final Boolean starred;

		/** The operator to use against the read times */
		public final Operator operator;

		/**
		 * The last read time to search against. This is the most recent time that the user opened
		 * the message.
		 */
		public final SearchDate firstReadTime;

		/**
		 * The first read time to search against. This is the first time the user ever opened or
		 * previewed the message.
		 */
		public final SearchDate lastReadTime;

		/** @param srch The view search query (with header prefix, required) */
		public ViewSearch(String srch)
		{
			user = null;
			int header = type.headerIndex(srch);
			if(header < 0)
				throw new IllegalArgumentException("View header required");
			srch = type.clean(srch);
			StringBuilder sb = new StringBuilder(srch);
			switch(header)
			{
			case 0: // view:
				operator = null;
				firstReadTime = null;
				lastReadTime = null;
				boolean not = false;
				if(sb.charAt(0) == '!')
				{
					not = true;
					sb.delete(0, 1);
				}
				if(sb.indexOf("read") == 0)
				{
					sb.delete(0, "read".length());
					read = Boolean.valueOf(!not);
					archived = null;
					starred = null;
				}
				else if(sb.indexOf("archived") == 0)
				{
					sb.delete(0, "archived".length());
					archived = Boolean.valueOf(!not);
					read = null;
					starred = null;
				}
				else if(sb.indexOf("starred") == 0)
				{
					sb.delete(0, "starred".length());
					starred = Boolean.valueOf(!not);
					read = null;
					archived = null;
				}
				else
					throw new IllegalArgumentException("Unrecognized view search: " + srch);
				break;
			case 1: // readTime:
				operator = Operator.parse(sb, srch);
				lastReadTime = SearchDate.parse(sb, srch);
				read = null;
				archived = null;
				starred = null;
				firstReadTime = null;
				break;
			case 2: // firstReadTime:
				operator = Operator.parse(sb, srch);
				firstReadTime = SearchDate.parse(sb, srch);
				read = null;
				archived = null;
				starred = null;
				lastReadTime = null;
				break;
			default:
				throw new IllegalArgumentException("Unrecognized view header: " + header);
			}
			trim(sb);
			if(sb.length() >= 2 && sb.charAt(0) == '(' && sb.charAt(sb.length() - 1) == ')')
			{
				sb.delete(0, 1);
				sb.delete(sb.length() - 1, sb.length());
				trim(sb);
				userName = sb.toString();
			}
			else if(sb.length() > 0)
				throw new IllegalArgumentException("Unrecognized view search suffix: " + sb);
			else
				userName = null;
		}

		/**
		 * Constructs a view search. Only one parameter may be searched for with each ViewSearch.
		 * User ExpressionSearch to form AND's or OR's of multiple view searches if desired.
		 * 
		 * @param _user The user whose view to search. May be null.
		 * @param _read Whether to search for read or unread messages.
		 * @param _archived Whether to search for archived or unarchived messages.
		 * @param _starred Whether to search for starred or unstarred messages.
		 * @param op The operator to use against the read times.
		 * @param frt The first read time to search against.
		 * @param lrt The last read time to search against.
		 */
		public ViewSearch(User _user, Boolean _read, Boolean _archived, Boolean _starred,
			Operator op, SearchDate frt, SearchDate lrt)
		{
			this(_user, null, _read, _archived, _starred, op, frt, lrt);
		}

		/**
		 * Constructs a view search. Only one parameter may be searched for with each ViewSearch.
		 * User ExpressionSearch to form AND's or OR's of multiple view searches if desired.
		 * 
		 * @param _userName The name of the user(s) whose view(s) to search. May be null.
		 * @param _read Whether to search for read or unread messages.
		 * @param _archived Whether to search for archived or unarchived messages.
		 * @param _starred Whether to search for starred or unstarred messages.
		 * @param op The operator to use against the read times.
		 * @param frt The first read time to search against.
		 * @param lrt The last read time to search against.
		 */
		public ViewSearch(String _userName, Boolean _read, Boolean _archived, Boolean _starred,
			Operator op, SearchDate frt, SearchDate lrt)
		{
			this(null, _userName, _read, _archived, _starred, op, frt, lrt);
		}

		private ViewSearch(User _user, String _userName, Boolean _read, Boolean _archived,
			Boolean _starred, Operator op, SearchDate frt, SearchDate lrt)
		{
			int count = 0;
			if(_read != null)
				count++;
			if(_archived != null)
				count++;
			if(_starred != null)
				count++;
			if(frt != null)
				count++;
			if(lrt != null)
				count++;
			if(count == 0)
				throw new IllegalArgumentException("No search input. read, archived, starred,"
					+ " first read time, or last read time must be searched for.");
			if(count > 1)
				throw new IllegalArgumentException("Multiple search inputs. Only one of read,"
					+ " archived, starred, first read time, or last read time may be searched for."
					+ " Use expression search to combine multiple view searches.");
			if((frt != null || lrt != null) && op == null)
				throw new IllegalArgumentException("No operator provided for read time search");
			else if(op != null && frt == null && lrt == null)
				throw new IllegalArgumentException("Operator provided but no read time");
			user = _user;
			userName = _userName;
			read = _read;
			archived = _archived;
			starred = _starred;
			operator = op;
			firstReadTime = frt;
			lastReadTime = lrt;
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
			if(read != null)
				ret = type.headers.get(0) + (read.booleanValue() ? "" : "!") + "read";
			else if(archived != null)
				ret = type.headers.get(0) + (archived.booleanValue() ? "" : "!") + "archived";
			else if(starred != null)
				ret = type.headers.get(0) + (starred.booleanValue() ? "" : "!") + "starred";
			else if(firstReadTime != null)
				ret = type.headers.get(1) + operator + firstReadTime;
			else if(lastReadTime != null)
				ret = type.headers.get(2) + operator + lastReadTime;
			else
				throw new IllegalStateException("No parameter selected for view search");
			if(user != null)
				ret += "(" + user.getName() + ")";
			else if(userName != null)
				ret += "(" + userName + ")";
			return ret;
		}
	}

	/** Searches for messages based on their subject text. Specified by "priority:..." */
	public static class PrioritySearch extends MessageSearch
	{
		/** The type of this search */
		public static final MessageSearchType type = MessageSearchType.priority;

		/** The priority of the messages to search for */
		public final Priority priority;

		/**
		 * @param srch The name of the priority to search for or the entire priority search query
		 *        (with header prefix)
		 */
		public PrioritySearch(String srch)
		{
			srch = type.clean(srch);
			Priority selected = null;
			for(Priority pri : Priority.values())
				if(pri.name().toLowerCase().equals(srch))
				{
					selected = pri;
					break;
				}
			if(selected == null)
				throw new IllegalArgumentException(srch + " is not a valid priority");
			priority = selected;
		}

		/** @param pri The priority to search for */
		public PrioritySearch(Priority pri)
		{
			priority = pri;
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
			return type.headers.get(0) + priority.name().toLowerCase();
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
			if("gb".equalsIgnoreCase(unitS))
				exp = 9;
			else if("mb".equalsIgnoreCase(unitS))
				exp = 6;
			else if("kb".equalsIgnoreCase(unitS))
				exp = 3;
			else if("b".equalsIgnoreCase(unitS))
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
		MsgSearchBuilder builder = new MsgSearchBuilder();
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
