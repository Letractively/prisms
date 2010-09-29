/*
 * MinorSubjectType.java Created Mar 3, 2010 by Andrew Butler, PSL
 */
package prisms.records2;

/**
 * Represents a change that can happen to a subject
 */
public interface ChangeType
{
	/**
	 * @return The databased name of the change type
	 */
	String name();

	/**
	 * @return The type of the minorSubject for changes of this type
	 */
	Class<?> getMinorType();

	/**
	 * @return The object type of this change type
	 */
	Class<?> getObjectType();

	/**
	 * @return Whether the object type should be stored by an integer (long) identifer as opposed to
	 *         a serialized string
	 */
	boolean isObjectIdentifiable();

	/**
	 * @param additivity The additivity of the change
	 * @return A display string for the change type, or null if the change is illegal
	 */
	String toString(int additivity);

	/**
	 * Prints this change type with some context
	 * 
	 * @param additivity The additivity of the change
	 * @param majorSubject The major subject that was changed
	 * @param minorSubject The minor subject in the change
	 * @return A string representing the change
	 */
	String toString(int additivity, Object majorSubject, Object minorSubject);

	/**
	 * Prints this change type with complete context
	 * 
	 * @param additivity The additivity of the change
	 * @param majorSubject The major subject that was changed
	 * @param minorSubject The minor subject in the change
	 * @param before The field value before the change
	 * @param after The field value after the change
	 * @return A string representing the change
	 */
	String toString(int additivity, Object majorSubject, Object minorSubject, Object before,
		Object after);
}
