/*
 * PrismsWms.java Created Aug 1, 2011 by Andrew Butler, PSL
 */
package prisms.arch.wms;

import java.awt.Color;

import org.json.simple.JSONObject;

import com.bbn.openmap.LatLonPoint;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;

/** A plugin that makes WMS overlays easier to provide */
public abstract class PrismsWms implements prisms.arch.wms.WmsPlugin
{
	/** The color used to allow colors underneath this layer to show through */
	public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

	/** Describes the shape of a point that may be drawn on the map */
	public static enum PointShape
	{
		/** Draws a square around the point */
		SQUARE("Square"),
		/** Draws a circle around the point */
		CIRCLE("Circle"),
		/** Draws an X through the point */
		X("X");

		private final String theDisplay;

		PointShape(String display)
		{
			theDisplay = display;
		}

		@Override
		public String toString()
		{
			return theDisplay;
		}
	}

	/** Draws impacts to an image */
	public static class GeoRenderer
	{
		private com.bbn.openmap.proj.Projection theOMProjection;

		private java.awt.Graphics2D theGraphics;

		private final com.bbn.openmap.omGraphics.OMRect theRectangle;

		private final com.bbn.openmap.omGraphics.OMLine theLine;

		private final com.bbn.openmap.omGraphics.OMPoly thePolygon;

		private final com.bbn.openmap.omGraphics.OMCircle theCircle;

		private final com.bbn.openmap.omGraphics.OMText theText;

		/**
		 * Creates an ImpactsLayer
		 * 
		 * @param omPro The OpenMap projection representing the geographical view to draw to.
		 *        {@link PrismsWms#createProjection(PrismsWmsRequest, prisms.arch.wms.BoundingBox)
		 *        )} may be used to generate this.
		 * @param graphics The graphics to draw to
		 */
		public GeoRenderer(com.bbn.openmap.proj.Projection omPro, java.awt.Graphics2D graphics)
		{
			theOMProjection = omPro;
			theGraphics = graphics;
			theRectangle = new com.bbn.openmap.omGraphics.OMRect();
			theLine = new com.bbn.openmap.omGraphics.OMLine();
			thePolygon = new com.bbn.openmap.omGraphics.OMPoly();
			theCircle = new com.bbn.openmap.omGraphics.OMCircle();
			theText = new com.bbn.openmap.omGraphics.OMText();

			thePolygon.setLineType(OMGraphic.LINETYPE_STRAIGHT);
			theText.setRenderType(OMGraphic.RENDERTYPE_OFFSET);
			theText.setJustify(com.bbn.openmap.omGraphics.OMText.JUSTIFY_LEFT);
			theText.setFont(theText.getFont().deriveFont(10.0f));
			theText.setX(0);
			theText.setY(10);
			theText.setFillPaint(TRANSPARENT);
		}

		/** @return The OpenMap projection that this layer uses to draw */
		public com.bbn.openmap.proj.Projection getProjection()
		{
			return theOMProjection;
		}

		/** @return The graphics that this layer draws on */
		public java.awt.Graphics2D getGraphics()
		{
			return theGraphics;
		}

		/**
		 * Renders a line to the image
		 * 
		 * @param p1 The beginning of the line
		 * @param p2 The end of the line
		 * @param c The color for the line
		 * @param lineType The line type for the line (See OMGraphicsConstants
		 * @param thickness The pixel thickness of the line
		 */
		public void addLine(OMPoint p1, OMPoint p2, Color c, int lineType, int thickness)
		{
			theLine.setRenderType(OMGraphic.RENDERTYPE_LATLON);
			theLine.setLL(new float [] {p1.getLat(), p1.getLon(), p2.getLat(), p2.getLon()});
			theLine.setLineType(lineType);
			theLine.setStroke(new java.awt.BasicStroke(thickness, 0, 0));
			theLine.setLinePaint(c);
			theLine.generate(theOMProjection);
			theLine.render(theGraphics);
		}

		/**
		 * Renders a point to the image
		 * 
		 * @param p The location of the point to render
		 * @param shape The shape to render
		 * @param size The size of the point
		 * @param weight The weight (graphic-pixel-thickness) of the point
		 * @param c The color to draw
		 */
		public void addPoint(OMPoint p, PointShape shape, int size, int weight, Color c)
		{
			java.awt.BasicStroke stroke = new java.awt.BasicStroke(weight, 0, 0);
			switch(shape)
			{
			case SQUARE: {
				theRectangle.setRenderType(OMGraphic.RENDERTYPE_OFFSET);
				theRectangle.setLocation(p.getLat(), p.getLon(), -(size + 1) / 2, -(size + 1) / 2,
					(size + 1) / 2, (size + 1) / 2);
				theRectangle.setFillPaint(TRANSPARENT);
				theRectangle.setLinePaint(c);
				theRectangle.setStroke(stroke);
				theRectangle.generate(theOMProjection);
				theRectangle.render(theGraphics);
				return;
			}
			case CIRCLE: {
				theCircle.setRenderType(OMGraphic.RENDERTYPE_OFFSET);
				theCircle.setLatLon(p.getLat(), p.getLon());
				theCircle.setWidth(size);
				theCircle.setHeight(size);
				theCircle.setFillPaint(TRANSPARENT);
				theCircle.setLinePaint(c);
				theCircle.setStroke(stroke);
				theCircle.generate(theOMProjection);
				theCircle.render(theGraphics);
				return;
			}
			case X: {
				theLine.setRenderType(OMGraphic.RENDERTYPE_OFFSET);
				theLine.setLL(new float [] {p.getLat(), p.getLon()});
				theLine.setPts(new int [] {-(size + 1) / 2, -(size + 1) / 2, (size + 1) / 2,
					(size + 1) / 2});
				theLine.setFillPaint(TRANSPARENT);
				theLine.setLinePaint(c);
				theLine.setStroke(stroke);
				theLine.generate(theOMProjection);
				theLine.draw(theGraphics);

				theLine.setPts(new int [] {-(size + 1) / 2, (size + 1) / 2, (size + 1) / 2,
					-(size + 1) / 2});
				theLine.generate(theOMProjection);
				theLine.render(theGraphics);
				return;
			}
			default:
				throw new IllegalStateException("Shape " + shape + " unaccounted for");
			}
		}

		/**
		 * Renders a rectangle to the image
		 * 
		 * @param p1 The location of one corner
		 * @param p2 The location of the opposite corner
		 * @param inside The color to draw for the inside of the rectangle
		 * @param border The color to draw for the border of the rectangle
		 * @param thickness The pixel thickness of the rectangle's sides
		 */
		public void addRectangle(OMPoint p1, OMPoint p2, Color inside, Color border, int thickness)
		{
			theRectangle.setLocation(p1.getLat(), p1.getLon(), p2.getLat(), p2.getLon(),
				OMGraphic.LINETYPE_STRAIGHT);
			theRectangle.setFillPaint(inside);
			theRectangle.setLinePaint(border);
			theRectangle.setStroke(new java.awt.BasicStroke(thickness, 0, 0));
			theRectangle.generate(theOMProjection);
			theRectangle.render(theGraphics);
		}

		/**
		 * Renders a circle to the image
		 * 
		 * @param center The location of the circle's center
		 * @param radius The radius of the circle in kilometers
		 * @param inside The color to draw for the inside of the circle
		 * @param border The color to draw for the border of the circle
		 * @param thickness The pixel thickness of the circle's border
		 */
		public void addCircle(OMPoint center, float radius, Color inside, Color border,
			int thickness)
		{
			theCircle.setRenderType(OMGraphic.RENDERTYPE_LATLON);
			theCircle.setLatLon(center.getLat(), center.getLon());
			theCircle.setRadius(radius, com.bbn.openmap.proj.Length.KM);
			theCircle.setLinePaint(border);
			theCircle.setFillPaint(inside);
			theCircle.setStroke(new java.awt.BasicStroke(thickness, 0, 0));
			theCircle.generate(theOMProjection);
			theCircle.render(theGraphics);
		}

		/**
		 * Renders a polygon to the image
		 * 
		 * @param verts The vertices of the polygon to render
		 * @param inside The color to draw for the inside of the polygon
		 * @param border The color to draw for the border of the polygon
		 * @param thickness The pixel thickness of the polygon's border
		 */
		public void addPolygon(OMPoint [] verts, Color inside, Color border, int thickness)
		{
			float [] llpoints = new float [verts.length * 2];
			for(int i = 0; i < verts.length; i++)
			{
				llpoints[i * 2] = verts[i].getLat();
				llpoints[i * 2 + 1] = verts[i].getLon();
			}
			thePolygon.setLocation(llpoints, OMGraphic.DECIMAL_DEGREES);
			thePolygon.setFillPaint(inside);
			thePolygon.setLinePaint(border);
			thePolygon.setIsPolygon(true);
			thePolygon.setStroke(new java.awt.BasicStroke(thickness, 0, 0));
			thePolygon.generate(theOMProjection);
			thePolygon.render(theGraphics);
		}

		/**
		 * Renders text to the image
		 * 
		 * @param p The location of the upper-left corner of the text
		 * @param text The text to render
		 * @param font The font to render the text in. May be null.
		 * @param c The color to render the text as
		 */
		public void addText(OMPoint p, String text, java.awt.Font font, Color c)
		{
			theText.setLat(p.getLat());
			theText.setLon(p.getLon());
			if(font != null)
				theText.setFont(font);
			theText.setData(text);
			theText.setLinePaint(c);
			theText.generate(theOMProjection);
			theText.render(theGraphics);
		}
	}

	private prisms.arch.PrismsSession theSession;

	private String theName;

	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig pluginEl)
	{
		theSession = session;
		theName = pluginEl.get("name");
	}

	public void initClient()
	{
	}

	/** @return The session that this WMS plugin serves */
	public prisms.arch.PrismsSession getSession()
	{
		return theSession;
	}

	/** @return This plugin's name */
	public String getName()
	{
		return theName;
	}

	public void drawMapOverlay(PrismsWmsRequest request, JSONObject event,
		java.io.OutputStream output) throws java.io.IOException
	{
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(request.getWidth(),
			request.getHeight(), java.awt.image.BufferedImage.TYPE_4BYTE_ABGR);
		GeoRenderer renderer;
		prisms.util.ProgramTracker.TrackNode track = prisms.util.PrismsUtils.track(getSession()
			.getApp().getEnvironment(), "Create WMS Render Context");
		try
		{
			renderer = createRenderer(request, (java.awt.Graphics2D) image.getGraphics());
		} finally
		{
			prisms.util.PrismsUtils.end(getSession().getApp().getEnvironment(), track);
		}
		if(renderer == null)
		{
			image.getGraphics().dispose();
			track = prisms.util.PrismsUtils.track(getSession().getApp().getEnvironment(),
				"Write WMS Image");
			try
			{
				if(request.getFormat().toLowerCase().contains("png"))
					javax.imageio.ImageIO.write(image, "png", output);
				else if(request.getFormat().toLowerCase().contains("jpg")
					|| request.getFormat().toLowerCase().contains("jpeg"))
					javax.imageio.ImageIO.write(image, "jpeg", output);
				else if(request.getFormat().toLowerCase().contains("gif"))
					javax.imageio.ImageIO.write(image, "gif", output);
				else
					throw new IllegalArgumentException("Unrecognized image format: "
						+ request.getFormat());
			} finally
			{
				prisms.util.PrismsUtils.end(getSession().getApp().getEnvironment(), track);
			}
			return;
		}
		track = prisms.util.PrismsUtils.track(getSession().getApp().getEnvironment(),
			"Render WMS Map");
		try
		{
			doGetMap(request, event, renderer);
		} finally
		{
			prisms.util.PrismsUtils.end(getSession().getApp().getEnvironment(), track);
			renderer.getGraphics().dispose();
		}
		track = prisms.util.PrismsUtils.track(getSession().getApp().getEnvironment(),
			"Write WMS Image");
		try
		{
			if(request.getFormat().toLowerCase().contains("png"))
				javax.imageio.ImageIO.write(image, "png", output);
			else if(request.getFormat().toLowerCase().contains("jpg")
				|| request.getFormat().toLowerCase().contains("jpeg"))
				javax.imageio.ImageIO.write(image, "jpeg", output);
			else if(request.getFormat().toLowerCase().contains("gif"))
				javax.imageio.ImageIO.write(image, "gif", output);
			else
				throw new IllegalArgumentException("Unrecognized image format: "
					+ request.getFormat());
		} finally
		{
			prisms.util.PrismsUtils.end(getSession().getApp().getEnvironment(), track);
		}
	}

	/**
	 * Draws custom data to an image
	 * 
	 * @param request The WMS request to render a response to
	 * @param event The PRISMS-specific data sent with the request
	 * @param renderer The renderer to draw the custom data to
	 */
	public abstract void doGetMap(PrismsWmsRequest request, JSONObject event, GeoRenderer renderer);

	/**
	 * Creates the impacts layer to draw to
	 * 
	 * @param request The WMS request to draw the result for
	 * @param graphics The graphics to draw to
	 * @return The impacts layer
	 */
	public static GeoRenderer createRenderer(PrismsWmsRequest request, java.awt.Graphics2D graphics)
	{
		BoundingBox box = request.getBounds();
		if(box.minLat >= -90 && box.maxLat <= 90)
			return new GeoRenderer(createProjection(request, box), graphics);
		if(box.minLat >= 90 || box.maxLat <= -90)
			return null;
		int top = 0, bottom = request.getHeight();
		if(box.minLat < -90)
		{
			bottom = Math.round((-90 - box.minLat) * request.getHeight()
				/ (box.maxLat - box.minLat));
			bottom = request.getHeight() - bottom;
		}
		if(box.maxLat > 90)
			top = Math.round((box.maxLat - 90) * request.getHeight() / (box.maxLat - box.minLat));
		graphics = (java.awt.Graphics2D) graphics.create(0, top, request.getWidth(), bottom - top);
		float minLat = box.minLat;
		if(minLat < -90)
			minLat = -90;
		float maxLat = box.maxLat;
		if(maxLat > 90)
			maxLat = 90;
		return new GeoRenderer(createProjection(request, new BoundingBox(minLat, maxLat,
			box.minLon, box.maxLon)), graphics);
	}

	/**
	 * Creates a projection for a WMS request's settings
	 * 
	 * @param request The WMS request to create the projection for
	 * @param box The bounding box for the WMS request. If null, the request's bounds will be used
	 * @return The projection to use for the request
	 */
	public static com.bbn.openmap.proj.Projection createProjection(PrismsWmsRequest request,
		BoundingBox box)
	{
		if(box == null)
			box = request.getBounds();
		if(request.getSRS().equalsIgnoreCase("EPSG:4326"))
		{ // Simple lat/lon projection
			LatLonPoint center = new LatLonPoint(box.getCenterLat(), box.getCenterLon());
			com.bbn.openmap.proj.LLXY ret = new com.bbn.openmap.proj.LLXY(center, 35000000,
				request.getWidth(), request.getHeight());
			LatLonPoint ul = new LatLonPoint(box.maxLat, box.minLon);
			LatLonPoint lr = new LatLonPoint(box.minLat, box.maxLon);
			ret.setScale(com.bbn.openmap.proj.ProjMath.getScale(ul, lr, ret));
			return ret;
		}
		else if(request.getSRS().equalsIgnoreCase("EPSG:900913")
			|| request.getSRS().equalsIgnoreCase("EPSG:3785"))
		{ // Mercator projection
			throw new IllegalArgumentException("Mercator is not supported in this WMS");
			/*PrismsWmsRequest.BoundingBox box = request.getBounds();
			LatLonPoint center = new LatLonPoint(box.getCenterLat(), box.getCenterLon());
			com.bbn.openmap.proj.Mercator ret = new com.bbn.openmap.proj.Mercator(center, 35000000,
				request.getWidth(), request.getHeight());
			LatLonPoint ul = new LatLonPoint(box.maxLat, box.minLon);
			LatLonPoint lr = new LatLonPoint(box.minLat, box.maxLon);
			ret.setScale(com.bbn.openmap.proj.ProjMath.getScale(ul, lr, ret));
			return ret;*/
		}
		else
			throw new IllegalArgumentException("SRS " + request.getSRS()
				+ " unsupported by this WMS");
	}

	/**
	 * Gets a lat/lon point defined by the given x/y pixel point in a request
	 * 
	 * @param request The WMS request defining the projection
	 * @param x The x-pixel on the screen
	 * @param y The y-pixel on the screen
	 * @return The point represented by the x/y point given
	 */
	public static LatLonPoint getPoint(PrismsWmsRequest request, int x, int y)
	{
		BoundingBox box = request.getBounds();
		if(box.minLat >= -90 && box.maxLat <= 90)
			return createProjection(request, null).inverse(x, y);
		if(box.minLat >= 90 || box.maxLat <= -90)
			return null;
		int top = 0, bottom = request.getHeight();
		if(box.minLat < -90)
		{
			bottom = Math.round((-90 - box.minLat) * request.getHeight()
				/ (box.maxLat - box.minLat));
			bottom = request.getHeight() - bottom;
		}
		if(box.maxLat > 90)
			top = Math.round((box.maxLat - 90) * request.getHeight() / (box.maxLat - box.minLat));
		if(y < top || y > bottom)
			return null;
		return createProjection(request, null).inverse(x, y);
	}
}
