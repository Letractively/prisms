/*
 * DefaultMessageManager.java Created Oct 1, 2010 by Andrew Butler, PSL
 */
package prisms.message;

import static prisms.util.DBUtils.boolFromSql;

import java.io.IOException;
import java.sql.*;

import org.apache.log4j.Logger;

import prisms.arch.PrismsException;
import prisms.arch.ds.Transactor;
import prisms.arch.ds.User;
import prisms.arch.ds.Transactor.TransactionOperation;
import prisms.message.MessageChangeTypes.MessageChange;
import prisms.message.MessageChangeTypes.RecipientChange;
import prisms.message.MessageChangeTypes.ViewChange;
import prisms.message.MessageSearch.AttachmentSearch;
import prisms.message.MessageSearch.AuthorSearch;
import prisms.message.MessageSearch.ContentSearch;
import prisms.message.MessageSearch.PrioritySearch;
import prisms.message.MessageSearch.RecipientSearch;
import prisms.message.MessageSearch.SentSearch;
import prisms.message.MessageSearch.SizeSearch;
import prisms.message.MessageSearch.SubjectSearch;
import prisms.message.MessageSearch.SuccessorSearch;
import prisms.message.MessageSearch.ViewSearch;
import prisms.records.RecordsTransaction;
import prisms.util.*;

/** The default {@link MessageManager} implementation for PRISMS */
public class DefaultMessageManager implements MessageManager
{
	static final Logger log = Logger.getLogger(DefaultMessageManager.class);

	final String theNamespace;

	final prisms.arch.ds.IDGenerator theIDs;

	final prisms.arch.ds.UserSource theUS;

	Transactor<PrismsMessageException> theTransactor;

	private prisms.records.DBRecordKeeper theRecordKeeper;

	int theBlockSize;

	private java.util.ArrayList<PreparedStatement> thePStatements;

	private PreparedStatement theContentGetter;

	private PreparedStatement theReceiptGetter;

	private PreparedStatement theAttachGetter;

	PreparedStatement theViewGetter;

	private PreparedStatement theViewMessageGetter;

	private PreparedStatement theContentDeleter;

	private PreparedStatement theContentInserter;

	/**
	 * Creates a message manager
	 * 
	 * @param namespace The namespace to get messages from
	 * @param us The user source to get users from
	 * @param ids The ID generator for this message manager to use
	 * @param pf The connection factory to use to connect to the data source
	 * @param connEl The connection element to use to connect to the data source
	 * @param recordKeeper The record keeper to keep track of changes to all messages in this data
	 *        source
	 */
	public DefaultMessageManager(String namespace, prisms.arch.ds.IDGenerator ids,
		prisms.arch.ds.UserSource us, prisms.arch.ConnectionFactory pf,
		prisms.arch.PrismsConfig connEl, prisms.records.DBRecordKeeper recordKeeper)
	{
		theNamespace = namespace;
		theIDs = ids;
		theUS = us;
		thePStatements = new java.util.ArrayList<PreparedStatement>();
		theTransactor = pf.getConnection(connEl, null,
			new Transactor.Thrower<PrismsMessageException>()
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
		theTransactor.addReconnectListener(new Transactor.ReconnectListener()
		{
			public void reconnected(boolean initial)
			{
				if(initial)
					return;
				try
				{
					theBlockSize = DBUtils.getFieldSize(theTransactor.getConnection(),
						"prisms_message_content", "content");
				} catch(Exception e)
				{
					theBlockSize = 1024;
				}
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
				+ "prisms_message_content WHERE messageNS=" + DBUtils.toSQL(theNamespace)
				+ " AND message=? AND duplicate=" + DBUtils.boolToSql(false)
				+ " ORDER BY contentIndex";
			theContentGetter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theContentGetter);

			sql = "SELECT * FROM " + theTransactor.getTablePrefix()
				+ "prisms_message_recipient WHERE messageNS=" + DBUtils.toSQL(theNamespace)
				+ " AND message=? AND (deleted=" + DBUtils.boolToSql(false) + " OR deleted='?')";
			theReceiptGetter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theReceiptGetter);

			sql = "SELECT id, attachName, contentType, crcCode, deleted FROM "
				+ theTransactor.getTablePrefix() + "prisms_message_attachment WHERE messageNS="
				+ DBUtils.toSQL(theNamespace) + " AND message=? AND (deleted="
				+ DBUtils.boolToSql(false) + " OR deleted='?')";
			theAttachGetter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theAttachGetter);

			sql = "SELECT * FROM " + theTransactor.getTablePrefix()
				+ "prisms_conversation_view WHERE" + " messageNS=" + DBUtils.toSQL(theNamespace)
				+ " AND conversation=? AND viewer=?";
			theViewGetter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theViewGetter);

			sql = "SELECT * FROM " + theTransactor.getTablePrefix()
				+ "prisms_message WHERE messageNS=" + DBUtils.toSQL(theNamespace)
				+ " AND conversation=? ORDER BY messageTime";
			theViewMessageGetter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theViewMessageGetter);

			sql = "DELETE FROM " + theTransactor.getTablePrefix() + "prisms_message_content WHERE"
				+ " messageNS=" + DBUtils.toSQL(theNamespace) + " AND message=?";
			theContentDeleter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theContentDeleter);

			sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_message_content"
				+ " (messageNS, message, contentIndex, duplicate, content) VALUES ("
				+ DBUtils.toSQL(theNamespace) + ", ?, ?, ?, ?)";
			theContentInserter = theTransactor.getConnection().prepareStatement(sql);
			thePStatements.add(theContentInserter);

		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not prepare statements for messaging: SQL="
				+ sql, e);
		}
	}

	public void disconnect()
	{
		closePStatements();
		theTransactor.release();
	}

	public MessageHeader [] getMessages(long... ids) throws PrismsMessageException
	{
		return getMessages(null, ids);
	}

	public ConversationView [] getConversations(User viewer, long... ids)
		throws PrismsMessageException
	{
		java.util.ArrayList<ConversationView> ret = new java.util.ArrayList<ConversationView>();
		theTransactor.checkConnected();
		synchronized(theViewGetter)
		{
			ResultSet rs = null;
			try
			{
				for(long id : ids)
				{
					theViewGetter.setLong(1, id);
					theViewGetter.setLong(2, viewer.getID());
					rs = theViewGetter.executeQuery();
					if(!rs.next())
					{
						ret.add(null);
						rs.close();
						rs = null;
						continue;
					}
					ConversationView view = new ConversationView(viewer);
					view.setID(rs.getLong("conversation"));
					view.setArchived(DBUtils.boolFromSql(rs.getString("archived")));
					view.setStarred(DBUtils.boolFromSql(rs.getString("starred")));
					rs.close();
					rs = null;

					theViewMessageGetter.setLong(1, view.getID());
					rs = theViewMessageGetter.executeQuery();

					while(rs.next())
						view.addMessage(getMessageHeader(rs));
					rs.close();
					rs = null;
					ret.add(view);
				}
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not get conversations", e);
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
				try
				{
					theViewGetter.clearParameters();
					theViewMessageGetter.clearParameters();
				} catch(SQLException e)
				{
					log.error("Could not clear view getter parameters", e);
				}
			}
		}
		return ret.toArray(new ConversationView [ret.size()]);
	}

	public long [] getMessages(Search search) throws PrismsMessageException
	{
		return search(search, false);
	}

	private long [] search(Search search, boolean conversation) throws PrismsMessageException
	{
		String sql = compileQuery(search, conversation, false);
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
			throw new PrismsMessageException("Could not search database", e);
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

	public long [] getConversations(Search search) throws PrismsMessageException
	{
		return search(search, true);
	}

	public ConversationView [] getConversations(User viewer, MessageHeader... messages)
		throws PrismsMessageException
	{
		java.util.Map<Long, ConversationView> convs = new java.util.HashMap<Long, ConversationView>();

		theTransactor.checkConnected();
		synchronized(theViewGetter)
		{
			ResultSet rs = null;
			try
			{
				for(MessageHeader header : messages)
				{
					ConversationView view = convs.get(Long.valueOf(header.getConversationID()));
					if(view != null)
						continue;
					theViewGetter.setLong(1, header.getConversationID());
					theViewGetter.setLong(2, viewer.getID());
					rs = theViewGetter.executeQuery();
					if(!rs.next())
					{
						rs.close();
						rs = null;
						continue;
					}
					view = new ConversationView(viewer);
					view.setID(rs.getLong("conversation"));
					view.setArchived(DBUtils.boolFromSql(rs.getString("archived")));
					view.setStarred(DBUtils.boolFromSql(rs.getString("starred")));
					rs.close();
					rs = null;

					theViewMessageGetter.setLong(1, view.getID());
					rs = theViewMessageGetter.executeQuery();

					while(rs.next())
					{
						long messageID = rs.getLong("id");
						MessageHeader cm = null;
						for(MessageHeader msg : messages)
							if(msg.getID() == messageID)
							{
								cm = msg;
								break;
							}
						if(cm == null)
							cm = getMessageHeader(rs);
						view.addMessage(cm);
					}
					rs.close();
					rs = null;
					convs.put(Long.valueOf(view.getID()), view);
				}
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not get conversations", e);
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
				try
				{
					theViewGetter.clearParameters();
					theViewMessageGetter.clearParameters();
				} catch(SQLException e)
				{
					log.error("Could not clear view getter parameters", e);
				}
			}
		}

		final ArrayUtils.ArrayAdjuster<ConversationView, MessageHeader, RuntimeException> [] adjuster;
		adjuster = new ArrayUtils.ArrayAdjuster [1];
		adjuster[0] = new ArrayUtils.ArrayAdjuster<ConversationView, MessageHeader, RuntimeException>(
			convs.values().toArray(new ConversationView [convs.size()]), messages,
			new ArrayUtils.DifferenceListener<ConversationView, MessageHeader>()
			{
				public boolean identity(ConversationView o1, MessageHeader o2)
				{
					return o2.getConversationID() == o1.getID();
				}

				public ConversationView added(MessageHeader o, int mIdx, int retIdx)
				{
					adjuster[0].nullElement();
					return null;
				}

				public ConversationView removed(ConversationView o, int oIdx, int incMod, int retIdx)
				{
					return null;
				}

				public ConversationView set(ConversationView o1, int idx1, int incMod,
					MessageHeader o2, int idx2, int retIdx)
				{
					return o1;
				}
			});
		return adjuster[0].adjust();
	}

	MessageView [] getViews(ResultSet rs, User viewer,
		java.util.Map<Long, MessageHeader> headersById) throws SQLException
	{
		java.util.ArrayList<MessageView> ret = new java.util.ArrayList<MessageView>();
		while(rs.next())
		{
			MessageView view = new MessageView(
				headersById.get(Long.valueOf(rs.getLong("message"))), viewer);
			view.setArchived(DBUtils.boolFromSql(rs.getString("archived")));
			view.setStarred(DBUtils.boolFromSql(rs.getString("starred")));
			view.setDeleted(DBUtils.boolFromSql(rs.getString("deleted")));
			ret.add(view);
		}
		return ret.toArray(new MessageView [ret.size()]);
	}

	MessageHeader [] getMessages(Statement stmt, long... ids) throws PrismsMessageException
	{
		prisms.util.DBUtils.KeyExpression expr = prisms.util.DBUtils.simplifyKeySet(ids, 200);
		prisms.util.DBUtils.KeyExpression[] exprs;
		if(expr instanceof prisms.util.DBUtils.OrExpression && expr.getComplexity() > 200)
			exprs = ((prisms.util.DBUtils.OrExpression) expr).exprs;
		else
			exprs = new prisms.util.DBUtils.KeyExpression [] {expr};

		java.util.ArrayList<MessageHeader> ret = new java.util.ArrayList<MessageHeader>();
		for(int i = 0; i < exprs.length; i++)
		{
			String sql = "SELECT id, conversation, author, messageTime, sent, priority, override"
				+ " FROM " + theTransactor.getTablePrefix() + "prisms_message WHERE messageNS="
				+ DBUtils.toSQL(theNamespace) + " AND " + exprs[i].toSQL("id");
			for(MessageHeader header : getMessages(sql, stmt))
				ret.add(header);
		}

		Long [] idObjs = new Long [ids.length];
		for(int i = 0; i < ids.length; i++)
			idObjs[i] = Long.valueOf(ids[i]);
		final ArrayUtils.ArrayAdjuster<MessageHeader, Long, RuntimeException> [] adjuster;
		adjuster = new ArrayUtils.ArrayAdjuster [1];
		adjuster[0] = new ArrayUtils.ArrayAdjuster<MessageHeader, Long, RuntimeException>(
			ret.toArray(new MessageHeader [ret.size()]), idObjs,
			new ArrayUtils.DifferenceListener<MessageHeader, Long>()
			{
				public boolean identity(MessageHeader o1, Long o2)
				{
					return o1 != null && o1.getID() == o2.longValue();
				}

				public MessageHeader added(Long o, int idx, int retIdx)
				{
					adjuster[0].nullElement();
					return null;
				}

				public MessageHeader removed(MessageHeader o, int idx, int incMod, int retIdx)
				{
					return o;
				}

				public MessageHeader set(MessageHeader o1, int idx1, int incMod, Long o2, int idx2,
					int retIdx)
				{
					return o1;
				}
			});
		return adjuster[0].adjust();
	}

	MessageHeader [] getMessages(String sql, Statement stmt) throws PrismsMessageException
	{
		boolean closeStmt = false;
		ResultSet rs = null;
		java.util.LinkedHashMap<Integer, MessageHeader> ret = new java.util.LinkedHashMap<Integer, MessageHeader>();
		try
		{
			if(stmt == null)
			{
				closeStmt = true;
				try
				{
					stmt = theTransactor.getConnection().createStatement();
				} catch(SQLException e)
				{
					throw new PrismsMessageException("Could not connect to database", e);
				}
			}
			rs = stmt.executeQuery(sql.toString());
			while(rs.next())
			{
				Integer id = Integer.valueOf(rs.getInt("id"));
				MessageHeader header = ret.get(id);
				if(header != null)
					continue; // An artifact of the inner join with the recipient table for search
				header = getMessageHeader(rs);
				if(header == null)
					continue;
				ret.put(id, header);
			}
			rs.close();
			rs = null;

			for(MessageHeader header : ret.values())
				fillMessageHeader(header, stmt, false);
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not retrieve messages: SQL=" + sql, e);
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
			if(stmt != null && closeStmt)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		return ret.values().toArray(new MessageHeader [ret.size()]);
	}

	/**
	 * Gets a message by ID, even if it has been deleted, along with its metadata, deleted or not
	 * 
	 * @param id The ID of the message to get
	 * @param stmt The statement to use to retrieve the data
	 * @return The message with the given ID
	 * @throws PrismsMessageException If an error occurs retrieving the message data
	 */
	public MessageHeader getMessageWithDeleted(long id, Statement stmt)
		throws PrismsMessageException
	{
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
			+ "prisms_message WHERE messageNS=" + DBUtils.toSQL(theNamespace) + " AND id=" + id;
		MessageHeader ret;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			ret = getMessageHeader(rs);
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not retrieve message with ID " + id, e);
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
		fillMessageHeader(ret, stmt, true);
		return ret;
	}

	MessageHeader getMessageHeader(ResultSet rs) throws SQLException, PrismsMessageException
	{
		User user = getUser(rs.getLong("author"));
		if(user == null)
			return null; // Couldn't find the author
		MessageHeader header = new MessageHeader(user);
		header.setID(rs.getLong("id"));
		header.setConversationID(rs.getLong("conversation"));
		header.setPriority(MessageHeader.Priority.values()[rs.getInt("priority")]);
		header.setOverride(boolFromSql(rs.getString("override")));
		header.setSent(boolFromSql(rs.getString("sent")));
		header.setTime(rs.getTimestamp("messageT").getTime());
		Number pred = (Number) rs.getObject("predecessor");
		if(pred != null)
			header.setPredecessorID(pred.longValue());
		header.setSubject(DBUtils.fromSQL(rs.getString("subject")));
		header.setLength(rs.getInt("contentLength"));
		header.setCRC(rs.getLong("crcCode"));
		return header;
	}

	private void fillMessageHeader(MessageHeader message, Statement stmt, boolean withDeleted)
		throws PrismsMessageException
	{
		String sql = null;
		ResultSet rs = null;
		try
		{
			synchronized(theReceiptGetter)
			{
				sql = "(prepared receipt getter)";
				theReceiptGetter.setLong(1, message.getID());
				theReceiptGetter.setString(2, DBUtils.boolToSql(withDeleted));
				rs = theReceiptGetter.executeQuery();
				while(rs.next())
				{
					User user = getUser(rs.getLong("recipient"));
					if(user == null)
					{
						log.warn("No such user " + rs.getLong("recipient") + " for message "
							+ message.getSubject());
						continue;
					}
					Receipt receipt = message.addRecipient(user);
					receipt.setApplicability(prisms.message.Receipt.Applicability.valueOf(rs
						.getString("applicability")));
					Timestamp time;
					time = rs.getTimestamp("firstViewed");
					receipt.setFirstViewed(time == null ? -1 : time.getTime());
					time = rs.getTimestamp("lastViewed");
					receipt.setLastViewed(time == null ? -1 : time.getTime());
					receipt.setDeleted(boolFromSql(rs.getString("deleted")));
				}
				rs.close();
				rs = null;
				theReceiptGetter.clearParameters();
			}

			synchronized(theAttachGetter)
			{
				sql = "(prepared attachment getter)";
				theAttachGetter.setLong(1, message.getID());
				theAttachGetter.setString(2, DBUtils.boolToSql(withDeleted));
				rs = theAttachGetter.executeQuery();
				while(rs.next())
				{
					long length;
					java.sql.Blob blob = rs.getBlob("content");
					try
					{
						length = blob.length();
					} finally
					{
						if(PrismsUtils.isJava6())
							blob.free();
					}
					Attachment attach = new Attachment(message, rs.getLong("id"),
						DBUtils.fromSQL(rs.getString("name")),
						DBUtils.fromSQL(rs.getString("type")), length, rs.getInt("crcCode"));
					attach.setDeleted(boolFromSql(rs.getString("deleted")));
					message.addAttachment(attach);
				}
				rs.close();
				rs = null;
				theAttachGetter.clearParameters();
			}
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not get message data: SQL=" + sql, e);
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

	private User getUser(long id) throws PrismsMessageException
	{
		User [] allUsers;
		try
		{
			allUsers = theUS.getActiveUsers();
		} catch(PrismsException e)
		{
			throw new PrismsMessageException("Could not get users", e);
		}
		for(User user : allUsers)
			if(user.getID() == id)
				return user;
		if(!(theUS instanceof prisms.arch.ds.ManageableUserSource))
			return null;
		try
		{
			allUsers = ((prisms.arch.ds.ManageableUserSource) theUS).getAllUsers();
		} catch(PrismsException e)
		{
			throw new PrismsMessageException("Could not get all users", e);
		}
		for(User user : allUsers)
			if(user.getID() == id)
				return user;
		return null;
	}

	public String previewMessage(MessageHeader header) throws PrismsMessageException
	{
		String ret = null;
		synchronized(theContentGetter)
		{
			ResultSet rs = null;
			try
			{
				theContentGetter.setLong(1, header.getID());
				rs = theContentGetter.executeQuery();
				if(rs.next())
					ret = rs.getString(1);
				else
					ret = "";
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
		}
		return ret;
	}

	public Message getMessage(MessageHeader header) throws PrismsMessageException
	{
		StringBuilder content = new StringBuilder();
		synchronized(theContentGetter)
		{
			ResultSet rs = null;
			try
			{
				theContentGetter.setLong(1, header.getID());
				rs = theContentGetter.executeQuery();
				while(rs.next())
					content.append(rs.getString(1));
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
		}
		Message ret = new Message(header);
		ret.setContent(content.toString());
		return ret;
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
					theBlob.free();
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
			if(!isClosed)
				close();
			super.finalize();
		}
	}

	public java.io.InputStream getAttachmentContent(Attachment attach)
		throws PrismsMessageException
	{
		String sql = "SELECT content from " + theTransactor.getTablePrefix()
			+ "prisms_message_attachment WHERE messageNS=" + DBUtils.toSQL(theNamespace)
			+ " AND id=" + attach.getID();
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
					MessageHeader dbHeader = getMessageWithDeleted(message.getHeader().getID(),
						stmt);
					if(dbHeader == null)
						dbInsertMessage(message, stmt, trans);
					else
						dbUpdateMessage(dbHeader, message, stmt, trans);
					return null;
				}
			}, "Could not add/modify message " + message.getHeader().getSubject());
	}

	public void putConversation(final ConversationView view, final RecordsTransaction trans)
		throws PrismsMessageException
	{
		theTransactor.performTransaction(
			new Transactor.TransactionOperation<PrismsMessageException>()
			{
				public Object run(Statement stmt) throws PrismsMessageException
				{
					ConversationView dbConv;
					synchronized(theViewGetter)
					{
						ResultSet rs = null;
						try
						{
							theViewGetter.setLong(1, view.getID());
							rs = theViewGetter.executeQuery();
							if(!rs.next())
								dbConv = null;
							else
							{
								dbConv = new ConversationView(view.getViewer());
								dbConv.setID(view.getID());
								dbConv.setArchived(DBUtils.boolFromSql(rs.getString("archived")));
								dbConv.setStarred(DBUtils.boolFromSql(rs.getString("starred")));
							}
						} catch(SQLException e)
						{
							throw new PrismsMessageException("Could not query conversation view", e);
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
					if(dbConv == null)
						dbInsertConversation(view, stmt, trans);
					else
						dbUpdateConversation(dbConv, view, stmt, trans);
					return null;
				}
			}, "Could not add/modify user " + view.getViewer() + "'s view of conversation");
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

	public Attachment createAttachment(final MessageHeader message, final String name,
		final String type, final java.io.InputStream data, final RecordsTransaction trans)
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
						id = theIDs.getNextID("prisms_message_attachment", "id", stmt,
							theTransactor.getTablePrefix(),
							"messageNS=" + DBUtils.toSQL(theNamespace));
					} catch(PrismsException e)
					{
						throw new PrismsMessageException("Could not get next attachment ID", e);
					}
					String sql = "INSERT INTO " + theTransactor.getTablePrefix()
						+ "prisms_message_attachment (messageNS, message, id, name, type, crcCode,"
						+ " content, deleted) VALUES (" + DBUtils.toSQL(theNamespace) + ", "
						+ message.getID() + ", " + id + ", " + DBUtils.toSQL(name) + ", "
						+ DBUtils.toSQL(type) + ", 0, ?, " + DBUtils.boolToSql(false) + ")";
					BlobInsertInputStream blobInput = new BlobInsertInputStream(data);
					java.sql.PreparedStatement ps = null;
					try
					{
						ps = theTransactor.getConnection().prepareStatement(sql);
						DBUtils.setBlob(ps, 1, java.sql.Types.BLOB, blobInput);
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
					sql = "UPDATE " + theTransactor.getTablePrefix()
						+ "prisms_message_attachment SET crcCode=" + blobInput.getCRC()
						+ " WHERE messageNS=" + DBUtils.toSQL(theNamespace) + " AND id=" + id;
					try
					{
						stmt.executeUpdate(sql);
					} catch(SQLException e)
					{
						throw new PrismsMessageException(
							"Could not update CRC code for attachment " + name + " in message "
								+ message.getSubject() + ": SQL=" + sql, e);
					}
					Attachment ret = new Attachment(message, id, name, type, blobInput.getLength(),
						blobInput.getCRC());
					addModification(trans, MessageSubjectType.attachment, null, 1, ret, null, null,
						ret.getMessage(), null);
					return ret;
				}
			}, "Could not add attachment to  message " + message.getSubject());
	}

	public void deleteAttachment(final Attachment attach, final RecordsTransaction trans)
		throws PrismsMessageException
	{
		theTransactor.performTransaction(new TransactionOperation<PrismsMessageException>()
		{
			public Object run(Statement stmt) throws PrismsMessageException
			{
				String sql = "UPDATE " + theTransactor.getTablePrefix()
					+ "prisms_message_attachment SET deleted=" + DBUtils.boolToSql(true)
					+ " WHERE messageNS=" + DBUtils.toSQL(theNamespace) + " AND id="
					+ attach.getID();
				try
				{
					stmt.executeUpdate(sql);
				} catch(SQLException e)
				{
					log.error("Could not mark attachment " + attach.getName() + " of message "
						+ attach.getMessage().getSubject() + " as deleted: SQL=" + sql, e);
				}
				attach.setDeleted(true);
				addModification(trans, MessageSubjectType.attachment, null, -1, attach, null, null,
					attach.getMessage(), null);
				return null;
			}
		}, "Could not delete attachment " + attach.getName() + " of message "
			+ attach.getMessage().getSubject());
	}

	void dbInsertMessage(Message message, Statement stmt, RecordsTransaction trans)
		throws PrismsMessageException
	{
		MessageHeader header = message.getHeader();
		String sql = null;
		ResultSet rs = null;
		try
		{
			if(header.getConversationID() >= 0)
			{
				sql = "SELECT id FROM " + theTransactor.getTablePrefix()
					+ "prisms_conversation WHERE" + " messageNS=" + DBUtils.toSQL(theNamespace)
					+ " AND id=" + header.getConversationID();
				rs = stmt.executeQuery(sql);
				boolean hasConv = rs.next();
				rs.close();
				rs = null;
				if(!hasConv)
				{
					sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_conversation"
						+ " (messageNS, id) VALUES (" + DBUtils.toSQL(theNamespace) + ", "
						+ header.getConversationID();
					stmt.executeUpdate(sql);
				}
			}
			else if(header.getPredecessorID() >= 0)
			{
				sql = "SELECT conversation FROM " + theTransactor.getTablePrefix()
					+ "prisms_message WHERE" + " messageNS=" + DBUtils.toSQL(theNamespace)
					+ " AND id=" + header.getPredecessorID();
				rs = stmt.executeQuery(sql);
				Number conv = null;
				if(rs.next())
					conv = (Number) rs.getObject(1);
				if(conv != null)
					header.setConversationID(conv.longValue());
			}

			if(header.getConversationID() < 0)
			{
				try
				{
					header
						.setConversationID(theIDs.getNextID("prisms_conversation", "id", stmt,
							theTransactor.getTablePrefix(),
							"messageNS=" + DBUtils.toSQL(theNamespace)));
				} catch(PrismsException e)
				{
					throw new PrismsMessageException("Could not generate new conversation ID", e);
				}
			}
			if(header.getID() < 0)
			{
				try
				{
					header
						.setID(theIDs.getNextID("prisms_message", "id", stmt,
							theTransactor.getTablePrefix(),
							"messageNS=" + DBUtils.toSQL(theNamespace)));
				} catch(PrismsException e)
				{
					throw new PrismsMessageException("Could not generate new conversation ID", e);
				}
			}
			sql = "INSERT INTO " + theTransactor.getTablePrefix()
				+ "prisms_message (messageNS, id,"
				+ " conversation, author, messageTime, sent, priority, predecessor, override,"
				+ " subject, crcCode, contentLength, messageSize) VALUES ("
				+ DBUtils.toSQL(theNamespace) + ", " + header.getID() + ", "
				+ header.getConversationID() + ", " + header.getAuthor().getID() + ", "
				+ header.getTime() + ", " + DBUtils.boolToSql(header.isSent()) + ", "
				+ header.getPriority().ordinal() + ", ";
			sql = (header.getPredecessorID() < 0 ? "NULL" : "" + header.getPredecessorID()) + ", ";
			sql += DBUtils.boolToSql(header.isOverride()) + ", "
				+ DBUtils.toSQL(header.getSubject()) + ", " + header.getCRC() + ", "
				+ message.getContent().length() + ", " + getMessageSize(message) + ")";
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
		addModification(trans, MessageSubjectType.message, null, 1, header, null, null, null, null);

		for(int r = 0; r < header.getReceiptCount(); r++)
			insertReceipt(header.getReceipt(r), stmt, null);
		insertContent(message);
	}

	private long getMS(MessageHeader header)
	{
		int size = 631;
		size += header.getReceiptCount() * 110;
		for(int a = 0; a < header.getAttachmentCount(); a++)
			size += 610 + header.getAttachment(a).getLength();
		return size;
	}

	long getMessageSize(Message message)
	{
		long size = getMS(message.getHeader());
		long contentLength = message.getContent().length();
		size += Math.ceil(contentLength * 1.0 / theBlockSize) * 2 * 2127;
		return size;
	}

	void insertReceipt(Receipt receipt, Statement stmt, RecordsTransaction trans)
		throws PrismsMessageException
	{
		String sql = "INSERT INTO " + theTransactor.getTablePrefix()
			+ "prisms_message_recipient (messageNS, message, id, recipient, applicability,"
			+ " firstReadTime, lastReadTime, deleted) VALUES (" + DBUtils.toSQL(theNamespace)
			+ ", " + receipt.getMessage().getID() + ", " + receipt.getID() + ", "
			+ receipt.getUser().getID() + ", " + receipt.getApplicability().ordinal() + ", ";
		sql += DBUtils.formatDate(receipt.getFirstViewed(), isOracle()) + ", ";
		sql += DBUtils.formatDate(receipt.getLastViewed(), isOracle()) + ", ";
		sql += DBUtils.boolToSql(false) + ")";
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

	void insertContent(Message message) throws PrismsMessageException
	{
		synchronized(theContentInserter)
		{
			int index = 0;
			for(int i = 0; index < message.getContent().length(); i++)
			{
				boolean duplicate = i % 2 == 1;
				int end = index + theBlockSize;
				if(end > message.getContent().length())
					end = message.getContent().length();
				try
				{
					theContentInserter.setLong(1, message.getHeader().getID());
					theContentInserter.setInt(2, i / 2);
					theContentInserter.setString(3, DBUtils.boolToSql(i % 2 == 1));
					theContentInserter.setString(4, message.getContent().substring(index, end));
					theContentInserter.clearParameters();
				} catch(SQLException e)
				{
					throw new PrismsMessageException("Could not insert content", e);
				}
				if(end == message.getContent().length() && !duplicate)
					break;
				if(i % 2 == 0)
					index += theBlockSize / 2;
				else
					index = theBlockSize * ((i + 1) / 2);
			}
		}
	}

	void dbUpdateMessage(final MessageHeader dbMessage, final Message message,
		final Statement stmt, final RecordsTransaction trans) throws PrismsMessageException
	{
		String status = "Modified message " + dbMessage.getSubject() + ":\n\t";
		String sql = "";
		MessageHeader header = message.getHeader();
		if(dbMessage.getTime() != header.getTime())
		{
			status += "Message time changed from " + PrismsUtils.print(dbMessage.getTime())
				+ " to " + PrismsUtils.print(header.getTime()) + "\n\t";
			sql += "messageTime=" + header.getTime() + ", ";
			addModification(trans, MessageSubjectType.message, MessageChange.time, 0, dbMessage,
				null, Long.valueOf(dbMessage.getTime()), null, null);
		}
		if(dbMessage.isSent() != header.isSent())
		{
			status += "Message " + (header.isSent() ? "sent" : "unsent") + "\n\t";
			sql += "sent=" + DBUtils.boolToSql(header.isSent()) + ", ";
			addModification(trans, MessageSubjectType.message, MessageChange.sent, 0, dbMessage,
				null, Boolean.valueOf(dbMessage.isSent()), null, null);
		}
		if(dbMessage.getPriority() != header.getPriority())
		{
			status += "Priority changed from " + dbMessage.getPriority() + " to "
				+ header.getPriority() + "\n\t";
			sql += "priority=" + header.getPriority().ordinal() + ", ";
			addModification(trans, MessageSubjectType.message, MessageChange.priority, 0,
				dbMessage, null, dbMessage.getPriority(), null, null);
		}
		if(!dbMessage.getSubject().equals(header.getSubject()))
		{
			status += "Subject changed from " + dbMessage.getSubject() + " to "
				+ header.getSubject() + "\n\t";
			sql += "subject=" + DBUtils.toSQL(header.getSubject()) + ", ";
			addModification(trans, MessageSubjectType.message, MessageChange.subject, 0, dbMessage,
				null, dbMessage.getSubject(), null, null);
		}
		if(dbMessage.getPredecessorID() != header.getPredecessorID())
		{
			MessageHeader pred = null;
			if(dbMessage.getPredecessorID() >= 0)
				try
				{
					pred = getMessages(dbMessage.getPredecessorID())[0];
				} catch(PrismsMessageException e)
				{
					throw new PrismsMessageException("No such predecessor with ID "
						+ dbMessage.getPredecessorID(), e);
				}
			status += "Predecessor changed from " + dbMessage.getPredecessorID() + " to "
				+ header.getPredecessorID() + "\n\t";
			sql += "predecessor="
				+ (header.getPredecessorID() < 0 ? "NULL" : "" + header.getPredecessorID()) + ", ";
			if(header.getPredecessorID() >= 0)
			{
				String subSql = "SELECT deleted WHERE id=" + header.getPredecessorID();
				ResultSet rs = null;
				try
				{
					rs = stmt.executeQuery(subSql);
					if(!rs.next())
						throw new PrismsMessageException("No such predecessor with ID "
							+ header.getPredecessorID() + " for message " + header.getSubject());
				} catch(SQLException e)
				{
					throw new PrismsMessageException(
						"Could not check for existence of predecessor with ID "
							+ header.getPredecessorID(), e);
				}
			}
			addModification(trans, MessageSubjectType.message, MessageChange.predecessor, 0,
				dbMessage, null, pred, null, null);
		}
		if(dbMessage.isOverride() != header.isOverride())
		{
			status += "Override changed to " + header.isOverride() + "\n\t";
			sql += "override=" + DBUtils.boolToSql(header.isOverride()) + ", ";
			addModification(trans, MessageSubjectType.message, MessageChange.override, 0,
				dbMessage, null, Boolean.valueOf(dbMessage.isOverride()), null, null);
		}
		long dbSize = getMessageSize(dbMessage, stmt);
		long newSize = getMessageSize(message);
		if(dbSize != newSize)
		{
			status += "Size changed from " + dbSize + " to " + newSize + "\n\t";
			sql += "messageSize=" + newSize + ", ";
		}

		if(sql.length() > 0)
		{
			log.debug(status.substring(0, status.length() - 2));
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

		if(dbMessage.getCRC() != header.getCRC() || dbMessage.getLength() != header.getLength())
		{
			status += "Content changed from " + dbMessage.getLength() + " to " + header.getLength()
				+ " characters\n\t";
			sql += "crcCode=" + header.getCRC() + ", content=?, ";
			// TODO How to do the modification? This is really big and inefficient.
			String oldContent = getMessage(dbMessage).getContent();
			synchronized(theContentDeleter)
			{
				try
				{
					theContentDeleter.setLong(1, dbMessage.getID());
					theContentDeleter.executeUpdate();
				} catch(SQLException e)
				{
					throw new PrismsMessageException("Could not update content of message "
						+ header, e);
				}
			}
			insertContent(message);
			addModification(trans, MessageSubjectType.message, MessageChange.content, 0, dbMessage,
				null, oldContent, null, null);
		}

		Receipt [] dbReceipts = new Receipt [dbMessage.getReceiptCount()];
		for(int r = 0; r < dbReceipts.length; r++)
			dbReceipts[r] = dbMessage.getReceipt(r);
		Receipt [] receipts = new Receipt [header.getReceiptCount()];
		for(int r = 0; r < receipts.length; r++)
			receipts[r] = header.getReceipt(r);
		ArrayUtils.adjust(dbReceipts, receipts,
			new ArrayUtils.DifferenceListener<Receipt, Receipt>()
			{
				public boolean identity(Receipt o1, Receipt o2)
				{
					return o1.equals(o2);
				}

				public Receipt added(Receipt o, int mIdx, int retIdx)
				{
					try
					{
						insertReceipt(o, stmt, trans);
					} catch(PrismsMessageException e)
					{
						log.error(e.getMessage(), e);
					}
					return o;
				}

				public Receipt removed(Receipt o, int oIdx, int incMod, int retIdx)
				{
					String sql2 = "UPDATE " + theTransactor.getTablePrefix()
						+ "prisms_message_recipient SET deleted=" + DBUtils.boolToSql(true)
						+ " WHERE messageNS=" + DBUtils.toSQL(theNamespace) + " AND id="
						+ o.getID();
					try
					{
						stmt.executeUpdate(sql2);
						o.setDeleted(true);
					} catch(SQLException e)
					{
						log.error("Could not delete recipient " + o + " of message "
							+ o.getMessage().getSubject() + ": SQL=" + sql2, e);
					}
					return null;
				}

				public Receipt set(Receipt o1, int idx1, int incMod, Receipt o2, int idx2,
					int retIdx)
				{
					try
					{
						dbUpdateReceipt(o1, o2, stmt, trans);
					} catch(PrismsMessageException e)
					{
						log.error(e.getMessage(), e);
					}
					return o1;
				}
			});
	}

	long getMessageSize(MessageHeader header, Statement stmt) throws PrismsMessageException
	{
		ResultSet rs = null;
		String sql = "SELECT messageSize FROM " + theTransactor.getTablePrefix()
			+ "prisms_message WHERE messageNS=" + DBUtils.toSQL(theNamespace) + " AND id="
			+ header.getID();
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

	void dbUpdateReceipt(Receipt dbReceipt, Receipt receipt, Statement stmt,
		RecordsTransaction trans) throws PrismsMessageException
	{
		String status = "Updating recipient " + dbReceipt + " of message "
			+ dbReceipt.getMessage().getSubject() + ":\n\t";
		String sql = "";
		if(dbReceipt.isDeleted() != receipt.isDeleted())
		{
			if(receipt.isDeleted())
				status += "Deleted\n\t";
			else
				status += "Re-created\n\t";
			sql += "deleted=" + DBUtils.boolToSql(receipt.isDeleted()) + ", ";
			addModification(trans, MessageSubjectType.recipient, null,
				receipt.isDeleted() ? -1 : 1, dbReceipt, null, null, dbReceipt.getMessage(), null);
		}
		if(dbReceipt.getApplicability() != receipt.getApplicability())
		{
			status += "Applicability changed to " + receipt.getApplicability() + "\n\t";
			sql += "applicability=" + receipt.getApplicability().ordinal() + ", ";
			addModification(trans, MessageSubjectType.recipient, RecipientChange.applicability, 0,
				receipt, null, dbReceipt.getApplicability(), dbReceipt.getMessage(), null);
		}
		if(dbReceipt.getFirstViewed() != receipt.getFirstViewed())
		{
			status += "First view changed from "
				+ (dbReceipt.getFirstViewed() >= 0 ? PrismsUtils.print(dbReceipt.getFirstViewed())
					: "none")
				+ " to "
				+ (receipt.getFirstViewed() >= 0 ? PrismsUtils.print(receipt.getFirstViewed())
					: "none") + "\n\t";
			sql += "firstView=" + DBUtils.formatDate(receipt.getFirstViewed(), isOracle()) + ", ";
			addModification(trans, MessageSubjectType.view, RecipientChange.firstReadTime, 0,
				receipt, null, Long.valueOf(dbReceipt.getFirstViewed()), receipt.getMessage(), null);
		}
		if(dbReceipt.getLastViewed() != receipt.getLastViewed())
		{
			status += "Last view changed from "
				+ (dbReceipt.getLastViewed() >= 0 ? PrismsUtils.print(dbReceipt.getLastViewed())
					: "none")
				+ " to "
				+ (receipt.getLastViewed() >= 0 ? PrismsUtils.print(receipt.getLastViewed())
					: "none") + "\n\t";
			sql += "lastView=" + DBUtils.formatDate(receipt.getLastViewed(), isOracle()) + ", ";
			addModification(trans, MessageSubjectType.view, RecipientChange.lastReadTime, 0,
				receipt, null, Long.valueOf(dbReceipt.getLastViewed()), receipt.getMessage(), null);
		}

		if(sql.length() > 0)
		{
			log.debug(status.substring(0, status.length() - 2));
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

	void dbInsertConversation(ConversationView view, Statement stmt, RecordsTransaction trans)
		throws PrismsMessageException
	{
		String sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_conversation_view ("
			+ "messageNS, conversation, viewer, archived, starred) VALUES ("
			+ DBUtils.toSQL(theNamespace) + ", " + view.getID() + ", " + view.getViewer().getID()
			+ ", " + DBUtils.boolToSql(view.isArchived()) + ", "
			+ DBUtils.boolToSql(view.isStarred()) + ")";
		try
		{
			stmt.executeUpdate(sql);
		} catch(SQLException e)
		{
			throw new PrismsMessageException("Could not insert conversation view: SQL=" + sql, e);
		}
		addModification(trans, MessageSubjectType.view, null, 1, view, null, null,
			view.getViewer(), null);
	}

	void dbUpdateConversation(ConversationView dbView, ConversationView view, Statement stmt,
		RecordsTransaction trans) throws PrismsMessageException
	{
		String status = "Updating " + dbView.getViewer() + "'s view of conversation " + dbView
			+ " updated:\n\t";
		String update = "";
		if(dbView.isArchived() != view.isArchived())
		{
			status += "Message " + (view.isArchived() ? "" : "un") + "archived\n\t";
			update += "archived=" + DBUtils.boolToSql(view.isArchived()) + ", ";
			addModification(trans, MessageSubjectType.view, ViewChange.archived, 0, view, null,
				Boolean.valueOf(dbView.isArchived()), view.getViewer(), null);
		}
		if(dbView.isStarred() != view.isStarred())
		{
			status += "Message " + (view.isStarred() ? "" : "un") + "starred\n\t";
			update += "starred=" + DBUtils.boolToSql(view.isStarred()) + ", ";
			addModification(trans, MessageSubjectType.view, ViewChange.starred, 0, view, null,
				Boolean.valueOf(dbView.isStarred()), view.getViewer(), null);
		}

		if(update.length() > 0)
		{
			log.debug(status.substring(0, status.length() - 2));
			update = "UPDATE " + theTransactor.getTablePrefix() + "prisms_conversation_view SET "
				+ update.substring(0, update.length() - 2) + " WHERE messageNS="
				+ DBUtils.toSQL(theNamespace) + " AND conversation=" + dbView.getID()
				+ " AND viewer=" + dbView.getViewer();
			try
			{
				stmt.executeUpdate(update);
			} catch(SQLException e)
			{
				throw new PrismsMessageException("Could not update conversation view: SQL="
					+ update, e);
			}
		}
	}

	private String compileQuery(Search search, boolean conversation, boolean withParameters)
		throws PrismsMessageException
	{
		StringBuilder joins = new StringBuilder();
		StringBuilder wheres = new StringBuilder();
		compileQuery(search, withParameters, joins, wheres);
		String select = "SELECT DISTINCT msg.";
		if(conversation)
			select += "conversation";
		else
			select += "id";
		select += " FROM " + theTransactor.getTablePrefix() + "prisms_message msg ";
		joins.insert(0, select);
		if(wheres.length() > 0)
		{
			joins.append(" WHERE ");
			joins.append(wheres);
		}
		return joins.toString();
	}

	private void compileQuery(Search search, boolean withParameters, StringBuilder joins,
		StringBuilder wheres) throws PrismsMessageException
	{
		if(search instanceof Search.NotSearch)
		{
			Search.NotSearch not = (Search.NotSearch) search;
			wheres.append("NOT ");
			boolean withParen = not.getParent() != null;
			if(withParen)
				wheres.append('(');
			compileQuery(not.getOperand(), withParameters, joins, wheres);
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
				compileQuery(srch, withParameters, joins, wheres);
			}
			if(withParen)
				wheres.append(')');
		}
		else if(search.getType() instanceof MessageSearch.MessageSearchType)
		{
			DBUtils.ConnType type = DBUtils.getType(theTransactor.getConnection());
			switch((MessageSearch.MessageSearchType) search.getType())
			{
			case successor:
				wheres.append("msg.predecessor=");
				wheres.append(((SuccessorSearch) search).message.getID());
				wheres.append("");
				break;
			case subject:
				wheres.append(DBUtils.getLowerFn(type));
				wheres.append("(msg.subject) LIKE %");
				wheres.append(DBUtils.toSQL(((SubjectSearch) search).search.toLowerCase()));
				wheres.append('%');
				break;
			case author:
				AuthorSearch authS = (AuthorSearch) search;
				if(authS.userName != null)
				{
					if(joins.indexOf("msg_author") < 0)
					{
						joins.append("INNER JOIN ");
						joins.append(theTransactor.getTablePrefix());
						joins.append("prisms_user msg_author ON ");
						joins.append("msg.author=");
						joins.append("msg_author.id ");
					}

					wheres.append("msg_author.userName=");
					wheres.append(DBUtils.toSQL(authS.userName));
				}
				else
				{
					wheres.append("msg.author=");
					wheres.append(authS.user.getID());
				}
				break;
			case sent:
				SentSearch sentS = (SentSearch) search;
				if(sentS.sent != null)
				{
					wheres.append("msg.sent=");
					wheres.append(DBUtils.boolToSql(sentS.sent.booleanValue()));
				}
				else
					createDateQuery(sentS.operator, sentS.sentTime, type, "msg.messageTime", wheres);
				break;
			case recipient:
				wheres.append('(');
				RecipientSearch recipS = (RecipientSearch) search;
				joinRecipient(joins, wheres, recipS.userName, recipS.user);
				if(recipS.withDeleted != null)
					wheres.append(" AND msg_recip.deleted="
						+ DBUtils.boolToSql(recipS.withDeleted.booleanValue()));
				if(recipS.applicability != null)
					wheres.append(" AND msg_recip.applicability=" + recipS.applicability.ordinal());
				wheres.append(")");
				break;
			case view:
				ViewSearch viewS = (ViewSearch) search;

				wheres.append('(');
				if(viewS.read != null)
				{
					joinRecipient(joins, wheres, viewS.userName, viewS.user);
					wheres.append(" AND msg_recip.firstReadTime IS ");
					if(!viewS.read.booleanValue())
						wheres.append("NOT ");
					wheres.append("NULL");
				}
				else if(viewS.archived != null)
				{
					joinView(joins, wheres, viewS.userName, viewS.user);
					wheres.append(" AND conv_view.archived=");
					wheres.append(DBUtils.boolToSql(viewS.archived.booleanValue()));
				}
				else if(viewS.starred != null)
				{
					joinView(joins, wheres, viewS.userName, viewS.user);
					wheres.append(" AND conv_view.starred=");
					wheres.append(DBUtils.boolToSql(viewS.starred.booleanValue()));
				}
				else if(viewS.firstReadTime != null)
				{
					joinRecipient(joins, wheres, viewS.userName, viewS.user);
					wheres.append(" AND ");
					createDateQuery(viewS.operator, viewS.firstReadTime, type,
						"msg_view.firstReadTime", wheres);
				}
				else if(viewS.lastReadTime != null)
				{
					joinRecipient(joins, wheres, viewS.userName, viewS.user);
					wheres.append(" AND ");
					createDateQuery(viewS.operator, viewS.lastReadTime, type,
						"msg_view.lastReadTime", wheres);
				}
				else
					throw new IllegalStateException("No search option selected for view search");
				wheres.append(')');
				break;
			case priority:
				PrioritySearch priS = (PrioritySearch) search;
				wheres.append(theTransactor.getTablePrefix());
				wheres.append("prisms_message.priority=");
				wheres.append(priS.priority.ordinal());
				break;
			case content:
				ContentSearch contS = (ContentSearch) search;
				int maxSize = Math.round(theBlockSize / 2 * 0.9f);
				if(contS.search.length() > maxSize)
					throw new IllegalArgumentException("Content searches may be no longer than "
						+ maxSize + " characters in length");
				if(joins.indexOf("msg_content") < 0)
				{
					joins.append("INNER JOIN ");
					joins.append(theTransactor.getTablePrefix());
					joins.append("prisms_message_content msg_content ON ");
					joins.append("msg_content.messageNS=msg.messageNS AND ");
					joins.append("msg_content.message=msg.id ");
				}
				wheres.append(DBUtils.getLowerFn(type));
				wheres.append("(msg_content.content) LIKE %");
				wheres.append(DBUtils.toSQL(contS.search.toLowerCase()));
				wheres.append('%');
				break;
			case attachment:
				AttachmentSearch attS = (AttachmentSearch) search;
				if(joins.indexOf("msg_attach") < 0)
				{
					joins.append("INNER JOIN ");
					joins.append(theTransactor.getTablePrefix());
					joins.append("prisms_message_attachment msg_attach ON ");
					joins.append("msg_attach.messageNS=msg.messageNS AND ");
					joins.append("msg_attach.message=msg.id ");
				}

				if(attS.name != null)
				{
					wheres.append(DBUtils.getLowerFn(type));
					wheres.append('(');
					wheres.append("msg_attach.attachName) LIKE %");
					wheres.append(DBUtils.toSQL(attS.name.toLowerCase()));
					wheres.append('%');
				}
				else if(attS.attachType != null)
				{
					wheres.append(DBUtils.getLowerFn(type));
					wheres.append('(');
					wheres.append("msg_attach.contentType) LIKE %");
					wheres.append(DBUtils.toSQL(attS.attachType.toLowerCase()));
					wheres.append('%');
				}
				else if(attS.size != null)
				{
					StringBuilder column = new StringBuilder();
					column.append(DBUtils.getLobLengthFn(type));
					column.append('(');
					column.append("msg.attach.content)");
					createSizeQuery(attS.size, column.toString(), wheres);
				}
				else
					throw new IllegalStateException("Unrecognized attachment search type: "
						+ search);
				break;
			case size:
				SizeSearch sizeS = (SizeSearch) search;
				createSizeQuery(sizeS, "msg.messageSize", wheres);
				break;
			}
		}
	}

	private void joinRecipient(StringBuilder joins, StringBuilder wheres, String userName, User user)
	{
		if(joins.indexOf("msg_recip") < 0)
		{
			joins.append("INNER JOIN ");
			joins.append(theTransactor.getTablePrefix());
			joins.append("prisms_message_recipient msg_recip ON ");
			joins.append("msg_recip.messageNS=msg.messageNS AND msg_recip.message=msg.id ");
		}
		if(userName != null)
		{
			if(joins.indexOf("msg_recip_user") < 0)
			{
				joins.append("INNER JOIN ");
				joins.append(theTransactor.getTablePrefix());
				joins.append("prisms_user msg_recip_user ON ");
				joins.append("msg_recip.recipient=msg_recip_user.id ");
			}

			wheres.append("msg_recip_user.userName=");
			wheres.append(DBUtils.toSQL(userName));
		}
		else
		{
			wheres.append("msg_recip.recipient=");
			wheres.append(user.getID());
		}
	}

	private void joinView(StringBuilder joins, StringBuilder wheres, String userName, User user)
	{
		if(joins.indexOf("conv_view") < 0)
		{
			joins.append("INNER JOIN ");
			joins.append(theTransactor.getTablePrefix());
			joins.append("prisms_conversation_view conv_view ON ");
			joins
				.append("conv_view.messageNS=msg.messageNS AND conv_view.conversation=msg.conversation");
		}
		if(userName != null)
		{
			if(joins.indexOf("conv_view_user") < 0)
			{
				joins.append("INNER JOIN ");
				joins.append(theTransactor.getTablePrefix());
				joins.append("prisms_user conv_view_user ON ");
				joins.append("conv_view.viewer=conv_view_user.id ");
			}

			wheres.append("msg_view_user.userName=");
			wheres.append(DBUtils.toSQL(userName));
		}
		else
		{
			wheres.append("conv_view.viewer=");
			wheres.append(user.getID());
		}
	}

	private void createDateQuery(MessageSearch.Operator operator, MessageSearch.SearchDate date,
		DBUtils.ConnType type, String column, StringBuilder wheres)
	{
		switch(operator)
		{
		case EQ:
			wheres.append(column);
			wheres.append(">=");
			wheres.append(DBUtils.formatDate(date.minTime, type == DBUtils.ConnType.ORACLE));
			wheres.append(" AND ");
			wheres.append(column);
			wheres.append("<=");
			wheres.append(DBUtils.formatDate(date.maxTime, type == DBUtils.ConnType.ORACLE));
			break;
		case NEQ:
			wheres.append(column);
			wheres.append("<=");
			wheres.append(DBUtils.formatDate(date.minTime, type == DBUtils.ConnType.ORACLE));
			wheres.append(" OR ");
			wheres.append(column);
			wheres.append(">=");
			wheres.append(DBUtils.formatDate(date.maxTime, type == DBUtils.ConnType.ORACLE));
			break;
		case GT:
			wheres.append(column);
			wheres.append(">=");
			wheres.append(DBUtils.formatDate(date.maxTime, type == DBUtils.ConnType.ORACLE));
			break;
		case GTE:
			wheres.append(column);
			wheres.append(">=");
			wheres.append(DBUtils.formatDate(date.minTime, type == DBUtils.ConnType.ORACLE));
			break;
		case LT:
			wheres.append(column);
			wheres.append("<=");
			wheres.append(DBUtils.formatDate(date.minTime, type == DBUtils.ConnType.ORACLE));
			break;
		case LTE:
			wheres.append(column);
			wheres.append("<=");
			wheres.append(DBUtils.formatDate(date.maxTime, type == DBUtils.ConnType.ORACLE));
			break;
		}
	}

	private void createSizeQuery(SizeSearch sizeS, String column, StringBuilder wheres)
	{
		float size = sizeS.size;
		for(int i = 0; i < sizeS.exp; i++)
			size *= 10;
		switch(sizeS.operator)
		{
		case EQ:
			wheres.append('(');
			wheres.append(column);
			wheres.append(">=");
			wheres.append(size * .9f);
			wheres.append(" AND ");
			wheres.append(column);
			wheres.append("<=");
			wheres.append(size * 1.1f);
			wheres.append(')');
			break;
		case NEQ:
			wheres.append('(');
			wheres.append(column);
			wheres.append("<");
			wheres.append(size * .9f);
			wheres.append(" OR ");
			wheres.append(column);
			wheres.append(">");
			wheres.append(size * 1.1f);
			wheres.append(')');
			break;
		case GT:
		case LT:
			wheres.append(column);
			wheres.append(">");
			wheres.append(size);
			break;
		case GTE:
			wheres.append(column);
			wheres.append(">=");
			wheres.append(size * .9f);
			break;
		case LTE:
			wheres.append(column);
			wheres.append("<=");
			wheres.append(size * 1.1f);
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
}
