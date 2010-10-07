/*
 * MemoryRecordKeeper.java Created Aug 3, 2010 by Andrew Butler, PSL
 */
package prisms.records2;

import java.util.ArrayList;

import prisms.util.ArrayUtils;
import prisms.util.DualKey;
import prisms.util.LongList;

/**
 * Keeps changes that have not yet been synchronized by all centers. This record keeper is ONLY
 * meant for synchronization purposes, as it purges changes and synchronization records as soon as
 * they are not needed for synchronization anymore.
 */
public class MemoryRecordKeeper implements RecordKeeper2
{
	/** Gives this record keeper access to the ID of an object */
	public interface IDGetter
	{
		/**
		 * @param item The item to get the ID of
		 * @return The ID of the item
		 */
		long getID(Object item);

		/**
		 * Gets all domains that qualify as change history for the given value's type
		 * 
		 * @param value The value to get the history of
		 * @return All domains that record history of the type of the value
		 * @throws PrismsRecordException If an error occurs getting the data
		 */
		SubjectType [] getHistoryDomains(Object value) throws PrismsRecordException;
	}

	private class SyncRecordHolder
	{
		SyncRecord theSyncRecord;

		ArrayList<ChangeRecord> theSuccessChanges;

		ArrayList<ChangeRecord> theErrorChanges;

		SyncRecordHolder(SyncRecord record)
		{
			theSyncRecord = record;
			theSuccessChanges = new ArrayList<ChangeRecord>();
			theErrorChanges = new ArrayList<ChangeRecord>();
		}
	}

	private final int theCenterID;

	private int theSyncPriority;

	private final IDGetter theIDGetter;

	private java.util.concurrent.locks.ReentrantLock theLock;

	private ArrayList<PrismsCenter> theCenters;

	private ArrayList<ChangeRecord> theChanges;

	private ArrayList<SyncRecordHolder> theSyncRecords;

	/** The last time this record keeper was checked for items that need to be purged */
	private long theLastPurge;

	private int theRetrySetting;

	private int theNextCenterID;

	private int theNextSyncRecordID;

	private int theNextChangeID;

	private java.util.HashMap<DualKey<Integer, Integer>, Long> theRecentPurges;

	/**
	 * Creates a record keeper with a random center ID
	 * 
	 * @param getter The ID getter for this record keeper
	 */
	public MemoryRecordKeeper(IDGetter getter)
	{
		this(getter, (int) (Math.random() * Integer.MAX_VALUE));
	}

	/**
	 * Creates a record keeper with a given ID
	 * 
	 * @param centerID The center ID for this record keeper
	 * @param getter The ID getter for this record keeper
	 */
	public MemoryRecordKeeper(IDGetter getter, int centerID)
	{
		theCenterID = centerID;
		theIDGetter = getter;
		theLock = new java.util.concurrent.locks.ReentrantLock();
		theCenters = new ArrayList<PrismsCenter>();
		theChanges = new ArrayList<ChangeRecord>();
		theSyncRecords = new ArrayList<SyncRecordHolder>();
		theLastPurge = System.currentTimeMillis();
		theRecentPurges = new java.util.HashMap<DualKey<Integer, Integer>, Long>();
		theRetrySetting = 2;
	}

	public int getCenterID()
	{
		return theCenterID;
	}

	public int getLocalPriority()
	{
		return theSyncPriority;
	}

	/**
	 * @param priority The synchronization priority for this keeper's local center
	 * @see PrismsCenter#getPriority()
	 */
	public void setLocalPriority(int priority)
	{
		theSyncPriority = priority;
	}

	public PrismsCenter [] getCenters() throws PrismsRecordException
	{
		return theCenters.toArray(new PrismsCenter [theCenters.size()]);
	}

	public void putCenter(PrismsCenter center, RecordUser user, SyncRecord record)
		throws PrismsRecordException
	{
		theLock.lock();
		try
		{
			if(center.getID() < 0)
			{
				center.setID(theNextCenterID);
				theNextCenterID++;
			}
			for(PrismsCenter c : theCenters)
			{
				if(center.equals(c))
				{
					if(center != c)
					{
						c.setCenterID(center.getCenterID());
						c.setName(center.getName());
						c.setServerURL(center.getServerURL());
						c.setServerUserName(center.getServerUserName());
						c.setServerPassword(center.getServerPassword());
						c.setServerSyncFrequency(center.getServerSyncFrequency());
						c.setLastImport(center.getLastImport());
						c.setClientUser(center.getClientUser());
						c.setChangeSaveTime(center.getChangeSaveTime());
						c.setLastExport(center.getLastExport());
						purge();
					}
					return;
				}
			}
			theCenters.add(center);
		} finally
		{
			theLock.unlock();
		}
	}

	public void removeCenter(PrismsCenter center, RecordUser user, SyncRecord record)
		throws PrismsRecordException
	{
		for(int c = 0; c < theCenters.size(); c++)
		{
			if(theCenters.get(c).equals(center))
			{
				theLock.lock();
				try
				{
					theCenters.remove(c);
					java.util.Iterator<SyncRecordHolder> iter = theSyncRecords.iterator();
					while(iter.hasNext())
						if(iter.next().theSyncRecord.getCenter().equals(center))
							iter.remove();
					purge();
				} finally
				{
					theLock.unlock();
				}
				return;
			}
		}
		throw new prisms.records2.PrismsRecordException("No such center " + center);
	}

	public int [] getAllCenterIDs() throws PrismsRecordException
	{
		int [] ret = new int [theRecentPurges.size()];
		int i = 0;
		java.util.Iterator<DualKey<Integer, Integer>> iter = theRecentPurges.keySet().iterator();
		while(iter.hasNext())
			ret[i++] = iter.next().getKey1().intValue();
		for(ChangeRecord change : theChanges)
		{
			int centerID = Record2Utils.getCenterID(change.id);
			boolean contained = false;
			for(i = 0; i < ret.length; i++)
				if(ret[i] == centerID)
				{
					contained = true;
					break;
				}
			if(contained)
				continue;
			ret = (int []) ArrayUtils.addP(ret, new Integer(centerID));
		}
		return ret;
	}

	public long getLatestChange(int centerID, int subjectCenter)
	{
		Long purged = theRecentPurges.get(new DualKey<Integer, Integer>(new Integer(centerID),
			new Integer(subjectCenter)));
		for(int c = theChanges.size() - 1; c >= 0; c--)
		{
			if(purged != null && purged.longValue() > theChanges.get(c).time)
				break;
			if(Record2Utils.getCenterID(theChanges.get(c).id) != centerID)
				continue;
			if(getSubjectCenter(theChanges.get(c)) != subjectCenter)
				continue;
			return theChanges.get(c).time;
		}
		return purged == null ? -1 : purged.longValue();
	}

	public int getSubjectCenter(long changeID)
	{
		for(ChangeRecord change : theChanges)
			if(change.id == changeID)
				return getSubjectCenter(change);
		return -1;
	}

	private long getID(Object item)
	{
		if(item instanceof PrismsCenter)
			return ((PrismsCenter) item).getID();
		else if(item instanceof AutoPurger2)
			return 0;
		else
			return theIDGetter.getID(item);
	}

	private int getSubjectCenter(ChangeRecord change)
	{
		if(change instanceof ChangeRecordError)
		{
			ChangeRecordError error = (ChangeRecordError) change;
			for(PrismsChange pc : PrismsChange.values())
				if(pc.name().equals(error.getSubjectType()))
					return theCenterID;
			return Record2Utils.getCenterID(error.getMajorSubjectID());
		}
		else
		{
			Object item = change.majorSubject;
			if(item instanceof PrismsCenter || item instanceof AutoPurger2)
				return theCenterID;
			return Record2Utils.getCenterID(theIDGetter.getID(item));
		}
	}

	SubjectType [] getHistoryDomains(Object item) throws PrismsRecordException
	{
		if(item instanceof PrismsCenter)
			return new SubjectType [] {PrismsChange.center};
		if(item instanceof AutoPurger2)
			return new SubjectType [] {PrismsChange.autoPurge};
		else
			return theIDGetter.getHistoryDomains(item);
	}

	public void setLatestChange(int centerID, int subjectCenter, long time)
	{
		Long oldTime = theRecentPurges.get(new DualKey<Integer, Integer>(new Integer(centerID),
			new Integer(subjectCenter)));
		if(oldTime != null && oldTime.longValue() >= time)
			return;
		for(int i = theChanges.size() - 1; i >= 0; i--)
		{
			ChangeRecord change = theChanges.get(i);
			if(change.time <= time)
				break;
			if(Record2Utils.getCenterID(change.id) == centerID
				&& getSubjectCenter(change) == subjectCenter)
				return;
		}
		theRecentPurges.put(new DualKey<Integer, Integer>(new Integer(centerID), new Integer(
			subjectCenter)), new Long(time));
	}

	public long [] getChangeIDs(int centerID, int subjectCenter, long since)
	{
		LongList ret = new LongList();
		for(ChangeRecord change : theChanges)
			if(matches(change, centerID, subjectCenter, since))
				ret.add(change.id);
		return ret.toArray();
	}

	private boolean matches(ChangeRecord change, int centerID, int subjectCenter, long since)
	{
		if(centerID >= 0 && Record2Utils.getCenterID(change.id) != centerID)
			return false;
		if(subjectCenter >= 0 && getSubjectCenter(change) != subjectCenter)
			return false;
		if(since > 0 && change.time < since)
			return false;
		return true;
	}

	public long [] sortChangeIDs(long [] changeIDs, boolean ascending)
	{
		Long [] cids = new Long [changeIDs.length];
		for(int i = 0; i < cids.length; i++)
			cids[i] = new Long(changeIDs[i]);
		cids = ArrayUtils.adjust(cids, theChanges.toArray(new ChangeRecord [theChanges.size()]),
			new ArrayUtils.DifferenceListener<Long, ChangeRecord>()
			{
				public boolean identity(Long o1, ChangeRecord o2)
				{
					return o1.longValue() == o2.id;
				}

				public Long added(ChangeRecord o, int mIdx, int retIdx)
				{
					return null;
				}

				public Long removed(Long o, int oIdx, int incMod, int retIdx)
				{
					return null;
				}

				public Long set(Long o1, int idx1, int incMod, ChangeRecord o2, int idx2, int retIdx)
				{
					return o1;
				}
			});
		for(int i = 0; i < cids.length; i++)
			changeIDs[i] = cids[i].longValue();
		return changeIDs;
	}

	public long [] getHistory(Object historyItem) throws PrismsRecordException
	{
		SubjectType [] subjects = getHistoryDomains(historyItem);
		LongList ret = new LongList();
		for(ChangeRecord change : theChanges)
			for(SubjectType subject : subjects)
				if(historyMatches(change, subject, historyItem))
				{
					ret.add(change.id);
					break;
				}
		return ret.toArray();
	}

	private boolean historyMatches(ChangeRecord change, SubjectType subject, Object historyItem)
	{
		boolean ret = false;
		boolean hit = false;
		if(change instanceof ChangeRecordError)
		{
			ChangeRecordError err = (ChangeRecordError) change;
			if(!err.getSubjectType().equals(subject.name()))
				return false;
			long id = getID(historyItem);
			if(subject.getMajorType().isInstance(historyItem))
			{
				hit = true;
				ret = id == err.getMajorSubjectID();
			}
			if(subject.getMetadataType1() != null
				&& subject.getMetadataType1().isInstance(historyItem))
			{
				hit = true;
				ret = id == err.getData1ID();
			}
			if(subject.getMetadataType2() != null
				&& subject.getMetadataType2().isInstance(historyItem))
			{
				hit = true;
				ret = id == err.getData2ID();
			}
			if(!hit)
			{
				ChangeType [] changes = (ChangeType []) subject.getChangeTypes().getEnumConstants();
				for(ChangeType ch : changes)
					if(ch.getMinorType() != null && ch.getMinorType().isInstance(historyItem))
					{
						hit = true;
						ret = id == err.getMinorSubjectID();
					}
			}
		}
		else
		{
			if(!change.type.subjectType.equals(subject))
				return false;
			if(subject.getMajorType().isInstance(historyItem))
			{
				hit = true;
				ret = change.majorSubject.equals(historyItem);
			}
			if(subject.getMetadataType1() != null
				&& subject.getMetadataType1().isInstance(historyItem))
			{
				hit = true;
				ret = historyItem.equals(change.data1);
			}
			if(subject.getMetadataType2() != null
				&& subject.getMetadataType2().isInstance(historyItem))
			{
				hit = true;
				ret = historyItem.equals(change.data2);
			}
			if(!hit)
			{
				ChangeType [] changes = (ChangeType []) subject.getChangeTypes().getEnumConstants();
				for(ChangeType ch : changes)
					if(ch.getMinorType() != null && ch.getMinorType().isInstance(historyItem))
					{
						hit = true;
						ret = historyItem.equals(change.minorSubject);
					}
			}
		}
		return ret;
	}

	public boolean hasChange(long changeID)
	{
		for(ChangeRecord ch : theChanges)
			if(ch.id == changeID)
				return true;
		return false;
	}

	public boolean hasSuccessfulChange(long changeID)
	{
		if(Record2Utils.getCenterID(changeID) == theCenterID)
			return true;
		for(SyncRecordHolder sync : theSyncRecords)
			for(ChangeRecord change : sync.theSuccessChanges)
				if(change.id == changeID)
					return true;
		return false;
	}

	public long [] getSuccessors(ChangeRecord change)
	{
		if(change instanceof ChangeRecordError)
			return new long [0];
		LongList ret = new LongList();
		for(ChangeRecord ch : theChanges)
			if(Record2Utils.isSuccessor(ch, change))
				ret.add(ch.id);
		return ret.toArray();
	}

	public ChangeRecord [] getChanges(long [] ids)
	{
		LongList idList = new LongList(ids);
		java.util.ArrayList<ChangeRecord> ret = new java.util.ArrayList<ChangeRecord>();
		for(ChangeRecord change : theChanges)
			if(idList.contains(change.id))
				ret.add(change);
		return ArrayUtils.adjust(ret.toArray(new ChangeRecord [ret.size()]),
			idList.toObjectArray(), new ArrayUtils.DifferenceListener<ChangeRecord, Long>()
			{
				public boolean identity(ChangeRecord o1, Long o2)
				{
					return o1.id == o2.longValue();
				}

				public ChangeRecord added(Long o, int mIdx, int retIdx)
				{
					return null;
				}

				public ChangeRecord removed(ChangeRecord o, int oIdx, int incMod, int retIdx)
				{
					return null;
				}

				public ChangeRecord set(ChangeRecord o1, int idx1, int incMod, Long o2, int idx2,
					int retIdx)
				{
					return o1;
				}
			});
	}

	public SyncRecord [] getSyncRecords(PrismsCenter center, Boolean isImport)
	{
		java.util.ArrayList<SyncRecord> ret = new java.util.ArrayList<SyncRecord>();
		for(SyncRecordHolder holder : theSyncRecords)
			if(holder.theSyncRecord.getCenter().equals(center))
			{
				if(isImport != null && holder.theSyncRecord.isImport() != isImport.booleanValue())
					continue;
				ret.add(holder.theSyncRecord);
			}
		return ret.toArray(new SyncRecord [ret.size()]);
	}

	public void putSyncRecord(SyncRecord record)
	{
		for(SyncRecordHolder holder : theSyncRecords)
			if(holder.theSyncRecord.equals(record))
			{
				if(holder.theSyncRecord != record)
				{
					holder.theSyncRecord.setImport(record.isImport());
					holder.theSyncRecord.setSyncType(record.getSyncType());
					holder.theSyncRecord.setParallelID(record.getParallelID());
					holder.theSyncRecord.setSyncError(record.getSyncError());
				}
				return;
			}
		theLock.lock();
		try
		{
			if(record.getID() < 0)
			{
				record.setID(theNextSyncRecordID);
				theNextSyncRecordID++;
			}
			for(SyncRecordHolder holder : theSyncRecords)
				if(holder.theSyncRecord.equals(record))
				{
					if(holder.theSyncRecord != record)
					{
						holder.theSyncRecord.setImport(record.isImport());
						holder.theSyncRecord.setSyncType(record.getSyncType());
						holder.theSyncRecord.setParallelID(record.getParallelID());
						holder.theSyncRecord.setSyncError(record.getSyncError());
					}
					return;
				}
			int min = 0, max = theSyncRecords.size();
			while(min < max)
			{
				int mid = (min + max) / 2;
				if(theSyncRecords.get(mid).theSyncRecord.getSyncTime() > record.getSyncTime())
					max = mid - 1;
				else if(theSyncRecords.get(mid).theSyncRecord.getSyncTime() < record.getSyncTime())
					min = mid + 1;
				else
					min = max = mid;
			}

			theSyncRecords.add(min, new SyncRecordHolder(record));
			purge();
		} finally
		{
			theLock.unlock();
		}
	}

	public long [] getSyncChanges(SyncRecord record) throws PrismsRecordException
	{
		theLock.lock();
		try
		{
			for(SyncRecordHolder holder : theSyncRecords)
				if(holder.theSyncRecord.equals(record))
				{
					LongList ret = new LongList();
					int s = 0, e = 0;
					while(s < holder.theSuccessChanges.size() || e < holder.theErrorChanges.size())
					{
						if(s == holder.theSuccessChanges.size())
						{
							ret.add(holder.theErrorChanges.get(e).id);
							e++;
						}
						else if(e == holder.theErrorChanges.size())
						{
							ret.add(holder.theSuccessChanges.get(s).id);
							s++;
						}
						else if(holder.theSuccessChanges.get(s).time < holder.theErrorChanges
							.get(e).time)
						{
							ret.add(holder.theSuccessChanges.get(s).id);
							s++;
						}
						else
						{
							ret.add(holder.theErrorChanges.get(e).id);
							e++;
						}
					}
					return ret.toArray();
				}
		} finally
		{
			theLock.unlock();
		}
		throw new prisms.records2.PrismsRecordException("No such sync record: " + record);
	}

	public long [] getErrorChanges(SyncRecord record) throws PrismsRecordException
	{
		theLock.lock();
		try
		{
			for(SyncRecordHolder holder : theSyncRecords)
				if(holder.theSyncRecord.equals(record))
				{
					LongList ret = new LongList();
					for(ChangeRecord change : holder.theErrorChanges)
						ret.add(change.id);
					return ret.toArray();
				}
		} finally
		{
			theLock.unlock();
		}
		throw new prisms.records2.PrismsRecordException("No such sync record: " + record);
	}

	public long [] getSuccessChanges(SyncRecord record) throws PrismsRecordException
	{
		theLock.lock();
		try
		{
			for(SyncRecordHolder holder : theSyncRecords)
				if(holder.theSyncRecord.equals(record))
				{
					LongList ret = new LongList();
					for(ChangeRecord change : holder.theSuccessChanges)
						ret.add(change.id);
					return ret.toArray();
				}
		} finally
		{
			theLock.unlock();
		}
		throw new prisms.records2.PrismsRecordException("No such sync record: " + record);
	}

	public void associate(ChangeRecord change, SyncRecord syncRecord, boolean error)
		throws PrismsRecordException
	{
		for(SyncRecordHolder holder : theSyncRecords)
			if(holder.theSyncRecord.equals(syncRecord))
			{
				if(error)
				{
					for(int i = 0; i < holder.theErrorChanges.size(); i++)
						if(holder.theErrorChanges.get(i).equals(change))
							return;
				}
				else
				{
					for(int i = 0; i < holder.theSuccessChanges.size(); i++)
						if(holder.theSuccessChanges.get(i).equals(change))
							return;
				}
				theLock.lock();
				try
				{
					ArrayList<ChangeRecord> list;
					if(error)
					{
						list = holder.theErrorChanges;
						int idx = holder.theSuccessChanges.indexOf(change);
						if(idx >= 0)
							holder.theSuccessChanges.remove(idx);
					}
					else
					{
						list = holder.theSuccessChanges;
						int idx = holder.theErrorChanges.indexOf(change);
						if(idx >= 0)
							holder.theErrorChanges.remove(idx);
					}
					int min = 0, max = list.size();
					while(min < max)
					{
						int mid = (min + max) / 2;
						if(list.get(mid).time > change.time)
							max = mid - 1;
						else if(list.get(mid).time < change.time)
							min = mid + 1;
						else
							min = max = mid;
					}
					list.add(min, change);
				} finally
				{
					theLock.unlock();
				}
				return;
			}
		throw new prisms.records2.PrismsRecordException("No such sync record: " + syncRecord);
	}

	public void removeSyncRecord(SyncRecord record) throws PrismsRecordException
	{
		theLock.lock();
		try
		{
			for(int h = 0; h < theSyncRecords.size(); h++)
				if(theSyncRecords.get(h).theSyncRecord.equals(record))
				{
					theSyncRecords.remove(h);
					return;
				}
		} finally
		{
			theLock.unlock();
		}
		throw new prisms.records2.PrismsRecordException("No such sync record: " + record);
	}

	public ChangeRecord persist(RecordUser user, SubjectType subjectType, ChangeType changeType,
		int additivity, Object majorSubject, Object minorSubject, Object previousValue,
		Object data1, Object data2) throws PrismsRecordException
	{
		theLock.lock();
		try
		{
			long id = theCenterID * 1L * Record2Utils.theCenterIDRange + theNextChangeID;
			theNextChangeID++;
			ChangeRecord record = new ChangeRecord(id, System.currentTimeMillis(), user,
				subjectType, changeType, additivity, majorSubject, minorSubject, previousValue,
				data1, data2);
			persist(record);
			return record;
		} finally
		{
			theLock.unlock();
		}
	}

	public void persist(ChangeRecord record) throws PrismsRecordException
	{
		for(ChangeRecord change : theChanges)
			if(change.equals(record))
				return;
		theLock.lock();
		try
		{
			int min = 0, max = theChanges.size();
			while(min < max)
			{
				int mid = (min + max) / 2;
				if(theChanges.get(mid).time > record.time)
					max = mid - 1;
				else if(theChanges.get(mid).time < record.time)
					min = mid + 1;
				else
					min = max = mid;
			}
			theChanges.add(min, record);
		} finally
		{
			theLock.unlock();
		}
	}

	public long getLatestPurgedChange(int centerID, int subjectCenter) throws PrismsRecordException
	{
		Long ret = theRecentPurges.get(new Integer(centerID));
		if(ret == null)
			return -1;
		return ret.longValue();
	}

	private void purge()
	{
		// This must only be called if the lock is already obtained
		if(theChanges.isEmpty())
			return;
		long now = System.currentTimeMillis();
		if(now - theLastPurge < 30000)
			return;
		theLastPurge = now;
		long purgeTime = Record2Utils.getPurgeSafeTime(theCenters
			.toArray(new PrismsCenter [theCenters.size()]));
		if(theChanges.get(0).time >= purgeTime)
			return;
		boolean purgedSomething = false;
		// Purge changes that aren't needed for synchronization anymore
		for(int i = 0; i < theChanges.size(); i++)
		{
			ChangeRecord change = theChanges.get(i);
			if(change.time >= purgeTime)
				break;
			if(hasSyncExportError(change))
				continue; // A center is waiting for the change to be re-sent
			for(SyncRecordHolder holder : theSyncRecords)
			{
				if(holder.theSyncRecord.getSyncTime() < change.time)
					continue; // The change can't be in this sync record
				holder.theErrorChanges.remove(change);
				holder.theSuccessChanges.remove(change);
			}
			purgedSomething = true;
			theChanges.remove(i);
			Integer centerID = new Integer(Record2Utils.getCenterID(change.id));
			Integer subjectCenter = new Integer(getSubjectCenter(change));
			DualKey<Integer, Integer> key = new DualKey<Integer, Integer>(centerID, subjectCenter);
			Long recentPurge = theRecentPurges.get(key);
			if(recentPurge == null || recentPurge.longValue() < change.time)
				theRecentPurges.put(key, new Long(change.time));
			i--;
		}
		if(purgedSomething)
		{
			// Purge sync records that aren't needed for synchronization anymore
			for(int i = 0; i < theSyncRecords.size(); i++)
			{
				SyncRecordHolder holder = theSyncRecords.get(i);
				if(holder.theSyncRecord.getSyncTime() >= purgeTime)
					continue;
				if(holder.theSyncRecord.getSyncError() != null)
				{
					theSyncRecords.remove(i);
					i--;
					continue;
				}
				if(!holder.theErrorChanges.isEmpty() || !holder.theSuccessChanges.isEmpty())
					continue;
				theSyncRecords.remove(i);
				i--;
			}
		}
	}

	private boolean hasSyncExportError(ChangeRecord change)
	{
		int [] count = new int [theCenters.size()];
		for(SyncRecordHolder holder : theSyncRecords)
		{
			int centerIdx = theCenters.indexOf(holder.theSyncRecord.getCenter());
			if(count[centerIdx] < 0)
				continue;
			if(holder.theSyncRecord.getSyncTime() < change.time || holder.theSyncRecord.isImport())
				continue;
			if(holder.theSuccessChanges.contains(change))
				count[centerIdx] = -1;
			else if(holder.theErrorChanges.contains(change))
				count[centerIdx]++;
		}
		/* Return true if the change hasn't been successfully sent to a center AND the sync on that
		 * change has not been attempted enough times to satisfy the retry setting */
		for(int c : count)
			if(c > 0 && c < theRetrySetting)
				return true;
		return false;
	}
}
