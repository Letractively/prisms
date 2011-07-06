/**
 * logegoryTree.java Created Apr 17, 2009 by Andrew Butler, PSL
 */
package log4j.ui;

import org.apache.log4j.Logger;

import prisms.arch.PrismsSession;
import prisms.ui.tree.DataTreeNode;

/** Displays the entire hierarchy of Log4j {@link Logger}s to the user */
public class LoggerTree extends prisms.ui.tree.DataTreeMgrPlugin
{
	@Override
	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		setSelectionMode(SelectionMode.SINGLE);
		super.initPlugin(session, config);
		Logger rootlog = org.apache.log4j.Logger.getRootLogger();
		setRoot(new LoggerNode(this, null, false, rootlog));
	}

	@Override
	public void initClient()
	{
		java.util.Enumeration<Logger> logs = org.apache.log4j.LogManager.getCurrentLoggers();
		while(logs.hasMoreElements())
			addLogger(logs.nextElement());
		super.initClient();
	}

	@Override
	public void setSelection(DataTreeNode [] nodes, boolean fromUser)
	{
		super.setSelection(nodes, fromUser);
		if(nodes.length != 1 || !(nodes[0] instanceof LoggerNode))
			return;
		Logger log = ((LoggerNode) nodes[0]).getLogger();
		getSession().setProperty(log4j.app.Log4jProperties.selectedLogger, log);
	}

	void addLogger(Logger log)
	{
		String [] path = log.getName().split("\\.");
		addLogger((LoggerNode) getRoot(), log, path, 0);
	}

	void addLogger(LoggerNode subtree, Logger log, String [] path, int pathIndex)
	{
		LoggerNode toUse = null;
		for(prisms.ui.tree.DataTreeNode child : subtree.getChildren())
		{
			String [] childPath = ((LoggerNode) child).getLogger().getName().split("\\.");
			if(childPath[pathIndex].equals(path[pathIndex]))
			{
				toUse = (LoggerNode) child;
				break;
			}
		}
		if(pathIndex == path.length - 1)
		{
			if(toUse == null)
			{
				toUse = new LoggerNode(this, subtree, subtree.hasPublicActions(), log);
				subtree.setChildren(prisms.util.ArrayUtils.add(subtree.getChildren(), toUse));
			}
			return;
		}
		if(toUse == null)
		{
			Logger intermediateLog;
			StringBuilder name = new StringBuilder();
			for(int p = 0; p < pathIndex + 1; p++)
			{
				name.append(path[p]);
				if(p != pathIndex)
					name.append(".");
			}
			intermediateLog = org.apache.log4j.Logger.getLogger(name.toString());
			toUse = new LoggerNode(this, subtree, subtree.hasPublicActions(), intermediateLog);
			subtree.setChildren(prisms.util.ArrayUtils.add(subtree.getChildren(), toUse));
		}
		addLogger(toUse, log, path, pathIndex + 1);
	}
}
