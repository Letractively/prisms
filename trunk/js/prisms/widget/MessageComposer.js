
__dojo.require("dijit.form.Button");

__dojo.require("dijit.Editor");

__dojo.provide("prisms.widget.MessageComposer");
__dojo.declare("prisms.widget.MessageComposer", [__dijit._Widget, __dijit._Templated, __dijit._Container], {

	templatePath: "__webContentRoot/view/prisms/templates/messageComposer.html",
	
	widgetsInTemplate: true,

	postCreate: function(){
		this.inherited("postCreate", arguments);
	},

	_send: function(){
	},

	_save: function(){
	},

	_discard: function(){
	},

	_attach: function(){
	}
});
