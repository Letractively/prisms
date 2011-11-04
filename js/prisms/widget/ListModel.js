/**
 * prisms.ListModel is a tree model that supplies a tree with data to make the tree act like a
 * non-hierarchical list.
 */

__dojo.provide("prisms.widget.ListModel");
__dojo.declare("prisms.widget.ListModel", null, {

	pluginName: "No pluginName specified",

	prisms: null,

	selectionMode: "none",

	constructor: function(params){
		this.pluginName=params.pluginName;
		if(params.notifySelection)
			this.notifySelection=params.notifySelection;
		this.theValue={id: "ROOT", isRoot: true, text: "Root", bgColor: "#ffffff",
			textColor: "#000000", actions:[], children: []};
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		if(this._rootCallback)
			this.prisms.loadPlugin(this);
	},

	processEvent: function(event){
		if(event.method=="setListParams")
			this.setListParams(event.params);
		else if(event.method=="setItems")
			this.setItems(event.items);
		else if(event.method=="addItem")
			this.addItem(event.item, event.index);
		else if(event.method=="removeItem")
			this.removeItem(event.item, event.index);
		else if(event.method=="moveItem")
			this.moveItem(event.item, event.index);
		else if(event.method=="changeItem")
			this.changeItem(event.item);
		else if(event.method=="setEnabled")
			this.setEnabled(event.enabled);
		else if(event.method=="setVisible")
			this.setVisible(event.visible);
		else if(event.method=="setSelectionMode")
			this.selectionMode=event.selectionMode;
		else if(event.method=="setFilteredItems")
			this.setFilteredItems(event.ids);
		else
			throw new Error("Unrecognized event: "+this.prisms.toJson(event));
	},

	shutdown: function(){
		this.theValue.text="-----";
		this.theValue.icon=null;
		this.theValue.bgColor="#ffffff";
		this.theValue.textColor="#000000";
		this.theValue.children=[];
		this.theValue.actions=[];
		this.onChange(this.theValue);
		this.onChildrenChange(this.theValue, this.theValue.children);
		this.setListParams({
			placeHolder: "",
			filter: "",
			title: "",
			icon: null,
			description: "",
			actions: []
		});
	},

	getRoot: function(onItem){
		if(this.isLoaded)
			onItem(this.theValue);
		else
		{
			this._rootCallback=onItem;
			if(this.prisms)
				this.prisms.loadPlugin(this);
		}
	},

	mayHaveChildren: function(item){
		return item==this.theValue;
	},

	getChildren: function(parentItem, onComplete){
		if(parentItem==this.theValue)
			onComplete(this.theValue.children);
		else
			onComplete([]);
	},

	getIdentity: function(item){
		return item.id;
	},

	getLabel: function(item){
		return PrismsUtils.fixUnicodeString(item.text);
	},

	newItem: function(args, parent){
	},

	/** Just a callback for the tree */
	onChange: function(item){
	},

	/** Just a callback for the tree */
	onChildrenChange: function(parent, newChildrenList){
	},

	/** Callback for SearchableList */
	setFilteredItems: function(ids){
	},

	setItems: function(items){
		this.theValue.children=items;
		if(this._rootCallback)
		{
			this._rootCallback(this.theValue);
			delete this._rootCallback;
			this.isLoaded=true;
		}
		else
			this.onChildrenChange(this.theValue, items);
	},

	addItem: function(item, index){
		this.theValue.children.splice(index, 0, item);
		this.onChildrenChange(this.theValue, this.theValue.children);
	},

	removeItem: function(item){
		var v;
		for(v=0;v<this.theValue.children.length;v++)
			if(this.theValue.children[v].id==item.id)
				break;
		if(v==this.theValue.children.length)
			throw new Error("Unrecognized item: "+this.prisms.toJson(item));
		var removed=this.theValue.children[v];
		this.theValue.children.splice(v, 1);
		this.onChildrenChange(this.theValue, this.theValue.children);
	},

	moveItem: function(item, index){
		this.removeItem(item);
		this.addItem(item, index);
	},

	changeItem: function(item){
		for(var v=0;v<this.theValue.children.length;v++)
			if(this.theValue.children[v].id==item.id)
			{
				__dojo.mixin(this.theValue.children[v], item);
				this.onChange(this.theValue.children[v]);
				break;
			}
	},

	setListParams: function(params){
		if(params.icon)
			this.theValue.icon=params.icon;
		if(params.title)
			this.theValue.text=params.title;
		if(params.description)
			this.theValue.description=params.description;
		if(params.actions)
			this.theValue.actions=params.actions;
		this.onChange(this.theValue);
	},

	setEnabled: function(enabled){
		if(enabled)
			this.theValue.textColor="#000000";
		else
			this.theValue.textColor="#a0a0a0";
		this.onChange(this.theValue);
	},

	/** Stub method for the tree to listen to */
	setVisible: function(visible){
	},

	performAction: function(action, items){
		var itemIDs=[];
		for(var i=0;i<items.length;i++)
		{
			if(items[i]==this.theValue)
				this.prisms.callApp(this.pluginName, "actionPerformed", {action: action});
			else
				itemIDs[itemIDs.length]=items[i].id;
		}
		this.prisms.callApp(this.pluginName, "actionPerformed", {action: action, paths: itemIDs});
	},

	getSelectionMode: function(){
		return this.selectionMode;
	},

	notifySelection: function(items){
		var ids=[];
		if(this.selectionMode=="single")
		{
			var i=items.length-1;
			while(i>=0 && !items[i].id)
				i--;
			if(i>=0)
				ids.push(items[i].id);
		}
		else if(this.selectionMode=="multiple")
		{
			for(var i=0;i<items.length;i++)
				if(items[i].id)
					ids.push(items[i].id);
		}
		else
			ids=null;
		if(ids!=null)
			this.prisms.callApp(this.pluginName, "notifySelection", {ids: ids});
	}
});
