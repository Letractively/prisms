/**
 * DBGroup.java Created Jun 25, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

/** A database implementation of a PRISMS group */
public class DBGroup extends prisms.arch.ds.UserGroup
{
	private int theID;

	private boolean isDeleted;

	/**
	 * @param src The source of this group
	 * @param name The name for this group
	 * @param app The application this group applies to
	 * @param id This group's database ID
	 */
	public DBGroup(prisms.arch.ds.UserSource src, String name, prisms.arch.PrismsApplication app,
		int id)
	{
		super(src, name, app);
		theID = id;
	}

	/**
	 * @return This group's database ID
	 */
	public int getID()
	{
		return theID;
	}

	/**
	 * @param id The ID to set for this group
	 */
	public void setID(int id)
	{
		theID = id;
	}

	/**
	 * @return Whether this group is deleted
	 */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	public boolean equals(Object o)
	{
		if(!(o instanceof DBGroup))
			return false;
		return ((DBGroup) o).theID == theID;
	}

	public int hashCode()
	{
		return theID;
	}
}
