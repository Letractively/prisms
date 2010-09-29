/*
 * PublicSharedObject.java Created Jun 26, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.ds.User;

/**
 * A key for an object that is owned by a user and can be made public or private so that other
 */
public class PublicShareKey implements ShareKey
{
	private User theOwner;

	prisms.arch.PrismsApplication theApp;

	private boolean isPublic;

	private boolean isShared;

	private String theViewAllPermission;

	private String theEditAllPermission;

	/**
	 * Creates a PublicSharedObject
	 * 
	 * @param owner The user that will own the new object
	 * @param app The application whose permissions govern the use of this key's object
	 * @param viewAllPermission The permission that allows a user to view this object without being
	 *        the owner or a member of any of this object's access groups
	 * @param editAllPermission The permission that allows a user to edit this object without being
	 *        the owner or a member of any of this object's access groups
	 * @param shared Whether this key's object should be shared between sessions
	 */
	public PublicShareKey(User owner, prisms.arch.PrismsApplication app, String viewAllPermission,
		String editAllPermission, boolean shared)
	{
		theOwner = owner;
		theApp = app;
		isShared = shared;
		theViewAllPermission = viewAllPermission;
		theEditAllPermission = editAllPermission;
	}

	public boolean isShared()
	{
		return isShared;
	}

	/**
	 * @return The user that owns this object. The owner of an object will always be able to view
	 *         and edit it.
	 */
	public User getOwner()
	{
		return theOwner;
	}

	/**
	 * @return Whether this object is marked as public, meaning it can be viewed by all users
	 */
	public boolean isPublic()
	{
		return isPublic;
	}

	/**
	 * @param p Whether this object should be public, meaning it can be viewed by all users
	 */
	public void setPublic(boolean p)
	{
		isPublic = p;
	}

	public boolean canView(User user)
	{
		if(isPublic
			|| user.getName().equals(theOwner.getName())
			|| (theViewAllPermission != null && user.getPermissions(theApp).has(
				theViewAllPermission))
			|| (theEditAllPermission != null && user.getPermissions(theApp).has(
				theEditAllPermission)))
			return true;
		return false;
	}

	public boolean canEdit(User user)
	{
		if(user.getName().equals(theOwner.getName())
			|| (theEditAllPermission != null && user.getPermissions(theApp).has(
				theEditAllPermission)))
			return true;
		return false;
	}

	public boolean canAdministrate(User user)
	{
		return canEdit(user);
	}

	public PublicShareKey clone()
	{
		PublicShareKey ret;
		try
		{
			ret = (PublicShareKey) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("Clone not supported", e);
		}
		return ret;
	}

	/**
	 * Clones this object, creating a new object that is identical to the original except that it
	 * may have a different owner
	 * 
	 * @param newOwner The owner for the cloned object
	 * @return The cloned object
	 */
	public PublicShareKey clone(User newOwner)
	{
		if(newOwner == null)
			newOwner = theOwner;
		PublicShareKey ret = clone();
		ret.theOwner = newOwner;
		return ret;
	}

	public boolean equals(Object o)
	{
		if(!(o instanceof PublicShareKey))
			return false;
		PublicShareKey psk = (PublicShareKey) o;
		return psk.theOwner.equals(theOwner) && psk.isPublic == isPublic
			&& equal(psk.theViewAllPermission, theViewAllPermission)
			&& equal(psk.theEditAllPermission, theEditAllPermission);
	}

	public int hashCode()
	{
		return theOwner.hashCode() * 13 + (isPublic ? 11 : 0)
			+ (theViewAllPermission == null ? 0 : theViewAllPermission.hashCode() * 7)
			+ (theEditAllPermission == null ? 0 : theEditAllPermission.hashCode() * 3);
	}

	private static boolean equal(String s1, String s2)
	{
		return s1 == null ? s2 == null : s1.equals(s2);
	}
}
