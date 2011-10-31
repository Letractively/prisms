/*
 * MessageAction.java Created Oct 11, 2011 by Andrew Butler, PSL
 */
package prisms.message;

/** Represents an action that a user needs to perform to satisfy a message */
public class MessageAction
{
	private long theID;

	private Message theMessage;

	private String theActionDescrip;

	private prisms.arch.ds.User theCompletedUser;

	private boolean isDeleted;

	/** @param msg The message that this action is for */
	public MessageAction(Message msg)
	{
		theMessage = msg;
		theID = -1;
	}

	/** @return This action's ID */
	public long getID()
	{
		return theID;
	}

	/** @param id The ID for this action */
	public void setID(long id)
	{
		theID = id;
	}

	/** @return The message that this action is for */
	public Message getMessage()
	{
		return theMessage;
	}

	/**
	 * @return The description of this action. This is typically a parseable string that can be
	 *         interpreted by the host application to tell the user what needs to be done to satisfy
	 *         the action.
	 */
	public String getDescrip()
	{
		return theActionDescrip;
	}

	/**
	 * @param descrip The description for this action
	 * @see #getDescrip()
	 */
	public void setDescrip(String descrip)
	{
		theActionDescrip = descrip;
	}

	/** @return The user that completed this action, or null if this action has not been completed */
	public prisms.arch.ds.User getCompletedUser()
	{
		return theCompletedUser;
	}

	/** @param user The user that has completed this action */
	public void setCompletedUser(prisms.arch.ds.User user)
	{
		theCompletedUser = user;
	}

	/** @return Whether this action has been deleted from its message */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	/** @param deleted Whether this action has been deleted from is message */
	public void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	/**
	 * Clones this action for a clone of this action's message
	 * 
	 * @param msg The message to clone this action for
	 * @return The cloned action
	 */
	MessageAction clone(Message msg)
	{
		MessageAction ret;
		try
		{
			ret = (MessageAction) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("MessageAction.clone not supported", e);
		}
		ret.theMessage = msg;
		return ret;
	}
}
