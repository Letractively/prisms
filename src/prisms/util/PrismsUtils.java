/**
 * PrismsUtils.java Created Sep 11, 2008 by Andrew Butler, PSL
 */
package prisms.util;

import java.util.Calendar;

import org.json.simple.JSONObject;

import prisms.arch.ds.PasswordConstraints;

/** A general utility class with methods useful in the PRISMS architecture */
public class PrismsUtils
{
	/** Represents different precisions to which a time can be represented in text */
	public enum TimePrecision
	{
		/** Represents a time to days */
		DAYS("ddMMMyyyy", false),
		/** Represents a time to minutes */
		MINUTES("ddMMMyyyy HHmm"),
		/** Represents a time to seconds */
		SECONDS("ddMMMyyyy HH:mm:ss"),
		/** Represents a time to milliseconds */
		MILLIS("ddMMMyyyy HH:mm:ss.SSS");

		private java.util.concurrent.locks.Lock theLock;

		/** The GMT date format for this precision */
		private final java.text.SimpleDateFormat gmtFormat;

		/** The local date format for this precision */
		private final java.text.SimpleDateFormat localFormat;

		TimePrecision(String dateFormat)
		{
			this(dateFormat, true);
		}

		TimePrecision(String dateFormat, boolean withZ)
		{
			theLock = new java.util.concurrent.locks.ReentrantLock();
			localFormat = new java.text.SimpleDateFormat(dateFormat);
			if(withZ)
				dateFormat += "'Z'";
			gmtFormat = new java.text.SimpleDateFormat(dateFormat);
			gmtFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
		}

		/**
		 * Prints a GMT-refrenced representation of the given time for this precision
		 * 
		 * @param time The time to represent
		 * @param local Whether to print the time in the local time zone or in GMT
		 * @return The representation of <code>time</code>
		 */
		public String print(long time, boolean local)
		{
			java.text.SimpleDateFormat format = local ? localFormat : gmtFormat;
			theLock.lock();
			try
			{
				return format.format(new java.util.Date(time));
			} finally
			{
				theLock.unlock();
			}
		}

		/**
		 * Parses a GMT-referenced date for this precision
		 * 
		 * @param time The formatted time string
		 * @param local Whether to print the time in the local time zone or in GMT
		 * @return The java time value represented by <code>time</code>
		 */
		public long parse(String time, boolean local)
		{
			java.text.SimpleDateFormat format = local ? localFormat : gmtFormat;
			theLock.lock();
			try
			{
				return format.parse(time).getTime();
			} catch(java.text.ParseException e)
			{
				throw new IllegalArgumentException("Cannot parse " + time + " to a time", e);
			} finally
			{
				theLock.unlock();
			}
		}
	}

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

	private static final String randomEvent = Long
		.toHexString((long) (Math.random() * Long.MAX_VALUE));

	private static final prisms.arch.event.PrismsProperty<?> randomProperty = prisms.arch.event.PrismsProperty
		.create(randomEvent, PrismsUtils.class);

	private static boolean isJava6;

	private static java.util.Random theRandom;

	static
	{
		theRandom = new java.util.Random();
		try
		{
			isJava6 = java.sql.Connection.class.getMethod("isValid", Integer.class) != null;
		} catch(Exception e)
		{
			isJava6 = false;
		}
	}

	private PrismsUtils()
	{
	}

	/** @return Whether the JVM being run has JDK-6 classes. */
	public static boolean isJava6()
	{
		return isJava6;
	}

	/**
	 * Creates a random hexadecimal string with the given length
	 * 
	 * @param chars The length for the random string
	 * @return The random string
	 */
	public static String getRandomString(int chars)
	{
		StringBuilder ret = new StringBuilder();
		while(ret.length() < chars)
		{
			int next = theRandom.nextInt();
			ret.append(Integer.toHexString(next));
		}
		if(ret.length() != chars)
			ret.setLength(chars);
		return ret.toString();
	}

	/** @return A non-negative random integer */
	public static int getRandomInt()
	{
		return theRandom.nextInt() >>> 1;
	}

	/**
	 * Prints a military-style GMT-referenced date
	 * 
	 * @param time The time to represent
	 * @return The <code>ddMMMyyyy HHmm'Z'</code> representation of <code>time</code>
	 */
	public static String print(long time)
	{
		return TimePrecision.MINUTES.print(time, false);
	}

	/**
	 * Parses a military-style GMT-referenced date
	 * 
	 * @param time The formatted time string
	 * @return The java time value represented by <code>time</code>
	 */
	public static long parse(String time)
	{
		return TimePrecision.MINUTES.parse(time, false);
	}

	/**
	 * Prints a time length to a string
	 * 
	 * @param length The length of time in milliseconds
	 * @return The representation of the time length
	 */
	public static String printTimeLength(long length)
	{
		StringBuilder sb = new StringBuilder();
		printTimeLength(length, sb, false);
		return sb.toString();
	}

	/**
	 * Prints a time length to a string builder
	 * 
	 * @param length The length of time in milliseconds
	 * @param sb The StringBuilder to write the representation of the time length into
	 * @param abbrev Whether to shorten the string intelligibly as much as possible
	 */
	public static void printTimeLength(long length, StringBuilder sb, boolean abbrev)
	{
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
			if(abbrev)
				sb.append("d ");
			else if(days > 1)
				sb.append(" days ");
			else
				sb.append(" day ");
		}
		if(hrs > 0)
		{
			sb.append(hrs);
			if(abbrev)
				sb.append("h ");
			else if(hrs > 1)
				sb.append(" hours ");
			else
				sb.append(" hour ");
		}
		if(mins > 0)
		{
			sb.append(mins);
			if(abbrev)
				sb.append("m ");
			else if(mins > 1)
				sb.append(" minutes ");
			else
				sb.append(" minute ");
		}
		if(secs > 0)
		{
			sb.append(secs);
			if(millis > 0)
			{
				sb.append('.');
				if(millis < 100)
					sb.append('0');
				if(millis < 10)
					sb.append('0');
				sb.append(millis);
				millis = 0;
			}
			if(abbrev)
				sb.append("s ");
			else if(secs > 1)
				sb.append(" seconds ");
			else
				sb.append(" second ");
		}
		if(millis > 0)
		{
			sb.append(millis);
			if(abbrev)
				sb.append("mil ");
			else if(millis > 1)
				sb.append(" millis");
			else
				sb.append(" milli");
		}
		if(sb.length() == 0)
			sb.append("no time");
		else if(sb.charAt(sb.length() - 1) == ' ')
			sb.deleteCharAt(sb.length() - 1);
	}

	/**
	 * @param start The start time of the interval whose end time to print
	 * @param end The end time of the interval to print
	 * @param timeZone The timezone to print in
	 * @param calField The calendar field precision to print to
	 * @return A string representing the interval's end time in a context where the start time is
	 *         known. This results in a shorter string that is quicker to read.
	 */
	public static String printEndTime(long start, long end, java.util.TimeZone timeZone,
		int calField)
	{
		Calendar sc = Calendar.getInstance();
		if(timeZone != null)
			sc.setTimeZone(timeZone);
		sc.setTimeInMillis(start);
		Calendar ec = Calendar.getInstance();
		if(timeZone != null)
			ec.setTimeZone(timeZone);
		ec.setTimeInMillis(end);

		StringBuilder ret = new StringBuilder();
		int length = 0;
		switch(calField)
		{
		case Calendar.MILLISECOND:
			ret.append(ec.get(Calendar.MILLISECOND));
			length += 3;
			while(ret.length() < length)
				ret.insert(0, '0');
			//$FALL-THROUGH$
		case Calendar.SECOND:
			if(length > 0)
			{
				ret.insert(0, '.');
				length++;
			}
			ret.insert(0, ec.get(Calendar.SECOND));
			length += 3;
			while(ret.length() < length)
				ret.insert(0, '0');
			if(end - start < 60L * 1000 && sc.get(Calendar.MINUTE) == ec.get(Calendar.MINUTE))
				return ret.toString();
			//$FALL-THROUGH$
		case Calendar.MINUTE:
		case Calendar.HOUR_OF_DAY:
			if(length > 0)
			{
				ret.insert(0, ':');
				length++;
			}
			ret.insert(0, ec.get(Calendar.MINUTE));
			length += 2;
			while(ret.length() < length)
				ret.insert(0, '0');
			ret.insert(0, ':');
			ret.insert(0, ec.get(Calendar.HOUR_OF_DAY));
			length += 3;
			while(ret.length() < length)
				ret.insert(0, '0');
			if(end - start < 24L * 60 * 60 * 1000
				&& sc.get(Calendar.DAY_OF_MONTH) == ec.get(Calendar.DAY_OF_MONTH))
				return ret.toString();
			//$FALL-THROUGH$
		case Calendar.DAY_OF_MONTH:
			int day = ec.get(Calendar.DAY_OF_MONTH);
			if(length > 0)
			{
				ret.insert(0, ' ');
				length++;
			}
			ret.insert(0, day);
			length += 2;
			while(ret.length() < length)
				ret.insert(0, '0');
			if(end - start < 31L * 24 * 60 * 60 * 1000
				&& sc.get(Calendar.MONTH) == ec.get(Calendar.MONTH))
			{
				String suffix;
				if(day / 10 == 1)
					suffix = "th";
				else if(day % 10 == 1)
					suffix = "st";
				else if(day % 10 == 2)
					suffix = "nd";
				else if(day % 10 == 3)
					suffix = "rd";
				else
					suffix = "th";
				ret.insert(2, suffix);
				return ret.toString();
			}
			//$FALL-THROUGH$
		case Calendar.MONTH:
			String [] months = new String [] {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul",
				"Aug", "Sep", "Oct", "Nov", "Dec"};
			ret.insert(2, months[ec.get(Calendar.MONTH)]);
			length += 3;
			if(sc.get(Calendar.YEAR) == ec.get(Calendar.YEAR))
				return ret.toString();
			//$FALL-THROUGH$
		case Calendar.YEAR:
			return print(end);
		}
		return ret.toString();
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
		int c;
		for(c = 0; c < str.length() && str.codePointAt(c) <= 0x7f; c++);
		if(c == str.length())
			return str;

		StringBuilder ret = new StringBuilder();
		ret.append(str.substring(0, c));
		for(; c < str.length(); c++)
		{
			if(str.codePointAt(c) > 0x7f)
			{
				ret.append("\\u");
				String hexString = Integer.toHexString(str.codePointAt(c));
				for(int i = 0; i < 4 - hexString.length(); i++)
					ret.append('0');
				ret.append(hexString);
			}
			else
				ret.append(str.charAt(c));
		}
		return ret.toString();
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
		int index = str.indexOf("\\u");
		if(index >= 0)
		{
			StringBuilder sb = new StringBuilder();
			charLoop: for(int c = 0; c < str.length(); c++)
			{
				if(c >= index && c < str.length() - 5 && str.charAt(c) == '\\'
					&& str.charAt(c + 1) == 'u')
				{
					final int preC = c;
					c += 2;
					int code = 0;
					for(int i = 0; i < 4; i++)
					{
						int hex = fromHex(str.charAt(c));
						if(hex < 0)
						{ // Doesn't match \\uXXXX--don't adjust
							c = preC;
							sb.append(str.charAt(c));
							continue charLoop;
						}
						code = code * 16 + hex;
						c++;
					}
					c--;
					char [] codeChars = Character.toChars(code);
					for(char ch : codeChars)
						sb.append(ch);
				}
				else
					sb.append(str.charAt(c));
			}
			str = sb.toString();
		}
		return str;
	}

	private static int fromHex(char c)
	{
		if(c >= '0' && c <= '9')
			return c - '0';
		else if(c >= 'a' && c <= 'f')
			return c - 'a' + 10;
		else if(c >= 'A' && c <= 'F')
			return c - 'A' + 10;
		else
			return -1;
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
	 * Serializes password constraints to JSON
	 * 
	 * @param constraints The password constraints to serialize
	 * @return The JSON-serialize password constraints
	 */
	public static JSONObject serializeConstraints(PasswordConstraints constraints)
	{
		JSONObject ret = new JSONObject();
		ret.put("minLength", Integer.valueOf(constraints.getMinCharacterLength()));
		ret.put("minUpperCase", Integer.valueOf(constraints.getMinUpperCase()));
		ret.put("minLowerCase", Integer.valueOf(constraints.getMinLowerCase()));
		ret.put("minDigits", Integer.valueOf(constraints.getMinDigits()));
		ret.put("minSpecialChars", Integer.valueOf(constraints.getMinSpecialChars()));
		ret.put("maxDuration", Long.valueOf(constraints.getMaxPasswordDuration()));
		ret.put("numUnique", Integer.valueOf(constraints.getNumPreviousUnique()));
		ret.put("minChangeInterval", Long.valueOf(constraints.getMinPasswordChangeInterval()));
		return ret;
	}

	/**
	 * Deserializes password constraints from JSON
	 * 
	 * @param constraints The JSON-serialized password constraints
	 * @return The deserialized password constraints
	 */
	public static PasswordConstraints deserializeConstraints(JSONObject constraints)
	{
		PasswordConstraints ret = new PasswordConstraints();
		ret.setMinCharacterLength(((Number) constraints.get("minLength")).intValue());
		ret.setMinUpperCase(((Number) constraints.get("minUpperCase")).intValue());
		ret.setMinLowerCase(((Number) constraints.get("minLowerCase")).intValue());
		ret.setMinDigits(((Number) constraints.get("minDigits")).intValue());
		ret.setMinSpecialChars(((Number) constraints.get("minSpecialChars")).intValue());
		ret.setMaxPasswordDuration(((Number) constraints.get("maxDuration")).longValue());
		ret.setNumPreviousUnique(((Number) constraints.get("numUnique")).intValue());
		ret.setMinPasswordChangeInterval(((Number) constraints.get("minChangeInterval"))
			.longValue());
		return ret;
	}

	/**
	 * Self-test method
	 * 
	 * @param args Command line args, ignored
	 */
	public static void main(String [] args)
	{
		String str = "This is a unicode string this (\\u00b0) is a unicode symbol";
		System.out.println("Original=" + str);
		str = decodeUnicode(str);
		System.out.println("Decoded=" + str);
		str = encodeUnicode(str);
		System.out.println("Encoded=" + str);
	}
}
