/*
 * Conversation.java Created Feb 15, 2011 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.arch.ds.User;

/** Represents a user's view of a conversation */
public class ConversationView implements Iterable<MessageHeader>
{
	private long theID;

	private final User theViewer;

	private boolean isArchived;

	private boolean isStarred;

	private java.util.ArrayList<MessageHeader> theMessages;

	/** @param viewer The user that this conversation view is for */
	public ConversationView(User viewer)
	{
		theViewer = viewer;
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

	/**
	 * @param index The index of the message to get
	 * @return The message in this conversation at the given index
	 */
	public MessageHeader getMessage(int index)
	{
		return theMessages.get(index);
	}

	public java.util.ListIterator<MessageHeader> iterator()
	{
		return theMessages.listIterator();
	}

	/** @param message The message to add to this conversation */
	public void addMessage(MessageHeader message)
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
}
