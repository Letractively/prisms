
dojo.require("prisms.widget.PrismsDialog");

dojo.provide("prisms.widget.LayerConfigurator");
dojo.declare("prisms.widget.LayerConfigurator", prisms.widget.PrismsDialog, {

	pluginName: "No pluginName specified",

	prisms: null,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this._listeners=[];
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
		this.layerListTable=table;
		this.containerNode.appendChild(table);
		table.style.width="500px";
		
		table=document.createElement("table");
		this.wmsConfigTable=table;
		this.containerNode.appendChild(table);
		table.style.width="500px";
		var tr=table.insertRow(-1);
		var td=tr.insertCell(0);
		td.colSpan=2;
		this.addLink(td, "Back To Layer List", function(){
			this.prisms.callApp(this.pluginName, "configureLayers");
		});
		this._listeners.pop();
		tr=table.insertRow(-1);
		td=tr.insertCell(0);
		td.style.textAlign="right";
		td.style.fontWeight="bold";
		td.innerHTML="Name:";
		td=tr.insertCell(1);
		this.wmsLayerName=document.createElement("input");
		this.wmsLayerName.type="text";
		td.appendChild(this.wmsLayerName);
		dojo.connect(this.wmsLayerName, "onchange", this, function(){
			if(this.dataLock)
				return;
			this.prisms.callApp(this.pluginName, "setLayerName", {
				layer: this.wmsLayer.name, name: this.wmsLayerName.value});
		});
		tr=table.insertRow(-1);
		var td=tr.insertCell(0);
		td.style.textAlign="right";
		td.style.fontWeight="bold";
		td.innerHTML="URL:";
		td=tr.insertCell(1);
		this.wmsLayerURL=document.createElement("input");
		this.wmsLayerURL.type="text";
		dojo.connect(this.wmsLayerURL, "onchange", this, function(){
			if(this.dataLock)
				return;
			this.prisms.callApp(this.pluginName, "setLayerURL", {
				layer: this.wmsLayer.name, url: this.wmsLayerURL.value});
		});
		td.appendChild(this.wmsLayerURL);
	},

	processEvent: function(event){
		if(event.method=="setLayers")
			this.setLayers(event.layers);
		else if(event.method=="configureWMS")
			this.configureWMS(event.layer);
		else
			throw new Error("Unrecognized "+this.pluginName+" method: "+event.method);
	},

	setLayers: function(layers){
		this.containerNode.style.height="";
		this.setTitle("Configure Map Layers");
		this.wmsConfigTable.style.display="none";
		this.layerListTable.style.display="table";
		this.clearListeners();
		var table=this.layerListTable;
		while(table.rows.length>0)
			table.deleteRow(0);
		for(var L=0;L<layers.length;L++)
			this.setLayerRow(table.insertRow(0), layers[L], L==0, L==layers.length-1);
		this.addLink(table.insertRow(-1).insertCell(-1), "Add WMS Layer", function(){
			this.prisms.callApp(this.pluginName, "addWMSLayer");
		});
		this.show();
		if(this.containerNode.clientHeight>this._underlay.domNode.clientHeight*2/3)
		{
			this.containerNode.style.overflow="scroll";
			this.containerNode.style.height=Math.round(this._underlay.domNode.clientHeight*2/3)+"px";
			this.domNode.style.top="25px";
		}
	},

	configureWMS: function(layer){
		this.containerNode.style.height="";
		this.wmsLayer=layer;
		this.dataLock=true;
		try{
			this.setTitle("Configure WMS Layer: "+layer.name);
			this.wmsConfigTable.style.display="table";
			this.layerListTable.style.display="none";
			this.clearListeners();
			var table=this.wmsConfigTable;
			while(table.rows.length>3)
				table.deleteRow(3);
			this.wmsLayerName.value=layer.name;
			this.wmsLayerURL.value=layer.url;
			var tr, td;
			if(layer.layers.length>0)
			{
				tr=table.insertRow(-1);
				td=tr.insertCell(0);
				td.colSpan=2;
				td.innerHTML="Available Layers";
				td.style.fontWeight="bold";
				td.style.fontSize="large";
			}
			for(var L=0;L<layer.layers.length;L++)
				this.setSubLayerRow(table.insertRow(-1), layer, layer.layers[L]);
		} finally{
			this.dataLock=false;
		}
		this.show();
		if(this.containerNode.clientHeight>this._underlay.domNode.clientHeight*2/3)
		{
			this.containerNode.style.overflow="scroll";
			this.containerNode.style.height=Math.round(this._underlay.domNode.clientHeight*2/3)+"px";
			this.domNode.style.top="25px";
		}
	},

	setLayerRow: function(tableRow, layer, isBottom, isTop){
		while(tableRow.cells.length<6)
			tableRow.insertCell(-1);
		tableRow.cells[0].innerHTML=layer.name;
		if(layer.enabled)
		{
			this.addLink(tableRow.cells[1], "Disable", function(){
				this.prisms.callApp(this.pluginName, "disableLayer", {layer: layer.name});
			});
			tableRow.cells[0].style.fontWeight="bold";
		}
		else
			this.addLink(tableRow.cells[1], "Enable", function(){
				this.prisms.callApp(this.pluginName, "enableLayer", {layer: layer.name});
			});
			
		if(layer.configurable)
		{
			this.addLink(tableRow.cells[2], "Configure", function(){
				this.prisms.callApp(this.pluginName, "configureLayer", {layer: layer.name});
			});
			this.addLink(tableRow.cells[3], "Delete", function(){
				this.prisms.callApp(this.pluginName, "deleteLayer", {layer: layer.name});
			});
		}
		var img;
		if(!isBottom)
		{
			img=document.createElement("img");
			img.src="../rsrc/icons/buttons/movedown.gif";
			tableRow.cells[4].appendChild(img);
			this._listeners.push(dojo.connect(tableRow.cells[4].childNodes[0], "onclick", this, function(){
				this.prisms.callApp(this.pluginName, "moveLayerDown", {layer: layer.name});
			}));
		}
		if(!isTop)
		{
			img=document.createElement("img");
			img.src="../rsrc/icons/buttons/moveup.gif";
			tableRow.cells[5].appendChild(img);
			this._listeners.push(dojo.connect(tableRow.cells[5].childNodes[0], "onclick", this, function(){
				this.prisms.callApp(this.pluginName, "moveLayerUp", {layer: layer.name});
			}));
		}
	},

	setSubLayerRow: function(tr, layer, subLayer) {
		var td=tr.insertCell(0);
		var labelCell=td;
		labelCell.innerHTML=subLayer.title;
		td=tr.insertCell(1);
		if(subLayer.enabled)
		{
			this.addLink(td, "Disable", function(){
				this.prisms.callApp(this.pluginName, "disableSubLayer", {
					layer: layer.name, subLayer: subLayer.name
				})
			});
			labelCell.style.fontWeight="bold";
		}
		else
			this.addLink(td, "Enable", function(){
				this.prisms.callApp(this.pluginName, "enableSubLayer", {
					layer: layer.name, subLayer: subLayer.name
				})
			});
	},

	clearListeners: function(){
		while(this._listeners.length>0)
			dojo.disconnect(this._listeners.pop());
	},

	addLink: function(tableCell, label, callback){
		var div=document.createElement("div");
		div.innerHTML="<a href=\"\" onclick=\"return false\" style=\"color:blue\">"+label+"</a>";
		var a=div.childNodes[0];
		this._listeners.push(dojo.connect(a, "onclick", this, callback));
		tableCell.appendChild(a);
		return a;
	},

	hide: function(){
		this.inherited(arguments);
		this.clearListeners();
	},

	setTitle: function(title){
		this.titleNode.innerHTML=title;
	}
});
