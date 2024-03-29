/*
 * PrismsLogger.java Created Aug 3, 2011 by Andrew Butler, PSL
 */
package prisms.logging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;

import org.apache.log4j.Logger;

import prisms.arch.PrismsException;
import prisms.util.*;

/** Provides a medium for collecting and viewing log information */
public class PrismsLogger implements
	prisms.util.SearchableAPI<LogEntry, PrismsLogger.LogField, PrismsException>
{
	static final Logger log = Logger.getLogger(PrismsLogger.class);

	static final Logger nodbLog = Logger.getLogger(PrismsLogger.class.getName() + ".excluded");

	static final int CONTENT_LENGTH = 1024;

	static final int CONTENT_OVERLAP = 32;

	static final int MAX_SIZE = 1000000;

	static final String MULTI_WILDCARD = "(<**>)";

	static final String SINGLE_WILDCARD = "(<..>)";

	/** Fields on which the history may be sorted */
	public static enum LogField implements prisms.util.Sorter.Field
	{
		/** The PRISMS instance that made the entry */
		INSTANCE("logInstance"),
		/** Sort on the severity of the entry */
		LOG_LEVEL("logLevel"),
		/** Sort on the entry time */
		LOG_TIME("logTime"),
		/** Sort on the user whose session made the entry */
		LOG_USER("logUser");

		private final String theDBValue;

		LogField(String dbValue)
		{
			theDBValue = dbValue;
		}

		@Override
		public String toString()
		{
			return theDBValue;
		}
	}

	private static final class DBLogEntrySearch extends
		DBPreparedSearch<LogEntrySearch, LogField, PrismsException>
	{
		protected DBLogEntrySearch(prisms.arch.ds.Transactor<PrismsException> transactor,
			String sql, Search srch, Sorter<LogField> sorter) throws PrismsException
		{
			super(transactor, sql, srch, sorter, LogEntrySearch.class);
		}

		@Override
		protected synchronized long [] execute(Object... params) throws PrismsException
		{
			return super.execute(params);
		}

		@Override
		protected void dispose()
		{
			super.dispose();
		}

		@Override
		protected void setParameter(int type, Object param, int index) throws PrismsException
		{
			if(param instanceof org.apache.log4j.Level)
				param = Integer.valueOf(((org.apache.log4j.Level) param).toInt());
			if(type == java.sql.Types.TIMESTAMP && param == null
				|| (param instanceof Long && ((Long) param).longValue() <= 0))
				param = Long.valueOf(365L * 24 * 60 * 60 * 1000);
			super.setParameter(type, param, index);
		}

		@Override
		protected void addSqlTypes(LogEntrySearch search, IntList types)
		{
			switch(search.getType())
			{
			case id:
				LogEntrySearch.IDSearch ids = (LogEntrySearch.IDSearch) search;
				if(ids.id == null)
					types.add(java.sql.Types.INTEGER);
				break;
			case instance:
				LogEntrySearch.InstanceSearch is = (LogEntrySearch.InstanceSearch) search;
				if(is.search == null)
					types.add(java.sql.Types.VARCHAR);
				break;
			case time:
				LogEntrySearch.LogTimeSearch lts = (LogEntrySearch.LogTimeSearch) search;
				if(lts.logTime == null)
					types.add(java.sql.Types.TIMESTAMP);
				break;
			case age:
				break;
			case app:
				LogEntrySearch.LogAppSearch las = (LogEntrySearch.LogAppSearch) search;
				if(las.search == null && !las.isNull())
					types.add(java.sql.Types.VARCHAR);
				break;
			case client:
				LogEntrySearch.LogClientSearch lcs = (LogEntrySearch.LogClientSearch) search;
				if(lcs.search == null && !lcs.isNull())
					types.add(java.sql.Types.VARCHAR);
				break;
			case user:
				LogEntrySearch.LogUserSearch lus = (LogEntrySearch.LogUserSearch) search;
				if(lus.getUser() == null && !lus.isNull())
					types.add(java.sql.Types.NUMERIC);
				break;
			case session:
				LogEntrySearch.LogSessionSearch lss = (LogEntrySearch.LogSessionSearch) search;
				if(lss.search == null && !lss.isNull())
					types.add(java.sql.Types.VARCHAR);
				break;
			case level:
				LogEntrySearch.LogLevelSearch lls = (LogEntrySearch.LogLevelSearch) search;
				if(lls.level == null)
					types.add(java.sql.Types.INTEGER);
				break;
			case loggerName:
				LogEntrySearch.LoggerNameSearch lns = (LogEntrySearch.LoggerNameSearch) search;
				if(lns.search == null)
					types.add(java.sql.Types.VARCHAR);
				break;
			case duplicate:
				LogEntrySearch.LogDuplicateSearch lds = (LogEntrySearch.LogDuplicateSearch) search;
				if(lds.getDuplicateID() == null && !lds.isNull())
					types.add(java.sql.Types.INTEGER);
				break;
			case content:
			case stackTrace:
				break;
			case saved:
				LogEntrySearch.LogSavedSearch lSavS = (LogEntrySearch.LogSavedSearch) search;
				if(lSavS.saveTime == null && !lSavS.isNull)
					types.add(java.sql.Types.TIMESTAMP);
				break;
			case size:
				LogEntrySearch.LogSizeSearch lSizS = (LogEntrySearch.LogSizeSearch) search;
				if(lSizS.size == null)
					types.add(java.sql.Types.INTEGER);
				break;
			}
		}

		@Override
		protected void addParamTypes(LogEntrySearch search, java.util.Collection<Class<?>> types)
		{
			PrismsLogger.addParamTypes(search, types);
		}
	}

	// May be used later
	@SuppressWarnings("unused")
	private static final class MemLogEntrySearch extends
		MemPreparedSearch<LogEntry, LogEntrySearch, LogField>
	{
		MemLogEntrySearch(Search search, Sorter<LogField> sorter)
		{
			super(search, sorter, LogEntrySearch.class);
		}

		@Override
		protected prisms.util.MemPreparedSearch.MatchState createState()
		{
			return null;
		}

		@Override
		protected BitSet matches(LogEntry [] items, BitSet filter, LogEntrySearch search,
			prisms.util.MemPreparedSearch.MatchState state, Object [] params)
		{
			for(int i = 0; i < items.length; i++)
			{
				if(!filter.get(i))
					continue;
				if(!matches(search, items[i], params))
					filter.set(i, false);
			}
			return filter;
		}

		@Override
		public int compare(LogEntry o1, LogEntry o2, LogField field)
		{
			// May need to implement this if this class is ever used
			return 0;
		}

		@Override
		protected void addParamTypes(LogEntrySearch search, Collection<Class<?>> types)
		{
			PrismsLogger.addParamTypes(search, types);
		}

		private boolean matches(LogEntrySearch search, LogEntry entry, Object [] params)
		{
			int p = 0;
			switch(search.getType())
			{
			case id:
				LogEntrySearch.IDSearch ids = (LogEntrySearch.IDSearch) search;
				int id;
				if(ids.id == null)
					id = ((Number) params[p++]).intValue();
				else
					id = ids.id.intValue();
				switch(ids.operator)
				{
				case EQ:
					return entry.getID() == id;
				case NEQ:
					return entry.getID() != id;
				case GT:
					return entry.getID() > id;
				case GTE:
					return entry.getID() >= id;
				case LT:
					return entry.getID() < id;
				case LTE:
					return entry.getID() <= id;
				}
				return true;
			case instance:
				LogEntrySearch.StringSearch ss = (LogEntrySearch.StringSearch) search;
				String str = ss.search;
				if(str == null)
					str = (String) params[0];
				return equal(entry.getInstanceLocation(), str);
			case app:
				ss = (LogEntrySearch.StringSearch) search;
				str = ss.search;
				if(str == null)
					str = (String) params[0];
				return equal(entry.getApp(), str);
			case client:
				ss = (LogEntrySearch.StringSearch) search;
				str = ss.search;
				if(str == null)
					str = (String) params[0];
				return equal(entry.getClient(), str);
			case session:
				ss = (LogEntrySearch.StringSearch) search;
				str = ss.search;
				if(str == null)
					str = (String) params[0];
				return equal(entry.getSessionID(), str);
			case loggerName:
				ss = (LogEntrySearch.StringSearch) search;
				str = ss.search;
				if(str == null)
					str = (String) params[0];
				return equal(entry.getLoggerName(), str);
			case user:
				LogEntrySearch.LogUserSearch lus = (LogEntrySearch.LogUserSearch) search;
				prisms.arch.ds.User user = lus.getUser();
				if(user == null && !lus.isNull())
				{
					if(params[0] == null)
						return entry.getUser() == null;
					else if(params[0] instanceof prisms.arch.ds.User)
						return params[0].equals(entry.getUser());
					else
						return entry.getUser() != null
							&& entry.getUser().getID() == ((Number) params[0]).longValue();
				}
				else if(user == null)
					return entry.getUser() == null;
				else
					return user.equals(entry.getUser());
			case time:
				LogEntrySearch.LogTimeSearch lts = (LogEntrySearch.LogTimeSearch) search;
				if(lts.logTime == null)
				{
					switch(lts.operator)
					{
					case EQ:
						return entry.getLogTime() == ((Number) params[0]).longValue();
					case NEQ:
						return entry.getLogTime() != ((Number) params[0]).longValue();
					case GT:
						return entry.getLogTime() > ((Number) params[0]).longValue();
					case GTE:
						return entry.getLogTime() >= ((Number) params[0]).longValue();
					case LT:
						return entry.getLogTime() < ((Number) params[0]).longValue();
					case LTE:
						return entry.getLogTime() <= ((Number) params[0]).longValue();
					}
					return true;
				}
				else
					return lts.logTime.matches(lts.operator, entry.getLogTime());
			case age:
				LogEntrySearch.LogAgeSearch lAgeS = (LogEntrySearch.LogAgeSearch) search;
				return lAgeS.logAge.matches(lAgeS.operator, System.currentTimeMillis(),
					entry.getLogTime());
			case level:
				LogEntrySearch.LogLevelSearch lls = (LogEntrySearch.LogLevelSearch) search;
				int level;
				if(lls.level != null)
					level = lls.level.toInt();
				else if(params[0] instanceof org.apache.log4j.Level)
					level = ((org.apache.log4j.Level) params[0]).toInt();
				else
					level = ((Number) params[0]).intValue();
				switch(lls.operator)
				{
				case EQ:
					return entry.getLevel().toInt() == level;
				case NEQ:
					return entry.getLevel().toInt() != level;
				case GT:
					return entry.getLevel().toInt() > level;
				case GTE:
					return entry.getLevel().toInt() >= level;
				case LT:
					return entry.getLevel().toInt() < level;
				case LTE:
					return entry.getLevel().toInt() <= level;
				}
				if(entry.getLevel().toInt() < level)
					return false;
				return true;
			case duplicate:
				LogEntrySearch.LogDuplicateSearch lds = (LogEntrySearch.LogDuplicateSearch) search;
				if(lds.getDuplicateID() == null)
				{
					if(lds.isNull())
						return entry.getDuplicateRef() < 0;
					else if(params[0] == null)
						return entry.getDuplicateRef() < 0;
					else
						return entry.getDuplicateRef() == ((Number) params[0]).intValue();
				}
				else
					return entry.getDuplicateRef() == lds.getDuplicateID().intValue();
			case content:
				ss = (LogEntrySearch.StringSearch) search;
				str = ss.search;
				return entry.getMessage() != null && entry.getMessage().contains(str);
			case stackTrace:
				ss = (LogEntrySearch.StringSearch) search;
				str = ss.search;
				return entry.getStackTrace() != null && entry.getStackTrace().contains(str);
			case saved:
				LogEntrySearch.LogSavedSearch lSavS = (LogEntrySearch.LogSavedSearch) search;
				if(lSavS.saveTime == null && !lSavS.isNull)
				{
					switch(lSavS.operator)
					{
					case EQ:
						return entry.getSaveTime() == ((Number) params[0]).longValue();
					case NEQ:
						return entry.getSaveTime() != ((Number) params[0]).longValue();
					case GT:
						return entry.getSaveTime() > ((Number) params[0]).longValue();
					case GTE:
						return entry.getSaveTime() >= ((Number) params[0]).longValue();
					case LT:
						return entry.getSaveTime() < ((Number) params[0]).longValue();
					case LTE:
						return entry.getSaveTime() <= ((Number) params[0]).longValue();
					}
					return true;
				}
				else if(lSavS.isNull)
					return entry.getSaveTime() <= 0;
				else
					return lSavS.saveTime.matches(lSavS.operator, entry.getLogTime());
			case size:
				LogEntrySearch.LogSizeSearch lSizS = (LogEntrySearch.LogSizeSearch) search;
				int size = (lSizS.size == null ? (Number) params[0] : lSizS.size).intValue();
				switch(lSizS.operator)
				{
				case EQ:
					return entry.getSize() == size;
				case NEQ:
					return entry.getSize() != size;
				case GT:
					return entry.getSize() > size;
				case GTE:
					return entry.getSize() >= size;
				case LT:
					return entry.getSize() < size;
				case LTE:
					return entry.getSize() <= size;
				}
				return true;
			}
			throw new IllegalStateException("Unrecognized log search type: " + search.getType());
		}

		private boolean equal(Object o1, Object o2)
		{
			return o1 == null ? o2 == null : o1.equals(o2);
		}
	}

	private final prisms.arch.PrismsEnv theEnv;

	prisms.arch.ds.Transactor<PrismsException> theTransactor;

	private java.util.HashMap<String, org.apache.log4j.Level> theLogConstraints;

	private String theExposedDir;

	private long theMinConfiguredAge;

	private long theMaxConfiguredAge;

	private int theMinConfiguredSize;

	private int theMaxConfiguredSize;

	private Search [] thePermanentExcludes;

	private boolean isConfigured;

	private java.util.concurrent.ConcurrentLinkedQueue<LogEntry> theQueueEntries;

	private java.util.LinkedList<LogEntry> thePastEntries;

	private AutoPurger thePurger;

	private Thread theWriterThread;

	org.apache.log4j.Appender theAppender;

	private Logger [] theLoggers;

	private long theLastPurge;

	private long thePurgeSet;

	private long theLastLoggerCheck;

	boolean isClosed;

	/**
	 * Creates a PRISMS logger
	 * 
	 * @param env The environment for this logger
	 */
	public PrismsLogger(prisms.arch.PrismsEnv env)
	{
		theEnv = env;
		theQueueEntries = new java.util.concurrent.ConcurrentLinkedQueue<LogEntry>();
		theLogConstraints = new HashMap<String, org.apache.log4j.Level>();
		theLoggers = new Logger [0];
		thePastEntries = new java.util.LinkedList<LogEntry>();
		thePermanentExcludes = new LogEntrySearch [0];
		thePurger = new AutoPurger();
	}

	/**
	 * @param config The logger configuration
	 * @return The directory to which exposed logging attachments may be written
	 */
	public static String getConfiguredExposedDir(prisms.arch.PrismsConfig config)
	{
		String exposed = config.get("exposed");
		if(exposed != null)
			try
			{
				exposed = exposed.replaceAll(" ", "_");
				java.io.File exposedFile = new java.io.File(exposed);
				if(!exposedFile.exists() && !exposedFile.mkdirs())
				{
					log.error("Exposed directory " + exposed + " could not be created");
					exposed = null;
				}
				else if(!exposedFile.isDirectory() && !exposedFile.delete()
					&& !exposedFile.mkdirs())
				{
					log.error("Exposed directory " + exposed
						+ " is not a directory and could not be created");
					exposed = null;
				}
				else if(!exposedFile.canWrite()
					&& (!PrismsUtils.isJava6() || !exposedFile.setWritable(true)))
				{
					log.error("Exposed directory " + exposed + " is not writable");
					exposed = null;
				}
				else
					exposed = exposedFile.getCanonicalPath();
			} catch(java.io.IOException e)
			{
				log.error("Exposed directory " + exposed + " could not be resolved", e);
				exposed = null;
			}
		if(exposed != null && !exposed.endsWith(java.io.File.separator))
			exposed += java.io.File.separator;
		return exposed;
	}

	/**
	 * Configures this logger
	 * 
	 * @param config The configuration element to use to configure this logger
	 */
	public void configure(prisms.arch.PrismsConfig config)
	{
		if(isConfigured)
			throw new IllegalStateException("This logger has already been configured");
		theExposedDir = getConfiguredExposedDir(config);
		prisms.arch.PrismsConfig purge = config.subConfig("purge");
		prisms.logging.LogEntrySearch.LogEntrySearchBuilder builder;
		builder = new prisms.logging.LogEntrySearch.LogEntrySearchBuilder(theEnv);
		if(purge != null)
		{
			theMinConfiguredSize = purge.getInt("max-size/min", 0);
			theMaxConfiguredSize = purge.getInt("max-size/max", Integer.MAX_VALUE);
			thePurger.setMaxSize(purge.getInt("max-size/default", 10000));

			theMinConfiguredAge = purge.getTime("max-age/min", 0);
			theMaxConfiguredAge = purge.getTime("max-age/max", Long.MAX_VALUE);
			thePurger.setMaxAge(purge.getTime("max-age/default", 6L * 30 * 24 * 60 * 60 * 1000));
			for(prisms.arch.PrismsConfig searchConfig : purge.subConfigs("exclude-searches/search"))
			{
				Search search;
				try
				{
					search = builder.createSearch(searchConfig.getValue());
				} catch(Exception e)
				{
					log.error(
						"Configured purge-excluded search not parseable: "
							+ searchConfig.getValue(), e);
					continue;
				}
				thePurger.excludeSearch(search);
				if(searchConfig.is("permanent", false))
					thePermanentExcludes = ArrayUtils.add(thePermanentExcludes, search);
			}
		}
		boolean newTransactor = theTransactor == null;
		if(newTransactor)
			theTransactor = theEnv.getConnectionFactory().getConnection(config, null,
				new prisms.arch.ds.Transactor.Thrower<PrismsException>()
				{
					public void error(String message) throws PrismsException
					{
						throw new PrismsException(message);
					}

					public void error(String message, Throwable cause) throws PrismsException
					{
						throw new PrismsException(message, cause);
					}
				});
		java.sql.Connection conn;
		try
		{
			conn = theTransactor.getConnection();
		} catch(PrismsException e)
		{
			throw new IllegalStateException("Connection "
				+ theTransactor.getConnectionConfig().get("name") + " is unavailable", e);
		}
		thePurgeSet = System.currentTimeMillis();
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_log_auto_purge";
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			boolean newInst = !rs.next();
			if(!newInst)
			{
				boolean update = false;
				int maxSize = rs.getInt("maxSize");
				if(maxSize >= theMinConfiguredSize && maxSize <= theMaxConfiguredSize)
					thePurger.setMaxSize(maxSize);
				else
				{
					update = true;
					log.warn("Persisted maximum log size " + maxSize
						+ " violates configured constraints. Using default.");
				}
				long maxAge = rs.getLong("maxAge");
				if(maxAge >= theMinConfiguredAge && maxAge <= theMaxConfiguredAge)
					thePurger.setMaxAge(maxAge);
				else
				{
					update = true;
					log.warn("Persisted maximum log entry age "
						+ PrismsUtils.printTimeLength(maxAge)
						+ " violates configured constraints. Using default.");
				}
				rs.close();
				rs = null;
				if(update)
				{
					sql = "UPDATE " + theTransactor.getTablePrefix()
						+ "prisms_log_auto_purge SET setTime="
						+ DBUtils.formatDate(thePurgeSet, DBUtils.isOracle(conn)) + ", maxSize="
						+ thePurger.getMaxSize() + ", maxAge=" + thePurger.getMaxAge();
					stmt.executeUpdate(sql);
				}
			}
			else
			{
				rs.close();
				rs = null;
				sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_log_auto_purge "
					+ "(setTime, maxSize, maxAge) VALUES ("
					+ DBUtils.formatDate(thePurgeSet, DBUtils.isOracle(conn)) + ", "
					+ thePurger.getMaxSize() + ", " + thePurger.getMaxAge() + ")";
				stmt.executeUpdate(sql);
			}

			final java.util.ArrayList<Search> dbExcludes = new java.util.ArrayList<Search>();
			if(newInst)
				for(Search ex : thePurger.getExcludeSearches())
					dbExcludes.add(ex);
			else
			{
				sql = "SELECT search FROM " + theTransactor.getTablePrefix()
					+ "prisms_log_purge_exclude";
				rs = stmt.executeQuery(sql);
				while(rs.next())
				{
					String srchStr = rs.getString(1);
					Search srch;
					try
					{
						srch = builder.createSearch(srchStr);
					} catch(Exception e)
					{
						log.error("Persisted purge exclusion " + srchStr + " is not parseable", e);
						continue;
					}
					dbExcludes.add(srch);
				}
				rs.close();
				rs = null;

				Search [] excludesArray = dbExcludes.toArray(new Search [dbExcludes.size()]);
				dbExcludes.clear();
				final AutoPurger purger = thePurger;
				final Search [] perms = thePermanentExcludes;
				ArrayUtils.adjust(thePurger.getExcludeSearches(), excludesArray,
					new ArrayUtils.DifferenceListener<Search, Search>()
					{
						public boolean identity(Search o1, Search o2)
						{
							return o1.equals(o2);
						}

						public Search added(Search o, int mIdx, int retIdx)
						{
							purger.excludeSearch(o);
							return null;
						}

						public Search removed(Search o, int oIdx, int incMod, int retIdx)
						{
							if(ArrayUtils.contains(perms, o))
								dbExcludes.add(o);
							else
								purger.includeSearch(o);
							return null;
						}

						public Search set(Search o1, int idx1, int incMod, Search o2, int idx2,
							int retIdx)
						{
							return null;
						}
					});
			}

			if(dbExcludes.size() > 0)
			{
				if(!newInst)
					log.warn("Permanently excluded loggers " + dbExcludes
						+ " not included in persisted auto-purger. Adding.");
				for(Search ex : dbExcludes)
				{
					String srchStr = ex.toString();
					if(srchStr.length() > 1024)
						log.error("Excluded log search " + srchStr
							+ " cannot be persisted--1024 chars max");
					sql = "INSERT INTO " + theTransactor.getTablePrefix()
						+ "prisms_log_purge_exclude (search) VALUES (" + DBUtils.toSQL(srchStr)
						+ ")";
					stmt.executeUpdate(sql);
				}
			}

			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_logger_config";
			rs = stmt.executeQuery(sql);
			theLastLoggerCheck = System.currentTimeMillis();
			while(rs.next())
			{
				String loggerName = rs.getString("logger");
				Number level = (Number) rs.getObject("logLevel");
				Logger logger = Logger.getLogger(loggerName);
				logger.setLevel(level == null ? null : org.apache.log4j.Level.toLevel(level
					.intValue()));
			}
			rs.close();
			rs = null;
		} catch(SQLException e)
		{
			throw new IllegalStateException("Could not query auto-purge: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		thePurger.seal();
		if(newTransactor)
			theTransactor.addReconnectListener(new prisms.arch.ds.Transactor.ReconnectListener()
			{
				public void reconnected(boolean initial)
				{
					if(initial)
						return;
					prepareStatements();
				}

				public void released()
				{
					destroyPreparedStatements();
				}
			});
		if(theIDGetter == null)
			prepareStatements();
		if(theAppender == null)
			theAppender = new org.apache.log4j.Appender()
			{
				private org.apache.log4j.spi.ErrorHandler theErrorHandler;

				public void doAppend(org.apache.log4j.spi.LoggingEvent evt)
				{
					log(evt.timeStamp, evt.getLevel(), evt.getLoggerName(), evt.getMessage()
						.toString(), evt.getThrowableInformation() == null ? null : evt
						.getThrowableInformation().getThrowable());
				}

				public String getName()
				{
					return "PRISMS Logger";
				}

				public void setName(String name)
				{
				}

				public boolean requiresLayout()
				{
					return false;
				}

				public org.apache.log4j.Layout getLayout()
				{
					return null;
				}

				public void setLayout(org.apache.log4j.Layout layout)
				{
				}

				public org.apache.log4j.spi.Filter getFilter()
				{
					return null;
				}

				public void addFilter(org.apache.log4j.spi.Filter filter)
				{
				}

				public void clearFilters()
				{
				}

				public org.apache.log4j.spi.ErrorHandler getErrorHandler()
				{
					return theErrorHandler;
				}

				public void setErrorHandler(org.apache.log4j.spi.ErrorHandler handler)
				{
					theErrorHandler = handler;
				}

				public void close()
				{
				}

				@Override
				public String toString()
				{
					return getName();
				}
			};
		doCheckForNewLoggers();
		if(theWriterThread == null)
		{
			Thread writer = new Thread(new Runnable()
			{
				public void run()
				{
					while(!isClosed)
					{
						try
						{
							doPeriodicCheck();
							try
							{
								Thread.sleep(50);
							} catch(InterruptedException e)
							{}
						} catch(Throwable e)
						{
							log.error("Could not persist entries", e);
						}
					}
				}
			});
			writer.setName("PRISMS Logging Persister");
			writer.setDaemon(false);
			writer.setPriority(Thread.MIN_PRIORITY);
			writer.start();
			theWriterThread = writer;
		}
		/* Configures logger constraints, which force the level of a particular logger to allow
		 * logs of a given level */
		for(prisms.arch.PrismsConfig c : config.subConfigs("logger-constraints/logger"))
		{
			String loggerName = c.get("name");
			if(loggerName == null)
			{
				log.warn("No logger name in logger constraint " + c);
				continue;
			}
			String levelName = c.get("level");
			if(levelName == null)
			{
				log.warn("No level set in logger constraint " + c);
				continue;
			}
			org.apache.log4j.Level level = org.apache.log4j.Level.toLevel(levelName.toUpperCase());
			if(level == null)
			{
				log.warn("No such level \"" + levelName + "\" in logger constraint " + c);
				continue;
			}
			theLogConstraints.put(loggerName, level);
			Logger logger = Logger.getLogger(loggerName);
			if(!level.isGreaterOrEqual(logger.getEffectiveLevel()))
			{ // The effective level is greater, so logs at the given level won't show up
				log.info("Level of logger " + loggerName + " level reduced from "
					+ logger.getEffectiveLevel() + " to " + level);
				logger.setLevel(level);
			}
		}
		isConfigured = true;
	}

	/**
	 * PRISMS Logging has a feature that allows files placed in a configurable exposed directory to
	 * be accessible by the user through links in the logs. A link to an exposed file may be created
	 * by printing the absolute path of the file in
	 * 
	 * @return The directory to which files can be written and referred to in logs for the user to
	 *         access
	 */
	public String getExposedDir()
	{
		return theExposedDir;
	}

	/** @return The auto-purger determining which log entries are purged automatically */
	public AutoPurger getAutoPurger()
	{
		return thePurger;
	}

	/**
	 * Sets this logger's auto purger, affecting which entries are kept
	 * 
	 * @param purger The auto-purger to use
	 * @throws IllegalArgumentException If the given purger violates this logger's constraints
	 * @throws PrismsException If the auto-purger cannot be persisted to the database
	 * @see #getMinConfiguredAge()
	 * @see #getMaxConfiguredAge()
	 * @see #getMinConfiguredSize()
	 * @see #getMaxConfiguredSize()
	 * @see #getPermanentExcludedSearches()
	 */
	public void setAutoPurger(AutoPurger purger) throws PrismsException
	{
		purger.seal();
		if(purger.getMaxAge() < theMinConfiguredAge || purger.getMaxAge() > theMaxConfiguredAge)
			throw new IllegalArgumentException("Configured age "
				+ PrismsUtils.printTimeLength(purger.getMaxAge())
				+ " is not within the valid range: "
				+ PrismsUtils.printTimeLength(theMinConfiguredAge) + " to "
				+ PrismsUtils.printTimeLength(theMaxConfiguredAge));
		if(purger.getMaxSize() < theMinConfiguredSize || purger.getMaxSize() > theMaxConfiguredSize)
			throw new IllegalArgumentException("Configured size " + purger.getMaxSize()
				+ " is not within the valid range: " + theMinConfiguredSize + " to "
				+ theMaxConfiguredSize);
		for(Search perm : thePermanentExcludes)
			if(!ArrayUtils.contains(purger.getExcludeSearches(), perm))
				throw new IllegalArgumentException("Logger " + perm + " cannot be purged");

		thePurgeSet = System.currentTimeMillis();

		String sql = null;
		Statement stmt = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "DELETE FROM " + theTransactor.getTablePrefix() + "prisms_log_purge_exclude";
			stmt.executeUpdate(sql);
			for(Search ex : purger.getExcludeSearches())
			{
				String srchStr = ex.toString();
				if(srchStr.length() > 1024)
					log.error("Excluded log search " + srchStr
						+ " cannot be persisted--1024 chars max");
				sql = "INSERT INTO " + theTransactor.getTablePrefix()
					+ "prisms_log_purge_exclude (search) VALUES (" + DBUtils.toSQL(srchStr) + ")";
				stmt.executeUpdate(sql);
			}
			sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_log_auto_purge SET setTime="
				+ DBUtils.formatDate(thePurgeSet, isOracle()) + ", maxSize=" + purger.getMaxSize()
				+ ", maxAge=" + purger.getMaxAge();
			boolean updated = stmt.executeUpdate(sql) > 0;
			if(!updated)
			{
				sql = "INSERT INTO " + theTransactor.getTablePrefix()
					+ "prisms_log_auto_purge (setTime, maxSize, maxAge) VALUES ("
					+ DBUtils.formatDate(thePurgeSet, isOracle()) + ", " + purger.getMaxSize()
					+ ", " + purger.getMaxAge() + ")";
				stmt.executeUpdate(sql);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not set auto purger: SQL=" + sql, e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		thePurger = purger;

		thePurgeQuery = createAutoPurgeQuery(thePurger);
	}

	/** @return The minimum value allowed for {@link AutoPurger#getMaxAge()} in this logger */
	public long getMinConfiguredAge()
	{
		return theMinConfiguredAge;
	}

	/** @return The maximum value allowed for {@link AutoPurger#getMaxAge()} in this logger */
	public long getMaxConfiguredAge()
	{
		return theMaxConfiguredAge;
	}

	/** @return The minimum value allowed for {@link AutoPurger#getMaxSize()} in this logger */
	public int getMinConfiguredSize()
	{
		return theMinConfiguredSize;
	}

	/** @return The maximum value allowed for {@link AutoPurger#getMaxSize()} in this logger */
	public int getMaxConfiguredSize()
	{
		return theMaxConfiguredSize;
	}

	/**
	 * @return All searches that MUST be included in {@link AutoPurger#getExcludeSearches()} in this
	 *         logger
	 */
	public Search [] getPermanentExcludedSearches()
	{
		return thePermanentExcludes.clone();
	}

	/**
	 * Begins the process of writing a log entry to the database. This method is called from the
	 * Log4j appender
	 */
	void log(long logTime, org.apache.log4j.Level level, String loggerName, String message,
		Throwable throwable)
	{
		if(isClosed)
			return;
		if(loggerName.equals(nodbLog.getName()))
			return;
		LogEntry entry = new LogEntry();
		prisms.arch.ds.IDGenerator.PrismsInstance inst = theEnv.getIDs().getLocalInstance();
		if(inst != null)
			entry.setInstanceLocation(theEnv.getIDs().getLocalInstance().location);
		else
			entry.setInstanceLocation("Unknown");
		entry.setLogTime(logTime);
		entry.setLevel(level);
		entry.setLoggerName(loggerName);

		prisms.arch.PrismsTransaction trans = theEnv.getTransaction();
		if(trans != null)
		{
			entry.setTrackingData(path(trans.getTracker().getCurrentTask()));
			if(trans.getApp() != null)
				entry.setApp(trans.getApp().getName());
			if(trans.getSession() != null)
			{
				entry.setClient(trans.getSession().getClient().getName());
				entry.setUser(trans.getSession().getUser());
				entry.setSessionID(trans.getSession().getMetadata().getID());
			}
		}
		if(throwable == null)
			entry.setMessage(message);
		else
		{
			entry.setMessage(message + "\n" + throwable);
			java.io.StringWriter str = new java.io.StringWriter();
			java.io.PrintWriter writer = new java.io.PrintWriter(str);
			throwable.printStackTrace(writer);
			String stackTrace = str.toString();
			if(stackTrace.startsWith(throwable.toString()))
				stackTrace = stackTrace.substring(throwable.toString().length());
			entry.setStackTrace(stackTrace);
		}
		theQueueEntries.add(entry);
	}

	void doPeriodicCheck()
	{
		if(isClosed)
			return;
		prisms.arch.PrismsTransaction trans = theEnv.transact(null);
		try
		{
			prisms.util.ProgramTracker.TrackNode track = trans.getTracker().start(
				"Check New Loggers");
			try
			{
				doCheckForNewLoggers();
			} finally
			{
				trans.getTracker().end(track);
			}
			track = trans.getTracker().start("Check Logger Configs");
			try
			{
				checkLoggerConfigs();
			} finally
			{
				trans.getTracker().end(track);
			}
			track = trans.getTracker().start("Auto-Purge");
			try
			{
				doAutoPurge();
			} finally
			{
				trans.getTracker().end(track);
			}
			track = trans.getTracker().start("Write Entries");
			try
			{
				doWriteEntries();
			} finally
			{
				trans.getTracker().end(track);
			}
		} finally
		{
			theEnv.finish(trans);
		}
	}

	private long lastLoggerCheck;

	private void doCheckForNewLoggers()
	{
		if(isClosed)
			return;
		long now = System.currentTimeMillis();
		if(now - lastLoggerCheck < 5000)
			return;
		lastLoggerCheck = now;
		java.util.ArrayList<Logger> loggers = new java.util.ArrayList<Logger>();
		java.util.Enumeration<Logger> en = org.apache.log4j.LogManager.getCurrentLoggers();
		while(en.hasMoreElements())
			loggers.add(en.nextElement());
		theLoggers = ArrayUtils.adjust(theLoggers, loggers.toArray(new Logger [loggers.size()]),
			new ArrayUtils.DifferenceListener<Logger, Logger>()
			{
				public boolean identity(Logger o1, Logger o2)
				{
					return o1 == o2;
				}

				public Logger added(Logger o, int mIdx, int retIdx)
				{
					o.addAppender(theAppender);
					return o;
				}

				public Logger removed(Logger o, int oIdx, int incMod, int retIdx)
				{
					o.removeAppender(theAppender);
					return null;
				}

				public Logger set(Logger o1, int idx1, int incMod, Logger o2, int idx2, int retIdx)
				{
					return o1;
				}
			});
	}

	private void doWriteEntries()
	{
		if(!thePastEntries.isEmpty())
		{
			long now = System.currentTimeMillis();
			java.util.Iterator<LogEntry> iter = thePastEntries.iterator();
			while(iter.hasNext())
			{
				LogEntry pastEntry = iter.next();
				if(now - pastEntry.getLogTime() < 200)
					break;
				iter.remove();
			}
		}
		prisms.arch.PrismsTransaction trans = theEnv.getTransaction();
		if(isClosed || theQueueEntries.isEmpty() || theInserter == null
			|| theDuplicateQuery == null)
			return;
		while(!theQueueEntries.isEmpty())
		{
			if(isClosed || theIDGetter == null)
				return;
			boolean written = false;
			LogEntry entry = theQueueEntries.poll();
			if(!thePastEntries.isEmpty())
			{
				boolean found = false;
				for(LogEntry pastEntry : thePastEntries)
					if(pastEntry.headersSame(entry))
					{
						found = true;
						break;
					}
				if(found)
					continue;
			}
			ResultSet rs = null;
			try
			{
				long msgCRC = crc(entry.getMessage());
				long stCRC = crc(entry.getStackTrace());
				long trackCRC = crc(entry.getTrackingData());

				// Check whether this entry is a duplicate
				theDuplicateQuery.setLong(1, msgCRC);
				theDuplicateQuery.setLong(2, stCRC);
				theDuplicateQuery.setLong(3, trackCRC);
				int duplicate = -1;
				prisms.util.ProgramTracker.TrackNode track = trans.getTracker().start(
					"Check Duplicate");
				try
				{
					rs = theDuplicateQuery.executeQuery();
					while(duplicate < 0 && rs.next())
					{
						boolean isDuplicate = true;
						String message = rs.getString("shortMessage");
						String stackTrace = null;
						String tracking = null;
						if(stCRC == -1 && entry.getMessage().equals(message))
						{}
						else
						{
							theContentQuery.setInt(1, rs.getInt("id"));
							StringBuilder msgSB = new StringBuilder();
							StringBuilder stSB = new StringBuilder();
							StringBuilder trackSB = new StringBuilder();
							boolean hasST = false;
							boolean hasTrack = false;
							ResultSet rs2 = theContentQuery.executeQuery();
							try
							{
								while(rs2.next())
								{
									char type = rs2.getString("contentType").charAt(0);
									StringBuilder sb;
									if(type == 's' || type == 'S')
									{
										sb = stSB;
										hasST = true;
									}
									else if(type == 't' || type == 'T')
									{
										sb = trackSB;
										hasTrack = true;
									}
									else
										sb = msgSB;
									sb.append(rs2.getString("content").substring(
										sb.length() - rs2.getInt("indexNum")));
								}
							} finally
							{
								rs2.close();
								rs2 = null;
							}
							if(message == null)
								message = msgSB.toString();
							stackTrace = hasST ? stSB.toString() : null;
							tracking = hasTrack ? trackSB.toString() : null;
							isDuplicate = entry.getMessage().equals(message);
							isDuplicate &= (entry.getStackTrace() == null ? stackTrace == null
								: entry.getStackTrace().equals(stackTrace));
							isDuplicate &= (entry.getTrackingData() == null ? tracking == null
								: entry.getTrackingData().equals(tracking));
						}
						if(isDuplicate)
							duplicate = rs.getInt("id");
					}
				} finally
				{
					trans.getTracker().end(track);
					if(rs != null)
						rs.close();
					rs = null;
				}

				// Insert the new entry
				int p = 1;
				track = trans.getTracker().start("Insert Entry");
				int id;
				try
				{
					p++; // Set the ID last
					theInserter.setString(p++, entry.getInstanceLocation());
					theInserter.setTimestamp(p++, new java.sql.Timestamp(entry.getLogTime()));
					if(entry.getApp() != null)
						theInserter.setString(p++, entry.getApp());
					else
						theInserter.setNull(p++, java.sql.Types.VARCHAR);
					if(entry.getClient() != null)
						theInserter.setString(p++, entry.getClient());
					else
						theInserter.setNull(p++, java.sql.Types.VARCHAR);
					if(entry.getUser() != null)
						theInserter.setLong(p++, entry.getUser().getID());
					else
						theInserter.setNull(p++, java.sql.Types.NUMERIC);
					if(entry.getSessionID() != null)
						theInserter.setString(p++, entry.getSessionID());
					else
						theInserter.setNull(p++, java.sql.Types.VARCHAR);
					theInserter.setInt(p++, entry.getLevel().toInt());
					if(entry.getLoggerName().length() > 256)
						entry.setLoggerName(entry.getLoggerName().substring(0, 256));
					theInserter.setString(p++, entry.getLoggerName());
					if(entry.getMessage().length() <= 100)
						theInserter.setString(p++, entry.getMessage());
					else
						theInserter.setNull(p++, java.sql.Types.VARCHAR);
					theInserter.setLong(p++, msgCRC);
					theInserter.setLong(p++, stCRC);
					theInserter.setLong(p++, trackCRC);
					if(duplicate >= 0)
						theInserter.setInt(p++, duplicate);
					else
						theInserter.setNull(p++, java.sql.Types.INTEGER);
					int size = 1;
					if(entry.getDuplicateRef() < 0)
					{
						if(entry.getMessage().length() > 100)
						{
							size++;
							int len = entry.getMessage().length() - CONTENT_LENGTH;
							if(len > 0)
								size += (len - 1) / (CONTENT_LENGTH - CONTENT_OVERLAP) + 1;
						}
						if(entry.getStackTrace() != null)
						{
							size++;
							int len = entry.getStackTrace().length() - CONTENT_LENGTH;
							if(len > 0)
								size += (len - 1) / (CONTENT_LENGTH - CONTENT_OVERLAP) + 1;
						}
						if(entry.getTrackingData() != null)
						{
							size++;
							int len = entry.getTrackingData().length() - CONTENT_LENGTH;
							if(len > 0)
								size += (len - 1) / (CONTENT_LENGTH - CONTENT_OVERLAP) + 1;
						}
					}
					theInserter.setInt(p++, size);
					try
					{
						id = theEnv.getIDs().getNextIntID(theIDGetter, "prisms_log_entry",
							theTransactor.getTablePrefix(), "id", null);
					} catch(PrismsException e)
					{
						log.error("Could not get log ID. Exiting.");
						isClosed = true;
						return;
					}
					theInserter.setInt(1, id);
					theInserter.executeUpdate();
					written = true;
				} finally
				{
					trans.getTracker().end(track);
				}

				track = trans.getTracker().start("Insert Content");
				try
				{
					if(duplicate < 0)
					{
						if(entry.getMessage().length() > 100)
						{
							theContentInserter.setInt(1, id);
							theContentInserter.setString(4, "M");
							String msg = entry.getMessage();
							if(msg.length() <= CONTENT_LENGTH)
							{
								theContentInserter.setInt(2, 0);
								theContentInserter.setString(3, msg);
								theContentInserter.executeUpdate();
							}
							else
							{
								int inc = CONTENT_LENGTH - CONTENT_OVERLAP;
								for(int i = 0; i < msg.length(); i += inc)
								{
									int end = i + CONTENT_LENGTH;
									int diff = end - msg.length();
									if(diff > 0)
									{
										end = msg.length();
										i -= diff;
									}
									theContentInserter.setInt(2, i);
									theContentInserter.setString(3, msg.substring(i, end));
									theContentInserter.executeUpdate();
									if(diff >= 0)
										break;
								}
							}
						}
						if(entry.getStackTrace() != null && entry.getStackTrace().length() > 0)
						{
							theContentInserter.setInt(1, id);
							theContentInserter.setString(4, "S");
							String st = entry.getStackTrace();
							if(st.length() <= CONTENT_LENGTH)
							{
								theContentInserter.setInt(2, 0);
								theContentInserter.setString(3, st);
								theContentInserter.executeUpdate();
							}
							else
							{
								int inc = CONTENT_LENGTH - CONTENT_OVERLAP;
								for(int i = 0; i < st.length(); i += inc)
								{
									int end = i + CONTENT_LENGTH;
									int diff = end - st.length();
									if(diff > 0)
									{
										end = st.length();
										i -= diff;
									}
									theContentInserter.setInt(2, i);
									theContentInserter.setString(3, st.substring(i, end));
									theContentInserter.executeUpdate();
									if(diff >= 0)
										break;
								}
							}
						}
						if(entry.getTrackingData() != null && entry.getTrackingData().length() > 0)
						{
							theContentInserter.setInt(1, id);
							theContentInserter.setString(4, "T");
							String st = entry.getTrackingData();
							if(st.length() <= CONTENT_LENGTH)
							{
								theContentInserter.setInt(2, 0);
								theContentInserter.setString(3, st);
								theContentInserter.executeUpdate();
							}
							else
							{
								int inc = CONTENT_LENGTH - CONTENT_OVERLAP;
								for(int i = 0; i < st.length(); i += inc)
								{
									int end = i + CONTENT_LENGTH;
									int diff = end - st.length();
									if(diff > 0)
									{
										end = st.length();
										i -= diff;
									}
									theContentInserter.setInt(2, i);
									theContentInserter.setString(3, st.substring(i, end));
									theContentInserter.executeUpdate();
									if(diff >= 0)
										break;
								}
							}
						}
					}
				} finally
				{
					trans.getTracker().end(track);
				}
			} catch(SQLException e)
			{
				nodbLog.error("Could not insert new log entry", e);
			} finally
			{
				if(written)
					thePastEntries.add(entry);
			}
		}
	}

	private long crc(String content)
	{
		if(content == null)
			return -1;
		if(content.length() == 0)
			return 0;
		final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		java.io.OutputStream crcOut = new java.io.OutputStream()
		{
			@Override
			public void write(int b) throws java.io.IOException
			{
				crc.update(b);
			}
		};
		try
		{
			java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(crcOut);
			writer.write(content);
			writer.close();
		} catch(java.io.IOException e)
		{
			throw new IllegalStateException("Could not check CRC of message");
		}
		return crc.getValue();
	}

	private String path(prisms.util.ProgramTracker.TrackNode track)
	{
		StringBuilder ret = new StringBuilder();
		while(track != null)
		{
			if(ret.length() > 0)
				ret.insert(0, '/');
			ret.insert(0, track.getName());
			track = track.getParent();
		}
		return ret.toString();
	}

	/**
	 * Adds a logger configuration to be persisted
	 * 
	 * @param loggerName The name of the logger to change the level of
	 * @param level The level for the logger--may be null to use the log level of its parent
	 * @throws PrismsException If an error occurs persisting the entry
	 * @throws IllegalArgumentException If the given level is to high for this logger's configured
	 *         constraints for the given logger
	 */
	public void addLoggerConfig(String loggerName, org.apache.log4j.Level level)
		throws PrismsException
	{
		org.apache.log4j.Level constraintLevel = theLogConstraints.get(loggerName);
		if(constraintLevel != null)
		{
			if(level != null)
			{
				if(level.isGreaterOrEqual(constraintLevel))
					throw new IllegalArgumentException("Logger " + loggerName
						+ " must allow logs of level " + constraintLevel);
			}
			else
			{
				Logger logger = Logger.getLogger(loggerName);
				org.apache.log4j.Level preLevel = logger.getLevel();
				try
				{
					logger.setLevel(null);
					if(logger.getEffectiveLevel().isGreaterOrEqual(constraintLevel))
					{
						throw new IllegalArgumentException("Logger " + loggerName
							+ " must allow logs of level " + constraintLevel);
					}
				} finally
				{
					logger.setLevel(preLevel);
				}
			}
		}
		else
		{
			for(java.util.Map.Entry<String, org.apache.log4j.Level> entry : theLogConstraints
				.entrySet())
			{
				if(entry.getKey().startsWith(loggerName)
					&& !entry.getValue().isGreaterOrEqual(level))
				{
					Logger logger = Logger.getLogger(entry.getKey());
					if(logger.getLevel() == null)
						/* This action would cause the constrained logger to be set at an effective
						 * level higher than its threshold. Allow the action, but modify the logger
						 * specifically so its configured logs go through. */
						logger.setLevel(entry.getValue());
				}
			}
		}
		String sql = "UPDATE " + theTransactor.getTablePrefix()
			+ "prisms_logger_config SET logLevel=" + (level == null ? "NULL" : "" + level.toInt())
			+ ", setTime=" + DBUtils.formatDate(System.currentTimeMillis(), isOracle())
			+ " WHERE logger=" + DBUtils.toSQL(loggerName);
		Statement stmt = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			if(stmt.executeUpdate(sql) == 0)
			{
				sql = "INSERT INTO " + theTransactor.getTablePrefix()
					+ "prisms_logger_config (logger, logLevel, setTime) VALUES ("
					+ DBUtils.toSQL(loggerName) + ", "
					+ (level == null ? "NULL" : "" + level.toInt()) + ", "
					+ DBUtils.formatDate(System.currentTimeMillis(), isOracle()) + ")";
				stmt.executeUpdate(sql);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not persist logger configuration", e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * Clears the database of logger configurations so that logging returns to the defaults
	 * configured in XML or otherwise next time the server starts
	 * 
	 * @return The number of logger configurations cleared as a result of the operation
	 * @throws PrismsException If an error occurs removing the configs
	 */
	public int clearLoggerConfigs() throws PrismsException
	{
		String sql = "DELETE FROM " + theTransactor.getTablePrefix()
			+ "prisms_logger_config WHERE setTime<="
			+ DBUtils.formatDate(theLastLoggerCheck, isOracle());
		Statement stmt = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			return stmt.executeUpdate(sql);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not remove persisted logger configurations", e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	public long [] search(Search search, Sorter<LogField> sorter) throws PrismsException
	{
		String sql = createQuery(search, sorter, false) + " ORDER BY " + getOrder(sorter);
		Statement stmt = null;
		ResultSet rs = null;
		LongList ret = new LongList();
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
				ret.add(rs.getLong(1));
		} catch(SQLException e)
		{
			throw new PrismsException("Could not query log entries: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error" + e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error: " + e);
				}
		}
		return ret.toArray();
	}

	/**
	 * @param search The search to search this data source with
	 * @return The number of items that would be returned from a call to
	 *         {@link #search(Search, Sorter)} with the given search
	 * @throws PrismsException If an error occurs during the search
	 */
	public int getItemCount(Search search) throws PrismsException
	{
		String sql = createQuery(search, null, false);
		sql = "SELECT COUNT(*) FROM " + sql.substring(sql.indexOf(theTransactor.getTablePrefix()));
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return 0;
			return rs.getInt(1);
		} catch(SQLException e)
		{
			throw new PrismsException("Could not query log entries: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error" + e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error: " + e);
				}
		}
	}

	/**
	 * @return All distinct instance/app/client/user/session combinations in the logging database
	 * @throws PrismsException If an error occurs retrieving the data
	 */
	public LogEntry [] getDistinctTypes() throws PrismsException
	{
		String sql = "SELECT DISTINCT logInstance, logApp, logClient, logUser, logSession FROM "
			+ theTransactor.getTablePrefix() + "prisms_log_entry";
		Statement stmt = null;
		ResultSet rs = null;
		java.util.ArrayList<LogEntry> ret = new java.util.ArrayList<LogEntry>();
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				LogEntry entry = new LogEntry();
				entry.setInstanceLocation(rs.getString("logInstance"));
				entry.setApp(rs.getString("logApp"));
				entry.setClient(rs.getString("logClient"));
				try
				{
					entry.setUser(theEnv.getUserSource().getUser(rs.getLong("logUser")));
				} catch(PrismsException e)
				{
					log.info("Could not get user with ID " + rs.getLong("logUser")
						+ ": may have been deleted");
				}
				entry.setSessionID(rs.getString("logSession"));
				ret.add(entry);
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not query log entries: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error" + e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error: " + e);
				}
		}
		return ret.toArray(new LogEntry [ret.size()]);
	}

	/**
	 * @return A 2-item array with the minimum and maximum log times in the database. Will be null
	 *         for an empty database
	 * @throws PrismsException If the information cannot be retrieved
	 */
	public long [] getTimeRange() throws PrismsException
	{
		String sql = null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "SELECT MIN(logTime), MAX(logTime) FROM " + theTransactor.getTablePrefix()
				+ "prisms_log_entry";
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			java.sql.Timestamp time = rs.getTimestamp(1);
			if(time == null)
				return null;
			return new long [] {time.getTime(), rs.getTimestamp(2).getTime()};
		} catch(SQLException e)
		{
			throw new PrismsException("Could not query min/max log time: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error" + e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error: " + e);
				}
		}
	}

	/**
	 * @return A 2-item array with the number of entries and the total size of all entries in the
	 *         database
	 * @throws PrismsException If the information cannot be retrieved
	 */
	public int [] getTotalSize() throws PrismsException
	{
		String sql = null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "SELECT COUNT(*), SUM(entrySize) FROM " + theTransactor.getTablePrefix()
				+ "prisms_log_entry WHERE entrySize<" + MAX_SIZE;
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return new int [] {0, 0};
			return new int [] {rs.getInt(1), rs.getInt(2)};
		} catch(SQLException e)
		{
			throw new PrismsException("Could not query size: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error" + e);
				}
		}
	}

	public prisms.util.SearchableAPI.PreparedSearch<LogField> prepare(Search search,
		Sorter<LogField> sorter) throws PrismsException
	{
		String sql = createQuery(search, sorter, true) + " ORDER BY " + getOrder(sorter);
		return new DBLogEntrySearch(theTransactor, sql, search, sorter);
	}

	public long [] execute(prisms.util.SearchableAPI.PreparedSearch<LogField> search,
		Object... params) throws PrismsException
	{
		return ((DBLogEntrySearch) search).execute(params);
	}

	public void destroy(prisms.util.SearchableAPI.PreparedSearch<LogField> search)
		throws PrismsException
	{
		((DBLogEntrySearch) search).dispose();
	}

	public LogEntry [] getItems(long... ids) throws PrismsException
	{
		prisms.util.DBUtils.KeyExpression key = DBUtils.simplifyKeySet(ids, 50);
		if(key == null)
			return new LogEntry [0];
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_log_entry WHERE ";
		Statement stmt = null;
		ResultSet rs = null;
		LogEntry [] entries;
		prisms.arch.PrismsTransaction trans = theEnv != null ? theEnv.getTransaction() : null;
		ProgramTracker.TrackNode track = PrismsUtils.track(trans, "Get Log Entries");
		try
		{
			java.util.ArrayList<LogEntry> ret = new java.util.ArrayList<LogEntry>();
			stmt = theTransactor.getConnection().createStatement();
			ProgramTracker.TrackNode track2 = PrismsUtils.track(trans, "Get Entry Headers");
			try
			{
				rs = DBUtils.executeQuery(stmt, sql, key, "", "id", 90);
				while(rs.next())
				{
					LogEntry entry = new LogEntry();
					entry.setID(rs.getInt("id"));
					entry.setInstanceLocation(rs.getString("logInstance"));
					entry.setLogTime(rs.getTimestamp("logTime").getTime());
					entry.setApp(rs.getString("logApp"));
					entry.setClient(rs.getString("logClient"));
					Number userID = (Number) rs.getObject("logUser");
					if(userID != null)
						try
						{
							entry.setUser(theEnv.getUserSource().getUser(userID.longValue()));
						} catch(PrismsException e)
						{
							throw new PrismsException("Could not get user for log entry", e);
						}
					entry.setSessionID(rs.getString("logSession"));
					entry.setLevel(org.apache.log4j.Level.toLevel(rs.getInt("logLevel")));
					entry.setLoggerName(rs.getString("loggerName"));
					entry.setMessage(rs.getString("shortMessage"));
					Number dup = (Number) rs.getObject("logDuplicate");
					if(dup == null)
						entry.setDuplicateRef(-1);
					else
						entry.setDuplicateRef(dup.intValue());
					java.sql.Timestamp time = rs.getTimestamp("entrySaved");
					if(time != null)
						entry.setSaveTime(time.getTime());
					entry.setSize(rs.getInt("entrySize"));
					ret.add(entry);
				}
				rs.close();
				rs = null;
			} finally
			{
				PrismsUtils.end(trans, track2);
			}

			// Sort the entries in the order of the IDs given
			Long [] idObjs = new Long [ids.length];
			for(int i = 0; i < ids.length; i++)
				idObjs[i] = Long.valueOf(ids[i]);
			final ArrayUtils.ArrayAdjuster<LogEntry, Long, RuntimeException> [] adjuster;
			adjuster = new ArrayUtils.ArrayAdjuster [1];
			adjuster[0] = new ArrayUtils.ArrayAdjuster<LogEntry, Long, RuntimeException>(
				ret.toArray(new LogEntry [ret.size()]), idObjs,
				new ArrayUtils.DifferenceListener<LogEntry, Long>()
				{
					public boolean identity(LogEntry o1, Long o2)
					{
						return o1 != null && o1.getID() == o2.longValue();
					}

					public LogEntry added(Long o, int idx, int retIdx)
					{
						adjuster[0].nullElement();
						return null;
					}

					public LogEntry removed(LogEntry o, int idx, int incMod, int retIdx)
					{
						return o;
					}

					public LogEntry set(LogEntry o1, int idx1, int incMod, Long o2, int idx2,
						int retIdx)
					{
						return o1;
					}
				});
			entries = adjuster[0].adjust();

			// Adjust the key set to get the content of duplicates
			long [] newIDs = ids.clone();
			for(int i = 0; i < ids.length; i++)
				if(entries[i] != null && entries[i].getDuplicateRef() >= 0)
					newIDs[i] = entries[i].getDuplicateRef();
			key = DBUtils.simplifyKeySet(newIDs, 50);

			// Now get the content (message and stack trace)
			HashMap<Integer, StringBuilder> messages = new HashMap<Integer, StringBuilder>();
			HashMap<Integer, StringBuilder> stacks = new HashMap<Integer, StringBuilder>();
			HashMap<Integer, StringBuilder> tracking = new HashMap<Integer, StringBuilder>();
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_log_content WHERE ";
			track2 = PrismsUtils.track(trans, "Get Log Content");
			try
			{
				rs = DBUtils.executeQuery(stmt, sql, key, "ORDER BY indexNum", "logEntry", 90);
				while(rs.next())
				{
					Integer logEntry = Integer.valueOf(rs.getInt("logEntry"));
					HashMap<Integer, StringBuilder> map;
					char type = rs.getString("contentType").charAt(0);
					if(type == 'M' || type == 'm')
						map = messages;
					else if(type == 'S' || type == 's')
						map = stacks;
					else
						map = tracking;
					StringBuilder sb = map.get(logEntry);
					if(sb == null)
					{
						sb = new StringBuilder(rs.getString("content"));
						map.put(logEntry, sb);
					}
					else
						sb.append(rs.getString("content").substring(
							sb.length() - rs.getInt("indexNum")));
				}
				rs.close();
				rs = null;
			} finally
			{
				PrismsUtils.end(trans, track2);
			}

			for(LogEntry entry : entries)
			{
				if(entry == null)
					continue;
				Integer msgKey = entry.getDuplicateRef() >= 0 ? Integer.valueOf(entry
					.getDuplicateRef()) : Integer.valueOf(entry.getID());
				StringBuilder sb = messages.get(msgKey);
				if(sb != null)
					entry.setMessage(sb.toString());
				sb = stacks.get(msgKey);
				if(sb != null)
					entry.setStackTrace(sb.toString());
				sb = tracking.get(msgKey);
				if(sb != null)
					entry.setTrackingData(sb.toString());
			}
		} catch(SQLException e)
		{
			throw new PrismsException("Could not query log entries: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error" + e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error: " + e);
				}
			PrismsUtils.end(trans, track);
		}
		return entries;
	}

	/**
	 * Purges a set of entries
	 * 
	 * @param ids The IDs of the entries to purge
	 * @return The number of entries actually purged
	 * @throws PrismsException If an error occurs purging the data
	 */
	public int purge(final int [] ids) throws PrismsException
	{
		Number ret = (Number) theTransactor.performTransaction(
			new prisms.arch.ds.Transactor.TransactionOperation<PrismsException>()
			{
				public Object run(Statement stmt) throws PrismsException
				{
					try
					{
						return Integer.valueOf(doPurge(new IntList(ids), stmt));
					} catch(SQLException e)
					{
						throw new PrismsException("Could not purge items", e);
					}
				}
			}, "Could not purge items");
		return ret.intValue();
	}

	/**
	 * Prevents a set of entries from being purged for a time
	 * 
	 * @param ids The IDs of the entries to save
	 * @param time The time until which the entries will not be purged
	 * @throws PrismsException If an error occurs setting the data
	 */
	public void save(int [] ids, final long time) throws PrismsException
	{
		final IntList idInts = new IntList(ids);
		idInts.setSorted(true);
		idInts.setUnique(true);
		theTransactor.performTransaction(
			new prisms.arch.ds.Transactor.TransactionOperation<PrismsException>()
			{
				public Object run(Statement stmt) throws PrismsException
				{
					String sql = "UPDATE " + theTransactor.getTablePrefix()
						+ "prisms_log_entry SET entrySaved=" + DBUtils.formatDate(time, isOracle())
						+ " WHERE ";
					DBUtils.KeyExpression keys = DBUtils.simplifyKeySet(idInts.toLongArray(), 90);
					try
					{
						DBUtils.executeUpdate(stmt, sql, keys, "", "id", 90);
					} catch(SQLException e)
					{
						throw new PrismsException("Could not update save times: SQL=" + sql, e);
					}
					return null;
				}
			}, "Could not save entries");
	}

	private String createQuery(Search search, Sorter<LogField> sorter, boolean withParameters)
		throws PrismsException
	{
		StringBuilder joins = new StringBuilder();
		StringBuilder wheres = new StringBuilder();
		if(search instanceof Search.ExpressionSearch)
			((Search.ExpressionSearch) search).simplify();
		if(search != null)
			createQuery(search, withParameters, joins, wheres);
		StringBuilder ret = new StringBuilder("SELECT DISTINCT logEntry.id");
		if(sorter != null)
			for(int i = 0; i < sorter.getSortCount(); i++)
			{
				ret.append(", logEntry.");
				ret.append(sorter.getField(i).toString());
			}
		else
			ret.append(", logEntry.logTime");
		ret.append(" FROM ");
		ret.append(theTransactor.getTablePrefix());
		ret.append("prisms_log_entry logEntry");
		ret.append(joins);
		if(wheres.length() > 0)
			ret.append(" WHERE ").append(wheres);
		return ret.toString();
	}

	private void createQuery(Search search, boolean withParameters, StringBuilder joins,
		StringBuilder wheres) throws PrismsException
	{
		if(search instanceof Search.NotSearch)
		{
			Search.NotSearch not = (Search.NotSearch) search;
			wheres.append("NOT ");
			boolean withParen = not.getParent() != null;
			if(withParen)
				wheres.append('(');
			createQuery(not.getOperand(), withParameters, joins, wheres);
			if(withParen)
				wheres.append(')');
		}
		else if(search instanceof LogEntrySearch.LogExpressionSearch
			&& ((LogEntrySearch.LogExpressionSearch) search).isSingle())
		{
			String srch = ((LogEntrySearch.StringSearch) ((LogEntrySearch.LogExpressionSearch) search)
				.getOperand(0)).search.toLowerCase();
			srch = MULTI_WILDCARD + srch + MULTI_WILDCARD;
			srch = DBUtils.toLikeClause(srch, DBUtils.getType(theTransactor.getConnection()),
				MULTI_WILDCARD, SINGLE_WILDCARD);
			if(joins.indexOf("logContent") < 0)
			{
				joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
				joins.append("prisms_log_content logContent ON logContent.logEntry=logEntry.id");
			}
			if(joins.indexOf("logContentDup") < 0)
			{
				joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
				joins.append("prisms_log_content logContentDup"
					+ " ON logContentDup.logEntry=logEntry.logDuplicate");
			}
			DBUtils.ConnType connType = DBUtils.getType(theTransactor.getConnection());
			wheres.append('(').append(DBUtils.getLowerFn(connType))
				.append("(logEntry.shortMessage) LIKE ").append(srch);
			wheres.append(" OR ").append(DBUtils.getLowerFn(connType))
				.append("(logContent.content) LIKE ").append(srch);
			wheres.append(" OR ").append(DBUtils.getLowerFn(connType))
				.append("(logContentDup.content) LIKE ").append(srch).append(')');
		}
		else if(search instanceof Search.ExpressionSearch)
		{
			Search.ExpressionSearch exp = (Search.ExpressionSearch) search;
			boolean withParen = exp.getParent() != null;
			if(withParen)
				wheres.append('(');
			boolean first = true;
			for(Search srch : exp)
			{
				if(!first)
				{
					if(exp.and)
						wheres.append(" AND ");
					else
						wheres.append(" OR ");
				}
				first = false;
				createQuery(srch, withParameters, joins, wheres);
			}
			if(withParen)
				wheres.append(')');
		}
		else if(search.getType() instanceof LogEntrySearch.LogEntrySearchType)
		{
			LogEntrySearch.LogEntrySearchType type = (LogEntrySearch.LogEntrySearchType) search
				.getType();
			switch(type)
			{
			case id:
				LogEntrySearch.IDSearch ids = (LogEntrySearch.IDSearch) search;
				wheres.append("logEntry.id").append(ids.operator.toString());
				if(ids.id != null)
					wheres.append(ids.id);
				else if(withParameters)
					wheres.append('?');
				else
					throw new PrismsException("No ID specified for ID search");
				break;
			case instance:
				LogEntrySearch.InstanceSearch is = (LogEntrySearch.InstanceSearch) search;
				wheres.append("logEntry.logInstance=");
				if(is.search != null)
					wheres.append(DBUtils.toSQL(is.search));
				else if(withParameters)
					wheres.append('?');
				else
					throw new PrismsException("No instance specified for instance search");
				break;
			case time:
				LogEntrySearch.LogTimeSearch lts = (LogEntrySearch.LogTimeSearch) search;
				appendTime(lts.operator, lts.logTime, "logEntry.logTime", wheres, withParameters);
				break;
			case age:
				LogEntrySearch.LogAgeSearch lAgeS = (LogEntrySearch.LogAgeSearch) search;
				wheres.append("logEntry.logTime");
				long now = System.currentTimeMillis();
				switch(lAgeS.operator)
				{
				case EQ:
					wheres.append(">=").append(
						DBUtils.formatDate(lAgeS.logAge.getMin(now), isOracle()));
					wheres.append(" AND logEntry.logTime<=").append(
						DBUtils.formatDate(lAgeS.logAge.getMax(now), isOracle()));
					break;
				case GT:
					wheres.append("<").append(
						DBUtils.formatDate(lAgeS.logAge.getTime(now), isOracle()));
					break;
				case GTE:
					wheres.append("<=").append(
						DBUtils.formatDate(lAgeS.logAge.getMax(now), isOracle()));
					break;
				case LT:
					wheres.append(">").append(
						DBUtils.formatDate(lAgeS.logAge.getTime(now), isOracle()));
					break;
				case LTE:
					wheres.append(">=").append(
						DBUtils.formatDate(lAgeS.logAge.getMin(now), isOracle()));
					break;
				case NEQ:
					wheres.append("<").append(
						DBUtils.formatDate(lAgeS.logAge.getMin(now), isOracle()));
					wheres.append(" OR logEntry.logTime>").append(
						DBUtils.formatDate(lAgeS.logAge.getMax(now), isOracle()));
					break;
				}
				break;
			case app:
				LogEntrySearch.LogAppSearch las = (LogEntrySearch.LogAppSearch) search;
				wheres.append("logEntry.logApp");
				if(las.search == null)
				{
					if(las.isNull())
						wheres.append(" IS NULL");
					else if(withParameters)
						wheres.append("=?");
					else
						throw new PrismsException("No application specified for app search");
				}
				else
					wheres.append('=').append(DBUtils.toSQL(las.search));
				break;
			case client:
				LogEntrySearch.LogClientSearch lcs = (LogEntrySearch.LogClientSearch) search;
				wheres.append("logEntry.logClient");
				if(lcs.search == null)
				{
					if(lcs.isNull())
						wheres.append(" IS NULL");
					else if(withParameters)
						wheres.append("=?");
					else
						throw new PrismsException("No client specified for client search");
				}
				else
					wheres.append('=').append(DBUtils.toSQL(lcs.search));
				break;
			case user:
				LogEntrySearch.LogUserSearch lus = (LogEntrySearch.LogUserSearch) search;
				wheres.append("logEntry.logUser=");
				if(lus.getUser() == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsException("No user specified for user search");
				}
				else
					wheres.append(lus.getUser().getID());
				break;
			case session:
				LogEntrySearch.LogSessionSearch lss = (LogEntrySearch.LogSessionSearch) search;
				wheres.append("logEntry.logSession");
				if(lss.search == null)
				{
					if(lss.isNull())
						wheres.append(" IS NULL");
					else if(withParameters)
						wheres.append("=?");
					else
						throw new PrismsException("No session ID specified for session search");
				}
				else
					wheres.append('=').append(DBUtils.toSQL(lss.search));
				break;
			case level:
				LogEntrySearch.LogLevelSearch lls = (LogEntrySearch.LogLevelSearch) search;
				wheres.append("logEntry.logLevel").append(lls.operator.toString());
				if(lls.level == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsException("No level specified for level search");
				}
				else
					wheres.append(lls.level.toInt());
				break;
			case loggerName:
				LogEntrySearch.LoggerNameSearch lns = (LogEntrySearch.LoggerNameSearch) search;
				wheres.append("logEntry.loggerName");
				if(lns.search == null)
				{
					if(withParameters)
						wheres.append("=?");
					else
						throw new PrismsException("No logger name specified for logger name search");
				}
				else
					wheres.append('=').append(DBUtils.toSQL(lns.search));
				break;
			case content:
				LogEntrySearch.LogContentSearch lConS = (LogEntrySearch.LogContentSearch) search;
				if(joins.indexOf("logContent") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_log_content logContent");
					joins.append(" ON logContent.logEntry=logEntry.id");
				}
				if(joins.indexOf("logContentDup") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_log_content logContentDup"
						+ " ON logContentDup.logEntry=logEntry.logDuplicate");
				}
				DBUtils.ConnType connType = DBUtils.getType(theTransactor.getConnection());
				String srch = lConS.search.toLowerCase();
				srch = MULTI_WILDCARD + srch + MULTI_WILDCARD;
				srch = DBUtils.toLikeClause(srch, connType, MULTI_WILDCARD, SINGLE_WILDCARD);
				wheres.append('(');
				wheres.append(DBUtils.getLowerFn(connType)).append("(logEntry.shortMessage) LIKE ")
					.append(srch);
				wheres.append(" OR (");
				wheres.append(DBUtils.getLowerFn(connType)).append("(logContent.content) LIKE ")
					.append(srch).append(" AND logContent.contentType='M'");
				wheres.append(") OR (");
				wheres.append(DBUtils.getLowerFn(connType)).append("(logContentDup.content) LIKE ")
					.append(srch).append(" AND logContentDup.contentType='M'");
				wheres.append("))");
				break;
			case stackTrace:
				LogEntrySearch.LogStackTraceSearch lsts = (LogEntrySearch.LogStackTraceSearch) search;
				if(joins.indexOf("logContent") < 0)
				{
					joins.append(" LEFT JOIN ");
					joins.append(theTransactor.getTablePrefix());
					joins.append("prisms_log_content logContent");
					joins.append(" ON logContent.logEntry=logEntry.id");
				}
				if(joins.indexOf("logContentDup") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_log_content logContentDup"
						+ " ON logContentDup.logEntry=logEntry.logDuplicate");
				}
				srch = lsts.search.toLowerCase();
				srch = MULTI_WILDCARD + srch + MULTI_WILDCARD;
				srch = DBUtils.toLikeClause(srch, DBUtils.getType(theTransactor.getConnection()),
					MULTI_WILDCARD, SINGLE_WILDCARD);
				connType = DBUtils.getType(theTransactor.getConnection());
				wheres.append("((");
				wheres.append(DBUtils.getLowerFn(connType)).append("(logContent.content) LIKE ")
					.append(srch).append(" AND logContentDup.contentType='S'");
				wheres.append(") OR (");
				wheres.append(DBUtils.getLowerFn(connType)).append("(logContentDup.content) LIKE ")
					.append(srch).append(" AND logContentDup.contentType='S'");
				wheres.append("))");
				break;
			case duplicate:
				LogEntrySearch.LogDuplicateSearch lds = (LogEntrySearch.LogDuplicateSearch) search;
				wheres.append("logEntry.logDuplicate");
				if(lds.getDuplicateID() == null)
				{
					if(lds.isNull())
						wheres.append(" IS NULL");
					else if(withParameters)
						wheres.append("=?");
					else
						throw new PrismsException("No duplicate ID specified for duplicate search");
				}
				else
					wheres.append('=').append(lds.getDuplicateID());
				break;
			case saved:
				LogEntrySearch.LogSavedSearch lSavS = (LogEntrySearch.LogSavedSearch) search;
				if(lSavS.saveTime == null && lSavS.isNull)
				{
					wheres.append("logEntry.entrySaved IS ");
					if(lSavS.operator == Search.Operator.NEQ)
						wheres.append("NOT ");
					wheres.append("NULL");
				}
				else
					appendTime(lSavS.operator, lSavS.saveTime, "entrySaved", wheres, withParameters);
				break;
			case size:
				LogEntrySearch.LogSizeSearch lSizS = (LogEntrySearch.LogSizeSearch) search;
				wheres.append("logEntry.entrySize");
				if(lSizS.size == null)
				{
					if(withParameters)
						wheres.append(lSizS.operator).append("?");
					else
						throw new PrismsException("No size specified for size search");
				}
				else
					wheres.append(lSizS.operator).append(lSizS.size);
				break;
			}
		}
		else
			throw new PrismsException("Unrecognized search type: " + search.getType());
	}

	private void appendTime(Search.Operator op, Search.SearchDate time, String field,
		StringBuilder wheres, boolean withParameters) throws PrismsException
	{
		if(time == null)
		{
			wheres.append(field);
			wheres.append(op);
			if(withParameters)
				wheres.append('?');
			else
				throw new PrismsException("No time specified for time search");
		}
		else if(time.minTime == time.maxTime)
		{
			wheres.append(field);
			wheres.append(op);
			wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
		}
		else
		{

			switch(op)
			{
			case EQ:
				wheres.append('(');
				wheres.append(field);
				wheres.append(">=");
				wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
				wheres.append(" AND ");
				wheres.append(field);
				wheres.append("<=");
				wheres.append(DBUtils.formatDate(time.maxTime, isOracle()));
				wheres.append(')');
				break;
			case NEQ:
				wheres.append('(');
				wheres.append(field);
				wheres.append('<');
				wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
				wheres.append(" OR ");
				wheres.append(field);
				wheres.append('>');
				wheres.append(DBUtils.formatDate(time.maxTime, isOracle()));
				wheres.append(')');
				break;
			case GT:
				wheres.append(field);
				wheres.append('>');
				wheres.append(DBUtils.formatDate(time.maxTime, isOracle()));
				break;
			case GTE:
				wheres.append(field);
				wheres.append(">=");
				wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
				break;
			case LT:
				wheres.append(field);
				wheres.append('<');
				wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
				break;
			case LTE:
				wheres.append(field);
				wheres.append("<=");
				wheres.append(DBUtils.formatDate(time.maxTime, isOracle()));
				break;
			}
		}
	}

	private String getOrder(Sorter<LogField> sorter)
	{
		StringBuilder order = new StringBuilder();
		if(sorter != null && sorter.getSortCount() > 0)
		{
			for(int sc = 0; sc < sorter.getSortCount(); sc++)
			{
				if(sc > 0)
					order.append(", ");
				order.append("logEntry.");
				order.append(sorter.getField(sc).toString());
				order.append(sorter.isAscending(sc) ? " ASC" : " DESC");
			}
		}
		else
			order.append("logEntry.logTime DESC");
		return order.toString();
	}

	/**
	 * @param purger The purger to test
	 * @return The IDs of all items that would be purged if the given purger was the auto purger for
	 *         this logger
	 * @throws IllegalArgumentException If the given purger violates this logger's constraints
	 */
	public int [] previewAutoPurge(AutoPurger purger)
	{
		if(purger.getMaxAge() < theMinConfiguredAge || purger.getMaxAge() > theMaxConfiguredAge)
			throw new IllegalArgumentException("Configured age "
				+ PrismsUtils.printTimeLength(purger.getMaxAge())
				+ " is not within the valid range: "
				+ PrismsUtils.printTimeLength(theMinConfiguredAge) + " to "
				+ PrismsUtils.printTimeLength(theMaxConfiguredAge));
		if(purger.getMaxSize() < theMinConfiguredSize || purger.getMaxSize() > theMaxConfiguredSize)
			throw new IllegalArgumentException("Configured size " + purger.getMaxSize()
				+ " is not within the valid range: " + theMinConfiguredSize + " to "
				+ theMaxConfiguredSize);
		for(Search perm : thePermanentExcludes)
			if(!ArrayUtils.contains(purger.getExcludeSearches(), perm))
				throw new IllegalArgumentException("Logger " + perm + " cannot be purged");

		IntList ret = getPurgeIDs(purger);
		return ret == null ? new int [0] : ret.toArray();
	}

	private static class LogIdTime
	{
		int id;

		long time;

		LogIdTime(int _id, long _time)
		{
			id = _id;
			time = _time;
		}
	}

	private void checkLoggerConfigs()
	{
		long now = System.currentTimeMillis();
		if(now - theLastLoggerCheck < 5000)
			return;
		ResultSet rs = null;
		try
		{
			theLoggerChecker.setTimestamp(1, new java.sql.Timestamp(theLastLoggerCheck));
			rs = theLoggerChecker.executeQuery();
			theLastLoggerCheck = now;
			while(rs.next())
			{
				String loggerName = rs.getString("logger");
				Number level = (Number) rs.getObject("logLevel");
				Logger logger = Logger.getLogger(loggerName);
				logger.setLevel(level == null ? null : org.apache.log4j.Level.toLevel(level
					.intValue()));
			}
		} catch(SQLException e)
		{
			log.error("Could not check for new logger configs", e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	private void doAutoPurge()
	{
		if(thePurger.getMaxSize() <= 0 && thePurger.getMaxAge() <= 0)
			return;
		long now = System.currentTimeMillis();
		if(now - theLastPurge < 10000)
			return;
		theLastPurge = now;

		String sql = null;
		Statement stmt = null;
		ResultSet rs = null;
		long thresh = -1;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			checkUpdatedPurger(stmt);
			IntList ids = getPurgeIDs(thePurger);
			doPurge(ids, stmt);

			sql = "SELECT MIN(logTime) FROM " + theTransactor.getTablePrefix() + "prisms_log_entry";
			rs = stmt.executeQuery(sql);
			if(rs.next() && rs.getTimestamp(1) != null)
				thresh = rs.getTimestamp(1).getTime();
		} catch(SQLException e)
		{
			log.error("Could not execute auto-purge: SQL=" + sql, e);
		} catch(PrismsException e)
		{
			log.error("Could not get connection for auto-purge", e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error!", e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error!", e);
				}
		}

		if(theExposedDir != null)
		{
			if(thresh <= 0 || thresh < now - thePurger.getMaxAge())
				thresh = now - thePurger.getMaxAge();
			autoPurge(new java.io.File(theExposedDir), true, thresh);
		}
	}

	private void autoPurge(java.io.File dir, boolean base, long thresh)
	{
		if(!dir.exists() || !dir.isDirectory() || thePurger == null || thePurger.getMaxAge() <= 0)
			return;
		java.io.File[] files = dir.listFiles();
		for(java.io.File f : files)
		{
			if(f.isHidden() || f.getName().startsWith(".") || !f.canRead())
				continue;
			if(f.isDirectory())
				autoPurge(f, false, thresh);
			else if(f.lastModified() < thresh)
				f.delete();
		}
		if(!base && files.length == 0 && dir.lastModified() < thresh)
			dir.delete();
	}

	synchronized int doPurge(IntList ids, Statement stmt) throws SQLException
	{
		if(ids == null)
			return 0;
		ids.setSorted(true); // Optimizes some calls below
		ids.setUnique(true);
		prisms.util.DBUtils.KeyExpression key = DBUtils.simplifyKeySet(ids.toLongArray(), 50);
		if(key == null)
			return 0;
		// Eliminate duplicates in items to be purged to avoid foreign key errors
		// Mark items as purged in case we can't actually delete them
		String sql = "UPDATE " + theTransactor.getTablePrefix()
			+ "prisms_log_entry SET logDuplicate=NULL, entrySize=" + MAX_SIZE + " WHERE ";
		ResultSet rs = null;
		try
		{
			DBUtils.executeUpdate(stmt, sql, key, "", "id", 90);

			sql = "SELECT id, logTime, logDuplicate FROM " + theTransactor.getTablePrefix()
				+ "prisms_log_entry WHERE ";
			java.util.HashMap<Integer, LogIdTime> dupMap = new HashMap<Integer, LogIdTime>();
			rs = DBUtils.executeQuery(stmt, sql, key, "", "logDuplicate", 90);
			while(rs.next())
			{
				int id = rs.getInt("id");
				int dup = rs.getInt("logDuplicate");
				long logTime = rs.getTimestamp("logTime").getTime();
				LogIdTime lit = dupMap.get(Integer.valueOf(dup));
				if(lit == null)
					dupMap.put(Integer.valueOf(dup), new LogIdTime(id, logTime));
				else if(logTime < lit.time)
				{
					lit.id = id;
					lit.time = logTime;
				}
			}
			rs.close();
			rs = null;
			for(java.util.Map.Entry<Integer, LogIdTime> entry : dupMap.entrySet())
			{
				int firstID = entry.getValue().id;

				// Transfers the original's content to the first duplicate that is not being deleted
				theContentTransferrer.setInt(1, firstID);
				theContentTransferrer.setInt(2, entry.getKey().intValue());
				int size = theContentTransferrer.executeUpdate() + 1;

				// Marks the first duplicate as an original
				theUnduplicator.setInt(1, size);
				theUnduplicator.setInt(2, firstID);
				theUnduplicator.executeUpdate();

				// Transfers all references to the original to the first duplicate (now an original)
				theDuplicateTransferrer.setInt(1, firstID);
				theDuplicateTransferrer.setInt(2, entry.getKey().intValue());
				theDuplicateTransferrer.executeUpdate();
			}

			sql = "DELETE FROM " + theTransactor.getTablePrefix() + "prisms_log_entry WHERE ";
			DBUtils.executeUpdate(stmt, sql, key, "", "id", 100);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error!", e);
				}
		}
		if(ids.size() > 100)
			log.debug("Purged " + ids.size() + " log entries");
		return ids.size();
	}

	private synchronized IntList getPurgeIDs(AutoPurger purger)
	{
		long now = System.currentTimeMillis();
		java.sql.PreparedStatement purgeQuery = null;
		ResultSet rs = null;
		try
		{
			rs = theSumQuery.executeQuery();
			if(!rs.next())
				return null;
			int sum = rs.getInt(1);
			java.sql.Timestamp time = rs.getTimestamp(2);
			if(time == null)
				return null;
			long oldest = time.getTime();
			rs.close();
			rs = null;

			int maxSize = purger.getMaxSize();
			if(maxSize <= 0)
				maxSize = Integer.MAX_VALUE;
			long minTime = now - purger.getMaxAge();
			if(minTime >= now)
				minTime = 0;
			if(sum <= maxSize && oldest >= minTime)
				return null;

			IntList ids = new IntList();
			try
			{
				purgeQuery = purger == thePurger ? thePurgeQuery : createAutoPurgeQuery(purger);
			} catch(PrismsException e)
			{
				log.error("Could not create purge query for auto-purger", e);
				return ids;
			}
			purgeQuery.setTimestamp(1, new java.sql.Timestamp(now));
			rs = purgeQuery.executeQuery();
			while(rs.next())
			{
				int sz = rs.getInt("entrySize");
				if(sz >= MAX_SIZE)
				{
					ids.add(rs.getInt("id"));
					continue;
				}
				long logTime = rs.getTimestamp("logTime").getTime();
				if(sum <= maxSize && logTime >= minTime)
					break;
				sum -= sz;
				ids.add(rs.getInt("id"));
			}
			rs.close();
			rs = null;

			return ids;
		} catch(SQLException e)
		{
			log.error("Could not query items for auto-purge", e);
			return null;
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error!", e);
				}
			if(purgeQuery != null && purger != thePurger)
				try
				{
					purgeQuery.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				} catch(Error e)
				{
					// Keep getting these from an HSQL bug--silence
					if(!e.getMessage().contains("compilation"))
						log.error("Error", e);
				}
		}
	}

	private void checkUpdatedPurger(Statement stmt)
	{
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_log_auto_purge";
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				long setTime = rs.getTimestamp("setTime").getTime();
				if(setTime > thePurgeSet)
				{
					prisms.logging.LogEntrySearch.LogEntrySearchBuilder builder;
					builder = new LogEntrySearch.LogEntrySearchBuilder(theEnv);
					AutoPurger purger = new AutoPurger();
					purger.setMaxSize(rs.getInt("maxSize"));
					purger.setMaxAge(rs.getLong("maxAge"));
					rs.close();
					rs = null;
					java.util.ArrayList<Search> dbExcludes = new java.util.ArrayList<Search>();
					sql = "SELECT search FROM " + theTransactor.getTablePrefix()
						+ "prisms_log_purge_exclude";
					rs = stmt.executeQuery(sql);
					while(rs.next())
					{
						String srchStr = rs.getString(1);
						Search srch;
						try
						{
							srch = builder.createSearch(srchStr);
						} catch(Exception e)
						{
							log.error("Updated search contains unparseable exclusion: " + srchStr,
								e);
							continue;
						}
						dbExcludes.add(srch);
						purger.excludeSearch(dbExcludes.get(dbExcludes.size() - 1));
					}
					rs.close();
					rs = null;
					/* Don't worry about the maxSize and maxAge constraints, but do enforce
					 * permanently excluded loggers */
					dbExcludes.clear();
					for(Search perm : thePermanentExcludes)
						if(!ArrayUtils.contains(purger.getExcludeSearches(), perm))
						{
							purger.excludeSearch(perm);
							dbExcludes.add(perm);
						}
					if(dbExcludes.size() > 0)
					{
						log.warn("Permanently excluded loggers " + dbExcludes
							+ " not included in new auto purge. Adding.");
						for(Search ex : dbExcludes)
						{
							String srchStr = ex.toString();
							if(srchStr.length() > 1024)
								continue;
							sql = "INSERT INTO " + theTransactor.getTablePrefix()
								+ "prisms_log_purge_exclude (search) VALUES ("
								+ DBUtils.toSQL(srchStr) + ")";
							stmt.executeUpdate(sql);
						}
					}
					purger.seal();
					thePurgeSet = setTime;
					thePurger = purger;
				}
			}
		} catch(SQLException e)
		{
			log.error("Could not query updated auto-purger: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error!", e);
				}
		}
	}

	private Boolean _isOracle = null;

	/**
	 * @return Whether this data source is using an oracle database or not
	 * @throws PrismsException If the connection cannot be obtained
	 */
	protected boolean isOracle() throws PrismsException
	{
		if(_isOracle == null)
			_isOracle = Boolean.valueOf(DBUtils.isOracle(theTransactor.getConnection()));
		return _isOracle.booleanValue();
	}

	String formatDate(long time) throws PrismsException
	{
		return DBUtils.formatDate(time, isOracle());
	}

	private java.sql.PreparedStatement theIDGetter;

	private java.sql.PreparedStatement theDuplicateQuery;

	private java.sql.PreparedStatement theContentQuery;

	private java.sql.PreparedStatement theInserter;

	private java.sql.PreparedStatement theContentInserter;

	private java.sql.PreparedStatement theSumQuery;

	private java.sql.PreparedStatement theLoggerChecker;

	private java.sql.PreparedStatement theUnduplicator;

	private java.sql.PreparedStatement theContentTransferrer;

	private java.sql.PreparedStatement theDuplicateTransferrer;

	private java.sql.PreparedStatement thePurgeQuery;

	void prepareStatements()
	{
		String sql;
		try
		{
			sql = prisms.arch.ds.IDGenerator.prepareNextIntID("prisms_log_entry",
				theTransactor.getTablePrefix(), "id", null);
			theIDGetter = theTransactor.getConnection().prepareStatement(sql);

			sql = "SELECT id, shortMessage FROM " + theTransactor.getTablePrefix()
				+ "prisms_log_entry WHERE logDuplicate IS NULL"
				+ " AND messageCRC=? AND stackTraceCRC=? AND trackingCRC=? AND entrySize<"
				+ MAX_SIZE;
			theDuplicateQuery = theTransactor.getConnection().prepareStatement(sql);

			sql = "SELECT * FROM " + theTransactor.getTablePrefix()
				+ "prisms_log_content WHERE logEntry=? ORDER BY indexNum";
			theContentQuery = theTransactor.getConnection().prepareStatement(sql);

			sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_log_entry"
				+ " (id, logInstance, logTime, logApp, logClient, logUser, logSession,"
				+ " logLevel, loggerName, shortMessage, messageCRC, stackTraceCRC, trackingCRC,"
				+ " logDuplicate, entrySize) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			theInserter = theTransactor.getConnection().prepareStatement(sql);

			sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_log_content (logEntry,"
				+ " indexNum, content, contentType) VALUES (?, ?, ?, ?)";
			theContentInserter = theTransactor.getConnection().prepareStatement(sql);

			sql = "SELECT SUM(entrySize), MIN(logTime) FROM " + theTransactor.getTablePrefix()
				+ "prisms_log_entry WHERE entrySize<" + MAX_SIZE;
			theSumQuery = theTransactor.getConnection().prepareStatement(sql);

			sql = "SELECT * FROM " + theTransactor.getTablePrefix()
				+ "prisms_logger_config WHERE setTime>=?";
			theLoggerChecker = theTransactor.getConnection().prepareStatement(sql);

			sql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_log_entry SET logDuplicate=NULL, entrySize=? WHERE id=?";
			theUnduplicator = theTransactor.getConnection().prepareStatement(sql);

			sql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_log_content SET logEntry=? WHERE logEntry=?";
			theContentTransferrer = theTransactor.getConnection().prepareStatement(sql);

			sql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_log_entry SET logDuplicate=? WHERE logDuplicate=?";
			theDuplicateTransferrer = theTransactor.getConnection().prepareStatement(sql);
		} catch(PrismsException e)
		{
			throw new IllegalStateException("Could not reach "
				+ theTransactor.getConnectionConfig().get("name") + " database", e);
		} catch(SQLException e)
		{
			throw new IllegalStateException("Could not prepare logging statements", e);
		}
		try
		{
			thePurgeQuery = createAutoPurgeQuery(thePurger);
		} catch(PrismsException e)
		{
			throw new IllegalStateException("Could not construct auto-purge query", e);
		}
	}

	java.sql.PreparedStatement createAutoPurgeQuery(AutoPurger purger) throws PrismsException
	{
		Search sz = new LogEntrySearch.LogSizeSearch(Search.Operator.GTE, Integer.valueOf(MAX_SIZE));
		Search save = new LogEntrySearch.LogSavedSearch(Search.Operator.EQ, null, true)
			.or(new LogEntrySearch.LogSavedSearch(Search.Operator.LTE, null, false));
		Search or = sz.or(save);
		if(thePurger.getExcludeSearches().length > 0)
		{
			Search excl = new Search.NotSearch(new Search.ExpressionSearch(false).addOps(thePurger
				.getExcludeSearches()));
			or = or.and(excl);
		}

		StringBuilder joins = new StringBuilder();
		StringBuilder wheres = new StringBuilder();
		createQuery(or, true, joins, wheres);
		StringBuilder ret = new StringBuilder(
			"SELECT DISTINCT logEntry.id, logEntry.logTime, logEntry.entrySize");
		ret.append(" FROM ");
		ret.append(theTransactor.getTablePrefix());
		ret.append("prisms_log_entry logEntry");
		ret.append(joins);
		if(wheres.length() > 0)
			ret.append(" WHERE ").append(wheres);
		ret.append(" ORDER BY logEntry.logTime");
		try
		{
			return theTransactor.getConnection().prepareStatement(ret.toString());
		} catch(SQLException e)
		{
			throw new PrismsException("Could not prepare auto-purge search", e);
		}
	}

	void destroyPreparedStatements()
	{
		if(theSumQuery == null)
			return;
		try
		{
			theIDGetter.close();
			theIDGetter = null;
			theDuplicateQuery.close();
			theDuplicateQuery = null;
			theContentQuery.close();
			theContentQuery = null;
			theInserter.close();
			theInserter = null;
			theContentInserter.close();
			theContentInserter = null;
			theSumQuery.close();
			theSumQuery = null;
			theUnduplicator.close();
			theUnduplicator = null;
			theContentTransferrer.close();
			theContentTransferrer = null;
			theDuplicateTransferrer.close();
			theDuplicateTransferrer = null;
		} catch(SQLException e)
		{
			log.error("Could not close prepared statements", e);
		} catch(Error e)
		{
			// Keep getting these from an HSQL bug--silence
			if(!e.getMessage().contains("compilation"))
				log.error("Error", e);
		}
	}

	/** Releases all resources associated with this logger */
	public void disconnect()
	{
		isClosed = true;
		destroyPreparedStatements();
		for(Logger logger : theLoggers)
			logger.removeAppender(theAppender);
		theTransactor.release();
	}

	/**
	 * Implements
	 * {@link prisms.util.AbstractPreparedSearch#addParamTypes(Search, java.util.Collection)} for
	 * log entry records
	 * 
	 * @param search The search to get the missing parameter types for
	 * @param types The collection to add the missing types to
	 */
	protected static void addParamTypes(LogEntrySearch search, java.util.Collection<Class<?>> types)
	{
		switch(search.getType())
		{
		case id:
			LogEntrySearch.IDSearch ids = (LogEntrySearch.IDSearch) search;
			if(ids.id == null)
				types.add(Integer.class);
			break;
		case instance:
			LogEntrySearch.InstanceSearch is = (LogEntrySearch.InstanceSearch) search;
			if(is.search == null)
				types.add(String.class);
			break;
		case time:
			LogEntrySearch.LogTimeSearch lts = (LogEntrySearch.LogTimeSearch) search;
			if(lts.logTime == null)
				types.add(java.util.Date.class);
			break;
		case age:
			break;
		case app:
			LogEntrySearch.LogAppSearch las = (LogEntrySearch.LogAppSearch) search;
			if(las.search == null)
				types.add(String.class);
			break;
		case client:
			LogEntrySearch.LogClientSearch lcs = (LogEntrySearch.LogClientSearch) search;
			if(lcs.search == null)
				types.add(String.class);
			break;
		case user:
			LogEntrySearch.LogUserSearch lus = (LogEntrySearch.LogUserSearch) search;
			if(lus.getUser() == null)
				types.add(Long.class);
			break;
		case session:
			LogEntrySearch.LogSessionSearch lss = (LogEntrySearch.LogSessionSearch) search;
			if(lss.search == null)
				types.add(String.class);
			break;
		case level:
			LogEntrySearch.LogLevelSearch lls = (LogEntrySearch.LogLevelSearch) search;
			if(lls.level == null)
				types.add(org.apache.log4j.Level.class);
			break;
		case loggerName:
			LogEntrySearch.LoggerNameSearch lns = (LogEntrySearch.LoggerNameSearch) search;
			if(lns.search == null)
				types.add(String.class);
			break;
		case duplicate:
			LogEntrySearch.LogDuplicateSearch lds = (LogEntrySearch.LogDuplicateSearch) search;
			if(lds.getDuplicateID() == null)
				types.add(Long.class);
			break;
		case content:
		case stackTrace:
			break;
		case saved:
			LogEntrySearch.LogSavedSearch lSavS = (LogEntrySearch.LogSavedSearch) search;
			if(lSavS.saveTime == null && !lSavS.isNull)
				types.add(java.util.Date.class);
			break;
		case size:
			LogEntrySearch.LogSizeSearch lSizS = (LogEntrySearch.LogSizeSearch) search;
			if(lSizS.size == null)
				types.add(Integer.class);
			break;
		}
	}
}
