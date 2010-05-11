
dojo.require("dijit.form.TextBox");

dojo.provide("manager.GroupEditor");
dojo.declare("manager.GroupEditor", [dijit._Widget, dijit._Contained, dijit._Templated, dijit._Container],
{
	templatePath: "/prisms/view/manager/templates/groupEditor.html",

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
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	/**
	 * Overrides the setVisible method in TabWidget
	 */
	setVisible: function(visible){
		this.domNode.style.display=(visible ? "table" : "none");
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
		this.descripField.disabled=!enabled;
	},

	setValue: function(value){
		this.dataLock=true;
		this.value = value;
		try{
			this.nameField.setValue(value.name);
			this.descripField.value=value.descrip;
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
		var newDescrip=this.descripField.value;
		this.prisms.callApp(this.pluginName, "descripChanged", {descrip: newDescrip});
	}
});
