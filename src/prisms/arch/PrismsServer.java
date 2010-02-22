/**
 * RemoteCommunicator.java Created Jul 31, 2007 by Andrew Butler, PSL
 */
package prisms.arch;

import java.util.concurrent.locks.Lock;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.ds.Hashing;
import prisms.arch.ds.User;
import prisms.arch.ds.UserSource;
import prisms.arch.wms.PrismsWmsRequest;
import prisms.arch.wms.WmsPlugin;

/**
 * The PrismsServer is a servlet that allows remote clients to initiate instances of applications.
 * This class manages the lifetime of the instances, or "sessions," and access to the sessions.
 */
public class PrismsServer extends javax.servlet.http.HttpServlet
{
	static final Logger log = Logger.getLogger(PrismsServer.class);

	/**
	 * A list of error codes that the PRISMServlet may return
	 */
	public static enum ErrorCode
	{
		/**
		 * Used when required data is missing from the request
		 */
		RequestIncomplete("Request Incomplete"),
		/**
		 * Used when the requested data cannot be retrieved
		 */
		RequestedDataUnavailable("Requested data unavailable"),
		/**
		 * Used when the request is denied for security reasons
		 */
		RequestDenied("Request Denied"),
		/**
		 * Used when the encryption used by the client is not understood for any reason
		 */
		DecryptionFailed("Decryption failed"),
		/**
		 * Used when the request fails due to an error on the server
		 */
		RequestFailed("Request failed");

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

	private UserSource theUserSource;

	private boolean isConfigured;

	java.util.HashMap<String, SessionMetadata> theSessions;

	private java.util.HashMap<Thread, SessionMetadata> theThreadSessions;

	java.util.concurrent.locks.ReentrantReadWriteLock theSessionLock;

	private Class<? extends Encryption> theEncryptionClass;

	java.util.HashMap<String, String> theEncryptionProperties;

	private RemoteEventSerializer theSerializer;

	private PersisterFactory thePersisterFactory;

	private long theCleanTimer;

	private long theCleanInterval;

	int theLoginFailTolerance = 3;

	long theUserCheckPeriod = 60000;

	/**
	 * Creates a PRISMS Server
	 */
	public PrismsServer()
	{
		this(PrismsServer.class.getResource("log4j.xml"));
	}

	/**
	 * Creates a PRISMS Server
	 * 
	 * @param log4jXML The address to the XML to use to initialize Log4j, or null if this server
	 *        should not initialize Log4j
	 */
	public PrismsServer(java.net.URL log4jXML)
	{
		if(log4jXML != null)
			initLog4j(log4jXML);

		theSessions = new java.util.HashMap<String, SessionMetadata>();
		theThreadSessions = new java.util.HashMap<Thread, SessionMetadata>();
		theSessionLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
		theSerializer = new JsonSerializer();
		theCleanTimer = System.currentTimeMillis();
		theCleanInterval = 60 * 1000;
	}

	/**
	 * Creates a PRISMS Server
	 * 
	 * @param log4jprops The properties used to initialize Log4j, or null if this server should not
	 *        initialize Log4j
	 */
	public PrismsServer(java.util.Properties log4jprops)
	{
		this((java.net.URL) null);
		if(log4jprops != null)
			initLog4j(log4jprops);
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
	 * @return Whether the configuration succeeded
	 * @throws IllegalStateException If an error occurs
	 */
	protected boolean configurePRISMS()
	{
		log.info("Configuring PRISMS...");
		org.dom4j.Element configEl = getConfigXML();

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
			thePersisterFactory.configure(this, persisterFactoryEl);
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
		return true;
	}

	/**
	 * @return This server's user source
	 */
	public UserSource getUserSource()
	{
		return theUserSource;
	}

	private String newSessionID()
	{
		String ret;
		Lock lock = theSessionLock.readLock();
		lock.lock();
		try
		{
			do
			{
				ret = Integer
					.toHexString((int) ((Math.random()
						* ((double) Integer.MAX_VALUE - (double) Integer.MIN_VALUE) + Integer.MIN_VALUE)));
			} while(theSessions.containsKey(ret));
		} finally
		{
			lock.unlock();
		}
		return ret;
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
		Lock lock = theSessionLock.writeLock();
		lock.lock();
		try
		{
			java.util.Iterator<SessionMetadata> sessionIter = theSessions.values().iterator();
			while(sessionIter.hasNext())
			{
				PrismsSession session = sessionIter.next().getSession();
				if(session != null && session.isExpired())
				{
					sessionIter.remove();
					session.destroy();
				}
			}
		} finally
		{
			lock.unlock();
		}
		theCleanTimer = time;
	}

	@Override
	protected void doGet(javax.servlet.http.HttpServletRequest req,
		javax.servlet.http.HttpServletResponse resp) throws javax.servlet.ServletException,
		java.io.IOException
	{
		if(!isConfigured)
		{
			log.info("Not yet configured! Configuring...");
			isConfigured = configurePRISMS();
			log.info("... Done Configuring.");
		}
		JSONArray ret = new JSONArray();
		String errorString = null;
		ErrorCode errorCode = null;
		String sessionID = req.getParameter("sessionID");
		if(sessionID == null && req.getCookies() != null)
		{
			for(javax.servlet.http.Cookie cookie : req.getCookies())
				if(cookie.getName().equals("PRISMSsessionID"))
				{
					sessionID = cookie.getValue();
					JSONObject evt = new JSONObject();
					evt.put("method", "setSessionID");
					evt.put("sessionID", sessionID);
					ret.add(evt);
					break;
				}
		}

		String appName = req.getParameter("app");

		String userName = req.getParameter("user");
		if("null".equals(userName))
			userName = null;
		String clientName = req.getParameter("client");
		String serviceName = req.getParameter("service");
		String clientServiceName = clientName;
		if(clientName == null)
			clientServiceName = serviceName;
		boolean encrypted = "true".equalsIgnoreCase(req.getParameter("encrypted"));
		String method = req.getParameter("method");
		String dataStr = req.getParameter("data");

		if(PrismsWmsRequest.isWMS(req))
		{
			/*
			 * Some WMS clients don't support multiple non-WMS parameters, so we have to account for
			 * that as best we can. The client and method names can be assumed to be "WMS" and the
			 * application name can be stored within the unencrypted data parameter.
			 */
			if(method == null)
				method = "WMS";
			if(clientName == null && serviceName == null)
				serviceName = "WMS";
			if((appName == null || userName == null) && dataStr != null)
			{
				JSONArray data;
				try
				{
					data = new JsonSerializer().deserialize(dataStr);
					if(appName == null)
						appName = (String) ((JSONObject) data.get(0)).get("app");
					if(userName == null)
						userName = (String) ((JSONObject) data.get(0)).get("user");
				} catch(Exception e)
				{
					log.warn("Couldn't retrieve necessary parameters from data");
				}
			}
			if(dataStr == null && !encrypted)
				dataStr = "{\"serverPadding\":\"padding\"}";
		}

		String sessionKey;
		SessionMetadata session = null;
		if(sessionID != null && userName != null && appName != null
			&& (serviceName != null || clientName != null))
		{
			sessionKey = sessionID + "/" + appName + "/"
				+ (serviceName != null ? serviceName : clientName) + "/" + userName;
			session = theSessions.get(sessionKey);
		}

		User user;
		PrismsApplication app;
		User appUser;
		ClientConfig client;
		if(session != null)
		{
			try
			{
				user = session.getUser();
				if(user.getApp() != null)
					appUser = user;
				else
					appUser = null;
				client = session.getClient();
				app = client.getApp();
			} catch(PrismsException e)
			{
				log.error("Could not get user and client", e);
				user = null;
				app = null;
				appUser = null;
				client = null;
			}
		}
		else
		{
			try
			{
				user = theUserSource.getUser(userName);
			} catch(PrismsException e)
			{
				log.error("Could not get user " + userName, e);
				user = null;
			}
			try
			{
				app = theUserSource.getApp(appName);
			} catch(PrismsException e)
			{
				log.error("Could not get application " + appName, e);
				app = null;
			}
			if(app != null && app.getServer() != this)
				app.setServer(this);
			try
			{
				appUser = theUserSource.getUser(user, app);
			} catch(PrismsException e)
			{
				log.error("Could not get application user " + user.getName(), e);
				appUser = null;
			}
			client = null;
			try
			{
				if(clientName != null && app != null)
					client = theUserSource.getClient(app, clientName);
				else if(serviceName != null && app != null)
					client = theUserSource.getClient(app, serviceName);
			} catch(PrismsException e)
			{
				log.error("Could not get client " + clientName, e);
				client = null;
			}
		}
		Lock lock;
		if(serviceName != null)
			sessionID = "null";
		boolean newSession = false;
		if(app != null && appUser != null)
		{
			if(serviceName == null && sessionID == null)
			{
				sessionID = newSessionID();
				if(req.getSession() != null)
				{
					javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie(
						"PRISMSsessionID", sessionID);
					cookie.setMaxAge(-1);
					resp.addCookie(cookie);
				}
				JSONObject evt = new JSONObject();
				evt.put("method", "setSessionID");
				evt.put("sessionID", sessionID);
				ret.add(evt);
			}
			sessionKey = sessionID + "/" + appName + "/" + clientServiceName + "/" + userName;
			lock = theSessionLock.readLock();
			lock.lock();
			try
			{
				session = theSessions.get(sessionKey);
			} finally
			{
				lock.unlock();
			}
			if(session == null)
			{
				try
				{
					session = new SessionMetadata(appUser, client, serviceName != null);
					newSession = true;
					lock = theSessionLock.writeLock();
					lock.lock();
					try
					{
						theSessions.put(sessionKey, session);
					} finally
					{
						lock.unlock();
					}
				} catch(PrismsException e)
				{
					log.error("Could not create session", e);
					errorString = "Could not create session: ";
					if(e.getCause() != null)
						errorString += e.getCause().getMessage();
					else
						errorString += e.getMessage();
					errorCode = ErrorCode.RequestDenied;
				}
			}
		}
		sessionKey = sessionID + "/" + appName + "/" + clientServiceName + "/" + userName;

		boolean decryptionFailed = false;
		String encryptedText = null;
		if(encrypted && session != null && !newSession && dataStr != null)
		{
			// For some reason, "+" gets mis-translated at a space
			dataStr = dataStr.replaceAll(" ", "+");
			encryptedText = dataStr;
			try
			{
				dataStr = session.decrypt(dataStr);
			} catch(Exception e)
			{
				log.error("Decryption of " + encryptedText + " failed with encryption "
					+ session.getEncryption(), e);
				decryptionFailed = true;
				encrypted = false;
			}
		}
		boolean tooShort = dataStr == null || dataStr.length() < 20;
		boolean deserializationFailed = false;
		JSONObject [] events = null;
		if(!decryptionFailed && session != null && !newSession && dataStr != null)
		{
			int commentIdx = dataStr.indexOf("-XXSERVERPADDING");
			if(commentIdx >= 0)
				dataStr = dataStr.substring(0, commentIdx);
			JSONArray jsonEvents;
			try
			{
				jsonEvents = theSerializer.deserialize(dataStr);
				events = (JSONObject []) jsonEvents.toArray(new JSONObject [jsonEvents.size()]);
			} catch(Exception e)
			{
				log.error("Deserialization failed: " + e.getMessage());
				deserializationFailed = true;
				if(encrypted)
					decryptionFailed = true;
			}
		}
		if(decryptionFailed && !user.isLocked())
		{
			log.warn("Decryption of " + encryptedText + " failed with encryption "
				+ session.getEncryption());
			try
			{
				session.loginFailed();
			} catch(PrismsException e)
			{
				log.error("Could not lock user", e);
			}
		}
		else if(session != null && !newSession && encrypted)
			session.loginSucceeded();

		PrismsWmsRequest wms = null;
		if("WMS".equalsIgnoreCase(method))
		{
			try
			{
				wms = PrismsWmsRequest.parseWMS(req);
			} catch(Throwable e)
			{
				PrismsWmsRequest.respondError(resp, e.getClass().getName() + ": " + e.getMessage());
				log.error("Error parsing WMS request", e);
				return;
			}
		}
		long pwdExp;
		try
		{
			if(appUser == null)
				pwdExp = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
			else
				pwdExp = theUserSource.getPasswordExpiration(appUser);
		} catch(PrismsException e)
		{
			errorString = "Could not get user " + appUser + "'s password expiration";
			errorCode = ErrorCode.RequestFailed;
			log.error(errorString, e);
			pwdExp = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
		}
		if(errorCode != null)
		{}
		else if(appName == null)
		{
			errorString = "No application specified";
			errorCode = ErrorCode.RequestIncomplete;
		}
		else if(app == null)
		{
			errorString = "No such application: \"" + appName + "\"";
			errorCode = ErrorCode.RequestedDataUnavailable;
		}
		else if(method == null)
		{
			errorString = "No server method specified";
			errorCode = ErrorCode.RequestIncomplete;
		}
		else if(clientName == null && serviceName == null)
		{
			errorString = "No client or service specified";
			errorCode = ErrorCode.RequestIncomplete;
		}
		else if(appUser == null && userName == null)
		{
			// No anonymous user for the specified application
			JSONObject evt = new JSONObject();
			evt.put("method", "login");
			evt.put("error", "Anonymous access forbidden for application " + appName
				+ ".  Please log in.");
			ret.add(evt);
		}
		else if(user == null)
		{
			JSONObject evt = new JSONObject();
			evt.put("method", "login");
			evt.put("error", "No such user: \"" + userName + "\"");
			evt.put("code", ErrorCode.RequestDenied.description);
			ret.add(evt);
		}
		else if(appUser == null)
		{
			JSONObject evt = new JSONObject();
			evt.put("method", "login");
			evt.put("error", "User " + userName
				+ " does not have permission to access application " + appName);
			evt.put("code", ErrorCode.RequestDenied.description);
			ret.add(evt);
		}
		else if(!encrypted && appUser.isEncryptionRequired())
		{
			if(wms != null)
				PrismsWmsRequest.respondError(resp, "User " + appUser.getName()
					+ " cannot access application " + app.getName()
					+ " without encryption, which WMS does not support");
			else
			{
				JSONObject evt = new JSONObject();
				evt.put("encryption", session.getEncryption().getParams());
				evt.put("method", "startEncryption");
				JSONObject hashing = session.getHashing().toJson();
				hashing.put("user", userName);
				evt.put("hashing", hashing);
				if("init".equals(method))
					evt.put("postAction", "callInit");
				evt.put("code", ErrorCode.RequestIncomplete.description);
				ret.add(evt);
			}
		}
		else if(encrypted && tooShort)
		{
			errorString = "Data string null or too short--at least 20 characters of data must be"
				+ " included to verify encryption.  Use \"-XXSERVERPADDING...\" for padding";
			errorCode = ErrorCode.RequestIncomplete;
		}
		else if(newSession && encrypted)
		{
			JSONObject evt = new JSONObject();
			evt.put("method", "startEncryption");
			evt.put("error", "Session has timed out or has been removed--please log in again");
			JSONObject hashing = session.getHashing().toJson();
			hashing.put("user", userName);
			evt.put("hashing", hashing);
			if("init".equals(method))
				evt.put("postAction", "callInit");
			evt.put("code", ErrorCode.RequestIncomplete.description);
			ret.add(evt);
			encrypted = false;
		}
		else if(decryptionFailed)
		{
			if(wms != null)
				PrismsWmsRequest.respondError(resp, "WMS does not support encryption");
			else
			{
				if(user.isLocked())
					ret.add(sendLogin(session, "Too many incorrect password attempts.\nUser "
						+ user.getName() + " is locked." + "  Contact your admin", "init"
						.equals(method), true));
				else if(session.authChanged)
					ret.add(sendLogin(session, user + "'s password has been changed."
						+ " Use the new password or contact your admin", "init".equals(method),
						true));
				else
					ret.add(sendLogin(session, "Incorrect password for user " + userName, "init"
						.equals(method), true));
				encrypted = false;
			}
		}
		else if(user.isLocked())
		{
			errorString = "User is locked.  Contact your admin.";
			errorCode = ErrorCode.RequestDenied;
		}
		else if(deserializationFailed)
		{
			errorString = "Event deserialization failed!";
			errorCode = ErrorCode.RequestFailed;
		}
		else if("validate".equals(method))
		{
			Validator validator = appUser.getValidator();
			JSONObject evt = new JSONObject();
			boolean valid;
			try
			{
				valid = validator == null
					|| validator.validate(appUser, app, session.theClient, req, events[0]);
			} catch(RuntimeException e)
			{
				valid = false;
				errorString = e.getMessage();
				errorCode = ErrorCode.RequestDenied;
			}
			if(valid)
			{
				evt.put("method", "callInit");
				session.validate();
			}
			else
			{
				JSONObject valInfo = validator.getValidationInfo(appUser, app, session.theClient,
					req);
				evt.put("method", "validate");
				evt.put("validationFailed", new Boolean(true));
				evt.put("params", valInfo);
			}
			ret.add(evt);
		}
		else if(appUser.getValidator() != null && !session.isValidated())
		{
			Validator validator = appUser.getValidator();
			JSONObject valInfo = validator.getValidationInfo(appUser, app, session.theClient, req);
			if(valInfo != null && wms != null)
				PrismsWmsRequest.respondError(resp, "Validation required for user "
					+ user.getName() + ", application" + app.getName());
			else if(valInfo == null)
			{
				try
				{
					if(!validator.validate(appUser, app, session.theClient, req, null))
						throw new IllegalArgumentException("Validation unsuccessful");
					session.validate();
				} catch(RuntimeException e)
				{
					errorString = e.getMessage();
					errorCode = ErrorCode.RequestDenied;
				}
			}
			else
			{
				JSONObject evt = new JSONObject();
				evt.put("method", "validate");
				evt.put("params", valInfo);
				ret.add(evt);
			}
		}
		else if("changePassword".equals(method))
		{
			long [] pwdData = null;
			JSONObject evt;
			try
			{
				JSONArray jsonPwdData = (JSONArray) events[0].get("passwordData");
				pwdData = new long [jsonPwdData.size()];
				for(int i = 0; i < pwdData.length; i++)
					pwdData[i] = ((Number) jsonPwdData.get(i)).longValue();
				theUserSource.setPassword(appUser, pwdData);
				evt = new JSONObject();
				evt.put("method", "callInit");
				ret.add(evt);
			} catch(Exception e)
			{
				log.error("Password change failed", e);
				if(pwdData != null)
					log.error("Could not set password data for user " + appUser.getName() + " to "
						+ prisms.util.ArrayUtils.toString(pwdData), e);
				else
					log.error("Could not set password data for user " + appUser.getName()
						+ ": no passwordData sent", e);
				ret.add(sendChangePassword(session, "Could not change password: " + e.getMessage(),
					true));
			}
		}
		else if(pwdExp < System.currentTimeMillis())
		{
			if(wms != null)
				PrismsWmsRequest.respondError(resp, "Password change required for user "
					+ user.getName());
			else
				try
				{
					ret.add(sendChangePassword(session, "Password change required for user "
						+ user.getName(), false));
				} catch(PrismsException e)
				{
					errorCode = ErrorCode.RequestFailed;
					errorString = "Could not get password change data";
				}
		}
		else if("tryChangePassword".equals(method))
		{
			session.renew();
			try
			{
				ret.add(sendChangePassword(session, null, false));
			} catch(PrismsException e)
			{
				errorCode = ErrorCode.RequestFailed;
				errorString = "Could not get password change data";
			}
		}
		else if("getVersion".equals(method))
		{
			JSONObject evt = new JSONObject();
			evt.put("method", "setVersion");

			JSONArray version = null;
			if(app.getVersion() != null)
			{
				version = new JSONArray();
				for(int v = 0; v < app.getVersion().length; v++)
					version.add(new Integer(app.getVersion()[v]));
			}
			evt.put("version", version);
			evt.put("modified", new Long(app.getModifiedDate()));
			ret.add(evt);
		}
		else if("init".equals(method))
		{
			JSONObject evt = new JSONObject();
			evt.put("method", "init");
			ret.add(evt);
			try
			{
				ret.addAll(session.init());
			} catch(Throwable e)
			{
				log.error("Could not create session", e);
				errorString = "Could not create session: " + e.getMessage();
				errorCode = ErrorCode.RequestFailed;
			}
		}
		else if(session.getSession() == null)
		{ // Client needs to be re-initialized
			if(wms != null)
				PrismsWmsRequest.respondError(resp, "Client needs to be re-initialized");
			else
			{
				JSONObject evt = new JSONObject();
				evt.put("method", "restart");
				ret.add(evt);
			}
		}
		else if("logout".equals(method))
		{
			destroySession(sessionKey, session);
			ret.add(sendLogin(null, "You have been successfully logged out", false, false));
			encrypted = false;
		}
		else if(events == null)
		{
			errorString = "No data to process in request";
			errorCode = ErrorCode.RequestIncomplete;
		}
		else if(events.length > 1)
		{
			errorString = "Only one event allowed per request";
			errorCode = ErrorCode.RequestFailed;
		}
		else if(events.length == 0)
		{
			errorString = "No events in the request";
			errorCode = ErrorCode.RequestIncomplete;
		}
		else if("processEvent".equals(method))
		{
			synchronized(theThreadSessions)
			{
				theThreadSessions.put(Thread.currentThread(), session);
			}
			try
			{
				ret.addAll(session.process(events[0]));
			} catch(Throwable e)
			{
				log.error("Could not process session events", e);
				errorString = "Could not process session events: " + e.getMessage();
				errorCode = ErrorCode.RequestFailed;
			} finally
			{
				theThreadSessions.remove(Thread.currentThread());
			}
		}
		else if(wms != null)
		{
			session.fillWmsRequest(events[0], wms, resp);
			session.renew();
			clean();
			return;
		}
		else if("generateImage".equals(method))
		{
			String reqURI = req.getRequestURI();
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
				return;
			}
			if(format.equals("jpg"))
				resp.setContentType("image/jpeg");
			else
				resp.setContentType("image/" + format);
			java.io.OutputStream out = resp.getOutputStream();
			session.generateImage(events[0], format, out);
			session.renew();
			clean();
			return;
		}
		else if("getDownload".equals(method))
		{
			session.getDownload(events[0], resp);
			session.renew();
			clean();
			return;
		}
		else if("doUpload".equals(method))
		{
			session.doUpload(events[0], req);
			// We redirect to avoid the browser's resend warning if the user refreshes
			resp.setStatus(301);
			resp.sendRedirect("nothing.html");
			session.renew();
			clean();
			return;
		}
		else
		{
			errorString = "Unable to process request: serverMethod " + method + " not defined";
			errorCode = ErrorCode.RequestIncomplete;
		}

		clean();
		if(errorString != null)
		{
			if(wms != null)
				PrismsWmsRequest.respondError(resp, errorString);
			else
			{
				JSONObject evt = new JSONObject();
				evt.put("method", "error");
				evt.put("message", errorString);
				evt.put("code", errorCode.description);
				log.warn("Error(" + errorCode.description + "): " + errorString);
				ret.add(evt);
			}
		}
		String serialized;
		try
		{
			if(client != null && client.getSerializer() != null)
				serialized = client.getSerializer().serialize(ret);
			else
				serialized = theSerializer.serialize(ret);
		} catch(java.io.NotSerializableException e)
		{
			log.error("Return events not serializable", e);
			ret.clear();
			JSONObject evt = new JSONObject();
			evt.put("method", "error");
			evt.put("message", "Return events not serializable");
			evt.put("code", ErrorCode.RequestFailed.description);
			log.warn("Error(" + ErrorCode.RequestFailed.description
				+ "): Return events not serializable");
			ret.add(evt);
			try
			{
				serialized = theSerializer.serialize(ret);
			} catch(java.io.NotSerializableException e2)
			{
				log.error("Double NSE--quitting", e2);
				return;
			}
		}
		if(encrypted && session != null)
		{
			try
			{
				serialized = session.encrypt(serialized);
			} catch(Exception e)
			{
				log.error("Error performing encryption", e);
				// Don't send unencrypted data if encryption is requested
				ret.clear();
				JSONObject evt = new JSONObject();
				evt.put("method", "error");
				evt.put("message", "Could not encrypt return events");
				evt.put("code", ErrorCode.RequestFailed.description);
				log.error("Error(" + ErrorCode.RequestFailed.description
					+ "): Could not encrypt return events");
				ret.add(evt);
				try
				{
					serialized = theSerializer.serialize(ret);
				} catch(java.io.NotSerializableException e2)
				{
					log.error("Double NSE--quitting", e2);
					return;
				}
			}
		}
		resp.setContentType("text/prisms-json");
		resp.getWriter().write(serialized);
	}

	@Override
	protected void doPost(javax.servlet.http.HttpServletRequest req,
		javax.servlet.http.HttpServletResponse resp) throws javax.servlet.ServletException,
		java.io.IOException
	{
		doGet(req, resp);
	}

	JSONObject sendLogin(SessionMetadata session, String error, boolean postInit, boolean isError)
	{
		JSONObject evt = new JSONObject();
		if(session != null)
		{
			evt.put("method", "startEncryption");
			evt.put("encryption", session.getEncryption().getParams());
			JSONObject hashing = session.getHashing().toJson();
			hashing.put("user", session.theUser.getName());
			evt.put("hashing", hashing);
			if(postInit)
				evt.put("postAction", "callInit");
			if(isError)
				evt.put("code", ErrorCode.RequestDenied.description);
		}
		else
			evt.put("method", "login");
		evt.put("error", error);
		return evt;
	}

	JSONObject sendChangePassword(SessionMetadata session, String message, boolean error)
		throws PrismsException
	{
		prisms.arch.ds.PasswordConstraints pc = theUserSource.getPasswordConstraints();
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
		JSONObject evt = new JSONObject();
		evt.put("method", "changePassword");
		if(error)
			evt.put("error", msg.toString());
		else
			evt.put("message", msg.toString());
		evt.put("constraints", prisms.arch.service.PrismsSerializer.serializeConstraints(pc));
		evt.put("hashing", session.getHashing().toJson());
		return evt;
	}

	void destroySession(String sessionKey, SessionMetadata session)
	{
		theSessions.remove(sessionKey);
		session.destroy();
		System.gc();
	}

	/**
	 * @return The session that this thread is executing in
	 */
	public PrismsSession getCurrentSession()
	{
		synchronized(theThreadSessions)
		{
			SessionMetadata ret = theThreadSessions.get(Thread.currentThread());
			if(ret == null)
				return null;
			return ret.theSession;
		}
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
		Lock lock = theSessionLock.writeLock();
		lock.lock();
		try
		{
			SessionMetadata [] sessions = theSessions.values().toArray(new SessionMetadata [0]);
			theSessions.clear();
			for(SessionMetadata s : sessions)
				s.destroy();
		} finally
		{
			lock.unlock();
		}
		theUserSource.disconnect();
		thePersisterFactory.destroy();
		System.gc();
	}

	class SessionMetadata
	{
		String theUserName;

		User theUser;

		ClientConfig theClient;

		PrismsSession theSession;

		Hashing theHashing;

		long [] theKey;

		private Encryption theEncryption;

		boolean isValidated;

		boolean isService;

		int theLoginFailed;

		boolean authChanged;

		long userLastChecked;

		SessionMetadata(User appUser, ClientConfig client, boolean service) throws PrismsException
		{
			theUserName = appUser.getName();
			theUser = appUser;
			theClient = client;
			isService = service;
			try
			{
				theHashing = getUserSource().getHashing();
			} catch(PrismsException e)
			{
				throw new IllegalStateException("Could not get user password hash", e);
			}
			checkAuthenticationData(true);
			authChanged = false;
			if(isService)
			{
				JSONArray initEvents = init();
				// The service doesn't need any initialization events and shouldn't even generate
				// any
				if(initEvents.size() != 0)
					log.warn("Initialization events generated and ignored for service "
						+ client.getName() + " of application " + appUser.getApp().getName() + ": "
						+ initEvents);
			}
		}

		void checkAuthenticationData(boolean init) throws PrismsException
		{
			long time = System.currentTimeMillis();
			if(!init && time - userLastChecked < theUserCheckPeriod)
				return;
			userLastChecked = time;
			if(!init || theUser == null)
			{
				User rootUser = getUserSource().getUser(theUserName);
				if(rootUser == null)
				{
					theUser = null;
					return;
				}
				User appUser = getUserSource().getUser(rootUser, theClient.getApp());
				if(appUser == null)
				{
					theUser = rootUser;
					return;
				}
			}
			long [] newKey = getUserSource().getKey(theUser, theHashing);
			if(theKey == null)
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
			else if(!prisms.util.ArrayUtils.equals(theKey, newKey))
			{
				theKey = newKey;
				// byte [] init = new byte [10];
				// for(int i = 0; i < init.length; i++)
				// init[i] = (byte) ((Math.random() - 0.5) * 2 * Byte.MAX_VALUE);
				if(theEncryption != null)
					theEncryption.dispose();
				theEncryption = createEncryption();
				theEncryption.init(theKey, null);
				authChanged = true;
			}
		}

		JSONArray init() throws PrismsException
		{
			if(theSession == null)
				theSession = getUserSource().createSession(theClient, theUser, isService);
			theSession.clearOutgoingQueue();
			theSession.init();
			return theSession.getEvents();
		}

		User getUser() throws PrismsException
		{
			checkAuthenticationData(false);
			return theUser;
		}

		ClientConfig getClient() throws PrismsException
		{
			// checkAuthenticationData(false);
			return theClient;
		}

		Hashing getHashing()
		{
			return theHashing;
		}

		Encryption getEncryption()
		{
			return theEncryption;
		}

		long [] getKey()
		{
			return theKey;
		}

		PrismsSession getSession()
		{
			return theSession;
		}

		boolean isValidated()
		{
			return isValidated;
		}

		void validate()
		{
			isValidated = true;
		}

		String decrypt(String encrypted) throws java.io.IOException
		{
			checkAuthenticationData(false);
			return theEncryption.decrypt(encrypted);
		}

		String encrypt(String text) throws java.io.IOException
		{
			/*
			 * TODO: For whatever reason, information encrypted by the server is not decrypted
			 * correctly by the javascript dojo blowfish implementation on the HTML client. Pending
			 * more extensive investigation, we'll just send information unencrypted.
			 */
			if(theSession != null && theSession.isService())
				return theEncryption.encrypt(text);
			return text;
		}

		void loginFailed() throws PrismsException
		{
			if(theUser.isLocked())
				return;
			if(authChanged)
			{ // If password changed, give a bonus attempt
				authChanged = false;
				return;
			}
			theLoginFailed++;
			if(theLoginFailed >= theLoginFailTolerance && !theUser.getName().equals("admin"))
			{
				theUser.setLocked(true);
				getUserSource().lockUser(theUser);
				theLoginFailed = 0;
			}
		}

		void loginSucceeded()
		{
			theLoginFailed = 0;
			authChanged = false;
		}

		synchronized JSONArray process(JSONObject event)
		{
			theSession.getApp().putApplicationEvents(theSession);
			int busyness = theSession.getBusyCount();
			if(theSession.getApp().isOpen(theSession))
			{
				try
				{
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
			}
			else
			{
				Lock lock = theSessionLock.writeLock();
				lock.lock();
				try
				{
					java.util.Iterator<SessionMetadata> iter = theSessions.values().iterator();
					while(iter.hasNext())
					{
						if(iter.next() == this)
							iter.remove();
					}
				} finally
				{
					lock.unlock();
				}
			}
			JSONArray ret = theSession.getEvents();
			if(theSession.getBusyCount() > busyness)
			{
				JSONObject getEventsEvent = new JSONObject();
				getEventsEvent.put("method", "getEvents");
				ret.add(getEventsEvent);
			}
			return ret;
		}

		synchronized void fillWmsRequest(JSONObject event, PrismsWmsRequest wms,
			javax.servlet.http.HttpServletResponse response) throws java.io.IOException
		{
			String pluginName = (String) event.get("plugin");
			WmsPlugin plugin = (WmsPlugin) theSession.getPlugin(pluginName);
			if(plugin == null)
			{
				PrismsWmsRequest.respondError(response, "No plugin " + pluginName + " loaded");
				log.error("No plugin " + pluginName + " loaded");
				return;
			}
			try
			{
				// Run session tasks
				theSession._process(null);
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
				PrismsWmsRequest.respondError(response, e.getClass().getName() + ": "
					+ e.getMessage());
				log.error("Could not fulfill " + wms.getRequest() + " typed WMS request", e);
			}
		}

		synchronized void generateImage(JSONObject event, String format, java.io.OutputStream output)
			throws java.io.IOException
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
			// Run session tasks
			theSession._process(null);
			((ImagePlugin) theSession.getPlugin(pluginName)).writeImage(method, format, xOffset,
				yOffset, refWidth, refHeight, imWidth, imHeight, output);
		}

		synchronized void getDownload(JSONObject event, javax.servlet.http.HttpServletResponse resp)
			throws java.io.IOException
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
			resp.setContentType(contentType);
			resp.setHeader("Content-Disposition", "attachment; filename=\""
				+ plugin.getFileName(event) + "\"");
			plugin.doDownload(event, resp.getOutputStream());
		}

		synchronized void doUpload(final JSONObject event, javax.servlet.http.HttpServletRequest req)
		{
			String pluginName = (String) event.get("plugin");
			if(theSession.getPlugin(pluginName) == null)
			{
				log.debug("No plugin " + pluginName + " loaded");
				return;
			}
			final UploadPlugin plugin = (UploadPlugin) theSession.getPlugin(pluginName);
			if(!org.apache.commons.fileupload.servlet.ServletFileUpload.isMultipartContent(req))
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
				items = upload.parseRequest(req);
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
						}
					}
				});
				didUpload = true;
			}
			if(!didUpload)
				throw new IllegalStateException("getUpload called with no non-form field");
		}

		void renew()
		{
			if(theSession != null)
				theSession.renew();
		}

		void destroy()
		{
			if(theSession != null)
				theSession.destroy();
			if(theEncryption != null)
				theEncryption.dispose();
		}
	}
}
