/*
 * MapLayerEditor.java Created Nov 9, 2009 by Andrew Butler, PSL
 */
package prisms.ui;

import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.log4j.Logger;
import org.geotools.ows.ServiceException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.bbn.openmap.Layer;
import com.bbn.openmap.plugin.PlugIn;
import com.bbn.openmap.plugin.PlugInLayer;
import com.bbn.openmap.plugin.wms.WMSPlugIn;

import prisms.arch.PrismsSession;
import prisms.util.ArrayUtils;

/**
 * Allows the user to edit and add layers to the IWEDA map
 */
public abstract class MapLayerEditor implements prisms.arch.AppPlugin
{
	static final Logger log = Logger.getLogger(MapLayerEditor.class);

	private PrismsSession theSession;

	private String theName;

	private String theSelectedWMS;

	private java.util.Set<org.geotools.data.ows.Layer> theWMSLayers;

	private prisms.util.preferences.Preference<Object> theLayersPref;

	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		if(theLayersPref == null)
			theLayersPref = new prisms.util.preferences.Preference<Object>(theName, "layers",
				prisms.util.preferences.Preference.Type.ARBITRARY, Object.class, false);
		readStoredPrefs();
	}

	/**
	 * Sets the preference that this editor will store the layers configuration to
	 * 
	 * @param pref The preference
	 */
	public void setLayersPreference(prisms.util.preferences.Preference<Object> pref)
	{
		theLayersPref = pref;
	}

	public void initClient()
	{
	}

	public void processEvent(JSONObject evt)
	{
		if("configureLayers".equals(evt.get("method")))
			sendLayers();
		else if("enableLayer".equals(evt.get("method")))
			setLayerEnabled((String) evt.get("layer"), true);
		else if("disableLayer".equals(evt.get("method")))
			setLayerEnabled((String) evt.get("layer"), false);
		else if("configureLayer".equals(evt.get("method")))
			configure((String) evt.get("layer"));
		else if("moveLayerDown".equals(evt.get("method")))
			moveDown((String) evt.get("layer"));
		else if("moveLayerUp".equals(evt.get("method")))
			moveUp((String) evt.get("layer"));
		else if("deleteLayer".equals(evt.get("method")))
			delete((String) evt.get("layer"));
		else if("addWMSLayer".equals(evt.get("method")))
			addWMSLayer();
		else if("setLayerName".equals(evt.get("method")))
			setLayerName((String) evt.get("layer"), (String) evt.get("name"));
		else if("setLayerURL".equals(evt.get("method")))
			setLayerURL((String) evt.get("layer"), (String) evt.get("url"));
		else if("disableSubLayer".equals(evt.get("method")))
			setSubLayerEnabled((String) evt.get("layer"), (String) evt.get("subLayer"), false);
		else if("enableSubLayer".equals(evt.get("method")))
			setSubLayerEnabled((String) evt.get("layer"), (String) evt.get("subLayer"), true);
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}

	/**
	 * @return This plugin's session
	 */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/**
	 * @return The name of this plugin
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * @return The map that this editor is to configure layers for
	 */
	public abstract PrismsOpenMapPlugin getMap();

	/**
	 * @return The preferences set that this editor will store preferences to
	 */
	public abstract prisms.util.preferences.Preferences getPreferences();

	/**
	 * @param layer The layer to display or not
	 * @return Whether this editor will display the given layer for the user to move or disable
	 */
	public boolean shouldShow(Layer layer)
	{
		return true;
	}

	/**
	 * @param layer The layer to determine configurability for
	 * @return Whether this editor will allow the user to configure the given layer
	 */
	public boolean canConfigure(Layer layer)
	{
		return layer instanceof PlugInLayer
			&& ((PlugInLayer) layer).getPlugIn() instanceof WMSPlugIn;
	}

	void sendLayers()
	{
		theSelectedWMS = null;
		theWMSLayers = null;
		JSONArray jsonLayers = null;
		PrismsOpenMapPlugin map = getMap();
		if(map != null)
		{
			jsonLayers = new JSONArray();
			Layer [] layers = map.getImageServer().getLayers().clone();
			ArrayUtils.reverse(layers);
			for(Layer layer : layers)
			{
				if(!shouldShow(layer))
					continue;
				JSONObject jsonLayer = new JSONObject();
				jsonLayers.add(jsonLayer);
				jsonLayer.put("name", layer.getName());
				jsonLayer.put("enabled", new Boolean(layer.isVisible()));
				String type;
				if(layer instanceof com.bbn.openmap.layer.rpf.RpfLayer)
					type = "CADRG";
				else if(layer instanceof com.bbn.openmap.layer.shape.ShapeLayer)
					type = "Shape";
				else if(layer instanceof com.bbn.openmap.layer.GraticuleLayer)
					type = "Graticule";
				else if(layer instanceof com.bbn.openmap.layer.daynight.DayNightLayer)
					type = "Day/Night";
				else if(layer instanceof PlugInLayer)
				{
					PlugIn plugin = ((PlugInLayer) layer).getPlugIn();
					if(plugin instanceof WMSPlugIn)
					{
						type = "WMS";
						jsonLayer.put("url", ((WMSPlugIn) plugin).getQueryHeader());
					}
					else
						type = "Other";
				}
				else
					type = "Other";

				jsonLayer.put("type", type);
				jsonLayer.put("configurable", new Boolean(canConfigure(layer)));
			}
		}
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setLayers");
		evt.put("layers", jsonLayers);
		theSession.postOutgoingEvent(evt);
	}

	void setLayerEnabled(String layerName, boolean enabled)
	{
		PrismsOpenMapPlugin map = getMap();
		if(map == null)
			throw new IllegalStateException("No Image Server Map to configure");

		for(Layer layer : map.getImageServer().getLayers())
			if(layer.getName().equals(layerName))
			{
				layer.setVisible(enabled);
				break;
			}
		map.draw();
		sendLayers();
		storePrefs();
	}

	void configure(String layerName)
	{
		PrismsOpenMapPlugin map = getMap();
		if(map == null)
			throw new IllegalStateException("No Image Server Map to configure");

		for(Layer layer : map.getImageServer().getLayers())
			if(layer.getName().equals(layerName))
			{
				configure(layer);
				break;
			}
	}

	void configure(Layer layer)
	{
		if(layer instanceof PlugInLayer)
		{
			PlugIn plugin = ((PlugInLayer) layer).getPlugIn();
			if(plugin instanceof WMSPlugIn)
				configureWMS((PlugInLayer) layer, (WMSPlugIn) plugin);
			else
				throw new IllegalArgumentException("Plugin of type " + plugin.getClass().getName()
					+ " is not configurable");
		}
		else
			throw new IllegalArgumentException("Layer of type " + layer.getClass().getName()
				+ " is not configurable");
	}

	void configureWMS(com.bbn.openmap.plugin.PlugInLayer layer,
		final com.bbn.openmap.plugin.wms.WMSPlugIn plugin)
	{
		String url = plugin.getQueryHeader();
		java.util.Set<org.geotools.data.ows.Layer> qLayers = null;
		if(theSelectedWMS != null && theSelectedWMS.equals(url))
			qLayers = theWMSLayers;
		else if(url != null && url.length() > 0)
		{
			theSelectedWMS = null;
			theWMSLayers = null;
			if(url.contains("?"))
				url += "&VERSION=1.1.0&REQUEST=GetCapabilities";
			else
				url += "?VERSION=1.1.0&REQUEST=GetCapabilities";
			prisms.ui.UI ui = (prisms.ui.UI) theSession.getPlugin("UI");
			final boolean [] finished = new boolean [] {false};
			if(ui != null)
				ui.startTimedTask(new prisms.ui.UI.ProgressInformer()
				{
					public String getTaskText()
					{
						return "Retrieving WMS Layers for " + plugin.getQueryHeader();
					}

					public int getTaskScale()
					{
						return 0;
					}

					public int getTaskProgress()
					{
						return 0;
					}

					public boolean isTaskDone()
					{
						return finished[0];
					}

					public boolean isCancelable()
					{
						return false;
					}

					public void cancel() throws IllegalStateException
					{
					}
				});
			try
			{
				org.geotools.data.wms.WebMapServer wms;
				try
				{
					wms = new org.geotools.data.wms.WebMapServer(new java.net.URL(url));
				} catch(ServiceException e)
				{
					throw new IllegalStateException("Could not get WMS information for "
						+ plugin.getQueryHeader(), e);
				} catch(MalformedURLException e)
				{
					throw new IllegalStateException("Could not get WMS information for "
						+ plugin.getQueryHeader(), e);
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not get WMS information for "
						+ plugin.getQueryHeader(), e);
				}
				qLayers = org.geotools.data.wms.WMSUtils.getQueryableLayers(wms.getCapabilities());
				theSelectedWMS = plugin.getQueryHeader();
				theWMSLayers = qLayers;
			} finally
			{
				finished[0] = true;
			}
		}
		JSONArray jsonLayers = new JSONArray();
		if(qLayers != null)
		{
			for(org.geotools.data.ows.Layer qLayer : qLayers)
			{
				JSONObject jsonLayer = new JSONObject();
				jsonLayers.add(jsonLayer);
				jsonLayer.put("name", qLayer.getName());
				jsonLayer.put("title", qLayer.getTitle());
				jsonLayer.put("enabled", new Boolean(ArrayUtils.contains(plugin.getLayers().split(
					","), qLayer.getName())));
			}
		}
		JSONObject jsonWMS = new JSONObject();
		jsonWMS.put("name", layer.getName());
		jsonWMS.put("url", plugin.getQueryHeader());
		jsonWMS.put("layers", jsonLayers);
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "configureWMS");
		evt.put("layer", jsonWMS);
		theSession.postOutgoingEvent(evt);
	}

	void moveDown(String layerName)
	{
		PrismsOpenMapPlugin map = getMap();
		if(map == null)
			throw new IllegalStateException("No Image Server Map to move layer within");

		Layer [] layers = map.getImageServer().getLayers();
		int idx = -1;
		for(int L = 0; L < layers.length; L++)
			if(layers[L].getName().equals(layerName))
			{
				idx = L;
				break;
			}
		if(idx < 0)
			throw new IllegalArgumentException("No such layer: " + layerName);
		if(idx == layers.length - 1)
			throw new IllegalArgumentException("Layer " + layerName + " is the bottom layer");
		ArrayUtils.move(layers, idx, idx + 1);
		map.getImageServer().setLayers(layers);
		sendLayers();
		map.draw();
		storePrefs();
	}

	void moveUp(String layerName)
	{
		PrismsOpenMapPlugin map = getMap();
		if(map == null)
			throw new IllegalStateException("No Image Server Map to move layer within");

		Layer [] layers = map.getImageServer().getLayers();
		int idx = -1;
		for(int L = 0; L < layers.length; L++)
			if(layers[L].getName().equals(layerName))
			{
				idx = L;
				break;
			}
		if(idx < 0)
			throw new IllegalArgumentException("No such layer: " + layerName);
		if(idx == 0)
			throw new IllegalArgumentException("Layer " + layerName + " is the top layer");
		ArrayUtils.move(layers, idx, idx - 1);
		map.getImageServer().setLayers(layers);
		sendLayers();
		map.draw();
		storePrefs();
	}

	void delete(String layerName)
	{
		PrismsOpenMapPlugin map = getMap();
		if(map == null)
			throw new IllegalStateException("No Image Server Map to delete layer from");

		Layer [] layers = map.getImageServer().getLayers();
		int idx = -1;
		for(int L = 0; L < layers.length; L++)
			if(layers[L].getName().equals(layerName))
			{
				idx = L;
				break;
			}
		if(idx < 0)
			throw new IllegalArgumentException("No such layer: " + layerName);
		if(layers[idx] instanceof PlugInLayer)
		{
			PlugIn plugin = ((PlugInLayer) layers[idx]).getPlugIn();
			if(!(plugin instanceof WMSPlugIn))
				throw new IllegalArgumentException("Cannot delete layer " + layerName
					+ "--not configurable");
		}
		else
			throw new IllegalArgumentException("Cannot delete layer " + layerName
				+ "--not configurable");
		layers = ArrayUtils.remove(layers, idx);
		map.getImageServer().setLayers(layers);
		sendLayers();
		map.draw();
		storePrefs();
	}

	void addWMSLayer()
	{
		PrismsOpenMapPlugin map = getMap();
		if(map == null)
			throw new IllegalStateException("No Image Server Map to add layer to");

		WMSPlugIn wms = new WMSPlugIn();
		wms.setWmsVersion("1.1.1");
		wms.setTransparent("true");
		wms.setBackgroundColor("white");
		PlugInLayer pil = new PlugInLayer();
		pil.setPlugIn(wms);
		pil.setVisible(true);
		map.getImageServer().setLayers(ArrayUtils.add(map.getImageServer().getLayers(), pil, 0));
		pil.setName(getNewLayerName(map.getImageServer().getLayers(), "New WMS Layer"));
		configureWMS(pil, wms);
		storePrefs();
	}

	String getNewLayerName(Layer [] layers, String firstTry)
	{
		if(!hasName(layers, firstTry))
			return firstTry;
		int i;
		for(i = 2; hasName(layers, firstTry + " " + i); i++);
		return firstTry + " " + i;
	}

	boolean hasName(Layer [] layers, String name)
	{
		for(Layer layer : layers)
			if(layer.getName().equals(name))
				return true;
		return false;
	}

	void setLayerName(String layerName, String name)
	{
		PrismsOpenMapPlugin map = getMap();
		if(map == null)
			throw new IllegalStateException("No Image Server Map to configure");

		Layer selectedLayer = null;
		for(Layer layer : map.getImageServer().getLayers())
			if(layer.getName().equals(layerName))
			{
				selectedLayer = layer;
				break;
			}
		if(selectedLayer == null)
			throw new IllegalArgumentException("No such layer: " + layerName);
		if(selectedLayer instanceof PlugInLayer)
		{
			PlugIn plugin = ((PlugInLayer) selectedLayer).getPlugIn();
			if(!(plugin instanceof WMSPlugIn))
				throw new IllegalArgumentException("Cannot set name of layer " + layerName
					+ "--not configurable");
		}
		else
			throw new IllegalArgumentException("Cannot set name of layer " + layerName
				+ "--not configurable");
		if(hasName(map.getImageServer().getLayers(), name))
		{
			configureWMS((PlugInLayer) selectedLayer, (WMSPlugIn) ((PlugInLayer) selectedLayer)
				.getPlugIn());
			throw new IllegalArgumentException("A layer named " + name + " already exists");
		}
		selectedLayer.setName(name);
		configureWMS((PlugInLayer) selectedLayer, (WMSPlugIn) ((PlugInLayer) selectedLayer)
			.getPlugIn());
		storePrefs();
	}

	void setLayerURL(String layerName, String url)
	{
		theSelectedWMS = null;
		theWMSLayers = null;
		PrismsOpenMapPlugin map = getMap();
		if(map == null)
			throw new IllegalStateException("No Image Server Map to configure");

		Layer selectedLayer = null;
		for(Layer layer : map.getImageServer().getLayers())
			if(layer.getName().equals(layerName))
			{
				selectedLayer = layer;
				break;
			}
		if(selectedLayer == null)
			throw new IllegalArgumentException("No such layer: " + layerName);
		WMSPlugIn wms = null;
		if(selectedLayer instanceof PlugInLayer)
		{
			PlugIn plugin = ((PlugInLayer) selectedLayer).getPlugIn();
			if(plugin instanceof WMSPlugIn)
				wms = ((WMSPlugIn) plugin);
			else
				throw new IllegalArgumentException("Cannot set URL of layer " + layerName
					+ "--not configurable");
		}
		else
			throw new IllegalArgumentException("Cannot set URL of layer " + layerName
				+ "--not configurable");
		wms.setQueryHeader(url);
		wms.setLayers("");
		map.draw();
		configureWMS((PlugInLayer) selectedLayer, wms);
		storePrefs();
	}

	void setSubLayerEnabled(String layerName, String subLayerName, boolean enabled)
	{
		PrismsOpenMapPlugin map = getMap();
		if(map == null)
			throw new IllegalStateException("No Image Server Map to configure");

		Layer selectedLayer = null;
		for(Layer layer : map.getImageServer().getLayers())
			if(layer.getName().equals(layerName))
			{
				selectedLayer = layer;
				break;
			}
		if(selectedLayer == null)
			throw new IllegalArgumentException("No such layer: " + layerName);
		WMSPlugIn wms = null;
		if(selectedLayer instanceof PlugInLayer)
		{
			com.bbn.openmap.plugin.PlugIn plugin = ((PlugInLayer) selectedLayer).getPlugIn();
			if(plugin instanceof WMSPlugIn)
				wms = (WMSPlugIn) plugin;
			else
				throw new IllegalArgumentException("No sublayers of layer " + layerName
					+ "--not WMS");
		}
		else
			throw new IllegalArgumentException("No sublayers of layer " + layerName + "--not WMS");
		if(enabled == ArrayUtils.contains(wms.getLayers().split(","), subLayerName))
			return;
		if(enabled)
		{
			if(wms.getLayers().length() == 0)
				wms.setLayers(subLayerName);
			else
				wms.setLayers(wms.getLayers() + "," + subLayerName);
		}
		else
		{
			wms.setLayers(wms.getLayers().replace(subLayerName, "").replace(",,", ","));
			if(wms.getLayers().length() > 0 && wms.getLayers().charAt(0) == ',')
				wms.setLayers(wms.getLayers().substring(1));
			if(wms.getLayers().length() > 0
				&& wms.getLayers().charAt(wms.getLayers().length() - 1) == ',')
				wms.setLayers(wms.getLayers().substring(0, wms.getLayers().length() - 1));
		}
		configureWMS((PlugInLayer) selectedLayer, wms);
		prisms.ui.UI ui = (prisms.ui.UI) theSession.getPlugin("UI");
		final boolean [] finished = new boolean [] {false};
		if(ui != null)
			ui.startTimedTask(new prisms.ui.UI.ProgressInformer()
			{
				public String getTaskText()
				{
					return "Redrawing WMS Layers";
				}

				public int getTaskScale()
				{
					return 0;
				}

				public int getTaskProgress()
				{
					return 0;
				}

				public boolean isTaskDone()
				{
					return finished[0];
				}

				public boolean isCancelable()
				{
					return false;
				}

				public void cancel() throws IllegalStateException
				{
				}
			});
		try
		{
			((PlugInLayer) selectedLayer).setList(((PlugInLayer) selectedLayer).prepare());
			map.draw();
		} finally
		{
			finished[0] = true;
		}
		storePrefs();
	}

	/**
	 * Reads the stored preference value and updates this editor's settings with it, modifying the
	 * map layers
	 */
	public void readStoredPrefs()
	{
		prisms.util.preferences.Preferences prefs = getPreferences();
		if(prefs == null || theLayersPref == null)
			return;
		final PrismsOpenMapPlugin map = getMap();
		if(map == null)
			return;
		JSONArray prefLayers = (JSONArray) prefs.get(theLayersPref);
		if(prefLayers == null)
			prefLayers = getDefaultLayerPref();
		if(prefLayers == null)
		{
			storePrefs();
			return;
		}
		map.getImageServer().setLayers(
			ArrayUtils.adjust(map.getImageServer().getLayers(), (JSONObject []) prefLayers
				.toArray(new JSONObject [0]),
				new ArrayUtils.DifferenceListener<Layer, JSONObject>()
				{
					public boolean identity(Layer o1, JSONObject o2)
					{
						if("WMS".equals(o2.get("type")))
						{
							if(o1 instanceof PlugInLayer
								&& ((PlugInLayer) o1).getPlugIn() instanceof WMSPlugIn)
								return equal(o2.get("url"), ((WMSPlugIn) ((PlugInLayer) o1)
									.getPlugIn()).getQueryHeader());
							else if(o1.getName().equals(o2.get("name")))
							{
								// Change preferences layer name to avoid clash
								o2.put("name", o2.get("name") + " - stored");
							}
							return false;
						}
						return o1.getName().equals(o2.get("name"));
					}

					public Layer added(JSONObject o, int idx, int retIdx)
					{
						if(!"WMS".equals(o.get("type")))
							return null;

						WMSPlugIn wms = new WMSPlugIn();
						wms.setWmsVersion("1.1.1");
						wms.setTransparent("true");
						wms.setBackgroundColor("white");
						wms.setQueryHeader((String) o.get("url"));
						wms.setLayers(join((JSONArray) o.get("layers")));
						PlugInLayer pil = new PlugInLayer();
						pil.setPlugIn(wms);
						pil.setVisible(((Boolean) o.get("enabled")).booleanValue());
						pil.setName((String) o.get("name"));
						return pil;
					}

					public Layer removed(Layer o, int idx, int incMod, int retIdx)
					{
						return o;
					}

					public Layer set(Layer o1, int idx1, int incMod, JSONObject o2, int idx2,
						int retIdx)
					{
						o1.setVisible(((Boolean) o2.get("enabled")).booleanValue());
						if("WMS".equals(o2.get("type")))
						{
							PlugInLayer pil = (PlugInLayer) o1;
							WMSPlugIn wms = (WMSPlugIn) pil.getPlugIn();
							wms.setQueryHeader((String) o2.get("url"));
							wms.setLayers(join((JSONArray) o2.get("layers")));
							pil.setList(pil.prepare());
						}
						return o1;
					}
				}));
		map.draw();
		storePrefs();
	}

	JSONArray getDefaultLayerPref()
	{
		return null;
	}

	String join(JSONArray layerNames)
	{
		if(layerNames == null)
			return null;
		StringBuilder ret = new StringBuilder();
		for(int i = 0; i < layerNames.size(); i++)
		{
			ret.append((String) layerNames.get(i));
			if(i < layerNames.size() - 1)
				ret.append(',');
		}
		return ret.toString();
	}

	/**
	 * Stores the configured layers in the preferences
	 */
	public void storePrefs()
	{
		prisms.util.preferences.Preferences prefs = getPreferences();
		if(prefs == null || theLayersPref == null)
			return;
		JSONArray prefLayers = (JSONArray) prefs.get(theLayersPref);
		if(prefLayers == null)
			prefLayers = new JSONArray();
		PrismsOpenMapPlugin map = getMap();
		JSONObject [] adjusted = ArrayUtils.adjust((JSONObject []) prefLayers
			.toArray(new JSONObject [0]), map.getImageServer().getLayers(),
			new ArrayUtils.DifferenceListener<JSONObject, Layer>()
			{
				public boolean identity(JSONObject o1, Layer o2)
				{
					if("WMS".equals(o1.get("type")))
					{
						if(o2 instanceof PlugInLayer
							&& ((PlugInLayer) o2).getPlugIn() instanceof WMSPlugIn)
							return equal(o1.get("url"),
								((WMSPlugIn) ((PlugInLayer) o2).getPlugIn()).getQueryHeader());
						else if(o2.getName().equals(o1.get("name")))
						{
							// Change preferences layer name to avoid clash
							o1.put("name", o1.get("name") + " - stored");
						}
						return false;
					}
					return o2.getName().equals(o1.get("name"));
				}

				public JSONObject added(Layer o, int idx, int retIdx)
				{
					if(!shouldShow(o))
						return null;
					JSONObject ret = new JSONObject();
					ret.put("name", o.getName());
					ret.put("enabled", new Boolean(o.isVisible()));
					String type;
					if(o instanceof com.bbn.openmap.layer.rpf.RpfLayer)
						type = "CADRG";
					else if(o instanceof com.bbn.openmap.layer.shape.ShapeLayer)
						type = "Shape";
					else if(o instanceof com.bbn.openmap.layer.GraticuleLayer)
						type = "Graticule";
					else if(o instanceof com.bbn.openmap.layer.daynight.DayNightLayer)
						type = "Day/Night";
					else if(o instanceof PlugInLayer)
					{
						PlugIn plugin = ((PlugInLayer) o).getPlugIn();
						if(plugin instanceof WMSPlugIn)
						{
							WMSPlugIn wms = (WMSPlugIn) plugin;
							type = "WMS";
							ret.put("url", wms.getQueryHeader());
							JSONArray layers = null;
							if(wms.getLayers() != null)
							{
								layers = new JSONArray();
								for(String layer : wms.getLayers().split(","))
									layers.add(layer);
							}
							ret.put("layers", layers);
						}
						else
							type = "Other";
					}
					else
						type = "Other";
					ret.put("type", type);
					return ret;
				}

				public JSONObject removed(JSONObject o, int idx, int incMod, int retIdx)
				{
					if("WMS".equals(o.get("type")))
						return null;
					return o;
				}

				public JSONObject set(JSONObject o1, int idx1, int incMod, Layer o2, int idx2,
					int retIdx)
				{
					o1.put("name", o2.getName());
					o1.put("enabled", new Boolean(o2.isVisible()));
					if(o2 instanceof PlugInLayer
						&& ((PlugInLayer) o2).getPlugIn() instanceof WMSPlugIn)
					{
						WMSPlugIn wms = (WMSPlugIn) ((PlugInLayer) o2).getPlugIn();
						o1.put("url", wms.getQueryHeader());
						JSONArray layers = (JSONArray) o1.get("layers");
						if(layers == null)
						{
							if(wms.getLayers() != null)
								layers = new JSONArray();
							o1.put("layers", layers);
						}
						else
							layers.clear();
						if(layers != null)
							for(String layer : wms.getLayers().split(","))
								layers.add(layer);
					}
					return o1;
				}
			});
		prefLayers = new JSONArray();
		for(JSONObject jsonO : adjusted)
			prefLayers.add(jsonO);
		prefs.set(theLayersPref, prefLayers);
	}

	static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}
}
