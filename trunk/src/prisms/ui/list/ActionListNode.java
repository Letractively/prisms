package prisms.ui.list;

import java.awt.Color;

import org.apache.log4j.Logger;

/**
 * A node that fires an event when selected (and deselects itself). The manager for this node must
 * have a selection mode of single or multiple.
 */
public class ActionListNode extends SimpleListPluginNode
{
	private static Logger log = Logger.getLogger(ActionListNode.class);

	String theEventName;

	private String theText;

	private String theIcon;

	/**
	 * Creates an ActionListNode
	 * 
	 * @param manager The manager for this node
	 * @param eventName The name of the event that this node should fire when selected
	 */
	public ActionListNode(DataListMgrPlugin manager, String eventName)
	{
		super(manager, false);
		theEventName = eventName;
		if(manager.getSelectionMode() == DataListMgrPlugin.SelectionMode.NONE)
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

	public String getID()
	{
		return Integer.toHexString(hashCode());
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
			DataListMgrPlugin plugin = (DataListMgrPlugin) getManager();
			plugin.setSelected(this, false);
			plugin.getSession().fireEvent(new prisms.arch.event.PrismsEvent(theEventName, "node", this));
		}
	}
}
