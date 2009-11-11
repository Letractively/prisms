/**
 * AbstractSimpleListNode.java Created Jan 29, 2008 by Andrew Butler, PSL
 */
package prisms.ui.list;

import javax.swing.Action;

import org.json.simple.JSONObject;

/**
 * Provides a default serialization implementation
 */
public abstract class AbstractSimpleListNode implements JsonListNode
{
	private final DataListManager theMgr;

	private Action [] theActions;

	private boolean isSelected;

	/**
	 * Creates a tree node
	 * 
	 * @param mgr The manager managing this node
	 */
	public AbstractSimpleListNode(DataListManager mgr)
	{
		theMgr = mgr;
		theActions = new Action [0];
	}

	/**
	 * @return The list manager managing this node
	 */
	public DataListManager getManager()
	{
		return theMgr;
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
	 * @see prisms.ui.list.DataListNode#setSelected(boolean)
	 */
	public void userSetSelected(boolean selected)
	{
		isSelected = selected;
	}

	/**
	 * @return The actions available to the user for this node
	 */
	public Action [] getAvailableActions()
	{
		return theActions;
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

	public NodeAction [] getActions()
	{
		NodeAction [] ret = new NodeAction [theActions.length];
		for(int a = 0; a < ret.length; a++)
		{
			boolean multi;
			if(theActions[a].getValue("multiple") instanceof String)
				multi = "true".equalsIgnoreCase((String) theActions[a].getValue("multiple"));
			else if(theActions[a].getValue("multiple") instanceof Boolean)
				multi = ((Boolean) theActions[a].getValue("multiple")).booleanValue();
			else
				multi = false;
			ret[a] = new NodeAction((String) theActions[a].getValue(Action.NAME), multi);
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
