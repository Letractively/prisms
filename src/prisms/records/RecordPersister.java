/*
 * RecordPersister.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/**
 * Allows the PRISMS RecordKeeper to interface with implementation-specific data
 */
public interface RecordPersister
{
	/**
	 * Holds all implementation-specific data in a change record
	 */
	public static final class ChangeData
	{
		/**
		 * The major subject of the change
		 */
		public Object majorSubject;

		/**
		 * The minor subject of the change
		 */
		public Object minorSubject;

		/**
		 * The first metadata of the change
		 */
		public Object data1;

		/**
		 * The second metadata of the change
		 */
		public Object data2;

		/**
		 * The previous value in the change
		 */
		public Object preValue;

		/**
		 * Creates an empty ChangeData
		 */
		public ChangeData()
		{
		}

		/**
		 * Creates a filled-out ChangeData
		 * 
		 * @param majS The major subject for the change
		 * @param minS The minor subject for the change
		 * @param md1 The first metadata for the change
		 * @param md2 The second metadata for the change
		 * @param pv The previous value for the change
		 */
		public ChangeData(Object majS, Object minS, Object md1, Object md2, Object pv)
		{
			this();
			majorSubject = majS;
			minorSubject = minS;
			data1 = md1;
			data2 = md2;
			preValue = pv;
		}
	}

	// The following methods are common with Synchronize2Impl

	/**
	 * Gets a record user by ID
	 * 
	 * @param id The ID of the user
	 * @return The user with the given ID
	 * @throws PrismsRecordException If no such user exists or an error occurs retriving the user
	 */
	RecordUser getUser(long id) throws PrismsRecordException;

	/**
	 * Gets a subject type by name
	 * 
	 * @param typeName The name of the subject type to get (see {@link SubjectType#name()})
	 * @return The subject type with the given name
	 * @throws PrismsRecordException If no such subject type exists or another error occurs
	 */
	SubjectType getSubjectType(String typeName) throws PrismsRecordException;

	/**
	 * Gets the database ID for a value
	 * 
	 * @param item The item to get the persistence ID for
	 * @return The ID to identify the object in a change record
	 */
	long getID(Object item);

	/**
	 * Retrieves the values associated with a set of IDs. Each parameter may be the already-parsed
	 * value (which may be simply returned in the ChangeData return), the identifier for the value,
	 * the serialized value (only in the case of the preValue), or null (except in the case of the
	 * major subject).
	 * 
	 * @param subjectType The subject type of the change record
	 * @param changeType The type of the change
	 * @param majorSubject The major subject or the ID of the major subject to retrieve
	 * @param minorSubject The minor subject or the ID of the minor subject to retrieve. May be
	 *        null.
	 * @param data1 The first metadata or the ID of the first metadata to retrieve. May be null.
	 * @param data2 The second metadata or the ID of the second metadata to retrieve. May be null.
	 * @param preValue The previous value (serialized, a string) or the ID of the previous value in
	 *        the change to retrieve. May be null.
	 * @return The ChangeData containing the values of each non-null ID.
	 * @throws PrismsRecordException If an error occurs retrieving any of the data.
	 */
	ChangeData getData(SubjectType subjectType, ChangeType changeType, Object majorSubject,
		Object minorSubject, Object data1, Object data2, Object preValue)
		throws PrismsRecordException;

	// End common methods

	/**
	 * @return All domains that this persister understands
	 * @throws PrismsRecordException If an error occurs getting the data
	 */
	SubjectType [] getAllSubjectTypes() throws PrismsRecordException;

	/**
	 * Gets all domains that qualify as change history for the given value's type
	 * 
	 * @param value The value to get the history of
	 * @return All domains that record history of the type of the value
	 * @throws PrismsRecordException If an error occurs getting the data
	 */
	SubjectType [] getHistoryDomains(Object value) throws PrismsRecordException;

	/**
	 * Serializes a change's object to a string. This method will only be called for change objects
	 * (previous values) if the change's ChangeType reports false for
	 * {@link ChangeType#isObjectIdentifiable()}. This method will not be called for instances of
	 * Boolean, Integer, Long, Float, Double, or String. Serialization of these types is handled
	 * internally.
	 * 
	 * @param change The change to serialize the previous value of
	 * @return The serialized object
	 * @throws PrismsRecordException If an error occurs serializing the object
	 */
	String serializePreValue(ChangeRecord change) throws PrismsRecordException;

	/**
	 * Called when a modification is purged that is the last link to an item in the records. The
	 * persister now has an opportunity to purge the item itself if desired.
	 * 
	 * @param item The item with no more records
	 * @param stmt A database statement to make deletion easier
	 * @throws PrismsRecordException If an error occurs purging the data
	 */
	void checkItemForDelete(Object item, java.sql.Statement stmt) throws PrismsRecordException;
}
