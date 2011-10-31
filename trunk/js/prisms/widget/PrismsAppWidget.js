
__dojo.require("dijit.layout.BorderContainer");
__dojo.require("dijit.layout.ContentPane");
__dojo.require("dijit.form.Button");
__dojo.require("dijit.Toolbar");
__dojo.require("dijit.Menu");

__dojo.require("prisms.DojoPrisms");
__dojo.require("prisms.PrismsUtils");
__dojo.require("prisms.widget.UI");
__dojo.require("prisms.widget.Preferences");
__dojo.require("prisms.widget.StatusToaster");

__dojo.provide("prisms.widget.PrismsAppWidget");
__dojo.declare("prisms.widget.PrismsAppWidget", [__dijit._Widget, __dijit._Container, __dijit._Templated], {
	prisms: null,

	templatePath: "__webContentRoot/view/prisms/templates/prismsAppWidget.html",

	appHtml: "No Application HTML specified",

	widgetsInTemplate: true,

	withLogin: true,

	withServerTime: true,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		if(!this.prisms)
			throw new Error("No prisms link set for app widget");
		this.dojoPrisms.setLoginMenu(this.loginMenu);
		this.aboutMenuItem.setLabel("About "+this.prisms.application);
		if(!this.withLogin || !__showLogin)
			this.loginMenu.domNode.style.display="none";

		if(!this.withServerTime)
			this.serverTimeToggle.domNode.style.display="none";

		this.prismsContent.setHref(this.appHtml);
	},

	appContentLoaded: function(){
		this.scanAttaches(this.prismsContent);
		this.setPrisms(this.prisms);
		this.prisms.prismsConnect();
		this.visibleContent.resize();
	},

	addToolbarButton: function(text, callback){
		var button=new __dijit.form.Button({label: text});
		__dojo.connect(button, "onClick", this, callback);
		button.domNode.style.cssFloat="right";
		this.toolbar.addChild(button);
	},

	addHelpMenuItem: function(text, callback){
		var item=new __dijit.MenuItem({label: text});
		__dojo.connect(item, "onClick", this, callback);
		this.helpMenu.dropDown.addChild(item, 0);
	},

	_setPreferencesVisible: function(visible){
		if(!visible)
		{
			this.preferencesMenuItem.domNode.style.display="none";
			if(!this.withServerTime)
				this.preferencesMenu.domNode.style.display="none";
		}
	},

	editPreferences: function(){
		this.prisms.callApp('Preferences', 'editPreferences');
	},

	toggleSvrTime: function(){
		var showTime=this.showServerTime ? false : true;
		if(showTime)
		{
			if(!this.prisms)
				return;
			var preTime=new Date().getTime();
			var svrTime=this.prisms.callServerSync("getServerTime")[0];
			var postTime=new Date().getTime();
			svrTime.localRef=(preTime+postTime)/2;
			this.showServerTime=svrTime;
			this.redrawServerTime();
			this.serverTimeInterval=window.setInterval(__dojo.hitch(this, function(){
				this.redrawServerTime();
			}), 100);
			this.serverTimeToggle.setLabel("Hide Server Time");
		}
		else
		{
			if(this.serverTimeInterval)
				window.clearInterval(this.serverTimeInterval);
			this.showServerTime=null;
			this.serverTimeDiv.innerHTML="";
			this.serverTimeToggle.setLabel("Show Server Time");
		}
	},

	redrawServerTime: function(){
		var svrTime=this.showServerTime;
		var newLocal=new Date().getTime();
		if(newLocal-svrTime.localRef>10*60000)
		{
			var preTime=new Date().getTime();
			svrTime=this.prisms.callServerSync("getServerTime")[0];
			var postTime=new Date().getTime();
			svrTime.localRef=(preTime+postTime)/2;
			this.showServerTime=svrTime;
		}
		var newOffset=newLocal-svrTime.localRef;
		var mil=svrTime.millis+newOffset;
		if(!this._timeMode) // Zulu/GMT
			mil-=svrTime.timeZoneOffset
		else // Server's default time zone
		{}
		var sec=svrTime.second+Math.floor(mil/1000);
		mil%=1000;
		var min=svrTime.minute+Math.floor(sec/60);
		sec%=60;
		if(sec==svrTime.displaySecond)
			return;
		svrTime.displaySecond=sec;
		var hour=svrTime.hour+Math.floor(min/60);
		min%=60;
		var day=svrTime.day-1+Math.floor(hour/24);
		hour%=24;
		var month=svrTime.month;
		var year=svrTime.year;
		while(day>=PrismsUtils.getMaxDay(month, year))
		{
			month++;
			if(month>12)
			{
				month=1;
				year++;
			}
		}
		day++;
		if(sec<10)
			sec="0"+sec;
		if(min<10)
			min="0"+min;
		if(hour<10)
			hour="0"+hour;
		if(day<10)
			day="0"+day;
		var display="Server Time: "+day+PrismsUtils.monthStrings[month-1]+year
			+" "+hour+min+"."+sec;
		if(!this._timeMode)
			display+="Z";
		else
			display+=" "+svrTime.timeZoneDisplay;
		this.serverTimeDiv.innerHTML=display;
	},

	_switchTimeZone: function(){
		if(!this.showServerTime)
			return;
		var timeMode=this._timeMode;
		if(typeof timeMode!="number")
			timeMode=0;
		timeMode++;
		if(timeMode>1)
			timeMode=0;
		var svrTime=this.showServerTime;
		if(timeMode==1 && svrTime.timeZoneOffset==0)
			timeMode++;
		if(timeMode>1)
			timeMode=0;
		this._timeMode=timeMode;
		this.redrawServerTime();
	},

	refresh: function(){
		this.prisms.callServer("init");
	},

	showVersion: function(){
		this.prisms.showVersion();
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		__dojo.connect(this.prisms, "shutdown", this, function(){
			if(this.serverTimeInterval)
				window.clearInterval(this.serverTimeInterval);
			this.showServerTime=null;
			this.serverTimeDiv.innerHTML="";
			this.serverTimeToggle.setLabel("Show Server Time");
		});
		this.setNodePrisms(this);
	},

	setNodePrisms: function(node){
		if(node!=this && typeof node.setPrisms=="function")
			node.setPrisms(this.prisms);
		var children;
		if(node.childNodes)
			children=node.childNodes;
		else if(node.domNode)
			children=node.domNode.childNodes;
		else
			children=[];

		for(var c=0;c<children.length;c++)
		{
			if(typeof children[c].getAttribute!="undefined")
			{
				var toSet;
				var id=children[c].getAttribute("widgetid");
				if(id && id.length>0)
				{
					var widget=__dijit.byId(id);
					if(widget)
						toSet=widget;
					else
						toSet=children[c];
				}
				else
					toSet=children[c];
				this.setNodePrisms(toSet);
			}
		}
	},

	scanAttaches: function(node){
		if(node.dojoAttachPoint)
			this[node.dojoAttachPoint]=node;
		else if(typeof node.getAttribute!="undefined" && node.getAttribute("dojoAttachPoint"))
			this[node.getAttribute("dojoAttachPoint")]=node;

		var children;
		if(node.childNodes)
			children=node.childNodes;
		else if(node.domNode)
			children=node.domNode.childNodes;
		else
			children=[];

		for(var c=0;c<children.length;c++)
		{
			if(typeof children[c].getAttribute!="undefined")
			{
				var toSet;
				var id=children[c].getAttribute("widgetid");
				if(id && id.length>0)
				{
					var widget=__dijit.byId(id);
					if(widget)
						toSet=widget;
					else
						toSet=children[c];
				}
				else
					toSet=children[c];
				this.scanAttaches(toSet);
			}
		}
	}
});
