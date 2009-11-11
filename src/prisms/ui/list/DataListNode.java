/**
 * DataListNode.java Created Oct 11, 2007 by Andrew Butler, PSL
 */
package prisms.ui.list;

/**
 * A simple node that contains methods for referencing and representing itself in a client
 */
public interface DataListNode
{
	/**
	 * @return The unique ID by which this node can be retrieved
	 */
	String getID();

	/**
	 * @return The text to represent this node on the client
	 */
	String getText();

	/**
	 * @return The background color of this node
	 */
	java.awt.Color getBackground();

	/**
	 * @return The text color of this node
	 */
	java.awt.Color getForeground();

	/**
	 * @return A string representing the icon that should be used to represent this node on the
	 *         client
	 */
	String getIcon();

	/**
	 * @return A description that my be displayed for this node
	 */
	String getDescription();

	/**
	 * @return Actions that may be performed on this node
	 */
	NodeAction [] getActions();

	/**
	 * @return Whether this node is marked as selected or not
	 */
	boolean isSelected();

	/**
	 * Programmatically sets this node's selection
	 * 
	 * @param selected Whether this node is to be marked selected or not
	 */
	void setSelected(boolean selected);

	/**
	 * Called when the user selects or deselects this node--may be vetoed programmatically
	 * 
	 * @param selected Whether the user wats this node to be marked selected
	 */
	void userSetSelected(boolean selected);

	/**
	 * Performs an action on this node
	 * 
	 * @param action The action to perform
	 */
	void doAction(String action);
}
