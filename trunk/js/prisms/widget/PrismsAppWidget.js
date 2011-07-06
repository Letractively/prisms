
__dojo.require("dijit.layout.BorderContainer");
__dojo.require("dijit.layout.ContentPane");
__dojo.require("dijit.form.Button");
__dojo.require("dijit.Toolbar");
__dojo.require("dijit.Menu");

__dojo.require("prisms.DojoPrisms");
__dojo.require("prisms.widget.UI");
__dojo.require("prisms.widget.Preferences");
__dojo.require("prisms.widget.StatusToaster");

__dojo.provide("prisms.widget.PrismsAppWidget");
__dojo.declare("prisms.widget.PrismsAppWidget", [__dijit._Widget, __dijit._Container, __dijit._Templated], {
	prisms: null,

	templatePath: "__webContentRoot/view/prisms/templates/prismsAppWidget.html",

	appHtml: "No Application HTML specified",

	widgetsInTemplate: true,

	withLogin: true,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		if(!this.prisms)
			throw new Error("No prisms link set for app widget");
		this.dojoPrisms.setLoginMenu(this.loginMenu);
		this.aboutMenuItem.setLabel("About "+this.prisms.application);
		if(!this.withLogin || !__showLogin)
			this.loginMenu.domNode.style.display="none";

		this.prismsContent.setHref(this.appHtml);
	},

	appContentLoaded: function(){
		this.scanAttaches(this.prismsContent);
		this.setPrisms(this.prisms);
		this.prisms.prismsConnect();
	},

	addToolbarButton: function(text, callback){
		var button=new __dijit.form.Button({label: text});
		__dojo.connect(button, "onClick", this, callback);
		button.domNode.style.cssFloat="right";
		this.toolbar.addChild(button);
	},

	addHelpMenuItem: function(text, callback){
		var item=new __dijit.MenuItem({label: text});
		__dojo.connect(item, "onClick", this, callback);
		this.helpMenu.dropDown.addChild(item, 0);
	},

	_setPreferencesVisible: function(visible){
		if(!visible)
			this.preferencesMenu.domNode.style.display="none";
	},

	editPreferences: function(){
		this.prisms.callApp('Preferences', 'editPreferences');
	},

	refresh: function(){
		this.prisms.callServer("init");
	},

	showVersion: function(){
		this.prisms.showVersion();
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.setNodePrisms(this);
	},

	setNodePrisms: function(node){
		if(node!=this && typeof node.setPrisms=="function")
			node.setPrisms(this.prisms);
		var children;
		if(node.childNodes)
			children=node.childNodes;
		else if(node.domNode)
			children=node.domNode.childNodes;
		else
			children=[];

		for(var c=0;c<children.length;c++)
		{
			if(typeof children[c].getAttribute!="undefined")
			{
				var toSet;
				var id=children[c].getAttribute("widgetid");
				if(id && id.length>0)
				{
					var widget=__dijit.byId(id);
					if(widget)
						toSet=widget;
					else
						toSet=children[c];
				}
				else
					toSet=children[c];
				this.setNodePrisms(toSet);
			}
		}
	},

	scanAttaches: function(node){
		if(node.dojoAttachPoint)
			this[node.dojoAttachPoint]=node;
		else if(typeof node.getAttribute!="undefined" && node.getAttribute("dojoAttachPoint"))
			this[node.getAttribute("dojoAttachPoint")]=node;

		var children;
		if(node.childNodes)
			children=node.childNodes;
		else if(node.domNode)
			children=node.domNode.childNodes;
		else
			children=[];

		for(var c=0;c<children.length;c++)
		{
			if(typeof children[c].getAttribute!="undefined")
			{
				var toSet;
				var id=children[c].getAttribute("widgetid");
				if(id && id.length>0)
				{
					var widget=__dijit.byId(id);
					if(widget)
						toSet=widget;
					else
						toSet=children[c];
				}
				else
					toSet=children[c];
				this.scanAttaches(toSet);
			}
		}
	}
});
