/*
 * ConnectionFactory.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.arch;

import prisms.arch.ds.Transactor;

/** A factory that configures database connections for the PRISMS framework */
public interface ConnectionFactory
{
	/**
	 * Configures this factory
	 * 
	 * @param configEl The configuration to use to configure this factory
	 */
	void configure(PrismsConfig configEl);

	/**
	 * Creates or retrieves an SQL connection using a configuration element
	 * 
	 * @param <T> The type of exception that the transactor will throw on errors
	 * @param config The configuration describing how to retrieve or create the connection
	 * @param duplicateID If not null, specifies an ID for a duplicate connection with the same
	 *        settings at the normal connection for the given config
	 * @param thrower The thrower to throw exceptions for the transactor. If this is null, the
	 *        result will be a Transactor&lt;java.sql.SQLException&gt;
	 * @return The configured connection
	 */
	<T extends Throwable> Transactor<T> getConnection(PrismsConfig config, String duplicateID,
		Transactor.Thrower<T> thrower);

	/**
	 * @param config The configuration that refers to a connection configuration
	 * @return The connection configuration referred to by the given reference configuration, or
	 *         null if the reference is invalid
	 */
	PrismsConfig getConnectionConfig(PrismsConfig config);

	/** Releases all of this factory's resources */
	void destroy();
}
