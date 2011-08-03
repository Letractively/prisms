/*
 * SearchableScaledList.java Created Jun 13, 2011 by Andrew Butler, PSL
 */
package prisms.ui.list;

import java.awt.Color;

import org.json.simple.JSONObject;

/**
 * A form of SearchableListPlugin that allows much better performance for very large lists of items.
 * This list only displays a set number of items at a time (the display count). Users must paginate
 * through the rest of the results. Items are not sent to the client until they are needed for
 * display and no items are displayed initially. This functionality can be canceled at run time
 * using the {@link #setUnscaled()} method.
 * 
 * @param <T> The type of item that the list represents
 */
public abstract class SearchableScaledList<T> extends SearchableListPlugin<T>
{
	private class NavNode extends SimpleListPluginNode
	{
		private final String theID;

		private final boolean isNext;

		private final boolean isAbs;

		NavNode(boolean next, boolean abs)
		{
			super(SearchableScaledList.this, false);
			theID = Integer.toHexString(hashCode());
			isNext = next;
			isAbs = abs;
		}

		public String getID()
		{
			return theID;
		}

		boolean isVisible(int total)
		{
			if(isNext)
			{
				if(isAbs)
					return getStart() + getDisplayCount() * 2 < total - 1;
				else
					return getStart() + getDisplayCount() < total - 1;
			}
			else
			{
				if(isAbs)
					return getStart() > getDisplayCount();
				else
					return getStart() > 0;
			}
		}

		public String getText()
		{
			int total = getMatchedItemCount();
			if(!isVisible(total))
				return "";
			StringBuilder ret = new StringBuilder();
			if(isNext)
			{
				if(isAbs)
					ret.append("Go To End");
				else
				{
					ret.append("Next ");
					ret.append(getDisplayCount());
					ret.append(" Items (");
					ret.append(getStart() + getDisplayCount() + 1);
					ret.append(" - ");
					int end = getStart() + getDisplayCount() * 2;
					if(end > total)
						end = total;
					ret.append(end);
					ret.append(" of ");
					ret.append(total);
					ret.append(')');
				}
			}
			else
			{
				if(isAbs)
					ret.append("Go To Beginning");
				else
				{
					ret.append("Previous ");
					ret.append(getDisplayCount());
					ret.append(" Items (");
					ret.append(getStart() - getDisplayCount() + 1);
					ret.append(" - ");
					ret.append(getStart());
					ret.append(" of ");
					ret.append(getMatchedItemCount());
					ret.append(')');
				}
			}
			return ret.toString();
		}

		public Color getBackground()
		{
			if(isVisible(getMatchedItemCount()))
				return Color.lightGray;
			else
				return Color.white;
		}

		public Color getForeground()
		{
			return Color.blue;
		}

		public String getIcon()
		{
			return null;
		}

		public String getDescription()
		{
			if(!isVisible(getMatchedItemCount()))
				return null;
			else if(isNext)
			{
				if(isAbs)
					return "Navigates to the end of the list";
				else
					return "Navigates to the next set of items in the list";
			}
			else
			{
				if(isAbs)
					return "Navigates to the beginning of the list";
				else
					return "Navigates to the previous set of items in the list";
			}
		}
	}

	private class DisplayAllNode extends SimpleListPluginNode
	{
		private final String theID;

		DisplayAllNode()
		{
			super(SearchableScaledList.this, false);
			theID = Integer.toHexString(hashCode());
		}

		public String getID()
		{
			return theID;
		}

		public String getText()
		{
			return "Display First " + getDisplayCount() + " Items";
		}

		public Color getBackground()
		{
			return Color.lightGray;
		}

		public Color getForeground()
		{
			return Color.blue;
		}

		public String getIcon()
		{
			return null;
		}

		public String getDescription()
		{
			return "Allows you to browse through all items in the list";
		}
	}

	private boolean isScaled = true;

	private boolean isDisplayingAll;

	private int theStart;

	private int theDisplayCount;

	private NavNode theBeginNode;

	private NavNode thePreviousNode;

	private NavNode theNextNode;

	private NavNode theEndNode;

	private DisplayAllNode theDAN;

	prisms.util.preferences.Preference<Integer> theDisplayCountPref;

	@Override
	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
		if(isScaled && config.get("scaled") != null)
			isScaled = config.is("scaled", true);
		if(!isScaled)
			return;
		clearListener();
		theBeginNode = new NavNode(false, true);
		thePreviousNode = new NavNode(false, false);
		theNextNode = new NavNode(true, false);
		theEndNode = new NavNode(true, true);
		theDAN = new DisplayAllNode();
		if(theDisplayCount == 0)
			theDisplayCount = 25;
		session.addEventListener("preferencesChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.PrismsSession session2,
				prisms.arch.event.PrismsEvent evt)
			{
				if(!(evt instanceof prisms.util.preferences.PreferenceEvent))
					return;
				prisms.util.preferences.PreferenceEvent pEvt = (prisms.util.preferences.PreferenceEvent) evt;
				if(pEvt.getPreference().equals(theDisplayCountPref))
				{
					setDisplayCount(((Integer) pEvt.getNewValue()).intValue());
				}
			}

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + "." + getName()
					+ " List Size Preference Applier";
			}
		});
	}

	@Override
	public void initClient()
	{
		if(!isScaled)
		{
			super.initClient();
			return;
		}
		prisms.arch.PrismsTransaction trans = getSession().getApp().getEnvironment()
			.getTransaction();
		if(trans != null
			&& trans.getStage().ordinal() < prisms.arch.PrismsTransaction.Stage.initSession
				.ordinal())
			return;

		JSONObject evt = new JSONObject();
		evt.put("plugin", getName());
		evt.put("method", "setSelectionMode");
		evt.put("selectionMode", getSelectionMode().toString().toLowerCase());
		getSession().postOutgoingEvent(evt);

		setListParams();

		sendDisplay();
	}

	@Override
	public void processEvent(JSONObject evt)
	{
		if(isScaled && "notifySelection".equals(evt.get("method")))
		{
			String [] ids = (String []) ((org.json.simple.JSONArray) evt.get("ids"))
				.toArray(new String [0]);
			if(prisms.util.ArrayUtils.contains(ids, thePreviousNode.getID()))
				previous();
			else if(prisms.util.ArrayUtils.contains(ids, theNextNode.getID()))
				next();
			else if(prisms.util.ArrayUtils.contains(ids, theBeginNode.getID()))
			{
				theStart = 0;
				sendDisplay();
			}
			else if(prisms.util.ArrayUtils.contains(ids, theEndNode.getID()))
			{
				int newStart;
				int total = getMatchedItemCount();
				if(total == 0)
					newStart = 0;
				else
				{
					newStart = total - getDisplayCount();
					newStart = (newStart / getDisplayCount() + 1) * getDisplayCount();
					while(newStart >= total)
						newStart -= getDisplayCount();
					if(newStart < 0)
						newStart = 0;
				}
				theStart = newStart;
				sendDisplay();
			}
			else if(prisms.util.ArrayUtils.contains(ids, theDAN.getID()))
				displayAll();
		}
		super.processEvent(evt);
	}

	/**
	 * Makes this scaled list act just like its unscaled super class. This should be called BEFORE
	 * {@link #initPlugin(prisms.arch.PrismsSession, prisms.arch.PrismsConfig)}.
	 */
	public void setUnscaled()
	{
		isScaled = false;
	}

	/** @return Whether this list is scaled or has been marked as unscaled */
	public boolean isScaled()
	{
		return isScaled;
	}

	/** @return The index within the search of the first item visible in the display */
	public int getStart()
	{
		return theStart;
	}

	/** @return The number of items that this list displays at a time (the page size) */
	public int getDisplayCount()
	{
		return theDisplayCount;
	}

	/** @param count The number of items that this list should display at a time (the page size) */
	public void setDisplayCount(int count)
	{
		theStart = 0;
		theDisplayCount = count;
		if(isScaled)
			sendDisplay();
	}

	/**
	 * Allows the user to control the display size of this list with a preference. This must be
	 * called after {@link #initPlugin(prisms.arch.PrismsSession, prisms.arch.PrismsConfig)}.
	 * 
	 * @param pref The preference to control the display count
	 */
	public void setDisplayCountPreference(prisms.util.preferences.Preference<Integer> pref)
	{
		if(!isScaled)
			return;
		theDisplayCountPref = pref;

		prisms.util.preferences.Preferences prefs = getSession().getPreferences();
		if(prefs.get(pref) == null)
			prefs.set(pref, Integer.valueOf(getDisplayCount()));
		int dc = prefs.get(pref).intValue();
		if(dc != getDisplayCount())
			setDisplayCount(dc);
	}

	int getMatchedItemCount()
	{
		prisms.util.IntList disp = getSearchResults();
		int total;
		if(disp != null)
			total = disp.size();
		else if(isDisplayingAll)
			total = getItemCount();
		else
			total = 0;
		return total;
	}

	/** Navigates to the next set of items */
	public void next()
	{
		int total = getMatchedItemCount();
		int newStart = theStart + theDisplayCount;
		if(newStart < total)
		{
			theStart = newStart;
			sendDisplay();
		}
	}

	/** Navigates to the previous set of items */
	public void previous()
	{
		int newStart = theStart - theDisplayCount;
		if(newStart >= 0)
		{
			theStart = newStart;
			sendDisplay();
		}
	}

	/** Displays all items in the list to the user */
	public void displayAll()
	{
		isDisplayingAll = true;
		theStart = 0;
		setFilter(null);
	}

	@Override
	public void setFilter(String filter)
	{
		if(filter != null)
			isDisplayingAll = false;
		theStart = 0;
		super.setFilter(filter);
	}

	@Override
	protected void filterChanged()
	{
		int total = getMatchedItemCount();
		if(total == 0)
			theStart = 0;
		else
		{
			while(theStart >= total)
				theStart -= theDisplayCount;
			if(theStart < 0)
				theStart = 0;
		}
		super.filterChanged();
	}

	@Override
	protected void sendDisplay()
	{
		if(!isScaled)
		{
			super.sendDisplay();
			return;
		}
		JSONObject evt = new JSONObject();
		evt.put("plugin", getName());
		evt.put("method", "setItems");
		prisms.util.IntList displayed = getSearchResults();
		org.json.simple.JSONArray items = new org.json.simple.JSONArray();
		evt.put("items", items);
		if(displayed != null)
		{
			items.add(serialize(theBeginNode));
			items.add(serialize(thePreviousNode));
			for(int i = theStart; i < displayed.size() && i < theStart + theDisplayCount; i++)
				items.add(serialize(getItem(displayed.get(i))));
			items.add(serialize(theNextNode));
			items.add(serialize(theEndNode));
		}
		else if(isDisplayingAll)
		{
			items.add(serialize(theBeginNode));
			items.add(serialize(thePreviousNode));
			for(int i = theStart; i < getItemCount() && i < theStart + theDisplayCount; i++)
				items.add(serialize(getItem(i)));
			items.add(serialize(theNextNode));
			items.add(serialize(theEndNode));
		}
		else
			items.add(serialize(theDAN));
		getSession().postOutgoingEvent(evt);
		if(displayed != null || isDisplayingAll)
		{
			evt = new JSONObject();
			evt.put("plugin", getName());
			evt.put("method", "changeItem");
			evt.put("item", serialize(theBeginNode));
			getSession().postOutgoingEvent(evt);
			evt = new JSONObject();
			evt.put("plugin", getName());
			evt.put("method", "changeItem");
			evt.put("item", serialize(thePreviousNode));
			getSession().postOutgoingEvent(evt);
			evt = new JSONObject();
			evt.put("plugin", getName());
			evt.put("method", "changeItem");
			evt.put("item", serialize(theNextNode));
			getSession().postOutgoingEvent(evt);
			evt = new JSONObject();
			evt.put("plugin", getName());
			evt.put("method", "changeItem");
			evt.put("item", serialize(theEndNode));
			getSession().postOutgoingEvent(evt);
		}
		else
		{
			evt = new JSONObject();
			evt.put("plugin", getName());
			evt.put("method", "changeItem");
			evt.put("item", serialize(theDAN));
			getSession().postOutgoingEvent(evt);
		}
	}
}
