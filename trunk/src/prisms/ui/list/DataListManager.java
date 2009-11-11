/**
 * DataListManager.java Created Oct 11, 2007 by Andrew Butler, PSL
 */
package prisms.ui.list;

import prisms.ui.DataEvent;

/**
 * Manages a list of data nodes
 */
public class DataListManager
{
	private java.util.ArrayList<DataListNode> theNodes;

	private java.util.Collection<DataListListener> theListeners;

	/**
	 * Creates a DataListManager
	 */
	public DataListManager()
	{
		theNodes = new java.util.ArrayList<DataListNode>();
		theListeners = new java.util.ArrayList<DataListListener>();
	}

	/**
	 * @return The number of items in the list
	 */
	public int getItemCount()
	{
		return theNodes.size();
	}

	/**
	 * @param index The index of the item to get
	 * @return The item at the specific index in this list
	 */
	public DataListNode getItem(int index)
	{
		return theNodes.get(index);
	}

	/**
	 * @param nodes The nodes for this list
	 */
	public void setItems(DataListNode [] nodes)
	{
		theNodes.clear();
		for(DataListNode node : nodes)
			theNodes.add(node);
	}

	/**
	 * @param L The listener to be notified when the list data changes
	 */
	public void addListener(DataListListener L)
	{
		if(L != null)
			theListeners.add(L);
	}

	/**
	 * @param L The listener to remove from the notification list
	 * @return Whether the listener was found in the list of listeners
	 */
	public boolean removeListener(DataListListener L)
	{
		return theListeners.remove(L);
	}

	/**
	 * @param node The node to add
	 * @param index The index to add the node at
	 */
	public void addNode(DataListNode node, int index)
	{
		theNodes.add(index, node);
		fire(new DataListEvent(DataEvent.Type.ADD, node, index));
	}

	/**
	 * @param index The index of the node to remove
	 */
	public void removeNode(int index)
	{
		DataListNode node = theNodes.remove(index);
		fire(new DataListEvent(DataEvent.Type.REMOVE, node));
	}

	/**
	 * Moves a node within this list from one index to another
	 * 
	 * @param fromIndex The node's current index
	 * @param toIndex The index to move the node to
	 */
	public void moveNode(int fromIndex, int toIndex)
	{
		DataListNode node = theNodes.remove(fromIndex);
		theNodes.add(toIndex, node);
		fire(new DataListEvent(DataEvent.Type.MOVE, node, fromIndex));
	}

	/**
	 * To be called when a node is changed in the list. This will fire all necessary events to
	 * registered listeners
	 * 
	 * @param node The node that was changed
	 */
	public void nodeChanged(DataListNode node)
	{
		fire(new DataListEvent(DataEvent.Type.CHANGE, node));
	}

	/**
	 * Fires an event to all registered listeners
	 * 
	 * @param evt The event to fire
	 */
	private void fire(DataListEvent evt)
	{
		for(DataListListener L : theListeners)
			L.changeOccurred(evt);
	}
}
