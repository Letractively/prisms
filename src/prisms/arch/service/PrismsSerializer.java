/*
 * PrismsSerializer.java Created Dec 3, 2009 by Andrew Butler, PSL
 */
package prisms.arch.service;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.Permission;
import prisms.arch.PrismsApplication;
import prisms.arch.ds.*;

/** Serializes PRISMS objects for the web service */
public class PrismsSerializer
{
	/**
	 * Serializes password constraints to JSON
	 * 
	 * @param constraints The password constraints to serialize
	 * @return The JSON-serialize password constraints
	 */
	public static JSONObject serializeConstraints(PasswordConstraints constraints)
	{
		JSONObject ret = new JSONObject();
		ret.put("minLength", new Integer(constraints.getMinCharacterLength()));
		ret.put("minUpperCase", new Integer(constraints.getMinUpperCase()));
		ret.put("minLowerCase", new Integer(constraints.getMinLowerCase()));
		ret.put("minDigits", new Integer(constraints.getMinDigits()));
		ret.put("minSpecialChars", new Integer(constraints.getMinSpecialChars()));
		ret.put("maxDuration", new Long(constraints.getMaxPasswordDuration()));
		ret.put("numUnique", new Integer(constraints.getNumPreviousUnique()));
		ret.put("minChangeInterval", new Long(constraints.getMinPasswordChangeInterval()));
		return ret;
	}

	/**
	 * Deserializes password constraints from JSON
	 * 
	 * @param constraints The JSON-serialized password constraints
	 * @return The deserialized password constraints
	 */
	public static PasswordConstraints deserializeConstraints(JSONObject constraints)
	{
		PasswordConstraints ret = new PasswordConstraints();
		ret.setMinCharacterLength(((Number) constraints.get("minLength")).intValue());
		ret.setMinUpperCase(((Number) constraints.get("minUpperCase")).intValue());
		ret.setMinLowerCase(((Number) constraints.get("minLowerCase")).intValue());
		ret.setMinDigits(((Number) constraints.get("minDigits")).intValue());
		ret.setMinSpecialChars(((Number) constraints.get("minSpecialChars")).intValue());
		ret.setMaxPasswordDuration(((Number) constraints.get("maxDuration")).longValue());
		ret.setNumPreviousUnique(((Number) constraints.get("numUnique")).intValue());
		ret.setMinPasswordChangeInterval(((Number) constraints.get("minChangeInterval"))
			.longValue());
		return ret;
	}

	/**
	 * Serializes a user to JSON
	 * 
	 * @param user The user to serialize
	 * @return The JSON-serialized user
	 */
	public static JSONObject serializeUser(User user)
	{
		if(user == null)
			return null;
		JSONObject ret = new JSONObject();
		ret.put("name", user.getName());
		ret.put("id", Long.valueOf(user.getID()));
		ret.put("locked", new Boolean(user.isLocked()));
		return ret;
	}

	/**
	 * Deserializes a user from JSON
	 * 
	 * @param json The JSON-serialized user
	 * @param source The user source to set for the user
	 * @return The deserialized user
	 */
	public static User deserializeUser(JSONObject json, UserSource source)
	{
		if(json == null)
			return null;
		User ret = new User(source, (String) json.get("name"), ((Number) json.get("id")).intValue());
		ret.setLocked(((Boolean) json.get("locked")).booleanValue());
		return ret;
	}

	/**
	 * Serializes a set of users to JSON
	 * 
	 * @param users The users to serialize
	 * @return The JSON-serialized users
	 */
	public static JSONArray serializeUsers(User [] users)
	{
		if(users == null)
			return null;
		JSONArray ret = new JSONArray();
		for(User user : users)
			ret.add(serializeUser(user));
		return ret;
	}

	/**
	 * Deserializes a set of users from JSON
	 * 
	 * @param json The JSON-serialized users
	 * @param source The user source to set for the users
	 * @return The deserialized users
	 */
	public static User [] deserializeUsers(JSONArray json, UserSource source)
	{
		if(json == null)
			return null;
		User [] ret = new User [json.size()];
		for(int u = 0; u < ret.length; u++)
			ret[u] = deserializeUser((JSONObject) json.get(u), source);
		return ret;
	}

	/**
	 * Serializes a group to JSON
	 * 
	 * @param group The group to serialize
	 * @return The JSON-serialized group
	 */
	public static JSONObject serializeGroup(UserGroup group)
	{
		if(group == null)
			return null;
		JSONObject ret = new JSONObject();
		ret.put("name", group.getName());
		ret.put("description", group.getDescription());
		ret.put("permissions", serializePermissions(group.getPermissions().getAllPermissions()));
		return ret;
	}

	/**
	 * Deserializes a group from JSON
	 * 
	 * @param json The JSON-serialized group
	 * @param source The user source for the group
	 * @param app The group's application
	 * @return The deserialized group
	 */
	public static UserGroup deserializeGroup(JSONObject json, UserSource source,
		PrismsApplication app)
	{
		if(json == null)
			return null;
		UserGroup ret = new UserGroup(source, (String) json.get("name"), app);
		ret.setDescription((String) json.get("description"));
		prisms.arch.ds.GroupPermissions perms = new prisms.arch.ds.GroupPermissions();
		for(Permission p : deserializePermissions((JSONArray) json.get("permissions"), app))
			perms.addPermission(p);
		ret.setPermissions(perms);
		return ret;
	}

	/**
	 * Serializes a set of groups to JSON
	 * 
	 * @param groups The groups to serialize
	 * @return The JSON-serialized groups
	 */
	public static JSONArray serializeGroups(UserGroup [] groups)
	{
		if(groups == null)
			return null;
		JSONArray ret = new JSONArray();
		for(UserGroup group : groups)
			ret.add(serializeGroup(group));
		return ret;
	}

	/**
	 * Deserializes a set of groups from JSON
	 * 
	 * @param json The JSON-serialized groups
	 * @param source The user source for the groups
	 * @param app The groups' application
	 * @return The deserialized groups
	 */
	public static UserGroup [] deserializeGroups(JSONArray json, UserSource source,
		PrismsApplication app)
	{
		if(json == null)
			return null;
		UserGroup [] ret = new UserGroup [json.size()];
		for(int g = 0; g < ret.length; g++)
			ret[g] = deserializeGroup((JSONObject) json.get(g), source, app);
		return ret;
	}

	/**
	 * Serializes a permission to JSON
	 * 
	 * @param permission The permission to serialize
	 * @return The JSON-serialized permission
	 */
	public static JSONObject serializePermission(Permission permission)
	{
		JSONObject ret = new JSONObject();
		ret.put("name", permission.getName());
		ret.put("description", permission.getDescrip());
		return ret;
	}

	/**
	 * Deserializes a permission from JSON
	 * 
	 * @param json The JSON-serialized permission
	 * @param app The application for the permission
	 * @return The deserialized permission
	 */
	public static Permission deserializePermission(JSONObject json, PrismsApplication app)
	{
		return new Permission(app, (String) json.get("name"), (String) json.get("description"));
	}

	/**
	 * Serializes a set of permissions
	 * 
	 * @param permissions The permissions to serialize
	 * @return The JSON-serialized permissions
	 */
	public static JSONArray serializePermissions(Permission [] permissions)
	{
		JSONArray ret = new JSONArray();
		for(Permission p : permissions)
			ret.add(serializePermission(p));
		return ret;
	}

	/**
	 * Deserializes a set of permissions from JSON
	 * 
	 * @param json The JSON-serialized permissions
	 * @param app The application for the permissions
	 * @return The deserialized permissions
	 */
	public static Permission [] deserializePermissions(JSONArray json, PrismsApplication app)
	{
		Permission [] ret = new Permission [json.size()];
		for(int p = 0; p < ret.length; p++)
			ret[p] = deserializePermission((JSONObject) json.get(p), app);
		return ret;
	}
}
