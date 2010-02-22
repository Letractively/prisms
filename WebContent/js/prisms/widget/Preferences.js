
dojo.require("dijit.layout.TabContainer");
dojo.require("dijit.form.CheckBox");
dojo.require("dijit.form.TextBox");
dojo.require("dijit.form.NumberTextBox");
dojo.require("dijit.form.NumberSpinner");
dojo.require("dijit.form.Slider");
dojo.require("prisms.widget.PrismsDialog");
dojo.require("prisms.widget.ColorPicker");

dojo.provide("prisms.widget.Preferences");
dojo.declare("prisms.widget.Preferences", prisms.widget.PrismsDialog, {

	prisms: null,

	pluginName: "No pluginName specified",

	connectors: [],

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.titleNode.style.fontWeight="bold";
		this.titleNode.style.fontSize="large";
		var self=this;
		dojo.connect(this, "hide", function(){
			self.clearTabs();
		});
		var table=document.createElement("table");
		this.containerNode.appendChild(table);
		var tabPane=new dijit.layout.TabContainer();
		table.insertRow(-1).insertCell(-1).appendChild(tabPane.domNode);
		this.tabPane=tabPane;
		this.tabPane.startup();
		this.tabPane.domNode.style.width="640px";
		this.tabPane.domNode.style.height="400px";
		this.tabs=[];
		var ok=new dijit.form.Button({"label": "OK"});
		var cell=table.insertRow(-1).insertCell(-1);
		cell.align="center";
		cell.appendChild(ok.domNode);
		dojo.connect(ok, "onClick", this, this.hide);
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		this.titleNode.innerHTML=this.prisms.application+" Preferences";
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
			this.setDatum(event.domain, event.prefName, event.value);
		}
		else
			throw new Error("unrecognized "+this.pluginName+" event: "+this.prisms.toJson(event));
	},

	setData: function(data){
		this.clearTabs();
		for(var domain in data)
		{
			if(typeof data[domain]=="function")
				continue;
			var contentPane=new dijit.layout.ContentPane({title:domain});
			this.tabPane.addChild(contentPane);
			this.tabs.push(contentPane);
			var domainTable=document.createElement("table");
			contentPane.domNode.appendChild(domainTable);
			for(var p=0;p<data[domain].length;p++)
			{
				var pref=data[domain][p].name;
				var prefRow=domainTable.insertRow(-1);
				var prefNameCell=prefRow.insertCell(0);
				var prefValCell=prefRow.insertCell(1);
				prefNameCell.style.fontWeight="bold";
				prefNameCell.innerHTML=pref;
				var editor=this.createEditor(domain, pref, data[domain][p]);
				if(editor.domNode)
					prefValCell.appendChild(editor.domNode);
				else if(editor)
					prefValCell.appendChild(editor);
			}
		}
		this.tabPane.resize();
	},

	setDatum: function(domain, prefName, value){
		var domainMap=this._editors[domain];
		var editor;
		if(domainMap)
			editor=domainMap[prefName];
		if(!editor)
			throw new Error("No such preference: "+domain+"::"+prefName);
		this.dataLock=true;
		try{
			if(value.type=="ENUM")
			{
				for(var i=0;i<editor.options.length;i++)
					if(editor.options[i].text==value.value)
					{
						editor.selectedIndex=i;
						break;
					}
			}
			else if(value.type=="COLOR")
			{}
			else
				editor.setValue(value.value);
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
			editor=new dijit.form.CheckBox({});
			editor.setAttribute("checked", value);
			this.connectors.push(dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.checked);
			}));
		}
		else if(type=="INT" || type=="NONEG_INT")
		{
			var constraints={pattern:"#"};
			if(type=="NONEG_INT")
				constraints.min=0;
			editor=new dijit.form.NumberSpinner({constraints: constraints});
			editor.domNode.style.width="80px";
			editor.setValue(value);
			this.connectors.push(dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.getValue());
			}));
		}
		else if(type=="FLOAT" || type=="NONEG_FLOAT")
		{
			var constraints={pattern:"0.####"};
			if(type=="NONEG_FLOAT")
				constraints.min=0;
			if(type=="INT" || type=="NONEG_INT")

			editor=new dijit.form.NumberTextBox({constraints: constraints});
			editor.domNode.style.width="70px";
			editor.setValue(value);
			this.connectors.push(dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.getValue());
			}));
		}
		else if(type=="PROPORTION")
		{
			editor=new dijit.form.HorizontalSlider({minimum:0, maximum:1});
			editor.domNode.style.width="140px";
			editor.setValue(value);
			this.connectors.push(dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.getValue());
			}));
		}
		else if(type=="STRING")
		{
			editor=new dijit.form.TextBox({});
			editor.setValue(value);
			this.connectors.push(dojo.connect(editor, "onChange", function(){
				self.datumChanged(domain, prefName, editor.getValue());
			}));
		}
		else if(type=="COLOR")
		{
			editor=new prisms.widget.ColorPicker({displayAlpha: false});
			editor.setValue(value);
			editor.setBefore(value);
			this.connectors.push(dojo.connect(editor, "onChange", function(color){
				if(typeof color=="object")
					color=self.hexify(color);
				self.datumChanged(domain, prefName, color);
			}));
//			editor=new dijit.ColorPalette();
//			this.connectors.push(dojo.connect(editor, "onChange", function(color){
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
				if(dojo.isIE > 6)
					editor.add(option, 0);
				else
					editor.add(option, null);
			}
			this.connectors.push(dojo.connect(editor, "onchange", function(){
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
			dojo.disconnect(this.connectors[c]);
		this._editors={};
		for(var c=0;c<this.tabs.length;c++)
			this.tabPane.removeChild(this.tabs[c]);
		this.tabs=[];
	}
});
