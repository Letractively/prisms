/**
 * Worker.java Created Jun 13, 2008 by Andrew Butler, PSL
 */
package prisms.arch;

/**
 * Simply performs generic jobs in the background
 */
public interface Worker
{
	/**
	 * Runs a task in the background
	 * 
	 * @param r The task to perform
	 * @param listener The listener to notify in case the task throws a Throwable
	 */
	void run(Runnable r, ErrorListener listener);

	/**
	 * Informs this worker of the number of sessions in the application so it may know how many
	 * resources it should consume
	 * 
	 * @param count The number of sessions to be accommodated
	 */
	void setSessionCount(int count);

	/**
	 * Releases all of this worker's resources
	 */
	void close();

	/**
	 * An interface to listen for errors in a background task
	 */
	interface ErrorListener
	{
		/**
		 * Called when an error occurs in the background task
		 * 
		 * @param error The error that occurred
		 */
		void error(Error error);

		/**
		 * Called when a RuntimeException occurs in the background task
		 * 
		 * @param ex The exception that occurred
		 */
		void runtime(RuntimeException ex);
	}
}
