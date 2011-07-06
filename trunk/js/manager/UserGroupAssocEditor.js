
__dojo.provide("manager.UserGroupAssocEditor");
__dojo.declare("manager.UserGroupAssocEditor", [__dijit._Widget, __dijit._Container, __dijit._Contained, __dijit._Templated],
{
	templatePath: "__webContentRoot/view/manager/templates/userGroupAssocEditor.html",

	widgetsInTemplate: true,

	prisms: null,

	pluginName: "No pluginName specified",

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.visibilityNode=this.domNode;
		this.setVisible(false);

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
		if(event.method=="setEnabled")
			this.setEnabled(event.enabled);
		else if(event.method=="setData")
			this.setValue(event);
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
		this.groupHasUserCheck.setAttribute("disabled", !enabled);
	},

	setValue: function(value){
		if(!value.user || !value.group)
		{
			this.setVisible(false);
			return;
		}
		this.setVisible(true);
		this.dataLock=true;
		try{
			this.groupDescription.innerHTML=PrismsUtils.fixUnicodeString(value.group.descrip);
			this.groupHasUserText.innerHTML=PrismsUtils.fixUnicodeString(
				"&nbsp;&nbsp;&nbsp;User '"+value.user+"' membership in group  '"+value.group.name+"':");
			this.groupHasUserCheck.setAttribute("checked", value.group.selected);
		} finally{
			this.dataLock=false;
		}
	},

	_groupHasUserChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "membershipChanged",
			{"isMember": this.groupHasUserCheck.getValue() ? true : false});
	}
});