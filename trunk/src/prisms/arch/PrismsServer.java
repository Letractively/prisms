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

import prisms.arch.PrismsAuthenticator.RequestAuthenticator;
import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;
import prisms.arch.ds.UserSource;
import prisms.arch.wms.PrismsWmsRequest;
import prisms.arch.wms.WmsPlugin;
import prisms.util.ArrayUtils;
import prisms.util.PrismsUtils;
import prisms.util.ProgramTracker.TrackNode;

/**
 * This server is the root of the PRISMS architecture. It takes HTTP requests that conform to the
 * PRISMS specification and delegates them to the appropriate applications, sessions, and plugins.
 */
public class PrismsServer extends javax.servlet.http.HttpServlet
{
	static final Logger log = Logger.getLogger(PrismsServer.class);

	static final Logger sessionLog = Logger.getLogger("prisms.sessions");

	/**
	 * The threshold before a session expires at which the client will receive a warning that its
	 * session is about to expire
	 */
	public static final long WARN_EXPIRE_THRESHOLD = 120000L;

	private static final java.text.SimpleDateFormat MOD_DATE_FORMAT = new java.text.SimpleDateFormat(
		"ddMMMyyyy");

	/** An error code that will be sent back with events of type method="error" */
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

		/** A small description of the error */
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

	/**
	 * Represents a constraint on the number of times a remote host can query a PRISMS server within
	 * a set amount of time. If a host exceeds this constraint, the host may be prevented from
	 * accessing PRISMS data for a time.
	 */
	public static class ActivityConstraint
	{
		/**
		 * The amount of time within which a host may not hit this server {@link #maxHits} times, in
		 * seconds
		 */
		public final int constraintTime;

		/**
		 * The maximum number of times a host is permitted to access this server within the
		 * {@link #constraintTime} interval
		 */
		public final int maxHits;

		/**
		 * The recommended amount of time that a host will be locked out of the server for exceeding
		 * this constraint, in seconds. The actual lockout time will be determined by just how
		 * quickly the client uses up its max hit count compared to the constraint time.
		 */
		public final int lockTime;

		/**
		 * Creates an Activity Constraint
		 * 
		 * @param cTime {@link #constraintTime}
		 * @param hits {@link #maxHits}
		 * @param lTime {@link #lockTime}
		 */
		public ActivityConstraint(int cTime, int hits, int lTime)
		{
			constraintTime = cTime;
			maxHits = hits;
			lockTime = lTime;
		}
	}

	private static class AppConfigurator
	{
		private static class ClientConfigurator
		{
			ClientConfig theClient;

			PrismsConfig theClientConfigEl;

			private String theError;

			ClientConfigurator(PrismsConfig clientConfigEl)
			{
				theClientConfigEl = clientConfigEl;
			}

			void setClient(ClientConfig client)
			{
				theClient = client;
			}

			boolean shouldConfigureOnLoad()
			{
				PrismsConfig cce = theClientConfigEl;
				if(cce == null)
					return false;
				return cce.subConfig("load-immediately") != null;
			}

			synchronized String configureClient(AppConfig appConfig)
			{
				if(theError != null)
					return theError;
				if(theClientConfigEl == null)
					return null;
				log.info("Configuring client " + theClient.getName() + " of PRISMS application "
					+ theClient.getApp());
				try
				{
					appConfig.configureClient(theClient, theClientConfigEl);
				} catch(RuntimeException e)
				{
					log.error("Could not configure client " + theClient + " of application "
						+ theClient.getApp(), e);
					theError = e.toString();
				} catch(Error e)
				{
					log.error("Could not configure client " + theClient + " of application "
						+ theClient.getApp(), e);
					theError = e.toString();
				}
				if(theError != null)
					return theError;
				theClient.setConfigured(this);
				theClientConfigEl = null;
				return null;
			}
		}

		PrismsApplication theApp;

		final AppConfig theAppConfig;

		PrismsConfig theAppConfigEl;

		private String theError;

		private java.util.HashMap<String, ClientConfigurator> theClients;

		AppConfigurator(AppConfig appConfig, PrismsConfig appConfigEl)
		{
			theAppConfig = appConfig;
			theAppConfigEl = appConfigEl;
			theClients = new java.util.HashMap<String, ClientConfigurator>();
		}

		void setApp(PrismsApplication app)
		{
			theApp = app;
		}

		boolean shouldConfigureOnLoad()
		{
			PrismsConfig ace = theAppConfigEl;
			if(ace == null)
				return false;
			return ace.subConfig("load-immediately") != null;
		}

		synchronized String configureApp()
		{
			if(theError != null)
				return theError; // If configuration fails, don't try again
			if(theAppConfigEl == null)
				return null;
			log.info("Configuring PRISMS application " + theApp.getName());
			try
			{
				theAppConfig.configureApp(theApp, theAppConfigEl);
			} catch(RuntimeException e)
			{
				log.error("Could not configure application " + theApp, e);
				theError = e.toString();
			} catch(Error e)
			{
				log.error("Could not configure application " + theApp, e);
				theError = e.toString();
			}
			if(theError != null)
				return theError;
			theApp.setConfigured(this);
			theAppConfigEl = null;

			for(ClientConfigurator cc : theClients.values())
				if(cc.shouldConfigureOnLoad())
					cc.configureClient(theAppConfig);
			return null;
		}

		Object addClient(String clientName, PrismsConfig clientConfigEl)
		{
			ClientConfigurator cc = new ClientConfigurator(clientConfigEl);
			theClients.put(clientName, cc);
			return cc;
		}

		void setClient(ClientConfig client)
		{
			theClients.get(client.getName()).setClient(client);
		}

		String configureClient(ClientConfig client)
		{
			return theClients.get(client.getName()).configureClient(theAppConfig);
		}
	}

	/** Represents a request into the PRISMS framework */
	public static class PrismsRequest
	{
		/** The HTTP request represented by this PRISMS request */
		public final HttpServletRequest httpRequest;

		final HttpServletResponse theResponse;

		final RemoteEventSerializer theSerializer;

		/** The version of the client--only valid for M2M clients */
		public final String version;

		String serverMethod;

		String appName;

		String clientName;

		boolean isWMS;

		String dataString;

		PrismsAuthenticator auth;

		User user;

		PrismsApplication app;

		ClientConfig client;

		PrismsRequest(HttpServletRequest req, HttpServletResponse resp,
			RemoteEventSerializer serializer)
		{
			httpRequest = req;
			theResponse = resp;
			theSerializer = serializer;
			version = req.getParameter("version");
			serverMethod = req.getParameter("method");
			appName = req.getParameter("app");
			clientName = req.getParameter("client");
			if(clientName == null)
				clientName = req.getParameter("service");
			dataString = req.getParameter("data");
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
			else if(appName == null)
			{
				JSONObject data;
				try
				{
					data = theSerializer.deserialize(dataString);
					if(appName == null)
						appName = (String) data.get("app");
					if(clientName == null)
						clientName = (String) data.get("service");
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
			out.print(theSerializer.serialize(events));
			out.close();
		}

		/** @return The application that this request is for */
		public PrismsApplication getApp()
		{
			return app;
		}

		/** @return The client configuration that this request is for */
		public ClientConfig getClient()
		{
			return client;
		}

		/** @return The user that this request is for */
		public User getUser()
		{
			return user;
		}
	}

	private static class PrismsResponse
	{
		final PrismsAuthenticator.RequestAuthenticator reqAuth;

		ErrorCode code;

		String error;

		JSONArray toReturn;

		JSONObject toProcess;

		boolean shouldEncrypt;

		/**
		 * This constructor is used by the security session to return the events that need to be
		 * processed by the session.
		 */
		PrismsResponse(PrismsAuthenticator.RequestAuthenticator ra, JSONObject process)
		{
			reqAuth = ra;
			toProcess = process;
		}

		/**
		 * This constructor is used to return the events that need to be passed back to the client
		 */
		PrismsResponse(PrismsAuthenticator.RequestAuthenticator ra, JSONArray events,
			boolean encrypt)
		{
			reqAuth = ra;
			toReturn = events;
			shouldEncrypt = encrypt;
		}
	}

	private class SecuritySession
	{
		private final PrismsAuthenticator theAuth;

		private final User theUser;

		private final String theRemoteAddr;

		private final String theRemoteHost;

		private final prisms.util.ClientEnvironment theClientEnv;

		private PrismsAuthenticator.SessionAuthenticator theSessionAuth;

		private String [] theAccessApps;

		private boolean isAnonymous;

		private boolean isSystem;

		private volatile long userLastChecked;

		private long theCreationTime;

		private long theLastUsed;

		private boolean isLoggedIn;

		SecuritySession(PrismsAuthenticator auth, User user, HttpServletRequest req)
			throws PrismsException
		{
			theAuth = auth;
			theUser = user;
			if(req != null)
			{
				theRemoteAddr = req.getRemoteAddr();
				theRemoteHost = req.getRemoteHost();
				theClientEnv = prisms.util.ClientEnvironment.getClientEnv(req
					.getHeader("User-Agent"));
			}
			else
			{
				theRemoteAddr = "common";
				theRemoteHost = "common";
				theClientEnv = prisms.util.ClientEnvironment.getClientEnv(null);
			}
			UserSource us = getEnv().getUserSource();
			isAnonymous = user.equals(us.getUser(null));
			isSystem = us instanceof prisms.arch.ds.ManageableUserSource
				&& ((prisms.arch.ds.ManageableUserSource) us).getSystemUser().equals(user);
			checkAuthenticationData();
			theCreationTime = theLastUsed;
		}

		PrismsAuthenticator getAuth()
		{
			return theAuth;
		}

		User getUser()
		{
			return theUser;
		}

		String getRemoteHost()
		{
			return theRemoteHost;
		}

		String getRemoteAddr()
		{
			return theRemoteAddr;
		}

		prisms.util.ClientEnvironment getClientEnv()
		{
			return theClientEnv;
		}

		/**
		 * Checks this session holder's user against the data source periodically to see whether the
		 * password has changed or the user has been locked.
		 * 
		 * @return Whether the authentication data was renewed
		 * @throws PrismsException
		 */
		private boolean checkAuthenticationData() throws PrismsException
		{
			long time = System.currentTimeMillis();
			theLastUsed = time;
			if(time - userLastChecked < 30000)
				return false;
			synchronized(this)
			{
				if(time - userLastChecked < 30000)
					return false;
				userLastChecked = time;
				theAccessApps = new String [0];
				return true;
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
			if(theSessionAuth == null)
			{
				try
				{
					theSessionAuth = theAuth.createSessionAuthenticator(req, theUser);
				} catch(PrismsException e)
				{
					log.error("Could not initialize authentication", e);
					return error(null, req, ErrorCode.ServerError,
						"Could not initialize authentication: " + e.getMessage(), false);
				}
			}
			if(!req.client.isCommonSession() && !req.user.equals(theUser))
				return error(null, req, ErrorCode.RequestInvalid,
					"Attempted to access another user's session!", false);

			// First deal with attempted anonymous access
			if(isAnonymous && !req.client.allowsAnonymous())
			{
				JSONObject login;
				try
				{
					login = theSessionAuth.requestLogin(req);
				} catch(PrismsException e)
				{
					log.error("Could not send login information", e);
					return error(null, req, ErrorCode.ServerError,
						"Could not send login information: " + e.getMessage(), false);
				}
				if(login == null)
					return error(null, req, ErrorCode.ServerError,
						"Anonymous access forbidden for client " + req.client.getName()
							+ " of application " + req.app.getName(), false);
				else
					return sendLogin(null, req, "Anonymous access forbidden for client "
						+ req.client.getName() + " of application " + req.app.getName()
						+ ".  Please log in.", "init".equals(req.serverMethod), false);
			}
			if(req.getUser().isLocked())
				return error(null, req, ErrorCode.ValidationFailed, "User \"" + theUser.getName()
					+ "\" is locked. Contact your admin.", false);

			// Check with the authenticator to make sure the user has credentials
			prisms.arch.PrismsAuthenticator.RequestAuthenticator reqAuth;
			try
			{
				reqAuth = theSessionAuth.getRequestAuthenticator(req);
			} catch(PrismsException e)
			{
				log.error("Could not authenticate session", e);
				return error(null, req, ErrorCode.ServerError, "Could not authenticate session: "
					+ e.getMessage(), false);
			}
			if(reqAuth.isError())
			{
				if(reqAuth.shouldReattempt())
				{
					if(req.isWMS)
						return error(reqAuth, req, ErrorCode.ValidationFailed,
							"WMS does not support encryption.", false);
					else
						return sendLogin(reqAuth, req, reqAuth.getError(),
							"init".equals(req.serverMethod), reqAuth.getError() != null);
				}
				else
					return error(reqAuth, req, ErrorCode.ValidationFailed, reqAuth.getError(),
						false);
			}
			if(!isLoggedIn)
			{
				String className = theAuth.getClass().getName();
				if(className.indexOf('.') >= 0)
					className = className.substring(className.lastIndexOf('.') + 1);
				sessionLog.info("User " + theUser + " was authenticated by " + className);
				isLoggedIn = true;
			}

			// Check the user's access to the requested application
			if(!isSystem && !ArrayUtils.contains(theAccessApps, req.appName))
			{
				try
				{
					if(!getEnv().getUserSource().canAccess(theUser, req.app))
						return error(
							null,
							req,
							ErrorCode.ValidationFailed,
							"User \"" + theUser.getName()
								+ "\" does not have permission to access application \""
								+ req.app.getName() + "\"", false);
				} catch(PrismsException e)
				{
					log.error("Could not determine user " + theUser + "'s access to application "
						+ req.app.getName(), e);
					return error(null, req, ErrorCode.ServerError, "Could not determine user "
						+ theUser.getName() + "'s access to application " + req.app.getName(),
						false);
				}
				theAccessApps = ArrayUtils.add(theAccessApps, req.appName);
			}

			// Check the user's access to the client
			try
			{
				if(!isSystem)
					getEnv().getUserSource().assertAccessible(theUser, req.client);
			} catch(PrismsException e)
			{
				log.error("User " + theUser + " cannot access client " + req.client.getName()
					+ " of application " + req.app.getName(), e);
				return error(null, req, ErrorCode.ValidationFailed, e.getMessage(), false);
			}

			JSONObject authMsg = null;
			try
			{
				if(checkAuthenticationData())
				{
					long authID = theSessionAuth.recheck();
					if(authID >= 0)
					{
						authMsg = (JSONObject) sendLogin(reqAuth, req,
							"Password changed. Enter new password.", false, false).toReturn.get(0);
						authMsg.put("authID", Long.valueOf(authID));
					}
				}
			} catch(PrismsException e)
			{
				log.error("Could not check authentication data", e);
				return error(reqAuth, req, ErrorCode.ServerError,
					"Could not check authentication data: " + e.getMessage(), false);
			}

			PrismsResponse ret = processValidated(reqAuth, req);
			if(authMsg != null)
			{
				if(ret.toReturn == null)
				{
					ret.toReturn = new JSONArray();
					ret.toReturn.add(authMsg);
				}
				else
					ret.toReturn.add(authMsg);
			}
			return ret;
		}

		private PrismsResponse processValidated(RequestAuthenticator reqAuth, PrismsRequest req)
		{
			String dataStr = reqAuth.getData();

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
					return error(reqAuth, req, ErrorCode.RequestInvalid, "Deserialization of "
						+ dataStr + " failed", true);
				}
			}
			else
				event = null;
			// The encryption succeeded--the session knows the password

			if("changePassword".equals(req.serverMethod))
			{
				PrismsAuthenticator.AuthenticationError error;
				try
				{
					error = theSessionAuth.changePassword(event);
				} catch(PrismsException e)
				{
					log.error("Could not send change password", e);
					return error(reqAuth, req, ErrorCode.ServerError, "Could not change password: "
						+ e.getMessage(), false);
				}
				if(error != null)
				{
					JSONObject change;
					try
					{
						change = theSessionAuth.requestPasswordChange();
					} catch(PrismsException e)
					{
						log.error("Could not request password change", e);
						return error(reqAuth, req, ErrorCode.ServerError,
							"Could not request password change", true);
					}
					String msg = (String) change.remove("message");
					if(msg != null)
						msg = error.message + "\n" + msg;
					else
						msg = error.message;
					change.put("error", msg);
					change.put("method", "changePassword");
					return singleMessage(reqAuth, req, change, true);
				}
				return singleMessage(reqAuth, req, true, "callInit");
			}

			try
			{
				if(theSessionAuth.needsPasswordChange())
				{
					if(req.isWMS)
						return error(reqAuth, req, ErrorCode.ValidationFailed,
							"Password change required for user \"" + theUser.getName() + "\"",
							false);
					JSONObject change = theSessionAuth.requestPasswordChange();
					String msg = (String) change.get("message");
					if(msg != null)
						msg = "Password change required for user \"" + theUser.getName() + "\"\n"
							+ msg;
					else
						msg = "Password change required for user \"" + theUser.getName() + "\"";
					change.put("message", msg);
					change.put("method", "changePassword");
					return singleMessage(reqAuth, req, change, true);
				}
			} catch(PrismsException e)
			{
				log.error("Could not get password expiration", e);
				return error(reqAuth, req, ErrorCode.ServerError,
					"Could not get password expiration: " + e.getMessage(), true);
			}

			if("tryChangePassword".equals(req.serverMethod))
			{
				JSONObject change;
				try
				{
					change = theSessionAuth.requestPasswordChange();
				} catch(PrismsException e)
				{
					log.error("Could not request password change", e);
					return error(reqAuth, req, ErrorCode.ServerError,
						"Could not request password change", true);
				}
				change.put("method", "changePassword");
				return singleMessage(reqAuth, req, change, true);
			}
			if("getVersion".equals(req.serverMethod))
			{
				JSONArray version = null;
				if(req.app.getVersion() != null)
				{
					version = new JSONArray();
					for(int v = 0; v < req.app.getVersion().length; v++)
						version.add(Integer.valueOf(req.app.getVersion()[v]));
				}
				return singleMessage(reqAuth, req, true, "setVersion", "version", version,
					"modified", Long.valueOf(req.app.getModifiedDate()));
			}
			// The password is valid

			// The session is valid--it can do what it wants now

			return new PrismsResponse(reqAuth, event);
		}

		void writeReturn(PrismsAuthenticator.RequestAuthenticator reqAuth, PrismsRequest request,
			PrismsResponse response) throws IOException
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
			if(!request.client.isService() || compareVersions(request.version, "2.1.3") >= 0)
				str = PrismsUtils.encodeUnicode(str);
			if(response.shouldEncrypt)
				str = encrypt(reqAuth, request, str);
			request.theResponse.setContentType(serializer.getContentType(response.toReturn));
			String acceptEncoding = request.httpRequest.getHeader("accept-encoding");
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

		private String encrypt(PrismsAuthenticator.RequestAuthenticator reqAuth,
			PrismsRequest request, String text) throws PrismsException
		{
			/* TODO: For whatever reason, information encrypted by the server is not decrypted
			 * correctly by the javascript dojo blowfish implementation on the HTML client. Pending
			 * more extensive investigation, we'll just send information unencrypted. */
			if(request.client.isService())
				return reqAuth.encrypt(request.theResponse, text);
			return text;
		}

		PrismsResponse sendLogin(prisms.arch.PrismsAuthenticator.RequestAuthenticator reqAuth,
			PrismsRequest req, String error, boolean postInit, boolean isError)
		{
			JSONObject evt;
			try
			{
				evt = theSessionAuth.requestLogin(req);
			} catch(PrismsException e)
			{
				log.error("Could not send login information", e);
				return error(reqAuth, req, ErrorCode.ServerError,
					"Could not send login information: " + e.getMessage(), false);
			}
			if(evt == null)
				return singleMessage(reqAuth, req, PrismsUtils.rEventProps("method", "restart",
					"message", "You have been successfully logged out."), false);
			if(postInit)
				evt.put("postAction", "callInit");
			/* Prior to service client version 2.1.2, the client connector had a bug that caused an
			 * initial connection to fail if the result was a login request with a ValidationFailed
			 * code. */
			if(isError && compareVersions(req.version, "2.1.2") >= 0)
				evt.put("code", ErrorCode.ValidationFailed.description);
			if(error != null)
				evt.put("error", error);
			return singleMessage(reqAuth, req, evt, false);
		}

		private PrismsResponse error(RequestAuthenticator reqAuth, PrismsRequest req,
			ErrorCode code, String message, boolean encrypted)
		{
			/* Wait a little here. This degrades the effectiveness of a denial-of-service attack if
			 * the attacker does not have valid credentials */
			try
			{
				Thread.sleep(150);
			} catch(InterruptedException e)
			{}
			if("doUpload".equals(req.serverMethod))
				throw new IllegalStateException(code + ": " + message);
			JSONObject evt = new JSONObject();
			evt.put("code", code);
			evt.put("message", message);
			PrismsResponse ret = singleMessage(reqAuth, req, encrypted, "error", "code",
				code.description, "message", message);
			ret.code = code;
			ret.error = message;
			return ret;
		}

		private PrismsResponse singleMessage(RequestAuthenticator reqAuth, PrismsRequest req,
			boolean encrypted, String method, Object... params)
		{
			return singleMessage(reqAuth, req, toEvent(method, params), encrypted);
		}

		private PrismsResponse singleMessage(RequestAuthenticator reqAuth, PrismsRequest req,
			JSONObject evt, boolean encrypted)
		{
			JSONArray arr = new JSONArray();
			arr.add(evt);
			return new PrismsResponse(reqAuth, arr, encrypted);
		}

		long untilExpires()
		{
			long now = System.currentTimeMillis();
			long ret = Long.MAX_VALUE;
			long timeout = getSecurityTimeout();
			if(timeout > 0)
				ret = timeout - now + theLastUsed;
			long refresh = getSecurityRefresh();
			if(refresh > 0 && ret > refresh - now + theCreationTime)
				ret = refresh - now + theCreationTime;
			return ret;
		}

		void destroy()
		{
			try
			{
				theSessionAuth.destroy();
			} catch(PrismsException e)
			{
				log.error("Error destroying authentication metadata", e);
			}
		}
	}

	private class PrismsSessionHolder
	{
		private final HttpSession theHttpSession;

		private final SecuritySession theSecurity;

		private final User theUser;

		private final PrismsApplication theApp;

		private final ClientConfig theClient;

		private PrismsSession theSession;

		private final long theCreationTime;

		private boolean hasCreatedSession;

		/**
		 * Creates a session holder
		 * 
		 * @param httpSession The HTTP session that this session holder is in
		 * @param security The security session that allowed access to this session
		 * @param client The client configuration that this session is for
		 */
		public PrismsSessionHolder(HttpSession httpSession, SecuritySession security,
			ClientConfig client)
		{
			theHttpSession = httpSession;
			theSecurity = security;
			theUser = security.getUser();
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

		public PrismsSession getSession()
		{
			return theSession;
		}

		/**
		 * Processes an event for a validated session
		 * 
		 * @param reqAuth The request authenticator for the request
		 * @param req The request that requires a response
		 * @param event The event to process
		 * @return The response if the result was in an event form
		 * @throws IOException If an error occurs writing to the response
		 */
		public PrismsResponse processEvent(RequestAuthenticator reqAuth, PrismsRequest req,
			JSONObject event) throws IOException
		{
			if(hasCreatedSession)
			{
				prisms.arch.PrismsApplication.ApplicationLock lock;
				try
				{
					lock = theApp.getApplicationLock();
				} catch(PrismsException e)
				{
					log.error("Could not get application lock for " + theApp, e);
					return error(reqAuth, req, ErrorCode.ServerError,
						"Could not get application lock: " + e.getMessage());
				}
				if(lock != null && lock.getLockingSession() != theSession)
					return singleMessage(reqAuth, req, true, "appLocked", "message",
						lock.getMessage(), "scale", Integer.valueOf(lock.getScale()), "progress",
						Integer.valueOf(lock.getProgress()));
			}
			// Do prisms methods
			if("init".equals(req.serverMethod))
				return init(reqAuth, req);
			if(!hasCreatedSession)
			{ // Client needs to be re-initialized
				if(req.isWMS || req.client.isService())
				{ // For WMS we swallow this for simplicity
					PrismsResponse resp = init(reqAuth, req);
					if(resp.code != null)
						return resp;
				}
				else
					return singleMessage(reqAuth, req, true, "restart");
			}
			final PrismsTransaction trans = getEnv().transact(theSession,
				PrismsTransaction.Stage.processEvent);
			TrackNode track = trans.getTracker().start("Server Calls");
			try
			{
				if("logout".equals(req.serverMethod))
				{
					TrackNode track2 = trans.getTracker().start("Logout");
					try
					{
						theHttpSession.removeSession(this);
						destroy();
						sessionLog.info("Session "
							+ (theSession == null ? "" : theSession.getMetadata().getID() + " ")
							+ ", user " + theUser + ", application " + theClient.getApp()
							+ ", client " + theClient + " logged out.");
						return theSecurity.sendLogin(reqAuth, req,
							"You have been successfully logged out", false, false);
					} finally
					{
						trans.getTracker().end(track2);
					}
				}

				// Do application methods (processEvent, generateImage, doDownload, doUpload)
				if("processEvent".equals(req.serverMethod))
					return process(reqAuth, event);
				if(req.isWMS)
				{
					PrismsWmsRequest wmsReq = PrismsWmsRequest.parseWMS(req.httpRequest);
					return processWMS(reqAuth, req, event, wmsReq);
				}
				if("generateImage".equals(req.serverMethod))
				{
					String reqURI = req.httpRequest.getRequestURI();
					int dotIdx = reqURI.indexOf(".");
					String format = null;
					if(dotIdx >= 0)
						format = reqURI.substring(dotIdx + 1);
					if(format != null)
						format = format.toLowerCase();
					if(!"png".equals(format) && !"jpg".equals(format) && !"jpeg".equals(format)
						&& !"gif".equals(format))
					{
						log.error("Unrecognized image format: " + format
							+ ".  Cannot generate image");
						return new PrismsResponse(reqAuth, null);
					}
					if(format.equals("jpg"))
						req.theResponse.setContentType("image/jpeg");
					else
						req.theResponse.setContentType("image/" + format);
					java.io.OutputStream out = req.theResponse.getOutputStream();
					generateImage(event, format, out);
					return new PrismsResponse(reqAuth, null);
				}
				if("getDownload".equals(req.serverMethod))
				{
					getDownload(req.theResponse, event);
					return new PrismsResponse(reqAuth, null);
				}
				if("doUpload".equals(req.serverMethod))
				{
					doUpload(req.httpRequest, event);
					// We redirect to avoid the browser's resend warning if the user refreshes
					req.theResponse.setStatus(301);
					req.theResponse.sendRedirect("nothing.html");
					return new PrismsResponse(reqAuth, null);
				}
			} finally
			{
				trans.getTracker().end(track);
				getEnv().finish(trans);
			}
			return error(reqAuth, req, ErrorCode.RequestInvalid,
				"Unable to process request: serverMethod " + req.serverMethod + " not defined");
		}

		private PrismsResponse init(RequestAuthenticator reqAuth, PrismsRequest req)
		{
			JSONArray ret = new JSONArray();
			try
			{
				if(!hasCreatedSession)
				{
					synchronized(this)
					{
						if(!hasCreatedSession)
						{
							theSession = createSession(theClient, theUser, createMetadata());
							hasCreatedSession = true;
						}
					}
				}
			} catch(PrismsException e)
			{
				String error = "Could not create " + (theClient.isService() ? "service " : "UI ")
					+ theApp.getName() + " session for user " + theUser.getName() + ", client "
					+ theClient.getName();
				log.error(error, e);
				return error(reqAuth, req, ErrorCode.ServerError, error + ": " + e.getMessage());
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
				return error(reqAuth, req, ErrorCode.ApplicationError, error);
			}
			JSONObject evt = new JSONObject();
			evt.put("method", "init");
			evt.put("user", req.user.getName());
			ret.add(evt);
			ret.addAll(theSession.getEvents());
			return new PrismsResponse(reqAuth, ret, true);
		}

		private PrismsResponse process(RequestAuthenticator reqAuth, JSONObject event)
		{
			JSONArray ret = null;
			if(theSession.getClient().isService()
				|| Boolean.TRUE.equals(event.get("prisms-synchronous")))
			{
				TrackNode track = PrismsUtils.track(getEnv(), "processSync");
				try
				{
					ret = theSession.processSync(event);
				} finally
				{
					PrismsUtils.end(getEnv(), track);
				}
			}
			else
			{
				TrackNode track = PrismsUtils.track(getEnv(), "processAsync");
				try
				{
					ret = theSession.processAsync(event, null);
				} finally
				{
					PrismsUtils.end(getEnv(), track);
				}
			}
			long exp = untilExpires();
			if(exp <= WARN_EXPIRE_THRESHOLD)
			{
				JSONObject warnExpireEvent = new JSONObject();
				warnExpireEvent.put("method", "warnExpire");
				warnExpireEvent.put("expireTime", Long.valueOf(exp));
				ret.add(warnExpireEvent);
			}
			return new PrismsResponse(reqAuth, ret, true);
		}

		private PrismsResponse processWMS(RequestAuthenticator reqAuth, PrismsRequest req,
			JSONObject event, PrismsWmsRequest wms) throws IOException
		{
			String pluginName = (String) event.get("plugin");
			WmsPlugin plugin = (WmsPlugin) theSession.getPlugin(pluginName);
			HttpServletResponse response = req.theResponse;
			if(plugin == null)
				return error(reqAuth, req, ErrorCode.RequestInvalid, "No plugin " + pluginName
					+ " loaded");
			TrackNode track = PrismsUtils.track(getEnv(), "PRISMS Plugin " + pluginName + ".wms("
				+ wms.getRequest().name() + ")");
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
					if(wms.getHeight() == 0 || wms.getWidth() == 0
						|| wms.getBounds().minLat == wms.getBounds().maxLat
						|| wms.getBounds().minLon == wms.getBounds().maxLon)
						javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1,
							java.awt.image.BufferedImage.TYPE_4BYTE_ABGR), wms.getFormat(), out);
					else
					{
						java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(out);
						plugin.drawMapOverlay(wms, event, bos);
						bos.flush();
					}
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
				return error(reqAuth, req, ErrorCode.ApplicationError, e.getClass().getName()
					+ ": " + e.getMessage());
			} finally
			{
				PrismsUtils.end(getEnv(), track);
			}
			return new PrismsResponse(reqAuth, null);
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
			TrackNode track = PrismsUtils.track(getEnv(), "PRISMS Plugin " + pluginName
				+ ".writeImage()");
			try
			{
				((ImagePlugin) theSession.getPlugin(pluginName)).writeImage(method, format,
					xOffset, yOffset, refWidth, refHeight, imWidth, imHeight, out);
			} finally
			{
				PrismsUtils.end(getEnv(), track);
			}
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
			response.setHeader("Content-Disposition",
				"attachment; filename=\"" + plugin.getFileName(event) + "\"");
			int size = plugin.getDownloadSize(event);
			if(size >= 0)
				response.setContentLength(size);
			TrackNode track = PrismsUtils.track(getEnv(), "PRISMS Plugin " + pluginName
				+ ".doDownload");
			try
			{
				plugin.doDownload(event, response.getOutputStream());
			} catch(RuntimeException e)
			{
				log.error("Download failed", e);
			} catch(Error e)
			{
				log.error("Download failed", e);
			} finally
			{
				PrismsUtils.end(getEnv(), track);
			}
		}

		private void doUpload(HttpServletRequest request, final JSONObject event)
			throws IOException
		{
			final String pluginName = (String) event.get("plugin");
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
						TrackNode track = PrismsUtils.track(getEnv(), "PRISMS Plugin " + pluginName
							+ ".doUpload");
						try
						{
							plugin.doUpload(event, fileName, item.getContentType(),
								item.getInputStream(), item.getSize());
						} catch(java.io.IOException e)
						{
							log.error("Upload " + fileName + " failed", e);
						} catch(RuntimeException e)
						{
							log.error("Upload failed", e);
						} catch(Error e)
						{
							log.error("Upload failed", e);
						} finally
						{
							PrismsUtils.end(getEnv(), track);
							item.delete();
						}
					}
				});
				didUpload = true;
			}
			if(!didUpload)
				throw new IllegalStateException("getUpload called with no non-form field");
		}

		private prisms.arch.PrismsSession.SessionMetadata createMetadata()
		{
			return new PrismsSession.SessionMetadata(
				Integer.toHexString(theHttpSession.hashCode()), theSecurity.getAuth(),
				theSecurity.getRemoteAddr(), theSecurity.getRemoteHost(),
				theSecurity.getClientEnv(), theHttpSession.getGovernor());
		}

		private PrismsResponse error(RequestAuthenticator reqAuth, PrismsRequest req,
			ErrorCode code, String message)
		{
			JSONObject evt = new JSONObject();
			evt.put("code", code);
			evt.put("message", message);
			PrismsResponse ret = singleMessage(reqAuth, req, true, "error", "code",
				code.description, "message", message);
			ret.code = code;
			ret.error = message;
			return ret;
		}

		private PrismsResponse singleMessage(RequestAuthenticator reqAuth, PrismsRequest req,
			boolean encrypted, String method, Object... params)
		{
			return singleMessage(reqAuth, req, toEvent(method, params), encrypted);
		}

		private PrismsResponse singleMessage(RequestAuthenticator reqAuth, PrismsRequest req,
			JSONObject evt, boolean encrypted)
		{
			JSONArray arr = new JSONArray();
			arr.add(evt);
			return new PrismsResponse(reqAuth, arr, encrypted);
		}

		long untilExpires()
		{
			long ret = Long.MAX_VALUE;
			long timeout = theClient.getSessionTimeout();
			if(theSession != null)
				ret = theSession.untilExpires();
			else if(timeout > 0 && timeout - System.currentTimeMillis() + theCreationTime < ret)
				ret = timeout - System.currentTimeMillis() + theCreationTime;
			return ret;
		}

		boolean isKilled()
		{
			return theSession != null && theSession.isKilled();
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

		private SecuritySession [] theSecurities;

		private PrismsSessionHolder [] theSessionHolders;

		private long theLastUsed;

		private ClientGovernor theGovernor;

		HttpSession(String id, ClientGovernor gov)
		{
			theID = id;
			theSecurities = new SecuritySession [0];
			theSessionHolders = new PrismsSessionHolder [0];
			theLastUsed = System.currentTimeMillis();
			theGovernor = gov;
		}

		ClientGovernor getGovernor()
		{
			return theGovernor;
		}

		SecuritySession getSecurity(PrismsAuthenticator auth, User user)
		{
			theLastUsed = System.currentTimeMillis();
			for(SecuritySession sec : theSecurities)
				if(sec.getAuth().equals(auth) && sec.getUser().equals(user))
					return sec;
			return null;
		}

		synchronized SecuritySession createSecurity(PrismsAuthenticator auth, User user,
			HttpServletRequest req) throws PrismsException
		{
			for(SecuritySession sec : theSecurities)
				if(sec.getAuth().equals(auth) && sec.getUser().equals(user))
					return sec;
			SecuritySession newSec = new SecuritySession(auth, user, req);
			theSecurities = ArrayUtils.add(theSecurities, newSec);
			return newSec;
		}

		PrismsSessionHolder getSession(User user, ClientConfig client)
		{
			for(PrismsSessionHolder session : theSessionHolders)
				if(session.getUser().equals(user) && session.getClient().equals(client))
					return session;
			return null;
		}

		synchronized PrismsSessionHolder createSession(SecuritySession security, ClientConfig client)
		{
			for(PrismsSessionHolder session : theSessionHolders)
				if(session.getUser().equals(security.getUser())
					&& session.getClient().equals(client))
					return session;
			PrismsSessionHolder newSession = new PrismsSessionHolder(this, security, client);
			theSessionHolders = ArrayUtils.add(theSessionHolders, newSession);
			return newSession;
		}

		synchronized void removeSession(PrismsSessionHolder session)
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
			{
				if(sec.untilExpires() <= 0)
				{
					remove = true;
					break;
				}
			}
			if(!remove)
				for(PrismsSessionHolder session : theSessionHolders)
					if(session.untilExpires() <= 0 || session.isKilled())
					{
						remove = true;
						break;
					}
			if(remove)
			{
				synchronized(this)
				{
					for(int s = 0; s < theSessionHolders.length; s++)
					{
						PrismsSessionHolder holder = theSessionHolders[s];
						if(holder.untilExpires() <= 0 || holder.isKilled())
						{
							String key = theID + "/" + holder.getClient().getApp().getName() + "/"
								+ holder.getClient().getName() + "/" + holder.getUser().getName();
							if(holder.isKilled())
							{
								sessionLog.info("Session "
									+ (holder.getSession() == null ? "" : holder.getSession()
										.getMetadata().getID()
										+ " ") + ", user " + holder.getUser() + ", application "
									+ holder.getClient().getApp() + ", client "
									+ holder.getClient() + " has been killed by an administrator.");
								theEpitaphs.put(key, new SessionEpitaph(
									"Session has been killed by an administrator."));
							}
							else
							{
								sessionLog.info("Session "
									+ (holder.getSession() == null ? "" : holder.getSession()
										.getMetadata().getID()
										+ " ") + ", user " + holder.getUser() + ", application "
									+ holder.getClient().getApp() + ", client "
									+ holder.getClient() + " has timed out.");
								theEpitaphs.put(key, new SessionEpitaph("Session has timed out."));
							}
							theSessionHolders = ArrayUtils.remove(theSessionHolders, s);
							holder.destroy();
							s--;
						}
					}
					for(int s = 0; s < theSecurities.length; s++)
					{
						SecuritySession security = theSecurities[s];
						boolean hasUserSession = false;
						for(PrismsSessionHolder holder : theSessionHolders)
							if(holder.getUser().equals(security.getUser()))
							{
								hasUserSession = true;
								break;
							}
						if(!hasUserSession || security.untilExpires() <= 0)
						{
							theSecurities = ArrayUtils.remove(theSecurities, s);
							s--;
							security.destroy();
						}
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
			PrismsSessionHolder [] sessions = theSessionHolders;
			theSessionHolders = new PrismsSessionHolder [0];
			for(PrismsSessionHolder session : sessions)
				session.destroy();
		}
	}

	private enum ConfigStage
	{
		/** Configuration has not started */
		NEW,
		/** Security is being configured */
		SECURITY,
		/** The default remote event serializer is being configured */
		SERIALIZER,
		/** The connection factory is being created and configured */
		CONNECTION_FACTORY,
		/** The user source is being created */
		USER_SOURCE,
		/** The ID generator and logger are being configured */
		ID_GENERATOR,
		/** Global (cross-app) listeners are being created and configured */
		GLOBAL_LISTENERS,
		/** Applications are being loaded */
		APPLICATIONS,
		/** The user source is being configured */
		CONFIG_USER_SOURCE,
		/** Default content (users, groups, etc.) is being loaded */
		LOAD_CONTENT,
		/** The manager application is being configured */
		LOAD_MANAGER,
		/** Session authenticators are being loaded and configured */
		AUTHENTICATORS,
		/** This PRISMS server is completely configured */
		CONFIGURED;

		ConfigStage next()
		{
			return values()[ordinal() + 1];
		}
	}

	private static class ConfigProgress
	{
		ConfigStage theStage;

		PrismsConfig theUsEl;

		ConfigProgress()
		{
			theStage = ConfigStage.values()[0];
		}
	}

	private static class SessionEpitaph
	{
		final String message;

		final long time;

		SessionEpitaph(String msg)
		{
			message = msg;
			time = System.currentTimeMillis();
		}
	}

	/** A client activity counter to compare client activity to a particular activity constraint */
	public static class ClientActivityCounter
	{
		volatile long startTime;

		int hitCount;

		long unlockTime;

		ClientActivityCounter()
		{
		}

		/** @return The time when this activity counter was started or restarted */
		public long getStartTime()
		{
			return startTime;
		}

		/**
		 * @return The number of hits that this client has had since this counter was started or
		 *         restarted
		 */
		public int getHitCount()
		{
			return hitCount;
		}

		/** @return The time at which this counter will unlock, or -1 if it is not locked */
		public long getUnlockTime()
		{
			return unlockTime;
		}
	}

	/** Represents the activity of the client */
	public class ClientGovernor
	{
		private final ClientActivityCounter [] theActivities;

		ClientGovernor()
		{
			theActivities = new ClientActivityCounter [theActivityConstraints.length];
			for(int c = 0; c < theActivities.length; c++)
				theActivities[c] = new ClientActivityCounter();
		}

		long hit(String host)
		{
			long now = System.currentTimeMillis();
			for(int c = 0; c < theActivities.length; c++)
			{
				ActivityConstraint constraint = theActivityConstraints[c];
				ClientActivityCounter activity = theActivities[c];
				long oStart = activity.startTime;
				if(activity.unlockTime >= 0)
				{
					if(now > activity.unlockTime)
						activity.unlockTime = -1;
					else
						return activity.unlockTime;
				}
				if(now - oStart >= constraint.constraintTime * 1000)
				{
					activity.startTime = now;
					activity.hitCount = 1;
				}
				else
				{
					int newHits = activity.hitCount + 1;
					if(newHits >= constraint.maxHits)
					{
						/* Decide how long to lock the client out. If the client ran through all its
						 * hits in a very short time, make the lock time longer. If the last hit was
						 * *just* shy of the reset time, don't penalize the client for very long. */
						float weight = constraint.constraintTime * 1000f / (now - oStart) - 1;
						if(weight > 2)
							weight = 2;
						activity.unlockTime = now + (long) (weight * constraint.lockTime * 1000);

						if(host != null)
						{
							StringBuilder sb = new StringBuilder("Remote host ").append(host);
							sb.append(" locked out for ").append(newHits).append(" hits in ");
							PrismsUtils.printTimeLength(now - oStart, sb, false);
							sb.append(": Unlock set to ");
							sb.append(PrismsUtils.TimePrecision.SECONDS.print(activity.unlockTime,
								true));
							log.error(sb.toString());
						}
						activity.hitCount = 0;
						return activity.unlockTime;
					}
					else if(activity.startTime == oStart)
						activity.hitCount = newHits;
				}
			}
			return -1;
		}

		/**
		 * @return The time when this governor will unlock access of its client to the server, or -1
		 *         if the client is not currently locked out
		 */
		public long isLocked()
		{
			long now = System.currentTimeMillis();
			for(int c = 0; c < theActivities.length; c++)
				if(theActivities[c].unlockTime >= now)
					return theActivities[c].unlockTime;
			return -1;
		}

		/**
		 * Gets the activity counter for this client governor corresponding to an activity
		 * constraint set in the server. The counters in this governor are indexed identically to
		 * the constraints in the server.
		 * 
		 * @param index The index of the activity counter to get
		 * @return The activity counter at the given index
		 */
		public ClientActivityCounter getCounter(int index)
		{
			return theActivities[index];
		}

		/** @return The index of the counter that is currently closest to exceeding its constraint */
		public int getClosestCounter()
		{
			int ret = -1;
			float closest = 0;
			for(int c = 0; c < theActivities.length; c++)
			{
				float dist = getCloseness(c);
				if(dist > closest)
				{
					closest = dist;
					ret = c;
				}
			}
			return ret;
		}

		/**
		 * Checks how close the counter at the given index is to exceeding its constraint. 0 means
		 * there have been no hits from the client within any of the constraint times, 1 means that
		 * one or more of the constraints have just or are about to lock the client out. This method
		 * will not always return 1 when the client is locked out. In fact, a client that is locked
		 * out will gradually become farther and farther from exceeding its constraints, so that
		 * when it is unlocked, it will have larger thresholds before it is locked out again.
		 * 
		 * @param index The index of the constraint to test
		 * @return How close the counter at the given index is to exceeding its constraint. 0 means
		 *         there have been no hits from the client within any of the constraint times, 1
		 *         means that one or more of the constraints have just or are about to lock the
		 *         client out.
		 */
		public float getCloseness(int index)
		{
			ActivityConstraint constraint = theActivityConstraints[index];
			ClientActivityCounter activity = theActivities[index];
			long now = System.currentTimeMillis();
			long timeDist = now - activity.startTime;
			if(timeDist >= constraint.constraintTime * 1000)
				return 0;
			if(timeDist < constraint.constraintTime * 1000 / 2)
				timeDist = constraint.constraintTime * 1000 / 2;
			float dist = activity.hitCount * 1.0f / timeDist
				/ (constraint.maxHits * 1.0f / (constraint.constraintTime * 1000));
			if(dist > 1)
				dist = 1;
			return dist;
		}
	}

	private final PrismsEnv theEnv;

	private java.util.HashMap<String, AppConfigurator> theConfigs;

	private final java.util.LinkedHashMap<String, PrismsApplication> theApps;

	private final java.util.ArrayList<PrismsAuthenticator> theAuthenticators;

	private final java.util.concurrent.ConcurrentHashMap<String, HttpSession> theSessions;

	final java.util.concurrent.ConcurrentHashMap<String, SessionEpitaph> theEpitaphs;

	final prisms.util.DemandCache<String, ClientGovernor> theClientGovernors;

	private RemoteEventSerializer theSerializer;

	private long theCleanTimer;

	private long theCleanInterval;

	private long theSecurityTimeout;

	private long theSecurityRefresh;

	ActivityConstraint [] theActivityConstraints;

	private ConfigProgress theConfigProgress;

	private boolean isCheckingForRunaways;

	/** Creates a PRISMS server with default logging configuration */
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
		this(new PrismsEnv(), initDefaultLogging);
	}

	/**
	 * Creates a PRISMS server
	 * 
	 * @param env The environment for this server to use
	 * @param initDefaultLogging Whether to initialize the default logging or not
	 */
	public PrismsServer(PrismsEnv env, boolean initDefaultLogging)
	{
		theEnv = env;
		log.info("Loaded PRISMS");
		theSessions = new java.util.concurrent.ConcurrentHashMap<String, HttpSession>(32);
		theEpitaphs = new java.util.concurrent.ConcurrentHashMap<String, SessionEpitaph>();
		theClientGovernors = new prisms.util.DemandCache<String, ClientGovernor>(
			new prisms.util.DemandCache.Qualitizer<ClientGovernor>()
			{
				public float quality(ClientGovernor value)
				{
					if(value.isLocked() >= 0)
						return 1000;
					else
						return 1;
				}

				public float size(ClientGovernor value)
				{
					return 1;
				}
			}, 0, 15L * 60 * 1000);
		theSerializer = new JsonSerializer();
		theCleanTimer = System.currentTimeMillis();
		theCleanInterval = 1000;
		if(initDefaultLogging)
			initLog4j(getClass().getResource("log4j.xml"));
		theApps = new java.util.LinkedHashMap<String, PrismsApplication>();
		theAuthenticators = new java.util.ArrayList<PrismsAuthenticator>();
		theConfigs = new java.util.HashMap<String, AppConfigurator>();
		theConfigProgress = new ConfigProgress();
		isCheckingForRunaways = true;

		PrismsConfig configXML = getPrismsConfig();
		if(configXML.subConfig("load-immediately") != null)
			configurePRISMS(null);
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

	/** @return This server's environment */
	public PrismsEnv getEnv()
	{
		return theEnv;
	}

	/** @return The event serializer that this server uses by default */
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

	/** @return The activity constraints that prevent overuse of this server */
	public ActivityConstraint [] getActivityConstraints()
	{
		return theActivityConstraints.clone();
	}

	/**
	 * @return Whether this server checks for running transactions that are taking a long time to
	 *         finish
	 */
	public boolean isCheckingForRunaways()
	{
		return isCheckingForRunaways;
	}

	/**
	 * @param cfr Whether this server checks for running transactions that are taking a long time to
	 *        finish
	 */
	public void setCheckingForRunaways(boolean cfr)
	{
		isCheckingForRunaways = cfr;
	}

	/** @return The configuration to use to configure this server */
	protected PrismsConfig getPrismsConfig()
	{
		try
		{
			return PrismsConfig.fromXml(theEnv, "PRISMSConfig.xml",
				PrismsConfig.getLocation(getClass()));
		} catch(Exception e)
		{
			log.error("Could not read PRISMS config file!", e);
			throw new IllegalStateException("Could not read PRISMS config file", e);
		}
	}

	/**
	 * @return The configuration to use to configure this server's default applications. These are
	 *         the applications that come packaged with PRISMS and must exist to support the
	 *         architecture.
	 */
	protected PrismsConfig getDefaultAppConfig()
	{
		try
		{
			return PrismsConfig.fromXml(theEnv, "DefaultApplications.xml",
				PrismsConfig.getLocation(PrismsServer.class));
		} catch(Exception e)
		{
			log.error("Could not read PRISMS default applications file!", e);
			throw new IllegalStateException("Could not read PRISMS default applications file", e);
		}
	}

	/**
	 * Configures this server
	 * 
	 * @param req The request that is requesting the initialization of this PRISMS server. May be
	 *        null if no request is unavailable. Additional configuration may be required in this
	 *        case.
	 * @return null if successful, or an error message if something goes wrong
	 * @throws IllegalStateException If an error occurs
	 */
	protected synchronized String configurePRISMS(HttpServletRequest req)
	{
		if(theConfigProgress.theStage == ConfigStage.CONFIGURED)
			return null;
		log.info("Configuring PRISMS...");
		PrismsConfig pConfig = getPrismsConfig();
		isCheckingForRunaways = pConfig.is("checkForRunaways", true);
		String configXmlRef = getClass().getResource("PRISMSConfig.xml").toString();

		if(theConfigProgress.theStage.compareTo(ConfigStage.NEW) <= 0)
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		if(theConfigProgress.theStage.compareTo(ConfigStage.SECURITY) <= 0)
		{
			/* Parse the security timeout--the amount of time after which unused security sessions
			 * will be removed */
			theSecurityTimeout = pConfig.getTime("security/timeout");
			if(theSecurityTimeout < 0)
				theSecurityTimeout = 5L * 60 * 6000;

			/* Parse the security refresh--the amount of time after which security sessions are
			 * purged, forcing clients to re-validate */
			theSecurityRefresh = pConfig.getTime("security/refresh");

			/* Parse the activity constraints--the constraints that prevent denial-of-service
			 * attacks by enforcing maximum hit counts for certain time intervals */
			PrismsConfig [] actConsts = pConfig.subConfigs("security/activity-constraint");
			theActivityConstraints = new ActivityConstraint [actConsts.length];
			for(int c = 0; c < actConsts.length; c++)
			{
				long constTime = actConsts[c].getTime("constraint-time");
				int maxHits = actConsts[c].getInt("max-hits", -1);
				long lockTime = actConsts[c].getTime("lockout-time");
				if(constTime < 0)
				{
					log.error("A positive constraint-time must be specified for each activity constraint");
					continue;
				}
				if(maxHits < 0)
				{
					log.error("A positive max-hits must be specified for each activity constraint");
					continue;
				}
				if(lockTime < 0)
				{
					log.error("A positive lockout-time must be specified for each activity constraint");
					continue;
				}
				theActivityConstraints[c] = new ActivityConstraint((int) (constTime / 1000),
					maxHits, (int) (lockTime / 1000));
			}
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theConfigProgress.theStage.compareTo(ConfigStage.SERIALIZER) <= 0)
		{
			Class<? extends RemoteEventSerializer> serializerClass;
			try
			{
				serializerClass = pConfig.getClass("serializer", RemoteEventSerializer.class);
			} catch(ClassNotFoundException e)
			{
				String msg = "Class " + pConfig.get("serializer")
					+ " not found for remote event serializer";
				log.error(msg, e);
				throw new IllegalStateException(msg, e);
			} catch(ClassCastException e)
			{
				String msg = "Class " + pConfig.get("serializer")
					+ " is not a remote event serializer";
				log.error(msg, e);
				throw new IllegalStateException(msg, e);
			}
			if(serializerClass != null)
				try
				{
					theSerializer = serializerClass.newInstance();
				} catch(Throwable e)
				{
					log.error("Could not instantiate serializer " + serializerClass.getName(), e);
					throw new IllegalStateException("Could not instantiate serializer "
						+ serializerClass, e);
				}
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theConfigProgress.theStage.compareTo(ConfigStage.CONNECTION_FACTORY) <= 0)
		{
			Class<? extends ConnectionFactory> pfClass;
			try
			{
				pfClass = pConfig.getClass("connection-factory/class", ConnectionFactory.class);
			} catch(ClassNotFoundException e)
			{
				String msg = "Class " + pConfig.get("connection-factory/class")
					+ " not found for connection factory";
				log.error(msg, e);
				throw new IllegalStateException(msg, e);
			} catch(ClassCastException e)
			{
				String msg = "Class " + pConfig.get("connection-factory/class")
					+ " is not a connection factory";
				log.error(msg, e);
				throw new IllegalStateException(msg, e);
			}
			if(pfClass == null)
				throw new IllegalStateException(
					"No ConnectionFactory class set--cannot configure PRISMS");
			try
			{
				theEnv.setConnectionFactory(pfClass.newInstance());
			} catch(Throwable e)
			{
				log.error("Could not instantiate connection factory " + pfClass.getName(), e);
				throw new IllegalStateException("Could not instantiate connection factory "
					+ pfClass.getName(), e);
			}
			theEnv.getConnectionFactory().configure(pConfig.subConfig("connection-factory"));
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theConfigProgress.theStage.compareTo(ConfigStage.USER_SOURCE) <= 0)
		{
			theConfigProgress.theUsEl = pConfig.subConfig("datasource");
			if(theConfigProgress.theUsEl == null)
			{
				log.error("No datasource element in server config");
				throw new IllegalStateException("No datasource element in server config");
			}

			Class<? extends UserSource> usClass;
			try
			{
				usClass = theConfigProgress.theUsEl.getClass("class", UserSource.class);
			} catch(ClassNotFoundException e)
			{
				String msg = "Class " + theConfigProgress.theUsEl.get("class")
					+ " not found for user source";
				log.error(msg, e);
				throw new IllegalStateException(msg, e);
			} catch(ClassCastException e)
			{
				String msg = "Class " + theConfigProgress.theUsEl.get("class")
					+ " is not a user source";
				log.error(msg, e);
				throw new IllegalStateException(msg, e);
			}
			if(usClass == null)
				throw new IllegalStateException("No UserSource set--cannot configure PRISMS");
			try
			{
				theEnv.setUserSource(usClass.newInstance());
			} catch(Exception e)
			{
				log.error("Could not instantiate user source " + usClass.getName(), e);
				throw new IllegalStateException("Could not instantiate user source "
					+ usClass.getName(), e);
			}
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theConfigProgress.theStage.compareTo(ConfigStage.ID_GENERATOR) <= 0)
		{
			PrismsConfig connEl = theConfigProgress.theUsEl.subConfig("connection");
			prisms.arch.ds.IDGenerator ids = new prisms.arch.ds.IDGenerator(
				theEnv.getConnectionFactory(), connEl);
			theEnv.setIDs(ids);

			String localScheme = pConfig.get("local-scheme");
			int localPort = pConfig.getInt("local-port", -1);
			String localPath = pConfig.get("local-path");
			if(localPort > 0 && localScheme == null)
				throw new IllegalStateException("Cannot specify local-port without local-scheme");
			String addr;
			try
			{
				addr = java.net.InetAddress.getLocalHost().getCanonicalHostName();
			} catch(java.net.UnknownHostException e)
			{
				log.error("Could not get local host address."
					+ " Enterprise and self connections will not be available.", e);
				addr = null;
			}
			if(addr != null)
			{
				if(localScheme == null && req != null)
				{
					localScheme = req.getScheme();
					localPort = req.getServerPort();
				}
				if(localPath == null && req != null)
				{
					localPath = req.getContextPath();
					localPath = req.getRequestURI().substring(
						req.getRequestURI().indexOf(localPath));
					if(localPath.startsWith("/"))
						localPath = localPath.substring(1);
					if(localPath.endsWith("/"))
						localPath = localPath.substring(0, localPath.length() - 1);
				}

				if(localScheme != null && localPath != null)
				{
					if((localPort == 80 && localScheme.equalsIgnoreCase("http"))
						|| (localPort == 443 && localScheme.equalsIgnoreCase("https")))
						localPort = -1;
					if(localPath.startsWith("/"))
						localPath = localPath.substring(1);
					String loc = localScheme + "://" + addr
						+ (localPort > 0 ? ":" + localPort : "") + "/" + localPath;
					boolean valid = false;
					try
					{
						String test = loc;
						if(localPath.contains("?"))
							test += "&";
						else
							test += "?";
						test += "method=test";
						java.net.URL url = new java.net.URL(test);
						java.io.Reader reader = new java.io.InputStreamReader(url.openStream());
						java.io.StringWriter writer = new java.io.StringWriter();
						int read = reader.read();
						while(read >= 0)
						{
							writer.write(read);
							read = reader.read();
						}
						reader.close();
						writer.close();
						if(!writer.toString().startsWith("success"))
							log.warn("Self-reference location " + loc
								+ " does not point to a PRISMS server."
								+ " Enterprise and self connections will not be available.\n"
								+ writer.toString());
						else if(!writer.toString().equals(
							"success:" + Integer.toHexString(theEnv.hashCode())))
							log.warn("Self-reference location " + loc
								+ " does not point to this server."
								+ "Enterprise and self connections will not be available.");
						else
							valid = true;
					} catch(IOException e)
					{
						log.warn("Self-reference location " + loc + " cannot be contacted."
							+ " Enterprise and self connections will not be available.", e);
					}
					if(valid)
						ids.setLocalConnectInfo(loc);
				}
				else
					log.warn("local-scheme, local-port, and local-path elements must be present in"
						+ " config unless PRISMS is configured on demand. Enterprise and self"
						+ " connections will not be available");
			}

			try
			{
				ids.setConfigured();
			} catch(PrismsException e)
			{
				log.error("Could not configure ID generator for environment", e);
				throw new IllegalStateException("Could not configure ID generator for environment",
					e);
			}

			Worker worker;
			PrismsConfig workerEl = pConfig.subConfig("worker");
			if(workerEl == null || "threadpool".equals(workerEl.get("type")))
			{
				if(workerEl == null || workerEl.get("threads") == null)
					worker = new prisms.impl.ThreadPoolWorker("PRISMS Worker");
				else
					worker = new prisms.impl.ThreadPoolWorker("PRISMS Worker", workerEl.getInt(
						"threads", 0));
			}
			else
				throw new IllegalArgumentException("Unrecognized worker type in worker element "
					+ workerEl + "\nCannot configure application without worker");
			theEnv.setWorker(worker);

			PrismsConfig tracking = pConfig.subConfig("tracking");
			if(tracking != null)
				theEnv.setTrackConfigs(prisms.util.TrackerSet.parseTrackConfigs(tracking));
			PrismsConfig displayThresh = tracking.subConfig("display-thresholds");
			if(displayThresh != null)
			{
				prisms.arch.PrismsEnv.GlobalPrintConfig gpc = theEnv.getDefaultPrintConfig();
				gpc.setPrintThreshold(displayThresh.getTime("print", gpc.getPrintThreshold()));
				gpc.setDebugThreshold(displayThresh.getTime("debug", gpc.getDebugThreshold()));
				gpc.setInfoThreshold(displayThresh.getTime("info", gpc.getInfoThreshold()));
				gpc.setWarningThreshold(displayThresh.getTime("warn", gpc.getWarningThreshold()));
				gpc.setErrorThreshold(displayThresh.getTime("error", gpc.getErrorThreshold()));
				gpc.setTaskDisplayThreshold(displayThresh.getTime("task",
					gpc.getTaskDisplayThreshold()));
				gpc.setAccentThreshold(displayThresh.getFloat("accent", gpc.getAccentThreshold()));
			}

			PrismsConfig logConfig = pConfig.subConfig("logger");
			if(logConfig != null)
				theEnv.getLogger().configure(logConfig);
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theConfigProgress.theStage.compareTo(ConfigStage.GLOBAL_LISTENERS) <= 0)
		{
			// Add global listeners
			for(PrismsConfig listSetEl : pConfig.subConfigs("global-listeners/listener-set"))
			{
				String globalName = listSetEl.get("name");
				if(globalName == null)
				{
					log.error("No name attribute for listener-set");
					continue;
				}
				for(PrismsConfig listEl : listSetEl.subConfigs("property"))
				{
					@SuppressWarnings("rawtypes")
					Class<? extends prisms.arch.event.PropertyManager> mgrType;
					try
					{
						mgrType = listEl.getClass("type", prisms.arch.event.PropertyManager.class);
					} catch(ClassNotFoundException e)
					{
						String msg = "Class " + listEl.get("type") + " not found for property"
							+ " manager in global set " + globalName;
						log.error(msg, e);
						return msg + ": " + e;
					} catch(ClassCastException e)
					{
						String msg = "Class " + listEl.get("type") + " in global set " + globalName
							+ " is not a property manager";
						log.error(msg, e);
						return msg + ": " + e;
					}
					if(mgrType == null)
					{
						String msg = "No type configured for property manager in global set "
							+ globalName;
						log.error(msg);
						return msg;
					}
					prisms.arch.event.PropertyManager<?> mgr;
					try
					{
						mgr = mgrType.newInstance();
					} catch(Throwable e)
					{
						String msg = "Could not instantiate manager type " + mgrType.getName();
						log.error(msg, e);
						return msg + ": " + e;
					}
					theEnv.addGlobalManager(globalName, mgr, listEl);
				}
				for(PrismsConfig listEl : listSetEl.subConfigs("event"))
					theEnv.addGlobalEventListener(globalName, listEl);
				for(PrismsConfig listEl : listSetEl.subConfigs("monitor"))
					theEnv.addGlobalMonitorListener(globalName, listEl);
			}
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theConfigProgress.theStage.compareTo(ConfigStage.APPLICATIONS) <= 0)
		{
			// Now we load default applications, one of which must be the manager application
			PrismsConfig defAppConfig = getDefaultAppConfig();
			loadApps(defAppConfig, PrismsServer.class.getResource("PrismsServer.class").getQuery());

			// Now we load the custom applications
			defAppConfig = pConfig.subConfig("applications");
			if(defAppConfig == null)
				log.warn("No custom applications found in "
					+ getClass().getResource("PRISMSConfig.xml"));
			else
				loadApps(defAppConfig, configXmlRef);
			theEnv.seal();
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		PrismsApplication [] apps = theApps.values()
			.toArray(new PrismsApplication [theApps.size()]);

		if(theConfigProgress.theStage.compareTo(ConfigStage.CONFIG_USER_SOURCE) <= 0)
		{
			// Now we can configure the user source
			try
			{
				theEnv.getUserSource().configure(theConfigProgress.theUsEl, theEnv, apps);
			} catch(Exception e)
			{
				log.error("Could not configure data source " + e.getMessage());
				throw new IllegalStateException("Could not configure data source", e);
			}
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theConfigProgress.theStage.compareTo(ConfigStage.LOAD_CONTENT) <= 0)
		{
			boolean init;
			try
			{
				init = theEnv.getIDs().isNewInstall()
					|| theEnv.getUserSource().getActiveUsers().length == 0;
			} catch(PrismsException e)
			{
				log.error("Could not check user count", e);
				init = false;
			}
			String ref = pConfig.get("default-users");
			if(ref != null)
			{
				if(!(theEnv.getUserSource() instanceof prisms.arch.ds.ManageableUserSource))
				{
					if(init)
						log.error("Cannot load default users--user source is not manageable");
					else
						log.info("Cannot load default users--user source is not manageable");
				}
				else
				{
					try
					{
						loadDefaultUsers(PrismsConfig.fromXml(theEnv, ref, configXmlRef), init);
					} catch(java.io.IOException e)
					{
						log.error("Could not load default users", e);
					}
				}
			}
			else
				log.info("No default users to load");
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theConfigProgress.theStage.compareTo(ConfigStage.LOAD_MANAGER) <= 0)
		{
			for(PrismsApplication app : theApps.values())
				if(theEnv.isManager(app))
				{
					app.addManager(new prisms.util.persisters.ReadOnlyManager<PrismsApplication []>(
						app, prisms.arch.event.PrismsProperties.applications, apps));
					String error = theConfigs.get(app.getName()).configureApp();
					if(error != null)
					{
						log.error("Could not load manager application--PRISMS configuration failed: "
							+ error);
						return "Could not load manager application: " + error;
					}
					log.info("Configured manager application \"" + app.getName() + "\"");
					break;
				}
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theConfigProgress.theStage.compareTo(ConfigStage.AUTHENTICATORS) <= 0)
		{
			// Load authenticators
			for(PrismsConfig authEl : pConfig.subConfigs("authenticator"))
			{
				PrismsAuthenticator auth;
				try
				{
					auth = authEl.getClass("class", PrismsAuthenticator.class).newInstance();
				} catch(ClassNotFoundException e)
				{
					log.error("Could not find authenticator class " + authEl.get("class"), e);
					continue;
				} catch(ClassCastException e)
				{
					log.error("Class " + authEl.get("class") + " is not an authenticator", e);
					continue;
				} catch(NullPointerException e)
				{
					log.error("No class given for authenticator");
					continue;
				} catch(Throwable e)
				{
					log.error("Could not instantiate authenticator " + authEl.get("class"), e);
					continue;
				}
				auth.configure(authEl, theEnv.getUserSource(), apps);
				theAuthenticators.add(auth);
			}
			theConfigProgress.theStage = theConfigProgress.theStage.next();
		}

		if(theEnv.getIDs().getLocalInstance() != null)
			log.info("PRISMS Configured at " + theEnv.getIDs().getLocalInstance().location);
		else
			log.info("PRISMS Configured");

		// Now we configure applications that have been marked to be loaded immediately
		for(AppConfigurator config : theConfigs.values())
			if(config.shouldConfigureOnLoad())
				config.configureApp();
		return null;
	}

	private void loadApps(PrismsConfig appsConfig, String path)
	{
		for(PrismsConfig appEl : appsConfig.subConfigs("application"))
		{
			boolean manager = "true".equalsIgnoreCase(appEl.get("manager"));
			String appConfigXML = appEl.get("configXML");
			if(appConfigXML == null)
			{
				log.error("Missing configXML attribute in application");
				continue;
			}
			PrismsConfig appXml;
			try
			{

				appXml = PrismsConfig.fromXml(theEnv, appConfigXML, path, null);
			} catch(IOException e)
			{
				log.error("Could not read application config XML " + appConfigXML, e);
				continue;
			}
			if(appXml == null)
				continue;
			appEl = appXml;
			String appName = appEl.get("name");
			if(appName == null)
			{
				log.error("No name element in application configuration");
				continue;
			}
			PrismsApplication app = theApps.get(appName);
			if(app != null)
			{
				log.error("Application " + appName + " is already loaded--will not be reconfigured");
				continue;
			}
			Class<? extends AppConfig> configClass;
			try
			{
				configClass = appEl.getClass("config-class", AppConfig.class);
			} catch(Throwable e)
			{
				log.error("Could not get configuration class " + appEl.get("config-class")
					+ " to configure application " + appName, e);
				continue;
			}
			AppConfig config;
			if(configClass != null)
			{
				try
				{
					config = configClass.newInstance();
				} catch(Throwable e)
				{
					log.error("Could not instantiate configuration class " + configClass.getName()
						+ " to configure application " + appName, e);
					continue;
				}
			}
			else
				config = new AppConfig();
			String descrip = appEl.get("description");
			int [] version = null;
			String versionString = appEl.get("version");
			if(versionString != null)
			{
				String [] split = versionString.split("\\.");
				version = new int [split.length];
				for(int v = 0; version != null && v < version.length; v++)
				{
					try
					{
						version[v] = Integer.parseInt(split[v]);
					} catch(Exception e)
					{
						log.error("Invalid character in version: " + versionString, e);
						version = null;
					}
				}
			}
			if(version == null)
				version = new int [0];
			long modDate = -1;
			String dateString = appEl.get("modified");
			if(dateString != null)
			{
				try
				{
					modDate = MOD_DATE_FORMAT.parse(dateString).getTime();
				} catch(Exception e)
				{
					log.error("Invalid modified date in XML: " + dateString
						+ "--must be in form ddMMMyyyy", e);
				}
			}
			AppConfigurator ac = new AppConfigurator(config, appEl);
			app = new PrismsApplication(theEnv, appName, descrip, version, modDate, ac);
			theApps.put(app.getName(), app);
			ac.setApp(app);
			theConfigs.put(appName, ac);
			if(manager)
			{
				if(theEnv.hasManager())
					log.error("Only one manager application can be specified");
				else
					theEnv.setManagerApp(app);
			}
			for(PrismsConfig permEl : appEl.subConfigs("permissions/permission"))
			{
				String permName = permEl.get("name");
				descrip = permEl.get("description");
				app.addPermission(new Permission(app, permName, descrip));
			}
			for(PrismsConfig clientEl : appEl.subConfigs("clients/client"))
			{
				String clientConfigXML = clientEl.get("configXML");
				if(clientConfigXML == null)
				{
					log.error("Missing configXML attribute in client of application "
						+ app.getName());
					continue;
				}
				PrismsConfig clientXml;
				try
				{
					clientXml = PrismsConfig.fromXml(theEnv, clientConfigXML, appConfigXML, path);
				} catch(IOException e)
				{
					log.error("Could not client config XML " + clientConfigXML + " of application "
						+ app.getName(), e);
					continue;
				}
				if(clientXml == null)
					continue;
				clientEl = clientXml;
				String clientName = clientEl.get("name");
				if(clientName == null)
				{
					log.error("No name element in client config XML for application " + appName);
					continue;
				}
				ClientConfig client = app.getClient(clientName);
				if(client != null)
				{
					log.error("Client " + clientName + " of application " + appName
						+ " is already loaded--will not be reconfigured");
					continue;
				}
				descrip = clientEl.get("description");
				boolean service = clientEl.is("service", false);
				boolean commonSession = clientEl.is("common-session", false);
				boolean anonymous = clientEl.is("allowAnonymous", false);
				client = new ClientConfig(app, clientName, descrip, service, commonSession,
					anonymous, ac.addClient(clientName, clientEl));
				app.addClientConfig(client);
				ac.setClient(client);

				Class<? extends RemoteEventSerializer> serializerClass;
				try
				{
					serializerClass = clientEl.getClass("serializer", RemoteEventSerializer.class);
				} catch(ClassNotFoundException e)
				{
					String msg = "Class " + clientEl.get("serializer")
						+ " not found for remote event serializer";
					log.error(msg, e);
					throw new IllegalStateException(msg, e);
				} catch(ClassCastException e)
				{
					String msg = "Class " + clientEl.get("serializer")
						+ " is not a remote event serializer";
					log.error(msg, e);
					throw new IllegalStateException(msg, e);
				}
				if(serializerClass != null)
					try
					{
						theSerializer = serializerClass.newInstance();
					} catch(Throwable e)
					{
						log.error("Could not instantiate serializer " + serializerClass.getName(),
							e);
						throw new IllegalStateException("Could not instantiate serializer "
							+ serializerClass, e);
					}
				else
					client.setSerializer(new JsonSerializer());
				String sessionTimeout = clientEl.get("session-timeout");
				if(sessionTimeout == null)
				{
					log.error("Session timeout not specified for client " + client.getName()
						+ " of application " + client.getApp().getName());
					continue;
				}
				try
				{
					client.setSessionTimeout(Long.parseLong(sessionTimeout));
				} catch(NumberFormatException e)
				{
					log.error("Session timeout \"" + sessionTimeout + "\" invalid for client "
						+ client.getName() + " of application " + app.getName()
						+ ": must be an integer");
					continue;
				}
			}
		}
		if(!theEnv.hasManager())
			throw new IllegalStateException("No manager application configured in"
				+ " DefaultApplications.xml");
	}

	private void loadDefaultUsers(PrismsConfig config, boolean init) throws PrismsException
	{
		prisms.arch.ds.ManageableUserSource us = (prisms.arch.ds.ManageableUserSource) theEnv
			.getUserSource();
		if(init)
		{
			PrismsConfig pcEl = config.subConfig("password-constraints");
			if(pcEl != null)
			{
				prisms.arch.ds.PasswordConstraints pc = us.getPasswordConstraints();
				pc.setMinCharacterLength(pcEl.getInt("length", pc.getMinCharacterLength()));
				pc.setMinUpperCase(pcEl.getInt("upper", pc.getMinUpperCase()));
				pc.setMinLowerCase(pcEl.getInt("lower", pc.getMinLowerCase()));
				pc.setMinDigits(pcEl.getInt("digit", pc.getMinDigits()));
				pc.setMinSpecialChars(pcEl.getInt("special", pc.getMinSpecialChars()));
				long durationMult = 24L * 60 * 60 * 1000;
				pc.setMaxPasswordDuration(pcEl.getInt("duration",
					(int) (pc.getMaxPasswordDuration() / durationMult))
					* durationMult);
				long changeMult = 60L * 1000;
				pc.setMinPasswordChangeInterval(pcEl.getInt("change-interval",
					(int) (pc.getMinPasswordChangeInterval() / changeMult))
					* changeMult);
				us.setPasswordConstraints(pc);
			}
		}

		for(PrismsConfig groupEl : config.subConfigs("groups/group"))
		{
			if(!init && groupEl.is("init-only", false))
				continue;
			String groupName = groupEl.get("name");
			if(groupName == null)
			{
				log.error("No name for group");
				continue;
			}
			String appName = groupEl.get("app");
			if(appName == null)
			{
				log.error("No application name for group " + groupName);
				continue;
			}
			PrismsApplication app = theApps.get(appName);
			if(app == null)
			{
				log.error("No such application " + appName + " for group " + groupName);
				continue;
			}
			UserGroup group = null;
			UserGroup [] groups = us.getGroups(app);
			for(UserGroup g : groups)
				if(g.getName().equals(groupName))
				{
					group = g;
					break;
				}
			if(group == null)
			{
				group = us.createGroup(app, groupName,
					new prisms.records.RecordsTransaction(us.getSystemUser()));
				log.debug("Created " + appName + " group " + groupName);
			}
			boolean changed = false;
			String descrip = groupEl.get("description");
			if(descrip != null && !descrip.equals(group.getDescription()))
			{
				group.setDescription(descrip);
				changed = true;
			}
			for(PrismsConfig permConfig : groupEl.subConfigs("permission"))
			{
				if(!init && permConfig.is("init-only", false))
					continue;
				String permName = permConfig.getValue();
				Permission perm = app.getPermission(permName);
				if(perm == null)
				{
					log.error("No such " + appName + " permission " + permName);
					continue;
				}
				if(!group.getPermissions().has(permName))
				{
					changed = true;
					group.getPermissions().addPermission(perm);
					log.debug("Adding permission " + permName + " to " + appName + " group "
						+ groupName);
				}
			}
			if(changed)
				us.putGroup(group, new prisms.records.RecordsTransaction(us.getSystemUser()));
		}

		for(PrismsConfig userEl : config.subConfigs("users/user"))
		{
			if(!init && userEl.is("init-only", false))
				continue;
			String userName = userEl.get("name");
			User user = us.getUser(userName);
			if(user == null)
			{
				user = us.createUser(userName,
					new prisms.records.RecordsTransaction(us.getSystemUser()));
				log.debug("Created user " + userName);
			}
			boolean changed = false;
			if(userEl.get("admin") != null)
			{
				changed = true;
				user.setAdmin(userEl.is("admin", false));
			}
			String password = userEl.get("password");
			if(password != null && us.getPassword(user, us.getHashing()) == null)
			{
				us.setPassword(user, us.getHashing().partialHash(password), true);
				log.debug("Set initial password of user " + userName);
			}
			for(PrismsConfig appAssoc : userEl.subConfigs("app-assoc"))
			{
				if(!init && appAssoc.is("init-only", false))
					continue;
				String appName = appAssoc.getValue();
				PrismsApplication app = theApps.get(appName);
				if(app == null)
				{
					log.error("No such app " + appName + " to associate user " + userName + " with");
					continue;
				}
				if(!us.canAccess(user, app))
				{
					us.setUserAccess(user, app, true,
						new prisms.records.RecordsTransaction(us.getSystemUser()));
					log.debug("Granted user " + userName + " access to application " + appName);
				}
			}
			for(PrismsConfig groupEl : userEl.subConfigs("group"))
			{
				if(!init && groupEl.is("init-only", false))
					continue;
				String groupName = groupEl.get("name");
				if(groupName == null)
				{
					log.error("No name for group to associate with user " + userName);
					continue;
				}
				String appName = groupEl.get("app");
				PrismsApplication app = theApps.get(appName);
				if(app == null)
				{
					log.error("No such app " + appName + " to associate user " + userName
						+ " with group " + groupName);
					continue;
				}
				UserGroup group = null;
				for(UserGroup g : us.getGroups(app))
					if(g.getName().equals(groupName))
					{
						group = g;
						break;
					}
				if(group == null)
				{
					log.error("No such group " + groupName + " for application " + appName
						+ " to associate user " + userName + " with");
					continue;
				}
				if(!ArrayUtils.contains(user.getGroups(), group))
				{
					changed = true;
					boolean ro = user.isReadOnly();
					user.setReadOnly(false);
					try
					{
						user.addTo(group);
					} finally
					{
						user.setReadOnly(ro);
					}
					log.debug("Associated user " + userName + " with " + appName + " group "
						+ groupName);
				}
			}
			if(userEl.get("readonly") != null)
			{
				changed = true;
				user.setReadOnly(userEl.is("readonly", false));
			}
			if(changed)
				us.putUser(user, new prisms.records.RecordsTransaction(us.getSystemUser()));
		}
	}

	PrismsSession createSession(ClientConfig client, User user,
		prisms.arch.PrismsSession.SessionMetadata md) throws PrismsException
	{
		PrismsSession ret = new PrismsSession(client.getApp(), client, user, md);
		AppConfig appConfig = theConfigs.get(client.getApp().getName()).theAppConfig;
		PrismsTransaction trans = theEnv.transact(ret, PrismsTransaction.Stage.initApp);
		try
		{
			sessionLog.info("User " + user + " logged in to application " + client.getApp()
				+ ", client " + client.getName() + ", session " + md.getID());
			client.getApp().configureSession(ret);
			appConfig.configureSession(ret);
		} catch(Throwable e)
		{
			throw new PrismsException(e.getMessage(), e);
		} finally
		{
			theEnv.finish(trans);
		}
		trans = theEnv.transact(ret, PrismsTransaction.Stage.initClient);
		try
		{
			client.configure(ret);
		} catch(Throwable e)
		{
			throw new PrismsException("Could not configure session of client " + client.getName()
				+ " of application " + client.getApp().getName() + " for user " + user, e);
		} finally
		{
			theEnv.finish(trans);
		}
		return ret;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
	{
		if("test".equals(req.getParameter("method")))
		{ // Just a test to see if the server is alive
			java.io.PrintWriter out = resp.getWriter();
			out.write("success:");
			out.write(Integer.toHexString(theEnv.hashCode()));
			out.close();
			return;
		}

		JSONArray events = new JSONArray();
		JSONObject temp;

		String rh = req.getRemoteHost();
		if(rh == null)
			rh = "null";
		ClientGovernor clientGovernor = theClientGovernors.get(rh);
		if(clientGovernor == null && theConfigProgress.theStage == ConfigStage.CONFIGURED)
		{ /* Not synchronized properly on purpose--consequences are minimal and performance is premium */
			clientGovernor = new ClientGovernor();
			theClientGovernors.put(rh, clientGovernor);
		}
		long unlockTime = clientGovernor != null ? clientGovernor.hit(rh) : -1;
		if(unlockTime >= 0)
		{
			events.add(error(ErrorCode.RequestInvalid, "Client activity constraint exceeded."
				+ " Host " + rh + " may not access this server until "
				+ PrismsUtils.TimePrecision.SECONDS.print(unlockTime, true) + " server time"));
			resp.setContentType("text/prisms-json");
			java.io.PrintWriter out = resp.getWriter();
			out.print(theSerializer.serialize(events));
			out.close();
			return;
		}

		clean();
		PrismsRequest pReq = new PrismsRequest(req, resp, getSerializer());
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
		// Configure the server if it has not been yet
		if(theConfigProgress.theStage != ConfigStage.CONFIGURED)
		{
			String error;
			try
			{
				error = configurePRISMS(req);
			} catch(Throwable e)
			{
				log.error("PRISMS configuration failed", e);
				events.add(error(ErrorCode.ServerError,
					"PRISMS configuration failed: " + e.getMessage()));
				pReq.send(events);
				return;
			}
			if(error != null)
			{
				log.error("PRISMS configuration failed: " + error);
				events.add(error(ErrorCode.ServerError, "PRISMS configuration failed: " + error));
				pReq.send(events);
				return;
			}
		}

		if(clientGovernor == null)
		{
			clientGovernor = new ClientGovernor();
			theClientGovernors.put(rh, clientGovernor);
		}

		if(theEnv != null)
		{
			checkRunaways();
			theEnv.getIDs().updateInstance();
		}

		for(PrismsAuthenticator auth : theAuthenticators)
			if(auth.recognized(pReq))
			{
				pReq.auth = auth;
				break;
			}
		if(pReq.auth == null)
		{
			events.add(error(ErrorCode.RequestInvalid, "No authentication for request"));
			pReq.send(events);
			return;
		}
		// Fill in the request with user, app, client, etc.
		temp = fillRequest(pReq);
		if(temp != null)
		{
			events.add(temp);
			pReq.send(events);
			return;
		}

		final String sessionID;
		if(pReq.client.isCommonSession())
			sessionID = pReq.user.getName() + "/" + pReq.app.getName() + "/"
				+ pReq.client.getName();
		else
			sessionID = rh;

		HttpSession httpSession = theSessions.get(sessionID);
		if(httpSession == null)
		{
			if(pReq.client.isCommonSession() || pReq.isWMS || "init".equals(pReq.serverMethod))
			{
				/* If init is being called, they're already restarting--no need to tell them again. */
				httpSession = createSession(sessionID, clientGovernor);
			}
			else
			{
				/* No session sent.  This means the server has been redeployed. */
				events.add(toEvent("restart", "message", "Server has been restarted."
					+ " Refresh required."));
				pReq.send(events);
				return;
			}
		}
		if(httpSession.getGovernor() != null && httpSession.getGovernor() != clientGovernor)
			httpSession.getGovernor().hit(null);

		// Now we check the user's credentials
		SecuritySession security = httpSession.getSecurity(pReq.auth, pReq.user);
		if(security == null)
		{
			/* If the security has not been created for the user yet or if the security session has
			 * expired, create a new one and let the security tell them what to do. */
			try
			{
				security = httpSession.createSecurity(pReq.auth, pReq.user,
					pReq.client.isCommonSession() ? null : req);
			} catch(PrismsException e)
			{
				log.error("Could not retrieve validation info", e);
				events.add(error(ErrorCode.ServerError,
					"Could not retrieve validation info: " + e.getMessage()));
				pReq.send(events);
				return;
			}
		}
		PrismsResponse pResp = security.validate(pReq);
		if(pResp.toReturn != null && pResp.toProcess == null)
		{
			/* Security says the request was not valid or else it requires some more information. */
			events.addAll(pResp.toReturn);
			pResp.toReturn = events;
			security.writeReturn(pResp.reqAuth, pReq, pResp);
			return;
		}

		// The request is valid and authenticated. Now we get or create the session.
		PrismsSessionHolder session = httpSession.getSession(pReq.user, pReq.client);
		if(session == null)
		{
			if("init".equals(pReq.serverMethod) || pReq.client.isService())
			{
				Runtime runtime = Runtime.getRuntime();
				long maxMem = runtime.maxMemory();
				if(!theEnv.isManager(pReq.app) // Don't forbid access to the manager
					&& runtime.freeMemory() + (maxMem - runtime.totalMemory()) < maxMem * 0.15f)
				{
					events.add(error(ErrorCode.ServerError,
						"Request cannot be processed due to high memory load"));
					pReq.send(events);
					return;
				}
				session = httpSession.createSession(security, pReq.client);
			}
			else
			{
				SessionEpitaph epitaph = theEpitaphs.get(sessionID + "/" + pReq.app.getName() + "/"
					+ pReq.client.getName() + "/" + pReq.user.getName());
				if(epitaph != null)
				{
					events.add(toEvent("restart", "message", epitaph.message + " Refresh to use "
						+ pReq.appName + "."));
					pReq.send(events);
				}
				else
				{
					/* Don't know what happened to the session. A couple possibilities:
					 * 1) The server was restarted, but then another page on the same client logged
					 * 		in and both page are using HTTPS. This is the more common case.
					 * 2) They haven't contacted the PRISMS server in a long time and they timed
					 * 		out a while back.
					 * We'll assume the former--the consequences of being wrong are negligible. */
					events.add(toEvent("restart", "message", "Server has been restarted."
						+ " Refresh required."));
					pReq.send(events);
				}
				return;
			}
		}

		// Session is non-null. Process the event.
		JSONArray preRet = pResp.toReturn;
		pResp = session.processEvent(pResp.reqAuth, pReq, pResp.toProcess);
		if(preRet != null)
		{
			if(pResp.toReturn == null)
				pResp.toReturn = preRet;
			else
				pResp.toReturn.addAll(preRet);
		}
		clean();
		if(pResp.toReturn != null)
		{
			events.addAll(pResp.toReturn);
			pResp.toReturn = events;
			security.writeReturn(pResp.reqAuth, pReq, pResp);
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

	/**
	 * Compares two version strings, e.g. 1.0.0 and 1.5.0
	 * 
	 * @param v1 The first version string to compare
	 * @param v2 The second version string to compare
	 * @return 1 if the first version is newer, -1 if the first version is older, 0 if the versions
	 *         are the same
	 */
	public static int compareVersions(String v1, String v2)
	{
		if(v1 == null && v2 == null)
			return 0;
		if(v1 == null)
			return -1;
		if(v2 == null)
			return 1;
		int ret = 0;
		for(int c = 0; c < v1.length() && c < v2.length(); c++)
		{
			char c1 = v1.charAt(c);
			char c2 = v2.charAt(c);
			if(c1 == c2)
			{
				if(c1 == '.' && ret != 0)
					return ret;
				continue;
			}
			if(c1 == '.')
				return -1;
			if(c2 == '.')
				return 1;
			if(ret != 0)
				continue;
			ret = c1 > c2 ? 1 : -1;
		}
		return ret;
	}

	private JSONObject fillRequest(PrismsRequest req)
	{
		if(req.appName == null)
			return error(ErrorCode.RequestInvalid, "No application specified!");
		if(req.clientName == null)
			return error(ErrorCode.RequestInvalid, "No client configuration specified!");
		req.app = theApps.get(req.appName);
		if(req.app == null)
			return error(ErrorCode.RequestInvalid, "No such application \"" + req.appName + "\"");
		if(!req.app.isConfigured())
		{
			String error;
			try
			{
				error = theConfigs.get(req.app.getName()).configureApp();
			} catch(Throwable e)
			{
				log.error("Could not configure applcation " + req.appName, e);
				return error(ErrorCode.ServerError, "Application \"" + req.appName
					+ "\" could not be configured: " + e.getMessage());
			}
			if(error != null)
				return error(ErrorCode.ServerError, "Application \"" + req.appName
					+ "\" could not be configured: " + error);
		}
		req.client = req.app.getClient(req.clientName);
		if(req.client == null)
			return error(ErrorCode.RequestInvalid, "No such client \"" + req.clientName
				+ "\" for application \"" + req.appName + "\"");
		if(!req.client.isConfigured())
		{
			String error;
			try
			{
				error = theConfigs.get(req.app.getName()).configureClient(req.client);
			} catch(Throwable e)
			{
				log.error("Could not configure client " + req.clientName + " of application "
					+ req.appName, e);
				return error(ErrorCode.ServerError,
					"Client \"" + req.clientName + "\" of application \"" + req.appName
						+ "\" could not be configured: " + e.getMessage());
			}
			if(error != null)
				return error(ErrorCode.ServerError, "Client \"" + req.clientName
					+ "\" of application \"" + req.appName + "\" could not be configured: " + error);
		}
		try
		{
			req.user = req.auth.getUser(req);
		} catch(PrismsException e)
		{
			log.error("Could not get user for request", e);
			return error(ErrorCode.ServerError, "Could not get user: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Creates a session in response to an init request
	 * 
	 * @param req The request to create the session for
	 * @return The new session
	 */
	private synchronized HttpSession createSession(String sessionID, ClientGovernor cc)
	{
		HttpSession ret = theSessions.get(sessionID);
		if(ret != null)
			return ret;
		ret = new HttpSession(sessionID, cc);
		theSessions.put(sessionID, ret);
		return ret;
	}

	void removeSession(String id)
	{
		theSessions.remove(id);
	}

	/** Disposes of application sessions that are expired */
	public void clean()
	{
		long time = System.currentTimeMillis();
		if(time - theCleanTimer < theCleanInterval)
			return;
		theCleanTimer = Long.MAX_VALUE;
		java.util.Iterator<HttpSession> sessionIter = theSessions.values().iterator();
		while(sessionIter.hasNext())
		{
			HttpSession session = sessionIter.next();
			if(session != null && session.check())
				sessionIter.remove();
		}
		java.util.Iterator<SessionEpitaph> epitaphs = theEpitaphs.values().iterator();
		while(epitaphs.hasNext())
		{
			SessionEpitaph ep = epitaphs.next();
			if(time - ep.time > WARN_EXPIRE_THRESHOLD)
				epitaphs.remove();
		}
		theCleanTimer = time;
		System.gc();
	}

	private long runawayCheckFreq;

	private long lastRunawayCheck;

	void checkRunaways()
	{
		if(runawayCheckFreq == 0)
		{
			runawayCheckFreq = theEnv.getDefaultPrintConfig().getErrorThreshold();
			if(runawayCheckFreq < 0 || runawayCheckFreq > 365L * 20 * 60 * 60 * 1000)
				runawayCheckFreq = 60000;
		}
		if(!isCheckingForRunaways)
			return;
		long now = System.currentTimeMillis();
		if(now - lastRunawayCheck < runawayCheckFreq / 5)
			return;
		lastRunawayCheck = now;
		reallyCheckRunaways(now);
	}

	static class RunawayCheck
	{
		String transID;

		long lastLogged;
	}

	private prisms.util.ResourcePool<RunawayCheck> theCheckPool;

	RunawayCheck [] theChecks;

	private synchronized void reallyCheckRunaways(long now)
	{
		if(theCheckPool == null)
		{
			theCheckPool = new prisms.util.ResourcePool<RunawayCheck>(
				new prisms.util.ResourcePool.ResourceCreator<RunawayCheck>()
				{

					public RunawayCheck createResource()
						throws prisms.util.ResourcePool.ResourceCreationException
					{
						return new RunawayCheck();
					}

					public void destroyResource(RunawayCheck resource)
					{
					}
				}, 50);
		}
		if(lastRunawayCheck > now)
			return;
		PrismsTransaction [] trans = theEnv.getActiveTransactions();
		if(theChecks == null)
			theChecks = new RunawayCheck [0];
		final ArrayUtils.ArrayAdjuster<RunawayCheck, PrismsTransaction, RuntimeException> [] adjuster;
		adjuster = new ArrayUtils.ArrayAdjuster [1];
		adjuster[0] = new ArrayUtils.ArrayAdjuster<RunawayCheck, PrismsTransaction, RuntimeException>(
			theChecks, trans, new ArrayUtils.DifferenceListener<RunawayCheck, PrismsTransaction>()
			{
				public boolean identity(RunawayCheck o1, PrismsTransaction o2)
				{
					return o1.transID.equals(o2.getID());
				}

				public RunawayCheck added(PrismsTransaction o, int mIdx, int retIdx)
				{
					adjuster[0].nullElement();
					return null;
				}

				public RunawayCheck removed(RunawayCheck o, int oIdx, int incMod, int retIdx)
				{
					theChecks = ArrayUtils.remove(theChecks, incMod);
					return null;
				}

				public RunawayCheck set(RunawayCheck o1, int idx1, int incMod,
					PrismsTransaction o2, int idx2, int retIdx)
				{
					return o1;
				}
			});
		RunawayCheck [] checks = adjuster[0].adjust();
		for(int t = 0; t < trans.length; t++)
		{
			long freq = theEnv.getDefaultPrintConfig().getErrorThreshold();
			if(freq < 0 || freq > 365L * 20 * 60 * 60 * 1000)
				freq = 60000;
			if(runawayCheckFreq != freq)
				runawayCheckFreq = freq;
			if(checks[t] != null && now - checks[t].lastLogged < freq)
				continue;
			TrackNode ct = trans[t].getTracker().getCurrentTask();
			if(ct == null)
				return;
			if(now - ct.getLatestStart() < freq)
				continue;
			TrackNode root = ct;
			while(root.getParent() != null)
				root = root.getParent();

			if(trans[t].isFinished())
				continue;
			if(checks[t] == null)
			{
				try
				{
					checks[t] = theCheckPool.getResource(false);
				} catch(prisms.util.ResourcePool.ResourceCreationException e)
				{}
				if(checks[t] == null)
					return;
				checks[t].transID = trans[t].getID();
			}
			else if(now - checks[t].lastLogged < freq)
				continue;
			StringBuilder msg = new StringBuilder();
			msg.append("Possible runaway transaction ID#");
			msg.append(trans[t].getID());
			msg.append(':');
			msg.append("\n\tTime (transaction, method): ");
			PrismsUtils.printTimeLength(now - root.getFirstStart(), msg, false);
			msg.append(", ");
			PrismsUtils.printTimeLength(now - ct.getLatestStart(), msg, false);
			msg.append("\n\tSession: ");
			msg.append(trans[t].getSession() == null ? "none" : trans[t].getSession().toString());
			msg.append("\n\tThread: ");
			msg.append(trans[t].getThread().getName());
			msg.append("\n\tStage: ");
			msg.append(trans[t].getStage());
			msg.append("\n\tTracking Data: ");
			prisms.util.ProgramTracker.PrintConfig config = new prisms.util.ProgramTracker.PrintConfig();
			config.setAccentThreshold(8);
			config.setAsync(true);
			config.setTaskDisplayThreshold(100);
			trans[t].getTracker().printData(msg, config);
			Exception ex = new Exception("Current Stack Trace:");
			ex.setStackTrace(trans[t].getThread().getStackTrace());
			log.error(msg.toString(), ex);
			checks[t].lastLogged = now;
		}
	}

	@Override
	public void destroy()
	{
		log.info("PRISMS is shutting down");
		HttpSession [] sessions = theSessions.values().toArray(new HttpSession [0]);
		theSessions.clear();
		for(HttpSession session : sessions)
			session.destroy();
		for(PrismsApplication app : theApps.values())
			app.destroy();
		getEnv().getUserSource().disconnect();
		getEnv().getIDs().destroy();
		getEnv().getLogger().disconnect();
		getEnv().getConnectionFactory().destroy();
		System.gc();
	}
}
