
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
		prisms.doChangePassword=function(user, hashing, error){
			self.doChangePassword(user, hashing, error);
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

	getDefaultUser: function(){
		return dojo.cookie("prisms_user");
	},

	doChangePassword: function(user, hashing, error){
		this.formType="changePassword";
		if(error)
		{
			this.loginMessageNode.style.color="red";
			this.loginMessageNode.innerHTML=error;
		}
		else
		{
			this.loginMessageNode.style.color="black";
			this.loginMessageNode.innerHTML="Enter new password for "+user;
		}
		this.userNameRow.style.display="none";
		this.password2Row.style.display="block";
		this._hashing=hashing;
		this.loginDialog.show();
	},

	doLogin: function(error){
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
		
		this.userNameRow.style.display="block";
		this.passwordRow.style.display="block";
		
		this.password2Row.style.display="none";
		if(this.prisms._login && this.prisms._login.userName)
			this.userName.setValue(this.prisms._login.userName);
		this.loginDialog.show();
		this.password.focus();
	},

	doLogout: function(){
		this.prisms.submitLogin(null, null);
	},

	appLoading: function(){
		this.loadingMessageDiv.innerHTML="Loading "+this.prisms.application;
		this.loadingDialog.show();
	},

	appLoaded: function(){
		this.loadingDialog.hide();
	},

	loginSucceeded: function(userName){
		if(userName)
			this.loginButton.setLabel("Logout as "+userName);
		else
			this.loginButton.setLabel("Login...");
		if(!dojo.cookie.isSupported())
			return;
		if(userName)
			dojo.cookie("prisms_user", userName, {expires: 30});
		else
			dojo.cookie("prisms_user", null, {expires: -1});
	},

	serverError: function(message){
		this.prisms.processEvents([{plugin: "UI", method: "error", message: message}]);
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
		if(event.keyCode==dojo.keys.ENTER)
			this._submit();
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
				return;
			}
			this.password2.value="";
			this._cancelLock=true;
			try{
				this.loginDialog.hide();
			} finally{
				delete this._cancelLock;
			}

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
