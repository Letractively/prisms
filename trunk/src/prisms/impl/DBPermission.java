/**
 * DBPermission.java Created Jun 27, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

class DBPermission extends prisms.arch.ds.Permission
{
	private int theID;

	private boolean isDeleted;

	/**
	 * @param name The name for this permission
	 * @param descrip The description for this permission
	 * @param app The application for this permission
	 * @param id The database ID of this permission
	 */
	public DBPermission(String name, String descrip, prisms.arch.PrismsApplication app, int id)
	{
		super(name, descrip, app);
		theID = id;
	}

	public int getID()
	{
		return theID;
	}

	public void setID(int id)
	{
		theID = id;
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

	public boolean isDeleted()
	{
		return isDeleted;
	}

	public void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}
}
