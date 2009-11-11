/**
 * PropertyDataSource.java Created Oct 16, 2007 by Andrew Butler, PSL
 */
package prisms.arch.event;

/**
 * A data source for use in persisting and retrieving property values
 */
public interface PropertyDataSource
{
	/**
	 * Configures this data source
	 * 
	 * @param configEl The XML element to configure this data source with
	 */
	void configure(org.dom4j.Element configEl);

	/**
	 * @param propName The name of the property whose value to get
	 * @return The value of the named property
	 */
	Object getData(String propName);

	/**
	 * @param propName The name of the property to store
	 * @param value The value of the property to store
	 */
	void saveData(String propName, Object value);
}
