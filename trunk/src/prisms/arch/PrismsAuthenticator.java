/*
 * PrismsAuthenticator.java Created Nov 1, 2010 by Andrew Butler, PSL
 */
package prisms.arch;

import prisms.arch.PrismsServer.PrismsRequest;
import prisms.arch.ds.User;

/** Allows PRISMS to authenticate HTTP requests via modules */
public interface PrismsAuthenticator
{
	/**
	 * A session authenticator keeps all data needed to validate a single client's connection to
	 * PRISMS
	 */
	public static interface SessionAuthenticator
	{
		/**
		 * Asserts that the given request has permission to access PRISMS at all. This method does
		 * not need to test whether the given request has access to the target application, client,
		 * etc. It only needs to be concerned whether the user should be able to access PRISMS
		 * through the given request at all.
		 * 
		 * @param request The PRISMS request that the user is using to access PRISMS
		 * @param secure Whether this request is being made over a secure connection that has not
		 *        been jeopardized
		 * @return Either an {@link AuthenticationError} or the decrypted data parameter
		 * @throws PrismsException If an error occurs accessing the needed information
		 */
		RequestAuthenticator getRequestAuthenticator(PrismsRequest request, boolean secure)
			throws PrismsException;

		/**
		 * Allows this authenticator to request authentication in a custom manner
		 * 
		 * @param request The request made by the client before login
		 * @return An event to send to the client to ask the user to login. The response to this
		 *         event will be passed to
		 *         {@link SessionAuthenticator#getRequestAuthenticator(PrismsRequest, boolean)}
		 * @throws PrismsException If an error occurs accessing the needed information
		 */
		org.json.simple.JSONObject requestLogin(PrismsRequest request) throws PrismsException;

		/**
		 * @return Whether the user needs to change his/her password immediately
		 * @throws PrismsException If an error occurs getting the required information
		 */
		boolean needsPasswordChange() throws PrismsException;

		/**
		 * @return An event telling the client to change his/her password
		 * @throws PrismsException If an error occurs getting the required information
		 */
		org.json.simple.JSONObject requestPasswordChange() throws PrismsException;

		/**
		 * @param event The event asking for the password change
		 * @return The error that occurred changing the password, or null if the password change was
		 *         successful
		 * @throws PrismsException If an error occurs accessing the needed information
		 */
		AuthenticationError changePassword(org.json.simple.JSONObject event) throws PrismsException;

		/**
		 * Causes this authenticator to clear its caches for a particular session so that the
		 * authentication data it uses is strictly up-to-date
		 * 
		 * @param authInfo The authentication information for the session
		 * @return An ID for the authentication data being used now, or -1 if the authentication
		 *         data was already up-to-date.
		 * @throws PrismsException If an error occurs accessing the needed information
		 */
		long recheck() throws PrismsException;

		/**
		 * Destroys the given authentication info, releasing its resources
		 * 
		 * @param authInfo The authentication info to destroy
		 * @throws PrismsException If an error occurs accessing the needed information
		 */
		void destroy() throws PrismsException;
	}

	/** Contains authentication data for a single PRISMS request */
	public static interface RequestAuthenticator
	{
		/** @return Whether this authentication was unsuccessful */
		boolean isError();

		/**
		 * @return An error message that should display to the user, letting them know why the
		 *         request was not authenticated
		 */
		String getError();

		/**
		 * Used to determine whether the authentication error was recoverable, meaning the client
		 * should reattempt login, or not. This method is only useful if {@link #isError()} returns
		 * true.
		 * 
		 * @return Whether the client should reattempt login
		 */
		boolean shouldReattempt();

		/** @return If the request authenticated correctly, the decrypted content of the request */
		String getData();

		/**
		 * Encrypts data for passing to the client. This method may simply return the data
		 * unmodified if this is deemed secure enough.
		 * 
		 * @param response May be needed to set output parameters
		 * @param data The data to encrypt for sending to the client
		 * @return The encrypted data
		 * @throws PrismsException If an error occurs accessing the needed information
		 */
		String encrypt(javax.servlet.http.HttpServletResponse response, String data)
			throws PrismsException;
	}

	/** Represents a failure to authenticate */
	public static class AuthenticationError
	{
		/** The message to present to the user */
		public final String message;

		/** Whether login should be reattempted or an error returned */
		public final boolean reattempt;

		/**
		 * Creates an error response
		 * 
		 * @param errMessage The message to display to the user
		 * @param reLogin Whether login may be reattempted or else an error should be returned
		 */
		public AuthenticationError(String errMessage, boolean reLogin)
		{
			message = errMessage;
			reattempt = reLogin;
		}
	}

	/**
	 * Configures this authenticator
	 * 
	 * @param configEl The XML configuration element for this authenticator
	 * @param userSource The user source for this authenticator to use to get users from
	 * @param apps The applications that may be accessed behind this authenticator
	 */
	void configure(PrismsConfig configEl, prisms.arch.ds.UserSource userSource,
		PrismsApplication [] apps);

	/**
	 * @param request The request attempting to access PRISMS
	 * @return Whether this authenticator can perform authentication for the given request
	 */
	boolean recognized(PrismsRequest request);

	/**
	 * @param request The request attempting to access PRISMS
	 * @return The user represented by the request. This should NEVER be null.
	 * @throws PrismsException If an error occurs determining or retrieving the user for the request
	 */
	User getUser(PrismsRequest request) throws PrismsException;

	/**
	 * Allows this authenticator to create a chunk of information to be associated with the session
	 * that the request belongs to.
	 * 
	 * @param request The request attempting to access PRISMS
	 * @param user The user returned from {@link #getUser(PrismsRequest)} for the request
	 * @return Information to be passed to other methods in this authenticator for the same session
	 * @throws PrismsException If an error occurs creating the needed information
	 */
	SessionAuthenticator createSessionAuthenticator(PrismsRequest request, User user)
		throws PrismsException;
}
