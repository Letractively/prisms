/**
 * SimplePermissions.java Created Jun 25, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import prisms.arch.ds.Permission;
import prisms.arch.ds.Permissions;

/**
 * A simple implementation of Permissions
 */
public class SimplePermissions implements Permissions
{
	private final java.util.Map<String, Permission> theMap;

	/**
	 * Creates a Permissions set
	 */
	public SimplePermissions()
	{
		theMap = new java.util.HashMap<String, Permission>();
	}

	/**
	 * @see prisms.arch.ds.Permissions#has(java.lang.String)
	 */
	public boolean has(String capability)
	{
		return theMap.containsKey(capability);
	}

	/**
	 * @see prisms.arch.ds.Permissions#getPermission(java.lang.String)
	 */
	public Permission getPermission(String capability)
	{
		return theMap.get(capability);
	}

	/**
	 * @see prisms.arch.ds.Permissions#getAllPermissions()
	 */
	public Permission [] getAllPermissions()
	{
		Permission [] ret = theMap.values().toArray(new Permission [theMap.size()]);
		java.util.Arrays.sort(ret, new java.util.Comparator<Permission>()
		{
			public int compare(Permission p1, Permission p2)
			{
				return p1.getName().compareToIgnoreCase(p2.getName());
			}
		});
		return ret;
	}

	/**
	 * @param permission The permission to add
	 */
	public void addPermission(Permission permission)
	{
		theMap.put(permission.getName(), permission);
	}

	/**
	 * @param name The name of the permission to remove
	 */
	public void removePermission(String name)
	{
		theMap.remove(name);
	}
}