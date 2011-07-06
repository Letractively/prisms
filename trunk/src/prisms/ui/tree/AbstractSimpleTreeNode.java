/*
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

	private final String theID;

	private Action [] theActions;

	/** This node's parent */
	protected DataTreeNode theParent;

	/** This node's children */
	protected DataTreeNode [] theChildren;

	private boolean isSelected;

	private boolean selectionChangesNode;

	/**
	 * Creates a tree node
	 * 
	 * @param mgr The manager managing this node and its relatives
	 * @param parent This node's parent
	 */
	public AbstractSimpleTreeNode(DataTreeManager mgr, DataTreeNode parent)
	{
		theMgr = mgr;
		theID = Integer.toHexString(hashCode());
		theParent = parent;
		theChildren = new DataTreeNode [0];
		theActions = new Action [0];
	}

	/**
	 * Creates a tree node
	 * 
	 * @param mgr The manager managing this node and its relatives
	 * @param id The ID for this node
	 * @param parent This node's parent
	 */
	public AbstractSimpleTreeNode(DataTreeManager mgr, String id, DataTreeNode parent)
	{
		theMgr = mgr;
		theID = id;
		theParent = parent;
		theChildren = new DataTreeNode [0];
		theActions = new Action [0];
	}

	public final String getID()
	{
		return theID;
	}

	/** @return Whether selecting this node changes its appearance */
	public boolean selectionChangesNode()
	{
		return selectionChangesNode;
	}

	/** @param change Whether selection changes this node's appearance */
	public void setSelectionChangesNode(boolean change)
	{
		selectionChangesNode = change;
	}

	/**
	 * Does not fire any listeners. Intended to be called when creating the entire tree hierarchy
	 * 
	 * @param children The initial set of children for this node
	 */
	public void setChildren(DataTreeNode [] children)
	{
		DataTreeNode [] oldCh = children;
		theChildren = children;
		prisms.util.ArrayUtils.adjust(oldCh, children,
			new prisms.util.ArrayUtils.DifferenceListener<DataTreeNode, DataTreeNode>()
			{
				public boolean identity(DataTreeNode o1, DataTreeNode o2)
				{
					return o1.equals(o2);
				}

				public DataTreeNode added(DataTreeNode o, int mIdx, int retIdx)
				{
					if(o instanceof AbstractSimpleTreeNode && o.getParent() == null)
						((AbstractSimpleTreeNode) o).theParent = AbstractSimpleTreeNode.this;
					else if(o.getParent() != AbstractSimpleTreeNode.this)
						throw new IllegalStateException("Child " + o.getText() + " of node "
							+ getText() + " has a different parent");
					return null;
				}

				public DataTreeNode removed(DataTreeNode o, int oIdx, int incMod, int retIdx)
				{
					if(o instanceof AbstractSimpleTreeNode)
						((AbstractSimpleTreeNode) o).removed();
					return null;
				}

				public DataTreeNode set(DataTreeNode o1, int idx1, int incMod, DataTreeNode o2,
					int idx2, int retIdx)
				{
					return null;
				}
			});
	}

	/** @return The tree manager managing this node and its relatives */
	public DataTreeManager getManager()
	{
		return theMgr;
	}

	public DataTreeNode getParent()
	{
		return theParent;
	}

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
		if(node instanceof AbstractSimpleTreeNode && node.getParent() == null)
			((AbstractSimpleTreeNode) node).theParent = AbstractSimpleTreeNode.this;
		else if(node.getParent() != AbstractSimpleTreeNode.this)
			throw new IllegalStateException("Child " + node.getText() + " of node " + getText()
				+ " has a different parent");
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
		if(child instanceof AbstractSimpleTreeNode)
			((AbstractSimpleTreeNode) child).removed();
	}

	/**
	 * Although this operation does not affect this node's children, it is included since the
	 * implementation should be fairly standard
	 * 
	 * @param recursive Whether this node's descendants have been changed as well
	 */
	public void changed(boolean recursive)
	{
		if(theParent == null && theMgr.getRoot() != this)
			return;
		if(theParent != null && !prisms.util.ArrayUtils.contains(theParent.getChildren(), this))
			return;
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

	/** Called just after this node is removed from its parent or from the tree as the root. */
	protected void removed()
	{
		theParent = null;
		for(DataTreeNode child : theChildren)
			if(child instanceof AbstractSimpleTreeNode
				&& ((AbstractSimpleTreeNode) child).theParent == this)
				((AbstractSimpleTreeNode) child).removed();
	}

	public boolean isSelected()
	{
		return isSelected;
	}

	public void setSelected(boolean selected)
	{
		isSelected = selected;
	}

	public void userSetSelected(boolean selected)
	{
		boolean changed = isSelected != selected;
		isSelected = selected;
		if(changed && selectionChangesNode)
			changed(false);
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

	/** @return The actions available to the user for this node */
	public Action [] getAvailableActions()
	{
		return theActions;
	}

	public NodeAction [] getActions()
	{
		Action [] actions = theActions;
		int count = 0;
		for(int i = 0; i < actions.length; i++)
			if(actions[i].isEnabled())
				count++;
		NodeAction [] ret = new NodeAction [count];
		count = 0;
		for(int a = 0; a < actions.length; a++)
		{
			if(!actions[a].isEnabled())
				continue;
			boolean multi;
			if(actions[a].getValue("multiple") instanceof String)
				multi = "true".equalsIgnoreCase((String) actions[a].getValue("multiple"));
			else if(theActions[a].getValue("multiple") instanceof Boolean)
				multi = ((Boolean) actions[a].getValue("multiple")).booleanValue();
			else
				multi = false;
			if(count == ret.length)
			{
				ret = prisms.util.ArrayUtils.add(ret,
					new NodeAction((String) actions[a].getValue(Action.NAME), multi));
				count++;
			}
			else
				ret[count++] = new NodeAction((String) actions[a].getValue(Action.NAME), multi);
		}
		return ret;
	}

	public void doAction(String action)
	{
		Action [] actions = theActions;
		javax.swing.Action toDo = null;
		for(int a = 0; a < actions.length; a++)
			if(action.equals(actions[a].getValue(Action.NAME)))
			{
				toDo = actions[a];
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
		ret.put("bgColor", prisms.util.ColorUtils.toHTML(getBackground()));
		ret.put("textColor", prisms.util.ColorUtils.toHTML(getForeground()));
		ret.put("actions", prisms.util.JsonUtils.serialize(getActions()));
		return ret;
	}
}
