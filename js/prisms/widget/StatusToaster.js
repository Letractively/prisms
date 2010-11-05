
__dojo.require("dojox.widget.Toaster");

__dojo.provide("prisms.widget.StatusToaster");
__dojo.declare("prisms.widget.StatusToaster", __dojox.widget.Toaster, {

	pluginName: "No pluginName specified",

	prisms: null,

	_placeClip: function(){
		/* This is an awful, awful hack to get the toaster to not move itself up to the body level.
		 * This functionality can't be tolerated in a portlet environment. */
		var oldViewport=__dijit.getViewport;
		try{
			var node=this.domNode.parentNode;
			__dijit.getViewport=function(){ //HACK!!
				var ret= __dojo.coords(node);
				ret.l=-ret.x;
				ret.t=ret.y;
				return ret;
			};
			this.inherited("_placeClip", arguments);
		} finally{
			__dijit.getViewport=oldViewport; //end hack
		}
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.prisms.loadPlugin(this);
	},

	processEvent: function(event){
		if(event.method=="showStatus")
			this._handleMessage({
				message: PrismsUtils.fixUnicodeString(event.message),
				type: "message",
				duration: 5000
			});
		else if(event.method=="showError")
			this._handleMessage({
				message: PrismsUtils.fixUnicodeString(event.message),
				type: "error",
				duration: 5000
			});
		else
			throw new Error("Unrecognized "+this.pluginName+" method: "+event.method);
	},

	shutdown: function(){
	}
});
