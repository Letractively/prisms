/**
 * OpenMapWMS.java Created Mar 19, 2009 by Andrew Butler, PSL
 */
package prisms.arch.wms;

import java.util.Properties;

import org.json.simple.JSONObject;

import com.bbn.openmap.LatLonPoint;
import com.bbn.openmap.proj.LLXY;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.proj.Projection;

/** A simple OpenMap abstract implementation of the WMS standard for PRISMS */
public abstract class OpenMapWMS extends prisms.ui.PrismsOpenMapPlugin implements WmsPlugin
{
	/**
	 * Creates a WMS server
	 * 
	 * @param omProps The properties to initialize OpenMap with
	 */
	public OpenMapWMS(Properties omProps)
	{
		super(omProps);
	}

	@Override
	public void initClient()
	{
	}

	@Override
	public void processEvent(org.json.simple.JSONObject evt)
	{
		throw new IllegalArgumentException(getName() + " event not recognized: " + evt);
	}

	public synchronized void drawMapOverlay(PrismsWmsRequest request, JSONObject event,
		java.io.OutputStream output) throws java.io.IOException
	{
		String format = request.getFormat().toLowerCase();
		if(format.endsWith("png"))
			getImageServer().setFormatter(new com.bbn.openmap.image.PNGImageIOFormatter());
		else if(format.endsWith("jpg") || format.endsWith("jpeg"))
			getImageServer().setFormatter(new com.bbn.openmap.image.SunJPEGFormatter());
		else if(format.endsWith("gif"))
			getImageServer().setFormatter(new com.bbn.openmap.image.GIFImageIOFormatter());
		else
			throw new IllegalArgumentException("Unrecognized image format " + format);
		int layersMask = 0;
		int bitMask = 1;
		Projection proj = createProjection(request);
		com.bbn.openmap.Layer[] layers = getImageServer().getLayers();
		for(com.bbn.openmap.Layer layer : layers)
		{
			if(request.containsLayer(layer.getName()))
				layersMask |= bitMask;
			bitMask <<= 1;
		}
		if(request.getBackground() != null)
			getImageServer().setBackground(request.getBackground());
		output.write(getImageServer().createImage(proj, request.getWidth(), request.getHeight(),
			layersMask));
	}

	/**
	 * Creates a projection for a WMS request's settings
	 * 
	 * @param request The WMS request to create the projection for
	 * @return The projection to use for the request
	 */
	protected Projection createProjection(PrismsWmsRequest request)
	{
		BoundingBox box = request.getBounds();
		LatLonPoint center = new LatLonPoint(box.getCenterLat(), box.getCenterLon());
		LLXY ret = new LLXY(center, 35000000, request.getWidth(), request.getHeight());
		LatLonPoint ul = new LatLonPoint(box.maxLat, box.minLon);
		LatLonPoint lr = new LatLonPoint(box.minLat, box.maxLon);
		ret.setScale(ProjMath.getScale(ul, lr, ret));
		return ret;
	}

	/**
	 * Gets a lat/lon point defined by the given x/y pixel point in a request
	 * 
	 * @param request The WMS request defining the projection
	 * @param x The x-pixel on the screen
	 * @param y The y-pixel on the screen
	 * @return The point represented by the x/y point given
	 */
	public LatLonPoint getPoint(PrismsWmsRequest request, int x, int y)
	{
		return createProjection(request).inverse(x, y);
	}
}
