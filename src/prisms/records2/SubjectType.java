/*
 * RecordDomain.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records2;

/**
 * A type of change to persist
 */
public interface SubjectType
{
	/**
	 * @return The databased name of this subject type
	 */
	String name();

	/**
	 * @return The type of the major subject in changes of this type
	 */
	Class<?> getMajorType();

	/**
	 * @return The type of the first metadata object in changes of this type
	 */
	Class<?> getMetadataType1();

	/**
	 * @return The type of the second metadata object in changes of this type
	 */
	Class<?> getMetadataType2();

	/**
	 * @return An enumeration of change types possible for this subject type
	 */
	Class<? extends Enum<? extends ChangeType>> getChangeTypes();
}
