/*
 * SharedObject.java Created Jun 26, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

/**
 * A SharedObject is an object that can be shared between sessions of different users using a key
 * determining who can view and edit which objects
 */
public interface SharedObject
{
	/**
	 * @return This SharedObject's ShareKey which determines who can view and edit this object
	 */
	ShareKey getShareKey();
}
