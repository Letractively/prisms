/**
 * UserGroup.java Created Dec 19, 2007 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.PrismsApplication;

/** A group that a user can belong to that gives the user permissions */
public class UserGroup
{
	private final UserSource theSource;

	private String theName;

	private final PrismsApplication theApp;

	private String theDescription;

	private GroupPermissions thePermissions;

	/**
	 * Creates a UserGroup
	 * 
	 * @param src The source that the group belongs to
	 * @param name The name of the group
	 * @param app This group's application
	 */
	public UserGroup(UserSource src, String name, PrismsApplication app)
	{
		theSource = src;
		theName = name;
		theApp = app;
		thePermissions = new GroupPermissions();
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

	public String toString()
	{
		return theName;
	}
}
