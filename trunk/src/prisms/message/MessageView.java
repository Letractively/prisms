/*
 * MessageView.java Created Jan 10, 2011 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.arch.ds.User;

/**
 * A user's view of a message. Each user keeps their own settings for each message that can be
 * modified without affecting any other users' views.
 */
public class MessageView
{
	private final MessageHeader theMessage;

	private final User theViewer;

	private boolean isArchived;

	private boolean isStarred;

	private boolean isDeleted;

	/**
	 * Creates a message view
	 * 
	 * @param message The message that this view is for
	 * @param viewer The user that this view is for
	 */
	public MessageView(MessageHeader message, User viewer)
	{
		theMessage = message;
		theViewer = viewer;
	}

	/** @return The message that this view is for */
	public MessageHeader getMessage()
	{
		return theMessage;
	}

	/** @return The user that this message view is for */
	public User getViewer()
	{
		return theViewer;
	}

	/**
	 * @return Whether the viewer has archived the message so that it no longer shows up in their
	 *         inbox
	 */
	public boolean isArchived()
	{
		return isArchived;
	}

	/**
	 * @param archived Whether the viewer has archived the message so that it no longer shows up in
	 *        their inbox
	 */
	public void setArchived(boolean archived)
	{
		isArchived = archived;
	}

	/** @return Whether the user has marked this message as starred */
	public boolean isStarred()
	{
		return isStarred;
	}

	/** @param starred Whether the user has marked this message as starred */
	public void setStarred(boolean starred)
	{
		isStarred = starred;
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
}
