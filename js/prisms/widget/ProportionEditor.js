
__dojo.require("dijit.form.NumberTextBox");
__dojo.require("dijit.form.Slider");

__dojo.provide("prisms.widget.ProportionEditor");
__dojo.declare("prisms.widget.ProportionEditor", [__dijit._Widget, __dijit._Templated],
{  
	templatePath: "__webContentRoot/view/prisms/templates/proportionEditor.html",

	widgetsInTemplate: true,

	postCreate: function(){
		this.inherited(arguments);
	},

	getValue: function(){
		return this.slider.getValue();
	},

	setValue: function(value){
		this.dataLock=true;
		try{
			this.slider.setValue(value);
			this.textBox.value=Math.round(value*10000)/100;
		} finally{
			this.dataLock=false;
		}
	},

	onChange: function(value){
	},

	_sliderChanged: function(){
		if(this.dataLock)
			return;
		var value=this.slider.getValue();
		this.dataLock=true;
		try{
			this.textBox.value=Math.round(value*10000)/100;
		} finally{
			this.dataLock=false;
		}
		this.onChange(value);
	},

	_textBoxChanged: function(){
		if(this.dataLock)
			return;
		var value=this.textBox.value;
		if(isNaN(value))
		{
			alert(value+" is not a number");
			return;
		}
		value=value-0;
		if(value<0)
		{
			alert("Proportion cannot be negative");
			return;
		}
		else if(value>100)
		{
			alert("Proportion cannot be greater than 100%");
			return;
		}
		value/=100;
		this.dataLock=true;
		try{
			this.slider.setValue(value);
		} finally{
			this.dataLock=false;
		}
		this.onChange(value);
	},
});
