
dojo.provide("manager.UserGroupAssocEditor");
dojo.declare("manager.UserGroupAssocEditor", [dijit._Widget, dijit._Container, dijit._Contained, dijit._Templated],
{
	templatePath: "/prisms/view/manager/templates/userGroupAssocEditor.html",

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

	setVisible: function(visible){
		if(visible)
			this.visibilityNode.style.display="table";
		else
			this.visibilityNode.style.display="none";
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
			this.groupDescription.innerHTML=value.group.descrip;
			this.groupHasUserText.innerHTML="&nbsp;&nbsp;&nbsp;User '"+value.user
				+"' membership in group  '"+value.group.name+"':";
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