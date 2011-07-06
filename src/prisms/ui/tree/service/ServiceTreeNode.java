/*
 * ServiceTreeNode.java Created Apr 25, 2011 by Andrew Butler, PSL
 */
package prisms.ui.tree.service;

import org.json.simple.JSONObject;

import prisms.ui.tree.DataTreeNode;
import prisms.ui.tree.service.ServiceTree.TreeClient;
import prisms.util.ArrayUtils;

/**
 * A ServiceTreeNode is a node under a {@link ServiceTree} and represents a hierarchical piece of
 * data. The node's children may be pre-loaded so that they are initially available to the client,
 * or they may be showable on demand. In addition, nodes may appear differently to different clients
 * and a node's children may be unviewable to some clients.
 */
public abstract class ServiceTreeNode extends prisms.ui.tree.AbstractSimpleTreeNode
{
	private final boolean isPreLoaded;

	private TreeClient [] theClients;

	private boolean isLoaded;

	private TreeClient [] theLoadedAllClients;

	/**
	 * Creates a service tree node
	 * 
	 * @param service The manager for this node's tree
	 * @param parent The parent node for this node
	 * @param preLoaded Whether this node has its child data pre-loaded or not
	 */
	public ServiceTreeNode(ServiceTree service, ServiceTreeNode parent, boolean preLoaded)
	{
		super(service, Long.toHexString((long) (Math.random() * Long.MAX_VALUE)), parent);
		isPreLoaded = preLoaded;
		isLoaded = isPreLoaded;
		setChildren(new ServiceTreeNode [0]);
		theClients = new TreeClient [0];
		theLoadedAllClients = new TreeClient [0];
	}

	/**
	 * Creates a service tree node, specifying the node's ID. This is useful for collecting the
	 * information from multiple server trees into a single client tree.
	 * 
	 * @param service The manager for this node's tree
	 * @param id The ID for this node
	 * @param parent The parent node for this node
	 * @param preLoaded Whether this node has its child data pre-loaded or not
	 */
	public ServiceTreeNode(ServiceTree service, String id, ServiceTreeNode parent, boolean preLoaded)
	{
		super(service, id, parent);
		isPreLoaded = preLoaded;
		isLoaded = isPreLoaded;
		setChildren(new ServiceTreeNode [0]);
		theClients = new TreeClient [0];
		theLoadedAllClients = new TreeClient [0];
	}

	@Override
	public ServiceTree getManager()
	{
		return (ServiceTree) super.getManager();
	}

	@Override
	public ServiceTreeNode getParent()
	{
		return (ServiceTreeNode) super.getParent();
	}

	@Override
	public ServiceTreeNode [] getChildren()
	{
		return (ServiceTreeNode []) theChildren;
	}

	@Override
	public void setChildren(DataTreeNode [] children)
	{
		if(!(children instanceof ServiceTreeNode []))
		{
			ServiceTreeNode [] newCh = new ServiceTreeNode [children.length];
			for(int i = 0; i < children.length; i++)
			{
				if(children[i] instanceof ServiceTreeNode)
					newCh[i] = (ServiceTreeNode) children[i];
				else
					throw new IllegalArgumentException("Only ServiceTreeNodes may be children"
						+ " of a ServiceTreeNode");
			}
			children = newCh;
		}
		super.setChildren(children);
	}

	/** @return All clients that have loaded this node's child data (for non-pre-loaded nodes) */
	public TreeClient [] getLoadedClients()
	{
		return theClients;
	}

	/**
	 * @return Whether this node is pre-loaded, meaning that clients don't need to ask for the child
	 *         data--it is sent by default
	 */
	public boolean isPreLoaded()
	{
		return isPreLoaded;
	}

	/** @return Whether this node's child data has been loaded by any session */
	public boolean isLoaded()
	{
		return isLoaded;
	}

	/**
	 * @param client The client to test
	 * @return Whether the given client has this node's sub data in its view
	 */
	public boolean isLoaded(TreeClient client)
	{
		return isPreLoaded || prisms.util.ArrayUtils.contains(theClients, client);
	}

	/**
	 * @param client The client to test
	 * @return Whether the given client has loaded all of this node's data
	 */
	public boolean isAllLoaded(TreeClient client)
	{
		return prisms.util.ArrayUtils.contains(theClients, client);
	}

	/**
	 * Serializes this node (sans children) into a JSON object for transmission to the client
	 * 
	 * @param client The client to serialize this node for
	 * @return The serialized node
	 */
	public JSONObject toJSON(TreeClient client)
	{
		JSONObject ret = new JSONObject();
		ret.put("id", getID());
		ret.put("text", getText(client));
		ret.put("description", getDescription(client));
		ret.put("icon", getIcon(client));
		ret.put("bgColor", prisms.util.ColorUtils.toHTML(getBackground(client)));
		ret.put("textColor", prisms.util.ColorUtils.toHTML(getForeground(client)));
		ret.put("actions", prisms.util.JsonUtils.serialize(getActions(client)));
		String loadAction = isLoaded(client) ? null : getLoadAction(client);
		String loadAllAction = isLoaded(client) ? null : getLoadAllAction(client);
		String unloadAction = (isPreLoaded || !isLoaded(client)) ? null : getUnloadAction(client);
		if(loadAction != null)
			ret.put("loadAction", loadAction);
		if(loadAllAction != null)
			ret.put("loadAllAction", loadAllAction);
		if(unloadAction != null)
			ret.put("unloadAction", unloadAction);
		return ret;
	}

	/**
	 * @param client The client to represent the data to
	 * @return The text to display for this node to the given client
	 */
	public String getText(TreeClient client)
	{
		return getText();
	}

	/**
	 * @param client The client to represent the data to
	 * @return The icon to display for this node to the given client
	 */
	public String getIcon(TreeClient client)
	{
		return getIcon();
	}

	/**
	 * @param client The client to represent the data to
	 * @return The description to display for this node to the given client
	 */
	public String getDescription(TreeClient client)
	{
		return getDescription();
	}

	/**
	 * @param client The client to represent the data to
	 * @return The background color to display for this node to the given client
	 */
	public java.awt.Color getBackground(TreeClient client)
	{
		return getBackground();
	}

	/**
	 * @param client The client to represent the data to
	 * @return The text color to display for this node to the given client
	 */
	public java.awt.Color getForeground(TreeClient client)
	{
		return getForeground();
	}

	/**
	 * @param client The client to represent the data to
	 * @return The actions to display for this node to the given client
	 */
	public prisms.ui.list.NodeAction[] getActions(TreeClient client)
	{
		return getActions();
	}

	/**
	 * @param client The client requesting the action
	 * @param action The action requested by the client
	 */
	public void doAction(TreeClient client, String action)
	{
		doAction(action);
	}

	/**
	 * @param client The client to get the load action representation for
	 * @return The action to represent the ability to load this node's child data to the client
	 */
	public String getLoadAction(TreeClient client)
	{
		return "Load Content";
	}

	/**
	 * @param client The client to get the load all action representation for
	 * @return The action to represent the ability to load all of this node's descendant data to the
	 *         client
	 */
	public String getLoadAllAction(TreeClient client)
	{
		return "Load All Content";
	}

	/**
	 * @param client The client to get the unload action representation for
	 * @return The action to represent the ability to hide this node's child data from the client
	 */
	public String getUnloadAction(TreeClient client)
	{
		return "Unload Content";
	}

	/**
	 * Though this method does nothing by default (but call the method on its children), it allows
	 * nodes to display data that may change without events or where listening to the events is
	 * impractical. This method is called each and every time a client "pings" the server tree for
	 * changes. The tree is not locked before this is called, so no structural changes may occur as
	 * a result of this call. Calls to {@link ServiceTree#wlock()} will fail from this method, since
	 * the tree is read-locked for the client.
	 * 
	 * @param client The client that has pinged the tree
	 */
	public void check(TreeClient client)
	{
		if(!isLoaded(client))
			return;
		for(ServiceTreeNode child : getChildren())
			child.check(client);
	}

	/**
	 * Called when a client requests to view this node's child data
	 * 
	 * @param client The client that wants to see the data
	 * @param recursive Whether the client has requested to view all the data under this tree as
	 *        opposed to this node's direct children
	 */
	public void loadContent(TreeClient client, boolean recursive)
	{
		if(!recursive && getLoadAction(client) == null)
			throw new IllegalArgumentException("User " + client.user + " may not load content of "
				+ getText(client));
		if(recursive && getLoadAllAction(client) == null)
			throw new IllegalArgumentException("User " + client.user + " may not load all"
				+ " content of " + getText(client));
		if(!isLoaded)
			load(client);
		if(!ArrayUtils.contains(theClients, client))
			theClients = ArrayUtils.add(theClients, client);
		if(recursive && !ArrayUtils.contains(theLoadedAllClients, client))
			theLoadedAllClients = ArrayUtils.add(theLoadedAllClients, client);
		if(recursive)
			for(ServiceTreeNode child : getChildren())
				child.loadSubContent(client);
		prisms.ui.tree.DataTreeEvent evt = new prisms.ui.tree.DataTreeEvent(
			prisms.ui.tree.DataTreeEvent.Type.CHANGE, this);
		evt.setRecursive(true);
		client.addEvent(evt);
	}

	/**
	 * Loads this sub-tree's data into the client's view
	 * 
	 * @param client The client requesting the load
	 */
	protected void loadSubContent(TreeClient client)
	{
		boolean all = true;
		if(!isPreLoaded)
		{
			if(getLoadAllAction(client) == null)
			{
				if(getLoadAction(client) == null)
					return;
				else
					all = false;
			}
			if(!isLoaded)
				load(client);
		}
		if(!ArrayUtils.contains(theClients, client))
			theClients = ArrayUtils.add(theClients, client);
		if(!ArrayUtils.contains(theLoadedAllClients, client))
			theLoadedAllClients = ArrayUtils.add(theLoadedAllClients, client);
		if(all)
			for(ServiceTreeNode child : getChildren())
				child.loadSubContent(client);
	}

	/**
	 * Called when a client decides they don't need to see this node's child data anymore
	 * 
	 * @param client The client that has hidden this node's sub data from their view
	 * @param withEvent Whether to fire an event to the client
	 */
	public void unloadContent(TreeClient client, boolean withEvent)
	{
		if(ArrayUtils.contains(theClients, client))
		{
			theClients = ArrayUtils.remove(theClients, client);
			for(ServiceTreeNode child : getChildren())
				child.unloadContent(client, false);
			if(!isPreLoaded && theClients.length == 0)
				unloaded();
		}

		if(withEvent)
		{
			prisms.ui.tree.DataTreeEvent evt = new prisms.ui.tree.DataTreeEvent(
				prisms.ui.tree.DataTreeEvent.Type.CHANGE, this);
			evt.setRecursive(true);
			client.addEvent(evt);
		}
	}

	/**
	 * Loads this node's data. This method does nothing but set a flag. It should be overridden by
	 * subclasses.
	 * 
	 * @param client The client requesting the initial data load
	 */
	protected void load(TreeClient client)
	{
		isLoaded = true;
	}

	/**
	 * Called when this node's child data is no longer viewed by any clients. The node may choose to
	 * unload its child data to free resources.
	 */
	protected void unloaded()
	{
		if(isPreLoaded)
			return;
		isLoaded = false;
	}

	@Override
	public void add(prisms.ui.tree.DataTreeNode node, int index)
	{
		if(!(node instanceof ServiceTreeNode))
			throw new IllegalArgumentException(
				"Service tree nodes must be extensions of ServiceTreeNode");
		getManager().wlock();
		try
		{
			for(TreeClient client : theLoadedAllClients)
				((ServiceTreeNode) node).loadSubContent(client);
			super.add(node, index);
		} finally
		{
			getManager().wunlock();
		}
	}

	@Override
	public void remove(int index)
	{
		getManager().wlock();
		try
		{
			super.remove(index);
		} finally
		{
			getManager().wunlock();
		}
	}

	@Override
	public void changed(boolean recursive)
	{
		getManager().wlock();
		try
		{
			for(TreeClient client : theClients)
				if(getLoadAction(client) == null)
					unloadContent(client, false);
			if(recursive && theLoadedAllClients.length > 0)
				for(ServiceTreeNode child : getChildren())
					for(TreeClient client : theLoadedAllClients)
						child.loadSubContent(client);
			super.changed(recursive);
		} finally
		{
			getManager().wunlock();
		}
	}

	@Override
	public void move(int fromIdx, int toIdx)
	{
		getManager().wlock();
		try
		{
			super.move(fromIdx, toIdx);
		} finally
		{
			getManager().wunlock();
		}
	}

	/** Returns false--selection is a client-side function */
	@Override
	public boolean isSelected()
	{
		return false;
	}

	/** Does nothing--selection is a client-side function */
	@Override
	public void setSelected(boolean selected)
	{
	}

	/** Does nothing--selection is a client-side function */
	@Override
	public void userSetSelected(boolean selected)
	{
	}
}
