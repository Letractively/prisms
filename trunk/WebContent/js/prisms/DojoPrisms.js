
dojo.require("dijit.Dialog");
dojo.require("dijit.Toolbar")
dojo.require("dijit.form.Button");
dojo.require("dijit.form.TextBox");

dojo.provide("prisms.DojoPrisms");

/**
 * Implements the login functionality to ease PRISMS integration into a dojo 1.1 environment
 */
dojo.declare("prisms.DojoPrisms", [dijit._Widget, dijit._Templated], {

	templatePath: "/prisms/view/prisms/templates/dojoPrisms.html",

	widgetsInTemplate: true,

	postCreate: function(){
		this.inherited("postCreate", arguments);
/*		dojo.connect(this.loginDialog, "show", this, function(){
			if(this.userName.getValue().length>0)
				this.password.focus();
		});
		dojo.connect(this.userName, "onFocus", this, function(){
			dijit.selectInputText(this.userName);
		});
		dojo.connect(this.password, "onfocus", this, function(){
			dijit.selectInputText(this.password);
		});
		dojo.connect(this.password2, "onfocus", this, function(){
			dijit.selectInputText(this.password2);
		});*/
		this.uploadForm.target="uploadTarget";
		this.MONTHS=["Jan", "Feb", "Mar", "Apr", "May", "Jun",
		             "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		dojo.connect(prisms, "error", this, function(message){
			this.serverError(message);
		});
		dojo.connect(prisms, "doLogout", this, this.doLogout);
		dojo.connect(prisms, "appLoading", this, this.appLoading);
		dojo.connect(prisms, "appLoaded", this, this.appLoaded);
		dojo.connect(prisms, "loginSucceeded", this, this.loginSucceeded);
		var self=this;
		prisms.getDefaultUser=function(){
			return self.getDefaultUser();
		};
		prisms.doChangePassword=function(user, hashing, constraints, error, message){
			self.doChangePassword(user, hashing, constraints, error, message);
		};
		prisms.doLogin=function(error){
			self.doLogin(error);
		};
		prisms.serverError=function(error){
			self.serverError(error);
		};
		prisms.doRestart=function(event){
			self.doRestart(event);
		};
		prisms.showVersion=function(){
			self.showVersion();
		};
		prisms.showAppLocked=function(message){
			self.showAppLocked(message);
		};
		prisms.doDownload=function(event){
			self.doDownload(event);
		};
		prisms.doUpload=function(event){
			self.doUpload(event);
		};
		prisms._loginClicked=function(){
			self._loginClicked();
		};
	},

	setLoginMenu: function(loginMenu){
		this.loginMenu=loginMenu;
		this.loginLogoutButton=new dijit.MenuItem({label:"Log In..."});
		this.loginMenu.dropDown.addChild(this.loginLogoutButton);
		dojo.connect(this.loginLogoutButton, "onClick", this, this._doLoginLogout);
		this.switchUserButton=new dijit.MenuItem({label:"Switch User..."});
		this.switchUserButton.setAttribute("disabled", true);
		this.switchUserButton.domNode.title="Log in as another user";
		this.loginMenu.dropDown.addChild(this.switchUserButton);
		dojo.connect(this.switchUserButton, "onClick", this, this._doSwitchUser);
		this.changePasswordButton=new dijit.MenuItem({label:"Change Password..."});
		this.changePasswordButton.setAttribute("disabled", true);
		this.changePasswordButton.domNode.title="Change your password";
		this.loginMenu.dropDown.addChild(this.changePasswordButton);
		dojo.connect(this.changePasswordButton, "onClick", this, this._tryChangePassword);
	},

	getDefaultUser: function(){
		return dojo.cookie("prisms_user");
	},

	_doLoginLogout: function(){
		if(this.loggedIn)
			this.doLogout();
		else
			this.doLogin();
	},

	_doSwitchUser: function(){
		this.isSwitching=true;
		this._switchedFrom=this.prisms._login;
		this.doLogin();
	},

	_tryChangePassword: function(){
		this.prisms.callServer("tryChangePassword");
	},

	doChangePassword: function(user, hashing, constraints, error, message){
		delete this.changedPassword;
		this.formType="changePassword";
		if(error)
		{
			error=PrismsUtils.fixUnicodeString(error);
			var err=error.split("\n");
			error=err.join("<br />");
			this.loginMessageNode.style.color="red";
			this.loginMessageNode.innerHTML=error;
		}
		else
		{
			this.loginMessageNode.style.color="black";
			if(message)
			{
				message=PrismsUtils.fixUnicodeString(message);
				var msg=message.split("\n");
				message=msg.join("<br />");
				this.loginMessageNode.innerHTML=message;
			}
			else
				this.loginMessageNode.innerHTML="Enter new password for "+user;
		}
		PrismsUtils.setTableRowVisible(this.userNameRow, false);
		PrismsUtils.setTableRowVisible(this.password2Row, true);
		PrismsUtils.setTableRowVisible(this.capsLockWarn, false);
		this._hashing=hashing;
		this._constraints=constraints;
		this.loginDialog.show();
	},

	doLogin: function(error){
		if(this.changedPassword)
		{
			var pwd=this.changedPassword;
			delete this.changedPassword;
			this.prisms.submitLogin(this.prisms._login.userName, pwd);
		}
		if(!this.isSwitching)
		{
			this.prisms.shutdown();
			this.loggedIn=false;
			this.loginMenu.setLabel("Not logged in");
			this.loginLogoutButton.setLabel("Log In...");
			this.loginLogoutButton.domNode.title="Log into PRISMS";
			this.switchUserButton.setAttribute("disabled", true);
			this.changePasswordButton.setAttribute("disabled", true);
		}

		this.formType="login";
		if(error)
		{
			this.loginMessageNode.style.color="red";
			this.loginMessageNode.innerHTML=error;
		}
		else
		{
			this.loginMessageNode.style.color="black";
			this.loginMessageNode.innerHTML="Login";
		}
//		dojo.style(dijit.byId('spinnerId').domNode, {'visibility': 'hidden'});
//		dijit.byId('spinnerId').domNode.style.visibility = 'hidden';
		
		PrismsUtils.setTableRowVisible(this.userNameRow, true);
		PrismsUtils.setTableRowVisible(this.passwordRow, true);
		
		PrismsUtils.setTableRowVisible(this.capsLockWarn, false);
		PrismsUtils.setTableRowVisible(this.password2Row, false);
		if(this.prisms._login && this.prisms._login.userName)
			this.userName.setValue(this.prisms._login.userName);
		this.loginDialog.show();
		this.password.focus();
	},

	doLogout: function(){
		this.isSwitching=false;
		this.prisms.callServer("logout");
	},

	appLoading: function(){
		this.loadingMessageDiv.innerHTML="Loading "+this.prisms.application;
		this.loadingDialog.show();
	},

	appLoaded: function(){
		this.loadingDialog.hide();
	},

	loginSucceeded: function(userName){
		this.isSwitching=false;
		this.loggedIn=true;
		if(userName)
			this.loginMenu.setLabel("Logged in as "+userName);
		else
			this.loginMenu.setLabel("Logged in anonymously");
		this.loginLogoutButton.setLabel("Logout");
		this.loginLogoutButton.domNode.title="End this session.";
		this.switchUserButton.setAttribute("disabled", false);
		this.changePasswordButton.setAttribute("disabled", false);
		if(!dojo.cookie.isSupported())
			return;
		if(userName)
			dojo.cookie("prisms_user", userName, {expires: 30});
		else
			dojo.cookie("prisms_user", null, {expires: -1});
	},

	serverError: function(message){
		this.prisms.processEvents([{plugin: "UI", method: "error", message: message}]);
		if(this.isSwitching)
		{
			this.isSwitching=false;
			this.prisms._login=this._switchedFrom;
		}
	},

	doRestart: function(event){
		PrismsUtils.setTableRowVisible(this.messageDialogOKRow, true);
		if(event.message)
			this.messageNode.innerHTML=event.message;
		else
			this.messageNode.innerHTML=this.prisms.application+" must be reloaded";
		this.messageDialog.show();
	},

	showAppLocked: function(message){
		PrismsUtils.setTableRowVisible(this.messageDialogOKRow, false);
		if(message)
			this.messageNode.innerHTML=message;
		else
			this.messageNode.innerHTML=this.prisms.application+" is temporarily locked";
		this.messageDialog.show();
	},

	_restartNotified: function(){
		window.location.reload();
	},

	showVersion: function(){
		var alertString="Application "+this.prisms.application+", Client "+this.prisms.client+"\n";
		if(!this.prisms.appModified)
			alertString+="No version data available";
		else
		{
			if(!this.prisms.appVersion)
				alertString+="No version available";
			else
			{
				alertString+="Version ";
				for(var v=0;v<this.prisms.appVersion.length;v++)
				{
					alertString+=this.prisms.appVersion[v];
					if(v<this.prisms.appVersion.length-1)
						alertString+=".";
				}
			}
			alertString+="\n";
			var modDate=new Date(this.prisms.appModified);
			var dateStr=""+modDate.getUTCDate();
			if(dateStr.length<0)
				dateStr="0"+dateStr;
			dateStr+=this.MONTHS[modDate.getUTCMonth()];
			dateStr+=modDate.getUTCFullYear();
			alertString+="Modified "+dateStr;
		}
		alert(alertString);
	},

	doDownload: function(event){
		this.downloadFrame.src=this.prisms.getDownloadSource(event);
	},

	doUpload: function(event){
		this.uploadLabelCell.innerHTML=event.message;
		this.uploadEvent=event;
		this.uploadFileField.value="";
		this.uploadDialog.show();
	},

	_onKeyPress: function(event){
		var keyCode=event.keyCode;
		if(keyCode==__dojo.keys.ENTER)
			this._submit();

		var shiftPressed;
		if (typeof event.shiftKey!="undefined")
			shiftPressed = event.shiftKey;
		else if (typeof event.modifiers != "undefined")
			shift_status = (ev.modifiers & 4)!=0;
		var isLetter=false;
		var capsLock=false;
		if(!keyCode)
			keyCode=event.charCode;
		if(keyCode>=65 && keyCode<=90)
		{
			isLetter=true;
			capsLock=!shiftPressed;
		}
		else if(keyCode>=97 && keyCode<=122)
		{
			isLetter=true;
			capsLock=shiftPressed;
		}
		if(capsLock)
			PrismsUtils.setTableRowVisible(this.capsLockWarn, true);
		else if(isLetter)
			PrismsUtils.setTableRowVisible(this.capsLockWarn, false);
	},

	_submit: function(){
		if(this.formType=="login")
		{
			var userName=this.userName.getValue();
			var password=this.password.value;
			this.password.value="";
			this._cancelLock=true;
			try{
				this.loginDialog.hide();
			} finally{
				delete this._cancelLock;
			}

			this.prisms.submitLogin(userName, password);
		}
		else if(this.formType=="changePassword")
		{
			var pwd=this.password.value;
			this.password.value=""
			if(this.password2.value!=pwd)
			{
				this.loginMessageNode.innerHTML="Passwords do not match!";
				this.loginMessageNode.style.color="red";
				this.password2.value="";
				this.password.focus();
				return;
			}
			this.password2.value="";
			var msg=PrismsUtils.validatePassword(pwd, this._constraints);
			if(msg)
			{
				this.loginMessageNode.innerHTML=msg;
				this.loginMessageNode.style.color="red";
				this.password.focus();
				return;
			}
			this._cancelLock=true;
			try{
				this.loginDialog.hide();
			} finally{
				delete this._cancelLock;
			}

			this.changedPassword=pwd;
			this.prisms.submitPasswordChange(this._hashing, pwd);
		}
		else
			this.error("No formType for submit");
	},

	_loginClicked: function(){
		if(this.prisms._login && this.prisms._login.userName)
			this.prisms.doLogout();
		else
			this.prisms.doLogin();
	},

	_loginCanceled: function(){
		this.isSwitching=false
		if(this._cancelLock)
			return;
		this._cancelLock=true;
		try{
			this.loginDialog.hide();
		} finally{
			this._cancelLock=false;
		}
		this.password.value="";
		this.password2.value="";
		if(this.formType=="login" && !this.loggedIn)
			this.prisms.submitLogin(null, null);
	},

	_fileChanged: function(){
		this.uploadEvent.uploadFile=this.uploadFileField.value;
		this.uploadForm.action=this.prisms.getUploadURL(this.uploadEvent);
	},

	_uploadSubmitted: function(){
		this.uploadDialog.hide();
		//This starts the actual upload after the reference is given by the upload form submit
		var self=this;
		setTimeout(function(){
			self.prisms.callApp(null, "getEvents");
		}, 500);
	}
});
