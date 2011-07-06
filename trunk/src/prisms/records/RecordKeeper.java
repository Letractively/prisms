/*
 * RecordKeeper.java Created Jul 30, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/** Keeps track of changes for record-keeping or synchronization purposes */
public interface RecordKeeper extends
	prisms.util.SearchableAPI<ChangeRecord, RecordKeeper.ChangeField, PrismsRecordException>
{
	/** Fields on which the history may be sorted */
	public static enum ChangeField implements prisms.util.Sorter.Field
	{
		/** Sort on the subject type */
		CHANGE_TYPE("type"),
		/** Sort on the change time */
		CHANGE_TIME("changeTime"),
		/** Sort on the user that made the change */
		CHANGE_USER("changeUser");

		private final String theDBValue;

		ChangeField(String dbValue)
		{
			theDBValue = dbValue;
		}

		@Override
		public String toString()
		{
			return theDBValue;
		}
	}

	/** @return This database's identifier */
	int getCenterID();

	/**
	 * @return The local center's synchronization priority
	 * @see PrismsCenter#getPriority()
	 */
	int getLocalPriority();

	/**
	 * @return All non-deleted external centers known in this namespace
	 * @throws PrismsRecordException If an error occurs retrieving the centers
	 */
	PrismsCenter [] getCenters() throws PrismsRecordException;

	/**
	 * Adds a new center to the record keeper or updates an existing center
	 * 
	 * @param center The center to add or update
	 * @param trans The transaction to record the change with
	 * @throws PrismsRecordException If an error occurs persisting the data
	 */
	void putCenter(PrismsCenter center, RecordsTransaction trans) throws PrismsRecordException;

	/**
	 * Deletes a center
	 * 
	 * @param center The center to delete
	 * @param trans The transaction to record the change with
	 * @throws PrismsRecordException If an error occurs deleting the center
	 */
	void removeCenter(PrismsCenter center, RecordsTransaction trans) throws PrismsRecordException;

	/**
	 * @return All center IDs that have caused changes contained in this record keeper
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	int [] getAllCenterIDs() throws PrismsRecordException;

	/**
	 * @param centerID The center ID to get the time of the last change by
	 * @param subjectCenter The center ID of the data set to get the last change to
	 * @return The time of the latest change to the given data set by the given center
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	long getLatestChange(int centerID, int subjectCenter) throws PrismsRecordException;

	/**
	 * Gets the synchronization records for a center
	 * 
	 * @param center The center to get synchronization records for
	 * @param isImport true if this method should only get import records, false if it should only
	 *        get export records, null if it should get both types
	 * @return The requested synchronization records, sorted by time, most recent first
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	SyncRecord [] getSyncRecords(PrismsCenter center, Boolean isImport)
		throws PrismsRecordException;

	/**
	 * Puts a new synchronization record in the database or updates an existing record
	 * 
	 * @param record The synchronization record to add or update
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	void putSyncRecord(SyncRecord record) throws PrismsRecordException;

	/**
	 * Removes a synchronization record from the database
	 * 
	 * @param record The record to remove
	 * @throws PrismsRecordException If an error occurs deleting the record
	 */
	void removeSyncRecord(SyncRecord record) throws PrismsRecordException;

	/**
	 * @param changeID The ID of the center whose data set was modified
	 * @return The center ID of the data set that was modified by a change
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	int getSubjectCenter(long changeID) throws PrismsRecordException;

	/**
	 * Creates a search that retrieves the entire history of an item
	 * 
	 * @param historyItem The item to get the history of
	 * @return The search
	 * @throws PrismsRecordException If an error occurs creating the search
	 */
	prisms.util.Search getHistorySearch(Object historyItem) throws PrismsRecordException;

	/**
	 * Creates a search that will retrieve all changes to the same data as the given change, but
	 * occur after it.
	 * 
	 * @param change The change to get the successors of
	 * @return The search
	 * @throws PrismsRecordException If an error occurs creating the search
	 */
	prisms.util.Search getSuccessorSearch(ChangeRecord change) throws PrismsRecordException;

	/**
	 * @param changeID The ID of the change to check
	 * @return Whether the given change is already recorded in this record keeper
	 * @throws PrismsRecordException If an error occurs checking the existence of the change
	 */
	boolean hasChange(long changeID) throws PrismsRecordException;

	/**
	 * @param changeID The ID of the change to check
	 * @return Whether the given change has been successfully made in this record keeper
	 * @throws PrismsRecordException If an error occurs checking the existence or success of the
	 *         change
	 */
	boolean hasSuccessfulChange(long changeID) throws PrismsRecordException;

	/**
	 * Sorts changes by the time they were effected
	 * 
	 * @param changeIDs The ids of the changes to sort
	 * @param ascending Whether to sort the change IDs by ascending time (oldest first) or
	 *        descending
	 * @return The sorted change IDs
	 * @throws PrismsRecordException If an error occurs accessing the data
	 */
	long [] sortChangeIDs(long [] changeIDs, boolean ascending) throws PrismsRecordException;

	/**
	 * Persists a change
	 * 
	 * @param trans The transaction that represents the source of this change
	 * @param user The user that caused the change
	 * @param subjectType The subject type of the change
	 * @param changeType The type of the change to the subject
	 * @param additivity The additivity of the change
	 * @param majorSubject The major component of the subject
	 * @param minorSubject The minor component of the subject
	 * @param previousValue The value that was set for the field before this change
	 * @param data1 The first item of metadata
	 * @param data2 The second item of metadata
	 * @param localOnly Whether the change is local only, that is, not required for synchronization
	 * @return The change record that was created
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	ChangeRecord persist(RecordsTransaction trans, SubjectType subjectType, ChangeType changeType,
		int additivity, Object majorSubject, Object minorSubject, Object previousValue,
		Object data1, Object data2) throws PrismsRecordException;

	/**
	 * Persists a change that already has an assigned ID
	 * 
	 * @param record The record of the change
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	void persist(ChangeRecord record) throws PrismsRecordException;

	/**
	 * Associates a change with a synchronization record. For use when the change is already in the
	 * database when the synchronization attempt occurs.
	 * 
	 * @param change The already-existent change to associate with the sync record
	 * @param syncRecord The synchronization record to associate the change with
	 * @param error True if there was an error sending or interpreting the change
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	void associate(ChangeRecord change, SyncRecord syncRecord, boolean error)
		throws PrismsRecordException;

	/**
	 * @param centerID The ID to get the change by
	 * @param subjectCenter The ID of the center whose data set was modified
	 * @return The time of the most recent change caused by the given center that was purged from
	 *         this keeper
	 * @throws PrismsRecordException
	 */
	long getLatestPurgedChange(int centerID, int subjectCenter) throws PrismsRecordException;

	/**
	 * Sets the latest change time for the given center if the current latest change is too old.
	 * This call will have no effect if {@link #getLatestChange(int, int)} for centerID and
	 * subjectCenter is greater than or equal to the time argument.
	 * 
	 * @param centerID The ID of the center to set the latest change time by
	 * @param subjectCenter The ID of the center whose data set was modified
	 * @param time The latest change time for the center
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	void setLatestChange(int centerID, int subjectCenter, long time) throws PrismsRecordException;

	/** Frees all of this record keeper's resources */
	void disconnect();
}
