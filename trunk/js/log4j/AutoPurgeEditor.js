
__dojo.require("dijit.form.NumberSpinner");
__dojo.require("prisms.widget.TimeAmountEditor");

__dojo.provide("log4j.AutoPurgeEditor");
__dojo.declare("log4j.AutoPurgeEditor", [prisms.widget.PrismsDialog], {

	templatePath: "__webContentRoot/view/log4j/templates/autoPurgeEditor.html",

	widgetsInTemplate: true,

	pluginName: "No pluginName specified",

	isDisplayed: false,

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.prisms.loadPlugin(this);
		var self=this;
		this.prisms.addClientListener("editAutoPurge", function(){
			self.prisms.callApp(self.pluginName, "display");
		});
		this._excludeConnects=[];
		this._setupUI();
	},

	_setupUI: function(){
		this.dataLock=true;
		this.titleNode.innerHTML="Logging Auto-Purge Settings";
		this.containerNode.style.overflow="none";
		var table=document.createElement("table");
		this.containerNode.appendChild(table);
		table.style.width="290px";

		var tr=table.insertRow(-1);
		this.modifiedRow=tr;
		var td=tr.insertCell(-1);
		td.colSpan="2";
		td.style.fontWeight="bold";
		td.style.color="red";
		td.innerHTML="Auto Purge settings are not saved until &quot;Save&quot; is clicked."
			+"  Click &quot;Cancel&quot; to undo changes.";

		tr=table.insertRow(-1);
		tr.style.whiteSpace="nowrap";
		td=tr.insertCell(-1);
		td.style.textAlign="right";
		td.innerHTML="Current Entry Count:";
		td=tr.insertCell(-1);
		this.entryCountCell=td;

		tr=table.insertRow(-1);
		tr.style.whiteSpace="nowrap";
		td=tr.insertCell(-1);
		td.style.textAlign="right";
		td.innerHTML="Max Size:";
		td.title="The maximum amount of size log entries may take up before some are purged";
		td=tr.insertCell(-1);
		td.innerHTML=" KB";
		this.sizeSpinner=new __dijit.form.NumberSpinner({});
		this.sizeSpinner.domNode.style.width="100px";
		__dojo.connect(this.sizeSpinner, "onChange", this, this._sizeChanged);
		td.insertBefore(this.sizeSpinner.domNode, td.childNodes[0]);
	
		tr=table.insertRow(-1);
		tr.style.whiteSpace="nowrap";
		td=tr.insertCell(-1);
		td.style.textAlign="right";
		td.innerHTML="Current Total Size:";
		td=tr.insertCell(-1);
		this.totalSizeCell=td;

		tr=table.insertRow(-1);
		tr.style.whiteSpace="nowrap";
		td=tr.insertCell(-1);
		td.style.textAlign="right";
		td.innerHTML="Max Age:";
		td.title="The maximum age an entry can be before it is purged";
		td=tr.insertCell(-1);
		this.ageEditor=new prisms.widget.TimeAmountEditor({monthsEnabled: false, secondsEnabled: false});
		__dojo.connect(this.ageEditor, "onChange", this, this._ageChanged);
		td.appendChild(this.ageEditor.domNode);

		tr=table.insertRow(-1);
		tr.style.whiteSpace="nowrap";
		td=tr.insertCell(-1);
		td.style.textAlign="right";
		td.innerHTML="Current Oldest Entry:";
		td=tr.insertCell(-1);
		this.oldestTimeCell=td;

		tr=table.insertRow(-1);
		tr.style.whiteSpace="nowrap";
		td=tr.insertCell(-1);
		td.style.textAlign="center";
		td.innerHTML="Excluded Searches";
		td.title="Searches whose results cannot be purged";
		td=tr.insertCell(-1);
		this.excludeLink=this.createLink(true, true);
		td.appendChild(this.excludeLink);

		tr=table.insertRow(-1);
		tr.style.whiteSpace="nowrap";
		td=tr.insertCell(-1);
		td.colSpan="2";
		td.style.overflow="auto";
		this.excludeTable=document.createElement("table");
		this.excludeTable.style.width="100%"
		td.appendChild(this.excludeTable);

		tr=table.insertRow(-1);
		tr.style.whiteSpace="nowrap";
		td=tr.insertCell(-1);
		td.colSpan="2";
		var btnTable=document.createElement("table");
		btnTable.style.width="100%"
		td.appendChild(btnTable);
		tr=btnTable.insertRow(-1);
		td=tr.insertCell(-1);
		td.style.textAlign="center";
		this.saveButton=new __dijit.form.Button({label: "Save"});
		__dojo.connect(this.saveButton, "onClick", this, this.save);
		td.appendChild(this.saveButton.domNode);
		td=tr.insertCell(-1);
		td.style.textAlign="center";
		this.cancelButton=new __dijit.form.Button({label: "Cancel"});
		__dojo.connect(this.cancelButton, "onClick", this, this.hide);
		td.appendChild(this.cancelButton.domNode);
		this.dataLock=false;
	},

	processEvent: function(event){
		if(event.method=="setAutoPurger")
		{
			this.setAutoPurger(event.purger);
			this.show();
			this.cancelButton.focus();
		}
		else if(event.method=="setDSInfo")
			this.setDSInfo(event);
		else
			throw new Error("Unrecognized "+this.pluginName+" event: "+event.method);
	},

	setAutoPurger: function(purger){
		this.dataLock=true;
		try{
			this.isDisplayed=true;
			this.purger=purger;
			PrismsUtils.setTableRowVisible(this.modifiedRow, false);
			this.sizeSpinner.setAttribute("disabled", !purger.enabled);
			this.ageEditor.setDisabled(!purger.enabled);

			this.sizeSpinner.setValue(purger.size);
			this.ageEditor.setValue(purger.age);
			while(this.excludeTable.rows.length>0)
				this.excludeTable.deleteRow(this.excludeTable.rows.length-1);
			if(purger.enabled)
				this.excludeLink.style.color=purger.enabled ? "blue" : "gray";
			for(var i=0;i<purger.excludes.length;i++)
			{
				var tr=this.excludeTable.insertRow(-1);
				var td=tr.insertCell(0);
				td.innerHTML=PrismsUtils.fixUnicodeString(purger.excludes[i].display);
				td.title=purger.excludes[i].title;
				td=tr.insertCell(1);
				td.appendChild(this.createLink(false, purger.enabled && !purger.excludes[i].permanent));
			}

			this.entryCountCell.innerHTML="Unknown";
			this.totalSizeCell.innerHTML="Unknown";
			this.oldestTimeCell.innerHTML="Unknown";
		} finally{
			this.dataLock=false;
		}
	},

	setDSInfo: function(event){
		this.entryCountCell.innerHTML=event.entryCount;
		this.totalSizeCell.innerHTML=event.totalSize+" KB";
		if(event.oldestTime)
			this.oldestTimeCell.innerHTML=event.oldestTime;
	},

	shutdown: function(){
		this.entryCountCell.innerHTML="Unknown";
		this.totalSizeCell.innerHTML="Unknown";
		this.oldestTimeCell.innerHTML="Unknown";
		while(this.excludeTable.rows.length>0)
			this.excludeTable.deleteRow(this.excludeTable.rows.length-1);
		this.dataLock=true;
		try{
			this.hide();
		} finally{
			this.dataLock=false;
		}
	},

	save: function(){
		var purger={
			size: this.sizeSpinner.getValue(),
			age: this.ageEditor.getValue().seconds,
			excludes: []
		};
		for(var i=0;i<this.excludeTable.rows.length;i++)
			purger.excludes.push(this.excludeTable.rows[i].cells[0].innerHTML);
		this.prisms.callApp(this.pluginName, "setAutoPurger", {purger: purger});
		this.dataLock=true;
		try{
			this.hide();
		} finally{
			this.dataLock=false;
		}
	},

	hide: function(){
		this.inherited(arguments);
		if(this.dataLock)
			return;
		if(this.isDisplayed)
		{
			this.prisms.callApp(this.pluginName, "hide");
			this.isDisplayed=false;
		}
	},

	_sizeChanged: function(){
		console.log("Size changed to "+this.sizeSpinner.getValue());
		if(this.dataLock)
			return;
		if(!this.purger.enabled)
		{
			this.prisms.error("You do not have permission to modify the auto-purge settings");
			return;
		}
		var size=this.sizeSpinner.getValue();
		var err=null;
		if(isNaN(size))
			err=size+" is not a size";
		if(size<this.purger.minSize)
			err="Max Size cannot be less than "+this.purger.minSize;
		if(size>this.purger.maxSize)
			err="Max Size cannot be greater than "+this.purger.maxSize;
		if(err)
		{
			this.prisms.error(err);
			this.dataLock=true;
			try{
				this.sizeSpinner.setValue(this.purger.size);
			} finally{
				this.dataLock=false;
			}
		}
		else
		{
			this.purger.size=size;
			PrismsUtils.setTableRowVisible(this.modifiedRow, true);
		}
	},

	_ageChanged: function(){
		if(this.dataLock)
			return;
		if(!this.purger.enabled)
		{
			this.prisms.error("You do not have permission to modify the auto-purge settings");
			return;
		}
		var age=this.ageEditor.getValue().seconds;
		var err=null;
		if(isNaN(age))
			err=age+" is not an age";
		if(age<this.purger.minAge)
			err="Max Size cannot be less than "+this.purger.minAgeDisplay;
		if(age>this.purger.maxAge)
			err="Max Size cannot be greater than "+this.purger.maxAgeDisplay;
		if(err)
		{
			this.prisms.error(err);
			this.dataLock=true;
			try{
				this.ageEditor.setValue(this.purger.age);
			} finally{
				this.dataLock=false;
			}
		}
		else
		{
			this.purger.age=age;
			PrismsUtils.setTableRowVisible(this.modifiedRow, true);
		}
	},

	_addExclude: function(){
		if(this.dataLock)
			return;
		if(!this.purger.enabled)
			return;
		var tr=this.excludeTable.insertRow(-1);
		var td=tr.insertCell(-1);
		td.innerHTML="<input type=\"text\" />";
		var input=td.childNodes[0];
		input.value="Enter Exclusion Search";
		var changeConn=null;
		var blurConn=null;
		var inputTD=td;
		changeConn=__dojo.connect(input, "onchange", this, function(){
			__dojo.disconnect(changeConn);
			__dojo.disconnect(blurConn);
			if(!input.value || input.value.length==0)
				this.excludeTable.deleteRow(tr.rowIndex);
			else
			{
				inputTD.innerHTML=input.value;
				PrismsUtils.setTableRowVisible(this.modifiedRow, true);
			}
		});
		blurConn=__dojo.connect(input, "onblur", this, function(){
			__dojo.disconnect(changeConn);
			__dojo.disconnect(blurConn);
			this.excludeTable.deleteRow(tr.rowIndex);
		});
		td=tr.insertCell(-1);
		td.appendChild(this.createLink(false, true));
		input.select();
		input.focus();
	},

	_doRemove: function(tr){
		if(this.dataLock)
			return;
		if(!this.purger.enabled)
			return;
		var logger=tr.cells[0].innerHTML;
		for(var i=0;i<this.purger.excludes.length;i++)
			if(this.purger.excludes[i]==logger)
				return;
		this.excludeTable.deleteRow(tr.rowIndex);
		PrismsUtils.setTableRowVisible(this.modifiedRow, true);
	},

	createLink: function(add, enabled){
		var div=document.createElement("div");
		div.innerHTML="<a href=\"\" onclick=\"event.returnValue=false; return false;\" style=\"color:blue\">"
			+(add ? "Add..." : "Remove")+"</a>";
		var a=div.childNodes[0];
		if(enabled)
		{
			var conn=__dojo.connect(a, "onclick", this, function(){
				if(add)
					this._addExclude();
				else
					this._doRemove(a.parentNode.parentNode)
			});
			this._excludeConnects.push(conn);
		}
		else
			a.style.color="gray";
		return a;
	}
});
