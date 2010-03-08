/*
 * PrismsSynchronizer.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
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

	private String thePluginName = "REA Synchronization Service";

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

	private long theLastServerSync;

	/**
	 * Checks the synchronization status with a center
	 * 
	 * @param center The center to check synchronization with
	 * @param session The session get info necessary for the check
	 * @param pi The progress informer to notify of synchronization progress
	 * @return The number of updates that are waiting to be applied upon synchronization, or -1 if
	 *         the number cannot be determined (as upon initial synchronization)
	 * @throws IOException If an error occurs checking the synchronization status
	 */
	public int checkSync(PrismsCenter center, PrismsSession session, DefaultProgressInformer pi)
		throws IOException
	{
		if(pi != null)
			pi.setCancelable(true);
		if(center.getServerURL() == null || center.getServerURL().length() == 0)
			throw new IOException("No URL set for center " + center);
		if(pi != null)
			pi.setProgressText("Connecting to rules center " + center + " at URL "
				+ center.getServerURL());
		prisms.util.PrismsServiceConnector conn = new prisms.util.PrismsServiceConnector(center
			.getServerURL(), theAppName, theClientName, center.getServerUserName());
		conn.setPassword(center.getServerPassword());
		try
		{
			conn.init();
		} catch(prisms.util.PrismsServiceConnector.AuthenticationFailedException e)
		{
			IOException toThrow = new java.io.IOException(
				"User name/password combination is incorrect");
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		} catch(prisms.util.PrismsServiceConnector.PrismsServiceException pse)
		{
			IOException toThrow = new IOException(pse.getPrismsMessage());
			toThrow.setStackTrace(pse.getStackTrace());
			throw toThrow;
		} catch(java.io.IOException e)
		{
			IOException toThrow = new IOException("Could not communicate with server");
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}

		long lastSync;
		if(theKeeper == null)
			lastSync = theLastServerSync;
		else
			lastSync = center.getLastImport();

		if(pi != null)
			pi.setProgressText("Checking synchronization status for center " + center);
		JSONObject res;
		try
		{
			res = conn.getResult(thePluginName, "getNewItemCount", "centerID", new Integer(
				theKeeper.getCenterID()), "since", new Long(lastSync), "now", new Long(System
				.currentTimeMillis()));
		} catch(IOException e)
		{
			IOException toThrow = new IOException("Could not get synchronization item count");
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}
		if(center.getID() < 0)
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
			throw new IOException("The rule center contacted is different than the one stored.\n"
				+ "Please create a new rule center to synchronize to a different location.");
		}
		return ((Number) res.get("itemCount")).intValue();
	}

	/**
	 * Synchronizes with a rules center
	 * 
	 * @param center The rules center to synchronize with
	 * @param session The session to use for synchronization
	 * @param syncType The type of synchronization to be performed
	 * @param pi The progress informer to notify of synchronization progress
	 * @return The number of items synchronized
	 * @throws IOException If an error occurs during synchronization
	 */
	public int sync(PrismsCenter center, PrismsSession session, SyncRecord.Type syncType,
		DefaultProgressInformer pi) throws IOException
	{
		if(pi != null)
			pi.setCancelable(true);
		if(center.getServerURL() == null || center.getServerURL().length() == 0)
			throw new IOException("No URL set for center " + center);
		if(pi != null)
			pi.setProgressText("Connecting to rules center " + center + " at URL "
				+ center.getServerURL());
		prisms.util.PrismsServiceConnector conn = new prisms.util.PrismsServiceConnector(center
			.getServerURL(), theAppName, theClientName, center.getServerUserName());
		conn.setPassword(center.getServerPassword());
		try
		{
			conn.init();
		} catch(prisms.util.PrismsServiceConnector.AuthenticationFailedException e)
		{
			IOException toThrow = new IOException("User name/password combiniation is incorrect");
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		} catch(IOException e)
		{
			IOException toThrow = new IOException("Could not communicate with server");
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}

		if(pi != null)
			pi.setProgressText("Synchronizing with rules center " + center);
		long lastSync;
		if(theKeeper == null)
			lastSync = theLastServerSync;
		else
			lastSync = center.getLastImport();
		SyncRecord record = null;
		if(theKeeper != null)
		{
			record = new SyncRecord(center, syncType, System.currentTimeMillis(), true);
			record.setSyncError("?");
			session.fireEvent("syncAttempted", "record", record);
		}
		String error = null;
		Number serverRecordID = null;
		long now = System.currentTimeMillis();
		try
		{
			JSONObject res;
			try
			{
				res = conn.getResult(thePluginName, "synchronizeIWEDA", "since", lastSync < 0
					? null : new Long(lastSync), "now", new Long(now));
			} catch(IOException e)
			{
				log.error("Could not get synchronization items from server", e);
				IOException toThrow = new IOException(
					"Could not get synchronization items from server");
				toThrow.setStackTrace(e.getStackTrace());
				error = toThrow.getMessage();
				throw toThrow;
			}

			serverRecordID = (Number) res.get("recordID");
			if(record != null)
			{
				record.setParallelID(serverRecordID.intValue());
				session.fireEvent("syncAttemptChanged", "record", record);
			}
			try
			{
				if(center.getCenterID() < 0)
				{
					center.setCenterID(((Number) res.get("centerID")).intValue());
					session.fireEvent("centerChanged", "ruleCenter", center);
				}
				else if(center.getCenterID() != ((Number) res.get("centerID")).intValue())
				{
					throw new IOException("The rule center contacted is different than the one"
						+ " stored.\nPlease create a new rule center to synchronize to a different"
						+ " location.");
				}
				return synchronize(center, session, record, pi, res);
			} catch(IOException e)
			{
				error = e.getMessage();
				throw e;
			}
		} finally
		{
			if(error != null)
				theLastServerSync = now;
			if(record != null)
			{
				record.setSyncError(error);
				session.fireEvent("syncAttemptChanged", "record", record);
			}
		}
	}

	/**
	 * Synchronizes a center with the given JSON response
	 * 
	 * @param center The center to synchronize on
	 * @param session The session to use for synchronization
	 * @param record The synchronization records to write the modifications with
	 * @param pi The progress informer to notify the user with
	 * @param json The JSON synchronization service response
	 * @return The number of items synchronized
	 * @throws IOException If an error occurs during synchronization
	 */
	public int synchronize(PrismsCenter center, PrismsSession session, SyncRecord record,
		prisms.ui.UI.DefaultProgressInformer pi, JSONObject json) throws IOException
	{
		ChangeRecord [] mods;
		if(json.get("mods") != null)
		{
			try
			{
				mods = deserializeModifications((JSONArray) json.get("mods"));
			} catch(RuntimeException e)
			{
				log.error("Could not deserialize modifications", e);
				throw new IOException("Could not deserialize modifications");
			} catch(PrismsRecordException e)
			{
				log.error("Could not deserialize modifications", e);
				throw new IOException("Could not deserialize modifications");
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
				log.error("Could not deserialize items", e);
				throw new IOException("Could not deserialize items");
			} catch(PrismsRecordException e)
			{
				log.error("Could not deserialize items", e);
				throw new IOException("Could not deserialize items");
			}
		}
		else
			items = new Object [0];
		if(theKeeper != null)
			theKeeper.setSyncRecord(record);
		Object syncObj = theKeeper != null ? theKeeper : this;
		synchronized(syncObj)
		{
			SyncDataType data = theImpl.genSyncData(center, session, pi, items, null);
			try
			{
				if(mods.length > 0)
					syncModifications(center, session, mods, pi, data);
				if(items.length > 0)
					syncItems(center, session, items, pi, data);
			} catch(PrismsRecordException e)
			{
				log.error("Synchronization failed", e);
				throw new IOException("Synchronization failed: " + e.getMessage());
			} catch(RuntimeException e)
			{
				log.error("Synchronization failed", e);
				throw new IOException("Synchronization failed: " + e.getMessage());
			} finally
			{
				data.dispose();
				if(theKeeper != null)
					theKeeper.setSyncRecord(null);
			}
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
		for(PrismsChanges type : PrismsChanges.values())
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
		return theImpl.deserializeItem(json);
	}

	private void syncItems(PrismsCenter center, PrismsSession session, Object [] items,
		DefaultProgressInformer pi, SyncDataType data) throws PrismsRecordException
	{
		for(Object item : items)
		{
			if(item == null)
				continue; // May have already been done as a dependent of a previous item
			theImpl.syncObject(item, data);
		}
		data.dispose();
	}

	private void syncModifications(PrismsCenter center, PrismsSession session,
		ChangeRecord [] mods, prisms.ui.UI.DefaultProgressInformer pi, SyncDataType data)
		throws PrismsRecordException
	{
		for(ChangeRecord mod : mods)
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
						log.error("Could not enact modification " + mod, e);
						throw new PrismsRecordException("Could not enact modification: "
							+ e.getMessage());
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
}
