/*
 * ProgramTracker.java Created Dec 10, 2008 by Andrew Butler, PSL
 */
package prisms.util;

import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

/** A simple utility to help in finding performance bottlenecks in a program */
public class ProgramTracker implements Cloneable
{
	private static final Logger log = Logger.getLogger(ProgramTracker.class);

	private static final int DEFAULT_INDENT_INCREMENT = 3;

	static final SimpleDateFormat [] formats = new SimpleDateFormat [] {
		new SimpleDateFormat("ddMMM HH:mm:ss.SSS"), new SimpleDateFormat("dd HH:mm:ss.SSS"),
		new SimpleDateFormat("HH:mm:ss.SSS"), new SimpleDateFormat("mm:ss.SSS"),
		new SimpleDateFormat("ss.SSS")};

	static final java.text.NumberFormat PERCENT_FORMAT = new java.text.DecimalFormat("0.0");

	static final java.text.NumberFormat LENGTH_FORMAT = new java.text.DecimalFormat("0.000");

	/** The format used to print length statistics */
	public static final java.text.NumberFormat NANO_FORMAT = new java.text.DecimalFormat("0.00E0");

	/**
	 * This static variable is to be used for <b>temporary</b> debugging purposes only. It allows
	 * for easier profiling of applications without extensive code changes to access the correct
	 * tracker. However, if this variable is used in more than one place, it may lead to
	 * unpredictable results and thrown exceptions. A different mechanism MUST be developed to
	 * access a tracker if profiling is to be integrated into the application permanently.
	 */
	public static ProgramTracker instance;

	private static java.util.concurrent.ConcurrentHashMap<Thread, ProgramTracker> theThreadTrackers;

	static
	{
		theThreadTrackers = new java.util.concurrent.ConcurrentHashMap<Thread, ProgramTracker>();
	}

	/**
	 * Sets a tracker as the tracker for the current thread
	 * 
	 * @param tracker The tracker to set as a tracker thread
	 */
	public static void setThreadTracker(ProgramTracker tracker)
	{
		if(tracker != null && tracker.theCurrentThread != Thread.currentThread())
			throw new IllegalArgumentException("The given tracker is not tracking for this thread");
		if(tracker == null)
			theThreadTrackers.remove(Thread.currentThread());
		else
			theThreadTrackers.put(tracker.theCurrentThread, tracker);
	}

	/** @return The tracker for the current thread */
	public static ProgramTracker getThreadTracker()
	{
		return theThreadTrackers.get(Thread.currentThread());
	}

	/**
	 * A configuration class that allows the printing of results of a tracking session to be
	 * customized
	 */
	public static class PrintConfig implements Cloneable
	{
		private float theAccentThreshold;

		private boolean isAsync;

		private long theOverallDisplayThreshold;

		private long theTaskDisplayThreshold;

		private int theIndent;

		/** Creates a print config */
		public PrintConfig()
		{
			theAccentThreshold = 0;
			isAsync = false;
			theOverallDisplayThreshold = 0;
			theTaskDisplayThreshold = 0;
			theIndent = DEFAULT_INDENT_INCREMENT;
		}

		/**
		 * @return The threshold below which no tracking data will be printed. This parameter is for
		 *         external use only
		 */
		public long getOverallDisplayThreshold()
		{
			return theOverallDisplayThreshold;
		}

		/**
		 * @param thresh The threshold below which no tracking data will be printed. This parameter
		 *        is for external use only
		 */
		public void setOverallDisplayThreshold(long thresh)
		{
			theOverallDisplayThreshold = thresh;
		}

		/** @return The threshold below which tasks will be omitted from the results */
		public long getTaskDisplayThreshold()
		{
			return theTaskDisplayThreshold;
		}

		/** @param thresh The threshold below which tasks will be omitted from the results */
		public void setTaskDisplayThreshold(long thresh)
		{
			theTaskDisplayThreshold = thresh;
		}

		/** @return The threshold percent above which a task will be accented in the result */
		public float getAccentThreshold()
		{
			return theAccentThreshold;
		}

		/** @param thresh The threshold percent above which a task will be accented in the result */
		public void setAccentThreshold(float thresh)
		{
			theAccentThreshold = thresh;
		}

		/** @return Whether the printing is being done concurrently with the tracker's run */
		public boolean isAsync()
		{
			return isAsync;
		}

		/** @param async Whether the printing is being done concurrently with the tracker's run */
		public void setAsync(boolean async)
		{
			isAsync = async;
		}

		/** @return The number of spaces to indent nested tasks */
		public int getIndent()
		{
			return theIndent;
		}

		/** @param indent The number of spaces to indent nested tasks */
		public void setIndent(int indent)
		{
			theIndent = indent;
		}

		@Override
		public PrintConfig clone()
		{
			PrintConfig ret;
			try
			{
				ret = (PrintConfig) super.clone();
			} catch(CloneNotSupportedException e)
			{
				throw new IllegalStateException(e.getMessage(), e);
			}
			return ret;
		}
	}

	/** A node representing a single execution or an aggregate of executions of a routine */
	public static class TrackNode implements Cloneable
	{
		/** The name of the routine */
		String name;

		/** The number of executions aggregated in this node */
		int count;

		/** The first time this task was executed */
		long startTime;

		/** The last time this task was executed */
		long latestStartTime;

		long latestStartNanos;

		/** The last time this task ended */
		long endTime;

		/** The total amount of time the routine executed */
		long length;

		/** Statistics kept on the length of this routine */
		RunningStatistic lengthStats;

		/** The parent routine */
		TrackNode parent;

		/** The subroutines of this routine */
		java.util.ArrayList<TrackNode> children;

		/**
		 * The number of times that this routine was {@link ProgramTracker#start(String) start}ed
		 * but not explicitly {@link ProgramTracker#start(String) end}ed
		 */
		public int unfinished;

		boolean isReleased;

		TrackNode(TrackNode aParent, String aName, long time, boolean withStats)
		{
			children = new java.util.ArrayList<TrackNode>();
			init(aParent, aName, time, withStats);
		}

		void init(TrackNode aParent, String aName, long time, boolean withStats)
		{
			parent = aParent;
			name = aName;
			count = 1;
			startTime = time;
			latestStartTime = time;
			endTime = -1;
			length = 0;
			unfinished = 0;
			isReleased = false;
			if(withStats)
			{
				if(lengthStats != null)
					lengthStats.clear();
				else
					lengthStats = new RunningStatistic(25);
			}
			else if(lengthStats != null)
				lengthStats = null;
		}

		/** @return The name of the routine */
		public String getName()
		{
			return name;
		}

		/** @return The parent routine */
		public TrackNode getParent()
		{
			return parent;
		}

		/** @return The number of executions aggregated in this node */
		public int getCount()
		{
			return count;
		}

		/** @return The first time this task was executed */
		public long getFirstStart()
		{
			return startTime;
		}

		/** @return The last time this task was executed */
		public long getLatestStart()
		{
			return latestStartTime;
		}

		/** @return The last time this task ended */
		public long getLastEnd()
		{
			return endTime;
		}

		/** @return The total amount of time the routine executed */
		public long getLength()
		{
			return length;
		}

		/** @return Statistics kept on the length of this routine */
		public RunningStatistic getLengthStats()
		{
			return lengthStats;
		}

		/** @return The subroutines of this routine */
		public TrackNode [] getChildren()
		{
			return children.toArray(new TrackNode [children.size()]);
		}

		void clear()
		{
			if(lengthStats != null)
				lengthStats.clear();
			children.clear();
		}

		/**
		 * @param config The print configuration for printing
		 * @return The amount of time that this routine took exclusive of its child routines
		 */
		public long getLocalLength(PrintConfig config)
		{
			long ret = getRealLength();
			if(config == null || !config.isAsync())
				for(TrackNode ch : children)
					ret -= ch.length;
			else
				for(Object ch : children.toArray())
					ret -= ((TrackNode) ch).length;
			return ret;
		}

		/**
		 * @return The total amount of time this task has been running. Unlike {@link #length}, this
		 *         method takes into account the amount of time since {@link #latestStartTime} and
		 *         now if the task is currently running
		 */
		public long getRealLength()
		{
			long ret = length;
			if(endTime < latestStartTime)
				ret += (System.currentTimeMillis() - latestStartTime);
			return ret;
		}

		/**
		 * @param threshold The threshold percent
		 * @param totalTime The total time that this node's tracker ran
		 * @return Whether this task took at least <code>threshold</code>% of <code>totalTime</code>
		 */
		public boolean isAccented(float threshold, long totalTime)
		{
			if(totalTime <= 0)
				return false;
			boolean accent = false;
			long realLength = getRealLength();
			float localPercent = getLocalLength(null) * 100.0f / totalTime;
			if(threshold > 0)
			{
				long thresholdTime = (long) (threshold / 100.0f * totalTime);
				if(localPercent >= threshold)
					accent = true;
				else if(realLength >= thresholdTime)
				{
					long length2 = realLength;
					for(TrackNode child : children)
					{
						if(child.getRealLength() >= thresholdTime)
							length2 -= child.getRealLength();
					}
					if(length2 >= thresholdTime)
						accent = true;
				}
			}
			return accent;
		}

		/**
		 * Merges this node's data with another's
		 * 
		 * @param node The node to merge with this one
		 */
		public void merge(TrackNode node)
		{
			count += node.count;
			if(node.startTime < startTime)
				startTime = node.startTime;
			if(node.latestStartTime > latestStartTime)
			{
				latestStartTime = node.latestStartTime;
				endTime = node.endTime;
			}
			length += node.length;
			unfinished += node.unfinished;
			if(lengthStats != null && node.lengthStats != null)
				lengthStats.merge(node.lengthStats);
			for(TrackNode child : node.children)
			{
				boolean found = false;
				for(TrackNode thisChild : children)
					if(thisChild.name == child.name)
					{
						thisChild.merge(child);
						found = true;
						break;
					}
				if(!found)
					children.add(child.clone());
			}
		}

		@Override
		public TrackNode clone()
		{
			TrackNode ret;
			try
			{
				ret = (TrackNode) super.clone();
			} catch(CloneNotSupportedException e)
			{
				throw new IllegalStateException("Clone not supported", e);
			}
			if(lengthStats != null)
				ret.lengthStats = lengthStats.clone();
			ret.parent = null;
			ret.children = new java.util.ArrayList<TrackNode>();
			for(TrackNode child : children)
			{
				TrackNode childClone = child.clone();
				childClone.parent = this;
				ret.children.add(childClone);
			}
			return ret;
		}

		@Override
		public String toString()
		{
			return toString(0, 0, 0);
		}

		/**
		 * Prints a representation
		 * 
		 * @param indent The amount to indent this line
		 * @param lastTime The time that the parent task started
		 * @param totalTime The total time spent in the tracker
		 * @return A string representing this task's execution statistics
		 */
		public String toString(int indent, long lastTime, long totalTime)
		{
			StringBuilder sb = new StringBuilder();
			write(indent, lastTime, totalTime, sb, null);
			return sb.toString();
		}

		void write(int indent, long lastTime, long totalTime, StringBuilder sb, PrintConfig config)
		{
			long localLength = getLocalLength(config);
			float localPercent = 0;
			float totalPercent = 0;
			boolean accent = false;
			long realLength = getRealLength();
			float accentThresh = config == null ? 0 : config.getAccentThreshold();
			if(totalTime > 0)
			{
				localPercent = localLength * 100.0f / totalTime;
				totalPercent = realLength * 100.0f / totalTime;
				if(accentThresh > 0)
				{
					long thresholdTime = (long) (accentThresh / 100.0f * totalTime);
					if(localPercent >= accentThresh)
						accent = true;
					else if(realLength >= thresholdTime)
					{
						long length2 = realLength;
						for(TrackNode child : children)
						{
							if(child.getRealLength() >= thresholdTime)
								length2 -= child.getRealLength();
						}
						if(length2 >= thresholdTime)
							accent = true;
					}
				}
			}
			int actualIndent = indent;
			if(accent)
			{
				sb.append("* ");
				actualIndent -= 2;
			}
			for(int i = 0; i < actualIndent; i++)
				sb.append(' ');
			sb.append(name);
			if(unfinished > 0 || endTime < latestStartTime)
			{
				sb.append(" (unfinished x");
				int uf = unfinished;
				if(endTime < latestStartTime)
					uf++;
				sb.append(uf);
				sb.append(")");
			}
			sb.append(' ');
			sb.append('(');
			printTime(startTime, lastTime, sb);
			sb.append(')');
			if(count > 1)
			{
				sb.append('x');
				sb.append(count);
			}
			sb.append(':');
			sb.append(' ');
			PrismsUtils.printTimeLength(localLength, sb, true);
			if(localPercent > 0)
			{
				sb.append(' ');
				sb.append(PERCENT_FORMAT.format(localPercent));
				sb.append('%');
			}
			if(!children.isEmpty())
			{
				sb.append(' ');
				sb.append('(');
				PrismsUtils.printTimeLength(realLength, sb, true);
				if(parent != null && totalPercent > 0)
				{
					sb.append(' ');
					sb.append(PERCENT_FORMAT.format(totalPercent));
					sb.append('%');
				}
				sb.append(" total)");
			}
			if(accent && lengthStats != null && lengthStats.isInteresting())
			{
				sb.append("        ");
				sb.append(lengthStats.toString(NANO_FORMAT));
			}
			if(accent)
				sb.append(" *");
		}

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
		 * Serializes this tracking node and its children to JSON
		 * 
		 * @return The JSON representation of this node. May be deserialized with
		 *         {@link #fromJson(JSONObject)}.
		 */
		public JSONObject toJson()
		{
			JSONObject ret = new JSONObject();
			ret.put("name", name);
			ret.put("count", Integer.valueOf(count));
			ret.put("startTime", Long.valueOf(startTime));
			ret.put("latestStartTime", Long.valueOf(latestStartTime));
			ret.put("latestStartNanos", Long.valueOf(latestStartNanos));
			ret.put("endTime", Long.valueOf(endTime));
			ret.put("length", Long.valueOf(length));
			ret.put("unfinished", Integer.valueOf(unfinished));
			if(lengthStats != null)
				ret.put("lengthStats", lengthStats.toJson());
			org.json.simple.JSONArray jsonChildren = new org.json.simple.JSONArray();
			ret.put("children", jsonChildren);
			for(TrackNode child : children)
				jsonChildren.add(child.toJson());
			return ret;
		}

		/**
		 * Deserializes a track node from a JSON representation
		 * 
		 * @param parent The parent node for the new node
		 * @param json The JSON-serialized node, serialized with {@link #toJson()}
		 * @return A track node with the same content as the one that was serialized
		 */
		public static TrackNode fromJson(TrackNode parent, JSONObject json)
		{
			TrackNode ret = new TrackNode(parent, (String) json.get("name"),
				((Number) json.get("startTime")).longValue(), false);
			ret.count = ((Number) json.get("count")).intValue();
			ret.latestStartTime = ((Number) json.get("latestStartTime")).longValue();
			ret.latestStartNanos = ((Number) json.get("latestStartNanos")).longValue();
			ret.endTime = ((Number) json.get("endTime")).longValue();
			ret.length = ((Number) json.get("length")).longValue();
			ret.unfinished = ((Number) json.get("unfinished")).intValue();
			if(json.get("lengthStats") != null)
				ret.lengthStats = RunningStatistic.fromJson((JSONObject) json.get("lengthStats"));
			for(JSONObject node : (java.util.List<JSONObject>) json.get("children"))
				ret.children.add(fromJson(ret, node));
			return ret;
		}
	}

	private String theName;

	private java.util.ArrayList<TrackNode> theCacheNodes;

	private java.util.ArrayList<TrackNode> theNodes;

	boolean isWithStats;

	private TrackNode theCurrentNode;

	private boolean isOn;

	private Thread theCurrentThread;

	/**
	 * Creates a ProgramTracker
	 * 
	 * @param name A label for this tracker
	 */
	public ProgramTracker(String name)
	{
		this(name, false);
	}

	/**
	 * Creates a ProgramTracker
	 * 
	 * @param name A label for this tracker
	 * @param withStats Whether this tracker uses statistical analysis
	 */
	public ProgramTracker(String name, boolean withStats)
	{
		theName = name;
		theCacheNodes = new java.util.ArrayList<TrackNode>();
		theNodes = new java.util.ArrayList<TrackNode>();
		isOn = true;
		isWithStats = withStats;
	}

	/** @return This tracker's name */
	public String getName()
	{
		return theName;
	}

	/** @param name The name for this tracker */
	public void setName(String name)
	{
		theName = name;
	}

	/** @return Whether this tracker is recording data */
	public boolean isOn()
	{
		return isOn;
	}

	/** @param on Whether this tracker should be on or off */
	public void setOn(boolean on)
	{
		isOn = on;
	}

	/** @return Whether this tracker records statistics about each repeated procedure */
	public boolean isWithStats()
	{
		return isWithStats;
	}

	/** @param withStats Whether this tracker should record statistics about each repeated procedure */
	public void setWithStats(boolean withStats)
	{
		isWithStats = withStats;
	}

	private TrackNode newNode(TrackNode aParent, String aName, long time)
	{
		if(theCacheNodes.isEmpty())
			return new TrackNode(aParent, aName, time, isWithStats);
		else
		{
			TrackNode ret = theCacheNodes.remove(theCacheNodes.size() - 1);
			ret.init(aParent, aName, time, isWithStats);
			return ret;
		}
	}

	void releaseNode(TrackNode node)
	{
		if(node.isReleased)
			return;
		theCacheNodes.add(node);
		node.isReleased = true;
		for(TrackNode child : node.children)
			releaseNode(child);
		node.clear();
	}

	/** Clears all previous execution data from the tracker */
	public void clear()
	{
		theCurrentNode = null;
		if(!theNodes.isEmpty())
		{
			for(TrackNode node : theNodes)
				releaseNode(node);
			theNodes.clear();
		}
		theCurrentThread = null;
	}

	/**
	 * Notifies this tracker that a routine is beginning
	 * 
	 * @param routine The name of the routine that is beginning
	 * @return The routine being run. This will be passed to {@link #end(TrackNode)} when the
	 *         routine is finished.
	 */
	public final TrackNode start(String routine)
	{
		if(!isOn)
			return null;
		Thread ct = Thread.currentThread();
		if(theCurrentThread == null)
			theCurrentThread = ct;
		else if(theCurrentThread != ct)
			throw new IllegalStateException("Program Trackers may not be used by multiple threads!");
		routine = routine.intern();
		long time = System.currentTimeMillis();
		long nanos = -1;
		if(isWithStats)
			nanos = System.nanoTime();
		if(theCurrentNode == null)
		{
			theCurrentNode = newNode(null, routine, time);
			theNodes.add(theCurrentNode);
		}
		else
		{
			TrackNode aggregate = null;
			for(TrackNode child : theCurrentNode.children)
				if(child.getName() == routine)
				{
					aggregate = child;
					break;
				}
			if(aggregate != null)
			{
				aggregate.count++;
				aggregate.latestStartTime = time;
				if(isWithStats)
					aggregate.latestStartNanos = nanos;
				theCurrentNode = aggregate;
			}
			else
			{
				TrackNode newNode = newNode(theCurrentNode, routine, time);
				if(isWithStats)
					newNode.latestStartNanos = nanos;
				theCurrentNode.children.add(newNode);
				theCurrentNode = newNode;
			}
		}
		return theCurrentNode;
	}

	/**
	 * Notifies this tracker that a routine is ending
	 * 
	 * @param routine The node of the routine that is ending
	 */
	public final void end(TrackNode routine)
	{
		if(!isOn || routine == null)
			return;
		Thread ct = Thread.currentThread();
		if(theCurrentThread != ct)
			throw new IllegalStateException("Program Trackers may not be used by multiple threads!");
		long time = System.currentTimeMillis();
		long nanos = -1;
		if(isWithStats)
			nanos = System.nanoTime();
		if(theCurrentNode == null || theCurrentNode != routine)
		{
			TrackNode cn = theCurrentNode;
			while(cn != null && cn != routine)
				cn = cn.parent;
			if(cn != null)
			{
				cn = theCurrentNode;
				while(cn != null && cn != routine)
				{
					cn.unfinished++;
					cn.length += time - theCurrentNode.latestStartTime;
					cn.endTime = time;
					// But don't pollute the statistics
					cn = cn.parent;
				}
				theCurrentNode = cn;
			}
			else
			{
				cn = routine;
				while(cn != null && cn != theCurrentNode)
					cn = cn.parent;
				if(cn != null)
					throw new IllegalStateException("Routine " + routine.getName() + " ended twice"
						+ " or ended after parent routine");
				else
					throw new IllegalStateException("Routine " + routine.getName()
						+ " not started or ended twice: " + printData(new StringBuilder()));
			}
		}
		if(theCurrentNode != null)
		{
			if(isWithStats && theCurrentNode.lengthStats != null)
				theCurrentNode.lengthStats.add((nanos - theCurrentNode.latestStartNanos) / 1.0e9f);
			theCurrentNode.length += time - theCurrentNode.latestStartTime;
			theCurrentNode.endTime = time;
			theCurrentNode = theCurrentNode.parent;
		}
	}

	/** @return The node representing the task that is currently executing */
	public TrackNode getCurrentTask()
	{
		return theCurrentNode;
	}

	/** @return The raw data gathered by this tracker */
	public final TrackNode [] getData()
	{
		return theNodes.toArray(new TrackNode [theNodes.size()]);
	}

	/**
	 * Prints the data gathered by this tracker to {@link System#out}
	 * 
	 * @see #printData(StringBuilder)
	 */
	public final void printData()
	{
		printData(0);
	}

	/**
	 * Prints the data gathered by this tracker to {@link System#out}
	 * 
	 * @param threshold The threshold above which percent items in the profiling will be highlighted
	 */
	public final void printData(float threshold)
	{
		printData(System.out, threshold);
	}

	/**
	 * Prints the data gathered by this tracker to the given stream
	 * 
	 * @param out The stream to print this tracker's compiled information to
	 * @param threshold The threshold above which percent items in the profiling will be highlighted
	 */
	public final void printData(java.io.PrintStream out, float threshold)
	{
		PrintConfig config = new PrintConfig();
		config.setAccentThreshold(threshold);
		StringBuilder sb = new StringBuilder();
		printData(sb, config);
		out.println(sb.toString());
	}

	/**
	 * Prints the data gathered by this tracker to a string builder
	 * 
	 * @param sb The string builder to append this tracker's compiled information to
	 * @return The string builder passed in
	 */
	public final StringBuilder printData(StringBuilder sb)
	{
		return printData(sb, new PrintConfig());
	}

	/**
	 * Prints the data gathered by this tracker to a string builder
	 * 
	 * @param sb The string builder to append this tracker's compiled information to
	 * @param threshold The threshold above which percent items in the profiling will be highlighted
	 * @return The string builder passed in
	 */
	public final StringBuilder printData(StringBuilder sb, float threshold)
	{
		PrintConfig config = new PrintConfig();
		config.setAccentThreshold(threshold);
		return printData(sb, config);
	}

	/**
	 * Prints the data gathered by this tracker to a string builder
	 * 
	 * @param sb The string builder to append this tracker's compiled information to
	 * @param config The print configuration to use to customize the result
	 * @return The string builder passed in
	 */
	public final StringBuilder printData(StringBuilder sb, PrintConfig config)
	{
		if(theNodes.isEmpty())
		{
			sb.append("No profiling data for tracker " + theName);
			return sb;
		}
		sb.append("Profiling data for tracker " + theName + ":");
		long totalTime = 0;
		for(TrackNode node : theNodes)
			totalTime += node.getRealLength();
		for(TrackNode node : theNodes)
			print(node, sb, 0, totalTime, 0, config);
		return sb;
	}

	/** Prints the data gathered by this tracker this class's log with debug priority */
	public final void logDebug()
	{
		logDebug(0);
	}

	/**
	 * Prints the data gathered by this tracker this class's log with debug priority
	 * 
	 * @param threshold The threshold above which percent items in the profiling will be highlighted
	 */
	public final void logDebug(float threshold)
	{
		java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
		java.io.PrintStream stream = new java.io.PrintStream(baos);
		printData(stream, threshold);
		log.debug("\n" + new String(baos.toByteArray()));
	}

	@Override
	public ProgramTracker clone()
	{
		ProgramTracker ret;
		try
		{
			ret = (ProgramTracker) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException("Clone not supported", e);
		}
		ret.theCacheNodes = new java.util.ArrayList<TrackNode>();
		ret.theNodes = new java.util.ArrayList<TrackNode>();
		ret.theCurrentNode = null;
		ret.theCurrentThread = null;
		for(TrackNode node : theNodes)
		{
			TrackNode clone = node.clone();
			ret.theNodes.add(clone);
			if(node == theCurrentNode)
				ret.theCurrentNode = clone;
		}
		return ret;
	}

	/**
	 * Serializes this tracker to JSON
	 * 
	 * @return A JSON-serialized representation of this tracker. May be deserialized with
	 *         {@link #fromJson(JSONObject)}
	 */
	public JSONObject toJson()
	{
		JSONObject ret = new JSONObject();
		ret.put("name", theName);
		ret.put("withStats", Boolean.valueOf(isWithStats));
		org.json.simple.JSONArray nodes = new org.json.simple.JSONArray();
		ret.put("nodes", nodes);
		for(TrackNode node : theNodes)
			nodes.add(node.toJson());
		return ret;
	}

	/**
	 * Merges this tracker's data with another's
	 * 
	 * @param tracker The tracker whose data to merge
	 */
	public void merge(ProgramTracker tracker)
	{
		for(TrackNode node : tracker.theNodes)
		{
			boolean found = false;
			for(TrackNode thisNode : theNodes)
				if(thisNode.name == node.name)
				{
					thisNode.merge(node);
					found = true;
					break;
				}
			if(!found)
				theNodes.add(node.clone());
		}
	}

	private void print(TrackNode node, StringBuilder sb, long lastTime, long totalTime, int indent,
		PrintConfig config)
	{
		if(node.parent != null && node.getRealLength() < config.getTaskDisplayThreshold())
			return;
		sb.append('\n');
		node.write(indent, lastTime, totalTime, sb, config);
		if(!config.isAsync())
			for(TrackNode ch : node.children)
				print(ch, sb, lastTime, totalTime, indent + config.getIndent(), config);
		else
			for(Object ch : node.children.toArray())
				print((TrackNode) ch, sb, lastTime, totalTime, indent + config.getIndent(), config);
	}

	/**
	 * @param node The node to get the depth of
	 * @return The depth of the node
	 */
	public static int getDepth(TrackNode node)
	{
		int ret;
		for(ret = 1; node != null; ret++)
			node = node.parent;
		return ret;
	}

	/**
	 * Deserializes a JSON-serialized representation of a tracker
	 * 
	 * @param json The JSON representation of a tracker serialized with {@link #toJson()}
	 * @return A tracker with the same content as the one serialized
	 */
	public static ProgramTracker fromJson(JSONObject json)
	{
		ProgramTracker ret = new ProgramTracker((String) json.get("name"),
			((Boolean) json.get("withStats")).booleanValue());
		for(JSONObject node : (java.util.List<JSONObject>) json.get("nodes"))
			ret.theNodes.add(TrackNode.fromJson(null, node));
		return ret;
	}
}
