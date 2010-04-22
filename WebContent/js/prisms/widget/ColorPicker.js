dojo.require("dijit.form.Slider");

dojo.provide("prisms.widget.ColorPicker");
dojo.declare("prisms.widget.ColorPicker", [dijit._Widget, dijit._Templated], {

	templatePath: "/prisms/view/prisms/templates/colorPicker.html",

	widgetsInTemplate: true,

	displayAlpha: true,

	sqrt3: Math.sqrt(3),

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.setValue("#FFFFFF");
		if(!this.displayAlpha)
			PrismsUtils.setTableRowVisible(this.alphaRow, false);
		var height=this.colorHexagon.height;
//		dojo.marginBox(this.colorHexagon, {t:-height+1});
//		dojo.marginBox(this.shade, {t:-height*2+1});
//		dojo.marginBox(this.hexagonCell, {h:height});
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

	setBefore: function(value){
		if(typeof value=="string")
		{
			value={r: parseInt(value.substring(1, 3), 16),
				g: parseInt(value.substring(3, 5), 16),
				b: parseInt(value.substring(5, 7), 16)};
		}
		var brightness=this.getBrightness(value);
		if(brightness>=128)
			this.beforeCell.style.color="black";
		else
			this.beforeCell.style.color="white";
		this.beforeCell.style.backgroundColor=this.toHTML(value);
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
			var opacity=value.a/255;
			console.log("about to set opacity");
			if(dojo.isIE)
			{
				var ieOpacity=Math.round(opacity*100);
				this.colorHexagon.style.filter  = "alpha(opacity=" + ieOpacity + ")";
			}
			else
				this.colorHexagon.style.opacity=opacity;

			this.redBox.value=value.r;
			this.greenBox.value=value.g;
			this.blueBox.value=value.b;
			var brightness=this.getBrightness(value);
			this.brightnessSlider.setValue(brightness);
			if(brightness>=128)
			{
				this.marker.style.borderColor="black";
				this.currentCell.style.color="black";
			}
			else
			{
				this.marker.style.borderColor="white";
				this.currentCell.style.color="white";
			}
			opacity*=1-brightness/255;
			if(dojo.isIE)
			{
				var ieOpacity=Math.round(opacity*100);
				this.shade.style.filter  = "alpha(opacity=" + ieOpacity + ")";
			}
			else
				this.shade.style.opacity=opacity;
			this._checkBrightColor(value);
			this.currentCell.style.backgroundColor=this.toHTML(value);
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

	toHTML: function(rgbColor){
		var ret="#";
		var el=rgbColor.r.toString(16);
		if(el.length<2)
			el="0"+el;
		ret+=el;
		el=rgbColor.g.toString(16);
		if(el.length<2)
			el="0"+el;
		ret+=el;
		el=rgbColor.b.toString(16);
		if(el.length<2)
			el="0"+el;
		ret+=el;
		return ret;
	},

	_checkBrightColor: function(value){
		var bright=this.getBrightness(value);
		if(!this._brightColor)
		{
			if(bright>10)
				this._brightColor=this.getBrightColor(value);
			else
				this._brightColor={r: 255, g: 255, b: 255};
			return;
		}
		var bc=this.getBrightColor(value);
		if(!bc)
			return;
		if(this._isChannelDifferent(bc.r, this._brightColor.r, bright)
			|| this._isChannelDifferent(bc.g, this._brightColor.g, bright)
			|| this._isChannelDifferent(bc.b, this._brightColor.b, bright))
		{
			this._brightColor=bc;
			this._positionMarker(bc);
		}
	},

	_isChannelDifferent: function(v1, v2, scale){
		var diff=v1-v2;
		if(diff==0 || v2==0)
			return false;
		if(diff<0)
			diff=-diff;
		return diff>=255/scale;
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
		value.b=blue;
		this.setValue(value, true);
	},

	_setAlpha: function(alpha, notify){
		var value=this.getValue();
		value.a=alpha;
		this.setValue(value, notify);
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
		var coords=dojo.coords(this.alphaHexagon);
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


		var bright = this.brightnessSlider.getValue();
		r = r * bright/255;
		g = g * bright/255;
		b = b * bright/255;
		this.setValue({r: Math.round(r*255), g: Math.round(g*255), b: Math.round(b*255),
			a: this.value.a}, true);
	},

	_positionMarker: function(value){
		var sideLength=this.colorHexagon.width/2;
		var bright=this.getBrightness(value);
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
		dojo.marginBox(this.marker, {t: Math.round(y)-2, l:Math.round(x)-2});
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
		this.setValue(value, false);
		this._notifyUserLater();
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
		this._setAlpha(value, true);
	},

	_alphaChangedS: function(){
		if(this.dataLock)
			return false;
		var value=Math.round(this.alphaSlider.getValue());
		this.dataLock=true;
		try{
			this.alphaBox.value=value;
		} finally{
			this.dataLock=false;
		}
		this._setAlpha(value, false);
		this._notifyUserLater();
	},

	_notifyInterval: 500,

	_lastNotifyLaterCalled: 0,

	_notifyUserLater: function(){
		this._lastNotifyLaterCalled=new Date().getTime();
		if(!this._notificationThread)
		{
			this._notificationThread=setInterval(dojo.hitch(this, function(){
				if(this._lastNotifyLaterCalled<new Date().getTime()-this._notifyInterval)
				{
					var nt=this._notificationThread;
					this._notificationThread=null;
					this.setValue(this.getValue(), true);
					clearInterval(nt);
				}
			}), 250);
		}
	},

	onChange: function(){
	},

	error: function(msg){
		alert(msg);
	}
});
