/**
 * ImagePlugin.java Created Jan 25, 2008 by Andrew Butler, PSL
 */
package prisms.arch;

/**
 * A special type of plugin that can generate images
 */
public interface ImagePlugin extends AppPlugin
{
	/**
	 * Generates an image, writing it to the output
	 * 
	 * @param method For use by the plugin implementation to select or format the image
	 * @param format The format for the image output (e.g. "png")
	 * @param xOffset The x-offset to build the image for
	 * @param yOffset The y-offset to build the image for
	 * @param refWidth The width of the image to offset by
	 * @param refHeight The height of the image to offset by
	 * @param imWidth The width of the image to write
	 * @param imHeight The height of the image to write
	 * @param output The output stream to write the image to
	 * @throws java.io.IOException If a problem occurs writing the image to the stream
	 */
	void writeImage(String method, String format, int xOffset, int yOffset, int refWidth,
		int refHeight, int imWidth, int imHeight, java.io.OutputStream output)
		throws java.io.IOException;
}
