/*
 * PrismsOpenMapPlugin.java Created Nov 11, 2009 by Andrew Butler, PSL
 */
package prisms.ui;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.ImagePlugin;
import prisms.arch.PrismsSession;

import com.bbn.openmap.LatLonPoint;
import com.bbn.openmap.Layer;
import com.bbn.openmap.proj.LLXY;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.proj.Projection;

/** An image plugin that displays a map */
public class PrismsOpenMapPlugin implements ImagePlugin
{
	private static final Logger log = Logger.getLogger(PrismsOpenMapPlugin.class);

	/** A listener to receive mouse clicks and drags on the map */
	public static interface MouseListener
	{
		/**
		 * Called when the user clicks a point without dragging
		 * 
		 * @param point The point the user clicked
		 */
		void mouseClicked(LatLonPoint point);

		/**
		 * Called when the user begins a drag action
		 * 
		 * @param point The starting point of the drag action
		 */
		void dragStart(LatLonPoint point);

		/**
		 * Called when the user moves the mouse during a drag action
		 * 
		 * @param start The starting point of the drag action
		 * @param point The current mouse location
		 */
		void dragging(LatLonPoint start, LatLonPoint point);

		/**
		 * Called when the user finishes a drag action
		 * 
		 * @param start The starting point of the drag action
		 * @param end The end point of the drag action
		 */
		void dragged(LatLonPoint start, LatLonPoint end);
	}

	/** The format used to print a point's longitude */
	public static final java.text.NumberFormat LAT_LON_FORMAT = new java.text.DecimalFormat(
		"0.0000");

	private PrismsSession theSession;

	private String theName;

	private com.bbn.openmap.image.ImageServer theImageServer;

	private float theMinLat;

	private float theMaxLat;

	private float theMinLon;

	private float theMaxLon;

	private float theVertPanAmount = 0.5f;

	private float theHorizPanAmount = 0.5f;

	private float theZoomAmount = 0.5f;

	private javax.swing.Action[] theActions;

	private java.util.ArrayList<MouseListener> theListeners;

	/**
	 * Creates an OpenMap image server
	 * 
	 * @param omProps The OpenMap properties to use to initialize this map
	 */
	public PrismsOpenMapPlugin(java.util.Properties omProps)
	{
		theMinLat = 20;
		theMaxLat = 60;
		theMinLon = -117;
		theMaxLon = -77;
		theImageServer = new com.bbn.openmap.image.ImageServer(omProps);
		theImageServer.setBackground(new java.awt.Color(137, 197, 249));
		theListeners = new java.util.ArrayList<MouseListener>();
	}

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
	}

	public void initClient()
	{
		draw();
	}

	/** @return This plugin's name */
	public String getName()
	{
		return theName;
	}

	/** @return This plugin's session */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/** @return This plugin's internal OpenMap image server */
	public com.bbn.openmap.image.ImageServer getImageServer()
	{
		return theImageServer;
	}

	/**
	 * Creates a projection for this server's settings
	 * 
	 * @param width The width of the image to create
	 * @param height The height of the image to create
	 * @return The projection to use for this server and the given dimensions
	 */
	protected Projection createProjection(int width, int height)
	{
		LatLonPoint center = new LatLonPoint((theMinLat + theMaxLat) / 2,
			(theMinLon + theMaxLon) / 2);
		LatLonPoint ul = new LatLonPoint(theMaxLat, theMinLon);
		LatLonPoint lr = new LatLonPoint(theMinLat, theMaxLon);
		LLXY ret = new LLXY(center, 35000000, width, height);
		ret.setScale(ProjMath.getScale(ul, lr, ret) * 2.0f);
		return ret;
	}

	/**
	 * Creates a projection for this server's settings
	 * 
	 * @param xOffset The x-offset of the image to create
	 * @param yOffset The y-offset of the image to create
	 * @param refWidth The width of the image that the x offset refers to
	 * @param refHeight The height of the image that the y offset refers to
	 * @param imWidth The width of the image to create
	 * @param imHeight The height of the image to create
	 * @return The projection to use for this server and the given dimensions
	 */
	protected Projection createProjection(int xOffset, int yOffset, int refWidth, int refHeight,
		int imWidth, int imHeight)
	{
		LatLonPoint center = new LatLonPoint((theMinLat + theMaxLat) / 2,
			(theMinLon + theMaxLon) / 2);
		LatLonPoint ul = new LatLonPoint(theMaxLat, theMinLon);
		LatLonPoint lr = new LatLonPoint(theMinLat, theMaxLon);
		LLXY ret = new LLXY(center, 35000000, refWidth, refHeight);
		ret.setScale(ProjMath.getScale(ul, lr, ret) * 2.0f);

		LatLonPoint newCenter = ret.inverse(imWidth / 2 + xOffset, imHeight / 2 + yOffset);
		/* OpenMap seems to like to use the vertical difference for scaling, which causes problems
		 * when the image height is the same as the reference height.  Setting the latitude
		 * difference to 0 causes the projection (LLXY) constructor to use the width for scaling */
		float latDiff;
		if(imHeight != refHeight)
			latDiff = (theMaxLat - theMinLat) * imHeight * 1.0f / refHeight / 2;
		else
			latDiff = 0;
		float lonDiff = theMaxLon - theMinLon;
		lonDiff *= imWidth * 1.0f / refWidth / 2;
		ul = new LatLonPoint(newCenter.radlat_ + latDiff, newCenter.radlon_ - lonDiff);
		lr = new LatLonPoint(newCenter.radlat_ - latDiff, newCenter.radlon_ + lonDiff);
		ret = new LLXY(newCenter, 35000000, imWidth, imHeight);
		ret.setScale(ProjMath.getScale(ul, lr, ret) * 2.0f);
		return ret;
	}

	public void processEvent(JSONObject evt)
	{
		if("moveCenter".equals(evt.get("method")))
		{
			moveCenter(((Number) evt.get("xOffset")).intValue(),
				((Number) evt.get("yOffset")).intValue(), ((Number) evt.get("width")).intValue(),
				((Number) evt.get("height")).intValue());
		}
		else if("mapClicked".equals(evt.get("method")))
		{
			mapClicked(((Number) evt.get("x")).intValue(), ((Number) evt.get("y")).intValue(),
				((Number) evt.get("width")).intValue(), ((Number) evt.get("height")).intValue());
		}
		else if("mapDragStarted".equals(evt.get("method")))
		{
			mapDragStarted(((Number) evt.get("x")).intValue(), ((Number) evt.get("y")).intValue(),
				((Number) evt.get("width")).intValue(), ((Number) evt.get("height")).intValue());
		}
		else if("mapDragging".equals(evt.get("method")))
		{
			mapDragging(((Number) evt.get("startX")).intValue(),
				((Number) evt.get("startY")).intValue(), ((Number) evt.get("dragX")).intValue(),
				((Number) evt.get("dragY")).intValue(), ((Number) evt.get("width")).intValue(),
				((Number) evt.get("height")).intValue());
		}
		else if("mapDragged".equals(evt.get("method")))
		{
			int x1, x2, y1, y2, width, height;
			try
			{
				x1 = ((Number) evt.get("x1")).intValue();
				x2 = ((Number) evt.get("x2")).intValue();
				y1 = ((Number) evt.get("y1")).intValue();
				y2 = ((Number) evt.get("y2")).intValue();
				width = ((Number) evt.get("width")).intValue();
				height = ((Number) evt.get("height")).intValue();
			} catch(Exception e)
			{
				throw new IllegalArgumentException(
					"Expected integers: x1, x2, y1, y2, width, height", e);
			}
			mapDragged(x1, y1, x2, y2, width, height);
		}
		else if("areaSelected".equals(evt.get("method")))
		{
			int x1, x2, y1, y2, width, height;
			try
			{
				x1 = ((Number) evt.get("x1")).intValue();
				x2 = ((Number) evt.get("x2")).intValue();
				y1 = ((Number) evt.get("y1")).intValue();
				y2 = ((Number) evt.get("y2")).intValue();
				width = ((Number) evt.get("width")).intValue();
				height = ((Number) evt.get("height")).intValue();
			} catch(Exception e)
			{
				throw new IllegalArgumentException(
					"Expected integers: x1, x2, y1, y2, width, height", e);
			}
			com.bbn.openmap.proj.Projection pro = createProjection(width, height);
			LatLonPoint omPoint1 = pro.inverse(x1, y1);
			LatLonPoint omPoint2 = pro.inverse(x2, y2);
			float lat1, lon1, lat2, lon2;
			lat1 = omPoint1.getLatitude();
			lon1 = omPoint1.getLongitude();
			lat2 = omPoint2.getLatitude();
			lon2 = omPoint2.getLongitude();
			// TODO: This WILL have problems around the date line
			if(lat1 > lat2)
			{
				float temp = lat1;
				lat1 = lat2;
				lat2 = temp;
			}
			if(lon1 > lon2)
			{
				float temp = lon1;
				lon1 = lon2;
				lon2 = temp;
			}
			// If the user just clicked, only recenter the map
			if(x2 - x1 < 2 || y2 - y1 < 2)
				move((lat1 + lat2) / 2, (lon1 + lon2) / 2);
			else
				setExtent(lat1, lon1, lat2, lon2);
			draw();
		}
		else if("panLeft".equals(evt.get("method")))
		{
			float width = theMaxLon - theMinLon;
			if(width < 0)
				width += 360;
			float newMinLon = theMinLon - width * theHorizPanAmount;
			float newMaxLon = theMaxLon - width * theHorizPanAmount;
			if(newMinLon <= -180)
			{
				newMinLon += 360;
				newMaxLon += 360;
			}
			theMinLon = newMinLon;
			theMaxLon = newMaxLon;
			draw();
		}
		else if("panRight".equals(evt.get("method")))
		{
			float width = theMaxLon - theMinLon;
			if(width < 0)
				width += 360;
			float newMinLon = theMinLon + width * theHorizPanAmount;
			float newMaxLon = theMaxLon + width * theHorizPanAmount;
			if(newMaxLon > 180)
			{
				newMinLon -= 360;
				newMaxLon -= 360;
			}
			theMinLon = newMinLon;
			theMaxLon = newMaxLon;
			draw();
		}
		else if("panUp".equals(evt.get("method")))
		{
			float height = theMaxLat - theMinLat;
			float newMinLat = theMinLat + height * theVertPanAmount;
			float newMaxLat = theMaxLat + height * theVertPanAmount;
			if(newMinLat > 90 - height)
				newMinLat = 90 - height;
			if(newMaxLat > 90)
				newMaxLat = 90;
			theMinLat = newMinLat;
			theMaxLat = newMaxLat;
			draw();
		}
		else if("panDown".equals(evt.get("method")))
		{
			float height = theMaxLat - theMinLat;
			float newMinLat = theMinLat - height * theVertPanAmount;
			float newMaxLat = theMaxLat - height * theVertPanAmount;
			if(newMinLat < -90)
				newMinLat = -90;
			if(newMaxLat < -90 + height)
				newMaxLat = -90 + height;
			theMinLat = newMinLat;
			theMaxLat = newMaxLat;
			draw();
		}
		else if("zoomIn".equals(evt.get("method")))
		{
			float cLat = (theMinLat + theMaxLat) / 2;
			float cLon = (theMinLon + theMaxLon) / 2;
			float width = theMaxLon - theMinLon;
			float height = theMaxLat - theMinLat;
			width *= (1 - theZoomAmount);
			height *= (1 - theZoomAmount);
			theMinLon = cLon - width / 2;
			theMaxLon = cLon + width / 2;
			theMinLat = cLat - height / 2;
			theMaxLat = cLat + height / 2;
			draw();
		}
		else if("zoomOut".equals(evt.get("method")))
		{
			float cLat = (theMinLat + theMaxLat) / 2;
			float cLon = (theMinLon + theMaxLon) / 2;
			float width = theMaxLon - theMinLon;
			float height = theMaxLat - theMinLat;
			width *= (1 + theZoomAmount);
			height *= (1 + theZoomAmount);
			if(width > 360)
			{
				height *= 360 / width;
				width = 360;
			}
			if(height > 180)
			{
				width *= 180 / height;
				height = 180;
			}
			theMinLon = cLon - width / 2;
			theMaxLon = cLon + width / 2;
			theMinLat = cLat - height / 2;
			theMaxLat = cLat + height / 2;
			draw();
		}
		else if("getPointActions".equals(evt.get("method")))
		{
			JSONObject retEvt = new JSONObject();
			retEvt.put("plugin", theName);
			retEvt.put("method", "setPointActions");
			retEvt.put(
				"pointActions",
				getPointActions(((Number) evt.get("x")).intValue(),
					((Number) evt.get("y")).intValue(), ((Number) evt.get("width")).intValue(),
					((Number) evt.get("height")).intValue()));
			theSession.postOutgoingEvent(retEvt);
		}
		else if("performAction".equals(evt.get("method")))
		{
			performAction((String) evt.get("action"), ((Number) evt.get("x")).intValue(),
				((Number) evt.get("y")).intValue(), ((Number) evt.get("width")).intValue(),
				((Number) evt.get("height")).intValue());
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}

	public void writeImage(String method, String format, int xOffset, int yOffset, int refWidth,
		int refHeight, int imWidth, int imHeight, OutputStream output) throws IOException
	{
		if(imWidth < 0)
			imWidth = 0;
		if(imHeight < 0)
			imHeight = 0;
		if(format.equalsIgnoreCase("png"))
			theImageServer.setFormatter(new com.bbn.openmap.image.PNGImageIOFormatter());
		else if(format.equalsIgnoreCase("jpg"))
			theImageServer.setFormatter(new com.bbn.openmap.image.SunJPEGFormatter());
		else if(format.equalsIgnoreCase("gif"))
			theImageServer.setFormatter(new com.bbn.openmap.image.GIFImageIOFormatter());
		else
			throw new IllegalArgumentException("Unrecognized image format " + format);
		Projection proj;
		if(xOffset == 0 && yOffset == 0)
			proj = createProjection(imWidth, imHeight);
		else
			proj = createProjection(xOffset, yOffset, refWidth, refHeight, imWidth, imHeight);
		synchronized(this)
		{
			Layer [] layers = theImageServer.getLayers();
			int layerInclude = 0;
			int layerMask = 1;
			for(Layer layer : layers)
			{
				if(layer.isVisible())
					layerInclude |= layerMask;
				layerMask <<= 1;
			}
			output.write(theImageServer.createImage(proj, imWidth, imHeight, layerInclude));
		}
	}

	void moveCenter(int xOffset, int yOffset, int width, int height)
	{
		LatLonPoint p = createProjection(width, height).inverse(width / 2 - xOffset,
			height / 2 - yOffset);
		move(p.getLatitude(), p.getLongitude());
		draw();
	}

	/** Causes the client to redraw its image */
	public void draw()
	{
		theSession.postOutgoingEvent(prisms.util.PrismsUtils.rEventProps("plugin", theName,
			"method", "resetImage"));
	}

	/**
	 * Sets the approximate lat/lon extents to display for this map. Since client dimensions may
	 * vary, this may not be exact.
	 * 
	 * @param minLat The minimum latitude to display
	 * @param minLon The minimum longitude to display
	 * @param maxLat The maximum latitude to display
	 * @param maxLon The maximum longitude to display
	 */
	public void setExtent(float minLat, float minLon, float maxLat, float maxLon)
	{
		theMinLat = minLat;
		theMinLon = minLon;
		theMaxLat = maxLat;
		theMaxLon = maxLon;
	}

	/**
	 * Moves the center of this map's view to the given coordinates
	 * 
	 * @param lat The latitude that is to be the center of the map's view
	 * @param lon The longitude that is to be the center of the map's view
	 */
	public void move(float lat, float lon)
	{
		float latDiff = theMaxLat - theMinLat;
		float lonDiff = theMaxLon - theMinLon;
		theMinLat = lat - latDiff / 2;
		theMaxLat = lat + latDiff / 2;
		theMinLon = lon - lonDiff / 2;
		theMaxLon = lon + lonDiff / 2;
	}

	/**
	 * Adds a listener to receive mouse events
	 * 
	 * @param mma The listener to notify of mouse events
	 */
	public void addMouseListener(MouseListener mma)
	{
		theListeners.add(mma);
	}

	/**
	 * Removes a listener from receiving mouse events
	 * 
	 * @param mma The listener to cease notification of mouse events
	 */
	public void removeMouseListener(MouseListener mma)
	{
		theListeners.remove(mma);
	}

	private JSONObject getPointActions(int x, int y, int width, int height)
	{
		LatLonPoint omPoint = createProjection(width, height).inverse(x, y);

		prisms.arch.event.PrismsEvent actionsEvent = new prisms.arch.event.PrismsEvent(
			"getUserActions", "plugin", theName, "point", omPoint, "actions",
			new javax.swing.Action [0]);
		theSession.fireEvent(actionsEvent);
		javax.swing.Action[] newActions = (javax.swing.Action[]) actionsEvent
			.getProperty("actions");
		theActions = newActions;

		JSONObject ret = new JSONObject();
		ret.put(
			"label",
			LAT_LON_FORMAT.format(omPoint.getLatitude()) + ", "
				+ LAT_LON_FORMAT.format(omPoint.getLongitude()));
		org.json.simple.JSONArray actions = new org.json.simple.JSONArray();
		for(int a = 0; a < theActions.length; a++)
			actions.add(theActions[a].getValue(javax.swing.Action.NAME));
		ret.put("actions", actions);
		return ret;
	}

	private void performAction(String action, int x, int y, int width, int height)
	{
		for(int a = 0; a < theActions.length; a++)
			if(action.equals(theActions[a].getValue(javax.swing.Action.NAME)))
				theActions[a].actionPerformed(new java.awt.event.ActionEvent(this, 0, action));
	}

	private void mapClicked(int x, int y, int width, int height)
	{
		LatLonPoint omPoint = createProjection(width, height).inverse(x, y);
		log.debug("Clicked " + x + ", " + y + " in " + width + "x" + height + "=" + omPoint);
		for(MouseListener mma : theListeners)
			mma.mouseClicked(omPoint);
	}

	private void mapDragStarted(int x, int y, int width, int height)
	{
		LatLonPoint omPoint = createProjection(width, height).inverse(x, y);
		for(MouseListener mma : theListeners)
			mma.dragStart(omPoint);
	}

	private void mapDragging(int startX, int startY, int dragX, int dragY, int width, int height)
	{
		Projection pro = createProjection(width, height);
		LatLonPoint start = pro.inverse(startX, startY);
		LatLonPoint drag = pro.inverse(dragX, dragY);
		for(MouseListener mma : theListeners)
			mma.dragging(start, drag);
	}

	private void mapDragged(int startX, int startY, int endX, int endY, int width, int height)
	{
		Projection pro = createProjection(width, height);
		LatLonPoint start = pro.inverse(startX, startY);
		LatLonPoint end = pro.inverse(endX, endY);
		for(MouseListener mma : theListeners)
			mma.dragged(start, end);
	}
}
