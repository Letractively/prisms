/*
 * DefaultMessageManager.java Created Oct 1, 2010 by Andrew Butler, PSL
 */
package prisms.message;

import static prisms.util.DBUtils.boolToSql;
import static prisms.util.DBUtils.toSQL;

import java.io.IOException;
import java.sql.*;
import java.util.Collection;

import org.apache.log4j.Logger;

import prisms.arch.PrismsException;
import prisms.arch.ds.Transactor;
import prisms.arch.ds.Transactor.TransactionOperation;
import prisms.arch.ds.User;
import prisms.message.MessageChangeTypes.ConversationViewChange;
import prisms.message.MessageChangeTypes.MessageChange;
import prisms.message.MessageChangeTypes.RecipientChange;
import prisms.message.MessageSearch.SizeSearch;
import prisms.records.RecordsTransaction;
import prisms.util.*;
import prisms.util.Sorter.Field;

/** The default {@link MessageManager} implementation for PRISMS */
public class DefaultMessageManager implements MessageManager
{
	static final int CONTENT_LENGTH = 1024;

	static final int CONTENT_OVERLAP = 32;

	static final String MULTI_WILDCARD = "(<**>)";

	static final String SINGLE_WILDCARD = "(<..>)";

	static final Logger log = Logger.getLogger(DefaultMessageManager.class);

	private static class DBMessagePrepSearch extends
		prisms.util.DBPreparedSearch<MessageSearch, Field, PrismsMessageException>
	{
		DBMessagePrepSearch(Transactor<PrismsMessageException> transactor, String sql, Search srch,
			Sorter<Field> sorter) throws PrismsMessageException
		{
			super(transactor, sql, srch, null, MessageSearch.class);
		}

		@Override
		protected synchronized long [] execute(Object... params) throws PrismsMessageException
		{
			return super.execute(params);
		}

		@Override
		protected void dispose()
		{
			super.dispose();
		}

		@Override
		protected void addSqlTypes(MessageSearch search, IntList types)
		{
			switch(search.getType())
			{
			case id:
				MessageSearch.IDSearch ids = (MessageSearch.IDSearch) search;
				if(ids.id == null)
					types.add(java.sql.Types.NUMERIC);
				break;
			case conversation:
				MessageSearch.ConversationSearch convS = (MessageSearch.ConversationSearch) search;
				if(convS.conversationID == null)
					types.add(java.sql.Types.NUMERIC);
				break;
			case successor:
				MessageSearch.SuccessorSearch sucS = (MessageSearch.SuccessorSearch) search;
				if(sucS.message == null)
					types.add(java.sql.Types.NUMERIC);
				break;
			case subject:
				break;
			case author:
				MessageSearch.AuthorSearch authS = (MessageSearch.AuthorSearch) search;
				if(authS.user == null && authS.userName == null)
					types.add(java.sql.Types.NUMERIC);
				break;
			case sent:
				MessageSearch.SentSearch sentS = (MessageSearch.SentSearch) search;
				if(sentS.sentTime == null && sentS.sent == null)
					types.add(java.sql.Types.TIMESTAMP);
				break;
			case priority:
				MessageSearch.PrioritySearch priS = (MessageSearch.PrioritySearch) search;
				if(priS.priority == null)
					types.add(java.sql.Types.INTEGER);
				break;
			case recipient:
				MessageSearch.RecipientSearch recS = (MessageSearch.RecipientSearch) search;
				if(recS.user == null && recS.userName == null)
					types.add(java.sql.Types.NUMERIC);
				break;
			case readTime:
				MessageSearch.ReadTimeSearch rts = (MessageSearch.ReadTimeSearch) search;
				if(rts.user == null && rts.userName == null)
					types.add(java.sql.Types.NUMERIC);
				if(rts.readTime == null)
					types.add(java.sql.Types.TIMESTAMP);
				break;
			case view:
				MessageSearch.ViewSearch vs = (MessageSearch.ViewSearch) search;
				if(vs.user == null && vs.userName == null)
					types.add(java.sql.Types.NUMERIC);
				break;
			case content:
			case attachment:
			case size:
			case deleted:
				break;
			}
		}

		@Override
		protected void addParamTypes(MessageSearch search, Collection<Class<?>> types)
		{
			switch(search.getType())
			{
			case id:
				MessageSearch.IDSearch ids = (MessageSearch.IDSearch) search;
				if(ids.id == null)
					types.add(Long.class);
				break;
			case conversation:
				MessageSearch.ConversationSearch convS = (MessageSearch.ConversationSearch) search;
				if(convS.conversationID == null)
					types.add(Long.class);
				break;
			case successor:
				MessageSearch.SuccessorSearch sucS = (MessageSearch.SuccessorSearch) search;
				if(sucS.message == null)
					types.add(Long.class);
				break;
			case subject:
				break;
			case author:
				MessageSearch.AuthorSearch authS = (MessageSearch.AuthorSearch) search;
				if(authS.user == null && authS.userName == null)
					types.add(Long.class);
				break;
			case sent:
				MessageSearch.SentSearch sentS = (MessageSearch.SentSearch) search;
				if(sentS.sentTime == null && sentS.sent == null)
					types.add(java.util.Date.class);
				break;
			case priority:
				MessageSearch.PrioritySearch priS = (MessageSearch.PrioritySearch) search;
				if(priS.priority == null)
					types.add(Integer.class);
				break;
			case recipient:
				MessageSearch.RecipientSearch recS = (MessageSearch.RecipientSearch) search;
				if(recS.user == null && recS.userName == null)
					types.add(Long.class);
				break;
			case readTime:
				MessageSearch.ReadTimeSearch rts = (MessageSearch.ReadTimeSearch) search;
				if(rts.user == null && rts.userName == null)
					types.add(Long.class);
				if(rts.readTime == null)
					types.add(java.util.Date.class);
				break;
			case view:
				MessageSearch.ViewSearch vs = (MessageSearch.ViewSearch) search;
				if(vs.user == null && vs.userName == null)
					types.add(Long.class);
				break;
			case content:
			case attachment:
			case size:
			case deleted:
				break;
			}
		}
	}

	private class ViewAPI implements SearchableAPI<MessageView, Field, PrismsMessageException>
	{
		ViewAPI()
		{
		}

		public long [] search(Search search, Sorter<Field> sorter) throws PrismsMessageException
		{
			StringBuilder joins = new StringBuilder();
			StringBuilder wheres = new StringBuilder();
			if(search == null)
				search = new MessageSearch.DeletedSearch(Boolean.FALSE);
			else if(!hasDelSearch(search))
				search = search.and(new MessageSearch.DeletedSearch(Boolean.FALSE));
			if(search instanceof Search.ExpressionSearch)
				((Search.ExpressionSearch) search).simplify();
			joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
			joins.append("prisms_message_view msgView ON msgView.messageNS=msg.messageNS AND")
				.append(" msgView.viewMsg=msg.id");
			compileQuery(search, false, true, joins, wheres);
			String select = "SELECT DISTINCT msgView.id, msg.msgTime";
			select += " FROM " + theTransactor.getTablePrefix() + "prisms_message msg";
			if(joins.length() > 0)
				joins.insert(0, ' ');
			joins.insert(0, select);
			if(wheres.length() > 0)
			{
				joins.append(" WHERE ");
				joins.append(wheres);
			}
			String sql = joins.toString() + " ORDER BY msg.msgTime DESC";

			Statement stmt = null;
			ResultSet rs = null;
			try
			{
				theTransactor.checkConnected();
				stmt = theTransactor.getConnection().createStatement();
				rs = stmt.executeQuery(sql);
				LongList ids = new LongList();
				while(rs.next())
					ids.add(rs.getLong(1));
				rs.close();
				rs = null;
				return ids.toArray();
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not search messages", e);
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
		}

		public prisms.util.SearchableAPI.PreparedSearch<Field> prepare(Search search,
			Sorter<Field> sorter) throws PrismsMessageException
		{
			StringBuilder joins = new StringBuilder();
			StringBuilder wheres = new StringBuilder();
			if(search == null)
				search = new MessageSearch.DeletedSearch(Boolean.FALSE);
			else if(!hasDelSearch(search))
				search = search.and(new MessageSearch.DeletedSearch(Boolean.FALSE));
			if(search instanceof Search.ExpressionSearch)
				((Search.ExpressionSearch) search).simplify();
			joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
			joins.append("prisms_message_view msgView ON msgView.messageNS=msg.messageNS AND")
				.append(" msgView.viewMsg=msg.id");
			compileQuery(search, true, true, joins, wheres);
			String select = "SELECT DISTINCT msgView.id, msg.msgTime";
			select += " FROM " + theTransactor.getTablePrefix() + "prisms_message msg";
			if(joins.length() > 0)
				joins.insert(0, ' ');
			joins.insert(0, select);
			if(wheres.length() > 0)
			{
				joins.append(" WHERE ");
				joins.append(wheres);
			}
			String sql = joins.toString() + " ORDER BY msg.msgTime DESC";
			return new DBMessagePrepSearch(theTransactor, sql, search, sorter);
		}

		public long [] execute(prisms.util.SearchableAPI.PreparedSearch<Field> search,
			Object... params) throws PrismsMessageException
		{
			return ((DBMessagePrepSearch) search).execute(params);
		}

		public void destroy(prisms.util.SearchableAPI.PreparedSearch<Field> search)
			throws PrismsMessageException
		{
			((DBMessagePrepSearch) search).dispose();
		}

		public MessageView [] getItems(long... ids) throws PrismsMessageException
		{
			if(ids.length == 0)
				return new MessageView [0];
			LongList idList = new LongList(true, true);
			idList.addAll(ids);
			java.util.ArrayList<MessageView> ret = new java.util.ArrayList<MessageView>();
			java.util.HashMap<Long, MessageView []> viewsByMsgId = new java.util.HashMap<Long, MessageView []>();
			DBUtils.KeyExpression keys = DBUtils.simplifyKeySet(idList.toArray(), 90);
			String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
				+ "prisms_message_view msg INNER JOIN " + theTransactor.getTablePrefix()
				+ "prisms_conversation_view conv ON msg.messageNS=conv.messageNS AND"
				+ " msg.viewConversation=conv.id WHERE ";
			java.util.HashMap<Long, ConversationView> convs = new java.util.HashMap<Long, ConversationView>();
			Statement stmt = null;
			ResultSet rs = null;
			try
			{
				java.util.HashMap<Long, MessageView> msgViewsById = new java.util.HashMap<Long, MessageView>();
				stmt = theTransactor.getConnection().createStatement();
				rs = DBUtils.executeQuery(stmt, sql, keys, "", "msg.viewMsg", 90);
				while(rs.next())
				{
					Long convID = Long.valueOf(rs.getLong("conv.viewConversation"));
					ConversationView conv = convs.get(convID);
					if(conv == null)
					{
						User viewer;
						try
						{
							viewer = theEnv.getUserSource().getUser(rs.getLong("conv.viewUser"));
						} catch(PrismsException e)
						{
							throw new PrismsMessageException("Could not get user with ID "
								+ rs.getLong("conv.viewUser"), e);
						}
						conv = new ConversationView(convID.longValue(), viewer);
						conv.setID(rs.getLong("conv.id"));
						conv.setArchived(DBUtils.boolFromSql(rs.getString("conv.viewArchived")));
						conv.setStarred(DBUtils.boolFromSql(rs.getString("conv.viewStarred")));
						convs.put(convID, conv);
					}
					MessageView msgView = new MessageView(conv, null);
					Long msgID = Long.valueOf(rs.getLong("msg.viewMsg"));
					MessageView [] msgViews = viewsByMsgId.get(msgID);
					msgViews = ArrayUtils.add(msgViews, msgView);
					viewsByMsgId.put(msgID, msgViews);
					msgView.setID(rs.getLong("msg.id"));
					msgView.setDeleted(DBUtils.boolFromSql(rs.getString("msg.deleted")));
					msgViewsById.put(msgID, msgView);
					ret.add(msgView);
				}
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not get message views: SQL=" + sql, e);
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

			LongList msgIds = new LongList(true, true);
			for(Long id : viewsByMsgId.keySet())
				msgIds.add(id.longValue());
			Message [] msgs = DefaultMessageManager.this.getItems(msgIds.toArray());
			for(java.util.Map.Entry<Long, MessageView []> entry : viewsByMsgId.entrySet())
			{
				Message msg = msgs[msgIds.indexOf(entry.getKey().longValue())];
				for(MessageView view : entry.getValue())
					view.setMessage(msg);
			}
			Long [] idObjs = new Long [ids.length];
			for(int i = 0; i < ids.length; i++)
				idObjs[i] = Long.valueOf(ids[i]);
			final ArrayUtils.ArrayAdjuster<MessageView, Long, RuntimeException> [] adjuster;
			adjuster = new ArrayUtils.ArrayAdjuster [0];
			adjuster[0] = new ArrayUtils.ArrayAdjuster<MessageView, Long, RuntimeException>(
				ret.toArray(new MessageView [ret.size()]), idObjs,
				new ArrayUtils.DifferenceListener<MessageView, Long>()
				{
					public boolean identity(MessageView o1, Long o2)
					{
						return o1.getID() == o2.longValue();
					}

					public MessageView added(Long o, int mIdx, int retIdx)
					{
						adjuster[0].nullElement();
						return null;
					}

					public MessageView removed(MessageView o, int oIdx, int incMod, int retIdx)
					{
						return null;
					}

					public MessageView set(MessageView o1, int idx1, int incMod, Long o2, int idx2,
						int retIdx)
					{
						return o1;
					}
				});
			return adjuster[0].adjust();
		}
	}

	final String theNamespace;

	final prisms.arch.PrismsEnv theEnv;

	prisms.arch.ds.Transactor<PrismsMessageException> theTransactor;

	private prisms.records.DBRecordKeeper theRecordKeeper;

	private ViewAPI theViewAPI;

	private DemandCache<Long, Message> theMessageCache;

	private java.util.ArrayList<PreparedStatement> thePStatements;

	private PreparedStatement theContentGetter;

	private PreparedStatement theReceiptGetter;

	private PreparedStatement theAttachGetter;

	private PreparedStatement theActionInserter;

	private PreparedStatement theActionUpdater;

	private PreparedStatement theContentInserter;

	private PreparedStatement theContentDeleter;

	private PreparedStatement theContentUpdater;

	/**
	 * Creates a message manager
	 * 
	 * @param namespace The namespace to get messages from
	 * @param env The PRISMS environment to get messages in
	 * @param connEl The connection element to use to connect to the data source
	 * @param recordKeeper The record keeper to keep track of changes to all messages in this data
	 *        source
	 */
	public DefaultMessageManager(String namespace, prisms.arch.PrismsEnv env,
		prisms.arch.PrismsConfig connEl, prisms.records.DBRecordKeeper recordKeeper)
	{
		theNamespace = namespace;
		theEnv = env;
		thePStatements = new java.util.ArrayList<PreparedStatement>();
		theTransactor = theEnv.getConnectionFactory().getConnection(connEl, null,
			new prisms.arch.ds.Transactor.Thrower<PrismsMessageException>()
			{
				public void error(String message) throws PrismsMessageException
				{
					throw new PrismsMessageException(message);
				}

				public void error(String message, Throwable cause) throws PrismsMessageException
				{
					throw new PrismsMessageException(message, cause);
				}
			});
		theTransactor.addReconnectListener(new prisms.arch.ds.Transactor.ReconnectListener()
		{
			public void reconnected(boolean initial)
			{
				if(initial)
					return;
				closePStatements();
				try
				{
					createPStatements();
				} catch(PrismsMessageException e)
				{
					log.error("Could not close statements", e);
				}
			}

			public void released()
			{
			}
		});
		theRecordKeeper = recordKeeper;
		theViewAPI = new ViewAPI();
		theMessageCache = new DemandCache<Long, Message>(new DemandCache.Qualitizer<Message>()
		{
			public float quality(Message value)
			{
				return 1;
			}

			public float size(Message value)
			{
				return getMessageSize(value);
			}
		}, 250, 4L * 60 * 1000);
	}

	void closePStatements()
	{
		for(PreparedStatement pStmt : thePStatements)
			try
			{
				pStmt.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			} catch(Error e)
			{
				// Keep getting these from an HSQL bug--silence
				if(!e.getMessage().contains("compilation"))
					log.error("Error", e);
			}
		thePStatements.clear();
	}

	void createPStatements() throws PrismsMessageException
	{
		String sql = null;
		try
		{
			sql = "SELECT content FROM " + theTransactor.getTablePrefix()
				+ "prisms_message_content WHERE messageNS=" + toSQL(theNamespace)
				+ " AND message=? AND contentType=? ORDER BY indexNum";
			theContentGetter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theContentGetter);

			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_message_recipient"
				+ " WHERE messageNS=" + toSQL(theNamespace) + " AND (deleted=" + boolToSql(false)
				+ " OR deleted='?') ORDER BY rcptMessage";
			theReceiptGetter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theReceiptGetter);

			sql = "SELECT id, attName, attType, attLength, attCrc, deleted FROM "
				+ theTransactor.getTablePrefix() + "prisms_message_attachment WHERE messageNS="
				+ toSQL(theNamespace) + " AND attMessage=? AND (deleted=" + boolToSql(false)
				+ " OR deleted='?')";
			theAttachGetter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theAttachGetter);

			sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_message_action ("
				+ "messageNS, id, actionMessage, actionContent, actionCompleted, deleted) VALUES ("
				+ toSQL(theNamespace) + ", ?, ?, ?, ?, ?)";
			theActionInserter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theActionInserter);

			sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_message_action"
				+ " SET actionContent=? WHERE messageNS=" + toSQL(theNamespace) + " AND id=?";
			theActionUpdater = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theActionUpdater);

			sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_message_content"
				+ " (messageNS, message, contentType, indexNum, content) VALUES ("
				+ toSQL(theNamespace) + ", ?, ?, ?, ?)";
			theContentInserter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theContentInserter);

			sql = "DELETE FROM " + theTransactor.getTablePrefix() + "prisms_message_content WHERE"
				+ " messageNS=" + toSQL(theNamespace) + " AND message=? AND contentType=?"
				+ " AND indexNum>=?";
			theContentDeleter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theContentDeleter);

			sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_message_content"
				+ " SET content=? WHERE messageNS=" + toSQL(theNamespace) + " AND message=?"
				+ " AND contentType=? AND indexNum=?";
			theContentUpdater = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theContentUpdater);
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not prepare statements for messaging: SQL="
				+ sql, e);
		}
	}

	public SearchableAPI<MessageView, Field, PrismsMessageException> views()
	{
		return theViewAPI;
	}

	public long [] search(Search search, Sorter<Sorter.Field> sorter) throws PrismsMessageException
	{
		StringBuilder joins = new StringBuilder();
		StringBuilder wheres = new StringBuilder();
		if(search == null)
			search = new MessageSearch.DeletedSearch(Boolean.FALSE);
		else if(!hasDelSearch(search))
			search = search.and(new MessageSearch.DeletedSearch(Boolean.FALSE));
		if(search instanceof Search.ExpressionSearch)
			((Search.ExpressionSearch) search).simplify();
		compileQuery(search, false, false, joins, wheres);
		String select = "SELECT DISTINCT msg.id, msg.msgTime";
		select += " FROM " + theTransactor.getTablePrefix() + "prisms_message msg";
		if(joins.length() > 0)
			joins.insert(0, ' ');
		joins.insert(0, select);
		if(wheres.length() > 0)
		{
			joins.append(" WHERE ");
			joins.append(wheres);
		}
		String sql = joins.toString() + " ORDER BY msg.mgsTime DESC";
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			theTransactor.checkConnected();
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			LongList ids = new LongList();
			while(rs.next())
				ids.add(rs.getLong(1));
			rs.close();
			rs = null;
			return ids.toArray();
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not search messages", e);
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
	}

	public PreparedSearch<Field> prepare(Search search, Sorter<Field> sorter)
		throws PrismsMessageException
	{
		StringBuilder joins = new StringBuilder();
		StringBuilder wheres = new StringBuilder();
		if(search == null)
			search = new MessageSearch.DeletedSearch(Boolean.FALSE);
		else if(!hasDelSearch(search))
			search = search.and(new MessageSearch.DeletedSearch(Boolean.FALSE));
		if(search instanceof Search.ExpressionSearch)
			((Search.ExpressionSearch) search).simplify();
		compileQuery(search, true, false, joins, wheres);
		String select = "SELECT DISTINCT msg.id, msg.msgTime";
		select += " FROM " + theTransactor.getTablePrefix() + "prisms_message msg";
		if(joins.length() > 0)
			joins.insert(0, ' ');
		joins.insert(0, select);
		if(wheres.length() > 0)
		{
			joins.append(" WHERE ");
			joins.append(wheres);
		}
		String sql = joins.toString() + " ORDER BY msg.msgTime DESC";
		return new DBMessagePrepSearch(theTransactor, sql, search, sorter);
	}

	public long [] execute(PreparedSearch<Field> search, Object... params)
		throws PrismsMessageException
	{
		return ((DBMessagePrepSearch) search).execute(params);
	}

	public void destroy(PreparedSearch<Field> search) throws PrismsMessageException
	{
		((DBMessagePrepSearch) search).dispose();
	}

	public Message [] getItems(long... ids) throws PrismsMessageException
	{
		return getMessages(null, false, ids);
	}

	Message [] getMessages(Statement stmt, boolean withDeletedContent, long... ids)
		throws PrismsMessageException
	{
		if(ids.length == 0)
			return new Message [0];

		LongList idList = new LongList(ids);
		java.util.ArrayList<Message> cached = new java.util.ArrayList<Message>();
		for(int i = 0; i < idList.size(); i++)
		{
			Message cachedMsg = theMessageCache.get(Long.valueOf(idList.get(i)));
			if(cachedMsg != null)
			{
				cachedMsg = cachedMsg.clone();
				if(!withDeletedContent)
					cleanDeleted(cachedMsg);
				cached.add(cachedMsg);
				idList.remove(i);
				i--;
			}
		}
		if(idList.size() == 0)
			return cached.toArray(new Message [cached.size()]);

		DBUtils.KeyExpression expr = DBUtils.simplifyKeySet(idList.toArray(), 90);

		java.util.ArrayList<Message> ret = new java.util.ArrayList<Message>();
		java.util.HashMap<Long, Message> msgsById = new java.util.HashMap<Long, Message>();
		boolean closeAfter = stmt == null;
		if(stmt == null)
		{
			try
			{
				stmt = theTransactor.getConnection().createStatement();
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not create statement", e);
			}
		}
		ResultSet rs = null;
		try
		{
			String sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_message"
				+ " WHERE messageNS=" + toSQL(theNamespace);
			boolean hasLargeSubject = false;
			rs = DBUtils.executeQuery(stmt, sql + " AND ", expr, "", "id", 90);
			while(rs.next())
			{
				long authorID = rs.getLong("msgAuthor");
				User author;
				try
				{
					author = theEnv.getUserSource().getUser(authorID);
				} catch(PrismsException e)
				{
					throw new PrismsMessageException("Could not get author", e);
				}
				if(author == null)
				{
					log.error("No such user with ID " + authorID + "--could not retrieve message "
						+ rs.getLong("id"));
					continue;
				}
				Message msg = new Message(this, author);
				msg.setID(rs.getLong("id"));
				msg.setConversationID(rs.getLong("msgConversation"));
				msg.setPredecessorID(rs.getLong("msgPredecessor"));
				msg.setTime(rs.getLong("msgTime"));
				msg.setSent(DBUtils.boolFromSql(rs.getString("msgSent")));
				msg.setPriority(prisms.message.Message.Priority.values()[rs.getInt("msgPriority")]);
				String subject = DBUtils.fromSQL(rs.getString("msgShortSubject"));
				if(subject != null)
					msg.setSubject(subject);
				else
					hasLargeSubject = true;
				msg.setLength(rs.getInt("msgContentLength"));
				msg.setContentCRC(rs.getLong("msgContentCrc"));
				msg.setSize(rs.getInt("msgSize"));
				msg.setDeleted(DBUtils.boolFromSql(rs.getString("deleted")));
				ret.add(msg);
				msgsById.put(Long.valueOf(msg.getID()), msg);
			}
			rs.close();
			rs = null;
			if(ret.isEmpty())
				return new Message [ids.length];

			// Retrieve large subjects
			Message msg = null;
			if(hasLargeSubject)
			{
				sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_message_content"
					+ " WHERE messageNS=" + toSQL(theNamespace) + " AND contentType=" + toSQL("S");
				rs = DBUtils.executeQuery(stmt, sql + " AND ", expr, " ORDER BY message, indexNum",
					"message", 90);
				while(rs.next())
				{
					long msgID = rs.getLong("message");
					if(msg == null || msg.getID() != msgID)
						msg = msgsById.get(Long.valueOf(msgID));
					if(msg == null)
						continue;
					if(msg.getSubject() == null)
						msg.setSubject(rs.getString("content"));
					else
						msg.setSubject(msg.getSubject()
							+ rs.getString("content").substring(
								msg.getSubject().length() - rs.getInt("indexNum")));
				}
				rs.close();
				rs = null;
			}

			// Retrieve recipients
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_message_recipient"
				+ " WHERE messageNS=" + toSQL(theNamespace);
			if(!withDeletedContent)
				sql += " AND deleted=" + boolToSql(false);
			rs = DBUtils.executeQuery(stmt, sql + " AND ", expr, " ORDER BY rcptMessage",
				"rcptMessage", 90);
			msg = null;
			while(rs.next())
			{
				long msgID = rs.getLong("rcptMessage");
				if(msg == null || msg.getID() != msgID)
					msg = msgsById.get(Long.valueOf(msgID));
				if(msg == null)
					continue;
				long userID = rs.getLong("rcptUser");
				User user;
				try
				{
					user = theEnv.getUserSource().getUser(userID);
				} catch(PrismsException e)
				{
					throw new PrismsMessageException("Could not get author", e);
				}
				if(user == null)
				{
					log.error("No such user with ID " + userID
						+ "--could not retrieve recipient of message " + msg.getID());
					continue;
				}
				Recipient rcpt = msg.addRecipient(user);
				rcpt.setApplicability(Recipient.Applicability.values()[rs.getInt("rcptType")]);
				Timestamp time = rs.getTimestamp("firstViewed");
				rcpt.setFirstViewed(time == null ? -1 : time.getTime());
				time = rs.getTimestamp("lastViewed");
				rcpt.setLastViewed(time == null ? -1 : time.getTime());
				rcpt.setDeleted(DBUtils.boolFromSql(rs.getString("deleted")));
			}
			rs.close();
			rs = null;

			// Retrieve attachments
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_message_attachment"
				+ " WHERE messageNS=" + toSQL(theNamespace);
			if(!withDeletedContent)
				sql += " AND deleted=" + boolToSql(false);
			rs = DBUtils.executeQuery(stmt, sql + " AND ", expr, " ORDER BY attMessage",
				"attMessage", 90);
			msg = null;
			while(rs.next())
			{
				long msgID = rs.getLong("attMessage");
				if(msg == null || msg.getID() != msgID)
					msg = msgsById.get(Long.valueOf(msgID));
				if(msg == null)
					continue;
				Attachment att = new Attachment(msg, rs.getLong("id"), DBUtils.fromSQL(rs
					.getString("attName")), DBUtils.fromSQL(rs.getString("attType")),
					rs.getInt("attLength"), rs.getLong("attCrc"));
				att.setDeleted(DBUtils.boolFromSql(rs.getString("deleted")));
				msg.addAttachment(att);
			}
			rs.close();
			rs = null;

			// Retrieve actions
			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_message_action"
				+ " WHERE messageNS=" + toSQL(theNamespace);
			if(!withDeletedContent)
				sql += " AND deleted=" + boolToSql(false);
			rs = DBUtils.executeQuery(stmt, sql + " AND ", expr, " ORDER BY actionMessage",
				"actionMessage", 90);
			msg = null;
			while(rs.next())
			{
				long msgID = rs.getLong("actionMessage");
				if(msg == null || msg.getID() != msgID)
					msg = msgsById.get(Long.valueOf(msgID));
				if(msg == null)
					continue;
				MessageAction action = new MessageAction(msg);
				java.io.Reader reader = rs.getCharacterStream("actionDescrip");
				StringBuilder descrip = new StringBuilder();
				try
				{
					int read = reader.read();
					while(read >= 0)
					{
						descrip.append((char) read);
						read = reader.read();
					}
				} catch(IOException e)
				{
					log.error("Could not read description of action on message " + msg.getID(), e);
				} finally
				{
					try
					{
						reader.close();
					} catch(IOException e)
					{
						log.error("Connection error", e);
					}
				}
				action.setDescrip(descrip.toString());
				Number userID = (Number) rs.getObject("actionCompleted");
				if(userID != null)
				{
					User user;
					try
					{
						user = theEnv.getUserSource().getUser(userID.longValue());
					} catch(PrismsException e)
					{
						log.error("Could not get user " + userID
							+ " who completed action on message " + msg.getID(), e);
						continue;
					}
					action.setCompletedUser(user);
				}
				msg.addAction(action);
			}
			rs.close();
			rs = null;
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not get messages", e);
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
			if(closeAfter)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}

		for(Message msg : ret)
		{
			if(msg.getSubject() == null)
				msg.setSubject("");
			else
				msg.setSubject(PrismsUtils.decodeUnicode(msg.getSubject()));
			for(MessageAction action : msg.actions())
				action.setDescrip(PrismsUtils.decodeUnicode(action.getDescrip()));
		}
		if(cached.size() == 0 && ret.size() == 1 && withDeletedContent)
		{ // Single item retrieved from DB with deleted content. Cache for later.
			theMessageCache.put(Long.valueOf(ret.get(0).getID()), ret.get(0));
		}
		ret.addAll(cached);
		Long [] idObjs = new Long [ids.length];
		for(int i = 0; i < ids.length; i++)
			idObjs[i] = Long.valueOf(ids[i]);
		final ArrayUtils.ArrayAdjuster<Message, Long, RuntimeException> [] adjuster;
		adjuster = new ArrayUtils.ArrayAdjuster [1];
		adjuster[0] = new ArrayUtils.ArrayAdjuster<Message, Long, RuntimeException>(
			ret.toArray(new Message [ret.size()]), idObjs,
			new ArrayUtils.DifferenceListener<Message, Long>()
			{
				public boolean identity(Message o1, Long o2)
				{
					return o1 != null && o1.getID() == o2.longValue();
				}

				public Message added(Long o, int idx, int retIdx)
				{
					adjuster[0].nullElement();
					return null;
				}

				public Message removed(Message o, int idx, int incMod, int retIdx)
				{
					return o;
				}

				public Message set(Message o1, int idx1, int incMod, Long o2, int idx2, int retIdx)
				{
					return o1;
				}
			});
		return adjuster[0].adjust();
	}

	void cleanDeleted(Message msg)
	{
		for(Recipient rec : msg.recipients())
			if(rec.isDeleted())
				msg.removeReceipient(rec);
		for(Attachment att : msg.attachments())
			if(att.isDeleted())
				msg.removeAttachment(att);
		for(MessageAction action : msg.actions())
			if(action.isDeleted())
				msg.removeAction(action);
	}

	public Message getDBMessage(long id) throws PrismsMessageException
	{
		Statement stmt = null;
		try
		{
			try
			{
				stmt = theTransactor.getConnection().createStatement();
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not create statement", e);
			}
			Message ret = dbGetMessage(id, stmt);
			if(ret != null)
				ret = ret.clone();
			return ret;
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

	public String getContent(Message msg, int length) throws PrismsMessageException
	{
		synchronized(theContentGetter)
		{
			ResultSet rs = null;
			StringBuilder ret = new StringBuilder();
			try
			{
				theContentGetter.setLong(1, msg.getID());
				theContentGetter.setString(2, "C");
				rs = theContentGetter.executeQuery();
				while(rs.next() && ret.length() < length)
				{
					if(ret.length() == 0)
						ret.append(rs.getString("content"));
					else
						ret.append(rs.getString("content").substring(
							ret.length() - rs.getInt("indexNum")));
				}
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not get message content", e);
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
			return PrismsUtils.decodeUnicode(ret.toString());
		}
	}

	private static class BlobInputStream extends java.io.InputStream
	{
		private final Statement theStmt;

		private final ResultSet theRS;

		private final java.sql.Blob theBlob;

		private java.io.InputStream theInput;

		private boolean isClosed;

		BlobInputStream(Statement stmt, ResultSet rs, java.sql.Blob blob)
		{
			theStmt = stmt;
			theRS = rs;
			theBlob = blob;
		}

		@Override
		public int read() throws IOException
		{
			if(theInput == null)
				try
				{
					theInput = theBlob.getBinaryStream();
				} catch(SQLException e)
				{
					log.error("Could not get attachment stream", e);
					throw new IOException("Could not get attachment stream: " + e.getCause());
				}
			return theInput.read();
		}

		@Override
		public void close() throws IOException
		{
			if(isClosed)
				return;
			super.close();
			theInput.close();
			try
			{
				if(PrismsUtils.isJava6())
					try
					{
						theBlob.free();
					} catch(Throwable e)
					{}
				theRS.close();
				theStmt.close();
			} catch(SQLException e)
			{
				log.error("Could not release SQL resources", e);
				throw new IOException("Could not release stream resources: " + e.getMessage());
			}
		}

		@Override
		protected void finalize() throws Throwable
		{
			close();
			super.finalize();
		}
	}

	public java.io.InputStream getAttachmentContent(Attachment attach)
		throws PrismsMessageException
	{
		String sql = "SELECT content from " + theTransactor.getTablePrefix()
			+ "prisms_message_attachment WHERE messageNS=" + toSQL(theNamespace) + " AND id="
			+ attach.getID();
		Statement stmt = null;
		ResultSet rs = null;
		boolean doRelease = true;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				throw new PrismsMessageException("No such attachment with ID " + attach.getID()
					+ " of message " + attach.getMessage().getSubject());
			java.sql.Blob blob = rs.getBlob(1);
			doRelease = false;
			return new BlobInputStream(stmt, rs, blob);
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not get predecessor: SQL=" + sql, e);
		} finally
		{
			if(rs != null && doRelease)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(stmt != null && doRelease)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	public MessageView [] getMessageViews(User viewer, Message... messages)
		throws PrismsMessageException
	{
		if(messages.length == 0)
			return new MessageView [0];
		LongList ids = new LongList(true, true);
		java.util.HashMap<Long, Message> msgsById = new java.util.HashMap<Long, Message>();
		for(Message msg : messages)
		{
			ids.add(msg.getID());
			msgsById.put(Long.valueOf(msg.getID()), msg);
		}
		MessageView [] ret = new MessageView [messages.length];
		DBUtils.KeyExpression keys = DBUtils.simplifyKeySet(ids.toArray(), 90);
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_message_view msg"
			+ " INNER JOIN " + theTransactor.getTablePrefix() + "prisms_conversation_view conv"
			+ " ON msg.messageNS=conv.messageNS AND msg.viewConversation=conv.id"
			+ " WHERE msg.viewUser=" + viewer.getID() + " AND ";
		LongList neededConvs = new LongList(true, true);
		java.util.HashMap<Long, ConversationView> convs = new java.util.HashMap<Long, ConversationView>();
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			java.util.HashMap<Long, MessageView> msgViews = new java.util.HashMap<Long, MessageView>();
			stmt = theTransactor.getConnection().createStatement();
			rs = DBUtils.executeQuery(stmt, sql, keys, "", "msg.viewMsg", 90);
			while(rs.next())
			{
				Long convID = Long.valueOf(rs.getLong("conv.viewConversation"));
				ConversationView conv = convs.get(convID);
				if(conv == null)
				{
					conv = new ConversationView(convID.longValue(), viewer);
					conv.setID(rs.getLong("conv.id"));
					conv.setArchived(DBUtils.boolFromSql(rs.getString("conv.viewArchived")));
					conv.setStarred(DBUtils.boolFromSql(rs.getString("conv.viewStarred")));
					convs.put(convID, conv);
				}
				Long msgID = Long.valueOf(rs.getLong("msg.viewMsg"));
				MessageView msgView = new MessageView(conv, msgsById.get(msgID));
				msgView.setID(rs.getLong("msg.id"));
				msgView.setDeleted(DBUtils.boolFromSql(rs.getString("msg.deleted")));
				msgViews.put(msgID, msgView);
			}
			rs.close();
			rs = null;

			// If not all message views are present, we need to create them (if allowed)
			// First, determine which conversation views still need to be retrieved or created
			for(int i = 0; i < ret.length; i++)
			{
				Long msgID = Long.valueOf(messages[i].getID());
				ret[i] = msgViews.get(msgID);
				if(ret[i] == null && canView(viewer, messages[i]))
				{
					Long convID = Long.valueOf(messages[i].getConversationID());
					if(convs.get(convID) == null)
						neededConvs.add(convID.longValue());
				}
			}
			// Retrieve needed conversation views
			if(!neededConvs.isEmpty())
			{
				keys = DBUtils.simplifyKeySet(neededConvs.toArray(), 90);
				sql = "SELECT * FROM " + theTransactor.getTablePrefix()
					+ "prisms_conversation_view WHERE viewUser=" + viewer.getID() + " AND ";
				rs = DBUtils.executeQuery(stmt, sql, keys, "", "viewConversation", 90);
				while(rs.next())
				{
					ConversationView view = new ConversationView(rs.getLong("viewConversation"),
						viewer);
					view.setID(rs.getLong("id"));
					view.setArchived(DBUtils.boolFromSql(rs.getString("viewArchived")));
					view.setStarred(DBUtils.boolFromSql(rs.getString("viewStarred")));
					convs.put(Long.valueOf(view.getConversationID()), view);
				}
				rs.close();
				rs = null;
			}
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not get message views: SQL=" + sql, e);
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
		User system;
		try
		{
			system = theEnv.getUserSource().getSystemUser();
		} catch(PrismsException e)
		{
			throw new PrismsMessageException("Could not get system user to create message views", e);
		}
		// Create conversation views that do not exist
		for(int i = 0; i < neededConvs.size(); i++)
		{
			Long convID = Long.valueOf(neededConvs.get(i));
			if(convs.get(convID) != null)
				continue;
			ConversationView conv = new ConversationView(convID.longValue(), viewer);
			putConversation(conv, new RecordsTransaction(system));
			convs.put(convID, conv);
		}
		// Should have all needed conversation views
		// Now create new message views that are allowed
		for(int i = 0; i < ret.length; i++)
		{
			if(ret[i] != null || !canView(viewer, messages[i]))
				continue;
			ret[i] = new MessageView(convs.get(Long.valueOf(messages[i].getConversationID())),
				messages[i]);
			putMessageView(ret[i], new RecordsTransaction(system));
		}
		return ret;
	}

	boolean canView(User viewer, Message msg)
	{
		if(msg.getAuthor().equals(viewer))
			return true;
		for(Recipient rec : msg.recipients())
			if(rec.getUser().equals(viewer))
				return true;
		return false;
	}

	public ConversationHolder [] getConversations(long... messageViewIDs)
		throws PrismsMessageException
	{
		LongList mvids = new LongList(true, true);
		mvids.addAll(messageViewIDs);
		DBUtils.KeyExpression key = DBUtils.simplifyKeySet(mvids.toArray(), 90);
		String sql = "SELECT viewConversation FROM " + theTransactor.getTablePrefix()
			+ "prisms_message_view WHERE ";
		Statement stmt = null;
		ResultSet rs = null;
		java.util.HashMap<Long, ConversationHolder> convs = new java.util.HashMap<Long, ConversationHolder>();
		java.util.HashMap<Long, MessageView []> viewsByMsgId = new java.util.HashMap<Long, MessageView []>();
		try
		{
			LongList convIDs = new LongList(true, true);
			rs = DBUtils.executeQuery(stmt, sql, key, "", "id", 90);
			while(rs.next())
				convIDs.add(rs.getLong(1));
			rs.close();
			rs = null;

			sql = "SELECT * FROM " + theTransactor.getTablePrefix() + "prisms_conversation_view"
				+ " conv LEFT JOIN " + theTransactor.getTablePrefix() + "prisms_message_view"
				+ " msgView ON msgView.messageNS=conv.messageNS"
				+ " AND msgView.viewConversation=conv.id WHERE ";
			key = DBUtils.simplifyKeySet(convIDs.toArray(), 90);
			rs = DBUtils.executeQuery(stmt, sql, key, "", "conv.id", 90);
			while(rs.next())
			{
				Long convID = Long.valueOf(rs.getLong("conv.id"));
				ConversationHolder conv = convs.get(convID);
				if(conv == null)
				{
					User viewer;
					try
					{
						viewer = theEnv.getUserSource().getUser(rs.getLong("conv.viewUser"));
					} catch(PrismsException e)
					{
						throw new PrismsMessageException("Could not get viewer for conversation "
							+ convID, e);
					}
					ConversationView cv = new ConversationView(rs.getLong("conv.viewConversation"),
						viewer);
					conv = new ConversationHolder(cv);
					convs.put(convID, conv);
				}
				MessageView msgView = new MessageView(conv.getConversation(), null);
				msgView.setID(rs.getLong("msgView.id"));
				msgView.setDeleted(DBUtils.boolFromSql(rs.getString("msgView.deleted")));
				conv.addMessage(msgView, mvids.contains(msgView.getID()));
				Long msgId = Long.valueOf(rs.getLong("msgView.viewMsg"));
				viewsByMsgId.put(msgId, ArrayUtils.add(viewsByMsgId.get(msgId), msgView));
			}
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not get conversations: SQL=" + sql, e);
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
		LongList msgIds = new LongList(true, true);
		for(Long id : viewsByMsgId.keySet())
			msgIds.add(id.longValue());
		Message [] msgs = getItems(msgIds.toArray());
		for(java.util.Map.Entry<Long, MessageView []> entry : viewsByMsgId.entrySet())
		{
			Message msg = msgs[msgIds.indexOf(entry.getKey().longValue())];
			for(MessageView view : entry.getValue())
				view.setMessage(msg);
		}
		ConversationHolder [] ret = convs.values().toArray(new ConversationHolder [convs.size()]);
		java.util.Arrays.sort(ret, new java.util.Comparator<ConversationHolder>()
		{
			public int compare(ConversationHolder o1, ConversationHolder o2)
			{
				long diff = o1.getLastMessage().getMessage().getTime()
					- o2.getLastMessage().getMessage().getTime();
				return diff > 0 ? 1 : diff < 0 ? -1 : 0;
			}
		});
		return ret;
	}

	void addModification(RecordsTransaction trans, MessageSubjectType subjectType,
		prisms.records.ChangeType changeType, int add, Object majorSubject, Object minorSubject,
		Object previousValue, Object data1, Object data2) throws PrismsMessageException
	{
		if(trans == null || theRecordKeeper == null)
			return;
		if(!trans.shouldRecord())
			return;
		prisms.records.ChangeRecord record;
		try
		{
			record = theRecordKeeper.persist(trans, subjectType, changeType, add, majorSubject,
				minorSubject, previousValue, data1, data2);
		} catch(prisms.records.PrismsRecordException e)
		{
			throw new PrismsMessageException("Could not persist change record", e);
		}
		if(trans.getRecord() != null)
		{
			try
			{
				theRecordKeeper.associate(record, trans.getRecord(), false);
			} catch(prisms.records.PrismsRecordException e)
			{
				log.error("Could not associate change record with sync record", e);
			}
		}
	}

	public void putMessage(final Message message, final RecordsTransaction trans)
		throws PrismsMessageException
	{
		theTransactor.performTransaction(
			new Transactor.TransactionOperation<PrismsMessageException>()
			{
				public Object run(Statement stmt) throws PrismsMessageException
				{
					Message dbHeader = dbGetMessage(message.getID(), stmt);
					if(dbHeader == null)
						dbInsertMessage(message, stmt, trans);
					else
						dbUpdateMessage(dbHeader, message, stmt, trans);
					return null;
				}
			}, "Could not add/modify message " + message.getSubject());
	}

	public void putMessageView(final MessageView msg, final RecordsTransaction trans)
		throws PrismsMessageException
	{
		theTransactor.performTransaction(
			new Transactor.TransactionOperation<PrismsMessageException>()
			{
				public Object run(Statement stmt) throws PrismsMessageException
				{
					String sql = null;
					ResultSet rs = null;
					try
					{
						sql = "SELECT deleted FROM " + theTransactor.getTablePrefix()
							+ "prisms_message_view WHERE messageNS=" + toSQL(theNamespace)
							+ " AND id=" + msg.getID();
						rs = stmt.executeQuery(sql);
						Boolean deleted = rs.next() ? Boolean.valueOf(DBUtils.boolFromSql(rs
							.getString(1))) : null;
						rs.close();
						rs = null;
						if(deleted == null)
						{
							sql = "INSERT INTO " + theTransactor.getTablePrefix()
								+ "prisms_message_view (messageNS, id, viewMsg, viewUser,"
								+ " viewConversation, deleted) VALUES(" + toSQL(theNamespace)
								+ ", " + msg.getID() + ", " + msg.getMessage().getID() + ", "
								+ msg.getConversation().getViewer().getID()
								+ msg.getConversation().getID() + ", " + boolToSql(msg.isDeleted())
								+ ")";
							stmt.executeUpdate(sql);
							addModification(trans, MessageSubjectType.messageView, null, 1, msg,
								null, null, msg.getMessage(), msg.getConversation());
							if(msg.isDeleted())
								addModification(trans, MessageSubjectType.messageView, null, -1,
									msg, null, null, msg.getMessage(), msg.getConversation());
						}
						else if(deleted.booleanValue() != msg.isDeleted())
						{
							sql = "UPDATE " + theTransactor.getTablePrefix()
								+ "prisms_message_view SET deleted=" + boolToSql(msg.isDeleted())
								+ " WHERE messageNS=" + toSQL(theNamespace) + " AND id="
								+ msg.getID();
							stmt.executeUpdate(sql);
							addModification(trans, MessageSubjectType.messageView, null,
								msg.isDeleted() ? -1 : 1, msg, null, null, msg.getMessage(),
								msg.getConversation());
						}
					} catch(SQLException e)
					{
						throw new PrismsMessageException("Could not update user "
							+ msg.getConversation().getViewer() + "'s view of message "
							+ msg.getMessage().getSubject() + ": SQL=" + sql, e);
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
					return null;
				}
			}, "Could not persist user " + msg.getConversation().getViewer()
				+ "'s view of message " + msg.getMessage().getSubject());
	}

	public void putConversation(final ConversationView view, final RecordsTransaction trans)
		throws PrismsMessageException
	{
		theTransactor.performTransaction(
			new Transactor.TransactionOperation<PrismsMessageException>()
			{
				public Object run(Statement stmt) throws PrismsMessageException
				{
					String sql = null;
					ResultSet rs = null;
					try
					{
						sql = "SELECT viewArchived, viewStarred FROM "
							+ theTransactor.getTablePrefix()
							+ "prisms_message_view WHERE messageNS=" + toSQL(theNamespace)
							+ " AND id=" + view.getID();
						rs = stmt.executeQuery(sql);
						boolean hasEntry = rs.next();
						boolean archived = hasEntry ? DBUtils.boolFromSql(rs
							.getString("viewArchived")) : false;
						boolean starred = hasEntry ? DBUtils.boolFromSql(rs
							.getString("viewStarred")) : false;
						rs.close();
						rs = null;
						if(!hasEntry)
						{
							sql = "INSERT INTO " + theTransactor.getTablePrefix()
								+ "prisms_conversation_view (messageNS, id, viewConversation,"
								+ " viewUser, viewArchived, viewStarred) VALUES("
								+ toSQL(theNamespace) + ", " + view.getID() + ", "
								+ view.getConversationID() + ", " + view.getViewer().getID() + ", "
								+ boolToSql(view.isArchived()) + ", " + boolToSql(view.isStarred())
								+ ")";
							stmt.executeUpdate(sql);
							addModification(trans, MessageSubjectType.conversationView, null, 1,
								view, null, null, view.getViewer(),
								Long.valueOf(view.getConversationID()));
						}
						else if(archived != view.isArchived() || starred != view.isStarred())
						{
							sql = "UPDATE " + theTransactor.getTablePrefix()
								+ "prisms_conversation_view SET viewArchived="
								+ boolToSql(view.isArchived()) + ", viewStarred="
								+ boolToSql(view.isStarred()) + " WHERE messageNS="
								+ toSQL(theNamespace) + " AND id=" + view.getID();
							stmt.executeUpdate(sql);
							if(archived != view.isArchived())
								addModification(trans, MessageSubjectType.conversationView,
									ConversationViewChange.archived, 0, view, null,
									Boolean.valueOf(archived), view.getViewer(),
									Long.valueOf(view.getConversationID()));
							if(starred != view.isStarred())
								addModification(trans, MessageSubjectType.conversationView,
									ConversationViewChange.starred, 0, view, null,
									Boolean.valueOf(starred), view.getViewer(),
									Long.valueOf(view.getConversationID()));
						}
					} catch(SQLException e)
					{
						throw new PrismsMessageException("Could not update user "
							+ view.getViewer() + "'s view of conversation with ID "
							+ view.getConversationID() + ": SQL=" + sql, e);
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
					return null;
				}
			}, "Could not persist user " + view.getViewer() + "'s view of conversation with ID "
				+ view.getConversationID());
	}

	static class BlobInsertInputStream extends java.io.InputStream
	{
		private final java.io.InputStream theData;

		private final java.util.zip.CRC32 theCRC;

		private long theLength;

		BlobInsertInputStream(java.io.InputStream data)
		{
			theData = data;
			theCRC = new java.util.zip.CRC32();
		}

		@Override
		public int read() throws IOException
		{
			int ret = theData.read();
			if(ret >= 0)
			{
				theCRC.update(ret);
				theLength++;
			}
			return ret;
		}

		@Override
		public int read(byte [] b) throws IOException
		{
			int ret = super.read(b);
			if(ret > 0)
			{
				theCRC.update(b, 0, ret);
				theLength += ret;
			}
			return ret;
		}

		@Override
		public int read(byte [] b, int off, int len) throws IOException
		{
			int ret = super.read(b, off, len);
			if(ret > 0)
			{
				theCRC.update(b, off, ret);
				theLength += ret;
			}
			return ret;
		}

		public long getCRC()
		{
			return theCRC.getValue();
		}

		public long getLength()
		{
			return theLength;
		}
	}

	public Attachment createAttachment(final Message message, final String name, final String type,
		final java.io.InputStream data, final RecordsTransaction trans)
		throws PrismsMessageException, IOException
	{
		return (Attachment) theTransactor.performTransaction(
			new TransactionOperation<PrismsMessageException>()
			{
				public Object run(Statement stmt) throws PrismsMessageException
				{
					long id;
					try
					{
						id = theEnv.getIDs().getNextID("prisms_message_attachment", "id", stmt,
							theTransactor.getTablePrefix(), "messageNS=" + toSQL(theNamespace));
					} catch(PrismsException e)
					{
						throw new PrismsMessageException("Could not get next attachment ID", e);
					}
					String sql = "INSERT INTO " + theTransactor.getTablePrefix()
						+ "prisms_message_attachment (messageNS, id, attMessage, attName, attType,"
						+ " attLength, attCrc, attContent, deleted) VALUES (" + toSQL(theNamespace)
						+ ", " + id + ", " + message.getID() + ", " + toSQL(name) + ", "
						+ toSQL(type) + ", 0, 0, ?, " + boolToSql(false) + ")";
					BlobInsertInputStream blobInput = new BlobInsertInputStream(data);
					java.sql.PreparedStatement ps = null;
					try
					{
						ps = theTransactor.getConnection().prepareStatement(sql);
						DBUtils.setBlob(ps, 1, blobInput);
						ps.executeUpdate();
					} catch(SQLException e)
					{
						throw new PrismsMessageException("Could not insert attachment " + name
							+ " for message " + message.getSubject() + ": SQL=" + sql, e);
					} finally
					{
						try
						{
							if(ps != null)
								ps.close();
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
					Attachment ret = new Attachment(message, id, name, type, blobInput.getLength(),
						blobInput.getCRC());
					addModification(trans, MessageSubjectType.attachment, null, 1, ret, null, null,
						ret.getMessage(), null);
					message.addAttachment(ret);
					sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_message_attachment"
						+ " SET attLength=" + blobInput.getLength() + ", attCrc="
						+ blobInput.getCRC() + " WHERE messageNS=" + toSQL(theNamespace)
						+ " AND id=" + id;
					try
					{
						stmt.executeUpdate(sql);
					} catch(SQLException e)
					{
						throw new PrismsMessageException(
							"Could not update metadata for attachment " + name + " in message "
								+ message.getSubject() + ": SQL=" + sql, e);
					}
					return ret;
				}
			}, "Could not add attachment to  message " + message.getSubject());
	}

	Message dbGetMessage(long id, Statement stmt) throws PrismsMessageException
	{
		return getMessages(stmt, true, id)[0];
	}

	void dbInsertMessage(Message message, Statement stmt, RecordsTransaction trans)
		throws PrismsMessageException
	{
		String sql = null;
		ResultSet rs = null;
		String subject = PrismsUtils.encodeUnicode(message.getSubject());
		try
		{
			if(message.getConversationID() >= 0)
			{
				sql = "SELECT id FROM " + theTransactor.getTablePrefix() + "prisms_conversation"
					+ " WHERE messageNS=" + toSQL(theNamespace) + " AND id="
					+ message.getConversationID();
				rs = stmt.executeQuery(sql);
				boolean hasConv = rs.next();
				rs.close();
				rs = null;
				if(!hasConv)
				{
					sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_conversation"
						+ " (messageNS, id) VALUES (" + toSQL(theNamespace) + ", "
						+ message.getConversationID();
					stmt.executeUpdate(sql);
				}
			}
			else if(message.getPredecessorID() >= 0)
			{
				sql = "SELECT msgConversation FROM " + theTransactor.getTablePrefix()
					+ "prisms_message WHERE messageNS=" + toSQL(theNamespace) + " AND id="
					+ message.getPredecessorID();
				rs = stmt.executeQuery(sql);
				Number conv = null;
				if(rs.next())
					conv = (Number) rs.getObject(1);
				else
					throw new PrismsMessageException("Predecessor " + message.getPredecessorID()
						+ " does not exist");
				if(conv != null)
					message.setConversationID(conv.longValue());
			}

			if(message.getConversationID() < 0)
			{
				try
				{
					message.setConversationID(theEnv.getIDs().getNextID("prisms_conversation",
						"id", stmt, theTransactor.getTablePrefix(),
						"messageNS=" + toSQL(theNamespace)));
				} catch(PrismsException e)
				{
					throw new PrismsMessageException("Could not generate new conversation ID", e);
				}
			}
			if(message.getID() < 0)
			{
				try
				{
					message.setID(theEnv.getIDs().getNextID("prisms_message", "id", stmt,
						theTransactor.getTablePrefix(), "messageNS=" + toSQL(theNamespace)));
				} catch(PrismsException e)
				{
					throw new PrismsMessageException("Could not generate new message ID", e);
				}
			}
			sql = "INSERT INTO " + theTransactor.getTablePrefix()
				+ "prisms_message (messageNS, id,"
				+ " msgConversation, msgAuthor, msgTime, msgSent, msgPriority, msgPredecessor,"
				+ " msgShortSubject, msgContentLength, msgContentCrc, msgSize, deleted) VALUES ("
				+ toSQL(theNamespace) + ", " + message.getID() + ", " + message.getConversationID()
				+ ", " + message.getAuthor().getID() + ", " + message.getTime() + ", "
				+ boolToSql(message.isSent()) + ", " + message.getPriority().ordinal() + ", ";
			sql = (message.getPredecessorID() < 0 ? "NULL" : "" + message.getPredecessorID())
				+ ", ";
			if(subject.length() <= 100)
				sql += toSQL(subject);
			else
				sql += "NULL";
			sql += ", " + message.getContent(-1).length() + ", " + message.getContentCRC() + ", "
				+ getMessageSize(message) + ")";
			stmt.executeUpdate(sql);
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could insert message: SQL=" + sql, e);
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
		addModification(trans, MessageSubjectType.message, null, 1, message, null, null, null, null);

		for(Recipient r : message.recipients())
			insertReceipt(r, stmt, null);
		for(MessageAction a : message.actions())
			insertAction(a, stmt, null);
		if(subject.length() > 100)
			insertContent(message, "S");
		insertContent(message, "M");
	}

	private int getMS(Message header)
	{
		int ret = 1;
		if(header.getSubject().length() > 100)
		{
			ret++;
			int subSize = header.getSubject().length();
			subSize -= CONTENT_LENGTH;
			ret += Math.ceil(subSize * 1.0f / (CONTENT_LENGTH - CONTENT_OVERLAP));
		}
		ret += header.getReceiptCount();
		for(Attachment a : header.attachments())
			ret += 1 + a.getLength() / 1024;
		for(MessageAction a : header.actions())
			ret += 1 + a.getDescrip().length() / 1024;
		return ret;
	}

	int getMessageSize(Message message)
	{
		int ret = getMS(message);
		int contSize = message.getLength();
		if(contSize > 0)
		{
			ret++;
			contSize -= CONTENT_LENGTH;
			ret += Math.ceil(contSize * 1.0f / (CONTENT_LENGTH - CONTENT_OVERLAP));
		}
		return ret;
	}

	void insertReceipt(Recipient receipt, Statement stmt, RecordsTransaction trans)
		throws PrismsMessageException
	{
		if(receipt.getID() < 0)
			try
			{
				receipt.setID(theEnv.getIDs().getNextID("prisms_message_recipient", "id", stmt,
					theTransactor.getTablePrefix(), "messageNS=" + toSQL(theNamespace)));
			} catch(PrismsException e)
			{
				throw new PrismsMessageException("Could not generate new recipient ID", e);
			}
		String sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_message_recipient"
			+ " (messageNS, id, rcptMessage, rcptUser, rcptType, firstViewed, lastViewed, deleted)"
			+ " VALUES (" + toSQL(theNamespace) + receipt.getID() + ", "
			+ receipt.getMessage().getID() + ", " + receipt.getUser().getID() + ", "
			+ receipt.getApplicability().ordinal() + ", ";
		sql += DBUtils.formatDate(receipt.getFirstViewed(), isOracle()) + ", ";
		sql += DBUtils.formatDate(receipt.getLastViewed(), isOracle()) + ", ";
		sql += boolToSql(receipt.isDeleted()) + ")";
		try
		{
			stmt.executeUpdate(sql);
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not insert receipt " + receipt
				+ " for message " + receipt.getMessage().getSubject() + ": SQL=" + sql, e);
		}
		addModification(trans, MessageSubjectType.recipient, null, 1, receipt, null, null,
			receipt.getMessage(), null);
	}

	void insertAction(MessageAction action, Statement stmt, RecordsTransaction trans)
		throws PrismsMessageException
	{
		if(action.getID() < 0)
			try
			{
				action.setID(theEnv.getIDs().getNextID("prisms_message_action", "id", stmt,
					theTransactor.getTablePrefix(), "messageNS=" + toSQL(theNamespace)));
			} catch(PrismsException e)
			{
				throw new PrismsMessageException("Could not generate new action ID", e);
			}
		synchronized(theActionInserter)
		{
			try
			{
				theActionInserter.setLong(1, action.getID());
				theActionInserter.setLong(2, action.getMessage().getID());
				DBUtils
					.setClob(theActionInserter, 3, new java.io.StringReader(action.getDescrip()));
				if(action.getCompletedUser() != null)
					theActionInserter.setLong(4, action.getCompletedUser().getID());
				else
					theActionInserter.setNull(4, java.sql.Types.NUMERIC);
				theActionInserter.setString(5, action.isDeleted() ? "t" : "f");
				theActionInserter.executeUpdate();
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not insert action", e);
			}
		}
	}

	void insertContent(Message message, String type) throws PrismsMessageException
	{
		String content;
		if(type.equals("S"))
			content = message.getSubject();
		else
			content = message.getContent(-1);
		try
		{
			synchronized(theContentInserter)
			{
				theContentInserter.setLong(1, message.getID());
				theContentInserter.setString(2, type);
				if(content.length() <= CONTENT_LENGTH)
				{
					theContentInserter.setInt(2, 0);
					theContentInserter.setString(4, content);
					theContentInserter.executeUpdate();
				}
				else
				{
					int inc = CONTENT_LENGTH - CONTENT_OVERLAP;
					for(int i = 0; i < content.length(); i += inc)
					{
						int end = i + CONTENT_LENGTH;
						int diff = end - content.length();
						if(diff > 0)
						{
							end = content.length();
							i -= diff;
						}
						theContentInserter.setInt(2, i);
						theContentInserter.setString(3, content.substring(i, end));
						theContentInserter.executeUpdate();
						if(diff >= 0)
							break;
					}
				}
			}
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not insert message content", e);
		}
	}

	void updateContent(long messageID, String contentType, String dbContent, String content)
		throws PrismsMessageException
	{
		// Get the earliest index at which the two contents differ
		int idx = 0;
		for(idx = 0; idx < dbContent.length() && idx < content.length()
			&& dbContent.charAt(idx) == content.charAt(idx); idx++);
		int startIdx = CONTENT_LENGTH;
		if(startIdx > idx)
			startIdx = 0;
		else
		{
			while(startIdx < idx)
				startIdx += CONTENT_LENGTH - CONTENT_OVERLAP;
			if(startIdx > idx)
				startIdx -= CONTENT_LENGTH - CONTENT_OVERLAP;
		}
		if(idx == dbContent.length() && idx == content.length())
			return; // No difference
		if(startIdx < idx)
		{// The last content entry is not full, so we need to update it
			int end = startIdx + CONTENT_LENGTH;
			if(end > content.length())
				end = content.length();
			synchronized(theContentUpdater)
			{
				try
				{
					theContentUpdater.setString(1, content.substring(startIdx, end));
					theContentUpdater.setLong(2, messageID);
					theContentUpdater.setString(3, contentType);
					theContentUpdater.setInt(4, startIdx);
					if(theContentUpdater.executeUpdate() == 0)
						throw new PrismsMessageException("Content updating failed");
				} catch(SQLException e)
				{
					throw new PrismsMessageException("Could not update content", e);
				}
			}
			startIdx += CONTENT_LENGTH - CONTENT_OVERLAP;
		}
		if(startIdx < dbContent.length())
		{ // There are more entries of old content that need to be removed
			synchronized(theContentDeleter)
			{
				try
				{
					theContentDeleter.setLong(1, messageID);
					theContentDeleter.setString(2, contentType);
					theContentDeleter.setInt(3, startIdx);
					theContentDeleter.executeUpdate();
				} catch(SQLException e)
				{
					throw new PrismsMessageException("Could not update content", e);
				}
			}
		}
		if(startIdx < content.length())
		{ // New entries need to be added
			synchronized(theContentInserter)
			{
				while(startIdx < content.length())
				{
					try
					{
						theContentInserter.setLong(1, messageID);
						theContentInserter.setString(2, contentType);
						theContentInserter.setInt(3, startIdx);
						int end = startIdx + CONTENT_LENGTH - CONTENT_OVERLAP;
						int diff = end - content.length();
						if(diff > 0)
						{
							end = content.length();
							startIdx -= diff;
						}
						theContentInserter.setString(4, content.substring(startIdx, end));
						theContentInserter.executeUpdate();
					} catch(SQLException e)
					{
						throw new PrismsMessageException("Could not update content", e);
					}
				}
			}
		}
	}

	void dbUpdateMessage(final Message dbMessage, final Message message, final Statement stmt,
		final RecordsTransaction trans) throws PrismsMessageException
	{
		final String [] status = new String [] {"Modified message " + dbMessage.getSubject()
			+ ":\n\t"};
		int oStatLength = status[0].length();
		String sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_message SET ";
		boolean modified = false;
		if(dbMessage.getTime() != message.getTime())
		{
			modified = true;
			status[0] += "Message time changed from " + PrismsUtils.print(dbMessage.getTime())
				+ " to " + PrismsUtils.print(message.getTime()) + "\n\t";
			sql += "messageTime=" + message.getTime() + ", ";
			addModification(trans, MessageSubjectType.message, MessageChange.time, 0, dbMessage,
				null, Long.valueOf(dbMessage.getTime()), null, null);
			dbMessage.setTime(message.getTime());
		}
		if(dbMessage.isSent() != message.isSent())
		{
			modified = true;
			status[0] += "Message " + (message.isSent() ? "sent" : "unsent") + "\n\t";
			sql += "sent=" + boolToSql(message.isSent()) + ", ";
			addModification(trans, MessageSubjectType.message, MessageChange.sent, 0, dbMessage,
				null, Boolean.valueOf(dbMessage.isSent()), null, null);
			dbMessage.setSent(message.isSent());
		}
		if(dbMessage.getPriority() != message.getPriority())
		{
			modified = true;
			status[0] += "Priority changed from " + dbMessage.getPriority() + " to "
				+ message.getPriority() + "\n\t";
			sql += "priority=" + message.getPriority().ordinal() + ", ";
			addModification(trans, MessageSubjectType.message, MessageChange.priority, 0,
				dbMessage, null, dbMessage.getPriority(), null, null);
			dbMessage.setPriority(message.getPriority());
		}
		if(!dbMessage.getSubject().equals(message.getSubject()))
		{
			modified = true;
			status[0] += "Subject changed from " + dbMessage.getSubject() + " to "
				+ message.getSubject() + "\n\t";
			String dbSubject = PrismsUtils.encodeUnicode(dbMessage.getSubject());
			String subject = PrismsUtils.encodeUnicode(message.getSubject());
			if(dbSubject.length() > 100 && subject.length() > 100)
				updateContent(dbMessage.getID(), "S", dbSubject, subject);
			else
			{
				if(dbSubject.length() > 100)
					synchronized(theContentDeleter)
					{
						try
						{
							theContentDeleter.setLong(1, dbMessage.getID());
							theContentDeleter.setString(2, "S");
							theContentDeleter.setInt(3, 0);
							theContentDeleter.executeUpdate();
						} catch(SQLException e)
						{
							throw new PrismsMessageException("Could not update subject of message "
								+ message, e);
						}
					}
				if(subject.length() > 100)
				{
					sql += "subject=NULL, ";
					insertContent(message, "S");
				}
				else
					sql += "subject=" + toSQL(message.getSubject()) + ", ";
			}
			addModification(trans, MessageSubjectType.message, MessageChange.subject, 0, dbMessage,
				null, dbMessage.getSubject(), null, null);
			dbMessage.setSubject(message.getSubject());
		}
		if(dbMessage.getPredecessorID() != message.getPredecessorID())
		{
			modified = true;
			Message pred = null;
			if(dbMessage.getPredecessorID() >= 0)
			{
				String subSql = "SELECT deleted FROM " + theTransactor.getTablePrefix()
					+ "prisms_message WHERE messageNS=" + toSQL(theNamespace) + " AND id="
					+ dbMessage.getPredecessorID();
				ResultSet rs = null;
				try
				{
					rs = stmt.executeQuery(subSql);
					if(!rs.next())
						throw new PrismsMessageException("No such predecessor with ID "
							+ dbMessage.getPredecessorID() + " for message "
							+ dbMessage.getSubject());
				} catch(SQLException e)
				{
					throw new PrismsMessageException(
						"Could not check for existence of predecessor with ID "
							+ dbMessage.getPredecessorID(), e);
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
			if(message.getPredecessorID() >= 0)
			{
				String subSql = "SELECT deleted FROM " + theTransactor.getTablePrefix()
					+ "prisms_message WHERE messageNS=" + toSQL(theNamespace) + " AND id="
					+ message.getPredecessorID();
				ResultSet rs = null;
				try
				{
					rs = stmt.executeQuery(subSql);
					if(!rs.next())
						throw new PrismsMessageException("No such predecessor with ID "
							+ message.getPredecessorID() + " for message " + message.getSubject());
					if(DBUtils.boolFromSql(rs.getString(1)))
						throw new PrismsMessageException("Predecessor with ID "
							+ message.getPredecessorID() + " for message " + message.getSubject()
							+ " has been deleted");
				} catch(SQLException e)
				{
					throw new PrismsMessageException(
						"Could not check for existence of predecessor with ID "
							+ message.getPredecessorID(), e);
				}
			}
			status[0] += "Predecessor changed from " + dbMessage.getPredecessorID() + " to "
				+ message.getPredecessorID() + "\n\t";
			sql += "predecessor="
				+ (message.getPredecessorID() < 0 ? "NULL" : "" + message.getPredecessorID())
				+ ", ";
			addModification(trans, MessageSubjectType.message, MessageChange.predecessor, 0,
				dbMessage, null, pred, null, null);
			dbMessage.setPredecessorID(message.getPredecessorID());
		}
		if(dbMessage.getContentCRC() != message.getContentCRC()
			|| dbMessage.getLength() != message.getLength())
		{
			modified = true;
			status[0] += "Content changed from " + dbMessage.getLength() + " to "
				+ message.getLength() + " characters\n\t";
			sql += "msgContentLength=" + message.getLength() + ", msgContentCrc="
				+ message.getContentCRC() + ", ";
			String dbContent = dbMessage.getContent(-1);
			String content = message.getContent(-1);
			updateContent(dbMessage.getID(), "C", dbContent, content);
			addModification(trans, MessageSubjectType.message, MessageChange.content, 0, dbMessage,
				null, null, null, null);
			dbMessage.setContent(message.getContent(-1));
		}

		long dbSize = getMessageSize(dbMessage, stmt);
		long newSize = getMessageSize(message);
		if(dbSize != newSize)
		{
			status[0] += "Size changed from " + dbSize + " to " + newSize + "\n\t";
			sql += "msgSize=" + newSize + ", ";
		}

		if(modified)
		{
			sql = sql.substring(0, sql.length() - 2);
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not update message "
					+ dbMessage.getSubject() + ": SQL=" + sql, e);
			}
		}

		Recipient [] dbReceipts = new Recipient [dbMessage.getReceiptCount()];
		int r = 0;
		for(Recipient rec : dbMessage.recipients())
			dbReceipts[r++] = rec;
		Recipient [] receipts = new Recipient [message.getReceiptCount()];
		r = 0;
		for(Recipient rec : message.recipients())
			receipts[r++] = rec;
		ArrayUtils.adjust(dbReceipts, receipts,
			new ArrayUtils.DifferenceListener<Recipient, Recipient>()
			{
				public boolean identity(Recipient o1, Recipient o2)
				{
					return o1.equals(o2);
				}

				public Recipient added(Recipient o, int mIdx, int retIdx)
				{
					if(!o.isDeleted())
						status[0] += "Added recipient " + o + "\n\t";
					try
					{
						insertReceipt(o, stmt, trans);
						addModification(trans, MessageSubjectType.recipient, null, 1, o, null,
							null, o.getMessage(), null);
						if(o.isDeleted())
							addModification(trans, MessageSubjectType.recipient, null, -1, o, null,
								null, o.getMessage(), null);
					} catch(PrismsMessageException e)
					{
						log.error(e.getMessage(), e);
					}
					Recipient newRec = dbMessage.addRecipient(o.getUser());
					newRec.setID(o.getID());
					newRec.setApplicability(o.getApplicability());
					newRec.setFirstViewed(o.getFirstViewed());
					newRec.setLastViewed(o.getLastViewed());
					newRec.setDeleted(o.isDeleted());
					return o;
				}

				public Recipient removed(Recipient o, int oIdx, int incMod, int retIdx)
				{
					if(!o.isDeleted())
					{
						status[0] += "Removed recipient " + o + "\n\t";
						String sql2 = "UPDATE " + theTransactor.getTablePrefix()
							+ "prisms_message_recipient SET deleted=" + boolToSql(true)
							+ " WHERE messageNS=" + toSQL(theNamespace) + " AND id=" + o.getID();
						try
						{
							stmt.executeUpdate(sql2);
							addModification(trans, MessageSubjectType.recipient, null, -1, o, null,
								null, o.getMessage(), null);
						} catch(SQLException e)
						{
							log.error("Could not delete recipient " + o + " of message "
								+ o.getMessage().getSubject() + ": SQL=" + sql2, e);
						} catch(PrismsMessageException e)
						{
							log.error("Could not write record for recipient deletion", e);
						}
						o.setDeleted(true);
					}
					return o;
				}

				public Recipient set(Recipient o1, int idx1, int incMod, Recipient o2, int idx2,
					int retIdx)
				{
					try
					{
						dbUpdateReceipt(o1, o2, stmt, trans, status);
					} catch(PrismsMessageException e)
					{
						log.error(e.getMessage(), e);
					}
					return o1;
				}
			});

		Attachment [] dbAttaches = new Attachment [dbMessage.getAttachmentCount()];
		int a = 0;
		for(Attachment att : dbMessage.attachments())
			dbAttaches[a++] = att;
		Attachment [] attaches = new Attachment [message.getAttachmentCount()];
		a = 0;
		for(Attachment att : message.attachments())
			attaches[a++] = att;
		ArrayUtils.adjust(dbAttaches, attaches,
			new ArrayUtils.DifferenceListener<Attachment, Attachment>()
			{
				public boolean identity(Attachment o1, Attachment o2)
				{
					return o1.equals(o2);
				}

				public Attachment added(Attachment o, int mIdx, int retIdx)
				{
					log.error("Attachment added by another method than MessageManager.createAttachment!");
					return null;
				}

				public Attachment removed(Attachment o, int oIdx, int incMod, int retIdx)
				{
					if(!o.isDeleted())
					{
						status[0] += "Attachment " + o.getName() + " removed\n\t";
						String sql2 = "UPDATE " + theTransactor.getTablePrefix()
							+ "prisms_message_attachment SET deleted=" + boolToSql(true)
							+ " WHERE id=" + o.getID();
						try
						{
							stmt.executeUpdate(sql2);
							addModification(trans, MessageSubjectType.attachment, null, -1, o,
								null, null, o.getMessage(), null);
						} catch(SQLException e)
						{
							log.error("Could not delete attachment", e);
						} catch(PrismsMessageException e)
						{
							log.error("Could not write record for attachment deletion", e);
						}
						o.setDeleted(true);
					}
					return o;
				}

				public Attachment set(Attachment o1, int idx1, int incMod, Attachment o2, int idx2,
					int retIdx)
				{
					try
					{
						dbUpdateAttachment(o1, o2, stmt, trans, status);
					} catch(PrismsMessageException e)
					{
						log.error(e.getMessage(), e);
					}
					return o1;
				}
			});

		MessageAction [] dbActions = new MessageAction [dbMessage.getActionCount()];
		a = 0;
		for(MessageAction action : dbMessage.actions())
			dbActions[a++] = action;
		MessageAction [] actions = new MessageAction [message.getActionCount()];
		a = 0;
		for(MessageAction action : message.actions())
			actions[a++] = action;
		ArrayUtils.adjust(dbActions, actions,
			new ArrayUtils.DifferenceListener<MessageAction, MessageAction>()
			{
				public boolean identity(MessageAction o1, MessageAction o2)
				{
					return o1.equals(o2);
				}

				public MessageAction added(MessageAction o, int mIdx, int retIdx)
				{
					try
					{
						insertAction(o, stmt, trans);
					} catch(PrismsMessageException e)
					{
						log.error("Could not add action", e);
						return null;
					}
					MessageAction newAction = new MessageAction(dbMessage);
					dbMessage.addAction(newAction);
					newAction.setID(o.getID());
					newAction.setDescrip(o.getDescrip());
					newAction.setCompletedUser(o.getCompletedUser());
					try
					{
						addModification(trans, MessageSubjectType.action, null, 1, o, null, null,
							o.getMessage(), null);
						if(o.isDeleted())
							addModification(trans, MessageSubjectType.action, null, -11, o, null,
								null, o.getMessage(), null);
					} catch(PrismsMessageException e)
					{
						log.error("Could not write record for action creation", e);
					}
					return newAction;
				}

				public MessageAction removed(MessageAction o, int oIdx, int incMod, int retIdx)
				{
					if(!o.isDeleted())
					{
						status[0] += "Action " + o.getDescrip() + " removed\n\t";
						String sql2 = "UPDATE " + theTransactor.getTablePrefix()
							+ "prisms_message_action SET deleted=" + boolToSql(true) + " WHERE id="
							+ o.getID();
						try
						{
							stmt.executeUpdate(sql2);
							addModification(trans, MessageSubjectType.action, null, -11, o, null,
								null, o.getMessage(), null);
						} catch(SQLException e)
						{
							log.error("Could not delete attachment", e);
						} catch(PrismsMessageException e)
						{
							log.error("Could not write record for action deletion", e);
						}
						o.setDeleted(true);
					}
					return o;
				}

				public MessageAction set(MessageAction o1, int idx1, int incMod, MessageAction o2,
					int idx2, int retIdx)
				{
					try
					{
						dbUpdateAction(o1, o2, stmt, trans, status);
					} catch(PrismsMessageException e)
					{
						log.error(e.getMessage(), e);
					}
					return o1;
				}
			});

		if(status[0].length() != oStatLength)
			log.debug(status[0].substring(0, status[0].length() - 2));
	}

	long getMessageSize(Message header, Statement stmt) throws PrismsMessageException
	{
		ResultSet rs = null;
		String sql = "SELECT msgSize FROM " + theTransactor.getTablePrefix()
			+ "prisms_message WHERE messageNS=" + toSQL(theNamespace) + " AND id=" + header.getID();
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				throw new PrismsMessageException("No such message with ID " + header.getID());
			return rs.getLong(1);
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not query message size: SQL=" + sql, e);
		}
	}

	void dbUpdateReceipt(Recipient dbReceipt, Recipient receipt, Statement stmt,
		RecordsTransaction trans, String [] status) throws PrismsMessageException
	{
		String sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_message_recipient SET ";
		boolean modified = false;
		if(dbReceipt.isDeleted() != receipt.isDeleted())
		{
			modified = true;
			if(receipt.isDeleted())
				status[0] += "Recipient " + receipt + " deleted\n\t";
			else
				status[0] += "Recipient " + receipt + " re-added\n\t";
			sql += "deleted=" + boolToSql(receipt.isDeleted()) + ", ";
			addModification(trans, MessageSubjectType.recipient, null,
				receipt.isDeleted() ? -1 : 1, dbReceipt, null, null, dbReceipt.getMessage(), null);
			dbReceipt.setDeleted(receipt.isDeleted());
		}
		if(dbReceipt.getApplicability() != receipt.getApplicability())
		{
			modified = true;
			status[0] += "Applicability of recipient " + receipt + " changed to "
				+ receipt.getApplicability() + "\n\t";
			sql += "applicability=" + receipt.getApplicability().ordinal() + ", ";
			addModification(trans, MessageSubjectType.recipient, RecipientChange.applicability, 0,
				receipt, null, dbReceipt.getApplicability(), dbReceipt.getMessage(), null);
			dbReceipt.setApplicability(receipt.getApplicability());
		}
		if(dbReceipt.getFirstViewed() != receipt.getFirstViewed())
		{
			modified = true;
			status[0] += "Recipient " + receipt + " first view changed from ";
			status[0] += (dbReceipt.getFirstViewed() >= 0 ? PrismsUtils.print(dbReceipt
				.getFirstViewed()) : "none");
			status[0] += " to ";
			status[0] += (receipt.getFirstViewed() >= 0 ? PrismsUtils.print(receipt
				.getFirstViewed()) : "none") + "\n\t";
			sql += "firstView=" + DBUtils.formatDate(receipt.getFirstViewed(), isOracle()) + ", ";
			addModification(trans, MessageSubjectType.messageView, RecipientChange.firstViewed, 0,
				receipt, null, Long.valueOf(dbReceipt.getFirstViewed()), receipt.getMessage(), null);
			dbReceipt.setFirstViewed(receipt.getFirstViewed());
		}
		if(dbReceipt.getLastViewed() != receipt.getLastViewed())
		{
			modified = true;
			status[0] += "Recipient " + receipt + " last view changed from ";
			status[0] += (dbReceipt.getLastViewed() >= 0 ? PrismsUtils.print(dbReceipt
				.getLastViewed()) : "none");
			status[0] += " to ";
			status[0] += (receipt.getLastViewed() >= 0 ? PrismsUtils.print(receipt.getLastViewed())
				: "none") + "\n\t";
			sql += "lastView=" + DBUtils.formatDate(receipt.getLastViewed(), isOracle()) + ", ";
			addModification(trans, MessageSubjectType.messageView, RecipientChange.lastViewed, 0,
				receipt, null, Long.valueOf(dbReceipt.getLastViewed()), receipt.getMessage(), null);
			dbReceipt.setLastViewed(receipt.getLastViewed());
		}

		if(modified)
		{
			sql = sql.substring(0, sql.length() - 2);
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not update recipient " + dbReceipt
					+ " of message " + dbReceipt.getMessage().getSubject() + ": SQL=" + sql, e);
			}
		}
	}

	void dbUpdateAttachment(Attachment dbAttach, Attachment attach, Statement stmt,
		RecordsTransaction trans, String [] status) throws PrismsMessageException
	{
		String sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_message_attachment SET ";
		boolean modified = false;
		if(dbAttach.isDeleted() != attach.isDeleted())
		{
			modified = true;
			if(attach.isDeleted())
				status[0] += "Attachment " + dbAttach.getName() + " removed\n\t";
			else
				status[0] += "Attachment " + dbAttach.getName() + " re-added\n\t";
			sql += "deleted=" + boolToSql(attach.isDeleted()) + ", ";
			addModification(trans, MessageSubjectType.attachment, null,
				attach.isDeleted() ? -1 : 1, attach, null, null, attach.getMessage(), null);
			dbAttach.setDeleted(attach.isDeleted());
		}
		if(!dbAttach.getName().equals(attach.getName()))
		{
			modified = true;
			status[0] += "Attachment " + dbAttach.getName() + " renamed to " + attach.getName()
				+ "\n\t";
			sql += "name=" + toSQL(attach.getName());
			addModification(trans, MessageSubjectType.attachment,
				MessageChangeTypes.AttachmentChange.name, 0, attach, null, dbAttach.getName(),
				attach.getMessage(), null);
			dbAttach.setName(attach.getName());
		}
		if(modified)
		{
			sql = sql.substring(0, sql.length() - 2);
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not update attachment "
					+ dbAttach.getName() + " of message " + dbAttach.getMessage().getSubject()
					+ ": SQL=" + sql, e);
			}
		}
	}

	void dbUpdateAction(MessageAction dbAction, MessageAction action, Statement stmt,
		RecordsTransaction trans, String [] status) throws PrismsMessageException
	{
		String sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_message_action SET ";
		boolean modified = false;
		if(dbAction.isDeleted() != action.isDeleted())
		{
			modified = true;
			if(action.isDeleted())
				status[0] += "Action " + dbAction.getDescrip() + " removed\n\t";
			else
				status[0] += "Action " + dbAction.getDescrip() + " re-added\n\t";
			sql += "deleted=" + boolToSql(action.isDeleted()) + ", ";
			addModification(trans, MessageSubjectType.action, null, action.isDeleted() ? -1 : 1,
				action, null, null, action.getMessage(), null);
			dbAction.setDeleted(action.isDeleted());
		}
		if(!dbAction.getDescrip().equals(action.getDescrip()))
		{
			synchronized(theActionUpdater)
			{
				try
				{
					DBUtils.setClob(theActionInserter, 1,
						new java.io.StringReader(action.getDescrip()));
					theActionUpdater.setLong(2, dbAction.getID());
					theActionUpdater.executeUpdate();
				} catch(SQLException e)
				{
					throw new PrismsMessageException("Could not update action " + dbAction.getID()
						+ " of message " + dbAction.getMessage(), e);
				}
			}
			addModification(trans, MessageSubjectType.action,
				MessageChangeTypes.ActionChange.descrip, 0, action, null, dbAction.getDescrip(),
				action.getMessage(), null);
			dbAction.setDescrip(action.getDescrip());
		}
		if(!ArrayUtils.equals(dbAction.getCompletedUser(), action.getCompletedUser()))
		{
			modified = true;
			if(dbAction.getCompletedUser() == null)
				status[0] += "Action " + dbAction.getDescrip() + " completed by "
					+ action.getCompletedUser() + " (ID " + action.getCompletedUser().getID()
					+ ")\n\t";
			else if(action.getCompletedUser() == null)
				status[0] += "Action " + dbAction.getDescrip() + " marked as uncompleted\n\t";
			else
				status[0] += "Action " + dbAction.getDescrip() + " completed user changed from "
					+ dbAction.getCompletedUser() + " (ID " + dbAction.getCompletedUser().getID()
					+ ") to " + action.getCompletedUser() + " (ID "
					+ action.getCompletedUser().getID() + ")\n\t";
			sql += "actionCompleted="
				+ (action.getCompletedUser() == null ? "NULL" : ""
					+ action.getCompletedUser().getID()) + ", ";
			dbAction.setCompletedUser(action.getCompletedUser());
		}
		if(modified)
		{
			sql = sql.substring(0, sql.length() - 2);
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not update action " + dbAction.getID()
					+ " of message " + dbAction.getMessage().getSubject() + ": SQL=" + sql, e);
			}
		}
	}

	/**
	 * Checks a search for a {@link MessageSearch.DeletedSearch} instance, as this has impacts on
	 * how a search is performed.
	 * 
	 * @param search The search to search for a deleted type
	 * @return Whether the given search has a deleted search or not
	 */
	public static boolean hasDelSearch(Search search)
	{
		if(search instanceof MessageSearch.DeletedSearch)
			return true;
		else if(search instanceof Search.CompoundSearch)
			for(Search srch : (Search.CompoundSearch) search)
				if(hasDelSearch(srch))
					return true;
		return false;
	}

	void compileQuery(Search search, boolean withParameters, boolean withConversation,
		StringBuilder joins, StringBuilder wheres) throws PrismsMessageException
	{
		if(search instanceof Search.NotSearch)
		{
			Search.NotSearch not = (Search.NotSearch) search;
			wheres.append("NOT ");
			boolean withParen = not.getParent() != null;
			if(withParen)
				wheres.append('(');
			compileQuery(not.getOperand(), withParameters, withConversation, joins, wheres);
			if(withParen)
				wheres.append(')');
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
				compileQuery(srch, withParameters, withConversation, joins, wheres);
			}
			if(withParen)
				wheres.append(')');
		}
		else if(search.getType() instanceof MessageSearch.MessageSearchType)
		{
			switch((MessageSearch.MessageSearchType) search.getType())
			{
			case id:
				MessageSearch.IDSearch ids = (MessageSearch.IDSearch) search;
				wheres.append("msg.id");
				wheres.append(ids.operator);
				if(ids.id == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsMessageException("No ID specified in ID search");
				}
				else
					wheres.append(ids.id);
				break;
			case conversation:
				MessageSearch.ConversationSearch convS = (MessageSearch.ConversationSearch) search;
				wheres.append("msg.msgConversation=");
				if(convS.conversationID == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsMessageException(
							"No conversation ID specified in conversation search");
				}
				else
					wheres.append(convS.conversationID);
				break;
			case successor:
				MessageSearch.SuccessorSearch sucS = (MessageSearch.SuccessorSearch) search;
				wheres.append("msg.msgPredecessor=");
				if(sucS.message == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsMessageException(
							"No predecessor specified in successor search");
				}
				else
					wheres.append(sucS.message.getID());
				break;
			case subject:
				MessageSearch.SubjectSearch subS = (MessageSearch.SubjectSearch) search;
				if(joins.indexOf("msgContent") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_message_content msgContent").append(
						"ON msgContent.messageNS=msg.messageNS AND msgContent.message=msg.id");
				}
				DBUtils.ConnType connType = DBUtils.getType(theTransactor.getConnection());
				String srch = subS.search.toLowerCase();
				srch = MULTI_WILDCARD + srch + MULTI_WILDCARD;
				srch = DBUtils.toLikeClause(srch, connType, MULTI_WILDCARD, SINGLE_WILDCARD);
				wheres.append('(');
				wheres.append(DBUtils.getLowerFn(connType));
				wheres.append("(msg.shortSubject) LIKE ").append(srch);
				wheres.append(" OR (");
				wheres.append(DBUtils.getLowerFn(connType)).append("(msgContent.content) LIKE ")
					.append(srch).append(" AND msgContent.contentType='S'");
				wheres.append(')');
				break;
			case author:
				MessageSearch.AuthorSearch authS = (MessageSearch.AuthorSearch) search;
				if(authS.userName != null)
				{
					if(joins.indexOf("msgAuthor") < 0)
					{
						joins.append(" INNER JOIN ").append(theTransactor.getTablePrefix());
						joins.append("prisms_user msgAuthor ON msg.msgAuthor=msgAuthor.id");
					}
					wheres.append("msgAuthor.userName=").append(toSQL(authS.userName));
				}
				else if(authS.user != null)
					wheres.append("msg.msgAuthor=").append(authS.user.getID());
				else if(withParameters)
					wheres.append("msg.msgAuthor=?");
				else
					throw new PrismsMessageException("No user specified for author search");
				break;
			case sent:
				MessageSearch.SentSearch sentS = (MessageSearch.SentSearch) search;
				if(sentS.sent != null)
				{
					wheres.append("msg.msgSent=");
					wheres.append(boolToSql(sentS.sent.booleanValue()));
				}
				else
				{
					connType = DBUtils.getType(theTransactor.getConnection());
					createDateQuery(sentS.operator, sentS.sentTime, connType, "msg.msgTime", wheres);
				}
				break;
			case priority:
				MessageSearch.PrioritySearch priS = (MessageSearch.PrioritySearch) search;
				wheres.append("msg.msgPriority=");
				if(priS.priority == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsMessageException(
							"No priority specified for priority search");
				}
				else
					wheres.append(priS.priority.ordinal());
				break;
			case recipient:
				MessageSearch.RecipientSearch recS = (MessageSearch.RecipientSearch) search;
				if(joins.indexOf("msgReceipt") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_message_recipient msgReceipt").append(
						" ON msgReceipt.messageNS=msg.messageNS AND msgReceipt.rcptMessage=msg.id");
				}
				wheres.append('(');
				if(recS.userName != null)
				{
					if(joins.indexOf("msgReceiptUser") < 0)
					{
						joins.append(" INNER JOIN ").append(theTransactor.getTablePrefix());
						joins.append("prisms_user msgReceiptUser").append(
							" ON msgReceiptUser.id=msgReceipt.rcptUser");
					}
					wheres.append("msgReceiptUser.userName=").append(toSQL(recS.userName));
				}
				else
				{
					wheres.append("msgReceipt.rcptUser=");
					if(recS.user == null)
					{
						if(withParameters)
							wheres.append('?');
						else
							throw new PrismsMessageException("No user specified for receipt search");
					}
					else
						wheres.append(recS.user.getID());
				}
				if(recS.applicability != null)
					wheres.append(" AND msgReceipt.rcptType=").append(recS.applicability.ordinal());
				wheres.append(" AND msgReceipt.deleted=").append(boolToSql(false));
				wheres.append(")");
				break;
			case readTime:
				MessageSearch.ReadTimeSearch rts = (MessageSearch.ReadTimeSearch) search;
				if(joins.indexOf("msgReceipt") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_message_recipient msgReceipt").append(
						" ON msgReceipt.messageNS=msg.messageNS AND msgReceipt.rcptMessage=msg.id");
				}
				wheres.append('(');
				if(rts.userName != null)
				{
					if(joins.indexOf("msgReceiptUser") < 0)
					{
						joins.append(" INNER JOIN ").append(theTransactor.getTablePrefix());
						joins.append("prisms_user msgReceiptUser").append(
							" ON msgReceiptUser.id=msgReceipt.rcptUser");
					}
					wheres.append("msgReceiptUser.userName=").append(toSQL(rts.userName));
				}
				else
				{
					wheres.append("msgReceipt.rcptUser=");
					if(rts.user == null)
					{
						if(withParameters)
							wheres.append('?');
						else
							throw new PrismsMessageException("No user specified for receipt search");
					}
					else
						wheres.append(rts.user.getID());
				}
				wheres.append(" AND ");
				connType = DBUtils.getType(theTransactor.getConnection());
				if(rts.readTime != null)
					createDateQuery(rts.operator, rts.readTime, connType, rts.isLast ? "lastViewed"
						: "firstViewed", wheres);
				else if(withParameters)
				{
					wheres.append(rts.isLast ? "lastViewed" : "firstViewed");
					wheres.append(rts.operator);
					wheres.append('?');
				}
				else
					throw new PrismsMessageException("No time specified for read time search");
				wheres.append(" AND msgReceipt.deleted=").append(boolToSql(false));
				wheres.append(')');
				break;
			case view:
				if(!withConversation)
					throw new PrismsMessageException("Message search may not include view searches");
				MessageSearch.ViewSearch viewS = (MessageSearch.ViewSearch) search;
				if(joins.indexOf("msgView") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_message_view msgView").append(
						" ON msgView.messageNS=msg.messageNS AND msgView.viewMsg=msg.id");
				}
				if(viewS.deleted == null && joins.indexOf("msgConv") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_conversation_view msgConv")
						.append(" ON msgConv.messageNS=msgView.messageNS AND ")
						.append("msgView.viewConversation=msgConv.viewConversation");
				}
				wheres.append('(');
				if(viewS.user != null)
					wheres.append("msgView.viewUser=").append(viewS.user.getID());
				else if(viewS.userName != null)
				{
					if(joins.indexOf("msgViewer") < 0)
					{
						joins.append(" INNER JOIN ").append(theTransactor.getTablePrefix());
						joins.append("prisms_user msgViewer ON msgView.viewUser=msgViewer.id");
					}
					wheres.append("msgViewer.userName=").append(toSQL(viewS.userName));
				}

				if(viewS.archived != null)
				{
					wheres.append(" AND msgConv.viewArchived=");
					wheres.append(boolToSql(viewS.archived.booleanValue()));
				}
				else if(viewS.starred != null)
				{
					wheres.append(" AND msgConv.viewStarred=");
					wheres.append(boolToSql(viewS.starred.booleanValue()));
				}
				else if(viewS.deleted != null)
				{
					wheres.append(" AND msgView.deleted=");
					wheres.append(boolToSql(viewS.deleted.booleanValue()));
				}
				else
					throw new PrismsMessageException("No search option selected for view search");
				wheres.append(')');
				break;
			case content:
				MessageSearch.ContentSearch contS = (MessageSearch.ContentSearch) search;
				if(contS.search.length() > CONTENT_OVERLAP)
					throw new IllegalArgumentException("Content searches may be no longer than "
						+ CONTENT_OVERLAP + " characters in length");
				if(joins.indexOf("msgContent") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_message_content msgContent")
						.append(" ON msgContent.messageNS=msg.messageNS AND ")
						.append("msg_content.message=msg.id ");
				}
				connType = DBUtils.getType(theTransactor.getConnection());
				srch = contS.search.toLowerCase();
				srch = MULTI_WILDCARD + srch + MULTI_WILDCARD;
				srch = DBUtils.toLikeClause(srch, connType, MULTI_WILDCARD, SINGLE_WILDCARD);
				wheres.append(DBUtils.getLowerFn(connType));
				wheres.append("(msgContent.content) LIKE ").append(srch);
				break;
			case attachment:
				MessageSearch.AttachmentSearch attS = (MessageSearch.AttachmentSearch) search;
				if(joins.indexOf("msg_attach") < 0)
				{
					joins.append(" LEFT JOIN ").append(theTransactor.getTablePrefix());
					joins.append("prisms_message_attachment msgAttach");
					joins.append("ON msgAttach.messageNS=msg.messageNS").append(
						" AND msgAttach.message=msg.id");
				}

				connType = DBUtils.getType(theTransactor.getConnection());
				if(attS.name != null)
				{
					srch = attS.name.toLowerCase();
					srch = MULTI_WILDCARD + srch + MULTI_WILDCARD;
					srch = DBUtils.toLikeClause(srch, connType, MULTI_WILDCARD, SINGLE_WILDCARD);
					wheres.append(DBUtils.getLowerFn(connType));
					wheres.append("(msgAttach.attName) LIKE ").append(srch);
				}
				else if(attS.attachType != null)
					wheres.append("msgAttach.attType=").append(toSQL(attS.attachType));
				else if(attS.size != null)
					createSizeQuery(attS.size, "msgAttach.attachLength", wheres, 1);
				else
					throw new IllegalStateException("Unrecognized attachment search type: "
						+ search);
				wheres.append(" AND msgAttach.deleted=").append(boolToSql(false));
				break;
			case size:
				MessageSearch.SizeSearch sizeS = (MessageSearch.SizeSearch) search;
				createSizeQuery(sizeS, "msg.msgSize", wheres, 1024);
				break;
			case deleted:
				MessageSearch.DeletedSearch delS = (MessageSearch.DeletedSearch) search;
				wheres.append("msg.deleted");
				if(delS.deleted == null)
					wheres.append(" IS NOT NULL");
				else
					wheres.append('=').append(boolToSql(delS.deleted.booleanValue()));
				break;
			}
		}
	}

	private void createDateQuery(MessageSearch.Operator operator, MessageSearch.SearchDate date,
		DBUtils.ConnType type, String column, StringBuilder wheres)
	{
		switch(operator)
		{
		case EQ:
			wheres.append(column).append(">=");
			wheres.append(DBUtils.formatDate(date.minTime, type == DBUtils.ConnType.ORACLE));
			wheres.append(" AND ").append(column).append("<=");
			wheres.append(DBUtils.formatDate(date.maxTime, type == DBUtils.ConnType.ORACLE));
			break;
		case NEQ:
			wheres.append(column).append("<=");
			wheres.append(DBUtils.formatDate(date.minTime, type == DBUtils.ConnType.ORACLE));
			wheres.append(" OR ").append(column).append(">=");
			wheres.append(DBUtils.formatDate(date.maxTime, type == DBUtils.ConnType.ORACLE));
			break;
		case GT:
			wheres.append(column).append(">=");
			wheres.append(DBUtils.formatDate(date.maxTime, type == DBUtils.ConnType.ORACLE));
			break;
		case GTE:
			wheres.append(column).append(">=");
			wheres.append(DBUtils.formatDate(date.minTime, type == DBUtils.ConnType.ORACLE));
			break;
		case LT:
			wheres.append(column).append("<=");
			wheres.append(DBUtils.formatDate(date.minTime, type == DBUtils.ConnType.ORACLE));
			break;
		case LTE:
			wheres.append(column).append("<=");
			wheres.append(DBUtils.formatDate(date.maxTime, type == DBUtils.ConnType.ORACLE));
			break;
		}
	}

	private void createSizeQuery(SizeSearch sizeS, String column, StringBuilder wheres, int scale)
	{
		float size = sizeS.size;
		for(int i = 0; i < sizeS.exp; i++)
			size *= 10;
		size /= scale;
		switch(sizeS.operator)
		{
		case EQ:
			wheres.append('(').append(column).append(">=").append(Math.round(size * .9f));
			wheres.append(" AND ");
			wheres.append(column).append("<=").append(Math.round(size * 1.1f)).append(')');
			break;
		case NEQ:
			wheres.append('(').append(column).append("<").append(Math.round(size * .9f));
			wheres.append(" OR ");
			wheres.append(column).append(">").append(Math.round(size * 1.1f)).append(')');
			break;
		case GT:
		case LT:
			wheres.append(column).append(sizeS.operator).append(Math.round(size));
			break;
		case GTE:
			wheres.append(column).append(">=").append(Math.round(size * .9f));
			break;
		case LTE:
			wheres.append(column).append("<=").append(Math.round(size * 1.1f));
			break;
		}
	}

	private Boolean _isOracle;

	boolean isOracle() throws PrismsMessageException
	{
		if(_isOracle == null)
			_isOracle = Boolean.valueOf(DBUtils.isOracle(theTransactor.getConnection()));
		return _isOracle.booleanValue();
	}

	public void disconnect()
	{
		closePStatements();
		theTransactor.release();
	}
}
