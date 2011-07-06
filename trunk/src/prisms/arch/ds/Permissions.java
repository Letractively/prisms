/*
 * Permissions.java Created Oct 5, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.Permission;

/**
 * A simple permissions interface that allows an application to determine a user's allowed
 * capabilities based on a common set of names assigned to an indefinite number of abilities.
 */
public interface Permissions
{
	/**
	 * Tests whether a permission has been granted
	 * 
	 * @param capability The permission (capability) to test for
	 * @return Whether the given capability is allowed
	 */
	boolean has(String capability);

	/**
	 * @param capability The permission to get
	 * @return The permission of the given name
	 */
	Permission getPermission(String capability);

	/** @return All permissions associated with this Permissions object */
	Permission [] getAllPermissions();
}
