
__dojo.require("dijit.form.TextBox");
__dojo.require("dijit.form.CheckBox");
__dojo.require("prisms.widget.TimeAmountEditor");

__dojo.provide("manager.ClientEditor");
__dojo.declare("manager.ClientEditor", [dijit._Widget, __dijit._Templated, __dijit._Container],
{
	templatePath: "__webContentRoot/view/manager/templates/clientEditor.html",

	widgetsInTemplate: true,

	prisms: null,

	pluginName: "No pluginName specified",

	postCreate: function(){
		this.inherited("postCreate", arguments);

		if(!this.prisms)
		{
			var prisms=PrismsUtils.getPrisms(this);
			if(prisms)
				this.setPrisms(prisms);
			else
				console.error("No prisms parent for plugin "+this.pluginName);
		}
		this.dataLock=true;
		try{
			this.timeoutEditor.setValue(15*60);
		} finally{
			this.dataLock=false;
		}
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.prisms.loadPlugin(this);
	},

	processEvent: function(event){
		if(event.method=="setVisible")
			this.setVisible(event.visible);
		else if(event.method=="setEnabled")
			this.setEnabled(event.enabled);
		else if(event.method=="setValue")
			this.setValue(event.value);
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	setVisible: function(visible){
		this.domNode.style.display=(visible ? "block" : "none");
	},

	setEnabled: function(enabled){
		this.isEnabled=enabled;
		this.nameField.setAttribute("disabled", !enabled);
		this.descripField.setAttribute("disabled", !enabled);
		this.configXmlField.setAttribute("disabled", !enabled);
		this.validatorClassField.setAttribute("disabled", !enabled);
		this.serializerClassField.setAttribute("disabled", !enabled);
		this.serviceCheck.setAttribute("disabled", !enabled);
		this.timeoutEditor.setDisabled(!enabled);
		this.allowAnonymousCheck.setAttribute("disabled", !enabled);
	},

	setValue: function(value){
		this.dataLock=true;
		this.value = value;
		try{
			this.nameField.setValue(value.name);
			if(typeof value.descrip=="undefined")
			{
				this.descripLabel.style.visibility="hidden";
				this.descripField.domNode.parentNode.style.visibility="hidden";
			}
			else
			{
				this.descripLabel.style.visiblity="visible";
				this.descripField.domNode.parentNode.style.visibility="visible";
				if(value.descrip)
					this.descripField.setValue(value.descrip);
				else
					this.descripField.setValue("");
			}
			if(typeof value.configXML=="undefined")
			{
				this.configXmlLabel.style.visibility="hidden";
				this.configXmlField.domNode.parentNode.style.visibility="hidden";
			}
			else
			{
				this.configXmlField.domNode.style.backgroundColor="#ffffff";
				this.configXmlLabel.style.visiblity="visible";
				this.configXmlField.domNode.parentNode.style.visibility="visible";
				if(value.configXML)
					this.configXmlField.setValue(value.configXML);
				else
					this.configXmlField.setValue("");
			}
			if(typeof value.validatorClass=="undefined")
			{
				this.validatorClassLabel.style.visibility="hidden";
				this.validatorClassField.domNode.parentNode.style.visibility="hidden";
			}
			else
			{
				this.validatorClassField.domNode.style.backgroundColor="#ffffff";
				this.validatorClassLabel.style.visiblity="visible";
				this.validatorClassField.domNode.parentNode.style.visibility="visible";
				if(value.validatorClass)
					this.validatorClassField.setValue(value.validatorClass);
				else
					this.validatorClassField.setValue("");
			}
			if(typeof value.serializerClass=="undefined")
			{
				this.serializerClassLabel.style.visibility="hidden";
				this.serializerClassField.domNode.parentNode.style.visibility="hidden";
			}
			else
			{
				this.serializerClassField.domNode.style.backgroundColor="#ffffff";
				this.serializerClassLabel.style.visiblity="visible";
				this.serializerClassField.domNode.parentNode.style.visibility="visible";
				if(value.serializerClass)
					this.serializerClassField.setValue(value.serializerClass);
				else
					this.serializerClassField.setValue("");
			}
			this.serviceCheck.setAttribute("checked", value.isService);
			this.timeoutEditor.setValue(value.sessionTimeout);
			this.allowAnonymousCheck.setAttribute("checked", value.allowAnonymous);
		} finally{
			this.dataLock=false;
		}
	},

	_nameChanged: function(){
		if(this.dataLock)
			return;
		var newName=this.nameField.getValue();
		this.prisms.callApp(this.pluginName, "nameChanged", {name: newName});
	},

	_descripChanged: function(){
		if(this.dataLock)
			return;
		var newDescrip=this.descripField.getValue();
		this.prisms.callApp(this.pluginName, "descripChanged", {descrip: newDescrip});
	},

	_configXmlChanged: function(){
		if(this.dataLock)
			return;
		var newXML=this.configXmlField.getValue();
		this.prisms.callApp(this.pluginName, "configXmlChanged", {configXML: newXML});
	},

	_serializerClassChanged: function(){
		if(this.dataLock)
			return;
		var newClass=this.serializerClassField.getValue();
		this.prisms.callApp(this.pluginName, "serializerClassChanged", {validatorClass: newClass});
	},

	_validatorClassChanged: function(){
		if(this.dataLock)
			return;
		var newClass=this.validatorClassField.getValue();
		this.prisms.callApp(this.pluginName, "validatorClassChanged", {validatorClass: newClass});
	},

	_serviceChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "setService", {service: this.serviceCheck.checked});
	},

	_timeoutChanged: function(){
		if(this.dataLock)
			return;
		timeout=this.timeoutEditor.getValue().seconds;
		this.prisms.callApp(this.pluginName, "setTimeout", {timeout: timeout});
	},

	_allowAnonymousChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "setAllowAnonymous", {allowed: this.allowAnonymousCheck.checked});
	}
});
