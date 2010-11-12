/*
 * PrismsAuthenticator.java Created Nov 1, 2010 by Andrew Butler, PSL
 */
package prisms.arch;

import javax.servlet.http.HttpServletRequest;

import prisms.arch.ds.User;

/** Allows PRISMS to authenticate HTTP requests via modules */
public interface PrismsAuthenticator
{
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
	void configure(org.dom4j.Element configEl, prisms.arch.ds.UserSource userSource,
		PrismsApplication [] apps);

	/**
	 * @param request The request attempting to access PRISMS
	 * @return Whether this authenticator can perform authentication for the given request
	 */
	boolean recognized(HttpServletRequest request);

	/**
	 * @param request The request attempting to access PRISMS
	 * @return The user represented by the request. This should NEVER be null.
	 * @throws PrismsException If an error occurs determining or retrieving the user for the request
	 */
	User getUser(HttpServletRequest request) throws PrismsException;

	/**
	 * Allows this authenticator to create a chunk of information to be associated with the session
	 * that the request belongs to.
	 * 
	 * @param request The request attempting to access PRISMS
	 * @param user The user returned from {@link #getUser(HttpServletRequest)} for the request
	 * @return Information to be passed to other methods in this authenticator for the same session
	 * @throws PrismsException If an error occurs creating the needed information
	 */
	Object createAuthenticationInfo(HttpServletRequest request, User user) throws PrismsException;

	/**
	 * Asserts that the given request has permission to access PRISMS at all. This method does not
	 * need to test whether the given request has access to the target application, client, etc. It
	 * only needs to be concerned whether the user should be able to access PRISMS through the given
	 * request at all.
	 * 
	 * @param request The HTTP request that the user is using to access PRISMS
	 * @param authInfo The authentication info returned from
	 *        {@link #createAuthenticationInfo(HttpServletRequest, User)}
	 * @param secure Whether this request is being made over a secure connection that has not been
	 *        jeopardized
	 * @return Either an {@link AuthenticationError} or the decrypted data parameter
	 * @throws PrismsException If an error occurs accessing the needed information
	 */
	Object getAuthenticatedData(HttpServletRequest request, Object authInfo, boolean secure)
		throws PrismsException;

	/**
	 * Allows this authenticator to request authentication in a custom manner
	 * 
	 * @param request The request made by the client before login
	 * @param authInfo The object returned from
	 *        {@link #createAuthenticationInfo(HttpServletRequest, User)} for the request's session
	 * @return An event to send to the client to ask the user to login. The response to this event
	 *         will be passed to {@link #getAuthenticatedData(HttpServletRequest, Object, boolean)}
	 * @throws PrismsException If an error occurs accessing the needed information
	 */
	org.json.simple.JSONObject requestLogin(HttpServletRequest request, Object authInfo)
		throws PrismsException;

	/**
	 * Encrypts data for passing to the client. This method may simply return the data unmodified if
	 * this is deemed secure enough.
	 * 
	 * @param request The request to encrypt the response to
	 * @param data The data to encrypt for sending to the client
	 * @param authInfo The object returned from
	 *        {@link #createAuthenticationInfo(HttpServletRequest, User)} for the request's session
	 * @return The encrypted data
	 * @throws PrismsException If an error occurs accessing the needed information
	 */
	String encrypt(HttpServletRequest request, String data, Object authInfo) throws PrismsException;

	/**
	 * @param request The request from the client
	 * @param authInfo The object returned from
	 *        {@link #createAuthenticationInfo(HttpServletRequest, User)} for the request's session
	 * @return Whether the user needs to change his/her password immediately
	 * @throws PrismsException If an error occurs getting the required information
	 * @throws PrismsException If an error occurs accessing the needed information
	 */
	boolean needsPasswordChange(HttpServletRequest request, Object authInfo) throws PrismsException;

	/**
	 * @param request The request from the client
	 * @param authInfo The object returned from
	 *        {@link #createAuthenticationInfo(HttpServletRequest, User)} for the request's session
	 * @return An event telling the client to change his/her password
	 * @throws PrismsException
	 */
	org.json.simple.JSONObject requestPasswordChange(HttpServletRequest request, Object authInfo)
		throws PrismsException;

	/**
	 * @param request The request from the client asking for a password change
	 * @param authInfo The object returned from
	 *        {@link #createAuthenticationInfo(HttpServletRequest, User)} for the request's session
	 * @param event The event asking for the password change
	 * @return The error that occurred changing the password, or null if the password change was
	 *         successful
	 * @throws PrismsException If an error occurs accessing the needed information
	 */
	AuthenticationError changePassword(HttpServletRequest request, Object authInfo,
		org.json.simple.JSONObject event) throws PrismsException;

	/**
	 * Destroys the given authentication info, releasing its resources
	 * 
	 * @param authInfo The authentication info to destroy
	 * @throws PrismsException If an error occurs accessing the needed information
	 */
	void destroy(Object authInfo) throws PrismsException;
}
