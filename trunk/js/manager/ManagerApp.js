
__dojo.require("dijit.layout.BorderContainer");
__dojo.require("dijit.layout.LayoutContainer");
__dojo.require("dijit.layout.LinkPane");
__dojo.require("dijit.layout.TabContainer");
__dojo.require("dijit.layout.ContentPane");
__dojo.require("dijit.Toolbar");

__dojo.require("prisms.DojoPrisms");
__dojo.require("prisms.PrismsUtils");
__dojo.require("prisms.widget.PrismsWidget");
__dojo.require("prisms.widget.PrismsTabContainer");
__dojo.require("prisms.widget.ContentPane");
__dojo.require("prisms.widget.ListModel");
__dojo.require("prisms.widget.TreeModel");
__dojo.require("prisms.widget.SearchableList");
__dojo.require("prisms.widget.UI");

__dojo.require("manager.UserEditor");
__dojo.require("manager.ApplicationEditor");
__dojo.require("manager.ClientEditor");
__dojo.require("manager.UserAppAssocEditor");
__dojo.require("manager.UserGroupAssocEditor");
__dojo.require("manager.UserPermissionDisplay");
__dojo.require("manager.GroupEditor");
__dojo.require("manager.PermissionEditor");

__dojo.provide("manager.ManagerApp");

__dojo.declare("manager.ManagerApp", [prisms.widget.PrismsWidget, __dijit._Templated], {
	templatePath: "__webContentRoot/view/manager/managerApp.html",

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
