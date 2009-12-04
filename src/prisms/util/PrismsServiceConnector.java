/**
 * PRISMSConnector.java Created Feb 27, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.ds.Hashing;

/**
 * Connects to a PRISMS service. This class handles all the handshaking and protocols specific to
 * PRISMS so that any PRISMS web service can be accessed using this class (assuming the client knows
 * the required information including a password if necessary) simply by passing JSON objects.
 */
public class PrismsServiceConnector
{
	static final Logger log = Logger.getLogger(PrismsServiceConnector.class);

	/**
	 * The different server methods that may be used
	 */
	static enum ServerMethod
	{
		/**
		 * Requests server validation
		 */
		validate,
		/**
		 * Requests a password change
		 */
		changePassword,
		/**
		 * Initializes the session on the server
		 */
		init,
		/**
		 * Processes a client-generated event
		 */
		processEvent,
		/**
		 * Returns an image
		 */
		generateImage
	}

	/**
	 * Used when client validation is requested by the server
	 */
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

	/**
	 * Allows a remote method with a return value to be called asynchronously
	 */
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

	private boolean isPost;

	private final String theServiceURL;

	private final String theAppName;

	private final String theServiceName;

	private final String theUserName;

	private Object theEncryption;

	private String thePassword;

	private prisms.arch.ds.UserSource theUserSource;

	private int theEncryptionTries;

	private Validator theValidator;

	private int theValidationTries;

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
	}

	/**
	 * Sets the password to use for encryption if it is requested by the server. This takes
	 * precedence over the user source if it is set.
	 * 
	 * @param password The encryption password to use
	 */
	public void setPassword(String password)
	{
		thePassword = password;
		theEncryptionTries = 0;
		if(theEncryption != null)
			prisms.arch.Encryption.dispose(theEncryption);
		theEncryption = null;
	}

	/**
	 * Sets the user source for encryption information if it is requested by the server
	 * 
	 * @param source The user source to get encryption information from
	 */
	public void setUserSource(prisms.arch.ds.UserSource source)
	{
		theUserSource = source;
		theEncryptionTries = 0;
		if(theEncryption != null)
			prisms.arch.Encryption.dispose(theEncryption);
		theEncryption = null;
	}

	/**
	 * The validator to use if the server requires validation
	 * 
	 * @param validator The validator to be notified when the server requests validation
	 */
	public void setValidator(Validator validator)
	{
		theValidator = validator;
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
	 * @throws IOException If this service connector is unable to initialize communication with the
	 *         service. This may result from an incorrect password among other things.
	 */
	public void init() throws IOException
	{
		getResult(ServerMethod.init, null);
	}

	/**
	 * Calls the service expecting a return value
	 * 
	 * @param plugin The plugin to call
	 * @param method The method to call
	 * @param params The parameters to send to the method
	 * @return The method's result
	 * @throws IOException If a problem occurs calling the server
	 */
	public JSONObject getResult(String plugin, String method, Object... params) throws IOException
	{
		JSONObject rEventProps = PrismsUtils.rEventProps(params);
		if(rEventProps == null)
			rEventProps = new JSONObject();
		rEventProps.put("plugin", plugin);
		rEventProps.put("method", method);
		JSONArray serverReturn = callServer(ServerMethod.processEvent, rEventProps);
		if(serverReturn.size() == 0)
			throw new IOException("Error interfacing with server: No result returned: "
				+ serverReturn);
		JSONObject ret = (JSONObject) serverReturn.get(serverReturn.size() - 1);
		return ret;
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
	 * @throws IOException If a problem occurs calling the server
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
		Runnable run = new Runnable()
		{
			public void run()
			{
				try
				{
					callServer(ServerMethod.processEvent, rEventProps);
				} catch(IOException e)
				{
					if(sync)
						thrown[0] = e;
					else
						log.error("Remote procedure call threw exception", e);
				}
			}
		};
		if(sync)
		{
			run.run();
			if(thrown[0] != null)
				throw thrown[0];
		}
		else
		{
			Thread thread = new Thread(run, "PRISMS Service Connector Thread");
			thread.setDaemon(true);
			thread.start();
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
		Runnable run = new Runnable()
		{
			public void run()
			{
				JSONArray serverReturn;
				try
				{
					serverReturn = callServer(ServerMethod.processEvent, rEventProps);
				} catch(IOException e)
				{
					aRet.doError(e);
					return;
				}
				if(serverReturn.size() == 0)
				{
					aRet.doError(new IOException(
						"Error interfacing with server: No result returned: " + serverReturn));
					return;
				}
				JSONObject ret = (JSONObject) serverReturn.get(serverReturn.size() - 1);
				aRet.doReturn(ret);
			}
		};
		Thread thread = new Thread(run, "PRISMS Service Connector Thread");
		thread.setDaemon(true);
		thread.start();
	}

	JSONArray callServer(ServerMethod serverMethod, JSONObject event) throws IOException
	{
		JSONArray serverReturn;
		serverReturn = getResult(ServerMethod.processEvent, event);
		JSONArray ret = new JSONArray();
		if(serverReturn == null)
			return ret;
		for(int i = 0; i < serverReturn.size(); i++)
		{
			JSONObject json = (JSONObject) serverReturn.get(i);
			if(json.get("plugin") == null)
			{
				if("error".equals(json.get("method")))
				{
					log.error("service error: " + json);
					throw new IOException("Error calling serverMethod " + serverMethod
						+ " for event " + event + ":\n" + json.get("message"));
				}
				else if("callInit".equals(json.get("method")))
				{
					serverReturn.addAll(i + 1, callInit(event));
				}
				else if("startEncryption".equals(json.get("method")))
				{
					serverReturn.addAll(i + 1, startEncryption(prisms.arch.ds.Hashing
						.fromJson((JSONObject) json.get("hashing")), (String) json
						.get("postAction"), event));
				}
				else if("validate".equals(json.get("method")))
				{
					serverReturn.addAll(i + 1, validate((JSONObject) json.get("params"), event));
				}
				else if("login".equals(json.get("method")) && json.get("error") != null)
				{
					log.error("service error: " + json);
					throw new IOException("Error calling serverMethod " + serverMethod
						+ " for event " + event + ":\n" + json.get("error"));
				}
				else
					log.warn("Server message: " + json);
			}
			else
				ret.add(json);
			serverReturn.remove(i);
			i--;
		}
		if(ret != null)
			return ret;
		else
			throw new IOException("Error interfacing with server: No result returned: "
				+ serverReturn);
	}

	private JSONArray getResult(ServerMethod serverMethod, JSONObject request) throws IOException
	{
		java.io.InputStream is = getResultStream(serverMethod, request);
		try
		{
			StringBuilder ret = new StringBuilder();
			java.io.InputStreamReader reader = new java.io.InputStreamReader(is);
			int read = reader.read();
			while(read >= 0)
			{
				ret.append((char) read);
				read = reader.read();
			}
			String retStr = ret.toString();
			if(logRequestsResponses)
				log.info(retStr);
			if(theEncryption != null)
				retStr = prisms.arch.Encryption.decrypt(theEncryption, retStr);
			return (JSONArray) org.json.simple.JSONValue.parse(retStr);
		} catch(Throwable e)
		{
			IOException toThrow = new IOException("Could not perform PRISMS service call: "
				+ e.getMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}
	}

	private java.io.InputStream getResultStream(ServerMethod serverMethod, JSONObject request)
		throws IOException
	{
		String callURL = theServiceURL;
		callURL += "?app=" + encode(theAppName);
		callURL += "&service=" + encode(theServiceName);
		callURL += "&method=" + encode(serverMethod.toString());
		if(theUserName != null)
			callURL += "&user=" + encode(theUserName);
		String dataStr;
		if(request == null)
			dataStr = null;
		else
			dataStr = "[" + request.toString() + "]";
		if(dataStr != null && theEncryption != null)
		{
			if(dataStr.length() <= 20)
				dataStr += "-XXSERVERPADDING";
			callURL += "&encrypted=true";
			if(logRequestsResponses)
				log.info("Calling URL " + callURL + " with data " + dataStr);
			dataStr = prisms.arch.Encryption.encrypt(theEncryption, dataStr);
		}
		else if(logRequestsResponses)
			log.info("Calling URL " + callURL + " with data " + dataStr);
		if(dataStr != null)
			dataStr = encode(dataStr);
		try
		{
			java.io.InputStream is;
			if(isPost && dataStr != null)
			{
				java.net.URL url = new java.net.URL(callURL);
				java.net.URLConnection conn = url.openConnection();
				conn.setDoOutput(true);
				java.io.OutputStreamWriter wr = new java.io.OutputStreamWriter(conn
					.getOutputStream());
				wr.write("data=" + dataStr);
				wr.flush();
				is = conn.getInputStream();
			}
			else
			{
				if(dataStr != null)
					callURL += "&data=" + dataStr;
				is = new java.net.URL(callURL).openStream();
			}
			return is;
		} catch(java.net.MalformedURLException e)
		{
			IOException toThrow = new IOException("Could not access PRISMS service: "
				+ e.getMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		} catch(java.io.IOException e)
		{
			IOException toThrow = new IOException("Could not read PRISMS service: "
				+ e.getMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		} catch(ClassCastException e)
		{
			IOException toThrow = new IOException("PRISMS Service return value was not an array: "
				+ e.getMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		} catch(Throwable e)
		{
			IOException toThrow = new IOException("Could not perform PRISMS service call: "
				+ e.getMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}
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
		return getResultStream(ServerMethod.generateImage, params);
	}

	private JSONArray callInit(JSONObject postRequest) throws IOException
	{
		getResult(ServerMethod.init, null);
		return callServer(ServerMethod.processEvent, postRequest);
	}

	private synchronized JSONArray startEncryption(Hashing hashing, String postAction,
		JSONObject postRequest) throws IOException
	{
		if(theEncryptionTries > 0)
			throw new IOException("Invalid security info--encryption failed with encryption:"
				+ theEncryption + " for request " + postRequest);
		if(theEncryption != null)
			prisms.arch.Encryption.dispose(theEncryption);
		theEncryption = null;
		String key;
		if(thePassword != null)
		{
			long [] partialHash = hashing.partialHash(thePassword);
			key = hashing.generateKey(partialHash);
		}
		else if(theUserSource != null)
		{
			prisms.arch.ds.User user = theUserSource.getUser(theUserName);
			if(user == null)
				throw new IOException("No such user " + theUserName + " in data source");
			key = theUserSource.getKey(user, hashing);
		}
		else
			throw new IOException("Encryption required for access to application " + theAppName
				+ " by user " + theUserName);
		theEncryption = prisms.arch.Encryption.getEncryption(key);
		theEncryptionTries++;
		try
		{
			if("callInit".equals(postAction))
			{
				// No need to call this--this is accomplished by the postRequest
			}
			else if(postAction != null)
				log.warn("Unrecognized postAction: " + postAction);
			if(postRequest != null)
				return callServer(ServerMethod.processEvent, postRequest);
			else
				return null;
		} finally
		{
			theEncryptionTries--;
		}
	}

	private synchronized JSONArray validate(JSONObject params, JSONObject request)
		throws IOException
	{
		if(theValidationTries > 0)
			throw new IOException("Validation failed");
		if(theValidator == null)
			throw new IOException("Validation required for access to application " + theAppName
				+ " by user " + theUserName);
		JSONObject validated = theValidator.validate(params);
		theValidationTries++;
		try
		{
			callServer(ServerMethod.validate, validated);
			if(request != null)
				return callServer(ServerMethod.processEvent, request);
			else
				return null;
		} finally
		{
			theValidationTries--;
		}
	}
}
