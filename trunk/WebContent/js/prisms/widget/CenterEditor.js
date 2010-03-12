
dojo.provide("prisms.widget.CenterEditor");
dojo.declare("prisms.widget.CenterEditor", [dijit._Widget, dijit._Templated, dijit._Container], {

	prisms: null,

	pluginName: "No pluginName specified",

	templatePath: "/prisms/view/prisms/templates/centerEditor.html",

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
		this._linkConnects=[];
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.prisms.loadPlugin(this);
	},

	processEvent: function(event){
		if(event.method=="setCenter")
			this.setCenter(event.center, event.show);
		else if(event.method=="setUsers")
			this.setUsers(event.users);
		else if(event.method=="setEnabled")
			this.setEnabled(event.enabled, event.purgeEnabled);
		else if(event.method=="setSyncRecords")
		{
			this.importRecordsTable.setData(event.importRecords);
			this.exportRecordsTable.setData(event.exportRecords);
		}
		else if(event.method=="displaySyncInfo")
			this.displaySyncInfo(event.message, event.data);
		else
			throw new Error("Unrecognized "+this.pluginName+" method "+event.method);
	},

	setEnabled: function(enabled, purgeEnabled){
		this.isEnabled=enabled;
		this.nameField.disabled=!enabled;
		this.urlField.disabled=!enabled;
		this.serverUserField.disabled=!enabled;
		this.changePasswordLink.style.color=(enabled ? "blue" : "gray");
		this.syncFreqEditor.setDisabled(!enabled);
		this.clientUserSelect.disabled=!enabled;
		this.modSaveTimeEditor.setDisabled(!enabled);
		this.importFileSyncLink.style.color=(enabled ? "blue" : "gray");
		this.exportFileSyncLink.style.color=(enabled ? "blue" : "gray");
		this.syncNowLink.style.color=(enabled ? "blue" : "gray");

		this.importPurgeButton.setAttribute("disabled", !purgeEnabled);
		this.exportPurgeButton.setAttribute("disabled", !purgeEnabled);
	},

	setVisible: function(visible, show) {
		this.domNode.style.display = visible ? "block" : "none";
		if(visible && show)
			PrismsUtils.displayTab(this);
	},

	setUsers: function(users){
		this._fillUserSelect(this.clientUserSelect, users);
	},

	_fillUserSelect: function(select, users){
		while(select.options.length>0)
			select.remove(select.options.length-1);
		var option=document.createElement("option");
		option.value=null;
		option.text="No User";
		if(dojo.isIE > 6)
			select.add(option, 0);
		else
			select.add(option, null);
		for(var u=0;u<users.length;u++)
		{
			option=document.createElement("option");
			option.text=users[u];
			if(dojo.isIE > 6)
				select.add(option, 0);
			else
				select.add(option, null);
		}
	},

	setCenter: function(center, show) {
		if(!center)
		{
			this.setVisible(false);
			return;
		}

		this.setVisible(true, show);
		this.titleCell.innerHTML='Center "'+center.name+'"';
		this.center=center;
		this.dataLock=true;
		try{
			this.nameField.value=center.name;
			this.urlField.value=center.url;
			this.serverUserField.value=center.serverUserName;
			this.syncFreqEditor.setValue({seconds: center.syncFrequency});
			this.modSaveTimeEditor.setValue({seconds: center.modificationSaveTime});
			if(center.clientUser)
			{
				for(var i=0;i<this.clientUserSelect.options.length;i++)
					if(this.clientUserSelect.options[i].text==center.clientUser)
					{
						this.clientUserSelect.selectedIndex=i;
						break;
					}
			}
		} finally{
			this.dataLock=false;
		}
	},

	_nameChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "setName", {name: this.nameField.value});
	},

	_importFileSync: function(){
		if(this.dataLock || !this.isEnabled)
			return;
		this.prisms.callApp(this.pluginName, "importSyncByFile");
	},

	_exportFileSync: function(){
		if(this.dataLock || !this.isEnabled)
			return;
		this.prisms.callApp(this.pluginName, "exportSyncByFile");
	},

	_urlChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "setURL", {url: this.urlField.value});
	},

	_serverUserChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "setServerUser", {userName: this.serverUserField.value});
	},

	_clientUserChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "setClientUser", {
			user: this.clientUserSelect.options[this.clientUserSelect.selectedIndex].text});
	},

	_changePassword: function(){
		if(!this.isEnabled)
			return;
		this.passwordDialog.show();
	},

	protect: function(password){
		return this.xor_str(password, 93);
	},

	xor_str: function(to_enc, xor_key) {
		var the_res="";//the result will be here
		for(i=0;i<to_enc.length;++i)
			the_res+=String.fromCharCode(xor_key^to_enc.charCodeAt(i));
		return the_res;
	},

	_syncFreqChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "setSyncFrequency", {freq: this.syncFreqEditor.getValue()});
	},

	_modSaveTimeChanged: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "setModificationSaveTime", {
			saveTime: this.modSaveTimeEditor.getValue()});
	},

	_testURL: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "testURL");
	},

	_syncNow: function(){
		if(this.dataLock || !this.isEnabled)
			return;
		this.prisms.callApp(this.pluginName, "syncNow");
	},

	_showAllImportHistory: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "showAllImportHistory", {name: this.nameField.value});
	},

	_showAllExportHistory: function(){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "showAllExportHistory", {name: this.nameField.value});
	},

	displaySyncInfo: function(message, data){
		var msg=message.split("\n");
		message=msg.join("<br />");
		msg=message.split("\t");
		message=msg.join("&nbsp;&nbsp;&nbsp;&nbsp;");
		this.syncInfoMessage.innerHTML=message;
		this.syncInfoData.value=data;
		this.syncInfoDialog.show();
	},

	_sortByImp: function(column, ascending){
		this.prisms.callApp(this.pluginName, "sortBy", {column: column, ascending: ascending, import: true});
	},

	_sortByExp: function(column, ascending){
		this.prisms.callApp(this.pluginName, "sortBy", {column: column, ascending: ascending, import: false});
	},

	_goToImp: function(linkID){
		this.prisms.callApp(this.pluginName, "getRecordResults", {linkID: linkID});
	},

	_goToExp: function(linkID){
		this.prisms.callApp(this.pluginName, "getRecordResults", {linkID: linkID});
	},

	_navToImp: function(start){
		this.prisms.callApp(this.pluginName, "navigateTo", {import: true, start: start});
	},

	_navToExp: function(start){
		this.prisms.callApp(this.pluginName, "navigateTo", {import: false, start: start});
	},

	_selectChangedImp: function(start, end, selected){
		this.prisms.callApp(this.pluginName, "selectChanged", {import: true, start: start,
			end: end, selected: selected});
	},

	_selectChangedExp: function(start, end, selected){
		this.prisms.callApp(this.pluginName, "selectChanged", {import: false, start: start,
			end: end, selected: selected});
	},

	_purgeImportSyncs: function(){
		this.prisms.callApp(this.pluginName, "purgeSyncRecords", {import: true});
	},

	_purgeExportSyncs: function(){
		this.prisms.callApp(this.pluginName, "purgeSyncRecords", {import: false});
	},

	_onPasswordKeyPress: function(event){
		if(event.keyCode==dojo.keys.ENTER)
			this._passwordChanged();
	},
	_passwordChanged: function(){
		if(this.dataLock)
			return;
		this.passwordDialog.hide();
		this.prisms.callApp(this.pluginName, "setServerPassword", {
			password: this.protect(this.serverPassword.value)});
		this.dataLock=true;
		try{
			this.serverPassword.value="";
		} finally{
			this.dataLock=false;
		}
	},

	_passwordChangeCanceled: function(){
		this.passwordDialog.hide();
		this.dataLock=true;
		try{
			this.serverPassword.value="";
		} finally{
			this.dataLock=false;
		}
	},

	_closeSyncInfo: function(){
		this.syncInfoDialog.hide();
	}
});
