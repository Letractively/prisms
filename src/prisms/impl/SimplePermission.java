/**
 * SimplePermission.java Created Jun 27, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.Permission;

/**
 * A simple implementation of {@link Permission}
 */
public class SimplePermission implements Permission
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
	public SimplePermission(String name, String descrip, PrismsApplication app)
	{
		theName = name;
		theDescrip = descrip;
		theApp = app;
	}

	/**
	 * @see prisms.arch.ds.Permission#getName()
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * @see prisms.arch.ds.Permission#getDescrip()
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
	 * @see prisms.arch.ds.Permission#getApp()
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
