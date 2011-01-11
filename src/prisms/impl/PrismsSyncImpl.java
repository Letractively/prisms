/*
 * PrismsSyncImpl.java Created Dec 3, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import java.io.IOException;
import java.sql.Statement;

import org.json.simple.JSONObject;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsException;
import prisms.arch.ds.User;
import prisms.records2.*;
import prisms.util.json.JsonSerialWriter;

/** Implements record-keeping and synchronization for users in PRISMS */
public class PrismsSyncImpl implements RecordPersister2, Synchronize2Impl
{
	private final prisms.arch.ds.ManageableUserSource theUserSource;

	private final PrismsApplication [] theApps;

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
			}
			throw new PrismsRecordException("Unrecognized user change type: " + changeType);
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
		else
			return new SubjectType [0];
	}

	public String serialize(Object data) throws PrismsRecordException
	{
		throw new PrismsRecordException("Unrecognized serializable PRISMS type: "
			+ data.getClass().getName());
	}

	public void checkItemForDelete(Object item, Statement stmt) throws PrismsRecordException
	{
		if(item instanceof User)
		{
			User u = (User) item;
			if(u.isDeleted())
			{
				// TODO Purge the user
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

	private class PrismsItemIterator implements ItemIterator
	{
		private User [] theUsers;

		private int theIndex;

		PrismsItemIterator(int [] centerIDs) throws PrismsRecordException
		{
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
			return theIndex < theUsers.length;
		}

		public Object next() throws PrismsRecordException
		{
			User ret = theUsers[theIndex];
			theIndex++;
			return ret;
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
		}
		else if(item instanceof PrismsApplication)
		{
			PrismsApplication app = (PrismsApplication) item;
			jsonWriter.startProperty("name");
			jsonWriter.writeString(app.getName());
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
		else
			throw new PrismsRecordException("Unrecognized PRISMS item type: " + type);
	}

	public void parseContent(Object item, JSONObject json, boolean newItem, ItemReader reader)
		throws PrismsRecordException
	{
		if(item instanceof User)
		{
			User user = (User) item;
			boolean changed = false;
			boolean admin = Boolean.TRUE.equals(json.get("admin"));
			if(admin != user.isAdmin())
			{
				changed = true;
				user.setAdmin(admin);
			}
			boolean readOnly = Boolean.TRUE.equals(json.get("readOnly"));
			if(readOnly != user.isReadOnly())
			{
				changed = true;
				user.setReadOnly(readOnly);
			}
			if(changed)
				try
				{
					theUserSource.putUser(user, null);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not modify user", e);
				}
		}
		else if(item instanceof PrismsApplication)
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
		else
			throw new PrismsRecordException("Unrecognized PRISMS item type: "
				+ item.getClass().getName());
	}

	public void doChange(ChangeRecord change, Object currentValue, ItemReader reader)
		throws PrismsRecordException
	{
		switch((PrismsSubjectType) change.type.subjectType)
		{
		case user:
			User user = (User) change.majorSubject;
			if(change.type.changeType == null)
			{
				if(change.type.additivity > 0)
				{/* We already added the user in the parse methods*/}
				else
					try
					{
						theUserSource.deleteUser(user, null);
					} catch(PrismsException e)
					{
						throw new PrismsRecordException("Could not delete user " + user, e);
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
					theUserSource.setUserAccess(user, app, change.type.additivity > 0, null);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not set user " + user
						+ "'s access to app " + app, e);
				}
			}
			if(!switchHit)
				throw new PrismsRecordException("Unrecognized user change type: "
					+ change.type.changeType);
			if(changed)
				try
				{
					theUserSource.putUser(user, null);
				} catch(PrismsException e)
				{
					throw new PrismsRecordException("Could not update user " + user, e);
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
			case admin:
				return Boolean.valueOf(user.isAdmin());
			case readOnly:
				return Boolean.valueOf(user.isReadOnly());
			case appAccess:
				return null;
			}
			throw new PrismsRecordException("Unrecognized user change type: "
				+ change.type.changeType);
		}
		throw new PrismsRecordException("Unrecognized PRISMS subject type: "
			+ change.type.subjectType);
	}
}
