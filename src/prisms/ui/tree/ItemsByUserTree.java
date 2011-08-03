/*
 * ItemsByUserTree.java Created Aug 1, 2011 by Andrew Butler, PSL
 */
package prisms.ui.tree;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map.Entry;

import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsEvent;
import prisms.records.PrismsCenter;
import prisms.records.RecordUser;
import prisms.util.ArrayUtils;

/**
 * Displays a set of items, each of which belongs or is associated with a particular user. The
 * display is organized by center and user, similarly to {@link ItemsByCenterTree}.
 * 
 * @param <T> The type of item that is displayed by this tree
 */
public abstract class ItemsByUserTree<T> extends prisms.ui.tree.DataTreeMgrPlugin
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
						&& ((IBUTCenterNode) node).getCenter() != null
						&& ((IBUTCenterNode) node).getCenter().equals(evt.getProperty("center")))
						((IBUTCenterNode) node).changed(false);
			}
		});
		session.addEventListener("createItemClicked", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				ActionTreeNode node = (ActionTreeNode) evt.getProperty("node");
				if(node.getManager() != ItemsByUserTree.this)
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
	 * @param item The item to get the user for
	 * @return The user associated with the given item
	 */
	public abstract RecordUser getUser(T item);

	/** @return All centers that are represented in the application */
	protected abstract PrismsCenter [] getCenters();

	/** @return All items for this tree to represent */
	protected abstract T [] getItems();

	/** @return The title (text for the root node) for this tree */
	protected abstract String getTitle();

	/** @return The icon to display for this tree's root and for each item in the set */
	protected abstract String getIcon();

	/**
	 * Determines whether this session's user can view the given item. In particular, this may be
	 * adjusted so that some users only see locally created items.
	 * 
	 * @param item The item to determine whether the user can view
	 * @return Whether this session's user has permission to view the given item
	 */
	protected abstract boolean canView(T item);

	/**
	 * @return The text to display if the user of this session has permission to create new items,
	 *         or null if the user doesn't have this permission
	 */
	protected abstract String canCreate();

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
		String canCreate = canCreate();
		if(canCreate != null
			&& (getRoot().getChildren().length == 0 || !(getRoot().getChildren()[0] instanceof ActionTreeNode)))
		{
			ActionTreeNode atn = new ActionTreeNode(ItemsByUserTree.this, getRoot(),
				"createItemClicked");
			atn.setText(canCreate);
			atn.setIcon(getIcon());
			((CategoryNode) getRoot()).add(atn, 0);
		}
		else if(canCreate == null && getRoot().getChildren().length > 0
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
					centerID = prisms.arch.ds.IDGenerator.getCenterID(getUser(item).getID());
				else if(multipleCenters)
				{}
				else if(centerID != prisms.arch.ds.IDGenerator.getCenterID(getUser(item).getID()))
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
		itemChanged(getRoot(), item);
	}

	private boolean itemChanged(DataTreeNode node, T item)
	{
		if(item instanceof ItemsByUserTree.IBUTItemNode
			&& ((IBUTItemNode) node).getItem().equals(item))
		{
			((IBUTItemNode) node).changed(true);
			return true;
		}
		for(DataTreeNode subNode : node.getChildren())
			if(itemChanged(subNode, item))
				return true;
		return false;
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
			if(node instanceof ItemsByUserTree.IBUTItemNode
				&& ArrayUtils.contains(items, ((IBUTItemNode) node).getItem()))
			{
				selection = ArrayUtils.add(selection, node);
				break;
			}
			else if(node instanceof ItemsByUserTree.IBUTCenterNode)
			{
				for(DataTreeNode subNode : node.getChildren())
					if(subNode instanceof ItemsByUserTree.IBUTItemNode
						&& ArrayUtils.contains(items, ((IBUTItemNode) subNode).getItem()))
						selection = ArrayUtils.add(selection, subNode);
			}
		}
		setSelection(selection, false);
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

	void setContent(final AbstractSimpleTreeNode parent, HashMap<RecordUser, T []> items,
		final boolean withEvents)
	{
		Entry<RecordUser, T []> [] entries = items.entrySet().toArray(new Entry [items.size()]);
		java.util.Arrays.sort(entries, new java.util.Comparator<Entry<RecordUser, T []>>()
		{
			public int compare(Entry<RecordUser, T []> o1, Entry<RecordUser, T []> o2)
			{
				if(o1.getKey().equals(o2.getKey()))
					return 0;
				if(o1.getKey().equals(getUser()))
					return -1;
				if(o2.getKey().equals(getUser()))
					return 1;
				return o1.getKey().getName().compareToIgnoreCase(o2.getKey().getName());
			}
		});
		DataTreeNode [] newCh = ArrayUtils.adjust(parent.getChildren(), entries,
			new ArrayUtils.DifferenceListener<DataTreeNode, Entry<RecordUser, T []>>()
			{
				public boolean identity(DataTreeNode o1, Entry<RecordUser, T []> o2)
				{
					return o1 instanceof ItemsByUserTree.IBUTUserNode
						&& ((IBUTUserNode) o1).getUser().equals(o2.getKey());
				}

				public DataTreeNode added(Entry<RecordUser, T []> o, int mIdx, int retIdx)
				{
					IBUTUserNode ret = createUserNode(parent, o.getKey());
					if(withEvents)
						parent.add(ret, retIdx);
					return ret;
				}

				public DataTreeNode removed(DataTreeNode o, int oIdx, int incMod, int retIdx)
				{
					if(o instanceof ActionTreeNode)
						return o;
					if(withEvents)
						parent.remove(incMod);
					return null;
				}

				public DataTreeNode set(DataTreeNode o1, int idx1, int incMod,
					Entry<RecordUser, T []> o2, int idx2, int retIdx)
				{
					if(incMod != retIdx)
						parent.move(incMod, retIdx);
					((IBUTUserNode) o1).setUser(o2.getKey());
					setContent((IBUTUserNode) o1, o2.getValue(), withEvents);
					return o1;
				}
			});
		if(!withEvents)
			parent.setChildren(newCh);
	}

	void setContent(final AbstractSimpleTreeNode parent, T [] items, final boolean withEvents)
	{
		DataTreeNode [] newCh = ArrayUtils.adjust(parent.getChildren(), items,
			new ArrayUtils.DifferenceListener<DataTreeNode, T>()
			{
				public boolean identity(DataTreeNode o1, T o2)
				{
					return o1 instanceof ItemsByUserTree.IBUTItemNode
						&& ((IBUTItemNode) o1).getItem() == o2;
				}

				public DataTreeNode added(T o, int mIdx, int retIdx)
				{
					IBUTItemNode ret = createItemNode(parent, o);
					if(withEvents)
						parent.add(ret, retIdx);
					return ret;
				}

				public DataTreeNode removed(DataTreeNode o, int oIdx, int incMod, int retIdx)
				{
					if(o instanceof ActionTreeNode)
						return o;
					if(withEvents)
						parent.remove(incMod);
					return null;
				}

				public DataTreeNode set(DataTreeNode o1, int idx1, int incMod, T o2, int idx2,
					int retIdx)
				{
					if(incMod != retIdx)
						parent.move(incMod, retIdx);
					return o1;
				}
			});
		if(!withEvents)
			parent.setChildren(newCh);
	}

	private void setItemsByCenter(T [] items)
	{
		HashMap<Integer, HashMap<RecordUser, T []>> centers = new HashMap<Integer, HashMap<RecordUser, T []>>();
		for(T item : items)
		{
			Integer centerKey = Integer.valueOf(prisms.arch.ds.IDGenerator
				.getCenterID(getUser(item).getID()));
			HashMap<RecordUser, T []> centerItems = centers.get(centerKey);
			if(centerItems == null)
			{
				centerItems = new HashMap<RecordUser, T []>();
				centers.put(centerKey, centerItems);
			}
			RecordUser userKey = getUser(item);
			T [] userItems = centerItems.get(userKey);
			if(userItems == null)
			{
				userItems = (T []) java.lang.reflect.Array.newInstance(items.getClass()
					.getComponentType(), 1);
				userItems[0] = item;
			}
			else
				userItems = ArrayUtils.add(userItems, item);
			centerItems.put(userKey, userItems);
		}
		Entry<Integer, HashMap<RecordUser, T []>> [] entries = centers.entrySet().toArray(
			new Entry [centers.size()]);
		final int centerID = prisms.arch.ds.IDGenerator.getCenterID(getUser().getID());
		java.util.Arrays.sort(entries,
			new java.util.Comparator<Entry<Integer, HashMap<RecordUser, T []>>>()
			{
				public int compare(Entry<Integer, HashMap<RecordUser, T []>> o1,
					Entry<Integer, HashMap<RecordUser, T []>> o2)
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
		ArrayUtils
			.adjust(
				root.getChildren(),
				entries,
				new ArrayUtils.DifferenceListener<DataTreeNode, Entry<Integer, HashMap<RecordUser, T []>>>()
				{
					public boolean identity(DataTreeNode o1,
						Entry<Integer, HashMap<RecordUser, T []>> o2)
					{
						return o1 instanceof ItemsByUserTree.IBUTCenterNode
							&& ((IBUTCenterNode) o1).getCenterID() == o2.getKey().intValue();
					}

					public DataTreeNode added(Entry<Integer, HashMap<RecordUser, T []>> o,
						int mIdx, int retIdx)
					{
						IBUTCenterNode ret = createCenterNode(getCenter(o.getKey().intValue()), o
							.getKey().intValue());
						setContent(ret, o.getValue(), false);
						root.add(ret, retIdx);
						return ret;
					}

					public DataTreeNode removed(DataTreeNode o, int oIdx, int incMod, int retIdx)
					{
						if(o instanceof ActionTreeNode)
							return o;
						root.remove(incMod);
						return null;
					}

					public DataTreeNode set(DataTreeNode o1, int idx1, int incMod,
						Entry<Integer, HashMap<RecordUser, T []>> o2, int idx2, int retIdx)
					{
						final IBUTCenterNode centerNode = (IBUTCenterNode) o1;
						centerNode.setCenter(getCenter(o2.getKey().intValue()));
						if(incMod != retIdx)
							root.move(incMod, retIdx);
						setContent(centerNode, o2.getValue(), true);
						return o1;
					}
				});
	}

	private void setLocalItems(T [] items)
	{
		HashMap<RecordUser, T []> users = new HashMap<RecordUser, T []>();
		for(T item : items)
		{
			RecordUser userKey = getUser(item);
			T [] userItems = users.get(userKey);
			if(userItems == null)
			{
				userItems = (T []) java.lang.reflect.Array.newInstance(items.getClass()
					.getComponentType(), 1);
				userItems[0] = item;
			}
			else
				userItems = ArrayUtils.add(userItems, item);
			users.put(userKey, userItems);
		}
		Entry<RecordUser, T []> [] entries = users.entrySet().toArray(new Entry [users.size()]);
		java.util.Arrays.sort(entries, new java.util.Comparator<Entry<RecordUser, T []>>()
		{
			public int compare(Entry<RecordUser, T []> o1, Entry<RecordUser, T []> o2)
			{
				if(o1.getKey().equals(o2.getKey()))
					return 0;
				if(o1.getKey().equals(getUser()))
					return -1;
				if(o2.getKey().equals(getUser()))
					return 1;
				return o1.getKey().getName().compareToIgnoreCase(o2.getKey().getName());
			}
		});
		final prisms.ui.tree.AbstractSimpleTreeNode root = (prisms.ui.tree.AbstractSimpleTreeNode) getRoot();
		ArrayUtils.adjust(root.getChildren(), entries,
			new ArrayUtils.DifferenceListener<DataTreeNode, Entry<RecordUser, T []>>()
			{
				public boolean identity(DataTreeNode o1, Entry<RecordUser, T []> o2)
				{
					return o1 instanceof ItemsByUserTree.IBUTUserNode
						&& ((IBUTUserNode) o1).getUser().equals(o2.getKey());
				}

				public DataTreeNode added(Entry<RecordUser, T []> o, int mIdx, int retIdx)
				{
					IBUTUserNode ret = createUserNode(root, o.getKey());
					setContent(ret, o.getValue(), false);
					root.add(ret, retIdx);
					return ret;
				}

				public DataTreeNode removed(DataTreeNode o, int oIdx, int incMod, int retIdx)
				{
					if(o instanceof ActionTreeNode)
						return o;
					root.remove(incMod);
					return null;
				}

				public DataTreeNode set(DataTreeNode o1, int idx1, int incMod,
					Entry<RecordUser, T []> o2, int idx2, int retIdx)
				{
					final IBUTUserNode userNode = (IBUTUserNode) o1;
					userNode.setUser(o2.getKey());
					if(incMod != retIdx)
						root.move(incMod, retIdx);
					setContent(userNode, o2.getValue(), true);
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
				if(node instanceof ItemsByUserTree.IBUTItemNode)
					sel.add(((IBUTItemNode) node).getItem());
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
	protected ItemsByUserTree<T>.IBUTCenterNode createCenterNode(PrismsCenter center, int centerID)
	{
		return new IBUTCenterNode(center, centerID);
	}

	/**
	 * Creates a node to represent a user
	 * 
	 * @param parent The parent node for the new node
	 * @param user The user to represent
	 * @return The node to represent the center
	 */
	protected ItemsByUserTree<T>.IBUTUserNode createUserNode(DataTreeNode parent, RecordUser user)
	{
		return new IBUTUserNode(parent, user);
	}

	/**
	 * Creates a node to represent an item in the tree
	 * 
	 * @param parent The parent node for the new node
	 * @param item The item to represent
	 * @return The node to represent the item in the tree
	 */
	protected ItemsByUserTree<T>.IBUTItemNode createItemNode(DataTreeNode parent, T item)
	{
		return new IBUTItemNode(parent, item);
	}

	static java.awt.Color PARTIAL_SELECTED = new java.awt.Color(210, 210, 255);

	/**
	 * Represents a center in the tree, beneath which users belonging to the center are represented,
	 * with items belonging to each user under those
	 */
	public class IBUTCenterNode extends SimpleTreePluginNode
	{
		private PrismsCenter theCenter;

		private int theCenterID;

		IBUTCenterNode(PrismsCenter center, int centerID)
		{
			super(ItemsByUserTree.this, getRoot(), false);
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

		public Color getBackground()
		{
			for(DataTreeNode node : getChildren())
				for(DataTreeNode subNode : node.getChildren())
					if(subNode.isSelected())
						return PARTIAL_SELECTED;
			return Color.white;
		}

		public Color getForeground()
		{
			return Color.black;
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

	/** Represents a user in the tree, beneath which items belonging to the user are represented */
	public class IBUTUserNode extends SimpleTreePluginNode
	{
		private RecordUser theUser;

		IBUTUserNode(DataTreeNode parent, RecordUser user)
		{
			super(ItemsByUserTree.this, parent, false);
			theUser = user;
		}

		/** @return The user that this node represents. */
		public RecordUser getUser()
		{
			return theUser;
		}

		void setUser(RecordUser user)
		{
			theUser = user;
			changed(false);
		}

		public String getText()
		{
			return theUser.getName();
		}

		public Color getBackground()
		{
			for(DataTreeNode node : getChildren())
				if(node.isSelected())
					return PARTIAL_SELECTED;
			return Color.white;
		}

		public Color getForeground()
		{
			return Color.black;
		}

		public String getIcon()
		{
			return "prisms/user";
		}

		public String getDescription()
		{
			return "";
		}
	}

	/** Represents an item in the tree */
	public class IBUTItemNode extends SimpleTreePluginNode
	{
		private T theItem;

		IBUTItemNode(DataTreeNode parent, T item)
		{
			super(ItemsByUserTree.this, parent, true);
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
			return ItemsByUserTree.this.getIcon();
		}

		public Color getBackground()
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
