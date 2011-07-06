
__dojo.require("dijit.form.CheckBox");
__dojo.require("prisms.widget.TimeAmountEditor");

__dojo.provide("manager.ClientEditor");
__dojo.declare("manager.ClientEditor", [prisms.widget.TabWidget, __dijit._Templated],
{
	templatePath: "__webContentRoot/view/manager/templates/clientEditor.html",

	widgetsInTemplate: false,

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
			this.setVisible(event.visible, event.show);
		else if(event.method=="setValue")
			this.setValue(event.value);
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	shutdown: function(){
		this.setVisible(false);
	},

	setVisible: function(visible, show){
		PrismsUtils.setTableVisible(this.domNode, visible);
		if(show)
			this.setSelected(true, true);
	},

	setValue: function(value){
		this.dataLock=true;
		this.value = value;
		this.nameField.innerHTML=PrismsUtils.fixUnicodeString(value.name);
		if(typeof value.descrip=="undefined")
		{
			this.descripLabel.style.visibility="hidden";
			this.descripField.parentNode.style.visibility="hidden";
		}
		else
		{
			this.descripLabel.style.visiblity="visible";
			this.descripField.parentNode.style.visibility="visible";
			if(value.descrip)
				this.descripField.innerHTML=PrismsUtils.fixUnicodeString(value.descrip);
			else
				this.descripField.innerHTML="";
		}
		this.serviceCell.innerHTML=value.isService ? "Yes" : "No";
		this.timeoutCell.innerHTML=value.sessionTimeout;
		this.allowAnonymousCell.innerHTML=value.allowAnonymous ? "Yes" : "No";
	}
});
