/*
 * GroupSharedObject.java Created Jun 26, 2009 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.ds.User;
import prisms.util.ArrayUtils;

/**
 * A key to an object that is shared between users. A user's access to the object is determined by
 * the key's users.
 */
public class UserShareKey implements ShareKey, Cloneable
{
	private User theOwner;

	private String [] theAccessUsers;

	private boolean [] theEditUsers;

	private boolean isViewPublic;

	private boolean isEditPublic;

	private String theViewAllPermission;

	private String theEditAllPermission;

	/**
	 * Creates a userSharedObject
	 * 
	 * @param owner The user that will own the new object
	 * @param viewAllPermission The permission that allows a user to view this object without being
	 *        the owner or one of this object's access users
	 * @param editAllPermission The permission that allows a user to edit this object without being
	 *        the owner or one of this object's access users
	 */
	public UserShareKey(User owner, String viewAllPermission, String editAllPermission)
	{
		theOwner = owner;
		theViewAllPermission = viewAllPermission;
		theEditAllPermission = editAllPermission;
		theAccessUsers = new String [0];
		theEditUsers = new boolean [0];
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
	public String getUser(int idx)
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
	 * @param user The name of the user to get the edit permission of
	 * @return Whether the given user is allowed to edit this key's object
	 */
	public boolean canEdit(String user)
	{
		int idx = ArrayUtils.indexOf(theAccessUsers, user);
		if(idx < 0)
			return false;
		return theEditUsers[idx];
	}

	/**
	 * @return The names of all this key's access users
	 */
	public String [] getUsers()
	{
		return theAccessUsers;
	}

	/**
	 * @param user The name of the user to add access to this key's object to
	 */
	public void addAccessUser(String user)
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
	public void removeAccessUser(String user)
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
	public void setEditAccess(String user, boolean canEdit)
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
		theAccessUsers = new String [0];
		theEditUsers = new boolean [0];
	}

	/**
	 * @see prisms.util.persisters.ShareKey#canView(prisms.arch.ds.User)
	 */
	public boolean canView(User user)
	{
		if(isViewPublic || user.getName().equals(theOwner.getName())
			|| ArrayUtils.contains(theAccessUsers, user.getName())
			|| (theViewAllPermission != null && user.getPermissions().has(theViewAllPermission))
			|| (theEditAllPermission != null && user.getPermissions().has(theEditAllPermission)))
			return true;
		return false;
	}

	/**
	 * @see prisms.util.persisters.ShareKey#canEdit(prisms.arch.ds.User)
	 */
	public boolean canEdit(User user)
	{
		if(isEditPublic || user.getName().equals(theOwner.getName())
			|| (theEditAllPermission != null && user.getPermissions().has(theEditAllPermission)))
			return true;
		int u = ArrayUtils.indexOf(theAccessUsers, user.getName());
		if(u < 0)
			return false;
		return theEditUsers[u];
	}

	/**
	 * @see prisms.util.persisters.ShareKey#canAdministrate(prisms.arch.ds.User)
	 */
	public boolean canAdministrate(User user)
	{
		if(user.getName().equals(theOwner.getName())
			|| (theEditAllPermission != null && user.getPermissions().has(theEditAllPermission)))
			return true;
		return false;
	}

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

	public boolean equals(Object o)
	{
		if(o == this)
			return true;
		if(!(o instanceof UserShareKey))
			return false;
		UserShareKey psk = (UserShareKey) o;
		if(!(psk.isViewPublic == isViewPublic && psk.isEditPublic == isEditPublic
			&& psk.theOwner.equals(theOwner)
			&& equal(psk.theViewAllPermission, theViewAllPermission) && equal(
			psk.theEditAllPermission, theEditAllPermission)))
			return false;
		if(psk.theAccessUsers.length != theAccessUsers.length)
			return false;
		for(int g = 0; g < psk.theAccessUsers.length; g++)
		{
			int g2 = ArrayUtils.indexOf(theAccessUsers, psk.theAccessUsers[g]);
			if(g2 < 0)
				return false;
			if(psk.theEditUsers[g] != theEditUsers[g2])
				return false;
		}
		return true;
	}

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

	/**
	 * @return A JSON-array containing this share key's groups and their accessibility
	 */
	public JSONArray getUserJSON()
	{
		JSONArray ret = new JSONArray();
		for(int g = 0; g < theAccessUsers.length; g++)
		{
			JSONObject jsonG = new JSONObject();
			jsonG.put("userName", theAccessUsers[g]);
			jsonG.put("canView", new Boolean(true));
			jsonG.put("canEdit", new Boolean(canEdit(g)));
			ret.add(jsonG);
		}
		return ret;
	}

	/**
	 * Fills this key's groups out from a JSON array
	 * 
	 * @param json A JSON-array containing a list of groups and their accessibility
	 * @param user The user that is attempting to adjust the values
	 */
	public void fromGroupJSON(JSONArray json, final User user)
	{
		final boolean [] adminChecked = new boolean [] {user == null};
		ArrayUtils.adjust(theAccessUsers, (JSONObject []) json
			.toArray(new JSONObject [json.size()]),
			new ArrayUtils.DifferenceListener<String, JSONObject>()
			{
				public boolean identity(String o1, JSONObject o2)
				{
					return o1.equals(o2.get("userName"));
				}

				public String added(JSONObject o, int idx, int retIdx)
				{
					if(Boolean.FALSE.equals(o.get("canView")))
						return null;
					String userName = (String) o.get("userName");
					if(!adminChecked[0] && !canAdministrate(user))
						throw new IllegalArgumentException("User " + user.getName()
							+ " does not have permission to modify this object's sharing");
					adminChecked[0] = true;
					addAccessUser(userName);
					if(Boolean.TRUE.equals(o.get("canEdit")))
						setEditAccess(userName, true);
					return userName;
				}

				public String removed(String o, int idx, int incMod, int retIdx)
				{
					if(!adminChecked[0] && !canAdministrate(user))
						throw new IllegalArgumentException("User " + user.getName()
							+ " does not have permission to modify this object's sharing");
					removeAccessUser(o);
					return null;
				}

				public String set(String o1, int idx1, int incMod, JSONObject o2, int idx2,
					int retIdx)
				{
					if(Boolean.FALSE.equals(o2.get("canView")))
					{
						if(!adminChecked[0] && !canAdministrate(user))
							throw new IllegalArgumentException("User " + user.getName()
								+ " does not have permission to modify this object's sharing");
						removeAccessUser(o1);
						return null;
					}
					if(canEdit(o1) != Boolean.TRUE.equals(o2.get("canEdit")))
					{
						if(!adminChecked[0] && !canAdministrate(user))
							throw new IllegalArgumentException("User " + user.getName()
								+ " does not have permission to modify this object's sharing");
						setEditAccess(o1, Boolean.TRUE.equals(o2.get("canEdit")));
					}
					return o1;
				}
			});
	}
}
