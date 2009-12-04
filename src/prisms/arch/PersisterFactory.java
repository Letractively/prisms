/**
 * PersisterUtils.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.arch;

import org.dom4j.Element;

import prisms.arch.ds.UserSource;
import prisms.arch.event.PrismsProperty;
import prisms.arch.event.PropertyDataSource;

/**
 * A utility class to help with persistence
 */
public interface PersisterFactory
{
	/**
	 * Configures this factory
	 * 
	 * @param server The server that this factory is for
	 * @param configEl The XML element to use to configure this factory
	 */
	void configure(PrismsServer server, Element configEl);

	/**
	 * Creates a persister from an XML configuration element
	 * 
	 * @param <T> The type of persister to create
	 * @param persisterEl The configuration element representing a persister
	 * @param app The application to create the persister for
	 * @param property The name of the property to be persisted by the new persister
	 * @return A configured persister
	 */
	<T> Persister<T> create(Element persisterEl, PrismsApplication app, PrismsProperty<T> property);

	/**
	 * Creates and configures a data source from an XML element
	 * 
	 * @param el The XML element containing information on how to create and configure the data
	 *        source
	 * @return A new PropertyDataSource
	 */
	PropertyDataSource parseDS(Element el);

	/**
	 * Creates or retrieves an SQL connection using a configuration element
	 * 
	 * @param el The configuration element describing how to retrieve or create the connection
	 * @param userSource The user source of the application to get the connection for
	 * @return The configured connection
	 */
	java.sql.Connection getConnection(Element el, UserSource userSource);

	/**
	 * @param conn The connection to get the table prefix for
	 * @param connEl The connection element used to configure the given connection
	 * @param userSource The user source userd to configure the given connection
	 * @return The prefix that should be used before the name of each column in all SQL statements
	 *         executed on the given connection
	 */
	String getTablePrefix(java.sql.Connection conn, Element connEl, UserSource userSource);

	/**
	 * Performs a disconnect operation on a connection as configured by an XML configuration element
	 * 
	 * @param conn The connection to release
	 * @param connEl The XML element describing how to release the connection
	 */
	void disconnect(java.sql.Connection conn, Element connEl);

	/**
	 * Releases all of this factory's resources
	 */
	void destroy();
}
