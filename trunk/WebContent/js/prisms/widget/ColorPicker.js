dojo.require("dijit.form.Slider");

dojo.provide("prisms.widget.ColorPicker");
dojo.declare("prisms.widget.ColorPicker", [dijit._Widget, dijit._Templated], {

	templatePath: "../view/prisms/templates/colorPicker.html",

	widgetsInTemplate: true,

	displayAlpha: true,

	sqrt3: Math.sqrt(3),

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.setValue("#FFFFFF");
		if(!this.displayAlpha)
			PrismsUtils.setTableRowVisible(this.alphaRow, false);
		var width=this.colorHexagon.width+"px";
		var height=this.colorHexagon.height+"px";
		this.colorShadeTable.style.width=width;
		this.colorShadeTable.style.height=height;
		this.colorTableDiv.style.height=height;
		this.colorShade.style.height=height;
	},

	setVisible: function(visible){
		if(dojo.isIE && visible)
			this.domNode.style.display="block";
		else if(visible)
			this.domNode.style.display="table";
		else
			this.domNode.style.display="none";
	},

	isDisabled: function(){
		return this.disabled;
	},

	setDisabled: function(disabled){
		this.disabled=true;
		this.redBox.disabled=disabled;
		this.greenBox.disabled=disabled;
		this.blueBox.disabled=disabled;
		this.brightnessSlider.setAttribute("disabled", disabled);
		this.alphaSlider.setAttribute("disabled", disabled);
		this.alphaBox.disabled=disabled;
	},

	setValue: function(value, notifyUser){
		if(typeof value=="string")
		{
			value={r: parseInt(value.substring(1, 3), 16),
				g: parseInt(value.substring(3, 5), 16),
				b: parseInt(value.substring(5, 7), 16)};
		}
		this.dataLock=true;
		try{
			if(typeof value.a!="number")
				value.a=255;
			this.alphaSlider.setValue(value.a);
			this.alphaBox.value=value.a;
			this.redBox.value=value.r;
			this.greenBox.value=value.g;
			this.blueBox.value=value.b;
			var brightness=this.getBrightness(value);
			this.brightnessSlider.setValue(brightness);
			var opacity=1-brightness/255;
			this.colorShade.style.opacity=opacity;
			if(this.colorShade.style.filters)
				this.colorShade.style.filters.alpha.opacity=opacity*100;
			this._positionMarker(value);
			this._checkBrightColor(value);
		} finally{
			this.dataLock=false;
		}
		this.value=value;
		if(notifyUser)
			this.onChange(value);
	},
	
	getValue: function(){
		return {r: this.value.r, g: this.value.g,
			b: this.value.b, a: this.value.a};
	},

	getBrightness: function(value){
		var ret=value.r;
		if(value.g>ret)
			ret=value.g;
		if(value.b>ret)
			ret=value.b;
		return ret;
	},

	_checkBrightColor: function(value){
		if(!this._brightColor)
		{
			if(this.getBrightness(value)>10)
				this._brightColor=this.getBrightColor(value);
			else
				this._brightColor={r: 255, g: 255, b: 255};
			return;
		}
		var bc=this.getBrightColor(value);
		if(!bc)
			return;
		if(bc.r!=this._brightColor.r || bc.g!=this._brightColor.g || bc.g!=this._brightColor.b)
			this._brightColor=bc;
	},

	getBrightColor: function(value){
		var bright=this.getBrightness(value);
		if(bright==255)
			return {r: value.r, g: value.g, b: value.b};
		if(bright==0)
			return null;
		return {r: Math.round(value.r/bright*255), g: Math.round(value.g/bright*255),
			b: Math.round(value.b/bright*255)};
	},

	_setRed: function(red){
		var value=this.getValue();
		value.r=red;
		this.setValue(value, true);
	},

	_setGreen: function(green){
		var value=this.getValue();
		value.g=green;
		this.setValue(value, true);
	},

	_setBlue: function(blue){
		var value=this.getValue();
		value.g=green;
		this.setValue(value, true);
	},

	_setAlpha: function(alpha){
		var value=this.getValue();
		value.a=alpha;
		this.setValue(value, true);
	},

	_colorClicked: function(evt){
		var x, y;
		if(evt.clientX && evt.clientY){
			x=evt.clientX;
			y=evt.clientY;
		} else if(evt.offsetX && evt.offsetY){
			x=evt.offsetX;
			y=evt.offsetY;
		} else if(evt.layerX && evt.layerY){
			x=evt.layerX-1;
			y=evt.layerY-1;
		} else {
			x=evt.pageX-evt.target.offsetLeft;
			y=evt.pageY-evt.target.offsetTop;
		}
		var coords=dojo.coords(this.colorShadeTable);
		x-=coords.x;
		y-=coords.y;

		var sqrt3=this.sqrt3;
		var sideLength=this.colorHexagon.width/2;
		var _x=x;
		x=sideLength*sqrt3/2-y;
		y=sideLength-_x;
		
		var r, g, b;
		if(x >= 0)
		{
			if(y >= x / sqrt3)
			{
				r = 1;
				g = 1 - (y - x / sqrt3) / sideLength;
				b = 1 - (y + x / sqrt3) / sideLength;
			}
			else
			{
				r = 1 - (x / sqrt3 - y) / sideLength;
				g = 1;
				b = 1 - 2 * x / sqrt3 / sideLength;
			}
		}
		else
		{
			if(y >= -x / sqrt3)
			{
				r = 1;
				g = 1 - (y - x / sqrt3) / sideLength;
				b = 1 - (y + x / sqrt3) / sideLength;
			}
			else
			{
				r = 1 - (-y - x / sqrt3) / sideLength;
				g = 1 + 2 * x / sqrt3 / sideLength;
				b = 1;
			}
		}

		if(r < 0 || r > 1 || g < 0 || g > 1 || b < 0 || b > 1)
			return;

		this.setValue({r: Math.round(r*255), g: Math.round(g*255), b: Math.round(b*255),
			a: this.value.a}, true);
	},

	_positionMarker: function(value){
		var bright=this.getBrightness(value);
		if(bright>=128)
			this.marker.style.borderColor="black";
		else
			this.marker.style.borderColor="white";
		var sideLength=this.colorHexagon.width/2;
		if(bright==0)
		{
			this.marker.style.left=sideLength+"px";
			this.marker.style.top="0px";
			return;
		}
		var r=value.r/bright;
		var g=value.g/bright;
		var b=value.b/bright;
		var x, y;
		var sqrt3=this.sqrt3;
		if(r<0.999)
		{
			if(g<0.999)
			{
				x=(g-1)/2*sqrt3*sideLength;
				y=(r-1)*sideLength-x/sqrt3;
			}
			else if(b<0.999)
			{
				x=(1-b)/2*sqrt3*sideLength;
				y=(r-1)*sideLength+x/sqrt3;
			}
			else
			{
				x=0;
				y=0;
			}
		}
		else if(g<0.999 || b<0.999)
		{
			x=(g-b)*sideLength*sqrt3/2;
			y=(2-g-b)*sideLength/2;
		}
		else
		{
			x=0;
			y=0;
		}
		if(x<-sideLength*sqrt3/2 || x>sideLength*sqrt3/2 || y<-sideLength || y>sideLength)
			return;
		var _x=x;
		x=sideLength-y;
		y=sideLength*sqrt3/2-_x;
		dojo.marginBox(this.marker, {t: Math.round(y)-2-this.colorHexagon.height, l:Math.round(x)-2});  
	},

	_isInteger: function(s){
		return (s.toString().search(/^-?[0-9]+$/) == 0);
	},

	_redChanged: function(){
		if(this.dataLock)
			return false;
		var value=this.redBox.value;
		var errorMsg=null;
		if(isNaN(value))
			errorMsg=value+" is not a valid red value--must be an integer between 0 and 255";
		if(!this._isInteger(value))
			errorMsg=value+" is not a valid red value--must be an integer between 0 and 255";
		else if(value<0)
			errorMsg=value+" is not a valid red value--must be an integer between 0 and 255";
		else if(value>255)
			errorMsg=value+" is not a valid red value--must be an integer between 0 and 255";
		if(errorMsg)
		{
			this.error(errorMsg);
			this.dataLock=true;
			try{
				this.redBox.value=this.value.r;
			} finally{
				this.dataLock=false;
			}
		}
		this._setRed(value);
	},

	_greenChanged: function(){
		if(this.dataLock)
			return false;
		var value=this.greenBox.value;
		var errorMsg=null;
		if(isNaN(value))
			errorMsg=value+" is not a valid green value--must be an integer between 0 and 255";
		if(!this._isInteger(value))
			errorMsg=value+" is not a valid green value--must be an integer between 0 and 255";
		else if(value<0)
			errorMsg=value+" is not a valid green value--must be an integer between 0 and 255";
		else if(value>255)
			errorMsg=value+" is not a valid green value--must be an integer between 0 and 255";
		if(errorMsg)
		{
			this.error(errorMsg);
			this.dataLock=true;
			try{
				this.greenBox.value=this.value.g;
			} finally{
				this.dataLock=false;
			}
		}
		this._setGreen(value);
	},

	_blueChanged: function(){
		if(this.dataLock)
			return false;
		var value=this.blueBox.value;
		var errorMsg=null;
		if(isNaN(value))
			errorMsg=value+" is not a valid blue value--must be an integer between 0 and 255";
		if(!this._isInteger(value))
			errorMsg=value+" is not a valid blue value--must be an integer between 0 and 255";
		else if(value<0)
			errorMsg=value+" is not a valid blue value--must be an integer between 0 and 255";
		else if(value>255)
			errorMsg=value+" is not a valid blue value--must be an integer between 0 and 255";
		if(errorMsg)
		{
			this.error(errorMsg);
			this.dataLock=true;
			try{
				this.blueBox.value=this.value.b;
			} finally{
				this.dataLock=false;
			}
		}
		this._setBlue(value);
	},

	_brightnessChanged: function(){
		if(this.dataLock)
			return false;
		var value={r: this._brightColor.r, g: this._brightColor.g, b: this._brightColor.b};
		var newBright=this.brightnessSlider.getValue();
		value.r=Math.round(value.r*newBright/255);
		value.g=Math.round(value.g*newBright/255);
		value.b=Math.round(value.b*newBright/255);
		value.a=this.value.a;
		this.setValue(value, true);
	},

	_alphaChangedB: function(){
		if(this.dataLock)
			return false;
		var value=this.alphaBox.value;
		var errorMsg=null;
		if(isNaN(value))
			errorMsg=value+" is not a valid alpha value--must be an integer between 0 and 255";
		if(!this._isInteger(value))
			errorMsg=value+" is not a valid alpha value--must be an integer between 0 and 255";
		else if(value<0)
			errorMsg=value+" is not a valid alpha value--must be an integer between 0 and 255";
		else if(value>255)
			errorMsg=value+" is not a valid alpha value--must be an integer between 0 and 255";
		if(errorMsg)
		{
			this.error(errorMsg);
			this.dataLock=true;
			try{
				this.alphaBox.value=this.value.a;
			} finally{
				this.dataLock=false;
			}
		}
		this.dataLock=true;
		try{
			this.alphaSlider.setValue(value);
		} finally{
			this.dataLock=false;
		}
		this._setAlpha(value);
	},

	_alphaChangedS: function(){
		if(this.dataLock)
			return false;
		var value=this.alphaSlider.getValue();
		this.dataLock=true;
		try{
			this.alphaBox.value=value;
		} finally{
			this.dataLock=false;
		}
		this._setAlpha(value);
	},

	onChange: function(){
	}
});
