/*
 * SortTableStructure.java Created Nov 13, 2009 by Andrew Butler, PSL
 */
package prisms.ui;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A structure that allows for easier construction of the data content of a SortTable widget
 */
public class SortTableStructure
{
	/**
	 * Represents a row in the table
	 */
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

		/**
		 * @param cellIdx The index of the cell to get
		 * @return The TableCell at the given index
		 */
		public TableCell cell(int cellIdx)
		{
			return theCells[cellIdx];
		}

		/**
		 * @return Whether this row is selected or not
		 */
		public boolean isSelected()
		{
			return isSelected;
		}

		/**
		 * @param s Whether this row is selected or not
		 */
		public void setSelected(boolean s)
		{
			isSelected = s;
		}

		/**
		 * Clears this row of all data (does not remove the rows)
		 */
		public void clear()
		{
			isSelected = false;
			for(TableCell cell : theCells)
				cell.clear();
		}

		/**
		 * @return An object representing this TableRow
		 */
		public JSONObject serialize()
		{
			JSONObject ret = new JSONObject();
			ret.put("selected", new Boolean(isSelected));
			ret.put("cells", serializeCells());
			return ret;
		}

		/**
		 * @return All cells in this row, serialized
		 */
		public JSONArray serializeCells()
		{
			JSONArray ret = new JSONArray();
			for(TableCell cell : theCells)
				ret.add(cell.serialize());
			return ret;
		}
	}

	/**
	 * Represents a cell in the table
	 */
	public class TableCell
	{
		private String theLabel;

		private JSONObject theLinkID;

		private Object theIcon;

		private boolean isBold;

		private java.awt.Color theBGColor;

		private java.awt.Color theFontColor;

		/**
		 * @return The text for this cell
		 */
		public String getLabel()
		{
			return theLabel;
		}

		/**
		 * @param label The text for this cell
		 */
		public void setLabel(String label)
		{
			if(label == null)
				label = "";
			theLabel = label;
		}

		/**
		 * @return The ID that will be passed to the server if this cell's link is clicked, or null
		 *         if this cell is not linkable
		 */
		public JSONObject getLinkID()
		{
			return theLinkID;
		}

		/**
		 * @param linkID The ID that should be passed to the server if this cell's link is clicked,
		 *        or null if this cell should not be linkable
		 */
		public void setLinkID(JSONObject linkID)
		{
			theLinkID = linkID;
		}

		/**
		 * @return The location of the icon to display in this cell, or null if this cell does not
		 *         display an icon
		 */
		public Object getIcon()
		{
			return theIcon;
		}

		/**
		 * @param icon The location of the icon to display in this cell, or null if this cell should
		 *        not display an icon
		 */
		public void setIcon(Object icon)
		{
			if(!(icon == null || icon instanceof String || icon instanceof JSONObject))
				throw new IllegalArgumentException("Cannot set object of type "
					+ icon.getClass().getName() + " as an icon");
			theIcon = icon;
		}

		/**
		 * @return Whether this cell will display its label in bold
		 */
		public boolean isBold()
		{
			return isBold;
		}

		/**
		 * @param bold Whether this cell should display its label in bold
		 */
		public void setBold(boolean bold)
		{
			isBold = bold;
		}

		/**
		 * @param color The color that should be displayed as the background for this cell
		 */
		public void setBGColor(java.awt.Color color)
		{
			theBGColor = color;
		}

		/**
		 * @param color The color that should be displayed as the font color for this cell
		 */
		public void setFontColor(java.awt.Color color)
		{
			theFontColor = color;
		}

		/**
		 * @param label The label that this cell should display
		 * @param linkID The ID that should be passed to the server if this cell's link is clicked,
		 *        or null if this cell should not be linkable
		 * @param icon The location of the icon to display in this cell, or null if this cell should
		 *        not display an icon
		 */
		public void set(String label, JSONObject linkID, Object icon)
		{
			theLabel = label;
			theLinkID = linkID;
			if(!(icon == null || icon instanceof String || icon instanceof JSONObject))
				throw new IllegalArgumentException("Cannot set object of type "
					+ icon.getClass().getName() + " as an icon");
			theIcon = icon;
		}

		/**
		 * Clears this cell's data
		 */
		public void clear()
		{
			theLabel = "";
			theLinkID = null;
			theIcon = null;
			isBold = false;
			theBGColor = null;
			theFontColor = null;
		}

		/**
		 * @return Serializes this cell in a form readable by the SortTable widget
		 */
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

	/**
	 * Creates a table structure
	 * 
	 * @param columns The number of columns to be in the table
	 */
	public SortTableStructure(int columns)
	{
		theColumnNames = new String [columns];
		theColumnSortables = new boolean [columns];
		theRows = new TableRow [0];
	}

	/**
	 * @param colIdx The index of the column to get the name of
	 * @return The name of the column at the given index
	 */
	public String getColumnName(int colIdx)
	{
		return theColumnNames[colIdx];
	}

	/**
	 * @param colIdx The index of the column to get the sortability of
	 * @return Whether table data can be sorted on the given column
	 */
	public boolean isSortable(int colIdx)
	{
		return theColumnSortables[colIdx];
	}

	/**
	 * @param colIdx The index of the column to set metadata for
	 * @param name The name for the column
	 * @param sortable whether the column should be sortable
	 */
	public void setColumn(int colIdx, String name, boolean sortable)
	{
		theColumnNames[colIdx] = name;
		theColumnSortables[colIdx] = sortable;
	}

	/**
	 * @return The number of rows in this table
	 */
	public int getRowCount()
	{
		return theRows.length;
	}

	/**
	 * @param count The number of rows that this table should display
	 */
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

	/**
	 * @param rowIdx The index of the row to get
	 * @return The TableRow at the given index
	 */
	public TableRow row(int rowIdx)
	{
		return theRows[rowIdx];
	}

	/**
	 * Serializes this structure into an object that the SortTable dojo widget can understand
	 * 
	 * @param start The row index of the first row of data stored in this table
	 * @param end The row index of the last row of data stored in this table
	 * @param count The number of rows that are configured to be displayed to the user when the
	 *        number of rows is not limited
	 * @param total The total number of rows that are accessible to the user
	 * @return The object to be passed as content to the SortTable widget
	 */
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

	/**
	 * @return The serialized column headers of this table
	 */
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

	/**
	 * @return The serialized data content of this table
	 */
	public JSONArray serializeContent()
	{
		JSONArray ret = new JSONArray();
		for(TableRow row : theRows)
			ret.add(row.serialize());
		return ret;
	}
}
