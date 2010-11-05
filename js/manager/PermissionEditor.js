
__dojo.provide("manager.PermissionEditor");
__dojo.declare("manager.PermissionEditor", [__dijit._Widget, __dijit._Container, __dijit._Contained, __dijit._Templated],
{
	templatePath: "__webContentRoot/view/manager/templates/permissionEditor.html",

	widgetsInTemplate: true,

	prisms: null,

	pluginName: "No pluginName specified",

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.visibilityNode=this.domNode;

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
		else if(event.method=="setData")
			this.setValue(event.data);
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	shutdown: function(){
		this.setVisible(false);
	},

	setVisible: function(visible){
		PrismsUtils.setTableVisible(this.domNode, visible);
	},

	setEnabled: function(enabled){
		this.groupHasPermissionCheck.setAttribute("disabled", !enabled);
	},

	setValue: function(value){
		if(!value)
		{
			this.setVisible(false);
			return;
		}
		this.setVisible(true);
		this.dataLock=true;
		try{
			this.nameText.innerHTML=value.name;
			this.descripField.innerHTML=value.description;
			if(value.group)
			{
				PrismsUtils.setTableRowVisible(this.groupHasPermissionsRow, true);
				this.groupHasPermissionText.innerHTML="Group "+value.group.name+" has permission "
					+value.name;
				this.groupHasPermissionCheck.setAttribute("checked", value.group.selected);
			}
			else
				PrismsUtils.setTableRowVisible(this.groupHasPermissionsRow, false);
		} finally{
			this.dataLock=false;
		}
	},

	_groupHasPermissionChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "membershipChanged",
			{"isMember": this.groupHasPermissionCheck.getValue() ? true : false});
	}
});