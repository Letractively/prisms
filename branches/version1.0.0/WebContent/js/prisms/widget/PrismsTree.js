
(function(){

dojo.require("dijit.Tree");
dojo.require("dijit.Menu");
dojo.require("dijit.Tooltip");

dojo.declare("prisms.widget._PrismsTreeNode",
	dijit._TreeNode,
	{
		postCreate: function()
		{
			this.inherited("postCreate", arguments);
			this.selected=false;
		},

		_updateItemClasses: function(item)
		{
			this.item=item;
			this.inherited("_updateItemClasses", arguments);
			if(typeof item.icon == "string" && item.icon!="")
			{
				if(item.icon.charAt(0)=='{')
					item.icon=dojo.eval("["+item.icon+"]")[0];
			}
			if(typeof item.icon=="string")
				this.iconNode.style.backgroundImage="url(../rsrc/icons/"+item.icon+".png)";
			else if(typeof item.icon=="object" && item.icon!=null)
				this.iconNode.style.backgroundImage="url("
					+this.tree.model.prisms.getDynamicImageSource(item.icon.plugin, item.icon.method,
						0, 0, 16, 16, 16, 16)+")";
			if(typeof item.bgColor != "undefined")
				this.labelNode.style.backgroundColor=item.bgColor;
			if(typeof item.textColor != "undefined")
				this.labelNode.style.color=item.textColor;
		},

		setSelected: function(sel)
		{
			this.selected=sel;
			if(this.selected)
				this.labelNode.style.fontWeight="bold";
			else
				this.labelNode.style.fontWeight="normal";
		}
	}
);

dojo.declare("prisms.widget._PrismsTreeMenuItem",
	dijit.MenuItem,
	{
		postCreate: function()
		{
			this.inherited("postCreate", arguments);
			if(!this.tree)
				throw "A PrismsTreeMenuItem must be created with a PrismsTree";
		},

		setLabel: function(label)
		{
			this.label=label;
			this.containerNode.innerHTML=label;
		},

		onClick: function()
		{
			this.inherited("onClick", arguments);
			this.tree.fireAction(this.label);
		}
	}
);

dojo.provide("prisms.widget.PrismsTree");
dojo.declare("prisms.widget.PrismsTree", dijit.Tree,
{
	searchPrisms: true,

	postCreate: function()
	{
		this.inherited("postCreate", arguments);
		this.selection=[];
		this.menuItemCache=[];
		this.containerNodeTemplate=document.createElement("div");
		this.persist=false;
		this.prismsMenu=new dijit.Menu({});
		this.prismsMenu.bindDomNode(this.domNode);
		if(this.model.setVisible) {
			dojo.connect(this.model, "setVisible", this, this.setVisible);
		}
		dojo.connect(this.prismsMenu, "_openMyself", this, this.addMenuItems);
		this.loadButtonDiv=document.createElement("div");
		this.loadButtonDiv.style.display="none";
		this.loadButton=new dijit.form.Button({label: "Load"});
		this.domNode.appendChild(this.loadButtonDiv);
		this.loadButtonDiv.appendChild(this.loadButton.domNode);
		if(typeof this.model._hackAlertLazyLoading)
			dojo.connect(this.model, "_hackAlertLazyLoading", this, function(lazyLoadingHack){
				if(lazyLoadingHack)
					this.loadButtonDiv.style.display="block";
				else
					this.loadButtonDiv.style.display="none";
			});
		dojo.connect(this.loadButton, "onClick", this, function(){
			this.model.hackReload();
			this.domNode.removeChild(this.loadButtonDiv);
			this.loadButtonDiv.style.display="none";
		});

//		Dojo's tooltips are pretty annoying, so I'm disabling this
/*		var self=this;
		this.prismsTooltip=new dijit.Tooltip({
			connectId: [this.domNode.id],
			open: function(domNode){
				self.setTooltipText(domNode);
				this.inherited("open", arguments);
			}
		});*/

		if(!this.prisms && this.searchPrisms)
		{
			var prisms=PrismsUtils.getPrisms(this);
			if(prisms)
				this.setPrisms(prisms);
			else
				console.error("No prisms parent for plugin "+this.model.pluginName);
		}
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		if(this.model)
			this.model.setPrisms(prisms);
	},

    /**
     * This method is for my special override of dojo 1.1.1
     */
	createChildNode: function(params){
		return new prisms.widget._PrismsTreeNode(params);
	},

	/**
	 * This method is for dojo 1.3
	 */
	_createTreeNode: function(args){
		return new prisms.widget._PrismsTreeNode(args);
	},

	toggleSelection: function(treeNode)
	{
		if(typeof treeNode.setSelected != "function")
			return;
		var s;
		for(s=0;s<this.selection.length;s++)
			if(this.selection[s]==treeNode)
			{
				this.selection.splice(s, 1);
				treeNode.setSelected(false);
				break;
			}
		if(s==this.selection.length)
		{
			treeNode.setSelected(true);
			this.selection[this.selection.length]=treeNode;
		}
		this._notifySelection();
	},

	setSelection: function(treeNodes)
	{
		for(var n in treeNodes)
			if(typeof treeNodes[n].setSelected != "function")
				treeNodes.splice(n, 1);
		for(var s=0;s<this.selection.length;s++)
			this.selection[s].setSelected(false);
		this.selection=treeNodes;
		for(var s=0;s<this.selection.length;s++)
			this.selection[s].setSelected(true);
		this._notifySelection();
	},

	_notifySelection: function(){
		var items=[];
		for(var s=0;s<this.selection.length;s++)
			if(this.selection[s].item)
				items.push(this.selection[s].item);
		this.model.notifySelection(items);
	},

	isSelected: function(treeNode)
	{
		if(typeof treeNode.setSelected != "function")
			return false;
		var s;
		for(s=0;s<this.selection.length;s++)
			if(this.selection[s]==treeNode)
				return true;
		return false;
	},


	_onClick: function(e)
	{
		this._selectMultiple=e.ctrlKey;
		this._shiftKey=e.shiftKey;
		this.inherited("_onClick", arguments);
	},

	onClick: function(dataNode, treeNode)
	{
		if (this._shiftKey) {
			var startNode = this.lastClickedTreeNode;
			var endNode = treeNode;
			if (startNode.getParent()==endNode.getParent() && startNode.setSelected) {
				this.processShiftTreeNodes(startNode, endNode);
				this.inherited("onClick", arguments);
				return;
			}
		}
		this.lastClickedTreeNode = treeNode;

		if(this._selectMultiple)
			this.toggleSelection(treeNode);
		else
			this.setSelection([treeNode]);
		this.inherited("onClick", arguments);
	},
	
	processShiftTreeNodes: function(startNode, endNode) {
		var parent=startNode.getParent();
		var started=false;
		var selection=[];
		for (var c=0;c<parent.getChildren().length;c++) {
			var child=parent.getChildren()[c];
			if (started) {
				selection.push(child);
				if (child==startNode||child==endNode)
					break;
			}
			else if (child==startNode||child==endNode){
				started=true;
				selection.push(child);
			}
		}
		for (var s=0;s<this.selection.length;s++) 
			this.selection[s].setSelected(false);
		this.selection=selection;
		for (var s=0;s<this.selection.length;s++) 
			this.selection[s].setSelected(true);
		this._notifySelection();
	},

	addMenuItems: function(e)
	{
		dojo.stopEvent(e);
		var tn = dijit.getEnclosingWidget(e.target);	
		if(!tn || !tn.isTreeNode){
			return;
		}
		if(e.ctrlKey)
		{
			if(!this.isSelected(tn))
				this.toggleSelection(tn);
		}
		else
		{
			if(!this.isSelected(tn))
				this.setSelection([tn]);
		}
		if(this.selection.length==0)
			return;
		var actions=this.selection[0].item.actions;
		for(var s=1;s<this.selection.length;s++)
		{
			for(var a=0;a<actions.length;a++)
			{
				if(!actions[a].multiple)
				{
					actions.splice(a, 1);
					continue;
				}
				var i;
				for(i=0;i<this.selection[s].item.actions.length;i++)
					if(actions[a].text==this.selection[s].item.actions[i].text)
						break;
				if(i==this.selection[s].item.actions.length)
					actions.splice(a, 1);
			}
		}
		this.addMenuItemsFromActions(actions);
	},

	addMenuItemsFromActions: function(actions)
	{  
		var items=this.prismsMenu.getChildren();
		var i;
		for(i=0;i<items.length && i<actions.length;i++)
			items[i].setLabel(actions[i].text);
		for(;i<items.length;i++)
		{
			this.menuItemCache.push(items[i]);
			this.prismsMenu.removeChild(items[i]);
		}
		for(;i<actions.length;i++)
		{
			var item;
			if(this.menuItemCache.length>0)
			{
				item=this.menuItemCache.pop();
				item.setLabel(actions[i].text);
			}
			else
				item=new prisms.widget._PrismsTreeMenuItem({menu: this.prismsMenu, tree: this,
					label: actions[i].text});
			this.prismsMenu.addChild(item);
		}
	},

	setTooltipText: function(domNode)
	{
		var tn = dijit.getEnclosingWidget(domNode);
		if(!tn || !tn.isTreeNode){
			return;
		}
		if(tn.item && tn.item.description)
			this.prismsTooltip.label=tn.item.description;
		else
			this.prismsTooltip.label="";
	},

	fireAction: function(action)
	{
		var items=[];
		for(var s=0;s<this.selection.length;s++)
			items[s]=this.selection[s].item;
		if(typeof this.model.performAction == "function") 
			this.model.performAction(action, items);
		else if(typeof this.store.performAction == "function")
			this.store.performAction(action, items);
		else
			throw new Error("No performAction function in model or store for tree");
	},

	getIconClass: function(item)
	{
		if(!item)
			return "";
		if(typeof item.icon != "undefined" && item.icon)
			return "prismsTreeIcon";
		else
			return "";
	},

	setVisible: function(visible){
		if(visible)
			this.domNode.style.display="block";
		else
			this.domNode.style.display="none";
	}
});

})();
