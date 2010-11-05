
__dojo.require("dijit.layout.BorderContainer");
__dojo.require("dijit.Toolbar");
__dojo.require("dijit.Menu");
__dojo.require("dijit.form.Button");

__dojo.require("prisms.PrismsUtils");
__dojo.require("prisms.DojoPrisms");
__dojo.require("prisms.widget.PrismsWidget");
__dojo.require("prisms.widget.ContentPane");
__dojo.require("prisms.widget.UI");
__dojo.require("prisms.widget.TreeModel");
__dojo.require("prisms.widget.PrismsTree");

__dojo.require("log4j.LoggerEditor");

__dojo.provide("log4j.Log4jApp");

__dojo.declare("log4j.Log4jApp", [prisms.widget.PrismsWidget, __dijit._Templated], {
	templatePath: "__webContentRoot/view/log4j/log4jApp.html",

	widgetsInTemplate: true,

	postCreate: function(){
		if(this.dojoPrisms)
			this.dojoPrisms.setLoginMenu(this.loginMenu);
		if(this.prisms)
			this.setPrisms(this.prisms);
		this.inherited("postCreate", arguments);
		var self=this;
		__dojo.addOnLoad(function(){
			self.setPrisms(self.prisms);
			self.prisms.prismsConnect();
		});
	},

	refresh: function(){
		this.prisms.callServer("init");
	},

	showVersion: function(){
		this.prisms.showVersion();
	}
});
