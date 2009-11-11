
dojo.provide("prisms.widget.CloseX");
dojo.declare("prisms.widget.CloseX", [dijit._Widget, dijit._Contained], {

	blurredSrc: "../rsrc/icons/windowsXBlur.png",
	
	focusedSrc: "../rsrc/icons/windowsXFocus.png",

	postCreate: function(){
		this.domNode.style.position="absolute";
		this.img=document.createElement("img");
		this.img.src=this.blurredSrc;
		this.domNode.appendChild(this.img);
		dojo.connect(this.img, "onmouseenter", this, this._mouseEnter);
		dojo.connect(this.img, "onmouseout", this, this._mouseOut);
		dojo.connect(this.img, "onmousedown", this, this._mouseDown);
		dojo.connect(this.img, "onmouseup", this, this._mouseUp);
	},

	_mouseEnter: function(){
		this.img.src=this.focusedSrc;
//		this.imageChanged=true;
	},

	_mouseOut: function(){
//		if(this.imageChanged)
//		{
//			this.imageChanged=false;
//			return;
//		}
		this.img.src=this.blurredSrc;
		this.isPressed=false;
	},

	_mouseDown: function(){
		this.isPressed=true;
	},

	_mouseUp: function(){
		if(this.isPressed)
		{
			this.onClick();
		}
		this.isPressed=false;
	},

	onClick: function(){
	}
});
