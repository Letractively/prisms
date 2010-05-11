
dojo.require("dijit.layout.BorderContainer");
dojo.require("dijit.Toolbar");
dojo.require("dijit.Menu");
dojo.require("dijit.form.Button");

dojo.require("prisms.PrismsUtils");
dojo.require("prisms.DojoPrisms");
dojo.require("prisms.widget.PrismsWidget");
dojo.require("prisms.widget.ContentPane");
dojo.require("prisms.widget.UI");
dojo.require("prisms.widget.TreeModel");
dojo.require("prisms.widget.PrismsTree");

dojo.require("log4j.LoggerEditor");

dojo.provide("log4j.Log4jApp");

dojo.declare("log4j.Log4jApp", [prisms.widget.PrismsWidget, dijit._Templated], {
	templatePath: "/prisms/view/log4j/log4jApp.html",

	widgetsInTemplate: true,

	postCreate: function(){
		if(this.dojoPrisms)
			this.dojoPrisms.setLoginMenu(this.loginMenu);
		if(this.prisms)
			this.setPrisms(this.prisms);
		this.inherited("postCreate", arguments);
		var self=this;
		dojo.addOnLoad(function(){
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
