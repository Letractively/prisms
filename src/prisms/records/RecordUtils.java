/*
 * RecordUtils.java Created Jul 30, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import java.io.IOException;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsException;
import prisms.arch.ds.Transactor;
import prisms.arch.ds.User;
import prisms.records.ChangeSearch.ChangeFieldSearch.FieldType;
import prisms.records.MemoryRecordKeeper.IDGetter;
import prisms.util.Search;
import prisms.util.json.SAJParser.ParseException;

/** A set of utility methods useful in the prisms.records2 package */
public class RecordUtils
{
	/**
	 * The range of IDS that may exist in a given PRISMS center. This is left over for backward
	 * compatibility. New code should point to {@link prisms.arch.ds.IDGenerator#ID_RANGE}
	 */
	public static final int theCenterIDRange = 1000000000;

	/** The maximum number of times a given change will be sent for synchronization if it fails */
	public static int MAX_SYNC_TRIES = 10;

	/**
	 * An event property for either a {@link prisms.arch.event.PrismsEvent} or
	 * {@link prisms.arch.event.PrismsPCE} that instructs PRISMS not to pass the event to the
	 * persister.
	 */
	public static final String NO_PERSISTENCE = "no-persist";

	/**
	 * An event property for either a {@link prisms.arch.event.PrismsEvent} or
	 * {@link prisms.arch.event.PrismsPCE} that passes a pre-created transaction for PRISMS to use
	 * when writing change records as a consequence of the event.
	 */
	public static final String TRANSACTION_EVENT_PROP = "transaction";

	/**
	 * An event property for either a {@link prisms.arch.event.PrismsEvent} or
	 * {@link prisms.arch.event.PrismsPCE} that passes a user that caused the change for PRISMS to
	 * use when writing change records as a consequence of the event.
	 */
	public static final String TRANS_USER_EVENT_PROP = "trans-user";

	/**
	 * An event property for either a {@link prisms.arch.event.PrismsEvent} or
	 * {@link prisms.arch.event.PrismsPCE} that passes a synchornization record for PRISMS to use
	 * when writing change records as a consequence of the event.
	 */
	public static final String SYNC_RECORD_EVENT_PROP = "syncRecord";

	/**
	 * An event property for either a {@link prisms.arch.event.PrismsEvent} or
	 * {@link prisms.arch.event.PrismsPCE} that instructs PRISMS to persist the change, but not to
	 * write change records.
	 */
	public static final String PERSIST_NO_RECORDS = "no-records";

	/**
	 * A rank of a {@link PrismsCenter} based on its anticipated ability to import data on request
	 * 
	 * @see RecordUtils#getCenterStatus(PrismsCenter, RecordKeeper)
	 */
	public static enum CenterQuality
	{
		/** Only centers that are synchronizing successfully in automatic mode get this quality */
		GOOD,
		/**
		 * Centers that are not in automatic mode achieve this quality unless their last complete
		 * sync was a failure or their server settings have changed since the last sync
		 */
		NORMAL,
		/**
		 * Centers are given this rank if:
		 * <ul>
		 * <li>Their server settings have been changed since the last completed synchronization</li>
		 * <li>An automatic center has not completed an initial synchronization</li>
		 * <li>An automatic center has not completed a synchronization in more time than its sync
		 * frequency setting</li>
		 * </ul>
		 */
		POOR,
		/** Centers are given this rank if their previous completed synchronization was a failure */
		BAD;
	}

	/**
	 * A report on a center's status for importing changes
	 * 
	 * @see RecordUtils#getCenterStatus(PrismsCenter, RecordKeeper)
	 */
	public static class CenterStatus
	{
		/** The center's quality */
		public final CenterQuality quality;

		/** The reason for the given quality */
		public final String message;

		CenterStatus(CenterQuality q, String msg)
		{
			quality = q;
			message = msg;
		}
	}

	/**
	 * Analyzes a center's past behavior for importing changes and assigns it a quality
	 * 
	 * @param center The center to analyze
	 * @param keeper The record keeper to use in analysis
	 * @return The status of the center for importing changes
	 * @throws PrismsRecordException If an error occurs during analysis
	 */
	public static CenterStatus getCenterStatus(PrismsCenter center, RecordKeeper keeper)
		throws PrismsRecordException
	{
		SyncRecord [] syncs = keeper.getSyncRecords(center, Boolean.TRUE);
		SyncRecord lastImport = null;
		for(SyncRecord sync : syncs)
			if(!"?".equals(sync.getSyncError()))
			{
				lastImport = sync;
				break;
			}
		if(lastImport == null)
		{
			if(center.getServerSyncFrequency() > 0)
				return new CenterStatus(CenterQuality.POOR,
					"Center has not completed initial synchronization");
			else
				return new CenterStatus(CenterQuality.NORMAL,
					"Center has not completed inial synchronization");
		}
		long now = System.currentTimeMillis();
		if(lastImport.getSyncError() != null)
			return new CenterStatus(CenterQuality.BAD, "Center's last sync attempt failed");

		Search srch = new ChangeSearch.ChangeTypeSearch(PrismsChange.CenterChange.url, true);
		srch = srch.or(new ChangeSearch.ChangeTypeSearch(PrismsChange.CenterChange.serverCerts,
			true));
		srch = srch.or(new ChangeSearch.ChangeTypeSearch(PrismsChange.CenterChange.serverUserName,
			true));
		srch = srch.or(new ChangeSearch.ChangeTypeSearch(PrismsChange.CenterChange.serverPassword,
			true));
		srch = new ChangeSearch.SubjectTypeSearch(PrismsChange.center).and(srch);
		srch = srch.and(new ChangeSearch.ChangeTimeSearch(Search.Operator.GTE,
			new Search.SearchDate(lastImport.getSyncTime())));

		if(keeper.search(srch, null).length > 0)
			return new CenterStatus(CenterQuality.POOR,
				"Center's server settings have changed since last completed sync");
		if(center.getServerSyncFrequency() <= 0)
			return new CenterStatus(CenterQuality.NORMAL,
				"Center is available for manual synchronization");
		else if(lastImport.getSyncTime() >= now - center.getServerSyncFrequency())
			return new CenterStatus(CenterQuality.GOOD, "Center is synchronizing on schedule");
		else
			return new CenterStatus(CenterQuality.POOR,
				"Center has not completed synchronization since "
					+ prisms.util.PrismsUtils.print(lastImport.getSyncTime()));
	}

	/**
	 * Creates a transaction to be used for persisting changes caused by events
	 * 
	 * @param session The session in which the event occurred
	 * @param evt The event that occurred
	 * @param userSource The user source for the PRISMS environment
	 * @return The transaction to use for the change
	 */
	public static RecordsTransaction getTransaction(prisms.arch.PrismsSession session,
		prisms.arch.event.PrismsPCE<?> evt, prisms.arch.ds.UserSource userSource)
	{
		if(evt.get(NO_PERSISTENCE) != null)
			return null;
		RecordsTransaction ret = (RecordsTransaction) evt.get(TRANSACTION_EVENT_PROP);
		if(ret != null)
			return ret;
		RecordUser user = (RecordUser) evt.get(TRANS_USER_EVENT_PROP);
		if(user == null && session != null)
			user = session.getUser();
		if(user == null)
			try
			{
				user = userSource.getSystemUser();
			} catch(prisms.arch.PrismsException e)
			{
				throw new IllegalStateException("Could not get system user", e);
			}
		if(user == null)
			throw new IllegalStateException("PRISMS data must be changed from inside a session");
		SyncRecord record = (SyncRecord) evt.get(SYNC_RECORD_EVENT_PROP);
		boolean shouldRecord = true;
		if(Boolean.TRUE.equals(evt.get(PERSIST_NO_RECORDS)))
			shouldRecord = false;
		if(record != null)
			ret = new RecordsTransaction(user, record);
		else
			ret = new RecordsTransaction(user, shouldRecord);
		return ret;
	}

	/**
	 * Creates a transaction to be used for persisting changes caused by events
	 * 
	 * @param session The session in which the event occurred
	 * @param evt The event that occurred
	 * @param userSource The user source for the PRISMS environment
	 * @return The transaction to use for the change
	 */
	public static RecordsTransaction getTransaction(prisms.arch.PrismsSession session,
		prisms.arch.event.PrismsEvent evt, prisms.arch.ds.UserSource userSource)
	{
		if(evt.getProperty(NO_PERSISTENCE) != null)
			return null;
		RecordsTransaction ret = (RecordsTransaction) evt.getProperty(TRANSACTION_EVENT_PROP);
		if(ret != null)
			return ret;
		User user = (User) evt.getProperty(TRANS_USER_EVENT_PROP);
		if(user == null && session != null)
			user = session.getUser();
		if(user == null)
			try
			{
				user = userSource.getSystemUser();
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not get system user", e);
			}
		if(user == null)
			throw new IllegalStateException("PRISMS data must be changed from inside a session");
		SyncRecord record = (SyncRecord) evt.getProperty(SYNC_RECORD_EVENT_PROP);
		boolean shouldRecord = true;
		if(Boolean.TRUE.equals(evt.getProperty("db-persisted")))
			shouldRecord = false;
		if(Boolean.TRUE.equals(evt.getProperty(PERSIST_NO_RECORDS)))
			shouldRecord = false;
		evt.setProperty("db-persisted", Boolean.TRUE);
		if(record != null)
			ret = new RecordsTransaction(user, record);
		else
			ret = new RecordsTransaction(user, shouldRecord);
		return ret;
	}

	/**
	 * @param objectID The ID of an object
	 * @return The ID of the center where the given object was created
	 */
	public static int getCenterID(long objectID)
	{
		return (int) (objectID / theCenterIDRange);
	}

	/**
	 * Gets the change type under the subject type by name
	 * 
	 * @param subjectType The subject type to get a change type under
	 * @param typeName The name of the change type to get
	 * @return The change type of the given name under the given subject type
	 * @throws PrismsRecordException If no such change exists for the given subject type
	 */
	public static ChangeType getChangeType(SubjectType subjectType, String typeName)
		throws PrismsRecordException
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

	/**
	 * Checks a set of centers for the change save time and last time they synchronized to this
	 * center in order to get a purge-safe time after which change should not be purged so that they
	 * will be available to clients.
	 * 
	 * @param centers The centers to use to determine the safe time for purging changes
	 * @return The purge-safe time after which changes should not be purged
	 */
	public static long getPurgeSafeTime(PrismsCenter [] centers)
	{
		long nowTime = System.currentTimeMillis();
		long ret = nowTime;
		for(PrismsCenter center : centers)
		{
			long lastSync = center.getLastExport();
			long saveTime = center.getChangeSaveTime();

			if(lastSync <= 0)
			{ /* If this center has not synchronized yet, we have to send the whole data set next
				* time anyway, so this center doesn't affect our safe time */
				continue;
			}
			if(lastSync < nowTime - saveTime)
			{ /* If the center hasn't synchronized since its configured save time, we don't save
				* modifications for the center anymore to conserve space. This may hurt
				* synchronization because it's more likely that all items will have to be sent to
				* the center when it next synchronizes. Also, if the local center becomes
				* out-of-date by not synchronizing with this center beyond its configured save time
				* on the remote center, some modifications may be lost on whichever center
				* synchronizes first. We still don't save modifications because we don't know when
				* the center will be able to synchronize again if ever. To mitigate this problem,
				* modification save times should be increased through the user interface. */
				continue;
			}
			if(ret > lastSync)
				ret = lastSync;
		}
		return ret;
	}

	/**
	 * Gets the data ID of an item
	 * 
	 * @param item The item to get the ID of
	 * @param persister The persister to use to get the ID
	 * @return The data ID of the item
	 */
	public static long getDataID(Object item, Object persister)
	{
		if(item instanceof PrismsCenter)
			return ((PrismsCenter) item).getID();
		else if(item instanceof AutoPurger)
			return 0;
		else if(persister instanceof RecordPersister)
			return ((RecordPersister) persister).getID(item);
		else if(persister instanceof IDGetter)
			return ((IDGetter) persister).getID(item);
		else
			throw new IllegalArgumentException(persister.getClass().getName()
				+ " is not a persister or an ID getter");
	}

	/**
	 * Gets the history domains of an item
	 * 
	 * @param item The item to get the domains for
	 * @param persister The persister to use to get the domains
	 * @return The subject types of all possible changes on the given type of item
	 * @throws PrismsRecordException If the item is not valid for the given persister
	 */
	public static SubjectType [] getHistoryDomains(Object item, Object persister)
		throws PrismsRecordException
	{
		if(item instanceof PrismsCenter)
			return new SubjectType [] {PrismsChange.center};
		if(item instanceof AutoPurger)
			return new SubjectType [] {PrismsChange.autoPurge};
		else if(persister instanceof RecordPersister)
			return ((RecordPersister) persister).getHistoryDomains(item);
		else if(persister instanceof IDGetter)
			return ((IDGetter) persister).getHistoryDomains(item);
		else
			throw new IllegalArgumentException(persister.getClass().getName()
				+ " is not a persister or an ID getter");
	}

	/**
	 * Creates a search that retrieves the entire history of a given item
	 * 
	 * @param item The item to get the history of
	 * @param persister The {@link RecordPersister persister} or {@link MemoryRecordKeeper.IDGetter
	 *        IDGetter} to get the IDs of the data and the item's history domains
	 * @return The search
	 * @throws PrismsRecordException If an error occurs creating the search
	 */
	public static Search getHistorySearch(Object item, Object persister)
		throws PrismsRecordException
	{
		long itemID = getDataID(item, persister);
		SubjectType [] types = getHistoryDomains(item, persister);
		if(types.length == 0)
			return null;
		Search ret = null;
		for(int i = 0; i < types.length; i++)
		{
			Search temp = null;
			if(types[i].getMajorType().isInstance(item))
				temp = new ChangeSearch.ChangeFieldSearch(FieldType.major, Long.valueOf(itemID),
					true);
			if(types[i].getMetadataType1() != null && types[i].getMetadataType1().isInstance(item))
			{
				Search temp2 = new ChangeSearch.ChangeFieldSearch(FieldType.data1,
					Long.valueOf(itemID), true);
				if(temp != null)
					temp = temp.and(temp2);
				else
					temp = temp2;
			}
			if(types[i].getMetadataType2() != null && types[i].getMetadataType2().isInstance(item))
			{
				Search temp2 = new ChangeSearch.ChangeFieldSearch(FieldType.data2,
					Long.valueOf(itemID), true);
				if(temp != null)
					temp = temp.and(temp2);
				else
					temp = temp2;
			}
			if(temp == null)
			{
				ChangeType [] changes = (ChangeType []) types[i].getChangeTypes()
					.getEnumConstants();
				for(ChangeType ch : changes)
				{
					if(ch.getMinorType() != null && ch.getMinorType().isInstance(item))
					{
						temp = new ChangeSearch.ChangeFieldSearch(FieldType.minor,
							Long.valueOf(itemID), true);
						temp = new ChangeSearch.ChangeTypeSearch(ch, true).and(temp);
					}
				}
			}
			if(temp == null)
				throw new PrismsRecordException("Subject type " + types[i] + " does not apply to "
					+ item.getClass().getName());
			temp = new ChangeSearch.SubjectTypeSearch(types[i]).and(temp);
			if(ret != null)
				ret = ret.or(temp);
			else
				ret = temp;
		}
		return ret;
	}

	/**
	 * Creates a search that will retrieve all changes to the same data as the given change, but
	 * occur after it.
	 * 
	 * @param change The change to get the successors of
	 * @param persister The {@link RecordPersister persister} or {@link MemoryRecordKeeper.IDGetter
	 *        IDGetter} to get the IDs of the data in the change record
	 * @return The search
	 */
	public static Search getSuccessorSearch(ChangeRecord change, Object persister)
	{
		Search ret = new ChangeSearch.ChangeFieldSearch(FieldType.major, Long.valueOf(getDataID(
			change.majorSubject, persister)), true);
		if(change.minorSubject != null)
			ret = ret.and(new ChangeSearch.ChangeFieldSearch(FieldType.minor, Long
				.valueOf(getDataID(change.minorSubject, persister)), true));
		else
			ret = ret.and(new ChangeSearch.ChangeFieldSearch(FieldType.minor, null, true));
		if(change.data1 != null)
			ret = new ChangeSearch.ChangeFieldSearch(FieldType.data1, Long.valueOf(getDataID(
				change.data1, persister)), true).and(ret);
		if(change.data2 != null)
			ret = new ChangeSearch.ChangeFieldSearch(FieldType.data2, Long.valueOf(getDataID(
				change.data2, persister)), true).and(ret);
		if(change.type.changeType != null)
			ret = new ChangeSearch.ChangeTypeSearch(change.type.changeType, true).and(ret);
		if(change.type.additivity == 0)
			ret = new ChangeSearch.AdditivitySearch(Integer.valueOf(0)).and(ret);
		else
			ret = (new ChangeSearch.AdditivitySearch(Integer.valueOf(-1))
				.or(new ChangeSearch.AdditivitySearch(Integer.valueOf(-1)))).and(ret);
		ret = new ChangeSearch.SubjectTypeSearch(change.type.subjectType).and(ret);
		ret = ret.and(new ChangeSearch.ChangeTimeSearch(Search.Operator.GT, new Search.SearchDate(
			change.time)));
		return ret;
	}

	/**
	 * Creates a search to query changes that match the given center ID, subject center, and occur
	 * at or after a given time
	 * 
	 * @param centerID The center ID to search for changes on. May be negative to ignore this part
	 *        of the query.
	 * @param subjectCenter The subject center to search for changes on. May be negative to ignore
	 *        this part of the query.
	 * @param lastChange The time to search for changes on or after. May be negative to ignore this
	 *        part of the query.
	 * @return The search
	 */
	public static Search getSearch(int centerID, int subjectCenter, long lastChange)
	{
		Search ret = null;
		if(centerID >= 0)
			ret = new ChangeSearch.IDRange(Long.valueOf(prisms.arch.ds.IDGenerator
				.getMinID(centerID)), Long.valueOf(prisms.arch.ds.IDGenerator.getMaxID(centerID)));
		if(subjectCenter >= 0)
		{
			Search scs = new ChangeSearch.SubjectCenterSearch(Integer.valueOf(subjectCenter));
			if(ret != null)
				ret = ret.and(scs);
			else
				ret = scs;
		}
		if(lastChange > 0)
		{
			Search timeSearch = new ChangeSearch.ChangeTimeSearch(Search.Operator.GTE,
				new Search.SearchDate(lastChange));
			if(ret != null)
				ret = ret.and(timeSearch);
			else
				ret = timeSearch;
		}
		return ret;
	}

	/**
	 * Gets changes that need to be sent to the given center for synchronization
	 * 
	 * @param keeper The record keeper being used for synchronization
	 * @param center The center synchronizing with this center
	 * @param centerID The ID of the center to get changes by
	 * @param subjectCenter The ID of the data set to get changes to
	 * @param lastChange The time of the last change that the remote center has by the change center
	 *        to the given data set
	 * @return The IDs of all changes that should be sent to the remote center to be completely
	 *         synchronized with this center
	 * @throws prisms.records.PrismsRecordException If an error occurs retrieving the data
	 */
	public static long [] getSyncChanges(RecordKeeper keeper, PrismsCenter center, int centerID,
		int subjectCenter, long lastChange) throws PrismsRecordException
	{
		return keeper.search(getSearch(centerID, subjectCenter, lastChange), null);
	}

	/**
	 * Creates a search for changes associated with a particular sync record
	 * 
	 * @param record The sync record to get changes for
	 * @param error Whether to search for changes that were imported with errors or not or both
	 * @return The search
	 */
	public static Search getSyncRecordSearch(SyncRecord record, Boolean error)
	{
		Search ret = new ChangeSearch.SyncRecordSearch(Integer.valueOf(record.getID()));
		if(error != null)
			ret = ret.and(ChangeSearch.SyncRecordSearch.forChangeError(error));
		return ret;
	}

	/**
	 * Gets changes that need to be sent to the given center for synchronization because they were
	 * incorrectly received earlier
	 * 
	 * @param keeper The record keeper being used for synchronization
	 * @param center The center synchronizing with this center
	 * @param centerID The ID of the center to get changes by
	 * @param subjectCenter The ID of the data set to get changes to
	 * @return The IDs of all changes that should be sent to the remote center to be completely
	 *         synchronized with this center
	 * @throws prisms.records.PrismsRecordException If an error occurs retrieving the data
	 */
	public static long [] getSyncErrorChanges(RecordKeeper keeper, PrismsCenter center,
		int centerID, int subjectCenter) throws PrismsRecordException
	{
		class LongKey
		{
			long value;

			LongKey()
			{
			}

			LongKey(long val)
			{
				value = val;
			}

			@Override
			public boolean equals(Object o)
			{
				return o instanceof LongKey && ((LongKey) o).value == value;
			}

			@Override
			public int hashCode()
			{
				return (int) (value ^ (value >>> 32));
			}
		}
		java.util.HashMap<LongKey, int []> ret = new java.util.HashMap<LongKey, int []>();
		SyncRecord [] records = keeper.getSyncRecords(center, Boolean.FALSE);

		LongKey key = new LongKey();
		for(int r = records.length - 1; r >= 0; r--)
		{
			SyncRecord record = records[r];
			if(record.getSyncError() != null)
				continue;
			if(!ret.isEmpty())
			{
				long [] successChanges = keeper.search(getSyncRecordSearch(record, Boolean.FALSE),
					null);
				for(int i = 0; i < successChanges.length; i++)
				{
					key.value = successChanges[i];
					int [] count = ret.get(key);
					if(count != null)
						count[0] = -1;
				}
			}
			long [] errorChanges = keeper.search(getSyncRecordSearch(record, Boolean.TRUE), null);
			for(int i = 0; i < errorChanges.length; i++)
				if(getCenterID(errorChanges[i]) == centerID
					&& keeper.getSubjectCenter(errorChanges[i]) == subjectCenter)
				{
					key.value = errorChanges[i];
					int [] count = ret.get(key);
					if(count == null)
					{
						count = new int [] {1};
						ret.put(new LongKey(errorChanges[i]), count);
					}
					else if(count[0] >= 0)
						count[0]++;
				}
		}

		java.util.Iterator<int []> iter = ret.values().iterator();
		while(iter.hasNext())
		{
			int [] count = iter.next();
			if(count[0] < 0 || count[0] > MAX_SYNC_TRIES)
				iter.remove();
		}
		long [] retArray = new long [ret.size()];
		int i = 0;
		for(LongKey k : ret.keySet())
			retArray[i++] = k.value;
		return retArray;
	}

	/**
	 * Tests whether one change is a successor of another
	 * 
	 * @param test The change to test
	 * @param change The change to see if <code>test</code> is a successor of
	 * @return Whether <code>test</code> is a successor of <code>change</code>
	 */
	public static boolean isSuccessor(ChangeRecord test, ChangeRecord change)
	{
		if(change instanceof prisms.records.ChangeRecordError
			|| test instanceof prisms.records.ChangeRecordError)
			return false;
		if(test.time < change.time)
			return false;
		if(!change.type.subjectType.equals(test.type.subjectType))
			return false;
		if(!equal(change.type.changeType, test.type.changeType))
			return false;
		if((change.type.additivity == 0) != (test.type.additivity == 0))
			return false;
		if(!change.majorSubject.equals(test.majorSubject))
			return false;
		if(!equal(change.minorSubject, test.minorSubject))
			return false;
		return true;
	}

	private static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}

	/**
	 * Makes sure a synchronizer's dependents have centers set up. Synchronization will fail if the
	 * dependent centers are missing.
	 * 
	 * @param sync The synchronizer to check
	 * @param center The center for the given synchronizer to sync with
	 * @return An error message if one of the dependent synchronizer's does not have a parallel
	 *         center to the given center; or null if all dependents are ready.
	 * @throws PrismsRecordException If an error occurs getting a dependent center
	 */
	public static String areDependentsSetUp(PrismsSynchronizer sync, PrismsCenter center)
		throws PrismsRecordException
	{
		for(PrismsSynchronizer depend : sync.getDepends())
			if(sync.getDependCenter(depend, center) == null)
			{
				if(depend.getKeeper() instanceof DBRecordKeeper)
					return "Synchronization for "
						+ ((DBRecordKeeper) depend.getKeeper()).getNamespace()
						+ " has not been initialized";
				else
					return "Synchronization dependency failed: "
						+ depend.getImpls()[depend.getImpls().length - 1].getClass().getName();
			}
		return null;
	}

	/**
	 * Retrieves and serializes the time of the latest change for each center that this center has
	 * modifications from. This data is required by a remote center in order to receive
	 * synchronization data.
	 * 
	 * @param keeper The record keeper to use to retrieve the data
	 * @param jsw The writer to serialize the change data to
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 * @throws java.io.IOException If an error occurs writing to the stream
	 */
	public static void serializeCenterChanges(RecordKeeper keeper,
		prisms.util.json.JsonSerialWriter jsw) throws PrismsRecordException, java.io.IOException
	{
		jsw.startArray();
		int [] centerIDs = keeper.getAllCenterIDs();
		for(int centerID : centerIDs)
		{
			for(int subjectCenter : centerIDs)
			{
				long lastChange = keeper.getLatestChange(centerID, subjectCenter);
				if(lastChange > 0)
				{
					jsw.startObject();
					jsw.startProperty("centerID");
					jsw.writeNumber(Integer.valueOf(centerID));
					jsw.startProperty("subjectCenter");
					jsw.writeNumber(Integer.valueOf(subjectCenter));
					jsw.startProperty("latestChange");
					jsw.writeNumber(Long.valueOf(lastChange));
					jsw.endObject();
				}
			}
		}
		jsw.endArray();
	}

	/**
	 * Serializes center changes with dependencies into JSON. If the synchronizer has no
	 * dependencies, the result will be simply the serialized change array. If there are
	 * dependencies, then these will be encapsulated in the JSONObject which is returned. The
	 * complexity here is for the purpose of backward-compatibility.
	 * 
	 * @param synchronizer The synchronizer to serialize changes of
	 * @return The serialized latest change structure needed to generate synchronization data
	 * @throws PrismsRecordException If an error occurs
	 */
	public static Object serializeCenterChanges(PrismsSynchronizer synchronizer)
		throws PrismsRecordException
	{
		if(synchronizer.getDepends().length == 0)
			return serializeCenterChanges(synchronizer.getKeeper());
		org.json.simple.JSONObject ret = new org.json.simple.JSONObject();
		ret.put("changes", serializeCenterChanges(synchronizer.getKeeper()));
		org.json.simple.JSONArray depends = new org.json.simple.JSONArray();
		ret.put("depends", depends);
		for(PrismsSynchronizer depend : synchronizer.getDepends())
			depends.add(serializeCenterChanges(depend));
		return ret;
	}

	/**
	 * Retrieves and serializes the time of the latest change for each center that this center has
	 * modifications from. This data is required by a remote center in order to receive
	 * synchronization data.
	 * 
	 * @param keeper The record keeper to use to retrieve the data
	 * @return The serialized change data
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public static org.json.simple.JSONArray serializeCenterChanges(RecordKeeper keeper)
		throws PrismsRecordException
	{
		java.io.StringWriter writer = new java.io.StringWriter();
		try
		{
			serializeCenterChanges(keeper, new prisms.util.json.JsonStreamWriter(writer));
			return (org.json.simple.JSONArray) new prisms.util.json.SAJParser().parse(
				new java.io.StringReader(writer.toString()),
				new prisms.util.json.SAJParser.DefaultHandler());
		} catch(IOException e)
		{
			throw new PrismsRecordException("IO Exception???!!!", e);
		} catch(ParseException e)
		{
			throw new PrismsRecordException("Parse Exception!!!", e);
		}
	}

	/**
	 * Implements
	 * {@link prisms.util.AbstractPreparedSearch#addParamTypes(Search, java.util.Collection)} for
	 * change records
	 * 
	 * @param search The search to get the missing parameter types for
	 * @param types The collection to add the missing types to
	 */
	protected static void addParamTypes(ChangeSearch search, java.util.Collection<Class<?>> types)
	{
		switch(search.getType())
		{
		case id:
			ChangeSearch.IDRange ids = (ChangeSearch.IDRange) search;
			if(ids.getMinID() == null)
				types.add(Long.class);
			if(ids.getMaxID() == null)
				types.add(Long.class);
			break;
		case majorSubjectRange:
			ChangeSearch.MajorSubjectRange msr = (ChangeSearch.MajorSubjectRange) search;
			if(msr.getMinID() == null)
				types.add(Long.class);
			if(msr.getMaxID() == null)
				types.add(Long.class);
			break;
		case subjectCenter:
			ChangeSearch.SubjectCenterSearch scs = (ChangeSearch.SubjectCenterSearch) search;
			if(scs.getSubjectCenter() == null)
				types.add(Integer.class);
			break;
		case time:
			ChangeSearch.ChangeTimeSearch cts = (ChangeSearch.ChangeTimeSearch) search;
			if(cts.changeTime == null)
				types.add(java.util.Date.class);
			break;
		case user:
			ChangeSearch.ChangeUserSearch cus = (ChangeSearch.ChangeUserSearch) search;
			if(cus.getUser() == null)
				types.add(Long.class);
			break;
		case subjectType:
			ChangeSearch.SubjectTypeSearch sts = (ChangeSearch.SubjectTypeSearch) search;
			if(sts.getSubjectType() == null)
				types.add(SubjectType.class);
			break;
		case changeType:
			ChangeSearch.ChangeTypeSearch chType = (ChangeSearch.ChangeTypeSearch) search;
			if(!chType.isSpecified())
				types.add(ChangeType.class);
			break;
		case add:
			ChangeSearch.AdditivitySearch as = (ChangeSearch.AdditivitySearch) search;
			if(as.getAdditivity() == null)
				types.add(Integer.class);
			break;
		case field:
			ChangeSearch.ChangeFieldSearch cfs = (ChangeSearch.ChangeFieldSearch) search;
			if(!cfs.isFieldIDSpecified())
				types.add(Long.class);
			break;
		case syncRecord:
			ChangeSearch.SyncRecordSearch srs = (ChangeSearch.SyncRecordSearch) search;
			if(srs.isSyncRecordSet())
			{
				if(srs.getSyncRecordID() == null)
					types.add(Integer.class);
			}
			else if(srs.getTimeOp() != null)
			{
				if(srs.getTime() == null)
					types.add(java.util.Date.class);
			}
			else if(srs.getSyncError() != null)
			{}
			else if(srs.isChangeErrorSet())
			{
				if(srs.getChangeError() == null)
					types.add(Boolean.class);
			}
			else if(srs.isSyncImportSet())
			{
				if(srs.isSyncImport() == null)
					types.add(Boolean.class);
			}
			else
				throw new IllegalStateException("Unrecognized sync record search type: " + srs);
			break;
		case localOnly:
			break;
		}
	}

	/**
	 * Performs the effects of a {@link PrismsChange} type change record on this environment
	 * 
	 * @param change The change record to effect
	 * @param currentValue The current value of the change record's field
	 * @param recordKeeper The record keeper to use to effect the change
	 * @param trans The transaction to use to record the change
	 * @param centerProp The centers property to set the center in if the change is an addition or
	 *        removal
	 * @param apps The applications to perform the change in
	 * @throws PrismsRecordException If an error occurs performing the change
	 */
	public static void doPrismsChange(ChangeRecord change, Object currentValue,
		DBRecordKeeper recordKeeper, RecordsTransaction trans,
		prisms.arch.event.PrismsProperty<PrismsCenter []> centerProp, PrismsApplication... apps)
		throws PrismsRecordException
	{
		switch((PrismsChange) change.type.subjectType)
		{
		case center:
			PrismsCenter center = (PrismsCenter) change.majorSubject;
			PrismsChange.CenterChange cc = (PrismsChange.CenterChange) change.type.changeType;
			if(cc == null)
			{
				if(change.type.additivity > 0)
				{
					for(PrismsApplication app : apps)
						app.setGlobalProperty(centerProp,
							prisms.util.ArrayUtils.add(app.getGlobalProperty(centerProp), center),
							TRANSACTION_EVENT_PROP, trans);
					recordKeeper.putCenter(center, trans);
				}
				else
				{
					for(PrismsApplication app : apps)
						app.setGlobalProperty(centerProp, prisms.util.ArrayUtils.remove(
							app.getGlobalProperty(centerProp), center), TRANSACTION_EVENT_PROP,
							trans);
					recordKeeper.removeCenter(center, trans);
				}
				return;
			}
			switch(cc)
			{
			case name:
				center.setName((String) currentValue);
				break;
			case url:
				center.setServerURL((String) currentValue);
				break;
			case serverCerts:
				if(currentValue instanceof String)
					currentValue = DBRecordKeeper.deserializeCerts((String) currentValue);
				center.setCertificates((java.security.cert.X509Certificate[]) currentValue);
				break;
			case serverUserName:
				center.setServerUserName((String) currentValue);
				break;
			case serverPassword:
				center.setServerPassword((String) currentValue);
				break;
			case syncFrequency:
				center.setServerSyncFrequency(currentValue == null ? -1 : ((Number) currentValue)
					.longValue());
				break;
			case clientUser:
				center.setClientUser((RecordUser) currentValue);
				break;
			case changeSaveTime:
				center.setChangeSaveTime(currentValue == null ? -1 : ((Number) currentValue)
					.longValue());
				break;
			}
			recordKeeper.putCenter(center, trans);
			for(PrismsApplication app : apps)
				app.fireGlobally(null, new prisms.arch.event.PrismsEvent("centerChanged", "center",
					center, TRANSACTION_EVENT_PROP, trans));
			break;
		case autoPurge:
			AutoPurger purger = (AutoPurger) change.majorSubject;
			PrismsChange.AutoPurgeChange apc = (PrismsChange.AutoPurgeChange) change.type.changeType;
			switch(apc)
			{
			case entryCount:
				purger
					.setEntryCount(currentValue == null ? -1 : ((Number) currentValue).intValue());
				break;
			case age:
				purger.setAge(currentValue == null ? -1 : ((Number) currentValue).longValue());
				break;
			case excludeType:
				if(change.type.additivity > 0)
					purger.addExcludeType((RecordType) currentValue);
				else
					purger.removeExcludeType((RecordType) currentValue);
				break;
			case excludeUser:
				if(change.type.additivity > 0)
					purger.addExcludeUser((RecordUser) currentValue);
				else
					purger.removeExcludeUser((RecordUser) currentValue);
				break;
			}
			recordKeeper.setAutoPurger(purger, trans);
			for(PrismsApplication app : apps)
				app.fireGlobally(null, new prisms.arch.event.PrismsEvent("autoPurgeChanged"));
			break;
		}
	}

	/**
	 * Gets the databased value of a change's field
	 * 
	 * @param <E> The type of exception that may be thrown
	 * @param change The change to get the field value of
	 * @param recordNS The record namespace to get the value from
	 * @param transactor The transactor to use for database access
	 * @param persister The persister to get a user by ID if needed
	 * @return The databased value of the change's field
	 * @throws E If an error occurs getting the data
	 */
	public static <E extends Throwable> Object getDBValue(ChangeRecord change, String recordNS,
		Transactor<E> transactor, RecordPersister persister) throws E
	{
		if(change.type.changeType == null || change.type.changeType.getObjectType() == null)
			return null;
		switch((PrismsChange) change.type.subjectType)
		{
		case center:
			String sql = " FROM " + transactor.getTablePrefix()
				+ "prisms_center_view WHERE recordNS=" + prisms.util.DBUtils.toSQL(recordNS)
				+ " AND id=" + ((PrismsCenter) change.majorSubject).getID();
			switch((PrismsChange.CenterChange) change.type.changeType)
			{
			case name:
				return transactor.getDBItem(null, "SELECT name" + sql, String.class);
			case url:
				return transactor.getDBItem(null, "SELECT url" + sql, String.class);
			case serverCerts:
				byte [] bytes = transactor.getDBItem(null, "SELECT serverCerts" + sql,
					byte [].class);
				try
				{
					return java.security.cert.CertificateFactory.getInstance("X.509")
						.generateCertificates(new java.io.ByteArrayInputStream(bytes));
				} catch(java.security.cert.CertificateException e)
				{
					transactor.getThrower().error("Could not decode certificates", e);
					throw new IllegalStateException("Thrower didn't throw exception");
				}
			case serverUserName:
				return transactor.getDBItem(null, "SELECT serverUserName" + sql, String.class);
			case serverPassword:
				return transactor.getDBItem(null, "SELECT serverPassword" + sql, String.class);
			case syncFrequency:
				Number ret = transactor.getDBItem(null, "SELECT syncFrequency" + sql, Number.class);
				if(ret != null)
					ret = Long.valueOf(ret.longValue());
				return ret;
			case clientUser:
				Number userID = transactor.getDBItem(null, "SELECT clientUser" + sql, Number.class);
				if(userID == null)
					return null;
				try
				{
					return persister.getUser(userID.longValue());
				} catch(PrismsRecordException e)
				{
					transactor.getThrower().error("Could not get user with ID " + userID, e);
					return null;
				}
			case changeSaveTime:
				ret = transactor.getDBItem(null, "SELECT changeSaveTime" + sql, Number.class);
				if(ret != null)
					ret = Long.valueOf(ret.longValue());
				return ret;
			}
			break;
		case autoPurge:
			sql = " FROM " + transactor.getTablePrefix() + "prisms_auto_purge WHERE recordNS="
				+ prisms.util.DBUtils.toSQL(recordNS);
			switch((PrismsChange.AutoPurgeChange) change.type.changeType)
			{
			case entryCount:
				Number ret = transactor.getDBItem(null, "SELECT entryCount" + sql, Number.class);
				if(ret != null)
					ret = Integer.valueOf(ret.intValue());
				return ret;
			case age:
				ret = transactor.getDBItem(null, "SELECT age" + sql, Number.class);
				if(ret != null)
					ret = Long.valueOf(ret.longValue());
				return ret;
			case excludeType:
				return change.previousValue;
			case excludeUser:
				return null;
			}
		}
		throw new IllegalStateException("Unrecognized PRISMS change type: " + change.type);
	}
}
