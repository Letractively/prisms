/**
 * Permission.java Created Jun 27, 2008 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.PrismsApplication;

/**
 * Represents a permission in the PRISMS architecture
 */
public class Permission
{
	private final String theName;

	private String theDescrip;

	private final PrismsApplication theApp;

	/**
	 * Creates a simple permission
	 * 
	 * @param name The name of the permission
	 * @param descrip A description for the permission
	 * @param app The application that this permission belongs to
	 */
	public Permission(String name, String descrip, PrismsApplication app)
	{
		theName = name;
		theDescrip = descrip;
		theApp = app;
	}

	/**
	 * @return The name of this permission
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * @return A description of what capability this permission conveys
	 */
	public String getDescrip()
	{
		return theDescrip;
	}

	/**
	 * @param descrip The description to set for this permission
	 */
	public void setDescrip(String descrip)
	{
		theDescrip = descrip;
	}

	/**
	 * @return The application that this permission applies to
	 */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	public String toString()
	{
		return theName;
	}
}
