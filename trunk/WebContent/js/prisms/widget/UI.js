
dojo.require("prisms.widget.PrismsDialog");
dojo.require("dijit.ProgressBar");

dojo.provide("prisms.widget.UI");
dojo.declare("prisms.widget.UI", prisms.widget.PrismsDialog, {

	pluginName: "No pluginName specified",

	prisms: null,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this._setupUI();
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
		this.inputText=new dijit.form.TextBox({style: "width:400px"});
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
		this.progressBar=new dijit.ProgressBar();
		this.progressDiv=document.createElement("div");
		this.progressDiv.style.display="none";
		this.progressDiv.appendChild(this.progressBar.domNode);
		td.appendChild(this.progressDiv);

		tr=table.insertRow(-1);
		td=tr.insertCell(0);
		td.style.textAlign="center";
		this.okButton=new dijit.form.Button({label: "OK"});
		this.okDiv=document.createElement("div");
		this.okDiv.style.textAlign="center";
		td.appendChild(this.okDiv);
		this.okDiv.appendChild(this.okButton.domNode);
		
		this.cancelButton=new dijit.form.Button({label: "Cancel"});
		this.cancelDiv=document.createElement("div");
		this.cancelDiv.style.textAlign="center";
		td=tr.insertCell(1);
		td.style.textAlign="center";
		td.appendChild(this.cancelDiv);
		this.cancelDiv.style.display="none";
		this.cancelDiv.appendChild(this.cancelButton.domNode);
		this.containerNode.appendChild(table);

		dojo.connect(this.okButton, "onClick", this, this.okClicked);
		dojo.connect(this.cancelButton, "onClick", this, this.cancelClicked);
	},

	processEvent: function(event){
		if(this.open && this.event && this.event.method!=event.method)
			this._reset();
		if(this.event && this.event.refreshed && this.event.method==event.method)
			event.refreshed=this.event.refreshed;
		this.event=event;
	},

	postProcessEvents: function(){
		var event=this.event;
		if(!event)
			return;
		if(event.message)
		{
			event.message=PrismsUtils.fixUnicodeString(event.message);
			var msg=event.message.split("\n");
			event.message=msg.join("<br />");
		}
		if(event.method=="error")
			this.showError(event.message);
		else if(event.method=="warning")
			this.showWarning(event.message);
		else if(event.method=="info")
			this.showInfo(event.message);
		else if(event.method=="confirm")
			this.showConfirm(event.message, event.messageID);
		else if(event.method=="input")
			this.showInput(event.message, event.init, event.messageID);
		else if(event.method=="select")
			this.showSelect(event.message, event.options, event.initSelection, event.messageID);
		else if(event.method=="progress")
			this.showProgress(event.message, event.length, event.progress, event.cancelable,
				event.messageID);
		else if(event.method=="close")
			this.hide();
		else
			this.prisms.error("Unrecognized UI event: "+this.prisms.toJson(event));
	},

	showError: function(message){
		this.containerNode.style.backgroundColor="#FF4040";
		this.setTitle("Error");
		this.labelNode.innerHTML=message;
		this.returnType=null;
		this.show();
	},

	showWarning: function(message){
		this.containerNode.style.backgroundColor="#FFFF40";
		this.setTitle("Warning");
		this.labelNode.innerHTML=message;
		this.returnType=null;
		this.show();
	},

	showInfo: function(message){
		this.containerNode.style.backgroundColor="#8080FF";
		this.setTitle("Information Message");
		this.labelNode.innerHTML=message;
		this.returnType=null;
		this.show();
	},

	showConfirm: function(message, messageID){
		this.containerNode.style.backgroundColor="#FFFFFF";
		this.setTitle("Confirmation Required");
		this.labelNode.innerHTML=message;
		this.cancelDiv.style.display="block";
		this.returnType="boolean";
		this.show();
	},

	showInput: function(message, init, messageID){
		this.containerNode.style.backgroundColor="#FFFFFF";
		this.setTitle("Input Required");
		this.labelNode.innerHTML=message;
		this.cancelDiv.style.display="block";
		this.inputDiv.style.display="block";
		if(init)
			this.inputText.setValue(init);
		this.returnType="text";
		this.show();
	},

	showSelect: function(message, options, init, messageID){
		this.containerNode.style.backgroundColor="#FFFFFF";
		this.setTitle("Selection Required");
		this.labelNode.innerHTML=message;
		this.cancelDiv.style.display="block";
		this.selectDiv.style.display="block";
		for(var i=0;i<options.length;i++)
		{
			var y=document.createElement("option");
			y.text=options[i];
		    y.value=options[i];
			var x=this.selectBox;
			try{
				x.add(y,null); // standards compliant
			} catch(ex) {
				x.add(y); // IE only
				console.dir(x);
			}
		}
		if(init>=0 && init<options.length)
			this.selectBox.selectedIndex=init;
		
		this.returnType="selection";
		this.show();
	},

	showProgress: function(message, taskScale, taskProgress, cancelable, messageID){
		if(!this.event.refreshed)
		{ // Don't display the progress dialog initially--see if it's still active on the server
			this.event.refreshed=true;
			this.prisms.callApp(this.pluginName, "refresh");
			return;
		}
		this.containerNode.style.backgroundColor="#8080FF";
		this.setTitle("Processing--Please Wait");
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

		var pluginName=this.pluginName;
		if(!this.progressUpdateTimer)
		{
			var self=this;
			this.progressUpdateTimer=window.setInterval(function(){
				if(self.prisms.isActive())
					self.prisms.callApp(pluginName, "refresh");
				else
					self.hide();
			}, 1000);
		}

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
		if(this.progressUpdateTimer)
		{
			window.clearInterval(this.progressUpdateTimer);
			delete this.progressUpdateTimer;
		}
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
		this.titleNode.innerHTML=title;
	}
});