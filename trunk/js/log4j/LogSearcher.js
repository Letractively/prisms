
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
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.prisms.loadPlugin(this);
		this._layout();
	},

	processEvent: function(event){
		if(event.method=="setSearch")
			this.searchBox.value=event.search;
		else if(event.method=="setTooltip")
			this.searchBox.title=event.tooltip;
		else
			throw new Error("Unrecognized "+this.pluginName+" event: "+event.method);
	},

	shutdown: function(){
		this.setTypes({}, {});
	},

	_layout: function(){
		this.logViewer.layout();
	},

	setTypes: function(types, users){
		this.types=types;
		this.users=users;
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
