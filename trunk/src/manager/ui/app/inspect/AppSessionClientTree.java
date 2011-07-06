/*
 * AppSessionClientTree.java Created Apr 20, 2011 by Andrew Butler, PSL
 */
package manager.ui.app.inspect;

import manager.app.ManagerProperties;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.ClientConfig;
import prisms.arch.PrismsApplication;

/** The front-end display for the application/client/session tree in the manager */
public class AppSessionClientTree extends prisms.ui.tree.service.ClientTree
{
	static final Logger log = Logger.getLogger(AppSessionClientTree.class);

	ClientTreeNode thePerformanceNode;

	@Override
	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		setSelectionMode(SelectionMode.SINGLE);
		super.initPlugin(session, config);
		AppSessionRoot root = new AppSessionRoot("All Applications");
		PrismsApplication [] apps = session
			.getProperty(prisms.arch.event.PrismsProperties.applications);
		AppNode [] appNodes = new AppNode [apps.length];
		for(int a = 0; a < apps.length; a++)
			appNodes[a] = new AppNode(root, apps[a]);
		root.setChildren(appNodes);
		setRoot(root);
		session.addPropertyChangeListener(ManagerProperties.selectedApp,
			new prisms.arch.event.PrismsPCL<PrismsApplication>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsApplication> evt)
				{
					for(AppNode node : getRoot().getChildren())
						node.setSelected(node.getApp() == evt.getNewValue());
				}
			});
		session.addPropertyChangeListener(ManagerProperties.selectedClient,
			new prisms.arch.event.PrismsPCL<ClientConfig>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<ClientConfig> evt)
				{
					for(AppNode node : getRoot().getChildren())
					{
						if(evt.getOldValue() != null && node.getApp() == evt.getOldValue().getApp())
						{
							for(ClientNode cn : node.getChildren())
								if(cn.getClient() == evt.getOldValue())
									cn.setSelected(false);
						}
						else if(evt.getNewValue() != null
							&& node.getApp() == evt.getNewValue().getApp())
						{
							for(ClientNode cn : node.getChildren())
								if(cn.getClient() == evt.getNewValue())
									cn.setSelected(true);
						}
					}
				}
			});
		session.addEventListener("getPerformanceOptions",
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(prisms.arch.PrismsSession session2,
					prisms.arch.event.PrismsEvent evt)
				{
					if(thePerformanceNode instanceof SessionNode)
						evt.setProperty("options",
							((SessionNode) thePerformanceNode).getPerformanceOptions());
					else if(thePerformanceNode instanceof AppNode)
						evt.setProperty("options",
							((AppNode) thePerformanceNode).getPerformanceOptions());
				}
			});
		session.addEventListener("getPerformanceData", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.PrismsSession session2,
				prisms.arch.event.PrismsEvent evt)
			{
				if(thePerformanceNode instanceof SessionNode)
					evt.setProperty("data", ((SessionNode) thePerformanceNode)
						.getPerformanceData((prisms.util.TrackerSet.TrackConfig) evt
							.getProperty("config")));
				else if(thePerformanceNode instanceof AppNode)
					evt.setProperty("data", ((AppNode) thePerformanceNode)
						.getPerformanceData((prisms.util.TrackerSet.TrackConfig) evt
							.getProperty("config")));
			}
		});
	}

	@Override
	public AppSessionRoot getRoot()
	{
		return (AppSessionRoot) super.getRoot();
	}

	@Override
	public ClientTreeNode createNode(ClientTreeNode parent, String id, JSONObject content)
	{
		if(parent instanceof ClientNode)
			return new SessionNode((ClientNode) parent, id);
		else
			return super.createNode(parent, id, content);
	}

	class AppSessionRoot extends ClientTreeNode
	{
		AppSessionRoot(String title)
		{
			super(null, "ROOT");
			setText(title);
			setIcon("manager/application");
		}

		@Override
		public AppNode [] getChildren()
		{
			return (AppNode []) super.getChildren();
		}

		@Override
		public void userSetSelected(boolean selected)
		{
			if(selected)
				getSession().setProperty(ManagerProperties.selectedApp, null);
			super.userSetSelected(selected);
		}
	}

	class AppNode extends ClientTreeNode
	{
		private final PrismsApplication theApp;

		AppNode(AppSessionRoot root, PrismsApplication app)
		{
			super(root, "app/" + app.getName());
			setSelectionChangesNode(true);
			theApp = app;

			ClientNode [] newChildren = new ClientNode [app.getClients().length];
			for(int c = 0; c < newChildren.length; c++)
				newChildren[c] = new ClientNode(this, app.getClients()[c]);
			setChildren(newChildren);
			addAction(new javax.swing.AbstractAction("View Performance")
			{
				public void actionPerformed(java.awt.event.ActionEvent evt)
				{
					thePerformanceNode = AppNode.this;
					getSession().fireEvent("showPerformanceData");
				}
			});
		}

		PrismsApplication getApp()
		{
			return theApp;
		}

		@Override
		public String getText()
		{
			return theApp.getName();
		}

		@Override
		public String getIcon()
		{
			return "manager/application";
		}

		@Override
		public String getDescription()
		{
			return theApp.getDescription();
		}

		@Override
		public java.awt.Color getBackground()
		{
			if(getSession().getProperty(ManagerProperties.selectedApp) == theApp)
				return prisms.ui.list.SelectableList.DEFAULT_SELECTED_COLOR;
			else
				return super.getBackground();
		}

		@Override
		public ClientNode [] getChildren()
		{
			return (ClientNode []) super.getChildren();
		}

		@Override
		public void removed()
		{
			super.removed();
			if(thePerformanceNode == this)
				thePerformanceNode = null;
		}

		@Override
		public void userSetSelected(boolean selected)
		{
			if(selected)
				getSession().setProperty(ManagerProperties.selectedApp, theApp);
			else if(getSession().getProperty(ManagerProperties.selectedApp) == theApp)
				getSession().setProperty(ManagerProperties.selectedApp, theApp);
			super.userSetSelected(selected);
		}

		public prisms.util.TrackerSet.TrackConfig[] getPerformanceOptions()
		{
			return theApp.getTrackSet().getConfigs();
		}

		public prisms.util.ProgramTracker getPerformanceData(
			prisms.util.TrackerSet.TrackConfig config)
		{
			final RemoteSource [] sources = getSources();
			if(sources.length == 0)
				return null;
			final prisms.util.ProgramTracker[] trackers = new prisms.util.ProgramTracker [sources.length];
			final boolean [] received = new boolean [sources.length];
			final boolean [] finished = new boolean [1];
			final boolean [] canceled = new boolean [1];
			prisms.ui.UI.ProgressInformer pi = new prisms.ui.UI.ProgressInformer()
			{
				public int getTaskScale()
				{
					return received.length;
				}

				public int getTaskProgress()
				{
					int ret = 0;
					for(boolean r : received)
						if(r)
							ret++;
					return ret;
				}

				public boolean isTaskDone()
				{
					return finished[0];
				}

				public String getTaskText()
				{
					return "Retrieving performance information";
				}

				public boolean isCancelable()
				{
					return true;
				}

				public void cancel() throws IllegalStateException
				{
					canceled[0] = true;
				}
			};
			try
			{
				getSession().getUI().startTimedTask(pi);
				for(int s = 0; s < sources.length; s++)
				{
					final int finalS = s;
					sources[s].callMethodAsync("getPerformanceData", "setPerformanceData",
						new Return()
						{
							public void returned(JSONObject evt)
							{
								if(evt != null && evt.get("data") != null)
									trackers[finalS] = prisms.util.ProgramTracker
										.fromJson((JSONObject) evt.get("data"));
								received[finalS] = true;
							}
						}, "interval", Long.valueOf(config.getKeepTime()), "path", treePath(this));
				}
				boolean done = false;
				while(!done && !canceled[0])
				{
					done = true;
					for(boolean r : received)
						if(!r)
						{
							done = false;
							break;
						}
					if(!done)
						try
						{
							Thread.sleep(100);
						} catch(InterruptedException e)
						{}
				}
				if(canceled[0])
					return null;

				prisms.util.ProgramTracker ret = trackers[0];
				for(int i = 1; i < trackers.length; i++)
					ret.merge(trackers[i]);
				return ret;
			} finally
			{
				finished[0] = true;
			}
		}
	}

	class ClientNode extends ClientTreeNode
	{
		private final ClientConfig theClient;

		ClientNode(AppNode parent, ClientConfig client)
		{
			super(parent, "client/" + client.getApp().getName() + "/" + client.getName());
			setSelectionChangesNode(true);
			theClient = client;
		}

		ClientConfig getClient()
		{
			return theClient;
		}

		@Override
		public String getText()
		{
			String ret = theClient.getName();
			StringBuilder parens = new StringBuilder();
			if(theClient.isService())
				parens.append("service");
			if(parens.length() > 0)
				ret += "(" + parens + ")";
			return ret;
		}

		@Override
		public String getDescription()
		{
			return theClient.getDescription();
		}

		@Override
		public String getIcon()
		{
			return "manager/client";
		}

		@Override
		public java.awt.Color getBackground()
		{
			if(getSession().getProperty(ManagerProperties.selectedClient) == theClient)
				return prisms.ui.list.SelectableList.DEFAULT_SELECTED_COLOR;
			else
				return super.getBackground();
		}

		@Override
		public void userSetSelected(boolean selected)
		{
			if(selected)
				getSession().setProperty(ManagerProperties.selectedClient, theClient);
			else if(getSession().getProperty(ManagerProperties.selectedClient) == theClient)
				getSession().setProperty(ManagerProperties.selectedClient, theClient);
			super.userSetSelected(selected);
		}
	}

	class SessionNode extends ClientTreeNode
	{
		SessionNode(ClientNode parent, String id)
		{
			super(parent, id);
			addAction(new javax.swing.AbstractAction("View Performance")
			{
				public void actionPerformed(java.awt.event.ActionEvent evt)
				{
					thePerformanceNode = SessionNode.this;
					getSession().fireEvent("showPerformanceData");
				}
			});
		}

		@Override
		public ClientNode getParent()
		{
			return (ClientNode) super.getParent();
		}

		@Override
		public void removed()
		{
			super.removed();
			if(thePerformanceNode == this)
				thePerformanceNode = null;
		}

		public prisms.util.TrackerSet.TrackConfig[] getPerformanceOptions()
		{
			RemoteSource [] sources = getSources();
			if(sources.length != 1)
				return new prisms.util.TrackerSet.TrackConfig [0];
			JSONObject json = sources[0].callMethod("getPerformanceOptions",
				"setPerformanceOptions", "path", treePath(this));
			if(json == null || json.get("options") == null)
				return new prisms.util.TrackerSet.TrackConfig [0];
			JSONArray trackJson = (JSONArray) json.get("options");
			prisms.util.TrackerSet.TrackConfig[] configs = new prisms.util.TrackerSet.TrackConfig [trackJson
				.size()];
			for(int c = 0; c < configs.length; c++)
			{
				JSONObject tc = (JSONObject) trackJson.get(c);
				configs[c] = new prisms.util.TrackerSet.TrackConfig(
					((Number) tc.get("keepTime")).longValue(),
					((Boolean) tc.get("withStats")).booleanValue());
			}
			return configs;
		}

		public prisms.util.ProgramTracker getPerformanceData(
			prisms.util.TrackerSet.TrackConfig config)
		{
			RemoteSource [] sources = getSources();
			if(sources.length != 1)
				return null;
			JSONObject json = sources[0].callMethod("getPerformanceData", "setPerformanceData",
				"path", treePath(this), "interval", Long.valueOf(config.getKeepTime()));
			if(json == null || json.get("data") == null)
				return null;
			return prisms.util.ProgramTracker.fromJson((JSONObject) json.get("data"));
		}
	}
}
