/*
 * ReaSyncService2.java Created Aug 4, 2010 by Andrew Butler, PSL
 */
package prisms.records2;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.util.json.SAJParser.ParseException;

/**
 * A service that allows synchronization service clients to retrieve synchronization data and upload
 * synchronization receipts
 */
public abstract class PrismsSyncService2 implements prisms.arch.DownloadPlugin,
	prisms.arch.UploadPlugin
{
	private static final Logger log = Logger.getLogger(PrismsSyncService2.class);

	private static boolean DEBUG = false;

	private PrismsSession theSession;

	private String theName;

	volatile boolean isDownloadStarted;

	volatile boolean isDownloadCanceled;

	volatile boolean isDownloadFinished;

	volatile String theDownloadText;

	private prisms.ui.UI.DefaultProgressInformer thePI;

	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementTextTrim("name");
		theSession.addEventListener("downloadSyncRequest",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.event.PrismsEvent evt)
				{
					generateSyncRequest((PrismsCenter) evt.getProperty("center"),
						((Boolean) evt.getProperty("withRecords")).booleanValue());
				}
			});
		theSession.addEventListener("downloadSyncData", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				startRequestUpload((PrismsCenter) evt.getProperty("center"));
			}
		});
		theSession.addEventListener("uploadSyncData", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				startSyncUpload((PrismsCenter) evt.getProperty("center"));
			}
		});
	}

	/**
	 * @return The session this plugin instance belongs to
	 */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/**
	 * @return This plugin's name
	 */
	public String getName()
	{
		return theName;
	}

	public void initClient()
	{
	}

	public void processEvent(JSONObject evt)
	{
		if("checkSync".equals(evt.get("method")))
		{
			PrismsCenter center = getCenter();
			if(center == null)
				throw new IllegalArgumentException("No rules center mapped to user "
					+ theSession.getUser());
			prisms.records2.PrismsSynchronizer2 sync = getSynchronizer();
			int centerID = ((Number) evt.get("centerID")).intValue();
			if(center.getCenterID() < 0)
			{
				center.setCenterID(centerID);
				try
				{
					sync.getKeeper().putCenter(center, null, null);
				} catch(PrismsRecordException e)
				{
					log.error("Could not persist center ID", e);
				}
			}
			else if(center.getCenterID() != centerID)
				throw new IllegalStateException("This center is not mapped to user "
					+ center.getClientUser());
			LatestCenterChange [] changes = parseCenterChanges((JSONArray) evt.get("changes"));
			int [] check;
			try
			{
				check = sync.checkSync(center, changes,
					!Boolean.FALSE.equals(evt.get("withRecords")));
			} catch(PrismsRecordException e)
			{
				throw new IllegalStateException("Could not check synchronization status", e);
			}
			JSONObject ret = new JSONObject();
			ret.put("plugin", theName);
			ret.put("method", "returnSyncCheck");
			ret.put("sendObjects", new Integer(check[0]));
			ret.put("sendChanges", new Integer(check[1]));
			theSession.postOutgoingEvent(ret);
		}
		else if("receipt".equals(evt.get("method")))
		{
			prisms.records2.PrismsSynchronizer2 sync = getSynchronizer();
			String receipt = (String) evt.get("receipt");
			try
			{
				sync.readSyncReceipt(new java.io.StringReader(receipt));
			} catch(IOException e)
			{
				throw new IllegalStateException("Could not read sync receipt", e);
			}
			JSONObject ret = new JSONObject();
			ret.put("plugin", getName());
			ret.put("method", "receiptReceived");
			getSession().postOutgoingEvent(evt);
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}

	public String getFileName(JSONObject event)
	{
		if("downloadSyncRequest".equals(event.get("method")))
			return "SyncRequest.dat";
		else if("downloadSyncData".equals(event.get("method")))
			return "SyncData.dat";
		return "PrismsSync.dat";
	}

	public String getContentType(JSONObject event)
	{
		return null;
	}

	/**
	 * @return The synchronizer that this service should use
	 */
	protected abstract PrismsSynchronizer2 getSynchronizer();

	/**
	 * @return The record user represented by the user of this session
	 */
	protected abstract RecordUser getUser();

	/**
	 * @return Whether the current session's user has authority to synchronize data through the user
	 *         interface
	 */
	protected abstract boolean canSynchronizeUI();

	/**
	 * This method generates a file that can be used to generate synchronization data on another
	 * system
	 * 
	 * @param center
	 */
	void generateSyncRequest(PrismsCenter center, boolean withRecords)
	{
		isDownloadStarted = true;
		isDownloadCanceled = false;
		isDownloadFinished = false;
		theDownloadText = "Starting Synchronization Request Generation";
		prisms.ui.UI ui = (prisms.ui.UI) theSession.getPlugin("UI");
		/* Here is where we would generate our download content--use a progress dialog if this
		 * content takes a long time */
		ui.startTimedTask(new prisms.ui.UI.ProgressInformer()
		{
			public void cancel() throws IllegalStateException
			{
				isDownloadCanceled = true;
			}

			public int getTaskProgress()
			{
				return 0;
			}

			public int getTaskScale()
			{
				return 0;
			}

			public String getTaskText()
			{
				return theDownloadText;
			}

			public boolean isCancelable()
			{
				return true;
			}

			public boolean isTaskDone()
			{
				return isDownloadFinished || (!isDownloadStarted && isDownloadCanceled);
			}
		});
		if(!isDownloadCanceled)
		{
			JSONObject event = new JSONObject();
			event.put("downloadPlugin", theName);
			event.remove("plugin");
			event.put("downloadMethod", "downloadSyncRequest");
			event.put("method", "doDownload");
			event.put("centerID", new Integer(center.getID()));
			event.put("withRecords", new Boolean(withRecords));
			theSession.postOutgoingEvent(event);
		}
	}

	void startRequestUpload(PrismsCenter center)
	{
		JSONObject evt = new JSONObject();
		evt.put("uploadPlugin", theName);
		evt.remove("plugin");
		evt.put("uploadMethod", "uploadSyncRequest");
		evt.put("method", "doUpload");
		evt.put("message", "Select the synchronization request file to upload");
		evt.put("centerID", new Integer(center.getID()));
		theSession.postOutgoingEvent(evt);
	}

	/**
	 * This method generates the download content, then sends the event for the client to initialize
	 * the download
	 * 
	 * @param event the event that sparked the initial download request
	 */
	void startSyncDownload(PrismsCenter center, JSONObject params)
	{
		if(center == null)
			throw new IllegalArgumentException("No such center with ID " + params.get("centerID"));
		PrismsSynchronizer2 sync = getSynchronizer();
		int remoteCenterID = -1;
		if(params.containsKey("remoteCenterID"))
			remoteCenterID = ((Number) params.get("remoteCenterID")).intValue();
		if(remoteCenterID >= 0 && remoteCenterID != sync.getKeeper().getCenterID())
			throw new IllegalArgumentException("Sync request is not for this center");
		int localCenterID = ((Number) params.get("localCenterID")).intValue();
		if(center.getCenterID() < 0)
		{
			center.setCenterID(localCenterID);
			try
			{
				sync.getKeeper().putCenter(center, null, null);
			} catch(PrismsRecordException e)
			{
				log.error("Could not persist center ID", e);
			}
		}
		else if(center.getCenterID() != localCenterID)
			throw new IllegalArgumentException("Sync request is not from center " + center);
		if(localCenterID == remoteCenterID)
			throw new IllegalArgumentException("A center cannot synchronize with itself");
		int remotePriority = ((Number) params.get("syncPriority")).intValue();
		if(center.getPriority() != remotePriority)
		{
			center.setPriority(remotePriority);
			try
			{
				sync.getKeeper().putCenter(center, null, null);
			} catch(PrismsRecordException e)
			{
				log.error("Could not persist center's priority", e);
			}
		}
		isDownloadStarted = true;
		isDownloadCanceled = false;
		isDownloadFinished = false;
		theDownloadText = "Starting Synchronization Data Generation";
		prisms.ui.UI ui = (prisms.ui.UI) theSession.getPlugin("UI");
		/* Here is where we would generate our download content--use a progress dialog if this
		 * content takes a long time */
		thePI = new prisms.ui.UI.DefaultProgressInformer()
		{
			@Override
			public boolean isTaskDone()
			{
				return super.isTaskDone() || (!isDownloadStarted && isCanceled());
			}
		};
		thePI.setCancelable(true);
		thePI.setProgressText("Generating synchronization data file");
		ui.startTimedTask(thePI);
		if(!isDownloadCanceled)
		{
			JSONObject event = new JSONObject();
			event.put("downloadPlugin", theName);
			event.remove("plugin");
			event.put("downloadMethod", "downloadSyncData");
			event.put("method", "doDownload");
			event.put("centerID", new Integer(center.getID()));
			event.put("syncParams", params);
			theSession.postOutgoingEvent(event);
		}
	}

	void startSyncUpload(PrismsCenter center)
	{
		JSONObject evt = new JSONObject();
		evt.put("uploadPlugin", theName);
		evt.remove("plugin");
		evt.put("uploadMethod", "uploadSyncData");
		evt.put("method", "doUpload");
		evt.put("message", "Select the synchronization data file to upload");
		evt.put("centerID", new Integer(center.getID()));
		theSession.postOutgoingEvent(evt);
	}

	void showSyncReceipt(SyncRecord syncRecord)
	{
		getSession().fireEvent("genSyncReceipt", "syncRecord", syncRecord);
	}

	public void doDownload(JSONObject event, java.io.OutputStream stream)
		throws java.io.IOException
	{
		isDownloadStarted = true;
		if("synchronizeM2M".equals(event.get("method")))
		{
			PrismsCenter center = getCenter();
			if(center == null)
				throw new IllegalArgumentException("No rules center mapped to user "
					+ theSession.getUser());
			prisms.records2.SyncRecord.Type syncType;
			if("auto".equals(event.get("type")))
				syncType = prisms.records2.SyncRecord.Type.AUTOMATIC;
			else if("manual".equals(event.get("type")))
				syncType = prisms.records2.SyncRecord.Type.MANUAL_REMOTE;
			else
				throw new IllegalArgumentException("Unrecognized sync type: " + event.get("type"));
			prisms.records2.PrismsSynchronizer2 sync = getSynchronizer();
			int centerID = ((Number) event.get("centerID")).intValue();
			if(center.getCenterID() < 0)
			{
				center.setCenterID(centerID);
				try
				{
					sync.getKeeper().putCenter(center, null, null);
				} catch(PrismsRecordException e)
				{
					log.error("Could not persist center ID", e);
				}
			}
			else if(center.getCenterID() != centerID)
				throw new IllegalStateException("This center is not mapped to user "
					+ center.getClientUser());
			int remotePriority = ((Number) event.get("syncPriority")).intValue();
			if(center.getPriority() != remotePriority)
			{
				center.setPriority(remotePriority);
				try
				{
					sync.getKeeper().putCenter(center, null, null);
				} catch(PrismsRecordException e)
				{
					log.error("Could not persist center's priority", e);
				}
			}
			LatestCenterChange [] changes = parseCenterChanges((JSONArray) event.get("changes"));
			java.io.Writer writer = new java.io.OutputStreamWriter(
				new java.io.BufferedOutputStream(stream));
			if(DEBUG)
				writer = new prisms.util.LoggingWriter(writer,
					"C:\\Documents and Settings\\Andrew\\Desktop\\temp\\SyncData.json");
			try
			{
				sync.doSyncOutput(center, changes, syncType, writer, null,
					!Boolean.FALSE.equals(event.get("withRecords")),
					!Boolean.FALSE.equals(event.get("storeSyncRecord")));
			} catch(Throwable e)
			{
				throw new IllegalStateException(e.getMessage(), e);
			} finally
			{
				writer.close();
			}
		}
		else if("downloadSyncRequest".equals(event.get("method")))
		{
			try
			{
				if(!canSynchronizeUI())
					throw new IllegalArgumentException("User " + theSession.getUser() + " does not"
						+ " have permission to generate synchronization data for other centers");

				PrismsCenter center = getCenter(((Number) event.get("centerID")).intValue());
				if(center == null)
					throw new IllegalArgumentException("No such center with ID "
						+ event.get("centerID"));
				PrismsSynchronizer2 sync = getSynchronizer();
				stream = new prisms.util.ExportStream(stream);
				java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(stream);
				prisms.util.json.JsonStreamWriter jsw = new prisms.util.json.JsonStreamWriter(
					writer);
				jsw.startObject();
				jsw.startProperty("localCenterID");
				jsw.writeNumber(new Integer(sync.getKeeper().getCenterID()));
				jsw.startProperty("syncPriority");
				jsw.writeNumber(new Integer(sync.getKeeper().getLocalPriority()));
				if(center.getCenterID() >= 0)
				{
					jsw.startProperty("remoteCenterID");
					jsw.writeNumber(new Integer(center.getCenterID()));
				}
				jsw.startProperty("withRecords");
				jsw.writeBoolean(((Boolean) event.get("withRecords")).booleanValue());
				jsw.startProperty("changes");
				try
				{
					Record2Utils.serializeCenterChanges(sync.getKeeper(), jsw);
				} catch(PrismsRecordException e)
				{
					throw new IllegalStateException("Could not get center changes", e);
				}
				jsw.endObject();
				writer.flush();
				writer.close();
			} finally
			{
				isDownloadFinished = true;
			}
		}
		else if("downloadSyncData".equals(event.get("method")))
		{
			try
			{
				if(!canSynchronizeUI())
					throw new IllegalArgumentException("User " + theSession.getUser() + " does not"
						+ " have permission to generate synchronization data for other centers");

				JSONObject params = (JSONObject) event.get("syncParams");
				PrismsCenter center = getCenter(((Number) event.get("centerID")).intValue());
				if(center == null)
					throw new IllegalArgumentException("No such center with ID "
						+ params.get("centerID"));
				PrismsSynchronizer2 sync = getSynchronizer();
				int remoteCenterID = -1;
				if(params.containsKey("remoteCenterID"))
					remoteCenterID = ((Number) params.get("remoteCenterID")).intValue();
				if(remoteCenterID >= 0 && remoteCenterID != sync.getKeeper().getCenterID())
					throw new IllegalArgumentException("Sync request is not for this center");
				int localCenterID = ((Number) params.get("localCenterID")).intValue();
				if(center.getCenterID() < 0)
				{
					center.setCenterID(localCenterID);
					try
					{
						sync.getKeeper().putCenter(center, null, null);
					} catch(PrismsRecordException e)
					{
						log.error("Could not persist center ID", e);
					}
				}
				else if(center.getCenterID() != localCenterID)
					throw new IllegalArgumentException("Sync request is not from center " + center);
				if(localCenterID == remoteCenterID)
					throw new IllegalArgumentException("A center cannot synchronize with itself");
				int remotePriority = ((Number) params.get("syncPriority")).intValue();
				if(center.getPriority() != remotePriority)
				{
					center.setPriority(remotePriority);
					try
					{
						sync.getKeeper().putCenter(center, null, null);
					} catch(PrismsRecordException e)
					{
						log.error("Could not persist center's priority", e);
					}
				}
				LatestCenterChange [] changes = parseCenterChanges((JSONArray) params
					.get("changes"));
				stream = new prisms.util.ExportStream(stream);
				java.io.Writer writer = new java.io.OutputStreamWriter(stream);
				if(DEBUG)
					writer = new prisms.util.LoggingWriter(writer,
						"C:\\Documents and Settings\\Andrew\\Desktop\\temp\\SyncData.json");
				try
				{
					sync.doSyncOutput(center, changes, SyncRecord.Type.FILE, writer, thePI,
						!Boolean.FALSE.equals(params.get("withRecords")),
						!Boolean.FALSE.equals(params.get("storeSyncRecord")));
				} catch(PrismsRecordException e)
				{
					throw new IllegalStateException("Could not output sync data", e);
				} finally
				{
					writer.close();
				}
			} finally
			{
				thePI.setDone();
			}
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " download event: "
				+ event);
	}

	private LatestCenterChange [] parseCenterChanges(JSONArray json)
	{
		LatestCenterChange [] ret = new LatestCenterChange [json.size()];
		for(int i = 0; i < ret.length; i++)
		{
			JSONObject jcc = (JSONObject) json.get(i);
			ret[i] = new LatestCenterChange(((Number) jcc.get("centerID")).intValue(),
				((Number) jcc.get("subjectCenter")).intValue(),
				((Number) jcc.get("latestChange")).longValue());
		}
		return ret;
	}

	public void doUpload(JSONObject event, String fileName, String contentType,
		java.io.InputStream input, long size) throws java.io.IOException
	{
		if("receipt".equals(event.get("method")))
		{
			prisms.records2.PrismsSynchronizer2 sync = getSynchronizer();
			sync.readSyncReceipt(new java.io.InputStreamReader(input));
		}
		else if("uploadSyncRequest".equals(event.get("method")))
		{
			prisms.util.json.SAJParser.DefaultHandler handler;
			handler = new prisms.util.json.SAJParser.DefaultHandler();
			input = new prisms.util.ImportStream(input);
			try
			{
				new prisms.util.json.SAJParser().parse(new java.io.InputStreamReader(input),
					handler);
			} catch(ParseException e)
			{
				throw new IllegalStateException("Could not parse sync request", e);
			}
			PrismsCenter center = getCenter(((Number) event.get("centerID")).intValue());
			if(center == null)
				throw new IllegalArgumentException("No such center with ID "
					+ event.get("centerID"));
			startSyncDownload(center, (JSONObject) handler.finalValue());
		}
		else if("uploadSyncData".equals(event.get("method")))
		{
			prisms.ui.UI.AppLockProgress pi = new prisms.ui.UI.AppLockProgress(getSession()
				.getApp());
			pi.setPostReload(true);
			pi.setCancelable(true);
			pi.setProgressText("Uploading and parsing synchronization data");
			try
			{
				if(!canSynchronizeUI())
					throw new IllegalArgumentException("User " + theSession.getUser() + " does not"
						+ " have permission to generate synchronization data for other centers");

				PrismsCenter center = getCenter(((Number) event.get("centerID")).intValue());
				if(center == null)
					throw new IllegalArgumentException("No such center with ID "
						+ event.get("centerID"));
				input = new prisms.util.ImportStream(input);
				PrismsSynchronizer2 sync = getSynchronizer();
				java.io.Reader reader = new java.io.InputStreamReader(input);
				SyncRecord syncRecord = sync.doSyncInput(center, SyncRecord.Type.FILE, reader, pi,
					!Boolean.FALSE.equals(event.get("storeSyncRecord")));
				if(syncRecord != null)
					showSyncReceipt(syncRecord);
			} catch(PrismsRecordException e)
			{
				throw new IllegalArgumentException(e.getMessage(), e);
			} finally
			{
				pi.setDone();
			}
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " upload event: "
				+ event);
	}

	/**
	 * @param centerID The local ID of the center
	 * @return The center with the given ID
	 */
	protected PrismsCenter getCenter(int centerID)
	{
		PrismsCenter [] centers;
		try
		{
			centers = getSynchronizer().getKeeper().getCenters();
		} catch(prisms.records2.PrismsRecordException e)
		{
			throw new IllegalStateException("Could not retrieve centers", e);
		}
		for(PrismsCenter center : centers)
			if(center.getID() == centerID)
				return center;
		return null;
	}

	/**
	 * @return The PRISMS center represented by the current web service session
	 */
	protected PrismsCenter getCenter()
	{
		PrismsCenter [] centers;
		try
		{
			centers = getSynchronizer().getKeeper().getCenters();
		} catch(prisms.records2.PrismsRecordException e)
		{
			throw new IllegalStateException("Could not retrieve centers", e);
		}
		RecordUser sessionUser = getUser();
		for(PrismsCenter center : centers)
			if(center.getClientUser() != null && center.getClientUser().equals(sessionUser))
				return center;
		return null;
	}
}
