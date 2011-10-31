
__dojo.require("dijit.form.TextBox");
__dojo.require("dijit.form.NumberSpinner");

__dojo.provide("prisms.widget.TimeAmountEditor");
__dojo.declare("prisms.widget.TimeAmountEditor", [__dijit._Widget, __dijit._Templated],
{  
	templatePath: "__webContentRoot/view/prisms/templates/timeAmountEditor.html",

	widgetsInTemplate: true,

	enabled: true,

	monthsEnabled: true,
	
	secondsEnabled: true,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.yearsBox.domNode.style.display="none";
		this.connectOnBlur(this.yearsBox, this._yearsDeselected);
		this.monthsBox.domNode.style.display="none";
		this.connectOnBlur(this.monthsBox, this._monthsDeselected);
		this.daysBox.domNode.style.display="none";
		this.connectOnBlur(this.daysBox, this._daysDeselected);
		this.hoursBox.domNode.style.display="none";
		this.connectOnBlur(this.hoursBox, this._hoursDeselected);
		this.minutesBox.domNode.style.display="none";
		this.connectOnBlur(this.minutesBox, this._minutesDeselected);
		this.secondsBox.domNode.style.display="none";
		this.connectOnBlur(this.secondsBox, this._secondsDeselected);
		this.theValue={months:0, seconds:0};
		this.setValue({months:0, seconds:0});
		this.setDisabled(!this.enabled);
		this.setMonthsEnabled(this.monthsEnabled);
		this.setSecondsEnabled(this.secondsEnabled);
	},

	setVisible: function(visible){
		if(visible)
			this.domNode.style.display="block";
		else
			this.domNode.style.display="none";
	},

	setDisabled: function(disabled){
		this.enabled=!disabled;
		var linkColor;
		if(disabled)
			linkColor="black";
		else
			linkColor="blue";
		this.yearsLink.style.color=linkColor;
		this.monthsLink.style.color=linkColor;
		this.daysLink.style.color=linkColor;
		this.hoursLink.style.color=linkColor;
		this.minutesLink.style.color=linkColor;
		this.secondsLink.style.color=linkColor;
	},

	setMonthsEnabled: function(enabled){
		this.monthsEnabled=enabled;
		if(enabled)
		{
			this._yearsDeselected();
			this._monthsDeselected();
			this.yearsLabelCell.style.display="block";
			this.monthsLabelCell.style.display="block"; 
		} 
		else
		{
			this.yearsLink.style.display="none";
			this.yearsBox.domNode.style.display="none";
			this.yearsLabelCell.style.display="none";
			this.monthsLink.style.display="none";
			this.monthsBox.domNode.style.display="none";
			this.monthsLabelCell.style.display="none";
		}
	},

	setSecondsEnabled: function(enabled){
		this.secondsEnabled=enabled;
		if(enabled)
		{
			this._secondsDeselected();
			this.secondsLabelCell.style.display="block";
		}
		else
		{
			this.secondsLink.style.display="none";
			this.secondsBox.domNode.style.display="none";
			this.secondsLabelCell.style.display="none";
		}
	},

	getValue: function(){
		return this.theValue;
	},

	setValue: function(value){
		if(typeof value=="number")
			value={months:0, seconds:value};
		else if(!value.months)
			value.months=0;

		this.setMonths(value.months);
		this.setSeconds(value.seconds);
		this.theValue=value;
	},

	setMonths: function(months){
		if(this.dataLock)
			return;
		this.theValue.months=months;
		this.dataLock=true;
		try{
			this.monthsLink.innerHTML=""+(months%12);
			this.monthsBox.setValue(months%12);
			var years=Math.floor(months/12);
			this.yearsLink.innerHTML=""+years;
			this.yearsBox.setValue(years);
		} finally{
			this.dataLock=false;
		}
		this.onChange();
	},

	setSeconds: function(seconds){
		if(this.dataLock)
			return;
		this.theValue.seconds=seconds;
		this.dataLock=true;
		try{
			this.secondsLink.innerHTML=""+(seconds%60);
			this.secondsBox.setValue(seconds%60);
			var minutes=Math.floor(seconds/60);
			this.minutesLink.innerHTML=""+(minutes%60);
			this.minutesBox.setValue(minutes%60);
			var hours=Math.floor(minutes/60);
			this.hoursLink.innerHTML=""+(hours%24);
			this.hoursBox.setValue(hours%24);
			var days=Math.floor(hours/24);
			this.daysLink.innerHTML=""+days;
			this.daysBox.setValue(days);
		} finally{
			this.dataLock=false;
		}
		this.onChange();
	},

	_yearsSelected: function(){
		if(!this.enabled || !this.monthsEnabled)
			return;
		this.yearsLink.style.display="none";
		this.yearsBox.domNode.style.display="block";
		setTimeout(__dojo.hitch(this, function(){
			this.yearsBox.focusNode.focus();
		}), 50);
	},

	_yearsDeselected: function(){
		if(!this.monthsEnabled)
			return;
		this.yearsLink.style.display="block";
		this.yearsBox.domNode.style.display="none";
	},

	_yearsChanged: function(){
		this.setMonths(this.yearsBox.getValue()*12+this.monthsBox.getValue());
	},

	_monthsSelected: function(){
		if(!this.enabled || !this.monthsEnabled)
			return;
		this.monthsLink.style.display="none";
		this.monthsBox.domNode.style.display="block";
		setTimeout(__dojo.hitch(this, function(){
			this.monthsBox.focusNode.focus();
		}), 50);
	},

	_monthsDeselected: function(){
		if(!this.monthsEnabled)
			return;
		this.monthsLink.style.display="block";
		this.monthsBox.domNode.style.display="none";
	},

	_monthsChanged: function(){
		this.setMonths(this.yearsBox.getValue()*12+this.monthsBox.getValue());
	},

	_daysSelected: function(){
		if(!this.enabled)
			return;
		this.daysLink.style.display="none";
		this.daysBox.domNode.style.display="block";
		setTimeout(__dojo.hitch(this, function(){
			this.daysBox.focusNode.focus();
		}), 50);
	},

	_daysDeselected: function(){
		this.daysLink.style.display="block";
		this.daysBox.domNode.style.display="none";
	},

	_daysChanged: function(){
		this.setSeconds(((this.daysBox.getValue()*24 + this.hoursBox.getValue())*60
			+ this.minutesBox.getValue())*60 + this.secondsBox.getValue());
	},

	_hoursSelected: function(){
		if(!this.enabled)
			return;
		this.hoursLink.style.display="none";
		this.hoursBox.domNode.style.display="block";
		setTimeout(__dojo.hitch(this, function(){
			this.hoursBox.focusNode.focus();
		}), 50);
	},

	_hoursDeselected: function(){
		this.hoursLink.style.display="block";
		this.hoursBox.domNode.style.display="none";
	},

	_hoursChanged: function(){
		this.setSeconds(((this.daysBox.getValue()*24 + this.hoursBox.getValue())*60
			+ this.minutesBox.getValue())*60 + this.secondsBox.getValue());
	},

	_minutesSelected: function(){
		if(!this.enabled)
			return;
		this.minutesLink.style.display="none";
		this.minutesBox.domNode.style.display="block";
		setTimeout(__dojo.hitch(this, function(){
			this.minutesBox.focusNode.focus();
		}), 50);
	},

	_minutesDeselected: function(){
		this.minutesLink.style.display="block";
		this.minutesBox.domNode.style.display="none";
	},

	_minutesChanged: function(){
		this.setSeconds(((this.daysBox.getValue()*24 + this.hoursBox.getValue())*60
			+ this.minutesBox.getValue())*60 + this.secondsBox.getValue());
	},

	_secondsSelected: function(){
		if(!this.enabled)
			return;
		this.secondsLink.style.display="none";
		this.secondsBox.domNode.style.display="block";
		setTimeout(__dojo.hitch(this, function(){
			this.secondsBox.focusNode.focus();
		}), 50);
	},

	_secondsDeselected: function(){
		this.secondsLink.style.display="block";
		this.secondsBox.domNode.style.display="none";
	},

	_secondsChanged: function(){
		this.setSeconds(((this.daysBox.getValue()*24 + this.hoursBox.getValue())*60
			+ this.minutesBox.getValue())*60 + this.secondsBox.getValue());
	},

	onChange: function(){
	},

	connectOnBlur: function(spinner, blurFunction){
		var self=this;
		__dojo.connect(spinner.focusNode, "onblur", function(event){
			if(event.relatedTarget==spinner.upArrowNode || event.relatedTarget==spinner.downArrowNode)
				return;
			blurFunction.apply(self, []);
		});
		__dojo.connect(spinner.upArrowNode, "onblur", function(event){
			if(event.relatedTarget==spinner.focusNode || event.relatedTarget==spinner.downArrowNode)
				return;
			blurFunction.apply(self, []);
		});
		__dojo.connect(spinner.downArrowNode, "onblur", function(event){
			if(event.relatedTarget==spinner.focusNode || event.relatedTarget==spinner.upArrowNode)
				return;
			blurFunction.apply(self, []);
		});
	}
});
