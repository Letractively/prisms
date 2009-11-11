/**
 * PrismsUtils.java Created Sep 11, 2008 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * A general utility class with methods useful in the PRISMS architecture
 */
public class PrismsUtils
{
	/**
	 * Allows the
	 * {@link PrismsUtils#navigate(Object, Object, prisms.util.PrismsUtils.TreeInterpreter)} method
	 * to navigate the trees and match nodes
	 * 
	 * @param <T1> The type of nodes the tree contains
	 * @param <T2> The type of data matching the node to search for
	 */
	public static interface TreeInterpreter<T1, T2>
	{
		/**
		 * @param parent The parent node to get children for
		 * @return The children of the given parent node in the tree
		 */
		T1 [] getChildren(T1 parent);

		/**
		 * @param child The child data
		 * @return The parent data of the given child
		 */
		T2 getParent(T2 child);

		/**
		 * @param node1 The tree node to match
		 * @param node2 The data node to match
		 * @return Whether the data node matches the tree node
		 */
		boolean nodesMatch(T1 node1, T2 node2);
	}

	private PrismsUtils()
	{
	}

	/**
	 * Encodes all unicode special characters in a string to an ascii form. This method is necessary
	 * because the mechanism which PRISMS uses to send strings from server to client does not
	 * correctly account for these characters. They must then be decoded on the client.
	 * 
	 * @param str The string to encode the unicode characters in before passing to the client
	 * @return The original string with all the unicode characters encoded to "\\u****" as they
	 *         would be typed in java code
	 */
	public static String encodeUnicode(String str)
	{
		for(int c = 0; c < str.length(); c++)
		{
			if(str.codePointAt(c) > 0x7F)
			{
				String hexString = Integer.toHexString(str.codePointAt(c));
				while(hexString.length() < 4)
					hexString = "0" + hexString;
				str = str.substring(0, c) + "\\u" + hexString + str.substring(c + 1);
				c += 5;
			}
		}
		return str;
	}

	/**
	 * Decodes a string encoded by {@link #encodeUnicode(String)}. Returns all encoded unicode
	 * special characters in a string to their unicode characters from the ascii form. This method
	 * is necessary because the mechanism which PRISMS uses to send strings from server to client
	 * and back does not correctly account for these characters. They must then be decoded on the
	 * client.
	 * 
	 * @param str The string to decode the unicode characters from
	 * @return The original string with all the unicode characters decoded from "\\u****" as they
	 *         would be typed in java code
	 */
	public static String decodeUnicode(String str)
	{
		int idx = str.indexOf("\\u");
		while(idx >= 0)
		{
			if(idx < str.length() - 5 && isHexDigit(str.charAt(idx + 2))
				&& isHexDigit(str.charAt(idx + 3)) && isHexDigit(str.charAt(idx + 4))
				&& isHexDigit(str.charAt(idx + 5)))
			{
				str = str.substring(0, idx)
					+ Character.toChars(Integer.parseInt(str.substring(idx + 2, idx + 6), 16))[0]
					+ str.substring(idx + 6);
			}
			idx = str.indexOf("\\u", idx + 1);
		}
		return str;
	}

	private static boolean isHexDigit(char c)
	{
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	/**
	 * A quick function to write a properties map--intended for the PrismsEvent constructor
	 * 
	 * @param props The properties in name value pairs
	 * @return A Map of the given properties
	 * @throws IllegalArgumentException If the arguments are not in String, Object, String,
	 *         Object... order
	 */
	public static java.util.Map<String, Object> eventProps(Object... props)
		throws IllegalArgumentException
	{
		return PrismsUtils.rEventProps(props);
	}

	/**
	 * A quick function to write a properties JSONObject--intended for the
	 * {@link prisms.arch.PrismsSession#postOutgoingEvent(org.json.simple.JSONObject)} method
	 * 
	 * @param props The properties in name value pairs
	 * @return A Map of the given properties
	 * @throws IllegalArgumentException If the arguments are not in String, Object, String,
	 *         Object... order
	 */
	public static org.json.simple.JSONObject rEventProps(Object... props)
	{
		if(props.length % 2 != 0)
			throw new IllegalArgumentException("PrismsUtils.eventProps takes an even number of"
				+ " arguments, not " + props.length);
		org.json.simple.JSONObject ret = new org.json.simple.JSONObject();
		for(int i = 0; i < props.length; i += 2)
		{
			if(!(props[i] instanceof String))
				throw new IllegalArgumentException("Every other object passed to"
					+ " PrismsUtils.eventProps must be a string, not " + props[i]);
			ret.put(props[i], props[i + 1]);
		}
		return ret;
	}

	/**
	 * Modifies the stack trace to add information from the code that caused a Runnable task to be
	 * run. The result is as if the runnable run method was called directly instead of adding the
	 * task to be run later. This is beneficial because it gives information about what really
	 * caused the task to be run.
	 * 
	 * @param innerTrace The stack trace of the exception that was thrown in response to an error
	 * @param outerTrace The stack trace of an exception that was created (but not thrown) outside
	 *        the runnable task for reference
	 * @param className The name of the runnable class--typically calling this method with
	 *        "getClass().getName()" is what is required
	 * @param methodName The name of the method called in the runnable class--equivalent to
	 *        {@link Runnable#run()}
	 * @return The modified stack trace
	 */
	public static StackTraceElement [] patchStackTraces(StackTraceElement [] innerTrace,
		StackTraceElement [] outerTrace, String className, String methodName)
	{
		if(innerTrace == null || innerTrace.length == 0 || outerTrace == null
			|| outerTrace.length == 0)
			return innerTrace;
		int i = innerTrace.length - 1;
		while(i >= 0
			&& !(innerTrace[i].getClassName().equals(className) && innerTrace[i].getMethodName()
				.equals(methodName)))
			i--;
		if(i < 0)
			return innerTrace;
		StackTraceElement [] ret = new StackTraceElement [i + outerTrace.length];
		System.arraycopy(innerTrace, 0, ret, 0, i + 1);
		System.arraycopy(outerTrace, 1, ret, i + 1, outerTrace.length - 1);
		return ret;
	}

	/**
	 * Searches for a particular node in a tree structure
	 * 
	 * @param <T1> The type of nodes the tree contains
	 * @param <T2> The type of data matching the node to search for
	 * @param root The root of the tree or subtree to search in
	 * @param node The data to search the tree for
	 * @param interp The tree interpreter allowing this method to match nodes and navigate the tree
	 * @return The tree node found under <code>root</code> matching <code>node</code>
	 */
	public static <T1, T2> T1 navigate(T1 root, T2 node, PrismsUtils.TreeInterpreter<T1, T2> interp)
	{
		java.util.ArrayList<T2> path = new java.util.ArrayList<T2>();
		while(node != null && !interp.nodesMatch(root, node))
		{
			path.add(node);
			node = interp.getParent(node);
		}
		if(node == null) // node is not found beneath root
			return null;
		path.add(node);
		java.util.Collections.reverse(path);
		return navigate(root, path, interp, 1);
	}

	private static <T1, T2> T1 navigate(T1 root, java.util.List<T2> path,
		TreeInterpreter<T1, T2> interp, int pathIdx)
	{
		if(pathIdx == path.size())
			return root;
		T1 [] children = interp.getChildren(root);
		for(T1 child : children)
			if(interp.nodesMatch(child, path.get(pathIdx)))
				return navigate(child, path, interp, pathIdx + 1);
		return root; // Return the lowest subtree of root that is an ancestor of the target node
	}

	/**
	 * Searches for a node in a tree in a much more brute force way than
	 * {@link #navigate(Object, Object, TreeInterpreter)}. The getParent method in the tree
	 * interpreter passed to this method is never called, but rather the utility searches the entire
	 * tree depth-first for the given node.
	 * 
	 * @param <T1> The type of node in the tree to search
	 * @param <T2> The type of the node to search for
	 * @param root The root of the tree or subtree to search
	 * @param node The node to search for
	 * @param interp The interpreter for the tree and the search node
	 * @return The node in <code>root</code> matching <code>node</code> according to
	 *         <code>interp</code>
	 */
	public static <T1, T2> T1 search(T1 root, T2 node, PrismsUtils.TreeInterpreter<T1, T2> interp)
	{
		if(interp.nodesMatch(root, node))
			return root;
		for(T1 child : interp.getChildren(root))
		{
			T1 ret = search(child, node, interp);
			if(ret != null)
				return ret;
		}
		return null;
	}

	/**
	 * Prints a representation of the tree in a way that allows the developer to view the hierarchy
	 * 
	 * @param <T1> The type of node in the tree
	 * @param <T2> Arbitrary--only used to avoid creating a new tree interpreter interface
	 * @param root The root of the tree to print
	 * @param interp The interpreter to get the children of the root
	 * @return A string representing the tree
	 */
	public static <T1, T2> String printTree(T1 root, TreeInterpreter<T1, T2> interp)
	{
		return printTree(root, interp, 0);
	}

	private static <T1, T2> String printTree(T1 root, TreeInterpreter<T1, T2> interp, int indent)
	{
		StringBuilder ret = new StringBuilder();
		for(int i = 0; i < indent; i++)
			ret.append("    ");
		ret.append(root);
		for(T1 child : interp.getChildren(root))
		{
			ret.append('\n');
			ret.append(printTree(child, interp, indent + 1));
		}
		return ret.toString();
	}
}
