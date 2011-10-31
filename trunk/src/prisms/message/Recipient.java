/*
 * Recipient.java Created Sep 30, 2010 by Andrew Butler, PSL
 */
package prisms.message;

/** Represents a receipient of a PRISMS message */
public class Recipient implements Cloneable
{
	/** How the message applies to the recipient */
	public static enum Applicability
	{
		/** The recipient is in the To: line of the message */
		DIRECT("To"),
		/** The recipient is in the Cc: line of the message */
		COPIED("Cc"),
		/** The recipient is in the Bcc: line of the message */
		BLIND("Bcc");

		/** The string to display for this applicability */
		public final String display;

		private Applicability(String disp)
		{
			display = disp;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	private Message theMessage;

	private final prisms.arch.ds.User theUser;

	private long theID;

	private Applicability theApplicability;

	private long theFirstViewed;

	private long theLastViewed;

	private boolean isDeleted;

	/**
	 * Creates a recipient. This is public for flexibility for future implementations, but it should
	 * only be used by the {@link Message#addRecipient(prisms.arch.ds.User)} method of a
	 * {@link Message} extension.
	 * 
	 * @param header The message to be sent
	 * @param user The user to send the message to
	 */
	public Recipient(Message header, prisms.arch.ds.User user)
	{
		theMessage = header;
		theUser = user;
		theID = -1;
	}

	/** @return This recipient storage ID */
	public long getID()
	{
		return theID;
	}

	/** @param id The storage ID for this recipient */
	public void setID(long id)
	{
		theID = id;
	}

	/** @return The message that is to be sent */
	public Message getMessage()
	{
		return theMessage;
	}

	/** @return The user that the message is to be sent to */
	public prisms.arch.ds.User getUser()
	{
		return theUser;
	}

	/**
	 * @return How the message applies to the user
	 * @see Applicability#DIRECT
	 * @see Applicability#COPIED
	 * @see Applicability#BLIND
	 */
	public Applicability getApplicability()
	{
		return theApplicability;
	}

	/**
	 * @param app How the message applies to the user
	 * @see Applicability#DIRECT
	 * @see Applicability#COPIED
	 * @see Applicability#BLIND
	 */
	public void setApplicability(Applicability app)
	{
		theApplicability = app;
	}

	/**
	 * @return The first time this message was viewed by the user, or <0 if the user has not viewed
	 *         the message yet
	 */
	public long getFirstViewed()
	{
		return theFirstViewed;
	}

	/**
	 * @param time The first time this message was viewed by the user, or <0 if the user has not
	 *        viewed the message yet
	 */
	public void setFirstViewed(long time)
	{
		theFirstViewed = time;
	}

	/**
	 * @return The most recent time this message was viewed by the user, or <0 if the user has not
	 *         viewed the message yet
	 */
	public long getLastViewed()
	{
		return theLastViewed;
	}

	/**
	 * @param time The most recent time this message was viewed by the user, or <0 if the user has
	 *        not viewed the message yet
	 */
	public void setLastViewed(long time)
	{
		theLastViewed = time;
	}

	/** @return Whether this recipient has been removed from its message or not */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	/** @param deleted Whether this recipient has been removed from its message or not */
	public void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	/**
	 * Clones this recipient for a clone of this recipient's message
	 * 
	 * @param msg The message to clone this recipient for
	 * @return The cloned recipient
	 */
	Recipient clone(Message msg)
	{
		Recipient ret;
		try
		{
			ret = (Recipient) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("Recipient.clone not supported", e);
		}
		ret.theMessage = msg;
		return ret;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Recipient && ((Recipient) o).theID == theID;
	}

	@Override
	public int hashCode()
	{
		return ((int) theID) ^ ((int) (theID >>> 32));
	}

	@Override
	public String toString()
	{
		return theApplicability + ": " + theUser;
	}
}
