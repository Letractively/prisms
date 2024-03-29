/*
 * PrismsSynchronizer.java Created Jul 29, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.records.RecordPersister.ChangeData;
import prisms.records.SynchronizeImpl.ItemGetter;
import prisms.records.SynchronizeImpl.ItemWriter;
import prisms.util.ArrayUtils;
import prisms.util.json.SAJParser.ParseException;
import prisms.util.json.SAJParser.ParseState;

/** Provides the ability to keep two sets of java objects synchronized across a network connection */
public class PrismsSynchronizer
{
	/** Listens for new sync records created by the synchronizer */
	public interface SyncListener
	{
		/** @param record The new synchronization attempt */
		void syncAttempted(SyncRecord record);

		/** @param record The sync record that was changed */
		void syncChanged(SyncRecord record);
	}

	/**
	 * A default implementation of SyncListener that fires application-wide events whenever
	 * synchronization is attempted or a sync record changed
	 */
	public static class DefaultSyncListener implements SyncListener
	{
		private prisms.arch.PrismsApplication theApp;

		/**
		 * Creates a DefaultSyncListener
		 * 
		 * @param app The application to use to fire the events
		 */
		public DefaultSyncListener(prisms.arch.PrismsApplication app)
		{
			theApp = app;
		}

		public void syncAttempted(SyncRecord record)
		{
			theApp.fireGlobally(null, new prisms.arch.event.PrismsEvent("syncAttempted", "record",
				record));
		}

		public void syncChanged(SyncRecord record)
		{
			theApp.fireGlobally(null, new prisms.arch.event.PrismsEvent("syncAttemptChanged",
				"record", record));
		}
	}

	/**
	 * Allows calling methods a chance to create dependent centers or perform other operations after
	 * a center's center ID is set
	 */
	public static interface PostIDSet
	{
		/**
		 * Called after the center ID for a center is set
		 * 
		 * @param sync The synchronizer (may be this or a dependent center) that the center is for
		 * @param center The center whose ID has been set
		 * @throws PrismsRecordException If an unrecoverable error occurs performing the operation
		 */
		void postIDSet(PrismsSynchronizer sync, PrismsCenter center) throws PrismsRecordException;
	}

	/**
	 * The JSON property within a synchronization stream that the dependent synchronization data is
	 * sent in
	 */
	public static final String DEPENDS = "depends";

	/** The JSON property within a synchronization stream that the list of all items is sent as */
	public static final String ALL_ITEMS = "allItems";

	/** The JSON property within a synchronization stream that the list of change records is sent as */
	public static final String CHANGES = "changes";

	/**
	 * The JSON property within a synchronization stream that the center ID of the remote center is
	 * sent as
	 */
	public static final String CENTER_ID = "centerID";

	/**
	 * The JSON property within a synchronization stream that the ID of the synchronization record
	 * on the remote center is sent as
	 */
	public static final String PARALLEL_ID = "parallelID";

	static final Logger log = Logger.getLogger(PrismsSynchronizer.class);

	private static final class RuntimeWrapper extends RuntimeException
	{
		RuntimeWrapper(String message, Exception cause)
		{
			super(message, cause);
		}
	}

	static class ObjectID
	{
		final String type;

		final long id;

		ObjectID(String aType, long anID)
		{
			if(aType == null)
				throw new NullPointerException("Null object type");
			type = aType;
			id = anID;
		}

		@Override
		public boolean equals(Object o)
		{
			if(!(o instanceof ObjectID))
				return false;
			return ((ObjectID) o).type.equals(type) && ((ObjectID) o).id == id;
		}

		@Override
		public int hashCode()
		{
			return type.hashCode() * 13 + (int) (id / RecordUtils.theCenterIDRange * 7)
				+ (int) (id % RecordUtils.theCenterIDRange);
		}

		@Override
		public String toString()
		{
			return type + "(" + id + ")";
		}
	}

	/** A synchronization transaction */
	public class SyncTransaction
	{
		private SynchronizeImpl theImpl;

		private SyncRecord theSyncRecord;

		private boolean shouldStoreSyncRecord;

		private prisms.ui.UI.DefaultProgressInformer thePI;

		SyncTransaction(SynchronizeImpl impl, SyncRecord syncRecord, boolean storeSyncRecord,
			prisms.ui.UI.DefaultProgressInformer pi)
		{
			theImpl = impl;
			theSyncRecord = syncRecord;
			shouldStoreSyncRecord = storeSyncRecord;
			thePI = pi;
		}

		/** @return The sync implementation that this transaction uses */
		public SynchronizeImpl getImpl()
		{
			if(theImpl == null) // Client didn't send version--use earliest sync impl
				theImpl = PrismsSynchronizer.this.getImpls()[0];
			return theImpl;
		}

		/** @return The synchronizer that generated this transaction */
		public PrismsSynchronizer getSync()
		{
			return PrismsSynchronizer.this;
		}

		void setImpl(SynchronizeImpl impl)
		{
			theImpl = impl;
		}

		/** @return The sync record of this transaction's synchronization attempt */
		public SyncRecord getSyncRecord()
		{
			return theSyncRecord;
		}

		/** @return Whether this transaction's sync attempt will be stored as a sync record */
		public boolean shouldStoreSyncRecord()
		{
			return shouldStoreSyncRecord;
		}

		/** @return The progress informer that will be updated as this sync transaction progresses */
		public prisms.ui.UI.DefaultProgressInformer getPI()
		{
			return thePI;
		}

		int relativePriority(PrismsCenter center)
		{
			return center.getPriority() - getKeeper().getLocalPriority();
		}

		/**
		 * Gets the type of an item
		 * 
		 * @param item The item to get the string type of
		 * @return The string type of the given item
		 * @throws PrismsRecordException If the item's type is not recognized
		 */
		String getType(Object item) throws PrismsRecordException
		{
			if(item instanceof PrismsCenter)
				return "center";
			else if(item instanceof AutoPurger)
				return "purger";
			else
				return getImpl().getType(item.getClass());
		}

		/**
		 * Gets the identifier of an item
		 * 
		 * @param item The item to get the identifier of
		 * @return The item's identifier
		 */
		long getID(Object item)
		{
			if(item instanceof PrismsCenter)
				return ((PrismsCenter) item).getID();
			else if(item instanceof AutoPurger)
				return 0;
			else
				return getImpl().getID(item);
		}

		Object [] getDepends(Object item) throws PrismsRecordException
		{
			if(item instanceof PrismsCenter || item instanceof AutoPurger)
				return new Object [0];
			else
				return getImpl().getDepends(item);
		}

		/**
		 * Screens out changes that should not be sent for synchronization. Such changes may be kept
		 * only for record-keeping purposes on local data, as is the case with changes to PRISMS
		 * centers and auto-purge functionality.
		 * 
		 * @param record The record to test
		 * @return Whether the change record is one that should be sent to the remote center on sync
		 */
		boolean shouldSend(ChangeRecord record)
		{
			if(record instanceof ChangeRecordError)
			{
				for(PrismsChange pc : PrismsChange.values())
					if(pc.name().equals(((ChangeRecordError) record).getSubjectType()))
						return false;
				return true;
			}
			else if(record.type.subjectType instanceof PrismsChange)
				return false;
			else
				return getImpl().shouldSend(record);
		}

		Object getCurrentValue(ChangeRecord record) throws PrismsRecordException
		{
			if(record.type.subjectType instanceof PrismsChange)
			{
				switch((PrismsChange) record.type.subjectType)
				{
				case center:
					PrismsCenter center = (PrismsCenter) record.majorSubject;
					switch((PrismsChange.CenterChange) record.type.changeType)
					{
					case name:
						return center.getName();
					case url:
						return center.getServerURL();
					case serverCerts:
						return center.getCertificates();
					case serverUserName:
						return center.getServerUserName();
					case serverPassword:
						return center.getServerPassword();
					case syncFrequency:
						return Long.valueOf(center.getServerSyncFrequency());
					case clientUser:
						return center.getClientUser();
					case changeSaveTime:
						return Long.valueOf(center.getChangeSaveTime());
					}
					break;
				case autoPurge:
					AutoPurger purger = (AutoPurger) record.majorSubject;
					switch((PrismsChange.AutoPurgeChange) record.type.changeType)
					{
					case entryCount:
						return Integer.valueOf(purger.getEntryCount());
					case age:
						return Long.valueOf(purger.getAge());
					case excludeUser:
					case excludeType:
						return null;
					}
					break;
				}
				return null;
			}
			else
				return getImpl().getCurrentValue(record);
		}

		void writeItem(Object item, prisms.util.json.JsonSerialWriter jsonWriter,
			prisms.records.SynchronizeImpl.ItemWriter itemWriter, boolean justID)
			throws IOException, PrismsRecordException
		{
			if(item instanceof PrismsCenter)
			{
				PrismsCenter center = (PrismsCenter) item;
				jsonWriter.startProperty("name");
				jsonWriter.writeString(center.getName());
				if(justID)
					return;
				if(center.getCenterID() >= 0)
				{
					jsonWriter.startProperty("centerID");
					jsonWriter.writeNumber(Integer.valueOf(center.getCenterID()));
				}
				jsonWriter.startProperty("serverURL");
				jsonWriter.writeString(center.getServerURL());
				jsonWriter.startProperty("serverUserName");
				jsonWriter.writeString(center.getServerUserName());
				jsonWriter.startProperty("serverPassword");
				jsonWriter.writeString(center.getServerPassword());
				jsonWriter.startProperty("syncFrequency");
				jsonWriter.writeNumber(Long.valueOf(center.getServerSyncFrequency()));
				jsonWriter.startProperty("clientUser");
				itemWriter.writeItem(center.getClientUser());
				jsonWriter.startProperty("changeSaveTime");
				jsonWriter.writeNumber(Long.valueOf(center.getChangeSaveTime()));
				jsonWriter.startProperty("syncPriority");
				jsonWriter.writeNumber(Integer.valueOf(center.getPriority()));
				jsonWriter.startProperty("lastImport");
				jsonWriter.writeNumber(Long.valueOf(center.getLastImport()));
				jsonWriter.startProperty("lastExport");
				jsonWriter.writeNumber(Long.valueOf(center.getLastExport()));
				jsonWriter.startProperty("certificates");
				if(center.getCertificates() == null || center.getCertificates().length == 0)
					jsonWriter.writeNull();
				else
				{
					byte [] certBytes = DBRecordKeeper.getCertBytes(center.getCertificates());
					StringBuilder certStr = new StringBuilder();
					char [] hex = new char [] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
						'A', 'B', 'C', 'D', 'E', 'F'};
					for(byte b : certBytes)
						certStr.append(hex[(b & 0xf0) >>> 4]).append(hex[b & 0xf]);
					jsonWriter.writeString(certStr.toString());
				}
				if(center.isDeleted())
				{
					jsonWriter.startProperty("deleted");
					jsonWriter.writeBoolean(true);
				}
			}
			else if(item instanceof java.security.cert.X509Certificate[])
			{
				byte [] certBytes = DBRecordKeeper
					.getCertBytes((java.security.cert.X509Certificate[]) item);
				StringBuilder certStr = new StringBuilder();
				char [] hex = new char [] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A',
					'B', 'C', 'D', 'E', 'F'};
				for(byte b : certBytes)
					certStr.append(hex[(b & 0xf0) >>> 4]).append(hex[b & 0xf]);
				jsonWriter.writeString(certStr.toString());
			}
			else if(item instanceof AutoPurger)
			{
				if(justID)
					return;
				AutoPurger purger = (AutoPurger) item;
				jsonWriter.startProperty("age");
				jsonWriter.writeNumber(Long.valueOf(purger.getAge()));
				jsonWriter.startProperty("entryCount");
				jsonWriter.writeNumber(Integer.valueOf(purger.getEntryCount()));
				if(purger.getExcludeUsers().length > 0)
				{
					jsonWriter.startProperty("excludeUsers");
					jsonWriter.startArray();
					for(RecordUser user : purger.getExcludeUsers())
						itemWriter.writeItem(user);
					jsonWriter.endArray();
				}
				if(purger.getExcludeTypes().length > 0)
				{
					jsonWriter.startProperty("excludeTypes");
					jsonWriter.startArray();
					for(RecordType type : purger.getExcludeTypes())
					{
						jsonWriter.startObject();
						jsonWriter.startProperty("subjectType");
						jsonWriter.writeString(type.subjectType.name());
						if(type.changeType != null)
						{
							jsonWriter.startProperty("changeType");
							jsonWriter.writeString(type.changeType.name());
						}
						jsonWriter.startProperty("additivity");
						jsonWriter.writeString(type.additivity > 0 ? "+" : (type.additivity < 0
							? "-" : "0"));
						jsonWriter.endObject();
					}
					jsonWriter.endArray();
				}
			}
			else
				getImpl().writeItem(item, jsonWriter, itemWriter, justID);
		}

		Object parseID(JSONObject json, SynchronizeImpl.ItemReader reader, boolean [] newItem)
			throws PrismsRecordException
		{
			/* Currently at least, centers and auto-purge settings are local only, so these will never
			 * be sent across, meaning this code here will never be executed.  I'm leaving it in in case
			 * this condition ever changes. */
			if("center".equals(json.get("type")))
			{
				// Need to retrieve existing center
				PrismsCenter center = new PrismsCenter(((Number) json.get("id")).intValue(),
					(String) json.get("name"));
				newItem[0] = true;
				return center;
			}
			else if("purger".equals(json.get("type")))
			{
				// Need to retrieve existing auto-purger
				AutoPurger purger = new AutoPurger();
				newItem[0] = true;
				return purger;
			}
			else
				return getImpl().parseID(json, reader, newItem);
		}

		void parseContent(Object item, JSONObject json, boolean newItem,
			prisms.records.SynchronizeImpl.ItemReader reader) throws PrismsRecordException
		{
			/* Currently at least, centers and auto-purge settings are local only, so these will never
			 * be sent across, meaning this code here will never be executed.  I'm leaving it in in case
			 * this condition ever changes. */
			if(item instanceof PrismsCenter)
			{
				// Need to retrieve existing center
				PrismsCenter center = (PrismsCenter) item;
				if(json.get("centerID") != null)
					center.setCenterID(((Number) json.get("centerID")).intValue());
				center.setPriority(((Number) json.get("syncPriority")).intValue());
				center.setServerURL((String) json.get("serverURL"));
				center.setServerUserName((String) json.get("serverUserName"));
				center.setServerPassword((String) json.get("serverPassword"));
				center.setServerSyncFrequency(((Number) json.get("syncFrequency")).longValue());
				center.setClientUser((RecordUser) reader.read((JSONObject) json.get("clientUser")));
				center.setChangeSaveTime(((Number) json.get("changeSaveTime")).longValue());
				if(json.get("deleted") != null && ((Boolean) json.get("deleted")).booleanValue())
					center.setDeleted(true);
				getKeeper().putCenter(center, null);
			}
			else if(item instanceof AutoPurger)
			{
				// Need to retrieve existing auto-purger
				AutoPurger purger = (AutoPurger) item;
				purger.setAge(((Number) json.get("age")).longValue());
				purger.setEntryCount(((Number) json.get("entryCount")).intValue());
				if(json.get("excludeUsers") != null)
					for(Object user : (org.json.simple.JSONArray) json.get("excludeUsers"))
						purger.addExcludeUser((RecordUser) reader.read((JSONObject) user));
				if(json.get("excludeTypes") != null)
					for(Object type : (org.json.simple.JSONArray) json.get("excludeUsers"))
					{
						JSONObject jsonType = (JSONObject) type;
						SubjectType subject = getSubjectType((String) jsonType.get("subjectType"));
						ChangeType change = null;
						if(jsonType.get("changeType") != null)
							change = RecordUtils.getChangeType(subject,
								(String) jsonType.get("changeType"));
						char addCh = ((String) jsonType.get("additivity")).charAt(0);
						int add = addCh == '+' ? 1 : (addCh == '-' ? -1 : 0);
						purger.addExcludeType(new RecordType(subject, change, add));
					}
				if(getKeeper() instanceof DBRecordKeeper)
					((DBRecordKeeper) getKeeper()).setAutoPurger(purger, new RecordsTransaction(
						null, false));
			}
			else
				getImpl().parseContent(item, json, newItem, reader);
		}

		prisms.records.ChangeRecord parseChange(JSONObject json, PS2ItemReader reader)
		{
			if(json.containsKey("error"))
				return parseErrorChange(json, reader);
			long id = ((Number) json.get("id")).longValue();
			long time = ((Number) json.get("time")).longValue();
			long userID = ((Number) json.get("user")).longValue();
			SubjectType subjectType;
			try
			{
				subjectType = getSubjectType((String) json.get("subjectType"));
			} catch(PrismsRecordException e)
			{
				return parseErrorChange(json, reader);
			}
			if(subjectType == ChangeRecordError.ErrorSubjectType)
				return parseErrorChange(json, reader);
			String ctName = (String) json.get("changeType");
			prisms.records.ChangeType changeType = null;
			if(ctName != null)
			{
				for(prisms.records.ChangeType ct : (prisms.records.ChangeType[]) subjectType
					.getChangeTypes().getEnumConstants())
					if(ct.name().equals(ctName))
					{
						changeType = ct;
						break;
					}
				if(changeType == null)
					return parseErrorChange(json, reader);
			}
			char addChar = ((String) json.get("additivity")).charAt(0);
			int add = addChar == '+' ? 1 : (addChar == '-' ? -1 : 0);
			reader.setChangeData(new RecordType(subjectType, changeType, add), time, userID);
			RecordUser user;
			Object major = json.get("majorSubject");
			if(major instanceof JSONObject)
			{
				try
				{
					major = reader.read((JSONObject) major);
				} catch(Throwable e)
				{
					log.error("Could not parse major subject", e);
					return parseErrorChange(json, reader);
				}
			}
			if(major instanceof RecordUser && ((RecordUser) major).getID() == userID)
				user = (RecordUser) major;
			else
			{
				try
				{
					user = getImpl().getUser(userID, reader);
				} catch(PrismsRecordException e)
				{
					log.error("Could not get user", e);
					return parseErrorChange(json, reader);
				}
			}
			Object data1 = json.get("data1");
			Object data2 = json.get("data2");
			Object minor = json.get("minorSubject");
			Object preValue = json.get("preValue");
			try
			{
				if(data1 instanceof JSONObject)
					data1 = reader.read((JSONObject) data1);
				if(data2 instanceof JSONObject)
					data2 = reader.read((JSONObject) data2);
				if(minor instanceof JSONObject)
					minor = reader.read((JSONObject) minor);
				if(preValue instanceof JSONObject)
					preValue = reader.read((JSONObject) preValue);
			} catch(Throwable e)
			{
				log.error("Could not parse change data", e);
				return parseErrorChange(json, reader);
			}
			prisms.records.RecordPersister.ChangeData data;
			try
			{
				data = getData(subjectType, changeType, major, minor, data1, data2, preValue,
					reader);
			} catch(Throwable e)
			{
				log.error("Could not get change data", e);
				return parseErrorChange(json, reader);
			}
			try
			{
				if(data.data1 != null)
					reader.store(getType(data.data1), getID(data.data1), data.data1);
				if(data.data2 != null)
					reader.store(getType(data.data2), getID(data.data2), data.data2);
				if(data.minorSubject != null)
					reader.store(getType(data.minorSubject), getID(data.minorSubject),
						data.minorSubject);
				if(data.preValue != null)
				{
					String type = getType(data.preValue);
					if(type != null)
						reader.store(type, getID(data.preValue), data.preValue);
				}
			} catch(PrismsRecordException e)
			{
				log.error("Could not store data", e);
			}
			if(changeType != null && !changeType.isObjectIdentifiable() && data.preValue != null
				&& Number.class.isAssignableFrom(changeType.getObjectType())
				&& !changeType.getObjectType().equals(data.preValue.getClass()))
			{
				if(Long.class.equals(changeType.getObjectType()))
					data.preValue = Long.valueOf(((Number) data.preValue).longValue());
				else if(Integer.class.equals(changeType.getObjectType()))
					data.preValue = Integer.valueOf(((Number) data.preValue).intValue());
				else if(Double.class.equals(changeType.getObjectType()))
					data.preValue = Double.valueOf(((Number) data.preValue).doubleValue());
				else if(Float.class.equals(changeType.getObjectType()))
					data.preValue = Float.valueOf(((Number) data.preValue).floatValue());
				else if(Short.class.equals(changeType.getObjectType()))
					data.preValue = Short.valueOf(((Number) data.preValue).shortValue());
				else if(Byte.class.equals(changeType.getObjectType()))
					data.preValue = Byte.valueOf(((Number) data.preValue).byteValue());
			}
			try
			{
				return new prisms.records.ChangeRecord(id, false, time, user, subjectType,
					changeType, add, data.majorSubject, data.minorSubject, data.preValue,
					data.data1, data.data2);
			} catch(IllegalArgumentException e)
			{
				log.error("Could not instantiate change record with id " + id, e);
				return parseErrorChange(json, reader);
			}
		}

		private ChangeData getData(SubjectType subjectType, ChangeType changeType, Object major,
			Object minor, Object data1, Object data2, Object preValue, ItemGetter reader)
			throws PrismsRecordException
		{
			if(subjectType instanceof PrismsChange)
			{
				switch((PrismsChange) subjectType)
				{
				case center:
					PrismsCenter center;
					if(major instanceof PrismsCenter)
						center = (PrismsCenter) major;
					else
						center = getKeeper().getCenter(((Number) major).intValue());
					if(center == null)
						center = (PrismsCenter) reader.getItem("center",
							((Number) major).intValue());
					ChangeData ret = new ChangeData(center, null, null, null, null);
					if(changeType == null)
						return ret;
					switch((PrismsChange.CenterChange) changeType)
					{
					case name:
					case url:
					case serverUserName:
					case serverPassword:
						ret.preValue = preValue;
						return ret;
					case serverCerts:
						if(preValue == null)
							return ret;
						if(preValue instanceof java.security.cert.X509Certificate)
						{
							ret.preValue = preValue;
							return ret;
						}
						String spv = (String) preValue;
						byte [] bytes = new byte [spv.length() / 2];
						for(int i = 0; i < spv.length(); i += 2)
						{
							int dig = hexDig(spv.charAt(i));
							if(dig < 0)
								throw new PrismsRecordException(
									"Non-hex digit! Could not decode certificates");
							bytes[i / 2] = (byte) (dig << 4);
							dig = hexDig(spv.charAt(i + 1));
							if(dig < 0)
								throw new PrismsRecordException(
									"Non-hex digit! Could not decode certificates");
							bytes[i / 2] |= dig;
						}
						try
						{
							ret.preValue = java.security.cert.CertificateFactory
								.getInstance("X.509")
								.generateCertificates(new java.io.ByteArrayInputStream(bytes))
								.toArray(new java.security.cert.X509Certificate [0]);
						} catch(java.security.cert.CertificateException e)
						{
							throw new PrismsRecordException("Could not decode certificates", e);
						}
						return ret;
					case syncFrequency:
					case changeSaveTime:
						if(preValue instanceof Number)
							ret.preValue = preValue;
						else if(preValue instanceof String)
							ret.preValue = Long.valueOf((String) preValue);
						else
							ret.preValue = Long.valueOf(-1);
						return ret;
					case clientUser:
						if(preValue instanceof RecordUser)
							ret.preValue = minor;
						else if(preValue instanceof Number)
							ret.preValue = getImpl().getUser(((Number) preValue).longValue(),
								reader);
						return ret;
					}
					break;
				case autoPurge:
					if(!(getKeeper() instanceof DBRecordKeeper))
						throw new PrismsRecordException("No auto-purge capability enabled");
					AutoPurger purger = ((DBRecordKeeper) getKeeper()).getAutoPurger();
					ret = new ChangeData(purger, null, null, null, null);
					if(changeType == null)
						return ret;
					switch((PrismsChange.AutoPurgeChange) changeType)
					{
					case age:
						if(preValue instanceof Number)
							ret.preValue = preValue;
						else if(preValue instanceof String)
							ret.preValue = Long.valueOf((String) preValue);
						return ret;
					case entryCount:
						if(preValue instanceof Number)
							ret.preValue = preValue;
						else if(preValue instanceof String)
							ret.preValue = Integer.valueOf((String) preValue);
						return ret;
					case excludeType:
						String pvs = (String) preValue;
						SubjectType rtST = getSubjectType(pvs.substring(0, pvs.indexOf('/')));
						pvs = pvs.substring(pvs.indexOf('/') + 1);
						String ctName = pvs.substring(0, pvs.indexOf('/'));
						ChangeType rtCT = ctName.equals("null") ? null : RecordUtils.getChangeType(
							subjectType, ctName);
						pvs = pvs.substring(pvs.indexOf('/') + 1);
						int add;
						if(pvs.charAt(0) == '+')
							add = 1;
						else if(pvs.charAt(0) == '-')
							add = -1;
						else
							add = 0;
						ret.preValue = new RecordType(rtST, rtCT, add);
						return ret;
					case excludeUser:
						if(minor instanceof RecordUser)
							ret.minorSubject = minor;
						else if(minor instanceof Number)
							ret.minorSubject = getImpl().getUser(((Number) minor).longValue(),
								reader);
						return ret;
					}
					break;
				}
				throw new IllegalStateException("Unrecognized PRISMS change type: " + subjectType);
			}
			else
				return getImpl().getData(subjectType, changeType, major, minor, data1, data2,
					preValue, reader);
		}

		private int hexDig(char c)
		{
			if(c >= '0' && c <= '9')
				return c - '0';
			else if(c >= 'A' && c <= 'F')
				return c - 'A' + 10;
			else if(c >= 'a' && c <= 'f')
				return c - 'a' + 10;
			else
				return -1;
		}

		SubjectType getSubjectType(String name) throws PrismsRecordException
		{
			if(ChangeRecordError.ErrorSubjectType.name().equals(name))
				return ChangeRecordError.ErrorSubjectType;
			for(PrismsChange ch : PrismsChange.values())
				if(ch.name().equals(name))
					return ch;
			return getImpl().getSubjectType(name);
		}

		ChangeRecordError parseErrorChange(JSONObject json, ItemGetter getter)
		{
			long id = ((Number) json.get("id")).longValue();
			long time = ((Number) json.get("time")).longValue();
			final long userID = ((Number) json.get("user")).longValue();
			RecordUser user;
			try
			{
				user = getImpl().getUser(userID, getter);
			} catch(PrismsRecordException e)
			{
				user = new RecordUser()
				{

					public long getID()
					{
						return userID;
					}

					public String getName()
					{
						return "Unknown";
					}

					public boolean isDeleted()
					{
						return false;
					}
				};
			}
			ChangeRecordError ret = new ChangeRecordError(id, false, time, user);
			ret.setSubjectType((String) json.get("subjectType"));
			ret.setChangeType((String) json.get("changeType"));
			String add = (String) json.get("additivity");
			ret.setAdditivity(add.equals("+") ? 1 : (add.equals("-") ? -1 : 0));

			Object major = json.get("majorSubject");
			Object data1 = json.get("data1");
			Object data2 = json.get("data2");
			Object minor = json.get("minorSubject");
			Object preValue = json.get("preValue");
			if(major instanceof Number)
				ret.setMajorSubject(null, ((Number) major).longValue());
			else
				ret.setMajorSubject(null, ((Number) ((JSONObject) major).get("id")).longValue());

			if(minor instanceof Number)
				ret.setMinorSubject(null, ((Number) minor).longValue());
			else if(minor != null)
				ret.setMinorSubject(null, ((Number) ((JSONObject) minor).get("id")).longValue());

			if(data1 instanceof Number)
				ret.setData1(null, ((Number) data1).longValue());
			else if(data1 != null)
				ret.setData1(null, ((Number) ((JSONObject) data1).get("id")).longValue());

			if(data2 instanceof Number)
				ret.setData2(null, ((Number) data2).longValue());
			else if(data2 != null)
				ret.setData2(null, ((Number) ((JSONObject) data2).get("id")).longValue());

			if(preValue instanceof Boolean)
				preValue = preValue.toString();
			else if(preValue instanceof JSONObject)
				preValue = ((JSONObject) preValue).get("id");
			ret.setPreValue(null, preValue);
			return ret;
		}
	}

	/** Writes items and changes to a synchronization stream */
	public static class PS2ItemWriter implements ItemWriter
	{
		final SyncTransaction theTrans;

		final RecordKeeper theKeeper;

		final prisms.util.json.JsonStreamWriter theWriter;

		private final LatestCenterChange [] theLatestChanges;

		private final java.util.HashSet<ObjectID> theBag;

		private boolean optimizesForOldItems;

		private java.util.HashSet<ObjectID> theNewItems;

		private java.util.HashSet<ObjectID> theOldItems;

		/**
		 * @param trans The sync transaction to use to write synchronization data
		 * @param writer The JSON writer to write the data to
		 * @param changes The latest center changes of the remote center
		 */
		public PS2ItemWriter(SyncTransaction trans, prisms.util.json.JsonStreamWriter writer,
			LatestCenterChange [] changes)
		{
			theTrans = trans;
			theKeeper = trans.getSync().getKeeper();
			theWriter = writer;
			theBag = new java.util.HashSet<ObjectID>();
			theLatestChanges = changes;
			theNewItems = new java.util.HashSet<ObjectID>();
			theOldItems = new java.util.HashSet<ObjectID>();
		}

		void start() throws IOException
		{
			theWriter.startArray();
		}

		/**
		 * Writes an item to the stream
		 * 
		 * @param item The item to write
		 * @throws IOException If an error occurs writing to the stream
		 * @throws PrismsRecordException If an error occurs serializing the data
		 */
		public void writeItem(Object item) throws IOException, PrismsRecordException
		{
			if(item == null)
				theWriter.writeNull();
			else
			{
				String type = theTrans.getType(item);
				long id = type == null ? -1 : theTrans.getID(item);
				ObjectID oid = null;
				if(type != null && id >= 0)
					oid = new ObjectID(type, id);
				if(type == null || id < 0)
					doWrite(item);
				else if(theBag.contains(oid))
				{
					theWriter.startObject();
					theWriter.startProperty("type");
					theWriter.writeString(type);
					theWriter.startProperty("id");
					theWriter.writeNumber(Long.valueOf(id));
					theWriter.startProperty("-syncstore-");
					theWriter.writeBoolean(true);
					theWriter.endObject();
				}
				else if(optimizesForOldItems && !isNewItem(item, oid))
				{
					theBag.add(oid);
					theWriter.startObject();
					theWriter.startProperty("type");
					theWriter.writeString(type);
					theWriter.startProperty("id");
					theWriter.writeNumber(Long.valueOf(id));
					theWriter.startProperty("-localstore-");
					theWriter.writeBoolean(true);
					theTrans.writeItem(item, theWriter, this, true);
					theWriter.endObject();
				}
				else
				{
					theBag.add(oid);
					doWrite(item);
				}
			}
		}

		boolean isNewItem(Object item, ObjectID id) throws PrismsRecordException
		{
			if(theNewItems.contains(id))
				return true;
			if(theOldItems.contains(id))
				return false;
			int subjectCenter = RecordUtils.getCenterID(id.id);
			java.util.HashMap<Integer, Long> latestChanges = new java.util.HashMap<Integer, Long>();
			for(LatestCenterChange change : theLatestChanges)
				if(change.getSubjectCenter() == subjectCenter)
				{
					if(theKeeper.getLatestChange(change.getCenterID(), subjectCenter) < change
						.getLatestChange())
					{ // There may be a remote change that deleted the item--need to send fully
						theNewItems.add(id);
						return true;
					}
					latestChanges.put(Integer.valueOf(change.getCenterID()),
						Long.valueOf(change.getLatestChange()));
				}
			if(latestChanges.size() == 0)
			{
				theNewItems.add(id);
				return true;
			}
			prisms.util.Sorter<RecordKeeper.ChangeField> sorter = new prisms.util.Sorter<RecordKeeper.ChangeField>();
			sorter.addSort(RecordKeeper.ChangeField.CHANGE_TIME, false);
			long [] history = theKeeper.search(theKeeper.getHistorySearch(item), sorter);
			long changeTime = System.currentTimeMillis();
			/* If there is a creation change in the history of the item that the remote center has
			 * not yet received, then the item must be sent completely */
			boolean ret = false;
			for(int h = 0; h < history.length; h++)
			{
				Integer centerID = Integer.valueOf(RecordUtils.getCenterID(history[h]));
				Long lcTime = latestChanges.get(centerID);
				if(lcTime != null && lcTime.longValue() >= changeTime)
					continue;
				ChangeRecord record = theKeeper.getItems(new long [] {history[h]})[0];
				changeTime = record.time;
				if(lcTime != null && lcTime.longValue() >= record.time)
					continue;
				if(record.majorSubject.equals(item))
				{
					if(record.type.changeType != null || record.type.additivity <= 0)
						continue;
				}
				else if(item.equals(record.minorSubject))
				{
					if(record.type.additivity <= 0)
						continue;
				}
				ret = true;
				break;
			}
			if(!ret)
				for(Object depend : theTrans.getDepends(item))
				{
					String type = theTrans.getType(depend);
					if(type == null)
					{
						log.error("Type of depend " + depend.getClass() + " is null");
						continue;
					}
					ObjectID dependID = new ObjectID(type, theTrans.getID(depend));
					if(isNewItem(depend, dependID))
					{
						ret = true;
						break;
					}
				}
			if(ret)
				theNewItems.add(id);
			else
				theOldItems.add(id);
			return ret;
		}

		private void doWrite(Object item) throws IOException, PrismsRecordException
		{
			if(item == null)
				theWriter.writeNull();
			else if(item instanceof Character)
				theWriter.writeString("" + ((Character) item).charValue());
			else if(item instanceof Number)
				theWriter.writeNumber((Number) item);
			else if(item instanceof String)
				theWriter.writeString((String) item);
			else if(item instanceof Boolean)
				theWriter.writeBoolean(((Boolean) item).booleanValue());
			else
			{
				String type = theTrans.getType(item);
				if(type != null)
				{
					long id = theTrans.getID(item);
					theWriter.startObject();
					theWriter.startProperty("type");
					theWriter.writeString(type);
					if(id >= 0)
					{
						theWriter.startProperty("id");
						theWriter.writeNumber(Long.valueOf(id));
					}
					theTrans.writeItem(item, theWriter, this, false);
					theWriter.endObject();
				}
				else
					theTrans.writeItem(item, theWriter, this, false);
			}
		}

		/**
		 * Writes a change to the synchronization stream
		 * 
		 * @param change The change to write
		 * @param preError Whether the change has been sent before to the center but was not
		 *        imported correctly
		 * @throws IOException If an error occurs writing data to the stream
		 */
		public void writeChange(prisms.records.ChangeRecord change, boolean preError)
			throws IOException
		{
			optimizesForOldItems = !preError;
			try
			{
				theWriter.startObject();
				theWriter.startProperty("id");
				theWriter.writeNumber(Long.valueOf(change.id));
				theWriter.startProperty("time");
				theWriter.writeNumber(Long.valueOf(change.time));
				boolean error = false;
				if(change instanceof ChangeRecordError)
				{
					theWriter.startProperty("user");
					theWriter.writeNumber(Long.valueOf(change.user.getID()));
					theWriter.startProperty("error");
					theWriter.writeBoolean(true);
					ChangeRecordError err = (ChangeRecordError) change;
					theWriter.startProperty("subjectType");
					theWriter.writeString(err.getSubjectType());
					if(err.getChangeType() != null)
					{
						theWriter.startProperty("changeType");
						theWriter.writeString(err.getChangeType());
					}
					theWriter.startProperty("additivity");
					theWriter.writeString(err.getAdditivity() > 0 ? "+" : (err.getAdditivity() < 0
						? "-" : "0"));
					theWriter.startProperty("majorSubject");
					theWriter.writeNumber(Long.valueOf(err.getMajorSubjectID()));
					if(err.getMinorSubjectID() >= 0)
					{
						theWriter.startProperty("minorSubject");
						theWriter.writeNumber(Long.valueOf(err.getMinorSubjectID()));
					}
					if(err.getData1ID() >= 0)
					{
						theWriter.startProperty("data1");
						theWriter.writeNumber(Long.valueOf(err.getData1ID()));
					}
					if(err.getData2ID() >= 0)
					{
						theWriter.startProperty("data2");
						theWriter.writeNumber(Long.valueOf(err.getData2ID()));
					}
					if(err.getSerializedPreValue() != null)
					{
						theWriter.startProperty("preValue");
						if(err.getSerializedPreValue() instanceof Number)
							theWriter.writeNumber((Number) err.getSerializedPreValue());
						else
							theWriter.writeString((String) err.getSerializedPreValue());
					}
				}
				else
				{
					theWriter.startProperty("subjectType");
					theWriter.writeString(change.type.subjectType.name());
					if(change.type.changeType != null)
					{
						theWriter.startProperty("changeType");
						theWriter.writeString(change.type.changeType.name());
					}
					theWriter.startProperty("additivity");
					theWriter.writeString(change.type.additivity > 0 ? "+"
						: (change.type.additivity < 0 ? "-" : "0"));
					try
					{
						theWriter.startProperty("majorSubject");
						long id = theTrans.getID(change.majorSubject);
						ObjectID oid = new ObjectID(theTrans.getType(change.majorSubject), id);
						if(theBag.contains(oid)
							|| (!preError && !isNewItem(change.majorSubject, oid)))
							theWriter.writeNumber(Long.valueOf(id));
						else
							writeItem(change.majorSubject);

						theWriter.startProperty("user");
						theWriter.writeNumber(Long.valueOf(change.user.getID()));

						if(change.data1 != null)
						{
							theWriter.startProperty("data1");
							id = theTrans.getID(change.data1);
							oid = new ObjectID(theTrans.getType(change.data1), id);
							if(theBag.contains(oid) || (!preError && !isNewItem(change.data1, oid)))
								theWriter.writeNumber(Long.valueOf(id));
							else
								writeItem(change.data1);
						}

						if(change.data2 != null)
						{
							theWriter.startProperty("data2");
							id = theTrans.getID(change.data2);
							oid = new ObjectID(theTrans.getType(change.data2), id);
							if(theBag.contains(oid) || (!preError && !isNewItem(change.data2, oid)))
								theWriter.writeNumber(Long.valueOf(id));
							else
								writeItem(change.data2);
						}

						if(change.minorSubject != null)
						{
							theWriter.startProperty("minorSubject");
							id = theTrans.getID(change.minorSubject);
							oid = new ObjectID(theTrans.getType(change.minorSubject), id);
							if(theBag.contains(oid)
								|| (!preError && !isNewItem(change.minorSubject, oid)))
								theWriter.writeNumber(Long.valueOf(id));
							else
								writeItem(change.minorSubject);
						}

						if(change.previousValue != null)
						{
							theWriter.startProperty("preValue");
							if(change.type.changeType.isObjectIdentifiable())
							{
								id = theTrans.getID(change.previousValue);
								oid = new ObjectID(theTrans.getType(change.previousValue), id);
								if(theBag.contains(oid)
									|| (!preError && !isNewItem(change.previousValue, oid)))
									theWriter.writeNumber(Long.valueOf(id));
								else
									writeItem(change.previousValue);
							}
							else
								writeItem(change.previousValue);
						}
						Object currentValue = null;
						if(change.type.changeType != null)
							currentValue = theTrans.getCurrentValue(change);
						if(currentValue != null)
						{
							theWriter.startProperty("currentValue");
							writeItem(currentValue);
						}
					} catch(PrismsRecordException e)
					{
						log.error("Could not write item for change " + change + "(" + change.id
							+ ")", e);
						error = true;
					}
					if(theTrans.shouldStoreSyncRecord())
						try
						{
							theKeeper.associate(change, theTrans.getSyncRecord(), error);
						} catch(PrismsRecordException e)
						{
							log.error("Could not associate change " + change.id
								+ " with sync record " + theTrans.getSyncRecord(), e);
						}
				}
				theWriter.endObject();
			} finally
			{
				optimizesForOldItems = true;
			}
		}

		void writeSkippedChange() throws IOException
		{
			theWriter.startObject();
			theWriter.startProperty("skipped");
			theWriter.writeBoolean(true);
			theWriter.endObject();
		}

		void end() throws IOException
		{
			theWriter.endArray();
		}
	}

	/** Reads items from a synchronization stream */
	public static class PS2ItemReader extends prisms.util.json.SAJParser.DefaultHandler implements
		prisms.records.SynchronizeImpl.ItemReader, prisms.records.SynchronizeImpl.ItemGetter
	{
		/** Writes content to an output as it is read */
		private class ParseReader extends java.io.Reader
		{
			private final java.io.Reader theRead;

			private final java.io.Writer theWrite;

			ParseReader(java.io.Reader reader, java.io.Writer writer)
			{
				theRead = reader;
				theWrite = writer;
			}

			@Override
			public int read(char [] cbuf, int off, int len) throws IOException
			{
				len = theRead.read(cbuf, off, len);
				theWrite.write(cbuf, off, len);
				return len;
			}

			@Override
			public void close() throws IOException
			{
				theWrite.close();
			}
		}

		/**
		 * A reference is stored when the ID of an object is parsed but its content should be parsed
		 * later
		 */
		private class Reference
		{
			final Object theRef;

			final ObjectID theID;

			final JSONObject theJson;

			final boolean isNew;

			Reference(Object ref, ObjectID id, JSONObject json, boolean _new)
			{
				theRef = ref;
				theID = id;
				theJson = json;
				isNew = _new;
			}
		}

		private RecordType theChangeType;

		private long theChangeTime;

		private long theChangeUserID;

		private final SyncInputHandler theSyncInput;

		private final SyncTransaction theTrans;

		private final java.util.HashMap<ObjectID, Integer> theObjectPositions;

		private final java.util.LinkedHashSet<ObjectID> theExportedObjects;

		private final ObjectBag theBag;

		private final java.util.HashMap<ObjectID, JSONObject> thePreRegisters;

		private prisms.util.RandomAccessTextFile theFile;

		private int thePosition;

		private String theType;

		private int [] theCenterIDs;

		private java.util.HashMap<Integer, Reference []> theReferences;

		private int theDepth;

		private java.util.ArrayList<boolean []> theNewItems;

		/**
		 * External constructor for using pieces of synchronization outside of the context of
		 * synchronization
		 * 
		 * @param trans The sync transaction to read sync data for
		 */
		public PS2ItemReader(SyncTransaction trans)
		{
			this(trans, null);
		}

		PS2ItemReader(SyncTransaction trans, SyncInputHandler syncInput)
		{
			theTrans = trans;
			theSyncInput = syncInput;
			theObjectPositions = new java.util.HashMap<ObjectID, Integer>();
			theExportedObjects = new java.util.LinkedHashSet<ObjectID>();
			thePreRegisters = new java.util.HashMap<ObjectID, JSONObject>();
			theBag = new ObjectBag();
			theCenterIDs = new int [0];
			theReferences = new java.util.HashMap<Integer, Reference []>();
			theNewItems = new java.util.ArrayList<boolean []>();
		}

		void store(String type, long id, Object item)
		{
			theBag.add(type, id, item);
		}

		void setChangeData(RecordType type, long time, long userID)
		{
			theChangeType = type;
			theChangeTime = time;
			theChangeUserID = userID;
		}

		public Object read(JSONObject json) throws PrismsRecordException
		{
			if(json == null)
				return null;
			Object value = null;
			theDepth++;
			while(theNewItems.size() < theDepth)
				theNewItems.add(new boolean [1]);
			boolean [] newItem = theNewItems.get(theDepth - 1);
			try
			{
				String type = (String) json.get("type");
				Number idNum = (Number) json.get("id");
				long id;
				if(idNum != null)
					id = idNum.longValue();
				else
					id = -1;
				value = id < 0 ? null : theBag.get(type, id);
				if(value != null)
				{}
				else if(Boolean.TRUE.equals(json.get("-syncstore-")))
				{
					value = getItem(type, id);
					parseEmptyContent();
				}
				else if(Boolean.TRUE.equals(json.get("-localstore-")))
				{
					newItem[0] = false;
					value = theTrans.parseID(json, this, newItem);
					if(newItem[0])
						throw new PrismsRecordException("Item " + type + "/" + id
							+ " not present in data source");
					theBag.add(type, id, value);
				}
				else
				{
					newItem[0] = false;
					value = theTrans.parseID(json, this, newItem);
					if(id >= 0)
						theBag.add(type, id, value);
					parseEmptyContent();
					if(type != null && id >= 0)
					{
						if(theDepth == 1)
							parseContent(value, new ObjectID(type, id), json, newItem[0]);
						else
							addEmptyContent(value, new ObjectID(type, id), json, newItem[0]);
						parseEmptyContent();
					}
				}
			} finally
			{
				theDepth--;
			}
			return value;
		}

		public boolean isChange()
		{
			return theChangeType != null;
		}

		public RecordType getChangeType()
		{
			return theChangeType;
		}

		public long getChangeTime()
		{
			return theChangeTime;
		}

		public long getChangeUserID()
		{
			return theChangeUserID;
		}

		/**
		 * Preregisters a JSONObject for parsing, possibly before it has been loaded completely. The
		 * item will only be parsed when it is requested by a separate parsing request.
		 * 
		 * @param json The JSONObject representing the item to pre-register
		 */
		void preRegister(JSONObject json)
		{
			if(Boolean.TRUE.equals(json.get("-syncstore-")))
				return;
			String type = (String) json.get("type");
			if(type == null)
				return;
			long id = ((Number) json.get("id")).longValue();
			if(theBag.get(type, id) != null)
				return;
			ObjectID key = new ObjectID(type, id);
			if(!thePreRegisters.containsKey(key))
				thePreRegisters.put(key, json);
		}

		private void parseContent(Object value, ObjectID id, JSONObject json, boolean newItem)
			throws PrismsRecordException
		{
			if(newItem || theExportedObjects.contains(id))
				theTrans.parseContent(value, json, newItem, this);
		}

		private void addEmptyContent(Object ref, ObjectID id, JSONObject json, boolean isNew)
			throws PrismsRecordException
		{
			Reference [] refs = theReferences.get(Integer.valueOf(theDepth));
			refs = ArrayUtils.add(refs, new Reference(ref, id, json, isNew));
			theReferences.put(Integer.valueOf(theDepth), refs);
			parseDeepContent(json);
		}

		private void parseDeepContent(JSONObject json) throws PrismsRecordException
		{
			for(Object val : json.values())
			{
				if(val instanceof org.json.simple.JSONArray)
					parseDeepContent((org.json.simple.JSONArray) val);
				else if(val instanceof JSONObject)
				{
					JSONObject jsonVal = (JSONObject) val;
					if(jsonVal.get("type") instanceof String && jsonVal.get("id") instanceof Number
						&& !Boolean.TRUE.equals(jsonVal.get("-syncstore-"))
						&& !Boolean.TRUE.equals(jsonVal.get("-localstore-")))
						read(jsonVal);
				}
			}
		}

		private void parseDeepContent(org.json.simple.JSONArray json) throws PrismsRecordException
		{
			for(Object val : json)
			{
				if(val instanceof org.json.simple.JSONArray)
					parseDeepContent((org.json.simple.JSONArray) val);
				else if(val instanceof JSONObject)
				{
					JSONObject jsonVal = (JSONObject) val;
					if(jsonVal.get("type") instanceof String && jsonVal.get("id") instanceof Number
						&& !Boolean.TRUE.equals(jsonVal.get("-syncstore-"))
						&& !Boolean.TRUE.equals(jsonVal.get("-localstore-")))
						read(jsonVal);
				}
			}
		}

		private void parseEmptyContent() throws PrismsRecordException
		{
			Reference [] refs = theReferences.get(Integer.valueOf(theDepth + 1));
			if(refs == null)
				return;
			theReferences.put(Integer.valueOf(theDepth + 1), null);
			for(Reference ref : refs)
				parseContent(ref.theRef, ref.theID, ref.theJson, ref.isNew);
		}

		void parse(java.io.Reader reader, int itemCount) throws IOException,
			prisms.util.json.SAJParser.ParseException
		{
			java.io.File tempFile;
			if(theFile == null)
			{
				tempFile = java.io.File.createTempFile("SyncData" + hashCode(), null);
				tempFile.deleteOnExit();
				theFile = new prisms.util.RandomAccessTextFile(tempFile);
			}
			else
				tempFile = theFile.getFile();
			java.io.Writer writer = new java.io.BufferedWriter(new java.io.FileWriter(tempFile));
			ParseReader parseReader = new ParseReader(reader, writer);
			theTrans.getPI().setProgress(0);
			theTrans.getPI().setProgressScale(itemCount);
			new prisms.util.json.SAJParser().parse(parseReader, this);
			writer.close();
		}

		@Override
		public void startObject(ParseState state)
		{
			if(theTrans.getPI().isCanceled())
				throw new prisms.util.CancelException();
			super.startObject(state);
			thePosition = state.getIndex() - 1;
		}

		@Override
		public void startProperty(ParseState state, String name)
		{
			if(theTrans.getPI().isCanceled())
				throw new prisms.util.CancelException();
			super.startProperty(state, name);
			if(!name.equals("id"))
				theType = null;
		}

		@Override
		public void valueString(ParseState state, String value)
		{
			super.valueString(state, value);
			if("type".equals(state.top().getPropertyName()))
				theType = value;
		}

		@Override
		public void valueNumber(ParseState state, Number value)
		{
			if(theTrans.getPI().isCanceled())
				throw new prisms.util.CancelException();
			super.valueNumber(state, value);
			if("id".equals(state.top().getPropertyName()) && theType != null)
			{
				ObjectID id = new ObjectID(theType, value.longValue());
				if(state.getDepth() == 3) // Array, object, property. Exported item.
				{
					theTrans.getPI().setProgress(theTrans.getPI().getTaskProgress() + 1);
					theExportedObjects.add(id);
					Integer centerID = Integer.valueOf(RecordUtils.getCenterID(id.id));
					if(!ArrayUtils.containsP(theCenterIDs, centerID))
						theCenterIDs = (int []) ArrayUtils.addP(theCenterIDs, centerID);
				}
				if(!theObjectPositions.containsKey(id))
					theObjectPositions.put(id, Integer.valueOf(thePosition));
			}
		}

		public Object getItem(String type, long id) throws PrismsRecordException
		{
			Object ret = theBag.get(type, id);
			if(ret != null)
				return ret;
			ObjectID key = new ObjectID(type, id);
			JSONObject preRegistered = thePreRegisters.remove(key);
			if(preRegistered != null)
				return read(preRegistered);
			Integer pos = theObjectPositions.get(key);
			if(pos == null)
				throw new PrismsRecordException("No such object " + type + "/" + id
					+ " in full item list or local data set");
			/* Since this item is about to be sync'ed right now, we don't need to sync it when
			 * syncItems is called */
			theObjectPositions.remove(key);
			prisms.util.json.SAJParser.DefaultHandler handler = new prisms.util.json.SAJParser.DefaultHandler();
			try
			{
				java.io.Reader reader = theFile.access(pos.intValue());
				new prisms.util.json.SAJParser().parse(reader, handler);
			} catch(prisms.util.json.SAJParser.ParseException e)
			{
				throw new PrismsRecordException("Malformatted JSON in cached item list", e);
			} catch(IOException e)
			{
				throw new PrismsRecordException("Could not read cached item list", e);
			}
			JSONObject json = (JSONObject) handler.finalValue();
			if(json.containsKey("-syncstore-"))
				throw new PrismsRecordException("Illegal state--first item in full item set"
					+ " matching the given type/id is a reference");
			return read(json);
		}

		void syncItems(int stages, int stage) throws IOException, PrismsRecordException
		{
			String rootProgress;
			if(stages == 0)
				rootProgress = "Importing remote items";
			else
				rootProgress = "Importing remote items (stage " + stage + " of " + stages + ")";
			theTrans.getPI().setProgressText(rootProgress);

			theTrans.getPI().setProgress(0);
			theTrans.getPI().setProgressScale(theExportedObjects.size());
			int items = 0;
			/* For each item in the full list that was not sync'ed already from being needed by a
			 * change, sync the item with the data set. */
			setChangeData(null, -1, -1);
			prisms.util.json.SAJParser.DefaultHandler handler = new prisms.util.json.SAJParser.DefaultHandler();
			for(ObjectID id : theExportedObjects)
			{
				Integer pos = theObjectPositions.remove(id);
				if(pos != null)
				{
					java.io.Reader reader = theFile.access(pos.intValue());
					try
					{
						new prisms.util.json.SAJParser().parse(reader, handler);
					} catch(prisms.util.json.SAJParser.ParseException e)
					{
						throw new PrismsRecordException("Malformatted JSON in cached item list", e);
					}
					JSONObject json = (JSONObject) handler.finalValue();
					handler.reset();
					Object item;
					theDepth++;
					while(theNewItems.size() < theDepth)
						theNewItems.add(new boolean [1]);
					boolean [] newItem = theNewItems.get(theDepth - 1);
					newItem[0] = false;
					try
					{
						item = theTrans.parseID(json, this, newItem);
						theBag.add(id.type, id.id, item);
						parseEmptyContent();
						if(!json.containsKey("-localstore-"))
						{
							if(newItem[0]
								|| theTrans.relativePriority(theTrans.getSyncRecord().getCenter()) >= 0)
								parseContent(item, id, json, newItem[0]);
						}
						else if(newItem[0])
							log.error("Item " + id + " not locally stored!");
						parseEmptyContent();
						theTrans.getPI().setProgressText(
							rootProgress + "\nImported " + id.type + " "
								+ prisms.util.PrismsUtils.encodeUnicode("" + item));
					} catch(PrismsRecordException e)
					{
						log.error("Failed to import " + id, e);
					} catch(Exception e)
					{
						log.error("Faile to import " + id, e);
					} finally
					{
						theDepth--;
					}
				}
				items++;
				theTrans.getPI().setProgress(items);
			}
			stage++;
			theTrans.getPI().setProgressScale(0);
			theTrans.getPI().setProgress(0);
			theTrans.getPI().setProgressText(rootProgress);
			if(theTrans.relativePriority(theTrans.getSyncRecord().getCenter()) >= 0)
			{
				/* For each item in the data set that has no representation in the sent item list,
				 * delete the item */
				if(stages == 0)
					rootProgress = "Removing remotely deleted items";
				else if(stages == 4)
					rootProgress = "Removing remotely deleted items (stage 4 of " + stages + ")";
				else
					rootProgress = "Removing remotely deleted items (stage 3 of " + stages + ")";
				java.util.ArrayList<Object> toDelete = new java.util.ArrayList<Object>();
				java.util.ArrayList<ObjectID> delIDs = new java.util.ArrayList<ObjectID>();
				prisms.records.SynchronizeImpl.ItemIterator iter = theTrans.getImpl().getAllItems(
					theCenterIDs, theTrans.getSyncRecord().getCenter());
				while(true)
				{
					Object item;
					try
					{
						if(!iter.hasNext())
							break;
						item = iter.next();
					} catch(PrismsRecordException e)
					{
						log.error("Could not retrieve item from full set", e);
						continue;
					}
					ObjectID id = new ObjectID(theTrans.getType(item), theTrans.getID(item));
					if(!theExportedObjects.contains(id) && !theSyncInput.isNewItem(item, id.id))
					{
						toDelete.add(item);
						delIDs.add(id);
					}
				}
				theTrans.getPI().setProgressScale(toDelete.size());
				theExportedObjects.clear();
				items = 0;
				for(int i = 0; i < toDelete.size(); i++)
				{
					Object item = toDelete.get(i);
					ObjectID id = delIDs.get(i);
					try
					{
						theTrans.getImpl().delete(item, theTrans.getSyncRecord());
						items++;
						theTrans.getPI().setProgress(items);
						theTrans.getPI().setProgressText(
							rootProgress + "\nDeleted " + id.type + " " + item);
					} catch(Throwable e)
					{
						log.error("Could not delete item " + id.type + "/" + id.id, e);
					}
				}
				toDelete.clear();
				delIDs.clear();
				theTrans.getPI().setProgressText("Finished importing remote values");
				theTrans.getPI().setProgressScale(0);
				theTrans.getPI().setProgress(0);
			}
		}

		void close()
		{
			if(theFile == null)
				return;
			try
			{
				theFile.close(true);
			} catch(IOException e)
			{
				log.error("Could not close cached sync file", e);
			}
			theFile = null;
		}
	}

	/** Reads changes from a synchronization stream */
	public static class ChangeReader extends prisms.util.json.SAJParser.DefaultHandler
	{
		private final SyncTransaction theTrans;

		private final Reader theReader;

		private PS2ItemReader theGetter;

		private int theTotalChangeCount;

		private int theChangeCount;

		/**
		 * @param trans The sync transaction to use to read the synchronization data
		 * @param reader The reader to read the data from
		 * @param getter The item reader to read the components of changes with
		 * @param totalChangeCount The total number of changes in the stream (for progress purposes
		 *        only)
		 */
		public ChangeReader(SyncTransaction trans, Reader reader, PS2ItemReader getter,
			int totalChangeCount)
		{
			theTrans = trans;
			theReader = reader;
			theGetter = getter;
			theTotalChangeCount = totalChangeCount;
			theTrans.getPI().setProgressScale(totalChangeCount);
		}

		int parse() throws java.io.IOException, prisms.util.json.SAJParser.ParseException
		{
			new prisms.util.json.SAJParser().parse(theReader, this);
			return theChangeCount;
		}

		@Override
		public void valueNumber(ParseState state, Number value)
		{
			super.valueNumber(state, value);
			if("id".equals(state.top().getPropertyName()))
				theGetter.preRegister((JSONObject) top());
		}

		@Override
		public void endObject(ParseState state)
		{
			super.endObject(state);
			if(theTrans.getPI().isCanceled())
				throw new prisms.util.CancelException();
			if(getDepth() == 1)
			{ // Change-level
				JSONObject json = (JSONObject) finalValue();
				theChangeCount++;
				if(theTotalChangeCount > 0)
					theTrans.getPI().setProgress(theChangeCount);
				readChange(json);
			}
		}

		/**
		 * Parses a JSON-serialized change record
		 * 
		 * @param json The change record to read
		 */
		public void readChange(JSONObject json)
		{
			RecordKeeper keeper = theTrans.getSync().getKeeper();
			if(Boolean.TRUE.equals(json.get("skipped")))
				return;
			boolean store = true;
			ChangeRecord change = theTrans.parseChange(json, theGetter);
			try
			{
				if(keeper.hasChange(change.id))
				{
					store = false;
					if(keeper.hasSuccessfulChange(change.id))
						return;
				}
			} catch(PrismsRecordException e)
			{
				log.error("Could not check existence of change", e);
				return;
			}
			if(theTrans.getPI().isCanceled())
				throw new prisms.util.CancelException();
			boolean error = change instanceof ChangeRecordError;
			try
			{
				if(!error && keeper.search(keeper.getSuccessorSearch(change), null).length == 0)
				{
					Object currentValue = json.get("currentValue");
					if(currentValue != null && currentValue instanceof JSONObject)
						currentValue = theGetter.read((JSONObject) currentValue);
					if(currentValue != null
						&& change.type.changeType.getObjectType().isInstance(currentValue))
						theTrans.getPI().setProgressText(
							prisms.util.PrismsUtils.encodeUnicode("Importing "
								+ change.toString(currentValue)));
					else
						theTrans.getPI()
							.setProgressText(
								prisms.util.PrismsUtils.encodeUnicode("Importing "
									+ change.toString()));
					theTrans.getImpl().doChange(change, currentValue);
				}
			} catch(Exception e)
			{
				log.error("Could not perform change " + change.id, e);
				error = true;
			}
			try
			{
				if(store)
					keeper.persist(change);
				if(theTrans.shouldStoreSyncRecord())
					keeper.associate(change, theTrans.getSyncRecord(), error);
			} catch(PrismsRecordException e2)
			{
				log.error("Could not persist change " + change.id, e2);
			}
		}
	}

	private class SyncInputHandler extends prisms.util.json.SAJParser.DefaultHandler
	{
		final SyncTransaction theTrans;

		final Reader theReader;

		final PostIDSet thePIDS;

		private PS2ItemReader theItemReader;

		private long [] theNewChanges;

		private long [] theCreations;

		private Object [] theCurrentValues;

		private boolean isCenterIDSet;

		private int theTotalItemCount;

		private int theTotalChangeCount;

		private boolean hasDoneChanges;

		private int theStageCount;

		private int [] theCurrentStage;

		private java.util.ArrayList<LatestCenterChange> theLatestChanges;

		SyncInputHandler(SyncTransaction trans, Reader reader, PostIDSet pids)
		{
			theTrans = trans;
			theReader = reader;
			thePIDS = pids;
			theItemReader = new PS2ItemReader(theTrans, this);
			theTotalChangeCount = -1;
			theLatestChanges = new java.util.ArrayList<LatestCenterChange>();
		}

		@Override
		public void separator(ParseState state)
		{
			super.separator(state);
			if(theTrans.getPI().isCanceled())
				throw new prisms.util.CancelException();
			try
			{
				if(DEPENDS.equals(state.top().getPropertyName()))
				{
					if(!isCenterIDSet)
						throw new prisms.util.json.SAJParser.ParseException(
							"Can't synchronize dependencies before center ID is sent", state);
					final int [] dependIdx = new int [] {0};
					new prisms.util.json.SAJParser().parse(theReader,
						new prisms.util.json.SAJParser.DefaultHandler()
						{
							@Override
							public void startArray(ParseState state2)
							{
								super.startArray(state2);
								inputDependSync(state2);
							}

							@Override
							public void separator(ParseState state2)
							{
								super.separator(state2);
								inputDependSync(state2);
							}

							private void inputDependSync(ParseState state2)
							{
								PrismsSynchronizer subSync = getDepends()[dependIdx[0]];
								dependIdx[0]++;
								PrismsCenter subCenter;
								try
								{
									subCenter = getDependCenter(subSync, theTrans.getSyncRecord()
										.getCenter());
									if(subCenter == null)
										throw new PrismsRecordException("No dependent center"
											+ " parallel to "
											+ theTrans.getSyncRecord().getCenter().getName()
											+ " for synchronizer with impl "
											+ subSync.getImpls()[subSync.getImpls().length - 1]
												.getClass().getName());
									// TODO Perhaps adjust the progress input to append something
									// like "Synchronizing REA: "
									subSync.doSyncInput(subCenter, theTrans.getSyncRecord()
										.getSyncType(), theReader, theTrans.getPI(), thePIDS,
										theTrans.shouldStoreSyncRecord());
									state2.spoofValue();
								} catch(IOException e)
								{
									throw new RuntimeWrapper(
										"Could not read dependent synchronization data", e);
								} catch(ParseException e)
								{
									throw new RuntimeWrapper(
										"Could not parse dependent synchronization data", e);
								} catch(PrismsRecordException e)
								{
									throw new RuntimeWrapper(
										"Could not interpret dependent synchronization data", e);
								}
							}
						});
					state.spoofValue();
				}
				else if(ALL_ITEMS.equals(state.top().getPropertyName()))
				{
					checkStage();
					if(theStageCount == 0)
						theTrans.getPI().setProgressText("Retrieving and storing remote values");
					else
						theTrans.getPI().setProgressText(
							"Retrieving and storing remote values (Stage " + theCurrentStage[0]
								+ " of " + theStageCount + ")");
					if(!isCenterIDSet)
						throw new prisms.util.json.SAJParser.ParseException(
							"Can't import items before remote center ID is sent", state);
					super.valueNull(state);
					state.spoofValue();
					theItemReader.parse(theReader, theTotalItemCount);
					theCurrentStage[0]++;
					if(theTrans.getPI().isCanceled())
						throw new prisms.util.CancelException();
					theTrans.getPI().setProgressScale(0);
				}
				else if(CHANGES.equals(state.top().getPropertyName()))
				{
					if(hasDoneChanges)
						return;
					hasDoneChanges = true;
					checkStage();
					if(theStageCount == 0)
						theTrans.getPI().setProgressText("Importing changes");
					else
						theTrans.getPI().setProgressText(
							"Importing changes (Stage " + theCurrentStage[0] + " of "
								+ theStageCount + ")");
					if(!isCenterIDSet)
						throw new prisms.util.json.SAJParser.ParseException(
							"Can't import changes before center ID is sent", state);
					super.valueNull(state);
					state.spoofValue();
					// First, read in the changes from the remote center
					try
					{
						new ChangeReader(theTrans, theReader, theItemReader, theTotalChangeCount)
							.parse();
					} catch(prisms.util.CancelException e)
					{}
					if(theTotalChangeCount > 0)
						theCurrentStage[0]++;
					theTrans.getPI().setCancelable(false);
					// Next, synchronize this center's item set with the remote center's items
					try
					{
						theItemReader.syncItems(theStageCount, theCurrentStage[0]);
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper("Could not sync item set", e);
					}
					// Next, since that was successful, store the latest change times from the
					// remote center
					for(LatestCenterChange lcc : theLatestChanges)
					{
						try
						{
							getKeeper().setLatestChange(lcc.getCenterID(), lcc.getSubjectCenter(),
								lcc.getLatestChange());
						} catch(PrismsRecordException e)
						{
							log.error("Could not set latest change time", e);
						}
					}
					theLatestChanges.clear();
					if(theNewChanges != null && theNewChanges.length > 0)
					{
						/* After a destructive sync, we need to re-apply changes that have occurred
						 * since the last time the remote center synchronized with the local center. */
						String baseText;
						if(theStageCount == 0)
							baseText = "Recovering local changes";
						else
							baseText = "Recovering local changes (stage " + theStageCount + " of "
								+ theStageCount + ")";
						theTrans.getPI().setProgressText(baseText);
						theTrans.getPI().setProgress(0);
						theTrans.getPI().setProgressScale(0);
						theCreations = null;
						long [] batch = new long [theNewChanges.length < 100 ? theNewChanges.length
							: 100];
						for(int i = 0; i < theNewChanges.length; i += batch.length)
						{
							if(theNewChanges.length - i < batch.length)
								batch = new long [theNewChanges.length - i];
							System.arraycopy(theNewChanges, i, batch, 0, batch.length);
							ChangeRecord [] records;
							try
							{
								records = getKeeper().getItems(batch);
							} catch(PrismsRecordException e)
							{
								log.error("Could not get changes " + i + " through "
									+ (i + batch.length - 1), e);
								continue;
							}
							theTrans.getPI().setProgressScale(theNewChanges.length);
							for(int j = 0; j < records.length; j++)
							{
								theTrans.getPI().setProgress(i + j);
								if(records[j] instanceof ChangeRecordError)
								{
									log.error("Could not redo change " + records[j].id
										+ " after destructive sync");
									continue;
								}
								try
								{
									if(theTrans.shouldSend(records[j])
										&& getKeeper().search(
											getKeeper().getSuccessorSearch(records[j]), null).length == 0)
										theTrans.getImpl().doChange(records[j],
											theCurrentValues[i + j]);
								} catch(PrismsRecordException e)
								{
									log.error("Could not perform change " + records[j].id, e);
								}
								records[j] = null;
								theCurrentValues[i + j] = null;
							}
						}
						theNewChanges = null;
						theCurrentValues = null;
					}
					theTrans.getPI().setProgressScale(0);
					theTrans.getPI().setProgress(0);
					theTrans.getPI().setProgressText("Finalizing import and refreshing client");
					theItemReader.close();
				}
				else if("subSync".equals(state.top().getPropertyName()))
					subSync(state);
				else if("latestChanges".equals(state.top().getPropertyName()))
					subSyncOld(state);
			} catch(IOException e)
			{
				throw new RuntimeWrapper("Could not read synchronization stream", e);
			} catch(prisms.util.json.SAJParser.ParseException e)
			{
				throw new RuntimeWrapper("Could not parse synchronization stream", e);
			}
		}

		private void subSync(ParseState state) throws IOException,
			prisms.util.json.SAJParser.ParseException
		{
			if(!isCenterIDSet)
				throw new prisms.util.json.SAJParser.ParseException(
					"Can't import changes before center ID is sent", state);
			super.valueNull(state);
			state.spoofValue();
			theTrans.getPI().setCancelable(false);
			// TODO
		}

		private void subSyncOld(ParseState state) throws IOException,
			prisms.util.json.SAJParser.ParseException
		{
			if(!isCenterIDSet)
				throw new prisms.util.json.SAJParser.ParseException(
					"Can't import changes before center ID is sent", state);
			super.valueNull(state);
			state.spoofValue();
			theTrans.getPI().setCancelable(false);
			org.json.simple.JSONArray changes = (org.json.simple.JSONArray) prisms.util.json.SAJParser
				.parse(theReader);
			for(JSONObject change : (java.util.List<JSONObject>) changes)
			{
				int centerID = ((Number) change.get("centerID")).intValue();
				int subjectCenter = ((Number) change.get("subjectCenter")).intValue();
				long remoteTime = ((Number) change.get("latestChange")).longValue();
				theLatestChanges.add(new LatestCenterChange(centerID, subjectCenter, remoteTime));
			}
			/* If all items are sent from the remote center, then the synchronization
			 * is a destructive sync. We need to get all changes that we have since the
			 * last time the remote center synchronized with the local center and re-apply
			 * them after importing the remote center's data as-is. If, however, the remote
			 * center is not a priority, then the sync will not be evaluated destructively,
			 * so no post-destruct changes are needed. */
			if(theTrans.relativePriority(theTrans.getSyncRecord().getCenter()) < 0)
				return;
			try
			{
				theStageCount++;
				if(theTrans.getPI().isCanceled())
					throw new prisms.util.CancelException();
				SyncRequest req = new SyncRequest(theTrans.getSyncRecord().getCenter(), theTrans
					.getSyncRecord().getSyncType(),
					theLatestChanges.toArray(new LatestCenterChange [theLatestChanges.size()]),
					null);
				req.setWithRecords(true);
				req.setStoreSyncRecord(theTrans.shouldStoreSyncRecord());
				SyncOutput sync = getSyncOutput(theTrans, req, true);
				theNewChanges = sync.theChangeIDs;
				theCreations = new long [theNewChanges.length];
				java.util.Arrays.fill(theCreations, -1);
				theCurrentValues = new Object [theNewChanges.length];
				long [] batch = new long [theNewChanges.length < 100 ? theNewChanges.length : 100];
				for(int i = 0; i < theNewChanges.length; i += batch.length)
				{
					int len = theNewChanges.length - i;
					if(len < batch.length)
						batch = new long [len];
					else
						len = batch.length;
					System.arraycopy(theNewChanges, i, batch, 0, len);
					ChangeRecord [] records = getKeeper().getItems(batch);
					for(int j = 0; j < records.length; j++)
					{
						if(records[j] instanceof ChangeRecordError)
							continue;
						if(records[j].type.additivity == 0)
							theCurrentValues[i + j] = theTrans.getCurrentValue(records[j]);
						if(records[j].type.changeType == null && records[j].type.additivity > 0)
							theCreations[i] = theTrans.getID(records[j].majorSubject);
					}
				}
				if(theNewChanges.length > 0)
					theStageCount++;
			} catch(PrismsRecordException e)
			{
				throw new RuntimeWrapper("Could not retrieve changes"
					+ " since last sync by remote center", e);
			}
		}

		private void checkStage()
		{
			if(theCurrentStage != null)
				return;
			int count = 0;
			if(theTotalItemCount > 0)
			{
				count = 2;
				if(theTotalChangeCount > 0)
					count++;
				if(theTrans.relativePriority(theTrans.getSyncRecord().getCenter()) >= 0)
					count++;
			}
			theStageCount = count;
			theCurrentStage = new int [] {1};
		}

		@Override
		public void valueString(ParseState state, String value)
		{
			super.valueString(state, value);
			if("version".equals(state.top().getPropertyName()) && theTrans.getImpl() == null)
				theTrans.setImpl(getImpl(value, true));
		}

		@Override
		public void valueNumber(ParseState state, Number value)
		{
			super.valueNumber(state, value);
			if(PARALLEL_ID.equals(state.top().getPropertyName()))
				theTrans.getSyncRecord().setParallelID(value.intValue());
			else if(CENTER_ID.equals(state.top().getPropertyName()))
			{
				isCenterIDSet = true;
				if(theTrans.getSyncRecord().getCenter().getCenterID() < 0)
				{
					if(getKeeper().getCenterID() == value.intValue())
						throw new RuntimeWrapper("A center cannot synchronize with itself", null);
					theTrans.getSyncRecord().getCenter().setCenterID(value.intValue());
					try
					{
						getKeeper().putCenter(theTrans.getSyncRecord().getCenter(), null);
						if(thePIDS != null)
							thePIDS.postIDSet(PrismsSynchronizer.this, theTrans.getSyncRecord()
								.getCenter());
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper("Could not set center ID", e);
					}
				}
				else if(theTrans.getSyncRecord().getCenter().getCenterID() != value.intValue())
					throw new RuntimeWrapper("Synchronization data not sent from center "
						+ theTrans.getSyncRecord().getCenter(), new PrismsRecordException(
						"Synchronization data not sent from center "
							+ theTrans.getSyncRecord().getCenter()));
			}
			else if("syncPriority".equals(state.top().getPropertyName()))
			{
				if(theTrans.getSyncRecord().getCenter().getPriority() != value.intValue())
				{
					theTrans.getSyncRecord().getCenter().setPriority(value.intValue());
					try
					{
						getKeeper().putCenter(theTrans.getSyncRecord().getCenter(), null);
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper("Could not set center sync priority", e);
					}
				}
			}
			else if("itemCount".equals(state.top().getPropertyName()))
				theTotalItemCount = value.intValue();
			else if("changeCount".equals(state.top().getPropertyName()))
				theTotalChangeCount = value.intValue();
		}

		boolean isNewItem(Object item, long id)
		{
			for(int c = 0; c < theCreations.length; c++)
				if(theCreations[c] == id)
					return true;
			return false;
		}
	}

	private class SyncReceiptHandler extends prisms.util.json.SAJParser.DefaultHandler
	{
		Reader theReader;

		private PrismsCenter [] theCenters;

		private SyncRecord theRecord;

		private prisms.util.LongList theErrorChanges;

		SyncReceiptHandler(Reader reader)
		{
			theReader = reader;
			theErrorChanges = new prisms.util.LongList();
		}

		@Override
		public void endArray(ParseState state)
		{
			super.endArray(state);
			if("errors".equals(state.top().getPropertyName()))
			{
				if(theCenters == null)
					throw new RuntimeWrapper(CENTER_ID
						+ " must be the first element in a synchronization receipt", null);
				long [] ids = theErrorChanges.toArray();
				theErrorChanges.clear();
				long [] batch = new long [ids.length < 100 ? ids.length : 100];
				int i = 0;
				while(i < ids.length)
				{
					int length = ids.length - i;
					if(length > batch.length)
						length = batch.length;
					System.arraycopy(ids, i, batch, 0, length);
					ChangeRecord [] records;
					try
					{
						records = getKeeper().getItems(batch);
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper(
							"Could not retrieve change records for synchronization", e);
					}
					for(ChangeRecord record : records)
					{
						try
						{
							getKeeper().associate(record, theRecord, true);
						} catch(PrismsRecordException e)
						{
							log.error("Could not associate change " + record.id
								+ " with sync record for export", e);
						}
					}
					i += length;
				}
			}
		}

		@Override
		public void separator(ParseState state)
		{
			if(DEPENDS.equals(state.top().getPropertyName()))
			{
				if(theCenters == null)
					throw new RuntimeWrapper(CENTER_ID
						+ " must be the first element in a synchronization receipt", null);
				final int [] dependIdx = new int [] {0};
				try
				{
					new prisms.util.json.SAJParser().parse(theReader,
						new prisms.util.json.SAJParser.DefaultHandler()
						{
							@Override
							public void startArray(ParseState state2)
							{
								super.startArray(state2);
								inputDependSync(state2);
							}

							@Override
							public void separator(ParseState state2)
							{
								super.separator(state2);
								inputDependSync(state2);
							}

							private void inputDependSync(ParseState state2)
							{
								PrismsSynchronizer subSync = getDepends()[dependIdx[0]];
								dependIdx[0]++;
								try
								{
									subSync.readSyncReceipt(theReader);
									state2.spoofValue();
								} catch(IOException e)
								{
									throw new RuntimeWrapper(
										"Could not read dependent sync receipt", e);
								} catch(ParseException e)
								{
									throw new RuntimeWrapper(
										"Could not parse dependent sync receipt", e);
								} catch(PrismsRecordException e)
								{
									throw new RuntimeWrapper(
										"Could not interpret dependent sync receipt", e);
								}
							}
						});
					state.spoofValue();
				} catch(IOException e)
				{
					throw new RuntimeWrapper("Could not read dependent sync receipt", e);
				} catch(ParseException e)
				{
					throw new RuntimeWrapper("Could not parse dependent sync receipt", e);
				}
			}
		}

		@Override
		public void valueNumber(ParseState state, Number value)
		{
			super.valueNumber(state, value);
			if(CENTER_ID.equals(state.top().getPropertyName()))
			{
				PrismsCenter [] centers;
				try
				{
					centers = getKeeper().getCenters();
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not retrieve centers", e);
				}
				java.util.ArrayList<PrismsCenter> selCenters = new java.util.ArrayList<PrismsCenter>();
				for(PrismsCenter center : centers)
					if(center.getCenterID() == value.intValue())
						selCenters.add(center);
				if(selCenters.size() == 0)
					throw new RuntimeWrapper("No such center with ID " + value, null);
				theCenters = selCenters.toArray(new PrismsCenter [selCenters.size()]);
			}
			else if(theCenters == null)
				throw new RuntimeWrapper(CENTER_ID
					+ " must be the first element in a synchronization receipt", null);
			else if("syncRecord".equals(state.top().getPropertyName()))
			{
				SyncRecord [] records;
				for(PrismsCenter center : theCenters)
				{
					try
					{
						records = getKeeper().getSyncRecords(center, Boolean.FALSE);
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper("Could not retrieve export sync records", e);
					}
					if(records.length == 0)
						continue;
					for(SyncRecord record : records)
						if(record.getID() == value.intValue())
						{
							theRecord = record;
							break;
						}
				}
				if(theRecord == null)
				{
					if(theCenters.length == 1)
						throw new RuntimeWrapper("No such export sync record with ID " + value
							+ " for center " + theCenters[0], null);
					else
						throw new RuntimeWrapper("No such export sync record with ID " + value
							+ " in centers " + ArrayUtils.toString(theCenters), null);
				}
			}
			else if(theRecord == null)
				throw new RuntimeWrapper(
					"syncRecord must be the second element in a synchronization recept", null);
			else if(PARALLEL_ID.equals(state.top().getPropertyName()))
			{
				theRecord.setParallelID(value.intValue());
				try
				{
					getKeeper().putSyncRecord(theRecord);
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not store synchronization error message", e);
				}
				if(theSyncListener != null)
					theSyncListener.syncChanged(theRecord);
			}
			else if(state.top().token == prisms.util.json.SAJParser.ParseToken.ARRAY
				&& "errors".equals(state.fromTop(1).getPropertyName()))
				theErrorChanges.add(value.longValue());
		}

		@Override
		public void valueNull(ParseState state)
		{
			super.valueNull(state);
			if("syncError".equals(state.top().getPropertyName()))
			{
				theRecord.setSyncError(null);
				try
				{
					getKeeper().putSyncRecord(theRecord);
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not store synchronization error message", e);
				}
				theRecord.getCenter().setLastExport(theRecord.getSyncTime());
				try
				{
					getKeeper().putCenter(theRecord.getCenter(), null);
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not alter center's sync times", e);
				}
				if(theSyncListener != null)
					theSyncListener.syncChanged(theRecord);
			}
		}

		@Override
		public void valueString(ParseState state, String value)
		{
			super.valueString(state, value);
			if("syncError".equals(state.top().getPropertyName()))
			{
				theRecord.setSyncError(value);
				try
				{
					getKeeper().putSyncRecord(theRecord);
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not store synchronization error message", e);
				}
				if(theSyncListener != null)
					theSyncListener.syncChanged(theRecord);
			}
		}
	}

	private RecordKeeper theKeeper;

	private SynchronizeImpl [] theImpls;

	SyncListener theSyncListener;

	private PrismsSynchronizer [] theDepends;

	private String theSyncLoggingLoc;

	/**
	 * Creates a synchronizer
	 * 
	 * @param keeper The record keeper to keep track of changes
	 * @param impls The implementations to perform the synchronization of the data
	 */
	public PrismsSynchronizer(RecordKeeper keeper, SynchronizeImpl... impls)
	{
		theKeeper = keeper;
		theDepends = new PrismsSynchronizer [0];
		java.util.Arrays.sort(impls, new java.util.Comparator<SynchronizeImpl>()
		{
			public int compare(SynchronizeImpl s1, SynchronizeImpl s2)
			{
				return prisms.arch.PrismsConfig.compareVersions(s1.getVersion(), s2.getVersion());
			}
		});
		for(int i = 0; i < impls.length - 1; i++)
			if(prisms.arch.PrismsConfig.compareVersions(impls[i].getVersion(),
				impls[i + 1].getVersion()) == 0)
				throw new IllegalArgumentException("All synchronize implementations in a"
					+ " synchronizer must have differing versions");
		theImpls = impls;
	}

	/**
	 * @return All synchronize implementations available to this synchronizer for synchronizing with
	 *         various versions of the data set schema
	 */
	public SynchronizeImpl [] getImpls()
	{
		return theImpls;
	}

	/** @param listener The listener to listen for new sync records */
	public void setSyncListener(SyncListener listener)
	{
		theSyncListener = listener;
	}

	/**
	 * Adds a synchronizer dependency. If synchronizer A depends on synchronizer B, then whenever A
	 * attempts to sync, B will sync first, assuring that all B's data is up-to-date before A
	 * operates using that data.
	 * 
	 * @param synchronizer The synchronizer that this synchronizer depends on
	 */
	public void addDependency(PrismsSynchronizer synchronizer)
	{
		theDepends = ArrayUtils.add(theDepends, synchronizer);
	}

	/** @return All synchronizers whose data sets this synchronizer's data set depends on */
	public PrismsSynchronizer [] getDepends()
	{
		return theDepends;
	}

	/** @param logLoc The location to put logging data for synchronization */
	public void setSyncLoggingLoc(String logLoc)
	{
		if(!logLoc.endsWith("/") && !logLoc.endsWith("\\"))
			logLoc += java.io.File.separator;
		theSyncLoggingLoc = logLoc;
	}

	/**
	 * Creates a transaction for exporting or importing synchronization data between centers
	 * 
	 * @param version The highest version of synchronization that the remote center supports
	 * @param isImport Whether this center is importing data from or exporting data to the remote
	 *        center
	 * @param record The sync record that represents this synchronization instance
	 * @param storeSyncRecord Whethe the sync record should be recorded in record-keeping
	 * @param pi The progress informer to keep the user updated on how the synchronization is
	 *        progressing
	 * @return The transaction to use for synchronization
	 */
	public SyncTransaction transact(String version, boolean isImport, SyncRecord record,
		boolean storeSyncRecord, prisms.ui.UI.DefaultProgressInformer pi)
	{
		return new SyncTransaction(getImpl(version, isImport), record, storeSyncRecord, pi);
	}

	/**
	 * Reads a synchronization stream, performing the operations required to synchronize the local
	 * center's data with the remote center
	 * 
	 * @param center The center to synchronize this center's data with
	 * @param type The type of synchronization
	 * @param reader The synchronization stream
	 * @param pi The progress informer to alert the user to the status of synchronization. May be
	 *        null.
	 * @param pids The {@link PostIDSet} to invoke after the center's ID has been set
	 * @param storeSyncRecord Whether to store the sync record and associated changes
	 * @return The synchronization record of the synchronization that occurred
	 * @throws IOException If the stream cannot be completely read or parsed
	 * @throws PrismsRecordException If an error occurs importing the synchronization data
	 */
	public SyncRecord doSyncInput(PrismsCenter center, SyncRecord.Type type, Reader reader,
		prisms.ui.UI.DefaultProgressInformer pi, PostIDSet pids, boolean storeSyncRecord)
		throws IOException, PrismsRecordException
	{
		if(pi == null)
			pi = new prisms.ui.UI.DefaultProgressInformer();
		pi.setProgressText("Reading synchronization data");
		SyncRecord syncRecord = new SyncRecord(center, type, System.currentTimeMillis(), true);
		syncRecord.setSyncError("?");
		if(storeSyncRecord)
			try
			{
				theKeeper.putSyncRecord(syncRecord);
			} catch(PrismsRecordException e)
			{
				log.error("Could not persist synchronization record", e);
				throw new IOException("Could not persist synchronization record: " + e.getMessage());
			}
		if(theSyncListener != null && storeSyncRecord)
			theSyncListener.syncAttempted(syncRecord);
		java.io.File tempFile = null;
		if(theSyncLoggingLoc != null)
		{
			long time = System.currentTimeMillis();
			String fileName = theSyncLoggingLoc + "/SyncAttempt"
				+ prisms.util.PrismsUtils.TimePrecision.MINUTES.print(time, true);
			int secs = (int) (time % 60000) / 1000;
			if(secs < 10)
				fileName += '0';
			fileName += secs;
			fileName += ".json";
			tempFile = new java.io.File(fileName);
			if(!tempFile.exists())
			{
				try
				{
					if(tempFile.createNewFile())
						reader = new prisms.util.LoggingReader(reader, tempFile);
					else
						tempFile = null;
				} catch(IOException e)
				{
					tempFile = null;
				}
			}
		}
		boolean closed = false;
		prisms.util.json.SAJParser parser = new prisms.util.json.SAJParser();
		boolean preAI = false;
		if(getKeeper() instanceof DBRecordKeeper)
		{
			preAI = ((DBRecordKeeper) getKeeper()).hasAbsoluteIntegrity();
			((DBRecordKeeper) getKeeper()).setAbsoluteIntegrity(true);
		}
		try
		{
			parser.parse(reader, new SyncInputHandler(new SyncTransaction(null, syncRecord,
				storeSyncRecord, pi), reader, pids));
			syncRecord.setSyncError(null);
			if(storeSyncRecord)
				center.setLastImport(syncRecord.getSyncTime());
			if(storeSyncRecord)
				verifySyncSuccess(syncRecord);
		} catch(prisms.util.json.SAJParser.ParseException e)
		{
			syncRecord.setSyncError("Could not parse synchronization stream");
			reader.close();
			closed = true;
			throw new PrismsRecordException("Could not parse synchronization stream: "
				+ (tempFile != null ? tempFile.getAbsolutePath() : ""), e);
		} catch(RuntimeWrapper e)
		{
			syncRecord.setSyncError("Could not parse synchronization stream");
			if(e.getCause() instanceof IOException)
				throw (IOException) e.getCause();
			else if(e.getCause() instanceof PrismsRecordException)
				throw (PrismsRecordException) e.getCause();
			else
			{
				reader.close();
				closed = true;
				throw new PrismsRecordException("Could not parse synchronization stream: "
					+ (tempFile != null ? tempFile.getAbsolutePath() : ""), e.getCause());
			}
		} catch(prisms.util.CancelException e)
		{
			syncRecord.setSyncError("Import user canceled operation");
		} catch(Throwable e)
		{
			reader.close();
			closed = true;
			syncRecord.setSyncError("Could not parse synchronization stream");
			throw new PrismsRecordException("Could not parse synchronization stream"
				+ (tempFile != null ? tempFile.getAbsolutePath() : ""), e);
		} finally
		{
			if(getKeeper() instanceof DBRecordKeeper)
				((DBRecordKeeper) getKeeper()).setAbsoluteIntegrity(preAI);
			if(storeSyncRecord)
			{
				try
				{
					theKeeper.putSyncRecord(syncRecord);
				} catch(PrismsRecordException e)
				{
					log.error("Could not store synchronization record", e);
				}
				try
				{
					theKeeper.putCenter(center, null);
				} catch(PrismsRecordException e)
				{
					log.error("Could not store alter center's sync time", e);
				}
				if(theSyncListener != null)
					theSyncListener.syncChanged(syncRecord);
			}
			if(!closed)
			{
				reader.close();
				if(tempFile != null)
					if(!tempFile.delete())
						log.warn("Could not delete temporary sync file: " + tempFile);
			}
		}
		pi.setProgressText("Synchronization successful");
		return syncRecord;
	}

	/** A class representing data to be sent for synchronization */
	private static class SyncOutput
	{
		final prisms.util.IntList theLateIDs;

		final LatestCenterChange [] theLocalChanges;

		final long [] theChangeIDs;

		final prisms.util.LongList theErrorChanges;

		SyncOutput(prisms.util.IntList lateIDs, LatestCenterChange [] localChanges,
			long [] changeIDs, prisms.util.LongList errorChanges)
		{
			theLateIDs = lateIDs;
			theLocalChanges = localChanges;
			theChangeIDs = changeIDs;
			theErrorChanges = errorChanges;
		}
	}

	/**
	 * Creates synchronization output to be sent to a remote center
	 * 
	 * @param trans The sync transaction to generate sync output for
	 * @param request The sync request to satsify
	 * @param exclusiveForSubjects Whether to exclude data sets that the remote center is not aware
	 *        of. This is only true when the method is used internally.
	 * @return The synchronization output to send to the remote center
	 * @throws PrismsRecordException If an error occurs generating the data
	 */
	SyncOutput getSyncOutput(SyncTransaction trans, final SyncRequest request,
		boolean exclusiveForSubjects) throws PrismsRecordException
	{
		final int [] centerIDs = theKeeper.getAllCenterIDs();
		LatestCenterChange [] localChanges;
		{
			java.util.ArrayList<LatestCenterChange> ret = new java.util.ArrayList<LatestCenterChange>();
			for(int centerID : centerIDs)
				for(int subjectCenter : centerIDs)
				{
					if(exclusiveForSubjects)
					{
						boolean hit = false;
						for(LatestCenterChange lcc : request.getLatestChanges())
							if(lcc.getSubjectCenter() == subjectCenter)
							{
								hit = true;
								break;
							}
						if(!hit)
							continue;
					}
					long lastChange = theKeeper.getLatestChange(centerID, subjectCenter);
					if(lastChange > 0)
						ret.add(new LatestCenterChange(centerID, subjectCenter, lastChange));
				}
			localChanges = ret.toArray(new LatestCenterChange [ret.size()]);
		}

		final prisms.util.IntList lateIDs = new prisms.util.IntList();
		final prisms.util.LongList errorChanges = new prisms.util.LongList();
		final java.util.ArrayList<LatestCenterChange> updateChanges = new java.util.ArrayList<LatestCenterChange>();
		ArrayUtils
			.adjust(
				localChanges,
				request.getLatestChanges(),
				new ArrayUtils.DifferenceListenerE<LatestCenterChange, LatestCenterChange, PrismsRecordException>()
				{
					public boolean identity(LatestCenterChange o1, LatestCenterChange o2)
					{
						return o1.getCenterID() == o2.getCenterID()
							&& o1.getSubjectCenter() == o2.getSubjectCenter();
					}

					public LatestCenterChange added(LatestCenterChange o, int mIdx, int retIdx)
					{
						return null;
					}

					public LatestCenterChange removed(LatestCenterChange o, int oIdx, int incMod,
						int retIdx) throws PrismsRecordException
					{
						updateChanges.add(new LatestCenterChange(o.getCenterID(), o
							.getSubjectCenter(), -1));
						if(getKeeper().getLatestPurgedChange(o.getCenterID(), o.getSubjectCenter()) > 0)
						{
							if(!lateIDs.contains(o.getSubjectCenter()))
								lateIDs.add(o.getSubjectCenter());
						}
						return o;
					}

					public LatestCenterChange set(LatestCenterChange o1, int idx1, int incMod,
						LatestCenterChange o2, int idx2, int retIdx) throws PrismsRecordException
					{
						if(getKeeper().getLatestPurgedChange(o1.getCenterID(),
							o1.getSubjectCenter()) > o2.getLatestChange())
						{
							if(!lateIDs.contains(o1.getSubjectCenter()))
								lateIDs.add(o1.getSubjectCenter());
						}
						errorChanges.addAll(RecordUtils.getSyncErrorChanges(getKeeper(),
							request.getRequestingCenter(), o1.getCenterID(), o1.getSubjectCenter()));
						if(o1.getLatestChange() > o2.getLatestChange())
							updateChanges.add(o2);
						return o1;
					}
				});

		prisms.util.LongList changeIDs = new prisms.util.LongList();
		for(LatestCenterChange updateChange : updateChanges)
		{
			if(lateIDs.contains(updateChange.getSubjectCenter()) && !request.isWithRecords())
				continue;
			long [] ids;
			try
			{
				ids = RecordUtils.getSyncChanges(theKeeper, request.getRequestingCenter(),
					updateChange.getCenterID(), updateChange.getSubjectCenter(),
					updateChange.getLatestChange() + 1);
				changeIDs.addAll(ids);
			} catch(PrismsRecordException e)
			{
				throw new PrismsRecordException("Could not get change records for synchronization",
					e);
			}
		}
		if(changeIDs.size() <= 100)
		{ // Possibly some or all of these don't require being sent (e.g. changes to centers)
			try
			{
				ChangeRecord [] records = theKeeper.getItems(changeIDs.toArray());
				for(ChangeRecord record : records)
					if(record != null && !trans.shouldSend(record))
						changeIDs.removeValue(record.id);
			} catch(PrismsRecordException e)
			{
				throw new PrismsRecordException("Could not trim modifications", e);
			}
		}
		changeIDs.addAll(errorChanges, -1);
		long [] ids = changeIDs.toArray();
		changeIDs.clear();
		changeIDs = null;
		ids = theKeeper.sortChangeIDs(ids, true);
		return new SyncOutput(lateIDs, localChanges, ids, errorChanges);
	}

	/**
	 * Writes to a synchronization stream, performing the operations required to synchronize the
	 * remote center's data with the local center
	 * 
	 * @param request The synchronization request (and dependents) to use to get synchronization
	 *        data
	 * @param writer The synchronization stream
	 * @param pi The progress to use to inform the user of the status of the operation
	 * @return The record of the synchronization export
	 * @throws IOException If the stream cannot be completely written
	 * @throws PrismsRecordException If an error occurs gathering the data to write to the stream
	 */
	public SyncRecord doSyncOutput(ValueTree<SyncRequest> request, Writer writer,
		prisms.ui.UI.DefaultProgressInformer pi) throws IOException, PrismsRecordException
	{
		if(pi == null)
			pi = new prisms.ui.UI.DefaultProgressInformer();
		pi.setProgressText("Starting synchronization data generation");
		PrismsCenter center = request.getValue().getRequestingCenter();
		final SyncRecord syncRecord = new SyncRecord(center, request.getValue().getSyncType(),
			System.currentTimeMillis(), false);
		syncRecord.setSyncError("?");
		boolean storeSyncRecord = request.getValue().shouldStoreSyncRecord();
		if(storeSyncRecord)
			try
			{
				theKeeper.putSyncRecord(syncRecord);
			} catch(PrismsRecordException e)
			{
				throw new PrismsRecordException("Could not store synchronization record", e);
			}
		if(theSyncListener != null && storeSyncRecord)
			theSyncListener.syncAttempted(syncRecord);
		SyncTransaction trans = new SyncTransaction(
			getImpl(request.getValue().getVersion(), false), syncRecord, storeSyncRecord, pi);
		SyncOutput sync;
		try
		{
			sync = getSyncOutput(trans, request.getValue(), false);
		} catch(PrismsRecordException e)
		{
			log.error("Sync output failed", e);
			syncRecord.setSyncError(e.getMessage());
			try
			{
				if(storeSyncRecord)
					theKeeper.putSyncRecord(syncRecord);
			} catch(PrismsRecordException e2)
			{
				throw new PrismsRecordException("Could not store synchronization record", e2);
			}
			throw e;
		} catch(Throwable e)
		{
			log.error("Sync output failed", e);
			syncRecord.setSyncError("Exception: " + e.getClass().getName() + ": " + e.getMessage());
			try
			{
				if(storeSyncRecord)
					theKeeper.putSyncRecord(syncRecord);
			} catch(PrismsRecordException e2)
			{
				throw new PrismsRecordException("Could not store synchronization record", e2);
			}
			throw new PrismsRecordException("Sync output failed", e);
		}
		if(pi.isCanceled())
		{
			syncRecord.setSyncError("Export user canceled operation");
			if(storeSyncRecord)
				try
				{
					theKeeper.putSyncRecord(syncRecord);
				} catch(PrismsRecordException e)
				{
					throw new PrismsRecordException("Could not store synchronization record", e);
				}
			return syncRecord;
		}
		final prisms.util.json.JsonStreamWriter jsw = new prisms.util.json.JsonStreamWriter(writer);
		jsw.startObject();
		jsw.startProperty("version");
		jsw.writeString(trans.getImpl().getVersion());
		jsw.startProperty(CENTER_ID);
		jsw.writeNumber(Integer.valueOf(theKeeper.getCenterID()));
		if(theDepends.length > 0)
		{
			jsw.startProperty(DEPENDS);
			jsw.startArray();
			for(int d = 0; d < theDepends.length; d++)
			{
				PrismsSynchronizer subSync = theDepends[d];
				// TODO Possibly prefix the pi with something like "Creating REA Sync Output: "
				jsw.writeCustomValue();
				subSync.doSyncOutput(request.getChildren()[d], writer, pi);
			}
			jsw.endArray();
		}
		jsw.startProperty(PARALLEL_ID);
		jsw.writeNumber(Integer.valueOf(syncRecord.getID()));
		jsw.startProperty("sync-data");
		jsw.startArray();

		int stages = 0;
		if(!sync.theLateIDs.isEmpty())
			stages++;
		if(sync.theChangeIDs.length > 0)
			stages++;

		int stage = 1;
		try
		{
			PS2ItemWriter itemWriter = new PS2ItemWriter(trans,
				new prisms.util.json.JsonStreamWriter(writer), request.getValue()
					.getLatestChanges());
			jsw.startObject();
			if(!sync.theLateIDs.isEmpty())
			{
				pi.setProgressText("Writing local values to stream");
				int itemCount = 0;
				prisms.records.SynchronizeImpl.ItemIterator allItems;
				try
				{
					allItems = trans.getImpl().getAllItems(sync.theLateIDs.toArray(), center);
				} catch(PrismsRecordException e)
				{
					throw new prisms.records.PrismsRecordException(
						"Could not retrieve all items for synchronization", e);
				}
				while(true)
				{
					if(pi.isCanceled())
					{
						syncRecord.setSyncError("Export user canceled operation");
						if(storeSyncRecord)
							try
							{
								theKeeper.putSyncRecord(syncRecord);
							} catch(PrismsRecordException e)
							{
								throw new PrismsRecordException(
									"Could not store synchronization record", e);
							}
						return syncRecord;
					}
					try
					{
						if(!allItems.hasNext())
							break;
						allItems.next();
						itemCount++;
					} catch(PrismsRecordException e)
					{
						throw new prisms.records.PrismsRecordException(
							"Could not retrieve item for synchronization", e);
					}
				}
				allItems = null;
				jsw.startProperty("itemCount");
				jsw.writeNumber(Integer.valueOf(itemCount));
				String baseText = "Writing local values to stream";
				if(stages > 1)
					baseText += " (stage " + stage + " of " + stages + ")";
				pi.setProgressText(baseText);
				pi.setProgress(0);
				pi.setProgressScale(itemCount);
				jsw.startProperty("changeCount");
				jsw.writeNumber(Integer.valueOf(sync.theChangeIDs.length));
				jsw.startProperty(ALL_ITEMS);
				jsw.writeCustomValue();
				itemWriter.start();
				try
				{
					allItems = trans.getImpl().getAllItems(sync.theLateIDs.toArray(), center);
				} catch(PrismsRecordException e)
				{
					throw new prisms.records.PrismsRecordException(
						"Could not retrieve all items for synchronization", e);
				}
				while(true)
				{
					if(pi.isCanceled())
					{
						syncRecord.setSyncError("Export user canceled operation");
						if(storeSyncRecord)
							try
							{
								theKeeper.putSyncRecord(syncRecord);
							} catch(PrismsRecordException e)
							{
								throw new PrismsRecordException(
									"Could not store synchronization record", e);
							}
						return syncRecord;
					}
					Object next;
					try
					{
						if(!allItems.hasNext())
							break;
						next = allItems.next();
					} catch(PrismsRecordException e)
					{
						throw new prisms.records.PrismsRecordException(
							"Could not retrieve item for synchronization", e);
					}
					pi.setProgressText(baseText + "\nWriting " + trans.getType(next) + " "
						+ prisms.util.PrismsUtils.encodeUnicode("" + next));
					itemWriter.writeItem(next);
				}
				allItems = null;
				itemWriter.end();
				pi.setProgressText("Finished writing local values");
				stage++;

				jsw.startProperty("latestChanges");
				jsw.startArray();
				for(LatestCenterChange change : sync.theLocalChanges)
				{
					jsw.startObject();
					jsw.startProperty("centerID");
					jsw.writeNumber(Integer.valueOf(change.getCenterID()));
					jsw.startProperty("subjectCenter");
					jsw.writeNumber(Integer.valueOf(change.getSubjectCenter()));
					jsw.startProperty("latestChange");
					jsw.writeNumber(Long.valueOf(change.getLatestChange()));
					jsw.endObject();
				}
				jsw.endArray();
			}

			if(pi.isCanceled())
			{
				syncRecord.setSyncError("Export user canceled operation");
				if(storeSyncRecord)
					try
					{
						theKeeper.putSyncRecord(syncRecord);
					} catch(PrismsRecordException e)
					{
						throw new PrismsRecordException("Could not store synchronization record", e);
					}
				return syncRecord;
			}
			String baseText = "Writing changes to stream";
			if(stages > 1)
				baseText += " (stage " + stage + " of " + stages + ")";
			long [] ids = sync.theChangeIDs;
			pi.setProgressScale(ids.length);
			theKeeper.sortChangeIDs(ids, true);
			jsw.startProperty("changeCount");
			jsw.writeNumber(Integer.valueOf(ids.length));
			jsw.startProperty(CHANGES);
			jsw.writeCustomValue();
			itemWriter.start();
			long [] batch = new long [ids.length < 100 ? ids.length : 100];
			int i = 0, j = 0;
			while(j < ids.length)
			{
				if(pi.isCanceled())
				{
					syncRecord.setSyncError("Export user canceled operation");
					if(storeSyncRecord)
						try
						{
							theKeeper.putSyncRecord(syncRecord);
						} catch(PrismsRecordException e)
						{
							throw new PrismsRecordException(
								"Could not store synchronization record", e);
						}
					return syncRecord;
				}
				int length = ids.length - j;
				if(length > batch.length)
					length = batch.length;
				System.arraycopy(ids, j, batch, 0, length);
				ChangeRecord [] records;
				try
				{
					records = theKeeper.getItems(batch);
				} catch(PrismsRecordException e)
				{
					throw new prisms.records.PrismsRecordException(
						"Could not retrieve change records for synchronization", e);
				}
				for(ChangeRecord record : records)
				{
					if(pi.isCanceled())
					{
						syncRecord.setSyncError("Export user canceled operation");
						if(storeSyncRecord)
							try
							{
								theKeeper.putSyncRecord(syncRecord);
							} catch(PrismsRecordException e)
							{
								throw new PrismsRecordException(
									"Could not store synchronization record", e);
							}
						return syncRecord;
					}
					i++;
					pi.setProgress(i);
					if(!trans.shouldSend(record))
						continue;
					if(record instanceof ChangeRecordError
						&& sync.theLateIDs.contains(RecordUtils
							.getCenterID(((ChangeRecordError) record).getMajorSubjectID())))
						itemWriter.writeSkippedChange();
					long subjectID;
					if(record instanceof ChangeRecordError)
						subjectID = ((ChangeRecordError) record).getMajorSubjectID();
					else
						subjectID = trans.getID(record.majorSubject);
					if(sync.theLateIDs.contains(RecordUtils.getCenterID(subjectID)))
						itemWriter.writeSkippedChange();
					else
					{
						pi.setProgressText(baseText + "\nExported "
							+ prisms.util.PrismsUtils.encodeUnicode("" + record));
						itemWriter.writeChange(record, sync.theErrorChanges.contains(record.id));
					}
				}
				j += length;
			}
			itemWriter.end();
			jsw.endObject();
		} catch(RuntimeException e)
		{
			throw new PrismsRecordException("Synchronization export failed", e);
		}
		jsw.endArray();
		jsw.endObject();
		jsw.close();
		return syncRecord;
	}

	/**
	 * Generates a synchronization receipt and sends it to the writer
	 * 
	 * @param center The center to get the latest sync record and send the receipt for
	 * @param writer The writer to send the receipt over
	 * @param remoteSyncRecord The ID of the remote synchronization record, or -1 to generate a
	 *        receipt for the most recent sync receipt
	 * @throws IOException If a problem occurs writing to the writer
	 * @throws PrismsRecordException If an error occurs generating the synchronization receipt
	 */
	public void sendSyncReceipt(PrismsCenter center, Writer writer, int remoteSyncRecord)
		throws IOException, PrismsRecordException
	{
		SyncRecord [] records = theKeeper.getSyncRecords(center, Boolean.TRUE);
		prisms.util.json.JsonStreamWriter jsw = new prisms.util.json.JsonStreamWriter(writer);
		jsw.startObject();
		jsw.startProperty(CENTER_ID);
		jsw.writeNumber(Integer.valueOf(getKeeper().getCenterID()));
		if(records.length == 0)
		{
			jsw.startProperty("error");
			jsw.writeString("Synchronization not received");
			jsw.endObject();
			jsw.close();
			return;
		}
		SyncRecord record = records[records.length - 1];
		if(remoteSyncRecord >= 0 && record.getParallelID() != remoteSyncRecord)
		{
			record = null;
			for(SyncRecord r : records)
				if(r.getParallelID() == remoteSyncRecord)
				{
					record = r;
					break;
				}
			if(record == null)
			{
				jsw.startProperty("error");
				jsw.writeString("Synchronization not received");
				jsw.endObject();
				jsw.close();
				return;
			}
		}
		if(theDepends.length > 0)
		{
			jsw.startProperty(DEPENDS);
			jsw.startArray();
			for(int d = 0; d < theDepends.length; d++)
			{
				PrismsCenter subCenter = getDependCenter(theDepends[d], center);
				if(subCenter == null)
					throw new PrismsRecordException("No dependent center"
						+ " parallel to "
						+ center.getName()
						+ " for synchronizer with impl "
						+ theDepends[d].getImpls()[theDepends[d].getImpls().length - 1].getClass()
							.getName());
				SyncRecord [] dependSRs = theDepends[d].getKeeper().getSyncRecords(subCenter,
					Boolean.TRUE);
				int i;
				for(i = 0; i < dependSRs.length
					&& dependSRs[i].getSyncTime() > record.getSyncTime(); i++);
				if(i == dependSRs.length)
				{
					jsw.startObject();
					jsw.startProperty(CENTER_ID);
					jsw.writeNumber(Integer.valueOf(theDepends[d].getKeeper().getCenterID()));
					jsw.startProperty("error");
					jsw.writeString("Synchronization not received");
					jsw.endObject();
				}
				else
				{
					jsw.writeCustomValue();
					theDepends[d].sendSyncReceipt(subCenter, writer, dependSRs[i].getParallelID());
				}
			}
			jsw.endArray();
		}
		long [] errors = theKeeper.search(RecordUtils.getSyncRecordSearch(record, Boolean.TRUE),
			null);
		jsw.startProperty("syncRecord");
		jsw.writeNumber(Integer.valueOf(record.getParallelID()));
		jsw.startProperty(PARALLEL_ID);
		jsw.writeNumber(Integer.valueOf(record.getID()));
		jsw.startProperty("syncError");
		jsw.writeString(record.getSyncError());
		if(errors.length > 0)
		{
			jsw.startProperty("errors");
			jsw.startArray();
			for(long err : errors)
				jsw.writeNumber(Long.valueOf(err));
			jsw.endArray();
		}
		jsw.endObject();
		jsw.close();
	}

	/**
	 * Reads a synchronization receipt generated by another center
	 * 
	 * @param reader The reader to read the receipt from
	 * @throws IOException If an error occurs reading or importing the receipt
	 * @throws PrismsRecordException If an error occurs importing the receipt
	 */
	public void readSyncReceipt(Reader reader) throws IOException, PrismsRecordException
	{
		try
		{
			new prisms.util.json.SAJParser().parse(reader, new SyncReceiptHandler(reader));
		} catch(prisms.util.json.SAJParser.ParseException e)
		{
			log.error("Could not parse sync receipt", e);
			throw new IOException("Could not parse sync receipt: " + e.getMessage());
		} catch(RuntimeWrapper e)
		{
			if(e.getCause() instanceof PrismsRecordException)
				throw (PrismsRecordException) e.getCause();
			else
				throw new PrismsRecordException("Could not read sync receipt: " + e.getMessage(), e);
		}
	}

	/**
	 * Checks for the number of items that this center needs to send a remote center in order to get
	 * the remote center's data set up-to-date with this center
	 * 
	 * @param request The synchronization request (and dependents) to use to check the
	 *        synchronization status
	 * @return The number of changes needed to update the remote center--[0] is the number of items
	 *         that must be sent as a result of being out-of-date; [1] is the number of changes
	 *         needed to be sent.
	 * @throws PrismsRecordException If an error occurs accessing the data
	 */
	public int [] checkSync(ValueTree<SyncRequest> request) throws PrismsRecordException
	{
		SyncTransaction trans = new SyncTransaction(
			getImpl(request.getValue().getVersion(), false), null, false, null);
		SyncOutput sync = getSyncOutput(trans, request.getValue(), false);

		PrismsCenter center = request.getValue().getRequestingCenter();
		int [] ret = new int [] {0, 0};
		if(!sync.theLateIDs.isEmpty())
		{
			prisms.records.SynchronizeImpl.ItemIterator allItems;
			try
			{
				allItems = trans.getImpl().getAllItems(sync.theLateIDs.toArray(), center);
			} catch(PrismsRecordException e)
			{
				throw new prisms.records.PrismsRecordException(
					"Could not retrieve all items for synchronization", e);
			}
			while(allItems.hasNext())
			{
				try
				{
					allItems.next();
					ret[0]++;
				} catch(PrismsRecordException e)
				{
					throw new prisms.records.PrismsRecordException(
						"Could not retrieve item for synchronization", e);
				}
			}
		}
		ret[1] = sync.theChangeIDs.length;
		if(ret[0] > 0 || ret[1] > 0)
			for(int d = 0; d < theDepends.length; d++)
			{
				int [] subRet = theDepends[d].checkSync(request.getChildren()[d]);
				ret[0] += subRet[0];
				ret[1] += subRet[1];
			}
		return ret;
	}

	PrismsCenter getDependCenter(PrismsSynchronizer subSync, PrismsCenter center)
		throws PrismsRecordException
	{
		PrismsCenter subCenter = null;
		for(PrismsCenter c : subSync.getKeeper().getCenters())
			if(c.getCenterID() == center.getCenterID())
			{
				subCenter = c;
				break;
			}
		return subCenter;
	}

	/**
	 * @param version The version to interface with
	 * @param isImport If the impl is for an import or an export
	 * @return The implementation that interfaces specific sets of java objects with this PRISMS
	 *         synchronizer
	 */
	public SynchronizeImpl getImpl(String version, boolean isImport)
	{
		if(version == null)
			return theImpls[0];
		for(int i = 0; i < theImpls.length; i++)
		{
			int comp = prisms.arch.PrismsConfig.compareVersions(version, theImpls[i].getVersion());
			if(comp == 0)
				return theImpls[i];
			else if(comp > 0)
				continue;
			else
			{
				if(i == 0)
					return theImpls[i];
				else if(isImport)
					return theImpls[i];
				else
					return theImpls[i - 1];
			}
		}
		return theImpls[theImpls.length - 1];
	}

	/** @return The record keeper that this synchronizer uses to keep track of changes */
	public RecordKeeper getKeeper()
	{
		return theKeeper;
	}

	boolean verifySyncSuccess(SyncRecord syncRecord) throws PrismsRecordException
	{
		boolean ret = true;
		long [] changeIDs = theKeeper.search(RecordUtils.getSyncRecordSearch(syncRecord, null),
			null);
		long [] batch = new long [changeIDs.length < 100 ? changeIDs.length : 100];
		for(int i = 0; i < changeIDs.length;)
		{
			int len = changeIDs.length - i < batch.length ? changeIDs.length - i : batch.length;
			System.arraycopy(changeIDs, i, batch, 0, len);
			ChangeRecord [] records = theKeeper.getItems(batch);
			for(ChangeRecord record : records)
			{
				if(record == null)
					ret = false;
				else if(record instanceof ChangeRecordError)
					ret = false;
			}
			i += len;
		}
		return ret;
	}
}
