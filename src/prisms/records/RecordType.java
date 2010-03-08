/*
 * RecordType.java Created Mar 4, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/**
 * A compound type describing what a change record represents
 */
public class RecordType
{
	/**
	 * The type of subject that this change type effects
	 */
	public final SubjectType subjectType;

	/**
	 * The type of change to the subject that this represents
	 */
	public final ChangeType changeType;

	/**
	 * <ul>
	 * <li><b>1</b> If this change represents the addition of new data to the data set</li>
	 * <li><b>-1</b> If this change represents the removal of data from the data set</li>
	 * <li><b>0</b> If this change represents the modification of extant data in the data set</li>
	 * </ul>
	 */
	public final int additivity;

	/**
	 * Creates a record type
	 * 
	 * @param st See #subjectType
	 * @param ct See #changeType
	 * @param add See #additivity
	 */
	public RecordType(SubjectType st, ChangeType ct, int add)
	{
		subjectType = st;
		changeType = ct;
		additivity = add;
		if(!subjectType.getChangeTypes().isInstance(changeType))
			throw new IllegalArgumentException("Change type " + changeType
				+ " is not valid for subject type " + subjectType);
	}

	public boolean equals(Object o)
	{
		if(!(o instanceof RecordType))
			return false;
		RecordType rt = (RecordType) o;
		return subjectType.equals(rt.subjectType)
			&& (changeType == null ? rt.changeType == null : changeType.equals(rt.changeType))
			&& additivity == rt.additivity;
	}
}
