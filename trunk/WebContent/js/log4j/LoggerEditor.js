dojo.require("dijit.form.CheckBox");
dojo.require("dijit.form.TextBox");

dojo.provide("log4j.LoggerEditor");
dojo.declare("log4j.LoggerEditor", [dijit._Widget, dijit._Templated, dijit._Container], {

	prisms: null,

	pluginName: "No pluginName specified",

	templatePath: "/prisms/view/log4j/templates/loggerEditor.html",
	
	userEnabledStatus: false,

	widgetsInTemplate: true,

	searchPrisms: true,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.enabled=true;

		if(!this.prisms && this.searchPrisms)
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
		else if(event.method=="setLevels")
			this.setLevels(event.levels);
		else if(event.method=="setLogger")
			this.setLogger(event.logger);
		else
			throw new Error("Unrecognized event " + dojo.toJson(event))
	},
	
	setLevels: function(levels)
	{
		while(this.levelCombo.options.length>0)
			this.levelCombo.remove(this.levelCombo.options.length-1);
		for(var level in levels)
		{
			var option=document.createElement("option");
			option.text=levels[level];
			if(dojo.isIE > 6)
				this.levelCombo.add(option, 0);
			else
				this.levelCombo.add(option, null);
		}
	},
	
	setEnabled: function(enabled)
	{
		this.enabled=enabled;
		this.levelCheck.setAttribute("disabled", !enabled);
		if(this.levelCheck.checked)
			this.levelCombo.disabled = !enabled;
		this.additivity.setAttribute("disabled", !enabled);
	},
	
	setVisible: function(visible)
	{
		this.domNode.style.display = visible ? "block" : "none";
	},
	
	setLogger: function(logger){
		this.logger=logger;
		this.dataLock=true;
		try{
			if(!logger)
			{
				this.setVisible(false);
				return;
			}
			this.setVisible(true);
			this.loggerName.innerHTML=logger.name;
			if(logger.level)
			{
				this.levelCheck.setAttribute("checked", true);
				this.levelCombo.disabled=!this.enabled;
				for(var o=0;o<this.levelCombo.options.length;o++)
				{
					if(this.levelCombo.options[o].text==logger.level)
					{
						this.levelCombo.selectedIndex=o;
						break;
					}
				}
			}
			else
			{
				this.levelCheck.setAttribute("checked", false);
				this.levelCombo.disabled=true;
				this.levelCombo.selectedIndex=-1;
			}
			this.effectiveLevel.innerHTML=logger.effectiveLevel;
			this.additivity.setAttribute("checked", logger.additivity);
		} finally{
			this.dataLock=false;
		}
	},

	_closeEditor: function(){
		this.prisms.callApp(this.pluginName, "clear");
	},

	_levelChanged: function() {
		if(this.dataLock)
			return;
		var level;
		if(!this.levelCheck.checked)
			level=null;
		else
		{
			if(this.levelCombo.selectedIndex<0)
			{
				this.dataLock=true;
				try{
					for(var o=0;o<this.levelCombo.options.length;o++)
					{
						if(this.levelCombo.options[o].text==this.logger.effectiveLevel)
						{
							this.levelCombo.selectedIndex=o;
							break;
						}
					}
				} finally{
					this.dataLock=false;
				}
			}
			level=this.levelCombo.options[this.levelCombo.selectedIndex].text;
		}
		this.prisms.callApp(this.pluginName, "levelChanged", {level: level});
	},
	
	_additivityChanged: function() {
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "additivityChanged", {additivity: this.additivity.checked});
	},

	_printMessage: function(){
		if(this.dataLock)
			return;
		var message=this.messageBox.getValue();
		if(message==null || message.length==0)
		{
			alert("Enter a message to print");
			return;
		}
		this.prisms.callApp(this.pluginName, "printMessage", {message: message});
	},

	_onKeyPress: function(event){
		if(event.keyCode==dojo.keys.ENTER)
			this._printMessage();
	}
});