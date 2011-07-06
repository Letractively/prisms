
__dojo.require("dijit.layout.LayoutContainer");
__dojo.require("dijit.layout.LinkPane");
__dojo.require("dijit.layout.TabContainer");

__dojo.require("prisms.PrismsUtils");
__dojo.require("prisms.widget.PrismsAppWidget");
__dojo.require("prisms.widget.PrismsTabContainer");
__dojo.require("prisms.widget.ContentPane");
__dojo.require("prisms.widget.ListModel");
__dojo.require("prisms.widget.TreeModel");
__dojo.require("prisms.widget.PrismsTree");
__dojo.require("prisms.widget.SearchableList");
__dojo.require("prisms.widget.CloseX");

__dojo.require("manager.UserEditor");
__dojo.require("manager.ApplicationEditor");
__dojo.require("manager.ClientEditor");
__dojo.require("manager.UserAppAssocEditor");
__dojo.require("manager.UserGroupAssocEditor");
__dojo.require("manager.UserPermissionDisplay");
__dojo.require("manager.GroupEditor");
__dojo.require("manager.PermissionEditor");
__dojo.require("manager.PerformanceDisplay");

__dojo.provide("manager.ManagerApp");

__dojo.declare("manager.ManagerApp", prisms.widget.PrismsAppWidget, {
	appHtml: "__webContentRoot/view/manager/managerApp.html",
});
