/**
 * DataListListener.java Created Oct 11, 2007 by Andrew Butler, PSL
 */
package prisms.ui.list;

/**
 * A listener for changes to a DataList
 */
public interface DataListListener
{
	/**
	 * Called when a change to the data structure occurs
	 * 
	 * @param node The node that the change occurred on
	 */
	void changeOccurred(DataListEvent node);
}
