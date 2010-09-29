/**
 * Persister.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.arch;

/**
 * A Persister is an object that has the ability to take certain types of java objects and persist
 * them to various media and recreate the persisted objects.
 * 
 * @param <T> The type that this persister persists
 */
public interface Persister<T>
{
	/**
	 * Configures the persister
	 * 
	 * @param configEl The persister's configuration element
	 * @param app The application that the persister resides in
	 * @param property The property to persist
	 */
	void configure(org.dom4j.Element configEl, prisms.arch.PrismsApplication app,
		prisms.arch.event.PrismsProperty<T> property);

	/**
	 * @return All values that have been previously persisted
	 */
	T getValue();

	/**
	 * Links persisted values with current data from the application. This is important because some
	 * persisted objects refer to other objects that are persisted separately, causing duplication.
	 * This method allows the persister to persist identifying tags for the reference information
	 * and retrieve the tags for the {@link #getValue()} method and then retrieve the information
	 * based on the identifiers here. For example, a mission contains areas, which are persisted
	 * separately. If the missions were retrieved with full areas, the mission areas would be
	 * distinct from the areas retrieved from the area persister, so the duplicate areas in the
	 * mission would not be affected by changes to the application's areas.
	 * 
	 * @param value The values to link
	 * @return The linked values (may be different than the input if some values could not be
	 *         properly linked and so became invalid)
	 */
	T link(T value);

	/**
	 * Sets the values to be persisted to storage
	 * 
	 * @param <V> The type of value to set
	 * @param o The values to persist
	 * @param evt The event that represents the change. This is a raw value due to conflicts with
	 *        generics.
	 */
	@SuppressWarnings("rawtypes")
	<V extends T> void setValue(V o, prisms.arch.event.PrismsPCE evt);

	/**
	 * Called when a single element of the persister's value changes
	 * 
	 * @param fullValue The whole persisted value
	 * @param o The value that changed
	 * @param evt The event that represents the change
	 */
	void valueChanged(T fullValue, Object o, prisms.arch.event.PrismsEvent evt);

	/**
	 * Reloads this persister's value from the data source, clearing any cached resources that may
	 * be kept
	 */
	void reload();
}
