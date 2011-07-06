
__dojo.require("prisms.widget.TabWidget");

__dojo.provide("manager.PerformanceDisplay");
__dojo.declare("manager.PerformanceDisplay", [prisms.widget.TabWidget, __dijit._Templated, __dijit._Container],
{
	templatePath: "__webContentRoot/view/manager/templates/performanceDisplay.html",

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
//			else
//				console.error("No prisms parent for plugin "+this.pluginName);
		}
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.prisms.loadPlugin(this);
	},

	processEvent: function(event){
		if(event.method=="setVisible")
			this.setVisible(event.visible);
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	shutdown: function(){
		this.setVisible(false);
	},

	_closeDisplay: function(){
		this.prisms.callApp(this.pluginName, "close");
	}
});
