
dojo.require("dijit.form.Button");
dojo.require("dijit.form.NumberSpinner");

dojo.require("prisms.widget.SortTable");
dojo.require("prisms.widget.TimeAmountEditor");

dojo.provide("prisms.widget.History");
dojo.declare("prisms.widget.History", [prisms.widget.TabWidget, dijit._Templated, dijit._Container], {

	prisms: null,

	pluginName: "No pluginName specified",

	templatePath: "/prisms/view/prisms/templates/history.html",
	
	widgetsInTemplate: true,

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
		this.ageEditor.setValue({months: 0, seconds: 30*24*60*60});
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.sortTable.prisms=this.prisms;
		this.prisms.loadPlugin(this);
	},

	processEvent: function(event){
		if(event.method=="setItem")
			this.setItem(event.item);
		else if(event.method=="setSnapshotTime")
			this.setSnapshot(event.time);
		else if(event.method=="setPurgeEnabled")
			this.setPurgeEnabled(event.enabled);
		else if(event.method=="setAutoPurge")
			this.setAutoPurge(event.autoPurge);
		else if(event.method=="setContent")
			this.setContent(event.content, event.show);
		else if(event.method=="setFilter")
			this.setFilter(event.filter);
		else
			throw new Error("Unrecognized "+this.pluginName +" method: " + event.method)
	},

	setPurgeEnabled: function(enabled){
		this.purgeEnabled=enabled;
		if(this.autoPurge)
		{
			this.entryCountCheck.disabled=!enabled;
			this.entryCountEditor.setAttribute("disabled", !(enabled && typeof this.autoPurge.entryCount=="number"));
			this.ageCheck.disabled=!enabled;
			this.ageEditor.setDisabled(!(enabled && typeof this.autoPurge.age=="number"));
		}
		else
		{
			this.entryCountCheck.disabled=true;
			this.entryCountEditor.setAttribute("disabled", true);
			this.ageCheck.disabled=true;
			this.ageEditor.setDisabled(true);
		}
		/*this.excludeUsers.setDisabled(!enabled);*/
		this.excludeUsers.disabled=!enabled;
		this.autoPurgeButton.setAttribute("disabled", !enabled);
		this.purgeSelected.setAttribute("disabled", !enabled);
	},

	setAutoPurge: function(purge){
		this.autoPurgeNotificationCell.style.visibility="hidden";
		this.dataLock=true;
		try{
		this.autoPurge=purge;
		if(this.autoPurge)
		{
			this.entryCountEditor.setAttribute("disabled",
				!(this.purgeEnabled && typeof this.autoPurge.entryCount=="number"));
			this.ageEditor.setDisabled(!(this.purgeEnabled && typeof this.autoPurge.age=="number"));
		}
		else
		{
			this.entryCountCheck.disabled=true;
			this.entryCountEditor.setAttribute("disabled", true);
			this.ageCheck.disabled=true;
			this.ageEditor.setDisabled(true);
		}
		if(typeof purge.entryCount=="number")
		{
			this.entryCountCheck.checked=true;
			this.entryCountEditor.setValue(purge.entryCount);
		}
		else
			this.entryCountCheck.checked=false;
		if(typeof purge.age=="number")
		{
			this.ageCheck.checked=true;
			this.ageEditor.setValue({months: 0, seconds: purge.age});
		}
		else
			this.ageCheck.checked=false;
		/*this.excludeUsers.clearOptions();
		for(var i=0;i<purge.allUsers.length;i++)
		{
			var option=this.excludeUsers.addOption();
			option.text=purge.allUsers[i];
			for(var j=0;j<purge.excludeUsers.length;j++)
				if(purge.excludeUsers[j]==purge.allUsers[i])
				{
					option.selected=true;
					break;
				}
		}*/
		while(this.excludeUsers.options.length>0)
			this.excludeUsers.remove(this.excludeUsers.options.length-1);
		for(var i=0;i<purge.allUsers.length;i++)
		{
			var option=document.createElement("option");
			option.text=purge.allUsers[i];
			for(var j=0;j<purge.excludeUsers.length;j++)
				if(purge.excludeUsers[j]==purge.allUsers[i])
				{
					option.selected=true;
					break;
				}
			if(dojo.isIE > 6)
				this.excludeUsers.add(option, 0);
			else
				this.excludeUsers.add(option, null);
		}
		} finally{
			this.dataLock=false;
		}
	},

	setSnapshot: function(snapshot){
		this.snapshotTime.innerHTML=snapshot;
	},

	setItem: function(item){
		if(item)
		{
			if(item.isUserActivity)
				this.titleHeader.innerHTML="Actions of "+item.text;
			else if(item.isCenterActivity || item.isSyncRecordActivity)
			{
				if(item.import)
					this.titleHeader.innerHTML="Modifications imported from "+item.text;
				else
					this.titleHeader.innerHTML="Modifications exported to "+item.text;
			}
			else
				this.titleHeader.innerHTML="History Of "+item.text;
			this.showAllHistoryDiv.style.display="block";
		}
		else
		{
			this.titleHeader.innerHTML=this.pluginName;
			this.showAllHistoryDiv.style.display="none";
		}
	},

	setContent: function(content, show){
		this.sortTable.setData(content);
		if(show)
			this.setSelected(true, true);
	},

	_showAllHistory: function(){
		this.prisms.callApp(this.pluginName, "showAllHistory");
	},

	_refresh: function(){
		this.prisms.callApp(this.pluginName, "refresh");
	},

	_sortBy: function(columnLabel, ascending){
		this.prisms.callApp(this.pluginName, "setSort", {column: columnLabel, ascending: ascending});
	},

	_goTo: function(linkID){
		this.prisms.callApp(this.pluginName, "goToLink", {id: linkID});
	},

	_navTo: function(start){
		this.prisms.callApp(this.pluginName, "navigate", {start: start});
	},

	_selectChanged: function(start, end, selected){
		this.prisms.callApp(this.pluginName, "setSelected", {start: start, end: end, selected: selected});
	},

	_undoClicked: function(){
		this.prisms.callApp(this.pluginName, "undoSelected");
	},

	_purgeClicked: function(){
		this.prisms.callApp(this.pluginName, "purgeSelected");
	},

	_entryCountChanged: function(){
		if(this.dataLock)
			return;
		if(!this.purgeEnabled)
			return;
		if(this.entryCountCheck.checked)
			this.autoPurge.entryCount=this.entryCountEditor.getValue();
		else
			this.autoPurge.entryCount=null;
		this.entryCountEditor.setAttribute("disabled",
				!(this.purgeEnabled && typeof this.autoPurge.entryCount=="number"));
		this.autoPurgeNotificationCell.style.visibility="visible";
	},

	_ageChanged: function(){
		if(this.dataLock)
			return;
		if(!this.purgeEnabled)
			return;
		if(this.ageCheck.checked)
			this.autoPurge.age=this.ageEditor.getValue().seconds;
		else
			this.autoPurge.age=null;
		this.ageEditor.setDisabled(!(this.purgeEnabled && typeof this.autoPurge.age=="number"));
		this.autoPurgeNotificationCell.style.visibility="visible";
	},

	_excludeUsersChanged: function(index, selected){
		if(this.dataLock)
			return;
		if(typeof index=="number")
		{ // The prisms.widget.MultiSelect widget
			var name=this.excludeUsers.options[index].text;
			if(selected)
				this.autoPurge.excludeUsers.push(name);
			else
			{
				for(var i=0;i<this.autoPurge.excludeUsers.length;i++)
					if(this.autoPurge.excludeUsers[i]==name)
					{
						this.autoPurge.excludeUsers.splice(i, 1);
						break;
					}
			}
		}
		else
		{
			this.autoPurge.excludeUsers.splice(0, this.autoPurge.excludeUsers.length);
			for(var i=0;i<this.excludeUsers.options.length;i++)
				if(this.excludeUsers.options[i].selected)
					this.autoPurge.excludeUsers.push(this.excludeUsers.options[i].text);
		}
		this.autoPurgeNotificationCell.style.visibility="visible";
	},

	_sendAutoPurge: function(){
		var ap={};
		ap.entryCount=this.autoPurge.entryCount;
		ap.age=this.autoPurge.age;
		ap.excludeUsers=this.autoPurge.excludeUsers;
		this.prisms.callApp(this.pluginName, "setAutoPurge", {autoPurge: ap});
	}
});
