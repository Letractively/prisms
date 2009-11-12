/**
 * ThreadPoolWorker.java Created Jun 13, 2008 by Andrew Butler, PSL
 */
package prisms.impl;

import org.apache.log4j.Logger;

import prisms.arch.Worker;

/**
 * A fairly simple but safe thread pool. The main method, {@link #run(Runnable, ErrorListener)},
 * runs a user's task in its own thread.<br /> The number of threads in a ThreadPool grows and
 * shrinks quadratically.
 */
public class ThreadPoolWorker implements Worker
{
	private class TaskQueueObject
	{
		final Runnable task;

		final ErrorListener listener;

		TaskQueueObject(Runnable aTask, ErrorListener aListener)
		{
			task = aTask;
			listener = aListener;
		}
	}

	static Logger log = Logger.getLogger(ThreadPoolWorker.class);

	private int theMaxThreadCount;

	int theThreadCounter;

	private java.util.concurrent.locks.ReentrantLock theLock;

	private java.util.List<ReusableThread> theAvailableThreads;

	private java.util.List<ReusableThread> theInUseThreads;

	private java.util.List<TaskQueueObject> theTaskQueue;

	/**
	 * A subtype of thread that takes up <i>very</i> little CPU time while resting, but may execute
	 * a given task instantaneously. Groups of this type are managed by the {@link ThreadPoolWorker}
	 * class.
	 */
	private class ReusableThread extends Thread
	{
		private boolean isAlive;

		private Runnable theTask;

		private ErrorListener theListener;

		ReusableThread()
		{
			isAlive = true;
			String name = ThreadPoolWorker.this.getClass().getName();
			int idx = name.lastIndexOf(".");
			if(idx >= 0)
				name = name.substring(idx + 1);
			String subName = " " + getClass().getName();
			idx = subName.lastIndexOf(".");
			if(subName.lastIndexOf("$") > idx)
				idx = subName.lastIndexOf("$");
			if(idx >= 0)
				subName = subName.substring(idx + 1);
			setName(name + " " + subName + " #" + theThreadCounter);
			theThreadCounter++;
		}

		public void run()
		{
			while(isAlive)
			{
				if(theTask != null)
				{
					try
					{
						try
						{
							theTask.run();
						} catch(prisms.util.CancelException e)
						{
							log.info(e.getMessage(), e);
						} catch(Error e)
						{
							theListener.error(e);
						} catch(RuntimeException e)
						{
							theListener.runtime(e);
						} catch(Throwable e)
						{
							log.error("Should be impossible to get here", e);
						}
					} catch(Throwable e)
					{
						log.error("Error listener threw exception: " + e);
					}
					theTask = null;
					if(!isAlive)
						break;
					release(this);
				}
				if(!isAlive)
					break;
				try
				{
					Thread.sleep(24L * 60 * 60 * 1000);
				} catch(InterruptedException e)
				{
					// Do nothing--this is normal for waking up
				}
			}
		}

		/**
		 * Runs the given task
		 * 
		 * @param task The task to be run
		 * @param listener The listener to notify in case the task throws a Throwable
		 */
		void run(Runnable task, ErrorListener listener)
		{
			theListener = listener;
			theTask = task;
			interrupt();
		}

		/**
		 * Kills this thread as soon as its current task is finished, or immediately if this thread
		 * is not currently busy
		 */
		void kill()
		{
			isAlive = false;
			interrupt();
		}

		/**
		 * @return Whether the thread is running a task or available to run one
		 */
		boolean isActive()
		{
			return isAlive;
		}
	}

	/**
	 * Creates a ThreadPool with no active threads.
	 */
	public ThreadPoolWorker()
	{
		theLock = new java.util.concurrent.locks.ReentrantLock();
		theAvailableThreads = new java.util.ArrayList<ReusableThread>();
		theInUseThreads = new java.util.ArrayList<ReusableThread>();
		theTaskQueue = new java.util.LinkedList<TaskQueueObject>();
		theMaxThreadCount = 100;
	}

	/**
	 * Gets and executes a task in a thread from the pool or creates a new thread if none is
	 * available and the maximum thread count for the pool is not met. If the max thread count is
	 * met, the task will be queued up to run when a currently-executing thread finishes its task
	 * and is released.
	 * 
	 * @see Worker#run(Runnable, ErrorListener)
	 */
	public void run(Runnable task, ErrorListener listener)
	{
		theLock.lock();
		try
		{
			if(theAvailableThreads.size() == 0)
				adjustThreadCount();
			if(theAvailableThreads.size() == 0)
			{
				log.warn("Maximum thread count exceeded--"
					+ "waiting to execute task until threads are released");
				theTaskQueue.add(new TaskQueueObject(task, listener));
				return;
			}
			else
			{
				ReusableThread thread = theAvailableThreads.get(theAvailableThreads.size() - 1);
				theAvailableThreads.remove(theAvailableThreads.size() - 1);
				theInUseThreads.add(thread);
				if(!thread.isAlive())
					thread.start();
				thread.run(task, listener);
			}
		} finally
		{
			theLock.unlock();
		}
	}

	/**
	 * Releases a thread back to the thread pool after it has performed its task
	 * 
	 * @param thread The thread that has performed its task and may be pooled again and reused
	 */
	void release(ReusableThread thread)
	{
		theLock.lock();
		try
		{
			if(theTaskQueue.size() > 0)
			{
				TaskQueueObject task = theTaskQueue.get(0);
				theTaskQueue.remove(0);
				thread.run(task.task, task.listener);
			}
			else
			{
				theInUseThreads.remove(thread);
				if(thread.isActive())
					theAvailableThreads.add(thread);
				adjustThreadCount();
			}
		} finally
		{
			theLock.unlock();
		}
	}

	/**
	 * Shrinks the thread pool's size to zero. Calling this method oes not kill any currently
	 * executing tasks, but it will cause tasks that are queued up to never be executed (a situation
	 * that only arises when the number of tasks needing to be executed exceeds this pool's maximum
	 * thread count).
	 */
	public void close()
	{
		theLock.lock();
		try
		{
			theTaskQueue.clear();
			java.util.Iterator<ReusableThread> iter;
			iter = theInUseThreads.iterator();
			while(iter.hasNext())
				iter.next().kill();
			iter = theAvailableThreads.iterator();
			while(iter.hasNext())
			{
				iter.next().kill();
				iter.remove();
			}
		} finally
		{
			theLock.unlock();
		}
	}

	/**
	 * @return The total number of threads managed by this thread pool
	 */
	public int getThreadCount()
	{
		return theAvailableThreads.size() + theInUseThreads.size();
	}

	/**
	 * @return The number of threads in this thread pool available for new tasks
	 */
	public int getAvailableThreadCount()
	{
		return theAvailableThreads.size();
	}

	/**
	 * @return The number of threads in this thread pool currently executing tasks
	 */
	public int getInUseThreadCount()
	{
		return theInUseThreads.size();
	}

	private void adjustThreadCount()
	{
		theLock.lock();
		try
		{
			int newTC = getNewThreadCount();
			int total = getThreadCount();
			if(newTC < total)
			{ // Need to kill some threads
				int killCount = total - newTC;
				for(; theAvailableThreads.size() > 0 && killCount > 0; killCount--)
					theAvailableThreads.remove(theAvailableThreads.size() - 1).kill();
				for(; theInUseThreads.size() > 0 && killCount > 0; killCount--)
					theInUseThreads.remove(theInUseThreads.size() - 1).kill();
			}
			else if(newTC > total)
			{
				int spawnCount = newTC - total;
				for(int t = 0; t < spawnCount; t++)
					theAvailableThreads.add(0, new ReusableThread());
			}
		} finally
		{
			theLock.unlock();
		}
	}

	private int getNewThreadCount()
	{
		int used = getInUseThreadCount();
		int total = getThreadCount();
		int ret;
		if(used == total)
		{
			for(ret = 1; ret * ret <= total; ret++);
			ret = ret * ret;
		}
		else
		{
			int ceilUsedSqrt;
			for(ceilUsedSqrt = 1; ceilUsedSqrt * ceilUsedSqrt < used; ceilUsedSqrt++);
			int floorTotalSqrt;
			for(floorTotalSqrt = 1; floorTotalSqrt * floorTotalSqrt <= total; floorTotalSqrt++);
			floorTotalSqrt--;
			if(ceilUsedSqrt < floorTotalSqrt - 1)
				ret = (ceilUsedSqrt + 1) * (ceilUsedSqrt + 1);
			else
				ret = total;
		}
		if(ret > theMaxThreadCount)
			ret = theMaxThreadCount;
		return ret;
	}

	/**
	 * @return The maximum number of threads this ThreadPool will spawn before rejecting thread
	 *         requests. The default maximum thread count is 100.
	 */
	public int getMaxThreadCount()
	{
		return theMaxThreadCount;
	}

	/**
	 * @param maxTC The maximum number of threads this ThreadPool should spawn before rejecting
	 *        thread requests. If <code>maxTC</code> is smaller than the number of threads currently
	 *        in use, no thread requests will be granted until that number is one less than
	 *        <code>maxTC</code>.
	 */
	public void setMaxThreadCount(int maxTC)
	{
		if(maxTC <= 0)
			throw new IllegalArgumentException("Maximum thread count must be greater than 0, not "
				+ maxTC);
		theLock.lock();
		try
		{
			theMaxThreadCount = maxTC;
			if(getThreadCount() > theMaxThreadCount)
				adjustThreadCount();
		} finally
		{
			theLock.unlock();
		}
	}

	/**
	 * @see prisms.arch.Worker#setSessionCount(int)
	 */
	public void setSessionCount(int count)
	{
		setMaxThreadCount(count);
	}
}
