
__dojo.require("dijit.form.TextBox");
__dojo.require("prisms.PrismsUtils");

__dojo.declare("prisms.widget._SearchableListNode", [__dijit._Widget, __dijit._Templated], {

	templatePath: "__webContentRoot/view/prisms/templates/searchableListNode.html",

	list: null,

	item: null,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.selected=false;
		this.isSearchNode=true;
		if(this.item)
			this.setItem(this.item);
	},

	setItem: function(item){
		this.item=item;
		if(typeof item.icon == "string" && item.icon!="")
		{
			if(item.icon.charAt(0)=='{')
				item.icon=__dojo.eval("["+item.icon+"]")[0];
		}
		if(typeof item.icon=="string")
			this.iconNode.src="__webContentRoot/rsrc/icons/"+item.icon+".png";
		else if(typeof item.icon=="object" && item.icon!=null)
			this.iconNode.src=this.list.model.prisms.getDynamicImageSource(item.icon.plugin,
				item.icon.method, 0, 0, 16, 16, 16, 16);
		if(typeof item.bgColor != "undefined")
			this.labelNode.style.backgroundColor=item.bgColor;
		if(typeof item.textColor != "undefined")
			this.labelNode.style.color=item.textColor;
		this.labelNode.innerHTML=this.item.text;
	},

	isSelected: function(){
		return this.selected;
	},

	setSelected: function(sel){
		this.selected=sel;
		if(this.selected)
			this.labelNode.style.fontWeight="bold";
		else
			this.labelNode.style.fontWeight="normal";
	}
});

__dojo.declare("prisms.widget._SearchListMenuItem", __dijit.MenuItem, {
	postCreate: function(){
		this.inherited("postCreate", arguments);
		if(!this.list)
			throw "A SearchListMenuItem must be created with a SearchableList";
	},

	setLabel: function(label){
		this.label=label;
		this.containerNode.innerHTML=label;
	},

	onClick: function(){
		this.inherited("onClick", arguments);
		this.list.fireAction(this.label);
	}
});

__dojo.provide("prisms.widget.SearchableList");
__dojo.declare("prisms.widget.SearchableList", [__dijit._Widget, __dijit._Templated], {

	searchPrisms: true,

	model: null,

	showSearch: true, 

	templatePath: "__webContentRoot/view/prisms/templates/searchableList.html",
	
	widgetsInTemplate: true,

	postCreate: function(){
		this.inherited("postCreate", arguments);
		if(this.model)
			this.setModel(this.model);
		this.items=[];
		this.selection=[];
		this.filter=null;
		this.menuItemCache=[];
		this.prismsMenu=new __dijit.Menu({});
		this.prismsMenu.bindDomNode(this.domNode);
		__dojo.connect(this.domNode, "onclick", this, this.onClick);
		if(this.model.setVisible) {
			__dojo.connect(this.model, "setVisible", this, this.setVisible);
		}
		__dojo.connect(this.prismsMenu, "_openMyself", this, this.addMenuItems);
		this.actionLead=document.createElement("div");
		this.actionLead.style.position="absolute";
		this.actionIcon=document.createElement("div");
		this.actionLead.appendChild(this.actionIcon);
		this.actionIcon.style.position="relative";
		this.actionIcon.style.backgroundImage="url(__webContentRoot/rsrc/icons/prisms/actionIcon.png)";
		this.actionIcon.style.top="-18px";
		this.actionIcon.style.left="-4px";
		this.actionIcon.style.width="10px";
		this.actionIcon.style.height="6px";
		__dojo.connect(this.actionIcon, "onmouseover", this, this.showActions);
		if(!this.showSearch)
			this.filterRow.style.display="none";

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
		{
			this.model.setPrisms(prisms);
			this.init();
		}
	},

	init: function(){
		var self=this;
		this.model.getRoot(function(item){
			self.titleNode.setItem(item);
			self.model.getChildren(self.titleNode.item, function(items){
				self.setItems(items);
			});
		});
		__dojo.connect(this.model, "onChildrenChange", this, function(parent, newChildren){
			if(parent!=this.titleNode.item)
				return;
			this.setItems(newChildren);
		});
		__dojo.connect(this.model, "onChange", this, function(item){
			if(item==this.titleNode.item)
				this.rootChanged(item);
			else
				this.changed(item);
		});
		__dojo.connect(this.model, "setFilter", this, function(filter){
			this.filterText.setValue(filter);
		});
		__dojo.connect(this.model, "setFilteredItems", this, function(ids){
			this.setFilteredItems(ids);
		});
	},

	setModel: function(model){
		this.model=model;
		if(model && this.prisms)
		{
			model.setPrisms(this.prisms);
			this.init();
		}
	},

	setItems: function(items){
		var self=this;
		this.items=PrismsUtils.adjust(this.items, items, {
			identity: function(node, item){
				return self.model.getIdentity(node.item)==self.model.getIdentity(item);
			},

			added: function(item, idx2, retIdx){
				var ret=self.createNode(item);
				var tr;
				if(self.contentTable.rows.length==0)
				{
					tr=self.contentTable.insertRow(0);
					tr.parentNode.appendChild(ret.domNode);
					self.contentTable.deleteRow(0);
				}
				else
				{
					if(self.contentTable.rows.length==retIdx)
					{
						tr=self.contentTable.rows[retIdx-1];
						tr.parentNode.appendChild(ret.domNode);
					}
					else
					{
						tr=self.contentTable.rows[retIdx];
						tr.parentNode.insertBefore(ret.domNode, tr);
					}
				}
				return ret;
			},

			removed: function(node, idx1, incMod, retIdx){
				node.domNode.parentNode.deleteRow(incMod);
				return null;
			},

			set: function(node, idx1, item, idx2, incMod, retIdx){
				if(incMod!=retIdx)
					node.domNode.parentNode.insertBefore(node.domNode, self.contentTable.rows[retIdx]);
				return node;
			}
		});
		this.viewableItemsChanged();
	},

	viewableItemsChanged: function(){
		for(var i=0;i<this.items.length;i++)
		{
			var visible=false;
			if(!this.filter)
				visible=true;
			else
			{
				var id=this.model.getIdentity(this.items[i].item);
				for(var j=0;j<this.filter.length;j++)
					if(this.filter[j]==id)
					{
						visible=true;
						break;
					}
			}
			PrismsUtils.setTableRowVisible(this.items[i].domNode, visible);
		}
	},

	rootChanged: function(item){
		this.titleNode.setItem(item);
	},

	changed: function(item){
		var id=this.model.getIdentity(item);
		var node=this.getNode(item);
		if(node)
		{
			node.setItem(item);
			if(this.selection==node)
				node.labelNode.appendChild(this.actionLead);
		}
	},

	setFilteredItems: function(ids){
		this.filter=ids;
		this.viewableItemsChanged();
	},

	getNode: function(item){
		var id=this.model.getIdentity(item);
		for(var i=0;i<this.items.length;i++)
			if(this.model.getIdentity(this.items[i].item)==id)
				return this.items[i];
		return null;
	},

	createNode: function(item){
		return new prisms.widget._SearchableListNode({list: this, item: item});
	},

	onClick: function(e){
		var tn = __dijit.getEnclosingWidget(e.target);
		if(tn==this.filterText)
			return;
		if(e.shiftKey && this.model.selectionMode=="multiple")
		{
			if(!tn || !tn.isSearchNode)
				return;
			this.shiftSelect(tn);
		}
		else if(e.ctrlKey && this.model.selectionMode=="multiple")
		{
			if(!tn || !tn.isSearchNode)
				return;
			this.toggleSelection(tn);
		}
		else if(this.model.selectionMode=="single" || this.model.selectionMode=="multiple")
		{
			if(tn && tn.isSearchNode)
				this.setSelection([tn]);
			else
				this.setSelection(null);
		}
	},

	isSelected: function(tn){
		for(var s=0;s<this.selection.length;s++)
			if(this.selection[s]==tn)
				return true;
		return false;
	},

	shiftSelect: function(tn){
		if(this.selection.length==0)
			this.setSelection([tn]);
		else
		{
			var oSel=this.selection[0];
			var selection=[];
			var found=null;
			for(i=0;i<this.items.length;i++)
			{
				if(found==true)
				{
					selection.push(this.items[i]);
					if(this.items[i]==tn)
						break;
				}
				else if(found==false)
				{
					selection.splice(0, 0, this.items[i]);
					if(this.items[i]==oSel)
						break;
				}
				else if(this.items[i]==oSel)
				{
					selection.push(this.items[i]);
					found=true;
				}
				else if(this.items[i]==tn)
				{
					selection.push(this.items[i]);
					found=false;
				}
			}
			this.setSelection(selection);
		}
	},

	toggleSelection: function(tn){
		var selection=[];
		var contained=false;
		for(var s=0;s<this.selection.length;s++)
		{
			if(this.selection[s]==tn)
				contained=true;
			else
				selection.push(this.selection[s]);
		}
		if(!contained)
			selection.push(tn);
		this.setSelection(selection);
	},

	setSelection: function(selection){
		PrismsUtils.adjust(this.selection, selection, {
			identity: function(item1, item2){
				return item1==item2;
			},

			added: function(item, idx2, retIdx){
				item.setSelected(true);
			},

			removed: function(item, idx1, incMod, retIdx){
				item.setSelected(false);
			},

			set: function(item1, idx1, item2, idx2, incMod, retIdx){
			}
		});
		this.selection=selection;
		this._notifySelection();
	},

	getSelectedActions: function(){
		var actions=[];
		if(this.selection.length==0)
			return actions;
		for(var a=0;a<this.selection[0].item.actions.length;a++)
			actions.push(this.selection[0].item.actions[a]);
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
		return actions;
	},

	showActions: function(evt){
		this.prismsMenu._openMyself(evt);
	},

	addMenuItems: function(e){
		if(e)
		{
			__dojo.stopEvent(e);
			var tn = __dijit.getEnclosingWidget(e.target);	
			if(tn && tn.isSearchNode){
				if(e.shiftKey)
				{
					if(!this.isSelected(tn))
						this.shiftSelect(tn);
				}
				else if(e.ctrlKey)
				{
					if(!this.isSelected(tn))
						this.toggleSelection(tn);
				}
				else
				{
					if(!this.isSelected(tn))
						this.setSelection([tn]);
				}
			}
		}
		var actions=this.getSelectedActions();
		this.addMenuItemsFromActions(actions);
	},

	addMenuItemsFromActions: function(actions){  
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
				item=new prisms.widget._SearchListMenuItem({menu: this.prismsMenu, list: this,
					label: actions[i].text});
			this.prismsMenu.addChild(item);
		}
	},

	_notifySelection: function(){
		var actions=this.getSelectedActions();
		if(actions.length==0)
		{
			if(this.actionLead.parentNode)
				this.actionLead.parentNode.removeChild(this.actionLead);
		}
		else
			this.selection[0].labelNode.appendChild(this.actionLead);
		var items=[];
		for(var s=0;s<this.selection.length;s++)
			items.push(this.selection[s].item);
		this.model.notifySelection(items);
	},

	setVisible: function(visible){
		if(visible)
			this.domNode.style.display="block";
		else
			this.domNode.style.display="none";
	},

	fireAction: function(action){
		if(!this.selection)
			return;
		var items=[];
		for(var s=0;s<this.selection.length;s++)
			items.push(this.selection[s].item);
		if(typeof this.model.performAction == "function") 
			this.model.performAction(action, items);
		else
			throw new Error("No performAction function in model for list");
	},

	_filterTextChanged: function(){
		var self=this;
		setTimeout(function(){
			self.prisms.callApp(self.model.pluginName, "setFilter", {filter: self.filterText.getValue()});
		}, 100);
	}
});
