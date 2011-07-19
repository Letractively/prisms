/*
 * SyncRequest.java Created Jul 14, 2011 by Andrew Butler, PSL
 */
package prisms.records;

/** Represents a client's request for data to synchronize itself with the local data center */
public class SyncRequest
{
	private final PrismsCenter theRequestingCenter;

	private final SyncRecord.Type theSyncType;

	private LatestCenterChange [] theLatestChanges;

	private String theVersion;

	private boolean isWithRecords;

	private boolean shouldStoreSyncRecord;

	/**
	 * Creates a synchronization request
	 * 
	 * @param center The center requesting the synchronization
	 * @param type The type of synchronization requested
	 * @param changes The latest changes by center for the client
	 * @param version The latest version of synchronization implementation available on the client
	 */
	public SyncRequest(PrismsCenter center, SyncRecord.Type type, LatestCenterChange [] changes,
		String version)
	{
		theRequestingCenter = center;
		theSyncType = type;
		theLatestChanges = changes;
		theVersion = version;
	}

	/**
	 * @param withRecords Whether the remote center is interested in the integrity of their
	 *        record-keeping even to the detriment of synchronization performance
	 */
	public void setWithRecords(boolean withRecords)
	{
		isWithRecords = withRecords;
	}

	/** @param storeSyncRecord Whether to store the sync record from this synchronization */
	public void setStoreSyncRecord(boolean storeSyncRecord)
	{
		shouldStoreSyncRecord = storeSyncRecord;
	}

	/** @return The center requesting the synchronization */
	public PrismsCenter getRequestingCenter()
	{
		return theRequestingCenter;
	}

	/** @return The type of synchronization requested */
	public SyncRecord.Type getSyncType()
	{
		return theSyncType;
	}

	/** @return The latest changes by center for the client */
	public LatestCenterChange [] getLatestChanges()
	{
		return theLatestChanges;
	}

	/** @return The latest version of synchronization implementation available on the client */
	public String getVersion()
	{
		return theVersion;
	}

	/**
	 * @return Whether the remote center is interested in the integrity of their record-keeping even
	 *         to the detriment of synchronization performance
	 */
	public boolean isWithRecords()
	{
		return isWithRecords;
	}

	/** @return Whether to store the sync record from this synchronization */
	public boolean shouldStoreSyncRecord()
	{
		return shouldStoreSyncRecord;
	}
}
