/*
 * Header.java Created Sep 30, 2010 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.util.ArrayUtils;

/** All metadata attached to a PRISMS message */
public class MessageHeader
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

	private final prisms.arch.ds.User theAuthor;

	private long theID;

	private long theConversation;

	private long theTime;

	private boolean isSent;

	private Receipt [] theRecipients;

	private Priority thePriority;

	private String theSubject;

	private Attachment [] theAttachments;

	private long thePredecessorID;

	private boolean isOverride;

	private int theLength;

	private long theCRC;

	/**
	 * Creates a new message
	 * 
	 * @param author The creator of this message
	 */
	public MessageHeader(prisms.arch.ds.User author)
	{
		theAuthor = author;
		thePriority = Priority.LOW;
		thePredecessorID = -1;
		theSubject = "";
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

	/**
	 * @param index The index of the recipient to get
	 * @return The recipient at the given index
	 */
	public Receipt getReceipt(int index)
	{
		return theRecipients[index];
	}

	/**
	 * @param user The recipient to add
	 * @return The metadata that allows external code to set data about the recipient
	 */
	public Receipt addRecipient(prisms.arch.ds.User user)
	{
		Receipt ret = new Receipt(this, user);
		theRecipients = ArrayUtils.add(theRecipients, ret);
		return ret;
	}

	/** @param receipt The recipient to remove from the message */
	public void removeReceipient(Receipt receipt)
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
	 * {@link MessageManager#createAttachment(MessageHeader, String, String, java.io.InputStream, prisms.records.RecordsTransaction)}
	 * method of a {@link MessageManager} implementation.
	 * 
	 * @param attach The attachment to add
	 */
	public void addAttachment(Attachment attach)
	{
		theAttachments = ArrayUtils.add(theAttachments, attach);
	}

	/**
	 * Removes an attachment from this message. This is public for flexibility with external
	 * implementations, but should only be used by the
	 * {@link MessageManager#deleteAttachment(Attachment, prisms.records.RecordsTransaction)} method
	 * of a {@link MessageManager} implementation.
	 * 
	 * @param attach The attachment to remove
	 */
	public void removeAttachment(Attachment attach)
	{
		theAttachments = ArrayUtils.remove(theAttachments, attach);
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

	/** @return Whether this message overrides the importance of its predecessor */
	public boolean isOverride()
	{
		return isOverride;
	}

	/** @param override Whether this message overrides the importance of its predecessor */
	public void setOverride(boolean override)
	{
		isOverride = override;
	}

	/** @return The length of this message's content */
	public int getLength()
	{
		return theLength;
	}

	/** @param length The length of this message's content */
	public void setLength(int length)
	{
		theLength = length;
	}

	/** @return The CRC code of this message's content */
	public long getCRC()
	{
		return theCRC;
	}

	/** @param crc The CRC code of this message's content */
	public void setCRC(long crc)
	{
		theCRC = crc;
	}
}
