/*
 * MessageView.java Created Jan 10, 2011 by Andrew Butler, PSL
 */
package prisms.message;

/**
 * A user's view of a message. Each user keeps their own settings for each message that can be
 * modified without affecting any other users' views.
 */
public class MessageView
{
	private long theID;

	private ConversationView theConversation;

	private Message theMessage;

	private boolean isDeleted;

	/**
	 * Creates a message view
	 * 
	 * @param conversation The conversation view that this message view is a part of
	 * @param message The message that this view is for
	 */
	public MessageView(ConversationView conversation, Message message)
	{
		theID = -1;
		theConversation = conversation;
		theMessage = message;
	}

	/** @return This view's ID */
	public long getID()
	{
		return theID;
	}

	/** @param id The database ID for this view */
	public void setID(long id)
	{
		theID = id;
	}

	/** @return The conversation that this message is a part of */
	public ConversationView getConversation()
	{
		return theConversation;
	}

	/** @return The message that this view is for */
	public Message getMessage()
	{
		return theMessage;
	}

	/**
	 * This method should only be used by an implementation of {@link MessageManager}
	 * 
	 * @param msg The message that this view is for
	 */
	public void setMessage(Message msg)
	{
		theMessage = msg;
	}

	/** @return Whether the user has deleted this message */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	/** @param deleted Whether the user has deleted this message */
	public void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof MessageView && ((MessageView) o).theID == theID;
	}

	@Override
	public int hashCode()
	{
		return ((int) theID) ^ ((int) (theID >>> 32));
	}
}
