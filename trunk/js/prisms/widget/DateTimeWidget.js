__dojo.require("dijit.form.DateTextBox");
__dojo.require("dijit.form.TimeTextBox");

__dojo.provide("prisms.widget.DateTimeWidget");
__dojo.declare("prisms.widget.DateTimeWidget", [__dijit._Widget, __dijit._Templated, __dijit._Container], {

	templatePath: "__webContentRoot/view/prisms/templates/dateTimeWidget.html",

	widgetsInTemplate: true,

	displayDate: true,

	displayTime: true,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.setValue(new Date());
		if(!this.displayDate)
		{
			PrismsUtils.setTableCellVisible(this.dateCell, false);
		}
		if(!this.displayTime)
		{
			PrismsUtils.setTableCellVisible(this.timeCell, false);
			PrismsUtils.setTableCellVisible(this.zCell, false);
		}
	},

	setVisible: function(visible){
		if(__dojo.isIE && visible)
			this.domNode.style.display="block";
		else if(visible)
			this.domNode.style.display="table";
		else
			this.domNode.style.display="none";
	},

	isDisabled: function(){
		return this.dateBox.focusNode.disabled;
	},

	setDisabled: function(disabled){
		this.dateBox.setAttribute("disabled", disabled);
		this.timeBox.setAttribute("disabled", disabled);
		this.dateBox.focusNode.disabled=disabled;
		this.timeBox.focusNode.disabled=disabled;
	},

	setDisplayDate: function(display){
		if(display==this.displayDate)
			return;
		this.displayDate=display;
		PrismsUtils.setTableCellVisible(this.dateCell, display);
	},

	setDisplayTime: function(display){
		if(display==this.displayTime)
			return;
		this.displayTime=display;
		PrismsUtils.setTableCellVisible(this.timeCell, display);
		PrismsUtils.setTableCellVisible(this.zCell, display);
	},

	setValue: function(value, notifyUser){
		if(!value && value!=0)
			value=new Date();
		else if(typeof value.getTime!="function")
		{
			var valueD=new Date();
			valueD.setTime(value);
			value=valueD;
		}
		var dateChanged=false;
		var timeChanged=false;
		value.setTime(value.getTime()+value.getTimezoneOffset()*60000);
		this.dataLock=true;
		try{
			if(notifyUser)
			{
				var dateVal=this.dateBox.getValue()
				dateChanged=dateVal.getUTCFullYear()!=value.getUTCFullYear()
					|| dateVal.getUTCMonth()!=value.getUTCMonth()
					|| dateVal.getUTCDate()!=value.getUTCDate();
				var timeVal=this.timeBox.getValue();
				timeChanged=timeVal.getUTCHours()!=value.getUTCHours()
					|| timeVal.getUTCMinutes()!=value.getUTCMinutes();
			}
			this.dateBox.setValue(value);
			this.timeBox.setValue(value);
			this._time=new Date(this.timeBox.getValue().getTime());
		} finally{
			this.dataLock=false;
		}
		if(dateChanged)
			this.dateBox.domNode.style.backgroundColor="#b0b040";
		else
			this.dateBox.domNode.style.backgroundColor="#ffffff";
		if(timeChanged)
			this.timeBox.domNode.style.backgroundColor="#b0b040";
		else
			this.timeBox.domNode.style.backgroundColor="#ffffff";
	},

	getValue: function(){
		var dateVal=this.dateBox.getValue();
		var timeVal=this.timeBox.getValue();
		var ret=new Date(dateVal.getTime());
		ret.setHours(timeVal.getHours());
		ret.setMinutes(timeVal.getMinutes());
		ret.setSeconds(timeVal.getSeconds());
		ret.setTime(ret.getTime()-ret.getTimezoneOffset()*60000);
		return ret;
	},

	_dateBoxChanged: function(){
		if(this.dataLock)
			return;
		this.dateBox.domNode.style.backgroundColor="#ffffff";
		this.timeBox.domNode.style.backgroundColor="#ffffff";
		this.onChange();
	},

	_timeBoxChanged: function(){
		if(this.dataLock)
			return;
		var hDiff=this.timeBox.getValue().getHours()-this._time.getHours();
		if(hDiff>12)
		{
			var dateVal=this.dateBox.getValue();
			dateVal.setDate(dateVal.getDate()-1);
			this.dateBox.setValue(dateVal);
		}
		else if(hDiff<-12)
		{
			var dateVal=this.dateBox.getValue();
			dateVal.setDate(dateVal.getDate()+1);
			this.dateBox.setValue(dateVal);
		}
		this._time=new Date(this.timeBox.getValue().getTime());
		this.dateBox.domNode.style.backgroundColor="#ffffff";
		this.timeBox.domNode.style.backgroundColor="#ffffff";
		this.onChange();
	},

	onChange: function(){
	}
});
