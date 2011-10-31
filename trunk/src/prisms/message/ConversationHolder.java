/*
 * ConversationHolder.java Created Oct 20, 2011 by Andrew Butler, PSL
 */
package prisms.message;


/** Holds a conversation and its messages */
public class ConversationHolder implements Iterable<MessageView>
{
	private final ConversationView theConversation;

	private java.util.TreeSet<MessageView> theMessages;

	private prisms.util.LongList theMatched;

	/** @param conversation The conversation view that this holder is for */
	public ConversationHolder(ConversationView conversation)
	{
		theConversation = conversation;
		theMessages = new java.util.TreeSet<MessageView>(new java.util.Comparator<MessageView>()
		{
			public int compare(MessageView o1, MessageView o2)
			{
				long diff = o1.getMessage().getTime() - o2.getMessage().getTime();
				return diff < 0 ? -1 : diff > 0 ? 1 : 0;
			}
		});
		theMatched = new prisms.util.LongList();
	}

	/** @return The conversation view that this holder is for */
	public ConversationView getConversation()
	{
		return theConversation;
	}

	/**
	 * @param msg The message to add to this conversation
	 * @param match Whether the given message was searched for specifically
	 */
	public void addMessage(MessageView msg, boolean match)
	{
		theMessages.add(msg);
		if(match)
			theMatched.add(msg.getMessage().getID());
	}

	/** @param msg the message to remove from this conversation */
	public void removeMessage(MessageView msg)
	{
		theMessages.remove(msg);
		theMatched.removeValue(msg.getMessage().getID());
	}

	/** @return The number of messages in this conversation */
	public int getMessageCount()
	{
		return theMessages.size();
	}

	/** @return The first (earliest) message in this conversation */
	public MessageView getFirstMessage()
	{
		if(theMessages.isEmpty())
			return null;
		else
			return theMessages.first();
	}

	/** @return The last (most recent) message in this conversation */
	public MessageView getLastMessage()
	{
		if(theMessages.isEmpty())
			return null;
		else
			return theMessages.last();
	}

	public java.util.Iterator<MessageView> iterator()
	{
		return theMessages.iterator();
	}

	/**
	 * @param msg The message to check
	 * @return Whether the given message was searched for specifically in the search that returned
	 *         this conversation
	 */
	public boolean isMatched(MessageView msg)
	{
		return theMatched.contains(msg.getMessage().getID());
	}
}
