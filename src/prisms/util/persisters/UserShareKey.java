/*
 * GroupSharedObject.java Created Jun 26, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.User;
import prisms.util.ArrayUtils;

/**
 * A key to an object that is shared between users. A user's access to the object is determined by
 * the key's users.
 */
public class UserShareKey implements ShareKey
{
	private User theOwner;

	PrismsApplication theApp;

	private User [] theAccessUsers;

	private boolean [] theEditUsers;

	private boolean isViewPublic;

	private boolean isEditPublic;

	private boolean isShared;

	private String theViewAllPermission;

	private String theEditAllPermission;

	/**
	 * Creates a userSharedObject
	 * 
	 * @param owner The user that will own the new object
	 * @param app The application whose permissions govern the use of this key's object
	 * @param viewAllPermission The permission that allows a user to view this object without being
	 *        the owner or one of this object's access users
	 * @param editAllPermission The permission that allows a user to edit this object without being
	 *        the owner or one of this object's access users
	 * @param shared Whether this key's object should be shared between sessions
	 */
	public UserShareKey(User owner, PrismsApplication app, String viewAllPermission,
		String editAllPermission, boolean shared)
	{
		theOwner = owner;
		theApp = app;
		isShared = shared;
		theViewAllPermission = viewAllPermission;
		theEditAllPermission = editAllPermission;
		theAccessUsers = new User [0];
		theEditUsers = new boolean [0];
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
	 * @return The application that governs access to this key's object
	 */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/**
	 * @return The permission that this key uses to determine if a user is allowed to view all
	 *         objects with a key like this
	 */
	public String getViewPermission()
	{
		return theViewAllPermission;
	}

	/**
	 * @return The permission that this key uses to determine if a user is allowed to edit all
	 *         objects with a key like this
	 */
	public String getEditPermission()
	{
		return theEditAllPermission;
	}

	/**
	 * @return The number of users with permission to view this key's object
	 */
	public int getUserCount()
	{
		return theAccessUsers.length;
	}

	/**
	 * @param idx The index of the user to get
	 * @return The name of the access user at the given index
	 */
	public User getUser(int idx)
	{
		return theAccessUsers[idx];
	}

	/**
	 * Determines whether a user has view access with this shared key
	 * 
	 * @param group The name of the user to test access for
	 * @return The index of the user in this key (used for {@link #canEdit(int)} and
	 *         {@link #setEditAccess(int, boolean)}) or -1 if the user does not have view access to
	 *         this key's object
	 */
	public int hasAccessGroup(String group)
	{
		return ArrayUtils.indexOf(theAccessUsers, group);
	}

	/**
	 * @param groupIdx The index of the user to get the edit permission of
	 * @return Whether the user at the given index is allowed to edit this key's object
	 */
	public boolean canEdit(int groupIdx)
	{
		return theEditUsers[groupIdx];
	}

	/**
	 * @return The names of all this key's access users
	 */
	public User [] getUsers()
	{
		return theAccessUsers;
	}

	/**
	 * @param user The name of the user to add access to this key's object to
	 */
	public void addAccessUser(User user)
	{
		if(ArrayUtils.contains(theAccessUsers, user))
			return;
		boolean [] editUsers = new boolean [theEditUsers.length + 1];
		System.arraycopy(theEditUsers, 0, editUsers, 0, theEditUsers.length);
		theAccessUsers = ArrayUtils.add(theAccessUsers, user);
		theEditUsers = editUsers;
	}

	/**
	 * @param user The name of the user to remove access to this key's object from
	 */
	public void removeAccessUser(User user)
	{
		int idx = ArrayUtils.indexOf(theAccessUsers, user);
		if(idx >= 0)
		{
			boolean [] editUsers = new boolean [theEditUsers.length - 1];
			System.arraycopy(theEditUsers, 0, editUsers, 0, idx);
			System.arraycopy(theEditUsers, idx + 1, editUsers, idx, editUsers.length - idx);
			theAccessUsers = ArrayUtils.remove(theAccessUsers, idx);
			theEditUsers = editUsers;
		}
	}

	/**
	 * @param userIdx The index of the user to set the edit permission of
	 * @param canEdit Whether the user at the given index should be able to edit this key's object
	 */
	public void setEditAccess(int userIdx, boolean canEdit)
	{
		theEditUsers[userIdx] = canEdit;
	}

	/**
	 * @param user The name of the user to set the edit permission of
	 * @param canEdit Whether the given user should be able to edit this key's object
	 */
	public void setEditAccess(User user, boolean canEdit)
	{
		int idx = ArrayUtils.indexOf(theAccessUsers, user);
		if(idx < 0)
		{
			addAccessUser(user);
			idx = theAccessUsers.length - 1;
		}
		theEditUsers[idx] = canEdit;
	}

	/**
	 * @return Whether this key's object may be viewed by any user
	 */
	public boolean isViewPublic()
	{
		return isViewPublic;
	}

	/**
	 * @param viewPublic Whether this key's object should be viewable by any user
	 */
	public void setViewPublic(boolean viewPublic)
	{
		isViewPublic = viewPublic;
		if(!viewPublic)
			isEditPublic = false;
	}

	/**
	 * @return Whether this key's object may be viewed and modified by any user
	 */
	public boolean isEditPublic()
	{
		return isEditPublic;
	}

	/**
	 * @param editPublic Whether this key's object should be viewable and modifieable by any user
	 */
	public void setEditPublic(boolean editPublic)
	{
		isEditPublic = editPublic;
		if(editPublic)
			isViewPublic = true;
	}

	/**
	 * Clears this key's user access list
	 */
	public void clearUsers()
	{
		theAccessUsers = new User [0];
		theEditUsers = new boolean [0];
	}

	public boolean canView(User user)
	{
		if(isViewPublic
			|| user.equals(theOwner)
			|| ArrayUtils.contains(theAccessUsers, user)
			|| (theViewAllPermission != null && user.getPermissions(theApp).has(
				theViewAllPermission))
			|| (theEditAllPermission != null && user.getPermissions(theApp).has(
				theEditAllPermission)))
			return true;
		return false;
	}

	public boolean canEdit(User user)
	{
		if(isEditPublic
			|| user.equals(theOwner)
			|| (theEditAllPermission != null && user.getPermissions(theApp).has(
				theEditAllPermission)))
			return true;
		int u = ArrayUtils.indexOf(theAccessUsers, user);
		if(u < 0)
			return false;
		return theEditUsers[u];
	}

	public boolean canAdministrate(User user)
	{
		if(user.equals(theOwner)
			|| (theEditAllPermission != null && user.getPermissions(theApp).has(
				theEditAllPermission)))
			return true;
		return false;
	}

	@Override
	public UserShareKey clone()
	{
		UserShareKey ret;
		try
		{
			ret = (UserShareKey) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("Clone not supported", e);
		}
		ret.theAccessUsers = theAccessUsers.clone();
		ret.theEditUsers = theEditUsers.clone();
		return ret;
	}

	/**
	 * Clones this object, creating a new object that is identical to the original except that it
	 * may have a different owner
	 * 
	 * @param newOwner The owner for the cloned object
	 * @return The cloned object
	 */
	public UserShareKey clone(User newOwner)
	{
		if(newOwner == null)
			newOwner = theOwner;
		UserShareKey ret = clone();
		ret.theOwner = newOwner;
		return ret;
	}

	@Override
	public boolean equals(Object o)
	{
		if(o == this)
			return true;
		if(!(o instanceof UserShareKey))
			return false;
		final UserShareKey psk = (UserShareKey) o;
		if(!(psk.isViewPublic == isViewPublic && psk.isEditPublic == isEditPublic
			&& psk.theOwner.equals(theOwner)
			&& equal(psk.theViewAllPermission, theViewAllPermission) && equal(
				psk.theEditAllPermission, theEditAllPermission)))
			return false;
		final boolean [] ret = new boolean [] {true};
		ArrayUtils.adjust(theAccessUsers, psk.theAccessUsers,
			new ArrayUtils.DifferenceListener<User, User>()
			{
				public boolean identity(User o1, User o2)
				{
					return o1.equals(o2);
				}

				public User added(User o1, int mIdx, int retIdx)
				{
					if(canView(o1) != psk.canView(o1) || canEdit(o1) != psk.canEdit(o1))
						ret[0] = false;
					return null;
				}

				public User removed(User o1, int oIdx, int incMod, int retIdx)
				{
					if(canView(o1) != psk.canView(o1) || canEdit(o1) != psk.canEdit(o1))
						ret[0] = false;
					return null;
				}

				public User set(User o1, int idx1, int incMod, User o2, int idx2, int retIdx)
				{
					if(canView(o1) != psk.canView(o1) || canEdit(o1) != psk.canEdit(o1))
						ret[0] = false;
					return null;
				}
			});
		return ret[0];
	}

	@Override
	public int hashCode()
	{
		int userHash = 0;
		for(int g = 0; g < theAccessUsers.length; g++)
			userHash += theAccessUsers[g].hashCode() + (theEditUsers[g] ? 17 : 0);
		return theOwner.hashCode() * 13 + userHash * 11
			+ (theViewAllPermission == null ? 0 : theViewAllPermission.hashCode() * 7)
			+ (theEditAllPermission == null ? 0 : theEditAllPermission.hashCode() * 3)
			+ (isViewPublic ? 17 : 0) + (isEditPublic ? 19 : 0);
	}

	private static boolean equal(String s1, String s2)
	{
		return s1 == null ? s2 == null : s1.equals(s2);
	}
}
