/*
 * MessageSubjectType.java Created Jan 6, 2011 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.records.ChangeType;

/** Subjects of changes that can happen to a mission or its metadata */
public enum MessageSubjectType implements prisms.records.SubjectType
{
	/** Changes to a message's data */
	message(Message.class, null, null, MessageChangeTypes.MessageChange.class),
	/** Changes to one of the recipients of a message */
	recipient(Recipient.class, Message.class, null, MessageChangeTypes.RecipientChange.class),
	/** Changes to one of a message's attachments */
	attachment(Attachment.class, Message.class, null, MessageChangeTypes.AttachmentChange.class),
	/** Changes to one of a message's actions */
	action(MessageAction.class, Message.class, null, MessageChangeTypes.ActionChange.class),
	/** Changes to a user's view of a message */
	messageView(MessageView.class, Message.class, ConversationView.class, null),
	/** Changes to a user's view of a conversation. The metadata 2 is the conversation ID. */
	conversationView(ConversationView.class, prisms.arch.ds.User.class, Long.class,
		MessageChangeTypes.ConversationViewChange.class);

	private final Class<?> theMajorType;

	private final Class<?> theMDType1;

	private final Class<?> theMDType2;

	private final Class<? extends Enum<? extends ChangeType>> theChangeClass;

	MessageSubjectType(Class<?> majorType, Class<?> md1, Class<?> md2,
		Class<? extends Enum<? extends ChangeType>> fieldsClass)
	{
		theMajorType = majorType;
		theMDType1 = md1;
		theMDType2 = md2;
		theChangeClass = fieldsClass;
	}

	public Class<?> getMajorType()
	{
		return theMajorType;
	}

	public Class<?> getMetadataType1()
	{
		return theMDType1;
	}

	public Class<?> getMetadataType2()
	{
		return theMDType2;
	}

	public Class<? extends Enum<? extends ChangeType>> getChangeTypes()
	{
		return theChangeClass;
	}
}
