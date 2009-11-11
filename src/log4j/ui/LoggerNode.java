/**
 * Log4jCatNode.java Created Apr 17, 2009 by Andrew Butler, PSL
 */
package log4j.ui;

import java.awt.Color;

import org.apache.log4j.Logger;

import prisms.ui.tree.DataTreeMgrPlugin;
import prisms.ui.tree.DataTreeNode;
import prisms.ui.tree.SimpleTreePluginNode;

/**
 * Represents a {@link Logger} in a prisms tree
 */
public class LoggerNode extends SimpleTreePluginNode
{
	private Logger theLog;

	/**
	 * @param manager
	 * @param parent
	 * @param publicActions
	 * @param log The Logger for this node to represent
	 */
	public LoggerNode(DataTreeMgrPlugin manager, DataTreeNode parent, boolean publicActions,
		Logger log)
	{
		super(manager, parent, publicActions);
		theLog = log;
	}

	/**
	 * @return The Logger that this node represents
	 */
	public Logger getLogger()
	{
		return theLog;
	}

	/**
	 * @see prisms.ui.list.DataListNode#getBackground()
	 */
	public Color getBackground()
	{
		return Color.white;
	}

	/**
	 * @see prisms.ui.list.DataListNode#getDescription()
	 */
	public String getDescription()
	{
		return theLog.getName();
	}

	/**
	 * @see prisms.ui.list.DataListNode#getForeground()
	 */
	public Color getForeground()
	{
		return Color.black;
	}

	/**
	 * @see prisms.ui.list.DataListNode#getIcon()
	 */
	public String getIcon()
	{
		return "log4j/logger";
	}

	/**
	 * @see prisms.ui.list.DataListNode#getText()
	 */
	public String getText()
	{
		if(theLog.getName() != null)
		{
			String name = theLog.getName();
			int dotIdx = name.lastIndexOf('.');
			if(dotIdx >= 0)
				name = name.substring(dotIdx + 1);
			return name;
		}
		else
			return "Root";
	}
}
