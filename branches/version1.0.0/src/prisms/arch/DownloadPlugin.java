/**
 * DownloadPlugin.java Created Apr 16, 2009 by Andrew Butler, PSL
 */
package prisms.arch;

/**
 * A plugin that can be used to download a file to the client
 */
public interface DownloadPlugin extends AppPlugin
{
	/**
	 * Gets the MIME type of the file that will be created from the given event. Some examples are:
	 * <ul>
	 * <li><b>JSON:</b> text/json</li>
	 * <li><b>TEXT:</b> text/plain</li>
	 * <li><b>HTML:</b> text/html</li>
	 * <li><b>Rich Text Format:</b> text/rtf</li>
	 * </ul>
	 * If this method returns null, "application/octet-stream" will be used to download typeless
	 * file
	 * 
	 * @param event The event with parameters to use in generating the download
	 * @return The content type of the download
	 */
	String getContentType(org.json.simple.JSONObject event);

	/**
	 * Gets the name to download the file as
	 * 
	 * @param event The event with parameters to use in generating the download
	 * @return The name of the file to save the data as on the client
	 */
	String getFileName(org.json.simple.JSONObject event);

	/**
	 * Writes the download stream
	 * 
	 * @param event The event with parameters to use in generating the download
	 * @param stream The output stream to write the download data to
	 * @throws java.io.IOException If an error occurs writing the stream
	 */
	void doDownload(org.json.simple.JSONObject event, java.io.OutputStream stream)
		throws java.io.IOException;
}
