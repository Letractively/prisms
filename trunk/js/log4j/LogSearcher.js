
__dojo.require("prisms.widget.TabWidget");
__dojo.require("prisms.widget.CollapsePane");
__dojo.require("log4j.LogViewer");
__dojo.require("log4j.StoredSearches");

__dojo.provide("log4j.LogSearcher");
__dojo.declare("log4j.LogSearcher", [prisms.widget.TabWidget, __dijit._Templated], {

	templatePath: "__webContentRoot/view/log4j/templates/logSearcher.html",

	widgetsInTemplate: true,

	pluginName: "No pluginName specified",

	postCreate: function(){
		this.inherited(arguments);
		this._crumbAttaches=[];
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.prisms.loadPlugin(this);
		this._layout();
	},

	processEvent: function(event){
		if(event.method=="setSearch")
			this.searchBox.value=event.search;
		else if(event.method=="setCookieCrumbs")
			this.setCookieCrumbs(event.crumbs);
		else if(event.method=="setTooltip")
			this.searchBox.title=event.tooltip;
		else
			throw new Error("Unrecognized "+this.pluginName+" event: "+event.method);
	},

	shutdown: function(){
		this.dataLock=true;
		try{
			this.searchBox.value="";
		} finally{
			this.dataLock=false;
		}
		this.setCookieCrumbs([]);
		this.setTypes({}, {});
	},

	_layout: function(){
		this.logViewer.layout();
	},

	setTypes: function(types, users){
		this.types=types;
		this.users=users;
	},

	setCookieCrumbs: function(crumbs){
		for(var i=0;i<this._crumbAttaches.length;i++)
			__dojo.disconnect(this._crumbAttaches[i]);
		this._crumbAttaches.length=0;
		this.cookieCrumbCell.innerHTML="";
		for(var i=0;i<crumbs.length;i++)
		{
			var link=this.createLink(crumbs[i].text, i);
			link.title=crumbs[i].descrip;
			if(crumbs[i].selected)
				link.style.fontWeight="bold";
			this.cookieCrumbCell.appendChild(link);
			if(i<crumbs.length-1)
				this.cookieCrumbCell.appendChild(document.createTextNode(" > "));
		}
		PrismsUtils.setTableRowVisible(this.cookieCrumbRow, crumbs.length>0);
	},

	createLink: function(text, index){
		var div=document.createElement("div");
		div.innerHTML="<a href=\"\" onclick=\"event.returnValue=false; return false;\" style=\"color:blue;display:inline;padding:5px\">"
			+PrismsUtils.fixUnicodeString(text)+"</a>";
		var a=div.childNodes[0];
		this._crumbAttaches.push(__dojo.connect(a, "onclick", this, function(){
			this._doPrevSearch(index);
		}));
		return a;
	},

	_doPrevSearch: function(index){
		this.prisms.callApp(this.pluginName, "prevSearch", {index: index});
	},

	_searchClicked: function(){
		this.prisms.callApp(this.pluginName, "search", {search: this.searchBox.value});
	},

	_searchKeyPressed: function(event){
		var keyCode=event.keyCode;
		if(keyCode==__dojo.keys.ENTER)
			this.prisms.callApp(this.pluginName, "search", {search: this.searchBox.value});
	},

	_instanceChecked: function(){
	},

	_appChecked: function(){
		if(this.dataLock)
			return;
	},

	_clientChecked: function(){
	},

	_userChecked: function(){
	},

	_sessionChecked: function(){
	},

	_instanceSelected: function(){
		if(this.dataLock)
			return;
	},

	_appSelected: function(){
		if(this.dataLock)
			return;
	},

	_clientSelected: function(){
		if(this.dataLock)
			return;
	},

	_userSelected: function(){
		if(this.dataLock)
			return;
	},

	_sessionSelected: function(){
		if(this.dataLock)
			return;
	}
});
