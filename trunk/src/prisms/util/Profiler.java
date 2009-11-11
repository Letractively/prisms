/**
 * Profiler.java Created Aug 14, 2006 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * A class that allows a programmer to determine the amount of time spend doing certain routines
 */
public final class Profiler
{
	/**
	 * The static instance of Profiler, for ease of use
	 */
	public static final Profiler P = new Profiler("Main");

	private String theName;

	private String [] theRoutines;

	private long [] theRoutineStartTimes;

	private long [] theRoutineProfiles;

	private int [] theCallCounts;

	/**
	 * Creates a profiler
	 * 
	 * @param name The name for the profiler
	 */
	public Profiler(String name)
	{
		theName = name;
		clearSession();
	}

	/**
	 * Starts a profiling session
	 */
	public void startSession()
	{
		if(theRoutineStartTimes[0] >= 0)
			throw new IllegalStateException("Profiler session \"" + theName
				+ "\" is already running");
		theRoutineStartTimes[0] = System.currentTimeMillis();
	}

	/**
	 * Stops the profiling session
	 */
	public void stopSession()
	{
		long time = System.currentTimeMillis();
		for(int i = 0; i < theRoutines.length; i++)
		{
			if(theRoutineStartTimes[i] < 0)
				continue;
			theRoutineProfiles[i] += time - theRoutineStartTimes[i];
			theRoutineStartTimes[i] = -1;
		}
	}

	/**
	 * Removes all data from this Profiler, effectively starting over
	 */
	public void clearSession()
	{
		theRoutines = new String [] {"Other"};
		theRoutineStartTimes = new long [] {-1};
		theRoutineProfiles = new long [] {0};
		theCallCounts = new int [] {0};
	}

	/**
	 * @return A string report showing the amount of time spent in the various routines since the
	 *         session was cleared
	 */
	public String printReport()
	{
		StringBuffer ret = new StringBuffer("Profile \"" + theName + "\" Report:\n");
		int i;
		for(i = 1; i < theRoutines.length; i++)
		{
			ret.append(printProfile(i));
			ret.append('\n');
		}
		ret.append(printProfile(0));
		ret.append('\n');
		return ret.toString();
	}

	private String printProfile(int i)
	{
		StringBuffer ret = new StringBuffer(theRoutines[i]);
		ret.append(": ");
		if(i > 0)
		{
			ret.append(theCallCounts[i]);
			ret.append(" calls: ");
		}
		int days, hrs, mins, secs, millis;
		long profile = theRoutineProfiles[i];
		millis = (int) (profile % 1000);
		profile /= 1000;
		secs = (int) (profile % 60);
		profile /= 60;
		mins = (int) (profile % 60);
		profile /= 60;
		hrs = (int) (profile % 24);
		profile /= 24;
		days = (int) profile;
		if(days > 0)
		{
			ret.append(days);
			ret.append(" days ");
		}
		if(hrs > 0)
		{
			ret.append(hrs);
			ret.append(" hours ");
		}
		if(mins > 0)
		{
			ret.append(mins);
			ret.append(" minutes ");
		}
		if(secs > 0)
		{
			ret.append(secs);
			ret.append(" seconds ");
		}
		if(millis > 0)
		{
			ret.append(millis);
			ret.append(" millis");
		}
		return ret.toString();
	}

	/**
	 * Notifies the profiler that a routine has started
	 * 
	 * @param name The name of the routine
	 */
	public void startRoutine(String name)
	{
		name = name.intern();
		int i;
		for(i = 1; i < theRoutines.length && name != theRoutines[i]; i++);
		if(i == theRoutines.length)
		{
			String [] newRoutines = new String [i + 1];
			System.arraycopy(theRoutines, 0, newRoutines, 0, i);
			newRoutines[i] = name;
			theRoutines = newRoutines;
			long [] longArray = new long [i + 1];
			System.arraycopy(theRoutineStartTimes, 0, longArray, 0, i);
			longArray[i] = -1;
			theRoutineStartTimes = longArray;
			longArray = new long [i + 1];
			System.arraycopy(theRoutineProfiles, 0, longArray, 0, i);
			longArray[i] = 0;
			theRoutineProfiles = longArray;
			int [] intArray = new int [i + 1];
			System.arraycopy(theCallCounts, 0, intArray, 0, i);
			intArray[i] = 0;
			theCallCounts = intArray;
		}
		if(theRoutineStartTimes[i] >= 0)
		{
			stopRoutine(name);
			// throw new IllegalStateException("Routine " + name
			// + " already running: recursion not supported");
		}
		long time = System.currentTimeMillis();
		theRoutineStartTimes[i] = time;
		theCallCounts[i]++;
		if(theRoutineStartTimes[0] >= 0)
		{ // Stop the "other" routine
			theRoutineProfiles[0] += time - theRoutineStartTimes[0];
			theRoutineStartTimes[0] = -1;
		}
	}

	/**
	 * Notifies the profiler that a routine has ended
	 * 
	 * @param name The name of the routine
	 */
	public void stopRoutine(String name)
	{
		name = name.intern();
		int i;
		for(i = 1; i < theRoutines.length && name != theRoutines[i]; i++);
		if(i == theRoutines.length || theRoutineStartTimes[i] < 0)
			throw new IllegalStateException("Routine " + name
				+ " is already finished or has not been started");
		long time = System.currentTimeMillis();
		theRoutineProfiles[i] += time - theRoutineStartTimes[i];
		theRoutineStartTimes[i] = -1;
		for(i = 1; i < theRoutines.length && theRoutineStartTimes[i] < 0; i++);
		if(i == theRoutines.length)
			theRoutineStartTimes[0] = time;
	}
}
