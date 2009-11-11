
dojo.provide("prisms.widget.TreeModel");
dojo.declare("prisms.widget.TreeModel", null, {

	prisms: null,

	pluginName: "No pluginName specified",
	
	selectionMode: "none",
	
	constructor:function(params){
		this.pluginName=params.pluginName;
		this.theValue={id: "ROOT", text: "Root", bgColor: "#ffffff", textColor: "#000000",
			children:[], actions:[], path:["ROOT"]};
		/*
		 * By Andrew Butler, PSL
		 * 
		 * The idSuffix is a somewhat ugly hack around a problem with the tree.  Normally when
		 * this.onChildrenChange is called, the tree checks its node cache for nodes that match the
		 * identity of the children items.  If those nodes are found, they are inserted into the
		 * parent as-is without any update.  A refresh event on this tree is intended to make the
		 * tree refresh itself from scratch, but the ids of the nodes may be the same as before the
		 * refresh even though the nodes or their children have changed.  Therefore an idSuffix is
		 * kept and incremented for each refresh, then appended onto the id of each item in the tree
		 * (except the root, whose ID can never change).  This causes the tree to see every item in
		 * the tree as new after a refresh, effecting the desired changes.
		 */
		this.idSuffix=0;
	},

	setPrisms: function(prisms){
		this.prisms=prisms;
		if(this._rootCallback)
			this.prisms.loadPlugin(this);
	},
	
	hackReload: function() {
		this.loadChildren(this.hackPath);
		this.refreshFromData(this.hackRoot);
	},

	processEvent: function(event)
	{
		var path=null;
		if(event.path)
			path=event.path;
		else if(event.root)
			path=[event.root];
		if(path)
		{
			var pTemp=[];
			for(var p=0;p<path.length;p++)
			{
				this.addItemPaths(path[p], pTemp);
				pTemp.push(path[p].id);
			}
		}
		if(event.method=="refresh")
			this.refreshFromData(event.root);
		else if(event.method=="nodeAdded")
			this.addFromData(event.path, event.index);
		else if(event.method=="nodeRemoved")
			this.removeFromData(event.path);
		else if(event.method=="nodeChanged")
			this.changeFromData(event.path, event.recursive);
		else if(event.method=="nodeMoved")
			this.moveFromData(event.path, event.index);
		else if(event.method=="loadChildren")
			this.loadChildren(event.path);
		else if(event.method=="setVisible")
			this.setVisible(event.visible);
		else if(event.method=="setSelectionMode")
			this.selectionMode=event.selectionMode;
		else
			throw new Error("Unrecognized event: "+this.prisms.toJson(event));
	},

	addItemPaths: function(item, path){
		if(typeof path == "undefined")
			path=[];
		path.push((typeof item.id != "undefined" ? item.id : item));
		var pathClone=[];
		for(var p=0;p<path.length;p++)
			pathClone[p]=path[p];
		item.path=pathClone;
		if(item.children)
			for(var c=0;c<item.children.length;c++)
			{
				if(typeof item.children[c].id == "undefined")
					item.children[c]={id: item.children[c]};
				this.addItemPaths(item.children[c], path);
			}
		path.pop();
	},

	getRoot: function(onItem){
		this._rootCallback=onItem;
		if(this.prisms)
			this.prisms.loadPlugin(this);
	},

	mayHaveChildren: function(item){
		if(typeof item.children == "object" && item.children)
			return item.children.length>0;
		else
			return true;
	},

	getChildren: function(parentItem, onComplete){
		if(parentItem.children)
			onComplete(parentItem.children);
		else {
			this.requestLoadChildren(parentItem, onComplete);
		}
	},

	getIdentity: function(item){
		if(item.id==this.theValue.id)
			return "ROOT";	//Can't change the ID of the root
		return item.path.join("_")+this.idSuffix;
	},

	getLabel: function(item){
		return PrismsUtils.fixUnicodeString(item.text);
	},

	newItem: function(args, parent){
	},

	/**
	 * Just a callback for the tree
	 */
	onChange: function(item){
	},

	/**
	 * Just a callback for the tree
	 */
	onChildrenChange: function(parent, newChildrenList){
	},

	_hackAlertLazyLoading: function(lazyLoadingHack){
	},

	refreshFromData: function(root){
		if(root==null)
			return;
		this.idSuffix++;
		this.hackRoot = root;
		
		var oldValue=this.theValue;
		this.theValue=root;
		if(typeof root.children=="undefined" || root.children==null)
			this._hackAlertLazyLoading(true);
		if(this._rootCallback)
		{
			this._rootCallback(root);
			delete this._rootCallback;
		}
		else
		{
			this.onChange(root);
			this.onChildrenChange(root, root.children);
		}
	},

	navigate: function(path, depth)
	{
		if(typeof depth == "undefined" || depth==null)
			depth=path.length-1;
		if(!this.theValue)
			return null;
		return this.navigateNode(this.theValue, path, 0, depth);
	},

	navigateNode: function(valueNode, path, pathIdx, depth)
	{
		dojo.mixin(valueNode, path[pathIdx]);
		this.onChange(valueNode);
		if(pathIdx==depth)
			return valueNode;
		if(typeof valueNode.children == "undefined" || !valueNode.children)
			return null; //valueNode has not been expanded for this lazy-loading tree.  Nothing to do.
		if(valueNode.children.length==0)
		{
			this.prisms.error(this.pluginName+": Unable to navigate to parent for path", path);
			return null;
		}

		var children=valueNode.children;
		for(var c=0;c<children.length;c++)
			if(this.itemsEqual(children[c], path[pathIdx+1]))
				return this.navigateNode(children[c], path, pathIdx+1, depth);
		return null;
	},

	itemsEqual: function(item1, item2){
		var item1Id= (typeof item1.id == "undefined" ? item1 : item1.id);
		var item2Id= (typeof item2.id == "undefined" ? item2 : item2.id);
		return item1Id==item2Id;
	},

	addFromData: function(path, index){
		var parent=this.navigate(path, path.length-2);
		if(parent==null)
			return;
		if(typeof parent.children=="undefined" || !parent.children)
			return; //parent has not been expanded for this lazy-loading tree.  Nothing to do.
		if(index>parent.children.length)
			throw new Error("Illegal addition index: adding "+this.prisms.toJson(path)+" at index "+index);
		if(index<0)
			index=parent.children.length;
		parent.children.splice(index, 0, path[path.length-1]);
		this.onChildrenChange(parent, parent.children);
	},

	removeFromData: function(path){
		var parent=this.navigate(path, path.length-2);
		if(parent==null)
			return;
		if(typeof parent.children=="undefined" || !parent.children)
			return; //parent has not been expanded for this lazy-loading tree.  Nothing to do.
		for(var c=0;c<parent.children.length;c++)
		{
			if(parent.children[c].id==path[path.length-1].id)
			{
				var child=parent.children[c];
				parent.children.splice(c, 1);
				break;
			}
		}
		this.onChildrenChange(parent, parent.children);
	},

	changeFromData: function(path, recursive){
		var node=this.navigate(path, path.length-1);
		if(!node)
			return;
		this.onChange(path[path.length-1]);
		if(recursive)
			this.changeRecursive(path[path.length-1]);
	},

	changeRecursive: function(node){
		if(!node.children)
			return;
		for(var c=0;c<node.children.length;c++)
		{
			this.onChange(node.children[c]);
			this.changeRecursive(node.children[c]);
		}
	},

	moveFromData: function(path, index){
		var parent=this.navigate(path, path.length-2);
		if(parent==null)
			return;
		if(typeof parent.children=="undefined" || !parent.children)
			return; //parent has not been expanded for this lazy-loading tree.  Nothing to do.
		for(var c=0;c<parent.children.length;c++)
		{
			if(parent.children[c].id==path[path.length-1].id)
			{
				var child=parent.children[c];
				parent.children.splice(c, 1);
				parent.children.splice(index, 0, path[path.length-1]);
				break;
			}
		}
		this.onChildrenChange(parent, parent.children);
	},

	_loadCallbacks: {},

	loadChildren: function(path){
		this._hackAlertLazyLoading(false);
		
		var parent=this.navigate(path, path.length-1);
		var id=this.getIdentity(parent);
		var callback=this._loadCallbacks[id];
		delete this._loadCallbacks[id];
		if(!parent)
		{
			this.prisms.error("Could not load children of "+this.prisms.toJson(path));
			return;
		}
		var newParent=path[path.length-1];
		dojo.mixin(parent, newParent);
		
		this.hackPath = path;
		
		if(callback)
			callback.apply(window, [parent.children]);
	},

	requestLoadChildren: function(item, callback){
		this._loadCallbacks[this.getIdentity(item)]=callback;
		this.prisms.callApp(this.pluginName, "loadChildren", {path:item.path}, {sync:true});
	},

	performAction: function(action, items){
		var paths=[];
		for(var i=0;i<items.length;i++)
			paths[i]=items[i].path;
		this.prisms.callApp(this.pluginName, "actionPerformed", {action: action, paths: paths});
	},

	setVisible: function(visible){
	},

	getSelectionMode: function(){
		return this.selectionMode;
	},

	notifySelection: function(items){
		var paths=[];
		if(this.selectionMode=="single")
		{
			var i=items.length-1;
			while(i>=0 && !items[i].path)
				i--;
			if(i>=0)
				paths.push(items[i].path);
		}
		else if(this.selectionMode=="multiple")
		{
			for(var i=0;i<items.length;i++)
				if(items[i].path)
					paths.push(items[i].path);
		}
		else
			paths=null;
		if(paths!=null)
			this.prisms.callApp(this.pluginName, "notifySelection", {paths: paths});
	}
});
