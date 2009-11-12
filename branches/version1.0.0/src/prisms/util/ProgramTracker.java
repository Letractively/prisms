/**
 * ProgramTracker.java Created Dec 10, 2008 by Andrew Butler, PSL
 */
package prisms.util;

import static prisms.util.ArrayUtils.add;

import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;

/**
 * A simple utility to help in finding performance bottlenecks in a program
 */
public class ProgramTracker
{
	private static final Logger log = Logger.getLogger(ProgramTracker.class);

	private static final int INDENT_INCREMENT = 3;

	/**
	 * A node representing a single execution or an aggregate of executions of a routine
	 */
	public class TrackNode
	{
		/**
		 * The name of the routine
		 */
		public final String name;

		/**
		 * The number of executions aggregated in this node
		 */
		public int count;

		/**
		 * The first time this task was executed
		 */
		public long startTime;

		/**
		 * The last time this task was executed
		 */
		public long latestStartTime;

		/**
		 * The last time this task ended
		 */
		public long endTime;

		/**
		 * The total amount of time the routine executed
		 */
		public long length;

		/**
		 * The parent routine
		 */
		public TrackNode parent;

		/**
		 * The subroutines of this routine
		 */
		public TrackNode [] children;

		TrackNode(TrackNode aParent, String aName, long time)
		{
			parent = aParent;
			name = aName;
			count = 1;
			startTime = time;
			latestStartTime = time;
			endTime = -1;
			length = 0;
			children = new TrackNode [0];
		}
	}

	private String theName;

	private TrackNode [] theNodes;

	private TrackNode theCurrentNode;

	/**
	 * Creates a ProgramTracker
	 * 
	 * @param name A label for this tracker
	 */
	public ProgramTracker(String name)
	{
		theName = name;
		theNodes = new TrackNode [0];
	}

	/**
	 * Clears all previous execution data from the tracker
	 */
	public void clear()
	{
		theNodes = new TrackNode [0];
		theCurrentNode = null;
	}

	/**
	 * Notifies this tracker that a routine is beginning
	 * 
	 * @param routine The name of the routine that is beginning
	 */
	public final void start(String routine)
	{
		routine = routine.intern();
		long time = System.currentTimeMillis();
		if(theCurrentNode == null)
		{
			theCurrentNode = new TrackNode(null, routine, time);
			theNodes = add(theNodes, theCurrentNode);
		}
		else
		{
			TrackNode aggregate = null;
			int ct = 0;
			for(int i = 0; i < theCurrentNode.children.length; i++)
				if(theCurrentNode.children[i].name == routine)
				{
					if(theCurrentNode.children[i].count > 1)
					{
						aggregate = theCurrentNode.children[i];
						break;
					}
					ct++;
				}
			if(ct >= 4)
			{
				int firstIdx = -1;
				TrackNode [] toMerge = new TrackNode [ct];
				int tm = 0;
				TrackNode [] newCh = new TrackNode [theCurrentNode.children.length - ct + 1];
				int nc = 0;
				for(int i = 0; i < theCurrentNode.children.length; i++)
				{
					if(theCurrentNode.children[i].name == routine)
					{
						if(firstIdx < 0)
						{
							firstIdx = i;
							newCh[nc++] = null;
						}
						toMerge[tm++] = theCurrentNode.children[i];
					}
					else
						newCh[nc++] = theCurrentNode.children[i];
				}
				aggregate = merge(toMerge);
				newCh[firstIdx] = aggregate;
				theCurrentNode.children = newCh;
			}
			if(aggregate != null)
			{
				aggregate.count++;
				aggregate.latestStartTime = time;
				theCurrentNode = aggregate;
			}
			else
			{
				TrackNode newNode = new TrackNode(theCurrentNode, routine, time);
				theCurrentNode.children = add(theCurrentNode.children, newNode);
				theCurrentNode = newNode;
			}
		}
	}

	/**
	 * Notifies this tracker that a routine is ending
	 * 
	 * @param routine The name of the routine that is ending
	 */
	public final void end(String routine)
	{
		routine = routine.intern();
		long time = System.currentTimeMillis();
		if(theCurrentNode == null || theCurrentNode.name != routine)
			throw new IllegalStateException("routine " + routine + " not started or ended twice");
		theCurrentNode.length += time - theCurrentNode.latestStartTime;
		theCurrentNode.endTime = time;
		theCurrentNode = theCurrentNode.parent;
	}

	/**
	 * @return The raw data gathered by this tracker
	 */
	public final TrackNode [] getData()
	{
		return theNodes;
	}

	/**
	 * Prints the data gathered by this tracker to {@link System#out}
	 * 
	 * @see #printData(java.io.PrintStream)
	 */
	public final void printData()
	{
		printData(System.out);
	}

	/**
	 * Prints the data gathered by this tracker to the given stream
	 * 
	 * @param out
	 */
	public final void printData(java.io.PrintStream out)
	{
		if(theNodes.length == 0)
		{
			out.println("No profiling data for tracker " + theName);
			return;
		}
		out.println("Profiling data for tracker " + theName + ":");
		for(int n = 0; n < theNodes.length; n++)
			print(theNodes[n], out, 0, 0);
	}

	/**
	 * Prints the data gathered by this tracker this class's log with debug priority
	 */
	public final void logDebug()
	{
		java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
		java.io.PrintStream stream = new java.io.PrintStream(baos);
		printData(stream);
		log.debug("\n" + new String(baos.toByteArray()));
	}

	void print(TrackNode node, java.io.PrintStream out, long lastTime, int indent)
	{
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < indent; i++)
			sb.append(' ');
		sb.append(node.name);
		sb.append(' ');
		sb.append('(');
		printTime(node.startTime, lastTime, sb);
		sb.append(')');
		if(node.count > 1)
		{
			sb.append('x');
			sb.append(node.count);
		}
		sb.append(':');
		sb.append(' ');
		long length = node.length;
		for(TrackNode ch : node.children)
			length -= ch.length;
		sb.append(printTimeLength(length));
		sb.append('(');
		sb.append(printTimeLength(node.length));
		sb.append(" total)");
		out.println(sb.toString());
		lastTime = node.startTime;
		for(TrackNode ch : node.children)
		{
			print(ch, out, lastTime, indent + INDENT_INCREMENT);
			lastTime = ch.startTime;
		}
	}

	private SimpleDateFormat [] formats = new SimpleDateFormat [] {
		new SimpleDateFormat("ddMMM HH:mm:ss.SSS"), new SimpleDateFormat("dd HH:mm:ss.SSS"),
		new SimpleDateFormat("HH:mm:ss.SSS"), new SimpleDateFormat("mm:ss.SSS"),
		new SimpleDateFormat("ss.SSS")};

	private void printTime(long time, long lastTime, StringBuilder sb)
	{
		java.util.Date d = new java.util.Date(time);
		if(lastTime == 0)
		{
			sb.append(formats[0].format(d));
			return;
		}
		long diff = time - lastTime;
		int days, hrs, mins;
		diff /= 1000;
		diff /= 60;
		mins = (int) (diff % 60);
		diff /= 60;
		hrs = (int) (diff % 24);
		diff /= 24;
		days = (int) diff;
		if(days > 0)
			sb.append(formats[1].format(d));
		else if(hrs > 0)
			sb.append(formats[2].format(d));
		else if(mins > 0)
			sb.append(formats[3].format(d));
		else
			sb.append(formats[4].format(d));
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

	final TrackNode merge(TrackNode [] nodes)
	{
		TrackNode ret = nodes[0];
		java.util.LinkedHashMap<String, TrackNode []> subNodes;
		subNodes = new java.util.LinkedHashMap<String, TrackNode []>();
		for(int i = 0; i < ret.children.length; i++)
		{
			TrackNode [] subNodes_i = subNodes.get(ret.children[i].name);
			if(subNodes_i == null)
				subNodes_i = new TrackNode [] {ret.children[i]};
			else
				subNodes_i = add(subNodes_i, ret.children[i]);
			subNodes.put(ret.children[i].name, subNodes_i);
		}
		for(int i = 1; i < nodes.length; i++)
		{
			ret.length += nodes[i].length;
			ret.count += nodes[i].count;
			for(int c = 0; c < nodes[i].children.length; c++)
			{
				TrackNode [] subNodes_i = subNodes.get(nodes[i].children[c].name);
				if(subNodes_i == null)
					subNodes_i = new TrackNode [] {nodes[i].children[c]};
				else
					subNodes_i = add(subNodes_i, nodes[i].children[c]);
				subNodes.put(nodes[i].children[c].name, subNodes_i);
			}
		}
		ret.latestStartTime = nodes[nodes.length - 1].latestStartTime;
		ret.endTime = nodes[nodes.length - 1].endTime;
		ret.children = prisms.util.ArrayUtils.adjust(ret.children, subNodes.values().toArray(
			new TrackNode [subNodes.size()] []),
			new prisms.util.ArrayUtils.DifferenceListener<TrackNode, TrackNode []>()
			{
				/**
				 * @see prisms.util.ArrayUtils.DifferenceListener#identity(java.lang.Object,
				 *      java.lang.Object)
				 */
				public boolean identity(TrackNode o1, TrackNode [] o2)
				{
					return o1.name == o2[0].name;
				}

				public TrackNode set(TrackNode o1, int idx1, int incMod, TrackNode [] o2, int idx2,
					int retIdx)
				{
					return merge(o2);
				}

				/**
				 * @see prisms.util.ArrayUtils.DifferenceListener#added(java.lang.Object, int, int)
				 */
				public TrackNode added(TrackNode [] o, int index, int retIdx)
				{
					return merge(o);
				}

				public TrackNode removed(TrackNode o, int index, int incMod, int retIdx)
				{
					return null;
				}
			});
		return ret;
	}
}
