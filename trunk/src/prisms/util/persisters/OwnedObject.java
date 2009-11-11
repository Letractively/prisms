/**
 * Mission.java Created Oct 15, 2007 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.ds.User;

/**
 * Represents an object that is owned by a particular application {@link prisms.arch.ds.User}
 */
public interface OwnedObject
{
	/**
	 * @return This object's owner
	 */
	User getOwner();

	/**
	 * @return Whether the object is to be shared with other users or not
	 */
	boolean isPublic();
}
