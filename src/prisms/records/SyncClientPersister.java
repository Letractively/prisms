/*
 * SyncClientPersister.java Created Dec 2, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import org.apache.log4j.Logger;

/** Configures and sets a property of type {@link SyncServiceClient} */
public class SyncClientPersister implements prisms.arch.Persister<SyncServiceClient>
{
	private static final Logger log = Logger.getLogger(SyncClientPersister.class);

	private SyncServiceClient theClient;

	public void configure(prisms.arch.PrismsConfig config, prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<SyncServiceClient> property)
	{
		if(theClient == null)
		{
			String appName = config.get("service-app");
			String clientName = config.get("service-client");
			String servicePluginName = config.get("service-plugin");
			if(appName == null || clientName == null || servicePluginName == null)
				throw new IllegalStateException("service-app, service-client, and service-plugin"
					+ " elements required for center editor");
			PrismsSynchronizer sync = app.getGlobalProperty(prisms.arch.event.PrismsProperty.get(
				config.get("synchronizer"), PrismsSynchronizer.class));
			if(sync == null)
				return;
			theClient = new SyncServiceClient(sync, appName, clientName, servicePluginName);
			String trustStore = config.get("trust-store");
			if(trustStore != null)
			{
				String trustPwd = config.get("trust-password");
				try
				{
					theClient.setSecurityInfo(trustStore, trustPwd);
				} catch(java.security.GeneralSecurityException e)
				{
					log.error("Could not set security info", e);
				} catch(java.io.IOException e)
				{
					log.error("Trust store " + trustStore + " could not be read", e);
				}
			}
			if(config.is("https-allow-all", false))
				theClient.setAllowAllCerts(true);
			if(config.is("requires-records", false))
				theClient.setRequiresRecords(true);

			long interval = config.getTime("auto-sync-interval", -1);
			if(interval > 0)
				app.scheduleRecurringTask(new Runnable()
				{
					public void run()
					{
						doAutoSynchronize();
					}
				}, interval);
		}
	}

	public SyncServiceClient getValue()
	{
		return theClient;
	}

	public SyncServiceClient link(SyncServiceClient value)
	{
		return value;
	}

	public <V extends SyncServiceClient> void setValue(prisms.arch.PrismsSession session, V o,
		@SuppressWarnings("rawtypes") prisms.arch.event.PrismsPCE evt)
	{
	}

	public void valueChanged(prisms.arch.PrismsSession session, SyncServiceClient fullValue,
		Object o, prisms.arch.event.PrismsEvent evt)
	{
	}

	public void reload()
	{
	}

	/** Called to auto-synchronize all centers that are configured to synchronize on a schedule */
	protected void doAutoSynchronize()
	{
		prisms.records.PrismsSynchronizer sync = getValue().getSynchronizer();
		long time = System.currentTimeMillis();
		PrismsCenter [] centers;
		try
		{
			centers = sync.getKeeper().getCenters();
		} catch(PrismsRecordException e)
		{
			throw new IllegalStateException("Could not get centers", e);
		}
		PrismsCenter [] toSync = new PrismsCenter [0];
		for(int rc = 0; rc < centers.length; rc++)
		{
			long syncFreq = centers[rc].getServerSyncFrequency();
			if(syncFreq <= 0)
				continue;
			long lastSync = centers[rc].getLastImport();
			if(time - lastSync >= syncFreq)
				toSync = prisms.util.ArrayUtils.add(toSync, centers[rc]);
		}
		if(toSync.length == 0)
			return;
		for(PrismsCenter center : toSync)
		{
			String dependError;
			try
			{
				dependError = RecordUtils.areDependentsSetUp(sync, center);
			} catch(PrismsRecordException e)
			{
				log.error("Automatic synchronization for center " + center.getName()
					+ " cannot proceed: ", e);
				continue;
			}
			if(dependError != null)
			{
				log.error("Automatic synchronization for center " + center.getName()
					+ " cannot proceed--synchronization setup has not been completed: "
					+ dependError);
				continue;
			}
			SyncServiceClient syncClient = getValue();
			try
			{
				syncClient.synchronize(center, SyncRecord.Type.AUTOMATIC, null, null, true);
			} catch(prisms.records.PrismsRecordException e)
			{
				log.error("Automatic synchronization for center " + center.getName() + " failed", e);
			}
		}
	}
}
