
__dojo.require("dijit.layout.TabContainer");
__dojo.require("dijit.form.CheckBox");
__dojo.require("dijit.form.TextBox");
__dojo.require("dijit.form.NumberTextBox");
__dojo.require("dijit.form.NumberSpinner");
__dojo.require("prisms.widget.PrismsDialog");
__dojo.require("prisms.widget.ColorPicker");
__dojo.require("prisms.widget.ProportionEditor");

__dojo.provide("prisms.widget.Preferences");
__dojo.declare("prisms.widget.Preferences", prisms.widget.PrismsDialog, {

	prisms: null,

	pluginName: "Preferences",

	connectors: [],

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.titleNode.style.fontWeight="bold";
		this.titleNode.style.fontSize="large";
		var self=this;
		__dojo.connect(this, "hide", function(){
			self.clearTabs();
		});
		var table=document.createElement("table");
		this.containerNode.appendChild(table);
		var tabPane=new __dijit.layout.TabContainer();
		table.insertRow(-1).insertCell(-1).appendChild(tabPane.domNode);
		this.tabPane=tabPane;
		this.tabPane.startup();
		this.tabPane.domNode.style.width="640px";
		this.tabPane.domNode.style.height="400px";
		this.tabs=[];
		var ok=new __dijit.form.Button({"label": "OK"});
		var cell=table.insertRow(-1).insertCell(-1);
		cell.align="center";
		cell.appendChild(ok.domNode);
		__dojo.connect(ok, "onClick", this, this.hide);
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.titleNode.innerHTML=PrismsUtils.fixUnicodeString(this.prisms.application)+" Preferences";
		this.prisms.loadPlugin(this);
	},

	processEvent: function(event){
		if(event.method=="show")
		{
			this.setData(event.data)
			this.show();
		}
		else if(event.method=="set")
		{
			if(!this.open)
				return;
			this.setDatum(event.domain, event.prefName, event.type, event.value);
		}
		else if(event.method=="setVisible")
			this.setVisible(event.visible);
		else
			throw new Error("unrecognized "+this.pluginName+" event: "+this.prisms.toJson(event));
	},

	shutdown: function(){
		this.hide();
		this.clearTabs();
	},

	setVisible: function(visible){
	},

	setData: function(data){
		this.clearTabs();
		for(var domain in data)
		{
			if(typeof data[domain]=="function")
				continue;
			var contentPane=new __dijit.layout.ContentPane({title:domain});
			this.tabPane.addChild(contentPane);
			this.tabs.push(contentPane);
			var domainTable=document.createElement("table");
			contentPane.domNode.appendChild(domainTable);
			for(var p=0;p<data[domain].length;p++)
			{
				var pref=data[domain][p].name;
				var descrip=data[domain][p].descrip;
				var prefRow=domainTable.insertRow(-1);
				var prefDescripCell=prefRow.insertCell(0);
				prefDescripCell.style.width="16px";
				var prefDescDiv=document.createElement("div");
				prefDescripCell.appendChild(prefDescDiv);
				prefDescDiv.style.width="16px";
				prefDescDiv.style.height="16px";
				var prefNameCell=prefRow.insertCell(1);
				var prefValCell=prefRow.insertCell(2);
				prefNameCell.style.fontWeight="bold";
				if(descrip)
				{
					prefDescDiv.style.backgroundImage="url(__webContentRoot/rsrc/icons/prisms/help.png)";
					prefDescDiv.title=PrismsUtils.fixUnicodeString(descrip);
				}
				prefNameCell.innerHTML=PrismsUtils.fixUnicodeString(pref);
				var editor=this.createEditor(domain, pref, data[domain][p]);
				if(editor.domNode)
					prefValCell.appendChild(editor.domNode);
				else if(editor)
					prefValCell.appendChild(editor);
			}
		}
		this.tabPane.resize();
	},

	setDatum: function(domain, prefName, type, value){
		var domainMap=this._editors[domain];
		var editor;
		if(domainMap)
			editor=domainMap[prefName];
		if(!editor)
			throw new Error("No such preference: "+domain+"::"+prefName);
		this.dataLock=true;
		try{
			if(type=="ENUM")
			{
				for(var i=0;i<editor.options.length;i++)
					if(editor.options[i].text==value)
					{
						editor.selectedIndex=i;
						break;
					}
			}
			else if(type=="COLOR")
				editor.setValue(value, false);
			else if(editor.getValue()!=value)
				editor.setValue(value);
		} finally{
			this.dataLock=false;
		}
	},

	_editors: {},

	createEditor: function(domain, prefName, value){
		var self=this;
		var type=value.type;
		value=value.value;
		var editor;
		if(type=="BOOLEAN")
		{
			editor=new __dijit.form.CheckBox({});
			editor.setAttribute("checked", value);
			this.connectors.push(__dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.checked);
			}));
		}
		else if(type=="INT" || type=="NONEG_INT")
		{
			var constraints={pattern:"#"};
			if(type=="NONEG_INT")
				constraints.min=0;
			editor=new __dijit.form.NumberSpinner({constraints: constraints});
			editor.domNode.style.width="80px";
			editor.setValue(value);
			this.connectors.push(__dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.getValue());
			}));
		}
		else if(type=="FLOAT" || type=="NONEG_FLOAT")
		{
			var constraints={pattern:"0.####"};
			if(type=="NONEG_FLOAT")
				constraints.min=0;
			editor=new __dijit.form.NumberTextBox({constraints: constraints});
			editor.domNode.style.width="70px";
			editor.setValue(value);
			this.connectors.push(__dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.getValue());
			}));
		}
		else if(type=="PROPORTION")
		{
			editor=new prisms.widget.ProportionEditor();
			editor.setValue(value);
			this.connectors.push(__dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.getValue());
			}));
		}
		else if(type=="STRING")
		{
			editor=new __dijit.form.TextBox({});
			editor.setValue(value);
			this.connectors.push(__dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.getValue());
			}));
		}
		else if(type=="COLOR")
		{
			editor=new prisms.widget.ColorPicker({displayAlpha: false});
			editor.setValue(value);
			editor.setBefore(value);
			this.connectors.push(__dojo.connect(editor, "onChange", function(color){
				if(typeof color=="object")
					color=self.hexify(color);
				self.datumChanged(domain, prefName, color);
			}));
//			editor=new __dijit.ColorPalette();
//			this.connectors.push(__dojo.connect(editor, "onChange", function(color){
//				self.datumChanged(domain, prefName, color)
//			}));
		}
		else if(type=="ENUM")
		{
			editor=document.createElement("select");
			for(var o=0;o<value.options.length;o++)
			{
				var option=document.createElement("option");
				option.text=value.options[o];
				if(value.options[o]==value.value)
					option.selected=true;
				if(__dojo.isIE > 6)
					editor.add(option);
				else
					editor.add(option, null);
			}
			this.connectors.push(__dojo.connect(editor, "onchange", function(){
				self.datumChanged(domain, prefName, editor.options[editor.selectedIndex].text);
			}));
		}
		else
			return document.createTextNode(this.prisms.toJson(value));
		if(editor!=null)
		{
			if(!this._editors[domain])
				this._editors[domain]={};
			this._editors[domain][prefName]=editor;
		}
		return editor;
	},

	hexify: function(rgbColor){
		var ret="#";
		var el=rgbColor.r.toString(16);
		if(el.length<2)
			el="0"+el;
		ret+=el;
		el=rgbColor.g.toString(16);
		if(el.length<2)
			el="0"+el;
		ret+=el;
		el=rgbColor.b.toString(16);
		if(el.length<2)
			el="0"+el;
		ret+=el;
		return ret;
	},

	/**
	 * Called when data is changed from the client
	 */
	datumChanged: function(domain, prefName, value){
		if(this.dataLock)
			return;
		this.prisms.callApp(this.pluginName, "dataChanged", {domain: domain, prefName: prefName, value: value});
	},

	clearTabs: function(){
		for(var domain in this._editors)
		{
			if(typeof this._editors[domain]=="function")
			for(var pref in this._editors[domain])
				if(typeof this._editors[domain][pref].destroy=="function")
					this._editors[domain][pref].destroy();
		}
		for(var c=0;c<this.connectors.length;c++)
			__dojo.disconnect(this.connectors[c]);
		this._editors={};
		for(var c=0;c<this.tabs.length;c++)
			this.tabPane.removeChild(this.tabs[c]);
		this.tabs=[];
	}
});
