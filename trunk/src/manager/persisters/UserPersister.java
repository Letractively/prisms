/**
 * AppPersister.java Created Jul 10, 2008 by Andrew Butler, PSL
 */
package manager.persisters;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsException;
import prisms.arch.PrismsSession;
import prisms.arch.ds.User;
import prisms.arch.event.PrismsEvent;
import prisms.arch.event.PrismsPCE;

/** Retrieves all users from the user source */
public class UserPersister extends prisms.util.persisters.ListPersister<User>
{
	private PrismsApplication theManager;

	private PrismsApplication [] theApps;

	private boolean isLoaded;

	/** Creates a user persister */
	public UserPersister()
	{
		theApps = new PrismsApplication [0];
	}

	@Override
	public void configure(prisms.arch.PrismsConfig config, PrismsApplication app,
		prisms.arch.event.PrismsProperty<User []> property)
	{
		super.configure(config, app, property);
		theApps = prisms.util.ArrayUtils.add(theApps, app);
		if(app.getEnvironment().isManager(app))
			theManager = app;
		else if(theManager == null)
			throw new IllegalStateException("A " + getClass().getName()
				+ " must be configured in the" + " manager application first");
	}

	@Override
	protected boolean equivalent(User po, User avo)
	{
		return po.equals(avo);
	}

	@Override
	protected User clone(User toClone)
	{
		return toClone;
	}

	@Override
	public void reload()
	{
		// Don't reload the users
	}

	@Override
	protected User [] depersist()
	{
		if(isLoaded)
			return getValue();
		try
		{
			User [] ret = getApp().getEnvironment().getUserSource().getActiveUsers();
			isLoaded = true;
			return ret;
		} catch(PrismsException e)
		{
			throw new IllegalStateException("Could not retrieve users", e);
		}
	}

	@Override
	public void setValue(PrismsSession session, Object [] value,
		@SuppressWarnings("rawtypes") PrismsPCE evt)
	{
		User [] oldVal = getValue();
		super.setValue(session, value, evt);
		User [] newVal = getValue();
		if(prisms.util.ArrayUtils.equals(oldVal, newVal))
			return;
		for(PrismsApplication app : theApps)
			if(session != null && app != session.getApp())
				app.setGlobalProperty(getProperty(), newVal);
	}

	@Override
	protected User add(PrismsSession session, User newValue, PrismsPCE<User []> evt)
	{
		prisms.arch.ds.UserSource ds = getApp().getEnvironment().getUserSource();
		try
		{
			if(newValue.getID() >= 0 && ds.getUser(newValue.getID()) != null)
				return newValue; // User is already added
		} catch(PrismsException e)
		{
			throw new IllegalStateException("Could not query for new user", e);
		}
		if(session == null)
			throw new IllegalArgumentException("Users cannot be modified through"
				+ " a global operation");
		if(!session.getUser().getPermissions(theManager).has("createUser"))
			throw new IllegalArgumentException("User " + session.getUser()
				+ " does not have permission to create users");
		prisms.records.RecordsTransaction trans = prisms.records.RecordUtils.getTransaction(
			session, evt, theManager.getEnvironment().getUserSource());
		if(trans == null)
			return newValue;
		if(ds instanceof prisms.arch.ds.ManageableUserSource)
		{
			try
			{
				((prisms.arch.ds.ManageableUserSource) ds).putUser(newValue, trans);
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not add user", e);
			}
			return newValue;
		}
		return null;
	}

	@Override
	protected void remove(PrismsSession session, User removed, PrismsPCE<User []> evt)
	{
		prisms.arch.ds.UserSource ds = getApp().getEnvironment().getUserSource();
		try
		{
			User check = ds.getUser(removed.getID());
			if(check == null || check.isDeleted())
				return;
		} catch(PrismsException e)
		{
			throw new IllegalStateException("Could not query for deleted user", e);
		}
		if(session == null)
			throw new IllegalArgumentException("Users cannot be modified through"
				+ " a global operation");
		if(!manager.app.ManagerUtils.canEdit(session.getUser().getPermissions(theManager),
			removed.getPermissions(theManager)))
			throw new IllegalArgumentException("User " + session.getUser()
				+ " does not have permission to delete user " + removed);
		prisms.records.RecordsTransaction trans = prisms.records.RecordUtils.getTransaction(
			session, evt, theManager.getEnvironment().getUserSource());
		if(trans == null)
			return;
		if(ds instanceof prisms.arch.ds.ManageableUserSource)
		{
			try
			{
				((prisms.arch.ds.ManageableUserSource) ds).deleteUser(removed, trans);
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not add user", e);
			}
		}
	}

	@Override
	protected void update(PrismsSession session, User dbValue, User availableValue, PrismsEvent evt)
	{
		if(session == null)
			throw new IllegalArgumentException("Users cannot be modified through"
				+ " a global operation");
		if(!Boolean.TRUE.equals(evt.getProperty(PrismsApplication.GLOBALIZED_EVENT_PROPERTY))
			&& !manager.app.ManagerUtils.canEdit(session.getUser().getPermissions(theManager),
				availableValue.getPermissions(theManager)))
			throw new IllegalArgumentException("User " + session.getUser()
				+ " does not have permission to modify user " + availableValue);
		prisms.arch.ds.UserSource ds = getApp().getEnvironment().getUserSource();
		prisms.records.RecordsTransaction trans = prisms.records.RecordUtils.getTransaction(
			session, evt, theManager.getEnvironment().getUserSource());
		if(trans == null)
			return;
		if(ds instanceof prisms.arch.ds.ManageableUserSource)
		{
			try
			{
				((prisms.arch.ds.ManageableUserSource) ds).putUser(availableValue, trans);
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not add user", e);
			}
		}
		evt.setProperty("responsibleApp", session.getApp());
		for(PrismsApplication app : theApps)
			if(app != session.getApp())
				app.fireGlobally(null, evt);
	}
}
