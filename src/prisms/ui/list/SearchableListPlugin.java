/*
 * SearchableListPlugin.java Created May 11, 2010 by Andrew Butler, PSL
 */
package prisms.ui.list;

import org.json.simple.JSONObject;

import prisms.util.IntList;

/**
 * A subtype of {@link DataListMgrPlugin} that allows the user to filter the content by a string
 * 
 * @param <T> The type of the items in the list
 */
public abstract class SearchableListPlugin<T> extends SelectableList<T>
{
	private String theFilter;

	private String thePlaceholder;

	private volatile int theFilterNumber;

	private IntList theDisplayed;

	@Override
	public void initPlugin(prisms.arch.PrismsSession session, prisms.arch.PrismsConfig config)
	{
		super.initPlugin(session, config);
		if(getSelectionMode() == SelectionMode.NONE)
			setSelectionMode(SelectionMode.SINGLE);
		if(thePlaceholder == null)
			thePlaceholder = "Type to filter...";
	}

	@Override
	public void initClient()
	{
		super.initClient();
		sendDisplay();
	}

	@Override
	protected JSONObject getListParams()
	{
		JSONObject ret = super.getListParams();
		ret.put("filter", theFilter);
		ret.put("placeHolder", thePlaceholder);
		return ret;
	}

	@Override
	public void processEvent(JSONObject evt)
	{
		if("setFilter".equals(evt.get("method")))
			setFilter((String) evt.get("filter"));
		else
			super.processEvent(evt);
	}

	@Override
	public void setListData(T [] items)
	{
		theDisplayed = null;
		super.setListData(items);
		filterChanged();
	}

	@Override
	public void setItems(DataListNode [] items)
	{
		theDisplayed = null;
		super.setItems(items);
		filterChanged();
	}

	@Override
	public void addNode(DataListNode node, int index)
	{
		theDisplayed = null;
		super.addNode(node, index);
		filterChanged();
	}

	@Override
	public void nodeChanged(DataListNode node)
	{
		super.nodeChanged(node);
		filterChanged();
	}

	@Override
	public void removeNode(int index)
	{
		theDisplayed = null;
		super.removeNode(index);
		if(theDisplayed != null)
		{
			IntList newDisp = theDisplayed.clone();
			newDisp.removeAll(index);
			for(int i = 0; i < newDisp.size(); i++)
				if(newDisp.get(i) > index)
					newDisp.set(i, newDisp.get(i) - 1);
			newDisp.seal();
			theDisplayed = newDisp;
		}
		sendDisplay();
	}

	/**
	 * @return The placeholder string that shows up (greyed out) when the user hasn't typed anything
	 *         in the search box
	 */
	public String getPlaceholder()
	{
		return thePlaceholder;
	}

	/**
	 * @param placeholder The placeholder string to show up (greyed out) when the user hasn't typed
	 *        anything in the search box
	 */
	public void setPlaceholder(String placeholder)
	{
		thePlaceholder = placeholder;
		setListParams();
	}

	/** @return The filter that the user has entered to search for items */
	public String getFilter()
	{
		return theFilter;
	}

	/**
	 * Allows subclasses or external code to dynamically change the filter text
	 * 
	 * @param filter The filter to use for the search criteria
	 */
	public void setFilter(String filter)
	{
		if(filter != null && filter.length() == 0)
			filter = null;
		theFilter = filter;
		filterChanged();
		setListParams();
	}

	/**
	 * @return The results of the filter--the indices of the matched items, ordered by how well each
	 *         item matches the filter
	 */
	public IntList getSearchResults()
	{
		return theDisplayed;
	}

	/**
	 * @param node The node to check
	 * @return Whether the given node is visible to the user via the search
	 */
	public boolean isVisible(DataListNode node)
	{
		for(int i = 0; i < getItemCount(); i++)
			if(getItem(i).equals(node))
				return theDisplayed == null || theDisplayed.contains(i);
		return false;
	}

	/**
	 * Called when anything happens that may change what is displayed to the user. This may be a
	 * change to the
	 */
	protected void filterChanged()
	{
		if(searchItems(theFilter))
			sendDisplay();
	}

	private boolean searchItems(String filter)
	{
		theFilterNumber++;
		int filterNumber = theFilterNumber;
		if(filter == null || filter.length() == 0)
		{
			theDisplayed = null;
			return true;
		}
		final IntList indices = new IntList();
		final prisms.util.FloatList ranks = new prisms.util.FloatList();
		String [] filters = filter.split("\\s+");
		for(int i = 0; i < getItemCount(); i++)
		{
			if(getItem(i) instanceof SelectableList<?>.ItemNode)
			{
				float r = 1;
				for(String f : filters)
				{
					if(filterNumber != theFilterNumber)
						return false;
					int fDist = getFilterDistance(((ItemNode) getItem(i)).getObject(), f);
					if(fDist < 0)
						r = 0;
					r *= 1.0f / fDist;
					if(r == 0)
						break;
				}
				if(r > 0)
				{
					indices.add(i);
					ranks.add(r);
				}
			}
			else
			{
				indices.add(i);
				ranks.add(Float.POSITIVE_INFINITY);
			}
		}
		prisms.util.ArrayUtils.sort(ranks.toObjectArray(),
			new prisms.util.ArrayUtils.SortListener<Float>()
			{
				public int compare(Float o1, Float o2)
				{
					if(o1.floatValue() > o2.floatValue())
						return -1;
					else if(o1.floatValue() < o2.floatValue())
						return 1;
					else
						return 0;
				}

				public void swapped(Float o1, int idx1, Float o2, int idx2)
				{
					indices.swap(idx1, idx2);
					ranks.swap(idx1, idx2);
				}
			});
		if(filterNumber != theFilterNumber)
			return false;
		indices.seal();
		theDisplayed = indices;
		return true;
	}

	/** Sends the items matching the filter (or all items if there is no filter) */
	protected void sendDisplay()
	{
		IntList disp = theDisplayed;
		org.json.simple.JSONArray ids = new org.json.simple.JSONArray();
		if(theFilter == null)
			for(int i = 0; i < getItemCount(); i++)
				ids.add(getItem(i).getID());
		else if(disp != null)
			for(int i = 0; i < disp.size(); i++)
				ids.add(getItem(disp.get(i)).getID());
		JSONObject evt = new JSONObject();
		evt.put("plugin", getName());
		evt.put("method", "setFilteredItems");
		evt.put("ids", ids);
		getSession().postOutgoingEvent(evt);
	}

	/**
	 * Gets the degree to which an item matches a filter. 0 is the best match, larger numbers mean a
	 * more distant match, negative numbers mean no match at all.
	 * 
	 * @param item The item to compare to the filter
	 * @param filter The filter to compare to the item
	 * @return The degree to which the item matches the filter.
	 */
	protected int getFilterDistance(T item, String filter)
	{
		if(item == null)
			return -1;
		return distance(filter.toLowerCase(), item.toString().toLowerCase());
	}

	/**
	 * Determines the distance between two strings
	 * 
	 * @param filter The filter to compare the text to
	 * @param text The text to compare with the filter
	 * @return The degree to which the text matches the filter. 0 means the strings are equivalent,
	 *         larger numbers mean a more distant match, negative means no match at all.
	 */
	public static int distance(String filter, String text)
	{
		if(filter == null)
			return 0;
		if(text == null)
			return -1;
		int closest = -1;
		for(int i = 0; i < text.length() - filter.length() + 1; i++)
		{
			if(!equal(filter, text, i))
				continue;
			int dist = 10;
			if(i == 0)
			{
				dist -= 5;
				if(filter.length() == text.length())
					dist -= 5;
				else if(Character.isWhitespace(text.charAt(filter.length())))
					dist -= 3;
				else if(isDivider(text.charAt(filter.length())))
					dist -= 2;
			}
			else if(Character.isWhitespace(text.charAt(i - 1)))
			{
				dist -= 3;
				if(i + filter.length() == text.length()
					|| Character.isWhitespace(text.charAt(i + filter.length())))
					dist -= 3;
				else if(isDivider(text.charAt(i + filter.length())))
					dist -= 1;
			}
			else if(isDivider(text.charAt(i - 1)))
			{
				dist -= 2;
				if(i + filter.length() == text.length()
					|| Character.isWhitespace(text.charAt(i + filter.length())))
					dist -= 3;
				else if(isDivider(text.charAt(i + filter.length())))
					dist -= 2;
			}
			else if(i + filter.length() == text.length()
				|| Character.isWhitespace(text.charAt(i + filter.length())))
				dist -= 3;
			else if(isDivider(text.charAt(i + filter.length())))
				dist -= 2;
			if(closest < 0 || dist < closest)
				closest = dist;
		}
		return closest;
	}

	private static boolean equal(String filter, String text, int idx)
	{
		for(int c = 0; c < filter.length(); c++)
			if(text.charAt(c + idx) != filter.charAt(c))
				return false;
		return true;
	}

	private static boolean isDivider(char c)
	{
		return c == '-' || c == '_' || c == '+' || c == '=' || c == '(' || c == ')' || c == '/'
			|| c == '.' || c == ',' || c == '<' || c == '>' || c == ':' || c == ';';
	}
}
