/*
 * AllCentersList.java Created Dec 2, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import org.apache.log4j.Logger;

import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsProperty;
import prisms.ui.list.ActionListNode;
import prisms.ui.list.DataListNode;

/** Gives the user a view of all PRISMS centers to allow them to create, select, or delete them */
public abstract class AllCentersList extends prisms.ui.list.SelectableList<PrismsCenter>
{
	static final Logger log = Logger.getLogger(AllCentersList.class);

	private javax.swing.Action DELETE;

	PrismsProperty<PrismsCenter []> theCentersProp;

	PrismsProperty<PrismsCenter> theSelectedCenterProp;

	prisms.records.RecordUtils.CenterStatus theCurrentCheckingStatus;

	@Override
	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
		setDisplaySelectedOnly(false);
		DELETE = new javax.swing.AbstractAction("Delete")
		{
			public void actionPerformed(java.awt.event.ActionEvent evt)
			{
				throw new IllegalStateException("Should not get here");
			}
		};
		DELETE.putValue("multiple", Boolean.TRUE);
		theCentersProp = PrismsProperty.get(config.get("centers"), PrismsCenter [].class);
		theSelectedCenterProp = PrismsProperty
			.get(config.get("selectedCenter"), PrismsCenter.class);
		setSelectionMode(SelectionMode.SINGLE);
		checkCreatable();
		setListData(session.getProperty(theCentersProp));
		session.addPropertyChangeListener(theCentersProp,
			new prisms.arch.event.PrismsPCL<PrismsCenter []>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsCenter []> evt)
				{
					setListData(evt.getNewValue());
				}
			});
		final prisms.ui.list.SelectableList<PrismsCenter> self = this;
		prisms.arch.event.PrismsEventListener centerListener = new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				PrismsCenter center = (PrismsCenter) evt.getProperty("center");
				if(center == null)
					center = ((SyncRecord) evt.getProperty("record")).getCenter();
				for(DataListNode node : self)
					if(node instanceof prisms.ui.list.SelectableList<?>.ItemNode
						&& ((ItemNode) node).getObject().equals(center))
						((ItemNode) node).check();
			}
		};
		session.addEventListener("centerChanged", centerListener);
		session.addEventListener("syncAttempted", centerListener);
		session.addEventListener("syncAttemptChanged", centerListener);
		PrismsCenter sel = session.getProperty(theSelectedCenterProp);
		setSelectedObjects(sel == null ? new PrismsCenter [0] : new PrismsCenter [] {sel});
		session.addPropertyChangeListener(theSelectedCenterProp,
			new prisms.arch.event.PrismsPCL<PrismsCenter>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsCenter> evt)
				{
					PrismsCenter sel2 = evt.getNewValue();
					setSelectedObjects(sel2 == null ? new PrismsCenter [0]
						: new PrismsCenter [] {sel2});
				}
			});
		session.addEventListener("createCenterClicked", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				PrismsCenter [] allCenters = getSession().getProperty(theCentersProp);
				String newName = newCenterName(allCenters);
				PrismsCenter newCenter = new PrismsCenter(newName);
				getSession().setProperty(theCentersProp,
					prisms.util.ArrayUtils.add(allCenters, newCenter));
				getSession().setProperty(theSelectedCenterProp, newCenter);
			}
		});
	}

	/** May be called when a user's authority dealing with centers may have been changed */
	protected void userAuthorityChanged()
	{
		checkCreatable();
		for(int i = 0; i < getItemCount(); i++)
			AllCentersList.this.nodeChanged(getItem(i));
	}

	void checkCreatable()
	{
		boolean creatable = canCreate();
		if(creatable && !(getItemCount() > 0 && getItem(0) instanceof ActionListNode))
		{
			ActionListNode action = new ActionListNode(this, "createCenterClicked");
			action.setText("*Create Center*");
			action.setIcon("prisms/center");
			addNode(action, 0);
		}
		else if(!creatable && getItemCount() > 0 && getItem(0) instanceof ActionListNode)
			removeNode(0);
	}

	/** @return Whether this session's user has permission to create new centers */
	protected abstract boolean canCreate();

	String newCenterName(PrismsCenter [] centers)
	{
		String firstTry = "New Center";
		if(!hasName(centers, firstTry))
			return firstTry;
		int i;
		for(i = 2; hasName(centers, firstTry + " " + i); i++);
		return firstTry + " " + i;
	}

	boolean hasName(PrismsCenter [] centers, String name)
	{
		for(PrismsCenter s : centers)
			if(s.getName().equals(name))
				return true;
		return false;
	}

	@Override
	public synchronized void setSelection(DataListNode [] nodes, boolean fromUser)
	{
		super.setSelection(nodes, fromUser);
		if(fromUser && nodes.length == 1 && nodes[0] instanceof ActionListNode)
		{
			PrismsCenter sel = getSession().getProperty(theSelectedCenterProp);
			setSelectedObjects(sel == null ? new PrismsCenter [0] : new PrismsCenter [] {sel});
		}
	}

	@Override
	public void performAction(DataListNode [] items, String action)
	{
		if(action.equals("Delete"))
		{
			prisms.ui.UI ui = getSession().getUI();
			final java.util.ArrayList<PrismsCenter> centers;
			centers = new java.util.ArrayList<PrismsCenter>();
			StringBuilder error = new StringBuilder();
			for(DataListNode node : items)
			{
				if(!(node instanceof prisms.ui.list.SelectableList<?>.ItemNode))
					continue;

				final PrismsCenter center = ((ItemNode) node).getObject();

				if(!canDelete(center))
				{
					error.append("You do not have permission to delete data center: ");
					error.append(center);
					error.append('\n');
				}
				else
					centers.add(center);

				if(error.length() > 0)
					ui.error(error.substring(0, error.length() - 1));
			}
			if(centers.size() == 0)
				return;

			prisms.ui.UI.ConfirmListener cl = new prisms.ui.UI.ConfirmListener()
			{
				public void confirmed(boolean confirmed)
				{
					if(!confirmed)
						return;

					deleteCenters(centers.toArray(new PrismsCenter [centers.size()]));
				}
			};
			if(centers.size() == 1)
				ui.confirm("Are you sure you want to delete data center \"" + centers.get(0)
					+ "\"?", cl);
			else
				ui.confirm("Are you sure you want to delete these " + centers.size()
					+ " data centers?", cl);
		}
		else
			super.performAction(items, action);
	}

	/**
	 * @param center The center to delete
	 * @return Whether this session's user has permission to delete the given center
	 */
	protected abstract boolean canDelete(PrismsCenter center);

	/** @param centers The centers to delete from the active set in this session's record keeper */
	protected abstract void deleteCenters(PrismsCenter [] centers);

	/** @return The record keeper that governs the centers in this list. May be null. */
	protected abstract RecordKeeper getKeeper();

	/** @return The status of the center that is currently being checked */
	protected RecordUtils.CenterStatus getCurrentCheckingStatus()
	{
		return theCurrentCheckingStatus;
	}

	@Override
	public String getIcon()
	{
		return "prisms/center";
	}

	@Override
	public boolean canSelect(PrismsCenter item)
	{
		return true;
	}

	@Override
	public void doSelect(PrismsCenter item)
	{
		getSession().setProperty(theSelectedCenterProp, item);
	}

	@Override
	public boolean canDeselect(PrismsCenter item)
	{
		return true;
	}

	@Override
	public void doDeselect(PrismsCenter item)
	{
		if(item.equals(getSession().getProperty(theSelectedCenterProp)))
			getSession().setProperty(theSelectedCenterProp, null);
	}

	@Override
	public String getItemName(PrismsCenter item)
	{
		return item.getName();
	}

	@Override
	public String getItemIcon(PrismsCenter item)
	{
		if(theCurrentCheckingStatus == null)
			return "prisms/center";
		switch(theCurrentCheckingStatus.quality)
		{
		case GOOD:
			return "prisms/centerGood";
		case NORMAL:
			return "prisms/center";
		case POOR:
			return "prisms/centerPoor";
		case BAD:
			return "prisms/centerBad";
		}
		return "prisms/center";
	}

	@Override
	public String getItemDescrip(PrismsCenter item)
	{
		if(theCurrentCheckingStatus == null)
			return null;
		return theCurrentCheckingStatus.message;
	}

	@Override
	public ItemNode createObjectNode(PrismsCenter item)
	{
		ItemNode ret = new CenterListItem(item);
		if(canDelete(item))
			ret.addAction(DELETE);
		return ret;
	}

	/** Represents a center in this list */
	protected class CenterListItem extends ItemNode
	{
		/** @param obj The center to represent */
		public CenterListItem(PrismsCenter obj)
		{
			super(obj);
		}

		@Override
		protected void init()
		{
			RecordUtils.CenterStatus status;
			RecordKeeper keeper = getKeeper();
			if(keeper == null)
				status = null;
			else
				try
				{
					status = RecordUtils.getCenterStatus(getObject(), getKeeper());
				} catch(PrismsRecordException e)
				{
					log.error("Could not get status for center " + getObject(), e);
					status = null;
				}
			prisms.records.RecordUtils.CenterStatus preStatus = theCurrentCheckingStatus;
			theCurrentCheckingStatus = status;
			try
			{
				super.init();
			} finally
			{
				theCurrentCheckingStatus = preStatus;
			}
		}

		@Override
		public boolean check()
		{
			RecordUtils.CenterStatus status;
			RecordKeeper keeper = getKeeper();
			if(keeper == null)
				status = null;
			else
				try
				{
					status = RecordUtils.getCenterStatus(getObject(), getKeeper());
				} catch(PrismsRecordException e)
				{
					log.error("Could not get status for center " + getObject(), e);
					status = null;
				}
			prisms.records.RecordUtils.CenterStatus preStatus = theCurrentCheckingStatus;
			theCurrentCheckingStatus = status;
			try
			{
				return super.check();
			} finally
			{
				theCurrentCheckingStatus = preStatus;
			}
		}
	}
}
