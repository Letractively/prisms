
dojo.require("dijit.form.Slider");

dojo.provide("iweda.ColorPicker");
dojo.declare("iweda.ColorPicker", [dijit._Widget, dijit._Templated, dijit._Container], {

	templatePath: "/WeatherEffectsServlet/view/iweda/templates/colorPicker.html",

	widgetsInTemplate: true,

	_lastNotified: 0,

	alphaEnabled: false,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.setAlphaEnabled(this.alphaEnabled);
		this.dataLock=true;
		try{
			this.setRGBValue(0, 0, 0, 0);
		} finally{
			this.dataLock=false;
		}
	},

	setValue: function(value){
		if(typeof value.r != "undefined")
			this.setRGBValue(value.r, value.g, value.b, value.a);
		else if(typeof value.red != "undefined")
			this.setRGBValue(value.red, value.green, value.blue, value.alpha);
		else if(typeof value.charAt == "function" && value.charAt(0)=='#')
		{
			var color=this.fromHtml(value);
			this.setRGBValue(color.r, color.g, color.b, null);
		}
		else
			throw new Error(dojo.toJson(color)+" is not a color");
	},

	setRGBValue: function(r, g, b, a){
		this.dataLock=true;
		try{
			if(r!=null)
			{
				this.redSlider.setValue(r);
				this.redSpinner.setValue(r);
			}
			if(g!=null)
			{
				this.greenSlider.setValue(g);
				this.greenSpinner.setValue(g);
			}
			if(b!=null)
			{
				this.blueSlider.setValue(b);
				this.blueSpinner.setValue(b);
			}
			if(this.alphaEnabled && a != null)
			{
				this.opacitySlider.setValue(a);
				this.opacitySpinner.setValue(a);
			}
		} finally{
			this.dataLock=false;
		}
		if(r!=null && g!=null && b!=null)
			this.domNode.style.backgroundColor=this.toHtml(r, g, b);
	},

	getValue: function(){
		var ret={r: this.redSpinner.getValue(),
			g: this.greenSpinner.getValue(),
			b: this.blueSpinner.getValue()};
		if(this.alphaEnabled)
			ret.a=this.opacitySpinner.getValue();
		return ret;
	},

	onChange: function(value){
	},

	setEnabled: function(enabled){
		this.redSlider.setAttribute("disabled", !enabled);
		this.redSpinner.setAttribute("disabled", !enabled);
		this.greenSlider.setAttribute("disabled", !enabled);
		this.greenSpinner.setAttribute("disabled", !enabled);
		this.blueSlider.setAttribute("disabled", !enabled);
		this.blueSpinner.setAttribute("disabled", !enabled);
		this.opacitySlider.setAttribute("disabled", !enabled);
		this.opacitySpinner.setAttribute("disabled", !enabled);
	},

	setAlphaEnabled: function(alphaEnabled){
		PrismsUtils.setTableRowVisible(this.opacityRow, alphaEnabled);
	},

	_redSliderChanged: function(){
		if(this.dataLock)
			return;
		var r;
		this.dataLock=true;
		try{
			r=this.redSlider.getValue();
			r=Math.round(r);
			this.redSlider.setValue(r);
			this.redSpinner.setValue(r);
		} finally{
			this.dataLock=false;
		}
		this._changed(r, null, null, null);
	},

	_redSpinnerChanged: function(){
		if(this.dataLock)
			return;
		var r;
		this.dataLock=true;
		try{
			r=this.redSpinner.getValue();
			r=Math.round(r);
			this.redSlider.setValue(r);
			this.redSpinner.setValue(r);
		} finally{
			this.dataLock=false;
		}
		this._changed(r, null, null, null);
	},

	_greenSliderChanged: function(){
		if(this.dataLock)
			return;
		var g;
		this.dataLock=true;
		try{
			g=this.greenSlider.getValue();
			g=Math.round(g);
			this.greenSlider.setValue(g);
			this.greenSpinner.setValue(g);
		} finally{
			this.dataLock=false;
		}
		this._changed(null, g, null, null);
	},

	_greenSpinnerChanged: function(){
		if(this.dataLock)
			return;
		var g;
		this.dataLock=true;
		try{
			g=this.greenSpinner.getValue();
			g=Math.round(g);
			this.greenSlider.setValue(g);
			this.greenSpinner.setValue(g);
		} finally{
			this.dataLock=false;
		}
		this._changed(null, g, null, null);
	},

	_blueSliderChanged: function(){
		if(this.dataLock)
			return;
		var b;
		this.dataLock=true;
		try{
			b=this.blueSlider.getValue();
			b=Math.round(b);
			this.blueSlider.setValue(b);
			this.blueSpinner.setValue(b);
		} finally{
			this.dataLock=false;
		}
		this._changed(null, null, b, null);
	},

	_blueSpinnerChanged: function(){
		if(this.dataLock)
			return;
		var b;
		this.dataLock=true;
		try{
			b=this.blueSpinner.getValue();
			b=Math.round(b);
			this.blueSlider.setValue(b);
			this.blueSpinner.setValue(b);
		} finally{
			this.dataLock=false;
		}
		this._changed(null, null, b, null);
	},

	_opacitySliderChanged: function(){
		if(this.dataLock)
			return;
		var a;
		this.dataLock=true;
		try{
			a=this.opacitySlider.getValue();
			a=Math.round(a);
			this.opacitySlider.setValue(a);
			this.opacitySpinner.setValue(a);
		} finally{
			this.dataLock=false;
		}
		this._changed(null, null, null, a);
	},

	_opacitySpinnerChanged: function(){
		if(this.dataLock)
			return;
		var a;
		this.dataLock=true;
		try{
			a=this.opacitySpinner.getValue();
			a=Math.round(a);
			this.opacitySlider.setValue(a);
			this.opacitySpinner.setValue(a);
		} finally{
			this.dataLock=false;
		}
		this._changed(null, null, null, a);
	},

	_changed: function(r, g, b, a){
		if(!r)
			r=this.redSpinner.getValue();
		if(!g)
			g=this.greenSpinner.getValue();
		if(!b)
			b=this.blueSpinner.getValue();
		if(!a && this.alphaEnabled)
			a=this.opacitySpinner.getValue();
		this.domNode.style.backgroundColor=this.toHtml(r, g, b);
		if(this._onChangeCallback)
		{
			clearTimeout(this._onChangeCallback);
			delete this._onChangeCallback;
		}
		var value={r: r, g: g, b: b}
		var time=new Date().getTime();
		if(this._lastNotified<time-1000)
		{
			this._lastNotified=time;
			this.onChange(value);
		}
		else
		{
			var self=this;
			this._onChangeCallback=setTimeout(function(){
				delete self._onChangeCallback;
				self._lastNotified=new Date().getTime();
				self.onChange(value);
			}, 1000);
		}
	},

	fromHtml: function(color){
		return {r: parseInt(color.substring(1, 3), 16),
			g: parseInt(color.substring(3, 5), 16),
			b: parseInt(color.substring(5, 7), 16)};
	},

	toHtml: function(r, g, b){
		return "#"+this.toHex(r)+this.toHex(g)+this.toHex(b);
	},

	HEX_DIGS: ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'],

	toHex: function(value){
		return this.HEX_DIGS[Math.floor(value/16)]+this.HEX_DIGS[value%16];
	}
});
