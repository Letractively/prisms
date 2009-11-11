
dojo.require("dijit.layout.BorderContainer");
dojo.require("dijit.layout.LayoutContainer");
dojo.require("dijit.layout.LinkPane");
dojo.require("dijit.layout.TabContainer");
dojo.require("dijit.layout.ContentPane");
dojo.require("dijit.Toolbar");

dojo.require("prisms.DojoPrisms");
dojo.require("prisms.PrismsUtils");
dojo.require("prisms.widget.PrismsWidget");
dojo.require("prisms.widget.PrismsTabContainer");
dojo.require("prisms.widget.ContentPane");
dojo.require("prisms.widget.ListModel");
dojo.require("prisms.widget.TreeModel");
dojo.require("prisms.widget.PrismsTree");
dojo.require("prisms.widget.UI");

dojo.require("manager.UserEditor");
dojo.require("manager.ApplicationEditor");
dojo.require("manager.UserAppAssocEditor");
dojo.require("manager.UserGroupAssocEditor");
dojo.require("manager.UserPermissionDisplay");
dojo.require("manager.GroupEditor");
dojo.require("manager.PermissionEditor");

dojo.provide("manager.ManagerApp");

dojo.declare("manager.ManagerApp", [prisms.widget.PrismsWidget, dijit._Templated], {
	templatePath: "/prisms/view/manager/managerApp.html",

	widgetsInTemplate: true,

	postCreate: function(){
		if(this.dojoPrisms)
			this.dojoPrisms.loginButton=this.loginButton;
		if(this.prisms)
			this.setPrisms(this.prisms);
		this.inherited("postCreate", arguments);
		var self=this;
		dojo.addOnLoad(function(){
			self.setPrisms(self.prisms);
			self.prisms.prismsConnect();
		});
	},

	_loginClicked: function(){
		this.prisms._loginClicked();
	},

	refresh: function(){
		this.prisms.callServer("init");
	},

	showVersion: function(){
		this.prisms.showVersion();
	}
});
