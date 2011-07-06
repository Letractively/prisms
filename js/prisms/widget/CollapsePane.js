
__dojo.provide("prisms.widget.CollapsePane");
__dojo.declare("prisms.widget.CollapsePane", [__dijit._Widget, __dijit._Container], {

	expandIcon: "__webContentRoot/rsrc/icons/prisms/DownArrow.png",

	collapseIcon: "__webContentRoot/rsrc/icons/prisms/UpArrow.png",

	expandIconHover: "__webContentRoot/rsrc/icons/prisms/DownArrowLight.png",

	collapseIconHover: "__webContentRoot/rsrc/icons/prisms/UpArrowLight.png",

	collapsed: false,

	visible: true,

	title: "",

	governRowCount: 1,

	postCreate: function(){
		this.inherited(arguments);
		this.startup();
		this._collapsed=false;
		this.setVisible(this.visible);
		this.setCollapsed(this.collapsed);
	},

	startup: function(){
		if(this.domNode.parentNode==null) //Created programmatically--don't error out
		{
			this.domNode=document.createElement("tr");
			return;
		}
		if(this.domNode.tagName!="tr" && this.domNode.tagName!="TR")
			throw new Error("A CollapsePane must be a row in a table");
		var table=this.domNode.parentNode.parentNode;
		if(!this.titleRow)
		{
			var idx;
			for(idx=0;idx<table.rows.length && table.rows[idx]!=this.domNode;idx++);
			this.titleRow=table.insertRow(idx);
			
			//Setup title row
			var td=this.titleRow.insertCell(-1);
			td.colSpan=this._getMaxColumns(table);
			var titleTable=document.createElement("table");
			td.appendChild(titleTable);
			var tr=titleTable.insertRow(-1);
			this.expandButton=document.createElement("img");
			this.expandButton.src=this.collapseIcon;
			tr.insertCell(-1).appendChild(this.expandButton);
			__dojo.connect(this.expandButton, "onclick", this, this._expandClicked);
			__dojo.connect(this.expandButton, "onmousemove", this, this._hover);
			__dojo.connect(this.expandButton, "onmouseout", this, this._unhover);
			this.titleNode=document.createElement("div");
			tr.insertCell(-1).appendChild(this.titleNode);
			this.titleNode.style.fontWeight="bold";
			this.titleNode.style.fontSize="large";
			__dojo.connect(this.titleNode, "onclick", this, this._expandClicked);
			this.titleNode.innerHTML=PrismsUtils.fixUnicodeString(this.title);
		}

		if(!this._wipeIn || !this._wipeOut)
		{
			this.containerRows=[];
			var wipeIns=[];
			var wipeOuts=[]
			if(this.governRowCount)
			{
				for(idx=0;idx<table.rows.length && table.rows[idx]!=this.domNode;idx++);
				for(var idx2=0;idx2<this.governRowCount;idx2++)
				{
					this.containerRows.push(table.rows[idx+idx2]);
					wipeIns.push(__dojo.fx.wipeIn({node: table.rows[idx+idx2], duration: 150}));
					wipeOuts.push(__dojo.fx.wipeOut({node: table.rows[idx+idx2], duration: 150}));
				}
			}
			this._wipeIn=__dojo.fx.combine(wipeIns);
			this._wipeInConnect=__dojo.connect(this._wipeIn, "onEnd", this, function(){
				if(this.isHover)
					this.expandButton.src=this.collapseIconHover;
				else
					this.expandButton.src=this.collapseIcon;
			});
			this._wipeOut=__dojo.fx.combine(wipeOuts);
			this._wipeOutConnect=__dojo.connect(this._wipeOut, "onEnd", this, function(){
				if(this.isHover)
					this.expandButton.src=this.expandIconHover;
				else
					this.expandButton.src=this.expandIcon;
			});
		}
	},

	_getMaxColumns: function(table){
		var max=1;
		for(var r=0;r<table.rows.length;r++)
		{
			var max_r=0;
			for(var c=0;c<table.rows[r].cells.length;c++)
			{
				if(table.rows[r].cells[c].colSpan)
					max_r+=table.rows[r].cells[c].colSpan;
				else
					max_r++;
			}
			if(max_r>max)
				max=max_r;
		}
		return max;
	},

	setTitle: function(title){
		this.titleNode.innerHTML=PrismsUtils.fixUnicodeString(title);
	},

	setCollapsed: function(collapsed){
		if(!this.visible)
		{
			this._preCollapse=collapsed;
			return;
		}
		this.collapsed=collapsed;
		if(collapsed==this._collapsed)
			return;
		this._collapsed=collapsed;
		if(this.collapsed)
		{
			if(this._wipeIn.status() == "playing")
				this._wipeIn.stop();
			this._wipeOut.play();
		}
		else
		{
			if(this._wipeOut.status() == "playing")
				this._wipeOut.stop();
			this._wipeIn.play();
		}
	},

	setVisible: function(visible){
		if(visible==this.visible)
			return;
		if(!visible)
		{
			this._preCollapse=this.collapsed;
			this.setCollapsed(true);
		}
		PrismsUtils.setTableRowVisible(this.titleRow, visible);
		this.visible=visible;
		if(visible)
		{
			this.setCollapsed(this._preCollapse);
			delete this._preCollapse;
		}
	},

	/**
	 * @param count The number of rows that this pane should collapse and expand
	 */
	setGovernRowCount: function(count){
		if(this.governRowCount==count)
			return;
		if(this._wipeInConnect)
			__dojo.disconnect(this._wipeInConnect);
		if(this._wipeOutConnect)
			__dojo.disconnect(this._wipeOutConnect);
		this._wipeIn.destroy();
		delete this._wipeIn;
		this._wipeOut.destroy();
		delete this._wipeOut;
		this.governRowCount=count;
		this.startup();
	},

	_expandClicked: function(){
		this.setCollapsed(!this.collapsed);
	},

	_hover: function(){
		this.isHover=true;
		if(this.collapsed)
			this.expandButton.src=this.expandIconHover;
		else
			this.expandButton.src=this.collapseIconHover;
	},

	_unhover: function(){
		this.isHover=false;
		if(this.collapsed)
			this.expandButton.src=this.expandIcon;
		else
			this.expandButton.src=this.collapseIcon;
	}
});
