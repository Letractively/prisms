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

	private static final java.text.SimpleDateFormat theFcastFormat;

	private static final String randomEvent = Long
		.toHexString((long) (Math.random() * Long.MAX_VALUE));

	private static final prisms.arch.event.PrismsProperty<?> randomProperty = prisms.arch.event.PrismsProperty
		.create(randomEvent, PrismsUtils.class);

	static
	{
		theFcastFormat = new java.text.SimpleDateFormat("ddMMMyyyy HHmm'Z'");
		theFcastFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
	}

	private PrismsUtils()
	{
	}

	/**
	 * Prints a military-style GMT-referenced date
	 * 
	 * @param time The time to represent
	 * @return The <code>ddMMMyyyy HHmm'Z'</code> representation of <code>time</code>
	 */
	public static String print(long time)
	{
		return theFcastFormat.format(new java.util.Date(time));
	}

	/**
	 * Parses a military-style GMT-referenced date
	 * 
	 * @param time The formatted time string
	 * @return The java time value represented by <code>time</code>
	 */
	public static long parse(String time)
	{
		try
		{
			return theFcastFormat.parse(time).getTime();
		} catch(java.text.ParseException e)
		{
			throw new IllegalArgumentException("Cannot parse " + time + " to a time", e);
		}
	}

	/**
	 * Prints a time length to a string builder
	 * 
	 * @param length The length of time in milliseconds
	 * @return The representation of the time length into
	 */
	public static String printTimeLength(long length)
	{
		StringBuilder sb = new StringBuilder();
		if(length == 0)
			sb.append("no time");
		int days, hrs, mins, secs, millis;
		millis = (int) (length % 1000);
		length /= 1000;
		secs = (int) (length % 60);
		length /= 60;
		mins = (int) (length % 60);
		length /= 60;
		hrs = (int) (length % 24);
		length /= 24;
		days = (int) length;
		if(days > 0)
		{
			sb.append(days);
			sb.append(" days ");
		}
		if(hrs > 0)
		{
			sb.append(hrs);
			sb.append(" hours ");
		}
		if(mins > 0)
		{
			sb.append(mins);
			sb.append(" minutes ");
		}
		if(secs > 0)
		{
			sb.append(secs);
			sb.append(" seconds ");
		}
		if(millis > 0)
		{
			sb.append(millis);
			sb.append(" millis");
		}
		return sb.toString();
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

	/**
	 * @param session The session to check
	 * @param eventName The event name to check
	 * @return Whether the event has any listeners in the session
	 */
	public static boolean hasEventListeners(prisms.arch.PrismsSession session, String eventName)
	{
		prisms.arch.event.PrismsEventListener[] listeners = session.getEventListeners(eventName);
		prisms.arch.event.PrismsEventListener[] generalListeners = session
			.getEventListeners(randomEvent);
		return listeners.length > generalListeners.length;
	}

	/**
	 * @param session The session to check
	 * @param property The property to check
	 * @return Whether the property has any listeners in the session
	 */
	public static boolean hasPropertyListeners(prisms.arch.PrismsSession session,
		prisms.arch.event.PrismsProperty<?> property)
	{
		prisms.arch.event.PrismsPCL<?> [] listeners = session.getPropertyChangeListeners(property);
		prisms.arch.event.PrismsPCL<?> [] generalListeners = session
			.getPropertyChangeListeners(randomProperty);
		return listeners.length > generalListeners.length;
	}

	/**
	 * Retrieves and parses XML for a given location
	 * 
	 * @param location The location of the XML file to parse
	 * @param relative The locations to which the location may be relative
	 * @return The root element of the given XMl file
	 * @throws java.io.IOException If an error occurs finding, reading, or parsing the file
	 */
	public static org.dom4j.Element getRootElement(String location, String... relative)
		throws java.io.IOException
	{
		String newLocation = resolve(location, relative);
		if(newLocation == null)
			return null;
		java.net.URL configURL;
		if(newLocation.startsWith("classpath:/"))
		{ // Classpath resource
			configURL = PrismsUtils.class
				.getResource(newLocation.substring("classpath:/".length()));
			if(configURL == null)
			{
				throw new java.io.FileNotFoundException("Classpath configuration URL "
					+ newLocation + " refers to a non-existent resource");
			}
		}
		else if(newLocation.contains(":/"))
		{ // Absolute resource
			configURL = new java.net.URL(newLocation);
		}
		else
		{
			throw new java.io.IOException("Location " + newLocation + " is invalid");
		}
		org.dom4j.Element configEl;
		try
		{
			configEl = new org.dom4j.io.SAXReader().read(configURL).getRootElement();
		} catch(org.dom4j.DocumentException e)
		{
			throw new javax.imageio.IIOException("Could not read XML file " + location, e);
		}
		return configEl;
	}

	/**
	 * Gets the location of a class to use in resolving relative paths with
	 * {@link #getRootElement(String, String...)}
	 * 
	 * @param clazz The class to get the location of
	 * @return The location of the class file
	 */
	public static String getLocation(Class<?> clazz)
	{
		return "classpath://" + clazz.getName().replaceAll("\\.", "/") + ".class";
	}

	private static String resolve(String location, String... relative) throws java.io.IOException
	{
		if(location.contains(":/"))
			return location;
		else if(relative.length > 0)
		{
			String resolvedRel = resolve(relative[0], ArrayUtils.remove(relative, 0));
			if(resolvedRel.contains(":/"))
			{
				String newLocation = location;
				do
				{
					int lastSlash = resolvedRel.lastIndexOf("/");
					resolvedRel = resolvedRel.substring(0, lastSlash);
					if(newLocation.startsWith("../"))
						newLocation = newLocation.substring(3);
				} while(newLocation.startsWith("../"));
				if(!resolvedRel.contains(":/"))
				{
					throw new java.io.IOException("Location " + location + " relative to "
						+ ArrayUtils.toString(relative) + " is invalid");
				}
				return resolvedRel + "/" + newLocation;
			}
			else
				return null;
		}
		else
		{
			throw new java.io.IOException("Location " + location + " is invalid");
		}
	}
}
