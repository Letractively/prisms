/**
 * AbstractPersister.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.dom4j.Element;

import prisms.arch.PrismsApplication;
import prisms.arch.Persister;

/**
 * Implements some of the linking functionality needed by persisters
 * 
 * @param <T> The type of value to persist
 */
public abstract class AbstractPersister<T> implements Persister<T>
{
	private PrismsApplication theApp;

	// private LinkHelper theLinker;

	public void configure(Element configEl, PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property)
	{
		theApp = app;
		// theLinker = new LinkHelper();
		// Element linkConfig = configEl.element("link");
		// if(linkConfig != null)
		// theLinker.configure(linkConfig);
	}

	/**
	 * @return The application that this persister stores and retrieves data for
	 */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/**
	 * @see prisms.arch.Persister#link(java.lang.Object)
	 */
	public T link(T value)
	{
		return value;
		// return (T) theLinker.link(value, theApp);
	}
}
