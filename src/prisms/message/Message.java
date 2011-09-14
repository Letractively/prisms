/*
 * PrismsMessage.java Created Sep 30, 2010 by Andrew Butler, PSL
 */
package prisms.message;

/** A message to be sent in the PRISMS architecture */
public class Message
{
	private final MessageHeader theHeader;

	private String theContent;

	/**
	 * Creates a message
	 * 
	 * @param header The message header for the message
	 */
	public Message(MessageHeader header)
	{
		theHeader = header;
		theContent = "";
	}

	/** @return The message header for this message */
	public MessageHeader getHeader()
	{
		return theHeader;
	}

	/** @return This message's content */
	public String getContent()
	{
		return theContent;
	}

	/** @param content The content for this message */
	public void setContent(String content)
	{
		if(content == null)
			content = "";
		theContent = content;
		theHeader.setCRC(getCRC(theContent));
		theHeader.setLength(theContent.length());
	}

	/**
	 * Gets the CRC code of a message
	 * 
	 * @param content The content of the message to get the CRC code of
	 * @return The CRC code of the given string
	 */
	public static long getCRC(String content)
	{
		if(content.length() == 0)
			return 0;
		final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		java.io.OutputStream crcOut = new java.io.OutputStream()
		{
			@Override
			public void write(int b) throws java.io.IOException
			{
				crc.update(b);
			}
		};
		try
		{
			new java.io.OutputStreamWriter(crcOut).write(content);
		} catch(java.io.IOException e)
		{
			throw new IllegalStateException("Could not check CRC of message");
		}
		return crc.getValue();
	}
}
