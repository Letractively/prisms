package prisms.records;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

/** Interfaces with a PRISMS synchronization service to download synchronization data */
public class SyncServiceClient
{
	private static final Logger log = Logger.getLogger(SyncServiceClient.class);

	private prisms.records.PrismsSynchronizer theSync;

	private String theAppName;

	private String theClientName;

	private String thePlugin;

	private boolean requiresRecords;

	javax.net.ssl.X509TrustManager theSystemTrustManager;

	javax.net.ssl.X509TrustManager theSyncTrustManager;

	boolean isAllowAllCerts;

	/**
	 * Creates a service client
	 * 
	 * @param sync The synchronizer to use to interpret synchronization data
	 * @param appName The name of the application to connect to
	 * @param clientName The name of the client to connect to
	 * @param plugin The name of the plugin to connect to
	 */
	public SyncServiceClient(prisms.records.PrismsSynchronizer sync, String appName,
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

		try
		{
			javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory
				.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
			tmf.init((java.security.KeyStore) null);
			for(javax.net.ssl.TrustManager mgr : tmf.getTrustManagers())
				if(mgr instanceof javax.net.ssl.X509TrustManager)
				{
					theSystemTrustManager = (javax.net.ssl.X509TrustManager) mgr;
					break;
				}
			if(theSystemTrustManager == null)
				log.error("No X509 trust manager in system defaults");
		} catch(java.security.GeneralSecurityException e)
		{
			log.error("Security doesn't allow us to get the system trust manager", e);
		}
	}

	/** @return The synchronizer that interprets the M2M data sent and received by this client */
	public PrismsSynchronizer getSynchronizer()
	{
		return theSync;
	}

	/**
	 * Sets the trust store information for an HTTPS connection
	 * 
	 * @param trustStore The file location of the trust store to use for client certificates
	 * @param trustPassword The password to access the trust store
	 * @throws java.security.GeneralSecurityException If the security settings prevent access to SSL
	 *         settings
	 * @throws IOException If the trust store pointed to does not exist or cannot be read or parsed
	 */
	public void setSecurityInfo(String trustStore, String trustPassword)
		throws java.security.GeneralSecurityException, IOException
	{
		setSecurityInfo(trustStore == null ? null : new java.io.File(trustStore).toURI().toURL(),
			trustPassword);
	}

	/** @param allow Whether this client should ignore certificates sent by HTTPS servers */
	public void setAllowAllCerts(boolean allow)
	{
		isAllowAllCerts = allow;
	}

	/**
	 * Sets the trust store information for an HTTPS connection
	 * 
	 * @param trustStore The URL location of the trust store to use for client certificates
	 * @param trustPassword The password to access the trust store
	 * @throws java.security.GeneralSecurityException If the security settings prevent access to SSL
	 *         settings
	 * @throws IOException If the trust store pointed to does not exist or cannot be read or parsed
	 */
	public void setSecurityInfo(java.net.URL trustStore, String trustPassword)
		throws java.security.GeneralSecurityException, IOException
	{
		if(trustStore == null)
		{
			theSyncTrustManager = null;
			return;
		}

		java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
		ks.load(trustStore.openStream(), trustPassword.toCharArray());

		javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory
			.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ks);
		javax.net.ssl.X509TrustManager syncTM = null;
		for(javax.net.ssl.TrustManager tm : tmf.getTrustManagers())
			if(tm instanceof javax.net.ssl.X509TrustManager)
			{
				syncTM = (javax.net.ssl.X509TrustManager) tm;
				break;
			}
		if(syncTM == null)
			log.error("No X509 trust manager found for synchronizer");
		theSyncTrustManager = syncTM;
	}

	/**
	 * @return Whether the local center keeps meticulous records at the expense of perhaps slower
	 *         synchronization
	 */
	public boolean requiresRecords()
	{
		return requiresRecords;
	}

	/**
	 * @param required Whether the local center should keep meticulous records at the expense of
	 *        perhaps slower synchronization
	 */
	public void setRequiresRecords(boolean required)
	{
		requiresRecords = required;
	}

	/**
	 * Connects to the synchronization service
	 * 
	 * @param center The PRISMS center to connect to
	 * @return A service connector ready to send and receive data
	 * @throws PrismsRecordException If an error occurs setting up the connection
	 */
	public prisms.util.PrismsServiceConnector connect(final PrismsCenter center)
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
			conn.setTrustManager(new javax.net.ssl.X509TrustManager()
			{
				public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
					String authType) throws java.security.cert.CertificateException
				{
					if(isAllowAllCerts)
						return;
					if(center.getCertificates() != null && center.getCertificates().length > 0)
					{
						// TODO compare certificates in a smarter way
						if(prisms.util.ArrayUtils.equals(chain, center.getCertificates()))
							return;
						else
							throw new java.security.cert.CertificateException(
								"Server certificate for " + center.getServerURL() + " has changed");
					}
					if(theSyncTrustManager != null)
						try
						{
							theSyncTrustManager.checkServerTrusted(chain, authType);
							return;
						} catch(java.security.cert.CertificateException e)
						{}
					if(theSystemTrustManager != null)
						try
						{
							theSystemTrustManager.checkServerTrusted(chain, authType);
							return;
						} catch(java.security.cert.CertificateException e)
						{}
					throw new java.security.cert.CertificateException("Server certificate for "
						+ center.getServerURL() + " is not recognized");
				}

				public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
					String authType) throws java.security.cert.CertificateException
				{
				}

				public java.security.cert.X509Certificate[] getAcceptedIssuers()
				{
					return new java.security.cert.X509Certificate [0];
				}
			});
		} catch(java.security.GeneralSecurityException e)
		{
			if(center.getServerURL() != null && center.getServerURL().startsWith("https:"))
				log.error("Could not set trust manager for HTTPS synchronization", e);
		}
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
	 * Gets server certificates from the center's server URL
	 * 
	 * @param center The center to contact
	 * @return The server certificates presented by the center's server, or null if the center does
	 *         not use an HTTPS URL
	 * @throws PrismsRecordException If an error occurs getting the certificates
	 */
	public java.security.cert.X509Certificate[] getCertificates(PrismsCenter center)
		throws PrismsRecordException
	{
		if(center.getServerURL() == null)
			throw new PrismsRecordException("No server URL set for center " + center);
		prisms.util.PrismsServiceConnector conn = new prisms.util.PrismsServiceConnector(
			center.getServerURL(), theAppName, theClientName, center.getServerUserName());
		try
		{
			return conn.getServerCerts();
		} catch(java.security.GeneralSecurityException e)
		{
			throw new PrismsRecordException("Security environment does not permit", e);
		} catch(IOException e)
		{
			throw new PrismsRecordException("Could not contact server", e);
		}
	}

	/**
	 * Checks the status of synchronization between this center and a remote center
	 * 
	 * @param center The center to check the synchronization status with
	 * @param pi The progress informer to notify the user of the status of the operation
	 * @return The number of objects that must be sent to fulfill synchronization ([0]) and the
	 *         number of changes that must be sent ([1])
	 * @throws PrismsRecordException If an error occurs contacting the center or processing the data
	 */
	public int [] checkSync(PrismsCenter center, prisms.ui.UI.DefaultProgressInformer pi)
		throws PrismsRecordException
	{
		if(center.getServerURL() == null)
			throw new PrismsRecordException("No server URL set for center " + center);
		pi.setProgressText("Connecting to " + center.getServerURL());
		prisms.util.PrismsServiceConnector conn = connect(center);
		pi.setProgressText("Retrieving synchronization status");
		JSONObject retEvt = createRequest(theSync, center, SyncRecord.Type.MANUAL_REMOTE, false,
			false, pi);
		try
		{
			retEvt = conn.getResult(thePlugin, "checkSync", retEvt);
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
	 * Sets the center ID of the given center by contacting the center
	 * 
	 * @param center The center to get the center ID of
	 * @throws PrismsRecordException If an error occurs accessing the server or getting the
	 *         information
	 */
	public void setCenterID(PrismsCenter center) throws PrismsRecordException
	{
		if(center.getCenterID() >= 0)
			return;
		prisms.util.PrismsServiceConnector conn = connect(center);
		JSONObject result;
		try
		{
			result = conn.getResult(thePlugin, "getCenterID");
		} catch(IOException e)
		{
			throw new PrismsRecordException("Could not get center ID", e);
		}
		center.setCenterID(((Number) result.get("centerID")).intValue());
	}

	/**
	 * Synchronizes with a given center, pulling synchronization data from the remote center and
	 * importing it locally
	 * 
	 * @param center The center to synchronize with
	 * @param syncType The type of synchronization this represents (automatic or manual)
	 * @param pi The progress informer to use to notify the user of the progress of synchronization
	 * @param pids The post ID setter to use when the center ID of a center is set
	 * @param storeSyncRecord Whether to store the sync record and associated changes
	 * @throws PrismsRecordException If an error occurs connecting to the center or processing the
	 *         data
	 */
	public void synchronize(PrismsCenter center, SyncRecord.Type syncType,
		prisms.ui.UI.DefaultProgressInformer pi, prisms.records.PrismsSynchronizer.PostIDSet pids,
		boolean storeSyncRecord) throws PrismsRecordException
	{
		if(pi == null)
			pi = new prisms.ui.UI.DefaultProgressInformer();
		if(center.getServerURL() == null)
			throw new PrismsRecordException("No server URL set for center " + center);
		pi.setProgressText("Connecting to " + center.getServerURL());
		prisms.util.PrismsServiceConnector conn = connect(center);
		pi.setProgressText("Retrieving synchronization data");
		JSONObject evt = createRequest(theSync, center, syncType, requiresRecords, storeSyncRecord,
			pi);
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
				new java.io.BufferedInputStream(syncInput)), pi, pids, storeSyncRecord);
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

	private static JSONObject createRequest(PrismsSynchronizer sync, PrismsCenter center,
		SyncRecord.Type syncType, boolean requiresRecords, boolean storeSyncRecord,
		prisms.ui.UI.DefaultProgressInformer pi) throws PrismsRecordException
	{
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
		evt.put("version", sync.getImpls()[sync.getImpls().length - 1].getVersion());
		evt.put("changes", RecordUtils.serializeCenterChanges(sync));
		evt.put("withRecords", Boolean.valueOf(requiresRecords));
		evt.put("centerID", Long.valueOf(sync.getKeeper().getCenterID()));
		evt.put("syncPriority", Integer.valueOf(sync.getKeeper().getLocalPriority()));
		evt.put("storeSyncRecord", Boolean.valueOf(storeSyncRecord));
		if(sync.getDepends().length > 0)
		{
			org.json.simple.JSONArray depends = new org.json.simple.JSONArray();
			evt.put("subSyncs", depends);
			for(int d = 0; d < sync.getDepends().length; d++)
			{
				PrismsSynchronizer subSync = sync.getDepends()[d];
				PrismsCenter subCenter = sync.getDependCenter(subSync, center);
				if(subCenter == null)
					throw new PrismsRecordException("No dependent center" + " parallel to "
						+ center.getName() + " for synchronizer with impl "
						+ subSync.getImpls()[subSync.getImpls().length - 1].getClass().getName());
				depends.add(createRequest(subSync, subCenter, syncType, requiresRecords,
					storeSyncRecord, pi));
			}
		}
		return evt;
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
