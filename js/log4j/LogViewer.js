
__dojo.require("prisms.widget.FillPane");
__dojo.require("dijit.layout.ContentPane");
__dojo.require("log4j.LogEntry");

__dojo.provide("log4j.LogViewer");
__dojo.declare("log4j.LogViewer", [prisms.widget.FillPane, __dijit._Templated], {

	templatePath: "__webContentRoot/view/log4j/templates/logViewer.html",

	widgetsInTemplate: true,

	pluginName: "No pluginName specified",

	expanded: true,

	dupExpanded: false,

	stackExpanded: false,

	dupStackExpanded: false,

	showIDs: false,

	postCreate: function(){
		this.inherited(arguments);
		this.entries=[];
		this.connects=[];
		this.settings={
			id: false,
			time: true,
			instance: false,
			app: false,
			client: false,
			user: true,
			logger: true,
			tracking: false,
			showDuplicate: true
		};
		this.wrapCheck.setAttribute("checked", true);
		this.expandCheck.setAttribute("checked", this.expanded);
		this.dupExpandCheck.setAttribute("disabled", !this.expanded)
		this.dupExpandCheck.setAttribute("checked", this.expanded && this.dupExpanded);
		this.stackCheck.setAttribute("checked", this.stackExpanded);
		this.dupStackCheck.setAttribute("disabled", !this.stackExpanded);
		this.dupStackCheck.setAttribute("checked", this.stackExpanded && this.dupStackExpanded);
		this.idsCheck.setAttribute("checked", this.showIDs);
		this._scrolled();

		this.pageSize=250;
		this.start=0;
		this.count=0;
	},

	resize: function(){
		this.inherited(arguments);
		this._scrolled();
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		prisms.loadPlugin(this);
	},

	processEvent: function(event){
		if(event.method=="clear")
			this.clear();
		else if(event.method=="setSelectable")
		{
			this.selectable=event.selectable;
			PrismsUtils.setTableRowVisible(this.purgeRow, this.selectable);
			PrismsUtils.setTableRowVisible(this.protectRow, this.selectable);
			PrismsUtils.setTableRowVisible(this.selectAllRow, this.selectable);
		}
		else if(event.method=="setCount")
		{
			this.pageSize=event.page;
			this.start=event.start;
			this.count=event.count;
			var end=this.start+this.pageSize;
			if(end>this.count)
				end=this.count;
			if(this.start<end)
				this.statusDisplay.innerHTML=(this.start+1)+" - "+end+" of "+this.count;
			else if(this.count>1)
				this.statusDisplay.innerHTML=(this.start+1)+" of "+this.count;
			else
				this.statusDisplay.innerHTML="No Entries Selected";
			this.statusDisplay.style.display="inline";
			if(this.start>this.pageSize)
				this.pageFirst.style.display="inline"
			else
				this.pageFirst.style.display="none";
			if(this.start>0)
			{
				this.pageBack.style.display="inline";
				var preCount=this.pageSize;
				if(preCount>this.start)
					preCount=this.start;
				this.pageBack.innerHTML="&lt;Previous "+preCount;
			}
			else
				this.pageBack.style.display="none";
			if(end<this.count-this.pageSize)
				this.pageLast.style.display="inline";
			else
				this.pageLast.style.display="none";
			if(end<this.count)
			{
				this.pageForward.style.display="inline";
				var postCount=this.count-this.start-this.pageSize;
				if(postCount>this.pageSize)
					postCount=this.pageSize;
				this.pageForward.innerHTML="Next "+postCount+"&gt;";
			}
			else
				this.pageForward.style.display="none";
			
			this._scrolled();
		}
		else if(event.method=="addEntries")
		{
			for(var i=0;i<event.entries.length;i++)
				this._addEntry(-1, event.entries[i]);
		}
		else if(event.method=="addNewEntries")
		{
			var preNode=null;
			if(this.entries.length>0)
				preNode=this.entries[0].domNode;
			for(var i=0;i<event.entries.length;i++)
				this._addEntry(i, event.entries[i]);

			while(this.entries.length>this.pageSize)
				this._removeEntry(this.entries.length-1);
		}
		else if(event.method=="remove")
		{
			for(var i=0;i<this.entries.length;i++)
				if(this.entries[i].entry.id==event.entryID)
				{
					this._removeEntry(i);
					break;
				}
		}
		else if(event.method=="checkBack")
		{
			var self=this;
			window.setTimeout(function(){
				self.prisms.callApp(self.pluginName, "checkBack");
			}, 1000);
		}
		else
			throw new Error("Unrecognized "+this.pluginName+" event: "+event.method);
	},

	shutdown: function(){
		this.clear();
	},

	clear: function(){
		this.statusDisplay.style.display="none";
		this.pageFirst.style.display="none";
		this.pageBack.style.display="none";
		this.pageForward.style.display="none";
		this.pageLast.style.display="none";
		while(this.entries.length>0)
			this._removeEntry(this.entries.length-1);
	},

	_toggleShowMenu: function(){
		this._menuDisplayed=this._menuDisplayed ? false : true;
		if(this._menuDisplayed)
		{
			this.menu.style.display="inline";
			this.menuIcon.src="__webContentRoot/rsrc/icons/prisms/collapseNode.png";
			this._scrolled();
		}
		else
		{
			this.menu.style.display="none";
			this.menuIcon.src="__webContentRoot/rsrc/icons/prisms/expandNode.png";
		}
	},

	_scrolled: function(){
		this.menuBar.style.top=this.domNode.scrollTop+"px";
		this.menuBar.style.left=this.domNode.scrollLeft+"px";

		this.menuIcon.style.left=(this.domNode.clientWidth-this.menuIcon.clientWidth-3)+"px";
		this.menuIcon.style.top="3px";
		this.menu.style.left=(parseInt(this.menuIcon.style.left)-this.menu.clientWidth)+"px";
		this.menu.style.top=(parseInt(this.menuIcon.style.top)+this.menuIcon.clientHeight)+"px";

		this.pageFirst.style.left="5px";
		this.pageBack.style.left=(parseInt(this.pageFirst.style.left)+this.pageFirst.clientWidth+5)+"px";
		this.statusDisplay.style.left=Math.round((this.domNode.clientWidth-this.statusDisplay.clientWidth)/2)+"px";
		this.pageLast.style.left=(parseInt(this.menuIcon.style.left)-this.pageLast.clientWidth-5)+"px"
		this.pageForward.style.left=(parseInt(this.pageLast.style.left)-this.pageForward.clientWidth-5)+"px";
	},

	_wrapChecked: function(){
		var wrapped=this.wrapCheck.getValue() ? true : false;
		if(wrapped)
		{
			this.domNode.style.overflowX="hidden";
			this.domNode.style.whiteSpace="normal";
		}
		else
		{
			this.domNode.style.overflowX="auto";
			this.domNode.style.whiteSpace="nowrap";
		}
	},

	_expandChecked: function(){
		var expanded=this.expandCheck.getValue() ? true : false;
		this.dupExpandCheck.setAttribute("disabled", !expanded);
		this.dupExpandLabel.style.color=expanded ? "black" : "gray";
		if(!expanded)
			this.dupExpandCheck.setAttribute("checked", false);
		for(var i=0;i<this.entries.length;i++)
		{
			this.entries[i].expanded=expanded;
			if(!expanded)
				this.entries[i].dupExpanded=false;
			this.entries[i].render();
		}
	},

	_dupExpandChecked: function(){
		var expanded=this.dupExpandCheck.getValue() ? true : false;
		for(var i=0;i<this.entries.length;i++)
		{
			this.entries[i].dupExpanded=expanded;
			this.entries[i].render();
		}
	},

	_stackChecked: function(){
		var expanded=this.stackCheck.getValue() ? true : false;
		this.dupStackCheck.setAttribute("disabled", !expanded);
		this.dupStackLabel.style.color=expanded ? "black" : "gray";
		if(!expanded)
			this.dupStackCheck.setAttribute("checked", false);
		for(var i=0;i<this.entries.length;i++)
		{
			this.entries[i].stackExpanded=expanded;
			if(!expanded)
				this.entries[i].dupStackExpanded=false;
			this.entries[i].render();
		}
	},

	_dupStackChecked: function(){
		var expanded=this.dupStackCheck.getValue() ? true : false;
		for(var i=0;i<this.entries.length;i++)
		{
			this.entries[i].dupStackExpanded=expanded;
			this.entries[i].render();
		}
	},

	_idsChecked: function(){
		var checked=this.idsCheck.getValue() ? true : false;
		this.settings.id=checked;
		for(var i=0;i<this.entries.length;i++)
			this.entries[i].render();
	},

	_allMetaChecked: function(){
		var checked=this.allMetaCheck.getValue() ? true : false;
		this.settings.app=checked;
		this.settings.client=checked;
		this.settings.tracking=checked;
		for(var i=0;i<this.entries.length;i++)
			this.entries[i].render();
	},

	_saveClicked: function(){
		this.prisms.callApp(this.pluginName, "save", {settings: {
			expanded: this.expandCheck.getValue() ? true : false,
			dupExpanded: this.dupExpandCheck.getValue() ? true : false,
			stackExpanded: this.stackCheck.getValue() ? true : false,
			dupStackExpanded: this.dupStackCheck.getValue() ? true : false,
			ids: this.idsCheck.getValue() ? true : false,
			metaData: this.allMetaCheck.getValue() ? true : false
		}});
	},

	_purgeClicked: function(){
		this.prisms.callApp(this.pluginName, "purge");
	},

	_protectClicked: function(){
		this.prisms.callApp(this.pluginName, "protect");
	},

	_selectAllClicked: function(){
		this.prisms.callApp(this.pluginName, "selectAll");
	},

	_scrollFirst: function(){
		if(this.start==0)
			return;
		this.prisms.callApp(this.pluginName, "first");
	},

	_scrollBack: function(){
		if(this.start==0)
			return;
		this.prisms.callApp(this.pluginName, "previous");
	},

	_scrollForward: function(){
		if(this.start+this.pageSize>=this.count)
			return;
		this.prisms.callApp(this.pluginName, "next");
	},

	_scrollLast: function(){
		if(this.start+this.pageSize>=this.count)
			return;
		this.prisms.callApp(this.pluginName, "last");
	},

	_selected: function(entry, selected, event){
		this.prisms.callApp(this.pluginName, "setSelected", {entry: entry.id, selected: selected});
	},

	_addEntry: function(index, entry){
		var widget=new log4j.LogEntry({});
		var newConn=[__dojo.connect(widget, "selectChanged", this, function(event){
			this._selected(entry, entry.selected, event);
		})];
		if(index<0 || index>=this.entries.length)
		{
			this.domNode.appendChild(widget.domNode);
			this.entries.push(widget);
			this.connects.push(newConn);
		}
		else
		{
			this.domNode.insertBefore(widget.domNode, this.entries[index].domNode);
			this.entries.splice(index, 0, widget);
			this.connects.splice(index, 0, newConn);
		}
		widget.settings=this.settings;
		widget.selectable=this.selectable;
		widget.expanded=this.expandCheck.getValue() ? true : false;
		widget.dupExpanded=this.dupExpandCheck.getValue() ? true : false;
		widget.stackExpanded=this.stackCheck.getValue() ? true : false;
		widget.dupStackExpanded=this.dupStackCheck.getValue() ? true : false;
		widget.setValue(entry);
		return widget;
	},

	_removeEntry: function(index){
		for(var i=0;i<this.connects[index].length;i++)
			__dojo.disconnect(this.connects[index][i]);
		this.connects.splice(index, 1);
		this.entries[index].remove();
		this.entries.splice(index, 1);
	}
});
