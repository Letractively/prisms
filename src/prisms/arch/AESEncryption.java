package prisms.arch;

import java.io.IOException;

import javax.crypto.Cipher;

import org.json.simple.JSONObject;

public class AESEncryption implements Encryption
{
	private Cipher theCipher;

	private java.security.Key theKey;

	private java.security.spec.AlgorithmParameterSpec theParamSpec;

	public JSONObject getParams()
	{
		JSONObject ret = new JSONObject();
		ret.put("type", "AES");
		return ret;
	}

	public void init(long [] key, java.util.Map<String, Object> params)
	{ // Java JCE AES encryption--not working yet
		javax.crypto.KeyGenerator gen;
		try
		{
			gen = javax.crypto.KeyGenerator.getInstance("AES");
		} catch(java.security.NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("Could not access AES encryption", e);
		}
		gen.init(128); // Strong encryption

		// Generate the secret key specs.
		// javax.crypto.SecretKey skey = gen.generateKey();
		// byte [] raw = skey.getEncoded();
		// byte [] raw = key.getBytes();

		theKey = new javax.crypto.spec.SecretKeySpec(gen.generateKey().getEncoded(),
			"AES/CBC/PKCS5Padding");

		byte [] ivbytes = new byte[16];
		try
		{
			java.security.SecureRandom.getInstance("SHA1PRNG").nextBytes(ivbytes);
		} catch(java.security.NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("Could not access SHA1 random generator", e);
		}
		theParamSpec = new javax.crypto.spec.IvParameterSpec(ivbytes);

		// Instantiate the cipher

		try
		{
			theCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		} catch(java.security.GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not access AES encryption", e);
		}
	}

	public String encrypt(String text) throws IOException
	{
		try
		{
			theCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, theKey, theParamSpec);
		} catch(java.security.GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not perform AES encryption", e);
		}
		byte [] encrypted;
		try
		{
			encrypted = theCipher.doFinal(text.getBytes());
		} catch(java.security.GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not perform AES encryption", e);
		}
		return new sun.misc.BASE64Encoder().encode(encrypted);
	}

	public String decrypt(String encrypted) throws IOException
	{
		try
		{
			theCipher.init(javax.crypto.Cipher.DECRYPT_MODE, theKey, theParamSpec);
		} catch(java.security.GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not perform AES decryption", e);
		}
		byte [] data = new sun.misc.BASE64Decoder().decodeBuffer(encrypted);
		byte [] decrypted;
		try
		{
			decrypted = theCipher.doFinal(data);
		} catch(java.security.GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not perform AES decryption", e);
		}
		return new String(decrypted);
	}

	public void dispose()
	{
	}

	public String toString()
	{
		return "AES with key " + theKey;
	}
}
