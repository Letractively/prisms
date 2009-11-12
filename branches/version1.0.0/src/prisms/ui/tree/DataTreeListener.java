/**
 * DataTreeListener.java Created Sep 10, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

/**
 * A listener for changes to a DataTree
 */
public interface DataTreeListener
{
	/**
	 * Called when a change to the data structure occurs
	 * 
	 * @param evt The event that occurred
	 */
	void changeOccurred(DataTreeEvent evt);
}
