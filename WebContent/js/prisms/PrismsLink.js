
dojo.provide("prisms.PrismsLink");

dojo.require("dojox.encoding.crypto.Blowfish");

dojo.declare("prisms.PrismsLink", null, {
	application: "No Application Specified",

	client: "No Client Specified",

	servletURL: "No Servlet URL Specified",

	imageURL: "No Image URL Specified",

	connectImmediately: false,

	cipher: null,

	encryptionType: "dojo-blowfish",

	constructor: function(arguments){
		if(arguments.application)
			this.application=arguments.application;
		if(arguments.client)
			this.client=arguments.client;
		if(arguments.servletURL)
			this.servletURL=arguments.servletURL;
		if(arguments.imageURL)
			this.imageURL=arguments.imageURL;
		if(arguments.connectImmediately)
			this.connectImmediately=arguments.connectImmediately;
		if(arguments.cipher)
			this.cipher=arguments.cipher;
		
		this.BASE_64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
		this.theMaxKeyLength = 448 / 8;

		this.plugins={};
		this.theListeners={};
		this.lastPinged=0;
		this.error=dojo.hitch(console, console.error);
		this.debug=dojo.hitch(console, console.debug);
		this.toJson=dojo.hitch(dojo, dojo.toJson);
		if(!this.cipher)
			this.cipher=this.createCipher();
		this._login={};
		if(this.connectImmediately)
			this.prismsConnect();
	},

	createCipher: function(){
		if(this.encryptionType=="dojo-blowfish")
		{
			var _blowfishAO={cipherMode: dojox.encoding.crypto.cipherModes.ECB,
					outputType: dojox.encoding.crypto.outputTypes.Base64};
			return {
				encrypt: function(text, key){
					return dojox.encoding.crypto.Blowfish.encrypt(text, key, _blowfishAO);
				},

				decrypt: function(encrypted, key){
					return dojox.encoding.crypto.Blowfish.decrypt(encrypted, key, _blowfishAO);
				}
			};
		}
		else if(this.encryptionType=="AES")
		{
			GibberishAES.size(128);
			return {
				encrypt: function(text, key){
					return GibberishAES.enc(text, key);
				},

				decrypt: function(encrypted, key){
					return GibberishAES.dec(encrypted, key);
				}
			};
		}
		else
		{
			return {
				encrypt: function(text, key){
					return text;
				},

				decrypt: function(encrypted, key){
					return encrypted;
				}
			};
		}
	},
	
	getDefaultUser: function(){
		return null;
	},

	prismsConnect: function(){
		if(!this._login.userName)
			this._login.userName=this.getDefaultUser();
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
			var plugin=pluginsCalled[p];
			if(plugin && typeof plugin.postProcessEvents == "function")
				plugin.postProcessEvents();
		}
	},

	processEvent: function(event){
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
		if(event.method=="getEvents")
			this.callApp(null, "getEvents");
		else if(event.method=="setVersion")
		{
			this.appVersion=event.version;
			this.appModified=event.modified;
			this.appLoaded();
		}
		else if(event.method=="callInit")
			this.callServer("init");
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
			this.startEncryption(event.hashing, event.error, event.postAction)
		}
		else if(event.method=="validate")
			this.doValidate(event.hashing, event.validationFailed);
		else if(event.method=="changePassword")
			this.doChangePassword(this._login.userName, event.hashing, event.error);
		else if(event.method=="init")
		{
			this.loginSucceeded((this._login ? this._login.userName : null));
			this.appLoading();
			this.started=true;
			if(!this.pingID)
			{
				var self=this;
				this.pingID=setInterval(function(){
					var now=new Date().getTime();
					if(now-self.lastPinged<29500)
						return;
					self.callApp(null, "getEvents");
				}, 10000);
			}
			for(var p in this.plugins)
				this.callApp(null, "addPlugin", {pluginToAdd: p});
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
		else if(event.method=="appLocked")
		{
			this.showAppLocked(event.message);
			this.callApp(null, "getEvents");
		}
		else if(event.method=="doDownload")
		{
			if(!this.isActive())
				return;
			var dlEvent={};
			dlEvent.plugin=event.downloadPlugin;
			dlEvent.method=event.downloadMethod;
			for(var p in event)
			{
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
			var ulEvent={};
			ulEvent.plugin=event.uploadPlugin;
			ulEvent.method=event.uploadMethod;
			for(var p in event)
			{
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
	},

	isActive: function(){
		return typeof this.pingID != "undefined";
	},

	startEncryption: function(hashing, error, postAction){
		this.shutdown();
		if(postAction)
			this._postEncryptionAction=postAction;
		if(this._login && hashing && hashing.user!=this._login.userName)
		{
			this._encryptionKey=null;
			this.callServer("init");
			this.appLoading();
		}
		else if(!this._login || error || !this._login.password || this._login.password.length==0)
		{
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
			this._encryptionKey=this._getEncryptionKey(hashing, this._login.password);
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
		alert("ERROR! THIS CLIENT DOES NOT SUPPORT VALIDATION!")
	},

	doChangePassword: function(user, hashing, error){
		// Default implementation--may be overridden by subclasses
		alert("ERROR! THIS CLIENT DOES NOT SUPPORT PASSWORD CHANGE!"
			+"\nContact your administrator or visit the manager page to change it")
	},

	doLogin: function(error){
		// Default implementation--should be overridden by subclasses
		alert("ERROR! LOGIN DIALOG NOT IMPLEMENTED!")
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

	submitLogin: function(userName, password){
		if(this.pingID)
		{
			clearInterval(this.pingID);
			delete this.pingID;
		}
		this._login={userName: userName, password: password};
		delete this._encryptionKey;
		if(this._postLoginAction)
		{
			if(this._postLoginAction=="startEncryption")
				this.startEncryption(this._hashing, null, "callInit");
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
			alert("This "+this.application+" session has expired--reloading application");
		window.location.reload();
	},

	showAppLocked: function(message){
		alert(this.application+" is temporarily locked");
	},

	getServerRequest: function(params){
		if(!params)
			params={};
		params.sessionID=this.sessionID;
		params.app=this.application;
		params.client=this.client;
		params.user=this._login.userName
		params.encrypted=this._encryptionKey!=null;
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
			params.data=this.cipher.encrypt(params.data, this._encryptionKey);
		return params;
	},

	_xhrCall: function(params, xhrArgs){
		this.lastPinged=new Date().getTime();
		params=this.getServerRequest(params);
		var self=this;
		var args={
			url: self.servletURL,
			sync: false,
			timeout: 30000,
			preventCache: true,
			handleAs: "text",
			content: params,
			load: function(data){
				if(data.charAt(0)!='[' && data.charAt(data.length-1)!=']')
				{
					if(!self._encryptionKey)
					{
						self.error("Encryption not set!")
						return;
					}
					else
					{
						var decrypted=self.cipher.decrypt(data, self._encryptionKey);
						var len=decrypted.length;
						while(decrypted.charAt(len-1)<' ')
							len--;
						decrypted=decrypted.substring(0, len);
						data=decrypted;
					}
				}
				data=dojo.eval(data);
				self.processEvents(data);
			},
			error: function(error){
				self.appLoaded();
				if(error.status==0)
				{
					self.error(self.application+" is not accessible.  Please try again later.");
					self.shutdown();
				}
				else if(error.dojoType=="timeout")
					self.error(self.application+" timed out.  Try refreshing.\n"
						+"If that doesn't work, try again later.");
				else
					self.error("Could not execute server call: ", error);
				throw error;
			}
		};
		if(xhrArgs)
			dojo.mixin(args, xhrArgs);
		dojo.xhrPost(args);
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

	getDynamicImageSource: function(plugin, method, xOffset, yOffset, refWidth, refHeight, width,
		height){
		var params={plugin: plugin, method: method, xOffset: xOffset, yOffset: yOffset,
			refWidth: refWidth, refHeight: refHeight, width: width, height: height};
		var ret=this.imageURL+"?";
		ret+="sessionID="+this.sessionID;
		ret+="&app="+escape(this.application);
		ret+="&client="+escape(this.client);
		if(this._login && this._login.userName)
			ret+="&user="+this._login.userName;
		ret+="&encrypted="+(this._encryptionKey!=null);
		ret+="&method=generateImage";
		if(this._encryptionKey)
		{
			//The purpose of this is to ensure that the encrypted data is long enough to satisfy the
			//server that the client's encryption is valid
			params.serverPadding="padding";
			params=this.toJson(params);
			params=this.cipher.encrypt(params, this._encryptionKey);
		}
		else
			params=this.toJson(params);
		ret+="&data="+escape(params);
		return ret;
	},

	getDownloadSource: function(params){
		var ret=this.servletURL+"?";
		ret+="sessionID="+this.sessionID;
		ret+="&app="+escape(this.application);
		ret+="&client="+escape(this.client);
		if(this._login && this._login.userName)
			ret+="&user="+this._login.userName;
		ret+="&encrypted="+(this._encryptionKey!=null);
		ret+="&method=getDownload";
		if(this._encryptionKey)
		{
			//The purpose of this is to ensure that the encrypted data is long enough to satisfy the
			//server that the client's encryption is valid
			params.serverPadding="padding";
			params=this.toJson(params);
			params=this.cipher.encrypt(params, this._encryptionKey);
		}
		else
			params=this.toJson(params);
		ret+="&data="+escape(params);
		return ret;
	},

	getUploadURL: function(params){
		var ret=this.servletURL+"?";
		ret+="sessionID="+this.sessionID;
		ret+="&app="+escape(this.application);
		ret+="&client="+escape(this.client);
		if(this._login && this._login.userName)
			ret+="&user="+this._login.userName;
		ret+="&encrypted="+(this._encryptionKey!=null);
		ret+="&method=doUpload";
		if(this._encryptionKey)
		{
			//The purpose of this is to ensure that the encrypted data is long enough to satisfy the
			//server that the client's encryption is valid
			params.serverPadding="padding";
			params=this.toJson(params);
			params=this.cipher.encrypt(params, this._encryptionKey);
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
				copy[f]=""+val[f];
		}
		else
			copy=""+val;
		return this.toJson(copy);
	},

	_getEncryptionKey: function(hashing, password){
		var hash=this._hash(password, hashing);
		var keys=[];
		for(var h=0;h<hash.length;h++)
			keys.push(this._to64Str(hash[h]));
		while(this._isTooBig(keys))
			keys=this._downsize(keys);
		var ret="";
		for(var k=0;k<keys.length;k++)
			ret+=keys[k];
		return ret;
	},

	_to64Str: function(value){
		var ret = "";
		while(value > 0)
		{
			ret += this.BASE_64.charAt(value % 64);
			value =Math.floor(value/64);
		}
		return ret;
	},

	_isTooBig: function(keys){
		var size = 0;
		for(var k = 0; k < keys.length; k++)
			size += keys[k].length * 8;
		return size > this.theMaxKeyLength;
	},

	_downsize: function(keys){
		// Cancels the highest 64-base digit in each element to reduce its size
		// with minimal complexity loss
		var allOnes=true;
		for(var k = 0; k < keys.length; k++)
			if(keys[k].length > 1)
			{
				keys[k] = keys[k].slice(1);
				allOnes=false;
			}
		if(allOnes)
			keys.splice(0, 1);
		return keys;
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
			ret = ((ret + password.charCodeAt(p)) * mult) % mod;
		return ret;
	},

	_hash: function(password, params){
		var ret=this.partialHash(password, params);
		var secondaryMults=params.secondaryMultiples;
		var secondaryMods=params.secondaryModulos;
		for(var i = 0; i < ret.length; i++)
		{
			for(var j = 0; j < secondaryMults.length; j++)
				ret[i]=this._secondaryHashDigit(ret[i], secondaryMults[j], secondaryMods[j]);
		}
		return ret;
	},

	/*
	 * This function performs the operation (digit * mult) % mod correctly.
	 * Javascript uses floating-point operations to do this, which may cause
	 * small errors to propagate if done natively. This function accounts for
	 * and fixes those errors.
	 */
	_secondaryHashDigit: function(digit, mult, mod){
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