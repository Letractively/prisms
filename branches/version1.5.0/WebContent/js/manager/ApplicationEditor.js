
dojo.require("dijit.form.TextBox");

dojo.require("prisms.widget.TabWidget");

dojo.provide("manager.ApplicationEditor");
dojo.declare("manager.ApplicationEditor", [prisms.widget.TabWidget, dijit._Templated, dijit._Container],
{
	templatePath: "/prisms/view/manager/templates/applicationEditor.html",

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
		else if(event.method=="setConfigClassValid")
		{
			console.log("valid="+event.valid);
			if(event.valid)
				this.configClassField.domNode.style.backgroundColor="#ffffff";
			else
				this.configClassField.domNode.style.backgroundColor="#ff4040";
		}
		else if(event.method=="setConfigXmlValid")
		{
			if(event.valid)
				this.configXmlField.domNode.style.backgroundColor="#ffffff";
			else
				this.configXmlField.domNode.style.backgroundColor="#ff4040";
		}
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	/**
	 * Overrides the setVisible method in TabWidget
	 */
	setVisible: function(visible){
		this.domNode.style.display=(visible ? "block" : "none");
	},

	setEnabled: function(enabled){
		var self=this;
		//TODO: This is important for subtabs later
//		if(!this.initialized)
//		{
//			this.taskEditorTimeoutCount=0;
//			this.initTaskEditorTimeout=setTimeout(function(){
//				var tabs=dijit.byId("managerUserTabContainer");
//				var taskTab=dijit.byId("managerUserEditorTab");
//				var doClear=false;
//				if(tabs && taskTab)
//				{
//					tabs.selectChild(taskTab);
//					doClear=true;
//				}
//				self.taskEditorTimeoutCount++;
//				doClear|=self.taskEditorTimeoutCount>=10;
//				if(doClear)
//				{
//					clearTimeout(self.initTaskEditorTimeout);
//					delete self.initTaskEditorTimeout;
//					delete self.taskEditorTimeoutCount;
//					self.initialized=true;
//				}
//			}, 250);
//		}
		this.nameField.setAttribute("disabled", !enabled);
		this.descripField.setAttribute("disabled", !enabled);
		this.configClassField.setAttribute("disabled", !enabled);
		this.configXmlField.setAttribute("disabled", !enabled);
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
			if(typeof value.configClass=="undefined")
			{
				this.configClassLabel.style.visibility="hidden";
				this.configClassField.domNode.parentNode.style.visibility="hidden";
			}
			else
			{
				this.configClassField.domNode.style.backgroundColor="#ffffff";
				this.configClassLabel.style.visiblity="visible";
				this.configClassField.domNode.parentNode.style.visibility="visible";
				if(value.configClass)
					this.configClassField.setValue(value.configClass);
				else
					this.configClassField.setValue("");
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

	_configClassChanged: function(){
		if(this.dataLock)
			return;
		var newClass=this.configClassField.getValue();
		this.prisms.callApp(this.pluginName, "configClassChanged", {configClass: newClass});
	},

	_configXmlChanged: function(){
		if(this.dataLock)
			return;
		var newXML=this.configXmlField.getValue();
		this.prisms.callApp(this.pluginName, "configXmlChanged", {configXML: newXML});
	}
});
