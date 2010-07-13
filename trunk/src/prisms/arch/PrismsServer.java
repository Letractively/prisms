/*
 * PrismsServer.java Created Apr 14, 2010 by Andrew Butler, PSL
 */
package prisms.arch;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.ds.Hashing;
import prisms.arch.ds.User;
import prisms.arch.ds.UserSource;
import prisms.arch.wms.PrismsWmsRequest;
import prisms.arch.wms.WmsPlugin;
import prisms.util.ArrayUtils;

/**
 * This server is the root of the PRISMS architecture. It takes HTTP requests that conform to the
 * PRISMS specification and delegates them to the appropriate applications, sessions, and plugins.
 */
public class PrismsServer extends javax.servlet.http.HttpServlet
{
	static final Logger log = Logger.getLogger(PrismsServer.class);

	/**
	 * An error code that will be sent back with events of type method="error"
	 */
	public static enum ErrorCode
	{
		/**
		 * Signifies that an error occurred inside the PRISMS servlet such that the request could
		 * not be fulfilled
		 */
		ServerError("Server Error"),
		/**
		 * Signifies that an error occurred in the application such that the request could not be
		 * fulfilled
		 */
		ApplicationError("Application Error"),
		/**
		 * Signifies that some of the data fields in the request are missing or invalid, so that the
		 * request could not be understood or fulfilled
		 */
		RequestInvalid("Request Invalid"),
		/**
		 * Signifies that the request could not be validated and so is not authorized to access the
		 * requested data
		 */
		ValidationFailed("Validation Failed");

		/**
		 * A small description of the error
		 */
		public final String description;

		ErrorCode(String descrip)
		{
			description = descrip;
		}

		/**
		 * Gets the error code given the descriptor
		 * 
		 * @param descrip The descriptor of an error code
		 * @return The error code corresponding to the given descriptor
		 */
		public static ErrorCode fromDescrip(String descrip)
		{
			for(ErrorCode code : values())
				if(code.description.equals(descrip))
					return code;
			return null;
		}
	}

	class PrismsRequest
	{
		final HttpServletRequest theRequest;

		final HttpServletResponse theResponse;

		String sessionID;

		String serverMethod;

		String userName;

		String appName;

		String clientName;

		boolean isWMS;

		String dataString;

		boolean encrypted;

		User user;

		PrismsApplication app;

		ClientConfig client;

		PrismsRequest(HttpServletRequest req, HttpServletResponse resp)
		{
			theRequest = req;
			theResponse = resp;
			sessionID = req.getParameter("sessionID");
			serverMethod = req.getParameter("method");
			// Check for external certificate authentication
			java.security.cert.X509Certificate[] cert = (java.security.cert.X509Certificate[]) req
				.getAttribute("javax.servlet.request.X509Certificate");
			if(cert != null && cert.length > 0)
				userName = cert[0].getSubjectX500Principal().getName();
			// The CN and AKOID server variables may store the name from the user's CAC card
			if(userName == null)
				userName = req.getHeader("CN");
			if(userName == null)
				userName = req.getHeader("AKOID");
			if(userName == null)
				userName = req.getParameter("user");
			if("null".equals(userName))
				userName = null;
			appName = req.getParameter("app");
			clientName = req.getParameter("client");
			if(clientName == null)
				clientName = req.getParameter("service");
			dataString = req.getParameter("data");
			encrypted = "true".equalsIgnoreCase(req.getParameter("encrypted"));
			isWMS = PrismsWmsRequest.isWMS(req);
		}

		/**
		 * Some WMS clients don't support multiple non-WMS parameters, so we have to account for
		 * that as best we can. The client and serverMethod names can be assumed to be "WMS" and the
		 * application name can be stored within the unencrypted data parameter.
		 * 
		 * @return An error event if this method did not complete successfully
		 */
		JSONObject adjustWMS()
		{
			if(!isWMS)
				return null;
			if(serverMethod == null)
				serverMethod = "WMS";
			if(dataString == null)
				dataString = "{\"serverPadding\":\"padding\"}";
			else if(dataString.length() >= 2 && dataString.charAt(0) != '{')
				encrypted = true;
			else if(appName == null || userName == null)
			{
				JSONObject data;
				try
				{
					data = getSerializer().deserialize(dataString);
					if(appName == null)
						appName = (String) data.get("app");
					if(clientName == null)
						clientName = (String) data.get("service");
					if(userName == null)
						userName = (String) data.get("user");
					if(sessionID == null)
						sessionID = (String) data.get("sessionID");
				} catch(Exception e)
				{
					return error(ErrorCode.RequestInvalid,
						"Couldn't retrieve necessary parameters from data");
				}
			}
			if(clientName == null)
				clientName = "WMS";
			return null;
		}

		void send(JSONArray events) throws IOException
		{
			theResponse.setContentType("text/prisms-json");
			java.io.PrintWriter out = theResponse.getWriter();
			out.print(getSerializer().serialize(events));
			out.close();
		}
	}

	class PrismsResponse
	{
		ErrorCode code;

		String error;

		JSONArray toReturn;

		JSONObject toProcess;

		boolean shouldEncrypt;

		PrismsResponse(JSONObject process)
		{
			toProcess = process;
		}

		PrismsResponse(JSONArray events, boolean encrypt)
		{
			toReturn = events;
			shouldEncrypt = encrypt;
		}

		PrismsResponse(ErrorCode c, String e, JSONArray events, boolean encrypt)
		{
			code = c;
			error = e;
			toReturn = events;
			shouldEncrypt = encrypt;
		}

	}

	private class SecuritySession
	{
		private final HttpSession theHttpSession;

		private final User theUser;

		private Hashing theHashing;

		private long [] theKey;

		private Encryption theEncryption;

		private String [] theAccessApps;

		private String [] theValidations;

		private User theAnonymousUser;

		private int theLoginsFailed;

		private boolean authChanged;

		private long userLastChecked;

		private long theCreationTime;

		private long theLastUsed;

		private boolean hasLoggedIn;

		SecuritySession(HttpSession session, User user) throws PrismsException
		{
			theHttpSession = session;
			theUser = user;
			theHashing = PrismsServer.this.getUserSource().getHashing();
			theAccessApps = new String [0];
			theValidations = new String [0];
			checkAuthenticationData();
			theCreationTime = theLastUsed;
		}

		User getUser()
		{
			return theUser;
		}

		/**
		 * Checks this session holder's user against the data source periodically to see whether the
		 * password has changed or the user has been locked.
		 * 
		 * @throws PrismsException
		 */
		private void checkAuthenticationData() throws PrismsException
		{
			long time = System.currentTimeMillis();
			theLastUsed = time;
			if(isTrusted() || time - userLastChecked < theUserCheckPeriod)
				return;
			userLastChecked = time;
			theAnonymousUser = getUserSource().getUser(null);
			theAccessApps = new String [0];
			theValidations = new String [0];
			long [] newKey = getUserSource().getKey(theUser, theHashing);
			if(theKey == null && newKey != null)
			{
				theKey = newKey;
				if(theEncryption != null)
					theEncryption.dispose();
				theEncryption = createEncryption();
				theEncryption.init(theKey, theEncryptionProperties);
			}
			else if(newKey == null)
			{
				theKey = null;
				theEncryption = null;
			}
			else if(!ArrayUtils.equals(theKey, newKey))
			{
				theKey = newKey;
				// byte [] init = new byte [10];
				// for(int i = 0; i < init.length; i++)
				// init[i] = (byte) ((Math.random() - 0.5) * 2 * Byte.MAX_VALUE);
				if(theEncryption != null)
					theEncryption.dispose();
				if(theKey != null)
				{
					theEncryption = createEncryption();
					theEncryption.init(theKey, null);
				}
				authChanged = true;
			}
		}

		/**
		 * Screens a request to this session, making sure the request
		 * 
		 * @param req The request to be validated
		 * @return The response to be processed after validation.
		 */
		public PrismsResponse validate(PrismsRequest req)
		{
			try
			{
				checkAuthenticationData();
			} catch(PrismsException e)
			{
				log.error("Could not check authentication data", e);
				return error(req, ErrorCode.ServerError, "Could not check authentication data: "
					+ e.getMessage(), false);
			}
			if(theUser.isLocked())
				return error(req, ErrorCode.ValidationFailed, "User \"" + theUser.getName()
					+ "\" is locked. Contact your admin.", false);
			if(!isTrusted() && !ArrayUtils.contains(theAccessApps, req.appName))
			{
				try
				{
					if(!getUserSource().canAccess(theUser, req.app))
						return error(req, ErrorCode.ValidationFailed, "User \"" + theUser.getName()
							+ "\" does not have permission to access application \""
							+ req.app.getName() + "\"", false);
				} catch(PrismsException e)
				{
					log.error("Could not determine user " + theUser + "'s access to application "
						+ req.app.getName(), e);
					return error(req, ErrorCode.ServerError, "Could not determine user "
						+ theUser.getName() + "'s access to application " + req.app.getName(),
						false);
				}
				theAccessApps = ArrayUtils.add(theAccessApps, req.appName);
			}
			String dataStr = req.dataString;
			if(req.encrypted)
			{
				// For some reason, "+" gets mis-translated at a space
				dataStr = dataStr.replaceAll(" ", "+");
				String encryptedText = dataStr;
				try
				{
					dataStr = decrypt(req, dataStr);
					if(dataStr == null || dataStr.length() < 2 || dataStr.charAt(0) != '{'
						|| dataStr.charAt(dataStr.length() - 1) != '}')
					{
						/* Even though no exception was thrown the decryption has failed.
						 * The result is not a JSON object. */
						Exception toThrow = new Exception("Decrypted result: " + dataStr
							+ " is not a JSON object");
						toThrow.setStackTrace(new StackTraceElement [0]);
						throw toThrow; // To be caught in same method
					}
				} catch(Exception e)
				{
					log.error("Decryption of " + encryptedText + " failed with encryption "
						+ theEncryption, e);
					try
					{
						loginFailed();
					} catch(PrismsException e2)
					{
						log.error("Error processing request", e2);
						return error(req, ErrorCode.ServerError, "Error processing request: "
							+ e2.getMessage(), false);
					}
					if(req.isWMS)
						return error(req, ErrorCode.ValidationFailed,
							"WMS does not support encryption.", false);
					if(theUser.isLocked())
						return sendLogin(req, "Too many incorrect password attempts.\nUser "
							+ theUser.getName() + " is locked. Contact your admin", "init"
							.equals(req.serverMethod), true);
					else if(authChanged)
						return sendLogin(req, theUser + "'s password has been changed."
							+ " Use the new password or contact your admin.", "init"
							.equals(req.serverMethod), true);
					else
						return sendLogin(req, "Incorrect password for user " + theUser.getName(),
							"init".equals(req.serverMethod), true);
				}

				// Decryption succeeded
				if(dataStr.length() < 20)
					return error(req, ErrorCode.ValidationFailed, "Data string null or too short"
						+ "--at least 20 characters of data must be included to verify encryption."
						+ "  Use \"-XXSERVERPADDING...\" for padding", false);
				// Remove "-XXSERVERPADDING" comment if present
				int commentIdx = dataStr.indexOf("-XXSERVERPADDING");
				if(commentIdx >= 0)
					dataStr = dataStr.substring(0, commentIdx);
			}
			else if(theUser.equals(theAnonymousUser))
			{
				if(!req.client.allowsAnonymous())
					return singleMessage(req, false, "login", "error",
						"Anonymous access forbidden for client " + req.client.getName()
							+ " of application " + req.app.getName() + ".  Please log in.");
			}
			else if(!isTrusted()
				&& (!hasLoggedIn || !isLoginOnce() || req.client.isService() || !theHttpSession
					.isSecure()))
				return sendLogin(req, null, "init".equals(req.serverMethod), false);

			RemoteEventSerializer serializer = req.client.getSerializer();
			if(serializer == null)
				serializer = PrismsServer.this.getSerializer();

			// Now we know that the encryption seems to have succeeded. Now try deserialization.
			JSONObject event;
			if(dataStr != null)
			{
				try
				{
					event = serializer.deserialize(dataStr);
				} catch(Exception e)
				{
					log.error("Deserialization failed" + e.getMessage());
					return error(req, ErrorCode.RequestInvalid, "Deserialization of " + dataStr
						+ " failed", true);
				}
			}
			else
				event = null;
			// The encryption succeeded--the session knows the password
			loginSucceeded();

			if("validate".equals(req.serverMethod))
			{
				Validator validator = req.client.getValidator();
				JSONObject evt = new JSONObject();
				boolean valid;
				try
				{
					valid = validator == null
						|| validator.validate(theUser, req.app, req.client, req.theRequest, event);
				} catch(RuntimeException e)
				{
					return error(req, ErrorCode.ValidationFailed, e.getMessage(), true);
				} catch(IOException e)
				{
					return error(req, ErrorCode.ServerError, "Could not read validation data: "
						+ e.getMessage(), true);
				}
				if(!valid)
				{
					JSONObject valInfo = validator.getValidationInfo(theUser, req.app, req.client,
						req.theRequest);
					evt.put("method", "validate");
					evt.put("validationFailed", new Boolean(true));
					evt.put("params", valInfo);
					return singleMessage(req, evt, true);
				}
				else
				{
					// Validation successful--we're done. Tell the client to re-initialize.
					theValidations = ArrayUtils.add(theValidations, req.appName + "/"
						+ req.clientName);
					return singleMessage(req, true, "callInit");
				}
			}
			if(!ArrayUtils.contains(theValidations, req.appName + "/" + req.clientName)
				&& req.client.getValidator() != null)
			{
				Validator validator = req.client.getValidator();
				JSONObject valInfo = validator.getValidationInfo(theUser, req.app, req.client,
					req.theRequest);
				if(valInfo != null && req.isWMS)
					return error(req, ErrorCode.ValidationFailed, "Validation required for user \""
						+ theUser.getName() + "\" access to application \"" + req.app.getName()
						+ "\"", true);
				else if(valInfo == null)
				{
					try
					{
						if(!validator.validate(theUser, req.app, req.client, req.theRequest, null))
						{
							return error(req, ErrorCode.ValidationFailed, "Validator " + validator
								+ " prevents this session from being valid", true);
						}
						theValidations = ArrayUtils.add(theValidations, req.appName + "/"
							+ req.clientName);
					} catch(RuntimeException e)
					{
						return error(req, ErrorCode.ValidationFailed, e.getMessage(), true);
					} catch(IOException e)
					{
						return error(req, ErrorCode.ServerError, "Could not read validation data: "
							+ e.getMessage(), true);
					}
				}
				else
					return singleMessage(req, true, "validate", "params", valInfo);
			}
			// The session is validated or no validation is required

			if("changePassword".equals(req.serverMethod))
			{
				long [] pwdData = null;
				try
				{
					JSONArray jsonPwdData = (JSONArray) event.get("passwordData");
					pwdData = new long [jsonPwdData.size()];
					for(int i = 0; i < pwdData.length; i++)
						pwdData[i] = ((Number) jsonPwdData.get(i)).longValue();
					getUserSource().setPassword(theUser, pwdData, theUser.isAdmin());
					// Password change succeeded--we're done. Tell the client to re-initialize.
					return singleMessage(req, true, "callInit");
				} catch(Exception e)
				{
					log.error("Password change failed", e);
					if(pwdData != null)
						log.error("Could not set password data for user " + theUser.getName()
							+ " to " + ArrayUtils.toString(pwdData), e);
					else
						log.error("Could not set password data for user " + theUser.getName()
							+ ": no passwordData sent", e);
					return sendChangePassword(req, "Could not change password: " + e.getMessage(),
						true);
				}
			}

			long pwdExp;
			try
			{
				pwdExp = getUserSource().getPasswordExpiration(theUser);
			} catch(PrismsException e)
			{
				log.error("Could not get password expiration", e);
				return error(req, ErrorCode.ServerError, "Could not get password expiration: "
					+ e.getMessage(), true);
			}
			if(!isTrusted() && pwdExp > 0 && pwdExp < System.currentTimeMillis())
			{
				if(req.isWMS)
					return error(req, ErrorCode.ValidationFailed,
						"Password change required for user \"" + theUser.getName() + "\"", false);
				else
					return sendChangePassword(req, "Password change required for user \""
						+ theUser.getName() + "\"", false);
			}

			if("tryChangePassword".equals(req.serverMethod))
			{
				return sendChangePassword(req, null, false);
			}
			if("getVersion".equals(req.serverMethod))
			{
				JSONArray version = null;
				if(req.app.getVersion() != null)
				{
					version = new JSONArray();
					for(int v = 0; v < req.app.getVersion().length; v++)
						version.add(new Integer(req.app.getVersion()[v]));
				}
				return singleMessage(req, true, "setVersion", "version", version, "modified",
					new Long(req.app.getModifiedDate()));
			}
			// The password is valid

			// The session is valid--it can do what it wants now

			return new PrismsResponse(event);
		}

		void writeReturn(PrismsRequest request, PrismsResponse response) throws IOException
		{
			if(request.isWMS)
			{
				if(response.code != null)
					PrismsWmsRequest.respondError(request.theResponse, response.error);
				return;
			}
			RemoteEventSerializer serializer = request.client.getSerializer();
			if(serializer == null)
				serializer = getSerializer();
			String str;
			try
			{
				str = serializer.serialize(response.toReturn);
			} catch(java.io.NotSerializableException e)
			{
				JSONArray send = new JSONArray();
				send.add(prisms.util.PrismsUtils.rEventProps("method", "error", "message",
					"Could not serialize return events"));
				try
				{
					str = serializer.serialize(send);
				} catch(java.io.NotSerializableException e2)
				{
					throw new IllegalStateException("Could not serialize return events", e);
				}
			}
			if(response.shouldEncrypt && request.encrypted)
				str = encrypt(request, str);
			request.theResponse.setContentType("text/prisms-json");
			String acceptEncoding = request.theRequest.getHeader("accept-encoding");
			if(acceptEncoding != null && acceptEncoding.toLowerCase().contains("gzip"))
			{
				request.theResponse.setHeader("Content-Encoding", "gzip");
				java.io.OutputStream os = request.theResponse.getOutputStream();
				java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(os);
				java.io.PrintWriter out = new java.io.PrintWriter(gzos);
				out.write(str);
				out.close();
			}
			else
			{
				java.io.PrintWriter out = request.theResponse.getWriter();
				out.write(str);
				out.close();
			}
		}

		private String encrypt(PrismsRequest request, String text) throws IOException
		{
			/* TODO: For whatever reason, information encrypted by the server is not decrypted
			 * correctly by the javascript dojo blowfish implementation on the HTML client. Pending
			 * more extensive investigation, we'll just send information unencrypted. */
			if(request.client.isService())
			{
				java.nio.charset.Charset charSet = null;
				String acceptChs = request.theRequest.getHeader("Accept-Charset");
				if(acceptChs == null)
					charSet = java.nio.charset.Charset.defaultCharset();
				else
				{
					String [] chs = acceptChs.split("[;,]");
					for(int c = 0; c < chs.length && charSet == null; c++)
						charSet = java.nio.charset.Charset.forName(chs[c].trim());
					if(charSet == null)
						charSet = java.nio.charset.Charset.defaultCharset();
				}
				return theEncryption.encrypt(text, charSet);
			}
			return text;
		}

		private String decrypt(PrismsRequest request, String encrypted) throws IOException
		{
			if(theEncryption == null)
				return encrypted;
			java.nio.charset.Charset charSet;
			if(request.theRequest.getCharacterEncoding() == null)
				charSet = java.nio.charset.Charset.defaultCharset();
			else
				charSet = java.nio.charset.Charset.forName(request.theRequest
					.getCharacterEncoding());
			return theEncryption.decrypt(encrypted, charSet);
		}

		private void loginFailed() throws PrismsException
		{
			if(theUser.isLocked())
				return;
			if(authChanged)
			{ // If password changed, give a bonus attempt
				authChanged = false;
				return;
			}
			theLoginsFailed++;
			if(theLoginsFailed >= theLoginFailTolerance && !theUser.getName().equals("admin"))
			{
				theUser.setLocked(true);
				getUserSource().lockUser(theUser);
				theLoginsFailed = 0;
			}
		}

		private PrismsResponse sendLogin(PrismsRequest req, String error, boolean postInit,
			boolean isError)
		{
			if(theEncryption == null)
				return error(req, ErrorCode.ValidationFailed, "User " + req.userName
					+ " does not have a password set--consult your admin", false);
			JSONObject evt = new JSONObject();
			evt.put("method", "startEncryption");
			evt.put("encryption", theEncryption.getParams());
			JSONObject hashing = theHashing.toJson();
			hashing.put("user", theUser.getName());
			evt.put("hashing", hashing);
			if(postInit)
				evt.put("postAction", "callInit");
			if(isError)
				evt.put("code", ErrorCode.ValidationFailed.description);
			evt.put("error", error);
			return singleMessage(req, evt, false);
		}

		private PrismsResponse sendChangePassword(PrismsRequest req, String message, boolean error)
		{
			prisms.arch.ds.PasswordConstraints pc;
			try
			{
				pc = getUserSource().getPasswordConstraints();
			} catch(PrismsException e)
			{
				return error(req, ErrorCode.ServerError, "Could not get password change data", true);
			}
			StringBuilder msg = new StringBuilder();
			if(message != null)
				msg.append(message);
			if(pc.getNumConstraints() == 1)
			{
				if(msg.length() > 0)
					msg.append('\n');
				if(pc.getMinCharacterLength() > 0)
				{
					msg.append("The new password must be at least ");
					msg.append(pc.getMinCharacterLength());
					msg.append(" characters long");
				}
				else if(pc.getMinUpperCase() > 0)
				{
					msg.append("The new password must have at least ");
					msg.append(pc.getMinUpperCase());
					msg.append(" upper-case letter");
					if(pc.getMinUpperCase() > 1)
						msg.append('s');
				}
				else if(pc.getMinLowerCase() > 0)
				{
					msg.append("The new password must have at least ");
					msg.append(pc.getMinLowerCase());
					msg.append(" lower-case letter");
					if(pc.getMinLowerCase() > 1)
						msg.append('s');
				}
				else if(pc.getMinDigits() > 0)
				{
					msg.append("The new password must have at least ");
					msg.append(pc.getMinDigits());
					msg.append(" digit");
					if(pc.getMinDigits() > 1)
						msg.append('s');
					msg.append(" (0-9)");
				}
				else if(pc.getMinSpecialChars() > 0)
				{
					msg.append("The new password must have at least ");
					msg.append(pc.getMinSpecialChars());
					msg.append(" special character");
					if(pc.getMinSpecialChars() > 1)
						msg.append('s');
					msg.append(" (&, *, _, @, etc.)");
				}
				else if(pc.getNumConstraints() > 0)
				{
					msg.append("The new password must not be the same as ");
					if(pc.getNumConstraints() == 1)
						msg.append("your current password");
					else if(pc.getNumConstraints() == 2)
						msg.append("your current or previous passwords");
					else
					{
						msg.append("any of your previous ");
						msg.append(pc.getNumConstraints());
						msg.append(" passwords");
					}
				}
				else
					throw new IllegalStateException("Unaccounted-for password constraint");
			}
			else if(pc.getNumConstraints() > 1)
			{
				if(msg.length() > 0)
					msg.append('\n');
				msg.append("The new password must:");
				int count = 1;
				if(pc.getMinCharacterLength() > 0)
				{
					msg.append("\n\t");
					msg.append(count);
					msg.append(") be ");
					msg.append(pc.getMinCharacterLength());
					msg.append(" characters long");
					count++;
				}
				if(pc.getMinUpperCase() > 0)
				{
					msg.append("\n\t");
					msg.append(count);
					msg.append(") have at least ");
					msg.append(pc.getMinUpperCase());
					msg.append(" upper-case letter");
					if(pc.getMinUpperCase() > 1)
						msg.append('s');
				}
				if(pc.getMinLowerCase() > 0)
				{
					msg.append("\n\t");
					msg.append(count);
					msg.append(") have at least ");
					msg.append(pc.getMinLowerCase());
					msg.append(" lower-case letter");
					if(pc.getMinLowerCase() > 1)
						msg.append('s');
				}
				if(pc.getMinDigits() > 0)
				{
					msg.append("\n\t");
					msg.append(count);
					msg.append(") have at least ");
					msg.append(pc.getMinDigits());
					msg.append(" digit");
					if(pc.getMinDigits() > 1)
						msg.append('s');
					msg.append(" (0-9)");
				}
				if(pc.getMinSpecialChars() > 0)
				{
					msg.append("\n\t");
					msg.append(count);
					msg.append(") have at least ");
					msg.append(pc.getMinSpecialChars());
					msg.append(" special character");
					if(pc.getMinSpecialChars() > 1)
						msg.append('s');
					msg.append(" (&, *, _, @, etc.)");
				}
				if(pc.getNumConstraints() > 0)
				{
					msg.append("\n\t");
					msg.append(count);
					msg.append(") not be the same as ");
					if(pc.getNumConstraints() == 1)
						msg.append("your current password");
					else if(pc.getNumConstraints() == 2)
						msg.append("your current or previous passwords");
					else
					{
						msg.append("any of your previous ");
						msg.append(pc.getNumConstraints());
						msg.append(" passwords");
					}
				}
			}
			return singleMessage(req, true, "changePassword", (error ? "error" : "message"), msg
				.toString(), "constraints", prisms.arch.service.PrismsSerializer
				.serializeConstraints(pc), "hashing", theHashing.toJson());
		}

		private void loginSucceeded()
		{
			hasLoggedIn = true;
			theLoginsFailed = 0;
			authChanged = false;
		}

		private PrismsResponse error(PrismsRequest req, ErrorCode code, String message,
			boolean encrypted)
		{
			JSONObject evt = new JSONObject();
			evt.put("code", code);
			evt.put("message", message);
			PrismsResponse ret = singleMessage(req, encrypted, "error", "code", code.description,
				"message", message);
			ret.code = code;
			ret.error = message;
			return ret;
		}

		private PrismsResponse singleMessage(PrismsRequest req, boolean encrypted, String method,
			Object... params)
		{
			return singleMessage(req, toEvent(method, params), encrypted);
		}

		private PrismsResponse singleMessage(PrismsRequest req, JSONObject evt, boolean encrypted)
		{
			JSONArray arr = new JSONArray();
			arr.add(evt);
			return new PrismsResponse(arr, encrypted);
		}

		boolean isExpired()
		{
			long timeout = getSecurityTimeout();
			if(timeout > 0 && System.currentTimeMillis() - theLastUsed > timeout)
				return true;
			long refresh = getSecurityRefresh();
			if(refresh > 0 && System.currentTimeMillis() - theCreationTime > refresh)
				return true;
			return false;
		}

		void destroy()
		{
			if(theEncryption != null)
				theEncryption.dispose();
		}
	}

	private class SessionHolder
	{
		private final HttpSession theHttpSession;

		private final User theUser;

		private final PrismsApplication theApp;

		private final ClientConfig theClient;

		private PrismsSession theSession;

		private long theCreationTime;

		/**
		 * Creates a session holder
		 * 
		 * @param httpSession The HTTP session that this session holder is in
		 * @param user The user that this session is for
		 * @param client The client configuration that this session is for
		 */
		public SessionHolder(HttpSession httpSession, User user, ClientConfig client)
		{
			theHttpSession = httpSession;
			theUser = user;
			theApp = client.getApp();
			theClient = client;
			theCreationTime = System.currentTimeMillis();
		}

		public User getUser()
		{
			return theUser;
		}

		public ClientConfig getClient()
		{
			return theClient;
		}

		/**
		 * Processes an event for a validated session
		 * 
		 * @param req The request that requires a response
		 * @param event The event to process
		 * @return The response if the result was in an event form
		 * @throws IOException If an error occurs writing to the response
		 */
		public PrismsResponse processEvent(PrismsRequest req, JSONObject event) throws IOException
		{
			if(theSession != null)
			{
				if(!theApp.isOpen(theSession))
					return singleMessage(req, true, "restart", "message", theApp.getReloadMessage());
				String appLock = theApp.isLocked(theSession);
				if(appLock != null)
					return singleMessage(req, true, "appLocked", "message", appLock);
			}
			// Do prisms methods
			if("init".equals(req.serverMethod))
				return init(req);
			if(theSession == null)
			{ // Client needs to be re-initialized
				if(req.isWMS)
				{ // For WMS we swallow this for simplicity
					PrismsResponse resp = init(req);
					if(resp.code != null)
						return resp;
				}
				else
					return singleMessage(req, true, "restart");
			}
			if("logout".equals(req.serverMethod))
			{
				theHttpSession.removeSession(this);
				destroy();
				return singleMessage(req, false, "restart", "message",
					"You have been successfully logged out");
			}

			// Do application methods (processEvent, generateImage, doDownload, doUpload)
			if("processEvent".equals(req.serverMethod))
				return process(event);
			if(req.isWMS)
			{
				PrismsWmsRequest wmsReq = PrismsWmsRequest.parseWMS(req.theRequest);
				return processWMS(req, event, wmsReq);
			}
			if("generateImage".equals(req.serverMethod))
			{
				String reqURI = req.theRequest.getRequestURI();
				int dotIdx = reqURI.indexOf(".");
				String format = null;
				if(dotIdx >= 0)
					format = reqURI.substring(dotIdx + 1);
				if(format != null)
					format = format.toLowerCase();
				if(!"png".equals(format) && !"jpg".equals(format) && !"jpeg".equals(format)
					&& !"gif".equals(format))
				{
					log.error("Unrecognized image format: " + format + ".  Cannot generate image");
					return new PrismsResponse(null);
				}
				if(format.equals("jpg"))
					req.theResponse.setContentType("image/jpeg");
				else
					req.theResponse.setContentType("image/" + format);
				java.io.OutputStream out = req.theResponse.getOutputStream();
				generateImage(event, format, out);
				return new PrismsResponse(null);
			}
			if("getDownload".equals(req.serverMethod))
			{
				getDownload(req.theResponse, event);
				return new PrismsResponse(null);
			}
			if("doUpload".equals(req.serverMethod))
			{
				doUpload(req.theRequest, event);
				// We redirect to avoid the browser's resend warning if the user refreshes
				req.theResponse.setStatus(301);
				req.theResponse.sendRedirect("nothing.html");
				return new PrismsResponse(null);
			}
			return error(req, ErrorCode.RequestInvalid, "Unable to process request: serverMethod "
				+ req.serverMethod + " not defined");
		}

		private PrismsResponse init(PrismsRequest req)
		{
			JSONArray ret = new JSONArray();
			try
			{
				if(theSession == null)
					theSession = getUserSource().createSession(theClient, theUser);
			} catch(PrismsException e)
			{
				String error = "Could not create " + (theClient.isService() ? "service " : "UI ")
					+ theApp.getName() + " session for user " + theUser.getName() + ", client "
					+ theClient.getName();
				log.error(error, e);
				return error(req, ErrorCode.ServerError, error + ": " + e.getMessage());
			}
			theSession.clearOutgoingQueue();
			try
			{
				theSession.init();
			} catch(Throwable e)
			{
				String error = "Could not initialize "
					+ (theClient.isService() ? "service " : "UI ") + theApp.getName()
					+ " session for user " + theUser.getName() + ", client " + theClient.getName();
				log.error(error, e);
				if(e.getCause() != null)
					error = e.getCause().getMessage();
				else
					error += ": " + e.getMessage();
				return error(req, ErrorCode.ApplicationError, error);
			}
			JSONObject evt = new JSONObject();
			evt.put("method", "init");
			ret.add(evt);
			ret.addAll(theSession.getEvents());
			return new PrismsResponse(ret, true);
		}

		private PrismsResponse process(JSONObject event)
		{
			int busyness = theSession.getBusyCount();
			String invocationID = null;
			try
			{
				if(theSession.getClient().isService())
				{
					invocationID = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
					theSession.process(event, invocationID);
				}
				else
					theSession.process(event);
			} catch(Throwable e)
			{
				log.error("Session error processing events", e);
			}
			try
			{
				Thread.sleep(100);
			} catch(InterruptedException e)
			{}
			/*
			 * This code checks every quarter second to see if the event has been processed. If
			 * the processing isn't finished after 1/2 second, this method returns, leaving the
			 * final results of the event on the queue to be retrieved at the next client poll
			 * or user action. This allows progress bars to be shown to the user quickly while a
			 * long operation progresses.
			 */
			int waitCount = 0;
			while(theSession.getBusyCount() > busyness && waitCount < 2)
			{
				try
				{
					Thread.sleep(250);
				} catch(InterruptedException e)
				{}
				waitCount++;
			}
			JSONArray ret;
			if(invocationID != null)
				ret = theSession.getEvents(invocationID);
			else
				ret = theSession.getEvents();
			if(invocationID == null && theSession.getBusyCount() > busyness)
			{
				JSONObject getEventsEvent = new JSONObject();
				getEventsEvent.put("method", "getEvents");
				ret.add(getEventsEvent);
			}
			return new PrismsResponse(ret, true);
		}

		private PrismsResponse processWMS(PrismsRequest req, JSONObject event, PrismsWmsRequest wms)
			throws IOException
		{
			String pluginName = (String) event.get("plugin");
			WmsPlugin plugin = (WmsPlugin) theSession.getPlugin(pluginName);
			HttpServletResponse response = req.theResponse;
			if(plugin == null)
				return error(req, ErrorCode.RequestInvalid, "No plugin " + pluginName + " loaded");
			try
			{
				theSession.runTasks();
				java.io.PrintWriter writer;
				switch(wms.getRequest())
				{
				case GetCapabilities:
					response.setContentType("text/xml");
					writer = new java.io.PrintWriter(response.getOutputStream());
					writer.write(plugin.getCapabilities(wms, event));
					writer.close();
					break;
				case GetList:
					response.setContentType("text/xml");
					writer = new java.io.PrintWriter(response.getOutputStream());
					writer.write(plugin.getList(wms, event));
					writer.close();
					break;
				case Map:
					if(wms.getFormat().equals("jpg"))
						response.setContentType("image/jpeg");
					else
						response.setContentType("image/" + wms.getFormat());
					java.io.OutputStream out = response.getOutputStream();
					plugin.drawMapOverlay(wms, event, out);
					out.close();
					break;
				case GetFeatureInfo:
					response.setContentType("text/html");
					writer = new java.io.PrintWriter(response.getOutputStream());
					String featureInfo = plugin.getFeatureInfo(wms, event);
					writer.write(featureInfo);
					writer.close();
					break;
				case Other:
					response.setContentType("text/html");
					writer = new java.io.PrintWriter(response.getOutputStream());
					writer.write(plugin.respond(wms, event));
					writer.close();
					break;
				default:
					throw new IllegalStateException("WMS request type " + wms.getRequest()
						+ " not recognized");
				}
			} catch(Throwable e)
			{
				if(e instanceof java.io.IOException)
					throw (java.io.IOException) e;
				log.error("Could not fulfill " + wms.getRequest() + " typed WMS request", e);
				return error(req, ErrorCode.ApplicationError, e.getClass().getName() + ": "
					+ e.getMessage());
			}
			return new PrismsResponse(null);
		}

		private void generateImage(JSONObject event, String format, java.io.OutputStream out)
			throws IOException
		{
			String pluginName = (String) event.get("plugin");
			String method = (String) event.get("method");
			int xOffset = 0, yOffset = 0;
			if(event.containsKey("xOffset"))
				xOffset = ((Number) event.get("xOffset")).intValue();
			if(event.containsKey("yOffset"))
				yOffset = ((Number) event.get("yOffset")).intValue();
			int refWidth = ((Number) event.get("refWidth")).intValue();
			int refHeight = ((Number) event.get("refHeight")).intValue();
			int imWidth = ((Number) event.get("width")).intValue();
			int imHeight = ((Number) event.get("height")).intValue();
			if(theSession.getPlugin(pluginName) == null)
			{
				log.error("No plugin " + pluginName + " loaded");
				return;
			}
			theSession.runTasks();
			((ImagePlugin) theSession.getPlugin(pluginName)).writeImage(method, format, xOffset,
				yOffset, refWidth, refHeight, imWidth, imHeight, out);
		}

		private void getDownload(HttpServletResponse response, JSONObject event) throws IOException
		{
			String pluginName = (String) event.get("plugin");
			if(theSession.getPlugin(pluginName) == null)
			{
				log.error("No plugin " + pluginName + " loaded");
				return;
			}
			DownloadPlugin plugin = (DownloadPlugin) theSession.getPlugin(pluginName);
			// Run session tasks
			theSession._process(null);
			String contentType = plugin.getContentType(event);
			if(contentType == null)
				contentType = "application/octet-stream";
			response.setContentType(contentType);
			response.setHeader("Content-Disposition", "attachment; filename=\""
				+ plugin.getFileName(event) + "\"");
			plugin.doDownload(event, response.getOutputStream());
		}

		private void doUpload(HttpServletRequest request, final JSONObject event)
			throws IOException
		{
			String pluginName = (String) event.get("plugin");
			if(theSession.getPlugin(pluginName) == null)
			{
				log.debug("No plugin " + pluginName + " loaded");
				return;
			}
			final UploadPlugin plugin = (UploadPlugin) theSession.getPlugin(pluginName);
			if(!org.apache.commons.fileupload.servlet.ServletFileUpload.isMultipartContent(request))
				throw new IllegalArgumentException("getUpload called without multipart content");
			// Create a factory for disk-based file items
			org.apache.commons.fileupload.FileItemFactory factory;
			factory = new org.apache.commons.fileupload.disk.DiskFileItemFactory();

			// Create a new file upload handler
			org.apache.commons.fileupload.servlet.ServletFileUpload upload;
			upload = new org.apache.commons.fileupload.servlet.ServletFileUpload(factory);

			// Parse the request
			java.util.List<org.apache.commons.fileupload.FileItem> items;
			try
			{
				items = upload.parseRequest(request);
			} catch(org.apache.commons.fileupload.FileUploadException e)
			{
				throw new IllegalStateException("File upload failed", e);
			}
			String eventFileName = (String) event.get("uploadFile");
			if(eventFileName == null)
				throw new IllegalArgumentException("No uploadFile specified");
			eventFileName = org.apache.commons.io.FilenameUtils.getName(eventFileName);
			boolean didUpload = false;
			for(int i = 0; i < items.size(); i++)
			{
				final org.apache.commons.fileupload.FileItem item = items.get(i);
				if(item.isFormField())
				{
					log.info("Disregarded form field " + item.getFieldName() + ", value="
						+ item.getString());
					continue;
				}
				final String fileName = org.apache.commons.io.FilenameUtils.getName(item.getName());
				if(!fileName.equals(eventFileName))
				{
					log.error("Uploaded file " + fileName
						+ " does not match file specified in event: " + eventFileName);
					throw new IllegalArgumentException("Invalid upload file");
				}
				theSession.runEventually(new Runnable()
				{
					public void run()
					{
						try
						{
							plugin.doUpload(event, fileName, item.getContentType(), item
								.getInputStream(), item.getSize());
						} catch(java.io.IOException e)
						{
							log.error("Upload " + fileName + " failed", e);
						} finally
						{
							item.delete();
						}
					}
				});
				didUpload = true;
			}
			if(!didUpload)
				throw new IllegalStateException("getUpload called with no non-form field");
		}

		private PrismsResponse error(PrismsRequest req, ErrorCode code, String message)
		{
			JSONObject evt = new JSONObject();
			evt.put("code", code);
			evt.put("message", message);
			PrismsResponse ret = singleMessage(req, true, "error", "code", code.description,
				"message", message);
			ret.code = code;
			ret.error = message;
			return ret;
		}

		private PrismsResponse singleMessage(PrismsRequest req, boolean encrypted, String method,
			Object... params)
		{
			return singleMessage(req, toEvent(method, params), encrypted);
		}

		private PrismsResponse singleMessage(PrismsRequest req, JSONObject evt, boolean encrypted)
		{
			JSONArray arr = new JSONArray();
			arr.add(evt);
			return new PrismsResponse(arr, encrypted);
		}

		boolean isExpired()
		{
			long timeout = theClient.getSessionTimeout();
			if(theSession != null)
			{
				if(!theApp.isOpen(theSession))
					return true;
				if(theSession.isExpired())
					return true;
			}
			else if(timeout > 0 && System.currentTimeMillis() - theCreationTime > timeout)
				return true;
			return false;
		}

		void destroy()
		{
			if(theSession != null)
				theSession.destroy();
		}
	}

	private class HttpSession
	{
		private final String theID;

		private boolean isSecure;

		private SecuritySession [] theSecurities;

		private SessionHolder [] theSessionHolders;

		private long theLastUsed;

		HttpSession(String id, boolean secure)
		{
			theID = id;
			isSecure = secure;
			theSecurities = new SecuritySession [0];
			theSessionHolders = new SessionHolder [0];
			theLastUsed = System.currentTimeMillis();
		}

		String getID()
		{
			return theID;
		}

		boolean isSecure()
		{
			return isSecure;
		}

		void unsecure()
		{
			isSecure = false;
		}

		SecuritySession getSecurity(User user)
		{
			theLastUsed = System.currentTimeMillis();
			for(SecuritySession sec : theSecurities)
				if(sec.getUser().equals(user))
					return sec;
			return null;
		}

		synchronized SecuritySession createSecurity(User user) throws PrismsException
		{
			for(SecuritySession sec : theSecurities)
				if(sec.getUser().equals(user))
					return sec;
			SecuritySession newSec = new SecuritySession(this, user);
			theSecurities = ArrayUtils.add(theSecurities, newSec);
			return newSec;
		}

		SessionHolder getSession(User user, ClientConfig client)
		{
			for(SessionHolder session : theSessionHolders)
				if(session.getUser().equals(user) && session.getClient().equals(client))
					return session;
			return null;
		}

		synchronized SessionHolder createSession(User user, ClientConfig client)
		{
			for(SessionHolder session : theSessionHolders)
				if(session.getUser().equals(user) && session.getClient().equals(client))
					return session;
			SessionHolder newSession = new SessionHolder(this, user, client);
			theSessionHolders = ArrayUtils.add(theSessionHolders, newSession);
			return newSession;
		}

		synchronized void removeSession(SessionHolder session)
		{
			for(int s = 0; s < theSessionHolders.length; s++)
				if(theSessionHolders[s] == session)
				{
					theSessionHolders = ArrayUtils.remove(theSessionHolders, s);
					s--;
				}
		}

		boolean check()
		{
			boolean remove = false;
			for(SecuritySession sec : theSecurities)
				if(sec.isExpired())
				{
					remove = true;
					break;
				}
			if(!remove)
				for(SessionHolder session : theSessionHolders)
					if(session.isExpired())
					{
						remove = true;
						break;
					}
			if(remove)
			{
				synchronized(this)
				{
					for(int s = 0; s < theSecurities.length; s++)
						if(theSecurities[s].isExpired())
						{
							theSecurities = ArrayUtils.remove(theSecurities, s);
							s--;
						}
					for(int s = 0; s < theSessionHolders.length; s++)
						if(theSessionHolders[s].isExpired())
						{
							SessionHolder session = theSessionHolders[s];
							theSessionHolders = ArrayUtils.remove(theSessionHolders, s);
							session.destroy();
							s--;
						}
				}
			}
			return theSecurities.length == 0 && theSessionHolders.length == 0
				&& System.currentTimeMillis() - theLastUsed > getSecurityTimeout() * 2;
		}

		void destroy()
		{
			SecuritySession [] securities = theSecurities;
			theSecurities = new SecuritySession [0];
			for(SecuritySession security : securities)
				security.destroy();
			SessionHolder [] sessions = theSessionHolders;
			theSessionHolders = new SessionHolder [0];
			for(SessionHolder session : sessions)
				session.destroy();
		}
	}

	private UserSource theUserSource;

	private boolean isConfigured;

	private final java.util.concurrent.ConcurrentHashMap<String, HttpSession> theSessions;

	private Class<? extends Encryption> theEncryptionClass;

	java.util.HashMap<String, String> theEncryptionProperties;

	private RemoteEventSerializer theSerializer;

	private PersisterFactory thePersisterFactory;

	private long theCleanTimer;

	private long theCleanInterval;

	int theLoginFailTolerance = 3;

	long theUserCheckPeriod = 60000;

	private long theSecurityTimeout;

	private long theSecurityRefresh;

	private boolean loginOnce;

	private boolean isTrusted;

	/**
	 * Creates a PRISMS server with default logging configuration
	 */
	public PrismsServer()
	{
		this(true);
	}

	/**
	 * Creates a PRISMS server
	 * 
	 * @param initDefaultLogging Whether to initialize the default logging or not
	 */
	public PrismsServer(boolean initDefaultLogging)
	{
		theSessions = new java.util.concurrent.ConcurrentHashMap<String, HttpSession>(32);
		theSerializer = new JsonSerializer();
		theCleanTimer = System.currentTimeMillis();
		theCleanInterval = 60 * 1000;
		if(initDefaultLogging)
			initLog4j(getClass().getResource("log4j.xml"));
	}

	/**
	 * Initializes the log4j system for PRISMS
	 * 
	 * @param log4jXML The address to the XML to use to initialize Log4j, or null if this server
	 *        should not initialize Log4j
	 */
	public static void initLog4j(java.net.URL log4jXML)
	{
		System.out.println("Initializing logging");

		if(log4jXML != null)
		{
			org.apache.log4j.xml.DOMConfigurator.configure(log4jXML);
			System.out.println("Initialization of Log4j completed");
			log.info("Initialized Log4j successfully");
		}
		else
			System.out.println("could not find log4j.xml resource!");
	}

	/**
	 * Initializes the log4j system for PRISMS
	 * 
	 * @param log4jprops - the properties to use to initialize Log4j, or null if this server should
	 *        not initialize Log4j
	 */
	public static void initLog4j(java.util.Properties log4jprops)
	{
		System.out.println("Initializing logging");

		if(log4jprops != null)
		{
			org.apache.log4j.LogManager.resetConfiguration();
			org.apache.log4j.PropertyConfigurator.configure(log4jprops);
			System.out.println("Initialization of Log4j completed");
			log.info("Initialized Log4j successfully");
		}
		else
			System.out.println("could not find log4j properties!");
	}

	/**
	 * @return This server's persister factory
	 */
	public PersisterFactory getPersisterFactory()
	{
		return thePersisterFactory;
	}

	/**
	 * @param factory The PersisterFactory to set for this server
	 */
	public void setPersisterFactory(PersisterFactory factory)
	{
		thePersisterFactory = factory;
	}

	/**
	 * @return The event serializer that this server uses
	 */
	public RemoteEventSerializer getSerializer()
	{
		return theSerializer;
	}

	/**
	 * @return The amount of time (in milliseconds) that this server keeps unused security sessions
	 *         around before it purges them.
	 */
	public long getSecurityTimeout()
	{
		return theSecurityTimeout;
	}

	/**
	 * @return The amount of time (in milliseconds) that this server keeps security sessions around
	 *         before it purges them for security.
	 */
	public long getSecurityRefresh()
	{
		return theSecurityRefresh;
	}

	/**
	 * @return Whether this server allows clients to login to a security session once and then
	 *         assumes that the security tag is kept safe. The alternative is requiring the user to
	 *         log in for each application and whenever the browser is refreshed.
	 */
	public boolean isLoginOnce()
	{
		return loginOnce;
	}

	/**
	 * @return Whether this server assumes that requests given it have already been authentication
	 *         through another architecture
	 */
	public boolean isTrusted()
	{
		return isTrusted;
	}

	/**
	 * @return The XML to use to configure this server
	 */
	protected org.dom4j.Element getConfigXML()
	{
		try
		{
			return new org.dom4j.io.SAXReader().read(getClass().getResource("PRISMSConfig.xml"))
				.getRootElement();
		} catch(Exception e)
		{
			log.error("Could not read PRISMS config file!", e);
			throw new IllegalStateException("Could not read PRISMS config file", e);
		}
	}

	/**
	 * Configures this server
	 * 
	 * @throws IllegalStateException If an error occurs
	 */
	protected void configurePRISMS()
	{
		log.info("Configuring PRISMS...");
		org.dom4j.Element configEl = getConfigXML();

		org.dom4j.Element securityEl = configEl.element("security");
		org.dom4j.Element timeoutEl = securityEl == null ? null : securityEl.element("timeout");
		if(timeoutEl == null)
			theSecurityTimeout = 5L * 60 * 6000;
		else
		{
			int seconds = 0;
			if(timeoutEl.attributeValue("seconds") != null)
				seconds = Integer.parseInt(timeoutEl.attributeValue("seconds"));
			int minutes = 0;
			if(timeoutEl.attributeValue("minutes") != null)
				minutes = Integer.parseInt(timeoutEl.attributeValue("minutes"));
			int hours = 0;
			if(timeoutEl.attributeValue("hours") != null)
				hours = Integer.parseInt(timeoutEl.attributeValue("hours"));
			if(seconds == 0 && minutes == 0 && hours == 0)
			{
				log.error("No security timeout specified");
				minutes = 5;
			}
			theSecurityTimeout = ((hours * 60L + minutes) * 60 + seconds) * 1000;
		}
		org.dom4j.Element refreshEl = securityEl == null ? null : securityEl.element("refresh");
		if(refreshEl == null)
			theSecurityRefresh = -1;
		else
		{
			int seconds = 0;
			if(refreshEl.attributeValue("seconds") != null)
				seconds = Integer.parseInt(refreshEl.attributeValue("seconds"));
			int minutes = 0;
			if(refreshEl.attributeValue("minutes") != null)
				minutes = Integer.parseInt(refreshEl.attributeValue("minutes"));
			int hours = 0;
			if(refreshEl.attributeValue("hours") != null)
				hours = Integer.parseInt(refreshEl.attributeValue("hours"));
			if(seconds == 0 && minutes == 0 && hours == 0)
			{
				log.error("No security refresh specified");
				minutes = 5;
			}
			theSecurityRefresh = ((hours * 60L + minutes) * 60 + seconds) * 1000;
		}
		if(securityEl != null)
		{
			loginOnce = "true".equalsIgnoreCase(securityEl.elementTextTrim("loginOnce"));
			isTrusted = "true".equalsIgnoreCase(securityEl.elementTextTrim("validation-trusted"));
		}
		else
		{
			loginOnce = false;
			isTrusted = false;
		}

		theEncryptionProperties = new java.util.HashMap<String, String>();
		org.dom4j.Element encryptionEl = configEl.element("encryption");
		if(encryptionEl != null)
		{
			String encryptionClass = encryptionEl.elementTextTrim("class");
			try
			{
				theEncryptionClass = Class.forName(encryptionClass).asSubclass(Encryption.class);
			} catch(Throwable e)
			{
				log.error("Could not instantiate encryption " + encryptionClass, e);
				throw new IllegalStateException("Could not instantiate encryption "
					+ encryptionClass, e);
			}
			for(org.dom4j.Element propEl : (java.util.List<org.dom4j.Element>) encryptionEl
				.elements())
			{
				if(propEl.getName().equals("class"))
					continue;
				theEncryptionProperties.put(propEl.getName(), propEl.getTextTrim());
			}
		}
		else
			theEncryptionClass = BlowfishEncryption.class;

		String serializerClass = configEl.elementTextTrim("serializer");
		if(serializerClass != null)
			try
			{
				theSerializer = (RemoteEventSerializer) Class.forName(serializerClass)
					.newInstance();
			} catch(Throwable e)
			{
				log.error("Could not instantiate serializer " + serializerClass, e);
				throw new IllegalStateException("Could not instantiate serializer "
					+ serializerClass, e);
			}

		org.dom4j.Element persisterFactoryEl = configEl.element("persisterFactory");
		if(thePersisterFactory == null && persisterFactoryEl != null)
		{
			String className = persisterFactoryEl.elementTextTrim("class");
			try
			{
				thePersisterFactory = (PersisterFactory) Class.forName(className).newInstance();
			} catch(Throwable e)
			{
				log.error("Could not instantiate persister factory " + className, e);
				throw new IllegalStateException("Could not instantiate persister factory "
					+ className, e);
			}
			thePersisterFactory.configure(persisterFactoryEl);
		}
		if(thePersisterFactory == null)
			throw new IllegalStateException("No PersisterFactory set--cannot configure PRISMS");

		org.dom4j.Element dsEl = configEl.element("datasource");
		if(dsEl == null)
		{
			log.error("No datasource element in server config");
			throw new IllegalStateException("No datasource element in server config");
		}

		String dsClass = dsEl.elementTextTrim("class");
		try
		{
			theUserSource = (UserSource) Class.forName(dsClass).newInstance();
		} catch(Exception e)
		{
			log.error("Could not instantiate data source " + dsClass + e.getMessage());
			throw new IllegalStateException("Could not instantiate data source " + dsClass, e);
		}

		try
		{
			theUserSource.configure(dsEl, thePersisterFactory);
		} catch(Exception e)
		{
			log.error("Could not configure data source " + e.getMessage());
			throw new IllegalStateException("Could not configure data source", e);
		}
	}

	/**
	 * @return This server's user source
	 */
	public UserSource getUserSource()
	{
		return theUserSource;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
	{
		JSONArray events = new JSONArray();
		JSONObject temp;
		PrismsRequest pReq = new PrismsRequest(req, resp);
		if(pReq.isWMS)
		{
			temp = pReq.adjustWMS();
			if(temp != null)
			{
				events.add(temp);
				pReq.send(events);
				return;
			}
		}
		if(req.getParameterValues("user") != null && req.getParameterValues("user").length > 1)
		{
			/* If multiple user names are sent in the request, it could be an attacker attempting
			 * to take advantage of the trust relationship this PRISMS server has with the
			 * application server. It must be caught and rejected. */
			String [] userNames = req.getParameterValues("user");
			for(String un : userNames)
				if(!un.equals(pReq.userName))
				{
					events.add(error(ErrorCode.RequestInvalid,
						"Multiple user names sent in request"));
					pReq.send(events);
					return;
				}
		}

		// Configure the server if it has not been yet
		if(!isConfigured)
		{
			log.info("Not yet configured! Configuring...");
			try
			{
				configurePRISMS();
				isConfigured = true;
			} catch(Throwable e)
			{
				log.error("PRISMS configuration failed", e);
				events.add(error(ErrorCode.ServerError, "PRISMS configuration failed: "
					+ e.getMessage()));
				pReq.send(events);
				return;
			}
			log.info("... Done Configuring.");
		}

		// Fill in the request with user, app, client, etc.
		temp = fillRequest(pReq);
		if(temp != null)
		{
			events.add(temp);
			pReq.send(events);
			return;
		}
		if(pReq.client.isService())
			pReq.sessionID = pReq.user.getName() + "/" + pReq.app.getName() + "/"
				+ pReq.client.getName();

		HttpSession httpSession = null;
		if(pReq.sessionID == null)
		{
			if(req.getCookies() != null)
				for(javax.servlet.http.Cookie c : req.getCookies())
					if(c.getName().equals("prismsSessionID"))
						pReq.sessionID = c.getValue();
			if(pReq.sessionID != null)
				events.add(toEvent("setSessionID", "sessionID", pReq.sessionID));
			else
			{
				if(pReq.serverMethod.equals("init"))
				{
					/* If no session ID was sent on initialization, we create a session, send them
					 * the correct session ID and go on */
					httpSession = createSession(pReq);
					pReq.sessionID = httpSession.getID();
					javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie(
						"prismsSessionID", pReq.sessionID);
					cookie.setComment("Allows PRISMS to keep track of sessions between refreshes");
					cookie.setMaxAge(-1);
					resp.addCookie(cookie);
					events.add(toEvent("setSessionID", "sessionID", pReq.sessionID));
				}
				else
				{
					/* If no session ID was sent for anything but initialization, this is an error */
					events.add(error(ErrorCode.RequestInvalid,
						"Request is missing the sessionID parameter"));
					pReq.send(events);
					return;
				}
			}
		}
		if(httpSession == null)
			httpSession = theSessions.get(pReq.sessionID);
		if(httpSession == null)
		{
			if(pReq.client.isService() || "init".equals(pReq.serverMethod))
			{
				/* All services with the same user and client use a common session
				 * If init is being called, they're already restarting--no need to tell them again.
				 */
				httpSession = createSession(pReq);
				pReq.sessionID = httpSession.getID();
				if(!pReq.client.isService())
				{
					javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie(
						"prismsSessionID", pReq.sessionID);
					cookie.setComment("Allows PRISMS to keep track of sessions between refreshes");
					cookie.setMaxAge(-1);
					resp.addCookie(cookie);
					events.add(toEvent("setSessionID", "sessionID", pReq.sessionID));
				}
			}
			else
			{
				/* No session sent.  This means the server has been redeployed. */
				events.add(toEvent("restart", "message", "Server has been restarted."
					+ " Refresh required."));
				pReq.send(events);

				javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("prismsSessionID",
					pReq.sessionID);
				cookie.setMaxAge(0);
				resp.addCookie(cookie);
				return;
			}
		}
		/* If the session is being access in an unsecure manner, this compromises every other PRISMS
		 * session being used with the same ID because the sessionID could be stolen and used to
		 * connect, via HTTPS, to a PRISMS session without requiring a password. Therefore
		 * connectiong to PRISMS over HTTP "ruins" every HTTPS session's security. We will therefore
		 * need to require encryption for every PRISMS session within the HTTP session from now on.
		 * 
		 * Web service clients need send no sessionID, therefore encryption must always be required
		 * for web services.
		 */
		if(httpSession.isSecure() && (!req.isSecure() || pReq.client.isService()))
			httpSession.unsecure();

		// Now we check the user's credentials
		SecuritySession security = httpSession.getSecurity(pReq.user);
		if(security == null)
		{
			/* If the security has not been created for the user yet or if the security session has
			 * expired, create a new one and let the security tell them what to do. */
			try
			{
				security = httpSession.createSecurity(pReq.user);
			} catch(PrismsException e)
			{
				log.error("Could not retrieve validation info", e);
				events.add(error(ErrorCode.ServerError, "Could not retrieve validation info: "
					+ e.getMessage()));
				pReq.send(events);
				return;
			}
		}
		PrismsResponse pResp = security.validate(pReq);
		if(pResp.toReturn != null)
		{
			/* Security says the request was not valid or else it requires some more information. */
			events.addAll(pResp.toReturn);
			pResp.toReturn = events;
			security.writeReturn(pReq, pResp);
			return;
		}

		// The request is valid and authenticated. Now we get or create the session.
		SessionHolder session = httpSession.getSession(pReq.user, pReq.client);
		if(session == null)
		{
			if("init".equals(pReq.serverMethod) || pReq.client.isService())
			{
				Runtime runtime = Runtime.getRuntime();
				long maxMem = runtime.maxMemory();
				if(runtime.freeMemory() + (maxMem - runtime.totalMemory()) < maxMem * 0.15f)
				{
					events.add(error(ErrorCode.ServerError,
						"Request cannot be processed due to high load"));
					pReq.send(events);
					return;
				}
				session = httpSession.createSession(pReq.user, pReq.client);
			}
			else
			{
				/* The session has timed out or has been logged out.  We'll assume the former. */
				events
					.add(toEvent("restart", "message", "Session has timed out! Refresh required."));
				pReq.send(events);
				return;
			}
		}

		// Session is non-null. Process the event.
		pResp = session.processEvent(pReq, pResp.toProcess);
		clean();
		if(pResp.toReturn != null)
		{
			events.addAll(pResp.toReturn);
			pResp.toReturn = events;
			security.writeReturn(pReq, pResp);
			return;
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
		throws ServletException, IOException
	{
		doGet(req, resp);
	}

	static JSONObject toEvent(String method, Object... params)
	{
		JSONObject ret = prisms.util.PrismsUtils.rEventProps(params);
		if(method != null)
			ret.put("method", method);
		return ret;
	}

	static JSONObject error(ErrorCode code, String message)
	{
		return toEvent("error", "message", message, "code", code.description);
	}

	private JSONObject fillRequest(PrismsRequest req)
	{
		/* TODO: This calls PRISMS several times for each and every request. This would make using
		 * a PRISMS web service ridiculously slow.  Change so that users, apps, and clients are
		 * cached and only accessed when not present in the cache.  Refresh the cache periodically
		 * to allow for dynamic updates from the manager. */
		if(req.appName == null)
			return error(ErrorCode.RequestInvalid, "No application specified!");
		if(req.clientName == null)
			return error(ErrorCode.RequestInvalid, "No client configuration specified!");
		try
		{
			req.user = theUserSource.getUser(req.userName);
		} catch(PrismsException e)
		{
			log.error("Could not get user " + req.userName, e);
			return error(ErrorCode.ServerError, "Could not get user \"" + req.userName + "\": "
				+ e.getMessage());
		}
		if(req.user == null && isTrusted())
		{
			if(theUserSource instanceof prisms.arch.ds.ManageableUserSource)
			{
				try
				{
					req.user = ((prisms.arch.ds.ManageableUserSource) theUserSource)
						.createUser(req.userName);
				} catch(PrismsException e)
				{
					log.error("Could not create new user " + req.userName, e);
					return toEvent("login", "error", "Cannot create user " + req.userName);
				}
				if(req.user == null)
					return toEvent("login", "error", "Cannot create user " + req.userName);
			}
			else
				return toEvent("login", "error", "Cannot create users");
		}
		if(req.user == null)
		{
			if(req.userName == null)
				return toEvent("login", "error", "No anonymous user configured");
			else
				return toEvent("login", "error", "No such user \"" + req.userName + "\"");
		}
		try
		{
			req.app = theUserSource.getApp(req.appName);
		} catch(PrismsException e)
		{
			log.error("Could not get application " + req.appName, e);
			return error(ErrorCode.ServerError, "Could not get application \"" + req.appName
				+ "\": " + e.getMessage());
		}
		if(req.app == null)
			return error(ErrorCode.RequestInvalid, "No such application \"" + req.appName + "\"");
		if(req.app.getServer() != this)
			req.app.setServer(this);
		try
		{
			req.client = theUserSource.getClient(req.app, req.clientName);
		} catch(PrismsException e)
		{
			log.error("Could not get client " + req.clientName, e);
			return error(ErrorCode.ServerError, "Could not get application \"" + req.clientName
				+ "\": " + e.getMessage());
		}
		if(req.client == null)
			return error(ErrorCode.RequestInvalid, "No such client \"" + req.clientName
				+ "\" for application \"" + req.appName + "\"");
		return null;
	}

	/**
	 * Creates a session in response to an init request
	 * 
	 * @param req The request to create the session for
	 * @return The new session
	 */
	private HttpSession createSession(PrismsRequest req)
	{
		String id;
		if(req.client.isService())
			id = req.user.getName() + "/" + req.app.getName() + "/" + req.client.getName();
		else
			id = newSessionID() + "/" + req.theRequest.isSecure();
		HttpSession ret = new HttpSession(id, req.theRequest.isSecure());
		theSessions.put(id, ret);
		return ret;
	}

	private String newSessionID()
	{
		String ret;
		do
		{
			ret = Integer.toHexString((int) ((Math.random()
				* ((double) Integer.MAX_VALUE - (double) Integer.MIN_VALUE) + Integer.MIN_VALUE)));
		} while(theSessions.containsKey(ret));
		return ret;
	}

	void removeSession(String id)
	{
		theSessions.remove(id);
	}

	/**
	 * Disposes of application sessions that are expired
	 */
	public void clean()
	{
		long time = System.currentTimeMillis();
		if(time - theCleanTimer > theCleanInterval)
			return;
		theCleanTimer = Long.MAX_VALUE;
		java.util.Iterator<HttpSession> sessionIter = theSessions.values().iterator();
		while(sessionIter.hasNext())
		{
			HttpSession session = sessionIter.next();
			if(session != null && session.check())
				sessionIter.remove();
		}
		theCleanTimer = time;
		System.gc();
	}

	Encryption createEncryption()
	{
		try
		{
			return theEncryptionClass.newInstance();
		} catch(Exception e)
		{
			throw new IllegalStateException("Could not instantiate encryption "
				+ theEncryptionClass, e);
		}
	}

	@Override
	public void destroy()
	{
		log.debug("Destroying servlet");
		HttpSession [] sessions = theSessions.values().toArray(new HttpSession [0]);
		theSessions.clear();
		for(HttpSession session : sessions)
			session.destroy();
		theUserSource.disconnect();
		thePersisterFactory.destroy();
		System.gc();
	}
}
