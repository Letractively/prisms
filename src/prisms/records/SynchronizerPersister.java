/*
 * SynchronizerPersister.java Created Dec 2, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/** A simplified and configurable way of setting up a synchronizer in an environment */
public abstract class SynchronizerPersister implements prisms.arch.Persister<PrismsSynchronizer>
{
	private prisms.arch.PrismsApplication[] theApps;

	private PrismsSynchronizer theSynchronizer;

	public void configure(prisms.arch.PrismsConfig config, prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<PrismsSynchronizer> property)
	{
		theApps = prisms.util.ArrayUtils.add(theApps, app);
		if(theSynchronizer == null)
		{
			theSynchronizer = createSynchronizer(config, app);
			if(theSynchronizer == null)
				return;
			String logDir = app.getEnvironment().getLogger().getExposedDir();
			if(logDir != null)
				theSynchronizer.setSyncLoggingLoc(logDir);
			theSynchronizer.setSyncListener(new prisms.records.PrismsSynchronizer.SyncListener()
			{
				public void syncAttempted(SyncRecord record)
				{
					SynchronizerPersister.this.syncAttempted(record);
				}

				public void syncChanged(SyncRecord record)
				{
					SynchronizerPersister.this.syncChanged(record);
				}
			});
		}
	}

	/**
	 * @param config The configuration that is configuring this persister
	 * @param app The application that this persister is being configured for
	 * @return The synchronizer that this persister is to set for its property
	 */
	protected abstract PrismsSynchronizer createSynchronizer(prisms.arch.PrismsConfig config,
		prisms.arch.PrismsApplication app);

	/**
	 * Gets the location of the trust store to use for connecting to the selected center. Should be
	 * overridden by subclasses in environments where secure connections will be needed.
	 * 
	 * @return The location of the trust store
	 */
	protected String getTrustStore()
	{
		return null;
	}

	/**
	 * Gets the password to the trust store to use for connecting to the selected center. Should be
	 * overridden by subclasses in environments where secure connections will be needed.
	 * 
	 * @return The password to use to access the trust store
	 */
	protected String getTrustPassword()
	{
		return null;
	}

	/**
	 * Called when a synchronization is attempted
	 * 
	 * @param record The record of the attempted synchronization
	 */
	protected void syncAttempted(SyncRecord record)
	{
		for(prisms.arch.PrismsApplication app : theApps)
			app.fireGlobally(null, new prisms.arch.event.PrismsEvent("syncAttempted", "record",
				record));
	}

	/**
	 * Called when a synchronization record is changed
	 * 
	 * @param record The record of the synchronization
	 */
	protected void syncChanged(SyncRecord record)
	{
		for(prisms.arch.PrismsApplication app : theApps)
			app.fireGlobally(null, new prisms.arch.event.PrismsEvent("syncAttemptChanged",
				"record", record));
	}

	public PrismsSynchronizer getValue()
	{
		return theSynchronizer;
	}

	public PrismsSynchronizer link(PrismsSynchronizer value)
	{
		return value;
	}

	public <V extends PrismsSynchronizer> void setValue(prisms.arch.PrismsSession session, V o,
		@SuppressWarnings("rawtypes") prisms.arch.event.PrismsPCE evt)
	{
		throw new IllegalStateException("Synchronizer cannot be changed");
	}

	public void valueChanged(prisms.arch.PrismsSession session, PrismsSynchronizer fullValue,
		Object o, prisms.arch.event.PrismsEvent evt)
	{
		throw new IllegalArgumentException("Synchronizer cannot be affected by event " + evt.name);
	}

	public void reload()
	{
	}
}
