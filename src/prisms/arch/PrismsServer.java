/*
 * PrismsServer.java Created Apr 14, 2010 by Andrew Butler, PSL
 */
package prisms.arch;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.ds.User;
import prisms.arch.ds.UserGroup;
import prisms.arch.ds.UserSource;
import prisms.arch.wms.PrismsWmsRequest;
import prisms.arch.wms.WmsPlugin;
import prisms.util.ArrayUtils;
import prisms.util.PrismsUtils;

/**
 * This server is the root of the PRISMS architecture. It takes HTTP requests that conform to the
 * PRISMS specification and delegates them to the appropriate applications, sessions, and plugins.
 */
public class PrismsServer extends javax.servlet.http.HttpServlet
{
	static final Logger log = Logger.getLogger(PrismsServer.class);

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

	static class SessionKey
	{
	}

	static class AppConfigurator
	{
		private static class ClientConfigurator
		{
			ClientConfig theClient;

			Element theClientConfigXML;

			ClientConfigurator(Element clientConfigXML)
			{
				theClientConfigXML = clientConfigXML;
			}

			void setClient(ClientConfig client)
			{
				theClient = client;
			}

			synchronized void configureClient(AppConfig appConfig)
			{
				if(theClientConfigXML == null)
					return;
				appConfig.configureClient(theClient, theClientConfigXML);
				theClient.setConfigured(this);
				theClientConfigXML = null;
			}
		}

		PrismsApplication theApp;

		final AppConfig theAppConfig;

		Element theAppConfigXML;

		private java.util.HashMap<String, ClientConfigurator> theClients;

		AppConfigurator(AppConfig appConfig, Element appConfigXML)
		{
			theAppConfig = appConfig;
			theAppConfigXML = appConfigXML;
			theClients = new java.util.HashMap<String, ClientConfigurator>();
		}

		void setApp(PrismsApplication app)
		{
			theApp = app;
		}

		synchronized void configureApp()
		{
			if(theAppConfigXML == null)
				return;
			theAppConfig.configureApp(theApp, theAppConfigXML);
			theApp.setConfigured(this);
			theAppConfigXML = null;
		}

		Object addClient(String clientName, Element clientConfigXML)
		{
			ClientConfigurator cc = new ClientConfigurator(clientConfigXML);
			theClients.put(clientName, cc);
			return cc;
		}

		void setClient(ClientConfig client)
		{
			theClients.get(client.getName()).setClient(client);
		}

		void configureClient(ClientConfig client)
		{
			theClients.get(client.getName()).configureClient(theAppConfig);
		}
	}

	static class PrismsRequest
	{
		final HttpServletRequest theRequest;

		final HttpServletResponse theResponse;

		final RemoteEventSerializer theSerializer;

		String sessionID;

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
			theRequest = req;
			theResponse = resp;
			theSerializer = serializer;
			sessionID = req.getParameter("sessionID");
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
			out.print(theSerializer.serialize(events));
			out.close();
		}
	}

	static class PrismsResponse
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

		private final PrismsAuthenticator theAuth;

		private final User theUser;

		private Object theAuthInfo;

		private String [] theAccessApps;

		private User theAnonymousUser;

		private long userLastChecked;

		private long theCreationTime;

		private long theLastUsed;

		SecuritySession(HttpSession session, PrismsAuthenticator auth, User user)
			throws PrismsException
		{
			theHttpSession = session;
			theAuth = auth;
			theUser = user;
			theAccessApps = new String [0];
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
			if(time - userLastChecked < 30000)
				return;
			userLastChecked = time;
			theAnonymousUser = getEnv().getUserSource().getUser(null);
			theAccessApps = new String [0];
		}

		/**
		 * Screens a request to this session, making sure the request
		 * 
		 * @param req The request to be validated
		 * @return The response to be processed after validation.
		 */
		public PrismsResponse validate(PrismsRequest req)
		{
			if(theAuthInfo == null)
			{
				try
				{
					theAuthInfo = theAuth.createAuthenticationInfo(req.theRequest, theUser);
				} catch(PrismsException e)
				{
					log.error("Could not initialize authentication", e);
					return error(req, ErrorCode.ServerError,
						"Could not initialize authentication: " + e.getMessage(), false);
				}
			}

			if(req.user.equals(theAnonymousUser) && !req.client.allowsAnonymous())
			{
				JSONObject login;
				try
				{
					login = theAuth.requestLogin(req.theRequest, theAuthInfo);
				} catch(PrismsException e)
				{
					log.error("Could not send login information", e);
					return error(req, ErrorCode.ServerError, "Could not send login information: "
						+ e.getMessage(), false);
				}
				if(login == null)
					return error(req, ErrorCode.ServerError,
						"Anonymous access forbidden for client " + req.client.getName()
							+ " of application " + req.app.getName(), false);
				else
					return sendLogin(req,
						"Anonymous access forbidden for client " + req.client.getName()
							+ " of application " + req.app.getName() + ".  Please log in.",
						"init".equals(req.serverMethod), true);
			}
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
			if(!ArrayUtils.contains(theAccessApps, req.appName))
			{
				try
				{
					if(!getEnv().getUserSource().canAccess(theUser, req.app))
						return error(
							req,
							ErrorCode.ValidationFailed,
							"User \"" + theUser.getName()
								+ "\" does not have permission to access application \""
								+ req.app.getName() + "\"", false);
				} catch(PrismsException e)
				{
					log.error("Could not determine user " + theUser + "'s access to application "
						+ req.app.getName(), e);
					return error(req, ErrorCode.ServerError,
						"Could not determine user " + theUser.getName()
							+ "'s access to application " + req.app.getName(), false);
				}
				theAccessApps = ArrayUtils.add(theAccessApps, req.appName);
			}
			try
			{
				getEnv().getUserSource().assertAccessible(theUser, req.client);
			} catch(PrismsException e)
			{
				log.error("User " + theUser + " cannot access client " + req.client.getName()
					+ " of application " + req.app.getName(), e);
				return error(req, ErrorCode.ValidationFailed, e.getMessage(), false);
			}

			Object authData;
			try
			{
				authData = theAuth.getAuthenticatedData(req.theRequest, theAuthInfo,
					theHttpSession.isSecure());
			} catch(PrismsException e)
			{
				return error(req, ErrorCode.ServerError, "Could not authenticate session", false);
			}
			if(authData instanceof PrismsAuthenticator.AuthenticationError)
			{
				PrismsAuthenticator.AuthenticationError err = (PrismsAuthenticator.AuthenticationError) authData;
				if(err.reattempt)
				{
					if(req.isWMS)
						return error(req, ErrorCode.ValidationFailed,
							"WMS does not support encryption.", false);
					else
						return sendLogin(req, err.message, "init".equals(req.serverMethod),
							err.message != null);
				}
				else
					return error(req, ErrorCode.ValidationFailed, err.message, false);
			}
			String dataStr = (String) authData;

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

			if("changePassword".equals(req.serverMethod))
			{
				PrismsAuthenticator.AuthenticationError error;
				try
				{
					error = theAuth.changePassword(req.theRequest, theAuthInfo, event);
				} catch(PrismsException e)
				{
					log.error("Could not send change password", e);
					return error(req, ErrorCode.ServerError,
						"Could not change password: " + e.getMessage(), false);
				}
				if(error != null)
				{
					JSONObject change;
					try
					{
						change = theAuth.requestPasswordChange(req.theRequest, theAuthInfo);
					} catch(PrismsException e)
					{
						log.error("Could not request password change", e);
						return error(req, ErrorCode.ServerError,
							"Could not request password change", true);
					}
					String msg = (String) change.remove("message");
					if(msg != null)
						msg = error.message + "\n" + msg;
					else
						msg = error.message;
					change.put("error", msg);
					change.put("method", "changePassword");
					return singleMessage(req, change, true);
				}
				return singleMessage(req, true, "callInit");
			}

			try
			{
				if(theAuth.needsPasswordChange(req.theRequest, theAuthInfo))
				{
					if(req.isWMS)
						return error(req, ErrorCode.ValidationFailed,
							"Password change required for user \"" + theUser.getName() + "\"",
							false);
					JSONObject change = theAuth.requestPasswordChange(req.theRequest, theAuthInfo);
					String msg = (String) change.get("message");
					if(msg != null)
						msg = "Password change required for user \"" + theUser.getName() + "\"\n"
							+ msg;
					else
						msg = "Password change required for user \"" + theUser.getName() + "\"";
					change.put("message", msg);
					change.put("method", "changePassword");
					return singleMessage(req, change, true);
				}
			} catch(PrismsException e)
			{
				log.error("Could not get password expiration", e);
				return error(req, ErrorCode.ServerError,
					"Could not get password expiration: " + e.getMessage(), true);
			}

			if("tryChangePassword".equals(req.serverMethod))
			{
				JSONObject change;
				try
				{
					change = theAuth.requestPasswordChange(req.theRequest, theAuthInfo);
				} catch(PrismsException e)
				{
					log.error("Could not request password change", e);
					return error(req, ErrorCode.ServerError, "Could not request password change",
						true);
				}
				change.put("method", "changePassword");
				return singleMessage(req, change, true);
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
			if(response.shouldEncrypt)
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

		private String encrypt(PrismsRequest request, String text) throws PrismsException
		{
			/* TODO: For whatever reason, information encrypted by the server is not decrypted
			 * correctly by the javascript dojo blowfish implementation on the HTML client. Pending
			 * more extensive investigation, we'll just send information unencrypted. */
			if(request.client.isService())
				return theAuth.encrypt(request.theRequest, text, theAuthInfo);
			return text;
		}

		private PrismsResponse sendLogin(PrismsRequest req, String error, boolean postInit,
			boolean isError)
		{
			JSONObject evt;
			try
			{
				evt = theAuth.requestLogin(req.theRequest, theAuthInfo);
			} catch(PrismsException e)
			{
				log.error("Could not send login information", e);
				return error(req, ErrorCode.ServerError,
					"Could not send login information: " + e.getMessage(), false);
			}
			if(postInit)
				evt.put("postAction", "callInit");
			if(isError)
				evt.put("code", ErrorCode.ValidationFailed.description);
			evt.put("error", error);
			return singleMessage(req, evt, false);
		}

		private PrismsResponse error(PrismsRequest req, ErrorCode code, String message,
			boolean encrypted)
		{
			if("doUpload".equals(req.serverMethod))
				throw new IllegalStateException(code + ": " + message);
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
				theAuth.destroy(theAuthInfo);
			} catch(PrismsException e)
			{
				log.error("Error destroying authentication metadata", e);
			}
		}
	}

	private class PrismsSessionHolder
	{
		private final HttpSession theHttpSession;

		private final User theUser;

		private final PrismsApplication theApp;

		private final ClientConfig theClient;

		private PrismsSession theSession;

		private final long theCreationTime;

		/**
		 * Creates a session holder
		 * 
		 * @param httpSession The HTTP session that this session holder is in
		 * @param user The user that this session is for
		 * @param client The client configuration that this session is for
		 */
		public PrismsSessionHolder(HttpSession httpSession, User user, ClientConfig client)
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
				prisms.arch.PrismsApplication.ApplicationLock lock = theApp.getApplicationLock();
				if(lock != null && lock.getLockingSession() != theSession)
					return singleMessage(req, true, "appLocked", "message", lock.getMessage(),
						"scale", new Integer(lock.getScale()), "progress",
						new Integer(lock.getProgress()));
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
				PrismsWmsRequest wmsReq = PrismsWmsRequest.parseWMS(req.theRequest
					.getParameterMap());
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
					theSession = createSession(theClient, theUser);
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
			evt.put("user", req.user.getName());
			ret.add(evt);
			ret.addAll(theSession.getEvents());
			return new PrismsResponse(ret, true);
		}

		private PrismsResponse process(JSONObject event)
		{
			JSONArray ret = null;
			if(theSession.getClient().isService()
				|| Boolean.TRUE.equals(event.get("prisms-synchronous")))
				ret = theSession.processSync(event);
			else
				ret = theSession.processAsync(event, null);
			long exp = untilExpires();
			if(exp <= WARN_EXPIRE_THRESHOLD)
			{
				JSONObject warnExpireEvent = new JSONObject();
				warnExpireEvent.put("method", "warnExpire");
				warnExpireEvent.put("expireTime", new Long(exp));
				ret.add(warnExpireEvent);
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
				return error(req, ErrorCode.ApplicationError,
					e.getClass().getName() + ": " + e.getMessage());
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
			response.setHeader("Content-Disposition",
				"attachment; filename=\"" + plugin.getFileName(event) + "\"");
			try
			{
				plugin.doDownload(event, response.getOutputStream());
			} catch(RuntimeException e)
			{
				log.error("Download failed", e);
			} catch(Error e)
			{
				log.error("Download failed", e);
			}
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

		private PrismsSessionHolder [] theSessionHolders;

		private long theLastUsed;

		HttpSession(String id, boolean secure)
		{
			theID = id;
			isSecure = secure;
			theSecurities = new SecuritySession [0];
			theSessionHolders = new PrismsSessionHolder [0];
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

		SecuritySession getSecurity(PrismsAuthenticator auth, User user)
		{
			theLastUsed = System.currentTimeMillis();
			for(SecuritySession sec : theSecurities)
				if(sec.getAuth().equals(auth) && sec.getUser().equals(user))
					return sec;
			return null;
		}

		synchronized SecuritySession createSecurity(PrismsAuthenticator auth, User user)
			throws PrismsException
		{
			for(SecuritySession sec : theSecurities)
				if(sec.getAuth().equals(auth) && sec.getUser().equals(user))
					return sec;
			SecuritySession newSec = new SecuritySession(this, auth, user);
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

		synchronized PrismsSessionHolder createSession(User user, ClientConfig client)
		{
			for(PrismsSessionHolder session : theSessionHolders)
				if(session.getUser().equals(user) && session.getClient().equals(client))
					return session;
			PrismsSessionHolder newSession = new PrismsSessionHolder(this, user, client);
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
					if(session.untilExpires() <= 0)
					{
						remove = true;
						break;
					}
			if(remove)
			{
				synchronized(this)
				{
					for(int s = 0; s < theSecurities.length; s++)
						if(theSecurities[s].untilExpires() <= 0)
						{
							theSecurities = ArrayUtils.remove(theSecurities, s);
							s--;
						}
					for(int s = 0; s < theSessionHolders.length; s++)
						if(theSessionHolders[s].untilExpires() <= 0)
						{
							PrismsSessionHolder session = theSessionHolders[s];
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
			PrismsSessionHolder [] sessions = theSessionHolders;
			theSessionHolders = new PrismsSessionHolder [0];
			for(PrismsSessionHolder session : sessions)
				session.destroy();
		}
	}

	private PrismsEnv theEnv;

	private java.util.HashMap<String, AppConfigurator> theConfigs;

	private boolean isConfigured;

	private final java.util.LinkedHashMap<String, PrismsApplication> theApps;

	private final java.util.ArrayList<PrismsAuthenticator> theAuthenticators;

	private final java.util.concurrent.ConcurrentHashMap<String, HttpSession> theSessions;

	private RemoteEventSerializer theSerializer;

	private long theCleanTimer;

	private long theCleanInterval;

	private long theSecurityTimeout;

	private long theSecurityRefresh;

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
		theSessions = new java.util.concurrent.ConcurrentHashMap<String, HttpSession>(32);
		theSerializer = new JsonSerializer();
		theCleanTimer = System.currentTimeMillis();
		theCleanInterval = 60 * 1000;
		if(initDefaultLogging)
			initLog4j(getClass().getResource("log4j.xml"));
		theApps = new java.util.LinkedHashMap<String, PrismsApplication>();
		theAuthenticators = new java.util.ArrayList<PrismsAuthenticator>();
		theConfigs = new java.util.HashMap<String, AppConfigurator>();
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

	/** @return The XML to use to configure this server */
	protected Element getConfigXML()
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
	 * @return The XML to use to configure this server's default applications. These are the
	 *         applications that come packaged with PRISMS and must exist to support the
	 *         architecture.
	 */
	protected Element getDefaultAppXML()
	{
		try
		{
			return new org.dom4j.io.SAXReader().read(
				PrismsServer.class.getResource("DefaultApplications.xml")).getRootElement();
		} catch(Exception e)
		{
			log.error("Could not read PRISMS default applications file!", e);
			throw new IllegalStateException("Could not read PRISMS default applications file", e);
		}
	}

	/**
	 * Configures this server
	 * 
	 * @throws IllegalStateException If an error occurs
	 */
	protected synchronized void configurePRISMS()
	{
		if(isConfigured)
			return;
		log.info("Configuring PRISMS...");
		Element configEl = getConfigXML();
		String configXmlRef = getClass().getResource("PRISMSConfig.xml").toString();

		Element securityEl = configEl.element("security");
		Element timeoutEl = securityEl == null ? null : securityEl.element("timeout");
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
		Element refreshEl = securityEl == null ? null : securityEl.element("refresh");
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

		PersisterFactory factory = null;
		Element persisterFactoryEl = configEl.element("persisterFactory");
		if(theEnv == null && persisterFactoryEl != null)
		{
			String className = persisterFactoryEl.elementTextTrim("class");
			try
			{
				factory = (PersisterFactory) Class.forName(className).newInstance();
			} catch(Throwable e)
			{
				log.error("Could not instantiate persister factory " + className, e);
				throw new IllegalStateException("Could not instantiate persister factory "
					+ className, e);
			}
			factory.configure(persisterFactoryEl);
		}
		if(factory == null)
			throw new IllegalStateException("No PersisterFactory set--cannot configure PRISMS");

		Element dsEl = configEl.element("datasource");
		if(dsEl == null)
		{
			log.error("No datasource element in server config");
			throw new IllegalStateException("No datasource element in server config");
		}

		UserSource userSource;
		String dsClass = dsEl.elementTextTrim("class");
		try
		{
			userSource = (UserSource) Class.forName(dsClass).newInstance();
		} catch(Exception e)
		{
			log.error("Could not instantiate data source " + dsClass + e.getMessage());
			throw new IllegalStateException("Could not instantiate data source " + dsClass, e);
		}

		theEnv = new PrismsEnv(userSource, factory);

		// Add global listeners
		Element globalListeners = configEl.element("global-listeners");
		if(globalListeners != null)
		{
			for(Element listSetEl : (java.util.List<Element>) globalListeners
				.elements("listener-set"))
			{
				String globalName = listSetEl.attributeValue("name");
				if(globalName == null)
				{
					log.error("No name attribute for listener-set");
					continue;
				}
				for(Element listEl : (java.util.List<Element>) listSetEl.elements("property"))
				{
					String mgrType = listEl.attributeValue("type");
					prisms.arch.event.PropertyManager<?> mgr;
					try
					{
						mgr = (prisms.arch.event.PropertyManager<?>) Class.forName(mgrType)
							.newInstance();
					} catch(Throwable e)
					{
						log.error("Could not instantiate manager type " + mgrType, e);
						return;
					}
					theEnv.addGlobalManager(globalName, mgr, listEl);
				}
			}
		}

		// Now we load default applications, one of which must be the manager application
		Element appXml = getDefaultAppXML();
		loadApps(appXml, PrismsServer.class.getResource("PrismsServer.class").getQuery());

		// Now we load the custom applications
		appXml = configEl.element("applications");
		if(appXml == null)
			log.warn("No custom applications found in "
				+ getClass().getResource("PRISMSConfig.xml"));
		else
			loadApps(appXml, configXmlRef);
		theEnv.setConfigured();

		PrismsApplication [] apps = theApps.values()
			.toArray(new PrismsApplication [theApps.size()]);
		// Now we can configure the user source
		try
		{
			userSource.configure(dsEl, theEnv, apps);
		} catch(Exception e)
		{
			log.error("Could not configure data source " + e.getMessage());
			throw new IllegalStateException("Could not configure data source", e);
		}
		if(userSource.getIDs().isNewInstall())
		{
			String ref = configEl.elementTextTrim("default-users");
			if(ref != null)
			{
				if(!(userSource instanceof prisms.arch.ds.ManageableUserSource))
					log.error("Cannot load default users--user source is not manageable");
				else
				{
					Element defaultUsers;
					try
					{
						defaultUsers = PrismsUtils.getRootElement(ref, configXmlRef);
						loadDefaultUsers(defaultUsers);
					} catch(java.io.IOException e)
					{
						log.error("Could not load default users", e);
					}
				}
			}
			else
				log.info("No default users to load");
		}
		for(PrismsApplication app : theApps.values())
			if(theEnv.isManager(app))
			{
				app.addManager(new prisms.util.persisters.ReadOnlyManager<PrismsApplication []>(
					app, prisms.arch.event.PrismsProperties.applications, apps));
				theConfigs.get(app.getName()).configureApp();
				log.info("Configured manager application \"" + app.getName() + "\"");
				break;
			}

		// Load authenticators
		for(Element authEl : (java.util.List<Element>) configEl.elements("authenticator"))
		{
			String authClass = authEl.elementTextTrim("class");
			if(authClass == null)
			{
				log.error("No class given for authenticator");
				continue;
			}
			PrismsAuthenticator auth;
			try
			{
				auth = (PrismsAuthenticator) Class.forName(authClass).newInstance();
			} catch(Throwable e)
			{
				log.error("Could not load authenticator class " + authClass, e);
				continue;
			}
			auth.configure(authEl, userSource, apps);
			theAuthenticators.add(auth);
		}

		// Now we configure the applications
		isConfigured = true;
		log.info("PRISMS Configured");
	}

	private void loadApps(Element appXml, String path)
	{
		for(Element appEl : (java.util.List<Element>) appXml.elements("application"))
		{
			if(!appEl.getName().equals("application"))
				continue;
			boolean manager = "true".equalsIgnoreCase(appEl.attributeValue("manager"));
			String appConfigXML = appEl.attributeValue("configXML");
			if(appConfigXML == null)
			{
				log.error("Missing configXML attribute in application");
				continue;
			}
			try
			{
				appEl = PrismsUtils.getRootElement(appConfigXML, path, null);
			} catch(IOException e)
			{
				log.error("Could not read application config XML " + appConfigXML, e);
				continue;
			}
			if(appEl == null)
				continue;
			String appName = appEl.elementTextTrim("name");
			if(appName == null)
			{
				log.error("No name element in application XML");
				continue;
			}
			PrismsApplication app = theApps.get(appName);
			if(app != null)
			{
				log.error("Application " + appName + " is already loaded--will not be reconfigured");
				continue;
			}
			AppConfig config;
			String configClass = appEl.elementTextTrim("config-class");
			if(configClass != null)
			{
				try
				{
					config = (AppConfig) Class.forName(configClass).newInstance();
				} catch(Throwable e)
				{
					log.error("Could not instantiate configuration class " + configClass
						+ " to configure application " + appName, e);
					continue;
				}
			}
			else
				config = new AppConfig();
			String descrip = appEl.elementTextTrim("description");
			int [] version = null;
			String versionString = appEl.elementTextTrim("version");
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
			String dateString = appEl.elementTextTrim("modified");
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
			for(Element permEl : (java.util.List<Element>) appEl.element("permissions").elements())
			{
				if(!permEl.getName().equals("permission"))
					continue;
				String permName = permEl.elementTextTrim("name");
				descrip = permEl.elementTextTrim("description");
				app.addPermission(new Permission(app, permName, descrip));
			}
			for(Element clientEl : (java.util.List<Element>) appEl.element("clients").elements())
			{
				if(!clientEl.getName().equals("client"))
					continue;
				String clientConfigXML = clientEl.attributeValue("configXML");
				if(clientConfigXML == null)
				{
					log.error("Missing configXML attribute in client of application "
						+ app.getName());
					continue;
				}
				try
				{
					clientEl = PrismsUtils.getRootElement(clientConfigXML, appConfigXML, path);
				} catch(IOException e)
				{
					log.error("Could not client config XML " + clientConfigXML + " of application "
						+ app.getName(), e);
					continue;
				}
				if(clientEl == null)
					continue;
				String clientName = clientEl.elementTextTrim("name");
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
				descrip = clientEl.elementTextTrim("description");
				boolean service = "true".equalsIgnoreCase(clientEl.elementTextTrim("service"));
				boolean anonymous = "true".equalsIgnoreCase(clientEl
					.elementTextTrim("allowAnonymous"));
				client = new ClientConfig(app, clientName, descrip, service, anonymous,
					ac.addClient(clientName, clientEl));
				app.addClientConfig(client);
				ac.setClient(client);

				String serializerClass = clientEl.elementTextTrim("serializer");
				if(serializerClass != null)
					try
					{
						client.setSerializer((RemoteEventSerializer) Class.forName(serializerClass)
							.newInstance());
					} catch(Throwable e)
					{
						log.error(
							"Could not create event serializer " + serializerClass + " for client "
								+ client.getName() + " of application " + app.getName(), e);
						continue;
					}
				else
					client.setSerializer(new JsonSerializer());
				String sessionTimeout = clientEl.elementTextTrim("session-timeout");
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

	private void loadDefaultUsers(Element xml) throws PrismsException
	{
		prisms.arch.ds.ManageableUserSource us = (prisms.arch.ds.ManageableUserSource) theEnv
			.getUserSource();
		Element pcEl = xml.element("password-constraints");
		if(pcEl != null)
		{
			prisms.arch.ds.PasswordConstraints pc = us.getPasswordConstraints();
			if(pcEl.elementTextTrim("length") != null)
				pc.setMinCharacterLength(Integer.parseInt(pcEl.elementTextTrim("length")));
			if(pcEl.elementTextTrim("upper") != null)
				pc.setMinUpperCase(Integer.parseInt(pcEl.elementTextTrim("upper")));
			if(pcEl.elementTextTrim("lower") != null)
				pc.setMinLowerCase(Integer.parseInt(pcEl.elementTextTrim("lower")));
			if(pcEl.elementTextTrim("digit") != null)
				pc.setMinDigits(Integer.parseInt(pcEl.elementTextTrim("digit")));
			if(pcEl.elementTextTrim("special") != null)
				pc.setMinSpecialChars(Integer.parseInt(pcEl.elementTextTrim("special")));
			if(pcEl.elementTextTrim("duration") != null)
				pc.setMaxPasswordDuration(Integer.parseInt(pcEl.elementTextTrim("duration")) * 24L
					* 60 * 60 * 1000);
			if(pcEl.elementTextTrim("pre-unique") != null)
				pc.setNumPreviousUnique(Integer.parseInt(pcEl.elementTextTrim("pre-unique")));
			if(pcEl.elementTextTrim("change-interval") != null)
				pc.setMinPasswordChangeInterval(Integer.parseInt(pcEl
					.elementTextTrim("change-interval")) * 60L * 1000);
			us.setPasswordConstraints(pc);
		}

		Element groupsEl = xml.element("groups");
		if(groupsEl != null)
			for(Element groupEl : (java.util.List<Element>) groupsEl.elements("group"))
			{
				String groupName = groupEl.attributeValue("name");
				if(groupName == null)
				{
					log.error("No name for group");
					continue;
				}
				String appName = groupEl.attributeValue("app");
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
					group = us.createGroup(app, groupName);
					log.debug("Created " + appName + " group " + groupName);
				}
				boolean changed = false;
				String descrip = groupEl.elementTextTrim("description");
				if(descrip != null && !descrip.equals(group.getDescription()))
				{
					group.setDescription(descrip);
					changed = true;
				}
				for(Element permEl : (java.util.List<Element>) groupEl.elements("permission"))
				{
					String permName = permEl.getTextTrim();
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
					us.putGroup(group);
			}

		Element usersEl = xml.element("users");
		if(usersEl != null)
			for(Element userEl : (java.util.List<Element>) usersEl.elements("user"))
			{
				String userName = userEl.attributeValue("name");
				User user = us.getUser(userName);
				if(user == null)
				{
					user = us.createUser(userName);
					log.debug("Created user " + userName);
				}
				boolean changed = false;
				if(userEl.element("admin") != null)
				{
					changed = true;
					user.setAdmin("true".equalsIgnoreCase(userEl.elementTextTrim("admin")));
				}
				Boolean readOnly=null;
				if(userEl.attributeValue("readonly") != null)
					readOnly="true".equalsIgnoreCase(userEl.attributeValue("readonly"));
				String password = userEl.elementTextTrim("password");
				if(password != null && us.getKey(user, us.getHashing()) == null)
				{
					us.setPassword(user, us.getHashing().partialHash(password), true);
					log.debug("Set initial password of user " + userName);
				}
				for(Element appAssocEl : (java.util.List<Element>) userEl.elements("app-assoc"))
				{
					String appName = appAssocEl.getTextTrim();
					PrismsApplication app = theApps.get(appName);
					if(app == null)
					{
						log.error("No such app " + appName + " to associate user " + userName
							+ " with");
						continue;
					}
					if(!us.canAccess(user, app))
					{
						us.setUserAccess(user, app, true);
						log.debug("Granted user " + userName + " access to application " + appName);
					}
				}
				for(Element groupEl : (java.util.List<Element>) userEl.elements("group"))
				{
					String groupName = groupEl.attributeValue("name");
					if(groupName == null)
					{
						log.error("No name for group to associate with user " + userName);
						continue;
					}
					String appName = groupEl.attributeValue("app");
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
						user.addTo(group);
						log.debug("Associated user " + userName + " with " + appName + " group "
							+ groupName);
					}
				}
				if(readOnly!=null)
				{
					changed = true;
					user.setReadOnly(readOnly);
				}
				if(changed)
					us.putUser(user);
			}
	}

	PrismsSession createSession(ClientConfig client, User user) throws PrismsException
	{
		PrismsSession ret = new PrismsSession(client.getApp(), client, user);
		AppConfig appConfig = theConfigs.get(client.getApp().getName()).theAppConfig;
		try
		{
			client.getApp().configureSession(ret);
			appConfig.configureSession(ret);
		} catch(Throwable e)
		{
			throw new PrismsException(e.getMessage(), e);
		}
		try
		{
			client.configure(ret);
		} catch(Throwable e)
		{
			throw new PrismsException("Could not configure session of client " + client.getName()
				+ " of application " + client.getApp().getName() + " for user " + user, e);
		}
		return ret;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
	{
		JSONArray events = new JSONArray();
		JSONObject temp;

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
		if(!isConfigured)
		{
			try
			{
				configurePRISMS();
			} catch(Throwable e)
			{
				log.error("PRISMS configuration failed", e);
				events.add(error(ErrorCode.ServerError,
					"PRISMS configuration failed: " + e.getMessage()));
				pReq.send(events);
				return;
			}
		}

		for(PrismsAuthenticator auth : theAuthenticators)
			if(auth.recognized(req))
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
		SecuritySession security = httpSession.getSecurity(pReq.auth, pReq.user);
		if(security == null)
		{
			/* If the security has not been created for the user yet or if the security session has
			 * expired, create a new one and let the security tell them what to do. */
			try
			{
				security = httpSession.createSecurity(pReq.auth, pReq.user);
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
		if(pResp.toReturn != null)
		{
			/* Security says the request was not valid or else it requires some more information. */
			events.addAll(pResp.toReturn);
			pResp.toReturn = events;
			security.writeReturn(pReq, pResp);
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
		req.app = theApps.get(req.appName);
		if(req.app == null)
			return error(ErrorCode.RequestInvalid, "No such application \"" + req.appName + "\"");
		if(!req.app.isConfigured())
		{
			try
			{
				theConfigs.get(req.app.getName()).configureApp();
			} catch(Throwable e)
			{
				log.error("Could not configure applcation " + req.appName, e);
				return error(ErrorCode.ServerError, "Application \"" + req.appName
					+ "\" could not be configured: " + e.getMessage());
			}
		}
		req.client = req.app.getClient(req.clientName);
		if(req.client == null)
			return error(ErrorCode.RequestInvalid, "No such client \"" + req.clientName
				+ "\" for application \"" + req.appName + "\"");
		if(!req.client.isConfigured())
		{
			try
			{
				theConfigs.get(req.app.getName()).configureClient(req.client);
			} catch(Throwable e)
			{
				log.error("Could not configure client " + req.clientName + " of application "
					+ req.appName, e);
				return error(ErrorCode.ServerError,
					"Client \"" + req.clientName + "\" of application \"" + req.appName
						+ "\" could not be configured: " + e.getMessage());
			}
		}
		try
		{
			// TODO Direct PRISMS call. Redirect to cache.
			req.user = req.auth.getUser(req.theRequest);
		} catch(PrismsException e)
		{
			log.error("Could not get user for request", e);
			return error(ErrorCode.ServerError, "Could not get user: " + e.getMessage());
		}
		if(req.user == null)
			return error(ErrorCode.ServerError, "Could not get user");
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
		theCleanTimer = time;
		System.gc();
	}

	@Override
	public void destroy()
	{
		log.debug("Destroying servlet");
		HttpSession [] sessions = theSessions.values().toArray(new HttpSession [0]);
		theSessions.clear();
		for(HttpSession session : sessions)
			session.destroy();
		for(PrismsApplication app : theApps.values())
			app.destroy();
		getEnv().getUserSource().disconnect();
		getEnv().getPersisterFactory().destroy();
		System.gc();
	}
}
