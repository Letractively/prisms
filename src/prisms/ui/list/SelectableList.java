/**
 * AreaList.java Created Jan 29, 2008 by Andrew Butler, PSL
 */
package prisms.ui.list;

import java.awt.Color;

import prisms.arch.event.PrismsEvent;

/**
 * An abstract list UI implementation that represents a list where the user can select one or more
 * of the items, changing their appearance and calling listeners
 * 
 * @param <T> The type of value that this list holds
 */
public abstract class SelectableList<T> extends prisms.ui.list.DataListMgrPlugin
{
	/** The default background color for selected items */
	public static final Color DEFAULT_SELECTED_COLOR = new Color(176, 176, 255);

	boolean publicActions;

	boolean displaySelectedOnly;

	private Color theActiveBackground;

	private Color theActiveForeground;

	private Color theActiveOnlyBackground;

	private Color theActiveOnlyForeground;

	boolean theSelectionLock;

	boolean compareByIdentity = true;

	/** Creates a selectable list */
	public SelectableList()
	{
		theActiveBackground = DEFAULT_SELECTED_COLOR;
		theActiveForeground = new Color(0, 0, 0);
		theActiveOnlyBackground = theActiveBackground;
		theActiveOnlyForeground = Color.darkGray;
		displaySelectedOnly = true;
	}

	/** @return Whether items in this list generate events to allow for externally-generated actions */
	public boolean isPublicActions()
	{
		return publicActions;
	}

	/**
	 * @param pa Whether items in this list should generate events to allow for externally-generated
	 *        actions
	 */
	public void setPublicActions(boolean pa)
	{
		publicActions = pa;
	}

	/**
	 * @return Whether this list is displaying objects that are in the selected set but not in the
	 *         default set that is always displayed
	 */
	public boolean isDisplayingSelectedOnly()
	{
		return displaySelectedOnly;
	}

	/**
	 * @param d Whether this list should display objects that are in the selected set but not in the
	 *        default set that is always displayed
	 */
	public void setDisplaySelectedOnly(boolean d)
	{
		displaySelectedOnly = d;
	}

	/**
	 * Sets the kind of comparison this list does to its objects
	 * 
	 * @param c Whether this list should compare its objects by identity (==) or .equals()
	 */
	public void setCompareByIdentity(boolean c)
	{
		compareByIdentity = c;
	}

	/** @return The background color displayed for selected items */
	public Color getActiveBackground()
	{
		return theActiveBackground;
	}

	/** @param bg The background color to be displayed for selected items */
	public void setActiveBackground(Color bg)
	{
		theActiveBackground = bg;
	}

	/**
	 * @return The background color displayed for items that are only in the selected set and not in
	 *         the default set
	 */
	public Color getActiveOnlyBackground()
	{
		return theActiveOnlyBackground;
	}

	/**
	 * @param bg The background color to be displayed for items that are only in the selected set
	 *        and not in the default set
	 */
	public void setActiveOnlyBackground(Color bg)
	{
		theActiveOnlyBackground = bg;
	}

	/** @return The foreground color displayed for selected items */
	public Color getActiveForeground()
	{
		return theActiveForeground;
	}

	/** @param fg The foreground color to be displayed for selected items */
	public void setActiveForeground(Color fg)
	{
		theActiveForeground = fg;
	}

	/**
	 * @return The foreground color displayed for items that are only in the selected set and not in
	 *         the default set
	 */
	public Color getActiveOnlyForeground()
	{
		return theActiveOnlyForeground;
	}

	/**
	 * @param fg The foreground color to be displayed for items that are only in the selected set
	 *        and not in the default set
	 */
	public void setActiveOnlyForeground(Color fg)
	{
		theActiveOnlyForeground = fg;
	}

	@Override
	public abstract String getTitle();

	@Override
	public abstract String getIcon();

	/**
	 * Sets the set of items that are always displayed
	 * 
	 * @param items The available set of items
	 */
	public void setListData(T [] items)
	{
		DataListNode [] nodes = new DataListNode [getItemCount()];
		for(int i = 0; i < nodes.length; i++)
			nodes[i] = getItem(i);
		prisms.util.ArrayUtils.adjust(nodes, items,
			new prisms.util.ArrayUtils.DifferenceListener<DataListNode, T>()
			{
				public boolean identity(DataListNode o1, T o2)
				{
					if(!(o1 instanceof SelectableList<?>.ItemNode))
						return false;
					return ((ItemNode) o1).getObject() == o2;
				}

				public DataListNode added(T o, int index, int retIdx)
				{
					ItemNode newNode = createObjectNode(o);
					addNode(newNode, retIdx);
					return newNode;
				}

				public DataListNode removed(DataListNode o, int index, int incMod, int retIdx)
				{
					if(!(o instanceof SelectableList<?>.ItemNode))
						return o;
					if(((ItemNode) o).isSelectedOnly())
						return o;
					if(o.isSelected() && displaySelectedOnly)
					{
						((ItemNode) o).setSelectedOnly(true);
						return o;
					}
					removeNode(incMod);
					return null;
				}

				public DataListNode set(DataListNode o1, int idx1, int incMod, T o2, int idx2,
					int retIdx)
				{
					if(incMod != retIdx)
						moveNode(incMod, retIdx);
					return o1;
				}
			});
	}

	/**
	 * Sets the set of selected items
	 * 
	 * @param items The set of selected items
	 */
	public void setSelectedObjects(T [] items)
	{
		java.util.ArrayList<ItemNode> newSelected;
		newSelected = new java.util.ArrayList<ItemNode>();
		for(int i = 0; i < getItemCount(); i++)
		{
			if(!(getItem(i) instanceof SelectableList<?>.ItemNode))
				continue;
			boolean selected;
			if(compareByIdentity)
				selected = prisms.util.ArrayUtils.containsID(items,
					((ItemNode) getItem(i)).getObject());
			else
				selected = prisms.util.ArrayUtils.contains(items,
					((ItemNode) getItem(i)).getObject());
			if(selected)
				newSelected.add((ItemNode) getItem(i));
			else if(((ItemNode) getItem(i)).isSelectedOnly())
			{
				removeNode(i);
				i--;
			}
		}
		for(int a = 0; a < items.length; a++)
		{
			if(items[a] == null)
				continue;
			int i;
			for(i = 0; i < getItemCount(); i++)
				if(getItem(i) instanceof SelectableList<?>.ItemNode)
				{
					if(compareByIdentity && ((ItemNode) getItem(i)).getObject() == items[a])
						break;
					else if(!compareByIdentity
						&& ((ItemNode) getItem(i)).getObject().equals(items[a]))
						break;
				}
			if(i == getItemCount())
			{
				if(displaySelectedOnly)
				{
					ItemNode newNode = createObjectNode(items[a]);
					addNode(newNode, getItemCount());
					newSelected.add(newNode);
					newNode.setSelectedOnly(true);
				}
				else
				{
					items = prisms.util.ArrayUtils.remove(items, a);
					a--;
				}
			}
		}
		theSelectionLock = true;
		try
		{
			setSelection(newSelected.toArray(new SelectableList.ItemNode [0]), false);
		} finally
		{
			theSelectionLock = false;
		}
	}

	/**
	 * Gets the items that are selected in this list
	 * 
	 * @param array The array to use to put the objects in, or to get the type from if it is not big
	 *        enough
	 * @return The selected objects in this list
	 */
	public T [] getSelectedObjects(T [] array)
	{
		java.util.ArrayList<T> ret = new java.util.ArrayList<T>();
		for(int i = 0; i < getItemCount(); i++)
		{
			DataListNode node = getItem(i);
			if(node instanceof SelectableList<?>.ItemNode && ((ItemNode) node).isSelected())
				ret.add(((ItemNode) node).getObject());
		}
		return ret.toArray(array);
	}

	/**
	 * Creates a new list node for the given list item
	 * 
	 * @param item The item to create a node for
	 * @return A list node representing the object
	 */
	public ItemNode createObjectNode(T item)
	{
		return new ItemNode(item);
	}

	/**
	 * @param item The item to be selected
	 * @return Whether the given item can be selected
	 */
	public abstract boolean canSelect(T item);

	/**
	 * Selects the given item
	 * 
	 * @param item The item to select
	 */
	public abstract void doSelect(T item);

	/**
	 * @param item The item to be deselected
	 * @return Whether the given item can be deselected
	 */
	public abstract boolean canDeselect(T item);

	/**
	 * Deselects the given item
	 * 
	 * @param item The item to deselect
	 */
	public abstract void doDeselect(T item);

	/**
	 * @param item The item to get the display text for
	 * @return The display text for the given item
	 */
	public abstract String getItemName(T item);

	/**
	 * @param item The item to get the description for
	 * @return The description for the given item
	 */
	public String getItemDescrip(T item)
	{
		return null;
	}

	/**
	 * @param item The item to get the icon for
	 * @return The icon for the given item
	 */
	public abstract String getItemIcon(T item);

	/** A list node representing an item in this list */
	protected class ItemNode extends prisms.ui.list.SimpleListPluginNode
	{
		private T theObject;

		private final String theID;

		private String theName;

		private String theIcon;

		private String theDescrip;

		private Color theBackground;

		private Color theForeground;

		private boolean selectedOnly;

		/** @param obj The object to represent by this node */
		public ItemNode(T obj)
		{
			super(SelectableList.this, publicActions);
			theObject = obj;
			theID = Integer.toHexString(hashCode());
			init();
		}

		/** Initializes this item's display properties */
		protected void init()
		{
			theName = getItemName(getObject());
			theDescrip = getItemDescrip(getObject());
			theIcon = getItemIcon(getObject());
			theBackground = getBackground();
			theForeground = getForeground();
		}

		@Override
		public void userSetSelected(boolean selected)
		{
			if(selected)
			{
				if(!canSelect(theObject))
					return;
				super.userSetSelected(selected);
				if(!theSelectionLock)
					doSelect(theObject);
			}
			else
			{
				if(!canDeselect(theObject))
					return;
				super.userSetSelected(selected);
				if(!theSelectionLock)
					doDeselect(theObject);
			}
			nodeChanged(this);
		}

		/**
		 * @return Whether this item only exists in the selected list and not in the default list of
		 *         data that is always dsisplayed
		 */
		public boolean isSelectedOnly()
		{
			return selectedOnly;
		}

		/**
		 * @param selOnly Whether this item only exists in the selected list and not in the default
		 *        list of data that is always dsisplayed
		 */
		public void setSelectedOnly(boolean selOnly)
		{
			selectedOnly = selOnly;
		}

		/** @return The object that this node represents */
		public T getObject()
		{
			return theObject;
		}

		public String getID()
		{
			return theID;
		}

		public String getText()
		{
			return theName;
		}

		public String getDescription()
		{
			return theDescrip;
		}

		/**
		 * Checks whether this node's data has changed and needs to be re-represented to the client
		 * 
		 * @return Whether this item has been changed
		 */
		public boolean check()
		{
			boolean modified = false;
			if(!theName.equals(getItemName(getObject())))
			{
				theName = getItemName(getObject());
				modified = true;
			}
			if(theDescrip == null ? getItemDescrip(getObject()) == null : !theIcon
				.equals(getItemDescrip(getObject())))
			{
				theDescrip = getItemDescrip(getObject());
				modified = true;
			}
			if(theIcon == null ? getItemIcon(getObject()) == null : !theIcon
				.equals(getItemIcon(getObject())))
			{
				theIcon = getItemIcon(getObject());
				modified = true;
			}
			if(theBackground == null ? getBackground() == null : !theBackground
				.equals(getBackground()))
			{
				theBackground = getBackground();
				modified = true;
			}
			if(theForeground == null ? getForeground() == null : !theForeground
				.equals(getForeground()))
			{
				theForeground = getForeground();
				modified = true;
			}
			if(modified)
				nodeChanged(this);
			return modified;
		}

		public Color getBackground()
		{
			if(selectedOnly)
				return getActiveOnlyBackground();
			else if(isSelected())
				return getActiveBackground();
			else
				return Color.white;
		}

		public Color getForeground()
		{
			if(selectedOnly)
				return getActiveOnlyForeground();
			else if(isSelected())
				return getActiveForeground();
			else
				return Color.black;
		}

		public String getIcon()
		{
			return theIcon;
		}

		@Override
		protected void addEventProperties(PrismsEvent evt)
		{
			super.addEventProperties(evt);
			evt.setProperty("item", theObject);
		}
	}
}
