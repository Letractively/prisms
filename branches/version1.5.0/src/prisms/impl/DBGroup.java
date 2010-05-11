/**
 * DBGroup.java Created Jun 25, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

class DBGroup extends SimpleGroup
{
	private final int theID;

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

	public int getID()
	{
		return theID;
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
