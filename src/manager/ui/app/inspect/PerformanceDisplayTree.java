/*
 * PerformanceDisplay.java Created May 17, 2011 by Andrew Butler, PSL
 */
package manager.ui.app.inspect;

import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsEvent;
import prisms.ui.tree.CategoryNode;
import prisms.util.ArrayUtils;
import prisms.util.ProgramTracker.TrackNode;

/** A tree that displays performance data selected by the user */
public class PerformanceDisplayTree extends prisms.ui.tree.DataTreeMgrPlugin
{
	long theTotalTime;

	long theSnapshotTime;

	prisms.util.TrackerSet.TrackConfig theSelection;

	@Override
	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
		session.addEventListener("showPerformanceData", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, PrismsEvent evt)
			{
				refresh();
			}
		});
		CategoryNode root = new CategoryNode(this, null, "No Data Selected");
		root.setIcon("manager/time");
		root.setChildren(new TrackTreeNode [0]);
		root.addAction(new javax.swing.AbstractAction("Refresh")
		{
			public void actionPerformed(java.awt.event.ActionEvent evt)
			{
				PrismsEvent evt2 = new PrismsEvent("getPerformanceData");
				evt2.setProperty("config", theSelection);
				theSnapshotTime = System.currentTimeMillis();
				getSession().fireEvent(evt2);
				getSession().setProperty(manager.app.ManagerProperties.performanceData,
					(prisms.util.ProgramTracker) evt2.getProperty("data"));
			}
		});
		setRoot(root);
		session.addPropertyChangeListener(manager.app.ManagerProperties.performanceData,
			new prisms.arch.event.PrismsPCL<prisms.util.ProgramTracker>()
			{
				public void propertyChange(
					prisms.arch.event.PrismsPCE<prisms.util.ProgramTracker> evt)
				{
					setPerformanceData(evt.getNewValue());
				}
			});
	}

	void refresh()
	{
		PrismsEvent evt = new PrismsEvent("getPerformanceOptions");
		getSession().fireEvent(evt);
		final prisms.util.TrackerSet.TrackConfig[] configs = (prisms.util.TrackerSet.TrackConfig[]) evt
			.getProperty("options");
		if(configs == null || configs.length == 0)
		{
			getSession().getUI().error("No performance data available");
			return;
		}
		final String [] options = new String [configs.length];
		for(int o = 0; o < options.length; o++)
			options[o] = prisms.util.PrismsUtils.printTimeLength(configs[o].getKeepTime());
		prisms.ui.UI.SelectListener sl = new prisms.ui.UI.SelectListener()
		{
			public void selected(String option)
			{
				if(option == null)
					return;
				theSelection = configs[ArrayUtils.indexOf(options, option)];
				PrismsEvent evt2 = new PrismsEvent("getPerformanceData");
				evt2.setProperty("config", theSelection);
				getSession().fireEvent(evt2);
				theSnapshotTime = System.currentTimeMillis();
				getSession().setProperty(manager.app.ManagerProperties.performanceData,
					(prisms.util.ProgramTracker) evt2.getProperty("data"));
			}
		};
		getSession().getUI().select("Choose a time interval", options, 0, sl);
	}

	void setPerformanceData(prisms.util.ProgramTracker tracker)
	{
		final CategoryNode root = (CategoryNode) getRoot();
		theTotalTime = 0;
		if(tracker == null)
		{
			root.setText("No Data Selected");
			root.setChildren(new TrackTreeNode [0]);
		}
		else
		{
			for(TrackNode track : tracker.getData())
				theTotalTime += track.getLength();
			long actualLength = 0;
			if(tracker.getData().length > 0)
				actualLength = theSnapshotTime - tracker.getData()[0].getFirstStart();
			root.setText(tracker.getName() + "--Actual Interval: "
				+ prisms.util.PrismsUtils.printTimeLength(actualLength) + " Snapshot from "
				+ prisms.util.PrismsUtils.TimePrecision.SECONDS.print(theSnapshotTime, false));
			root.setChildren(ArrayUtils.adjust((TrackTreeNode []) root.getChildren(),
				tracker.getData(), new ArrayUtils.DifferenceListener<TrackTreeNode, TrackNode>()
				{
					public boolean identity(TrackTreeNode o1, TrackNode o2)
					{
						return o1.getTrackNode().getName() == o2.getName();
					}

					public TrackTreeNode added(TrackNode o, int mIdx, int retIdx)
					{
						return new TrackTreeNode(root, o);
					}

					public TrackTreeNode removed(TrackTreeNode o, int oIdx, int incMod, int retIdx)
					{
						return null;
					}

					public TrackTreeNode set(TrackTreeNode o1, int idx1, int incMod, TrackNode o2,
						int idx2, int retIdx)
					{
						o1.setTrackNode(o2);
						return o1;
					}
				}));
		}

		root.changed(true);
	}

	class TrackTreeNode extends CategoryNode
	{
		private TrackNode theTrackNode;

		TrackTreeNode(CategoryNode parent, TrackNode track)
		{
			super(PerformanceDisplayTree.this, parent, track.getName());
			setTrackNode(track);
		}

		TrackNode getTrackNode()
		{
			return theTrackNode;
		}

		void setTrackNode(TrackNode node)
		{
			theTrackNode = node;
			long lastTime = 0;
			if(getParent() instanceof TrackTreeNode)
				lastTime = ((TrackTreeNode) getParent()).getTrackNode().getFirstStart();
			setText(node.toString(0, lastTime, theTotalTime));
			if(node.isAccented(8, theTotalTime))
				setForeground(java.awt.Color.red);
			else
				setForeground(java.awt.Color.black);
			setIcon("manager/time");
			TrackTreeNode [] children;
			if(getChildren() instanceof TrackTreeNode [])
				children = (TrackTreeNode []) getChildren();
			else
				children = new TrackTreeNode [0];
			setChildren(ArrayUtils.adjust(children, node.getChildren(),
				new ArrayUtils.DifferenceListener<TrackTreeNode, TrackNode>()
				{
					public boolean identity(TrackTreeNode o1, TrackNode o2)
					{
						return o1.getTrackNode().getName() == o2.getName();
					}

					public TrackTreeNode added(TrackNode o, int mIdx, int retIdx)
					{
						return new TrackTreeNode(TrackTreeNode.this, o);
					}

					public TrackTreeNode removed(TrackTreeNode o, int oIdx, int incMod, int retIdx)
					{
						return null;
					}

					public TrackTreeNode set(TrackTreeNode o1, int idx1, int incMod, TrackNode o2,
						int idx2, int retIdx)
					{
						o1.setTrackNode(o2);
						return o1;
					}
				}));
		}
	}
}
