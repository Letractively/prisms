/*
 * MinorSubjectType.java Created Mar 3, 2010 by Andrew Butler, PSL
 */
package prisms.records;

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
}
