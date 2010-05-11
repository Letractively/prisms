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

	/**
	 * Gets a subject type by name
	 * 
	 * @param typeName The name of the subject type to get (see {@link SubjectType#name()})
	 * @return The subject type with the given name
	 * @throws PrismsRecordException If no such subject type exists or another error occurs
	 */
	SubjectType getType(String typeName) throws PrismsRecordException;

	/**
	 * @return All domains that this persister understands
	 * @throws PrismsRecordException If an error occurs getting the data
	 */
	SubjectType [] getAllDomains() throws PrismsRecordException;

	/**
	 * Gets all domains that qualify as change history for the given value's type
	 * 
	 * @param value The value to get the history of
	 * @return All domains that record history of the type of the value
	 * @throws PrismsRecordException If an error occurs getting the data
	 */
	SubjectType [] getHistoryDomains(Object value) throws PrismsRecordException;

	/**
	 * Gets a record user by ID
	 * 
	 * @param id The ID of the user
	 * @return The user with the given ID
	 * @throws PrismsRecordException If no such user exists or an error occurs retriving the user
	 */
	RecordUser getUser(long id) throws PrismsRecordException;

	/**
	 * Gets the database ID for a value
	 * 
	 * @param dbObject The object to get the persistence ID for
	 * @return The ID to identify the object in a change record
	 * @throws PrismsRecordException If an error occurs deriving the ID
	 */
	long getID(Object dbObject) throws PrismsRecordException;

	/**
	 * Retrieves the values associated with a set of IDs
	 * 
	 * @param subjectType The subject type of the change record
	 * @param changeType The type of the change
	 * @param majorSubjectID The ID of the major subject to retrieve
	 * @param minorSubjectID The ID of the minor subject to retrieve. May be null.
	 * @param data1ID The ID of the first metadata to retrieve. May be null.
	 * @param data2ID The ID of the second metadata to retrieve. May be null.
	 * @param preValueID The ID of the previous value in the change to retrieve. May be null.
	 * @return The ChangeData containing the values of each non-null ID.
	 * @throws PrismsRecordException If an error occurs retrieving any of the data.
	 */
	ChangeData getData(SubjectType subjectType, ChangeType changeType, long majorSubjectID,
		Number minorSubjectID, Number data1ID, Number data2ID, Number preValueID)
		throws PrismsRecordException;

	/**
	 * Serializes a change's object to a string. This method will only be called if the change's
	 * ChangeType reports true for {@link ChangeType#isObjectIdentifiable()}. This method will not
	 * be called for instances of Boolean, Integer, Long, Float, Double, or String. Serialization of
	 * these types is handled internally.
	 * 
	 * @param data The change object to serialize
	 * @return The serialized object
	 * @throws PrismsRecordException If an error occurs serializing the object
	 */
	String serialize(Object data) throws PrismsRecordException;

	/**
	 * Serializes a change's object from a string. This method will only be called if the change's
	 * ChangeType reports true for {@link ChangeType#isObjectIdentifiable()}. This method will not
	 * be called for types Boolean, Integer, Long, Float, Double, or String. Deserialization of
	 * these types is handled internally.
	 * 
	 * @param type The type of the object to deserialize
	 * @param serialized The serialized object
	 * @return The deserialized object
	 * @throws PrismsRecordException If an error occurs deserializing the object
	 */
	Object deserialize(Class<?> type, String serialized) throws PrismsRecordException;

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
