
dojo.provide("prisms.widget.PrismsDialog");

dojo.require("dijit.Dialog");

/**
 * This class is a hack around certain constraints of dijit.Dialog.  Mainly, this dialog differs
 * in that instances of this dialog only govern (block) this dialog's parent node, not the entire
 * screen.  Some of this class's functionality requires awful, awful hacks.
 */
dojo.declare("prisms.widget.PrismsDialogUnderlay", dijit.DialogUnderlay, {
	postCreate: function(){
		var oldBody=dojo.body;
		try{
			var node=this.dialog.domNode.parentNode;
			dojo.body=function(){ //HACK!!
				return node;
			};
			this.inherited("postCreate", arguments);
		} finally{
			dojo.body=oldBody; //end hack
		}
	},

	layout: function(){
		var oldViewport=dijit.getViewport;
		try{
			var self=this;
			dijit.getViewport=function(){ //HACK!!
				return self._getViewport();
			};
			this.inherited("layout", arguments);
		} finally{
			dijit.getViewport=oldViewport; //end hack
		}
	},

	_getViewport: function(){
		var node=this.dialog.domNode.parentNode;
		var parentNode;
		if(this._layoutParentNode)
			parentNode=this._layoutParentNode;
		else
		{
			parentNode=node;
			while(this.isStatic(parentNode.parentNode))
				parentNode=parentNode.parentNode;
			this._layoutParentNode=parentNode;
		}
		var ret= dojo.coords(node);
		var parentCoords=dojo.coords(parentNode);
		ret.l=ret.x-parentCoords.x;
		ret.t=ret.y-parentCoords.y;
		return ret;
	},

	isStatic: function(node){
		if(node==document.body)
			return false;
		var pos=node.style.position;
		if(!pos || pos=="static" || pos=="inherit")
			return true;
		return false;
	}
});

dojo.declare("prisms.widget.PrismsDialog", dijit.Dialog, {
	postCreate: function(){
		var oldBody=dojo.body;
		try{
			var node=this.domNode.parentNode;
			dojo.body=function(){ //HACK!!
				while(node.getAttribute("noDialogContainer"))
					node=node.parentNode;
				return node;
			};
			this.inherited("postCreate", arguments);
		} finally{
			dojo.body=oldBody; //end hack
		}
	},

	_position: function(){
		var oldViewport=dijit.getViewport;
		try{
			var self=this;
			dijit.getViewport=function(){ //HACK!!
				return self._underlay._getViewport();
			};
			this.inherited("_position", arguments);
		} finally{
			dijit.getViewport=oldViewport; //end hack
		}
	},

	_setup: function(){
		// summary: 
		//		stuff we need to do before showing the Dialog for the first
		//		time (but we defer it until right beforehand, for
		//		performance reasons)

		if(this.titleBar){
			this._moveable = new dojo.dnd.TimedMoveable(this.domNode, { handle: this.titleBar, timeout: 0 });
		}

		this._underlay=new prisms.widget.PrismsDialogUnderlay({ //HACK!!
			dialog: this,
			id: this.id+"_underlay",
			"class": dojo.map(this["class"].split(/\s/), function(s){ return s+"_underlay"; }).join(" ")
		});

		var node = this.domNode;
		this._fadeIn = dojo.fx.combine(
			[dojo.fadeIn({
				node: node,
				duration: this.duration
			 }),
			 dojo.fadeIn({
				node: this._underlay.domNode,
				duration: this.duration,
				onBegin: dojo.hitch(this._underlay, "show")
			 })
			]
		);

		this._fadeOut = dojo.fx.combine(
			[dojo.fadeOut({
				node: node,
				duration: this.duration,
				onEnd: function(){
					node.style.visibility="hidden";
					node.style.top = "-9999px";
				}
			 }),
			 dojo.fadeOut({
				node: this._underlay.domNode,
				duration: this.duration,
				onEnd: dojo.hitch(this._underlay, "hide")
			 })
			]
		);
	}
});
