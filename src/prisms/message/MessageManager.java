/*
 * MessageManager.java Created Sep 30, 2010 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.records.RecordsTransaction;

/** A MessageManager is a repository for PRISMS messages */
public interface MessageManager extends
	prisms.util.SearchableAPI<Message, prisms.util.Sorter.Field, PrismsMessageException>
{
	/** @return An API that allows searching for message views instead of messages */
	prisms.util.SearchableAPI<MessageView, prisms.util.Sorter.Field, PrismsMessageException> views();

	/**
	 * Gets a message with all the content it has ever had, including deleted recipients and
	 * attachments
	 * 
	 * @param id The ID of the message to get
	 * @return The message with all its content
	 * @throws PrismsMessageException If an error occurs retrieving the data
	 */
	Message getDBMessage(long id) throws PrismsMessageException;

	/**
	 * Retrieves the content of a message
	 * 
	 * @param message The message to get the content of
	 * @param length The number of characters in the content to get. Use -1 to retrieve entire
	 *        content.
	 * @return The content of the message with at least the given length or the full length for the
	 *         message (whichever is shorter)
	 * @throws PrismsMessageException If an error occurs retrieving the data
	 */
	String getContent(Message message, int length) throws PrismsMessageException;

	/**
	 * Gets an attachment's content
	 * 
	 * @param attach The attachment to get the content of
	 * @return An input stream to retrieve the attachment's content
	 * @throws PrismsMessageException If an error occurs retrieving the data or if the attachment
	 *         does not exist in the store
	 */
	java.io.InputStream getAttachmentContent(Attachment attach) throws PrismsMessageException;

	/**
	 * Retrieves a user's view of one or more conversations
	 * 
	 * @param viewer The user to get the message views for
	 * @param messages The messages to get the views for
	 * @return The user's message views for the messages, in the same order as the messages
	 *         parameter. If the user does not have a view of a given message, the view at that
	 *         index will be null.
	 * @throws PrismsMessageException
	 */
	MessageView [] getMessageViews(prisms.arch.ds.User viewer, Message... messages)
		throws PrismsMessageException;

	/**
	 * Gets conversations whose messages matched a search
	 * 
	 * @param messageViewIDs The IDs of the message views to get the conversations for
	 * @return The conversations (complete with messages) of the given message views. The result is
	 *         ordered by the time of the most recent message in the conversation (most recent
	 *         first).
	 * @throws PrismsMessageException If an error occurs retrieving the data
	 */
	ConversationHolder [] getConversations(long... messageViewIDs) throws PrismsMessageException;

	/**
	 * Stores a new message or modifies one previously created
	 * 
	 * @param message The message to create or modify
	 * @param trans The transaction for record-keeping
	 * @throws PrismsMessageException If an error occurs storing the data
	 */
	void putMessage(Message message, RecordsTransaction trans) throws PrismsMessageException;

	/**
	 * Modifies a message view
	 * 
	 * @param msg The message view to modify
	 * @param trans The transaction for record-keeping
	 * @throws PrismsMessageException If an error occurs storing the data
	 */
	void putMessageView(MessageView msg, RecordsTransaction trans) throws PrismsMessageException;

	/**
	 * Creates or modifies a user's view of a conversation
	 * 
	 * @param view The view to create or modify
	 * @param trans The transaction for record-keeping
	 * @throws PrismsMessageException If an error occurs storing the data
	 */
	void putConversation(ConversationView view, RecordsTransaction trans)
		throws PrismsMessageException;

	/**
	 * Attaches data to a message
	 * 
	 * @param message The message to attach data to
	 * @param name The name of the attachment
	 * @param type The type of the attachment (preferably MIME)
	 * @param data The input stream to get the attachment's content from
	 * @param trans The transaction for record-keeping
	 * @return The new attachment
	 * @throws PrismsMessageException If an error occurs storing the data
	 * @throws java.io.IOException If an error occurs reading the attachment's content
	 */
	Attachment createAttachment(Message message, String name, String type,
		java.io.InputStream data, RecordsTransaction trans) throws PrismsMessageException,
		java.io.IOException;

	/** Releases this manager's resources */
	void disconnect();
}
