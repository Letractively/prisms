/**
 * DBPermission.java Created Jun 27, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import prisms.arch.PrismsApplication;

class DBPermission extends SimplePermission
{
	private int theID;

	/**
	 * @param name The name for this permission
	 * @param descrip The description for this permission
	 * @param app The application for this permission
	 * @param id The database ID of this permission
	 */
	public DBPermission(String name, String descrip, PrismsApplication app, int id)
	{
		super(name, descrip, app);
		theID = id;
	}

	public int getID()
	{
		return theID;
	}

	public boolean equals(Object o)
	{
		if(!(o instanceof DBPermission))
			return false;
		return ((DBPermission) o).theID == theID;
	}

	public int hashCode()
	{
		return theID;
	}
}
