
dojo.require("dijit.form.TextBox");
dojo.require("dijit.form.CheckBox");

dojo.require("prisms.widget.TabWidget");
dojo.require("prisms.widget.DateTimeWidget");

dojo.provide("manager.UserEditor");
dojo.declare("manager.UserEditor", [prisms.widget.TabWidget, dijit._Templated, dijit._Container], {
	templatePath: "/prisms/view/manager/templates/userEditor.html",

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
		else if(event.method=="setValue")
			this.setValue(event.value);
		else if(event.method=="changePassword")
			this.changePassword(event.hashing);
		else
			throw new Error("event "+event.plugin+"."+event.method+" not recognized");
	},

	/**
	 * Overrides the setVisible method in TabWidget
	 */
	setVisible: function(visible){
		this.domNode.style.display=(visible ? "block" : "none");
	},

	setEnabled: function(enabled){
		var self=this;
		//TODO: This is important for subtabs later
//		if(!this.initialized)
//		{
//			this.taskEditorTimeoutCount=0;
//			this.initTaskEditorTimeout=setTimeout(function(){
//				var tabs=dijit.byId("managerMainTabContainer");
//				var taskTab=dijit.byId("managerUserTab");
//				var doClear=false;
//				if(tabs && taskTab)
//				{
//					tabs.selectChild(taskTab);
//					doClear=true;
//				}
//				self.taskEditorTimeoutCount++;
//				doClear|=self.taskEditorTimeoutCount>=10;
//				if(doClear)
//				{
//					clearTimeout(self.initTaskEditorTimeout);
//					delete self.initTaskEditorTimeout;
//					delete self.taskEditorTimeoutCount;
//					self.initialized=true;
//				}
//			}, 250);
//		}
		
		
		this.nameField.setAttribute("disabled", !enabled);
		this.changePasswordButton.setAttribute("disabled", !enabled);
		this.passwordExpireCheck.setAttribute("disabled", !enabled);
		this.passwordExpiration.setDisabled(!enabled);
	},

	setValue: function(value){
		this.dataLock=true;
		this.value = value;
		try{
			
			this.nameField.setValue(value.name);
			if(value.passwordExpiration)
			{
				this.passwordExpirationTable.style.display="table";
				this.passwordExpiration.setValue(value.passwordExpiration);
				this.passwordExpireCheck.setValue(true);
			}
			else
			{
				this.passwordExpirationTable.style.display="none";
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
		this.passwordUserName.innerHTML="Change password for user \""+this.nameField.value+"\"";
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
