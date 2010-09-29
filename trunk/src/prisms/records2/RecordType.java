/*
 * RecordType.java Created Mar 4, 2010 by Andrew Butler, PSL
 */
package prisms.records2;


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
		if(changeType != null && subjectType.getChangeTypes() != null
			&& !subjectType.getChangeTypes().isInstance(changeType))
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

	public String toString()
	{
		return recordTypeString(subjectType, changeType, additivity);
	}

	/**
	 * @param subjectType The subject type to represent
	 * @param changeType The change type to represent
	 * @param additivity The additivity of the change to represent
	 * @return A string representation of the change type
	 */
	public static String recordTypeString(SubjectType subjectType, ChangeType changeType,
		int additivity)
	{
		if(changeType == null)
		{
			if(additivity > 0)
				return prettify(subjectType.toString()) + " Created";
			else
				return prettify(subjectType.toString()) + " Deleted";
		}
		else
			return changeType.toString(additivity);
	}

	/**
	 * @param label The camel-case label, e.g. "assetRuleChanged"
	 * @return The user-displayable label, e.g. "Asset Rule Changed"
	 */
	public static final String prettify(String label)
	{
		StringBuilder ret = new StringBuilder();
		for(int c = 0; c < label.length(); c++)
		{
			if(c == 0)
				ret.append(upper(label.charAt(c)));
			else if(isUpper(label.charAt(c)))
			{
				ret.append(' ');
				ret.append(label.charAt(c));
			}
			else
				ret.append(label.charAt(c));
		}
		return ret.toString();
	}

	static final char upper(char c)
	{
		if(isUpper(c))
			return c;
		return (char) (c + 'A' - 'a');
	}

	static final boolean isUpper(char c)
	{
		return c >= 'A' && c <= 'Z';
	}
}
