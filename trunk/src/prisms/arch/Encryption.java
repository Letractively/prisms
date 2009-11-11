/**
 * Encryption.java Created Mar 2, 2009 by Andrew Butler, PSL
 */
package prisms.arch;

import javax.crypto.Cipher;

/**
 * Performs encryption and decryption for the PRISMS architecture
 */
public class Encryption
{
	private static final String TYPE = "blowfish";

	static class BlowfishEncryption
	{
		String key;

		blowfishj.BlowfishECB cipher;

		public String toString()
		{
			return "Blowfish ECB with key " + key;
		}
	}

	static class AESEncryption
	{
		Cipher cipher;

		java.security.Key key;

		java.security.spec.AlgorithmParameterSpec paramSpec;

		public String toString()
		{
			return "AES with key " + key;
		}
	}

	/**
	 * @param key The key to use for encryption
	 * @return The object that will be used to perform the encryption
	 */
	public static Object getEncryption(String key)
	{
		if(TYPE.equals("blowfish"))
		{ // Simple blowfish encryption
			BlowfishEncryption ret = new BlowfishEncryption();
			ret.key = key;
			ret.cipher = new blowfishj.BlowfishECB(key.getBytes(), 0, key.length());
			return ret;
		}
		else if(TYPE.equals("AES"))
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

			AESEncryption ret = new AESEncryption();
			ret.key = new javax.crypto.spec.SecretKeySpec(gen.generateKey().getEncoded(),
				"AES/CBC/PKCS5Padding");

			byte [] ivbytes = new byte [16];
			try
			{
				java.security.SecureRandom.getInstance("SHA1PRNG").nextBytes(ivbytes);
			} catch(java.security.NoSuchAlgorithmException e)
			{
				throw new IllegalStateException("Could not access SHA1 random generator", e);
			}
			ret.paramSpec = new javax.crypto.spec.IvParameterSpec(ivbytes);

			// Instantiate the cipher

			try
			{
				ret.cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			} catch(java.security.GeneralSecurityException e)
			{
				throw new IllegalStateException("Could not access AES encryption", e);
			}
			return ret;
		}
		else
			throw new IllegalStateException("Encryption type " + TYPE + " not recognized");
	}

	/**
	 * Encrypts a data string
	 * 
	 * @param encryption The encryption object (@see {@link #getEncryption(String)})
	 * @param data The data to encrypt
	 * @return The encrypted data
	 */
	public static String encrypt(Object encryption, String data)
	{
		if(encryption instanceof BlowfishEncryption)
		{
			int size = data.length();
			int blockSize = blowfishj.BlowfishECB.BLOCKSIZE;
			if(size % blockSize > 0)
				size += (blockSize - size % blockSize);
			byte [] input = new byte [size];
			System.arraycopy(data.getBytes(), 0, input, 0, data.length());
			byte [] ret = new byte [size];
			((BlowfishEncryption) encryption).cipher.encrypt(input, 0, ret, 0, size);
			return new sun.misc.BASE64Encoder().encode(ret);
		}
		else if(encryption instanceof AESEncryption)
		{
			AESEncryption aes = (AESEncryption) encryption;
			try
			{
				aes.cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, aes.key, aes.paramSpec);
			} catch(java.security.GeneralSecurityException e)
			{
				throw new IllegalStateException("Could not perform AES encryption", e);
			}
			byte [] encrypted;
			try
			{
				encrypted = aes.cipher.doFinal(data.getBytes());
			} catch(java.security.GeneralSecurityException e)
			{
				throw new IllegalStateException("Could not perform AES encryption", e);
			}
			return new sun.misc.BASE64Encoder().encode(encrypted);
		}
		else
			throw new IllegalArgumentException("Unrecognized encryption: "
				+ encryption.getClass().getName());
	}

	/**
	 * Decrypts an encrypted data string
	 * 
	 * @param encryption The encryption object (@see {@link #getEncryption(String)})
	 * @param encrypted The data to decrypt
	 * @return The decrypted data
	 * @throws java.io.IOException If the data cannot be interpretd
	 */
	public static String decrypt(Object encryption, String encrypted) throws java.io.IOException
	{
		if(encrypted.startsWith("[") && encrypted.endsWith("]"))
			return encrypted;
		else if(encryption instanceof BlowfishEncryption)
		{
			byte [] data = new sun.misc.BASE64Decoder().decodeBuffer(encrypted);
			byte [] ret = new byte [data.length];
			((BlowfishEncryption) encryption).cipher.decrypt(data, 0, ret, 0, ret.length);
			int retSize = ret.length;
			while(retSize > 0 && ret[retSize - 1] < ' ')
				retSize--;
			if(retSize != ret.length)
			{
				byte [] realRet = new byte [retSize];
				System.arraycopy(ret, 0, realRet, 0, retSize);
				ret = realRet;
			}
			return new String(ret);
		}
		else if(encryption instanceof AESEncryption)
		{
			AESEncryption aes = (AESEncryption) encryption;
			try
			{
				aes.cipher.init(javax.crypto.Cipher.DECRYPT_MODE, aes.key, aes.paramSpec);
			} catch(java.security.GeneralSecurityException e)
			{
				throw new IllegalStateException("Could not perform AES decryption", e);
			}
			byte [] data = new sun.misc.BASE64Decoder().decodeBuffer(encrypted);
			byte [] decrypted;
			try
			{
				decrypted = aes.cipher.doFinal(data);
			} catch(java.security.GeneralSecurityException e)
			{
				throw new IllegalStateException("Could not perform AES decryption", e);
			}
			return new String(decrypted);
		}
		else
			throw new IllegalArgumentException("Unrecognized encryption: "
				+ encryption.getClass().getName());
	}

	/**
	 * Disposes of the encryption object
	 * 
	 * @param encryption The encryption object to dispose of
	 */
	public static void dispose(Object encryption)
	{
		if(encryption instanceof BlowfishEncryption)
			((BlowfishEncryption) encryption).cipher.cleanUp();
		else if(encryption instanceof AESEncryption)
		{ /*No final actions necessary */}
		else
			throw new IllegalArgumentException("Unrecognized encryption: "
				+ encryption.getClass().getName());
	}
}
