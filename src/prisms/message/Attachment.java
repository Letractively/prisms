/*
 * Attachment.java Created Sep 30, 2010 by Andrew Butler, PSL
 */
package prisms.message;

/** Represents an attachment of arbitrary type to a message */
public class Attachment
{
	private final MessageHeader theMessage;

	private final long theID;

	private final String theName;

	private final String theType;

	private final long theLength;

	private final long theCRC;

	private boolean isDeleted;

	/**
	 * Creates an attachment. This is public so that classes from other packages can implement
	 * {@link MessageManager}, but it should only be used by the
	 * {@link MessageManager#createAttachment(MessageHeader, String, String, java.io.InputStream, prisms.records.RecordsTransaction)}
	 * method of an implementation of that class.
	 * 
	 * @param message The message to attach the data to
	 * @param id The storage ID of this attachment
	 * @param name The name of this attachment
	 * @param type The type of this attachment
	 * @param length The length of this attachment's content
	 * @param crc The CRC code of this attachment's content
	 */
	public Attachment(MessageHeader message, long id, String name, String type, long length,
		long crc)
	{
		theMessage = message;
		theID = id;
		theName = name;
		theType = type;
		theLength = length;
		theCRC = crc;
	}

	/** @return The header of the message that the data is attached to */
	public MessageHeader getMessage()
	{
		return theMessage;
	}

	/** @return The storage ID of the attachment */
	public long getID()
	{
		return theID;
	}

	/** @return The name of this attachment */
	public String getName()
	{
		return theName;
	}

	/** @return The type of this attachment--traditionally, but not necessarily, a MIME type */
	public String getType()
	{
		return theType;
	}

	/** @return The number of bytes in the attachment */
	public long getLength()
	{
		return theLength;
	}

	/** @return The CRC code for this attacment's content */
	public long getCRC()
	{
		return theCRC;
	}

	/** @return Whether this attachment has been removed from its message */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	/** @param deleted Whether this attachment has been removed from its message */
	public void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}
}
