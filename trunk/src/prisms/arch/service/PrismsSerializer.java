/*
 * PrismsSerializer.java Created Dec 3, 2009 by Andrew Butler, PSL
 */
package prisms.arch.service;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.*;
import prisms.impl.SimpleGroup;
import prisms.impl.SimplePermission;
import prisms.impl.SimpleUser;

/**
 * Serializes PRISMS objects for the web service
 */
public class PrismsSerializer
{
	/**
	 * Serializes a user to JSON
	 * 
	 * @param user The user to serialize
	 * @return The JSON-serialized user
	 */
	public static JSONObject serializeUser(SimpleUser user)
	{
		if(user == null)
			return null;
		JSONObject ret = new JSONObject();
		ret.put("name", user.getName());
		if(user.getRootUser() != null)
			ret.put("root", serializeUser(user.getRootUser()));
		ret.put("app", user.getApp().getName());
		if(user.getValidator() instanceof prisms.impl.PlaceholderValidator)
			ret.put("validator", ((prisms.impl.PlaceholderValidator) user.getValidator())
				.getValidatorClassName());
		else if(user.getValidator() != null)
			ret.put("validator", user.getValidator().getClass().getName());
		ret.put("encryptionRequired", new Boolean(user.isEncryptionRequired()));
		ret.put("locked", new Boolean(user.isLocked()));
		return ret;
	}

	/**
	 * Deserializes a user from JSON
	 * 
	 * @param json The JSON-serialized user
	 * @param source The user source to set for the user
	 * @param app The application for the user, if configured in the JSON
	 * @return The deserialized user
	 */
	public static SimpleUser deserializeUser(JSONObject json, UserSource source,
		PrismsApplication app)
	{
		if(json == null)
			return null;
		SimpleUser ret = new SimpleUser(
			deserializeUser((JSONObject) json.get("root"), source, app), app.getName().equals(
				json.get("app")) ? app : null);
		String valClass = (String) json.get("validator");
		if(valClass != null)
		{
			try
			{
				prisms.arch.Validator validator = (prisms.arch.Validator) Class.forName(valClass)
					.newInstance();
				ret.setValidator(validator);
			} catch(Throwable e)
			{
				ret.setValidator(new prisms.impl.PlaceholderValidator(valClass));
			}
		}
		ret.setEncryptionRequired(((Boolean) json.get("encryptionRequired")).booleanValue());
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
			ret.add(serializeUser((SimpleUser) user));
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
			ret[u] = deserializeUser((JSONObject) json.get(u), source, null);
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
		SimpleGroup ret = new SimpleGroup(source, (String) json.get("name"), app);
		ret.setDescription((String) json.get("description"));
		prisms.impl.SimplePermissions perms = new prisms.impl.SimplePermissions();
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
		return new SimplePermission((String) json.get("name"), (String) json.get("description"),
			app);
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
