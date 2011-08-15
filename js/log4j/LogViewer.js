
__dojo.require("prisms.widget.FillPane");
__dojo.require("dijit.layout.ContentPane");
__dojo.require("log4j.LogEntry");

__dojo.provide("log4j.LogViewer");
__dojo.declare("log4j.LogViewer", [prisms.widget.FillPane, __dijit._Templated], {

	templatePath: "__webContentRoot/view/log4j/templates/logViewer.html",

	widgetsInTemplate: true,

	pluginName: "No pluginName specified",

	expanded: true,

	stackExpanded: false,

	showIDs: false,

	postCreate: function(){
		this.inherited(arguments);
		this.entries=[];
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
		this.stackCheck.setAttribute("checked", this.stackExpanded);
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
		else if(event.method=="setCount")
		{
			this.pageSize=event.page;
			this.start=event.start;
			this.count=event.count;
			var end=this.start+this.pageSize;
			if(end>this.count)
				end=this.count;
			if(this.start>0)
			{
				var preCount=this.pageSize;
				if(preCount>this.start)
					preCount=this.start;
				this.pageBackText.innerHTML="&#9650;"+this.start+" - "+end+" of "+this.count
					+". Display previous "+preCount+" entries&#9650;";
			}
			else
				this.pageBackText.innerHTML=this.start+" - "+end+" of "+this.count;
			if(end<this.count)
			{
				var postCount=this.count-this.start-this.pageSize;
				if(postCount>this.pageSize)
					postCount=this.pageSize;
				this.pageForwardText.innerHTML="&#9660;"+this.start+" - "+end+" of "+this.count
					+". Display next "+postCount+" entries&#9660;";
			}
			else
				this.pageForwardText.innerHTML=this.start+" - "+end+" of "+this.count;
			
			this.pageBack.style.display="block";
			this.pageForward.style.display="block";
			this._scrolled();
		}
		else if(event.method=="addEntries")
		{
			for(var i=0;i<event.entries.length;i++)
			{
				var widget=new log4j.LogEntry({});
				this.domNode.insertBefore(widget.domNode, this.pageForward);
				widget.settings=this.settings;
				widget.expanded=this.expandCheck.getValue() ? true : false;
				widget.stackExpanded=this.stackCheck.getValue() ? true : false;
				widget.setValue(event.entries[i]);
				this.entries.push(widget);
				while(this.entries.length>this.pageSize)
				{
					this.entries[this.entries.length-1].remove();
					this.entries.splice(this.entries.length-1, 1);
				}
			}
		}
		else if(event.method=="addNewEntries")
		{
			var preNode=null;
			if(this.entries.length>0)
				preNode=this.entries[0].domNode;
			else
				preNode=this.pageForward;
			for(var i=0;i<event.entries.length;i++)
			{
				var widget=new log4j.LogEntry({});
				this.domNode.insertBefore(widget.domNode, preNode);
				widget.settings=this.settings;
				widget.expanded=this.expandCheck.getValue() ? true : false;
				widget.stackExpanded=this.stackCheck.getValue() ? true : false;
				widget.setValue(event.entries[i]);
				this.entries.splice(0, 0, widget);
			}
		}
		else if(event.method=="remove")
		{
			for(var i=0;i<this.entries.length;i++)
				if(this.entries[i].entry.id==event.entryID)
				{
					this.entries[i].remove();
					this.entries.splice(i, 1);
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
		this.pageBack.style.display="none";
		this.pageForward.style.display="none";
		for(var i=0;i<this.entries.length;i++)
			this.entries[i].remove();
		this.entries.length=0;
	},

	_showMenu: function(){
		this.menu.style.display="inline";
		this.menuIcon.style.display="none";
		this._scrolled();
		this.wrapCheck.domNode.focus();
	},

	_hideMenu: function(event){
		if(event.target!=this.menu && event.target!=this.menuTable)
			return;
		this.menu.style.display="none";
		this.menuIcon.style.display="inline";
		this._scrolled();
	},

	_scrolled: function(){
		this.menu.style.top=(parseInt(this.domNode.scrollTop)+5)+"px"
		this.menuIcon.style.top=(parseInt(this.domNode.scrollTop)+5)+"px"
		var x=this.domNode.clientWidth+this.domNode.scrollLeft;
		this.menu.style.left=(x-this.menu.clientWidth-5)+"px";
		this.menuIcon.style.left=(x-this.menuIcon.clientWidth-5)+"px";
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
		for(var i=0;i<this.entries.length;i++)
		{
			this.entries[i].expanded=expanded;
			this.entries[i].render();
		}
	},

	_stackChecked: function(){
		var expanded=this.stackCheck.getValue() ? true : false;
		for(var i=0;i<this.entries.length;i++)
		{
			this.entries[i].stackExpanded=expanded;
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

	_scrollBack: function(){
		if(this.start==0)
			return;
		this.prisms.callApp(this.pluginName, "previous");
	},

	_scrollForward: function(){
		if(this.start+this.pageSize>=this.count)
			return;
		this.prisms.callApp(this.pluginName, "next");
	}
});
