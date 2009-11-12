/**
 * DataTreeNode.java Created Sep 10, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import prisms.ui.list.DataListNode;

/**
 * An extension of a list node that allows for a hierarchy of nodes
 */
public interface DataTreeNode extends DataListNode
{
	/**
	 * @return This node's parent
	 */
	DataTreeNode getParent();

	/**
	 * @return This node's children
	 */
	DataTreeNode [] getChildren();
}
