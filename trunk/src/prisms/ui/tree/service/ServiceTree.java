/*
 * ServiceTree.java Created Apr 25, 2011 by Andrew Butler, PSL
 */
package prisms.ui.tree.service;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.ui.tree.DataTreeEvent;
import prisms.ui.tree.DataTreeNode;

/**
 * A ServiceTree is designed to be a piece of a client's display. A {@link ClientTree} may point to
 * many ServiceTrees with a common data model and integrate all the data between each ServiceTree
 * instance. The ServiceTree's data may be configured to only load piece-by-piece on demand. This is
 * useful when the combined trees' data may be very large. The client may perform many operations on
 * the tree's data, defined completely by the subclasses of {@link ServiceTreeNode} used.
 */
public class ServiceTree extends prisms.ui.tree.DataTreeManager implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(ServiceTree.class);

	/** Represents a client that has a view of this tree */
	public class TreeClient
	{
		private class TreeEvent
		{
			final DataTreeEvent event;

			final JSONArray path;

			final long time;

			TreeEvent(DataTreeEvent evt)
			{
				event = evt;
				path = jsonPath((ServiceTreeNode) evt.getNode(), TreeClient.this);
				time = System.currentTimeMillis();
			}
		}

		/** The client's ID */
		public final String clientID;

		/** The user for this client's session. This may be used to determine the client's view. */
		public final prisms.arch.ds.User user;

		/** The UI widget to use to communicate with the client */
		public final prisms.ui.UI ui;

		/** The status plugin to use to communicate with the client */
		public final prisms.ui.StatusPlugin status;

		private prisms.util.preferences.Preferences thePrefs;

		private java.util.ArrayList<TreeEvent> theEventQueue;

		private long theLastCheck;

		/**
		 * Creates a TreeClient
		 * 
		 * @param id The ID of the client
		 * @param u The user using the client
		 */
		protected TreeClient(String id, prisms.arch.ds.User u)
		{
			clientID = id;
			user = u;
			ui = new prisms.ui.UI.NormalUI(getSession());
			status = new prisms.ui.StatusPlugin();
			status.initPlugin(getSession(), null);
			prisms.util.preferences.PreferencesPersister prefPersister = null;
			for(prisms.arch.event.PrismsPCL<?> pm : getSession().getPropertyChangeListeners(
				prisms.arch.event.PrismsProperties.preferences))
				if(pm instanceof prisms.util.persisters.UserSpecificManager
					&& ((prisms.util.persisters.UserSpecificManager<?>) pm).getPersister() instanceof prisms.util.preferences.PreferencesPersister)
				{
					prefPersister = (prisms.util.preferences.PreferencesPersister) ((prisms.util.persisters.UserSpecificManager<?>) pm)
						.getPersister();
					break;
				}
			if(prefPersister != null)
				thePrefs = prefPersister.getValue(getSession().getApp(), u);
			theEventQueue = new java.util.ArrayList<TreeEvent>();
		}

		/** @return The user for this client's session */
		public prisms.arch.ds.User getUser()
		{
			return user;
		}

		/** @return The UI widget to use to communicate with the client */
		public prisms.ui.UI getUI()
		{
			return ui;
		}

		/** @return The status plugin to use to communicate with the client */
		public prisms.ui.StatusPlugin getStatus()
		{
			return status;
		}

		/** @return The preferences of the client user */
		public prisms.util.preferences.Preferences getPreferences()
		{
			return thePrefs;
		}

		void checked()
		{
			theLastCheck = System.currentTimeMillis();
		}

		/** @return The last time this client checked in */
		public long getLastCheck()
		{
			return theLastCheck;
		}

		/** @param evt The tree event to report to this client */
		public synchronized void addEvent(DataTreeEvent evt)
		{
			boolean doAdd = true;
			if(evt.getType() == DataTreeEvent.Type.REFRESH)
				theEventQueue.clear();
			else if(evt.getType() == DataTreeEvent.Type.CHANGE)
			{
				java.util.Iterator<TreeEvent> iter = theEventQueue.iterator();
				while(iter.hasNext())
				{
					DataTreeEvent itEvt = iter.next().event;
					if(isAncestor(evt.getNode(), itEvt.getNode()))
					{
						if(evt.isRecursive()
							&& (evt.getNode() != itEvt.getNode() || evt.getType() == itEvt
								.getType()))
							iter.remove();
						else if(evt.getType() == itEvt.getType() && !itEvt.isRecursive()
							&& evt.getNode() == itEvt.getNode())
							iter.remove();
					}
				}
			}
			else if(evt.getType() == DataTreeEvent.Type.REMOVE)
			{
				java.util.Iterator<TreeEvent> iter = theEventQueue.iterator();
				while(iter.hasNext())
				{
					DataTreeEvent itEvt = iter.next().event;
					if(isAncestor(evt.getNode(), itEvt.getNode()))
					{
						if(itEvt.getNode() == evt.getNode()
							&& itEvt.getType() == DataTreeEvent.Type.ADD)
							doAdd = false;
						iter.remove();
					}
				}
			}
			else if(evt.getType() == DataTreeEvent.Type.MOVE)
			{
				java.util.Iterator<TreeEvent> iter = theEventQueue.iterator();
				while(iter.hasNext())
				{
					DataTreeEvent itEvt = iter.next().event;
					if(evt.getType() == itEvt.getType() && evt.getNode() == itEvt.getNode())
						iter.remove();
				}
			}
			if(doAdd)
				theEventQueue.add(new TreeEvent(evt));
		}

		synchronized JSONObject [] getEvents(long lastCheck)
		{
			java.util.ArrayList<JSONObject> ret = new java.util.ArrayList<JSONObject>();
			java.util.Iterator<TreeEvent> iter = theEventQueue.iterator();
			while(iter.hasNext())
			{
				TreeEvent evt = iter.next();
				if(evt.time < lastCheck)
				{
					iter.remove();
					continue;
				}
				JSONObject ret_i = new JSONObject();
				ret_i.put("plugin", getName());
				String method;
				switch(evt.event.getType())
				{
				case ADD:
					method = "nodeAdded";
					ret_i.put("path", evt.path);
					ret_i.put("index", Integer.valueOf(evt.event.getIndex()));
					break;
				case REMOVE:
					method = "nodeRemoved";
					ret_i.put("path", evt.path);
					break;
				case CHANGE:
					method = "nodeChanged";
					ret_i.put("path", evt.path);
					if(!evt.event.isRecursive())
						((JSONObject) evt.path.get(evt.path.size() - 1)).remove("children");
					ret_i.put("recursive", Boolean.valueOf(evt.event.isRecursive()));
					break;
				case MOVE:
					method = "nodeMoved";
					ret_i.put("path", evt.path);
					ret_i.put("index", Integer.valueOf(evt.event.getIndex()));
					break;
				case REFRESH:
					method = "refresh";
					ret_i.put("root", evt.path.get(0));
					break;
				default:
					method = null;
				}
				ret_i.put("method", method);
				ret.add(ret_i);
			}
			return ret.toArray(new JSONObject [ret.size()]);
		}
	}

	private PrismsSession theSession;

	private String theName;

	java.util.concurrent.ConcurrentLinkedQueue<TreeClient> theClients;

	java.util.concurrent.locks.ReentrantReadWriteLock theLock;

	private java.util.concurrent.locks.Lock theWriteLock;

	private long theClientSaveTime;

	/** Creates a ServiceTree */
	public ServiceTree()
	{
		theClients = new java.util.concurrent.ConcurrentLinkedQueue<TreeClient>();
		addListener(new prisms.ui.tree.DataTreeListener()
		{
			public void changeOccurred(DataTreeEvent evt)
			{
				for(TreeClient client : theClients)
					if(evt.getNode().getParent() == null
						|| ((ServiceTreeNode) evt.getNode().getParent()).isLoaded(client))
						client.addEvent(evt);
			}
		});
		theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
	}

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		getSession().getApp().scheduleOneTimeTask(new Runnable()
		{
			public void run()
			{
				purgeClients();
			}
		}, 30000);
		theClientSaveTime = config.getInt("client-save-time", 60000) * 1000L;
	}

	public void initClient()
	{
		// Service tree--no init client events
	}

	public void processEvent(JSONObject evt)
	{
		String clientID = (String) evt.get("clientID");
		TreeClient client = null;
		for(TreeClient c : theClients)
			if(c.clientID.equals(clientID))
			{
				client = c;
				break;
			}
		String method = (String) evt.get("method");
		boolean preInit = false;
		if(client == null)
		{
			preInit = true;
			if("destroyed".equals(method))
				return;
			String userName = (String) evt.get("user");
			if(userName == null)
				throw new IllegalArgumentException("user not specified for new client");
			client = init(clientID, userName);
		}
		long lastCheck = ((Number) evt.get("lastCheck")).longValue();
		client.checked();
		if("init".equals(method))
		{
			if(!preInit)
				client.addEvent(new DataTreeEvent(DataTreeEvent.Type.REFRESH, getRoot()));
		}
		else if("check".equals(method))
			check(client, lastCheck);
		else if("load".equals(method))
			load(client, (ServiceTreeNode) navigate(path(evt)),
				Boolean.TRUE.equals(evt.get("withDescendants")), lastCheck);
		else if("unload".equals(method))
			unload(client, (ServiceTreeNode) navigate(path(evt)), lastCheck);
		else if("actionPerformed".equals(method))
		{
			if(!(evt.get("paths") instanceof JSONArray))
				throw new IllegalArgumentException("paths array expected");
			if(!(evt.get("action") instanceof String))
				throw new IllegalArgumentException("action string expected");
			String action = (String) evt.get("action");
			JSONArray [] jsonPaths = (JSONArray []) ((JSONArray) evt.get("paths"))
				.toArray(new JSONArray [0]);
			ServiceTreeNode [] nodes = new ServiceTreeNode [0];
			for(int p = 0; p < jsonPaths.length; p++)
			{
				String [] path = (String []) jsonPaths[p].toArray(new String [jsonPaths[p].size()]);
				ServiceTreeNode target;
				try
				{
					target = (ServiceTreeNode) navigate(path);
				} catch(IllegalArgumentException e)
				{
					throw new IllegalStateException("Could not find path for action " + action, e);
				}
				nodes = prisms.util.ArrayUtils.add(nodes, target);
			}
			if(nodes.length > 0)
				performAction(client, nodes, action, lastCheck);
		}
		else if("destroyed".equals(method))
			destroyed(client);
		else if("refresh".equals(method) || "eventReturned".equals(method)
			|| "cancel".equals(method))
			client.ui.processEvent(evt);
		else
			throw new IllegalArgumentException("Unrecognized " + getName() + " event: " + evt);
	}

	/**
	 * @return The manager session that this tree is in. This is not a way to communicate with the
	 *         client. For that purpose, use the {@link TreeClient} class's fields.
	 */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/** @return This plugin's name */
	public String getName()
	{
		return theName;
	}

	@Override
	public ServiceTreeNode getRoot()
	{
		return (ServiceTreeNode) super.getRoot();
	}

	@Override
	public void setRoot(DataTreeNode root)
	{
		if(!(root instanceof ServiceTreeNode))
			throw new IllegalArgumentException(
				"Only ServiceTreeNodes may exist under a ServiceTree");
		super.setRoot(root);
	}

	/**
	 * Gets a string array path from an event
	 * 
	 * @param evt The event to get the path for
	 * @return The path pointed to by the event
	 */
	public static final String [] path(JSONObject evt)
	{
		return ((java.util.List<String>) evt.get("path")).toArray(new String [0]);
	}

	/**
	 * Locks this tree so that nothing can happen to it until {@link #wunlock()} is called. The tree
	 * should be locked every time its data or a client's view changes
	 */
	public void wlock()
	{
		java.util.concurrent.locks.Lock lock = theLock.writeLock();
		lock.lock();
		theWriteLock = lock;
	}

	/** Unlocks this tree after a call to {@link #wlock()} */
	public void wunlock()
	{
		theWriteLock.unlock();
	}

	/**
	 * Creates a new TreeClient for this tree
	 * 
	 * @param id The ID of the client
	 * @param u The user using the client
	 * @return The new TreeClient to use for this tree
	 */
	public TreeClient createClient(String id, prisms.arch.ds.User u)
	{
		return new TreeClient(id, u);
	}

	private TreeClient init(String clientID, String userName)
	{
		TreeClient client = null;
		wlock();
		try
		{
			for(TreeClient c : theClients)
				if(c.clientID.equals(clientID))
				{
					client = c;
					break;
				}
			if(client == null)
			{
				prisms.arch.ds.User user;
				try
				{
					user = getSession().getApp().getEnvironment().getUserSource().getUser(userName);
				} catch(prisms.arch.PrismsException e)
				{
					throw new IllegalStateException("Could not get user named " + userName, e);
				}
				if(user == null)
					throw new IllegalStateException("No such user named " + userName);
				client = createClient(clientID, user);
				theClients.add(client);
			}
			client.addEvent(new DataTreeEvent(DataTreeEvent.Type.REFRESH, getRoot()));
			for(JSONObject evt : client.getEvents(-1))
				getSession().postOutgoingEvent(evt);
		} finally
		{
			wunlock();
		}
		return client;
	}

	private void check(TreeClient client, long lastCheck)
	{
		java.util.concurrent.locks.Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			getRoot().check(client);
			for(JSONObject evt : client.getEvents(lastCheck))
				getSession().postOutgoingEvent(evt);
		} finally
		{
			lock.unlock();
		}
	}

	private void load(TreeClient client, ServiceTreeNode node, boolean recursive, long lastCheck)
	{
		wlock();
		try
		{
			node.loadContent(client, recursive);
			for(JSONObject evt : client.getEvents(lastCheck))
				getSession().postOutgoingEvent(evt);
		} catch(Throwable e)
		{
			log.error("Could not load " + (recursive ? "descendants " : "children ") + " of "
				+ node.getText(client), e);
			client.ui.error("Load failed: " + e.getMessage());
		} finally
		{
			wunlock();
		}
	}

	private void unload(TreeClient client, ServiceTreeNode node, long lastCheck)
	{
		wlock();
		try
		{
			node.unloadContent(client, true);
			for(JSONObject evt : client.getEvents(lastCheck))
				getSession().postOutgoingEvent(evt);
		} catch(Throwable e)
		{
			log.error("Could not unload children  of " + node.getText(client), e);
			client.ui.error("Unload failed: " + e.getMessage());
		} finally
		{
			wunlock();
		}
	}

	/**
	 * Called when the client requests a custom action on one or more tree nodes
	 * 
	 * @param client The client that requested the action
	 * @param nodes The nodes that the client selected for the action
	 * @param action The action that was requested
	 * @param lastCheck The last time the client received data from this tree
	 */
	protected void performAction(TreeClient client, ServiceTreeNode [] nodes, String action,
		long lastCheck)
	{
		try
		{
			for(ServiceTreeNode node : nodes)
				node.doAction(client, action);
		} catch(Throwable e)
		{
			log.error("Could not execute action " + action, e);
			client.ui.error("Could not perform action " + action + ": " + e.getMessage());
		}
		check(client, lastCheck);
	}

	private void destroyed(TreeClient client)
	{
		wlock();
		try
		{
			getRoot().unloadContent(client, false);
			theClients.remove(client);
		} finally
		{
			wunlock();
		}
	}

	void purgeClients()
	{
		wlock();
		try
		{
			long purgeTime = System.currentTimeMillis() - theClientSaveTime;
			for(TreeClient client : theClients)
				if(client.getLastCheck() < purgeTime)
					destroyed(client);
		} finally
		{
			wunlock();
		}
	}

	JSONArray jsonPath(ServiceTreeNode node, TreeClient client)
	{
		JSONArray pathArr = new JSONArray();
		pathArr.add(serializeRecursive(node, client));
		for(node = node.getParent(); node != null; node = node.getParent())
			pathArr.add(0, node.toJSON(client));
		fixUnicode(pathArr);
		return pathArr;
	}

	private JSONObject serializeRecursive(ServiceTreeNode node, TreeClient client)
	{
		if(node == null)
			return null;
		JSONObject ret = node.toJSON(client);
		if(node.getChildren() != null && node.getChildren().length > 0
			&& (node.getParent() == null || node.isLoaded(client)))
		{
			JSONArray children = new JSONArray();
			for(ServiceTreeNode child : node.getChildren())
				children.add(serializeRecursive(child, client));
			ret.put("children", children);
		}
		else
			ret.put("children", new JSONArray());
		return ret;
	}

	private static void fixUnicode(JSONArray json)
	{
		for(int i = 0; i < json.size(); i++)
		{
			Object val = json.get(i);
			if(val instanceof String)
				json.set(i, prisms.util.PrismsUtils.encodeUnicode((String) val));
			else if(val instanceof JSONObject)
				fixUnicode((JSONObject) val);
			else if(val instanceof JSONArray)
				fixUnicode((JSONArray) val);
		}
	}

	private static void fixUnicode(JSONObject json)
	{
		for(java.util.Map.Entry<String, Object> entry : ((java.util.Map<String, Object>) json)
			.entrySet())
		{
			if(entry.getValue() instanceof String)
				entry.setValue(prisms.util.PrismsUtils.encodeUnicode((String) entry.getValue()));
			else if(entry.getValue() instanceof JSONObject)
				fixUnicode((JSONObject) entry.getValue());
			else if(entry.getValue() instanceof JSONArray)
				fixUnicode((JSONArray) entry.getValue());
		}
	}

	static boolean isAncestor(DataTreeNode ancestor, DataTreeNode descendant)
	{
		while(descendant != ancestor && descendant != null)
			descendant = descendant.getParent();
		return descendant != null;
	}
}
