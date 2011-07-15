/*
 * AppSessionTree.java Created Apr 13, 2011 by Andrew Butler, PSL
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
			return null;
		}

		void check()
		{
			for(ServiceTreeNode child : getChildren())
				((AppNode) child).check();
		}
	}

	class AppNode extends ServiceTreeNode
	{
		final PrismsApplication theApp;

		private prisms.arch.PrismsApplication.SessionWatcher theWatcher;

		AppNode(AppSessionRoot root, PrismsApplication app)
		{
			super(AppSessionServerTree.this, "app/" + app.getName(), root, true);
			theApp = app;

			ServiceTreeNode [] newChildren = new ServiceTreeNode [app.getClients().length];
			for(int c = 0; c < newChildren.length; c++)
				newChildren[c] = new ClientNode(this, app.getClients()[c]);
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
						if(((ClientNode) cn).getClient() == session.getClient())
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
						if(((ClientNode) cn).getClient() == session.getClient())
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
						if(((ClientNode) cn).getClient() == session.getClient())
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
					((ClientNode) cn).destroy();
					cn.setChildren(new ServiceTreeNode [0]);
				}
				theWatcher = null;
			}
		}

		void check()
		{
			if(theApp == null)
				return;
			for(ServiceTreeNode child : getChildren())
				((ClientNode) child).check();
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

		prisms.arch.ClientConfig getClient()
		{
			return theClient;
		}

		@Override
		public String getLoadAction(TreeClient client)
		{
			return "View Sessions";
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
			((AppNode) getParent()).loadSessions();
		}

		@Override
		protected void unloaded()
		{
			super.unloaded();
			((AppNode) getParent()).checkUnloadSessions();
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
		public String getLoadAction(TreeClient client)
		{
			return "View Properties";
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
			if(getParent() == null)
				return;
			theText = getSessionText();
			changed(false);
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
		private PrismsSession theSession;

		private PrismsProperty<?> theProperty;

		DataInspector theInspector;

		private final DataInspector.NodeController theController;

		private DataInspector.ChangeListener theChangeListener;

		PropertyNode(SessionNode parent, PrismsProperty<?> property)
		{
			super(AppSessionServerTree.this, parent, false);
			theSession = parent.getSession();
			theProperty = property;
			theInspector = getInspector(theSession, theProperty);
			theController = new DataInspector.NodeController()
			{
				public PrismsApplication getApp()
				{
					return ((SessionNode) getParent()).getSession().getApp();
				}

				public PrismsSession getSession()
				{
					return ((SessionNode) getParent()).getSession();
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
				public synchronized void propertyChanged(Object value)
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
					setValueChildren(PropertyNode.this, values, theInspector, false, true, null, 0,
						values.length);
				}

				public synchronized void valueChanged(Object [] path, boolean recursive)
				{
					for(AbstractValueNode child : (AbstractValueNode []) getChildren())
						if(child.valueChanged(path, 0, recursive))
							break;
				}
			};
			synchronized(theChangeListener)
			{
				theInspector.registerSessionListener(theSession, theChangeListener);
				Object value = theSession.getProperty(theProperty);
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

		void destroy()
		{
			if(theChangeListener != null)
				theInspector.deregisterSessionListener(theChangeListener);
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
			int power = 1;
			int pLimit = groupLimit;
			while(pLimit * 5 / 4 <= end - start)
			{
				power++;
				pLimit *= groupLimit;
			}
			power--;
			pLimit /= groupLimit;
			int count = (end - start) / pLimit;
			if((count > 1 && (end - start) % pLimit > pLimit / 4)
				|| (count == 1 && (end - start) % pLimit > pLimit / 2))
				count++;
			if(count == 1)
			{
				power--;
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
					((ValueSetNode) parent.getChildren()[i]).setValue(values, i * pLimit, (i + 1)
						* pLimit, withEvents, children, discarded);
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

		PrismsSession getValueSession()
		{
			ServiceTreeNode sn = getParent();
			while(!(sn instanceof SessionNode))
				sn = sn.getParent();
			return ((SessionNode) sn).getSession();
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

		abstract boolean valueChanged(Object [] path, int pathIdx, boolean recursive);
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
		boolean valueChanged(Object [] path, int pathIdx, boolean recursive)
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
			if(!init && theValue == value)
			{
				valueChanged(new Object [] {theValue}, 0, true);
				return;
			}
			theValue = value;
			check(true, withEvents);
		}

		@Override
		boolean valueChanged(Object [] path, int pathIdx, boolean recursive)
		{
			if(theValue == null)
				return path[pathIdx] == null;
			if(!theValue.equals(path[pathIdx]))
			{
				if(theValue instanceof Object []
					|| (theValue != null && theValue.getClass().isArray()))
				{
					for(ServiceTreeNode child : getChildren())
						if(((ValueNode) child).valueChanged(path, pathIdx, recursive))
							return true;
				}
				return false;
			}

			if(pathIdx == path.length - 1)
			{
				check(true, true);
				return true;
			}
			else
			{
				check(false, true);
				if(pathIdx < path.length - 1)
					for(ServiceTreeNode child : getChildren())
						if(((ValueNode) child).valueChanged(path, pathIdx + 1, recursive))
							return true;
				return false;
			}
		}

		@Override
		public NodeAction [] getActions(TreeClient client)
		{
			if(getParent() == null)
				return new NodeAction [0];
			NodeAction [] ret = super.getActions();
			if(theValue == null || theValue instanceof Object [] || theValue.getClass().isArray())
				return ret;
			ServiceTreeNode parent = getParent();
			while(!(parent instanceof SessionNode))
				parent = parent.getParent();
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
		boolean valueChanged(Object [] path, int pathIdx, boolean recursive)
		{
			for(AbstractValueNode child : getChildren())
				if(child.valueChanged(path, pathIdx, recursive))
					return true;
			return false;
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
	 * @param session The session to get the inspector from
	 * @param property The property to get the inspector for
	 * @return The inspector for the given property in the session, or null if no such inspector
	 *         exists
	 */
	public static DataInspector getInspector(PrismsSession session, PrismsProperty<?> property)
	{
		for(prisms.arch.event.PrismsPCL<?> pcl : session.getPropertyChangeListeners(property))
			if(pcl instanceof prisms.arch.event.PropertyManager
				&& ((prisms.arch.event.PropertyManager<?>) pcl).getInspector() != null)
				return ((prisms.arch.event.PropertyManager<?>) pcl).getInspector();
		return null;
	}
}
