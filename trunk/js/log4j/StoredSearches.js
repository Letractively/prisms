
__dojo.require("dijit.form.Button");

__dojo.provide("log4j.StoredSearches");
__dojo.declare("log4j.StoredSearches", [__dijit._Widget, __dijit._Templated], {

	templatePath: "__webContentRoot/view/log4j/templates/storedSearches.html",

	widgetsInTemplate: true,

	pluginName: "No pluginName specified",

	postCreate: function(){
		this.inherited(arguments);
		this.searchConnects=[];
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		prisms.loadPlugin(this);
	},

	processEvent: function(event){
		if(event.method=="setSearches")
		{
			this.shutdown();
			var hasSelected=false;
			for(var s=0;s<event.searches.length;s++)
			{
				var link=this.createLink(event.searches[s].name);
				if(event.searches[s].selected)
				{
					hasSelected=true;
					link.style.fontWeight="bold";
					this.forget.setLabel("Forget Search \""+event.searches[s].name+"\"");
				}
				this.domNode.insertBefore(link, this.remember.domNode);
			}
			if(hasSelected)
			{
				this.remember.domNode.style.display="none";
				this.forget.domNode.style.display="inline";
			}
			else
			{
				if(event.isSearched)
					this.remember.domNode.style.display="inline";
				else
					this.remember.domNode.style.display="none";
				this.forget.domNode.style.display="none";
			}
			this.onChange();
		}
		else
			throw new Error("Unrecognized "+this.pluginName+" event: "+event.method);
	},

	shutdown: function(){
		for(var i=0;i<this.searchConnects.length;i++)
			__dojo.disconnect(this.searchConnects[i]);
		this.searchConnects.length=0;
		for(var i=0;i<this.domNode.childNodes.length;i++)
			if(this.domNode.childNodes[i].tagName=="A")
			{
				this.domNode.removeChild(this.domNode.childNodes[i]);
				i--;
			}
		this.forget.setLabel("Forget Search");
	},

	onChange: function(){
	},

	_doSearch: function(text){
		this.prisms.callApp(this.pluginName, "doSearch", {search: text});
	},

	_remember: function(){
		this.prisms.callApp(this.pluginName, "rememberSearch");
	},

	_forget: function(){
		this.prisms.callApp(this.pluginName, "forgetSearch");
	},

	createLink: function(text){
		var div=document.createElement("div");
		div.innerHTML="<a href=\"\" onclick=\"event.returnValue=false; return false;\" style=\"color:blue;display:inline;padding:5px\">"
			+PrismsUtils.fixUnicodeString(text)+"</a>";
		var a=div.childNodes[0];
		this.searchConnects.push(__dojo.connect(a, "onclick", this, function(){
			this._doSearch(text)
		}));
		return a;
	}
});
