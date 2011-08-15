
__dojo.require("dijit.layout.BorderContainer");
__dojo.require("dijit.layout.TabContainer");

__dojo.require("prisms.PrismsUtils");
__dojo.require("prisms.widget.PrismsAppWidget");
__dojo.require("prisms.widget.ContentPane");
__dojo.require("prisms.widget.TreeModel");
__dojo.require("prisms.widget.PrismsTree");

__dojo.require("log4j.LogSearcher");
__dojo.require("log4j.LoggerEditor");
__dojo.require("log4j.AutoPurgeEditor");

__dojo.provide("log4j.Log4jApp");

__dojo.declare("log4j.Log4jApp", prisms.widget.PrismsAppWidget, {
	appHtml: "__webContentRoot/view/log4j/log4jApp.html",

	appContentLoaded: function(){
		this.inherited(arguments);
		this.addToolbarButton("Edit Auto Purge", this.editAutoPurge);
	},

	editAutoPurge: function(){
		this.prisms.fireClientEvent("editAutoPurge");
	}
});
