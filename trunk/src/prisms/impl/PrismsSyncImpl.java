/*
 * PrismsSyncImpl.java Created Dec 3, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import java.io.IOException;
import java.sql.Statement;

import org.json.simple.JSONObject;

import prisms.arch.Permission;
import prisms.arch.PrismsApplication;
import prisms.arch.PrismsException;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;
import prisms.records.*;
import prisms.util.json.JsonSerialWriter;

/** Implements record-keeping and synchronization for users in PRISMS */
public class PrismsSyncImpl implements RecordPersister, SynchronizeImpl, ScaleImpl
{
	private final prisms.arch.ds.ManageableUserSource theUserSource;

	final PrismsApplication [] theApps;

	/**
	 * Creates a record-keeping/synchronization implementation
	 * 
	 * @param userSource The user source that this impl uses to retrieve, create, modify and delete
	 *        users
	 * @param apps All applications available in this PRISMS environment
	 */
	public PrismsSyncImpl(prisms.arch.ds.ManageableUserSource userSource, PrismsApplication [] apps)
	{
		theUserSource = userSource;
		theApps = apps;
	}

	/** @return This impl's user source */
	public prisms.arch.ds.ManageableUserSource getUserSource()
	{
		return theUserSource;
	}

	public String getVersion()
	{
		return "3.0.1";
	}

	public boolean shouldSend(ChangeRecord change)
	{
		return true;
	}

	public User getUser(long id) throws PrismsRecordException
	{
		User ret;
		try
		{
			ret = theUserSource.getUser(id);
		} catch(PrismsException e)
		{
			throw new PrismsRecordException("Could not get user", e);
		}
		if(ret == null)
			throw new PrismsRecordException("No such user with ID " + id);
		return ret;
	}

	public SubjectType getSubjectType(String typeName) throws PrismsRecordException
	{
		for(PrismsSubjectType pst : PrismsSubjectType.values())
			if(pst.name().equals(typeName))
				return pst;
		throw new PrismsRecordException("No such PRISMS subject type " + typeName);
	}

	public long getID(Object item)
	{
		if(item instanceof User)
			return ((User) item).getID();
		else if(item instanceof PrismsApplication)
			return ((PrismsApplication) item).getName().hashCode();
		else if(item instanceof UserGroup)
			return ((UserGroup) item).getID();
		else if(item instanceof Permission)
			return ((Permission) item).getName().hashCode();
		else
			throw new IllegalStateException("Unrecognized PRISMS type: "
				+ item.getClass().getName());
	}

	public ChangeData getData(SubjectType subjectType, ChangeType changeType, Object majorSubject,
		Object minorSubject, Object data1, Object data2, Object preValue)
		throws PrismsRecordException
	{
		return getData(subjectType, changeType, majorSubject, minorSubject, data1, data2, preValue,
			new ItemGetter()
			{
				public Object getItem(String type, long id) throws PrismsRecordException
				{
					throw new PrismsRecordException("No such item " + type + "/" + id
						+ " in PRISMS");
				}
			});
	}

	public ChangeData getData(SubjectType subjectType, ChangeType changeType, Object majorSubject,
		Object minorSubject, Object data1, Object data2, Object preValue, ItemGetter getter)
		throws PrismsRecordException
	{
		switch((PrismsSubjectType) subjectType)
		{
		case user:
			User user;
			if(majorSubject instanceof User)
				user = (User) majorSubject;
			else
				try
				{
					user = theUserSource.getUser(((Number) majorSubject).longValue());
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not get user " + majorSubject, e);
				}
			if(user == null)
				user = (User) getter.getItem("user", ((Number) majorSubject).longValue());
			if(changeType == null)
				return new ChangeData(user, null, null, null, null);
			switch((PrismsChangeTypes.UserChange) changeType)
			{
			case name:
			case locked:
			case admin:
			case readOnly:
				return new ChangeData(user, null, null, null, preValue);
			case appAccess:
				PrismsApplication app;
				if(minorSubject instanceof PrismsApplication)
					app = (PrismsApplication) minorSubject;
				else
					app = getApp(((Number) minorSubject).intValue());
				return new ChangeData(user, app, null, null, preValue);
			case group:
				UserGroup group;
				if(minorSubject instanceof UserGroup)
					group = (UserGroup) minorSubject;
				else
					try
					{
						group = theUserSource.getGroup(((Number) minorSubject).longValue());
					} catch(PrismsException e)
					{
						throw new PrismsRecordException("Could not get group " + minorSubject, e);
					}
				return new ChangeData(user, group, null, null, null);
			}
			throw new PrismsRecordException("Unrecognized user change type: " + changeType);
		case group:
			UserGroup group;
			if(majorSubject instanceof UserGroup)
				group = (UserGroup) majorSubject;
			else
				try
				{
					group = theUserSource.getGroup(((Number) majorSubject).longValue());
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not get group " + minorSubject, e);
				}
			if(group == null)
				group = (UserGroup) getter.getItem("group", ((Number) majorSubject).longValue());
			if(changeType == null)
				return new ChangeData(group, null, group.getApp(), null, null);
			switch((PrismsChangeTypes.GroupChange) changeType)
			{
			case name:
			case descrip:
				return new ChangeData(group, null, group.getApp(), null, preValue);
			case permission:
				Permission perm;
				if(minorSubject instanceof Permission)
					perm = (Permission) minorSubject;
				else
				{
					perm = null;
					for(Permission p : group.getApp().getPermissions())
						if(p.hashCode() == ((Number) minorSubject).intValue())
							perm = p;
					if(perm == null)
						throw new PrismsRecordException("No such permission in app "
							+ group.getApp());
				}
				return new ChangeData(group, perm, group.getApp(), null, null);
			}
		}
		throw new PrismsRecordException("Unrecognized PRISMS subject type: " + subjectType);
	}

	private PrismsApplication getApp(int id)
	{
		for(PrismsApplication app : theApps)
			if(app.getName().hashCode() == id)
				return app;
		return null;
	}

	public SubjectType [] getAllSubjectTypes() throws PrismsRecordException
	{
		return PrismsSubjectType.values();
	}

	public SubjectType [] getHistoryDomains(Object value) throws PrismsRecordException
	{
		if(value instanceof User)
			return new SubjectType [] {PrismsSubjectType.user};
		else if(value instanceof UserGroup)
			return new SubjectType [] {PrismsSubjectType.group};
		else
			return new SubjectType [0];
	}

	public String serializePreValue(ChangeRecord change) throws PrismsRecordException
	{
		throw new PrismsRecordException("Unrecognized serializable PRISMS type: "
			+ change.previousValue.getClass().getName());
	}

	public void checkItemForDelete(Object item, Statement stmt) throws PrismsRecordException
	{
		if(item instanceof User)
		{
			User u = (User) item;
			if(u.isDeleted())
			{
				try
				{
					if(theUserSource instanceof DBUserSource)
						((DBUserSource) theUserSource).purgeUser(u, stmt);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not purge user " + item, e);
				}
			}
		}
		else if(item instanceof UserGroup)
		{
			UserGroup g = (UserGroup) item;
			if(g.isDeleted())
			{
				try
				{
					if(theUserSource instanceof DBUserSource)
						((DBUserSource) theUserSource).purgeGroup(g, stmt);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not purge user group " + item, e);
				}
			}
		}
	}

	public User getUser(long id, ItemGetter getter) throws PrismsRecordException
	{
		User ret;
		try
		{
			ret = theUserSource.getUser(id);
		} catch(PrismsException e)
		{
			throw new PrismsRecordException("Could not get user with ID " + id, e);
		}
		if(ret == null)
			ret = (User) getter.getItem("user", id);
		return ret;
	}

	public String getType(Class<?> type) throws PrismsRecordException
	{
		if(User.class.equals(type))
			return "user";
		else if(PrismsApplication.class.equals(type))
			return "app";
		else if(UserGroup.class.equals(type))
			return "group";
		else if(Permission.class.equals(type))
			return "permission";
		else
			return null;
	}

	public Object [] getDepends(Object item) throws PrismsRecordException
	{
		return new Object [0];
	}

	public ItemIterator getAllItems(int [] centerIDs, PrismsCenter syncCenter)
		throws PrismsRecordException
	{
		return new PrismsItemIterator(centerIDs);
	}

	private enum ItemIterState
	{
		GROUP, USER;

		ItemIterState next()
		{
			int nextIdx = ordinal() + 1;
			ItemIterState [] vals = values();
			if(nextIdx == vals.length)
				return null;
			return vals[nextIdx];
		}
	}

	private class PrismsItemIterator implements ItemIterator
	{
		private ItemIterState theState;

		private UserGroup [] theGroups;

		private User [] theUsers;

		private int theIndex;

		PrismsItemIterator(int [] centerIDs) throws PrismsRecordException
		{
			java.util.ArrayList<UserGroup> groups = new java.util.ArrayList<UserGroup>();
			try
			{
				for(PrismsApplication app : theApps)
					for(UserGroup g : getUserSource().getGroups(app))
						groups.add(g);
			} catch(PrismsException e)
			{
				throw new PrismsRecordException("Could not get all groups", e);
			}
			theGroups = groups.toArray(new UserGroup [groups.size()]);

			java.util.ArrayList<User> users = new java.util.ArrayList<User>();
			try
			{
				for(User user : getUserSource().getAllUsers())
					if(!user.isDeleted() && hasID(centerIDs, user.getID()))
						users.add(user);
			} catch(PrismsException e)
			{
				throw new PrismsRecordException("Could not get all users", e);
			}
			theUsers = users.toArray(new User [users.size()]);

			theState = ItemIterState.GROUP;
		}

		private boolean hasID(int [] centerIDs, long id)
		{
			int centerID = prisms.arch.ds.IDGenerator.getCenterID(id);
			for(int cid : centerIDs)
				if(cid == centerID)
					return true;
			return false;
		}

		public boolean hasNext() throws PrismsRecordException
		{
			if(theState == null)
				return false;
			switch(theState)
			{
			case GROUP:
				if(theIndex < theGroups.length)
					return true;
				theState = theState.next();
				theIndex = 0;
				//$FALL-THROUGH$
			case USER:
				if(theIndex < theUsers.length)
					return true;
				theState = theState.next();
			}
			return false;
		}

		public Object next() throws PrismsRecordException
		{
			switch(theState)
			{
			case GROUP:
				UserGroup group = theGroups[theIndex];
				theIndex++;
				return group;
			case USER:
				User user = theUsers[theIndex];
				theIndex++;
				return user;
			}
			throw new IllegalStateException("Unrecognized item iterator state");
		}
	}

	public void writeItem(Object item, JsonSerialWriter jsonWriter, ItemWriter itemWriter,
		boolean justID) throws IOException, PrismsRecordException
	{
		if(item instanceof User)
		{
			User user = (User) item;
			if(user.isDeleted())
			{
				jsonWriter.startProperty("deleted");
				jsonWriter.writeBoolean(true);
			}
			if(justID)
				return;
			jsonWriter.startProperty("name");
			jsonWriter.writeString(user.getName());
			jsonWriter.startProperty("admin");
			jsonWriter.writeBoolean(user.isAdmin());
			jsonWriter.startProperty("readOnly");
			jsonWriter.writeBoolean(user.isReadOnly());

			jsonWriter.startProperty("groups");
			jsonWriter.startArray();
			for(UserGroup g : user.getGroups())
				itemWriter.writeItem(g);
			jsonWriter.endArray();
		}
		else if(item instanceof PrismsApplication)
		{
			PrismsApplication app = (PrismsApplication) item;
			jsonWriter.startProperty("name");
			jsonWriter.writeString(app.getName());
		}
		else if(item instanceof UserGroup)
		{
			UserGroup group = (UserGroup) item;
			jsonWriter.startProperty("application");
			itemWriter.writeItem(group.getApp());
			if(group.isDeleted())
			{
				jsonWriter.startProperty("deleted");
				jsonWriter.writeBoolean(true);
			}
			if(justID)
				return;
			jsonWriter.startProperty("name");
			jsonWriter.writeString(group.getName());
			jsonWriter.startProperty("descrip");
			jsonWriter.writeString(group.getDescription());

			jsonWriter.startProperty("permissions");
			jsonWriter.startArray();
			for(Permission p : group.getPermissions().getAllPermissions())
				itemWriter.writeItem(p);
			jsonWriter.endArray();
		}
		else if(item instanceof Permission)
		{
			Permission perm = (Permission) item;
			jsonWriter.startProperty("application");
			itemWriter.writeItem(perm.getApp());
			if(justID)
				return;
			jsonWriter.startProperty("name");
			jsonWriter.writeString(perm.getName());
			jsonWriter.startProperty("descrip");
			jsonWriter.writeString(perm.getDescrip());
		}
		else
			throw new PrismsRecordException("Unrecognized PRISMS item type: "
				+ item.getClass().getName());
	}

	public Object parseID(JSONObject json, ItemReader reader, boolean [] newItem)
		throws PrismsRecordException
	{
		String type = (String) json.get("type");
		long id;
		{
			Number idObj = (Number) json.get("id");
			if(idObj != null)
				id = idObj.longValue();
			else
				id = -1;
		}
		if("user".equals(type))
		{
			User ret;
			try
			{
				ret = theUserSource.getUser(id);
			} catch(PrismsException e)
			{
				throw new PrismsRecordException("Could not get user with ID " + id, e);
			}
			if(ret != null)
				return ret;
			newItem[0] = true;
			ret = new User(theUserSource, (String) json.get("name"), id);
			if(Boolean.TRUE.equals(json.get("deleted")))
				ret.setDeleted(true);
			try
			{
				theUserSource.putUser(ret, null);
			} catch(PrismsException e)
			{
				throw new PrismsRecordException("Could not add user " + ret, e);
			}
			return ret;
		}
		else if("app".equals(type))
		{
			PrismsApplication app = getApp((int) id);
			return app;
		}
		else if("group".equals(type))
		{
			UserGroup ret;
			try
			{
				ret = theUserSource.getGroup(id);
			} catch(PrismsException e)
			{
				throw new PrismsRecordException("Could not get group with ID " + id, e);
			}
			if(ret != null)
				return ret;
			newItem[0] = true;
			PrismsApplication app = (PrismsApplication) reader.read((JSONObject) json
				.get("application"));
			ret = new UserGroup(theUserSource, (String) json.get("name"), app, id);
			if(Boolean.TRUE.equals(json.get("deleted")))
				ret.setDeleted(true);
			try
			{
				theUserSource.putGroup(ret, null);
			} catch(PrismsException e)
			{
				throw new PrismsRecordException("Could not add group " + ret, e);
			}
			return ret;
		}
		else if("permission".equals(type))
		{
			PrismsApplication app = (PrismsApplication) reader.read((JSONObject) json
				.get("application"));
			String name = (String) json.get("name");
			Permission ret = null;
			for(Permission p : app.getPermissions())
				if(p.getName().equals(name))
				{
					ret = p;
					break;
				}
			if(ret == null)
				ret = new Permission(app, name, (String) json.get("descrip"));
			return ret;
		}
		else
			throw new PrismsRecordException("Unrecognized PRISMS item type: " + type);
	}

	public void parseContent(Object item, JSONObject json, boolean newItem, ItemReader reader)
		throws PrismsRecordException
	{
		if(item instanceof User)
		{
			final User user = (User) item;
			final boolean [] changed = new boolean [] {false};
			String name = (String) json.get("name");
			if(!user.getName().equals(name))
			{
				changed[0] = true;
				user.setName(name);
			}
			boolean admin = Boolean.TRUE.equals(json.get("admin"));
			if(admin != user.isAdmin())
			{
				changed[0] = true;
				user.setAdmin(admin);
			}
			boolean readOnly = Boolean.TRUE.equals(json.get("readOnly"));
			if(readOnly != user.isReadOnly())
			{
				changed[0] = true;
				user.setReadOnly(readOnly);
			}

			org.json.simple.JSONArray jsonGroups = (org.json.simple.JSONArray) json.get("groups");
			UserGroup [] groups = new UserGroup [jsonGroups.size()];
			for(int p = 0; p < groups.length; p++)
				groups[p] = (UserGroup) reader.read((JSONObject) jsonGroups.get(p));
			prisms.util.ArrayUtils.adjust(user.getGroups(), groups,
				new prisms.util.ArrayUtils.DifferenceListener<UserGroup, UserGroup>()
				{
					public boolean identity(UserGroup o1, UserGroup o2)
					{
						return o1.equals(o2);
					}

					public UserGroup added(UserGroup o, int mIdx, int retIdx)
					{
						user.addTo(o);
						changed[0] = true;
						return null;
					}

					public UserGroup removed(UserGroup o, int oIdx, int incMod, int retIdx)
					{
						user.removeFrom(o);
						changed[0] = true;
						return null;
					}

					public UserGroup set(UserGroup o1, int idx1, int incMod, UserGroup o2,
						int idx2, int retIdx)
					{
						return null;
					}
				});

			if(changed[0])
				try
				{
					theUserSource.putUser(user,
						new RecordsTransaction(theUserSource.getSystemUser(), false));
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not modify user", e);
				}
		}
		else if(item instanceof PrismsApplication)
		{}
		else if(item instanceof UserGroup)
		{
			final UserGroup group = (UserGroup) item;
			final boolean [] changed = new boolean [] {false};
			String name = (String) json.get("name");
			if(!group.getName().equals(name))
			{
				changed[0] = true;
				group.setName(name);
			}
			String descrip = (String) json.get("descrip");
			if(!group.getDescription().equals(descrip))
			{
				changed[0] = true;
				group.setDescription(descrip);
			}
			org.json.simple.JSONArray jsonPerms = (org.json.simple.JSONArray) json
				.get("permissions");
			Permission [] perms = new Permission [jsonPerms.size()];
			for(int p = 0; p < perms.length; p++)
				perms[p] = (Permission) reader.read((JSONObject) jsonPerms.get(p));
			prisms.util.ArrayUtils.adjust(group.getPermissions().getAllPermissions(), perms,
				new prisms.util.ArrayUtils.DifferenceListener<Permission, Permission>()
				{
					public boolean identity(Permission o1, Permission o2)
					{
						return o1.equals(o2);
					}

					public Permission added(Permission o, int mIdx, int retIdx)
					{
						group.getPermissions().addPermission(o);
						changed[0] = true;
						return null;
					}

					public Permission removed(Permission o, int oIdx, int incMod, int retIdx)
					{
						group.getPermissions().removePermission(o.getName());
						changed[0] = true;
						return null;
					}

					public Permission set(Permission o1, int idx1, int incMod, Permission o2,
						int idx2, int retIdx)
					{
						return null;
					}
				});
			if(changed[0])
				try
				{
					theUserSource.putGroup(group,
						new RecordsTransaction(theUserSource.getSystemUser(), false));
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not modify group", e);
				}
		}
		else if(item instanceof Permission)
		{}
		else
			throw new PrismsRecordException("Unrecognized PRISMS item type: "
				+ item.getClass().getName());
	}

	public void delete(Object item, SyncRecord syncRecord) throws PrismsRecordException
	{
		if(item instanceof User)
		{
			try
			{
				theUserSource.deleteUser((User) item, null);
			} catch(PrismsException e)
			{
				throw new PrismsRecordException("Could not delete user " + item, e);
			}
		}
		else if(item instanceof UserGroup)
		{
			try
			{
				theUserSource.deleteGroup((UserGroup) item, null);
			} catch(PrismsException e)
			{
				throw new PrismsRecordException("Could not delete group " + item, e);
			}
		}
		else
			throw new PrismsRecordException("Unrecognized PRISMS item type: "
				+ item.getClass().getName());
	}

	public void doChange(ChangeRecord change, Object currentValue) throws PrismsRecordException
	{
		doChange(change, currentValue, false);
	}

	private void doChange(ChangeRecord change, Object currentValue, boolean memOnly)
		throws PrismsRecordException
	{
		RecordsTransaction trans;
		if(memOnly)
			trans = new RecordsTransaction();
		else
			trans = new RecordsTransaction(null, false);
		if(change.type.subjectType instanceof PrismsChange)
		{
			RecordUtils.doPrismsChange(change, currentValue, theUserSource.getRecordKeeper(),
				trans, (prisms.arch.event.PrismsProperty<PrismsCenter []>) null);
			return;
		}
		switch((PrismsSubjectType) change.type.subjectType)
		{
		case user:
			User user = (User) change.majorSubject;
			if(change.type.changeType == null)
			{
				try
				{
					if(change.type.additivity > 0)
					{
						if(memOnly)
							theUserSource.putUser(user, trans);
						else
						{/* We already added the user in the parse methods*/}
					}
					else
						theUserSource.deleteUser(user, trans);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not add/delete user " + user, e);
				}
				return;
			}
			boolean changed = true;
			boolean switchHit = false;
			switch((PrismsChangeTypes.UserChange) change.type.changeType)
			{
			case name:
				switchHit = true;
				user.setName((String) currentValue);
				break;
			case locked:
				switchHit = true;
				user.setLocked(((Boolean) currentValue).booleanValue());
				break;
			case admin:
				switchHit = true;
				user.setAdmin(((Boolean) currentValue).booleanValue());
				break;
			case readOnly:
				switchHit = true;
				user.setReadOnly(((Boolean) currentValue).booleanValue());
				break;
			case appAccess:
				switchHit = true;
				changed = false;
				PrismsApplication app = (PrismsApplication) change.minorSubject;
				try
				{
					theUserSource.setUserAccess(user, app, change.type.additivity > 0, trans);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not set user " + user
						+ "'s access to app " + app, e);
				}
				break;
			case group:
				switchHit = true;
				if(change.type.additivity > 0)
					user.addTo((UserGroup) change.minorSubject);
				else
					user.removeFrom((UserGroup) change.minorSubject);
				break;
			}
			if(!switchHit)
				throw new PrismsRecordException("Unrecognized user change type: "
					+ change.type.changeType);
			if(changed)
				try
				{
					theUserSource.putUser(user, trans);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not update user " + user, e);
				}
			return;
		case group:
			UserGroup group = (UserGroup) change.majorSubject;
			if(change.type.changeType == null)
			{
				try
				{
					if(change.type.additivity > 0)
					{
						if(memOnly)
							theUserSource.putGroup(group, trans);
						else
						{/* We already added the group in the parse methods*/}
					}
					else
						theUserSource.deleteGroup(group, trans);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not add/delete group " + group, e);
				}
				return;
			}
			changed = true;
			switchHit = false;
			switch((PrismsChangeTypes.GroupChange) change.type.changeType)
			{
			case name:
				switchHit = true;
				group.setName((String) currentValue);
				break;
			case descrip:
				switchHit = true;
				group.setDescription((String) currentValue);
				break;
			case permission:
				switchHit = true;
				if(change.type.additivity > 0)
					group.getPermissions().addPermission(
						(prisms.arch.Permission) change.minorSubject);
				else
					group.getPermissions().removePermission(
						((prisms.arch.Permission) change.minorSubject).getName());
				break;
			}
			if(changed)
				try
				{
					theUserSource.putGroup(group, trans);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not update group " + group, e);
				}
			return;
		}
		throw new PrismsRecordException("Unrecognized PRISMS subject type: "
			+ change.type.subjectType);
	}

	public Object getCurrentValue(ChangeRecord change) throws PrismsRecordException
	{
		switch((PrismsSubjectType) change.type.subjectType)
		{
		case user:
			User user = (User) change.majorSubject;
			switch((PrismsChangeTypes.UserChange) change.type.changeType)
			{
			case name:
				return user.getName();
			case locked:
				return Boolean.valueOf(user.isLocked());
			case admin:
				return Boolean.valueOf(user.isAdmin());
			case readOnly:
				return Boolean.valueOf(user.isReadOnly());
			case appAccess:
				return null;
			case group:
				return null;
			}
			throw new PrismsRecordException("Unrecognized user change type: "
				+ change.type.changeType);
		case group:
			UserGroup group = (UserGroup) change.majorSubject;
			switch((PrismsChangeTypes.GroupChange) change.type.changeType)
			{
			case name:
				return group.getName();
			case descrip:
				return group.getDescription();
			case permission:
				return null;
			}
		}
		throw new PrismsRecordException("Unrecognized PRISMS subject type: "
			+ change.type.subjectType);
	}

	// ScaleImpl methods

	public Object getDBCurrentValue(ChangeRecord record) throws PrismsRecordException
	{
		if(!(theUserSource instanceof ScalableUserSource))
			throw new PrismsRecordException("User source is not scalable");
		try
		{
			return ((ScalableUserSource) theUserSource).getDBValue(record);
		} catch(PrismsException e)
		{
			throw new PrismsRecordException("Could not get databased field value", e);
		}
	}

	public void doMemChange(ChangeRecord record, Object currentValue) throws PrismsRecordException
	{
		doChange(record, currentValue, true);
	}
}
