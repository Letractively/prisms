/*
 * LogEntrySearch.java Created Aug 3, 2011 by Andrew Butler, PSL
 */
package prisms.logging;

import prisms.arch.ds.User;
import prisms.util.Search;

/** Searches that can be performed on a {@link PrismsLogger} */
public abstract class LogEntrySearch extends Search
{
	/** The available types of searches */
	public static enum LogEntrySearchType implements SearchType
	{
		/** Search on the ID value of log entries */
		id() {
			public IDRange create(String search, SearchBuilder builder)
			{
				throw new IllegalArgumentException("Cannot create an ID range search like this");
			}
		},
		/** Searches on the instance location from which a log entry was made */
		instance("instance:") {
			public InstanceSearch create(String search, SearchBuilder builder)
			{
				return new InstanceSearch(search);
			}
		},
		/** Searches on log time */
		time("time:"/* TODO, "age:" */) {
			public LogTimeSearch create(String search, SearchBuilder builder)
			{
				return new LogTimeSearch(search);
			}
		},
		/** Searches on the application from which a log entry was made */
		app("app:") {
			public LogAppSearch create(String search, SearchBuilder builder)
			{
				return new LogAppSearch(search, true);
			}
		},
		/** Searches on the client configuration from which a log entry was made */
		client("client:") {
			public LogClientSearch create(String search, SearchBuilder builder)
			{
				return new LogClientSearch(search, true);
			}
		},
		/** Searches on the user of the session that created this log entry */
		user("user:") {
			public LogUserSearch create(String search, SearchBuilder builder)
			{
				LogEntrySearchBuilder logSB = (LogEntrySearchBuilder) builder;
				String userName = clean(search);
				User ret = null;
				if(!search.equalsIgnoreCase("none"))
				{
					prisms.arch.ds.UserSource users = logSB.getEnv().getUserSource();
					try
					{
						ret = users.getUser(userName);
						if(ret == null && users instanceof prisms.arch.ds.ManageableUserSource)
						{
							long localCenter = logSB.getEnv().getIDs().getCenterID();
							User [] allUsers = ((prisms.arch.ds.ManageableUserSource) users)
								.getAllUsers();
							for(User u : allUsers)
								if(prisms.arch.ds.IDGenerator.getCenterID(u.getID()) == localCenter
									&& u.getName().equals(userName))
								{
									ret = u;
									break;
								}
						}
					} catch(prisms.arch.PrismsException e)
					{
						throw new IllegalStateException("Could not get user " + userName, e);
					}
					if(ret == null)
						throw new IllegalArgumentException("No such user: " + userName);
				}
				return new LogUserSearch(ret, true);
			}
		},
		/** Searches on the session from which a log entry was made */
		session("session:") {
			public LogSessionSearch create(String search, SearchBuilder builder)
			{
				return new LogSessionSearch(search, true);
			}
		},
		/** Searches on the severity level of a log entry */
		level("level:") {
			public LogLevelSearch create(String search, SearchBuilder builder)
			{
				return new LogLevelSearch(search);
			}
		},
		/** Searches on the name of the logger that made a log entry */
		loggerName("logger:") {
			public LoggerNameSearch create(String search, SearchBuilder builder)
			{
				return new LoggerNameSearch(search);
			}
		},
		/** Searches for a string in a log entry's content */
		content("content:") {
			public LogContentSearch create(String search, SearchBuilder builder)
			{
				return new LogContentSearch(search);
			}
		},
		/** Searches for a string in a log entry's stack trace */
		stackTrace("stackTrace:") {
			public LogStackTraceSearch create(String search, SearchBuilder builder)
			{
				return new LogStackTraceSearch(search);
			}
		},
		/** Searches for log entries that are duplicates of a different entry */
		duplicate("duplicate:") {
			public LogDuplicateSearch create(String search, SearchBuilder builder)
			{
				search = clean(search);
				Integer dupID;
				if(search.equalsIgnoreCase("none"))
					dupID = null;
				else
					try
					{
						dupID = Integer.valueOf(search, 16);
					} catch(NumberFormatException e)
					{
						throw new IllegalArgumentException(
							"Duplicate IDs must be specified in hexadecimal. " + search
								+ " is invalid.");
					}
				return new LogDuplicateSearch(dupID, true);
			}
		};

		/** The headers that prefix a search of this type */
		public final java.util.List<String> headers;

		LogEntrySearchType(String... _headers)
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
			if(srch == null)
				return srch;
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

	/** Searches for log entries based on text in a field. */
	public static abstract class StringSearch extends LogEntrySearch
	{
		/** The search string that this log entries search is for */
		public final String search;

		/**
		 * @param srch The string to search for, or the entire search query (with header prefix)
		 * @param nullable Whether the user can specify "none" to set this search's text to null
		 */
		protected StringSearch(String srch, boolean nullable)
		{
			srch = getType().clean(srch);
			if(nullable && srch.equalsIgnoreCase("none"))
				search = null;
			else
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

		@Override
		public int hashCode()
		{
			return getClass().hashCode() + (search == null ? 0 : search.hashCode());
		}

		@Override
		public boolean equals(Object obj)
		{
			return getClass() == obj.getClass() && equal(search, ((StringSearch) obj).search);
		}
	}

	/** A search for a range of log entry IDs */
	public static class IDRange extends LogEntrySearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.id;

		private Integer theMinID;

		private Integer theMaxID;

		/**
		 * @param minID The minimum log entry ID to search for. May be null for a prepared search.
		 * @param maxID The maximum log entry ID to search for. May be null for a prepared search.
		 */
		public IDRange(Integer minID, Integer maxID)
		{
			theMinID = minID;
			theMaxID = maxID;
		}

		/** @return The minimum log entry ID to search for. May be null for a prepared search. */
		public Integer getMinID()
		{
			return theMinID;
		}

		/** @return The maximum log entry ID to search for. May be null for a prepared search. */
		public Integer getMaxID()
		{
			return theMaxID;
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return (theMinID == null ? "?" : "" + theMinID) + ">=id<="
				+ (theMaxID == null ? "?" : "" + theMaxID);
		}

		@Override
		public boolean equals(Object obj)
		{
			return obj instanceof IDRange && equal(theMinID, ((IDRange) obj).theMinID)
				&& equal(theMaxID, ((IDRange) obj).theMaxID);
		}
	}

	/** A search for log entries from a particular instance */
	public static class InstanceSearch extends StringSearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.instance;

		/** @param aLocation The instance location to search for. May be null for a prepared search. */
		public InstanceSearch(String aLocation)
		{
			super(aLocation, false);
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public InstanceSearch clone()
		{
			return this; // Immutable
		}
	}

	/** A search for log entries by change time */
	public static class LogTimeSearch extends LogEntrySearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.time;

		/** The operator determining how to use the search date */
		public final Operator operator;

		/** When the entry was logged. May be null for a prepared search. */
		public final SearchDate logTime;

		/** @param srch The entire search query (with or without header) */
		public LogTimeSearch(String srch)
		{
			srch = type.clean(srch);
			StringBuilder sb = new StringBuilder(srch);
			operator = Operator.parse(sb, srch);
			logTime = SearchDate.parse(sb, srch);
		}

		/**
		 * @param op The operator to determine how to use the search time
		 * @param time The change time to search against. May be null for a prepared search.
		 */
		public LogTimeSearch(Operator op, SearchDate time)
		{
			operator = op;
			logTime = time;
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public LogTimeSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			return type.headers.get(0) + operator + logTime;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof LogTimeSearch && operator.equals(((LogTimeSearch) o).operator)
				&& equal(logTime, ((LogTimeSearch) o).logTime);
		}
	}

	/** Searches on the application from which a log entry was made */
	public static class LogAppSearch extends StringSearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.app;

		private final boolean isNull;

		/**
		 * @param appName The name of the application to search on
		 * @param _null Whether, if the given appName is null, this search should be treated as a
		 *        search for log entries with no application or as a parameterized search
		 */
		public LogAppSearch(String appName, boolean _null)
		{
			super(appName, true);
			isNull = _null && search == null;
		}

		/**
		 * @return Whether, if the app name is null, this search should be treated as a search for
		 *         log entries with no application or as a parameterized search
		 */
		public boolean isNull()
		{
			return isNull;
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public LogAppSearch clone()
		{
			return this; // Immutable
		}
	}

	/** Searches on the client configuration from which a log entry was made */
	public static class LogClientSearch extends StringSearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.client;

		private final boolean isNull;

		/**
		 * @param clientName The name of the client configuration to search on
		 * @param _null Whether, if the given clientName is null, this search should be treated as a
		 *        search for log entries with no client or as a parameterized search
		 */
		public LogClientSearch(String clientName, boolean _null)
		{
			super(clientName, true);
			isNull = _null && search == null;
		}

		/**
		 * @return Whether, if the client name is null, this search should be treated as a search
		 *         for log entries with no client or as a parameterized search
		 */
		public boolean isNull()
		{
			return isNull;
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public LogClientSearch clone()
		{
			return this; // Immutable
		}
	}

	/** A search for log entries from sessions of a particular user */
	public static class LogUserSearch extends LogEntrySearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.user;

		private final User theUser;

		private final boolean isNull;

		/**
		 * @param user The user to search for changes by. May be null for a prepared search.
		 * @param _null Whether, if the given user is null, this search should be treated as a
		 *        search for log entries with no user or as a parameterized search
		 */
		public LogUserSearch(User user, boolean _null)
		{
			theUser = user;
			isNull = _null && user == null;
		}

		/** @return The user to search for changes by. May be null for a prepared search. */
		public User getUser()
		{
			return theUser;
		}

		/**
		 * @return Whether, if the user is null, this search should be treated as a search for log
		 *         entries with no user or as a parameterized search
		 */
		public boolean isNull()
		{
			return isNull;
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			if(theUser.getName().contains(" "))
				return "user:\"" + theUser.getName() + "\"";
			else
				return "user:" + theUser.getName();
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof LogUserSearch && equal(theUser, ((LogUserSearch) o).theUser)
				&& isNull == ((LogUserSearch) o).isNull;
		}
	}

	/** A search for log entries from a certain session */
	public static class LogSessionSearch extends StringSearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.session;

		private final boolean isNull;

		/**
		 * @param sessionID The ID of the session to search for. May be null for either a
		 *        parameterized search or a search for log entries not bound to a session.
		 * @param _null Whether, if sessionID is null, this search is a search for log entries not
		 *        bound to a session as opposed to a parameterized search
		 */
		public LogSessionSearch(String sessionID, boolean _null)
		{
			super(sessionID, true);
			isNull = _null && search == null;
		}

		/**
		 * @return Whether, if the session ID is null, this search should be treated as a search for
		 *         log entries with no session or as a parameterized search
		 */
		public boolean isNull()
		{
			return isNull;
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public LogSessionSearch clone()
		{
			return this; // Immutable
		}
	}

	/** A search for log entries within a certain level range */
	public static class LogLevelSearch extends LogEntrySearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.level;

		/** The operator determining how to use the level */
		public final Operator operator;

		/** The level to compare with */
		public final org.apache.log4j.Level level;

		/**
		 * Parses a log level search
		 * 
		 * @param srch Operator + level name
		 */
		public LogLevelSearch(String srch)
		{
			srch = type.clean(srch);
			StringBuilder sb = new StringBuilder(srch);
			operator = Operator.parse(sb, srch);
			level = org.apache.log4j.Level.toLevel(sb.toString().toUpperCase(), null);
			if(level == null)
				throw new IllegalArgumentException("Unrecognized Log4j level: " + srch);
		}

		/**
		 * @param op The operator to determine how to use the level
		 * @param lvl The level to compare with
		 */
		public LogLevelSearch(Operator op, org.apache.log4j.Level lvl)
		{
			operator = op;
			level = lvl;
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return type.headers.get(0) + operator + level;
		}

		@Override
		public boolean equals(Object o)
		{
			if(!(o instanceof LogLevelSearch))
				return false;
			LogLevelSearch lls = (LogLevelSearch) o;
			return equal(operator, lls.operator) && equal(level, lls.level);
		}
	}

	/** A search for log entries from a particular portion of code */
	public static class LoggerNameSearch extends StringSearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.loggerName;

		/** @param loggerName The name of the logger to search for */
		public LoggerNameSearch(String loggerName)
		{
			super(loggerName, false);
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public LoggerNameSearch clone()
		{
			return this; // Immutable
		}
	}

	/** Searches for a string within a log entry's message */
	public static class LogContentSearch extends StringSearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.content;

		/** @param srch The string to search for in the log content */
		public LogContentSearch(String srch)
		{
			super(srch, false);
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public LogContentSearch clone()
		{
			return this; // Immutable
		}
	}

	/** Searches for a string within a log entry's stack trace */
	public static class LogStackTraceSearch extends StringSearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.stackTrace;

		/** @param srch The string to search for in the log stack trace */
		public LogStackTraceSearch(String srch)
		{
			super(srch, false);
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public LogStackTraceSearch clone()
		{
			return this; // Immutable
		}
	}

	/** A search for log entries that are duplicates of a particular entry */
	public static class LogDuplicateSearch extends LogEntrySearch
	{
		/** The type of this search */
		public static final LogEntrySearchType type = LogEntrySearchType.duplicate;

		private final Integer theDuplicateID;

		private final boolean isNull;

		/**
		 * @param duplicateID The ID of the entry to search for duplicates of. May be null for
		 *        either a parameterized search or a search for entries that are not duplicates.
		 * @param _null Whether, if duplicateID is null, this search is a search for entries that
		 *        are not duplicates as opposed to a parameterized search
		 */
		public LogDuplicateSearch(Integer duplicateID, boolean _null)
		{
			theDuplicateID = duplicateID;
			isNull = _null && theDuplicateID == null;
		}

		/** @return The ID of the entry to search duplicates for. May be null. */
		public Integer getDuplicateID()
		{
			return theDuplicateID;
		}

		/**
		 * @return Whether, if the duplicate entry is null, this search should be treated as a
		 *         search for log entries that are not duplicates or as a parameterized search
		 */
		public boolean isNull()
		{
			return isNull;
		}

		@Override
		public LogEntrySearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return "duplicate:"
				+ (theDuplicateID == null ? "none" : Integer.toHexString(theDuplicateID.intValue()));
		}

		@Override
		public LogDuplicateSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof LogDuplicateSearch && isNull == ((LogDuplicateSearch) o).isNull
				&& equal(theDuplicateID, ((LogDuplicateSearch) o).theDuplicateID);
		}
	}

	/**
	 * A special extension of an expression search that expresses a ContentSearch and SubjectSearch
	 * OR'ed together with the same text as a single string
	 */
	public static class LogExpressionSearch extends ExpressionSearch
	{
		/** @param _and Whether the expression is AND'ed or OR'ed */
		public LogExpressionSearch(boolean _and)
		{
			super(_and);
		}

		@Override
		public LogExpressionSearch or(Search srch)
		{
			return (LogExpressionSearch) super.or(srch);
		}

		@Override
		public LogExpressionSearch and(Search srch)
		{
			return (LogExpressionSearch) super.and(srch);
		}

		@Override
		public LogExpressionSearch addOps(Search... ops)
		{
			return (LogExpressionSearch) super.addOps(ops);
		}

		/**
		 * If one operand is a content search and the other is a stack trace search for the same
		 * search string, this is probably the result of simply searching for a string.
		 * 
		 * @return Whether this search is the same as a search for a string
		 */
		public boolean isSingle()
		{
			if(and || getOperandCount() != 2)
				return false;
			Search left = getOperand(0);
			Search right = getOperand(1);
			if((left instanceof LogContentSearch && right instanceof LogStackTraceSearch)
				|| (left instanceof LogStackTraceSearch && right instanceof LogContentSearch))
			{
				String leftSrch = ((StringSearch) left).search;
				String rightSrch = ((StringSearch) right).search;
				return leftSrch.equals(rightSrch);
			}
			return false;
		}

		@Override
		public String toString()
		{
			if(isSingle())
			{
				String srch = ((StringSearch) getOperand(0)).search;
				if(srch.indexOf(' ') >= 0)
					return "\"" + srch + "\"";
				else
					return srch;
			}
			return super.toString();
		}
	}

	@Override
	public abstract LogEntrySearchType getType();

	/** Builds a log entry search from a string */
	public static class LogEntrySearchBuilder extends SearchBuilder
	{
		private prisms.arch.PrismsEnv theEnv;

		/** @param env The PRISMS environment to build the search in */
		public LogEntrySearchBuilder(prisms.arch.PrismsEnv env)
		{
			theEnv = env;
		}

		/** @return The PRISMS environment that the search is being built in */
		public prisms.arch.PrismsEnv getEnv()
		{
			return theEnv;
		}

		@Override
		public Search createSearch(String searchText)
		{
			if(searchText.equals("*"))
				return null;
			return super.createSearch(searchText);
		}

		@Override
		public SearchType [] getAllTypes()
		{
			return LogEntrySearchType.values();
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
			return new LogExpressionSearch(false).addOps(new LogContentSearch(srch),
				new LogStackTraceSearch(srch));
		}
	}

	static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}
}
