
__dojo.require("prisms.widget.TabWidget");

__dojo.provide("manager.ApplicationEditor");
__dojo.declare("manager.ApplicationEditor", [prisms.widget.TabWidget, __dijit._Templated, __dijit._Container],
{
	templatePath: "__webContentRoot/view/manager/templates/applicationEditor.html",

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
		else if(event.method=="setValue")
			this.setValue(event.value);
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	shutdown: function(){
		this.setVisible(false);
	},

	setVisible: function(visible){
		PrismsUtils.setTableVisible(this.domNode, visible);
		this.inherited("setVisible", arguments);
	},

	setValue: function(value){
		this.dataLock=true;
		this.value = value;
		try{
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
		} finally{
			this.dataLock=false;
		}
	}
});
