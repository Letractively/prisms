/*
 * DataTreeEvent.java Created Sep 10, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

/** An event that may occur on a tree */
public class DataTreeEvent extends prisms.ui.DataEvent
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

	/** @return The node that this event occurred on */
	public DataTreeNode getNode()
	{
		return theNode;
	}

	/** @return The child index that this even occurred at */
	public int getIndex()
	{
		return theIndex;
	}

	/** @return Whether this event should be applied recursively or not */
	public boolean isRecursive()
	{
		return theRecursive;
	}

	/** @param recursive Whether this event should be applied recursively or not */
	public void setRecursive(boolean recursive)
	{
		theRecursive = recursive;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		switch(getType())
		{
		case ADD:
			ret.append("Adding ").append(theNode.getText());
			if(theNode.getParent() == null)
				ret.append(" as root");
			else
				ret.append(" to ").append(theNode.getParent().getText()).append(" at ")
					.append(theIndex);
			break;
		case REMOVE:
			ret.append("Removing ").append(theNode.getText());
			if(theNode.getParent() == null)
				ret.append(" as root");
			else
				ret.append(" from ").append(theNode.getParent().getText());
			break;
		case CHANGE:
			ret.append("Changing ");
			if(theNode.getParent() == null)
				ret.append(" root node ");
			else
				ret.append(theNode.getParent().getText()).append('/');
			ret.append(theNode.getText());
			if(theRecursive)
				ret.append(" recursively");
			break;
		case MOVE:
			ret.append("Moving ");
			if(theNode.getParent() != null)
				ret.append(theNode.getParent().getText()).append('/');
			ret.append(theNode.getText()).append(" to index ").append(theIndex);
			break;
		case REFRESH:
			ret.append("Refreshing tree structure");
			break;
		}
		return ret.toString();
	}
}
