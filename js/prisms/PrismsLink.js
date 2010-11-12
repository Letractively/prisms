
__dojo.provide("prisms.PrismsLink");

__dojo.require("prisms.Encryption");

__dojo.declare("prisms.PrismsLink", null, {
	application: "No Application Specified",

	client: "No Client Specified",

	servletURL: "No Servlet URL Specified",

	imageURL: "No Image URL Specified",

	connectImmediately: false,

	cipher: null,

	constructor: function(args){
		if(args.application)
			this.application=args.application;
		if(args.client)
			this.client=args.client;
		if(args.servletURL)
			this.servletURL=args.servletURL;
		if(args.imageURL)
			this.imageURL=args.imageURL;
		if(args.connectImmediately)
			this.connectImmediately=args.connectImmediately;

		this.theMaxKeyLength = 448 / 8;

		this.plugins={};
		this.theListeners={};
		this.lastPinged=0;
		this.error=__dojo.hitch(console, console.error);
		this.debug=__dojo.hitch(console, console.debug);
		this.toJson=__dojo.hitch(__dojo, __dojo.toJson);
		this._login={};
		if(this.connectImmediately)
			this.prismsConnect();
		var self=this;
		dojo.addOnUnload(function(){
			self.disconnect();
		});
	},

	getDefaultUser: function(){
		return null;
	},

	prismsConnect: function(){
		if(!this._login.userName)
			this._login.userName=this.getDefaultUser();
		this.appLoading();
		this.callServer("init");
	},

	loadPlugin: function(plugin){
		if(typeof plugin["pluginName"]!="string")
		{
			this.error("Could not load plugin--no name");
			return;
		}
		else if(typeof plugin["processEvent"]!="function")
		{
			this.error("Could not load plugin "+plugin.pluginName+"--no processEvent method");
			return;
		}
		else if(typeof plugin["shutdown"]!="function")
		{
			this.error("Could not load plugin "+plugin.pluginName+"--no shutdown method");
			return;
		}
		this.plugins[plugin.pluginName]=plugin;
		if(this.started)
			this.callApp(null, "addPlugin", {pluginToAdd: plugin.pluginName});
	},

	processEvents: function(events){
		var pluginsCalled={};
		for(var e=0;e<events.length;e++)
		{
			this.processEvent(events[e]);
			pluginsCalled[events[e].plugin]=this.plugins[events[e].plugin];
		}
		for(var p in pluginsCalled)
		{
			if(typeof pluginsCalled[p]=="function")
				continue;
			var plugin=pluginsCalled[p];
			if(plugin && typeof plugin.postProcessEvents == "function")
				plugin.postProcessEvents();
		}
	},

	processEvent: function(event){
		if(this._appLocked && event.method!="appLocked")
		{
			this.appLoaded();
			this._appLocked=false;
		}
		if(!event.plugin)
		{
			this.processPrismsEvent(event);
			return;
		}
		var plugin=this.plugins[event.plugin];
		if(!plugin)
		{
			this.debug("Plugin \""+event.plugin+"\" unrecognized");
			return;
		}
		try{
			plugin.processEvent(event);
		} catch(error){
			this.error("Plugin "+event.plugin+" failed to process event ", event, error);
			throw error;
		}
	},

	processPrismsEvent: function(event){
//		this.appLoaded();
		var self=this;
		if(this._appLocked && event.method!="appLocked")
		{
			this.appLoaded();
			this._appLocked=false;
		}
		if(event.method=="getEvents")
		{
			window.setTimeout(function(){
				self.callApp(null, "getEvents", {"taskID": event.taskID});
			}, 500);
		}
		else if(event.method=="setVersion")
		{
			this.appVersion=event.version;
			this.appModified=event.modified;
			this.appLoaded();
		}
		else if(event.method=="callInit")
		{
			this.appLoading();
			this.callServer("init");
		}
		else if(event.method=="login")
		{
			this.appLoaded();
			if(event.error)
				this.doLogin(event.error);
			else
				this.doLogin();
		}
		else if(event.method=="startEncryption")
		{
//			if(this.started && !this.isActive())
//				return;
			this.startEncryption(event.encryption, event.hashing, event.error, event.postAction);
		}
		else if(event.method=="validate")
			this.doValidate(event.hashing, event.validationFailed);
		else if(event.method=="changePassword")
		{
			this.appLoaded();
			this.doChangePassword(this._login.userName, event.hashing, event.constraints,
				event.error, event.message);
		}
		else if(event.method=="init")
		{
			if(event.user)
			{
				if(!this._login)
					this._login={};
				this._login.userName=event.user;
			}
			this.loginSucceeded(this._login.userName);
			this.appLoading();
			this.started=true;
			if(!this.pingID)
			{
				this.pingID=setInterval(function(){
					var now=new Date().getTime();
					if(now-self.lastPinged<29500)
						return;
					self.callApp(null, "getEvents");
				}, 10000);
			}
			for(var p in this.plugins)
			{
				if(typeof this.plugins[p]=="function")
					continue;
				this.callApp(null, "addPlugin", {pluginToAdd: p});
			}
			this.callServer("getVersion");
		}
		else if(event.method=="setSessionID")
		{
			this.sessionID=event.sessionID;
		}
		else if(event.method=="error")
		{
			this.appLoaded();
			this.serverError(event.message);
		}
		else if(event.method=="restart")
		{
			if(this.started && !this.isActive())
				return;
			this.shutdown();
			this.doRestart(event);
		}
		else if(event.method=="warnExpire")
			this.warnExpire(event.expireTime);
		else if(event.method=="appLocked")
		{
			this.showAppLocked(event.message, event.scale, event.progress);
			this._appLocked=true;
		}
		else if(event.method=="doDownload")
		{
			if(!this.isActive())
				return;
			this.appLoaded();
			var dlEvent={};
			dlEvent.plugin=event.downloadPlugin;
			dlEvent.method=event.downloadMethod;
			for(var p in event)
			{
				if(typeof p.charAt!="function")
					continue;
				if(p=="plugin" || p=="method" || p=="downloadPlugin" || p=="downloadMethod")
					continue;
				dlEvent[p]=event[p];
			}
			this.doDownload(dlEvent);
		}
		else if(event.method=="doUpload")
		{
			if(!this.isActive())
				return;
			this.appLoaded();
			var ulEvent={};
			ulEvent.plugin=event.uploadPlugin;
			ulEvent.method=event.uploadMethod;
			for(var p in event)
			{
				if(typeof p.charAt!="function")
					continue;
				if(p=="plugin" || p=="method" || p=="uploadPlugin" || p=="uploadMethod")
					continue;
				ulEvent[p]=event[p];
			}
			this.doUpload(ulEvent);
		}
		else
			throw new Error("Unrecognized PRISMS event: "+this.toJson(event));
	},

	shutdown: function(){
		if(this.pingID)
		{
			clearInterval(this.pingID);
			delete this.pingID;
		}
		for(var p in this.plugins)
		{
			if(typeof this.plugins[p]=="function")
				continue;
			try{
				this.plugins[p].shutdown();
			} catch(e){
				console.error("Plugin "+p+" failed to shutdown", e);
			}
		}
	},

	isActive: function(){
		return typeof this.pingID != "undefined";
	},

	startEncryption: function(encryption, hashing, error, postAction){
		this.shutdown();
		if(postAction)
			this._postEncryptionAction=postAction;
		if(this._login && hashing && hashing.user!=this._login.userName)
		{
			this.cipher=null;
			this.callServer("init");
			this.appLoading();
		}
		else if(!this._login || error || !this._login.password || this._login.password.length==0)
		{
			this._encryption=encryption;
			this._hashing=hashing;
			this._postLoginAction="startEncryption";
			this.appLoaded();
			if(error)
				this.doLogin(error);
			else if(!this._login.password || this._login.password.length==0)
				this.doLogin("Password required for user "+this._login.userName);
			else
				this.doLogin();
		}
		else
		{
			this.cipher=prisms.Encryption.createCipher(encryption);
			this.cipher.init(this._getEncryptionKey(hashing, this._login.password));
			if(this._postEncryptionAction)
			{
				if(this._postEncryptionAction=="callInit")
				{
					this.callServer("init");
					this.appLoading();
				}
				else
					this.error("Unrecognized post-encryption action: "+this._postEncryptionAction);
				delete this._postEncryptionAction;
			}
		}
	},

	doValidate: function(hashing, failed){
		// Default implementation--may be overridden by subclasses
		alert("ERROR! THIS CLIENT DOES NOT SUPPORT VALIDATION!");
	},

	doChangePassword: function(user, hashing, constraints, error, message){
		// Default implementation--may be overridden by subclasses
		alert("ERROR! THIS CLIENT DOES NOT SUPPORT PASSWORD CHANGE!"
			+"\nContact your administrator or visit the manager page to change it")
	},

	doLogin: function(error){
		// Default implementation--should be overridden by subclasses
		alert("ERROR! LOGIN DIALOG NOT IMPLEMENTED!");
	},

	loginSucceeded: function(userName){
		// To be used by subclasses to change GUI state when a user changes logins
	},

	appLoading: function(){
		// To be used by subclasses to display a message that the application is loading
	},

	appLoaded: function(){
		// To be used by subclasses to hide the message saying that the application is loading
	},

	doDownload: function(event){
		alert("ERROR! doDownload NOT IMPLEMENTED!");
	},

	doUpload: function(event){
		alert("ERROR! doUpload NOT IMPLEMENTED!");
	},

	warnExpire: function(expireTime){
		// To be used by subclasses to display a message to the user that their session is about to
		// expire
	},

	submitLogin: function(userName, password){
		this.appLoading();
		if(this.pingID)
		{
			clearInterval(this.pingID);
			delete this.pingID;
		}
		this._login={userName: userName, password: password};
		delete this.cipher;
		if(this._postLoginAction)
		{
			if(this._postLoginAction=="startEncryption")
				this.startEncryption(this._encryption, this._hashing, null, "callInit");
			else
				this.error("Unrecognized post-login action: "+this._postLoginAction);
			delete this._postLoginAction;
		}
		else
			this.callServer("init");
	},

	submitPasswordChange: function(hashing, pwd){
		this.callServer("changePassword",
			{data:{passwordData: this.partialHash(pwd, hashing)}});
	},

	serverError: function(message){
		this.error(message);
	},

	doRestart: function(event){
		if(event.message)
			alert(message);
		else
			alert(this.application+" must be reloaded--reloading application");
		window.location.reload();
	},

	showAppLocked: function(message, scale, progress){
		alert(this.application+" is temporarily locked: "+message);
	},

	getServerRequest: function(params){
		if(!params)
			params={};
		if(this.sessionID)
			params.sessionID=this.sessionID;
		params.app=this.application;
		params.client=this.client;
		params.user=this._login.userName;
		params.encrypted=this.cipher ? true : false;
		if(params.encrypted)
		{
			var data=params.data;
			if(typeof data != "object")
				data={};
			//The purpose of this is to ensure that the encrypted data is long enough to satisfy the
			//server that the client's encryption is valid
			data.serverPadding="padding";
			params.data=data;
		}
		this._serializeDeep(params);
		if(params.encrypted)
			params.data=this.cipher.encrypt(params.data);
		return params;
	},

	_xhrCall: function(params, xhrArgs){
		this.lastPinged=new Date().getTime();
		params=this.getServerRequest(params);
		var self=this;
		var args={
			url: self.servletURL,
			sync: false,
			timeout: 60000,
			preventCache: true,
			handleAs: "text",
			content: params,
			load: function(data){
				this.lastPinged=new Date().getTime();
				self._processAjaxInput(data);
			},
			error: function(error){
				this.lastPinged=new Date().getTime();
				self.appLoaded();
				if(error.status==0)
				{
					self.error(self.application+" is not accessible.  Please try again later.");
					self.shutdown();
				}
				else if(error.__dojoType=="timeout")
					self.error(self.application+" timed out.  Try refreshing.\n"
						+"If that doesn't work, try again later.");
				else
					self.error("Could not execute server call: ", error);
				throw error;
			}
		};
		if(xhrArgs)
			__dojo.mixin(args, xhrArgs);
		__dojo.xhrPost(args);
	},

	_processAjaxInput: function(data){
		if((data.charAt(0)!='[' || data.charAt(data.length-1)!=']')
			&& (data.charAt(0)!='{' || data.charAt(data.length-1)!='}'))
		{
			if(!this.cipher)
			{
				this.error( "Encryption not set!" );
				return;
			}
			else
			{
				var decrypted=this.cipher.decrypt(data);
				var len=decrypted.length;
				while(len>0 && decrypted.charAt(len-1)<' ')
					len--;
				decrypted=decrypted.substring(0, len);
				data=decrypted;
			}
		}
		var originalData = data;
		try {
			data=__dojo.eval(data);
		} catch (err) {
			console.log("ERROR WITH JAVASCRIPT EVAL! ",err, originalData);
			throw new Error("Error with js eval: "+err.message);
		}
		this.validateJson(data);
		if(originalData.charAt(0)=='[')
			this.processEvents(data);
		else if(originalData.charAt(0)=='{')
			this.processEvent(data);
	},

	callServer: function(method, params, xhrArgs){
		if(!params)
			params={};
		params.method=method;
		this._xhrCall(params, xhrArgs);
	},

	callApp: function(plugin, method, params, xhrArgs){
		if(!params)
			params={};
		if(plugin)
			params.plugin=plugin;
		if(method)
			params.method=method;
		this._xhrCall({method: "processEvent", data: params}, xhrArgs);
	},

	getWmsSource: function(plugin, method){
		var params={plugin: plugin, sessionWMS: true};
		var ret=this.imageURL+"?";
		if(this.sessionID)
			ret+="sessionID="+this.sessionID+"&";
		ret+="app="+escape(this.application);
		ret+="&client="+escape(this.client);
		if(this._login && this._login.userName)
			ret+="&user="+this._login.userName;
		ret+="&encrypted="+(this.cipher ? true : false);
		ret+="&method=generateImage";
		if(this.cipher)
		{
			//The purpose of this is to ensure that the encrypted data is long enough to satisfy the
			//server that the client's encryption is valid
			params.serverPadding="padding";
			params=this.toJson(params);
			params=this.cipher.encrypt(params);
		}
		else
			params=this.toJson(params);
		ret+="&data="+escape(params);
		return ret;
	},

	getDynamicImageSource: function(plugin, method, xOffset, yOffset, refWidth, refHeight, width,
		height){
		var params={plugin: plugin, method: method, xOffset: xOffset, yOffset: yOffset,
			refWidth: refWidth, refHeight: refHeight, width: width, height: height};
		var ret=this.imageURL+"?";
		if(this.sessionID)
			ret+="sessionID="+this.sessionID+"&";
		ret+="app="+escape(this.application);
		ret+="&client="+escape(this.client);
		if(this._login && this._login.userName)
			ret+="&user="+this._login.userName;
		ret+="&encrypted="+(this.cipher ? true : false);
		ret+="&method=generateImage";
		if(this.cipher)
		{
			//The purpose of this is to ensure that the encrypted data is long enough to satisfy the
			//server that the client's encryption is valid
			params.serverPadding="padding";
			params=this.toJson(params);
			params=this.cipher.encrypt(params);
		}
		else
			params=this.toJson(params);
		ret+="&data="+escape(params);
		return ret;
	},

	getDownloadSource: function(params){
		var ret=this.servletURL+"?";
		if(this.sessionID)
			ret+="sessionID="+this.sessionID+"&";
		ret+="app="+escape(this.application);
		ret+="&client="+escape(this.client);
		if(this._login && this._login.userName)
			ret+="&user="+this._login.userName;
		ret+="&encrypted="+(this.cipher ? true : false);
		ret+="&method=getDownload";
		if(this.cipher)
		{
			//The purpose of this is to ensure that the encrypted data is long enough to satisfy the
			//server that the client's encryption is valid
			params.serverPadding="padding";
			params=this.toJson(params);
			params=this.cipher.encrypt(params);
		}
		else
			params=this.toJson(params);
		ret+="&data="+escape(params);
		return ret;
	},

	getUploadURL: function(params){
		var ret=this.servletURL+"?";
		if(this.sessionID)
			ret+="sessionID="+this.sessionID+"&";
		ret+="app="+escape(this.application);
		ret+="&client="+escape(this.client);
		if(this._login && this._login.userName)
			ret+="&user="+this._login.userName;
		ret+="&encrypted="+(this.cipher ? true : false);
		ret+="&method=doUpload";
		if(this.cipher)
		{
			//The purpose of this is to ensure that the encrypted data is long enough to satisfy the
			//server that the client's encryption is valid
			params.serverPadding="padding";
			params=this.toJson(params);
			params=this.cipher.encrypt(params);
		}
		else
			params=this.toJson(params);
		ret+="&data="+escape(params);
		return ret;
	},

	addClientListener: function(eventName, listener){
		var listeners=this.theListeners[eventName];
		if(!listeners)
			listeners=[];
		listeners.push(listener);
		this.theListeners[eventName]=listeners;
	},

	fireClientEvent: function(eventName){
		var listeners=this.theListeners[eventName];
		if(!listeners)
			return;

		var args=[];
		for(var a=1;a<arguments.length;a++)
			args[a-1]=arguments[a];

		for(var L=0;L<listeners.length;L++)
		{
			if(typeof listeners[L] == "function")
				listeners[L].apply(listeners[L], args);
			else if(typeof listeners.exec != "function")
				listeners[L].exec.apply(listeners[L].context, args);
			else
				throw new Error("No exec method in listener for event "+eventName);
		}
	},

	validateJson: function(obj){
		if("Inf"==obj)
			return Infinity;
		if("-Inf"==obj)
			return -Infinity;
		if(obj==null)
			return null;
		if(typeof obj == "object")
		{
			for(var f in obj)
			{
				var value=obj[f];
				if(typeof value=="function")
					continue;
				value=this.validateJson(value);
				if(value)
					obj[f]=value;
			}
		}
		return null;
	},

	_serializeDeep: function(val){
		for(var field in val)
			if(typeof val[field] == "object" || typeof val[field] == "array")
				val[field]=this.toJson(val[field]);
	},

	toJsonShallow: function(val, notRec){
		if(!val || typeof val != "object")
			return val;
		var copy=null;
		if(val.length && val.splice)
		{
			copy=[];
			for(var i in val)
				copy[i]=this.toJsonShallow(val[i], true);
		}
		else if(!notRec)
		{
			copy={};
			for(var f in val)
			{
				if(typeof copy[f]=="function")
					continue;
				copy[f]=""+val[f];
			}
		}
		else
			copy=""+val;
		return this.toJson(copy);
	},

	disconnect: function(){
		//TODO disconnect comet if connected
	},

	_getEncryptionKey: function(hashing, password){
		return this._hash(password, hashing);
	},

	partialHash: function(password, params){
		var primaryMults=params.primaryMultiples;
		var primaryMods=params.primaryModulos;
		var ret=[];
		for(var i=0;i<primaryMults.length;i++)
			ret[i]=this._primaryHashDigit(password, primaryMults[i], primaryMods[i]);
		return ret;
	},

	_primaryHashDigit: function(password, mult, mod){
		var ret = 0;
		for(var p = 0; p < password.length; p++)
			ret = this._multModCalc(ret+password.charCodeAt(p), mult, mod);
		return ret;
	},

	_hash: function(password, params){
		var ret=this.partialHash(password, params);
		var secondaryMults=params.secondaryMultiples;
		var secondaryMods=params.secondaryModulos;
		for(var i = 0; i < ret.length; i++)
		{
			for(var j = 0; j < secondaryMults.length; j++)
				ret[i]=this._multModCalc(ret[i], secondaryMults[j], secondaryMods[j]);
		}
		return ret;
	},

	/*
	 * This function performs the operation (digit * mult) % mod correctly.
	 * Javascript uses floating-point operations to do this, which may cause
	 * small errors to propagate if done natively. This function accounts for
	 * and fixes those errors.
	 */
	_multModCalc: function(digit, mult, mod){
		var newVal=Math.floor(digit * mult);
		var lastDigit=(digit%1000)*(mult%1000);
		lastDigit=lastDigit%1000;
		lastDigit-=newVal%1000;
		if(lastDigit>500)
			lastDigit-=1000;
		else if(lastDigit<-500)
			lastDigit+=1000;
		newVal = newVal % mod+lastDigit;
		if(newVal<0)
			newVal+=mod;
		else if(newVal>=mod)
			newVal-=mod;
		return newVal;
	}
});
