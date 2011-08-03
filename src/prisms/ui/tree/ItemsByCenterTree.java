/*
 * ItemByCenterTree.java Created Oct 6, 2010 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import java.awt.Color;
import java.util.Map.Entry;

import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsEvent;
import prisms.records.PrismsCenter;
import prisms.util.ArrayUtils;

/**
 * Displays a set of items to the user, organizing them by the center they were created at.
 * 
 * @param <T> The type of item to display
 */
public abstract class ItemsByCenterTree<T> extends prisms.ui.tree.DataTreeMgrPlugin
{
	private T [] theTypeArray;

	@Override
	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig pluginEl)
	{
		super.initPlugin(session, pluginEl);
		setSelectionMode(SelectionMode.MULTIPLE);
		CategoryNode root = new CategoryNode(this, null, getTitle());
		root.setIcon(getIcon());
		setRoot(root);
		checkCreate();

		getSession().addEventListener("centerChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				for(DataTreeNode node : getRoot().getChildren())
					if(node instanceof ItemsByCenterTree.IBCTCenterNode
						&& ((IBCTCenterNode) node).getCenter() != null
						&& ((IBCTCenterNode) node).getCenter().equals(evt.getProperty("center")))
						((IBCTCenterNode) node).changed(false);
			}
		});
		session.addEventListener("createItemClicked", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				ActionTreeNode node = (ActionTreeNode) evt.getProperty("node");
				if(node.getManager() != ItemsByCenterTree.this)
					return;
				createItem();
			}
		});
		setItems(getItems());
	}

	/** @return The record user corresponding to this session */
	public prisms.records.RecordUser getUser()
	{
		return getSession().getUser();
	}

	/**
	 * @param item The item to get the center ID of
	 * @return The ID of the center at which the given item was created
	 */
	public abstract int getCenterID(T item);

	/** @return All centers that are represented in the application */
	protected abstract PrismsCenter [] getCenters();

	/** @return All items for this tree to represent */
	protected abstract T [] getItems();

	/** @return Whether the user of this session has permission to create new items */
	protected abstract boolean canCreate();

	/**
	 * Determines whether this session's user can view the given item. In particular, this may be
	 * adjusted so that some users only see locally created items.
	 * 
	 * @param item The item to determine whether the user can view
	 * @return Whether this session's user has permission to view the given item
	 */
	protected abstract boolean canView(T item);

	/** @return The string to display to the user that they can choose to create a new item */
	protected abstract String getCreateString();

	/** @return The title (text for the root node) for this tree */
	protected abstract String getTitle();

	/** @return The icon to display for this tree's root and for each item in the set */
	protected abstract String getIcon();

	/**
	 * Creates a new item into the set. {@link #setItems(Object[])} must be used after the item is
	 * created to make the new item show up in the tree.
	 */
	protected abstract void createItem();

	/**
	 * @param item The item to represent
	 * @return The text to represent the item on its tree node
	 */
	protected abstract String getItemText(T item);

	/**
	 * @param item The item to describe
	 * @return The text to display for the item's description
	 */
	protected String getItemDescrip(T item)
	{
		return "";
	}

	/**
	 * @param item The item to get the icon for
	 * @return The icon to display for the item
	 */
	protected String getItemIcon(T item)
	{
		return getIcon();
	}

	/**
	 * @param item The item to get the background for
	 * @param selected Whether the item has been selected by the user
	 * @return The background color to display for the item
	 */
	protected Color getItemBackground(T item, boolean selected)
	{
		if(selected)
			return prisms.ui.list.SelectableList.DEFAULT_SELECTED_COLOR;
		else
			return java.awt.Color.white;
	}

	/**
	 * @param item The item to get the foreground for
	 * @return The foreground color to display for the item
	 */
	protected Color getItemForeground(T item)
	{
		return Color.black;
	}

	/**
	 * A call back for when the user selects a set of items in the tree
	 * 
	 * @param items The items that were selected by the user
	 */
	protected abstract void selectionSet(T [] items);

	/** Refreshes all the data in this tree */
	protected void refreshValues()
	{
		checkCreate();
		setItems(getItems());
		((CategoryNode) getRoot()).changed(true);
	}

	/**
	 * Checks the tree to make sure it correctly represents whether the user has permission to
	 * create new items.
	 */
	protected final void checkCreate()
	{
		boolean canCreate = canCreate();
		if(canCreate
			&& (getRoot().getChildren().length == 0 || !(getRoot().getChildren()[0] instanceof ActionTreeNode)))
		{
			ActionTreeNode atn = new ActionTreeNode(ItemsByCenterTree.this, getRoot(),
				"createItemClicked");
			atn.setText(getCreateString());
			atn.setIcon(getIcon());
			((CategoryNode) getRoot()).add(atn, 0);
		}
		else if(!canCreate && getRoot().getChildren().length > 0
			&& getRoot().getChildren()[0] instanceof ActionTreeNode)
			((CategoryNode) getRoot()).remove(0);
	}

	/**
	 * @param items The set of items to display. These are further screened by
	 *        {@link #canView(Object)}
	 */
	public void setItems(T [] items)
	{
		if(theTypeArray == null)
			theTypeArray = (T []) java.lang.reflect.Array.newInstance(items.getClass()
				.getComponentType(), 0);
		java.util.ArrayList<T> toDisplay = new java.util.ArrayList<T>();
		int centerID = -1;
		boolean multipleCenters = false;
		for(T item : items)
			if(canView(item))
			{
				toDisplay.add(item);
				if(centerID < 0)
					centerID = getCenterID(item);
				else if(multipleCenters)
				{}
				else if(centerID != getCenterID(item))
					multipleCenters = true;
			}
		items = toDisplay.toArray(theTypeArray);
		if(multipleCenters)
			setItemsByCenter(items);
		else
			setLocalItems(items);
	}

	/**
	 * Changes an item's representation in this tree in response to a change in the item itself
	 * 
	 * @param item The item that has changed
	 */
	public void itemChanged(T item)
	{
		for(DataTreeNode node : getRoot().getChildren())
		{
			if(node instanceof ItemsByCenterTree.IBCTItemNode
				&& ((IBCTItemNode) node).getItem().equals(item))
			{
				((IBCTItemNode) node).changed(false);
				break;
			}
			else if(node instanceof ItemsByCenterTree.IBCTCenterNode)
			{
				for(DataTreeNode subNode : node.getChildren())
					if(subNode instanceof ItemsByCenterTree.IBCTItemNode
						&& ((IBCTItemNode) subNode).getItem().equals(item))
						((IBCTItemNode) subNode).changed(false);
			}
		}
	}

	/**
	 * Sets the items that are selected in this tree
	 * 
	 * @param items The items that are selected
	 */
	public void setSelection(T [] items)
	{
		DataTreeNode [] selection = new DataTreeNode [0];
		for(DataTreeNode node : getRoot().getChildren())
		{
			if(node instanceof ItemsByCenterTree.IBCTItemNode
				&& ArrayUtils.contains(items, ((IBCTItemNode) node).getItem()))
			{
				selection = ArrayUtils.add(selection, node);
				break;
			}
			else if(node instanceof ItemsByCenterTree.IBCTCenterNode)
			{
				for(DataTreeNode subNode : node.getChildren())
					if(subNode instanceof ItemsByCenterTree.IBCTItemNode
						&& ArrayUtils.contains(items, ((IBCTItemNode) subNode).getItem()))
						selection = ArrayUtils.add(selection, subNode);
			}
		}
		setSelection(selection, false);
	}

	private void setItemsByCenter(T [] items)
	{
		java.util.HashMap<Integer, T []> centers = new java.util.HashMap<Integer, T []>();
		for(T item : items)
		{
			Integer key = Integer.valueOf(getCenterID(item));
			T [] centerItems = centers.get(key);
			if(centerItems == null)
			{
				centerItems = (T []) java.lang.reflect.Array.newInstance(items.getClass()
					.getComponentType(), 1);
				centerItems[0] = item;
			}
			else
				centerItems = ArrayUtils.add(centerItems, item);
			centers.put(key, centerItems);
		}
		Entry<Integer, T []> [] entries = centers.entrySet().toArray(new Entry [centers.size()]);
		final int centerID = prisms.arch.ds.IDGenerator.getCenterID(getUser().getID());
		java.util.Arrays.sort(entries, new java.util.Comparator<Entry<Integer, T []>>()
		{
			public int compare(Entry<Integer, T []> o1, Entry<Integer, T []> o2)
			{
				if(o1.getKey().intValue() == centerID)
					return -1;
				if(o2.getKey().intValue() == centerID)
					return 1;
				PrismsCenter center1 = getCenter(o1.getKey().intValue());
				PrismsCenter center2 = getCenter(o2.getKey().intValue());
				if(center1 != null && center2 == null)
					return -1;
				else if(center2 != null && center1 == null)
					return 1;
				else if(center1 != null)
					return center1.getName().compareToIgnoreCase(center2.getName());
				else
					return 0;
			}
		});
		final prisms.ui.tree.AbstractSimpleTreeNode root = (prisms.ui.tree.AbstractSimpleTreeNode) getRoot();
		ArrayUtils.adjust(getRoot().getChildren(), entries,
			new ArrayUtils.DifferenceListener<DataTreeNode, Entry<Integer, T []>>()
			{
				public boolean identity(DataTreeNode o1, Entry<Integer, T []> o2)
				{
					return o1 instanceof ItemsByCenterTree.IBCTCenterNode
						&& ((IBCTCenterNode) o1).getCenterID() == o2.getKey().intValue();
				}

				public DataTreeNode added(Entry<Integer, T []> o, int mIdx, int retIdx)
				{
					IBCTCenterNode ret = createCenterNode(getCenter(o.getKey().intValue()), o
						.getKey().intValue());
					DataTreeNode [] children = new DataTreeNode [o.getValue().length];
					for(int i = 0; i < children.length; i++)
						children[i] = createItemNode(ret, o.getValue()[i]);
					ret.setChildren(children);
					root.add(ret, retIdx);
					return ret;
				}

				public DataTreeNode removed(DataTreeNode o, int oIdx, int incMod, int retIdx)
				{
					if(!(o instanceof ItemsByCenterTree.IBCTItemNode)
						&& !(o instanceof ItemsByCenterTree.IBCTCenterNode))
						return o;
					root.remove(incMod);
					return null;
				}

				public DataTreeNode set(DataTreeNode o1, int idx1, int incMod,
					Entry<Integer, T []> o2, int idx2, int retIdx)
				{
					final IBCTCenterNode centerNode = (IBCTCenterNode) o1;
					centerNode.setCenter(getCenter(o2.getKey().intValue()));
					if(incMod != retIdx)
						root.move(incMod, retIdx);
					ArrayUtils.adjust(o1.getChildren(), o2.getValue(),
						new ArrayUtils.DifferenceListener<DataTreeNode, T>()
						{
							public boolean identity(DataTreeNode _o1, T _o2)
							{
								return _o1 instanceof ItemsByCenterTree.IBCTItemNode
									&& ((IBCTItemNode) _o1).getItem().equals(_o2);
							}

							public DataTreeNode added(T o, int mIdx, int _retIdx)
							{
								IBCTItemNode ret = createItemNode(centerNode, o);
								centerNode.add(ret, _retIdx);
								return ret;
							}

							public DataTreeNode removed(DataTreeNode o, int oIdx, int _incMod,
								int _retIdx)
							{
								if(!(o instanceof ItemsByCenterTree.IBCTItemNode))
									return o;
								centerNode.remove(_incMod);
								return null;
							}

							public DataTreeNode set(DataTreeNode _o1, int _idx1, int _incMod,
								T _o2, int _idx2, int _retIdx)
							{
								if(_incMod != _retIdx)
									centerNode.move(_incMod, _retIdx);
								return _o1;
							}
						});
					return o1;
				}
			});
	}

	/**
	 * @param id The center ID of the center to get
	 * @return The center whose center ID is given, or null if there is no such center
	 */
	protected PrismsCenter getCenter(int id)
	{
		for(prisms.records.PrismsCenter center : getCenters())
			if(center.getCenterID() == id)
				return center;
		return null;
	}

	private void setLocalItems(T [] items)
	{
		ArrayUtils.adjust(getRoot().getChildren(), items,
			new ArrayUtils.DifferenceListener<DataTreeNode, T>()
			{
				public boolean identity(DataTreeNode o1, T o2)
				{
					return o1 instanceof ItemsByCenterTree.IBCTItemNode
						&& ((IBCTItemNode) o1).getItem().equals(o2);
				}

				public DataTreeNode added(T o, int idx, int retIdx)
				{
					IBCTItemNode ret = createItemNode(getRoot(), o);
					((CategoryNode) getRoot()).add(ret, retIdx);
					return ret;
				}

				public DataTreeNode removed(DataTreeNode o, int idx, int incMod, int retIdx)
				{
					if(!(o instanceof ItemsByCenterTree.IBCTItemNode)
						&& !(o instanceof ItemsByCenterTree.IBCTCenterNode))
						return o;
					((CategoryNode) getRoot()).remove(incMod);
					return null;
				}

				public DataTreeNode set(DataTreeNode o1, int idx1, int incMod, T o2, int idx2,
					int retIdx)
				{
					if(incMod != retIdx)
						((CategoryNode) getRoot()).move(incMod, retIdx);
					return o1;
				}
			});
	}

	@Override
	public synchronized void setSelection(DataTreeNode [] nodes, boolean fromUser)
	{
		super.setSelection(nodes, fromUser);
		if(!fromUser)
			return;
		checkCreate();
		if(nodes.length == 1 && nodes[0] instanceof ActionTreeNode)
		{/*Action node--don't adjust the selected item set*/}
		else
		{
			java.util.ArrayList<T> sel = new java.util.ArrayList<T>();
			for(DataTreeNode node : nodes)
				if(node instanceof ItemsByCenterTree.IBCTItemNode)
					sel.add(((IBCTItemNode) node).getItem());
			selectionSet(sel.toArray(theTypeArray));
		}
	}

	/**
	 * Creates a node to represent a center
	 * 
	 * @param center The center to represent--may be null if the center has not been defined in this
	 *        application
	 * @param centerID The ID of the center to represent
	 * @return The node to represent the center
	 */
	protected ItemsByCenterTree<T>.IBCTCenterNode createCenterNode(PrismsCenter center, int centerID)
	{
		return new IBCTCenterNode(center, centerID);
	}

	/**
	 * Creates a node to represent an item in the tree
	 * 
	 * @param parent The parent node for the new node
	 * @param item The item to represent
	 * @return The node to represent the item in the tree
	 */
	protected ItemsByCenterTree<T>.IBCTItemNode createItemNode(DataTreeNode parent, T item)
	{
		return new IBCTItemNode(parent, item);
	}

	static java.awt.Color PARTIAL_SELECTED = new java.awt.Color(210, 210, 255);

	/** Represents a center in the tree, beneath which items belonging to the center are reprsented */
	public class IBCTCenterNode extends prisms.ui.tree.SimpleTreePluginNode
	{
		private PrismsCenter theCenter;

		private int theCenterID;

		IBCTCenterNode(prisms.records.PrismsCenter center, int centerID)
		{
			super(ItemsByCenterTree.this, getRoot(), false);
			theCenter = center;
			theCenterID = centerID;
		}

		/** @return The center that this node represents. May be null. */
		public PrismsCenter getCenter()
		{
			return theCenter;
		}

		/** @return The ID of the center that this node represents. */
		public int getCenterID()
		{
			return theCenterID;
		}

		void setCenter(PrismsCenter center)
		{
			theCenter = center;
			changed(false);
		}

		public String getText()
		{
			if(theCenter != null)
				return theCenter.getName();
			else if(theCenterID == prisms.arch.ds.IDGenerator.getCenterID(getUser().getID()))
				return "Local";
			else
				return "Unknown (ID " + Integer.toHexString(theCenterID).toUpperCase() + ")";
		}

		public java.awt.Color getBackground()
		{
			for(DataTreeNode node : getChildren())
				if(node.isSelected())
					return PARTIAL_SELECTED;
			return java.awt.Color.white;
		}

		public java.awt.Color getForeground()
		{
			return java.awt.Color.black;
		}

		public String getIcon()
		{
			return "prisms/center";
		}

		public String getDescription()
		{
			return "";
		}
	}

	/** Represents an item in the tree */
	public class IBCTItemNode extends prisms.ui.tree.SimpleTreePluginNode
	{
		private T theItem;

		IBCTItemNode(DataTreeNode parent, T item)
		{
			super(ItemsByCenterTree.this, parent, true);
			setSelectionChangesNode(true);
			theItem = item;
			setSelectionChangesNode(true);
		}

		/** @return The item that this node represents */
		public T getItem()
		{
			return theItem;
		}

		public String getText()
		{
			return getItemText(theItem);
		}

		public String getIcon()
		{
			return ItemsByCenterTree.this.getIcon();
		}

		public java.awt.Color getBackground()
		{
			return getItemBackground(theItem, isSelected());
		}

		public Color getForeground()
		{
			return getItemForeground(theItem);
		}

		public String getDescription()
		{
			return getItemDescrip(theItem);
		}

		@Override
		protected void addEventProperties(PrismsEvent evt)
		{
			super.addEventProperties(evt);
			evt.setProperty("item", theItem);
		}
	}
}
