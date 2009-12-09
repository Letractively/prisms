/**
 * DataListManager.java Created Oct 11, 2007 by Andrew Butler, PSL
 */
package prisms.ui.list;

import prisms.ui.DataEvent;

/**
 * Manages a list of data nodes
 */
public class DataListManager implements Iterable<DataListNode>
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

	public java.util.ListIterator<DataListNode> iterator()
	{
		final DataListNode [] _copy = theNodes.toArray(new DataListNode [theNodes.size()]);
		return new java.util.ListIterator<DataListNode>()
		{
			DataListNode [] copy = _copy;

			private int idx = 0;

			boolean movedForward = false;

			public boolean hasNext()
			{
				return idx < copy.length;
			}

			public DataListNode next()
			{
				DataListNode ret = copy[idx];
				idx++;
				movedForward = true;
				return ret;
			}

			public boolean hasPrevious()
			{
				return idx > 0;
			}

			public DataListNode previous()
			{
				idx--;
				movedForward = false;
				return copy[idx];
			}

			public int previousIndex()
			{
				return idx - 1;
			}

			public int nextIndex()
			{
				return idx;
			}

			public void add(DataListNode o)
			{
				if(idx == 0)
					addNode(o, idx);
				else
				{
					int i;
					for(i = 0; i < getItemCount(); i++)
						if(getItem(i) == copy[idx - 1])
							break;
					if(i != getItemCount())
					{
						addNode(o, i + 1);
						copy = prisms.util.ArrayUtils.add(copy, o, idx);
						idx++;
						return;
					}

					if(idx == copy.length)
					{
						addNode(o, getItemCount());
						copy = prisms.util.ArrayUtils.add(copy, o);
						idx++;
						return;
					}

					for(i = 0; i < getItemCount(); i++)
						if(getItem(i) == copy[idx])
							break;
					if(i != getItemCount())
					{
						addNode(o, i);
						copy = prisms.util.ArrayUtils.add(copy, o, idx);
						idx++;
						return;
					}
					addNode(o, getItemCount());
					copy = prisms.util.ArrayUtils.add(copy, o);
				}
			}

			public void remove()
			{
				int _idx = idx;
				if(movedForward)
					_idx--;
				int i;
				for(i = 0; i < getItemCount(); i++)
					if(getItem(i) == copy[_idx])
						break;

				if(i != getItemCount())
					removeNode(i);
				copy = prisms.util.ArrayUtils.remove(copy, _idx);
				idx--;
			}

			public void set(DataListNode o)
			{
				int _idx = idx;
				if(movedForward)
					_idx--;
				int i;
				for(i = 0; i < getItemCount(); i++)
					if(getItem(i) == copy[_idx])
						break;

				if(i != getItemCount())
				{
					removeNode(i);
					addNode(o, i);
				}
				copy[_idx] = o;
			}
		};
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
