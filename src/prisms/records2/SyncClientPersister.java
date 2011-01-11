/*
 * SyncClientPersister.java Created Dec 2, 2010 by Andrew Butler, PSL
 */
package prisms.records2;

import org.apache.log4j.Logger;

/** Configures and sets a property of type {@link SyncServiceClient} */
public class SyncClientPersister implements prisms.arch.Persister<SyncServiceClient>
{
	private static final Logger log = Logger.getLogger(SyncClientPersister.class);

	private SyncServiceClient theClient;

	public void configure(org.dom4j.Element configEl, prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<SyncServiceClient> property)
	{
		String intervalS = configEl.elementTextTrim("auto-sync-interval");
		if(theClient == null)
		{
			String appName = configEl.elementTextTrim("service-app");
			String clientName = configEl.elementTextTrim("service-client");
			String servicePluginName = configEl.elementTextTrim("service-plugin");
			if(appName == null || clientName == null || servicePluginName == null)
				throw new IllegalStateException("service-app, service-client, and service-plugin"
					+ " elements required for center editor");
			PrismsSynchronizer2 sync = app.getGlobalProperty(prisms.arch.event.PrismsProperty.get(
				configEl.elementTextTrim("synchronizer"), PrismsSynchronizer2.class));
			theClient = new SyncServiceClient(sync, appName, clientName, servicePluginName);
			String trustStore = configEl.elementTextTrim("trust-store");
			if(trustStore != null)
			{
				String trustPwd = configEl.elementTextTrim("trust-password");
				theClient.setSecurityInfo(trustStore, trustPwd);
			}
			if("true".equalsIgnoreCase(configEl.elementTextTrim("requires-records")))
				theClient.setRequiresRecords(true);
		}
		if(intervalS != null)
			app.scheduleRecurringTask(new Runnable()
			{
				public void run()
				{
					doAutoSynchronize();
				}
			}, Long.parseLong(intervalS));
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
		prisms.records2.PrismsSynchronizer2 sync = getValue().getSynchronizer();
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
				dependError = Record2Utils.areDependentsSetUp(sync, center);
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
			} catch(prisms.records2.PrismsRecordException e)
			{
				log.error("Automatic synchronization for center " + center.getName() + " failed", e);
			}
		}
	}
}
