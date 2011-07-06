package prisms.arch;

import java.io.IOException;

import org.json.simple.JSONObject;

/** The blowfish encryption implementation. Uses blowfishj. */
public class BlowfishEncryption implements Encryption
{
	private static String BASE_64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

	private int theMaxKeyLength;

	private String theKey;

	private blowfishj.BlowfishECB theCipher;

	/** Creates a blowfish encryption implementation */
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
		StringBuilder ret = new StringBuilder();
		for(int k = 0; k < keyStringEls.length; k++)
			ret.append(keyStringEls[k]);
		return ret.toString();
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
		if(value < 0)
			value = -value;
		StringBuilder ret = new StringBuilder();
		while(value > 0)
		{
			ret.append(BASE_64.charAt((int) (value % 64)));
			value /= 64;
		}
		return ret.toString();
	}

	public String encrypt(String text) throws IOException
	{
		byte [] textBytes;
		if(prisms.util.PrismsUtils.isJava6())
			textBytes = text.getBytes(CHAR_SET);
		else
			textBytes = text.getBytes(CHAR_SET.name());
		int size = textBytes.length;
		int blockSize = blowfishj.BlowfishECB.BLOCKSIZE;
		if(size % blockSize > 0)
			size += (blockSize - size % blockSize);
		byte [] input = new byte [size];
		System.arraycopy(textBytes, 0, input, 0, textBytes.length);
		byte [] ret = new byte [size];
		theCipher.encrypt(input, 0, ret, 0, size);

		// return new sun.misc.BASE64Encoder().encode(ret);
		// Sun whines about BASE64 being proprietary. Use apache instead...
		return new org.apache.commons.codec.binary.Base64().encodeToString(ret);
	}

	public String decrypt(String encrypted) throws IOException
	{
		// byte [] data = new sun.misc.BASE64Decoder().decodeBuffer(encrypted);
		// Sun whines about BASE64 being proprietary. Use apache instead...
		byte [] data = org.apache.commons.codec.binary.Base64.decodeBase64(encrypted);

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
		if(prisms.util.PrismsUtils.isJava6())
			return new String(ret, CHAR_SET);
		else
			return new String(ret, CHAR_SET.name());
	}

	public void dispose()
	{
		theCipher.cleanUp();
	}

	@Override
	protected void finalize()
	{
		dispose();
	}

	@Override
	public String toString()
	{
		return "Blowfish ECB " + (theKey == null ? "null" : Integer.toHexString(theKey.hashCode()));
	}
}
