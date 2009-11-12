/**
 * Validator.java Created Aug 21, 2008 by Andrew Butler, PSL
 */
package prisms.arch;

import prisms.arch.ds.User;

/**
 * Validates a remote user, allowing or disallowing creation of a session. The validator also has an
 * opportunity to modify the user on validation, allowing, for instance, addition of groups and
 * permissions.
 */
public interface Validator
{
	/**
	 * Gets information to transmit to the client that will allow the client to submit a validation
	 * request to this object
	 * 
	 * @param user The user to get validation information for
	 * @param app The application to get validation information for
	 * @param client The client to get validation information for
	 * @param req The HTTP request that attempted to create and initialize a session
	 * @return JSON-serializable information that the client can use to submit a validation request
	 *         to this object. The client must recognize this information to be useful. If this
	 *         value is null, it means that this validator's
	 *         {@link #validate(User, PrismsApplication, ClientConfig, javax.servlet.http.HttpServletRequest, org.json.simple.JSONObject)}
	 *         method may be called with null data.
	 */
	org.json.simple.JSONObject getValidationInfo(User user, PrismsApplication app,
		ClientConfig client, javax.servlet.http.HttpServletRequest req);

	/**
	 * Called when a user requests to be validated by this object. If the validation succeeds this
	 * method may modify the user, perhaps by adding groups and permissions.
	 * 
	 * @param user The user requesting validation
	 * @param app The application that the user is attempting to access
	 * @param client The client that the user is attempting to access
	 * @param request The HTTP request that initiated the validation request
	 * @param data The validation data submitted by the client, or null if
	 *        {@link #getValidationInfo(User, PrismsApplication, ClientConfig, javax.servlet.http.HttpServletRequest)}
	 *        returned null.
	 * @return Whether the validation succeeds
	 * @throws java.io.IOException If request information cannot be retrieved
	 */
	boolean validate(User user, PrismsApplication app, ClientConfig client,
		javax.servlet.http.HttpServletRequest request, org.json.simple.JSONObject data)
		throws java.io.IOException;
}
