/*
 * SharedObjectEditor.java Created Nov 9, 2010 by Andrew Butler, PSL
 */
package prisms.ui;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.ds.User;

/**
 * Eases editing of {@link prisms.util.persisters.SharedObject}s. This class's method interface with
 * the prisms.widget.SharedObjectAccess dojo widget.
 */
public class SharedObjectEditor
{
	static final Logger log = Logger.getLogger(SharedObjectEditor.class);

	/**
	 * Allows the calling code complete control over what happens when a shared key is modified by
	 * an event
	 */
	public interface EventInterpreter
	{
		/**
		 * Sets whether the key should be view- or edit-public
		 * 
		 * @param view Whether the key should be view-public
		 * @param edit Whether the key should be edit-public
		 */
		void setPublic(boolean view, boolean edit);

		/**
		 * Sets the access of a particular user to the shared object
		 * 
		 * @param user The user whose access to set
		 * @param view Whether the user should be able to see the object
		 * @param edit Whether the user should be able to edit the object
		 */
		void setUserAccess(prisms.arch.ds.User user, boolean view, boolean edit);
	}

	/**
	 * Serializes the shared key of an object into JSON
	 * 
	 * @param key The shared key to serialize
	 * @param users All PRISMS users in the environment
	 * @param centers For labeling the users' residences
	 * @param localID The id of the local center
	 * @param json The json object to serialize the key into
	 */
	public static void serializeKey(prisms.util.persisters.UserShareKey key, User [] users,
		prisms.records.PrismsCenter[] centers, int localID, JSONObject json)
	{
		json.put("isViewPublic", Boolean.valueOf(key.isViewPublic()));
		json.put("isEditPublic", Boolean.valueOf(key.isEditPublic()));
		JSONArray jsonUsers = new JSONArray();
		json.put("shareUsers", jsonUsers);
		for(User u : users)
		{
			if(u.equals(key.getOwner()))
				continue;
			JSONObject jsonUser = new JSONObject();
			jsonUsers.add(jsonUser);
			String serID;
			if(u.getID() >= 0)
				serID = Long.toHexString(u.getID());
			else
				serID = "-" + Long.toHexString(-u.getID());
			jsonUser.put("id", serID);
			jsonUser.put("userName", u.getName());
			prisms.arch.ds.Permissions perms = key.getApp() == null ? null : u.getPermissions(key
				.getApp());
			if(key.canView(u))
			{
				jsonUser.put("canView", Boolean.TRUE);
				if(perms != null
					&& (perms.has(key.getViewPermission()) || perms.has(key.getEditPermission())))
					jsonUser.put("globalView", Boolean.TRUE);
			}
			else
				jsonUser.put("canView", Boolean.FALSE);
			if(key.canEdit(u))
			{
				jsonUser.put("canEdit", Boolean.TRUE);
				if(perms != null && perms.has(key.getEditPermission()))
					jsonUser.put("globalEdit", Boolean.TRUE);
			}
			else
				jsonUser.put("canEdit", Boolean.FALSE);
			int cid = prisms.arch.ds.IDGenerator.getCenterID(u.getID());
			if(cid == localID)
				jsonUser.put("local", Boolean.TRUE);
			else if(centers != null)
				for(prisms.records.PrismsCenter center : centers)
					if(center.getCenterID() == cid)
					{
						jsonUser.put("center", center.getName());
						break;
					}
		}
	}

	/**
	 * Deserializes a key serialized with
	 * {@link #serializeKey(prisms.util.persisters.UserShareKey, User[], prisms.records.PrismsCenter [], int, JSONObject)}
	 * 
	 * @param key The key to modify. This key is not modified at all by the method itself, but may
	 *        be modified by the interpreter
	 * @param users All users in the PRISMS environment
	 * @param createUsers Whether to create ad-hoc users if a user is deserialized that is not in
	 *        the environment
	 * @param json The JSONObject containing the serialized key
	 * @param interpreter The interpreter to modify the key and perform other
	 *        implementation-specific operations
	 */
	public static void deserializeKey(final prisms.util.persisters.UserShareKey key, User [] users,
		final boolean createUsers, JSONObject json, final EventInterpreter interpreter)
	{
		if(Boolean.FALSE.equals(json.get("isViewPublic")))
		{
			if(key.isViewPublic())
				interpreter.setPublic(false, false);
		}
		else if(Boolean.TRUE.equals(json.get("isEditPublic")))
		{
			if(!key.isEditPublic())
				interpreter.setPublic(true, true);
		}
		else if(json.containsKey("isViewPublic"))
		{
			if(json.containsKey("isEditPublic"))
			{
				if(!key.isViewPublic() || key.isEditPublic())
					interpreter.setPublic(true, false);
			}
			else
			{
				if(!key.isViewPublic())
					interpreter.setPublic(true, false);
			}
		}

		JSONArray jsonUsers = (JSONArray) json.get("shareUsers");
		if(jsonUsers != null)
		{
			prisms.util.ArrayUtils.adjust(users,
				(JSONObject []) jsonUsers.toArray(new JSONObject [jsonUsers.size()]),
				new prisms.util.ArrayUtils.DifferenceListener<User, JSONObject>()
				{
					public boolean identity(User o1, JSONObject o2)
					{
						if(o2.containsKey("id"))
							return o1.getID() == Long.parseLong((String) o2.get("id"), 16);
						else
							return prisms.arch.ds.IDGenerator.getCenterID(key.getOwner().getID()) == prisms.arch.ds.IDGenerator
								.getCenterID(o1.getID()) && o1.getName().equals(o2.get("userName"));
					}

					public User added(JSONObject o, int idx, int retIdx)
					{
						if(Boolean.FALSE.equals(o.get("canView")) || !o.containsKey("canView")
							&& !o.containsKey("canEdit"))
							return null;
						if(createUsers)
						{
							User ret = new User(null, (String) o.get("userName"), o
								.containsKey("id") ? Long.parseLong((String) o.get("id"), 16) : -1);
							prisms.arch.ds.UserGroup g = null;
							if(Boolean.TRUE.equals(o.get("globalView")))
							{
								g = new prisms.arch.ds.UserGroup(null, "Key Gen Permissions", key
									.getApp(), -1);
								g.getPermissions().addPermission(
									new prisms.arch.Permission(key.getApp(), key
										.getViewPermission(), "Key Generated"));
								ret.addTo(g);
							}
							if(Boolean.TRUE.equals(o.get("globalEdit")))
							{
								if(g == null)
								{
									g = new prisms.arch.ds.UserGroup(null, "Key Gen Permissions",
										key.getApp(), -1);
									ret.addTo(g);
								}
								g.getPermissions().addPermission(
									new prisms.arch.Permission(key.getApp(), key
										.getViewPermission(), "Key Generated"));
							}
							else
							{
								boolean edit = Boolean.TRUE.equals(o.get("canEdit"));
								interpreter.setUserAccess(ret, true, edit);
							}
							return ret;
						}
						log.error("Unrecognized user: " + o);
						return null;
					}

					public User removed(User o, int idx, int incMod, int retIdx)
					{
						if(key.canView(o))
							interpreter.setUserAccess(o, false, false);
						return null;
					}

					public User set(User o1, int idx1, int incMod, JSONObject o2, int idx2,
						int retIdx)
					{
						if(Boolean.FALSE.equals(o2.get("canView")))
						{
							if(key.canView(o1))
								interpreter.setUserAccess(o1, false, false);
						}
						else if(Boolean.TRUE.equals(o2.get("canEdit")))
						{
							if(!key.canEdit(o1))
								interpreter.setUserAccess(o1, true, true);
						}
						else if(o2.containsKey("canView"))
						{
							if(o2.containsKey("canEdit"))
							{
								if(!key.canView(o1) || key.canEdit(o1))
									interpreter.setUserAccess(o1, true, false);
							}
							else
							{
								if(!key.canView(o1))
									interpreter.setUserAccess(o1, true, false);
							}
						}
						return o1;
					}
				});
		}
	}

	/**
	 * Interprets an event from the client that may change the way an object is shared
	 * 
	 * @param key The key of the shared object. This will not be modified by the method itself, but
	 *        may be modified by callbacks to the interpreter
	 * @param evt The event from the client
	 * @param users All users available in PRISMS
	 * @param interpreter The interpreter to modify the key and perform other
	 *        implementation-specific operations
	 * @return Whether the given event was, in fact, an event targeted at the share key
	 */
	public static boolean interpretKeyEvent(prisms.util.persisters.UserShareKey key,
		JSONObject evt, User [] users, EventInterpreter interpreter)
	{
		if("setViewPublic".equals(evt.get("method")))
		{
			boolean isPublic = ((Boolean) evt.get("viewPublic")).booleanValue();
			if(key.isViewPublic() != isPublic)
				interpreter.setPublic(isPublic, false);
		}
		else if("setEditPublic".equals(evt.get("method")))
		{
			boolean isPublic = ((Boolean) evt.get("editPublic")).booleanValue();
			if(key.isEditPublic() != isPublic)
				interpreter.setPublic(true, isPublic);
		}
		else if("setUserAccess".equals(evt.get("method")))
		{
			JSONObject jsonUser = (JSONObject) evt.get("user");
			long userID = Long.valueOf((String) jsonUser.get("id"), 16).longValue();
			User user = null;
			for(User u : users)
				if(u.getID() == userID)
				{
					user = u;
					break;
				}
			if(user == null)
				throw new IllegalArgumentException("No such user with ID " + userID);

			boolean canView = ((Boolean) jsonUser.get("canView")).booleanValue();
			boolean canEdit = ((Boolean) jsonUser.get("canEdit")).booleanValue();
			if(key.canView(user) != canView || key.canEdit(user) != canEdit)
				interpreter.setUserAccess(user, canView, canEdit);
		}
		else
			return false;
		return true;
	}
}
