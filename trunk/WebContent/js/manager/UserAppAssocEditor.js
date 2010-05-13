
dojo.provide("manager.UserAppAssocEditor");
dojo.declare("manager.UserAppAssocEditor", [dijit._Widget, dijit._Container, dijit._Contained, dijit._Templated],
{
	templatePath: "/prisms/view/manager/templates/userAppAssocEditor.html",

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
			this.setData(event);
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
		this.canAccessCheck.setAttribute("disabled", !enabled);
	},

	setData: function(data){
		if(!data.user || !data.app)
		{
			this.setVisible(false);
			return;
		}
		this.setVisible(true);
		this.dataLock=true;
		try{
			this.userAccessText.innerHTML="User '"+data.user+"' access to application '"+data.app+"':";
			this.canAccessCheck.setAttribute("checked", data.accessible);
		} finally{
			this.dataLock=false;
		}
	},

	_canAccessChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "accessChanged",
			{"accessible": this.canAccessCheck.getValue() ? true : false});
	}
});