/**
 * Permission.java Created Jun 27, 2008 by Andrew Butler, PSL
 */
package prisms.arch;


/** Represents a permission in the PRISMS architecture */
public class Permission
{
	private final PrismsApplication theApp;

	private final String theName;

	private final String theDescrip;

	/**
	 * Creates a simple permission
	 * 
	 * @param app The application that this permission belongs to
	 * @param name The name of the permission
	 * @param descrip A description for the permission
	 */
	public Permission(PrismsApplication app, String name, String descrip)
	{
		theApp = app;
		theName = name;
		theDescrip = descrip;
	}

	/** @return The application that this permission applies to */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/** @return The name of this permission */
	public String getName()
	{
		return theName;
	}

	/** @return A description of what capability this permission conveys */
	public String getDescrip()
	{
		return theDescrip;
	}

	public String toString()
	{
		return theName;
	}
}
