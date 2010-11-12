/*
 * UserSourceAuthenticator.java Created Nov 1, 2010 by Andrew Butler, PSL
 */
package prisms.impl;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.json.simple.JSONObject;

import prisms.arch.*;
import prisms.arch.ds.User;
import prisms.arch.ds.UserSource;
import prisms.util.ArrayUtils;

/** Authenticates using the usernames/passwords stored in the UserSource */
public class UserSourceAuthenticator implements PrismsAuthenticator
{
	static final Logger log = Logger.getLogger(UserSourceAuthenticator.class);

	private class USAuthInfo
	{
		private final User theUser;

		private prisms.arch.ds.Hashing theHashing;

		private long [] theKey;

		private prisms.arch.Encryption theEncryption;

		private int theLoginsFailed;

		private boolean authChanged;

		private User theAnonymousUser;

		private long userLastChecked;

		private boolean hasLoggedIn;

		USAuthInfo(User user) throws PrismsException
		{
			theUser = user;
			theHashing = theUserSource.getHashing();
			checkAuthenticationData();
		}

		/**
		 * Checks this session holder's user against the data source periodically to see whether the
		 * password has changed or the user has been locked.
		 * 
		 * @throws PrismsException
		 */
		void checkAuthenticationData() throws PrismsException
		{
			long time = System.currentTimeMillis();
			if(time - userLastChecked < theUserCheckPeriod)
				return;
			userLastChecked = time;
			theAnonymousUser = theUserSource.getUser(null);
			long [] newKey = theUserSource.getKey(theUser, theHashing);
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

		Object authenticated(HttpServletRequest request, boolean secure) throws PrismsException
		{
			checkAuthenticationData();
			String dataStr = request.getParameter("data");
			if(dataStr != null && isEncrypted(dataStr))
			{
				if(theEncryption == null)
					return new AuthenticationError("User " + theUser.getName()
						+ " does not have a password set--consult your admin", true);
				// For some reason, "+" gets mis-translated at a space
				dataStr = dataStr.replaceAll(" ", "+");
				String encryptedText = dataStr;
				try
				{
					dataStr = decrypt(request, dataStr);
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
					loginFailed();
					if(theUser.isLocked())
						return new AuthenticationError(
							"Too many incorrect password attempts.\nUser " + theUser.getName()
								+ " is locked. Contact your admin", true);
					else if(authChanged)
						return new AuthenticationError(theUser + "'s password has been changed."
							+ " Use the new password or contact your admin.", true);
					else
						return new AuthenticationError("Incorrect password for user "
							+ theUser.getName(), true);
				}

				// Decryption succeeded
				if(dataStr.length() < 20)
					return new AuthenticationError("Data string null or too short"
						+ "--at least 20 characters of data must be included to verify encryption."
						+ "  Use \"-XXSERVERPADDING...\" for padding", false);
				// Remove "-XXSERVERPADDING" comment if present
				int commentIdx = dataStr.indexOf("-XXSERVERPADDING");
				if(commentIdx >= 0)
					dataStr = dataStr.substring(0, commentIdx);
				loginSucceeded();
			}
			else if(theUser.equals(theAnonymousUser))
				return dataStr;
			else if(!hasLoggedIn || !loginOnce || !secure)
				return new AuthenticationError(null, true);
			return dataStr;
		}

		private String decrypt(HttpServletRequest request, String encrypted)
			throws java.io.IOException
		{
			if(theEncryption == null)
				return encrypted;
			java.nio.charset.Charset charSet;
			if(request.getCharacterEncoding() == null)
				charSet = java.nio.charset.Charset.defaultCharset();
			else
				charSet = java.nio.charset.Charset.forName(request.getCharacterEncoding());
			return theEncryption.decrypt(encrypted, charSet);
		}

		JSONObject requestLogin() throws PrismsException
		{
			checkAuthenticationData();
			JSONObject evt = new JSONObject();
			if(theEncryption != null)
			{
				evt.put("method", "startEncryption");
				evt.put("encryption", theEncryption.getParams());
				JSONObject hashing = theHashing.toJson();
				hashing.put("user", theUser.getName());
				evt.put("hashing", hashing);
			}
			else
				evt.put("method", "login");
			return evt;
		}

		String encrypt(HttpServletRequest request, String data)
		{
			java.nio.charset.Charset charSet = null;
			String acceptChs = request.getHeader("Accept-Charset");
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
			try
			{
				return theEncryption.encrypt(data, charSet);
			} catch(IOException e)
			{
				throw new IllegalStateException("Could not encrypt data", e);
			}
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
				theUser.setLocked(true);
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

		boolean needsPasswordChange(HttpServletRequest request) throws PrismsException
		{
			String dataStr = request.getParameter("data");
			if(!isEncrypted(dataStr))
				return false;
			long pwdExp;
			pwdExp = theUserSource.getPasswordExpiration(theUser);
			return pwdExp > 0 && pwdExp < System.currentTimeMillis();
		}

		JSONObject requestPasswordChange() throws PrismsException
		{
			checkAuthenticationData();
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
			JSONObject ret = new JSONObject();
			ret.put("constraints", prisms.arch.service.PrismsSerializer.serializeConstraints(pc));
			ret.put("hashing", theUserSource.getHashing().toJson());
			return ret;
		}

		AuthenticationError changePassword(JSONObject event)
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

		void destroy()
		{
			if(theEncryption != null)
				theEncryption.dispose();
		}
	}

	UserSource theUserSource;

	private String theUserParam;

	private Class<? extends prisms.arch.Encryption> theEncryptionClass;

	java.util.HashMap<String, String> theEncryptionProperties;

	boolean loginOnce;

	long theUserCheckPeriod = 60000;

	int theLoginFailTolerance = 3;

	public void configure(Element configEl, UserSource userSource, PrismsApplication [] apps)
	{
		theUserSource = userSource;
		theUserParam = configEl.elementTextTrim("userparam");
		if(theUserParam == null)
			theUserParam = "user";
		theEncryptionProperties = new java.util.HashMap<String, String>();
		Element encryptionEl = configEl.element("encryption");
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
			for(Element propEl : (java.util.List<Element>) encryptionEl.elements())
			{
				if(propEl.getName().equals("class"))
					continue;
				theEncryptionProperties.put(propEl.getName(), propEl.getTextTrim());
			}
		}
		else
			theEncryptionClass = BlowfishEncryption.class;
		loginOnce = "true".equalsIgnoreCase(configEl.elementTextTrim("loginOnce"));
	}

	static boolean isEncrypted(String dataStr)
	{
		return dataStr != null && dataStr.length() >= 2 && dataStr.charAt(0) != '{';
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

	public boolean recognized(HttpServletRequest request)
	{
		return true; // This is a default authenticator--it may be used in any case as a last resort
	}

	public User getUser(HttpServletRequest request) throws PrismsException
	{
		String userName = request.getParameter(theUserParam);
		if(userName == null)
		{
			String data = request.getParameter("data");
			if(data != null && !isEncrypted(data))
			{
				org.json.simple.JSONObject desData;
				try
				{
					desData = (org.json.simple.JSONObject) org.json.simple.JSONValue.parse(data);
					userName = (String) desData.get(theUserParam);
				} catch(Throwable e)
				{
					log.error("Could not parse data", e);
				}
			}
		}
		User ret = theUserSource.getUser(userName);
		if(ret == null)
			throw new PrismsException("No such user \"" + userName + "\"");
		return ret;
	}

	public Object createAuthenticationInfo(HttpServletRequest request, User user)
		throws PrismsException
	{
		return new USAuthInfo(user);
	}

	public Object getAuthenticatedData(HttpServletRequest request, Object authInfo, boolean secure)
		throws PrismsException
	{
		return ((USAuthInfo) authInfo).authenticated(request, secure);
	}

	public JSONObject requestLogin(HttpServletRequest request, Object authInfo)
		throws PrismsException
	{
		return ((USAuthInfo) authInfo).requestLogin();
	}

	public String encrypt(HttpServletRequest request, String data, Object authInfo)
		throws PrismsException
	{
		return ((USAuthInfo) authInfo).encrypt(request, data);
	}

	public boolean needsPasswordChange(HttpServletRequest request, Object authInfo)
		throws PrismsException
	{
		return ((USAuthInfo) authInfo).needsPasswordChange(request);
	}

	public JSONObject requestPasswordChange(HttpServletRequest request, Object authInfo)
		throws PrismsException
	{
		return ((USAuthInfo) authInfo).requestPasswordChange();
	}

	public AuthenticationError changePassword(HttpServletRequest request, Object authInfo,
		JSONObject event) throws PrismsException
	{
		return ((USAuthInfo) authInfo).changePassword(event);
	}

	public void destroy(Object authInfo) throws PrismsException
	{
		((USAuthInfo) authInfo).destroy();
	}
}
