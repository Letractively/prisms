/**
 * DataTreeManager.java Created Sep 10, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

/**
 * Allows a tree structure to be monitored for changes
 */
public class DataTreeManager
{
	private DataTreeNode theRoot;

	private java.util.Collection<DataTreeListener> theListeners;

	/**
	 * Creates a DataTreeManager
	 */
	public DataTreeManager()
	{
		theListeners = new java.util.ArrayList<DataTreeListener>();
	}

	/**
	 * @return The root node of this manager's tree
	 */
	public DataTreeNode getRoot()
	{
		return theRoot;
	}

	/**
	 * @param root The root node for this manager's tree
	 */
	public void setRoot(DataTreeNode root)
	{
		theRoot = root;
		treeRefreshed();
	}

	/**
	 * @param L The listener to be notified when the tree structure or data changes
	 */
	public void addListener(DataTreeListener L)
	{
		if(L != null)
			theListeners.add(L);
	}

	/**
	 * @param L The listener to remove from the notification list
	 * @return Whether the listener was found in the list of listeners
	 */
	public boolean removeListener(DataTreeListener L)
	{
		return theListeners.remove(L);
	}

	/**
	 * To be called by the underlying tree structure when a node is added to the tree. This will
	 * fire all necessary events to registered listeners
	 * 
	 * @param node The node that was added
	 * @param index The index that the node was added at under its parent
	 */
	public void nodeAdded(DataTreeNode node, int index)
	{
		fire(new DataTreeEvent(DataTreeEvent.Type.ADD, node, index));
	}

	/**
	 * To be called by the underlying tree structure when a node is removed the tree. This will fire
	 * all necessary events to registered listeners
	 * 
	 * @param node The node that was removed
	 */
	public void nodeRemoved(DataTreeNode node)
	{
		fire(new DataTreeEvent(DataTreeEvent.Type.REMOVE, node));
	}

	/**
	 * To be called by the underlying tree structure when a node is changed in the tree. This will
	 * fire all necessary events to registered listeners
	 * 
	 * @param node The node that was changed
	 * @param recursive Whether the change affected this node's children
	 */
	public void nodeChanged(DataTreeNode node, boolean recursive)
	{
		DataTreeEvent toFire = new DataTreeEvent(DataTreeEvent.Type.CHANGE, node);
		toFire.setRecursive(recursive);
		fire(toFire);
	}

	/**
	 * To be called by the underlying tree structure when a node is moved within the tree (but at
	 * the same level with the same parent node). This will fire all necessary events to registered
	 * listeners
	 * 
	 * @param node The node that was moved
	 * @param toIdx The index to move the node to under its parent
	 */
	public void nodeMoved(DataTreeNode node, int toIdx)
	{
		DataTreeEvent toFire = new DataTreeEvent(DataTreeEvent.Type.MOVE, node, toIdx);
		fire(toFire);
	}

	/**
	 * To be called by the underlying tree structure when the tree structure is refreshed. This will
	 * fire all necessary events to registered listeners
	 */
	public void treeRefreshed()
	{
		DataTreeEvent toFire = new DataTreeEvent(DataTreeEvent.Type.REFRESH, theRoot);
		fire(toFire);
	}

	/**
	 * @param node The data node to get the path of
	 * @return A string array path representing the data node's position in the tree
	 */
	public static String [] path(DataTreeNode node)
	{
		int len = 0;
		for(DataTreeNode n = node; n != null; n = n.getParent(), len++);
		String [] ret = new String [len];
		for(DataTreeNode n = node; n != null; n = n.getParent())
		{
			len--;
			ret[len] = n.getID();
		}
		return ret;
	}

	/**
	 * Navigates through this manager's tree to the node described by the path
	 * 
	 * @param path The path to navigate to
	 * @return The data node represented by the path
	 */
	public DataTreeNode navigate(String [] path)
	{
		DataTreeNode root = getRoot();
		if(!root.getID().equals(path[0]))
			throw new IllegalArgumentException("Root ID does not match first path element: "
				+ root.getID() + " and " + path[0]);
		return navigate(root, path, 1);
	}

	private static DataTreeNode navigate(DataTreeNode root, String [] path, int pathIdx)
	{
		if(pathIdx == path.length)
			return root;
		for(DataTreeNode c : root.getChildren())
			if(c.getID().equals(path[pathIdx]))
				return navigate(c, path, pathIdx + 1);
		throw new IllegalArgumentException("Node \"" + root.getID() + "\" does not have a child \""
			+ path[pathIdx] + "\"");
	}

	/**
	 * Fires an event to all registered listeners
	 * 
	 * @param evt The event to fire
	 */
	private void fire(DataTreeEvent evt)
	{
		for(DataTreeListener L : theListeners)
			L.changeOccurred(evt);
	}
}
