
__dojo.provide("manager.UserPermissionDisplay");
__dojo.declare("manager.UserPermissionDisplay", [__dijit._Widget, __dijit._Container, __dijit._Contained, __dijit._Templated],
{
	templatePath: "__webContentRoot/view/manager/templates/userPermissionDisplay.html",

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

	shutdown: function(){
		this.setValue({});
	},

	setValue: function(value){
		if(!value.permission || !value.permission.descrip)
			this.permissionDescription.innerHTML="";
		else
			this.permissionDescription.innerHTML=PrismsUtils.fixUnicodeString(value.permission.descrip);
	}
});