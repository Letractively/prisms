/*
 * AppSessionServerTree.java Created Apr 13, 2011 by Andrew Butler, PSL
 */
package manager.ui.app.inspect;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.lang.reflect.Array;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsApplication;
import prisms.arch.PrismsSession;
import prisms.arch.ds.User;
import prisms.arch.event.*;
import prisms.arch.event.DataInspector.InspectSession;
import prisms.ui.list.NodeAction;
import prisms.ui.tree.DataTreeNode;
import prisms.ui.tree.service.ServiceTreeNode;
import prisms.util.ArrayUtils;

/** This server tree allows access to all session data in this enterprise instance */
public class AppSessionServerTree extends prisms.ui.tree.service.ServiceTree
{
	static final Logger log = Logger.getLogger(AppSessionServerTree.class);

	int theGroupingLimit;

	class ASSTreeClient extends TreeClient implements DataInspector.InspectSession
	{
		ASSTreeClient(String id, User u)
		{
			super(id, u);
		}
	}

	@Override
	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
		AppSessionRoot root = new AppSessionRoot("All Applications");
		PrismsApplication [] apps = session
			.getProperty(prisms.arch.event.PrismsProperties.applications);
		AppNode [] appNodes = new AppNode [apps.length];
		for(int a = 0; a < apps.length; a++)
			appNodes[a] = new AppNode(root, apps[a]);
		root.setChildren(appNodes);
		setRoot(root);
		session.addEventListener("destroy", new PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, PrismsEvent evt)
			{
				((AppSessionRoot) getRoot()).destroy();
			}
		});
		theGroupingLimit = 20;
	}

	@Override
	public void processEvent(JSONObject evt)
	{
		((AppSessionRoot) getRoot()).check();
		if("getPerformanceOptions".equals(evt.get("method")))
		{
			ServiceTreeNode node = (ServiceTreeNode) navigate(path(evt));
			JSONObject ret = new JSONObject();
			ret.put("plugin", getName());
			ret.put("method", "setPerformanceOptions");
			if(node instanceof SessionNode)
				ret.put("options", getPerformanceOptions(((SessionNode) node).getSession()
					.getTrackSet()));
			else if(node instanceof AppNode)
				ret.put("options", getPerformanceOptions(((AppNode) node).getApp().getTrackSet()));
			else
				return;
			getSession().postOutgoingEvent(ret);
		}
		else if("getPerformanceData".equals(evt.get("method")))
		{
			ServiceTreeNode node = (ServiceTreeNode) navigate(path(evt));
			long interval = ((Number) evt.get("interval")).longValue();
			JSONObject ret = new JSONObject();
			ret.put("plugin", getName());
			ret.put("method", "setPerformanceData");
			if(node instanceof SessionNode)
				ret.put("data",
					getPerformanceData(((SessionNode) node).getSession().getTrackSet(), interval));
			else if(node instanceof AppNode)
				ret.put("data",
					getPerformanceData(((AppNode) node).getApp().getTrackSet(), interval));
			else
				return;
			getSession().postOutgoingEvent(ret);
		}
		else
			super.processEvent(evt);
	}

	@Override
	public void performAction(TreeClient client, ServiceTreeNode [] nodes, String action,
		long lastCheck)
	{
		if("Kill Session".equals(action))
		{
			if(!client.getUser().getPermissions(getSession().getApp()).has("Kill Sessions"))
			{
				client.getUI().error("You do not have permission to kill sessions");
				return;
			}
			final java.util.ArrayList<SessionNode> sessions = new java.util.ArrayList<SessionNode>();
			for(int n = 0; n < nodes.length; n++)
			{
				if(nodes[n] instanceof SessionNode)
					sessions.add((SessionNode) nodes[n]);
				else
				{
					nodes = ArrayUtils.remove(nodes, n);
					n--;
				}
			}
			if(sessions.size() > 0)
			{
				String msg = "Are you sure you want to terminate ";
				if(nodes.length > 1)
					msg += "these " + nodes.length + " sessions?";
				else
					msg += sessions.get(0).getSession().getUser() + "'s session using "
						+ sessions.get(0).getSession().getClient() + "?";
				client.ui.confirm(msg, new prisms.ui.UI.ConfirmListener()
				{
					public void confirmed(boolean confirm)
					{
						if(!confirm)
							return;
						for(SessionNode session : sessions)
						{
							session.getSession().kill(getSession().getApp());
							session.check();
						}
					}
				});
			}
		}
		else
			super.performAction(client, nodes, action, lastCheck);
	}

	@Override
	public TreeClient createClient(String id, User u)
	{
		return new ASSTreeClient(id, u);
	}

	private org.json.simple.JSONArray getPerformanceOptions(prisms.util.TrackerSet trackers)
	{
		org.json.simple.JSONArray ret = new org.json.simple.JSONArray();
		for(prisms.util.TrackerSet.TrackConfig config : trackers.getConfigs())
		{
			JSONObject jsonConfig = new JSONObject();
			jsonConfig.put("keepTime", Long.valueOf(config.getKeepTime()));
			jsonConfig.put("withStats", Boolean.valueOf(config.isWithStats()));
			ret.add(jsonConfig);
		}
		return ret;
	}

	private JSONObject getPerformanceData(prisms.util.TrackerSet trackers, long interval)
	{
		prisms.util.ProgramTracker tracker = trackers.getTrackData(interval);
		return tracker == null ? null : tracker.toJson();
	}

	class AppSessionRoot extends ServiceTreeNode
	{
		private final String theTitle;

		AppSessionRoot(String title)
		{
			super(AppSessionServerTree.this, "ROOT", null, true);
			theTitle = title;
		}

		public String getText()
		{
			return theTitle;
		}

		public Color getBackground()
		{
			return Color.white;
		}

		public Color getForeground()
		{
			return Color.black;
		}

		public String getIcon()
		{
			return "manager/application";
		}

		public String getDescription()
		{
			StringBuilder ret = new StringBuilder();
			ret.append("Installed:").append(
				prisms.util.PrismsUtils.print(getSession().getApp().getEnvironment().getIDs()
					.getInstallDate()));
			ret.append("            \nInstallation ID:").append(
				getSession().getApp().getEnvironment().getIDs().getCenterID());
			return ret.toString();
		}

		@Override
		public NodeAction [] getActions(TreeClient client)
		{
			NodeAction [] ret = super.getActions(client);
			if(client.getUser().getPermissions(getSession().getApp()).has("Export Data"))
				ret = ArrayUtils.add(ret, new NodeAction("Export Data", false));
			return ret;
		}

		@Override
		public void doAction(final TreeClient client, String action)
		{
			if(action.equals("Export Data"))
			{
				if(!client.getUser().getPermissions(getSession().getApp()).has("Export Data"))
				{
					client.getUI().error("You do not have permissions to export data");
					return;
				}
				if(getSession().getApp().getEnvironment().getLogger().getExposedDir() == null)
				{
					client.getUI().error("No exposed directory to export data to");
					return;
				}
				final prisms.arch.ds.PrismsDataExportImport.Export exporter;
				exporter = new prisms.arch.ds.PrismsDataExportImport.Export(getSession()
					.getProperty(PrismsProperties.applications));
				if(client.getUser().getPermissions(getSession().getApp()).has("Engineering"))
				{
					client.getUI().select("Select the type of export to perform",
						new String [] {"Local", "Global"}, 0, new prisms.ui.UI.SelectListener()
						{
							public void selected(String option)
							{
								if(option == null)
									return;
								boolean global = option.equals("Global");
								exporter.exportData(client.getUI(), global);
							}
						});
				}
				else
					exporter.exportData(client.getUI(), false);
			}
			else
				super.doAction(client, action);
		}

		void check()
		{
			for(ServiceTreeNode child : getChildren())
				((AppNode) child).check();
		}

		void destroy()
		{
			for(ServiceTreeNode child : getChildren())
				((AppNode) child).destroy();
		}
	}

	class AppNode extends ServiceTreeNode
	{
		final PrismsApplication theApp;

		private prisms.arch.PrismsApplication.SessionWatcher theWatcher;

		private InstancesNode theInstancesNode;

		AppNode(AppSessionRoot root, PrismsApplication app)
		{
			super(AppSessionServerTree.this, "app/" + app.getName(), root, true);
			theApp = app;

			theInstancesNode = new InstancesNode(this);
			ServiceTreeNode [] newChildren = new ServiceTreeNode [app.getClients().length + 1];
			newChildren[0] = theInstancesNode;
			for(int c = 0; c < app.getClients().length; c++)
				newChildren[c + 1] = new ClientNode(this, app.getClients()[c]);
			setChildren(newChildren);
		}

		/** @return The application represented by this node */
		public PrismsApplication getApp()
		{
			return theApp;
		}

		public String getText()
		{
			return theApp.getName();
		}

		public String getIcon()
		{
			return "manager/application";
		}

		public String getDescription()
		{
			return theApp.getDescription();
		}

		public Color getBackground()
		{
			return Color.white;
		}

		public Color getForeground()
		{
			return Color.black;
		}

		@Override
		public NodeAction [] getActions(TreeClient client)
		{
			NodeAction [] ret = super.getActions(client);
			if(theInstancesNode.isVisible(client))
				ret = ArrayUtils.add(ret, new NodeAction("Hide Instances", false));
			else
				ret = ArrayUtils.add(ret, new NodeAction("View Instances", false));
			return ret;
		}

		@Override
		public void doAction(TreeClient client, String action)
		{
			if("View Instances".equals(action))
			{
				theInstancesNode.addVisibleClient(client);
				client.addEvent(new prisms.ui.tree.DataTreeEvent(
					prisms.ui.tree.DataTreeEvent.Type.CHANGE, this));
			}
			else if("Hide Instances".equals(action))
			{
				theInstancesNode.removeVisibleClient(client);
				if(((InstanceNode) theInstancesNode.getChildren()[0]).isLoaded(client))
					((InstanceNode) theInstancesNode.getChildren()[0]).unloadContent(client, false);
				client.addEvent(new prisms.ui.tree.DataTreeEvent(
					prisms.ui.tree.DataTreeEvent.Type.CHANGE, this));
			}
			else
				super.doAction(client, action);
		}

		void loadSessions()
		{
			if(theWatcher != null)
				return;

			theWatcher = new PrismsApplication.SessionWatcher()
			{
				public synchronized void sessionAdded(PrismsSession session)
				{
					boolean found = false;
					for(ServiceTreeNode cn : getChildren())
						if(cn instanceof ClientNode
							&& ((ClientNode) cn).getClient() == session.getClient())
						{
							found = true;
							((ClientNode) cn).add(new SessionNode((ClientNode) cn, session),
								cn.getChildren().length);
							break;
						}
					if(!found)
						log.error("Client " + session.getClient() + " not found in app tree");
				}

				public synchronized void sessionRemoved(PrismsSession session)
				{
					for(ServiceTreeNode cn : getChildren())
						if(cn instanceof ClientNode
							&& ((ClientNode) cn).getClient() == session.getClient())
						{
							for(int c = 0; c < cn.getChildren().length; c++)
								if(((SessionNode) cn.getChildren()[c]).getSession() == session)
								{
									((ClientNode) cn).remove(c);
									((ClientNode) cn).destroy();
									break;
								}
							break;
						}
				}
			};
			synchronized(theWatcher)
			{
				PrismsSession [] sessions = theApp.watchSessions(theWatcher, getSession().getApp());
				for(PrismsSession session : sessions)
				{
					boolean found = false;
					for(ServiceTreeNode cn : getChildren())
						if(cn instanceof ClientNode
							&& ((ClientNode) cn).getClient() == session.getClient())
						{
							found = true;
							((ClientNode) cn).add(new SessionNode((ClientNode) cn, session),
								cn.getChildren().length);
							break;
						}
					if(!found)
						log.error("Client " + session.getClient() + " not found in app tree");
				}
			}
		}

		void checkUnloadSessions()
		{
			if(theWatcher == null)
				return;
			for(ServiceTreeNode child : getChildren())
				if(child.getLoadedClients().length > 0)
					return;
			synchronized(theWatcher)
			{
				theApp.stopWatching(theWatcher);
				for(ServiceTreeNode cn : getChildren())
				{
					if(cn instanceof ClientNode)
					{
						((ClientNode) cn).destroy();
						cn.setChildren(new ServiceTreeNode [0]);
					}
				}
				theWatcher = null;
			}
		}

		void check()
		{
			if(theApp == null)
				return;
			for(ServiceTreeNode child : getChildren())
			{
				if(child instanceof InstancesNode)
					((InstancesNode) child).check();
				else if(child instanceof ClientNode)
					((ClientNode) child).check();
			}
			if(getParent() == null)
				return;
		}

		void destroy()
		{
			if(theWatcher != null)
				theApp.stopWatching(theWatcher);
			for(ServiceTreeNode child : getChildren())
			{
				if(child instanceof InstanceNode)
					((InstanceNode) child).destroy();
				else if(child instanceof ClientNode)
					((ClientNode) child).destroy();
				child.setChildren(new ServiceTreeNode [0]);
			}
		}
	}

	class InstancesNode extends ServiceTreeNode
	{
		InstancesNode(AppNode parent)
		{
			super(AppSessionServerTree.this, parent.getApp().getName() + "/instances", parent, true);
			setInvisible(true);
			setChildren(new InstanceNode [] {new InstanceNode(this)});
		}

		@Override
		public AppNode getParent()
		{
			return (AppNode) super.getParent();
		}

		public String getText()
		{
			return "Instances";
		}

		public Color getBackground()
		{
			return Color.white;
		}

		public Color getForeground()
		{
			return Color.black;
		}

		public String getIcon()
		{
			return null;
		}

		public String getDescription()
		{
			return "Contains all instances in which application properties may be viewed";
		}

		void check()
		{
			for(ServiceTreeNode node : getChildren())
				((InstanceNode) node).check();
		}
	}

	class InstanceNode extends ServiceTreeNode
	{
		private PrismsApplication theApp;

		private prisms.arch.ds.IDGenerator.PrismsInstance theInstance;

		prisms.arch.event.PrismsPCL<Object> thePCL;

		java.util.concurrent.ConcurrentLinkedQueue<PrismsPCE<Object>> theEvents;

		private boolean propertiesSet;

		private boolean useJMX;

		InstanceNode(InstancesNode parent)
		{
			super(AppSessionServerTree.this, parent, false);
			theApp = getParent().getParent().getApp();
			useJMX = true;
		}

		@Override
		public InstancesNode getParent()
		{
			return (InstancesNode) super.getParent();
		}

		public String getText()
		{
			theInstance = theApp.getEnvironment().getIDs().getLocalInstance();
			if(theInstance != null)
				return theInstance.location;
			else
				return "Unknown";
		}

		public Color getBackground()
		{
			return Color.white;
		}

		public Color getForeground()
		{
			return Color.black;
		}

		public String getIcon()
		{
			return null;
		}

		public String getDescription()
		{
			StringBuilder ret = new StringBuilder();
			if(theInstance != null)
				ret.append("Initialized ").append(
					prisms.util.PrismsUtils.print(theInstance.initTime));
			Runtime runtime = Runtime.getRuntime();
			ret.append("            \nCPUs:").append(runtime.availableProcessors());
			if(useJMX)
				try
				{
					java.lang.management.OperatingSystemMXBean osMX = java.lang.management.ManagementFactory
						.getOperatingSystemMXBean();
					if(osMX instanceof com.sun.management.OperatingSystemMXBean)
					{
						com.sun.management.OperatingSystemMXBean sunMXB = (com.sun.management.OperatingSystemMXBean) osMX;
						long startClock = System.currentTimeMillis();
						long startCPU = sunMXB.getProcessCpuTime();
						try
						{
							Thread.sleep(500);
						} catch(InterruptedException e)
						{}
						long endClock = System.currentTimeMillis();
						long endCPU = sunMXB.getProcessCpuTime();
						ret.append("            \n")
							.append(
								Math.round((endCPU - startCPU) * 100 / 1.0e6
									/ (endClock - startClock) / osMX.getAvailableProcessors()))
							.append("% usage");
					}
					else
						useJMX = false;
				} catch(Exception e)
				{
					useJMX = false;
				}
			ret.append("            \nAvailableMem:")
				.append(Math.round(runtime.maxMemory() / 1024 / 1024)).append("MB");
			ret.append("            \nMemInUse:")
				.append(Math.round(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024)
				.append("MB");
			return ret.toString();
		}

		@Override
		public String getLoadAction(TreeClient client)
		{
			if(client.getUser().getPermissions(getSession().getApp()).has("Inspect"))
				return "View Properties";
			else
				return null;
		}

		@Override
		public String getLoadAllAction(TreeClient client)
		{
			return null;
		}

		@Override
		public String getUnloadAction(TreeClient client)
		{
			return "Hide Properties";
		}

		@Override
		protected void load(TreeClient client)
		{
			super.load(client);
			loadProperties();
		}

		@Override
		protected void unloaded()
		{
			super.unloaded();
			unloadProperties();
		}

		@Override
		public void check(TreeClient client)
		{
			super.check(client);
			client.addEvent(new prisms.ui.tree.DataTreeEvent(
				prisms.ui.tree.DataTreeEvent.Type.CHANGE, this));
		}

		void loadProperties()
		{
			if(theEvents != null)
				return;
			theEvents = new java.util.concurrent.ConcurrentLinkedQueue<PrismsPCE<Object>>();
			thePCL = new prisms.arch.event.PrismsPCL<Object>()
			{
				public void propertyChange(PrismsPCE<Object> evt)
				{
					// We're only interested in when a property is added or removed here
					if((evt.getOldValue() != null) == (evt.getNewValue() != null))
						return;
					theEvents.add(evt);
				}

				@Override
				public String toString()
				{
					return "Manager AllAppsServer App Props Listener";
				}
			};
			theApp.addGlobalPropertyChangeListener(thePCL);
			PrismsProperty<?> [] props = theApp.getGlobalProperties();
			java.util.Arrays.sort(props, new java.util.Comparator<PrismsProperty<?>>()
			{
				public int compare(PrismsProperty<?> o1, PrismsProperty<?> o2)
				{
					return o1.getName().compareToIgnoreCase(o2.getName());
				}
			});
			java.util.ArrayList<ServiceTreeNode> children = new java.util.ArrayList<ServiceTreeNode>();
			for(int p = 0; p < props.length; p++)
				if(theApp.getGlobalProperty(props[p]) != null)
					children.add(new PropertyNode(this, props[p]));
			setChildren(children.toArray(new ServiceTreeNode [children.size()]));

			propertiesSet = true;
		}

		void unloadProperties()
		{
			if(!propertiesSet)
				return;
			propertiesSet = false;
			theApp.removeGlobalPropertyChangeListener(thePCL);
			for(ServiceTreeNode child : getChildren())
				((PropertyNode) child).destroy();
			setChildren(new ServiceTreeNode [0]);
			theEvents = null;
		}

		public void check()
		{
			if(!propertiesSet)
				return;
			if(theEvents.isEmpty() || getChildren().length == 0
				|| !(getChildren()[0] instanceof InstanceNode))
				return;
			synchronized(this)
			{
				InstanceNode inst = (InstanceNode) getChildren()[0];
				java.util.Iterator<PrismsPCE<Object>> iter = theEvents.iterator();
				nodeLoop: while(iter.hasNext())
				{
					PrismsPCE<Object> evt = iter.next();
					iter.remove();
					PrismsProperty<?> property = evt.getProperty();
					for(PrismsPCE<Object> evt2 : theEvents)
						if(evt2.getProperty() == property)
							continue nodeLoop;
					for(int c = 0; c < getChildren().length; c++)
					{
						PropertyNode pn = (PropertyNode) inst.getChildren()[c];
						if(pn.getProperty() == property)
						{
							if(evt.getNewValue() == null)
							{
								pn.destroy();
								inst.remove(c);
							}
							break;
						}
						else if(evt.getNewValue() != null
							&& pn.getProperty().getName().compareToIgnoreCase(property.getName()) > 0)
						{
							inst.add(new PropertyNode(inst, property), c);
							break;
						}
					}
				}
			}
		}

		void destroy()
		{
			if(thePCL != null)
				theApp.removeGlobalPropertyChangeListener(thePCL);
			for(ServiceTreeNode child : getChildren())
				if(child instanceof PropertyNode)
					((PropertyNode) child).destroy();
		}
	}

	class ClientNode extends ServiceTreeNode
	{
		private final prisms.arch.ClientConfig theClient;

		private boolean isConfigured;

		ClientNode(AppNode parent, prisms.arch.ClientConfig client)
		{
			super(AppSessionServerTree.this, "client/" + client.getApp().getName() + "/"
				+ client.getName(), parent, false);
			theClient = client;
			isConfigured = theClient.isConfigured();
		}

		@Override
		public AppNode getParent()
		{
			return (AppNode) super.getParent();
		}

		prisms.arch.ClientConfig getClient()
		{
			return theClient;
		}

		@Override
		public String getLoadAction(TreeClient client)
		{
			if(client.getUser().getPermissions(getSession().getApp()).has("View Sessions"))
				return "View Sessions";
			else
				return null;
		}

		@Override
		public String getLoadAllAction(TreeClient client)
		{
			return null;
		}

		@Override
		public String getUnloadAction(TreeClient client)
		{
			return "Hide Sessions";
		}

		@Override
		protected void load(TreeClient client)
		{
			super.load(client);
			getParent().loadSessions();
		}

		@Override
		protected void unloaded()
		{
			super.unloaded();
			getParent().checkUnloadSessions();
		}

		public String getText()
		{
			String ret = theClient.getName();
			StringBuilder parens = new StringBuilder();
			if(theClient.isService())
				parens.append("service");
			if(!theClient.isConfigured())
			{
				if(parens.length() > 0)
					parens.append(", ");
				parens.append("not configured");
			}
			if(parens.length() > 0)
				ret += "(" + parens + ")";
			return ret;
		}

		public String getDescription()
		{
			return theClient.getDescription();
		}

		public String getIcon()
		{
			return "manager/client";
		}

		public Color getBackground()
		{
			return Color.white;
		}

		public Color getForeground()
		{
			return Color.black;
		}

		void check()
		{
			if(getParent() == null)
				return;
			if(isConfigured != theClient.isConfigured())
			{
				isConfigured = theClient.isConfigured();
				changed(false);
			}
			for(ServiceTreeNode child : getChildren())
				((SessionNode) child).check();
		}

		@Override
		protected void removed()
		{
			super.removed();
			destroy();
		}

		void destroy()
		{
			for(ServiceTreeNode child : getChildren())
				((SessionNode) child).destroy();
		}
	}

	class SessionNode extends ServiceTreeNode
	{
		final PrismsSession theSession;

		private javax.swing.Action theKillAction;

		prisms.arch.event.PrismsPCL<Object> thePCL;

		java.util.concurrent.ConcurrentLinkedQueue<PrismsPCE<Object>> theEvents;

		private boolean propertiesSet;

		private String theText;

		SessionNode(ClientNode parent, PrismsSession session)
		{
			super(AppSessionServerTree.this, parent, false);
			theSession = session;
			theText = getSessionText();
			theKillAction = new javax.swing.AbstractAction("Kill Session")
			{
				public void actionPerformed(ActionEvent e)
				{
				}
			};
			addAction(theKillAction);
		}

		@Override
		public ClientNode getParent()
		{
			return (ClientNode) super.getParent();
		}

		@Override
		public String getLoadAction(TreeClient client)
		{
			if(client.getUser().getPermissions(AppSessionServerTree.this.getSession().getApp())
				.has("Inspect"))
				return "View Properties";
			else
				return null;
		}

		@Override
		public String getLoadAllAction(TreeClient client)
		{
			return null;
		}

		@Override
		public String getUnloadAction(TreeClient client)
		{
			return "Hide Properties";
		}

		@Override
		public void check(TreeClient client)
		{
			theText = getSessionText();
			client.addEvent(new prisms.ui.tree.DataTreeEvent(
				prisms.ui.tree.DataTreeEvent.Type.CHANGE, this));
			super.check(client);
		}

		@Override
		protected void load(TreeClient client)
		{
			super.load(client);
			loadProperties();
		}

		@Override
		protected void unloaded()
		{
			super.unloaded();
			unloadProperties();
		}

		PrismsSession getSession()
		{
			return theSession;
		}

		public String getText()
		{
			return theText;
		}

		public String getDescription()
		{
			StringBuilder ret = new StringBuilder();
			prisms.arch.PrismsSession.SessionMetadata md = theSession.getMetadata();
			ret.append("Authentication:");
			String authName = md.getAuth().getClass().getName();
			int idx = authName.lastIndexOf('.');
			if(idx >= 0)
				authName = authName.substring(idx + 1);
			idx = authName.indexOf("Authenticator");
			if(idx > 0 && idx + "Authenticator".length() == authName.length())
				authName = authName.substring(0, idx);
			ret.append(authName);
			ret.append("                  \nInitiated:");
			ret.append(prisms.util.PrismsUtils.TimePrecision.SECONDS.print(
				theSession.getCreationTime(), true));
			ret.append("                  \nLast Access:");
			ret.append(prisms.util.PrismsUtils.TimePrecision.SECONDS.print(
				theSession.getLastAccess(), true));
			ret.append("                  \nRemote Host:");
			ret.append(md.getRemoteHost());
			if(!md.getRemoteHost().equals(md.getRemoteAddr()))
			{
				ret.append("                  \nRemote Address:");
				ret.append(md.getRemoteAddr());
			}
			prisms.arch.ds.IDGenerator.PrismsInstance instance = AppSessionServerTree.this
				.getSession().getApp().getEnvironment().getIDs().getLocalInstance();
			if(instance != null)
			{
				ret.append("                  \nServer:");
				ret.append(instance.location);
			}
			ret.append("                  \nBrowser:");
			ret.append(md.getClientEnv().browserName);
			if(md.getClientEnv().browserVersion != null)
				ret.append(" " + md.getClientEnv().browserVersion);
			ret.append("                  \nOS:");
			ret.append(md.getClientEnv().osName);
			if(md.getClientEnv().osVersion != null)
				ret.append(" " + md.getClientEnv().osVersion);
			return ret.toString();
		}

		public String getIcon()
		{
			return "manager/user";
		}

		public Color getBackground()
		{
			return Color.white;
		}

		public Color getForeground()
		{
			if(System.currentTimeMillis() - theSession.getLastAccess() >= theSession.getClient()
				.getSessionTimeout() - prisms.arch.PrismsServer.WARN_EXPIRE_THRESHOLD)
				return Color.red;
			else
				return Color.black;
		}

		String getSessionText()
		{
			String ret = theSession.getUser().getName();
			if(theSession.isKilled())
				ret += " (killed)";
			return ret;
		}

		void loadProperties()
		{
			if(theEvents != null)
				return;
			theEvents = new java.util.concurrent.ConcurrentLinkedQueue<PrismsPCE<Object>>();
			thePCL = new prisms.arch.event.PrismsPCL<Object>()
			{
				public void propertyChange(PrismsPCE<Object> evt)
				{
					// We're only interested in when a property is added or removed here
					if((evt.getOldValue() != null) == (evt.getNewValue() != null))
						return;
					theEvents.add(evt);
				}

				@Override
				public String toString()
				{
					return "Manager AllAppsServer All Props Listener";
				}
			};
			theSession.addPropertyChangeListener(thePCL);
			PrismsProperty<?> [] props = theSession.getAllProperties();
			java.util.Arrays.sort(props, new java.util.Comparator<PrismsProperty<?>>()
			{
				public int compare(PrismsProperty<?> o1, PrismsProperty<?> o2)
				{
					return o1.getName().compareToIgnoreCase(o2.getName());
				}
			});
			for(int p = 0; p < props.length; p++)
				if(theSession.getProperty(props[p]) != null)
					add(new PropertyNode(this, props[p]), getChildren().length);

			propertiesSet = true;
		}

		void unloadProperties()
		{
			if(!propertiesSet)
				return;
			propertiesSet = false;
			theSession.removePropertyChangeListener(thePCL);
			for(ServiceTreeNode child : getChildren())
				((PropertyNode) child).destroy();
			setChildren(new ServiceTreeNode [0]);
			theEvents = null;
		}

		void check()
		{
			for(ServiceTreeNode pn : getChildren())
				if(pn instanceof PropertyNode)
					((PropertyNode) pn).checkPeriodic();
			if(getParent() == null)
				return;
			if(!propertiesSet)
				return;
			if(theEvents.isEmpty())
				return;
			synchronized(this)
			{
				java.util.Iterator<PrismsPCE<Object>> iter = theEvents.iterator();
				nodeLoop: while(iter.hasNext())
				{
					PrismsPCE<Object> evt = iter.next();
					iter.remove();
					PrismsProperty<?> property = evt.getProperty();
					for(PrismsPCE<Object> evt2 : theEvents)
						if(evt2.getProperty() == property)
							continue nodeLoop;
					for(int c = 0; c < getChildren().length; c++)
					{
						PropertyNode pn = (PropertyNode) getChildren()[c];
						if(pn.getProperty() == property)
						{
							if(evt.getNewValue() == null)
							{
								pn.destroy();
								remove(c);
							}
							break;
						}
						else if(evt.getNewValue() != null
							&& pn.getProperty().getName().compareToIgnoreCase(property.getName()) > 0)
						{
							add(new PropertyNode(this, property), c);
							break;
						}
					}
				}
			}
		}

		void destroy()
		{
			if(thePCL != null)
				theSession.removePropertyChangeListener(thePCL);
			for(ServiceTreeNode child : getChildren())
				((PropertyNode) child).destroy();
		}
	}

	class PropertyNode extends ServiceTreeNode
	{
		final PrismsApplication theApp;

		final PrismsSession theSession;

		private final PrismsProperty<?> theProperty;

		DataInspector theInspector;

		private final DataInspector.NodeController theController;

		private DataInspector.ChangeListener theChangeListener;

		java.util.ArrayList<Runnable> theEventQueue;

		PropertyNode(ServiceTreeNode parent, PrismsProperty<?> property)
		{
			super(AppSessionServerTree.this, parent, false);
			theProperty = property;
			if(parent instanceof SessionNode)
			{
				theSession = ((SessionNode) parent).getSession();
				theApp = theSession.getApp();
				theInspector = getInspector(theSession.getPropertyChangeListeners(theProperty));
			}
			else
			{
				theApp = ((InstanceNode) parent).getParent().getParent().getApp();
				theSession = null;
				theInspector = getInspector(theApp.getManagers(theProperty));
			}

			theEventQueue = new java.util.ArrayList<Runnable>();
			theController = new DataInspector.NodeController()
			{
				public PrismsApplication getApp()
				{
					return theApp;
				}

				public PrismsSession getSession()
				{
					return theSession;
				}

				public Object getValue()
				{
					return null;
				}

				public void load(InspectSession session, boolean recursive)
				{
					if(isLoaded())
						return;
					PropertyNode.this.loadContent((TreeClient) session, false);
				}

				public void unload(InspectSession session)
				{
					PropertyNode.this.unloadContent((TreeClient) session, true);
				}
			};
		}

		PrismsProperty<?> getProperty()
		{
			return theProperty;
		}

		@Override
		public String getLoadAction(TreeClient client)
		{
			if(theInspector != null)
				return "Inspect";
			else
				return null;
		}

		@Override
		public String getLoadAllAction(TreeClient client)
		{
			return null;
		}

		@Override
		public String getUnloadAction(TreeClient client)
		{
			if(theInspector != null)
				return "Uninspect";
			else
				return null;
		}

		@Override
		protected void load(TreeClient client)
		{
			super.load(client);
			doInspect();
		}

		@Override
		protected void unloaded()
		{
			super.unloaded();
			uninspect();
		}

		public String getText()
		{
			return theProperty.getName();
		}

		public String getDescription()
		{
			return null;
		}

		@Override
		public Color getBackground(TreeClient client)
		{
			if(theInspector == null)
				return Color.white;
			try
			{
				return theInspector.getPropertyBackground((ASSTreeClient) client, theController);
			} catch(Throwable e)
			{
				log.error("Failed to get background for property", e);
				return Color.white;
			}
		}

		@Override
		public Color getForeground(TreeClient client)
		{
			if(theInspector == null)
				return Color.lightGray;
			try
			{
				return theInspector.getPropertyForeground((ASSTreeClient) client, theController);
			} catch(Throwable e)
			{
				log.error("Failed to get foreground for property", e);
				return Color.black;
			}
		}

		@Override
		public String getIcon(TreeClient client)
		{
			if(theInspector == null)
				return null;
			try
			{
				return theInspector.getPropertyIcon((ASSTreeClient) client, theController);
			} catch(Throwable e)
			{
				log.error("Failed to get icon for property", e);
				return null;
			}
		}

		@Override
		public String getDescription(TreeClient client)
		{
			if(theInspector == null)
				return null;
			try
			{
				return theInspector.getPropertyDescrip((ASSTreeClient) client, theController);
			} catch(Throwable e)
			{
				log.error("Failed to get description for property", e);
				return null;
			}
		}

		public Color getBackground()
		{
			return Color.white;
		}

		public Color getForeground()
		{
			return Color.black;
		}

		public String getIcon()
		{
			return null;
		}

		@Override
		public NodeAction [] getActions(TreeClient client)
		{
			NodeAction [] ret = super.getActions();
			if(theInspector != null)
				ret = ArrayUtils.concat(NodeAction.class, ret,
					theInspector.getPropertyActions((ASSTreeClient) client, theController));
			return ret;
		}

		@Override
		public void doAction(TreeClient client, String action)
		{
			if(theInspector != null)
			{
				for(NodeAction a : theInspector.getPropertyActions((ASSTreeClient) client,
					theController))
					if(a.getText().equals(action))
					{
						theInspector.performPropertyAction((ASSTreeClient) client, theController,
							action);
						return;
					}
			}
			super.doAction(action);
		}

		void doInspect()
		{
			destroy();
			theChangeListener = new DataInspector.ChangeListener()
			{
				public void propertyChanged(final Object value)
				{
					synchronized(this)
					{
						theEventQueue.add(new Runnable()
						{
							public void run()
							{
								Object [] values;
								if(value instanceof Object [])
									values = (Object []) value;
								else if(value != null && value.getClass().isArray())
								{
									values = new Object [Array.getLength(value)];
									for(int i = 0; i < values.length; i++)
										values[i] = Array.get(value, i);
								}
								else
									values = new Object [] {value};
								setValueChildren(PropertyNode.this, values, theInspector, false,
									true, null, 0, values.length);
							}
						});
					}
				}

				public void valueChanged(final Object item, final boolean recursive)
				{
					synchronized(this)
					{
						theEventQueue.add(new Runnable()
						{
							public void run()
							{
								for(AbstractValueNode child : (AbstractValueNode []) getChildren())
									child.valueChanged(item, recursive);
							}
						});
					}
				}
			};
			synchronized(theChangeListener)
			{
				Object value;
				if(theSession != null)
				{
					theInspector.registerSessionListener(theSession, theChangeListener);
					value = theSession.getProperty(theProperty);
				}
				else
				{
					theInspector.registerGlobalListener(theApp, theChangeListener);
					value = theApp.getGlobalProperty(theProperty);
				}
				Object [] values;
				if(value instanceof Object [])
					values = (Object []) value;
				else if(value != null && value.getClass().isArray())
				{
					values = new Object [Array.getLength(value)];
					for(int i = 0; i < values.length; i++)
						values[i] = Array.get(value, i);
				}
				else
					values = new Object [] {value};
				setValueChildren(this, values, theInspector, false, false, null, 0, values.length);
				changed(true);
			}
		}

		void uninspect()
		{
			destroy();
		}

		void checkPeriodic()
		{
			runEvents();
			for(ServiceTreeNode node : getChildren())
				if(node instanceof AbstractValueNode)
					((AbstractValueNode) node).checkPeriodic();
		}

		synchronized void runEvents()
		{
			java.util.Iterator<Runnable> iter = theEventQueue.iterator();
			while(iter.hasNext())
			{
				try
				{
					iter.next().run();
				} catch(Exception e)
				{
					log.error("Could not update inspected properties", e);
				}
				iter.remove();
			}
		}

		void destroy()
		{
			if(theChangeListener != null)
			{
				if(theSession != null)
					theInspector.deregisterSessionListener(theChangeListener);
				else
					theInspector.deregisterGlobalListener(theChangeListener);
			}
			theChangeListener = null;
			setChildren(new AbstractValueNode [0]);
		}
	}

	boolean setValueChildren(final ServiceTreeNode parent, Object [] values,
		final DataInspector inspector, final boolean recursive, final boolean withEvents,
		java.util.ArrayList<ValueNode> discarded, int start, int end)
	{
		if(!(parent instanceof ValueSetNode || parent.isLoaded()))
			return false;
		final AbstractValueNode [] children = (AbstractValueNode []) parent.getChildren();
		final boolean areGroups = children.length > 0 && children[0] instanceof ValueSetNode;
		final int groupLimit = theGroupingLimit;
		if(end >= values.length)
			end = values.length - 1;
		if(end - start > 100000)
		{
			parent.setChildren(new AbstractValueNode [] {new ErrorValueNode(parent,
				"Too many elements (" + (end - start) + ")--cannot inspect")});
			if(withEvents)
				parent.changed(true);
			return true;
		}
		else if(end - start > groupLimit * 5 / 4)
		{
			int pLimit = groupLimit;
			while(pLimit * 5 / 4 <= end - start)
				pLimit *= groupLimit;
			pLimit /= groupLimit;
			int count = (end - start) / pLimit;
			if((count > 1 && (end - start) % pLimit > pLimit / 4)
				|| (count == 1 && (end - start) % pLimit > pLimit / 2))
				count++;
			if(count == 1)
			{
				pLimit /= groupLimit;
				count = (end - start) / pLimit;
				if((end - start) % pLimit > pLimit / 4)
					count++;
			}

			if(discarded == null)
				discarded = new java.util.ArrayList<ValueNode>();
			if(children.length != count || !areGroups)
			{
				AbstractValueNode [] newCh = new AbstractValueNode [count];
				System.arraycopy(children, 0, newCh, 0, children.length > count ? count
					: children.length);
				for(int i = 0; i < newCh.length; i++)
				{
					int tempEnd = start + (i + 1) * pLimit;
					if(i == newCh.length - 1)
						tempEnd = end;
					if(newCh[i] instanceof ValueSetNode)
						((ValueSetNode) newCh[i]).setValue(values, start + i * pLimit, tempEnd,
							withEvents, children, discarded);
					else
						newCh[i] = new ValueSetNode(parent, values, start + i * pLimit, tempEnd,
							withEvents, children, discarded);
				}
				for(int i = newCh.length; i < count; i++)
				{
					int tempEnd = end + (i + 1) * pLimit;
					if(i == newCh.length - 1)
						tempEnd = end;
					newCh[i] = new ValueSetNode(parent, values, start + i * pLimit, tempEnd,
						withEvents, children, discarded);
				}

				parent.setChildren(newCh);
				if(withEvents)
					parent.changed(true);
				return true;
			}
			else
			{
				for(int i = 0; i < parent.getChildren().length; i++)
				{
					int _start = i * pLimit;
					int _end;
					if(i == parent.getChildren().length - 1)
						_end = values.length - 1;
					else
						_end = (i + 1) * pLimit;
					((ValueSetNode) parent.getChildren()[i]).setValue(values, _start, _end,
						withEvents, children, discarded);
				}
				return true;
			}
		}
		else
		{
			final java.util.ArrayList<ValueNode> [] fDiscarded = discarded == null ? null
				: new java.util.ArrayList [] {discarded};
			final int [] startEnd = new int [] {start, end};
			final boolean [] changed = new boolean [1];
			AbstractValueNode [] newCh = ArrayUtils.adjust(children, values,
				new ArrayUtils.DifferenceListener<AbstractValueNode, Object>()
				{
					public boolean identity(AbstractValueNode o1, Object o2)
					{
						return o1 instanceof ValueNode && ((ValueNode) o1).getValue() == o2;
					}

					public AbstractValueNode added(Object o, int mIdx, int retIdx)
					{
						if(mIdx < startEnd[0] || mIdx > startEnd[1])
							return null;
						ValueNode ret = null;
						for(AbstractValueNode ch : children)
							if(ch instanceof ValueSetNode)
							{
								ret = ((ValueSetNode) ch).contains(o, true);
								if(ret != null)
									break;
							}
						if(ret == null)
						{
							ret = new ValueNode(parent, inspector);
							ret.setValue(o, true, false);
						}
						if(withEvents)
						{
							changed[0] = true;
							parent.add(ret, retIdx);
						}
						for(TreeClient client : parent.getLoadedClients())
							if(parent.isAllLoaded(client))
								ret.loadSubContent(client);
						return ret;
					}

					public AbstractValueNode removed(AbstractValueNode o, int oIdx, int incMod,
						int retIdx)
					{
						if(withEvents)
						{
							changed[0] = true;
							parent.remove(incMod);
						}
						if(fDiscarded != null && o instanceof ValueNode)
							fDiscarded[0].add((ValueNode) o);
						return null;
					}

					public AbstractValueNode set(AbstractValueNode o1, int idx1, int incMod,
						Object o2, int idx2, int retIdx)
					{
						if(idx2 < startEnd[0] || idx2 > startEnd[1])
						{
							if(withEvents)
							{
								changed[0] = true;
								parent.remove(incMod);
							}
							if(fDiscarded != null && o1 instanceof ValueNode)
								fDiscarded[0].add((ValueNode) o1);
							return null;
						}
						if(incMod != retIdx)
						{
							if(withEvents)
							{
								parent.move(incMod, retIdx);
								changed[0] = true;
							}
							if(recursive)
							{
								changed[0] = true;
								((ValueNode) o1).check(recursive, withEvents);
							}
						}
						return o1;
					}
				});
			if(!withEvents)
				parent.setChildren(newCh);
			return changed[0];
		}
	}

	abstract class AbstractValueNode extends ServiceTreeNode
	{
		AbstractValueNode(ServiceTreeNode parent, boolean preLoaded)
		{
			super(AppSessionServerTree.this, parent, preLoaded);
		}

		@Override
		public AbstractValueNode [] getChildren()
		{
			return (AbstractValueNode []) super.getChildren();
		}

		@Override
		public void setChildren(DataTreeNode [] children)
		{
			if(!(children instanceof AbstractValueNode []))
			{
				AbstractValueNode [] newCh = new AbstractValueNode [children.length];
				for(int i = 0; i < children.length; i++)
				{
					if(children[i] instanceof AbstractValueNode)
						newCh[i] = (AbstractValueNode) children[i];
					else
						throw new IllegalArgumentException("Only AbstractValueNodes may be"
							+ " children of a AbstractValueNode");
				}
				children = newCh;
			}
			super.setChildren(children);
		}

		public Color getBackground()
		{
			return Color.white;
		}

		public Color getForeground()
		{
			return Color.black;
		}

		public String getIcon()
		{
			return null;
		}

		public String getDescription()
		{
			return null;
		}

		void checkPeriodic()
		{
			for(AbstractValueNode node : getChildren())
				node.checkPeriodic();
		}

		abstract boolean valueChanged(Object item, boolean recursive);
	}

	class ErrorValueNode extends AbstractValueNode
	{
		private final String theMessage;

		ErrorValueNode(ServiceTreeNode parent, String message)
		{
			super(parent, true);
			theMessage = message;
		}

		@Override
		boolean valueChanged(Object item, boolean recursive)
		{
			return true;
		}

		@Override
		public Color getForeground()
		{
			return Color.red;
		}

		public String getText()
		{
			return theMessage;
		}
	}

	class ValueNode extends AbstractValueNode
	{
		private Object theValue;

		final DataInspector theInspector;

		final prisms.arch.event.DataInspector.NodeController theController;

		ValueNode(ServiceTreeNode parent, DataInspector inspector)
		{
			super(parent, false);
			theInspector = inspector;
			theController = new DataInspector.NodeController()
			{
				public PrismsApplication getApp()
				{
					ServiceTreeNode p = getParent();
					while(p != null)
					{
						if(p instanceof AppNode)
							return ((AppNode) p).theApp;
						p = p.getParent();
					}
					return null;
				}

				public PrismsSession getSession()
				{
					ServiceTreeNode p = getParent();
					while(p != null)
					{
						if(p instanceof SessionNode)
							return ((SessionNode) p).theSession;
						else if(p instanceof AppNode)
							return null;
						p = p.getParent();
					}
					return null;
				}

				public Object getValue()
				{
					return ValueNode.this.getValue();
				}

				public void load(InspectSession session, boolean recursive)
				{
					loadContent((TreeClient) session, recursive);
				}

				public void unload(InspectSession session)
				{
					unloadContent((TreeClient) session, true);
				}
			};
		}

		Object getValue()
		{
			return theValue;
		}

		@Override
		public String getLoadAction(TreeClient client)
		{
			if(theValue == null)
				return null;
			else if(theValue instanceof Object [] || theValue.getClass().isArray())
				return "Load elements";
			String ret = theInspector.canDescend((ASSTreeClient) client, theController, false);
			if(ret != null && theInspector.getChildren(theController).length > 0)
				return ret;
			else
				return null;
		}

		@Override
		public String getLoadAllAction(TreeClient client)
		{
			if(theValue == null)
				return null;
			else if(theValue instanceof Object [] || theValue.getClass().isArray())
				return null;
			String ret = theInspector.canDescend((ASSTreeClient) client, theController, true);
			if(ret != null && theInspector.getChildren(theController).length > 0)
				return ret;
			else
				return null;
		}

		@Override
		public String getUnloadAction(TreeClient client)
		{
			return theInspector.getHideLabel((ASSTreeClient) client, theController);
		}

		@Override
		protected void load(TreeClient client)
		{
			super.load(client);
			loadSubData();
		}

		@Override
		protected void unloaded()
		{
			super.unloaded();
		}

		@Override
		public void check(TreeClient client)
		{
			if(theInspector != null)
			{
				prisms.arch.event.DataInspector.ItemMetadata md = theInspector
					.getMetadata(theController);
				if(md != null && md.isVolatile())
					client.addEvent(new prisms.ui.tree.DataTreeEvent(
						prisms.ui.tree.DataTreeEvent.Type.CHANGE, this));
			}
			super.check(client);
		}

		@Override
		public String getText(TreeClient client)
		{
			if(theValue == null)
				return "null";
			else if(theValue instanceof Object [] || theValue.getClass().isArray())
				return className(theValue.getClass());
			else
			{
				try
				{
					return theInspector.getText((ASSTreeClient) client, theController);
				} catch(Throwable e)
				{
					log.error("Failed to get text for value", e);
					return "Error";
				}
			}
		}

		@Override
		public String getDescription(TreeClient client)
		{
			if(theValue == null || theValue instanceof Object [] || theValue.getClass().isArray())
				return null;
			else
			{
				try
				{
					return theInspector.getDescrip((ASSTreeClient) client, theController);
				} catch(Throwable e)
				{
					log.error("Failed to get description for value", e);
					return null;
				}
			}
		}

		@Override
		public String getIcon(TreeClient client)
		{
			if(theValue == null || theValue instanceof Object [] || theValue.getClass().isArray())
				return null;
			else
			{
				try
				{
					return theInspector.getIcon((ASSTreeClient) client, theController);
				} catch(Throwable e)
				{
					log.error("Failed to get icon for value", e);
					return null;
				}
			}
		}

		@Override
		public Color getBackground(TreeClient client)
		{
			if(theValue == null || theValue instanceof Object [] || theValue.getClass().isArray())
				return Color.white;
			else
			{
				try
				{
					return theInspector.getBackground((ASSTreeClient) client, theController);
				} catch(Throwable e)
				{
					log.error("Failed to get background for value", e);
					return Color.white;
				}
			}
		}

		@Override
		public Color getForeground(TreeClient client)
		{
			if(theValue == null || theValue instanceof Object [] || theValue.getClass().isArray())
				return Color.black;
			else
			{
				try
				{
					return theInspector.getForeground((ASSTreeClient) client, theController);
				} catch(Throwable e)
				{
					log.error("Failed to get foreground for value", e);
					return Color.black;
				}
			}
		}

		public String getText()
		{
			return String.valueOf(theValue);
		}

		void setValue(Object value, boolean init, boolean withEvents)
		{
			if(init || theValue != value)
				theValue = value;
			check(true, withEvents);
		}

		@Override
		boolean valueChanged(Object item, boolean recursive)
		{
			if(theValue == null)
				return item == null;
			if(theValue.equals(item))
			{
				check(recursive, true);
				return true;
			}
			boolean ret = false;
			for(ServiceTreeNode child : getChildren())
				ret |= ((ValueNode) child).valueChanged(item, recursive);
			if(ret)
				check(false, true);
			return ret;
		}

		@Override
		public NodeAction [] getActions(TreeClient client)
		{
			if(getParent() == null)
				return new NodeAction [0];
			NodeAction [] ret = super.getActions();
			if(theValue == null || theValue instanceof Object [] || theValue.getClass().isArray())
				return ret;
			ret = ArrayUtils.concat(NodeAction.class, ret,
				theInspector.getActions((ASSTreeClient) client, theController));
			return ret;
		}

		@Override
		public void doAction(TreeClient client, String action)
		{
			if(theInspector != null && theValue != null && !theValue.getClass().isArray())
			{
				NodeAction [] actions = theInspector.getActions((ASSTreeClient) client,
					theController);
				for(NodeAction a : actions)
					if(a.getText().equals(action))
					{
						theInspector.performAction((ASSTreeClient) client, theController, action);
						return;
					}
			}
			super.doAction(action);
		}

		void check(final boolean recursive, final boolean withEvents)
		{
			if(getParent() == null)
				return;
			if(theValue instanceof Object [] || (theValue != null && theValue.getClass().isArray()))
			{
				Object [] values;
				if(theValue instanceof Object [])
					values = (Object []) theValue;
				else
				{
					values = new Object [Array.getLength(theValue)];
					for(int i = 0; i < values.length; i++)
						values[i] = Array.get(theValue, i);
				}
				boolean changed = setValueChildren(this, values, theInspector, recursive,
					withEvents, null, 0, values.length);
				if(!changed)
					changed(false);
			}
			else
			{
				if(withEvents)
					changed(false);

				if(recursive && getLoadedClients().length > 0)
				{
					Object [] children = theInspector.getChildren(theController);
					setValueChildren(this, children, theInspector, recursive, withEvents, null, 0,
						children.length);
				}
			}
		}

		/* Overridden to avoid synthetic access from inner classes */
		@Override
		protected void loadSubContent(TreeClient client)
		{
			super.loadSubContent(client);
		}

		void loadSubData()
		{
			Object [] children;
			if(theValue == null || theValue instanceof Object [] || theValue.getClass().isArray())
			{
				if(theValue instanceof Object [])
					children = (Object []) theValue;
				else
				{
					children = new Object [Array.getLength(theValue)];
					for(int i = 0; i < children.length; i++)
						children[i] = Array.get(theValue, i);
				}
			}
			else
				children = theInspector.getChildren(theController);
			setValueChildren(this, children, theInspector, false, true, null, 0, children.length);
		}

		void unloadSubData()
		{
			setChildren(new ValueNode [0]);
		}
	}

	class ValueSetNode extends AbstractValueNode
	{
		private int theStart;

		private int theEnd;

		ValueSetNode(ServiceTreeNode parent, Object [] values, int start, int end,
			boolean withEvents, AbstractValueNode [] oldChildren,
			java.util.ArrayList<ValueNode> discarded)
		{
			super(parent, false);
			setValue(values, start, end, withEvents, oldChildren, discarded);
		}

		@Override
		public String getLoadAction(TreeClient client)
		{
			return "Show Elements";
		}

		@Override
		public String getLoadAllAction(TreeClient client)
		{
			return null;
		}

		@Override
		public String getUnloadAction(TreeClient client)
		{
			return "Hide Elements";
		}

		DataInspector getInspector()
		{
			ServiceTreeNode parent = getParent();
			while(parent != null)
			{
				if(parent instanceof PropertyNode)
					return ((PropertyNode) parent).theInspector;
				else if(parent instanceof ValueNode)
					return ((ValueNode) parent).theInspector;
				parent = parent.getParent();
			}
			throw new IllegalStateException("Could not get inspector");
		}

		ValueNode contains(Object value, boolean remove)
		{
			for(int c = 0; c < getChildren().length; c++)
			{
				AbstractValueNode child = getChildren()[c];
				if(child instanceof ValueSetNode)
				{
					ValueNode ret = ((ValueSetNode) child).contains(value, remove);
					if(ret != null)
						return ret;
				}
				else if(child instanceof ValueNode)
				{
					if(((ValueNode) child).getValue() == value)
					{
						if(remove)
							setChildren(ArrayUtils.remove(getChildren(), c));
						return (ValueNode) child;
					}
				}
			}
			return null;
		}

		void setValue(Object [] values, int start, int end, boolean withEvents,
			AbstractValueNode [] oldChildren, java.util.ArrayList<ValueNode> discarded)
		{
			theStart = start;
			if(end >= values.length)
				end = values.length - 1;
			theEnd = end;
			setValueChildren(this, values, getInspector(), false, withEvents, discarded, start, end);
		}

		public String getText()
		{
			StringBuilder ret = new StringBuilder();
			ret.append('[');
			ret.append(theStart);
			ret.append('-');
			ret.append(theEnd);
			ret.append(']');
			return ret.toString();
		}

		@Override
		public String getText(TreeClient client)
		{
			StringBuilder ret = new StringBuilder();
			ret.append('[');
			ret.append(theStart);
			ret.append('-');
			ret.append(theEnd);
			ret.append(']');
			String first = getFirstText(client);
			if(first == null)
				return ret.toString();
			String last = getLastText(client);
			if(last == null)
				return ret.toString();
			if(first.equals(last))
			{
				ret.append(' ');
				ret.append(first);
				return ret.toString();
			}
			int charDiff = 0;
			for(; charDiff < first.length() && charDiff < last.length()
				&& first.charAt(charDiff) == last.charAt(charDiff); charDiff++);
			if(charDiff >= 7)
			{
				ret.append(' ');
				ret.append(first.substring(0, charDiff));
				ret.append('\u2026');
				return ret.toString();
			}
			if(first.length() > 7)
				first = shorten(first);
			if(last.length() > 7)
				last = shorten(last);
			ret.append(' ');
			ret.append(first);
			ret.append(" to ");
			ret.append(last);
			return ret.toString();
		}

		private String getFirstText(TreeClient client)
		{
			if(getChildren().length == 0)
				return null;
			else if(getChildren()[0] instanceof ValueSetNode)
				return ((ValueSetNode) getChildren()[0]).getFirstText(client);
			else
				return ((ValueNode) getChildren()[0]).getText(client);
		}

		private String getLastText(TreeClient client)
		{
			if(getChildren().length == 0)
				return null;
			else if(getChildren()[getChildren().length - 1] instanceof ValueSetNode)
				return ((ValueSetNode) getChildren()[getChildren().length - 1]).getLastText(client);
			else
				return ((ValueNode) getChildren()[getChildren().length - 1]).getText(client);
		}

		private String shorten(String name)
		{
			name = name.substring(0, 7);
			int i = name.length() - 1;
			while(i >= 0 && Character.isWhitespace(name.charAt(i)))
				i--;
			if(i < name.length() - 1)
				name = name.substring(0, i + 1);
			name += "\u2026";
			return name;
		}

		@Override
		public String getIcon(TreeClient client)
		{
			if(getChildren().length == 0)
				return null;
			String icon = getChildren()[0].getIcon(client);
			if(icon == null)
				return null;
			for(int i = 0; i < getChildren().length; i++)
				if(!icon.equals(getChildren()[i].getIcon(client)))
					return null;
			return icon;
		}

		@Override
		boolean valueChanged(Object item, boolean recursive)
		{
			boolean ret = false;
			for(AbstractValueNode child : getChildren())
				ret |= child.valueChanged(item, recursive);
			return ret;
		}
	}

	static int getSize(Object value)
	{
		int ret = 0;
		if(value == null)
		{}
		else if(value instanceof Object [])
			for(Object val : ((Object []) value))
				ret += getSize(val);
		else if(value.getClass().isArray())
			ret = Array.getLength(value);
		else
			ret = 1;
		return ret;
	}

	static String className(Class<?> clazz)
	{
		if(clazz.isArray())
			return className(clazz.getComponentType()) + "[]";
		else
			return clazz.getName();
	}

	/**
	 * Gets the inspector for a property
	 * 
	 * @param pcls The listeners to get the inspector from
	 * @return The inspector for the given property in the session, or null if no such inspector
	 *         exists
	 */
	public static DataInspector getInspector(PrismsPCL<?> [] pcls)
	{
		for(prisms.arch.event.PrismsPCL<?> pcl : pcls)
			if(pcl instanceof prisms.arch.event.PropertyManager
				&& ((prisms.arch.event.PropertyManager<?>) pcl).getInspector() != null)
				return ((prisms.arch.event.PropertyManager<?>) pcl).getInspector();
		return null;
	}
}
