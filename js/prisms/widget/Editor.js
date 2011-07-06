
__dojo.provide("prisms.widget.Editor");
__dojo.declare("prisms.widget.Editor", [__dijit._Widget, __dijit._Templated], {

	templatePath: "__webContentRoot/view/prisms/templates/editor.html",

	widgetsInTemplate: true,

	postCreate: function(){
		
	},

	_bold: function(){
	},

	_italic: function(){
	},

	_underline: function(){
	},

	_editIn: function(){
		console.log("Now editing");
	},

	_editOut: function(){
		console.log("Not editing");
	},

	_key: function(event){
		var ch;
		if(__dojo.isIE)
			ch=event.charCode;
		else
			ch=event.which;
		this.editArea.innerHTML+=ch;
	},

	_dblClick: function(){
	}
});
