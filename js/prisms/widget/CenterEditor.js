
__dojo.require("prisms.widget.TabWidget");
__dojo.require("prisms.widget.CertificateViewer");

__dojo.provide("prisms.widget.CenterEditor");
__dojo.declare("prisms.widget.CenterEditor", [prisms.widget.TabWidget, __dijit._Templated, __dijit._Container], {

	prisms: null,

	pluginName: "No pluginName specified",

	templatePath: "__webContentRoot/view/prisms/templates/centerEditor.html",

	widgetsInTemplate: true,

	visible: true,

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
		if(!this.visible)
			this.setVisible(false);
		delete this["visible"];
	},

	processEvent: function(event){
		if(event.method=="hide")
			this.setVisible(false);
		else if(event.method=="setCenter")
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
		else if(event.method=="showCertificate")
			this.showCertificate(event.certificate);
		else if(event.method=="checkCertificate")
			this.checkCertificate(event.newCert, event.oldCert);
		else
			throw new Error("Unrecognized "+this.pluginName+" method "+event.method);
	},

	shutdown: function(){
		this.setEditorVisible(false);
		this.certlock=true;
		try{
			this.certificateDialog.hide();
		} finally{
			this.certLock=false;
		}
	},

	setEnabled: function(enabled, purgeEnabled){
		this.isEnabled=enabled;
		this.nameField.disabled=!enabled;
		this.urlField.disabled=!enabled;
		this.serverUserField.disabled=!enabled;
		this.changePasswordLink.style.color=(enabled ? "blue" : "gray");
		this.syncFreqEditor.setDisabled(!enabled);
		this.clientUserSelect.disabled=!enabled;
//		this.modSaveTimeEditor.setDisabled(!enabled);
		this.importFileSyncLink.style.color=(enabled ? "blue" : "gray");
		this.exportFileSyncLink.style.color=(enabled ? "blue" : "gray");
		this.syncNowLink.style.color=(enabled ? "blue" : "gray");

		this.importPurgeButton.setAttribute("disabled", !purgeEnabled);
		this.exportPurgeButton.setAttribute("disabled", !purgeEnabled);
	},

	setEditorVisible: function(visible, show) {
		var oldSelect=this.isSelected();
		this.setVisible(true);
		if(!show && !oldSelect)
			this.setSelected(false);
		this.domNode.style.display = visible ? "block" : "none";
		if(visible && show)
			this.setVisible(true, true);
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
		if(__dojo.isIE > 6)
			select.add(option);
		else
			select.add(option, null);
		for(var u=0;u<users.length;u++)
		{
			option=document.createElement("option");
			option.text=users[u];
			if(__dojo.isIE > 6)
				select.add(option);
			else
				select.add(option, null);
		}
	},

	setCenter: function(center, show) {
		if(!center)
		{
			this.setEditorVisible(false);
			return;
		}

		this.setEditorVisible(true, show);
		this.titleCell.innerHTML=PrismsUtils.fixUnicodeString('Center "'+center.name+'"');
		this.center=center;
		this.dataLock=true;
		try{
			if(center.name==null)
				center.name="";
			if(center.url==null)
				center.url="";
			if(center.serverUserName==null)
				center.serverUserName="";
			if(this.nameField.value!=center.name)
				this.nameField.value=center.name;
			if(this.urlField.value!=center.url)
				this.urlField.value=center.url;
			PrismsUtils.setTableCellVisible(this.certViewIcon, center.url.indexOf("https://")==0);
			if(this.serverUserField.value!=center.serverUserName)
				this.serverUserField.value=center.serverUserName;
			if(this.syncFreqEditor.getValue().seconds!=center.syncFrequency)
				this.syncFreqEditor.setValue({seconds: center.syncFrequency});
			PrismsUtils.setTableCellVisible(this.centerStatusCell, center.status);
			if(center.status)
			{
				this.centerStatusCell.innerHTML=center.status;
				switch(center.quality)
				{
				case "GOOD":
					this.centerStatusCell.style.color="#00c000";
					break;
				case "NORMAL":
					this.centerStatusCell.style.color="#a0a0a0";
					break;
				case "POOR":
					this.centerStatusCell.style.color="#a0a000";
					break;
				case "BAD":
					this.centerStatusCell.style.color="#ff0000";
					break;
				default:
					this.centerStatusCell.style.color="black";
					break;
				}
			}
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

	showCertificate: function(cert){
		this.certDialogText1.containerNode.innerHTML="Center "+PrismsUtils.fixUnicodeString(this.center.name)
			+" currently recognizes the following security certificate:";
		this.acceptButton.setLabel("OK");
		this.denyButton.setLabel("Clear");

		this.certificateDialog.domNode.style.width="320px";
		this.comparePane.domNode.style.display="none";
		this.soloViewer.domNode.style.display="block";
		this.soloViewer.setValue(cert);
		this.certDialogText2.containerNode.innerHTML="";

		this.isChecking=false;
		this.certificateDialog.show();
		this.certContainer.resize();
	},

	checkCertificate: function(newCert, oldCert){
		this.certDialogText1.containerNode.innerHTML="Center "+PrismsUtils.fixUnicodeString(this.center.name)
			+" has presented a security certificate:";
		this.acceptButton.setLabel("Accept");
		this.denyButton.setLabel("Deny");
		if(oldCert)
		{
			this.certificateDialog.domNode.style.width="700px";
			this.soloViewer.domNode.style.display="none";
			this.comparePane.domNode.style.display="block";
			this.oldCertViewer.setValue(oldCert);
			this.newCertViewer.setValue(newCert);
			this.certDialogText2.containerNode.innerHTML="Do you want to accept the new certificate?<br />"
				+"Make sure you trust the certificate before clicking \"Accept\".";
		}
		else
		{
			this.certificateDialog.domNode.style.width="320px";
			this.comparePane.domNode.style.display="none";
			this.soloViewer.domNode.style.display="block";
			this.soloViewer.setValue(newCert);
			this.certDialogText2.containerNode.innerHTML="Do you want to accept the certificate?<br />"
				+"Make sure you trust the certificate before clicking \"Accept\".";
		}
		this.isChecking=true;
		this.certificateDialog.show();
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
		var url=this.urlField.value;
		PrismsUtils.setTableCellVisible(this.certViewIcon, url.indexOf("https://")==0);
		this.prisms.callApp(this.pluginName, "setURL", {url: url});
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
		this.syncInfoMessage.innerHTML=PrismsUtils.fixUnicodeString(message);
		this.syncInfoData.value=data;
		this.syncInfoDialog.show();
	},

	_sortByImp: function(column, ascending){
		this.prisms.callApp(this.pluginName, "sortBy", {column: column, ascending: ascending, isImport: true});
	},

	_sortByExp: function(column, ascending){
		this.prisms.callApp(this.pluginName, "sortBy", {column: column, ascending: ascending, isImport: false});
	},

	_goToImp: function(linkID){
		this.prisms.callApp(this.pluginName, "getRecordResults", {linkID: linkID});
	},

	_goToExp: function(linkID){
		this.prisms.callApp(this.pluginName, "getRecordResults", {linkID: linkID});
	},

	_navToImp: function(start){
		this.prisms.callApp(this.pluginName, "navigateTo", {isImport: true, start: start});
	},

	_navToExp: function(start){
		this.prisms.callApp(this.pluginName, "navigateTo", {isImport: false, start: start});
	},

	_selectChangedImp: function(start, end, selected){
		this.prisms.callApp(this.pluginName, "selectChanged", {isImport: true, start: start,
			end: end, selected: selected});
	},

	_selectChangedExp: function(start, end, selected){
		this.prisms.callApp(this.pluginName, "selectChanged", {isImport: false, start: start,
			end: end, selected: selected});
	},

	_purgeImportSyncs: function(){
		this.prisms.callApp(this.pluginName, "purgeSyncRecords", {isImport: true});
	},

	_purgeExportSyncs: function(){
		this.prisms.callApp(this.pluginName, "purgeSyncRecords", {isImport: false});
	},

	_onPasswordKeyPress: function(event){
		if(event.keyCode==__dojo.keys.ENTER)
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
	},

	_viewCertClicked: function(){
		this.prisms.callApp(this.pluginName, "viewCertificate");
	},

	_certClose: function(){
		this.certLock=true;
		try{
			this.certificateDialog.hide();
		} finally{
			this.certLock=false;
		}
		this.soloViewer.setValue(null);
	},

	_certAccept: function(){
		this.certLock=true;
		try{
			this.certificateDialog.hide();
		} finally{
			this.certLock=false;
		}
		this.soloViewer.setValue(null);
		if(this.isChecking)
		{
			this.newCertViewer.setValue(null);
			this.oldCertViewer.setValue(null);
			this.prisms.callApp(this.pluginName, "acceptCertificate", {accepted: true});
		}
	},

	_certDeny: function(){
		if(this.certLock)
			return;
		this.certLock=true;
		try{
			this.certificateDialog.hide();
		} finally{
			this.certLock=false;
		}
		this.soloViewer.setValue(null);
		if(this.isChecking)
		{
			this.newCertViewer.setValue(null);
			this.oldCertViewer.setValue(null);
			this.prisms.callApp(this.pluginName, "acceptCertificate", {accepted: false});
		}
		else
			this.prisms.callApp(this.pluginName, "clearCertificate");
	}
});
