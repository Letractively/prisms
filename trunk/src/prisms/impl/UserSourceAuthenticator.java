/*
 * UserSourceAuthenticator.java Created Nov 1, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.*;
import prisms.arch.PrismsServer.PrismsRequest;
import prisms.arch.ds.User;
import prisms.util.ArrayUtils;

/** Authenticates using the usernames/passwords stored in the UserSource */
public class UserSourceAuthenticator implements PrismsAuthenticator
{
	static final Logger log = Logger.getLogger(UserSourceAuthenticator.class);

	/** The error message to display for a general authentication failure */
	public static final String LOGIN_FAIL_MESSAGE = "Invalid username/password";

	private static class USReqAuth implements RequestAuthenticator
	{
		// private final PrismsRequest theRequest;

		private final Encryption theEncryption;

		private String theDecryptedData;

		private String theErrorMessage;

		private boolean shouldReattempt;

		USReqAuth(PrismsRequest req, Encryption enc, String decrypted)
		{
			// theRequest = req;
			theEncryption = enc;
			theDecryptedData = decrypted;
		}

		USReqAuth(PrismsRequest req, String message, boolean reattempt)
		{
			// theRequest = req;
			theEncryption = null;
			theErrorMessage = message;
			shouldReattempt = reattempt;
		}

		public String encrypt(javax.servlet.http.HttpServletResponse response, String data)
		{
			if(theEncryption != null)
				try
				{
					return theEncryption.encrypt(data);
				} catch(java.io.IOException e)
				{
					throw new IllegalStateException("Could not encrypt data", e);
				}
			else
				return data;
		}

		public boolean isError()
		{
			return theErrorMessage != null || shouldReattempt;
		}

		public String getError()
		{
			return theErrorMessage;
		}

		public boolean shouldReattempt()
		{
			return shouldReattempt;
		}

		public String getData()
		{
			return theDecryptedData;
		}
	}

	static class DummySessionData
	{
		prisms.arch.ds.Hashing hashing;

		Encryption enc;

		int loginFails;
	}

	private class DummySessionAuth implements SessionAuthenticator
	{
		private prisms.util.DemandCache<String, DummySessionData> theEncryptions;

		DummySessionAuth()
		{
			theEncryptions = new prisms.util.DemandCache<String, DummySessionData>(
				new prisms.util.DemandCache.Qualitizer<DummySessionData>()
				{
					public float quality(DummySessionData value)
					{
						return 1;
					}

					public float size(DummySessionData value)
					{
						float ret = 2;
						if(value.hashing != null)
							ret += 100;
						if(value.enc != null)
							ret += 100;
						return ret;
					}
				}, 5000, 15L * 60 * 1000);
		}

		public RequestAuthenticator getRequestAuthenticator(PrismsRequest request)
			throws PrismsException
		{
			DummySessionData data = theEncryptions.get(request.getUser().getName());
			if(data == null)
			{
				data = new DummySessionData();
				theEncryptions.put(request.getUser().getName(), data);
			}
			String dataStr = request.httpRequest.getParameter("data");
			if(dataStr != null && isEncrypted(dataStr))
			{
				if(data.loginFails > theLoginFailTolerance)
				{
					request.getUser().setLocked(true);
					if(data.hashing != null)
						data.hashing = null;
					if(data.enc != null)
					{
						data.enc.dispose();
						data.enc = null;
					}
				}
				else
					data.loginFails++;
			}
			return new USReqAuth(request, LOGIN_FAIL_MESSAGE, true);
		}

		public JSONObject requestLogin(PrismsRequest request) throws PrismsException
		{
			DummySessionData data = theEncryptions.get(request.getUser().getName());
			if(data == null)
			{
				data = new DummySessionData();
				theEncryptions.put(request.getUser().getName(), data);
			}
			if(data.hashing == null)
				data.hashing = theUserSource.getHashing();
			if(data.enc == null)
			{
				data.enc = createEncryption();
				long now = System.currentTimeMillis();
				data.enc.init(new long [] {now, Math.abs(now ^ 0xffffffffffffffffL)},
					theEncryptionProperties);
			}

			JSONObject evt = new JSONObject();
			evt.put("method", "startEncryption");
			evt.put("encryption", data.enc.getParams());
			JSONObject hashing = data.hashing.toJson();
			hashing.put("user", request.getUser().getName());
			evt.put("hashing", hashing);
			return evt;
		}

		public boolean needsPasswordChange() throws PrismsException
		{
			return false;
		}

		public JSONObject requestPasswordChange() throws PrismsException
		{
			return null; // Can't get here because they can't log in successfully
		}

		public AuthenticationError changePassword(JSONObject event) throws PrismsException
		{
			return null; // Can't get here because they can't log in successfully
		}

		public long recheck() throws PrismsException
		{
			return -1;
		}

		public void destroy() throws PrismsException
		{
			theEncryptions.purge(true);
		}
	}

	private class USSessionAuth implements SessionAuthenticator
	{
		private final User theUser;

		private final boolean isAnonymous;

		private final boolean isSystem;

		private final prisms.arch.ds.Hashing theHashing;

		private prisms.arch.ds.UserSource.Password thePassword;

		private Encryption theEncryption;

		private int theLoginsFailed;

		private boolean authChanged;

		private long userLastChecked;

		private boolean hasLoggedIn;

		USSessionAuth(User user) throws PrismsException
		{
			theUser = user;
			isAnonymous = user.equals(theUserSource.getUser(null));
			isSystem = theUserSource instanceof prisms.arch.ds.ManageableUserSource
				&& user.equals(((prisms.arch.ds.ManageableUserSource) theUserSource)
					.getSystemUser());
			theHashing = theUserSource.getHashing();
			checkAuthenticationData(true);
		}

		/**
		 * Checks this session holder's user against the data source periodically to see whether the
		 * password has changed or the user has been locked.
		 * 
		 * @param force Whether to force this auth info to update from the database
		 * @throws PrismsException If an error occurs contacting the database
		 */
		long checkAuthenticationData(boolean force) throws PrismsException
		{
			long time = System.currentTimeMillis();
			if(!force && time - userLastChecked < theUserCheckPeriod)
				return -1;
			synchronized(this)
			{
				if(time < userLastChecked)
					return -1;
				userLastChecked = time;
				prisms.arch.ds.UserSource.Password pwd = theUserSource.getPassword(theUser,
					theHashing);
				if(thePassword == null && pwd != null)
				{
					thePassword = pwd;
					theEncryption = createEncryption();
					theEncryption.init(thePassword.key, theEncryptionProperties);
					return pwd.setTime;
				}
				else if(pwd == null)
				{
					if(isSystem)
					{
						// If the user is System, set the initial password
						thePassword = theUserSource.setPassword(theUser, theHashing
							.generateKey(theHashing.partialHash(java.util.UUID.randomUUID()
								.toString())), true);
						theEncryption = createEncryption();
						theEncryption.init(thePassword.key, null);
						authChanged = true;
						return thePassword.setTime;
					}
					thePassword = null;
					theEncryption = null;
					return -1;
				}
				else if(!ArrayUtils.equals(thePassword.key, pwd.key))
				{
					thePassword = pwd;
					// byte [] init = new byte [10];
					// for(int i = 0; i < init.length; i++)
					// init[i] = (byte) ((Math.random() - 0.5) * 2 * Byte.MAX_VALUE);
					if(thePassword != null)
					{
						theEncryption = createEncryption();
						theEncryption.init(thePassword.key, null);
					}
					authChanged = true;
					return pwd.setTime;
				}
				else if(isSystem && thePassword.setTime < time - 60000)
				{
					// If the user is System, change the password randomly every minute
					thePassword = theUserSource.setPassword(theUser,
						theHashing.generateKey(theHashing.partialHash(java.util.UUID.randomUUID()
							.toString())), true);
					theEncryption = createEncryption();
					theEncryption.init(thePassword.key, null);
					authChanged = true;
					return thePassword.setTime;
				}
				else
					return -1;
			}
		}

		public long recheck() throws PrismsException
		{
			return checkAuthenticationData(true);
		}

		public RequestAuthenticator getRequestAuthenticator(PrismsRequest request)
			throws PrismsException
		{
			String dataStr = request.httpRequest.getParameter("data");
			Encryption enc = theEncryption;
			if(dataStr != null && isEncrypted(dataStr))
			{
				if(enc == null)
					return new USReqAuth(request, "User " + theUser.getName()
						+ " does not have a password set--consult your admin", true);
				// For some reason, "+" gets mis-translated at a space
				dataStr = dataStr.replaceAll(" ", "+");
				String encryptedText = dataStr;
				try
				{
					dataStr = decrypt(request, dataStr, enc);
					if(dataStr != null && dataStr.startsWith("__XENC"))
					{} // "Safely" encoded URL
					else if(dataStr == null || dataStr.length() < 2 || dataStr.charAt(0) != '{'
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
					USReqAuth ret = checkOlderPasswords(request,
						request.httpRequest.getParameter("data").replaceAll(" ", "+"));
					if(ret != null)
						return ret;
					log.debug("Decryption of " + encryptedText + " failed with encryption "
						+ theEncryption, e);
					loginFailed();
					if(theUser.isLocked())
						return new USReqAuth(request,
							"Too many incorrect password attempts.\nUser " + theUser.getName()
								+ " is locked. Contact your admin", true);
					else if(authChanged)
						return new USReqAuth(request, theUser + "'s password has been changed."
							+ " Use the new password or contact your admin.", true);
					else
						// return new USReqAuth(this, "Incorrect password for user "
						// + theUser.getName(), true);
						return new USReqAuth(request, LOGIN_FAIL_MESSAGE, true);
				}

				// Decryption succeeded
				if(dataStr.length() < 20)
					return new USReqAuth(request, "Data string null or too short"
						+ "--at least 20 characters of data must be included to verify encryption."
						+ "  Use \"-XXSERVERPADDING...\" for padding", false);
				// Remove "-XXSERVERPADDING" comment if present
				int commentIdx = dataStr.indexOf("-XXSERVERPADDING");
				if(commentIdx >= 0)
					dataStr = dataStr.substring(0, commentIdx);
				loginSucceeded();
			}
			else if(isAnonymous)
				return new USReqAuth(request, null, dataStr);
			else if(!request.getClient().isService()
				&& (!hasLoggedIn || !loginOnce || !request.httpRequest.isSecure()))
				return new USReqAuth(request, LOGIN_FAIL_MESSAGE, true);
			return new USReqAuth(request, null, dataStr);
		}

		String decrypt(PrismsRequest request, String encrypted, Encryption enc)
			throws java.io.IOException
		{
			if(enc == null)
				return encrypted;
			return enc.decrypt(encrypted);
		}

		private USReqAuth checkOlderPasswords(PrismsRequest request, String dataStr)
			throws PrismsException
		{
			long now = System.currentTimeMillis();
			prisms.arch.ds.UserSource.Password[] passwords = theUserSource.getOldPasswords(theUser,
				theHashing);
			for(int p = 0; p < passwords.length; p++)
			{
				if(passwords[p].expire < now)
					continue;
				Encryption enc = createEncryption();
				enc.init(passwords[p].key, theEncryptionProperties);
				boolean used = false;
				try
				{
					String aTry = enc.decrypt(dataStr);
					if(aTry != null && aTry.startsWith("__XENC"))
					{} // "Safely" encoded URL
					else if(aTry == null || aTry.length() < 2 || aTry.charAt(0) != '{'
						|| aTry.charAt(aTry.length() - 1) != '}')
						continue;
					if(now - passwords[p].setTime > theOldPasswordTolerance)
						return new USReqAuth(request, theUser + "'s password"
							+ " has been changed. Use the new password or contact your admin.",
							true);
					used = true;
					return new USReqAuth(request, enc, aTry);
				} catch(Exception e)
				{
					continue;
				} finally
				{
					if(!used)
						enc.dispose();
				}
			}
			return null;
		}

		public JSONObject requestLogin(PrismsRequest request) throws PrismsException
		{
			checkAuthenticationData(false);
			JSONObject evt = new JSONObject();
			if(theEncryption != null)
			{
				evt.put("method", "startEncryption");
				evt.put("encryption", theEncryption.getParams());
				JSONObject hashing = theHashing.toJson();
				hashing.put("user", request.getUser().getName());
				evt.put("hashing", hashing);
			}
			else
			{
				evt.put("method", "login");
				evt.put("error", "No password set for user " + theUser);
			}
			return evt;
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
			if(theLoginsFailed >= theLoginFailTolerance && !theUser.isAdmin())
			{
				theUserSource.lockUser(theUser);
				theLoginsFailed = 0;
			}
		}

		private void loginSucceeded()
		{
			hasLoggedIn = true;
			theLoginsFailed = 0;
			authChanged = false;
		}

		public boolean needsPasswordChange() throws PrismsException
		{
			long pwdExp;
			pwdExp = theUserSource.getPasswordExpiration(theUser);
			return pwdExp > 0 && pwdExp < System.currentTimeMillis();
		}

		public JSONObject requestPasswordChange() throws PrismsException
		{
			prisms.arch.ds.PasswordConstraints pc = theUserSource.getPasswordConstraints();
			StringBuilder msg = new StringBuilder();
			if(pc.getNumConstraints() == 1)
			{
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
					count++;
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
					count++;
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
					count++;
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
					count++;
				}
				if(pc.getNumPreviousUnique() > 0)
				{
					msg.append("\n\t");
					msg.append(count);
					msg.append(") not be the same as ");
					if(pc.getNumPreviousUnique() == 1)
						msg.append("your current password");
					else if(pc.getNumPreviousUnique() == 2)
						msg.append("your current or previous passwords");
					else
					{
						msg.append("any of your previous ");
						msg.append(pc.getNumPreviousUnique());
						msg.append(" passwords");
					}
					count++;
				}
			}
			JSONObject ret = new JSONObject();
			ret.put("message", msg.toString());
			ret.put("constraints", prisms.util.PrismsUtils.serializeConstraints(pc));
			ret.put("hashing", theUserSource.getHashing().toJson());
			return ret;
		}

		public AuthenticationError changePassword(JSONObject event) throws PrismsException
		{
			long [] pwdData = null;
			try
			{
				org.json.simple.JSONArray jsonPwdData = (org.json.simple.JSONArray) event
					.get("passwordData");
				pwdData = new long [jsonPwdData.size()];
				for(int i = 0; i < pwdData.length; i++)
					pwdData[i] = ((Number) jsonPwdData.get(i)).longValue();
				theUserSource.setPassword(theUser, pwdData, theUser.isAdmin());
				// Password change succeeded--we're done. Tell the client to re-initialize.
				return null;
			} catch(Exception e)
			{
				log.error("Password change failed", e);
				if(pwdData != null)
					log.error("Could not set password data for user " + theUser.getName() + " to "
						+ ArrayUtils.toString(pwdData), e);
				else
					log.error("Could not set password data for user " + theUser.getName()
						+ ": no passwordData sent", e);
				return new AuthenticationError("Could not change password: " + e.getMessage(), true);
			}
		}

		public void destroy()
		{
			if(theEncryption != null)
				theEncryption.dispose();
		}
	}

	static class DummyUser extends User
	{
		private final String theRealName;

		DummyUser(User base, String name)
		{
			super(base.getSource(), name, base.getID());
			theRealName = name;
		}

		@Override
		public String getName()
		{
			return theRealName;
		}
	}

	prisms.arch.ds.UserSource theUserSource;

	private String theUserParam;

	private Class<? extends prisms.arch.Encryption> theEncryptionClass;

	java.util.HashMap<String, String> theEncryptionProperties;

	private User theDummyUser;

	private prisms.util.DemandCache<String, DummyUser> theDummyUsers;

	private DummySessionAuth theDummyAuth;

	boolean loginOnce;

	long theUserCheckPeriod = 60000;

	int theLoginFailTolerance = 3;

	long theOldPasswordTolerance = 3L * 60 * 1000;

	boolean revealLoginMistakes = false;

	public void configure(PrismsConfig config, prisms.arch.ds.UserSource userSource,
		PrismsApplication [] apps)
	{
		theUserSource = userSource;
		theUserParam = config.get("userparam");
		if(theUserParam == null)
			theUserParam = "user";
		theEncryptionProperties = new java.util.HashMap<String, String>();
		PrismsConfig encryptionEl = config.subConfig("encryption");
		if(encryptionEl != null)
		{
			String encryptionClass = encryptionEl.get("class");
			try
			{
				theEncryptionClass = Class.forName(encryptionClass).asSubclass(Encryption.class);
			} catch(Throwable e)
			{
				log.error("Could not instantiate encryption " + encryptionClass, e);
				throw new IllegalStateException("Could not instantiate encryption "
					+ encryptionClass, e);
			}
			for(PrismsConfig propEl : encryptionEl.subConfigs())
			{
				if(propEl.getName().equals("class"))
					continue;
				theEncryptionProperties.put(propEl.getName(), propEl.getValue());
			}
		}
		else
			theEncryptionClass = BlowfishEncryption.class;
		loginOnce = config.is("loginOnce", false);

		String ucp = config.get("recheck-time");
		if(ucp != null)
			theUserCheckPeriod = Long.parseLong(ucp);
		theLoginFailTolerance = config.getInt("login-attempts", theLoginFailTolerance);
		String opt = config.get("password-tolerance-time");
		if(opt != null)
			theOldPasswordTolerance = Long.parseLong(opt);
		revealLoginMistakes = config.is("reveal-login-mistakes", false);
		if(!revealLoginMistakes)
		{
			int id = LOGIN_FAIL_MESSAGE.hashCode();
			if(id == 0)
				id = -5;
			else if(id > 0)
				id = -id;
			theDummyUser = new User(null, "Dummy", id);
			theDummyUsers = new prisms.util.DemandCache<String, DummyUser>(null, 50,
				10L * 60 * 1000);
			theDummyAuth = new DummySessionAuth();
		}
	}

	static boolean isEncrypted(String dataStr)
	{
		if(dataStr == null || dataStr.length() < 2)
			return true;
		else if(dataStr.startsWith("__XENC"))
			return false;
		else if(dataStr.startsWith("{"))
			return false;
		else
			return true;
	}

	prisms.arch.Encryption createEncryption()
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

	public boolean recognized(PrismsRequest request)
	{
		return true; // This is a default authenticator--it may be used in any case as a last resort
	}

	public User getUser(PrismsRequest request) throws PrismsException
	{
		String userName = request.httpRequest.getParameter(theUserParam);
		if(userName == null)
		{
			String data = request.httpRequest.getParameter("data");
			if(data != null && !isEncrypted(data))
			{
				data = prisms.util.PrismsUtils.decodeSafe(data);
				org.json.simple.JSONObject desData;
				try
				{
					desData = (org.json.simple.JSONObject) org.json.simple.JSONValue.parse(data);
					userName = (String) desData.get(theUserParam);
				} catch(Throwable e)
				{
					log.error("Could not parse data: " + data, e);
				}
			}
		}
		User ret = theUserSource.getUser(userName);
		if(ret == null && !revealLoginMistakes)
		{
			ret = theDummyUsers.get(userName);
			if(ret == null)
			{
				long id = Math.abs(userName.hashCode());
				id <<= 31;
				id |= (Math.abs(userName.toUpperCase().hashCode()) ^ Math.abs(userName
					.toLowerCase().hashCode()));
				ret = new DummyUser(theDummyUser, userName);
				theDummyUsers.put(userName, (DummyUser) ret);
			}
		}
		return ret;
	}

	public SessionAuthenticator createSessionAuthenticator(PrismsRequest request, User user)
		throws PrismsException
	{
		if(user instanceof DummyUser)
			return theDummyAuth;
		else
			return new USSessionAuth(user);
	}
}
