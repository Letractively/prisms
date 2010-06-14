/*
 * PrismsSynchronizer.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import static prisms.util.PrismsUtils.rEventProps;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsApplication;
import prisms.arch.event.PrismsEvent;
import prisms.ui.UI.DefaultProgressInformer;

/**
 * Synchronizes resources between centers
 * 
 * @param <SyncDataType> The type of the synchronization data that this synchronizer's
 *        implementation uses
 */
public class PrismsSynchronizer<SyncDataType extends SynchronizeImpl.SyncData>
{
	Logger log = Logger.getLogger(PrismsSynchronizer.class);

	private RecordKeeper theKeeper;

	private SynchronizeImpl<SyncDataType> theImpl;

	private String theAppName;

	private String theClientName;

	private String thePluginName;

	/**
	 * Creates a synchronizer
	 * 
	 * @param keeper The record keeper to use for this implementation
	 * @param impl The synchronization implementation for the data set
	 */
	public PrismsSynchronizer(RecordKeeper keeper, SynchronizeImpl<SyncDataType> impl)
	{
		theKeeper = keeper;
		theImpl = impl;
	}

	/**
	 * @return The synchronize implementation that powers this synchronizer
	 */
	public SynchronizeImpl<SyncDataType> getImpl()
	{
		return theImpl;
	}

	/**
	 * Sets the PRISMS application info this synchronizer needs to connect to a PRISMS server
	 * 
	 * @param appName The name of the application to connect to
	 * @param clientName The name of the client configuration to use
	 * @param pluginName The name of the plugin to communicate with
	 */
	public void setAppInfo(String appName, String clientName, String pluginName)
	{
		if(appName == null || clientName == null || pluginName == null)
			throw new IllegalStateException(
				"Application, client, and plugin names must be specified");
		theAppName = appName;
		theClientName = clientName;
		thePluginName = pluginName;
	}

	/**
	 * @return This synchronizer's record keeper. May be null.
	 */
	public RecordKeeper getRecordKeeper()
	{
		return theKeeper;
	}

	/**
	 * @param center The center to connect to
	 * @return An uninitialized connection to connect to the given center
	 * @throws PrismsRecordException If the connection cannot be made
	 */
	public prisms.util.PrismsServiceConnector connect(PrismsCenter center)
		throws PrismsRecordException
	{
		prisms.util.PrismsServiceConnector conn = new prisms.util.PrismsServiceConnector(center
			.getServerURL(), theAppName, theClientName, center.getServerUserName());
		conn.setPassword(center.getServerPassword());
		try
		{
			conn.init();
		} catch(prisms.util.PrismsServiceConnector.AuthenticationFailedException e)
		{
			throw new PrismsRecordException("User name/password combination is incorrect", e);
		} catch(prisms.util.PrismsServiceConnector.PrismsServiceException pse)
		{
			throw new PrismsRecordException(pse.getMessage(), pse);
		} catch(java.io.IOException e)
		{
			throw new PrismsRecordException("Could not communicate with server", e);
		}
		return conn;
	}

	/**
	 * @return The name of the plugin that this synchronizer calls
	 */
	public String getPluginName()
	{
		return thePluginName;
	}

	/**
	 * Synchronizes with all centers for which a synchronization attempt is due
	 * 
	 * @param app The application to synchronize data into
	 * @param pi The progress informer to notify of synchronization progress
	 */
	public void autoSync(PrismsApplication app, DefaultProgressInformer pi)
	{
		if(theKeeper == null)
		{
			log.error("Cannot auto-sync without a Jme3 data source");
			return;
		}
		if(app.getApplicationLock() != null)
		{
			log.info("Suspending auto-synchronization until application is unlocked");
			return;
		}
		if(pi != null)
			pi.setProgressText("Checking centers for synchronization requirement");
		long time = System.currentTimeMillis();
		PrismsCenter [] centers;
		try
		{
			centers = theKeeper.getCenters();
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
		if(pi != null)
			pi.setProgressScale(toSync.length);
		for(PrismsCenter rc : toSync)
		{
			try
			{
				sync(rc, app, SyncRecord.Type.AUTOMATIC, pi, null, true);
			} catch(PrismsRecordException e)
			{
				log.error("Could not synchronize with center " + rc, e);
			}
		}
	}

	/**
	 * Checks the synchronization status with a center
	 * 
	 * @param center The center to check synchronization with
	 * @param pi The progress informer to notify of synchronization progress
	 * @return The number of updates that are waiting to be applied upon synchronization, or -1 if
	 *         the number cannot be determined (as upon initial synchronization)
	 * @throws PrismsRecordException If an error occurs checking the synchronization status
	 */
	public int checkSync(PrismsCenter center, DefaultProgressInformer pi)
		throws PrismsRecordException
	{
		if(pi != null)
			pi.setCancelable(true);
		if(center.getServerURL() == null || center.getServerURL().length() == 0)
			throw new PrismsRecordException("No URL set for center " + center);
		if(pi != null)
			pi.setProgressText("Connecting to center " + center + " at URL "
				+ center.getServerURL());
		prisms.util.PrismsServiceConnector conn = connect(center);
		long lastSync = center.getLastImport();

		if(pi != null)
			pi.setProgressText("Checking synchronization status for center " + center);
		JSONObject res;
		try
		{
			res = conn.getResult(thePluginName, "getNewItemCount", "centerID", new Integer(
				theKeeper == null ? 0 : theKeeper.getCenterID()), "since", new Long(lastSync),
				"now", new Long(System.currentTimeMillis()));
		} catch(prisms.util.PrismsServiceConnector.PrismsServiceException e)
		{
			throw new PrismsRecordException("Could not get synchronization item count:\n"
				+ e.getPrismsMessage(), e);
		} catch(IOException e)
		{
			throw new PrismsRecordException("Could not get synchronization item count:\n"
				+ e.getMessage(), e);
		}
		if(center.getCenterID() < 0)
		{
			center.setCenterID(((Number) res.get("centerID")).intValue());
			try
			{
				theKeeper.putCenter(center, null);
			} catch(PrismsRecordException e)
			{
				log.error("Could not set centerID", e);
			}
		}
		else if(center.getCenterID() != ((Number) res.get("centerID")).intValue())
		{
			throw new PrismsRecordException(
				"The center contacted is different than the one stored.\n"
					+ "Please create a new center to synchronize to a different location.");
		}
		return ((Number) res.get("itemCount")).intValue();
	}

	/**
	 * Synchronizes with a center
	 * 
	 * @param center The center to synchronize with
	 * @param app The application to synchronize data into
	 * @param syncType The type of synchronization to be performed
	 * @param pi The progress informer to notify of synchronization progress
	 * @param session The session that is causing this synchronization--may be null
	 * @param lockApp Whether to lock the entire application while the synchronization is occurring
	 * @return The number of items synchronized
	 * @throws PrismsRecordException If an error occurs during synchronization
	 */
	public int sync(PrismsCenter center, PrismsApplication app, SyncRecord.Type syncType,
		DefaultProgressInformer pi, prisms.arch.PrismsSession session, boolean lockApp)
		throws PrismsRecordException
	{
		if(pi != null)
			pi.setCancelable(true);
		if(center.getServerURL() == null || center.getServerURL().length() == 0)
			throw new PrismsRecordException("No URL set for center " + center);
		if(pi != null)
			pi.setProgressText("Connecting to center " + center + " at URL "
				+ center.getServerURL());
		long lastSync = center.getLastImport();
		SyncRecord record = null;
		if(theKeeper != null)
		{
			record = new SyncRecord(center, syncType, System.currentTimeMillis(), true);
			record.setSyncError("?");
			theKeeper.putSyncRecord(record);
			app.fireGlobally(null, new PrismsEvent("syncAttempted", "record", record));
		}
		String error = null;
		Number serverRecordID = null;
		long now = System.currentTimeMillis();
		int centerID = theKeeper == null ? 0 : theKeeper.getCenterID();
		prisms.util.PrismsServiceConnector conn = null;
		try
		{
			try
			{
				conn = connect(center);
			} catch(PrismsRecordException e)
			{
				error = e.getMessage();
				throw e;
			}

			if(pi != null)
				pi.setProgressText("Synchronizing with center " + center);
			JSONObject res;
			try
			{
				res = conn.getResult(thePluginName, "synchronize", "centerID",
					new Integer(centerID), "since", lastSync < 0 ? null : new Long(lastSync),
					"now", new Long(now), "syncType", (record == null ? syncType : record
						.getSyncType()).toString(), "recordID", new Integer(record != null ? record
						.getID() : 0));
			} catch(prisms.util.PrismsServiceConnector.PrismsServiceException e)
			{
				throw new PrismsRecordException("Could not get synchronization items from server: "
					+ e.getPrismsMessage(), e);
			} catch(IOException e)
			{
				throw new PrismsRecordException("Could not get synchronization items from server: "
					+ e.getMessage(), e);
			}

			if(record != null)
			{
				serverRecordID = (Number) res.get("recordID");
				record.setParallelID(serverRecordID.intValue());
				theKeeper.putSyncRecord(record);
				app.fireGlobally(null, new PrismsEvent("syncAttemptChanged", "record", record));
			}
			if(center.getCenterID() < 0)
			{
				center.setCenterID(((Number) res.get("centerID")).intValue());
				if(theKeeper != null)
					try
					{
						theKeeper.putCenter(center, null);
					} catch(PrismsRecordException e)
					{
						throw new PrismsRecordException(
							"Synchronization failed: Could not write center ID: " + e.getMessage(),
							e);
					}
			}
			else if(center.getCenterID() != ((Number) res.get("centerID")).intValue())
			{
				throw new PrismsRecordException(
					"The center contacted is different than the one stored.\n"
						+ "Please create a new center to synchronize to a different location.");
			}
			return synchronize(center, app, record, pi, res, session, lockApp);
		} catch(PrismsRecordException e)
		{
			error = e.getMessage();
			throw e;
		} catch(Throwable e)
		{
			error = "Could not synchronize: " + e.getMessage();
			throw new PrismsRecordException("Could not synchronize: " + e.getMessage(), e);
		} finally
		{
			if(error == null)
			{
				center.setLastImport(now);
				if(theKeeper != null)
					try
					{
						theKeeper.putCenter(center, null);
					} catch(PrismsRecordException e)
					{
						log.error("Could not write center " + center + "'s last import time", e);
					}
			}
			if(record != null)
			{
				record.setSyncError(error);
				if(theKeeper != null)
					try
					{
						theKeeper.putSyncRecord(record);
					} catch(PrismsRecordException e)
					{
						log.error("Could not write synchronization error", e);
					}
				app.fireGlobally(null, new PrismsEvent("syncAttemptChanged", "record", record));
			}
			if(serverRecordID != null)
			{
				try
				{
					conn.callProcedure(thePluginName, "reportSuccess", true, "centerID",
						new Integer(centerID), "recordID", serverRecordID, "syncError", error);
				} catch(IOException e)
				{
					if(error != null)
						log.error("Could not report synchronization error \"" + error
							+ "\" to server", e);
					else
						log.error("Could not report synchonization success to server", e);
				}
			}
		}
	}

	/**
	 * Synchronizes a center with the given JSON response
	 * 
	 * @param center The center to synchronize on
	 * @param app The application to synchronize data into
	 * @param record The synchronization records to write the modifications with
	 * @param pi The progress informer to notify the user with
	 * @param json The JSON synchronization service response
	 * @param session The session that is causing this synchronization--may be null
	 * @param lockApp Whether to lock the entire application while the synchronization is occurring
	 * @return The number of items synchronized
	 * @throws PrismsRecordException If an error occurs during synchronization
	 */
	public int synchronize(PrismsCenter center, final PrismsApplication app, SyncRecord record,
		final prisms.ui.UI.DefaultProgressInformer pi, JSONObject json,
		final prisms.arch.PrismsSession session, final boolean lockApp)
		throws PrismsRecordException
	{
		ChangeRecord [] mods;
		if(json.get("mods") != null)
		{
			try
			{
				mods = deserializeModifications((JSONArray) json.get("mods"));
			} catch(RuntimeException e)
			{
				throw new PrismsRecordException("Could not deserialize modifications", e);
			} catch(PrismsRecordException e)
			{
				throw new PrismsRecordException("Could not deserialize modifications", e);
			}
		}
		else
			mods = new ChangeRecord [0];
		Object [] items;
		if(json.get("items") != null)
		{
			try
			{
				items = deserializeItems((JSONArray) json.get("items"));
			} catch(RuntimeException e)
			{
				throw new PrismsRecordException("Could not deserialize items", e);
			} catch(PrismsRecordException e)
			{
				log.error("Could not deserialize items", e);
				throw new PrismsRecordException("Could not deserialize items", e);
			}
		}
		else
			items = new Object [0];
		if(mods.length == 0 && items.length == 0)
			return 0;
		final String lockText;
		if(session != null)
			lockText = session.getUser() + " is synchronizing with " + center.getName()
				+ ".  Please wait.";
		else
			lockText = "Automatically synchronizing with " + center.getName() + ".  Please wait.";
		prisms.ui.UI.DefaultProgressInformer lockPI = new prisms.ui.UI.DefaultProgressInformer()
		{
			@Override
			public void setProgressText(String text)
			{
				super.setProgressText(text);
				if(pi != null)
					pi.setProgressText(text);
				if(lockApp)
				{
					String percent;
					if(getTaskScale() > 0)
						percent = (getTaskProgress() * 100.0f / getTaskScale()) + "%  ";
					else
						percent = "";
					app.setApplicationLock(lockText + "\n" + percent + text, session);
				}
			}

			@Override
			public void setProgressScale(int scale)
			{
				super.setProgressScale(scale);
				if(pi != null)
					pi.setProgressScale(scale);
				setProgressText(getTaskText());
			}

			@Override
			public void setProgress(int progress)
			{
				super.setProgress(progress);
				if(pi != null)
					pi.setProgress(progress);
			}
		};
		if(lockApp)
			app.setApplicationLock(lockText, session);
		try
		{

			SyncDataType data = theImpl.genSyncData(this, center, app, lockPI, items, mods);
			if(theKeeper != null)
				theKeeper.setSyncRecord(record);
			try
			{
				if(mods.length > 0)
					syncModifications(data);
				if(items.length > 0)
					syncItems(data);
			} catch(PrismsRecordException e)
			{
				throw new PrismsRecordException("Synchronization failed: " + e.getMessage(), e);
			} catch(RuntimeException e)
			{
				throw new PrismsRecordException("Synchronization failed: " + e.getMessage(), e);
			} finally
			{
				data.dispose();
			}
		} finally
		{
			if(lockApp)
				app.setApplicationLock(null, null);
		}
		return mods.length + items.length;
	}

	ChangeRecord [] deserializeModifications(JSONArray mods) throws PrismsRecordException
	{
		ChangeRecord [] ret = new ChangeRecord [mods.size()];
		for(int i = 0; i < ret.length; i++)
			ret[i] = deserializeModification((JSONObject) mods.get(i));
		return ret;
	}

	ChangeRecord deserializeModification(JSONObject json) throws PrismsRecordException
	{
		long id = ((Number) json.get("id")).longValue();
		long time = ((Number) json.get("time")).longValue();
		RecordUser user = (RecordUser) deserializeItem((JSONObject) json.get("user"));
		SubjectType subjectType = getType((String) json.get("subjectType"));
		ChangeType changeType = getChangeType(subjectType, (String) json.get("changeType"));
		int add = ((Number) json.get("additivity")).intValue();
		Object majorSubject = deserializeItem((JSONObject) json.get("majorSubject"));
		Object minorSubject = deserializeItem((JSONObject) json.get("minorSubject"));
		Object preValue = deserializeItem((JSONObject) json.get("preValue"));
		Object data1 = deserializeItem((JSONObject) json.get("data1"));
		Object data2 = deserializeItem((JSONObject) json.get("data2"));
		return new ChangeRecord(id, time, user, subjectType, changeType, add, majorSubject,
			minorSubject, preValue, data1, data2);
	}

	SubjectType getType(String typeName) throws PrismsRecordException
	{
		for(PrismsChange type : PrismsChange.values())
			if(type.toString().equals(typeName))
				return type;
		return theImpl.getType(typeName);
	}

	ChangeType getChangeType(SubjectType subjectType, String typeName) throws PrismsRecordException
	{
		if(typeName == null)
			return null;
		Class<? extends Enum<? extends ChangeType>> types = subjectType.getChangeTypes();
		if(types == null)
			throw new PrismsRecordException("Change domain " + subjectType + " allows no fields: "
				+ typeName);
		for(Enum<? extends ChangeType> f : types.getEnumConstants())
		{
			if(f.toString().equals(typeName))
				return (ChangeType) f;
		}
		throw new PrismsRecordException("Change type " + typeName
			+ " does not exist in subject type " + subjectType);
	}

	Object [] deserializeItems(JSONArray items) throws PrismsRecordException
	{
		Object [] ret = new Object [items.size()];
		for(int i = 0; i < ret.length; i++)
			ret[i] = deserializeItem((JSONObject) items.get(i));
		return ret;
	}

	Object deserializeItem(JSONObject json) throws PrismsRecordException
	{
		if(json == null)
			return null;
		String type = (String) json.get("type");
		Object value = json.get("value");
		if("int".equals(type))
		{
			if(value instanceof Integer)
				return value;
			else
				return new Integer(((Number) value).intValue());
		}
		if("long".equals(type))
		{
			if(value instanceof Long)
				return value;
			else
				return new Long(((Number) value).longValue());
		}
		if("float".equals(type))
		{
			if(value instanceof Float)
				return value;
			else if(value == null)
				return new Float(Float.NaN);
			else
				return new Float(((Number) value).floatValue());
		}
		if("double".equals(type))
		{
			if(value instanceof Double)
				return value;
			else if(value == null)
				return new Double(Double.NaN);
			else
				return new Double(((Number) value).doubleValue());
		}
		if("string".equals(type))
			return value;
		if("boolean".equals(type))
			return value;
		return theImpl.deserializeItem(json);
	}

	private void syncItems(SyncDataType data) throws PrismsRecordException
	{
		for(Object item : data.items)
		{
			if(item == null)
				continue; // May have already been done as a dependent of a previous item
			theImpl.syncObject(item, data);
		}
		data.dispose();
	}

	private void syncModifications(SyncDataType data) throws PrismsRecordException
	{
		for(ChangeRecord mod : data.mods)
		{
			if(mod == null)
				continue;
			ChangeRecord [] history;
			if(theKeeper == null)
				history = new ChangeRecord [0];
			else
				try
				{
					long [] historyIDs = theKeeper.getSuccessors(mod);
					history = theKeeper.getChanges(historyIDs);
				} catch(PrismsRecordException e)
				{
					log.error("Could not get item history: " + mod, e);
					continue;
				}
			ChangeRecord [] remoteSuccessors = getRemoteSuccessors(mod, data);
			for(int rsIdx = 0; rsIdx < remoteSuccessors.length; rsIdx++)
			{
				ChangeRecord rs = remoteSuccessors[rsIdx];
				boolean valid;
				switch(rs.type.additivity)
				{
				case 1:
					/* If the item has been created or deleted more recently than this creation,
					 * then the modification is superfluous */
					valid = (history.length == 0 || history[history.length - 1].time < rs.time)
						&& !theImpl.itemExists(rs, data);
					if(!valid)
						continue;
					/* This item needs to be added even if it will be deleted later because it needs
					 * to be in the database for the modification to take effect.
					 * boolean doAdd = rsIdx == remoteSuccessors.length - 1;
					 * if(doAdd && !addItem(rs, data))
					 * 		continue; */
					if(!theImpl.addObject(rs, data))
						continue;
					break;
				case -1:
					/* If the item has been created or deleted more recently than this deletion,
					 * then the modification is superfluous */
					valid = (history.length == 0 || history[history.length - 1].time < rs.time)
						&& theImpl.itemExists(rs, data);
					if(!valid)
						continue;
					boolean doRemove = rsIdx == remoteSuccessors.length - 1;
					if(doRemove && !theImpl.deleteObject(rs, data))
						continue;
					break;
				default: // case 0:
					if(!theImpl.itemExists(rs, data))
						continue;
					/* If the field in the item has been modifed more recently than this
					 * modification, we don't want to modify the item but we do want to write the
					 * history of the change */
					boolean doMod = (history.length == 0 || history[history.length - 1].time <= rs.time)
						&& rsIdx == remoteSuccessors.length - 1;
					if(doMod && !theImpl.setField(rs, theImpl.getFieldValue(rs, data), data))
						continue;
					break;
				}
				if(theKeeper != null)
					try
					{
						theKeeper.persist(rs);
					} catch(PrismsRecordException e)
					{
						throw new PrismsRecordException("Could not enact modification: "
							+ e.getMessage(), e);
					}
			}
		}
	}

	ChangeRecord [] getRemoteSuccessors(ChangeRecord mod,
		prisms.records.SynchronizeImpl.SyncData data)
	{
		java.util.ArrayList<ChangeRecord> ret = new java.util.ArrayList<ChangeRecord>();
		for(int m = 0; m < data.mods.length; m++)
		{
			if(data.mods[m] == null)
				continue;
			if(!mod.type.subjectType.equals(data.mods[m].type.subjectType))
				continue;
			if(!equal(mod.type.changeType, data.mods[m].type.changeType))
				continue;
			if(!equal(mod.majorSubject, data.mods[m].majorSubject))
				continue;
			if(!equal(mod.minorSubject, data.mods[m].minorSubject))
				continue;
			if((mod.type.additivity == 0) != (data.mods[m].type.additivity == 0))
				continue;
			ret.add(data.mods[m]);
			data.mods[m] = null;
		}
		return ret.toArray(new ChangeRecord [ret.size()]);
	}

	static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}

	// Export methods

	/**
	 * Generates the JSON response to a synchronization request from the given center
	 * 
	 * @param json The JSONObject to fill in with the info. If null, a new one will be created
	 * @param record The synchronization record
	 * @param sinceTime The last time the center synchronized with this center
	 * @param app The application to use to get the information
	 * @return The JSON synchronization response
	 * @throws PrismsRecordException If an error occurs generating the synchronization data
	 */
	public JSONObject genSynchronizeJson(JSONObject json, SyncRecord record, long sinceTime,
		PrismsApplication app) throws PrismsRecordException
	{
		if(json == null)
			json = new JSONObject();
		if(needsItems(record.getCenter(), sinceTime, record.getCenter().getClientUser()))
			json.put("items", serializeItems(app, record.getCenter()));
		if(record.getCenter().getCenterID() != 0 || json.get("items") == null)
		{ /* Don't put modifications for for a zero-center unless they save space */
			ChangeRecord [] mods = getExportModsSince(record.getCenter().getClientUser(), sinceTime);
			for(ChangeRecord mod : mods)
			{
				try
				{
					if(record.getCenter().getCenterID() > 0)
						theKeeper.associate(mod, record);
				} catch(PrismsRecordException e)
				{
					log.error("Could not associate client synchronization record " + record
						+ " with modification " + mod, e);
				}
			}
			json.put("mods", serializeChanges(mods));
		}
		return json;
	}

	JSONArray serializeChanges(ChangeRecord [] mods) throws PrismsRecordException
	{
		JSONArray ret = new JSONArray();
		for(ChangeRecord mod : mods)
		{
			if(mod.type.subjectType instanceof prisms.records.RecordKeeper.ErrorSubjectType)
				continue;
			if(mod.type.subjectType instanceof PrismsChange)
				continue;
			JSONObject json = new JSONObject();
			json.put("id", new Long(mod.id));
			json.put("time", new Long(mod.time));
			json.put("user", serializeItem(mod.user));
			json.put("subjectType", mod.type.subjectType.name());
			json.put("changeType", mod.type.changeType == null ? null : mod.type.changeType.name());
			json.put("additivity", new Integer(mod.type.additivity));
			json.put("majorSubject", serializeItem(mod.majorSubject));
			json.put("minorSubject", serializeItem(mod.minorSubject));
			json.put("preValue", serializeItem(mod.previousValue));
			json.put("data1", serializeItem(mod.data1));
			json.put("data2", serializeItem(mod.data2));
			ret.add(json);
		}
		return ret;
	}

	JSONArray serializeItems(PrismsApplication app, PrismsCenter center)
		throws PrismsRecordException
	{
		Object [] items = theImpl.getExportItems(app, center);
		JSONArray ret = new JSONArray();
		for(int i = 0; i < items.length; i++)
			ret.add(serializeItem(items[i]));
		return ret;
	}

	JSONObject serializeItem(Object item) throws PrismsRecordException
	{
		if(item == null)
			return null;
		if(item instanceof Integer)
			return rEventProps("type", "int", "value", item);
		else if(item instanceof Long)
			return rEventProps("type", "long", "value", item);
		else if(item instanceof Float)
		{
			if(((Float) item).isNaN())
				return rEventProps("type", "float", "value", null);
			else
				return rEventProps("type", "float", "value", item);
		}
		else if(item instanceof Double)
		{
			if(((Double) item).isNaN())
				return rEventProps("type", "double", "value", null);
			else
				return rEventProps("type", "double", "value", item);
		}
		else if(item instanceof String)
			return rEventProps("type", "string", "value", item);
		else if(item instanceof Boolean)
			return rEventProps("type", "boolean", "value", item);
		else
			return theImpl.serializeItem(item);
	}

	/**
	 * @param user The user to use to get the modifications
	 * @param since The earliest time at which to get modifications
	 * @return All modifications that have occurred to the data set since the given time
	 */
	public ChangeRecord [] getExportModsSince(RecordUser user, long since)
	{
		ChangeRecord [] ret;
		try
		{
			long [] ids = theKeeper.getChangeIDs(since);
			ret = theKeeper.getChanges(ids);
		} catch(PrismsRecordException e)
		{
			throw new IllegalStateException("Could not get modifications for synchronization", e);
		}
		prisms.util.ArrayUtils.reverse(ret);
		for(int m = 0; m < ret.length; m++)
			if(!shouldSend(ret[m]))
			{
				ret = prisms.util.ArrayUtils.remove(ret, m);
				m--;
			}
		return ret;
	}

	boolean shouldSend(ChangeRecord record)
	{
		if(record.type.subjectType instanceof prisms.records.RecordKeeper.ErrorSubjectType)
			return false;
		if(record.type.subjectType instanceof PrismsChange)
			return false;
		return true;
	}

	boolean needsItems(PrismsCenter center, long sinceTime, RecordUser user)
	{
		long time = System.currentTimeMillis();
		if(sinceTime <= time - center.getChangeSaveTime())
			return true;
		ChangeRecord [] history;
		try
		{
			long [] historyIDs = theKeeper.getHistory(center);
			history = theKeeper.getChanges(historyIDs);
		} catch(PrismsRecordException e)
		{
			log.error("Could not get center history", e);
			return false; // We'll assume the mod save time hasn't changed
		}
		for(ChangeRecord mod : history)
		{
			if(mod.time < sinceTime)
				return false;
			if(mod.type.changeType != PrismsChange.CenterChange.changeSaveTime)
				continue;
			long oldSaveTime = ((Number) mod.previousValue).longValue();
			if(sinceTime < mod.time - oldSaveTime)
				return true;
		}
		return false;
	}

	/**
	 * Gets the number of items that would need to be sent in a synchronization response
	 * 
	 * @param center The center synchronizing with this center
	 * @param sinceTime The last time the center synchronized with this center
	 * @param app The application to use to get the information
	 * @return The number of items (including modifications and items) that would be sent in
	 *         response to a request for synchronization
	 */
	public int getItemCount(PrismsCenter center, long sinceTime, PrismsApplication app)
	{
		ChangeRecord [] mods = getExportModsSince(center.getClientUser(), sinceTime);
		int itemCount = mods.length;
		if(needsItems(center, sinceTime, center.getClientUser()))
			itemCount += theImpl.getExportItems(app, center).length;
		return itemCount;
	}
}
