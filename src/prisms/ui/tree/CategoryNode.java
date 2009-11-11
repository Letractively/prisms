/**
 * CategoryNode.java Created Dec 13, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import java.awt.Color;


/**
 * A simple named node
 */
public class CategoryNode extends AbstractSimpleTreeNode
{
	private String theTitle;

	private String theIcon;

	/**
	 * Creates a Category node
	 * 
	 * @param mgr The tree manager managing the tree structure
	 * @param parent This node's parent
	 * @param title The text for this node
	 */
	public CategoryNode(DataTreeManager mgr, DataTreeNode parent, String title)
	{
		super(mgr, parent);
		theTitle = title;
	}

	public void setChildren(DataTreeNode [] children)
	{
		super.setChildren(children);
	}

	/**
	 * @see prisms.ui.list.DataListNode#getText()
	 */
	public String getText()
	{
		return theTitle;
	}

	/**
	 * @param text The text that this node should display
	 */
	public void setText(String text)
	{
		theTitle = text;
	}

	/**
	 * @see prisms.ui.list.DataListNode#getDescription()
	 */
	public String getDescription()
	{
		return theTitle;
	}

	/**
	 * @see prisms.ui.list.DataListNode#getIcon()
	 */
	public String getIcon()
	{
		return theIcon;
	}

	/**
	 * @param icon The icon for this node
	 */
	public void setIcon(String icon)
	{
		theIcon = icon;
	}

	/**
	 * @see prisms.ui.list.DataListNode#getBackground()
	 */
	public Color getBackground()
	{
		return Color.white;
	}

	/**
	 * @see prisms.ui.list.DataListNode#getForeground()
	 */
	public Color getForeground()
	{
		return Color.black;
	}

	public String toString()
	{
		return "Category: " + theTitle;
	}
}
