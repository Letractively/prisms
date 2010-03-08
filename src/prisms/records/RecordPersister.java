/*
 * RecordPersister.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

public interface RecordPersister
{
	public static final class ChangeData
	{
		public Object majorSubject;

		public Object minorSubject;

		public Object data1;

		public Object data2;

		public Object preValue;

		public ChangeData()
		{
		}

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

	SubjectType getType(String typeName) throws PrismsRecordException;

	SubjectType [] getHistoryDomains(Object value) throws PrismsRecordException;

	RecordUser getUser(long id) throws PrismsRecordException;

	long getID(Object dbObject) throws PrismsRecordException;

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
}
