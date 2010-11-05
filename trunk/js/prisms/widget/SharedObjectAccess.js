__dojo.require("dijit.form.CheckBox");

__dojo.provide("prisms.widget.SharedObjectAccess");
__dojo.declare("prisms.widget.SharedObjectAccess", [__dijit._Widget, __dijit._Templated],
{  
	templatePath: "__webContentRoot/view/prisms/templates/sharedObjectAccess.html",

	widgetsInTemplate: true,

	enabled: true,

	viewColor: "#8080ff",

	editColor: "#60d060",

	allViewable: false,

	allEditable: false,

	theValue: [],

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.setValue(this.theValue);
	},

	setAllViewable: function(av){
		this.allViewable=av;
		if(!av)
			this.setAllEditable(false);
		this.viewCheck.setAttribute("disabled", !this.enabled || this.allViewable);
		this.evalColors();
	},

	setAllEditable: function(ae){
		this.allEditable=ae;
		if(ae)
			this.setAllViewable(true);
		this.editCheck.setAttribute("disabled", !this.enabled || this.allEditable);
		this.evalColors();
	},

	setVisible: function(visible){
		if(visible)
			this.domNode.style.display="table";
		else
			this.domNode.style.display="none";
	},

	setEnabled: function(enabled){
		this.enabled=enabled;
		this.viewCheck.setAttribute("disabled", !this.enabled || this.allViewable);
		this.editCheck.setAttribute("disabled", !this.enabled || this.allEditable);
	},

	getValue: function(){
		return this.theValue;
	},

	setAllGroups: function(groups){
		this.dataLock=true;
		try{
			var selGroup=null;
			if(this.groupSelect.selectedIndex>=0)
				selGroup=this.groupSelect.options[this.groupSelect.selectedIndex].text;
			while(this.groupSelect.options.length>0)
				this.groupSelect.remove(this.groupSelect.options.length-1);
			var selIdx=-1;
			for(var g=0;g<groups.length;g++)
			{
				var option=document.createElement("option");
				option.text=groups[g];
				if(this.canEdit(groups[g]))
					option.style.backgroundColor=this.editColor;
				else if(this.canView(groups[g]))
					option.style.backgroundColor=this.viewColor;
				else
					option.style.backgroundColor="#ffffff";
				if(groups[g]==selGroup)
					selIdx=g;
				if(__dojo.isIE > 6)
					this.groupSelect.add(option);
				else
					this.groupSelect.add(option, null);
			}
			if(selIdx>=0)
				this.groupSelect.selectedIndex=selIdx;
		} finally{
			this.dataLock=false;
		}
		this.evalColors();
	},

	setValue: function(value){
		this.theValue=value;
		this.evalColors();
	},

	evalColors: function(){
		for(var i=0;i<this.groupSelect.options.length;i++)
		{
			var group=this.groupSelect.options[i].text;
			var canEdit=this.canEdit(group);
			var canView=canEdit || this.canView(group);
			var color;
			if(canEdit)
				color=this.editColor;
			else if(canView)
				color=this.viewColor;
			else
				color="#ffffff";
			this.groupSelect.options[i].style.backgroundColor=color;
		}
		this._groupChanged();
	},

	_groupChanged: function(){
		if(this.dataLock)
			return;
		var selIdx=this.groupSelect.selectedIndex;
		this.dataLock=true;
		try{
			if(selIdx<0)
			{
				this.groupSelect.style.backgroundColor="#ffffff";
				this.viewCheck.setAttribute("checked", false);
				this.viewCheck.setAttribute("disabled", true);
				this.editCheck.setAttribute("checked", false);
				this.editCheck.setAttribute("disabled", true);
			}
			else
			{
				var group=this.groupSelect.options[selIdx].text;
				var canEdit=this.canEdit(group);
				var canView=canEdit || this.canView(group);
				var color;
				if(canEdit)
					color=this.editColor;
				else if(canView)
					color=this.viewColor;
				else
					color="#ffffff";
				this.groupSelect.style.backgroundColor=color;
				this.groupSelect.options[selIdx].style.backgroundColor=color;

				this.viewCheck.setAttribute("disabled", !this.enabled || this.allViewable);
				this.viewCheck.setAttribute("checked", canView);
				this.editCheck.setAttribute("disabled", !this.enabled || this.allEditable);
				this.editCheck.setAttribute("checked", canEdit);
			}
		} finally{
			this.dataLock=false;
		}
	},

	getGroupIndex: function(userName){
		for(var v=0;v<this.theValue.length;v++)
			if(this.theValue[v].userName==userName)
				return v;
		return -1;
	},

	canView: function(userName){
		if(this.allViewable)
			return true;
		var gIdx=this.getGroupIndex(userName);
		if(gIdx<0)
			return false;
		return this.theValue[gIdx].canView;
	},

	canEdit: function(userName){
		if(this.allEditable)
			return true;
		var gIdx=this.getGroupIndex(userName);
		if(gIdx<0)
			return false;
		return this.theValue[gIdx].canEdit;
	},

	_viewChanged: function(){
		if(this.dataLock)
			return;
		var selIdx=this.groupSelect.selectedIndex;
		var checked=this.viewCheck.checked ? true : false;
		var group=this.groupSelect.options[selIdx].text;
		var gIdx=this.getGroupIndex(group);
		if(gIdx<0)
		{
			gIdx=this.theValue.length;
			this.theValue.push({userName: group, canView: false, canEdit: false});
		}
		this.theValue[gIdx].canView=checked;
		if(!checked)
		{
			this.theValue[gIdx].canEdit=false;
			this.dataLock=true;
			try{
				this.editCheck.setAttribute("checked", false);
			} finally{
				this.dataLock=false;
			}
		}
		this.onChange(this.theValue[gIdx]);
	},

	_editChanged: function(){
		if(this.dataLock)
			return;
		var selIdx=this.groupSelect.selectedIndex;
		var checked=this.editCheck.checked ? true : false;
		var group=this.groupSelect.options[selIdx].text;
		var gIdx=this.getGroupIndex(group);
		if(gIdx<0)
		{
			gIdx=this.theValue.length;
			this.theValue.push({userName: group, canView: false, canEdit: false});
		}
		this.theValue[gIdx].canEdit=checked;
		if(checked)
		{
			this.theValue[gIdx].canView=true;
			this.dataLock=true;
			try{
				this.viewCheck.setAttribute("checked", true);
			} finally{
				this.dataLock=false;
			}
		}
		this.onChange(this.theValue[gIdx]);
	},

	onChange: function(change){
		var selIdx=this.groupSelect.selectedIndex;
		this._groupChanged();
	}
});
