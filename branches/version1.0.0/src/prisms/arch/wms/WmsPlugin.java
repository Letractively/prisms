/**
 * WmsPlugin.java Created Mar 18, 2009 by Andrew Butler, PSL
 */
package prisms.arch.wms;

import org.json.simple.JSONObject;

import prisms.arch.AppPlugin;

/**
 * WMS support in the PRISMS architecture.
 */
public interface WmsPlugin extends AppPlugin
{
	/**
	 * Returns a standard WMS capabilities XML describing this plugin's capabilities in response to
	 * a {@link PrismsWmsRequest.RequestType#GetCapabilities} request type
	 * 
	 * @param request The WMS request
	 * @param event The json data that was passed with the response
	 * @return The capabilities XML to return to the client
	 */
	String getCapabilities(PrismsWmsRequest request, JSONObject event);

	/**
	 * Returns a standard WMS list XML describing this plugin's capabilities in response to a
	 * {@link PrismsWmsRequest.RequestType#GetList} request type
	 * 
	 * @param request The WMS request
	 * @param event The json data that was passed with the response
	 * @return The list XML to return to the client
	 */
	String getList(PrismsWmsRequest request, JSONObject event);

	/**
	 * Draws a map overlay to an output stream in response to a
	 * {@link PrismsWmsRequest.RequestType#Map} request type
	 * 
	 * @param request The WMS request
	 * @param event The json data that was passed with the response
	 * @param output The ouput stream to write the image to
	 * @throws java.io.IOException If an error occurs writing the image
	 */
	void drawMapOverlay(PrismsWmsRequest request, JSONObject event, java.io.OutputStream output)
		throws java.io.IOException;

	/**
	 * Returns feature info for a particular point in response to a
	 * {@link PrismsWmsRequest.RequestType#GetFeatureInfo} request type
	 * 
	 * @param request The WMS request
	 * @param event The json data that was passed with the response
	 * @return HTML text response to the feature info request
	 */
	String getFeatureInfo(PrismsWmsRequest request, JSONObject event);

	/**
	 * Responds to a WMS request with {@link PrismsWmsRequest.RequestType#Other} request parameter
	 * 
	 * @param request The WMS request
	 * @param event The json data that was passed with the response
	 * @return HTML text response to the request
	 */
	String respond(PrismsWmsRequest request, JSONObject event);
}
