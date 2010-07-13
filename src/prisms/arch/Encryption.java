/**
 * Encryption.java Created Mar 2, 2009 by Andrew Butler, PSL
 */
package prisms.arch;

import java.io.IOException;

/**
 * Performs encryption and decryption for the PRISMS architecture
 */
public interface Encryption
{
	/**
	 * Initializes the encryption with a key and parameters
	 * 
	 * @param key The encryption key data to use
	 * @param params The encryption parameters to use
	 */
	public void init(long [] key, java.util.Map<String, String> params);

	/**
	 * @return Parameters necessary to duplicate this encryption on the client side
	 */
	public org.json.simple.JSONObject getParams();

	/**
	 * Encrypts a data string
	 * 
	 * @param text The data to encrypt
	 * @param charSet The character set to use to encode the data
	 * @return The encrypted data
	 * @throws IOException If an error occurs encrypting the data
	 */
	public String encrypt(String text, java.nio.charset.Charset charSet) throws IOException;

	/**
	 * Decrypts an encrypted data string
	 * 
	 * @param encrypted The data to decrypt
	 * @param charSet The character set to use to decode the data
	 * @return The decrypted data
	 * @throws java.io.IOException If the data cannot be interpreted
	 */
	public String decrypt(String encrypted, java.nio.charset.Charset charSet) throws IOException;

	/**
	 * Disposes of the encryption object
	 */
	public void dispose();
}
