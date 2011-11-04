/*
 * DataListMgrPlugin.java Created Jan 29, 2008 by Andrew Butler, PSL
 */
package prisms.ui.list;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;

/** An PRISMS plugin that manages a list of data */
public class DataListMgrPlugin extends DataListManager implements prisms.arch.AppPlugin
{
	/** A listener that posts appropriate events to the client to represent node changes */
	public static class NodeEventListener implements DataListListener
	{
		private final DataListMgrPlugin theMgr;

		/** @param mgr The manager plugin to listen for */
		public NodeEventListener(DataListMgrPlugin mgr)
		{
			theMgr = mgr;
		}

		/** @return The manager that this listener is listening for */
		public DataListMgrPlugin getMgr()
		{
			return theMgr;
		}

		public void changeOccurred(DataListEvent evt)
		{
			prisms.arch.PrismsTransaction trans = theMgr.getSession().getApp().getEnvironment()
				.getTransaction();
			if(trans != null
				&& trans.getStage() != prisms.arch.PrismsTransaction.Stage.processEvent)
				return;
			JSONObject ret = new JSONObject();
			ret.put("plugin", theMgr.getName());
			String method;
			JSONObject jsonNode = theMgr.serialize(evt.getNode());
			switch(evt.getType())
			{
			case ADD:
				method = "addItem";
				ret.put("item", jsonNode);
				ret.put("index", Integer.valueOf(evt.getIndex()));
				break;
			case REMOVE:
				method = "removeItem";
				ret.put("item", jsonNode);
				break;
			case MOVE:
				method = "moveItem";
				ret.put("item", jsonNode);
				for(int i = 0; i < theMgr.getItemCount(); i++)
					if(theMgr.getItem(i) == evt.getNode())
					{
						ret.put("index", Integer.valueOf(i));
						break;
					}
				break;
			case CHANGE:
				method = "changeItem";
				ret.put("item", jsonNode);
				break;
			default:
				method = null;
			}
			ret.put("method", method);
			theMgr.getSession().postOutgoingEvent(ret);
		}
	}

	private static final Logger log = Logger.getLogger(DataListMgrPlugin.class);

	/**
	 * A selection mode determining what kind of selection this list plugin supports on the server.
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

	private SelectionMode theSelectionMode;

	private DataListListener theListener;

	/** Creates a data list manager plugin */
	public DataListMgrPlugin()
	{
		theSelectionMode = SelectionMode.NONE;
		theListener = new NodeEventListener(this);
		addListener(theListener);
	}

	/** Clears out the listener that monitors this data set to send UI events */
	protected void clearListener()
	{
		if(theListener != null)
		{
			removeListener(theListener);
			theListener = null;
		}
	}

	@Override
	public void setItems(DataListNode [] items)
	{
		super.setItems(items);
		initClient();
	}

	/** @return This plugin's session */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/** @return This plugin's name */
	public String getName()
	{
		return theName;
	}

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
	}

	public void initClient()
	{
		prisms.arch.PrismsTransaction trans = getSession().getApp().getEnvironment()
			.getTransaction();
		if(trans != null
			&& trans.getStage().ordinal() < prisms.arch.PrismsTransaction.Stage.initSession
				.ordinal())
			return;

		JSONObject evt = new JSONObject();
		evt.put("plugin", getName());
		evt.put("method", "setItems");
		JSONArray items = new JSONArray();
		for(int i = 0; i < getItemCount(); i++)
			items.add(serialize(getItem(i)));
		evt.put("items", items);
		getSession().postOutgoingEvent(evt);

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setSelectionMode");
		evt.put("selectionMode", getSelectionMode().toString().toLowerCase());
		getSession().postOutgoingEvent(evt);

		setListParams();
	}

	/** Sends list of parameters to the client to represent this list as a whole */
	public void setListParams()
	{
		if(getSession() == null)
			return;
		JSONObject evt = new JSONObject();
		evt.put("plugin", getName());
		evt.put("method", "setListParams");
		evt.put("params", getListParams());
		getSession().postOutgoingEvent(evt);
	}

	/** @return A list of parameters to the client to represent this list as a whole */
	protected JSONObject getListParams()
	{
		JSONObject params = new JSONObject();
		params.put("title", getTitle());
		params.put("icon", getIcon());
		params.put("description", getDescription());
		params.put("actions", prisms.util.JsonUtils.serialize(getActions()));
		return params;
	}

	/** @return This list's title */
	public String getTitle()
	{
		return getName();
	}

	/** @return The icon to represent this list */
	public String getIcon()
	{
		return null;
	}

	/** @return A description for this list */
	public String getDescription()
	{
		return null;
	}

	/** @return This list's selection mode */
	public SelectionMode getSelectionMode()
	{
		return theSelectionMode;
	}

	/** @param mode The selection mode for this list */
	public synchronized void setSelectionMode(SelectionMode mode)
	{
		theSelectionMode = mode;
		if(getSession() != null)
		{
			JSONObject evt = new JSONObject();
			evt.put("plugin", theName);
			evt.put("method", "setSelectionMode");
			evt.put("selectionMode", mode.toString().toLowerCase());
			getSession().postOutgoingEvent(evt);
		}
		switch(theSelectionMode)
		{
		case NONE:
			for(int i = 0; i < getItemCount(); i++)
				if(getItem(i).isSelected())
					getItem(i).setSelected(false);
			break;
		case SINGLE:
			boolean hasSelected = false;
			for(int i = getItemCount() - 1; i >= 0; i--)
				if(getItem(i).isSelected())
				{
					if(hasSelected)
						getItem(i).setSelected(false);
					else
						hasSelected = true;
				}
			break;
		default:
			break;
		}
	}

	/** @return All selected nodes in this manager */
	public DataListNode [] getSelection()
	{
		java.util.ArrayList<DataListNode> ret = new java.util.ArrayList<DataListNode>();
		for(int i = 0; i < getItemCount(); i++)
			if(getItem(i).isSelected())
				ret.add(getItem(i));
		return ret.toArray(new DataListNode [ret.size()]);
	}

	/**
	 * Selects or deselects a particular node
	 * 
	 * @param node The node to change the selection status of
	 * @param selected Whether the node is to be selected or deselected
	 */
	public synchronized void setSelected(DataListNode node, boolean selected)
	{
		if(node.isSelected())
			return;
		if(selected)
		{
			switch(theSelectionMode)
			{
			case NONE:
				return;
			case SINGLE:
				for(int i = 0; i < getItemCount(); i++)
					if(getItem(i) != node && getItem(i).isSelected())
						getItem(i).setSelected(false);
				break;
			default:
				break;
			}
			node.setSelected(true);
		}
		else
			node.setSelected(false);
	}

	/**
	 * @param nodes The set of nodes to be selected in this list
	 * @param fromUser Whether the operation is user-initiated (and therefore can be vetoed
	 *        programmatically) or is from a programmatic change (and therefore unvetoable)
	 */
	public synchronized void setSelection(DataListNode [] nodes, boolean fromUser)
	{
		switch(theSelectionMode)
		{
		case NONE:
			return;
		case SINGLE:
			if(nodes.length > 1)
				nodes = new DataListNode [] {nodes[nodes.length - 1]};
			break;
		default:
			break;
		}
		// Deselect nodes first
		for(int i = 0; i < getItemCount(); i++)
		{
			boolean selected = prisms.util.ArrayUtils.contains(nodes, getItem(i));
			if(getItem(i).isSelected() && !selected)
			{
				if(fromUser)
					getItem(i).userSetSelected(false);
				else
					getItem(i).setSelected(false);
			}
		}
		// Now select nodes
		for(int i = 0; i < getItemCount(); i++)
		{
			boolean selected = prisms.util.ArrayUtils.contains(nodes, getItem(i));
			if(!getItem(i).isSelected() && selected)
			{
				if(fromUser)
					getItem(i).userSetSelected(true);
				else
					getItem(i).setSelected(true);
			}
		}
	}

	/** @return Actions that may be done on the list as a whole */
	public NodeAction [] getActions()
	{
		return new NodeAction [0];
	}

	/**
	 * Performs an action on this list
	 * 
	 * @param action The action to perform
	 */
	public void doAction(String action)
	{
	}

	/**
	 * Serializes a node
	 * 
	 * @param node The node to serialize
	 * @return A JSONObject representing the node
	 */
	protected JSONObject serialize(DataListNode node)
	{
		if(node instanceof JsonListNode)
			return ((JsonListNode) node).toJSON();
		else
			throw new IllegalArgumentException(DataListMgrPlugin.class.getName()
				+ " must use only JsonListNodes");
	}

	/**
	 * Performs an action on a group of items
	 * 
	 * @param items The items to perform the action on
	 * @param action The action to perform
	 */
	public void performAction(DataListNode [] items, String action)
	{
		for(DataListNode item : items)
			item.doAction(action);
	}

	public void processEvent(JSONObject evt)
	{
		if("actionPerformed".equals(evt.get("method")))
		{
			if(!(evt.get("action") instanceof String))
				throw new IllegalArgumentException("action string expected");
			String action = (String) evt.get("action");
			JSONArray ids = (JSONArray) evt.get("paths");
			if(ids == null)
				doAction(action);
			else
			{
				DataListNode [] items = new DataListNode [0];
				int i, j;
				for(i = 0; i < ids.size(); i++)
				{
					for(j = 0; j < getItemCount(); j++)
						if(ids.get(i).equals(getItem(j).getID()))
						{
							items = prisms.util.ArrayUtils.add(items, getItem(j));
							break;
						}
					if(j == getItemCount())
					{
						log.error("Unrecognized path: " + ids.get(i));
						continue;
					}
				}
				if(items.length > 0)
					performAction(items, action);
			}
		}
		else if("notifySelection".equals(evt.get("method")))
		{
			String [] ids = (String []) ((JSONArray) evt.get("ids")).toArray(new String [0]);
			DataListNode [] selection = new DataListNode [0];
			for(int i = 0; i < getItemCount(); i++)
			{
				if(prisms.util.ArrayUtils.contains(ids, getItem(i).getID()))
					selection = prisms.util.ArrayUtils.add(selection, getItem(i));
			}
			setSelection(selection, true);
		}
		else
			throw new IllegalArgumentException("Unrecognized event: " + evt);
	}
}
