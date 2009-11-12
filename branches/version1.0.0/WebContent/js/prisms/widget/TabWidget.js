
dojo.provide("prisms.widget.TabWidget");
dojo.declare("prisms.widget.TabWidget", [dijit._Widget, dijit._Contained], {
	_visible: true,
	
	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.initTabParents();
		this.loadParentTabs();
	},

	initTabParents: function(){
		this.isTabWidget=true;
		var tab=this;
		var prevTab;
		while(tab!=null && !tab.tablist)
		{
			prevTab=tab;
			tab=this.getWidgetParent(tab);
		}
		if(tab==null)
			return;
		else
		{
			this.parentTab=prevTab;
			this.parentTabContainer=tab;
		}
		this._visible=true;
		this.siblingPreMap={};
		var pre=true;
		var children=this.parentTabContainer.getChildren();
		for(var c=0;c<children.length;c++)
		{
			if(children[c]==this.parentTab)
				pre=false;
			else
				this.siblingPreMap[children[c].domNode.id]=pre;
		}
	},

	loadParentTabs: function(){
/*		if(!this.parentTabContainer || this.parentTabContainer.allTabsLoaded)
			return;
		this.parentTabContainer.allTabsLoaded=true;
		var oldSelected=this.parentTabContainer.selectedChildWidget;
		var tabs=this.parentTabContainer.getChildren();
		for(var t=0;t<tabs.length;t++)
			this.parentTabContainer.selectChild(tabs[t]);
		tabs=this.parentTabContainer.getChildren();
		for(var t=0;t<tabs.length;t++)
			if(tabs[t]==oldSelected)
			{
				this.parentTabContainer.selectChild(tabs[t]);
				break;
			}
*/	},

	setVisible: function(visible){
		if(!this.parentTab)
			return;
		if(this._visible==visible)
			return;
		if(visible)
		{
			var index=this.getAddToIndex();
			if(index>=0)
				this.parentTabContainer.addChild(this.parentTab, index);
			this._visible=true;
			this.setSelected(true);
			if(this.parentTabContainer.getChildren().length==2
				&& this.parentTabContainer.getChildren()[0].isPlaceholder)
				this.parentTabContainer.removeChild(this.parentTabContainer.getChildren()[0]);
		}
		else
		{
			if(this.parentTabContainer.getChildren().length==1)
			{
				var toAdd=new dijit.layout.ContentPane({title: "..."});
				toAdd.isPlaceholder=true;
				this.parentTabContainer.addChild(toAdd, 1);
			}
			var index=this.getContainerIndex();
			if(index>=0)
				this.parentTabContainer.removeChild(this.parentTab);
			this._visible=false;
		}
	},

	isSelected: function(){
		return this.parentTabContainer.selectedChildWidget==this.parentTab;
	},

	setSelected: function(selected){
		if(selected)
			this.parentTabContainer.selectChild(this.parentTab);
		else
		{
			if(this.isSelected()){
				this.parentTabContainer.selectedChildWidget = undefined;
				var children = this.parentTabContainer.getChildren();
				for(var c=0;c<children.length;c++)
				{
					if(!children[c].isTabWidget || children[c]._visible)
					{
						this.parentTabContainer.selectChild(children[c]);
						break;
					}
				}
			}
		}
	},

	getAddToIndex: function(){
		if(this.parentTabContainer.getChildren().length==1
			&& this.parentTabContainer.getChildren()[0].isPlaceholder)
			return 1;
		var children=this.parentTabContainer.getChildren();
		for(var c=0;c<children.length;c++)
		{
			if(children[c]==this.parentTab)
				return -1;
			if(!this.siblingPreMap[children[c].domNode.id])
				return c;
		}
		return this.parentTabContainer.getChildren().length;
	},

	getContainerIndex: function(){
		var children=this.parentTabContainer.getChildren();
		for(var c=0;c<children.length;c++)
		{
			if(children[c]==this.parentTab)
				return c;
		}
		return -1;
	},

	getWidgetParent: function(widget){
		var parentDomNode=widget.domNode.parentNode;
		var ret;
		while(parentDomNode.parentNode && (parentDomNode.id==null || parentDomNode.id.length==0
			|| !(ret=dijit.byId(parentDomNode.id))))
			parentDomNode=parentDomNode.parentNode;
		return ret;
	}
});
