/**
 * JsonTreeNode.java Created Nov 5, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import prisms.ui.list.JsonListNode;

/**
 * A DataTreeNode that simplifies remote communicating by serializing itself easily
 */
public interface JsonTreeNode extends DataTreeNode, JsonListNode
{
}
