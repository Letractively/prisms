/*
 * SynchronizeService.java Created Dec 21, 2009 by Andrew Butler, PSL
 */
package prisms.records;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.util.json.JsonElement;

/**
 * Provides the synchronization service
 */
public abstract class SynchronizeService implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(SynchronizeService.class);

	private PrismsSession theSession;

	private String thePluginName;

	private JsonElement theClientValidator;

	private JsonElement theServerValidator;

	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		thePluginName = pluginEl.elementTextTrim("name");
		JsonElement [] validators = getValidators();
		if(validators != null)
		{
			if(validators.length > 0)
				theClientValidator = validators[0];
			if(validators.length > 1)
				theServerValidator = validators[1];
		}
	}

	/**
	 * @return The client input and server output validators, respectively against which to validate
	 *         this service
	 */
	protected JsonElement [] getValidators()
	{
		JsonElement client, server;
		java.net.URL url = getClass().getResource("SynchronizeService.json");
		if(url == null)
			log.error("No SynchronizeService.json schema--web service will be unvalidated");
		else
		{
			prisms.util.json.JsonElement schema = prisms.util.json.JsonSchemaParser.parseSchema(
				"rea3", url);
			if(!(schema instanceof prisms.util.json.JsonObjectElement))
				log.error("SynchronizeService.json schema is not properly formatted"
					+ "--web service will be unvalidated");
			else
			{
				client = ((prisms.util.json.JsonObjectElement) schema).getChild("client");
				server = ((prisms.util.json.JsonObjectElement) schema).getChild("server");
				return new JsonElement [] {client, server};
			}
		}
		return null;
	}

	public void initClient()
	{
	}

	/**
	 * @return The session using this service
	 */
	public PrismsSession getSession()
	{
		return theSession;
	}

	public void processEvent(JSONObject evt)
	{
		long hereNow = System.currentTimeMillis();

		try
		{
			if(theClientValidator != null)
				theClientValidator.validate(evt);
		} catch(prisms.util.json.JsonSchemaException e)
		{
			throw new IllegalArgumentException("Illegal JSON input: " + evt, e);
		}
		RecordUser user = getUser();
		int centerID = ((Number) evt.get("centerID")).intValue();
		if(centerID == getCenterID())
			throw new IllegalArgumentException("A center cannot synchronize with itself");
		PrismsCenter center = null;
		for(PrismsCenter rc : getCenters())
			if(user.equals(rc.getClientUser()))
			{
				center = rc;
				break;
			}
		if(center == null || (center.getCenterID() >= 0 && center.getCenterID() != centerID))
			throw new IllegalArgumentException("Client user " + user + " has not been given"
				+ " access to synchronization for your center. Contact the admin at this center"
				+ " before attempting to synchronize again");

		Number since = (Number) evt.get("since");
		Number thereNow = (Number) evt.get("now");
		String typeStr = (String) evt.get("syncType");
		Number clientRecordID = (Number) evt.get("recordID");
		JSONObject ret = new JSONObject();
		ret.put("plugin", thePluginName);
		PrismsSynchronizer<?> synchronizer = getSynchronizer();
		if(center.getCenterID() < 0)
		{
			center.setCenterID(centerID);
			theSession.fireEvent("centerChanged", "center", center);
		}
		ret.put("centerID", new Integer(synchronizer.getRecordKeeper() != null ? synchronizer
			.getRecordKeeper().getCenterID() : 0));

		long sinceTime;
		if(since != null && thereNow != null)
			sinceTime = since.longValue() - thereNow.longValue() + hereNow;
		else
			sinceTime = -1;
		SyncRecord syncAttempt = null;
		if("synchronize".equals(evt.get("method")))
		{
			log.debug("Center " + center + " is attempting to synchronize");
			if(typeStr == null)
				throw new IllegalArgumentException("Must include synchronization type for record");
			SyncRecord.Type type = SyncRecord.Type.byName(typeStr);
			if(type == null)
				throw new IllegalArgumentException("Synchronization type " + typeStr
					+ " is invalid");
			syncAttempt = new SyncRecord(center, type, System.currentTimeMillis(), false);
			if(centerID != 0) // Allow for unrecorded synchronization from zero center
			{
				syncAttempt.setParallelID(clientRecordID.intValue());
				syncAttempt.setSyncError("?");
				theSession.fireEvent("syncAttempted", "record", syncAttempt);
				ret.put("recordID", new Integer(syncAttempt.getID()));
			}
			try
			{
				synchronizer.genSynchronizeJson(ret, syncAttempt, sinceTime, theSession);
			} catch(PrismsRecordException e)
			{
				throw new IllegalStateException("Could not generate synchronization data", e);
			}
		}
		else if("getNewItemCount".equals(evt.get("method")))
		{
			log.debug("Center " + center + " is attempting to check synchronization status");
			ret.put("itemCount", new Integer(synchronizer.getItemCount(center, sinceTime,
				theSession)));
		}
		else if("reportSuccess".equals(evt.get("method")))
		{
			if(synchronizer.getRecordKeeper() == null || centerID == 0)
				return;
			int recordID = ((Number) evt.get("recordID")).intValue();
			SyncRecord [] records;
			try
			{
				records = synchronizer.getRecordKeeper().getSyncRecords(center, Boolean.FALSE);
			} catch(PrismsRecordException e)
			{
				log.error("Could not get synchronization records", e);
				return;
			}
			SyncRecord record = null;
			for(SyncRecord r : records)
				if(r.getID() == recordID)
				{
					record = r;
					break;
				}
			if(record == null)
				throw new IllegalArgumentException("No such synchronization record: ID " + recordID);
			/* We don't set the variable syncAttempt here because even if we fail in this method,
			 * the synchronization attempt's success isn't affected */
			String syncError = (String) evt.get("syncError");
			if(syncError != null)
				syncError = "Error on client: " + syncError;
			log.debug("Center " + center + " is reporting its synchronization success: "
				+ (syncError == null ? "Success!" : syncError));
			record.setSyncError(syncError);
			theSession.fireEvent("syncAttemptChanged", "record", record);
		}
		else
			throw new IllegalArgumentException("Unrecognized " + thePluginName + " event: " + evt);

		try
		{
			if(theServerValidator != null)
				theServerValidator.validate(ret);
		} catch(prisms.util.json.JsonSchemaException e)
		{
			if(syncAttempt != null)
			{
				syncAttempt.setSyncError("Illegal JSON output: " + e.getMessage());
				theSession.fireEvent("syncAttemptChanged", "record", syncAttempt);
			}
			throw new IllegalStateException("Illegal JSON output", e);
		}

		theSession.postOutgoingEvent(ret);
	}

	/**
	 * @return The local center ID
	 */
	protected abstract int getCenterID();

	/**
	 * @return The user for this session
	 */
	protected abstract RecordUser getUser();

	/**
	 * @return All PRISMS centers that may synchronize to this service
	 */
	protected abstract PrismsCenter [] getCenters();

	/**
	 * @return The synchronizer to generate and handle synchronization data
	 */
	protected abstract PrismsSynchronizer<?> getSynchronizer();
}
