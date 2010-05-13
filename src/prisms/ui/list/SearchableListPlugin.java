/*
 * SearchableListPlugin.java Created May 11, 2010 by Andrew Butler, PSL
 */
package prisms.ui.list;

import org.json.simple.JSONObject;

/**
 * A subtype of {@link DataListMgrPlugin} that allows the user to filter the content by a string
 * 
 * @param <T> The type of the items in the list
 */
public abstract class SearchableListPlugin<T> extends SelectableList<T>
{
	private String theFilter;

	@Override
	public void initPlugin(prisms.arch.PrismsSession session, org.dom4j.Element pluginEl)
	{
		super.initPlugin(session, pluginEl);
		setSelectionMode(SelectionMode.SINGLE);
	}

	@Override
	public void initClient()
	{
		super.initClient();
		JSONObject evt = new JSONObject();
		evt.put("plugin", getName());
		evt.put("method", "setFilter");
		evt.put("filter", theFilter);
		getSession().postOutgoingEvent(evt);
		setFilter(theFilter);
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
		super.setListData(items);
		setFilter(theFilter);
	}

	@Override
	public void setItems(DataListNode [] items)
	{
		super.setItems(items);
		setFilter(theFilter);
	}

	@Override
	public void addNode(DataListNode node, int index)
	{
		super.addNode(node, index);
		setFilter(theFilter);
	}

	@Override
	public void removeNode(int index)
	{
		super.removeNode(index);
		setFilter(theFilter);
	}

	void setFilter(String filter)
	{
		if(filter != null && filter.length() == 0)
			filter = null;
		theFilter = filter;
		if(filter != null)
			filter = filter.toLowerCase();
		JSONObject evt = new JSONObject();
		evt.put("plugin", getName());
		evt.put("method", "setFilteredItems");
		if(filter == null || filter.length() == 0)
			evt.put("ids", null);
		else
		{
			final DataListNode [] items = new DataListNode [getItemCount()];
			for(int i = 0; i < items.length; i++)
				items[i] = getItem(i);
			Integer [] rank = new Integer [items.length];
			for(int i = 0; i < items.length; i++)
			{
				if(items[i] instanceof SelectableList.ItemNode)
					rank[i] = new Integer(getFilterDistance(((ItemNode) items[i]).getObject(),
						filter));
				else
					rank[i] = new Integer(0);
			}
			prisms.util.ArrayUtils.sort(rank, new prisms.util.ArrayUtils.SortListener<Integer>()
			{
				public int compare(Integer o1, Integer o2)
				{
					return o1.intValue() - o2.intValue();
				}

				public void swapped(Integer o1, int idx1, Integer o2, int idx2)
				{
					DataListNode temp = items[idx1];
					items[idx1] = items[idx2];
					items[idx2] = temp;
				}
			});
			org.json.simple.JSONArray ids = new org.json.simple.JSONArray();
			for(int i = 0; i < items.length; i++)
				if(rank[i].intValue() >= 0)
					ids.add(items[i].getID());
			evt.put("ids", ids);
		}
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
		return distance(filter, item.toString());
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
		text = text.toLowerCase();
		for(int i = 0; i < text.length() - filter.length() + 1; i++)
		{
			if(!equal(filter, text, i))
				continue;
			int ret = i;
			if(text.length() > filter.length() + 1)
				ret++;
			return ret;
		}
		return -1;
	}

	private static boolean equal(String filter, String text, int idx)
	{
		for(int c = 0; c < filter.length(); c++)
			if(text.charAt(c + idx) != filter.charAt(c))
				return false;
		return true;
	}
}
