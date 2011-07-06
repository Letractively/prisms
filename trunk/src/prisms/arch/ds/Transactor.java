/*
 * Transactor.java Created Jun 17, 2011 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import prisms.arch.ConnectionFactory;
import prisms.arch.PrismsConfig;

/**
 * Allows operations to be performed in transaction mode so that a particular operation either
 * occurs completely or else has no effect at all
 * 
 * @param <T> The type of exception that operations can throw
 */
public interface Transactor<T extends Throwable> extends Cloneable
{
	/**
	 * An interface for performing modular transactions which can be rolled back in the event of a
	 * failure. Used for {@link Transactor#performTransaction(TransactionOperation, String)}.
	 * 
	 * @param <T> The type of exception that this operation can throw
	 */
	public interface TransactionOperation<T extends Throwable>
	{
		/**
		 * Performs an operation. The implementation of this method is responsible for all database
		 * calls.
		 * 
		 * @param stmt The database statement to use for reading and writing database data
		 * @return The object that should be returned from the calling method
		 * @throws T If an error occurs accessing the data
		 */
		Object run(java.sql.Statement stmt) throws T;
	}

	/** Allows calling code to be notified when a JDBC connection must be remade */
	public interface ReconnectListener
	{
		/**
		 * Called when the connection has been reconnected as a result of becoming invalid
		 * 
		 * @param initial Whether this is a result of an initial connection to the database
		 */
		public void reconnected(boolean initial);

		/**
		 * Called when {@link Transactor#release()} is called before transactor's resources are
		 * released
		 */
		public void released();
	}

	/**
	 * A Thrower is responsible for throwing a custom type of exception when an error happens in the
	 * transactor or in a {@link Transactor.TransactionOperation#run(java.sql.Statement)
	 * transaction}.
	 * 
	 * @param <T> The type of exception that this thrower throws
	 */
	public interface Thrower<T extends Throwable>
	{
		/**
		 * Throws an instance of this thrower's type of exception
		 * 
		 * @param message The message for the exception to throw
		 * @throws T Always
		 */
		void error(String message) throws T;

		/**
		 * Throws an instance of this thrower's type of exception
		 * 
		 * @param message The message for the exception to throw
		 * @param cause The cause for the exception to throw
		 * @throws T Always
		 */
		void error(String message, Throwable cause) throws T;
	}

	/** @return The connection configuration used to configure this transactor's connection */
	prisms.arch.PrismsConfig getConnectionConfig();

	/**
	 * @return The duplicate ID used to get this connection from
	 *         {@link ConnectionFactory#getConnection(PrismsConfig, String, Thrower)}
	 */
	String getDuplicateID();

	/**
	 * Adds a listener to be notified when a reconnect occurs
	 * 
	 * @param listener The listener to notify
	 */
	void addReconnectListener(ReconnectListener listener);

	/**
	 * Removes a listener from being notified when a reconnect occurs
	 * 
	 * @param listener The listener not to notify
	 * @return Whether the listener existed in this transactor
	 */
	boolean removeReconnectListener(ReconnectListener listener);

	/** @return The table prefix for this transactor's connection */
	String getTablePrefix();

	/** @return This transactor's synchronization lock */
	java.util.concurrent.locks.ReentrantReadWriteLock getLock();

	/** @return The Thrower used to create this Transactor */
	Thrower<T> getThrower();

	/**
	 * @return A positive ID that changes every time this transactor must reconnect to the database.
	 *         A value of 0 indicates that the connection has not been made.
	 */
	int getConnectionID();

	/**
	 * @return A new transactor with a duplicate of this transactor's connection. The connection
	 *         that the new transactor manages will be completely independent of this transactor's
	 *         connection.
	 */
	Transactor<T> clone();

	/**
	 * @return This transactor's connection
	 * @throws T If an error occurs connecting to the database
	 */
	java.sql.Connection getConnection() throws T;

	/**
	 * Checks the connection and attempts to renew it if it has timed out or otherwise become
	 * invalid
	 * 
	 * @return Whether the connection was made as a result of this call
	 * @throws T If the connection has been lost and cannot be renewed
	 */
	boolean checkConnected() throws T;

	/**
	 * Performs a modular operation. If an error occurs during the operation or when attempting to
	 * commit the database changes, all database modifications will be rolled back to keep the
	 * database in a consistent state.
	 * 
	 * @param <T2> The type of exception that the operation can throw
	 * @param op The operation to perform
	 * @param ifError The text for the error to throw if the commit statement fails
	 * @return The result of the operation (see {@link TransactionOperation#run(java.sql.Statement)}
	 *         )
	 * @throws T If an error occurs performing the transaction
	 */
	<T2 extends T> Object performTransaction(TransactionOperation<T2> op, String ifError) throws T;

	/**
	 * Retrieves values from a single row of the database.
	 * 
	 * @param <T2> The type of the item to retrieve
	 * @param stmt The statement to use to connect to the database. If null, this method will create
	 *        a temporary statement and close it after.
	 * @param sql The SQL to use to retrieve the value
	 * @param type The type of the item to retrieve: May be String, Number, or java.util.Date
	 * @return The Retreived value
	 * @throws T If an error occurs retrieving the value, 0 or more than 1 rows are selected by the
	 *         SQL, or the retrieved value is not of the given type.
	 */
	<T2> T2 getDBItem(java.sql.Statement stmt, String sql, Class<T2> type) throws T;

	/**
	 * Releases resources associated with this Transactor. This may or may not result in the
	 * database connection being closed
	 */
	void release();
}
