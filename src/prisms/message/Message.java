/*
 * Message.java Created Sep 30, 2010 by Andrew Butler, PSL
 */
package prisms.message;

import java.util.Iterator;

import prisms.util.ArrayUtils;

/** All metadata attached to a PRISMS message */
public class Message implements Cloneable
{
	/** The set of priorities with which a message can be sent */
	public static enum Priority
	{
		/** Low priority */
		LOW,
		/** Mid priority */
		MEDIUM,
		/** High priority */
		HIGH;
	}

	private long theID;

	private MessageManager theManager;

	private final prisms.arch.ds.User theAuthor;

	private long theConversation;

	private long theTime;

	private boolean isSent;

	private Recipient [] theRecipients;

	private Priority thePriority;

	private String theSubject;

	private Attachment [] theAttachments;

	private MessageAction [] theActions;

	private long thePredecessorID;

	private int theContentLength;

	private long theContentCRC;

	private String theContent;

	private int theContentBuffer;

	private int theSize;

	private boolean isDeleted;

	/**
	 * Creates a new message
	 * 
	 * @param manager The message manager that is the source of this message
	 * @param author The creator of this message
	 */
	public Message(MessageManager manager, prisms.arch.ds.User author)
	{
		theID = -1;
		theManager = manager;
		theAuthor = author;
		thePriority = Priority.LOW;
		thePredecessorID = -1;
		theSubject = "";
		theRecipients = new Recipient [0];
		theAttachments = new Attachment [0];
		theActions = new MessageAction [0];
	}

	/** @return The user that created this message */
	public prisms.arch.ds.User getAuthor()
	{
		return theAuthor;
	}

	/** @return This message's storage ID */
	public long getID()
	{
		return theID;
	}

	/** @param id The storage ID for this message */
	public void setID(long id)
	{
		theID = id;
	}

	/** @return The ID of the conversation that this message is associated with */
	public long getConversationID()
	{
		return theConversation;
	}

	/**
	 * This method should ONLY be called from this message's data source
	 * 
	 * @param id The ID of the conversation to associate this message with.
	 */
	public void setConversationID(long id)
	{
		theConversation = id;
	}

	/**
	 * @return If this message has been sent, this is the time it was sent; otherwise it is the time
	 *         the message was created.
	 */
	public long getTime()
	{
		return theTime;
	}

	/**
	 * Sets the time this message was sent or the time the message was created. This is public for
	 * flexibility with external implementations, but should only be used by methods in an
	 * implementation of {@link MessageManager}. External code should always use {@link #send()} to
	 * send the message and should not otherwise modify the time.
	 * 
	 * @param time The time this message was sent or created
	 */
	public void setTime(long time)
	{
		theTime = time;
	}

	/** @return Whether this message has been sent or is still in draft form */
	public boolean isSent()
	{
		return isSent;
	}

	/**
	 * Sets whether the message has been sent. This is public for flexibility with external
	 * implementations, but should only be used by methods in an implementation of
	 * {@link MessageManager}. External code should always use {@link #send()} to send the message.
	 * 
	 * @param sent Whether the message is sent
	 */
	public void setSent(boolean sent)
	{
		isSent = sent;
	}

	/** Marks this message as sent and deliverable to its receipients */
	public void send()
	{
		if(isSent)
			return;
		isSent = true;
		theTime = System.currentTimeMillis();
	}

	/** @return The number of recipients of the message */
	public int getReceiptCount()
	{
		return theRecipients.length;
	}

	static class AbstractIterable<T> implements Iterable<T>
	{
		class AbstractIterator implements java.util.Iterator<T>
		{
			private int theIndex;

			public boolean hasNext()
			{
				return theIndex < theItems.length;
			}

			public T next()
			{
				return theItems[theIndex++];
			}

			public void remove()
			{
				throw new UnsupportedOperationException(
					"remove() is not supported for this iterator");
			}
		}

		final T [] theItems;

		AbstractIterable(T [] items)
		{
			theItems = items;
		}

		public Iterator<T> iterator()
		{
			return new AbstractIterator();
		}
	}

	/** @return An iterable through the recipients of this message */
	public Iterable<Recipient> recipients()
	{
		return new AbstractIterable<Recipient>(theRecipients);
	}

	/**
	 * @param user The recipient to add
	 * @return The metadata that allows external code to set data about the recipient
	 */
	public Recipient addRecipient(prisms.arch.ds.User user)
	{
		Recipient ret = new Recipient(this, user);
		theRecipients = ArrayUtils.add(theRecipients, ret);
		return ret;
	}

	/** @param receipt The recipient to remove from the message */
	public void removeReceipient(Recipient receipt)
	{
		theRecipients = ArrayUtils.remove(theRecipients, receipt);
	}

	/** @return The priority with which this message was or is to be sent */
	public Priority getPriority()
	{
		return thePriority;
	}

	/** @param pri The priority with which this message is to be sent */
	public void setPriority(Priority pri)
	{
		thePriority = pri;
	}

	/** @return The subject of the message */
	public String getSubject()
	{
		return theSubject;
	}

	/** @param subject The subject for the message */
	public void setSubject(String subject)
	{
		if(subject == null)
			subject = "";
		theSubject = subject;
	}

	/** @return The number of attachments to this message */
	public int getAttachmentCount()
	{
		return theAttachments.length;
	}

	/** @return An iterable through the attachments of this message */
	public Iterable<Attachment> attachments()
	{
		return new AbstractIterable<Attachment>(theAttachments);
	}

	/**
	 * @param index The index of the attachment to get
	 * @return The attachment at the given index
	 */
	public Attachment getAttachment(int index)
	{
		return theAttachments[index];
	}

	/**
	 * Adds an attachment to this mesage. This is public for flexibility with external
	 * implementations, but should only be used by the
	 * {@link MessageManager#createAttachment(Message, String, String, java.io.InputStream, prisms.records.RecordsTransaction)}
	 * method of a {@link MessageManager} implementation.
	 * 
	 * @param attach The attachment to add
	 */
	public void addAttachment(Attachment attach)
	{
		theAttachments = ArrayUtils.add(theAttachments, attach);
	}

	/**
	 * Removes an attachment from this message
	 * 
	 * @param attach The attachment to remove
	 */
	public void removeAttachment(Attachment attach)
	{
		theAttachments = ArrayUtils.remove(theAttachments, attach);
	}

	/** @return The number of actions in this message */
	public int getActionCount()
	{
		return theActions.length;
	}

	/** @return An iterable through the attachments of this message */
	public Iterable<MessageAction> actions()
	{
		return new AbstractIterable<MessageAction>(theActions);
	}

	/** @param action The action to add to this message */
	public void addAction(MessageAction action)
	{
		theActions = ArrayUtils.add(theActions, action);
	}

	/** @param action The action to remove from this message */
	public void removeAction(MessageAction action)
	{
		theActions = ArrayUtils.remove(theActions, action);
	}

	/** @return The ID of this message's predecessor */
	public long getPredecessorID()
	{
		return thePredecessorID;
	}

	/** @param id The ID of this message's predecessor */
	public void setPredecessorID(long id)
	{
		thePredecessorID = id;
	}

	/** @return The length of this message's content */
	public int getLength()
	{
		return theContentLength;
	}

	/** @param length The length of this message's content */
	public void setLength(int length)
	{
		theContentLength = length;
	}

	/** @return The CRC code of this message's content */
	public long getContentCRC()
	{
		return theContentCRC;
	}

	/** @param crc The CRC code of this message's content */
	public void setContentCRC(long crc)
	{
		theContentCRC = crc;
	}

	/**
	 * @param buffer The number of characters of the message's content to retrieve, or <=0 to
	 *        retrieve the entire message's content
	 * @return This message's content, out to the given number of characters
	 * @throws PrismsMessageException If the information has not yet been retrieved for this message
	 *         and retrieval from the message manager fails
	 */
	public String getContent(int buffer) throws PrismsMessageException
	{
		if(theContentBuffer < 0 || (buffer > 0 && buffer <= theContentBuffer))
			return theContent;
		theContent = theManager.getContent(this, buffer);
		if(buffer <= 0 || theContent.length() < buffer)
			theContentBuffer = -1;
		else
			theContentBuffer = theContent.length();
		return theContent;
	}

	/** @param content The content for this message */
	public void setContent(String content)
	{
		if(content == null)
			content = "";
		theContent = content;
		theContentBuffer = theContent.length();
		setContentCRC(getCRC(theContent));
		setLength(theContent.length());
	}

	/**
	 * Gets the CRC code of a message
	 * 
	 * @param content The content of the message to get the CRC code of
	 * @return The CRC code of the given string
	 */
	public static long getCRC(String content)
	{
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
			new java.io.OutputStreamWriter(crcOut).write(content);
		} catch(java.io.IOException e)
		{
			throw new IllegalStateException("Could not check CRC of message");
		}
		return crc.getValue();
	}

	/** @return The approximate number of kilobytes this message takes up */
	public int getSize()
	{
		return theSize;
	}

	/** @param size The approximate number of kilobytes this message takes up */
	public void setSize(int size)
	{
		theSize = size;
	}

	/** @return Whether this message has been deleted */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	/** @param deleted Whether this message has been deleted */
	public void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	@Override
	public Message clone()
	{
		Message ret;
		try
		{
			ret = (Message) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("Clone not supported", e);
		}
		for(int i = 0; i < ret.theRecipients.length; i++)
			ret.theRecipients[i] = ret.theRecipients[i].clone(this);
		for(int i = 0; i < ret.theAttachments.length; i++)
			ret.theAttachments[i] = ret.theAttachments[i].clone(this);
		for(int i = 0; i < ret.theActions.length; i++)
			ret.theActions[i] = ret.theActions[i].clone(this);
		return ret;
	}

	@Override
	public String toString()
	{
		return theAuthor + ": " + theSubject;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Message && ((Message) o).theID == theID;
	}

	@Override
	public int hashCode()
	{
		return ((int) theID) ^ ((int) (theID >>> 32));
	}
}
