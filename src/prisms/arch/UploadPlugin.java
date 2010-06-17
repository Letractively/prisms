/**
 * UploadPlugin.java Created Apr 16, 2009 by Andrew Butler, PSL
 */
package prisms.arch;

/**
 * An UploadPlugin is a plugin that deals with an uploaded file
 */
public interface UploadPlugin extends AppPlugin
{
	/**
	 * Processes the uploaded file. The contents of the file MUST be dealt with in this method. When
	 * the method returns, the contents will be purged and the input stream will no longer be valid.
	 * 
	 * @param event The event that caused the upload
	 * @param fileName The name of the uploaded file
	 * @param contentType The MIME type of the uploaded file
	 * @param input The input stream to get the file's contents with
	 * @param size The size of the file
	 * @throws java.io.IOException If an error occurs getting the contents of the file
	 */
	void doUpload(org.json.simple.JSONObject event, String fileName, String contentType,
		java.io.InputStream input, long size) throws java.io.IOException;
}
