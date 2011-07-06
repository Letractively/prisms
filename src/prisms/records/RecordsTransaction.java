/*
 * RecordsTransaction.java Created Sep 28, 2009 by Andrew Butler, PSL
 */
package prisms.records;

/** Contains metadata that may be used by a data data source for determining how to persist records */
public class RecordsTransaction
{
	private final RecordUser theUser;

	private long theTime;

	private boolean isMemoryOnly;

	private boolean isWithRecords;

	private prisms.records.SyncRecord theRecord;

	/**
	 * Creates a memory-only transaction. Transactions of this type write nothing to the database,
	 * but they do modify internal caches.
	 */
	public RecordsTransaction()
	{
		this(null, false);
		isMemoryOnly = true;
	}

	/**
	 * Creates a normal transaction that will record changes
	 * 
	 * @param user The user that is attempting to access or modify the data source
	 */
	public RecordsTransaction(RecordUser user)
	{
		this(user, true);
	}

	/**
	 * Creates a transaction to be recorded under a specific sync record
	 * 
	 * @param user The user that is attempting to access or modify the data source
	 * @param record The sync record to associate with the change record
	 */
	public RecordsTransaction(RecordUser user, prisms.records.SyncRecord record)
	{
		theUser = user;
		theRecord = record;
	}

	/**
	 * Creates a transaction that will write data to the database, but will not record the changes
	 * 
	 * @param user The user that is attempting to access or modify the data source
	 * @param withRecords Whether the change should be recorded
	 */
	public RecordsTransaction(RecordUser user, boolean withRecords)
	{
		theUser = user;
		isWithRecords = withRecords;
	}

	/** @return The user that is attempting to access or modify the data source */
	public RecordUser getUser()
	{
		return theUser;
	}

	/**
	 * @return Whether or not this transaction is for a memory-only change that writes nothing to
	 *         the database
	 */
	public boolean isMemoryOnly()
	{
		return isMemoryOnly;
	}

	/** @return Whether the change should be recorded */
	public boolean shouldRecord()
	{
		return !isMemoryOnly && isWithRecords;
	}

	/** @return The sync record to assocate with the change record */
	public prisms.records.SyncRecord getRecord()
	{
		return theRecord;
	}

	/** @return The time at which this transaction was activated */
	public long getTime()
	{
		return theTime;
	}

	/** Activates this transaction, setting its time */
	public void setTime()
	{
		theTime = System.currentTimeMillis();
	}
}
