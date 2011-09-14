/*
 * MessageSubjectType.java Created Jan 6, 2011 by Andrew Butler, PSL
 */
package prisms.message;

import prisms.records.ChangeType;

/** Subjects of changes that can happen to a mission or its metadata */
public enum MessageSubjectType implements prisms.records.SubjectType
{
	/** Changes to a message's data */
	message(MessageHeader.class, null, null, MessageChangeTypes.MessageChange.class),
	/** Changes to one of the recipients of a message */
	recipient(Receipt.class, MessageHeader.class, null, MessageChangeTypes.RecipientChange.class),
	/** Changes to one of a message's attachments */
	attachment(Attachment.class, MessageHeader.class, null, null),
	/** Changes to a user's view of a conversation */
	view(ConversationView.class, prisms.arch.ds.User.class, null,
		MessageChangeTypes.ViewChange.class);

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
