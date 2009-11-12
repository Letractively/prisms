
dojo.provide("manager.UserPermissionDisplay");
dojo.declare("manager.UserPermissionDisplay", [dijit._Widget, dijit._Container, dijit._Contained, dijit._Templated],
{
	templatePath: "/prisms/view/manager/templates/userPermissionDisplay.html",

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
		if(event.method=="setData")
			this.setValue(event);
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	setValue: function(value){
		if(!value.permission || !value.permission.descrip)
			this.permissionDescription.innerHTML="";
		else
			this.permissionDescription.innerHTML=value.permission.descrip;
	}
});