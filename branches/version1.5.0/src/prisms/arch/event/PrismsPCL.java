/**
 * PrismsPCL.java Created Mar 10, 2009 by Andrew Butler, PSL
 */
package prisms.arch.event;

/**
 * A substitute for {@link java.beans.PropertyChangeListener} that listens for typed properties
 * 
 * @param <T> The type of property this listener listens to
 */
public interface PrismsPCL<T>
{
	/**
	 * Called when the property changes
	 * 
	 * @param evt The event that represents the property change
	 */
	void propertyChange(PrismsPCE<T> evt);
}
