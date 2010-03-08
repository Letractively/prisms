/*
 * RecordUser.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/**
 * A user that may make changes to objects whose records are being kept by PRISMS
 */
public interface RecordUser
{
	/**
	 * @return The user's database ID
	 */
	long getID();

	/**
	 * @return The user's name for display
	 */
	String getName();
}
