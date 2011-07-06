/*
 * SyncRecord.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/**
 * Represents either an attempt by this data center to synchronize with another (import), or an
 * attempt by another center to synchronize with this center (export).
 */
public class SyncRecord
{
	/** The type of the synchronization attempt */
	public static enum Type
	{
		/**
		 * Represents a synchronization that occurred automatically as a result of the
		 * {@link PrismsCenter#getServerSyncFrequency()} setting
		 */
		AUTOMATIC("Automatic"),
		/**
		 * Represents a synchronization that occurred as a result of the user clicking the
		 * "Sync Now" link
		 */
		MANUAL_REMOTE("Manual Remote"),
		/**
		 * Represents a synchronization that occurred as a result of the user importing or exporting
		 * a synchronization file.
		 */
		FILE("File");

		private final String theDisplay;

		Type(String display)
		{
			theDisplay = display;
		}

		/**
		 * @param name The name of the type to get
		 * @return The type of the given name, or null if no such type exists
		 */
		public static Type byName(String name)
		{
			for(Type t : values())
				if(t.theDisplay.equals(name))
					return t;
			return null;
		}

		@Override
		public String toString()
		{
			return theDisplay;
		}
	}

	private int theID;

	private int theParallelID;

	private PrismsCenter theCenter;

	private Type theSyncType;

	private long theSyncTime;

	private boolean isImport;

	private String theSyncError;

	/**
	 * Creates a sync record
	 * 
	 * @param id The database ID for the sync record
	 * @param center The other center involved in the synchronization (besides the local center)
	 * @param syncType The type of synchronization attempted
	 * @param syncTime The time at which the synchronization was attempted
	 * @param _isImport Whether this synchronization is an import synchronization (the local center
	 *        attempting to synchronize with another center) or an export synchronization (another
	 *        center attempting to synchronize with this center)
	 */
	public SyncRecord(int id, PrismsCenter center, Type syncType, long syncTime, boolean _isImport)
	{
		theID = id;
		theParallelID = -1;
		theCenter = center;
		theSyncType = syncType;
		theSyncTime = syncTime;
		isImport = _isImport;
	}

	/**
	 * Creates a sync record
	 * 
	 * @param center The other center involved in the synchronization (besides the local center)
	 * @param syncType The type of synchronization attempted
	 * @param syncTime The time at which the synchronization was attempted
	 * @param _isImport Whether this synchronization is an import synchronization (the local center
	 *        attempting to synchronize with another center) or an export synchronization (another
	 *        center attempting to synchronize with this center)
	 */
	public SyncRecord(PrismsCenter center, Type syncType, long syncTime, boolean _isImport)
	{
		this(-1, center, syncType, syncTime, _isImport);
	}

	/** @return This sync record's local database ID */
	public int getID()
	{
		return theID;
	}

	/** @param id The local database ID for this sync record */
	public void setID(int id)
	{
		theID = id;
	}

	/**
	 * @return The ID of the synchronization record on the other center that represents the same
	 *         synchronization as this record.
	 */
	public int getParallelID()
	{
		return theParallelID;
	}

	/**
	 * @param id The ID of the synchronization record on the other center that represents the same
	 *        synchronization as this record.
	 */
	public void setParallelID(int id)
	{
		theParallelID = id;
	}

	/** @return The other center (besides the local center) involved in this synchronization attempt */
	public PrismsCenter getCenter()
	{
		return theCenter;
	}

	/** @return The type of synchronization that was attempted */
	public Type getSyncType()
	{
		return theSyncType;
	}

	/** @param type The type of synchronization that was attempted */
	public void setSyncType(Type type)
	{
		theSyncType = type;
	}

	/**
	 * @return The time of the synchronization that was attempted. This may be the time sent with
	 *         the web-service synchronization request or the time that the synchronization file was
	 *         generated.
	 */
	public long getSyncTime()
	{
		return theSyncTime;
	}

	/**
	 * @param time The time of the synchronization that was attempted. This may be the time sent
	 *        with the web-service synchronization request or the time that the synchronization file
	 *        was generated.
	 */
	public void setSyncTime(long time)
	{
		theSyncTime = time;
	}

	/**
	 * @return Whether this synchronization is an import synchronization (the local center
	 *         attempting to synchronize with another center) or an export synchronization (another
	 *         center attempting to synchronize with this center)
	 */
	public boolean isImport()
	{
		return isImport;
	}

	/**
	 * @param importSync Whether this synchronization is an import synchronization (the local center
	 *        attempting to synchronize with another center) or an export synchronization (another
	 *        center attempting to synchronize with this center)
	 */
	public void setImport(boolean importSync)
	{
		isImport = importSync;
	}

	/**
	 * @return null if this synchronization attempt was successful; otherwise this will be the error
	 *         that caused the synchronization attempt to fail.
	 */
	public String getSyncError()
	{
		return theSyncError;
	}

	/**
	 * @param error null if this synchronization attempt was successful; otherwise this will be the
	 *        error that caused the synchronization attempt to fail.
	 */
	public void setSyncError(String error)
	{
		theSyncError = error;
	}

	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof SyncRecord))
			return false;
		return theID == ((SyncRecord) o).theID;
	}

	@Override
	public int hashCode()
	{
		assert false : "hashCode not designed";
		return 42; // any arbitrary constant will do
	}

	@Override
	public String toString()
	{
		return theSyncType + (isImport ? " import from " : " export to ") + theCenter + " at "
			+ prisms.util.PrismsUtils.print(theSyncTime);
	}
}
