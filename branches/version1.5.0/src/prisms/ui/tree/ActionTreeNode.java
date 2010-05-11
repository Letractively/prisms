package prisms.ui.tree;

import java.awt.Color;

import org.apache.log4j.Logger;

/**
 * A node that fires an event when selected (and deselects itself). The manager for this node must
 * have a selection mode of single or multiple.
 */
public class ActionTreeNode extends SimpleTreePluginNode
{
	private static final Logger log = Logger.getLogger(ActionTreeNode.class);

	String theEventName;

	private String theText;

	private String theIcon;

	/**
	 * Creates an ActionTreeNode
	 * 
	 * @param manager The manager for this node
	 * @param parent The parent for this node
	 * @param eventName The name of the event that this node should fire when selected
	 */
	public ActionTreeNode(DataTreeMgrPlugin manager, DataTreeNode parent, String eventName)
	{
		super(manager, parent, false);
		theEventName = eventName;
		if(manager.getSelectionMode() == DataTreeMgrPlugin.SelectionMode.NONE)
			log.warn("ActionTreeNodes will not fire their events in trees"
				+ " whose selection mode is none: " + manager.getName());
	}

	/**
	 * Sets the text for this node
	 * 
	 * @param text The text to display to the user
	 */
	public void setText(String text)
	{
		theText = text;
	}

	/**
	 * Sets the icon for this node
	 * 
	 * @param icon The icon to display for this node
	 */
	public void setIcon(String icon)
	{
		theIcon = icon;
	}

	public Color getBackground()
	{
		return Color.ORANGE;
	}

	public String getDescription()
	{
		return null;
	}

	public Color getForeground()
	{
		return Color.BLACK;
	}

	public String getIcon()
	{
		return theIcon;
	}

	public String getText()
	{
		return theText;
	}

	@Override
	public void userSetSelected(boolean selected)
	{
		if(selected)
		{
			DataTreeMgrPlugin plugin = (DataTreeMgrPlugin) getManager();
			plugin.setSelected(this, false);
			plugin.getSession().fireEvent(new prisms.arch.event.PrismsEvent(theEventName, "node", this));
		}
	}
}
