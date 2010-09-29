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
 * the key's groups.
 */
public class GroupShareKey implements ShareKey
{
	private User theOwner;

	prisms.arch.PrismsApplication theApp;

	private String [] theAccessGroups;

	private boolean [] theEditGroups;

	private boolean isViewPublic;

	private boolean isEditPublic;

	private boolean isShared;

	private String theViewAllPermission;

	private String theEditAllPermission;

	/**
	 * Creates a GroupSharedObject
	 * 
	 * @param owner The user that will own the new object
	 * @param app The application whose permissions govern the use of this key's object
	 * @param viewAllPermission The permission that allows a user to view this object without being
	 *        the owner or a member of any of this object's access groups
	 * @param editAllPermission The permission that allows a user to edit this object without being
	 *        the owner or a member of any of this object's access groups
	 * @param shared Whether this key's object should be shared between sessions
	 */
	public GroupShareKey(User owner, prisms.arch.PrismsApplication app, String viewAllPermission,
		String editAllPermission, boolean shared)
	{
		theOwner = owner;
		theApp = app;
		isShared = shared;
		theViewAllPermission = viewAllPermission;
		theEditAllPermission = editAllPermission;
		theAccessGroups = new String [0];
		theEditGroups = new boolean [0];
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
	 * @return The number of groups with permission to view this key's object
	 */
	public int getGroupCount()
	{
		return theAccessGroups.length;
	}

	/**
	 * @param idx The index of the group to get
	 * @return The name of the access group at the given index
	 */
	public String getGroup(int idx)
	{
		return theAccessGroups[idx];
	}

	/**
	 * Determines whether a group has view access with this shared key
	 * 
	 * @param group The name of the group to test access for
	 * @return The index of the group in this key (used for {@link #canEdit(int)} and
	 *         {@link #setEditAccess(int, boolean)}) or -1 if the group does not have view access to
	 *         this key's object
	 */
	public int hasAccessGroup(String group)
	{
		return ArrayUtils.indexOf(theAccessGroups, group);
	}

	/**
	 * @param groupIdx The index of the group to get the edit permission of
	 * @return Whether the group at the given index is allowed to edit this key's object
	 */
	public boolean canEdit(int groupIdx)
	{
		return theEditGroups[groupIdx];
	}

	/**
	 * @param group The name of the group to get the edit permission of
	 * @return Whether the given group is allowed to edit this key's object
	 */
	public boolean canEdit(String group)
	{
		int idx = ArrayUtils.indexOf(theAccessGroups, group);
		if(idx < 0)
			return false;
		return theEditGroups[idx];
	}

	/**
	 * @return The names of all this key's access groups
	 */
	public String [] getGroups()
	{
		return theAccessGroups;
	}

	/**
	 * @param group The name of the group to add access to this key's object to
	 */
	public void addAccessGroup(String group)
	{
		if(ArrayUtils.contains(theAccessGroups, group))
			return;
		boolean [] editGroups = new boolean [theEditGroups.length + 1];
		System.arraycopy(theEditGroups, 0, editGroups, 0, theEditGroups.length);
		theAccessGroups = ArrayUtils.add(theAccessGroups, group);
		theEditGroups = editGroups;
	}

	/**
	 * @param group The name of the group to remove access to this key's object from
	 */
	public void removeAccessGroup(String group)
	{
		int idx = ArrayUtils.indexOf(theAccessGroups, group);
		if(idx >= 0)
		{
			boolean [] editGroups = new boolean [theEditGroups.length - 1];
			System.arraycopy(theEditGroups, 0, editGroups, 0, idx);
			System.arraycopy(theEditGroups, idx + 1, editGroups, idx, editGroups.length - idx);
			theAccessGroups = ArrayUtils.remove(theAccessGroups, idx);
			theEditGroups = editGroups;
		}
	}

	/**
	 * @param groupIdx The index of the group to set the edit permission of
	 * @param canEdit Whether the group at the given index should be able to edit this key's object
	 */
	public void setEditAccess(int groupIdx, boolean canEdit)
	{
		theEditGroups[groupIdx] = canEdit;
	}

	/**
	 * @param group The name of the group to set the edit permission of
	 * @param canEdit Whether the given group should be able to edit this key's object
	 */
	public void setEditAccess(String group, boolean canEdit)
	{
		int idx = ArrayUtils.indexOf(theAccessGroups, group);
		if(idx < 0)
		{
			addAccessGroup(group);
			idx = theAccessGroups.length - 1;
		}
		theEditGroups[idx] = canEdit;
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

	public boolean canView(User user)
	{
		if(isViewPublic
			|| user.getName().equals(theOwner.getName())
			|| (theViewAllPermission != null && user.getPermissions(theApp).has(
				theViewAllPermission))
			|| (theEditAllPermission != null && user.getPermissions(theApp).has(
				theEditAllPermission)))
			return true;
		for(String group : theAccessGroups)
			for(int g = 0; g < user.getGroups().length; g++)
				if(group.equals(user.getGroups()[g].getName()))
					return true;
		return false;
	}

	public boolean canEdit(User user)
	{
		if(isEditPublic
			|| user.getName().equals(theOwner.getName())
			|| (theEditAllPermission != null && user.getPermissions(theApp).has(
				theEditAllPermission)))
			return true;
		for(int g = 0; g < theAccessGroups.length; g++)
		{
			if(!theEditGroups[g])
				continue;
			for(String group : theAccessGroups)
				for(int g2 = 0; g2 < user.getGroups().length; g2++)
					if(group.equals(user.getGroups()[g2].getName()))
						return true;
		}
		return false;
	}

	public boolean canAdministrate(User user)
	{
		if(user.getName().equals(theOwner.getName())
			|| (theEditAllPermission != null && user.getPermissions(theApp).has(
				theEditAllPermission)))
			return true;
		return false;
	}

	public GroupShareKey clone()
	{
		GroupShareKey ret;
		try
		{
			ret = (GroupShareKey) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("Clone not supported", e);
		}
		ret.theAccessGroups = theAccessGroups.clone();
		ret.theEditGroups = theEditGroups.clone();
		return ret;
	}

	/**
	 * Clones this object, creating a new object that is identical to the original except that it
	 * may have a different owner
	 * 
	 * @param newOwner The owner for the cloned object
	 * @return The cloned object
	 */
	public GroupShareKey clone(User newOwner)
	{
		if(newOwner == null)
			newOwner = theOwner;
		GroupShareKey ret = clone();
		ret.theOwner = newOwner;
		return ret;
	}

	public boolean equals(Object o)
	{
		if(o == this)
			return true;
		if(!(o instanceof GroupShareKey))
			return false;
		GroupShareKey psk = (GroupShareKey) o;
		if(!(psk.isViewPublic == isViewPublic && psk.isEditPublic == isEditPublic
			&& psk.theOwner.equals(theOwner)
			&& equal(psk.theViewAllPermission, theViewAllPermission) && equal(
			psk.theEditAllPermission, theEditAllPermission)))
			return false;
		if(psk.theAccessGroups.length != theAccessGroups.length)
			return false;
		for(int g = 0; g < psk.theAccessGroups.length; g++)
		{
			int g2 = ArrayUtils.indexOf(theAccessGroups, psk.theAccessGroups[g]);
			if(g2 < 0)
				return false;
			if(psk.theEditGroups[g] != theEditGroups[g2])
				return false;
		}
		return true;
	}

	public int hashCode()
	{
		int groupHash = 0;
		for(int g = 0; g < theAccessGroups.length; g++)
			groupHash += theAccessGroups[g].hashCode() + (theEditGroups[g] ? 17 : 0);
		return theOwner.hashCode() * 13 + groupHash * 11
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
	public JSONArray getGroupJSON()
	{
		JSONArray ret = new JSONArray();
		for(int g = 0; g < theAccessGroups.length; g++)
		{
			JSONObject jsonG = new JSONObject();
			jsonG.put("groupName", theAccessGroups[g]);
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
	 */
	public void fromGroupJSON(JSONArray json)
	{
		theAccessGroups = new String [0];
		theEditGroups = new boolean [0];
		for(int g = 0; g < json.size(); g++)
		{
			JSONObject jsonG = (JSONObject) json.get(g);
			if(Boolean.FALSE.equals(jsonG.get("canView")))
				continue;
			String groupName = (String) jsonG.get("groupName");
			addAccessGroup(groupName);
			setEditAccess(groupName, Boolean.TRUE.equals(jsonG.get("canEdit")));
		}
	}
}
