
__dojo.require("dijit.form.TextBox");
__dojo.require("dijit.form.CheckBox");

__dojo.require("prisms.widget.TabWidget");
__dojo.require("prisms.widget.DateTimeWidget");

__dojo.provide("manager.UserEditor");
__dojo.declare("manager.UserEditor", [prisms.widget.TabWidget, __dijit._Templated, __dijit._Container], {
	templatePath: "__webContentRoot/view/manager/templates/userEditor.html",

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
			else
				console.error("No prisms parent for plugin "+this.pluginName);
		}
		this.isReadOnly=false;
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
		else if(event.method=="setReadOnly")
			this.setReadOnly(event.readOnly);
		else if(event.method=="setValue")
			this.setValue(event.value);
		else if(event.method=="changePassword")
			this.changePassword(event.hashing, event.constraints);
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	shutdown: function(){
		this.setVisible(false);
	},

	setVisible: function(visible){
		this.domNode.style.display=(visible ? "block" : "none");
		this.inherited("setVisible", arguments);
	},

	setEnabled: function(enabled){
		this.isEnabled=enabled;
		this.nameField.setAttribute("disabled", !enabled || this.isReadOnly);
		this.changePasswordButton.setAttribute("disabled", !enabled);
		this.passwordExpireCheck.setAttribute("disabled", !enabled);
		this.passwordExpiration.setDisabled(!enabled);
	},

	setReadOnly: function(readOnly){
		this.isReadOnly=readOnly;
		this.setEnabled(this.isEnabled);
	},

	setValue: function(value){
		this.dataLock=true;
		this.value = value;
		try{
			if(this.nameField.getValue()!=value.name)
				this.nameField.setValue(value.name);
			if(value.passwordExpiration)
			{
				PrismsUtils.setTableVisible(this.passwordExpirationTable, true);
				if(this.passwordExpiration.getValue().getTime()!=value.passwordExpiration)
					this.passwordExpiration.setValue(value.passwordExpiration);
				this.passwordExpireCheck.setValue(true);
			}
			else
			{
				PrismsUtils.setTableVisible(this.passwordExpirationTable, false);
				this.passwordExpiration.setValue(new Date().getTime());
				this.passwordExpireCheck.setValue(false);
			}
			this.userLockCheck.setValue(value.locked);
		} finally{
			this.dataLock=false;
		}
	},

	changePassword: function(hashing, constraints){
		this.hashing=hashing;
		this.constraints=constraints;
		this.password1.value="";
		this.password2.value="";
		this.passwordUserName.innerHTML=PrismsUtils.fixUnicodeString("Change password for user \""+this.nameField.value+"\"");
		this.changePasswordDialog.show();
	},

	_nameChanged: function(){
		if(this.dataLock)
			return;
		var newName=this.nameField.getValue();
		this.prisms.callApp(this.pluginName, "nameChanged", {name: newName});
	},

	_passwordExpireChanged: function(){
		if(this.dataLock)
			return;
		var checked=this.passwordExpireCheck.getValue();
		if(checked)
		{
			if(this.passwordExpirationTable.style.display=="none"
				&& this.passwordExpiration.getValue().getTime()<new Date().getTime())
			{
				this.dataLock=true;
				try{
					this.passwordExpiration.setValue(new Date().getTime()+30*24*60*60*1000);
				} finally{
					this.dataLock=false;
				}
			}
			this.passwordExpirationTable.style.display="table";
		}
		else
			this.passwordExpirationTable.style.display="none";
		this.passwordExpiration.setDisabled(!checked);
		var expiration=null;
		if(checked)
			expiration=this.passwordExpiration.getValue().getTime();
		this.prisms.callApp(this.pluginName, "passwordExpirationChanged",
			{expiration: expiration});
	},
	
	
	_passwordChange: function(){
		this.prisms.callApp(this.pluginName, "changePassword");
	},

	_passwordChangeAccepted: function(){
		if(!this.hashing)
			throw new Error("No validation parameters set");
		var pwd=this.password1.value;
		if(this.password2.value!=pwd)
		{
			this.prisms.error("Passwords do not match!");
			return;
		}
		var msg=PrismsUtils.validatePassword(pwd, this.constraints);
		if(msg)
		{
			this.prisms.error(msg);
			return;
		}
		this.changePasswordDialog.hide();
		this.password1.value="";
		this.password2.value="";
		var hash=this.prisms.partialHash(pwd, this.hashing);
		this.prisms.callApp(this.pluginName, "doChangePassword", {passwordHash: hash});
	},

	_passwordChangeCanceled: function(){
		this.changePasswordDialog.hide();
		this.password1.value="";
		this.password2.value="";
	},

	_userLockChanged: function(){
		this.prisms.callApp(this.pluginName, "setLocked", {locked: this.userLockCheck.checked});
	}
});
