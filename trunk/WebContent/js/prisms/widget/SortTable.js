
dojo.provide("prisms.widget.SortTable");
dojo.declare("prisms.widget.SortTable", dijit._Widget, {

	sortAscIcon: "../rsrc/icons/prisms/sortAscIcon.png",

	sortDescIcon: "../rsrc/icons/prisms/sortDescIcon.png",

	postCreate: function(){
		this.inherited(arguments);
		this.dojoConnects=[];
		this.setupTable();
	},

	setupTable: function(){
		this.table=document.createElement("table");
		this.table.style.width="100%";
		this.table.border=1;
		this.domNode.appendChild(this.table);
		this.topSelectRow=this.table.insertRow(-1);
		this.headerRow=this.table.insertRow(-1);
		this.contentRows=[];
		this.bottomSelectRow=this.table.insertRow(-1);
		this.selectAllDispLinks=[];
		this.deselectAllDispLinks=[];
		this.selectAllLinks=[];
		this.deselectAllLinks=[];
		this.firstLinks=[];
		this.previousLinks=[];
		this.displayingCells=[];
		this.totalCountCells=[];
		this.pageBoxes=[];
		this.nextLinks=[];
		this.lastLinks=[];

		this.setupSelectRow(this.topSelectRow);
		this.setupSelectRow(this.bottomSelectRow);
	},

	setupSelectRow: function(row){
		var td=row.insertCell(0);
		var rowTable=document.createElement("table");
		td.appendChild(rowTable);
		rowTable.style.width="100%";
		var bigRow=rowTable.insertRow(0);
		var table=document.createElement("table");
		bigRow.insertCell(-1).appendChild(table);
		var tr=table.insertRow(0);
		td=tr.insertCell(-1);
		td.innerHTML="Select:";
		td=tr.insertCell(-1);
		td.style.width=5;

		td=tr.insertCell(-1);
		td.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\""
			+" title=\"Selects All Displayed Items\">All Displayed</a>";
		var link=td.childNodes[0];
		this.selectAllDispLinks.push(link);
		dojo.connect(link, "onclick", this, this._selectAllDisplayed);
		td=tr.insertCell(-1);
		td.innerHTML=", ";

		td=tr.insertCell(-1);
		td.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\""
			+" title=\"Deselects All Displayed Items\">None Displayed</a>";
		link=td.childNodes[0];
		this.deselectAllDispLinks.push(link);
		dojo.connect(link, "onclick", this, this._deselectAllDisplayed);
		td=tr.insertCell(-1);
		td.innerHTML=", ";

		td=tr.insertCell(-1);
		td.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\""
			+ " title=\"Selects All Items In Search, Even Those Not Displayed\">All</a>";
		link=td.childNodes[0];
		this.selectAllLinks.push(link);
		dojo.connect(link, "onclick", this, this._selectAll);
		td=tr.insertCell(-1);
		td.innerHTML=", ";

		td=tr.insertCell(-1);
		td.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\""
			+" title=\"Deselects All Items In Search, Even Those Not Displayed\">None</a>";
		link=td.childNodes[0];
		this.selectAllLinks.push(link);
		dojo.connect(link, "onclick", this, this._deselectAll);

		table=document.createElement("table");
		bigRow.insertCell(-1).appendChild(table);
		table.style.cssFloat="right";
		tr=table.insertRow(-1);

		td=tr.insertCell(-1);
		td.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\">&lt;&lt;First</a>";
		link=td.childNodes[0];
		this.firstLinks.push(link);
		dojo.connect(link, "onclick", this, this._firstClicked);

		td=tr.insertCell(-1);
		td.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\">&lt;Previous</a>";
		link=td.childNodes[0];
		this.previousLinks.push(link);
		dojo.connect(link, "onclick", this, this._previousClicked);

		td=tr.insertCell(-1);
		var select=document.createElement("select");
		this.pageBoxes.push(select);
		td.appendChild(select);
		dojo.connect(select, "onchange", this, function(){
			this._pageChanged(select.selectedIndex);
		});

		td=tr.insertCell(-1);
		td.style.textAlign="center";
		td.innerHTML="of";
		td.style.width=20;

		td=tr.insertCell(-1);
		td.style.fontWeight="bold";
		this.totalCountCells.push(td);

		td=tr.insertCell(-1);
		td.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\">Next&gt;</a>";
		link=td.childNodes[0];
		this.nextLinks.push(link);
		dojo.connect(link, "onclick", this, this._nextClicked);

		td=tr.insertCell(-1);
		td.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\">Last&gt;&gt;</a>";
		link=td.childNodes[0];
		this.lastLinks.push(link);
		dojo.connect(link, "onclick", this, this._lastClicked);
	},

	setData: function(data){
		this.dataLock=true;
		try{
			this.tableData=data;
			while(this.dojoConnects.length>0)
				dojo.disconnect(this.dojoConnects.pop());
			while(this.contentRows.length>0)
				this.table.deleteRow(this.contentRows.pop().rowIndex);
			while(this.headerRow.cells.length>0)
				this.headerRow.deleteCell(this.headerRow.cells.length-1);
			this.setMetadata(data.metadata);
			this.setColumns(data.columns);
			this.setContent(data.content);
		} finally{
			this.dataLock=false;
		}
	},

	/**
	 * @param metadata {
	 * 		start: The first entry in the current table data
	 * 		end: The last entry in the current table data
	 * 		count: The number of entries to be displayed per page (not always the same as end-start)
	 * 		total: The number of entries available to the table
	 * }
	 */
	setMetadata: function(metadata){
		for(var i=0;i<this.pageBoxes.length;i++)
			this._setRowMetadata(i, metadata);
	},

	_setRowMetadata: function(index, metadata){
		this.totalCountCells[index].innerHTML=metadata.total;
		while(this.pageBoxes[index].options.length>0)
			this.pageBoxes[index].remove(this.pageBoxes[index].options.length-1);
		for(var i=1;i<=metadata.total;i+=metadata.count)
		{
			var option=document.createElement("option");
			var max=i+metadata.count-1;
			if(max>metadata.total)
				max=metadata.total;
			if(i==max)
				option.text=i;
			else
				option.text=i+" - "+max;
			if(dojo.isIE > 6)
				this.pageBoxes[index].add(option, 0);
			else
				this.pageBoxes[index].add(option, null);
		}
		if(metadata.start%metadata.count!=1
			|| (metadata.end%metadata.count!=0 && metadata.end!=metadata.total))
		{
			var option=document.createElement("option");
			option.text=metadata.start+" - "+metadata.end;
			if(dojo.isIE > 6)
				this.pageBoxes[index].add(option, 0);
			else
				this.pageBoxes[index].add(option, null);
			this.pageBoxes.selectedIndex=this.pageBoxes.options.length-1;
		}
		else
			this.pageBoxes[index].selectedIndex=(metadata.start-1)/metadata.count;
		PrismsUtils.setTableCellVisible(this.firstLinks[index].parentNode, metadata.start>metadata.count+1);
		PrismsUtils.setTableCellVisible(this.previousLinks[index].parentNode, metadata.start>1);
		this.nextLinks[index].parentNode.style.visibility=(metadata.end<metadata.total) ? "visible" : "hidden";
		this.lastLinks[index].parentNode.style.visibility=(metadata.end<metadata.total-metadata.count) ? "visible" : "hidden";
	},

	setColumns: function(columns){
		this.topSelectRow.cells[0].colSpan=columns.length+1;
		this.bottomSelectRow.cells[0].colSpan=columns.length+1;
		this.headerRow.insertCell(0); // For the check boxes
		var c;
		for(c=0;c<columns.length;c++)
		{
			var newCell=this.headerRow.insertCell(c+1);
			newCell.align="center";
			newCell.appendChild(this._createColumnHeader(columns[c]));
		}
	},

	setContent: function(rows){
		for(var r=0;r<rows.length;r++)
		{
			var tr=this.table.insertRow(this.headerRow.rowIndex+this.contentRows.length+1);
			this.contentRows.push(tr);
			this._fillContentRow(tr, rows[r], r);
		}
	},

	_createColumnHeader: function(column){
		var text=document.createElement("span");
		text.style.fontWeight="bold";
		text.style.fontSize="large"
		text.innerHTML=column.label;
		if(column.sortable)
		{
			var cellTable=document.createElement("table");
			var tr=cellTable.insertRow(-1);
			var td=tr.insertCell(-1);
			td.appendChild(text);
			td=tr.insertCell(-1);
			var iconTable=document.createElement("table");
			td.appendChild(iconTable);
			var img=document.createElement("img");
			img.src=this.sortAscIcon;
			img.title="Sort Ascending";
			iconTable.insertRow(-1).insertCell(-1).appendChild(img);
			img.parentNode.style.fontSize="0px";
			this.dojoConnects.push(dojo.connect(img, "onclick", this, function(){
				this.sortOn(column.label, true);
			}));
			img=document.createElement("img");
			img.src=this.sortDescIcon;
			img.title="Sort Descending";
			iconTable.insertRow(-1).insertCell(-1).appendChild(img);
			img.parentNode.style.fontSize="0px";
			this.dojoConnects.push(dojo.connect(img, "onclick", this, function(){
				this.sortOn(column.label, false);
			}));
			return cellTable;
		}
		else
			return text;
	},

	_fillContentRow: function(tr, dataRow, rowIndex){
		var td=tr.insertCell(-1);
		td.innerHTML="<input type=\"checkbox\" />";
		var checkBox=td.childNodes[0];
		checkBox.checked=dataRow.selected ? "on" : null;
		checkBox.sortTable=this;
		this.dojoConnects.push(dojo.connect(checkBox, "onclick", checkBox, function(event){
			var idx=this.sortTable.tableData.metadata.start+rowIndex;
			var selected=this.checked ? true : false;
			this.sortTable._selectChanged(idx, selected, event.shiftKey);
		}));
		tr.selectCheck=checkBox;

		for(var c=0;c<dataRow.cells.length;c++)
		{
			td=tr.insertCell(-1);
			this._createContentCell(td, dataRow.cells[c]);
		}
	},

	_createContentCell: function(td, dataCell){
		if(dataCell==null)
			return;
		var labelCell;
		if(dataCell.linkID)
		{
			td.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\">"+dataCell.label+"</a>";
			labelCell=td.childNodes[0];
			this.dojoConnects.push(dojo.connect(labelCell, "onclick", this, function(){
				this.goToLink(dataCell.linkID);
			}));
			td.innerHTML="";
		}
		else
		{
			labelCell=document.createElement("span");
			labelCell.innerHTML=dataCell.label;
		}
		if(dataCell.tooltip)
			labelCell.title=dataCell.tooltip; //TODO Parse \n into <br />
		if(dataCell.style)
		{
			if(dataCell.style.bold)
				labelCell.style.fontWeight="bold";
			if(dataCell.style.bgColor)
				td.style.backgroundColor=dataCell.style.bgColor;
			if(dataCell.style.fontColor)
				labelCell.style.color=dataCell.style.fontColor;
		}
		if(dataCell.icon)
		{
			var img=document.createElement("img");
			if(typeof dataCell.icon=="string")
				img.src="../rsrc/icons/"+dataCell.icon+".png";
			else if(this.prisms)
				img.src=this.prisms.getDynamicImageSource(dataCell.icon.plugin, dataCell.icon.method,
					0, 0, 16, 16, 16, 16);
			else
				console.error("No prisms for dynamic image: "+dojo.toJson(dataCell.icon));
			var table=document.createElement("table");
			td.appendChild(table);
			tr=table.insertRow(0);
			tr.insertCell(0).appendChild(img);
			tr.insertCell(1).appendChild(labelCell);
		}
		else
			td.appendChild(labelCell);
	},

	_selectChanged: function(idx, selected, shift){
		if(shift && this.lastSelectedIdx)
		{
			var start=this.lastSelectedIdx;
			var end=idx;
			if(start>end)
			{
				end=start;
				start=idx;
			}
			for(var i=start;i<end;i++)
			{
				var rowIdx=i-this.tableData.metadata.start;
				if(rowIdx<0 || i>this.tableData.metadata.end)
					continue;
				this.table.rows[this.headerRow.rowIndex+1+rowIdx].selectCheck.checked=this.lastSelected;
			}
			this.selectChanged(start, end, this.lastSelected);
		}
		else
		{
			this.selectChanged(idx, idx, selected);
			this.lastSelectedIdx=idx;
			this.lastSelected=selected;
		}
	},

	_selectAllDisplayed: function(){
		for(var c=0;c<this.contentRows.length;c++)
			if(!this.contentRows[c].selectCheck.checked)
				this.contentRows[c].selectCheck.checked=true;
		this.selectChanged(this.tableData.metadata.start, this.tableData.metadata.end, true);
	},

	_deselectAllDisplayed: function(){
		for(var c=0;c<this.contentRows.length;c++)
			if(this.contentRows[c].selectCheck.checked)
				this.contentRows[c].selectCheck.checked=false;
		this.selectChanged(this.tableData.metadata.start, this.tableData.metadata.end, false);
	},

	_selectAll: function(){
		for(var c=0;c<this.contentRows.length;c++)
			if(!this.contentRows[c].selectCheck.checked)
				this.contentRows[c].selectCheck.checked=true;
		this.selectChanged(1, this.tableData.metadata.total, true);
	},

	_deselectAll: function(){
		for(var c=0;c<this.contentRows.length;c++)
			if(this.contentRows[c].selectCheck.checked)
				this.contentRows[c].selectCheck.checked=null;
		this.selectChanged(1, this.tableData.metadata.total, false);
	},

	_firstClicked: function(){
		this.navTo(1);
	},

	_previousClicked: function(){
		var start=this.tableData.metadata.start-this.tableData.metadata.count;
		if(start<1)
			start=1;
		this.navTo(start);
	},

	_nextClicked: function(){
		var start=this.tableData.metadata.start+this.tableData.metadata.count;
		if(start>this.tableData.metadata.total)
			start=this.tableData.metadata.total;
		this.navTo(start);
	},

	_lastClicked: function(){
		var start=Math.round(this.tableData.metadata.total/this.tableData.metadata.count)
			*this.tableData.metadata.count+1;
		this.navTo(start);
	},

	_pageChanged: function(page){
		if(this.dataLock)
			return;
		var start=this.tableData.metadata.count*page+1;
		if(start<1)
			start=1;
		else if(start>=this.tableData.metadata.total)
			start=this.tableData.metadata.total;
		this.navTo(start);
	},

	_refresh: function(){
		this.navTo(this.tableData.metadata.start);
	},

	navTo: function(index){
	},

	selectChanged: function(startRow, endRow, selected){
	},

	sortOn: function(/*string*/ columnLabel, /*boolean*/ ascending){
	},

	goToLink: function(linkID){
	}
});
