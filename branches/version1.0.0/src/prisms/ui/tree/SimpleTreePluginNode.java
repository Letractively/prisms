/**
 * SimpleTreePluginNode.java Created Apr 1, 2008 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import prisms.ui.list.NodeAction;

/**
 * A version of tree node that has the option to check the session for actions from other plugins
 */
public abstract class SimpleTreePluginNode extends AbstractSimpleTreeNode
{
	private boolean hasPublicActions;

	/**
	 * @param manager The manager plugin for this node's tree structure
	 * @param parent This node's parent
	 * @param publicActions Whether this node publishes an event allowing listeners to add actions
	 */
	public SimpleTreePluginNode(DataTreeMgrPlugin manager, DataTreeNode parent,
		boolean publicActions)
	{
		super(manager, parent);
		hasPublicActions = publicActions;
	}

	/**
	 * @return Whether this node exposes its action set publicly
	 */
	public boolean hasPublicActions()
	{
		return hasPublicActions;
	}

	/**
	 * @see prisms.ui.tree.AbstractSimpleTreeNode#setSelected(boolean)
	 */
	@Override
	public final void setSelected(boolean selected)
	{
		super.setSelected(selected);
		getManager().nodeChanged(this, false);
	}

	/**
	 * @param pa Whether this node should check the session for actions that other plugins might
	 *        want to attach to this node
	 */
	public void setPublicActions(boolean pa)
	{
		hasPublicActions = pa;
	}

	/**
	 * @see prisms.ui.tree.AbstractSimpleTreeNode#getActions()
	 */
	@Override
	public NodeAction [] getActions()
	{
		if(hasPublicActions)
		{
			DataTreeMgrPlugin plugin = (DataTreeMgrPlugin) getManager();
			javax.swing.Action[] actions = getAvailableActions();
			for(int a = 0; a < actions.length; a++)
				if(actions[a].getValue("temporary") != null)
					removeAction(actions[a]);
			prisms.arch.event.PrismsEvent actionsEvent = new prisms.arch.event.PrismsEvent("getUserActions",
				"plugin", plugin.getName(), "node", this, "actions", new javax.swing.Action [0]);
			addEventProperties(actionsEvent);
			plugin.getSession().fireEvent(actionsEvent);
			javax.swing.Action[] newActions = (javax.swing.Action[]) actionsEvent
				.getProperty("actions");
			for(int a = 0; a < newActions.length; a++)
			{
				newActions[a].putValue("temporary", "true");
				addAction(newActions[a]);
			}
		}
		return super.getActions();
	}

	/**
	 * Allows subclasses to add parameters to the plugin event sent to check for actions
	 * 
	 * @param evt The event to customize and add information to
	 */
	protected void addEventProperties(prisms.arch.event.PrismsEvent evt)
	{
	}
}
