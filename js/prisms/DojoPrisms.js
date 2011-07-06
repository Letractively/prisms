
__dojo.require("dijit.Dialog");
__dojo.require("dijit.form.Button");
__dojo.require("dijit.form.TextBox");

__dojo.provide("prisms.DojoPrisms");

/**
 * Implements the login functionality to ease PRISMS integration into a dojo 1.1 environment
 */
__dojo.declare("prisms.DojoPrisms", [__dijit._Widget, __dijit._Templated], {

	templatePath: "__webContentRoot/view/prisms/templates/dojoPrisms.html",

	widgetsInTemplate: true,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.uploadForm.target="uploadTarget";
		this.MONTHS=["Jan", "Feb", "Mar", "Apr", "May", "Jun",
		             "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		__dojo.connect(prisms, "error", this, function(message){
			this.serverError(message);
		});
		__dojo.connect(prisms, "doLogout", this, this.doLogout);
		__dojo.connect(prisms, "appLoading", this, this.appLoading);
		__dojo.connect(prisms, "appLoaded", this, this.appLoaded);
		__dojo.connect(prisms, "loginSucceeded", this, this.loginSucceeded);
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
		prisms.warnExpire=function(expireTime){
			self.warnExpire(expireTime);
		};
		prisms.showAppLocked=function(message, scale, progress){
			self.showAppLocked(message, scale, progress);
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
		__dojo.connect(this.loginLogoutButton, "onClick", this, this._doLoginLogout);
		this.switchUserButton=new dijit.MenuItem({label:"Switch User..."});
		this.switchUserButton.setAttribute("disabled", true);
		this.switchUserButton.domNode.title="Log in as another user";
		this.loginMenu.dropDown.addChild(this.switchUserButton);
		__dojo.connect(this.switchUserButton, "onClick", this, this._doSwitchUser);
		this.changePasswordButton=new dijit.MenuItem({label:"Change Password..."});
		this.changePasswordButton.setAttribute("disabled", true);
		this.changePasswordButton.domNode.title="Change your password";
		this.loginMenu.dropDown.addChild(this.changePasswordButton);
		__dojo.connect(this.changePasswordButton, "onClick", this, this._tryChangePassword);
	},

	getDefaultUser: function(){
		return __dojo.cookie("prisms_user");
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
				this.loginMessageNode.innerHTML=PrismsUtils.fixUnicodeString("Enter new password for "+user);
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
			this.loginMessageNode.innerHTML=PrismsUtils.fixUnicodeString(error);
		}
		else
		{
			this.loginMessageNode.style.color="black";
			this.loginMessageNode.innerHTML="Login";
		}
//		__dojo.style(__dijit.byId('spinnerId').domNode, {'visibility': 'hidden'});
//		__dijit.byId('spinnerId').domNode.style.visibility = 'hidden';
		
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
		this.loadingMessageDiv.innerHTML=PrismsUtils.fixUnicodeString("Loading "+this.prisms.application);
		this.loadingDialog.show();
	},

	appLoaded: function(){
		this.loadingDialog.hide();
		if(this.appLockUpdateTimer)
		{
			window.clearInterval(this.appLockUpdateTimer);
			this.appLockUpdateTimer=null;
		}
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
		if(__dojo.cookie.isSupported())
		{
			if(userName)
				__dojo.cookie("prisms_user", userName, {expires: 30});
			else
				__dojo.cookie("prisms_user", null, {expires: -1});
		}
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
			this.messageNode.innerHTML=PrismsUtils.fixUnicodeString(event.message);
		else
			this.messageNode.innerHTML=PrismsUtils.fixUnicodeString(this.prisms.application+" must be reloaded");
		this.isRestarted=true;
		this.messageDialog.show();
	},

	warnExpire: function(expireTime){
		this.expireTime=new Date().getTime()+expireTime;
		PrismsUtils.setTableRowVisible(this.messageDialogOKRow, true);
		if(!this.expireInterval)
		{
			var self=this;
			this.expireInterval=window.setInterval(function(){
				self._warnExpire();
			}, 1000);
		}
		this._warnExpire();
		this.messageDialog.show();
	},

	/** Called by a timer while the session is in danger of expiring */
	_warnExpire: function(){
		var exp=Math.round((this.expireTime-new Date().getTime())/1000);
		if(exp<0)
		{
			window.clearInterval(this.expireInterval);
			this.expireInterval=null;
			this.prisms.shutdown();
			this.doRestart({message: "Session has timed out! Refresh required."});
			this.messageDialog.layout();
		}
		else if(exp<=60)
			this.messageNode.innerHTML=PrismsUtils.fixUnicodeString("Your session will expire in "+exp
				+" seconds. Click OK to continue using "+this.prisms.application+".");
		else
			this.messageNode.innerHTML=PrismsUtils.fixUnicodeString("Your session will expire in approx. "+Math.ceil(exp/60)
				+" minutes. Click OK to continue using "+this.prisms.application+".");
	},

	showAppLocked: function(message, scale, progress){
		if(message)
		{
			message=PrismsUtils.fixUnicodeString(message);
			var msg=message.split("\n");
			message=msg.join("<br />");
			msg=message.split("\t");
			var leftAlign=msg.length>1;
			if(leftAlign)
				this.loadingMessageDiv.style.textAlign="left";
			else
				this.loadingMessageDiv.style.textAlign="center";
			message=msg.join("&nbsp;&nbsp;&nbsp;&nbsp;");
			this.loadingMessageDiv.innerHTML=PrismsUtils.fixUnicodeString(message);
		}
		else
			this.loadingMessageDiv.innerHTML=PrismsUtils.fixUnicodeString(
				this.prisms.application+" is temporarily locked");
		if(scale && typeof progress=="number")
		{
			this.loadingProgressBar.indeterminate=false;
			this.loadingProgressBar.maximum=scale;
			this.loadingProgressBar.progress=progress;
		}
		else
			this.loadingProgressBar.indeterminate=true;
		this.loadingProgressBar.update();
		this.loadingDialog.show();
		if(!this.appLockUpdateTimer)
		{
			var self=this;
			this.appLockUpdateTimer=window.setInterval(function(){
				if(self.prisms.isActive())
					self.prisms.callApp(null, "getEvents");
				else
					self.hide();
			}, 1000);
		}
	},

	_messageOKPressed: function(){
		if(this.expireInterval)
		{ // User decided to renew their session
			window.clearInterval(this.expireInterval);
			this.expireInterval=null;
			this.prisms.callApp(null, "renew");
			this.messageDialog.hide();
		}
		else if(this.isRestarted)// The message was that the session has expired.  Reload.
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
		this.uploadLabelCell.innerHTML=PrismsUtils.fixUnicodeString(event.message);
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
				this.loginMessageNode.innerHTML=PrismsUtils.fixUnicodeString(msg);
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
