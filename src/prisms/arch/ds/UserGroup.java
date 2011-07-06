/*
 * UserGroup.java Created Dec 19, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.PrismsApplication;

/** A group that a user can belong to that gives the user permissions */
public class UserGroup
{
	private long theID;

	private final UserSource theSource;

	private String theName;

	private final PrismsApplication theApp;

	private String theDescription;

	private GroupPermissions thePermissions;

	private boolean isDeleted;

	/**
	 * Creates a UserGroup
	 * 
	 * @param src The source that the group belongs to
	 * @param name The name of the group
	 * @param app This group's application
	 * @param id The storage ID for this group
	 */
	public UserGroup(UserSource src, String name, PrismsApplication app, long id)
	{
		theSource = src;
		theName = name;
		theApp = app;
		theID = id;
		thePermissions = new GroupPermissions();
	}

	/** @return This group's storage ID */
	public long getID()
	{
		return theID;
	}

	/** @param id The storage ID for this group */
	public void setID(long id)
	{
		theID = id;
	}

	/** @return This group's source */
	public UserSource getSource()
	{
		return theSource;
	}

	/** @return This group's name */
	public String getName()
	{
		return theName;
	}

	/** @param name The name for this group */
	public void setName(String name)
	{
		theName = name;
	}

	/** @return The name of the application that this group applies to */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/** @return The group's description */
	public String getDescription()
	{
		return theDescription;
	}

	/** @param descrip A description for the group */
	public void setDescription(String descrip)
	{
		theDescription = descrip;
	}

	/** @return This group's permissions */
	public GroupPermissions getPermissions()
	{
		return thePermissions;
	}

	/** @param per The permissions for this group */
	public void setPermissions(GroupPermissions per)
	{
		thePermissions = per;
	}

	/** @return Whether this group is deleted */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	/** @param deleted Whether this group is deleted */
	public void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof UserGroup))
			return false;
		return ((UserGroup) o).theID == theID;
	}

	@Override
	public int hashCode()
	{
		if(theID >= 0)
			return ((int) theID) ^ ((int) (theID >>> 32));
		else
			return theName.hashCode();
	}

	@Override
	public String toString()
	{
		return theName;
	}
}
