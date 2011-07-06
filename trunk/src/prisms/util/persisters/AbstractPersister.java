/**
 * AbstractPersister.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.Persister;
import prisms.arch.PrismsApplication;

/**
 * Implements some of the linking functionality needed by persisters
 * 
 * @param <T> The type of value to persist
 */
public abstract class AbstractPersister<T> implements Persister<T>
{
	private PrismsApplication theApp;

	// private LinkHelper theLinker;

	public void configure(prisms.arch.PrismsConfig config, PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property)
	{
		theApp = app;
		// theLinker = new LinkHelper();
		// Element linkConfig = configEl.element("link");
		// if(linkConfig != null)
		// theLinker.configure(linkConfig);
	}

	/** @return The application that this persister stores and retrieves data for */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	public T link(T value)
	{
		return value;
		// return (T) theLinker.link(value, theApp);
	}
}
