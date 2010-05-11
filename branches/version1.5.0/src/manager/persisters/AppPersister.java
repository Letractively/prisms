/**
 * AppPersister.java Created Jul 10, 2008 by Andrew Butler, PSL
 */
package manager.persisters;

import org.apache.log4j.Logger;

import prisms.arch.PrismsApplication;

/**
 * Retrieves all applications from the user source
 */
public class AppPersister extends prisms.util.persisters.AbstractPersister<PrismsApplication []>
{
	private static final Logger log = Logger.getLogger(AppPersister.class);

	/**
	 * @see prisms.arch.Persister#getValue()
	 */
	public PrismsApplication [] getValue()
	{
		if(!(getApp().getDataSource() instanceof prisms.arch.ds.ManageableUserSource))
		{
			log.error("Cannot retrieve apps--user source is not manageable");
			return new PrismsApplication [0];
		}
		else
			try
			{
				return ((prisms.arch.ds.ManageableUserSource) getApp().getDataSource())
					.getAllApps();
			} catch(prisms.arch.PrismsException e)
			{
				log.error("Could not retrieve apps", e);
				return new PrismsApplication [0];
			}
	}

	/**
	 * @see prisms.arch.Persister#setValue(java.lang.Object)
	 */
	public void setValue(PrismsApplication [] o)
	{
		// Changes should not be made directly to an application, but rather through the manager,
		// so persistence is not handled here
	}

	/**
	 * @see prisms.arch.Persister#valueChanged(java.lang.Object, java.lang.Object)
	 */
	public void valueChanged(PrismsApplication [] fullValue, Object o)
	{
		// Changes should not be made directly to an application, but rather through the manager,
		// so persistence is not handled here
	}

	public void reload()
	{
		// No cache to clear
	}
}
