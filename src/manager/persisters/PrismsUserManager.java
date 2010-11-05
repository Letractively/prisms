/*
 * PrismsUserManager.java Created Nov 3, 2010 by Andrew Butler, PSL
 */
package manager.persisters;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.ManageableUserSource;
import prisms.arch.ds.User;

/** Manages the full set of users available in PRISMS between applications */
public class PrismsUserManager extends prisms.util.persisters.PersistingPropertyManager<User []>
{
	private User [] theUsers;

	ManageableUserSource.UserSetListener theListener;

	@Override
	public void configure(final PrismsApplication app, org.dom4j.Element configEl)
	{
		super.configure(app, configEl);
		if(theListener != null
			&& app.getEnvironment().getUserSource() instanceof ManageableUserSource)
		{
			theListener = new ManageableUserSource.UserSetListener()
			{
				public void userSetChanged(User [] users)
				{
					setValue(users);
					globalAdjustValues(null, null);
				}

				public void userChanged(User user)
				{
					fireGlobalEvent(null, "prismsUserChanged", "user", user);
				}
			};
			((ManageableUserSource) app.getEnvironment().getUserSource()).addListener(theListener);
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
	protected void globalAdjustValues(PrismsApplication app, prisms.arch.PrismsSession session,
		Object... eventProps)
	{
		super.globalAdjustValues(app, session, eventProps);
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
