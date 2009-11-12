/**
 * UserGroup.java Created Dec 19, 2007 by Andrew Butler, PSL
 */
package prisms.impl;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.Permissions;
import prisms.arch.ds.UserSource;

/**
 * A group that a user can belong to that gives the user permissions
 */
class SimpleGroup implements prisms.arch.ds.UserGroup
{
	private final UserSource theSource;

	private String theName;

	private final PrismsApplication theApp;

	private String theDescription;

	private Permissions thePermissions;

	/**
	 * Creates a UserGroup
	 * 
	 * @param src The source that the group belongs to
	 * @param name The name of the group
	 * @param app This group's application
	 */
	public SimpleGroup(UserSource src, String name, PrismsApplication app)
	{
		theSource = src;
		theName = name;
		theApp = app;
	}

	/**
	 * @return This user's source
	 */
	public UserSource getSource()
	{
		return theSource;
	}

	/**
	 * @return This user's name
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * @param name The name for this user
	 */
	public void setName(String name)
	{
		theName = name;
	}

	/**
	 * @return The name of the application that this group applies to
	 */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/**
	 * @return The group's description
	 */
	public String getDescription()
	{
		return theDescription;
	}

	/**
	 * @param descrip A description for the group
	 */
	public void setDescription(String descrip)
	{
		theDescription = descrip;
	}

	/**
	 * @return This user's permissions
	 */
	public Permissions getPermissions()
	{
		return thePermissions;
	}

	/**
	 * @param per The permissions for this user
	 */
	public void setPermissions(Permissions per)
	{
		thePermissions = per;
	}

	public String toString()
	{
		return theName;
	}

}
