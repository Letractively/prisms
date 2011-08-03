/*
 * ScaledRecordKeeper.java Created Feb 17, 2011 by Andrew Butler, PSL
 */
package prisms.records;

import prisms.records.ChangeSearch.ChangeFieldSearch.FieldType;
import prisms.util.ArrayUtils;
import prisms.util.Search;

/**
 * A scaled record keeper is a record keeper that monitors a database for modifications that may be
 * written to the database by other instances of this type pointing to the same database. In this
 * way, the memory representations of a data set may be kept consistent across multiple server
 * instances simply by pointing to a common database.
 */
public class ScaledRecordKeeper extends DBRecordKeeper
{
	// /** A tag for an item returned from {@link ScaledRecordKeeper#lock(Object, boolean)} */
	// public interface Lock
	// {
	// }
	//
	// private class LockImpl implements Lock
	// {
	// final String theType;
	//
	// final long theID;
	//
	// transient boolean isLocked;
	//
	// LockImpl(String type, long id)
	// {
	// theType = type;
	// theID = id;
	// isLocked = true;
	// }
	//
	// void unlocked()
	// {
	// isLocked = false;
	// }
	// }

	/**
	 * The maximum age, in seconds, of synchronizations before they are purged and the items
	 * released
	 */
	public static int SYNC_PURGE_AGE_SECONDS = 5 * 60;

	private ScaleImpl theImpl;

	private ScaledRecordKeeper [] theDepends;

	private PreparedSearch<ChangeField> theChangeGetter;

	private PreparedSearch<ChangeField> theSuccessorCheck;

	private long theCheckInterval;

	private long theLastCheck;

	private long theTimeBeforeCheck;

	private prisms.util.LongList theProcessedChanges;

	// private long theLastPurge;

	/**
	 * @see DBRecordKeeper#DBRecordKeeper(String, prisms.arch.PrismsConfig,
	 *      prisms.arch.ConnectionFactory, prisms.arch.ds.IDGenerator)
	 */
	public ScaledRecordKeeper(String namespace, prisms.arch.PrismsConfig connEl,
		prisms.arch.ConnectionFactory factory, prisms.arch.ds.IDGenerator ids)
	{
		super(namespace, connEl, factory, ids);
		theCheckInterval = 10000;
		theProcessedChanges = new prisms.util.LongList();
		theDepends = new ScaledRecordKeeper [0];
		theTimeBeforeCheck = theLastCheck = System.currentTimeMillis();
	}

	@Override
	public void setPersister(RecordPersister persister)
	{
		super.setPersister(persister);
		try
		{
			if(theChangeGetter != null)
				destroyPreparedSearches();
			createPreparedSearches();
		} catch(PrismsRecordException e)
		{
			throw new IllegalStateException("Could not prepare searches", e);
		}
	}

	/**
	 * @param impl The scaling implementation to use to update the application's state when a change
	 *        is written to the database by another instance
	 */
	public void setScaleImpl(ScaleImpl impl)
	{
		theImpl = impl;
	}

	/**
	 * Adds a dependency to this record keeper--the dependent record keeper will always be
	 * up-to-date before this record keeper attempts to update itself
	 * 
	 * @param depend The dependent scaled record keeper to add
	 */
	public void addDependency(ScaledRecordKeeper depend)
	{
		theDepends = ArrayUtils.add(theDepends, depend);
	}

	/** @return All record keepers whose data sets this record keeper's data set depends on */
	public ScaledRecordKeeper [] getDepends()
	{
		return theDepends;
	}

	/**
	 * @return The frequency at which {@link #checkChanges(boolean)} will actually perform a check
	 *         when the force parameter is false
	 */
	public long getCheckInterval()
	{
		return theCheckInterval;
	}

	/**
	 * @param checkInterval The frequency at which {@link #checkChanges(boolean)} will actually
	 *        perform a check when the force parameter is false
	 */
	public void setCheckInterval(long checkInterval)
	{
		theCheckInterval = checkInterval;
	}

	@Override
	public void persist(ChangeRecord record) throws PrismsRecordException
	{
		synchronized(this)
		{
			theProcessedChanges.add(record.id);
		}
		super.persist(record);
	}

	// /**
	// * Purges synchronization locks in the database older than {@link #SYNC_PURGE_AGE_SECONDS}
	// * seconds
	// *
	// * @param force Whether to perform the purge even if a purge has been executed recently
	// * @throws PrismsRecordException If an error occurs while trying to purge
	// */
	// public void purgeLocks(boolean force) throws PrismsRecordException
	// {
	// long now = System.currentTimeMillis();
	// long age = SYNC_PURGE_AGE_SECONDS * 60L * 1000;
	// if(!force && (now - theLastPurge) < age)
	// return;
	// synchronized(thePurgeSyncStatement)
	// {
	// if(!force && (now - theLastPurge) < age)
	// return;
	// try
	// {
	// java.sql.Timestamp purgeTime = new java.sql.Timestamp(now - age);
	// thePurgeSyncStatement.setTimestamp(1, purgeTime);
	// thePurgeSyncStatement.executeUpdate();
	// } catch(SQLException e)
	// {
	// throw new PrismsRecordException("Could not purge synchronizations", e);
	// } finally
	// {
	// try
	// {
	// thePurgeSyncStatement.clearParameters();
	// } catch(SQLException e)
	// {
	// log.error("Could not clear purge parameters", e);
	// }
	// }
	// }
	// theLastPurge = now;
	// }
	//
	// /**
	// * Obtains a lock on an item that is enforced across all instances accessing the same database
	// *
	// * @param item The item to lock in the enterprise
	// * @param wait Whether to wait for the lock or not
	// * @return An object representing the lock, to be used with {@link #unlock(Lock)}. May be null
	// * if <code>wait</code> is false and another thread or instance already has the lock.
	// * @throws PrismsRecordException If the lock cannot be obtained due to an internal error
	// */
	// public Lock lock(Object item, final boolean wait) throws PrismsRecordException
	// {
	// purgeLocks(false);
	// String type = theImpl.getType(item.getClass());
	// long id = theImpl.getID(item);
	// LockImpl ret = null;
	// do
	// {
	// synchronized(theSyncStatement)
	// {
	// try
	// {
	// try
	// {
	// theSyncStatement.setString(1, prisms.util.DBUtils.toSQL(type));
	// theSyncStatement.setLong(2, id);
	// java.sql.Timestamp time = new java.sql.Timestamp(System.currentTimeMillis());
	// theSyncStatement.setTimestamp(3, time);
	// } catch(SQLException e)
	// {
	// throw new PrismsRecordException("Could not perform lock", e);
	// }
	// try
	// {
	// theSyncStatement.executeUpdate();
	// checkChanges(true);
	// ret = new LockImpl(type, id);
	// } catch(SQLException e)
	// {
	// // This is normal and means that the item has already been synchronized on
	// }
	// } finally
	// {
	// try
	// {
	// theSyncStatement.clearParameters();
	// } catch(SQLException e)
	// {
	// throw new PrismsRecordException("Could not reset sync statement", e);
	// }
	// }
	// }
	// if(ret == null && wait)
	// try
	// {
	// Thread.sleep(100);
	// } catch(InterruptedException e)
	// {}
	// } while(ret == null && wait);
	// return ret;
	// }
	//
	// /**
	// * Unlocks an item locked by a previous call to {@link #lock(Object, boolean)}
	// *
	// * @param lock The lock obtained from the call to {@link #lock(Object, boolean)}
	// * @throws PrismsRecordException If the lock cannot be released due to an internal error
	// */
	// public void unlock(Lock lock) throws PrismsRecordException
	// {
	// if(!(lock instanceof LockImpl))
	// throw new IllegalArgumentException("Lock " + lock + " was not issued by this keeper");
	// LockImpl lck = (LockImpl) lock;
	// if(!lck.isLocked)
	// return;
	// synchronized(theUnsyncStatement)
	// {
	// try
	// {
	// theUnsyncStatement.setString(1, prisms.util.DBUtils.toSQL(lck.theType));
	// theUnsyncStatement.setLong(2, lck.theID);
	// theUnsyncStatement.executeUpdate();
	// lck.unlocked();
	// } catch(SQLException e)
	// {
	// throw new PrismsRecordException(
	// "Could not unlock " + lck.theType + "/" + lck.theID, e);
	// }
	// }
	// }

	/**
	 * Checks the database for changes to this record keeper's data set and implements them if there
	 * are any
	 * 
	 * @param force Whether to force a check or to allow this record keeper to check every
	 *        {@link #getCheckInterval()} millis
	 * @return Whether the check was successful (will be true if <code>force</code> is false and the
	 *         check interval has not passed since the last actual check)
	 */
	public boolean checkChanges(boolean force)
	{
		long now = System.currentTimeMillis();
		if(!force && (now - theLastCheck) < theCheckInterval)
			return true;

		synchronized(this)
		{
			if(!force && (now - theLastCheck) < theCheckInterval)
				return true;

			for(ScaledRecordKeeper depend : theDepends)
				depend.checkChanges(true);

			Long timeBefore = Long.valueOf(theTimeBeforeCheck);
			prisms.util.LongList ids;
			try
			{
				ids = new prisms.util.LongList(execute(theChangeGetter, timeBefore, timeBefore));
			} catch(PrismsRecordException e)
			{
				log.error("Could not query for changes from scaled environment", e);
				return false;
			}
			ids.removeAll(theProcessedChanges);

			if(ids.isEmpty())
				return true;

			boolean ret = true;
			boolean success = true;
			int batchCount = 100;
			if(batchCount > ids.size())
				batchCount = ids.size();
			long [] batch = new long [batchCount];
			for(int i = 0; i < ids.size(); i += batchCount)
			{
				if(batchCount + i > ids.size())
				{
					batchCount = ids.size() - i;
					batch = new long [batchCount];
				}
				ids.arrayCopy(i, batch, 0, batch.length);
				ChangeRecord [] changes;
				try
				{
					changes = getItems(batch);
				} catch(PrismsRecordException e)
				{
					log.error("Could not get change batch", e);
					success = false;
					ret = false;
					break;
				}
				for(ChangeRecord change : changes)
				{
					if(change instanceof ChangeRecordError)
						continue;
					long [] successors = null;
					try
					{
						successors = execute(
							theSuccessorCheck,
							change.type.subjectType.name(),
							Integer.valueOf(change.type.additivity),
							Integer.valueOf(change.type.additivity),
							change.type.changeType == null ? null : change.type.changeType.name(),
							Long.valueOf(getDataID(change.majorSubject)),
							change.minorSubject == null ? null : Long
								.valueOf(getDataID(change.minorSubject)), change.data1 == null
								? null : Long.valueOf(getDataID(change.data1)),
							change.data2 == null ? null : Long.valueOf(getDataID(change.data2)),
							Long.valueOf(change.time));
					} catch(PrismsRecordException e)
					{
						log.error("Could not check for successors", e);
						ret = false;
					}
					if(successors != null && successors.length > 0)
						continue;
					Object currentValue = null;
					if(change.type.changeType != null
						&& change.type.changeType.getObjectType() != null)
						try
						{
							currentValue = theImpl.getDBCurrentValue(change);
						} catch(PrismsRecordException e)
						{
							log.error(
								"Could not retrieve current value for change from DB for change "
									+ change, e);
							ret = false;
							continue;
						}
					try
					{
						theImpl.doMemChange(change, currentValue);
					} catch(PrismsRecordException e)
					{
						log.error(
							"Could not implement change from scaled environment: "
								+ change.toString(currentValue), e);
						ret = false;
					}
				}
				if(!success)
					return ret;
				theProcessedChanges.clear();
				theProcessedChanges.addAll(ids);
				ids.clear();
				theTimeBeforeCheck = theLastCheck;
				theLastCheck = now;
			}
			return ret;
		}
	}

	private void createPreparedSearches() throws PrismsRecordException
	{
		Search srch;
		srch = new ChangeSearch.ChangeTimeSearch(Search.Operator.GTE, null);
		Search srch2 = ChangeSearch.SyncRecordSearch.forImport(Boolean.TRUE);
		srch2 = srch2.and(new ChangeSearch.SyncRecordSearch(Search.Operator.GTE, null));
		srch2 = srch2.and(ChangeSearch.SyncRecordSearch.forChangeError(Boolean.FALSE));
		srch = srch.or(srch2);
		srch = srch.and(new ChangeSearch.LocalOnlySearch(null));
		prisms.util.Sorter<ChangeField> sorter = new prisms.util.Sorter<ChangeField>();
		sorter.addSort(ChangeField.CHANGE_TIME, true);
		theChangeGetter = prepare(srch, sorter);

		srch = new ChangeSearch.ChangeFieldSearch(FieldType.major, null, false);
		srch = srch.and(new ChangeSearch.ChangeFieldSearch(FieldType.minor, null, false));
		srch = srch.and(new ChangeSearch.ChangeFieldSearch(FieldType.data1, null, false));
		srch = srch.and(new ChangeSearch.ChangeFieldSearch(FieldType.data2, null, false));
		srch = new ChangeSearch.ChangeTypeSearch(null, false).and(srch);
		srch = new ChangeSearch.AdditivitySearch(null).or(new ChangeSearch.AdditivitySearch(null))
			.and(srch);
		srch = new ChangeSearch.SubjectTypeSearch(null).and(srch);
		srch = srch.and(new ChangeSearch.ChangeTimeSearch(Search.Operator.GT, null));
		srch = srch.and(new ChangeSearch.LocalOnlySearch(null));
		theSuccessorCheck = prepare(srch, null);
	}

	private void destroyPreparedSearches() throws PrismsRecordException
	{
		destroy(theChangeGetter);
		destroy(theSuccessorCheck);
	}

	@Override
	protected void connectionUpdated()
	{
		super.connectionUpdated();
		try
		{
			createPreparedSearches();
		} catch(PrismsRecordException e)
		{
			log.error("Could not prepare statements", e);
		}
	}

	@Override
	public void disconnect()
	{
		try
		{
			destroyPreparedSearches();
		} catch(PrismsRecordException e)
		{
			log.error("Could not destroy prepared searches", e);
		}
		super.disconnect();
	}
}
