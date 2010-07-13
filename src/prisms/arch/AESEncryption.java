package prisms.arch;

import java.io.IOException;

import javax.crypto.Cipher;

import org.json.simple.JSONObject;

/**
 * Implements AES in counter mode. 128, 192, and 256 bit encryption is supported
 */
public class AESEncryption implements Encryption
{
	private Cipher theEncryptCipher;

	private Cipher theDecryptCipher;

	private java.security.Key theKey;

	private int theBits;

	private javax.crypto.spec.IvParameterSpec theParamSpec;

	public JSONObject getParams()
	{
		JSONObject ret = new JSONObject();
		ret.put("type", "AES");
		ret.put("mode", "CBC");
		ret.put("bits", new Integer(theBits));
		org.json.simple.JSONArray iv = new org.json.simple.JSONArray();
		for(int i = 0; i < theParamSpec.getIV().length; i++)
			iv.add(new Integer(theParamSpec.getIV()[i]));
		ret.put("iv", iv);
		return ret;
	}

	public void init(long [] key, java.util.Map<String, String> params)
	{ // Java JCE AES encryption--not working yet
		theBits = Integer.parseInt(params.get("bits"));
		byte [] rawKey = genCipherKey(key, theBits);

		theKey = new javax.crypto.spec.SecretKeySpec(rawKey, "AES");

		byte [] ivbytes = new byte [16];
		try
		{
			java.security.SecureRandom.getInstance("SHA1PRNG").nextBytes(ivbytes);
		} catch(java.security.NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("Could not access SHA1 random generator", e);
		}
		theParamSpec = new javax.crypto.spec.IvParameterSpec(ivbytes);

		// Instantiate the ciphers

		try
		{
			theEncryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			theEncryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, theKey, theParamSpec);
			theDecryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			theDecryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, theKey, theParamSpec);
		} catch(java.security.GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not access AES encryption", e);
		}
	}

	public String encrypt(String text, java.nio.charset.Charset charSet) throws IOException
	{
		byte [] encrypted;
		try
		{
			encrypted = theEncryptCipher.doFinal(text.getBytes(charSet.name()));
		} catch(java.security.GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not perform AES encryption", e);
		}
		return new sun.misc.BASE64Encoder().encode(encrypted);
	}

	public String decrypt(String encrypted, java.nio.charset.Charset charSet) throws IOException
	{
		byte [] data = new sun.misc.BASE64Decoder().decodeBuffer(encrypted);
		byte [] decrypted;
		try
		{
			decrypted = theDecryptCipher.doFinal(data);
		} catch(java.security.GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not perform AES decryption", e);
		}
		return new String(decrypted, charSet.name());
	}

	public void dispose()
	{
	}

	byte [] genCipherKey(long [] key, int bits)
	{
		if(bits != 128 && bits != 192 && bits != 256)
			throw new IllegalArgumentException("Only 128, 192, and 256 bit encryption supported");
		byte [] ret = new byte [bits / 8];
		long mask = 0xFF;
		int shift = 0;
		for(int i = 0; i < ret.length; i++)
		{
			if(i != 0 && i % key.length == 0)
			{
				mask <<= 8;
				shift += 8;
			}
			ret[i] = (byte) ((key[i % key.length] & mask) >>> shift);
		}
		return ret;
	}

	public String toString()
	{
		return "AES";
	}
}
