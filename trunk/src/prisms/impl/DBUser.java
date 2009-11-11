/**
 * DBUser.java Created Jun 25, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

class DBUser extends SimpleUser
{
	private final int theID;

	public DBUser(prisms.arch.ds.UserSource source, String name, int id)
	{
		super(source, name);
		theID = id;
	}

	public DBUser(DBUser rootUser, prisms.arch.PrismsApplication app)
	{
		super(rootUser, app);
		theID = rootUser.getID();
	}

	public int getID()
	{
		return theID;
	}

	/*
	public DBUser clone()
	{
		return (DBUser) super.clone();
	}
	*/
}
