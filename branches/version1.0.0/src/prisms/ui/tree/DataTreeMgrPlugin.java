/**
 * DataTreeMgrPlugin.java Created Oct 2, 2007 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;

/**
 * DataTreeMgrPlugin is an AppPlugin that listens for changes to a tree structure and sends events
 * instructing a client to change its representation of the tree
 */
public class DataTreeMgrPlugin extends DataTreeManager implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(DataTreeMgrPlugin.class);

	/**
	 * A selection mode determining what kind of selection this tree plugin supports on the server.
	 * This does not affect the client's selection mode, but only what happens on the server when
	 * nodes are selected.
	 */
	public static enum SelectionMode
	{
		/**
		 * Selection not supported on the server
		 */
		NONE,
		/**
		 * Single node selection supported
		 */
		SINGLE,
		/**
		 * Multiple node selection supported
		 */
		MULTIPLE;
	}

	private PrismsSession theSession;

	private String theName;

	private boolean isLazyLoading;

	private SelectionMode theSelectionMode;

	java.util.ArrayList<DataTreeNode> theSelection;

	/**
	 * Creates a DataTreeMgrPlugin
	 */
	public DataTreeMgrPlugin()
	{
		theSelection = new java.util.ArrayList<DataTreeNode>();
		theSelectionMode = SelectionMode.NONE;
		addListener(new DataTreeListener()
		{
			public void changeOccurred(DataTreeEvent evt)
			{
				JSONObject ret = new JSONObject();
				ret.put("plugin", getName());
				String method;
				JSONArray jsonPath = jsonPath(evt.getNode());
				switch(evt.getType())
				{
				case ADD:
					method = "nodeAdded";
					ret.put("path", jsonPath);
					ret.put("index", new Integer(evt.getIndex()));
					break;
				case REMOVE:
					method = "nodeRemoved";
					ret.put("path", jsonPath);
					break;
				case CHANGE:
					method = "nodeChanged";
					ret.put("path", jsonPath);
					if(!evt.isRecursive())
						((JSONObject) jsonPath.get(jsonPath.size() - 1)).remove("children");
					ret.put("recursive", new Boolean(evt.isRecursive()));
					break;
				case MOVE:
					method = "nodeMoved";
					ret.put("path", jsonPath);
					ret.put("index", new Integer(evt.getIndex()));
					break;
				case REFRESH:
					method = "refresh";
					ret.put("root", jsonPath.get(0));
					break;
				default:
					method = null;
				}
				ret.put("method", method);
				getSession().postOutgoingEvent(ret);
			}
		});
	}

	/**
	 * @return This plugin's session
	 */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/**
	 * @return This plugin's name
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * @return This tree's selection mode
	 */
	public SelectionMode getSelectionMode()
	{
		return theSelectionMode;
	}

	/**
	 * @param mode The selection mode for this tree
	 */
	public synchronized void setSelectionMode(SelectionMode mode)
	{
		theSelectionMode = mode;
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setSelectionMode");
		evt.put("selectionMode", mode.toString().toLowerCase());
		if(getSession() != null)
			getSession().postOutgoingEvent(evt);
		switch(theSelectionMode)
		{
		case NONE:
			for(DataTreeNode node : theSelection)
				node.setSelected(false);
			theSelection.clear();
			break;
		case SINGLE:
			for(int s = 0; s < theSelection.size() - 1; s++)
				theSelection.get(s).setSelected(false);
			DataTreeNode node = null;
			if(theSelection.size() > 0)
				node = theSelection.get(theSelection.size() - 1);
			theSelection.clear();
			if(node != null)
				theSelection.add(node);
			break;
		default:
			break;
		}
	}

	/**
	 * @return All selected tree nodes in this manager
	 */
	public DataTreeNode [] getSelection()
	{
		return theSelection.toArray(new DataTreeNode [theSelection.size()]);
	}

	/**
	 * Selects or deselects a particular node
	 * 
	 * @param node The node to change the selection status of
	 * @param selected Whether the node is to be selected or deselected
	 */
	public synchronized void setSelected(DataTreeNode node, boolean selected)
	{
		if(theSelection.contains(node) == selected)
			return;
		if(selected)
		{
			switch(theSelectionMode)
			{
			case NONE:
				return;
			case SINGLE:
				for(DataTreeNode selNode : theSelection)
					selNode.setSelected(false);
				theSelection.clear();
				break;
			default:
				break;
			}
			theSelection.add(node);
			node.setSelected(true);
		}
		else
		{
			theSelection.remove(node);
			node.setSelected(false);
		}
	}

	/**
	 * @param nodes The set of nodes to be selected in this tree
	 * @param fromUser Whether the operation is user-initiated (and therefore can be vetoed
	 *        programmatically) or is from a programmatic change (and therefore unvetoable)
	 */
	public synchronized void setSelection(DataTreeNode [] nodes, final boolean fromUser)
	{
		switch(theSelectionMode)
		{
		case NONE:
			return;
		case SINGLE:
			if(nodes.length > 1)
				nodes = new DataTreeNode [] {nodes[nodes.length - 1]};
			break;
		default:
			break;
		}
		prisms.util.ArrayUtils.adjust(theSelection.toArray(new DataTreeNode [theSelection.size()]),
			nodes, new prisms.util.ArrayUtils.DifferenceListener<DataTreeNode, DataTreeNode>()
			{
				/**
				 * @see prisms.util.ArrayUtils.DifferenceListener#identity(java.lang.Object,
				 *      java.lang.Object)
				 */
				public boolean identity(DataTreeNode o1, DataTreeNode o2)
				{
					return o1 == o2;
				}

				public DataTreeNode set(DataTreeNode o1, int idx1, int incMod, DataTreeNode o2,
					int idx2, int retIdx)
				{
					return o1;
				}

				/**
				 * @see prisms.util.ArrayUtils.DifferenceListener#added(java.lang.Object, int, int)
				 */
				public DataTreeNode added(DataTreeNode o, int index, int retIdx)
				{
					theSelection.add(o);
					if(fromUser)
						o.userSetSelected(true);
					else
						o.setSelected(true);
					return o;
				}

				public DataTreeNode removed(DataTreeNode o, int index, int incMod, int retIdx)
				{
					theSelection.remove(o);
					if(fromUser)
						o.userSetSelected(false);
					else
						o.setSelected(false);
					return null;
				}
			});
	}

	/**
	 * @return Whether this tree publishes all its data to the client on initialization or only as
	 *         the information is needed by the tree (when the user expands a node)
	 */
	public boolean isLazyLoading()
	{
		return isLazyLoading;
	}

	/**
	 * @param lazyLoading Whether this tree should publish all its data to the client on
	 *        initialization or only as the information is needed by the tree (when the user expands
	 *        a node)
	 */
	public void setLazyLoading(boolean lazyLoading)
	{
		isLazyLoading = lazyLoading;
	}

	/**
	 * @see prisms.arch.AppPlugin#initClient()
	 */
	public void initClient()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", getName());
		evt.put("method", "refresh");
		evt.put("root", serializeRecursive(getRoot()));
		getSession().postOutgoingEvent(evt);
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setSelectionMode");
		evt.put("selectionMode", getSelectionMode().toString().toLowerCase());
		getSession().postOutgoingEvent(evt);
	}

	/**
	 * Creates a JSONArray from a java array
	 * 
	 * @param node The node to get the path of
	 * @return The path as a JSONArray
	 */
	public JSONArray jsonPath(DataTreeNode node)
	{
		JSONArray pathArr = new JSONArray();
		pathArr.add(serializeRecursive(node));
		for(node = node.getParent(); node != null; node = node.getParent())
		{
			if(node instanceof JsonTreeNode)
				pathArr.add(0, ((JsonTreeNode) node).toJSON());
			else
				pathArr.add(0, new JSONObject());
		}
		return pathArr;
	}

	private JSONObject serializeRecursive(DataTreeNode node)
	{
		if(node == null)
			return null;
		JSONObject ret;
		if(node instanceof JsonTreeNode)
			ret = ((JsonTreeNode) node).toJSON();
		else
			ret = new JSONObject();
		if(node.getChildren() == null || node.getChildren().length == 0)
			ret.put("children", new JSONArray());
		else if(!isLazyLoading || node.getChildren().length == 0)
		{
			JSONArray children = new JSONArray();
			for(DataTreeNode child : node.getChildren())
				children.add(serializeRecursive(child));
			ret.put("children", children);
		}
		return ret;
	}

	/**
	 * @see prisms.arch.AppPlugin#initPlugin(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
	}

	/**
	 * Performs an action on a group of nodes
	 * 
	 * @param nodes The nodes to perform the action on
	 * @param action The action to perform
	 */
	public void performAction(DataTreeNode [] nodes, String action)
	{
		for(DataTreeNode node : nodes)
			node.doAction(action);
	}

	/**
	 * @see prisms.arch.AppPlugin#processEvent(org.json.simple.JSONObject)
	 */
	public void processEvent(JSONObject evt)
	{
		if("loadChildren".equals(evt.get("method")))
		{
			if(!(evt.get("path") instanceof JSONArray))
				throw new IllegalArgumentException("path array expected");
			String [] path = (String []) ((JSONArray) evt.get("path")).toArray(new String [0]);
			DataTreeNode target = navigate(path);
			JSONObject ret = new JSONObject();
			ret.put("plugin", getName());
			ret.put("method", "loadChildren");
			JSONArray jsonPath = jsonPath(target);
			JSONArray jsonTargetChildren = (JSONArray) ((JSONObject) jsonPath
				.get(jsonPath.size() - 1)).get("children");
			if(jsonTargetChildren == null)
			{
				jsonTargetChildren = new JSONArray();
				((JSONObject) jsonPath.get(jsonPath.size() - 1))
					.put("children", jsonTargetChildren);
			}
			jsonTargetChildren.clear();
			for(int c = 0; c < target.getChildren().length; c++)
				jsonTargetChildren.add(serializeRecursive(target.getChildren()[c]));
			ret.put("path", jsonPath);
			getSession().postOutgoingEvent(ret);
		}
		else if("actionPerformed".equals(evt.get("method")))
		{
			if(!(evt.get("paths") instanceof JSONArray))
				throw new IllegalArgumentException("paths array expected");
			if(!(evt.get("action") instanceof String))
				throw new IllegalArgumentException("action string expected");
			String action = (String) evt.get("action");
			JSONArray [] jsonPaths = (JSONArray []) ((JSONArray) evt.get("paths"))
				.toArray(new JSONArray [0]);
			DataTreeNode [] nodes = new DataTreeNode [0];
			for(int p = 0; p < jsonPaths.length; p++)
			{
				String [] path = (String []) jsonPaths[p].toArray(new String [jsonPaths[p].size()]);
				DataTreeNode target;
				try
				{
					target = navigate(path);
				} catch(IllegalArgumentException e)
				{
					log.error("Could not find path for action " + action, e);
					continue;
				}
				nodes = prisms.util.ArrayUtils.add(nodes, target);
			}
			if(nodes.length > 0)
				performAction(nodes, action);
		}
		else if("notifySelection".equals(evt.get("method")))
		{
			if(!(evt.get("paths") instanceof JSONArray))
				throw new IllegalArgumentException("paths array expected");
			JSONArray pathsArray = (JSONArray) evt.get("paths");
			DataTreeNode [] newSelected = new DataTreeNode [pathsArray.size()];
			String [] path;
			for(int n = 0; n < newSelected.length; n++)
			{
				path = (String []) ((JSONArray) pathsArray.get(n)).toArray(new String [0]);
				newSelected[n] = navigate(path);
			}
			setSelection(newSelected, true);
		}
		else
			throw new IllegalArgumentException("Unrecognized event: " + evt);
	}
}
