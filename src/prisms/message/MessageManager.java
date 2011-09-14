/*
 * MessageManager.java Created Sep 30, 2010 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.arch.ds.User;
import prisms.records.RecordsTransaction;
import prisms.util.Search;

/** A MessageManager is a repository for PRISMS messages */
public interface MessageManager
{
	/**
	 * Gets messages from this manager by their IDs
	 * 
	 * @param ids The IDs of the messages to get
	 * @return All messages with the given IDs, in the order of the IDs given
	 * @throws PrismsMessageException If an error occurs retrieving the data
	 */
	MessageHeader [] getMessages(long... ids) throws PrismsMessageException;

	/**
	 * Gets conversations from this manager by their IDs
	 * 
	 * @param viewer The viewer to get the conversations for
	 * @param ids The IDs of the conversations to get
	 * @return All conversations with the given IDs, in the order of the IDs given
	 * @throws PrismsMessageException If an error occurs retrieving the data
	 */
	ConversationView [] getConversations(User viewer, long... ids) throws PrismsMessageException;

	/**
	 * Searches for messages in this manager
	 * 
	 * @param search The search to search messages with
	 * @return The IDs of all messages within this manager's data source that match the given search
	 * @throws PrismsMessageException If an error occurs retrieving the data
	 */
	long [] getMessages(Search search) throws PrismsMessageException;

	/**
	 * Searches for conversations in this manager
	 * 
	 * @param search The search to search conversations with
	 * @return The IDs of all conversations within this manager's data source that have one or more
	 *         messages that match the given search
	 * @throws PrismsMessageException If an error occurs retrieving the data
	 */
	long [] getConversations(Search search) throws PrismsMessageException;

	/**
	 * Retrieves a user's view of one or more conversations
	 * 
	 * @param viewer The user to get the conversation views for
	 * @param messages The messages to get the conversation views for
	 * @return The user's conversation views for the messages, in the same order as the messages
	 *         parameter. If two messages are in the same conversation, that conversation will only
	 *         appear once in the return array, at the index of the first message in the
	 *         conversation. If the user does not have a view of a given message, the view at that
	 *         index will be null.
	 * @throws PrismsMessageException
	 */
	ConversationView [] getConversations(prisms.arch.ds.User viewer, MessageHeader... messages)
		throws PrismsMessageException;

	/**
	 * Retrieves the first part of the contents of a message
	 * 
	 * @param header The message's header
	 * @return The first part of the message's content
	 * @throws PrismsMessageException If an error occurs retrieving the data or if the message does
	 *         not exist in the store
	 */
	String previewMessage(MessageHeader header) throws PrismsMessageException;

	/**
	 * Retrieves the content of a message
	 * 
	 * @param header The message's header
	 * @return The message with its content
	 * @throws PrismsMessageException If an error occurs retrieving the data or if the message does
	 *         not exist in the store
	 */
	Message getMessage(MessageHeader header) throws PrismsMessageException;

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
	 * Stores a new message or modifies one previously created
	 * 
	 * @param message The message to create or modify
	 * @param trans The transaction for record-keeping
	 * @throws PrismsMessageException If an error occurs storing the data
	 */
	void putMessage(Message message, RecordsTransaction trans) throws PrismsMessageException;

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
	Attachment createAttachment(MessageHeader message, String name, String type,
		java.io.InputStream data, RecordsTransaction trans) throws PrismsMessageException,
		java.io.IOException;

	/**
	 * Deletes an attachment
	 * 
	 * @param attach The attachment to delete
	 * @param trans The transaction for record-keeping
	 * @throws PrismsMessageException If an error occurs deleting the attachment
	 */
	void deleteAttachment(Attachment attach, RecordsTransaction trans)
		throws PrismsMessageException;

	/** Releases this manager's resources */
	void disconnect();
}
