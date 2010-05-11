/*
 * SimplePermissions.java Created May 6, 2010 by Andrew Butler, PSL
 */
package prisms.arch.ds;

/**
 * Implements Permissions in a simple way
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

	public boolean has(String capability)
	{
		return theMap.containsKey(capability);
	}

	public Permission getPermission(String capability)
	{
		return theMap.get(capability);
	}

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
