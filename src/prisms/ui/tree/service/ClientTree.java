/*
 * ClientTree.java Created Apr 25, 2011 by Andrew Butler, PSL
 */
package prisms.ui.tree.service;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.arch.ds.IDGenerator.PrismsInstance;
import prisms.ui.list.NodeAction;
import prisms.ui.tree.DataTreeNode;
import prisms.util.ArrayUtils;

/**
 * A ClientTree is the front-end of one or more instances of {@link ServiceTree}. A ClientTree, by
 * default, will contact every PRISMS instance on the enterprise with a configured service name and
 * plugin name and accumulate the data found in the ServiceTrees at those plugin addresses to
 * display to the user.
 */
public class ClientTree extends prisms.ui.tree.DataTreeMgrPlugin
{
	static final Logger log = Logger.getLogger(ClientTree.class);

	private static final prisms.impl.ThreadPoolWorker theWorker;

	static
	{
		theWorker = new prisms.impl.ThreadPoolWorker("Client Tree Worker");
	}

	/** Allows a return value to be passed to the caller asynchronously */
	public interface Return
	{
		/**
		 * Called when a remote call returns with a value
		 * 
		 * @param evt The return value of the call
		 */
		void returned(JSONObject evt);
	}

	/**
	 * Represents a server where a {@link ServiceTree} resides serving data to this
	 * {@link ClientTree}
	 */
	public class RemoteSource implements Comparable<RemoteSource>
	{
		/** The PRISMS instance where the {@link ServiceTree} is located */
		public final PrismsInstance instance;

		boolean isOpen;

		boolean isConfirmed;

		prisms.util.PrismsServiceConnector conn;

		long theLastCheck;

		RemoteSource(PrismsInstance pi)
		{
			instance = pi;
			isOpen = true;
		}

		private void connect()
		{
			conn = new prisms.util.PrismsServiceConnector(instance.location, getSession().getApp()
				.getName(), getServiceName(), "System");
			conn.setWorker(getWorker());
			conn.setUserSource(getSession().getApp().getEnvironment().getUserSource());
			try
			{
				/* This connector connects to a location pointed to by the database. This should be
				 * secure enough without verifying the HTTPS certificates that might be presented.
				 * This validation would be difficult to configure and seems unnecessary, so we'll
				 * implicitly trust any connection just as we would for HTTP. */
				conn.setTrustManager(new javax.net.ssl.X509TrustManager()
				{
					public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
						String authType) throws java.security.cert.CertificateException
					{
					}

					public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
						String authType) throws java.security.cert.CertificateException
					{
					}

					public java.security.cert.X509Certificate[] getAcceptedIssuers()
					{
						return null;
					}
				});
			} catch(java.security.GeneralSecurityException e)
			{
				log.error("Could not set trust manager for service connector", e);
			}
		}

		/**
		 * Sends a request to the source without waiting for the result
		 * 
		 * @param method The method to call
		 * @param params The parameter list to send, in the form name, value, name, value...
		 */
		public void sendAsync(final String method, Object... params)
		{
			if(!isOpen)
				return;
			if(conn == null)
				connect();
			params = prisms.util.ArrayUtils.addAll(params, "clientID", theClientID, "user",
				getSession().getUser().getName(), "lastCheck", Long.valueOf(theLastCheck));
			final long now = System.currentTimeMillis();
			conn.getResultsAsync(getServicePlugin(), method,
				new prisms.util.PrismsServiceConnector.AsyncReturns()
				{
					public void doReturn(JSONArray returnVals)
					{
						theLastCheck = now;
						if(!returnVals.isEmpty())
							processServiceEvents(RemoteSource.this, returnVals);
						isConfirmed = true;
					}

					public void doError(IOException e)
					{
						log.error("Could not call method " + method + " at " + instance.location, e);
						if(!isConfirmed)
							isOpen = false;
					}
				}, params);
		}

		/**
		 * Sends a request to the source, waiting for the result before returning
		 * 
		 * @param method The method to call
		 * @param params The parameter list to send, in the form name, value, name, value...
		 */
		public void sendSync(String method, Object... params)
		{
			if(!isOpen)
				return;
			if(conn == null)
				connect();
			params = prisms.util.ArrayUtils.addAll(params, "clientID", theClientID, "user",
				getSession().getUser().getName(), "lastCheck", Long.valueOf(theLastCheck));
			long now = System.currentTimeMillis();
			JSONArray events;
			try
			{
				events = conn.getResults(getServicePlugin(), method, params);
				isConfirmed = true;
			} catch(IOException e)
			{
				log.error("Could not call method " + method + " at " + instance.location, e);
				if(!isConfirmed)
					isOpen = false;
				return;
			}
			theLastCheck = now;
			if(!events.isEmpty())
				processServiceEvents(this, events);
		}

		/**
		 * Sends a request to the source without waiting for the result
		 * 
		 * @param method The method to call
		 * @param returnMethod The method expected in the return value. Only the first event
		 *        returned from the server whose method parameter is equal to this value will be
		 *        returned (via the {@link Return} object).
		 * @param ret The Return to use to return the value from the method call to the caller
		 * @param params The parameter list to send, in the form name, value, name, value...
		 */
		public void callMethodAsync(final String method, final String returnMethod,
			final Return ret, Object... params)
		{
			if(!isOpen)
				return;
			if(conn == null)
				connect();
			params = prisms.util.ArrayUtils.addAll(params, "clientID", theClientID, "user",
				getSession().getUser().getName(), "lastCheck", Long.valueOf(theLastCheck));
			final long now = System.currentTimeMillis();
			conn.getResultsAsync(getServicePlugin(), method,
				new prisms.util.PrismsServiceConnector.AsyncReturns()
				{
					public void doReturn(JSONArray returnVals)
					{
						JSONObject retEvt = null;
						for(int e = 0; e < returnVals.size(); e++)
							if(returnMethod.equals(((JSONObject) returnVals.get(e)).get("method")))
							{
								retEvt = (JSONObject) returnVals.get(e);
								returnVals.remove(e);
								break;
							}
						theLastCheck = now;
						if(!returnVals.isEmpty())
							processServiceEvents(RemoteSource.this, returnVals);
						isConfirmed = true;
						ret.returned(retEvt);
					}

					public void doError(IOException e)
					{
						log.error("Could not call method " + method + " at " + instance.location, e);
						if(!isConfirmed)
							isOpen = false;
					}
				}, params);
		}

		/**
		 * Sends a request to the source expecting a return value
		 * 
		 * @param method The method to call
		 * @param returnMethod The method expected in the return value. Only the first event
		 *        returned from the server whose method parameter is equal to this value will be
		 *        returned.
		 * @param params The parameter list to send, in the form name, value, name, value...
		 * @return The return value from the call
		 */
		public JSONObject callMethod(String method, String returnMethod, Object... params)
		{
			if(!isOpen)
				return null;
			if(conn == null)
				connect();
			params = prisms.util.ArrayUtils.addAll(params, "clientID", theClientID, "user",
				getSession().getUser().getName(), "lastCheck", Long.valueOf(theLastCheck));
			JSONArray events;
			try
			{
				events = conn.getResults(getServicePlugin(), method, params);
				isConfirmed = true;
			} catch(IOException e)
			{
				log.error("Could not call method " + method + " at " + instance.location, e);
				if(!isConfirmed)
					isOpen = false;
				return null;
			}
			long now = System.currentTimeMillis();
			JSONObject ret = null;
			for(int e = 0; e < events.size(); e++)
				if(returnMethod.equals(((JSONObject) events.get(e)).get("method")))
				{
					ret = (JSONObject) events.get(e);
					events.remove(e);
					break;
				}
			theLastCheck = now;
			if(!events.isEmpty())
				processServiceEvents(this, events);
			return ret;
		}

		@Override
		public boolean equals(Object o)
		{
			if(o == this)
				return true;
			if(!(o instanceof RemoteSource))
				return false;
			return ((RemoteSource) o).instance.location.equals(instance.location);
		}

		@Override
		public int hashCode()
		{
			return instance.location.hashCode();
		}

		public int compareTo(RemoteSource o)
		{
			if(equals(o))
				return 0;
			if(instance.local)
				return -1;
			if(o.instance.local)
				return 1;
			return instance.location.compareToIgnoreCase(o.instance.location);
		}
	}

	final String theClientID;

	private RemoteSource [] theSources;

	private String theServiceName;

	private String theServicePlugin;

	long theHeartBeatFreq;

	private Runnable theHeartBeat;

	long theLastHeartBeat;

	/** Creates a ClientTree */
	public ClientTree()
	{
		theClientID = Integer.toHexString(hashCode());
		theSources = new RemoteSource [0];
	}

	@Override
	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
		theServiceName = config.get("service-name");
		theServicePlugin = config.get("service-plugin");
		theHeartBeat = new Runnable()
		{
			public void run()
			{
				if(System.currentTimeMillis() - theLastHeartBeat > theHeartBeatFreq / 3)
					heartBeat();
			}
		};
		String hb = config.get("heart-beat");
		if(hb != null)
			setHeartBeat(Integer.parseInt(hb) * 1000);
	}

	static prisms.impl.ThreadPoolWorker getWorker()
	{
		return theWorker;
	}

	@Override
	public synchronized void setSelection(DataTreeNode [] nodes, boolean fromUser)
	{
		super.setSelection(nodes, fromUser);
		if(System.currentTimeMillis() - theLastHeartBeat > 500)
			heartBeat();
	}

	/** @return The name of the service that this client tree hits for its data */
	public String getServiceName()
	{
		return theServiceName;
	}

	/** @return The name of the plugin that this client tree hits for its data */
	public String getServicePlugin()
	{
		return theServicePlugin;
	}

	/** @param heartBeat The frequency with which this client tree contacts its servers for new data */
	public void setHeartBeat(long heartBeat)
	{
		if(theHeartBeatFreq > 0)
			getSession().getApp().stopRecurringTask(theHeartBeat);
		theHeartBeatFreq = heartBeat;
		if(theHeartBeatFreq > 0)
			getSession().getApp().scheduleRecurringTask(theHeartBeat, theHeartBeatFreq);
	}

	@Override
	public ClientTreeNode getRoot()
	{
		return (ClientTreeNode) super.getRoot();
	}

	@Override
	public void setRoot(DataTreeNode root)
	{
		if(!(root instanceof ClientTreeNode))
			throw new IllegalArgumentException("The root of a ClientTree must be a ClientTreeNode");
		super.setRoot(root);
	}

	prisms.ui.UI getUI()
	{
		return getSession().getUI();
	}

	synchronized void processServiceEvents(RemoteSource source, JSONArray events)
	{
		for(JSONObject evt : (java.util.List<JSONObject>) events)
			_processServiceEvent(source, evt);
	}

	/** Processes events from a {@link ServiceTree} that serves this client tree */
	private void _processServiceEvent(RemoteSource source, JSONObject event)
	{
		String method = (String) event.get("method");
		if("refresh".equals(method))
		{
			JSONObject root = (JSONObject) event.get("root");
			getRoot().setContent(root, source, true, true);
		}
		else if("nodeAdded".equals(method))
		{
			JSONArray path = (JSONArray) event.get("path");
			ClientTreeNode parent = navigate(source, path, path.size() - 2);
			if(parent == null)
			{
				log.warn("Could not navigate to parent path " + printPath(path));
				return;
			}
			JSONObject jsonNode = (JSONObject) path.get(path.size() - 1);
			String id = (String) jsonNode.get("id");
			for(ClientTreeNode node : parent.getChildren())
				if(node.getID().equals(id))
				{
					node.setContent(jsonNode, source, true, true);
					return;
				}
			ClientTreeNode toAdd = createNode(parent, id, jsonNode);
			toAdd.setContent(jsonNode, source, true, false);
			int index = ((Number) event.get("index")).intValue();
			int srcNodes = -1;
			boolean added = false;
			for(int c = 0; c < parent.getChildren().length; c++)
				if(ArrayUtils.contains(parent.getChildren()[c].getSources(), source))
				{
					if(index == 0)
					{
						added = true;
						parent.add(toAdd, c);
						break;
					}
					srcNodes++;
					if(srcNodes == index - 1)
					{
						added = true;
						parent.add(toAdd, c + 1);
						break;
					}
				}
			if(!added)
				parent.add(toAdd, parent.getChildren().length);
		}
		else if("nodeRemoved".equals(method))
		{
			JSONArray path = (JSONArray) event.get("path");
			ClientTreeNode node = navigate(source, path, path.size() - 1);
			if(node == null)
				return;
			node.removed(source, true);
		}
		else if("nodeChanged".equals(method))
		{
			JSONArray path = (JSONArray) event.get("path");
			ClientTreeNode node = navigate(source, path, path.size() - 1);
			if(node == null)
			{
				log.warn("Could not navigate to parent path " + printPath(path));
				return;
			}
			boolean recursive = Boolean.TRUE.equals(event.get("recursive"));
			node.setContent((JSONObject) path.get(path.size() - 1), source, recursive, false);
			node.changed(recursive);
		}
		else if("nodeMoved".equals(method))
		{
			JSONArray path = (JSONArray) event.get("path");
			ClientTreeNode node = navigate(source, path, path.size() - 1);
			if(node == null)
			{
				log.warn("Could not navigate to parent path " + printPath(path));
				return;
			}
			ClientTreeNode parent = node.getParent();
			int nowIndex = ArrayUtils.indexOf(parent.getChildren(), node);
			int index = ((Number) event.get("index")).intValue();
			int srcNodes = -1;
			for(int c = 0; c < parent.getChildren().length; c++)
				if(ArrayUtils.contains(parent.getChildren()[c].getSources(), source))
				{
					if(index == 0)
					{
						parent.move(nowIndex, c);
						break;
					}
					srcNodes++;
					if(srcNodes == index - 1)
					{
						parent.move(nowIndex, c + 1);
						break;
					}
				}
		}
		else if("error".equals(method) || "warning".equals(method) || "info".equals(method)
			|| "confirm".equals(method) || "input".equals(method) || "select".equals(method)
			|| "progress".equals(method) || "close".equals(method))
			doUI(source, event); // UI event
		else
			log.error("Unrecognized service method: " + method);
	}

	private void doUI(final RemoteSource source, JSONObject event)
	{
		final String messageID = (String) event.get("messageID");
		class ForwardedProgress implements prisms.ui.UI.ProgressInformer
		{
			private JSONObject theEvent;

			private long theEventTime;

			private boolean isUpdating;

			private boolean isCanceling;

			private boolean isStarted;

			private boolean isDone;

			private void check()
			{
				if(!isStarted)
					return;
				long now = System.currentTimeMillis();
				if(isDone || isUpdating || now - theEventTime <= 500)
					return;
				synchronized(this)
				{
					if(isDone || isUpdating || now - theEventTime <= 500)
						return;
					isUpdating = true;
					try
					{
						source.sendSync("refresh");
					} finally
					{
						theEventTime = System.currentTimeMillis();
						isUpdating = false;
					}
				}
			}

			void start()
			{
				isStarted = true;
			}

			public int getTaskScale()
			{
				check();
				if(!theEvent.containsKey("length"))
					return 0;
				else
					return ((Number) theEvent.get("length")).intValue();
			}

			public int getTaskProgress()
			{
				check();
				if(!theEvent.containsKey("progress"))
					return 0;
				else
					return ((Number) theEvent.get("progress")).intValue();
			}

			public boolean isTaskDone()
			{
				check();
				return isDone;
			}

			public String getTaskText()
			{
				check();
				return (String) theEvent.get("message");
			}

			public boolean isCancelable()
			{
				check();
				return !isCanceling && Boolean.TRUE.equals(theEvent.get("cancelable"));
			}

			public void cancel() throws IllegalStateException
			{
				isCanceling = true;
				synchronized(this)
				{
					isUpdating = true;
					try
					{
						source.sendSync("cancel", "messageID", messageID);
					} finally
					{
						theEventTime = System.currentTimeMillis();
						isUpdating = false;
					}
				}
			}

			void setEvent(JSONObject evt)
			{
				theEvent = evt;
			}

			void setDone()
			{
				isDone = true;
			}
		}
		String method = (String) event.get("method");
		String message = (String) event.get("message");
		if("error".equals(method))
			getUI().error(message, new prisms.ui.UI.AcknowledgeListener()
			{
				public void acknowledged()
				{
					source.sendSync("eventReturned", "messageID", messageID);
				}
			});
		else if("warning".equals(method))
			getUI().warn(message, new prisms.ui.UI.AcknowledgeListener()
			{
				public void acknowledged()
				{
					source.sendSync("eventReturned", "messageID", messageID);
				}
			});
		else if("info".equals(method))
			getUI().info(message, new prisms.ui.UI.AcknowledgeListener()
			{
				public void acknowledged()
				{
					source.sendSync("eventReturned", "messageID", messageID);
				}
			});
		else if("confirm".equals(method))
			getUI().confirm(message, new prisms.ui.UI.ConfirmListener()
			{
				public void confirmed(boolean confirm)
				{
					source.sendSync("eventReturned", "messageID", messageID, "value",
						Boolean.valueOf(confirm));
				}
			});
		else if("input".equals(method))
			getUI().input(message, (String) event.get("init"), new prisms.ui.UI.InputListener()
			{
				public void inputed(String input)
				{
					source.sendSync("eventReturned", "messageID", messageID, "value", input);
				}
			});
		else if("select".equals(method))
		{
			String [] options = ((java.util.List<String>) event.get("options"))
				.toArray(new String [0]);
			int init;
			if(event.containsKey("initSelection"))
				init = ((Number) event.get("initSelection")).intValue();
			else
				init = 0;
			getUI().select(message, options, init, new prisms.ui.UI.SelectListener()
			{
				public void selected(String input)
				{
					source.sendSync("eventReturned", "messageID", messageID, "value", input);
				}
			});
		}
		else if("progress".equals(method))
		{
			boolean found = false;
			for(prisms.ui.UI.EventObject evtObj : getUI().getListeners())
				if(evtObj.listener instanceof ForwardedProgress)
				{
					found = true;
					((ForwardedProgress) evtObj.listener).setEvent(event);
				}
			if(!found)
			{
				ForwardedProgress pi = new ForwardedProgress();
				pi.setEvent(event);
				getUI().startTimedTask(pi);
				pi.start();
			}
		}
		else if("close".equals(method))
		{
			for(prisms.ui.UI.EventObject evtObj : getUI().getListeners())
				if(evtObj.listener instanceof ForwardedProgress)
					((ForwardedProgress) evtObj.listener).setDone();
		}
		else
			throw new IllegalStateException("Unrecognized UI event: " + event);
	}

	private ClientTreeNode navigate(RemoteSource source, JSONArray path, int depth)
	{
		if(depth < 0)
			depth = path.size() - 1;
		return navigate(source, getRoot(), path, 0, depth);
	}

	private ClientTreeNode navigate(RemoteSource source, ClientTreeNode node, JSONArray path,
		int pathIdx, int depth)
	{
		JSONObject pathEl = (JSONObject) path.get(pathIdx);
		if(pathIdx == depth)
			return node;
		node.setContent(pathEl, source, false, true);
		String childID = (String) ((JSONObject) path.get(pathIdx + 1)).get("id");
		for(ClientTreeNode child : node.getChildren())
			if(child.getID().equals(childID))
				return navigate(source, child, path, pathIdx + 1, depth);
		return null;
	}

	private String printPath(JSONArray path)
	{
		StringBuilder ret = new StringBuilder();
		for(JSONObject node : (java.util.List<JSONObject>) path)
		{
			if(ret.length() > 0)
				ret.append('/');
			ret.append(node.get("text"));
			ret.append('(');
			ret.append(node.get("id"));
			ret.append(')');
		}
		return ret.toString();
	}

	/** Checks all server trees for changes to the data */
	protected void heartBeat()
	{
		PrismsInstance [] instances = getInstancesToUse();
		if(instances != null)
			setInstances(instances);
	}

	/** @return All instances that this tree should contact for data */
	protected PrismsInstance [] getInstancesToUse()
	{
		PrismsInstance [] instances;
		try
		{
			instances = getSession().getApp().getEnvironment().getIDs().getOtherInstances();
			PrismsInstance local = getSession().getApp().getEnvironment().getIDs()
				.getLocalInstance();
			if(local != null)
				instances = prisms.util.ArrayUtils.add(instances, local, 0);
		} catch(prisms.arch.PrismsException e)
		{
			log.error("Could not get enterprise instances", e);
			instances = null;
		}
		return instances;
	}

	private synchronized void setInstances(PrismsInstance [] instances)
	{
		if(ArrayUtils.equalsUnordered(theSources, instances, new ArrayUtils.EqualsChecker()
		{
			public boolean equals(Object o1, Object o2)
			{
				return ((RemoteSource) o1).instance.location.equals(((PrismsInstance) o2).location);
			}
		}))
		{
			for(RemoteSource source : theSources)
				source.sendAsync("check");
		}
		else
		{
			RemoteSource [] oldSources = theSources;
			theSources = ArrayUtils.adjust(theSources, instances,
				new ArrayUtils.DifferenceListener<RemoteSource, PrismsInstance>()
				{
					public boolean identity(RemoteSource o1, PrismsInstance o2)
					{
						return o1.instance.location.equals(o2.location);
					}

					public RemoteSource added(PrismsInstance o, int mIdx, int retIdx)
					{
						return new RemoteSource(o);
					}

					public RemoteSource removed(RemoteSource o, int oIdx, int incMod, int retIdx)
					{
						return null;
					}

					public RemoteSource set(RemoteSource o1, int idx1, int incMod,
						PrismsInstance o2, int idx2, int retIdx)
					{
						if(o2.initTime > o1.instance.initTime)
							return new RemoteSource(o2);
						else
							return o1;
					}
				});
			ArrayUtils.adjust(oldSources, theSources,
				new ArrayUtils.DifferenceListener<RemoteSource, RemoteSource>()
				{
					public boolean identity(RemoteSource o1, RemoteSource o2)
					{
						return o1.equals(o2);
					}

					public RemoteSource added(RemoteSource o, int mIdx, int retIdx)
					{
						o.sendAsync("init");
						return null;
					}

					public RemoteSource removed(RemoteSource o, int oIdx, int incMod, int retIdx)
					{
						getRoot().removed(o, true);
						return null;
					}

					public RemoteSource set(RemoteSource o1, int idx1, int incMod, RemoteSource o2,
						int idx2, int retIdx)
					{
						if(o1 != o2)
						{
							getRoot().removed(o1, true);
							o2.sendAsync("init");
						}
						return null;
					}
				});
		}
	}

	/**
	 * Creates a new tree node to represent a new data entity on a server
	 * 
	 * @param parent The parent for the node
	 * @param id The ID for the node
	 * @param content The content for the node. The node's content should not be set at this step.
	 *        That will be accomplished by a call to
	 *        {@link ClientTreeNode#setContent(JSONObject, RemoteSource, boolean, boolean)} later.
	 *        It is provided here for potential aid in choosing a correct ClientTreeNode subclass or
	 *        other initialization steps for ClientTree subclasses.
	 * @return The node to use to represent the new data
	 */
	public ClientTreeNode createNode(ClientTreeNode parent, String id, JSONObject content)
	{
		return new ClientTreeNode(parent, id);
	}

	@Override
	public void performAction(DataTreeNode [] nodes, final String action)
	{
		ArrayList<ClientTreeNode> loadNodes = null;
		ArrayList<ClientTreeNode> loadAllNodes = null;
		ArrayList<ClientTreeNode> unloadNodes = null;
		ArrayList<ClientTreeNode> actionNodes = null;
		for(int n = 0; n < nodes.length; n++)
		{
			ClientTreeNode node = (ClientTreeNode) nodes[n];
			if(action.equals(node.theLoadAction))
			{
				if(loadNodes == null)
					loadNodes = new ArrayList<ClientTreeNode>();
				loadNodes.add(node);
			}
			else if(action.equals(node.theLoadAllAction))
			{
				if(loadAllNodes == null)
					loadAllNodes = new ArrayList<ClientTreeNode>();
				loadAllNodes.add(node);
			}
			else if(action.equals(node.theUnloadAction))
			{
				if(unloadNodes == null)
					unloadNodes = new ArrayList<ClientTreeNode>();
				unloadNodes.add(node);
			}
			else
			{
				boolean hadSuperAction = false;
				for(NodeAction na : node.getSuperActions())
					if(na.getText().equals(action))
					{
						hadSuperAction = true;
						node.doAction(action);
						break;
					}
				if(!hadSuperAction)
				{
					if(actionNodes == null)
						actionNodes = new ArrayList<ClientTreeNode>();
					actionNodes.add(node);
				}
			}
		}

		int scale = 0;
		if(loadNodes != null)
			for(ClientTreeNode node : loadNodes)
				scale += node.getSources().length;
		if(loadAllNodes != null)
			for(ClientTreeNode node : loadAllNodes)
				scale += node.getSources().length;
		if(unloadNodes != null)
			for(ClientTreeNode node : unloadNodes)
				scale += node.getSources().length;
		if(actionNodes != null)
			for(ClientTreeNode node : actionNodes)
				scale += node.getSources().length;

		final int fScale = scale;
		final int [] stage = new int [1];
		final int [] progress = new int [1];
		final boolean [] finished = new boolean [1];
		prisms.ui.UI.ProgressInformer pi = new prisms.ui.UI.ProgressInformer()
		{
			public int getTaskScale()
			{
				return fScale;
			}

			public int getTaskProgress()
			{
				if(fScale == 0)
					return 0;
				return fScale - progress[0];
			}

			public boolean isTaskDone()
			{
				return finished[0];
			}

			public String getTaskText()
			{
				switch(stage[0])
				{
				case 0:
				case 1:
					return "Retrieving content";
				case 2:
					return "Unloading content";
				case 3:
					return "Performing action \"" + action + "\"";
				default:
					return "Unknown stage";
				}
			}

			public boolean isCancelable()
			{
				return false;
			}

			public void cancel() throws IllegalStateException
			{
			}
		};
		getUI().startTimedTask(pi);
		try
		{
			if(loadNodes != null)
				for(ClientTreeNode node : loadNodes)
				{
					node.isLoaded = true;
					JSONArray path = node.getPath();
					for(RemoteSource src : node.getSources())
					{
						src.sendSync("load", "path", path);
						progress[0]++;
					}
				}
			stage[0]++;

			if(loadAllNodes != null)
				for(ClientTreeNode node : loadAllNodes)
				{
					node.isLoaded = true;
					node.isAllLoaded = true;
					JSONArray path = node.getPath();
					for(RemoteSource src : node.getSources())
					{
						src.sendSync("load", "path", path, "withDescendants", Boolean.TRUE);
						progress[0]++;
					}
				}
			stage[0]++;

			if(unloadNodes != null)
				for(ClientTreeNode node : unloadNodes)
				{
					node.isLoaded = false;
					node.isAllLoaded = false;
					JSONArray path = node.getPath();
					for(RemoteSource src : node.getSources())
					{
						src.sendSync("unload", "path", path);
						progress[0]++;
					}
				}
			stage[0]++;

			if(actionNodes != null)
			{
				java.util.LinkedHashMap<RemoteSource, ClientTreeNode []> sourceNodes;
				sourceNodes = new java.util.LinkedHashMap<RemoteSource, ClientTreeNode []>();
				for(ClientTreeNode node : actionNodes)
					for(RemoteSource src : node.getSources())
					{
						ClientTreeNode [] srcNodes = sourceNodes.get(src);
						if(srcNodes == null)
							srcNodes = new ClientTreeNode [] {node};
						else
							srcNodes = ArrayUtils.add(srcNodes, node);
						sourceNodes.put(src, srcNodes);
					}
				for(java.util.Map.Entry<RemoteSource, ClientTreeNode []> entry : sourceNodes
					.entrySet())
				{
					JSONArray paths = new JSONArray();
					for(ClientTreeNode node : entry.getValue())
						paths.add(node.getPath());
					entry.getKey().sendSync("actionPerformed", "clientID", theClientID, "paths",
						paths, "action", action);
					progress[0] += entry.getValue().length;
				}
				sourceNodes.clear();
			}
		} finally
		{
			finished[0] = true;
		}
	}

	void destroy()
	{
		for(final RemoteSource src : theSources)
			src.sendAsync("destroyed");
	}

	/** The only type of node that may be present in a {@link ClientTree} */
	public class ClientTreeNode extends prisms.ui.tree.SimpleTreePluginNode implements
		Comparable<ClientTreeNode>
	{
		private RemoteSource [] theNodeSources;

		private String theText;

		private String theDescription;

		private Color theBackground;

		private Color theForeground;

		private String theIcon;

		private NodeAction [] theActions;

		boolean isLoaded;

		boolean isAllLoaded;

		String theLoadAction;

		String theLoadAllAction;

		String theUnloadAction;

		/**
		 * @param parent The parent for this node
		 * @param id The ID for this node
		 */
		public ClientTreeNode(ClientTreeNode parent, String id)
		{
			super(ClientTree.this, id, parent, false);
			setChildren(new ClientTreeNode [0]);
			theNodeSources = new RemoteSource [0];
			theText = "----";
			theBackground = Color.white;
			theForeground = Color.black;
			theActions = new NodeAction [0];
		}

		@Override
		public ClientTree getManager()
		{
			return (ClientTree) super.getManager();
		}

		@Override
		public ClientTreeNode getParent()
		{
			return (ClientTreeNode) super.getParent();
		}

		@Override
		public ClientTreeNode [] getChildren()
		{
			return (ClientTreeNode []) super.getChildren();
		}

		@Override
		public void setChildren(DataTreeNode [] children)
		{
			if(!(children instanceof ClientTreeNode []))
			{
				for(DataTreeNode node : children)
					if(!(node instanceof ClientTreeNode))
						throw new IllegalArgumentException(
							"Only ClientTreeNodes can be children of a" + " ClientTreeNode");
				ClientTreeNode [] newCh = new ClientTreeNode [children.length];
				System.arraycopy(children, 0, newCh, 0, children.length);
				children = newCh;
			}
			super.setChildren(children);
		}

		@Override
		public void add(DataTreeNode node, int index)
		{
			if(!(node instanceof ClientTreeNode))
				throw new IllegalArgumentException("Only ClientTreeNodes can be children of a"
					+ " ClientTreeNode");
			super.add(node, index);
		}

		public int compareTo(ClientTreeNode o)
		{
			return getPriority() - o.getPriority();
		}

		private int getPriority()
		{
			if(getParent() == null)
				return 0;
			RemoteSource [] parSrc = getParent().getSources();
			for(int s = 0; s < parSrc.length; s++)
				if(ArrayUtils.contains(theNodeSources, parSrc[s]))
					return s;
			return parSrc.length;
		}

		/** @param text The text that this node should display */
		public void setText(String text)
		{
			theText = text;
		}

		/** @param desc The description that this node should display */
		public void setDescription(String desc)
		{
			theDescription = desc;
		}

		/** @param icon The icon that this node should display */
		public void setIcon(String icon)
		{
			theIcon = icon;
		}

		/** @param bg The background color that this node should display */
		public void setBackground(Color bg)
		{
			theBackground = bg;
		}

		/** @param fg The text color that this node should display */
		public void setForeground(Color fg)
		{
			theForeground = fg;
		}

		/**
		 * Sets this node's content
		 * 
		 * @param content The content sent from the server
		 * @param source The remote server where the representation came from
		 * @param withChildren Whether to adjust this node's children with the content or just the
		 *        node's own display
		 * @param withEvents Whether to fire events if content has changed
		 * @return Whether the node or any of its descendants were changed by this call
		 */
		protected boolean setContent(JSONObject content, final RemoteSource source,
			boolean withChildren, boolean withEvents)
		{
			if(!ArrayUtils.contains(theNodeSources, source))
				synchronized(this)
				{
					if(!ArrayUtils.contains(theNodeSources, source))
					{
						theNodeSources = ArrayUtils.add(theNodeSources, source);
						java.util.Arrays.sort(theNodeSources);
					}
				}
			String text = (String) content.get("text");
			String desc = (String) content.get("description");
			String icon = (String) content.get("icon");
			Color bg = prisms.util.ColorUtils.fromHTML((String) content.get("bgColor"));
			Color fg = prisms.util.ColorUtils.fromHTML((String) content.get("textColor"));

			final boolean [] changed = new boolean [1];
			if(!theText.equals(text))
			{
				theText = text;
				changed[0] = true;
			}
			if(!equal(desc, theDescription))
			{
				theDescription = desc;
				changed[0] = true;
			}
			if(!equal(icon, theIcon))
			{
				theIcon = icon;
				changed[0] = true;
			}
			if(!theBackground.equals(bg))
			{
				theBackground = bg;
				changed[0] = true;
			}
			if(!theForeground.equals(fg))
			{
				theForeground = fg;
				changed[0] = true;
			}

			ArrayList<NodeAction> actions = new ArrayList<NodeAction>();
			theLoadAction = (String) content.get("loadAction");
			theLoadAllAction = (String) content.get("loadAllAction");
			theUnloadAction = (String) content.get("unloadAction");
			if((theLoadAction != null || theLoadAllAction != null) && isLoaded)
				/* If the load action is available and this node is loaded, request the children
				 * from the service automatically */
				loadContent(source, isAllLoaded);
			else
			{
				if(theLoadAction != null)
					actions.add(new NodeAction(theLoadAction, true));
				if(theLoadAllAction != null)
					actions.add(new NodeAction(theLoadAllAction, true));
			}
			if(theUnloadAction != null)
			{
				isLoaded = true;
				actions.add(new NodeAction(theUnloadAction, true));
			}
			for(JSONObject jsonAction : (java.util.List<JSONObject>) content.get("actions"))
				actions.add(new NodeAction((String) jsonAction.get("text"), Boolean.TRUE
					.equals(jsonAction.get("multiple"))));
			theActions = actions.toArray(new NodeAction [actions.size()]);

			if(withChildren)
			{
				JSONArray jsonChA = (JSONArray) content.get("children");
				JSONObject [] jsonCh = (JSONObject []) jsonChA.toArray(new JSONObject [jsonChA
					.size()]);
				final boolean withEvents2 = withEvents && getChildren().length > 0;
				ClientTreeNode [] newCh = ArrayUtils.adjust(getChildren(), jsonCh,
					new ArrayUtils.DifferenceListener<ClientTreeNode, JSONObject>()
					{
						public boolean identity(ClientTreeNode o1, JSONObject o2)
						{
							return o1.getID().equals(o2.get("id"));
						}

						public ClientTreeNode added(JSONObject o, int mIdx, int retIdx)
						{
							ClientTreeNode ret = createNode(ClientTreeNode.this,
								(String) o.get("id"), o);
							ret.setContent(o, source, true, false);
							if(withEvents2)
								add(ret, retIdx);
							return ret;
						}

						public ClientTreeNode removed(ClientTreeNode o, int oIdx, int incMod,
							int retIdx)
						{
							o.removed(source, withEvents2);
							if(o.getSources().length == 0)
							{
								if(withEvents2)
									remove(incMod);
								return null;
							}
							else
								return o;
						}

						public ClientTreeNode set(ClientTreeNode o1, int idx1, int incMod,
							JSONObject o2, int idx2, int retIdx)
						{
							// Don't worry about index changes. We'll sort and reindex next.
							if(o1.setContent(o2, source, true, withEvents2))
								changed[0] = false;
							return o1;
						}
					});
				java.util.Arrays.sort(newCh);
				if(!withEvents)
					setChildren(newCh);
				else if(!withEvents2)
				{
					setChildren(newCh);
					changed(true);
					withEvents = false;
				}
				else
				{
					ArrayUtils.adjust(getChildren(), newCh,
						new ArrayUtils.DifferenceListener<ClientTreeNode, ClientTreeNode>()
						{
							public boolean identity(ClientTreeNode o1, ClientTreeNode o2)
							{
								return o1 == o2;
							}

							public ClientTreeNode added(ClientTreeNode o, int mIdx, int retIdx)
							{
								return o;
							}

							public ClientTreeNode removed(ClientTreeNode o, int oIdx, int incMod,
								int retIdx)
							{
								return null;
							}

							public ClientTreeNode set(ClientTreeNode o1, int idx1, int incMod,
								ClientTreeNode o2, int idx2, int retIdx)
							{
								if(incMod != retIdx)
									move(incMod, retIdx);
								return o2;
							}
						});
				}
			}
			if(changed[0] && withEvents)
				changed(false);
			return changed[0];
		}

		/**
		 * Called when the reference to this node is removed from a remote {@link ServiceTree}
		 * server
		 * 
		 * @param source The server that this node was removed from
		 * @param withEvents Whether to fire events if the effects of this need to be represented in
		 *        the client's view
		 */
		protected void removed(RemoteSource source, boolean withEvents)
		{
			synchronized(this)
			{
				theNodeSources = ArrayUtils.remove(theNodeSources, source);
			}
			if(theNodeSources.length == 0)
			{
				if(withEvents)
				{
					if(getParent() != null)
						getParent().remove(ArrayUtils.indexOf(getParent().getChildren(), this));
				}
				else
					setChildren(new ClientTreeNode [0]);
				return;
			}
			int removed = 0;
			for(int c = 0; c < getChildren().length; c++)
			{
				getChildren()[c].removed(source, withEvents && theNodeSources.length > 0);
				if(getChildren()[c].getSources().length == 0)
				{
					removed++;
					if(withEvents)
						remove(c);
				}
			}
			if(!withEvents && removed > 0)
			{
				ClientTreeNode [] newCh = new ClientTreeNode [getChildren().length - removed];
				for(int c1 = 0, c2 = 0; c1 < getChildren().length; c1++)
					if(getChildren()[c1].getSources().length > 0)
						newCh[c2++] = getChildren()[c1];
				setChildren(newCh);
			}
		}

		/** @return All sources that have a representation of this data */
		protected RemoteSource [] getSources()
		{
			return theNodeSources;
		}

		public String getText()
		{
			return theText;
		}

		public String getDescription()
		{
			return theDescription;
		}

		public Color getBackground()
		{
			return theBackground;
		}

		public Color getForeground()
		{
			return theForeground;
		}

		public String getIcon()
		{
			return theIcon;
		}

		@Override
		public NodeAction [] getActions()
		{
			NodeAction [] ret = super.getActions();
			ret = ArrayUtils.addAll(ret, theActions);
			return ret;
		}

		NodeAction [] getSuperActions()
		{
			return super.getActions();
		}

		private void loadContent(final RemoteSource source, boolean withDescendants)
		{
			source.sendAsync("load", "path", getPath(), "withDescendants",
				Boolean.valueOf(withDescendants));
		}

		JSONArray getPath()
		{
			JSONArray ret = new JSONArray();
			ClientTreeNode node = this;
			while(node != null)
			{
				ret.add(0, node.getID());
				node = node.getParent();
			}
			return ret;
		}
	}

	static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}
}
