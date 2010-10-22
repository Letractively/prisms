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
	 * Represents a request that was rejected by the PRISMS architecture for some reason
	 */
	public static class PrismsServiceException extends IOException
	{
		private final prisms.arch.PrismsServer.ErrorCode theErrorCode;

		private final String thePrismsMsg;

		/**
		 * Creates a PrismsServiceException
		 * 
		 * @param msg The client message
		 * @param errorCode The error code from the server
		 * @param prismsMsg The error message from the server
		 */
		public PrismsServiceException(String msg, prisms.arch.PrismsServer.ErrorCode errorCode,
			String prismsMsg)
		{
			super(msg);
			theErrorCode = errorCode;
			thePrismsMsg = prismsMsg;
		}

		/**
		 * @return The error code from the server
		 */
		public prisms.arch.PrismsServer.ErrorCode getErrorCode()
		{
			return theErrorCode;
		}

		/**
		 * @return The error message from the server
		 */
		public String getPrismsMessage()
		{
			return thePrismsMsg;
		}
	}

	/**
	 * Thrown when the user name/password combination fails to validate with the server
	 */
	public static class AuthenticationFailedException extends IOException
	{
		/**
		 * @see IOException#IOException(String)
		 */
		public AuthenticationFailedException(String s)
		{
			super(s);
		}
	}

	/**
	 * Automatically generates a new password when one expires
	 */
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
	}

	/**
	 * The different server methods that may be used
	 */
	static enum ServerMethod
	{
		/** Requests server validation */
		validate,
		/** Requests a password change */
		changePassword,
		/** Initializes the session on the server */
		init,
		/** Processes a client-generated event */
		processEvent,
		/** Returns an image */
		generateImage,
		/** Returns a stream of data */
		getDownload,
		/** Uploads data to the service */
		doUpload
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

	private static class ResponseStream
	{
		final java.io.InputStream input;

		final java.nio.charset.Charset charSet;

		ResponseStream(java.io.InputStream in, java.nio.charset.Charset chars)
		{
			input = in;
			charSet = chars;
		}
	}

	private boolean isPost;

	private final String theServiceURL;

	private final String theAppName;

	private final String theServiceName;

	private final String theUserName;

	private prisms.arch.Encryption theEncryption;

	private String thePassword;

	private boolean tryEncryptionAgain;

	private PasswordChanger thePasswordChanger;

	private prisms.arch.ds.UserSource theUserSource;

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
		if(theEncryption != null)
			theEncryption.dispose();
		theEncryption = null;
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
		theUserSource = source;
		if(theEncryption != null)
			theEncryption.dispose();
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
		serverReturn = getResult(serverMethod, event);
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
					throw new PrismsServiceException("Error calling serverMethod " + serverMethod
						+ " for event " + event + ":\n" + json.get("message"),
						prisms.arch.PrismsServer.ErrorCode.fromDescrip((String) json.get("code")),
						(String) json.get("message"));
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
							tryEncryptionAgain = false;
							theEncryption = null;
						}
						else
							throw new AuthenticationFailedException("Invalid security info--"
								+ "encryption failed with encryption:" + theEncryption
								+ " for request " + event);
					}
					serverReturn.addAll(
						i + 1,
						startEncryption(
							prisms.arch.ds.Hashing.fromJson((JSONObject) json.get("hashing")),
							(JSONObject) json.get("encryption"), (String) json.get("postAction"),
							serverMethod, event));
				}
				else if("validate".equals(json.get("method")))
				{
					serverReturn.addAll(i + 1, validate((JSONObject) json.get("params"), event));
				}
				else if("login".equals(json.get("method")) && json.get("error") != null)
				{
					log.error("service error: " + json);
					throw new AuthenticationFailedException((String) json.get("error"));
				}
				else if("changePassword".equals(json.get("method")))
				{
					Hashing hashing = Hashing.fromJson((JSONObject) json.get("hashing"));
					prisms.arch.ds.PasswordConstraints constraints = prisms.arch.service.PrismsSerializer
						.deserializeConstraints((JSONObject) json.get("constraints"));
					String message = (String) (json.get("error") == null ? json.get("message")
						: json.get("error"));
					serverReturn.addAll(callChangePassword(hashing, constraints, message));
				}
				else if("restart".equals(json.get("method")))
					return callServer(serverMethod, event);
				else if("init".equals(json.get("method")))
				{
					// Do nothing here--connection successful
				}
				else
					log.warn("Server message: " + json);
			}
			else
				ret.add(json);
			serverReturn.remove(i);
			i--;
		}
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
			if(theEncryption != null && isEncrypted(retStr))
				retStr = theEncryption.decrypt(retStr, response.charSet);
			if(logRequestsResponses)
				log.info(retStr);
			return (JSONArray) org.json.simple.JSONValue.parse(retStr);
		} catch(Throwable e)
		{
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
		String dataStr = getEncodedDataString(request);
		try
		{
			java.net.HttpURLConnection conn = getURL(serverMethod, dataStr);
			conn.setRequestProperty("Accept-Encoding", "gzip");
			conn.setRequestProperty("Accept-Charset", java.nio.charset.Charset.defaultCharset()
				.name());
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
			java.nio.charset.Charset charSet = getCharset(conn);
			return new ResponseStream(is, charSet);
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

	private java.net.HttpURLConnection getURL(ServerMethod serverMethod, String dataStr)
		throws IOException
	{
		String callURL = theServiceURL;
		callURL += "?app=" + encode(theAppName);
		callURL += "&client=" + encode(theServiceName);
		callURL += "&method=" + encode(serverMethod.toString());
		if(theUserName != null)
			callURL += "&user=" + encode(theUserName);
		if(theEncryption != null)
			callURL += "&encrypted=true";
		if(dataStr != null && !isPost)
			callURL += "&data=" + dataStr;
		java.net.URL url = new java.net.URL(callURL);
		java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
		conn.setRequestProperty("Accept-Encoding", "gzip");
		conn.setRequestProperty("Accept-Charset", java.nio.charset.Charset.defaultCharset().name());
		return conn;
	}

	private String getEncodedDataString(JSONObject params) throws IOException
	{
		String dataStr = null;
		if(params != null)
		{
			dataStr = params.toString();
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
				dataStr = theEncryption.encrypt(dataStr, java.nio.charset.Charset.defaultCharset());
			dataStr = encode(dataStr);
		}
		return dataStr;
	}

	private static java.nio.charset.Charset getCharset(java.net.URLConnection conn)
	{
		String contentType = conn.getContentType();
		if(contentType == null)
			return java.nio.charset.Charset.forName("Cp1252");
		contentType = contentType.toLowerCase();
		int idx = contentType.indexOf("charset=");
		if(idx < 0)
			return java.nio.charset.Charset.forName("Cp1252");
		contentType = contentType.substring(idx + "charset=".length());
		idx = contentType.indexOf(";");
		if(idx >= 0)
			contentType = contentType.substring(0, idx);
		return java.nio.charset.Charset.forName(contentType);
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

	static String BOUNDARY = Long.toHexString((long) (Math.random() * Long.MAX_VALUE));

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
		final java.net.HttpURLConnection conn = getURL(ServerMethod.doUpload, dataStr);
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
		String postAction, ServerMethod postServerMethod, JSONObject postRequest)
		throws IOException
	{
		if(theEncryption != null)
			theEncryption.dispose();
		theEncryption = null;
		long [] key;
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
		theEncryption = createEncryption((String) encryptionParams.get("type"));
		theEncryption.init(key, encryptionParams);
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

	private prisms.arch.Encryption createEncryption(String type)
	{
		if(type.equals("blowfish"))
			return new prisms.arch.BlowfishEncryption();
		else if(type.equals("AES"))
			return new prisms.arch.AESEncryption();
		else
			throw new IllegalArgumentException("Unrecognized encryption type: " + type);
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

	private JSONArray callChangePassword(Hashing hashing,
		prisms.arch.ds.PasswordConstraints constraints, String message) throws IOException
	{
		if(thePasswordChanger == null)
			throw new PrismsServiceException("Password change requested",
				prisms.arch.PrismsServer.ErrorCode.ValidationFailed, message);
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
			pwdData.add(new Long(hash[h]));
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
		// TODO check against constraints
		return null;
	}
}
