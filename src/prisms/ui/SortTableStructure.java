/*
 * SortTableStructure.java Created Nov 13, 2009 by Andrew Butler, PSL
 */
package prisms.ui;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class SortTableStructure
{
	public class TableRow
	{
		private boolean isSelected;

		private TableCell [] theCells;

		TableRow()
		{
			theCells = new TableCell [theColumnNames.length];
			for(int i = 0; i < theCells.length; i++)
				theCells[i] = new TableCell();
		}

		public TableCell cell(int cellIdx)
		{
			return theCells[cellIdx];
		}

		public boolean isSelected()
		{
			return isSelected;
		}

		public void setSelected(boolean s)
		{
			isSelected = s;
		}

		public void clear()
		{
			isSelected = false;
			for(TableCell cell : theCells)
				cell.clear();
		}

		public JSONObject serialize()
		{
			JSONObject ret = new JSONObject();
			ret.put("selected", new Boolean(isSelected));
			ret.put("cells", serializeCells());
			return ret;
		}

		public JSONArray serializeCells()
		{
			JSONArray ret = new JSONArray();
			for(TableCell cell : theCells)
				ret.add(cell.serialize());
			return ret;
		}
	}

	public class TableCell
	{
		private String theLabel;

		private JSONObject theLinkID;

		private Object theIcon;

		private boolean isBold;

		private java.awt.Color theBGColor;

		private java.awt.Color theFontColor;

		public String getLabel()
		{
			return theLabel;
		}

		public void setLabel(String label)
		{
			if(label == null)
				label = "";
			theLabel = label;
		}

		public JSONObject getLinkID()
		{
			return theLinkID;
		}

		public void setLinkID(JSONObject linkID)
		{
			theLinkID = linkID;
		}

		public Object getIcon()
		{
			return theIcon;
		}

		public void setIcon(Object icon)
		{
			if(!(icon == null || icon instanceof String || icon instanceof JSONObject))
				throw new IllegalArgumentException("Cannot set object of type "
					+ icon.getClass().getName() + " as an icon");
			theIcon = icon;
		}

		public boolean isBold()
		{
			return isBold;
		}

		public void setBold(boolean bold)
		{
			isBold = bold;
		}

		public void setBGColor(java.awt.Color color)
		{
			theBGColor = color;
		}

		public void setFontColor(java.awt.Color color)
		{
			theFontColor = color;
		}

		public void set(String label, JSONObject linkID, Object icon)
		{
			theLabel = label;
			theLinkID = linkID;
			if(!(icon == null || icon instanceof String || icon instanceof JSONObject))
				throw new IllegalArgumentException("Cannot set object of type "
					+ icon.getClass().getName() + " as an icon");
			theIcon = icon;
		}

		public void clear()
		{
			theLabel = "";
			theLinkID = null;
			theIcon = null;
			isBold = false;
			theBGColor = null;
			theFontColor = null;
		}

		public JSONObject serialize()
		{
			JSONObject ret = new JSONObject();
			ret.put("label", theLabel);
			if(theLinkID != null)
				ret.put("linkID", theLinkID);
			if(theIcon != null)
				ret.put("icon", theIcon);
			if(isBold || theBGColor != null || theFontColor != null)
			{
				JSONObject style = new JSONObject();
				ret.put("style", style);
				if(isBold)
					style.put("bold", Boolean.TRUE);
				if(theBGColor != null)
					style.put("bgColor", prisms.util.JsonUtils.toHTML(theBGColor));
				if(theFontColor != null)
					style.put("fontColor", prisms.util.JsonUtils.toHTML(theFontColor));
			}
			return ret;
		}
	}

	String [] theColumnNames;

	private boolean [] theColumnSortables;

	private TableRow [] theRows;

	public SortTableStructure(int columns)
	{
		theColumnNames = new String [columns];
		theColumnSortables = new boolean [columns];
		theRows = new TableRow [0];
	}

	public String getColumnName(int colIdx)
	{
		return theColumnNames[colIdx];
	}

	public boolean isSortable(int colIdx)
	{
		return theColumnSortables[colIdx];
	}

	public void setColumn(int colIdx, String name, boolean sortable)
	{
		theColumnNames[colIdx] = name;
		theColumnSortables[colIdx] = sortable;
	}

	public int getRowCount()
	{
		return theRows.length;
	}

	public void setRowCount(int count)
	{
		if(theRows.length == count)
			return;
		TableRow [] newRows = new TableRow [count];
		if(theRows.length > count)
			System.arraycopy(theRows, 0, newRows, 0, count);
		else
		{
			System.arraycopy(theRows, 0, newRows, 0, theRows.length);
			for(int i = theRows.length; i < count; i++)
				newRows[i] = new TableRow();
		}
		theRows = newRows;
	}

	public TableRow row(int rowIdx)
	{
		return theRows[rowIdx];
	}

	public JSONObject serialize(int start, int end, int count, int total)
	{
		JSONObject ret = new JSONObject();
		JSONObject metadata = new JSONObject();
		ret.put("metadata", metadata);
		metadata.put("start", new Integer(start));
		metadata.put("end", new Integer(end));
		metadata.put("count", new Integer(count));
		metadata.put("total", new Integer(total));
		ret.put("columns", serializeColumns());
		ret.put("content", serializeContent());
		return ret;
	}

	public JSONArray serializeColumns()
	{
		JSONArray ret = new JSONArray();
		for(int i = 0; i < theColumnNames.length; i++)
		{
			JSONObject col = new JSONObject();
			ret.add(col);
			col.put("label", theColumnNames[i]);
			col.put("sortable", new Boolean(theColumnSortables[i]));
		}
		return ret;
	}

	public JSONArray serializeContent()
	{
		JSONArray ret = new JSONArray();
		for(TableRow row : theRows)
			ret.add(row.serialize());
		return ret;
	}
}
