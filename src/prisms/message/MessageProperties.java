/*
 * MessageProperties.java Created Jan 10, 2011 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.arch.event.PrismsProperty;

/** Properties associated with the PRISMS messaging feature */
public class MessageProperties
{
	/** The set of messages viewable by a session */
	public static final PrismsProperty<MessageView []> messages = PrismsProperty.create(
		"prisms/messages", MessageView [].class);
}
