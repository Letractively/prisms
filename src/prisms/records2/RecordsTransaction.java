/*
 * RulesTransaction.java Created Sep 28, 2009 by Andrew Butler, PSL
 */
package prisms.records2;

/** Contains metadata that may be used by a data data source for determining how to persist records */
public class RecordsTransaction
{
	private final RecordUser theUser;

	private long theTime;

	private boolean thePersist;

	private prisms.records2.SyncRecord theRecord;

	/**
	 * Creates a transaction
	 * 
	 * @param user The user that is attempting to access or modify the rules data source
	 */
	public RecordsTransaction(RecordUser user)
	{
		this(user, true);
	}

	/**
	 * Creates a transaction
	 * 
	 * @param user The user that is attempting to access or modify the rules data source
	 * @param record The sync record to associate with the change record
	 */
	public RecordsTransaction(RecordUser user, prisms.records2.SyncRecord record)
	{
		theUser = user;
		theRecord = record;
	}

	/**
	 * @param user The user that is attempting to access or modify the rules data source
	 * @param persist Whether the change should be recorded
	 */
	public RecordsTransaction(RecordUser user, boolean persist)
	{
		theUser = user;
		thePersist = persist;
	}

	/** @return The user that is attempting to access or modify the rules data source */
	public RecordUser getUser()
	{
		return theUser;
	}

	/** @return Whether the change should be recorded */
	public boolean shouldPersist()
	{
		return thePersist;
	}

	/** @return The sync record to assocate with the change record */
	public prisms.records2.SyncRecord getRecord()
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
