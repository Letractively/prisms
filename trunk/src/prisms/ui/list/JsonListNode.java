/**
 * JsonListNode.java Created Jan 29, 2008 by Andrew Butler, PSL
 */
package prisms.ui.list;

/**
 * A DataListNode that simplifies remote communicating by serializing itself easily
 */
public interface JsonListNode extends DataListNode
{
	/**
	 * @return A JSONObject representing this node
	 */
	public org.json.simple.JSONObject toJSON();
}
