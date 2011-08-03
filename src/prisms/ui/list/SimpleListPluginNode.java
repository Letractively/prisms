/*
 * SimpleListPluginNode.java Created Feb 18, 2009 by Andrew Butler, PSL
 */
package prisms.ui.list;

/**
 * A version of list node that has the option to check the session for actions from other plugins
 */
public abstract class SimpleListPluginNode extends AbstractSimpleListNode
{
	private boolean hasPublicActions;

	/**
	 * @param manager The manager plugin for this node's list structure
	 * @param publicActions Whether this node publishes an event allowing listeners to add actions
	 */
	public SimpleListPluginNode(DataListMgrPlugin manager, boolean publicActions)
	{
		super(manager);
		hasPublicActions = publicActions;
	}

	@Override
	public final void setSelected(boolean selected)
	{
		super.setSelected(selected);
		getManager().nodeChanged(this);
	}

	/**
	 * @param pa Whether this node should check the session for actions that other plugins might
	 *        want to attach to this node
	 */
	public void setPublicActions(boolean pa)
	{
		hasPublicActions = pa;
	}

	@Override
	public NodeAction [] getActions()
	{
		if(hasPublicActions)
		{
			DataListMgrPlugin plugin = (DataListMgrPlugin) getManager();
			javax.swing.Action[] actions = getAvailableActions();
			for(int a = 0; a < actions.length; a++)
				if(actions[a].getValue("temporary") != null)
					removeAction(actions[a]);
			prisms.arch.event.PrismsEvent actionsEvent = new prisms.arch.event.PrismsEvent(
				"getUserActions", "plugin", plugin.getName(), "node", this, "actions",
				new javax.swing.Action [0]);
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
