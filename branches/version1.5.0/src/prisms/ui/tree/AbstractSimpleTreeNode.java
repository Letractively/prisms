/**
 * AbstractSimpleTreeNode.java Created Oct 5, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import javax.swing.Action;

import org.json.simple.JSONObject;

import prisms.ui.list.NodeAction;

/**
 * A simple partial implementation of DataTreeNode that handles the manipulation of children,
 * alerting the node's manager of changes
 */
public abstract class AbstractSimpleTreeNode implements JsonTreeNode
{
	private final DataTreeManager theMgr;

	private Action [] theActions;

	/**
	 * This node's parent
	 */
	protected DataTreeNode theParent;

	/**
	 * This node's children
	 */
	protected DataTreeNode [] theChildren;

	private boolean isSelected;

	/**
	 * Creates a tree node
	 * 
	 * @param mgr The manager managing this node and its relatives
	 * @param parent This node's parent
	 */
	public AbstractSimpleTreeNode(DataTreeManager mgr, DataTreeNode parent)
	{
		theMgr = mgr;
		theParent = parent;
		theChildren = new DataTreeNode [0];
		theActions = new Action [0];
	}

	public final String getID()
	{
		return Integer.toHexString(hashCode());
	}

	/**
	 * Does not fire any listeners. Intended to be called when creating the entire tree hierarchy
	 * 
	 * @param children The initial set of children for this node
	 */
	public void setChildren(DataTreeNode [] children)
	{
		theChildren = children;
	}

	/**
	 * @return The tree manager managing this node and its relatives
	 */
	public DataTreeManager getManager()
	{
		return theMgr;
	}

	/**
	 * @see prisms.ui.tree.DataTreeNode#getParent()
	 */
	public DataTreeNode getParent()
	{
		return theParent;
	}

	/**
	 * @see prisms.ui.tree.DataTreeNode#getChildren()
	 */
	public DataTreeNode [] getChildren()
	{
		return theChildren;
	}

	/**
	 * Adds a node
	 * 
	 * @param node The node to add
	 * @param index The child index to add the node at
	 */
	public void add(DataTreeNode node, int index)
	{
		theChildren = prisms.util.ArrayUtils.add(theChildren, node, index);
		theMgr.nodeAdded(node, index);
	}

	/**
	 * Removes a node
	 * 
	 * @param index The index of the node to remove
	 */
	public void remove(int index)
	{
		DataTreeNode child = theChildren[index];
		theChildren = prisms.util.ArrayUtils.remove(theChildren, index);
		theMgr.nodeRemoved(child);
	}

	/**
	 * Although this operation does not affect this node's children, it is included since the
	 * implementation should be fairly standard
	 * 
	 * @param recursive Whether this node's descendants have been changed as well
	 */
	public void changed(boolean recursive)
	{
		theMgr.nodeChanged(this, recursive);
	}

	/**
	 * Moves a child node from one index to another.
	 * 
	 * @param fromIdx The index of the node to move
	 * @param toIdx The new index for the node
	 */
	public void move(int fromIdx, int toIdx)
	{
		DataTreeNode child = theChildren[fromIdx];
		int st = fromIdx;
		for(; st < toIdx; st++)
			theChildren[st] = theChildren[st + 1];
		for(; st > toIdx; st--)
			theChildren[st] = theChildren[st - 1];
		theChildren[toIdx] = child;
		theMgr.nodeMoved(child, toIdx);
	}

	/**
	 * @see prisms.ui.list.DataListNode#isSelected()
	 */
	public boolean isSelected()
	{
		return isSelected;
	}

	/**
	 * @see prisms.ui.list.DataListNode#setSelected(boolean)
	 */
	public void setSelected(boolean selected)
	{
		isSelected = selected;
	}

	/**
	 * @see prisms.ui.list.DataListNode#userSetSelected(boolean)
	 */
	public void userSetSelected(boolean selected)
	{
		isSelected = selected;
	}

	/**
	 * Adds an action to be available to the user for this node
	 * 
	 * @param action The action to add
	 */
	public void addAction(Action action)
	{
		if(!(action.getValue(Action.NAME) instanceof String))
			throw new IllegalArgumentException("Actions for tree nodes require a string value");
		if(!prisms.util.ArrayUtils.contains(theActions, action))
			theActions = prisms.util.ArrayUtils.add(theActions, action);
	}

	/**
	 * Removes an action from availability for the user on this node
	 * 
	 * @param action The action to remove
	 * @return If an action was removed
	 */
	public boolean removeAction(Action action)
	{
		int idx = prisms.util.ArrayUtils.indexOf(theActions, action);
		if(idx >= 0)
			theActions = prisms.util.ArrayUtils.remove(theActions, action);
		return idx >= 0;
	}

	/**
	 * @return The actions available to the user for this node
	 */
	public Action [] getAvailableActions()
	{
		return theActions;
	}

	public NodeAction [] getActions()
	{
		NodeAction [] ret = new NodeAction [0];
		for(int a = 0; a < theActions.length; a++)
		{
			if(!theActions[a].isEnabled())
				continue;
			boolean multi;
			if(theActions[a].getValue("multiple") instanceof String)
				multi = "true".equalsIgnoreCase((String) theActions[a].getValue("multiple"));
			else if(theActions[a].getValue("multiple") instanceof Boolean)
				multi = ((Boolean) theActions[a].getValue("multiple")).booleanValue();
			else
				multi = false;
			ret = prisms.util.ArrayUtils.add(ret, new NodeAction((String) theActions[a]
				.getValue(Action.NAME), multi));
		}
		return ret;
	}

	public void doAction(String action)
	{
		javax.swing.Action toDo = null;
		for(int a = 0; a < theActions.length; a++)
			if(action.equals(theActions[a].getValue(Action.NAME)))
			{
				toDo = theActions[a];
				break;
			}
		if(toDo == null)
			throw new IllegalArgumentException("Unrecognized action " + action + " on node "
				+ getText());
		if(!toDo.isEnabled())
			throw new IllegalArgumentException("Action " + action + " on node " + getText()
				+ " is disabled");
		java.awt.event.ActionEvent evt = new java.awt.event.ActionEvent(this, 0, action);
		toDo.actionPerformed(evt);
	}

	public JSONObject toJSON()
	{
		JSONObject ret = new JSONObject();
		ret.put("id", getID());
		ret.put("text", getText());
		ret.put("icon", getIcon());
		ret.put("description", getDescription());
		ret.put("bgColor", prisms.util.JsonUtils.toHTML(getBackground()));
		ret.put("textColor", prisms.util.JsonUtils.toHTML(getForeground()));
		ret.put("actions", prisms.util.JsonUtils.serialize(getActions()));
		return ret;
	}
}
