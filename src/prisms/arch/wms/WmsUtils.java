/*
 * WmsUtils.java Created Jul 7, 2010 by Andrew Butler, PSL
 */
package prisms.arch.wms;

import org.dom4j.Element;

/**
 * A utility class to perform various WMS operations, such as retrieving available layers for a WMS
 * server
 */
public class WmsUtils
{
	/** Represents a layer available from a WMS server */
	public static class WmsLayer
	{
		/**
		 * The ID of the layer--this is what should be passed in, comma-separated, in the LAYERS
		 * parameter of the URL
		 */
		public final String name;

		/** The title of the layer--a human-readable title to display for the layer */
		public final String title;

		/**
		 * A more lengthy description of the layer--contained in the Abstract element of the
		 * capabilities XML (abstract is a keyword in java, so I called it descrip).
		 */
		public final String descrip;

		/**
		 * Creates a WmsLayer
		 * 
		 * @param aName The formal name for the layer
		 * @param aTitle A short human-readable title for the layer
		 * @param aDescrip A more lengthy description of the layer
		 */
		public WmsLayer(String aName, String aTitle, String aDescrip)
		{
			name = aName;
			title = aTitle;
			descrip = aDescrip;
		}
	}

	private String [] theCachedWmsUrls;

	private WmsLayer [][] theCachedWmsLayers;

	/**
	 * Creates a utility instance
	 * 
	 * @param size The cache size for WMS layers
	 */
	public WmsUtils(final int size)
	{
		theCachedWmsUrls = new String [size];
		theCachedWmsLayers = new WmsLayer [size] [];
	}

	/**
	 * Retrieves all WMS layers available from a WMS source
	 * 
	 * @param wmsURL The URL of the WMS source to get the layers for
	 * @param pi The progress for this method to report to as the layers are retrieved from the WMS
	 *        capabilities response
	 * @return The set of layers available from the given WMS source
	 * @throws prisms.arch.PrismsException If an error occurs retrieving the data
	 */
	public synchronized WmsLayer [] getWMSLayers(String wmsURL,
		prisms.ui.UI.DefaultProgressInformer pi) throws prisms.arch.PrismsException
	{
		try
		{
			for(int i = 0; i < theCachedWmsUrls.length; i++)
			{
				if(wmsURL.equals(theCachedWmsUrls[i]))
				{
					WmsLayer [] layers = theCachedWmsLayers[i];
					for(; i > 0; i--)
					{
						theCachedWmsUrls[i] = theCachedWmsUrls[i - 1];
						theCachedWmsLayers[i] = theCachedWmsLayers[i - 1];
					}
					theCachedWmsUrls[0] = wmsURL;
					theCachedWmsLayers[0] = layers;
					return layers;
				}
			}
			if(pi != null)
				pi.setProgressText("Connecting to " + wmsURL);
			String connect = wmsURL;
			if(connect.contains("?"))
				connect += "&";
			else
				connect += "?";
			connect += "REQUEST=GetCapabilities";
			java.io.InputStream input;
			try
			{
				input = new java.net.URL(connect).openStream();
			} catch(java.io.IOException e)
			{
				throw new prisms.arch.PrismsException("Could not connect to " + wmsURL, e);
			}
			if(pi != null && pi.isCanceled())
				return null;
			if(pi != null)
				pi.setProgressText("Reading capabilities of " + wmsURL);
			Element root;
			try
			{
				root = new org.dom4j.io.SAXReader().read(input).getRootElement();
			} catch(org.dom4j.DocumentException e)
			{
				throw new prisms.arch.PrismsException("Could not read capabilities of " + wmsURL, e);
			} finally
			{
				try
				{
					input.close();
				} catch(java.io.IOException e)
				{}
			}
			if(pi != null && pi.isCanceled())
				return null;
			if(pi != null)
				pi.setProgressText("Getting layers of " + wmsURL);

			Element capabilities = root.element("Capability");
			if(capabilities == null)
			{
				Element error = root.element("ServiceException");
				if(root.getName().equals("ServiceExceptionReport") && error != null)
					throw new prisms.arch.PrismsException("Could not request layers: "
						+ error.getTextTrim());
				else
					throw new prisms.arch.PrismsException("Malformed capabilities response");
			}
			java.util.ArrayList<WmsLayer> ret = new java.util.ArrayList<WmsLayer>();
			for(Element layerEl : (java.util.List<Element>) capabilities.elements("Layer"))
				addLayers(ret, layerEl, pi);
			if(pi != null && pi.isCanceled())
				return null;
			for(int i = theCachedWmsUrls.length - 1; i > 0; i--)
			{
				theCachedWmsUrls[i] = theCachedWmsUrls[i - 1];
				theCachedWmsLayers[i] = theCachedWmsLayers[i - 1];
			}
			theCachedWmsUrls[0] = wmsURL;
			theCachedWmsLayers[0] = ret.toArray(new WmsLayer [ret.size()]);
			return theCachedWmsLayers[0];
		} finally
		{
			if(pi != null)
				pi.setDone();
		}
	}

	void addLayers(java.util.ArrayList<WmsLayer> layers, Element element,
		prisms.ui.UI.DefaultProgressInformer pi)
	{
		if(pi != null && pi.isCanceled())
			return;
		java.util.List<Element> subLayers = element.elements("Layer");
		if(subLayers.isEmpty())
		{
			String name = element.elementTextTrim("Name");
			Element descrip = element.element("Description");
			if(descrip == null)
				descrip = element;
			String title = descrip.elementTextTrim("Title");
			String abstractDescrip = descrip.elementTextTrim("Abstract");
			if(abstractDescrip != null)
			{
				abstractDescrip = abstractDescrip.replaceAll("\n", " ");
				abstractDescrip = abstractDescrip.replaceAll("\r", "");
			}
			layers.add(new WmsLayer(name, title, abstractDescrip));
		}
		else
			for(Element subLayer : subLayers)
				addLayers(layers, subLayer, pi);
	}
}
