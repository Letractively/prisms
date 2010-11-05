
__dojo.require("dijit.form.CheckBox");
__dojo.require("prisms.widget.TimeAmountEditor");

__dojo.provide("manager.ClientEditor");
__dojo.declare("manager.ClientEditor", [__dijit._Widget, __dijit._Templated, __dijit._Container],
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
	},

	setValue: function(value){
		this.dataLock=true;
		this.value = value;
		try{
			this.nameField.innerHTML=value.name;
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
					this.descripField.innerHTML=value.descrip;
				else
					this.descripField.innerHTML="";
			}
			this.serviceCheck.setAttribute("checked", value.isService);
			this.timeoutEditor.setValue(value.sessionTimeout);
			this.allowAnonymousCheck.setAttribute("checked", value.allowAnonymous);
		} finally{
			this.dataLock=false;
		}
	}
});
