/*
 * MessageManagerPersister.java Created Feb 16, 2011 by Andrew Butler, PSL
 */
package prisms.message;

/** Implements a persister that creates a {@link DefaultMessageManager} on application startup */
public class MessageManagerPersister implements prisms.arch.Persister<MessageManager>
{
	private MessageManager theManager;

	public void configure(prisms.arch.PrismsConfig configEl, prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<MessageManager> property)
	{
		if(theManager != null)
			return;
		String messageNS = configEl.get("namespace");
		if(messageNS == null)
			throw new IllegalStateException("No namespace for messaging set");
		boolean withRecords = configEl.is("records", false);
		prisms.records.DBRecordKeeper keeper;
		if(withRecords)
			keeper = new prisms.records.DBRecordKeeper("messages/" + messageNS, configEl, app
				.getEnvironment().getConnectionFactory(), app.getEnvironment().getIDs());
		else
			keeper = null;
		theManager = new DefaultMessageManager(messageNS, app.getEnvironment(), configEl, keeper);
	}

	public MessageManager getValue()
	{
		return theManager;
	}

	public MessageManager link(MessageManager value)
	{
		return value;
	}

	public <V extends MessageManager> void setValue(prisms.arch.PrismsSession session, V o,
		@SuppressWarnings("rawtypes")
		prisms.arch.event.PrismsPCE evt)
	{
	}

	public void valueChanged(prisms.arch.PrismsSession session, MessageManager fullValue, Object o,
		prisms.arch.event.PrismsEvent evt)
	{
	}

	public void reload()
	{
	}
}
