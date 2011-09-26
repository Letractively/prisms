
__dojo.require("prisms.widget.PrismsDialog");
__dojo.require("dijit.ProgressBar");

__dojo.provide("prisms.widget.UI");
__dojo.declare("prisms.widget.UI", prisms.widget.PrismsDialog, {

	pluginName: "UI",

	prisms: null,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this._setupUI();
		this._lastUpdate=0;
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.prisms.loadPlugin(this);
	},

	_setupUI: function(){
		this.titleBar.style.textAlign="center";
		this.titleNode.style.textAlign="center";
		var table=document.createElement("table");
		table.style.width="500px";
		
		tr=table.insertRow(-1);
		td=tr.insertCell(0);
		td.colSpan=2;
		this.labelNode=document.createElement("div");
		this.labelNode.style.width="100%";
		this.labelNode.style.textAlign="center";
		td.appendChild(this.labelNode);

		tr=table.insertRow(-1);
		td=tr.insertCell(0);
		td.colSpan=2;
		this.inputText=new __dijit.form.TextBox({style: "width:400px"});
		this.inputDiv=document.createElement("div");
		this.inputDiv.style.display="none";
		this.inputDiv.appendChild(this.inputText.domNode);
		td.appendChild(this.inputDiv);
		
		tr=table.insertRow(-1);
		td=tr.insertCell(0);
		td.colSpan=2;
		this.selectBox=document.createElement("select");
		this.selectDiv=document.createElement("div");
		this.selectDiv.style.display="none";
		this.selectDiv.appendChild(this.selectBox);
		td.appendChild(this.selectDiv);

		tr=table.insertRow(-1);
		td=tr.insertCell(0);
		td.colSpan=2;
		this.progressBar=new __dijit.ProgressBar();
		this.progressDiv=document.createElement("div");
		this.progressDiv.style.display="none";
		this.progressDiv.appendChild(this.progressBar.domNode);
		td.appendChild(this.progressDiv);

		tr=table.insertRow(-1);
		td=tr.insertCell(0);
		td.style.textAlign="center";
		this.okButton=new __dijit.form.Button({label: "OK"});
		this.okDiv=document.createElement("div");
		this.okDiv.style.textAlign="center";
		td.appendChild(this.okDiv);
		this.okDiv.appendChild(this.okButton.domNode);
		
		this.cancelButton=new __dijit.form.Button({label: "Cancel"});
		this.cancelDiv=document.createElement("div");
		this.cancelDiv.style.textAlign="center";
		td=tr.insertCell(1);
		td.style.textAlign="center";
		td.appendChild(this.cancelDiv);
		this.cancelDiv.style.display="none";
		this.cancelDiv.appendChild(this.cancelButton.domNode);
		this.containerNode.appendChild(table);

		__dojo.connect(this.okButton, "onClick", this, this.okClicked);
		__dojo.connect(this.cancelButton, "onClick", this, this.cancelClicked);
	},

	processEvent: function(event){
		if(event.timeStamp && this.event && this.event.timeStamp>event.timeStamp)
			return;
		if(this.open && this.event && this.event.method!=event.method)
			this._reset();
		if(this.event && this.event.refreshed && this.event.method==event.method)
			event.refreshed=this.event.refreshed;
		this.event=event;
	},

	shutdown: function(){
	},

	postProcessEvents: function(){
		var event=this.event;
		if(!event)
			return;
		if(event.message)
		{
			var leftAlign=event.message.indexOf('\t')>=0;
			event.message=PrismsUtils.fixUnicodeString(event.message);
			if(leftAlign)
				this.labelNode.style.textAlign="left";
			else
				this.labelNode.style.textAlign="center";

			var spaces=0;
			for(var c=0;c<event.message.length;c++)
			{
				if(event.message.charAt(c)==' ')
					spaces++;
				else if(spaces>1)
				{
					var newMsg=event.message.substring(0, c-spaces);
					for(var s=0;s<spaces;s++)
						newMsg+="&nbsp;";
					newMsg+=event.message.substring(c);
					spaces=0;
					event.message=newMsg;
				}
				else if(spaces>0)
					spaces=0;
			}
		}
		if(event.method=="error")
			this.showError(event.message, event);
		else if(event.method=="warning")
			this.showWarning(event.message, event);
		else if(event.method=="info")
			this.showInfo(event.message, event);
		else if(event.method=="confirm")
			this.showConfirm(event.message, event.messageID, event);
		else if(event.method=="input")
			this.showInput(event.message, event.init, event.messageID, event);
		else if(event.method=="select")
			this.showSelect(event.message, event.options, event.initSelection, event.messageID, event);
		else if(event.method=="progress")
			this.showProgress(event.message, event.length, event.progress, event.cancelable,
				event.messageID, event);
		else if(event.method=="close")
			this.hide();
		else
			this.prisms.error("Unrecognized UI event: "+this.prisms.toJson(event));
	},

	showError: function(message, event){
		this.containerNode.style.backgroundColor="#FF4040";
		if(event.title)
			this.setTitle(PrismsUtils.fixUnicodeString(event.title));
		else
			this.setTitle("Error");
		if(event.okLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.okLabel));
		else
			this.okButton.setLabel("OK");
		this.labelNode.innerHTML=message;
		this.returnType=null;
		this.show();
	},

	showWarning: function(message, event){
		this.containerNode.style.backgroundColor="#FFFF40";
		if(event.title)
			this.setTitle(PrismsUtils.fixUnicodeString(event.title));
		else
			this.setTitle("Warning");
		if(event.okLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.okLabel));
		else
			this.okButton.setLabel("OK");
		this.labelNode.innerHTML=message;
		this.returnType=null;
		this.show();
	},

	showInfo: function(message, event){
		this.containerNode.style.backgroundColor="#B0B0FF";
		if(event.title)
			this.setTitle(PrismsUtils.fixUnicodeString(event.title));
		else
			this.setTitle("Information Message");
		if(event.okLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.okLabel));
		else
			this.okButton.setLabel("OK");
		this.labelNode.innerHTML=message;
		this.returnType=null;
		this.show();
	},

	showConfirm: function(message, messageID, event){
		this.containerNode.style.backgroundColor="#FFFFFF";
		if(event.title)
			this.setTitle(PrismsUtils.fixUnicodeString(event.title));
		else
			this.setTitle("Confirmation Required");
		if(event.okLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.okLabel));
		else
			this.okButton.setLabel("OK");
		if(event.cancelLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.cancelLabel));
		else
			this.cancelButton.setLabel("Cancel");
		this.labelNode.innerHTML=message;
		this.cancelDiv.style.display="block";
		this.returnType="boolean";
		this.show();
	},

	showInput: function(message, init, messageID, event){
		this.containerNode.style.backgroundColor="#FFFFFF";
		if(event.title)
			this.setTitle(PrismsUtils.fixUnicodeString(event.title));
		else
			this.setTitle("Input Required");
		if(event.okLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.okLabel));
		else
			this.okButton.setLabel("OK");
		if(event.cancelLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.cancelLabel));
		else
			this.cancelButton.setLabel("Cancel");
		this.labelNode.innerHTML=message;
		this.cancelDiv.style.display="block";
		this.inputDiv.style.display="block";
		if(init)
			this.inputText.setValue(PrismsUtils.fixUnicodeString(init));
		this.returnType="text";
		this.show();
	},

	showSelect: function(message, options, init, messageID, event){
		this.containerNode.style.backgroundColor="#FFFFFF";
		if(event.title)
			this.setTitle(PrismsUtils.fixUnicodeString(event.title));
		else
			this.setTitle("Selection Required");
		if(event.okLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.okLabel));
		else
			this.okButton.setLabel("OK");
		if(event.cancelLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.cancelLabel));
		else
			this.cancelButton.setLabel("Cancel");
		this.labelNode.innerHTML=message;
		this.cancelDiv.style.display="block";
		this.selectDiv.style.display="block";
		for(var i=this.selectBox.options.length-1;i>=0;i--)
			this.selectBox.remove(this.selectBox.options[i]);
		for(var i=0;i<options.length;i++)
		{
			var y=document.createElement("option");
			y.text=PrismsUtils.fixUnicodeString(options[i]);
		    y.value=PrismsUtils.fixUnicodeString(options[i]);
			var x=this.selectBox;
			try{
				x.add(y,null); // standards compliant
			} catch(ex) {
				x.add(y); // IE only
			}
		}
		if(init>=0 && init<options.length)
			this.selectBox.selectedIndex=init;
		
		this.returnType="selection";
		this.show();
	},

	showProgress: function(message, taskScale, taskProgress, cancelable, messageID, event){
		if(!this.event.refreshed)
		{ // Don't display the progress dialog initially--see if it's still active on the server
			this.event.refreshed=true;
			this._lastUpdate=new Date().getTime();
			this.prisms.callApp(this.pluginName, "refresh");
			return;
		}
		this.containerNode.style.backgroundColor="#8080FF";
		if(event.title)
			this.setTitle(event.title);
		else
			this.setTitle("Processing--Please Wait");
		if(event.cancelLabel)
			this.okButton.setLabel(PrismsUtils.fixUnicodeString(event.cancelLabel));
		else
			this.cancelButton.setLabel("Cancel");
		this.labelNode.innerHTML=message;
		this.okDiv.style.display="none";
		if(cancelable)
		{
			this.cancelDiv.style.display="block";
			this.closeButtonNode.style.display="block";
		}
		else
		{
			this.cancelDiv.style.display="none";
			this.closeButtonNode.style.display="none";
		}
		this.progressDiv.style.display="block";
		if(taskScale>0 && taskProgress<taskScale)
		{
			this.progressBar.indeterminate=false;
			this.progressBar.maximum=taskScale;
			this.progressBar.progress=taskProgress;
		}
		else
			this.progressBar.indeterminate=true;
		this.progressBar.update();

		var now=new Date().getTime();
		if(this.prisms.isActive() && !this._waitingToUpdate)
		{
			this._waitingtoUpdate=true;
			if(now-this._lastUpdate>500)
			{
				this._lastUpdate=now;
				this.prisms.callApp(this.pluginName, "refresh", null, {
					finished: __dojo.hitch(this, function(){
						this._waitingToUpdate=false;
					})
				});
			}
			else
			{
				window.setTimeout(__dojo.hitch(this, function(){
					this._lastUpdate=new Date().getTime();
					this.prisms.callApp(this.pluginName, "refresh", null, {
						finished: __dojo.hitch(this, function(){
							this._waitingToUpdate=false;
						})
					});
				}), 500);
			}
		}
		if(!this.updateInterval)
			this.updateInterval=window.setInterval(__dojo.hitch(this, function(){
				var now=new Date().getTime();
				if(!this._waitingToUpdate && now-this._lastUpdate>4500)
				{
					this._lastUpdate=now;
					this.prisms.callApp(this.pluginName, "refresh");
				}
				else if(now-this._lastUpdate>=60000)
				{
					this._lastUpdate=now;
					this.prisms.callApp(this.pluginName, "refresh");
				}
			}), 5000);

		this.returnType="cancel";
		this.show();
	},

	hide: function(/*boolean*/ accept){
		this.inherited("hide", arguments);
		if(!this.event || !this.event.messageID)
			return;
		if(typeof accept != "boolean")
			accept=false; //User cancelled with the top-right X

		if(this.returnType=="boolean")
			this.prisms.callApp(this.pluginName, "eventReturned", {messageID: this.event.messageID,
				value: accept});
		else if(!accept && this.returnType)
			this.prisms.callApp(this.pluginName, "eventReturned", {messageID: this.event.messageID,
				accept: accept});
		else if(!this.returnType)
			this.prisms.callApp(this.pluginName, "eventReturned", {messageID: this.event.messageID});
		else if(this.returnType=="text")
			this.prisms.callApp(this.pluginName, "eventReturned", {messageID: this.event.messageID,
				value: this.inputText.getValue()});
		else if(this.returnType=="selection")
			this.prisms.callApp(this.pluginName, "eventReturned", {messageID: this.event.messageID,
				value: this.selectBox.options[this.selectBox.selectedIndex].text});
		else if(this.returnType=="cancel")
			this.prisms.callApp(this.pluginName, "cancel", {messageID: this.event.messageID});
		else
			throw new Error("Unrecognized return type: "+this.returnType);
		this._reset();
	},
	
	_reset: function(){
		if(this.updateInterval)
			window.clearInterval(this.updateInterval);
		this.updateInterval=null;
		this.closeButtonNode.style.display="block";
		this.okDiv.style.display="block";
		this.cancelDiv.style.display="none";
		this.inputDiv.style.display="none";
		this.inputText.setValue("");
		this.selectDiv.style.display="none";
		this.progressDiv.style.display="none";
		for(var i=this.selectBox.options.length-1;i>=0;i--)
			this.selectBox.remove(this.selectBox.options[i]);
		delete this.event;
	},

	okClicked: function(){
		this.hide(true);
	},

	cancelClicked: function(){
		this.prisms.callApp(this.pluginName, "cancel", {messageID: this.event.messageID});
		if(this.event==null || this.event.method!="progress")
			this.hide(false);
	},

	setTitle: function(title){
		this.titleNode.innerHTML=PrismsUtils.fixUnicodeString(title);
	}
});