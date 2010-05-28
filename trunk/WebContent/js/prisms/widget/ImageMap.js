
dojo.require("dijit._Widget");
dojo.require("prisms.widget.LayerConfigurator");
dojo.provide("prisms.widget.ImageMap");

function PrismsMapPlugin(prisms, pluginName){
	this.prisms=prisms;

	this.pluginName=pluginName;
	
	this.loaded=false;

	this.needsRefresh=false;

	this.mapWidgets=[];

	this.dragMinDistance=2;

	this.processEvent=function(event){
		if(event.method=="resetImage")
			this.needsRefresh=true;
		else if(event.method=="setPointActions")
		{
			for(var i=0;i<this.mapWidgets.length;i++)
				this.mapWidgets[i].pointActions=event.pointActions;
		}
		else
			throw new Error("Unrecognized "+this.pluginName+" event: "+this.prisms.toJson(event));
	};

	this.postProcessEvents=function(){
		if(this.needsRefresh)
		{
			this.needsRefresh=false;
			this.resetImages();
		}
	};

	this.addMapWidget=function(widget){
		this.mapWidgets.push(widget);
		if(this.loaded)
			widget.refreshImage(this.imageNoCache-1);
	};

	this.imageNoCache=0;

	this.resetImages=function(){
		this.loaded=true;
		for(var i=0;i<this.mapWidgets.length;i++)
			this.mapWidgets[i].refreshImage(this.imageNoCache);
		this.imageNoCache++;
	};
}

function MapDragImageUtil(map){

	this.imageDivisions=4;

	this.images= [];

	this.imageVisibles= [];

	this.setup=function(map){
		this.map=map;
		var dim=this.imageDivisions*2+1;
		for(var i=0;i<dim;i++)
		{
			this.images[i]=[];
			this.imageVisibles[i]=[];
			for(var j=0;j<dim;j++)
			{
				if(i==this.imageDivisions && j==this.imageDivisions)
				{
					this.images[i][j]=map.imageTag;
					continue;
				}
				this.images[i][j]=document.createElement("img");
				this.images[i][j].style.position="absolute";
				map.domNode.appendChild(this.images[i][j]);
			}
		}
		this.imageVisibles[this.imageDivisions][this.imageDivisions]=true;
		this.setVisible(false);
	};

	this.setVisible= function(visible){
		var display;
		if(visible)
			display="block";
		else
			display="none";
		var dim=this.imageDivisions*2+1;
		for(var i=0;i<dim;i++)
			for(var j=0;j<dim;j++)
			{
				if(i==this.imageDivisions && j==this.imageDivisions)
					continue;
				if(!visible)
				{
					this.imageVisibles[i][j]=false;
					this.images[i][j].src=null;
				}
				this.images[i][j].style.display=display;
			}
	};

	this.drag=function(x, y, w, h){
		this.setVisible(true);
		var imDiv=this.imageDivisions;
		var xDim=Math.round(w/imDiv);
		var yDim=Math.round(h/imDiv);
		
		//Position the main image
		dojo.marginBox(this.images[imDiv][imDiv], {t: this.map.imageTop+y,
			l: x, w: w, h: h});

		for(var r=-imDiv;r<=imDiv;r++)
		{
			var vis_r=true;
			var yPos=y+r*yDim;
			if(r>0)
				yPos+=h-yDim;
			if(yPos>=h) // Image is too low--invisible
				vis_r=false;
			var h_r;
			if(vis_r)
			{
				if(r==0)
					h_r=h;
				else
					h_r=yDim;
				if(yPos+h_r<=0) // Image is too high--invisible
					vis_r=false;
			}

			for(var c=-imDiv;c<=imDiv;c++)
			{
				if(r==0 && c==0)
					continue; //Main image is already positioned and displayed
				if(!vis_r)
				{
					this.images[r+imDiv][c+imDiv].style.display="none";
					continue;
				}

				var vis_c=true;
				var xPos=x+c*xDim;
				if(c>0)
					xPos+=w-xDim;
				if(xPos>=w) // Image is too far right--invisible
					vis_c=false;
				var w_c;
				if(vis_c)
				{
					if(c==0)
						w_c=w;
					else
					w_c=xDim;
					if(xPos+w_c<=0) // Image is too far left--invisible
						vis_c=false;
				}
				
				if(!vis_c)
				{
					this.images[r+imDiv][c+imDiv].style.display="none";
					continue;
				}
				if(!this.imageVisibles[r+imDiv][c+imDiv])
				{
					this.images[r+imDiv][c+imDiv].src=this.map.prisms.getDynamicImageSource(
						this.map.pluginName, "getMapImage", xPos-x, yPos-y, w, h, w_c, h_r)
						+"&nocache="+this.map.noCache;
					this.imageVisibles[r+imDiv][c+imDiv]=true;
				}
				this.images[r+imDiv][c+imDiv].style.display="block";
				var box={l: xPos, t: this.map.imageTop+yPos, w: w_c, h: h_r};
				dojo.marginBox(this.images[r+imDiv][c+imDiv], box);
			}
		}
	};

	this.setup(map);
}

dojo.require("dijit.layout._LayoutWidget");
dojo.require("dijit._Templated");

dojo.declare("prisms.widget._ImageMapMenuItem",
	dijit.MenuItem,
	{
		postCreate: function()
		{
			this.inherited("postCreate", arguments);
			if(!this.map)
				throw "An ImageMapMenuItem must be created with an ImageMap";
		},

		setLabel: function(label)
		{
			this.label=label;
			this.containerNode.innerHTML=label;
		},

		onClick: function()
		{
			this.inherited("onClick", arguments);
			this.map.fireAction(this.label);
		}
	}
);

dojo.declare("prisms.widget.ImageMap", [dijit.layout._LayoutWidget, dijit._Templated],
{
	templatePath: "/prisms/view/prisms/templates/imageMap.html",

	widgetsInTemplate: true,

	prisms: null,

	pluginName: "No pluginName specified",

	isAreaSelecting: false,

	isDragMoving: false,

	showToolBar: true,

	isDragSelectable: true,

	isDragMovable: false,

	rightClickable: true,
	
	configPluginName: "",

	/**
	 * The interval between sends of mouse drag information during a drag operation
	 */
	dragSendDelayTime: 250,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		if (dojo.isIE) 
			this.isDragMovable=false;
		if(this.rightClickable)
		{
			this.popupMenu=new dijit.Menu({});
			//In IE, events go to the image tag.  In firefox, they go to the blanketDiv
			this.popupMenu.bindDomNode(this.imageTag);
			this.popupMenu.bindDomNode(this.blanketDiv);
			dojo.connect(this.popupMenu, "_openMyself", this, this.addMenuItems);
		}
		this.dragImageUtil=new MapDragImageUtil(this);

		var normalSelectTip="Click to change to normal select mode, where clicking and dragging"
			+" changes values in the application";
		var areaSelectTip="Click to change to area select mode, where dragging a rectangle zooms"
			+" to it and clicking on a point recenters";
		var dragMoveTip="Click to change to drag mode, where clicking and dragging pans"
			+" the map around";
		this.normalSelectButton.title="Normal select mode.  "+areaSelectTip;
		this.dragMoveButton.title="Drag mode.  "+normalSelectTip;
		if(this.isDragMovable)
			this.areaSelectButton.title="Area select mode.  "+dragMoveTip;
		else
			this.areaSelectButton.title="Area select mode.  "+normalSelectTip;

		if(!this.prisms)
		{
			var prisms=PrismsUtils.getPrisms(this);
			if(prisms)
				this.setPrisms(prisms);
			else
				console.error("No prisms parent for plugin "+this.pluginName);
		}
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		if(typeof this.prisms.plugins[this.pluginName]=="undefined")
			this.prisms.loadPlugin(this.createMapPlugin(this.prisms, this.pluginName));
		this.prisms.plugins[this.pluginName].addMapWidget(this);
		if(this.configPluginName && this.configPluginName.length>0)
		{
			this.layerConfigurator.pluginName=this.configPluginName;
			this.layerConfigurator.setPrisms(this.prisms);
		}
	},

	createMapPlugin: function(prisms, pluginName){
		return new PrismsMapPlugin(this.prisms, this.pluginName);
	},

	refreshImage: function(noCache){
		this.noCache=noCache;
		if(this.imageWidth && this.imageHeight)
		{
			this.blockMouse=true;
			this.waitIcon.style.display="block";
			this.setCursor();
			this.imageTag.src=this.prisms.getDynamicImageSource(this.pluginName, "getMapImage",
				0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight)
				+"&nocache="+this.noCache;
		}
		dojo.marginBox(this.blanketDiv, {l:0, t: this.imageTop,
			w: this.imageWidth, h: this.imageHeight});
	},

	refreshImageFromResize: function(){
		if(this.resizeRefreshTimer)
		{
			clearTimeout(this.resizeRefreshTimer);
			delete this.resizeRefreshTimer;
		}
		var self=this;
		this.resizeRefreshTimer=setTimeout(function(){self.refreshImage(self.noCache);}, 500);
	},
	
	_mouseDown: function(event){
		event.preventDefault();
		event.stopPropagation();
		if(this.blockMouse)
			return;
		var coords=this._mapEventCoords(event);
		this._mouseDownPoint=coords;
		return false;
	},

	_mouseUp: function(event){
		if(this.blockMouse)
			return;
		if(!this._mouseDownPoint)
			return;
		var mouseDownPoint=this._mouseDownPoint;
		delete this._mouseDownPoint;
		var coords=this._mapEventCoords(event);
		if(this.isAreaSelecting)
		{
			this.prisms.callApp(this.pluginName, "areaSelected", {
				x1: mouseDownPoint.x, y1: mouseDownPoint.y, x2: coords.x, y2: coords.y,
				width: this.imageWidth, height: this.imageHeight});
		}
		else if(this.isDragMoving)
		{
			var xOff=coords.x-mouseDownPoint.x;
			var yOff=coords.y-mouseDownPoint.y;
			this.prisms.callApp(this.pluginName, "moveCenter", {xOffset: xOff, yOffset: yOff,
				width: this.imageWidth, height: this.imageHeight});
		}
		else if(this.isDragging)
			this.prisms.callApp(this.pluginName, "mapDragged", {
				x1: mouseDownPoint.x, y1: mouseDownPoint.y,
				x2: coords.x, y2: coords.y,
				width: this.imageWidth, height: this.imageHeight});
		else
			this.prisms.callApp(this.pluginName, "mapClicked", {x: coords.x, y: coords.y,
				width: this.imageWidth, height: this.imageHeight});
		delete this._dragTime;
		delete this._mapSize;
		delete this._lastReportedDrag;
		if(this._dragInterval)
		{
			window.clearInterval(this._dragInterval);
			delete this._dragInterval;
		}
		this.isDragging=false;
	},

	_mouseMoved: function(event){
		if(this.blockMouse)
			return;
		if(this.isAreaSelecting)
			return;
		if((!this.isDragSelectable && !this.isDragMoving) || !this._mouseDownPoint)
			return;
		var coords=this._mapEventCoords(event);
		if(this.isDragMoving)
		{
			this.dragImageUtil.drag(coords.x-this._mouseDownPoint.x, coords.y-this._mouseDownPoint.y,
				this.imageWidth, this.imageHeight);
			return;
		}
		this._mouseCurrentPoint=coords;
		this._mapSize={w: this.imageWidth, h: this.imageHeight};
		if(!this.isDragging)
		{
			var p1=this._mouseDownPoint;
			var p2=this._mouseCurrentPoint;
			var dist=(p2.x-p1.x)*(p2.x-p1.x)+(p2.y-p1.y)*(p2.y-p1.y);
			if(dist<this.dragMinDistance)
				return;
			this.isDragging=true;
			var self=this;
			this.prisms.callApp(this.pluginName, "mapDragStarted", {x: this._mouseDownPoint.x,
				y: this._mouseDownPoint.y, width: this.imageWidth, height: this.imageHeight});
			this._dragInterval=window.setInterval(function(){
				var doSend=false;
				if(!self._lastReportedDrag)
					doSend=true;
				else if(self._lastReportedDrag.x!=self._mouseCurrentPoint.x
					|| self._lastReportedDrag.y!=self._mouseCurrentPoint.y)
					doSend=true;
				if(doSend)
				{
					self.prisms.callApp(self.pluginName, "mapDragging",
						{startX: self._mouseDownPoint.x, startY: self._mouseDownPoint.y,
						dragX: self._mouseCurrentPoint.x, dragY: self._mouseCurrentPoint.y,
						width: self.imageWidth, height: self.imageHeight});
					self._lastReportedDrag=self._mouseCurrentPoint;
				}
			}, this.dragSendDelayTime);
		}
	},

	_mouseOut: function(event){
		if(this._mouseDownPoint)
			this._mouseUp(event);
	},

	_imageLoaded: function(){
		if(!this.isDragMoving || !this._mouseDownPoint)
		{
			dojo.marginBox(this.imageTag, {l: 0, t: this.imageTop, w: this.imageWidth, h: this.imageHeight});
			this.dragImageUtil.setVisible(false);
		}
		this.blockMouse=false;
		this.waitIcon.style.display="none";
		this.setCursor();
	},

	_dragMove: function(coords){
	},

	_mapEventCoords: function(event){
		var x, y;
		if (event.offsetX && event.offsetY) {
			x=event.offsetX;
			y=event.offsetY;
		} else if(event.layerX && event.layerY){
			x=event.layerX-1;
			y=event.layerY-1;
		} else {
			x=event.pageX-event.target.offsetLeft;
			y=event.pageY-event.target.offsetTop;
		}
		return {x: x, y: y};
	},

	_panLeftClicked: function(){
		this.prisms.callApp(this.pluginName, "panLeft");
	},

	_panRightClicked: function(){
		this.prisms.callApp(this.pluginName, "panRight");
	},

	_panUpClicked: function(){
		this.prisms.callApp(this.pluginName, "panUp");
	},

	_panDownClicked: function(){
		this.prisms.callApp(this.pluginName, "panDown");
	},

	_zoomInClicked: function(){
		this.prisms.callApp(this.pluginName, "zoomIn");
	},

	_zoomOutClicked: function(){
		this.prisms.callApp(this.pluginName, "zoomOut");
	},

	/**
	 * Called when the user clicks the pointer, dotted rectangle, or hand icon to change what the
	 * mouse does on the map.
	 */
	_mouseModeRotate: function(){
		if(this.isAreaSelecting)
		{
			if(this.isDragMovable)
			{ //Move to map dragging mode, where dragging the mouse moves the map around like google maps
				this.isAreaSelecting=false;
				this.isDragMoving=true;
				this.areaSelectButton.style.display="none";
				this.dragMoveButton.style.display="block";
				this.normalSelectButton.style.display="none";
				dojo.marginBox(this.dragMoveButton,
					{t: (this.imageTop-this.dragMoveButton.clientHeight)/2});
			}
			else
			{
				this.isAreaSelecting=false;
				this.isDragMoving=false;
				this.areaSelectButton.style.display="none";
				this.dragMoveButton.style.display="none";
				this.normalSelectButton.style.display="block";
				dojo.marginBox(this.normalSelectButton,
					{t: (this.imageTop-this.normalSelectButton.clientHeight)/2});
			}
		}
		else if(this.isDragMoving)
		{
			this.isAreaSelecting=false;
			this.isDragMoving=false;
			this.areaSelectButton.style.display="none";
			this.dragMoveButton.style.display="none";
			this.normalSelectButton.style.display="block";
			dojo.marginBox(this.normalSelectButton,
				{t: (this.imageTop-this.normalSelectButton.clientHeight)/2});
		}
		else
		{
			this.isAreaSelecting=true;
			this.isDragMoving=false;
			this.areaSelectButton.style.display="block";
			this.dragMoveButton.style.display="none";
			this.normalSelectButton.style.display="none";
			dojo.marginBox(this.areaSelectButton,
				{t: (this.imageTop-this.areaSelectButton.clientHeight)/2});
		}
		this.setCursor();
	},

	_configureLayers: function(){
		this.prisms.callApp(this.configPluginName, "configureLayers");
	},

	setCursor: function(){
		if(this.blockMouse)
			this.blanketDiv.style.cursor="wait";
		else if(this.isDragMoving)
			this.blanketDiv.style.cursor="move";
		else if(this.isAreaSelecting)
			this.blanketDiv.style.cursor="crosshair";
		else
			this.blanketDiv.style.cursor="auto";
	},

	addMenuItems: function(evt){
		var coords=this._mapEventCoords(evt);
		delete this.pointActions;
		this.prisms.callApp(this.pluginName, "getPointActions", {
			x: coords.x, y: coords.y, width: this.imageWidth, height: this.imageHeight}, {sync:true});
		if(this.pointActions)
		{
			this.actionCoords=coords;
			this.addMenuItemsFromActions(this.pointActions.label, this.pointActions.actions);
		}
	},

	addMenuItemsFromActions: function(message, actions)
	{
		if(!message)
			message=this.pluginName;
		var msgSplit=message.split("\n");
		message=msgSplit.join("<br />");
		message="<div style='text-align:center'>"+message+"</div>";
		if(!actions)
			actions=[];
		var items=this.popupMenu.getChildren();
		if(items.length==0)
		{
			items[0]=new prisms.widget._ImageMapMenuItem({
				menu: this.prismsMenu, map: this, label: message});
			this.popupMenu.addChild(items[0]);
			items[0].setAttribute("disabled", true);
			items[1]=new dijit.MenuSeparator({});
			this.popupMenu.addChild(items[1]);
		}
		else
			items[0].setLabel("<b>"+message+"</b>");
		var i;
		for(i=2;i<items.length && i-2<actions.length;i++)
			items[i].setLabel(actions[i-2]);
		for(;i<items.length;i++)
			this.prismsMenu.removeChild(items[i]);
		for(;i-2<actions.length;i++)
		{
			var item=new prisms.widget._ImageMapMenuItem({
				menu: this.prismsMenu, map: this, label: actions[i-2]});
			this.popupMenu.addChild(item);
		}
	},

	fireAction: function(action){
		this.prisms.callApp(this.pluginName, "performAction", {action: action,
			x: this.actionCoords.x, y: this.actionCoords.y,
			width: this.imageWidth, height: this.imageHeight});
	},

	layout: function(){
		var height=this.panLeftButton.clientHeight;
		if(this.showToolBar)
		{
			this.buttonPanel.style.display="block";
			if(this.panUpButton.clientHeight+this.panDownButton.clienHeight+2>height)
				height=this.panUpButton.clientHeight+this.panDownButton.clientHeight+2;
			if(this.panRightButton.clientHeight>height)
				height=this.panRightButton.clientHeight;
			if(this.zoomInButton.clientHeight>height)
				height=this.zoomInButton.clientHeight;
			if(this.zoomOutButton.clientHeight>height)
				height=this.zoomOutButton.clientHeight;
			if(this.areaSelectButton.clientHeight>height)
				height=this.areaSelectButton.clientHeight;
			if(this.dragMoveButton.clientHeight>height)
				height=this.dragMoveButton.clientHeight;
			if(this.normalSelectButton.clientHeight>height)
				height=this.normalSelectButton.clientHeight;
			dojo.marginBox(this.buttonPanel, {l: 0, t: 0, w: this.domNode.clientWidth, h: height});
	
			var left=0;
			dojo.marginBox(this.panLeftButton, {l: left, t: (height-this.panLeftButton.clientHeight)/2});
			left+=this.panLeftButton.clientWidth;
			dojo.marginBox(this.panUpButton, {l: left,
				t: (height-this.panUpButton.clientHeight-this.panDownButton.clientHeight-2)/2});
			dojo.marginBox(this.panDownButton, {l: left,
				t: this.panUpButton.offsetTop+this.panUpButton.clientHeight+2});
			if(this.panUpButton.clientWidth>=this.panDownButton.clientWidth)
				left+=this.panUpButton.clientWidth;
			else
				left+=this.panDownButton.clientWidth;
			dojo.marginBox(this.panRightButton, {l: left, t: (height-this.panRightButton.clientHeight)/2});
			left+=this.panRightButton.clientWidth;
			left+=10;
			dojo.marginBox(this.zoomInButton, {l: left, t: (height-this.zoomInButton.clientHeight)/2});
			left+=this.zoomInButton.clientWidth;
			left+=5;
			dojo.marginBox(this.zoomOutButton, {l: left, t: (height-this.zoomOutButton.clientHeight)/2});
			left+=this.zoomOutButton.clientWidth;
			left+=10;
			dojo.marginBox(this.areaSelectButton, {l: left,
				t: (height-this.areaSelectButton.clientHeight)/2});
			dojo.marginBox(this.dragMoveButton, {l: left,
				t: (height-this.dragMoveButton.clientHeight)/2});
			dojo.marginBox(this.normalSelectButton, {l: left,
				t: (height-this.normalSelectButton.clientHeight)/2});
			if(this.configPluginName)
			{
				var mouseButtonWidth=this.areaSelectButton.clientWidth;
				if(this.dragMoveButton.clientWidth>mouseButtonWidth)
					mouseButtonWidth=this.dragMoveButton.clientWidth;
				if(this.normalSelectButton.clientWidth>mouseButtonWidth)
					mouseButtonWidth=this.normalSelectButton.clientWidth;
				left+=mouseButtonWidth+10;
				dojo.marginBox(this.layersConfigureButton, {l: left,
					t: (height-this.layersConfigureButton.clientHeight)/2});
			}
			else
				this.layersConfigureButton.style.display="none";
		}
		else
			this.buttonPanel.style.display="none";

		this.imageWidth=this.domNode.clientWidth;
		this.imageHeight=this.domNode.clientHeight-height;
		this.imageTop=height;
		dojo.marginBox(this.waitIcon, {l: this.imageWidth/2-this.waitIcon.clientWidth/2,
			t: this.imageHeight/2-this.waitIcon.clientHeight/2});
		this.refreshImageFromResize(this.noCache);
	}
});
