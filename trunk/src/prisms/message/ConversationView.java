/*
 * ConversationView.java Created Feb 15, 2011 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.arch.ds.User;

/** Represents a user's view of a conversation */
public class ConversationView implements Iterable<MessageView>
{
	private long theID;

	private long theConversationID;

	private final User theViewer;

	private boolean isArchived;

	private boolean isStarred;

	private java.util.ArrayList<MessageView> theMessages;

	/**
	 * @param conversationID The ID of the conversation that this is to be a view of
	 * @param viewer The user that this conversation view is for
	 */
	public ConversationView(long conversationID, User viewer)
	{
		theConversationID = conversationID;
		theViewer = viewer;
		theMessages = new java.util.ArrayList<MessageView>();
		theID = -1;
	}

	/** @return This conversation's ID */
	public long getID()
	{
		return theID;
	}

	/** @param id The ID for this conversation */
	public void setID(long id)
	{
		theID = id;
	}

	/** @return The ID of the conversation that this is a view of */
	public long getConversationID()
	{
		return theConversationID;
	}

	/** @return The user that this conversation view is for */
	public User getViewer()
	{
		return theViewer;
	}

	/** @return The number of messages in this conversation view */
	public int getMessageCount()
	{
		return theMessages.size();
	}

	public java.util.ListIterator<MessageView> iterator()
	{
		return theMessages.listIterator();
	}

	/** @param message The message to add to this conversation */
	public void addMessage(MessageView message)
	{
		theMessages.add(message);
	}

	/**
	 * @return Whether the viewer has archived the conversation so that it no longer shows up in
	 *         their inbox
	 */
	public boolean isArchived()
	{
		return isArchived;
	}

	/**
	 * @param archived Whether the viewer has archived the conversation so that it no longer shows
	 *        up in their inbox
	 */
	public void setArchived(boolean archived)
	{
		isArchived = archived;
	}

	/** @return Whether the user has marked this conversation as starred */
	public boolean isStarred()
	{
		return isStarred;
	}

	/** @param starred Whether the user has marked this conversation as starred */
	public void setStarred(boolean starred)
	{
		isStarred = starred;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof ConversationView && ((ConversationView) o).theID == theID;
	}

	@Override
	public int hashCode()
	{
		return ((int) theID) ^ ((int) (theID >>> 32));
	}
}
