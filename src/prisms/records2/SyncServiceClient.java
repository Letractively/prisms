package prisms.records2;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

/** Interfaces with a PRISMS synchronization service to download synchronization data */
public class SyncServiceClient
{
	private static final Logger log = Logger.getLogger(SyncServiceClient.class);

	private prisms.records2.PrismsSynchronizer2 theSync;

	private String theAppName;

	private String theClientName;

	private String thePlugin;

	/**
	 * Creates a service client
	 * 
	 * @param sync The synchronizer to use to interpret synchronization data
	 * @param appName The name of the application to connect to
	 * @param clientName The name of the client to connect to
	 * @param plugin The name of the plugin to connect to
	 */
	public SyncServiceClient(prisms.records2.PrismsSynchronizer2 sync, String appName,
		String clientName, String plugin)
	{
		if(sync == null)
			throw new IllegalArgumentException("Synchronizer needed");
		if(appName == null || clientName == null || plugin == null)
			throw new IllegalArgumentException("Connection information needed");
		theSync = sync;
		theAppName = appName;
		theClientName = clientName;
		thePlugin = plugin;
	}

	/**
	 * Connects to the synchronization service
	 * 
	 * @param center The PRISMS center to connect to
	 * @return A service connector ready to send and receive data
	 * @throws PrismsRecordException If an error occurs setting up the connection
	 */
	public prisms.util.PrismsServiceConnector connect(PrismsCenter center)
		throws PrismsRecordException
	{
		if(center.getServerURL() == null)
			throw new PrismsRecordException("No server URL set for center " + center);
		if(center.getServerUserName() == null)
			throw new PrismsRecordException("No server user name set for center " + center);
		prisms.util.PrismsServiceConnector conn = new prisms.util.PrismsServiceConnector(
			center.getServerURL(), theAppName, theClientName, center.getServerUserName());
		conn.setPassword(center.getServerPassword());
		try
		{
			conn.init();
		} catch(prisms.util.PrismsServiceConnector.AuthenticationFailedException e)
		{
			throw new PrismsRecordException("Username/password combination for center " + center
				+ " is incorrect", e);
		} catch(prisms.util.PrismsServiceConnector.PrismsServiceException e)
		{
			throw new PrismsRecordException(center + " says: " + e.getPrismsMessage(), e);
		} catch(IOException e)
		{
			throw new PrismsRecordException("Could not communicate with center " + center
				+ ". Check the server URL", e);
		}
		return conn;
	}

	/**
	 * Checks the status of synchronization between this center and a remote center
	 * 
	 * @param center The center to check the synchronization status with
	 * @param pi The progress informer to notify the user of the status of the operation
	 * @param withRecords Whether the local center makes use of record-keeping and prefers to
	 *        receive the remote changes rather than faster synchronization
	 * @return The number of objects that must be sent to fulfill synchronization ([0]) and the
	 *         number of changes that must be sent ([1])
	 * @throws PrismsRecordException If an error occurs contacting the center or processing the data
	 */
	public int [] checkSync(PrismsCenter center, prisms.ui.UI.DefaultProgressInformer pi,
		boolean withRecords) throws PrismsRecordException
	{
		if(center.getServerURL() == null)
			throw new PrismsRecordException("No server URL set for center " + center);
		pi.setProgressText("Connecting to " + center.getServerURL());
		prisms.util.PrismsServiceConnector conn = connect(center);
		pi.setProgressText("Retrieving synchronization status");
		JSONObject retEvt;
		try
		{
			retEvt = conn.getResult(thePlugin, "checkSync", "changes",
				Record2Utils.serializeCenterChanges(theSync.getKeeper()), "withRecords",
				new Boolean(withRecords), "centerID", new Long(theSync.getKeeper().getCenterID()),
				"syncPriority", new Integer(theSync.getKeeper().getLocalPriority()));
		} catch(prisms.util.PrismsServiceConnector.PrismsServiceException e)
		{
			pi.setDone();
			throw new PrismsRecordException(center + " says: " + e.getPrismsMessage(), e);
		} catch(IOException e)
		{
			pi.setDone();
			throw new PrismsRecordException("Could not check synchronization for center " + center,
				e);
		}
		pi.setProgressText("Synchronization status retrieved");
		int [] ret = new int [2];
		ret[0] = ((Number) retEvt.get("sendObjects")).intValue();
		ret[1] = ((Number) retEvt.get("sendChanges")).intValue();
		pi.setDone();
		return ret;
	}

	/**
	 * Synchronizes with a given center, pulling synchronization data from the remote center and
	 * importing it locally
	 * 
	 * @param center The center to synchronize with
	 * @param syncType The type of synchronization this represents (automatic or manual)
	 * @param pi The progress informer to use to notify the user of the progress of synchronization
	 * @param withRecords Whether the local center makes use of record-keeping and prefers to
	 *        receive the remote changes rather than faster synchronization
	 * @param storeSyncRecord Whether to store the sync record and associated changes
	 * @throws PrismsRecordException If an error occurs connecting to the center or processing the
	 *         data
	 */
	public void synchronize(PrismsCenter center, SyncRecord.Type syncType,
		prisms.ui.UI.DefaultProgressInformer pi, boolean withRecords, boolean storeSyncRecord)
		throws PrismsRecordException
	{
		if(pi == null)
			pi = new prisms.ui.UI.DefaultProgressInformer();
		if(center.getServerURL() == null)
			throw new PrismsRecordException("No server URL set for center " + center);
		pi.setProgressText("Connecting to " + center.getServerURL());
		prisms.util.PrismsServiceConnector conn = connect(center);
		pi.setProgressText("Retrieving synchronization data");
		JSONObject evt = new JSONObject();
		switch(syncType)
		{
		case AUTOMATIC:
			evt.put("type", "auto");
			break;
		case MANUAL_REMOTE:
			evt.put("type", "manual");
			break;
		case FILE:
			pi.setDone();
			throw new PrismsRecordException("File sync cannot be used with service client");
		}
		evt.put("changes", Record2Utils.serializeCenterChanges(theSync.getKeeper()));
		evt.put("withRecords", new Boolean(withRecords));
		evt.put("centerID", new Long(theSync.getKeeper().getCenterID()));
		evt.put("syncPriority", new Integer(theSync.getKeeper().getLocalPriority()));
		evt.put("storeSyncRecord", new Boolean(storeSyncRecord));
		java.io.InputStream syncInput;
		try
		{
			syncInput = conn.getDownload(thePlugin, "synchronizeM2M", evt);
		} catch(prisms.util.PrismsServiceConnector.PrismsServiceException e)
		{
			pi.setDone();
			throw new PrismsRecordException(center + " says: " + e.getPrismsMessage(), e);
		} catch(IOException e)
		{
			pi.setDone();
			throw new PrismsRecordException("Could not communicate with center " + center, e);
		}
		pi.setProgressText("Synchronizing with " + center);
		SyncRecord record;
		try
		{
			record = theSync.doSyncInput(center, syncType, new java.io.InputStreamReader(
				new java.io.BufferedInputStream(syncInput)), pi, storeSyncRecord);
		} catch(Throwable e)
		{
			if(storeSyncRecord)
				pi.setProgressText("Synchronization failed--sending receipt");
			else
				pi.setProgressText("Synchronization failed");
			if(storeSyncRecord)
				try
				{
					SyncRecord [] records = theSync.getKeeper()
						.getSyncRecords(center, Boolean.TRUE);
					if(records.length > 0)
						sendSyncReceipt(conn, records[records.length - 1], pi);
				} catch(PrismsRecordException e2)
				{
					log.error("Could not get sync records", e2);
				}
			pi.setDone();
			if(e instanceof Error)
				throw (Error) e;
			else if(e instanceof RuntimeException)
				throw (RuntimeException) e;
			else if(e instanceof java.io.IOException)
				throw new PrismsRecordException("Could not read synchronization data", e);
			else
				throw (PrismsRecordException) e;
		} finally
		{
			try
			{
				syncInput.close();
			} catch(IOException e)
			{
				log.warn("Could not close synchronization download", e);
			}
		}
		pi.setProgressText("Synchronization successful--sending receipt");
		pi.setDone();
		if(storeSyncRecord)
			sendSyncReceipt(conn, record, pi);
	}

	private void sendSyncReceipt(prisms.util.PrismsServiceConnector conn, SyncRecord record,
		prisms.ui.UI.DefaultProgressInformer pi) throws PrismsRecordException
	{
		java.io.StringWriter syncOutput = new java.io.StringWriter();
		try
		{
			theSync.sendSyncReceipt(record.getCenter(), syncOutput, record.getParallelID());
		} catch(IOException e)
		{
			throw new PrismsRecordException(
				"Synchronization successful, but could not send sync receipt", e);
		}
		try
		{
			conn.getResult(thePlugin, "receipt", "receipt", syncOutput.toString());
		} catch(IOException e)
		{
			throw new PrismsRecordException("Could not send receipt", e);
		}
		if(pi != null)
			pi.setProgressText("Synchronization complete");
	}
}
