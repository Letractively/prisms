
__dojo.require("dijit.layout.TabContainer");
__dojo.require("prisms.widget.PrismsWidget");

__dojo.provide("prisms.widget.PrismsTabContainer");
__dojo.declare("prisms.widget.PrismsTabContainer", __dijit.layout.TabContainer, {
	prisms: null,

	postMixInProperties: function(){
		this.inherited(arguments);
		this.setPrisms=prisms.widget.PrismsWidget.prototype.setPrisms;
		this._setChildPrisms=prisms.widget.PrismsWidget.prototype._setChildPrisms;
	},

	startup: function(){
		this.inherited(arguments);
		var children=this.getChildren();
		var oldSelected=this.selectedChildWidget;
		for(var c=0;c<children.length;c++)
			this.selectChild(children[c]);
		children=this.getChildren();
		for(var c=0;c<children.length;c++)
			if(children[c]=oldSelected)
			{
				this.selectChild(children[c]);
				break;
			}
		if(this.prisms)
			this.setPrisms(this.prisms);
	}
});
