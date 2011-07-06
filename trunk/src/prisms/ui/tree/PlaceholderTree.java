/*
 * PlaceholderTree.java Created Feb 5, 2009 by Andrew Butler, PSL
 */
package prisms.ui.tree;

/**
 * A simple tree plugin with sample nodes, intended as a UI placeholder to show a tree widget when
 * the backend has not yet been implemented
 */
public class PlaceholderTree extends DataTreeMgrPlugin
{
	@Override
	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
		PlaceholderTreeNode root = new PlaceholderTreeNode(null, "Root");
		setRoot(root);
		PlaceholderTreeNode child1 = new PlaceholderTreeNode(root, "A Node");
		PlaceholderTreeNode child2 = new PlaceholderTreeNode(root, "Another Node");
		PlaceholderTreeNode child3 = new PlaceholderTreeNode(root, "And Again");
		root.setChildren(new PlaceholderTreeNode [] {child1, child2, child3});
		PlaceholderTreeNode subchild1 = new PlaceholderTreeNode(child2, "A subnode");
		PlaceholderTreeNode subchild2 = new PlaceholderTreeNode(child2, "Another subnode");
		child2.setChildren(new PlaceholderTreeNode [] {subchild1, subchild2});
		PlaceholderTreeNode grandchild1 = new PlaceholderTreeNode(subchild1, "A grandnode");
		PlaceholderTreeNode grandchild2 = new PlaceholderTreeNode(subchild1, "Another grandnode");
		subchild1.setChildren(new PlaceholderTreeNode [] {grandchild1, grandchild2});
	}

	private class PlaceholderTreeNode extends SimpleTreePluginNode
	{
		private String theName;

		/**
		 * @param parent The node's parent
		 * @param name The name for the node (its text)
		 */
		public PlaceholderTreeNode(DataTreeNode parent, String name)
		{
			super(PlaceholderTree.this, parent, false);
			theName = name;
		}

		public String getText()
		{
			return theName;
		}

		public String getDescription()
		{
			return "A simple placeholder node named " + theName;
		}

		public String getIcon()
		{
			return "asset";
		}

		public java.awt.Color getBackground()
		{
			return java.awt.Color.white;
		}

		public java.awt.Color getForeground()
		{
			return java.awt.Color.black;
		}
	}
}
