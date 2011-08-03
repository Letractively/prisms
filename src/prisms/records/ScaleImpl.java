/*
 * ScaleImpl.java Created Feb 24, 2011 by Andrew Butler, PSL
 */
package prisms.records;

/**
 * An interface with methods required by {@link ScaledRecordKeeper} to implements scaling an
 * application across multiple servers with a common database. This method has much in common with
 * {@link RecordPersister} and {@link SynchronizeImpl}, so an implementation of one or both of those
 * interfaces will have less trouble to implement this interface as well.
 */
public interface ScaleImpl
{
	/**
	 * @param item The item to get the ID of
	 * @return The item's ID
	 * @throws PrismsRecordException If an error occurs getting the information or if the item's
	 *         type is unrecognized
	 * 
	 * @see RecordPersister#getID(Object)
	 */
	long getID(Object item) throws PrismsRecordException;

	/**
	 * Gets the current value of a change record's field from the database. This differs from
	 * {@link SynchronizeImpl#getCurrentValue(ChangeRecord)} in that that method may return the
	 * value from a memory representation. This method <i>must</i> retrieve the value from the
	 * database since it may have changed due to a different server writing the data.
	 * 
	 * @param record The change record to get the field value of
	 * @return The current field value of the given change record's field in the database
	 * @throws PrismsRecordException If an error occurs retrieving the change
	 */
	Object getDBCurrentValue(ChangeRecord record) throws PrismsRecordException;

	/**
	 * Performs a change to synchronize the data set's local memory representation with the current
	 * databased set. This change should be propagated <b>in memory ONLY</b>. No implementation data
	 * or change records should be written to the database as a direct result of this call.
	 * 
	 * @param record The change record to perform
	 * @param currentValue The new value of the change's field
	 * @throws PrismsRecordException If an error occurs performing the change
	 */
	void doMemChange(ChangeRecord record, Object currentValue) throws PrismsRecordException;
}
