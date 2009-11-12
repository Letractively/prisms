
dojo.provide("prisms.widget.PrismsWidget");
dojo.declare("prisms.widget.PrismsWidget", [dijit._Widget, dijit._Container], {
	prisms: null,

	setPrisms: function(prisms){
		this.prisms=prisms;
		var children=this.getChildren();
		for(var c=0;c<children.length;c++)
			this._setChildPrisms(children[c], prisms);
	},

	_setChildPrisms: function(child, prisms){
		if(typeof child.setPrisms=="function")
		{
			if(!child.prisms)
				child.setPrisms(prisms);
		}
		else if(typeof child.getChildren=="function")
		{
			var children=child.getChildren();
			for(var c=0;c<children.length;c++)
				this._setChildPrisms(children[c], prisms);
		}
		else
		{
			if(child.domNode)
				child=child.domNode;
			var children=child.childNodes;
			for(var c=0;c<children.length;c++)
			{
				var grandchild=children[c];
				if(grandchild.id)
				{
					var grandWidget=dijit.byId(grandchild.id);
					if(grandWidget && grandWidget.domNode==grandchild)
						grandchild=grandWidget;
				}
				this._setChildPrisms(grandchild, prisms);
			}
		}
	}
});
