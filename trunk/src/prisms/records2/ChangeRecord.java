/*
 * PrismsRecord.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records2;

/**
 * A record of a change to an object or a relationship
 */
public class ChangeRecord
{
	/**
	 * The change's database ID
	 */
	public final long id;

	/**
	 * The time at which this change was made
	 */
	public final long time;

	/**
	 * The user that caused this change
	 */
	public final RecordUser user;

	/**
	 * The type of this change record
	 */
	public final RecordType type;

	/**
	 * The major subject of the change--the main object on which the change occurred. Typically but
	 * not always, the major subject is the only object whose value and properties are actually
	 * affected by the change.
	 */
	public final Object majorSubject;

	/**
	 * The minor subject of the change. The minor subject is present when this change represents a
	 * change to a relationship between two objects, specifically the major subject and the minor
	 * subject. The minor subject's value is typically not altered. This type is a type whose
	 * changes are themselves being recorded.
	 */
	public final Object minorSubject;

	/**
	 * The value that was replaced in the major subject or its relationship to the minor subject.
	 * May be null if:
	 * <ul>
	 * <li>This change is not a field modification</li>
	 * <li>The previous value of the field was null</li>
	 * </ul>
	 */
	public final Object previousValue;

	/**
	 * A data object connected to the change record.
	 */
	public final Object data1;

	/**
	 * Another data object connected to the change record.
	 */
	public final Object data2;

	/**
	 * Creates a change record
	 * 
	 * @param _id The database ID of the change
	 * @param _time The time of the change
	 * @param _user The user that caused the change
	 * @param _subjectType The type of the subject of this change
	 * @param _changeType The subtype of the change
	 * @param _additivity The additivity of the change (See {@link RecordType#additivity})
	 * @param _majorSubject The major subject of the change (See {@link #majorSubject})
	 * @param _minorSubject The minor subject of the change (See {@link #minorSubject})
	 * @param _previousValue The previous value of the field before this change
	 * @param _data1 Metadata to attach to the change record
	 * @param _data2 More metadata to attach to the change record
	 */
	public ChangeRecord(long _id, long _time, RecordUser _user, SubjectType _subjectType,
		ChangeType _changeType, int _additivity, Object _majorSubject, Object _minorSubject,
		Object _previousValue, Object _data1, Object _data2)
	{
		id = _id;
		type = new RecordType(_subjectType, _changeType, _additivity);
		time = _time;
		user = _user;
		majorSubject = _majorSubject;
		minorSubject = _minorSubject;
		previousValue = _previousValue;
		data1 = _data1;
		data2 = _data2;
		if(!type.subjectType.getMajorType().isInstance(majorSubject))
			throw new IllegalArgumentException("Major subject " + majorSubject
				+ " is not valid for subject type " + type.subjectType);
		if(type.subjectType.getMetadataType1() != null)
		{
			if(!type.subjectType.getMetadataType1().isInstance(data1))
				throw new IllegalArgumentException("Metadata " + majorSubject
					+ " is not valid for first metadata of subject type " + type.subjectType);
		}
		else if(data1 != null)
			throw new IllegalArgumentException("Subject type " + type.subjectType
				+ " does not allow metadata");
		if(type.subjectType.getMetadataType2() != null)
		{
			if(!type.subjectType.getMetadataType2().isInstance(data2))
				throw new IllegalArgumentException("Metadata " + majorSubject
					+ " is not valid for second metadata of subject type " + type.subjectType);
		}
		else if(data2 != null)
			throw new IllegalArgumentException("Subject type " + type.subjectType
				+ " does not allow 2 metadata objects");
		if(type.additivity == 0 && type.changeType == null)
			throw new IllegalArgumentException(
				"A change type must be specified for a modification (additivity=0)");
		if(type.changeType != null && type.changeType.getMinorType() != null)
		{
			if(!type.changeType.getMinorType().isInstance(minorSubject))
				throw new IllegalArgumentException("Minor subject " + minorSubject
					+ " is not valid for change type " + type.changeType);
		}
		else if(minorSubject != null)
			throw new IllegalArgumentException("Change type " + type.changeType
				+ " does not allow a minor subject");
		if(previousValue != null)
		{
			if(!type.changeType.getObjectType().isInstance(previousValue))
				throw new IllegalArgumentException("Previous value (" + previousValue
					+ ") is not valid for change type " + type.changeType + ": Not an instance of "
					+ type.changeType.getObjectType().getName());
		}
	}

	public String toString()
	{
		if(type.changeType == null)
		{
			if(type.additivity > 0)
				return RecordType.prettify(type.subjectType.toString()) + " " + majorSubject
					+ " created";
			else
				return RecordType.prettify(type.subjectType.toString()) + " " + majorSubject
					+ " deleted";
		}
		else
			return type.changeType.toString(type.additivity, majorSubject, minorSubject);
	}

	/**
	 * Prints this modification out with context
	 * 
	 * @param postValue The value of the field after this change, or null if this is not a field
	 *        modification
	 * @return A string representing the change
	 */
	public String toString(Object postValue)
	{
		if(type.changeType == null)
			return toString();
		else
			return type.changeType.toString(type.additivity, majorSubject, minorSubject,
				previousValue, postValue);
	}
}
