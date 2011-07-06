/*
 * PrismsUserManager.java Created Nov 3, 2010 by Andrew Butler, PSL
 */
package manager.persisters;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.ManageableUserSource;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;

/** Manages the full set of users available in PRISMS between applications */
public class PrismsUserManager extends prisms.util.persisters.PersistingPropertyManager<User []>
{
	private User [] theUsers;

	PrismsApplication theManager;

	ManageableUserSource.UserSetListener theListener;

	@Override
	public void configure(final PrismsApplication app, prisms.arch.PrismsConfig config)
	{
		super.configure(app, config);
		if(app.getEnvironment().isManager(app))
			theManager = app;
		if(theListener == null
			&& app.getEnvironment().getUserSource() instanceof ManageableUserSource)
		{
			ManageableUserSource us = (ManageableUserSource) app.getEnvironment().getUserSource();
			theListener = new ManageableUserSource.UserSetListener()
			{
				public void userSetChanged(User [] users)
				{
					setValue(users);
					globalAdjustValues();
				}

				public void userChanged(User user)
				{
					fireGlobalEvent(null, "prismsUserChanged", "user", user, "prismsPersisted",
						Boolean.TRUE);
				}

				public void userAuthorityChanged(User user)
				{
					fireGlobalEvent(null, "prismsUserAuthChanged", "user", user, "prismsPersisted",
						Boolean.TRUE);
				}

				public void groupSetChanged(PrismsApplication _app, UserGroup [] groups)
				{
					if(theManager != null)
						theManager.fireGlobally(null, new prisms.arch.event.PrismsEvent(
							"appGroupsChanged", "app", _app, "groups", groups, "prismsPersisted",
							Boolean.TRUE));
				}

				public void groupChanged(UserGroup group)
				{
					if(theManager != null)
						theManager.fireGlobally(null, new prisms.arch.event.PrismsEvent(
							"groupChanged", "group", group, "prismsPersisted", Boolean.TRUE));
				}
			};
			us.addListener(theListener);
			if(us.getRecordKeeper() instanceof prisms.records.ScaledRecordKeeper)
			{
				final prisms.records.ScaledRecordKeeper srk = (prisms.records.ScaledRecordKeeper) us
					.getRecordKeeper();
				srk.setCheckInterval(1500);
				app.scheduleRecurringTask(new Runnable()
				{
					public void run()
					{
						srk.checkChanges(false);
					}
				}, 2000);
			}

			app.addDestroyTask(new Runnable()
			{
				public void run()
				{
					((ManageableUserSource) app.getEnvironment().getUserSource())
						.removeListener(theListener);
				}
			});
		}
	}

	@Override
	public void changeValues(final prisms.arch.PrismsSession session,
		prisms.arch.event.PrismsPCE<User []> evt)
	{
		theUsers = evt.getNewValue();
		super.changeValues(session, evt);
	}

	@Override
	protected void globalAdjustValues(Object... eventProps)
	{
		super.globalAdjustValues(eventProps);
	}

	@Override
	protected void fireGlobalEvent(PrismsApplication app, String name, Object... eventProps)
	{
		super.fireGlobalEvent(app, name, eventProps);
	}

	@Override
	public User [] getGlobalValue()
	{
		return theUsers;
	}

	@Override
	public void setValue(User [] value)
	{
		theUsers = value;
	}

	@Override
	public User [] getApplicationValue(PrismsApplication app)
	{
		return theUsers;
	}

	@Override
	public boolean isValueCorrect(prisms.arch.PrismsSession session, User [] val)
	{
		return prisms.util.ArrayUtils.equals(theUsers, val);
	}

	@Override
	public User [] getCorrectValue(prisms.arch.PrismsSession session)
	{
		return theUsers;
	}
}
