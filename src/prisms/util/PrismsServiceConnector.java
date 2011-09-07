/*
 * PrismsServiceConnector.java Created Feb 27, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.Lock;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.Encryption;
import prisms.arch.ds.Hashing;

/**
 * Connects to a PRISMS service. This class handles all the handshaking and protocols specific to
 * PRISMS so that any PRISMS web service can be accessed using this class (assuming the client knows
 * the required information including a password if necessary) simply by passing JSON objects.
 */
public class PrismsServiceConnector
{
	static final Logger log = Logger.getLogger(PrismsServiceConnector.class);

	/** A version indicator sent to the service--may enable version-specific code */
	public static final String PRISMS_SERVICE_VERSION = "2.1.3";

	/** The name of the system property to set the SSL handler package in */
	public static final String SSL_HANDLER_PROP = "java.protocol.handler.pkgs";

	/** The name of the system property to set the trust store location in */
	public static final String TRUST_STORE_PROP = "javax.net.ssl.trustStore";

	/** The name of the system property to set the trust store password in */
	public static final String TRUST_STORE_PWD_PROP = "javax.net.ssl.trustStorePassword";

	/** Represents a request that was rejected by the PRISMS architecture for some reason */
	public static class PrismsServiceException extends IOException
	{
		private final prisms.arch.PrismsServer.ErrorCode theErrorCode;

		private final String thePrismsMsg;

		private JSONObject theParams;

		/**
		 * Creates a PrismsServiceException
		 * 
		 * @param msg The client message
		 * @param errorCode The error code from the server
		 * @param prismsMsg The error message from the server
		 * @param params The error parameters from the servers
		 */
		public PrismsServiceException(String msg, prisms.arch.PrismsServer.ErrorCode errorCode,
			String prismsMsg, JSONObject params)
		{
			super(msg);
			theErrorCode = errorCode;
			thePrismsMsg = prismsMsg;
			theParams = params;
		}

		/** @return The error code from the server */
		public prisms.arch.PrismsServer.ErrorCode getErrorCode()
		{
			return theErrorCode;
		}

		/** @return The error message from the server */
		public String getPrismsMessage()
		{
			return thePrismsMsg;
		}

		/** @return Extra information describing the error or its cause. May be null. */
		public JSONObject getParams()
		{
			return theParams;
		}
	}

	/** Thrown when the user name/password combination fails to validate with the server */
	public static class AuthenticationFailedException extends IOException
	{
		/** @see IOException#IOException(String) */
		public AuthenticationFailedException(String s)
		{
			super(s);
		}
	}

	/** Automatically generates a new password when one expires */
	public static interface PasswordChanger
	{
		/**
		 * Creates a new password
		 * 
		 * @param message The message to use to create the new password (user interface display)
		 * @param constraints The constraints that the password must meet
		 * @return The new password to use
		 */
		String getNewPassword(String message, prisms.arch.ds.PasswordConstraints constraints);

		/**
		 * Called when the password is changed externally (not as a result of the connector's
		 * session). This allows the calling software the opportunity to query or generate the
		 * password that was set.
		 * 
		 * @param setTime The time at which the password was set
		 * @return The new password to use for encryption
		 */
		String getChangedPassword(long setTime);
	}

	/** The different server methods that may be used */
	static enum ServerMethod
	{
		/** Requests server validation */
		validate,
		/** Requests a password change */
		changePassword,
		/** Initializes the session on the server */
		init,
		/** Retrieves version information on the application */
		getVersion,
		/** Processes a client-generated event */
		processEvent,
		/** Returns an image */
		generateImage,
		/** Returns a stream of data */
		getDownload,
		/** Uploads data to the service */
		doUpload
	}

	/** Used when client validation is requested by the server */
	public static interface Validator
	{
		/**
		 * Called when client validation is requested by the server
		 * 
		 * @param validationInfo The validation information sent by the server
		 * @return Validation information to be used by the server to validate the client
		 */
		JSONObject validate(JSONObject validationInfo);
	}

	/** Allows a remote method with a return value to be called asynchronously */
	public static interface AsyncReturn
	{
		/**
		 * Called when the method finishes successfully
		 * 
		 * @param returnVal The return value of the method
		 */
		void doReturn(JSONObject returnVal);

		/**
		 * Called when an error occurs invoking the remote method
		 * 
		 * @param e The exception that occurred
		 */
		void doError(IOException e);
	}

	/** Allows the service to be called asynchronously for multiple return values */
	public static interface AsyncReturns
	{
		/**
		 * Called when the service call finishes successfully
		 * 
		 * @param returnVals The return values of the service call
		 */
		void doReturn(JSONArray returnVals);

		/**
		 * Called when an error occurs invoking the service
		 * 
		 * @param e The exception that occurred
		 */
		void doError(IOException e);
	}

	/** A structure that holds version information returned from the service */
	public static class VersionInfo
	{
		/** The application-specific version numbers */
		public final int [] version;

		/** The date of the most recent modification to the application */
		public final long modified;

		/**
		 * Creates the version info structure
		 * 
		 * @param v The version numbers
		 * @param m The modification date
		 */
		public VersionInfo(int [] v, long m)
		{
			version = v;
			modified = m;
		}
	}

	private static class ResponseStream
	{
		final java.io.InputStream input;

		final Encryption encryption;

		ResponseStream(java.io.InputStream in, Encryption enc)
		{
			input = in;
			encryption = enc;
		}
	}

	private static class SecurityRetriever implements javax.net.ssl.X509TrustManager
	{
		private java.security.cert.X509Certificate[] theCerts;

		SecurityRetriever()
		{
		}

		public java.security.cert.X509Certificate[] getAcceptedIssuers()
		{
			return new java.security.cert.X509Certificate [0];
		}

		public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType)
		{
		}

		public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType)
		{
			theCerts = certs;
		}

		java.security.cert.X509Certificate[] getCerts()
		{
			return theCerts;
		}
	}

	private boolean isPost;

	private final String theServiceURL;

	private final String theAppName;

	private final String theServiceName;

	private final String theUserName;

	private prisms.arch.PrismsEnv theEnv;

	private prisms.arch.Worker theWorker;

	private java.util.concurrent.locks.ReentrantReadWriteLock theCredentialLock;

	private Encryption theEncryption;

	private String theSessionID;

	private String thePassword;

	private boolean tryEncryptionAgain;

	private PasswordChanger thePasswordChanger;

	private prisms.arch.ds.UserSource theUserSource;

	private javax.net.ssl.X509TrustManager theTrustManager;

	private javax.net.ssl.SSLSocketFactory theSocketFactory;

	private boolean logRequestsResponses;

	/**
	 * Creates a connector
	 * 
	 * @param serviceURL The base URL of the service
	 * @param appName The name of the application to access
	 * @param serviceName The name of the service configuration to access
	 * @param userName The name of the user to connect as
	 */
	public PrismsServiceConnector(String serviceURL, String appName, String serviceName,
		String userName)
	{
		theServiceURL = serviceURL;
		theAppName = appName;
		theServiceName = serviceName;
		theUserName = userName;
		isPost = true;
		theCredentialLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
		theWorker = new prisms.impl.ThreadPoolWorker("PRISMS Service Connector: " + serviceURL,
			Runtime.getRuntime().availableProcessors());
	}

	/**
	 * Sets the password to use for encryption if it is requested by the server. This takes
	 * precedence over the user source if it is set.
	 * 
	 * @param password The encryption password to use
	 */
	public void setPassword(String password)
	{
		Lock lock = theCredentialLock.writeLock();
		lock.lock();
		try
		{
			thePassword = password;
			theEncryption = null;
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @param changer The password changer to use in case a password change is required while using
	 *        the service
	 */
	public void setPasswordChanger(PasswordChanger changer)
	{
		thePasswordChanger = changer;
	}

	/**
	 * Sets the user source for encryption information if it is requested by the server
	 * 
	 * @param source The user source to get encryption information from
	 */
	public void setUserSource(prisms.arch.ds.UserSource source)
	{
		Lock lock = theCredentialLock.writeLock();
		lock.lock();
		try
		{
			theUserSource = source;
			theEncryption = null;
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @return The PRISMS environment that this connector uses to make transactions with its
	 *         asynchronous server calls
	 */
	public prisms.arch.PrismsEnv getEnv()
	{
		return theEnv;
	}

	/**
	 * <p>
	 * Sets the PRISMS environment that this connector will use to make transactions when
	 * asynchronous server calls are made. Without this field set, connections will be made without
	 * PRISMS transactions. With it set, transactions will be created for each connection made,
	 * resulting in enhanced debug information being made available.
	 * </p>
	 * <p>
	 * It is not necessary to set this field, as of course would not be possible in an environment
	 * outside the PRISMS architecture. This field is only used if asynchronous service calls are
	 * used.
	 * </p>
	 * 
	 * @param env The PRISMS environment to use for transactions
	 */
	public void setEnv(prisms.arch.PrismsEnv env)
	{
		theEnv = env;
	}

	/** @return The user name that this connector is using to log into the service */
	public String getUserName()
	{
		return theUserName;
	}

	/**
	 * @return The worker that this connector uses for asynchronous requests. It is an instance of
	 *         {@link prisms.impl.ThreadPoolWorker} by default
	 */
	public prisms.arch.Worker getWorker()
	{
		return theWorker;
	}

	/** @param worker the worker that this connector will use for asynchronous requests. */
	public void setWorker(prisms.arch.Worker worker)
	{
		if(worker == null)
			throw new NullPointerException();
		theWorker = worker;
	}

	/**
	 * Sets the security parameters for a HTTPS connections in general
	 * 
	 * @param handlerPkg The handler package for the HTTPS protocol
	 * @param provider The HTTPS security provider
	 */
	public static void setGlobalSecurityInfo(String handlerPkg, java.security.Provider provider)
	{
		if(handlerPkg != null)
		{
			String handlers = System.getProperty(SSL_HANDLER_PROP);
			if(handlers == null || !handlers.contains(handlerPkg))
				System.setProperty(SSL_HANDLER_PROP, handlers + "|" + handlerPkg);
		}
		if(provider != null)
			java.security.Security.addProvider(provider);
	}

	/**
	 * Sets the trust manager that this connector uses with HTTPS connections
	 * 
	 * @param trustManager The trust manager to validate HTTPS connections
	 * @throws NoSuchAlgorithmException If the "SSL" algorithm cannot be found in the environment
	 * @throws KeyManagementException If the SSL context cannot be initialized with the given trust
	 *         manager
	 */
	public void setTrustManager(javax.net.ssl.X509TrustManager trustManager)
		throws NoSuchAlgorithmException, KeyManagementException
	{
		theTrustManager = trustManager;
		javax.net.ssl.SSLContext sc;
		sc = javax.net.ssl.SSLContext.getInstance("SSL");
		sc.init(null, new javax.net.ssl.TrustManager [] {theTrustManager},
			new java.security.SecureRandom());
		theSocketFactory = sc.getSocketFactory();
	}

	/**
	 * Sets whether this connector logs every request and response it sends to Log4j. The logs are
	 * made with {@link org.apache.log4j.Level#INFO} priority. If this connector is used by multiple
	 * threads or if asynchronous communication is used, requests/responses will be logged in the
	 * order they are received, which may make matching the requests and responses difficult.
	 * 
	 * @param logRR Whether this connector should log its transactions to Log4j
	 */
	public void setLogRequestsResponses(boolean logRR)
	{
		logRequestsResponses = logRR;
	}

	/**
	 * Sets the HTTP method used to send data to the server
	 * 
	 * @param post true if the POST method is to be used; false if GET is to be used
	 */
	public void setPost(boolean post)
	{
		isPost = post;
	}

	/**
	 * Initializes communication with the server, securing the connection with encryption if
	 * necessary. This should be called (and the call completed) prior to using the service methods
	 * only if any asynchronous communication will be used. If only synchronous communication is
	 * used, calling this method is not necessary. But it may be used to initialize the server for
	 * quicker initial communication or as a ping to see if the service is still running and this
	 * connector is still connected.
	 * 
	 * @throws AuthenticationFailedException If this connector is unable to log in to the PRISMS
	 *         server
	 * @throws PrismsServiceException If a PRISMS error occurs
	 * @throws IOException If this service connector is unable to initialize communication with the
	 *         service for any other reason
	 */
	public void init() throws AuthenticationFailedException, PrismsServiceException, IOException
	{
		JSONObject initObject = new JSONObject();
		initObject.put("iAm", "whoIsayIam");
		initObject.put("youCan", "validateMeNow");
		callServer(ServerMethod.init, initObject);
	}

	/**
	 * Queries the service for version information on the application that this connector accesses
	 * 
	 * @return The version information for the given application
	 * @throws AuthenticationFailedException If this connector is unable to log in to the PRISMS
	 *         server
	 * @throws PrismsServiceException If a PRISMS error occurs
	 * @throws IOException If this service connector is unable to retrieve the information
	 */
	public VersionInfo getVersion() throws AuthenticationFailedException, PrismsServiceException,
		IOException
	{
		JSONArray retArray;
		prisms.util.ProgramTracker.TrackNode track = null;
		if(theEnv != null)
			track = PrismsUtils.track(theEnv, "PRISMS connect");
		try
		{
			retArray = callServer(ServerMethod.getVersion, new JSONObject());
		} finally
		{
			if(theEnv != null)
				PrismsUtils.end(theEnv, track);
		}
		if(retArray.size() == 0)
			throw new IOException("Error interfacing with server: No result returned: " + retArray);
		if(retArray.size() > 1)
			throw new IOException("Error interfacing with server: Multiple results returned: "
				+ retArray);
		JSONObject jsonVersion = (JSONObject) retArray.get(0);
		if(!"setVersion".equals(jsonVersion.get("method")))
			throw new IOException(
				"Error interfacing with server: Returned result is not version info: " + retArray);
		JSONArray jsonV = (JSONArray) jsonVersion.get("version");
		int [] v = new int [jsonV.size()];
		for(int i = 0; i < v.length; i++)
			v[i] = ((Number) jsonV.get(i)).intValue();
		return new VersionInfo(v, ((Number) jsonVersion.get("modified")).longValue());
	}

	/**
	 * Calls the service expecting a return value
	 * 
	 * @param plugin The plugin to call
	 * @param method The method to call
	 * @param params The parameters to send to the method
	 * @return The method's result
	 * @throws AuthenticationFailedException If this connector is unable to log in to the PRISMS
	 *         server
	 * @throws PrismsServiceException If a PRISMS error occurs
	 * @throws IOException If any other problem occurs calling the server
	 */
	public JSONObject getResult(String plugin, String method, Object... params) throws IOException
	{
		JSONObject rEventProps;
		prisms.util.ProgramTracker.TrackNode track = null;
		if(theEnv != null)
			track = PrismsUtils.track(theEnv, "PRISMS connect");
		try
		{
			rEventProps = PrismsUtils.rEventProps(params);
		} finally
		{
			if(theEnv != null)
				PrismsUtils.end(theEnv, track);
		}
		if(rEventProps == null)
			rEventProps = new JSONObject();
		return getResult(plugin, method, rEventProps);
	}

	/**
	 * Calls the service expecting a return value
	 * 
	 * @param plugin The plugin to call
	 * @param method The method to call
	 * @param params The parameters to send to the method
	 * @return The method's result
	 * @throws AuthenticationFailedException If this connector is unable to log in to the PRISMS
	 *         server
	 * @throws PrismsServiceException If a PRISMS error occurs
	 * @throws IOException If any other problem occurs calling the server
	 */
	public JSONObject getResult(String plugin, String method, JSONObject params) throws IOException
	{
		params.put("plugin", plugin);
		params.put("method", method);
		JSONArray serverReturn;
		prisms.util.ProgramTracker.TrackNode track = null;
		if(theEnv != null)
			track = PrismsUtils.track(theEnv, "PRISMS connect");
		try
		{
			serverReturn = callServer(ServerMethod.processEvent, params);
		} finally
		{
			if(theEnv != null)
				PrismsUtils.end(theEnv, track);
		}
		if(serverReturn.size() == 0)
			throw new IOException("Error interfacing with server: No result returned: "
				+ serverReturn);
		JSONObject ret = (JSONObject) serverReturn.get(serverReturn.size() - 1);
		return ret;
	}

	/**
	 * Calls the service where any number of return values may be expected
	 * 
	 * @param plugin The plugin to call
	 * @param method The method to call
	 * @param params The parameters to send to the method
	 * @return The method's results
	 * @throws AuthenticationFailedException If this connector is unable to log in to the PRISMS
	 *         server
	 * @throws PrismsServiceException If a PRISMS error occurs
	 * @throws IOException If any other problem occurs calling the server
	 */
	public JSONArray getResults(String plugin, String method, Object... params) throws IOException
	{
		JSONObject rEventProps = PrismsUtils.rEventProps(params);
		if(rEventProps == null)
			rEventProps = new JSONObject();
		rEventProps.put("plugin", plugin);
		rEventProps.put("method", method);
		prisms.util.ProgramTracker.TrackNode track = null;
		if(theEnv != null)
			track = PrismsUtils.track(theEnv, "PRISMS connect");
		try
		{
			return callServer(ServerMethod.processEvent, rEventProps);
		} finally
		{
			if(theEnv != null)
				PrismsUtils.end(theEnv, track);
		}
	}

	/**
	 * Calls the service expecting no return value
	 * 
	 * @param plugin The plugin to call
	 * @param method The method to call
	 * @param sync Whether the call is to synchronous (will not return until the procedure is
	 *        completed on the server) or asynchronous (returns immediately and lets the procedure
	 *        run)
	 * @param params The parameters to send to the method
	 * @throws AuthenticationFailedException If this connector is unable to log in to the PRISMS
	 *         server
	 * @throws PrismsServiceException If a PRISMS error occurs
	 * @throws IOException If any other problem occurs calling the server
	 */
	public void callProcedure(String plugin, String method, final boolean sync, Object... params)
		throws IOException
	{
		final JSONObject rEventProps;
		{
			JSONObject propTemp = PrismsUtils.rEventProps(params);
			if(propTemp == null)
				propTemp = new JSONObject();
			rEventProps = propTemp;
		}
		rEventProps.put("plugin", plugin);
		rEventProps.put("method", method);
		final IOException [] thrown = new IOException [1];
		final Exception [] outerStack = new Exception [1];
		final prisms.arch.PrismsTransaction.Stage[] stage = new prisms.arch.PrismsTransaction.Stage [1];
		final prisms.arch.PrismsApplication[] app = new prisms.arch.PrismsApplication [1];
		final prisms.arch.PrismsSession[] session = new prisms.arch.PrismsSession [1];
		Runnable run = new Runnable()
		{
			public void run()
			{
				prisms.arch.PrismsTransaction trans = null;
				if(getEnv() != null && session[0] != null)
					trans = getEnv().transact(session[0], stage[0]);
				else if(app[0] != null)
					trans = getEnv().transact(app[0]);
				prisms.util.ProgramTracker.TrackNode track = null;
				if(getEnv() != null)
					track = PrismsUtils.track(getEnv(), "PRISMS connect");
				try
				{
					callServer(ServerMethod.processEvent, rEventProps);
				} catch(IOException e)
				{
					if(sync)
						thrown[0] = e;
					else
					{
						e.setStackTrace(PrismsUtils.patchStackTraces(e.getStackTrace(),
							outerStack[0].getStackTrace(), getClass().getName(), "run"));
						log.error("Remote procedure call threw exception", e);
					}
				} finally
				{
					if(getEnv() != null)
						PrismsUtils.end(getEnv(), track);
					if(trans != null)
						getEnv().finish(trans);
				}
			}
		};
		if(sync)
		{
			prisms.util.ProgramTracker.TrackNode track = null;
			if(theEnv != null)
				track = PrismsUtils.track(theEnv, "PRISMS connect");
			try
			{
				run.run();
			} finally
			{
				if(theEnv != null)
					PrismsUtils.end(theEnv, track);
			}
			if(thrown[0] != null)
				throw thrown[0];
		}
		else
		{
			if(theEnv != null)
			{
				prisms.arch.PrismsTransaction trans = theEnv.getTransaction();
				if(trans != null)
				{
					stage[0] = trans.getStage();
					app[0] = trans.getApp();
					session[0] = trans.getSession();
				}
			}
			outerStack[0] = new Exception();
			theWorker.run(run, new prisms.arch.Worker.ErrorListener()
			{
				public void error(Error error)
				{
					log.error("Remote procedure call threw exception", error);
				}

				public void runtime(RuntimeException ex)
				{
					log.error("Remote procedure call threw exception", ex);
				}
			});
		}
	}

	/**
	 * Calls the service asynchronously, expecting a return value
	 * 
	 * @param plugin The plugin to call
	 * @param method The method to call
	 * @param aRet The interface to receive the return value of the remote call or the error that
	 *        occurred
	 * @param params The parameters to send to the method
	 */
	public void getResultAsync(String plugin, String method, final AsyncReturn aRet,
		Object... params)
	{
		final JSONObject rEventProps;
		{
			JSONObject propTemp = PrismsUtils.rEventProps(params);
			if(propTemp == null)
				propTemp = new JSONObject();
			rEventProps = propTemp;
		}
		rEventProps.put("plugin", plugin);
		rEventProps.put("method", method);
		final Exception [] outerStack = new Exception [1];
		final prisms.arch.PrismsTransaction.Stage[] stage = new prisms.arch.PrismsTransaction.Stage [1];
		final prisms.arch.PrismsApplication[] app = new prisms.arch.PrismsApplication [1];
		final prisms.arch.PrismsSession[] session = new prisms.arch.PrismsSession [1];
		Runnable run = new Runnable()
		{
			public void run()
			{
				prisms.arch.PrismsTransaction trans = null;
				if(getEnv() != null && session[0] != null)
					trans = getEnv().transact(session[0], stage[0]);
				else if(app[0] != null)
					trans = getEnv().transact(app[0]);
				try
				{
					prisms.util.ProgramTracker.TrackNode track = null;
					if(getEnv() != null)
						track = PrismsUtils.track(getEnv(), "PRISMS connect");
					JSONArray serverReturn;
					try
					{
						serverReturn = callServer(ServerMethod.processEvent, rEventProps);
					} catch(IOException e)
					{
						e.setStackTrace(PrismsUtils.patchStackTraces(e.getStackTrace(),
							outerStack[0].getStackTrace(), getClass().getName(), "run"));
						aRet.doError(e);
						return;
					} finally
					{
						if(getEnv() != null)
							PrismsUtils.end(getEnv(), track);
					}
					if(serverReturn.size() == 0)
					{
						IOException e = new IOException(
							"Error interfacing with server: No result returned: " + serverReturn);
						e.setStackTrace(PrismsUtils.patchStackTraces(e.getStackTrace(),
							outerStack[0].getStackTrace(), getClass().getName(), "run"));
						aRet.doError(e);
						return;
					}
					JSONObject ret = (JSONObject) serverReturn.get(serverReturn.size() - 1);
					aRet.doReturn(ret);
				} finally
				{
					if(trans != null)
						getEnv().finish(trans);
				}
			}
		};
		if(theEnv != null)
		{
			prisms.arch.PrismsTransaction trans = theEnv.getTransaction();
			if(trans != null)
			{
				stage[0] = trans.getStage();
				app[0] = trans.getApp();
				session[0] = trans.getSession();
			}
		}
		outerStack[1] = new Exception();
		theWorker.run(run, new prisms.arch.Worker.ErrorListener()
		{
			public void error(Error error)
			{
				log.error("Remote service call threw exception", error);
			}

			public void runtime(RuntimeException ex)
			{
				log.error("Remote service call threw exception", ex);
			}
		});
	}

	/**
	 * Calls the service asynchronously, expecting a return value
	 * 
	 * @param plugin The plugin to call
	 * @param method The method to call
	 * @param aRet The interface to receive the return values of the service call or the error that
	 *        occurred
	 * @param params The parameters to send to the method
	 */
	public void getResultsAsync(String plugin, String method, final AsyncReturns aRet,
		Object... params)
	{
		final JSONObject rEventProps;
		{
			JSONObject propTemp = PrismsUtils.rEventProps(params);
			if(propTemp == null)
				propTemp = new JSONObject();
			rEventProps = propTemp;
		}
		rEventProps.put("plugin", plugin);
		rEventProps.put("method", method);
		final Exception [] outerStack = new Exception [1];
		final prisms.arch.PrismsTransaction.Stage[] stage = new prisms.arch.PrismsTransaction.Stage [1];
		final prisms.arch.PrismsApplication[] app = new prisms.arch.PrismsApplication [1];
		final prisms.arch.PrismsSession[] session = new prisms.arch.PrismsSession [1];
		Runnable run = new Runnable()
		{
			public void run()
			{
				prisms.arch.PrismsTransaction trans = null;
				if(getEnv() != null && session[0] != null)
					trans = getEnv().transact(session[0], stage[0]);
				else if(app[0] != null)
					trans = getEnv().transact(app[0]);
				try
				{
					prisms.util.ProgramTracker.TrackNode track = null;
					if(getEnv() != null)
						track = PrismsUtils.track(getEnv(), "PRISMS connect");
					JSONArray serverReturn;
					try
					{
						serverReturn = callServer(ServerMethod.processEvent, rEventProps);
					} catch(IOException e)
					{
						e.setStackTrace(PrismsUtils.patchStackTraces(e.getStackTrace(),
							outerStack[0].getStackTrace(), getClass().getName(), "run"));
						aRet.doError(e);
						return;
					} finally
					{
						if(getEnv() != null)
							PrismsUtils.end(getEnv(), track);
					}
					aRet.doReturn(serverReturn);
				} finally
				{
					if(trans != null)
						getEnv().finish(trans);
				}
			}
		};
		if(theEnv != null)
		{
			prisms.arch.PrismsTransaction trans = theEnv.getTransaction();
			if(trans != null)
			{
				stage[0] = trans.getStage();
				app[0] = trans.getApp();
				session[0] = trans.getSession();
			}
		}
		outerStack[0] = new Exception();
		theWorker.run(run, new prisms.arch.Worker.ErrorListener()
		{
			public void error(Error error)
			{
				log.error("Remote service call threw exception", error);
			}

			public void runtime(RuntimeException ex)
			{
				log.error("Remote service call threw exception", ex);
			}
		});
	}

	JSONArray callServer(ServerMethod serverMethod, JSONObject event) throws IOException
	{
		JSONArray serverReturn;
		serverReturn = getResult(serverMethod, event);
		JSONArray ret = new JSONArray();
		if(serverReturn == null)
			return ret;
		if(theEncryption == null)
			tryEncryptionAgain = true;
		for(int i = 0; i < serverReturn.size(); i++)
		{
			JSONObject json = (JSONObject) serverReturn.get(i);
			if(json.get("plugin") == null)
			{
				if("error".equals(json.get("method")))
				{
					log.error("service error: " + json);
					throw new PrismsServiceException("Error calling serverMethod " + serverMethod
						+ " for event " + event + ":\n" + json.get("message"),
						prisms.arch.PrismsServer.ErrorCode.fromDescrip((String) json.get("code")),
						(String) json.get("message"), (JSONObject) json.get("params"));
				}
				else if("callInit".equals(json.get("method")))
				{
					serverReturn.addAll(i + 1, callInit(event));
				}
				else if("startEncryption".equals(json.get("method")))
				{
					if(prisms.arch.PrismsServer.ErrorCode.ValidationFailed.description.equals(json
						.get("code")))
					{
						if(tryEncryptionAgain)
						{
							Lock lock = theCredentialLock.writeLock();
							lock.lock();
							try
							{
								tryEncryptionAgain = false;
								theEncryption = null;
							} finally
							{
								lock.unlock();
							}
						}
						else
							throw new AuthenticationFailedException("Invalid security info--"
								+ "encryption failed with encryption:" + theEncryption
								+ " for request " + event);
					}
					Number authID = (Number) json.get("authID");
					serverReturn.addAll(
						i + 1,
						startEncryption(
							prisms.arch.ds.Hashing.fromJson((JSONObject) json.get("hashing")),
							(JSONObject) json.get("encryption"), (String) json.get("postAction"),
							serverMethod, event, authID != null ? authID.longValue() : -1));
				}
				else if("login".equals(json.get("method")) && json.get("error") != null)
				{
					log.error("service error: " + json);
					throw new AuthenticationFailedException((String) json.get("error"));
				}
				else if("changePassword".equals(json.get("method")))
				{
					Hashing hashing = Hashing.fromJson((JSONObject) json.get("hashing"));
					prisms.arch.ds.PasswordConstraints constraints = prisms.util.PrismsUtils
						.deserializeConstraints((JSONObject) json.get("constraints"));
					String message = (String) (json.get("error") == null ? json.get("message")
						: json.get("error"));
					serverReturn.addAll(callChangePassword(hashing, constraints, message));
				}
				else if("restart".equals(json.get("method")))
				{
					theSessionID = null;
					return callServer(serverMethod, event);
				}
				else if("init".equals(json.get("method")))
				{
					// Do nothing here--connection successful
				}
				else if("setSessionID".equals(json.get("method")))
					// Used by older versions of the PRISMS server
					theSessionID = (String) json.get("sessionID");
				else
					log.warn("Server message: " + json);
			}
			else
				ret.add(json);
			serverReturn.remove(i);
			i--;
		}
		if(ret.size() > 0)
			tryEncryptionAgain = true;
		return ret;
	}

	private JSONArray getResult(ServerMethod serverMethod, JSONObject request) throws IOException
	{
		ResponseStream response = getResultStream(serverMethod, request);
		java.io.InputStreamReader reader = new java.io.InputStreamReader(response.input);
		try
		{
			StringBuilder ret = new StringBuilder();
			int read = reader.read();
			while(read >= 0)
			{
				ret.append((char) read);
				read = reader.read();
			}
			String retStr = ret.toString();
			if(response.encryption != null && isEncrypted(retStr))
			{
				retStr = response.encryption.decrypt(retStr);
				if(!retStr.startsWith("["))
					throw new IOException("Decryption failed: " + retStr);
			}
			if(logRequestsResponses)
				log.info(retStr);
			retStr = PrismsUtils.decodeUnicode(retStr);
			JSONArray retJson;
			try
			{
				retJson = (JSONArray) org.json.simple.JSONValue.parse(retStr);
			} catch(Error e)
			{
				log.error("Parsing failed", e);
				retJson = null;
			}
			if(retJson == null)
				throw new IOException("Could not parse json response:\n" + retStr);
			return retJson;
		} catch(Throwable e)
		{
			log.error("Service call failed", e);
			IOException toThrow = new IOException("Could not perform PRISMS service call: "
				+ e.getMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		} finally
		{
			reader.close();
		}
	}

	private boolean isEncrypted(String str)
	{
		return !str.startsWith("[") || !str.endsWith("]");
	}

	private ResponseStream getResultStream(ServerMethod serverMethod, JSONObject request)
		throws IOException
	{
		String dataStr;
		Encryption enc;
		Lock lock = theCredentialLock.readLock();
		lock.lock();
		try
		{
			enc = theEncryption;
			dataStr = getEncodedDataString(request);
		} finally
		{
			lock.unlock();
		}
		try
		{
			java.net.HttpURLConnection conn = getURL(serverMethod, dataStr, enc);
			conn.setRequestProperty("Accept-Encoding", "gzip");
			conn.setRequestProperty("Accept-Charset", java.nio.charset.Charset.defaultCharset()
				.name());
			conn.setRequestProperty("User-Agent", "PRISMS-Service/" + PRISMS_SERVICE_VERSION);
			if(isPost && dataStr != null)
			{
				conn.setDoOutput(true);
				conn.connect();
				java.io.OutputStreamWriter wr;
				wr = new java.io.OutputStreamWriter(conn.getOutputStream());
				wr.write("data=" + dataStr);
				wr.close();
			}
			else
				conn.connect();
			java.io.InputStream is = conn.getInputStream();
			String encoding = conn.getContentEncoding();
			if(encoding != null && encoding.equalsIgnoreCase("gzip"))
				is = new java.util.zip.GZIPInputStream(is);
			return new ResponseStream(is, enc);
		} catch(ClassCastException e)
		{
			IOException toThrow = new IOException("PRISMS Service return value was not an array: "
				+ e.getMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		} catch(Throwable e)
		{
			IOException toThrow = new IOException("Could not perform PRISMS service call to "
				+ theServiceURL + ": " + e);
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}
	}

	private java.net.HttpURLConnection getURL(ServerMethod serverMethod, String dataStr,
		Encryption enc) throws IOException
	{
		String callURL = theServiceURL;
		callURL += "?app=" + encode(theAppName);
		callURL += "&client=" + encode(theServiceName);
		callURL += "&method=" + encode(serverMethod.toString());
		callURL += "&version=" + PRISMS_SERVICE_VERSION;
		if(theSessionID != null)
			callURL += "&sessionID=" + encode(theSessionID);
		if(theUserName != null)
			callURL += "&user=" + encode(theUserName);
		if(enc != null)
			callURL += "&encrypted=true";
		if(dataStr != null && !isPost)
			callURL += "&data=" + dataStr;

		java.net.HttpURLConnection conn;
		java.net.URL url = new java.net.URL(callURL);
		conn = (java.net.HttpURLConnection) url.openConnection();
		if(conn instanceof javax.net.ssl.HttpsURLConnection && theSocketFactory != null)
		{
			javax.net.ssl.HttpsURLConnection sConn = (javax.net.ssl.HttpsURLConnection) conn;
			((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(theSocketFactory);
			sConn.setHostnameVerifier(new javax.net.ssl.HostnameVerifier()
			{
				public boolean verify(String hostname, javax.net.ssl.SSLSession session)
				{
					return true;
				}
			});
		}
		if(isPost)
			conn.setRequestMethod("POST");
		conn.setRequestProperty("Accept-Encoding", "gzip");
		conn.setRequestProperty("Accept-Charset", java.nio.charset.Charset.defaultCharset().name());
		return conn;
	}

	/**
	 * Contacts the server and retrieves the security certificate information provided by the SSL
	 * server.
	 * 
	 * @return The SSL certificates provided by the server, or null if the connection is not over
	 *         SSL
	 * @throws IOException If the connection cannot be made
	 * @throws NoSuchAlgorithmException If the "SSL" algorithm cannot be found in the environment
	 * @throws KeyManagementException If the SSL context cannot be initialized with the given trust
	 *         manager
	 */
	public java.security.cert.X509Certificate[] getServerCerts() throws IOException,
		NoSuchAlgorithmException, KeyManagementException
	{
		String callURL = theServiceURL;
		callURL += "?method=test";
		java.net.HttpURLConnection conn;
		java.net.URL url = new java.net.URL(callURL);
		conn = (java.net.HttpURLConnection) url.openConnection();
		if(conn instanceof javax.net.ssl.HttpsURLConnection)
		{
			SecurityRetriever retriever = new SecurityRetriever();
			javax.net.ssl.SSLContext sc;
			sc = javax.net.ssl.SSLContext.getInstance("SSL");
			sc.init(null, new javax.net.ssl.TrustManager [] {retriever},
				new java.security.SecureRandom());
			javax.net.ssl.HttpsURLConnection sConn = (javax.net.ssl.HttpsURLConnection) conn;
			((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(sc.getSocketFactory());
			sConn.setHostnameVerifier(new javax.net.ssl.HostnameVerifier()
			{
				public boolean verify(String hostname, javax.net.ssl.SSLSession session)
				{
					return true;
				}
			});
			conn.connect();
			return retriever.getCerts();
		}
		else
			return null;
	}

	private String getEncodedDataString(JSONObject params) throws IOException
	{
		String dataStr = null;
		if(params != null)
		{
			dataStr = params.toString();
			dataStr = PrismsUtils.encodeUnicode(dataStr);
			if(theEncryption != null)
			{
				if(dataStr.length() <= 20)
					dataStr += "-XXSERVERPADDING";
				if(logRequestsResponses)
					log.info("Calling service with data " + dataStr);
			}
			else if(logRequestsResponses)
				log.info("Calling service with data " + dataStr);
			if(theEncryption != null)
			{
				Lock lock = theCredentialLock.readLock();
				lock.lock();
				try
				{
					dataStr = theEncryption.encrypt(dataStr);
				} finally
				{
					lock.unlock();
				}
			}
			dataStr = encode(dataStr);
		}
		return dataStr;
	}

	private static String encode(String toEncode) throws IOException
	{
		try
		{
			return java.net.URLEncoder.encode(toEncode, "UTF-8");
		} catch(java.io.UnsupportedEncodingException e)
		{
			IOException toThrow = new IOException(e.getMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}
	}

	/**
	 * Gets an image from the service
	 * 
	 * @param plugin The plugin to access
	 * @param method The method to call
	 * @param params The parameters to call the method with
	 * @param format The image format
	 * @return An input stream containing the image data
	 * @throws IOException If the image cannot be retrieved
	 */
	public java.io.InputStream getImageStream(String plugin, String method, JSONObject params,
		String format) throws IOException
	{
		if(params == null)
			params = new JSONObject();
		params.put("plugin", plugin);
		params.put("method", method);
		params.put("format", format);
		return getResultStream(ServerMethod.generateImage, params).input;
	}

	/**
	 * Gets binary data from the service
	 * 
	 * @param plugin The name of the download plugin to request data from
	 * @param method The method to send to the plugin
	 * @param params The data parameters to send to the plugin
	 * @return An input stream containing the data
	 * @throws IOException If the data cannot be retrieved
	 */
	public java.io.InputStream getDownload(String plugin, String method, JSONObject params)
		throws IOException
	{
		if(params == null)
			params = new JSONObject();
		params.put("plugin", plugin);
		params.put("method", method);
		return getResultStream(ServerMethod.getDownload, params).input;
	}

	String BOUNDARY = Long.toHexString((long) (Math.random() * Long.MAX_VALUE));

	/**
	 * Uploads data to the service TODO This does not currently work
	 * 
	 * @param fileName The name of the file to send to the servlet
	 * @param mimeType The content type to send to the servlet
	 * @param plugin The name of the upload plugin to send the data to
	 * @param method The method to send to the plugin
	 * @param params The data parameters to send to the plugin
	 * @return The output stream to write the upload data to
	 * @throws IOException If an error occurs doing the upload
	 */
	public java.io.OutputStream uploadData(String fileName, String mimeType, String plugin,
		String method, Object... params) throws IOException
	{
		if(params == null)
			params = new Object [0];
		JSONObject jsonParams = PrismsUtils.rEventProps(params);
		jsonParams.put("plugin", plugin);
		jsonParams.put("method", method);
		jsonParams.put("uploadFile", fileName);
		String dataStr = getEncodedDataString(jsonParams);
		Encryption enc;
		final java.net.HttpURLConnection conn;
		Lock lock = theCredentialLock.readLock();
		lock.lock();
		try
		{
			enc = theEncryption;
			conn = getURL(ServerMethod.doUpload, dataStr, enc);
		} finally
		{
			lock.unlock();
		}
		conn.setRequestProperty("Accept-Charset", java.nio.charset.Charset.defaultCharset().name());
		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setUseCaches(false);
		conn.setDefaultUseCaches(false);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Connection", "Keep-Alive");
		// c.setRequestProperty("HTTP_REFERER", codebase);
		conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
		final java.io.OutputStream os = conn.getOutputStream();
		final java.io.Writer out = new java.io.OutputStreamWriter(os,
			java.nio.charset.Charset.defaultCharset());
		if(isPost && dataStr != null)
		{
			out.write("--");
			out.write(BOUNDARY);
			out.write("\r\n");
			// write content header
			out.write("Content-Disposition: form-data; name=\"data\";");
			out.write("\r\n");
			out.write("Content-Type: text/plain\r\nContent-Transfer-Encoding: 8bit");
			out.write("\r\n");
			out.write("\r\n");
			out.write(dataStr);
		}

		out.write("\r\n");
		out.write("--");
		out.write(BOUNDARY);
		out.write("\r\n");
		// write content header
		out.flush();
		out.write("Content-Disposition: file; name=\"Upload Data\"; filename=\"" + fileName + "\"");
		out.write("\r\n");
		if(mimeType != null)
		{
			out.write("Content-Type: " + mimeType);
			out.write("\r\n");
		}
		out.write("\r\n");
		out.flush();
		// TODO Outgoing data is not encrypted. Should it be?
		return new java.io.OutputStream()
		{
			@Override
			public void write(int b) throws IOException
			{
				os.write(b);
			}

			@Override
			public void write(byte [] b) throws IOException
			{
				os.write(b);
			}

			@Override
			public void write(byte [] b, int off, int len) throws IOException
			{
				os.write(b, off, len);
			}

			@Override
			public void flush() throws IOException
			{
				os.flush();
			}

			@Override
			public void close() throws IOException
			{
				os.flush();
				out.write("\r\n");
				out.write("--");
				out.write(BOUNDARY);
				out.write("--");
				out.write("\r\n");
				out.write("\r\n");
				out.flush();
				out.close();
				java.io.InputStream in = conn.getInputStream();
				int read = in.read();
				while(read >= 0)
					read = in.read();
				in.close();
				conn.disconnect();
			}
		};
	}

	private JSONArray callInit(JSONObject postRequest) throws IOException
	{
		getResult(ServerMethod.init, null);
		return callServer(ServerMethod.processEvent, postRequest);
	}

	private synchronized JSONArray startEncryption(Hashing hashing, JSONObject encryptionParams,
		String postAction, ServerMethod postServerMethod, JSONObject postRequest, long authID)
		throws IOException
	{
		Lock lock = theCredentialLock.writeLock();
		lock.lock();
		try
		{
			theEncryption = null;
			long [] key;
			if(theUserSource != null)
			{
				prisms.arch.ds.User user = theUserSource.getUser(theUserName);
				if(user == null)
					throw new IOException("No such user " + theUserName + " in data source");
				prisms.arch.ds.UserSource.Password pwd = theUserSource.getPassword(user, hashing);
				if(pwd == null)
					throw new IOException("No password set for user " + theUserName);
				if(authID < 0 || pwd.setTime == authID)
					key = theUserSource.getPassword(user, hashing).key;
				else
				{
					System.out.println("Changed passwords");
					key = null;
					prisms.arch.ds.UserSource.Password[] pwds = theUserSource.getOldPasswords(user,
						hashing);
					for(prisms.arch.ds.UserSource.Password p : pwds)
						if(p.setTime == authID)
						{
							key = p.key;
							break;
						}
					if(key == null)
						throw new IOException("Password set at " + PrismsUtils.print(authID)
							+ " not found");
				}
			}
			else if(authID >= 0)
			{
				if(thePasswordChanger == null)
					throw new IOException("Password changed externally and no password"
						+ " changer configured");
				thePassword = thePasswordChanger.getChangedPassword(authID);
				long [] partialHash = hashing.partialHash(thePassword);
				key = hashing.generateKey(partialHash);
			}
			else if(thePassword != null)
			{
				long [] partialHash = hashing.partialHash(thePassword);
				key = hashing.generateKey(partialHash);
			}
			else
				throw new IOException("Encryption required for access to application " + theAppName
					+ " by user " + theUserName);
			theEncryption = createEncryption((String) encryptionParams.get("type"));
			theEncryption.init(key, encryptionParams);
		} finally
		{
			lock.unlock();
		}
		if("callInit".equals(postAction))
		{
			// No need to call this--this is accomplished by the postRequest
		}
		else if(postAction != null)
			log.warn("Unrecognized postAction: " + postAction);
		if(postServerMethod != null)
			return callServer(postServerMethod, postRequest);
		else
			return null;
	}

	private Encryption createEncryption(String type)
	{
		if(type.equals("blowfish"))
			return new prisms.arch.BlowfishEncryption();
		else if(type.equals("AES"))
			return new prisms.arch.AESEncryption();
		else
			throw new IllegalArgumentException("Unrecognized encryption type: " + type);
	}

	private JSONArray callChangePassword(Hashing hashing,
		prisms.arch.ds.PasswordConstraints constraints, String message) throws IOException
	{
		if(thePasswordChanger == null)
			throw new PrismsServiceException("Password change requested",
				prisms.arch.PrismsServer.ErrorCode.ValidationFailed, message, null);
		String newPwd = null;
		do
		{
			newPwd = thePasswordChanger.getNewPassword(message, constraints);
			message = checkPassword(newPwd, constraints);
			if(message != null)
				newPwd = null;
		} while(newPwd == null);

		long [] hash = hashing.partialHash(newPwd);
		JSONArray pwdData = new JSONArray();
		for(int h = 0; h < hash.length; h++)
			pwdData.add(Long.valueOf(hash[h]));
		JSONObject changeEvt = new JSONObject();
		changeEvt.put("method", "changePassword");
		changeEvt.put("passwordData", pwdData);
		tryEncryptionAgain = true;
		thePassword = newPwd;
		JSONArray evts = new JSONArray();
		evts.add(changeEvt);
		return evts;
	}

	private String checkPassword(String pwd, prisms.arch.ds.PasswordConstraints constraints)
	{
		if(pwd.length() < constraints.getMinCharacterLength())
			return "Password must be at least " + constraints.getMinCharacterLength()
				+ " character" + (constraints.getMinCharacterLength() > 1 ? "s" : "") + " long";
		int lc = 0;
		int uc = 0;
		int dig = 0;
		int special = 0;
		for(int c = 0; c < pwd.length(); c++)
		{
			char ch = pwd.charAt(c);
			if(Character.isLowerCase(ch))
				lc++;
			else if(Character.isUpperCase(ch))
				uc++;
			else if(Character.isDigit(ch))
				dig++;
			else
				special++;
		}
		if(lc < constraints.getMinLowerCase())
			return "Password must contain at least " + constraints.getMinLowerCase()
				+ " lower-case letter" + (constraints.getMinLowerCase() > 1 ? "s" : "");
		if(lc < constraints.getMinUpperCase())
			return "Password must contain at least " + constraints.getMinUpperCase()
				+ " upper-case letter" + (constraints.getMinUpperCase() > 1 ? "s" : "");
		if(lc < constraints.getMinDigits())
			return "Password must contain at least " + constraints.getMinDigits()
				+ " numeric digit" + (constraints.getMinDigits() > 1 ? "s" : "");
		if(lc < constraints.getMinLowerCase())
			return "Password must contain at least " + constraints.getMinSpecialChars()
				+ " special character" + (constraints.getMinSpecialChars() > 1 ? "s" : "");
		return null;
	}
}
