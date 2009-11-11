/**
 * DataTreeEvent.java Created Sep 10, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import prisms.ui.DataEvent;

/**
 * An event that may occur on a DataTree
 */
public class DataTreeEvent extends DataEvent
{
	private DataTreeNode theNode;

	private boolean theRecursive;

	private int theIndex;

	/**
	 * Creates a DataTreeEvent
	 * 
	 * @param type The type of the event
	 * @param node The node that the event occurred on
	 */
	public DataTreeEvent(Type type, DataTreeNode node)
	{
		super(type);
		theNode = node;
		theIndex = -1;
	}

	/**
	 * Creates a DataTreeNode
	 * 
	 * @param type The type of the event
	 * @param node The node the event occurred on
	 * @param index The child index that the event occurred at
	 */
	public DataTreeEvent(Type type, DataTreeNode node, int index)
	{
		this(type, node);
		theIndex = index;
	}

	/**
	 * @return The node that this event occurred on
	 */
	public DataTreeNode getNode()
	{
		return theNode;
	}

	/**
	 * @return The child index that this even occurred at
	 */
	public int getIndex()
	{
		return theIndex;
	}

	/**
	 * @return Whether this event should be applied recursively or not
	 */
	public boolean isRecursive()
	{
		return theRecursive;
	}

	/**
	 * @param recursive Whether this event should be applied recursively or not
	 */
	public void setRecursive(boolean recursive)
	{
		theRecursive = recursive;
	}
}
