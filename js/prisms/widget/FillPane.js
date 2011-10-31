
/*
 * A FillPane attempts to fill all the space in its parent that is left over from the parent's other
 * children
 */
__dojo.provide("prisms.widget.FillPane");
__dojo.declare("prisms.widget.FillPane", [dijit.layout._LayoutWidget, dijit._Contained], {
	postCreate: function(){
		this.inherited(arguments);
		__dojo.connect(window, "onresize", this, this.resize);
	},

	resize: function(){
		if(!this.getParent() || typeof this.getParent().getChildren!="function")
			return;
		var width=this.getParent().domNode.clientWidth;
		var height=this.getParent().domNode.clientHeight;
		var children=this.getParent().getChildren();
		var thisD={
			left: this.domNode.offsetLeft,
			top: this.domNode.offsetTop,
			width: this.domNode.clientWidth,
			height: this.domNode.clientHeight
		};
		for(var i=0;i<children.length;i++)
		{
			var x=children[i].domNode.offsetLeft;
			var w=children[i].domNode.clientWidth;
			if(x+w<=thisD.left || x>=thisD.left+thisD.width)
				width-=w;
			var y=children[i].domNode.offsetTop;
			var h=children[i].domNode.clientHeight;
			if(y+h<=thisD.top || y>=thisD.top+thisD.height)
				height-=h;
		}
		
		if(this.domNode.style.borderLeftWidth)
			width-=parseInt(this.domNode.style.borderLeftWidth);
		if(this.domNode.style.borderRightWidth)
			width-=parseInt(this.domNode.style.borderRightWidth);
		if(this.domNode.style.borderTopWidth)
			height-=parseInt(this.domNode.style.borderTopWidth);
		if(this.domNode.style.borderBottomWidth)
			height-=parseInt(this.domNode.style.borderBottomWidth);
		
		this.domNode.style.width=width+"px";
		this.domNode.style.height=height+"px";
	},

	layout: function(){
		this.resize();
	}
});
