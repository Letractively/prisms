/**
 * CategoryNode.java Created Dec 13, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import java.awt.Color;

/** A simple named node */
public class CategoryNode extends AbstractSimpleTreeNode
{
	private String theText;

	private String theIcon;

	private String theDescription;

	private Color theBG;

	private Color theFG;

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
		theText = title;
		theDescription = title;
		theBG = Color.white;
		theFG = Color.black;
	}

	public String getText()
	{
		return theText;
	}

	/** @param text The text that this node should display */
	public void setText(String text)
	{
		theText = text;
	}

	public String getDescription()
	{
		return theDescription;
	}

	/** @param descrip The description that this node should display */
	public void setDescription(String descrip)
	{
		theDescription = descrip;
	}

	public String getIcon()
	{
		return theIcon;
	}

	/** @param icon The icon for this node */
	public void setIcon(String icon)
	{
		theIcon = icon;
	}

	public Color getBackground()
	{
		return theBG;
	}

	/** @param bg The background color that this node should display */
	public void setBackground(Color bg)
	{
		theBG = bg;
	}

	public Color getForeground()
	{
		return theFG;
	}

	/** @param fg The foreground color that this node should display */
	public void setForeground(Color fg)
	{
		theFG = fg;
	}

	@Override
	public String toString()
	{
		return "Category: " + theText;
	}
}
