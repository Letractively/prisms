package prisms.arch;

import java.io.IOException;

import org.json.simple.JSONObject;

/**
 * The blowfish encryption implementation. Uses blowfishj.
 */
public class BlowfishEncryption implements Encryption
{
	private static String BASE_64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

	private int theMaxKeyLength;

	private String theKey;

	private blowfishj.BlowfishECB theCipher;

	/**
	 * Creates a blowfish encryption implementation
	 */
	public BlowfishEncryption()
	{
		theMaxKeyLength = 448 / 8;
	}

	public JSONObject getParams()
	{
		JSONObject ret = new JSONObject();
		ret.put("type", "blowfish");
		return ret;
	}

	public void init(long [] key, java.util.Map<String, String> params)
	{
		theKey = createKey(key);
		theCipher = new blowfishj.BlowfishECB(theKey.getBytes(), 0, theKey.length());
	}

	private String createKey(long [] longKey)
	{
		String [] keyStringEls = new String [longKey.length];
		for(int h = 0; h < longKey.length; h++)
			keyStringEls[h] = toKeyString(longKey[h]);
		while(isTooBig(keyStringEls))
			keyStringEls = downsize(keyStringEls);
		String ret = "";
		for(int k = 0; k < keyStringEls.length; k++)
			ret += keyStringEls[k];
		return ret;
	}

	private boolean isTooBig(String [] keys)
	{
		int size = 0;
		for(int k = 0; k < keys.length; k++)
			size += keys[k].length() * 8;
		return size > theMaxKeyLength;
	}

	private String [] downsize(String [] keys)
	{
		// Cancels the highest 64-base digit in each element to reduce its size with minimal
		// complexity loss
		boolean allOnes = true;
		for(int k = 0; k < keys.length; k++)
			if(keys[k].length() > 1)
			{
				keys[k] = keys[k].substring(1);
				allOnes = false;
			}
		if(allOnes)
			keys = prisms.util.ArrayUtils.remove(keys, 0);
		return keys;
	}

	private static String toKeyString(long value)
	{
		String ret = "";
		while(value > 0)
		{
			ret += BASE_64.charAt((int) (value % 64));
			value /= 64;
		}
		return ret;
	}

	public String encrypt(String text, java.nio.charset.Charset charSet) throws IOException
	{
		int size = text.length();
		int blockSize = blowfishj.BlowfishECB.BLOCKSIZE;
		if(size % blockSize > 0)
			size += (blockSize - size % blockSize);
		byte [] input = new byte [size];
		System.arraycopy(text.getBytes(charSet.name()), 0, input, 0, text.length());
		byte [] ret = new byte [size];
		theCipher.encrypt(input, 0, ret, 0, size);
		return new sun.misc.BASE64Encoder().encode(ret);
	}

	public String decrypt(String encrypted, java.nio.charset.Charset charSet) throws IOException
	{
		byte [] data = new sun.misc.BASE64Decoder().decodeBuffer(encrypted);
		byte [] ret = new byte [data.length];
		theCipher.decrypt(data, 0, ret, 0, ret.length);
		int retSize = ret.length;
		while(retSize > 0 && ret[retSize - 1] < ' ')
			retSize--;
		if(retSize != ret.length)
		{
			byte [] realRet = new byte [retSize];
			System.arraycopy(ret, 0, realRet, 0, retSize);
			ret = realRet;
		}
		return new String(ret, charSet.name());
	}

	public void dispose()
	{
		theCipher.cleanUp();
	}

	public String toString()
	{
		return "Blowfish ECB with key " + theKey;
	}
}
