
dojo.require("prisms.widget.TabWidget");
dojo.require("prisms.PrismsUtils");

dojo.provide("prisms.widget.ContentPane");
dojo.declare("prisms.widget.ContentPane", [dijit.layout.ContentPane, dijit._Container, dijit._Contained], {
	_visible: true,

	postCreate: function(){
		this.initTabParents=prisms.widget.TabWidget.prototype.initTabParents;
		this.loadParentTabs=prisms.widget.TabWidget.prototype.loadParentTabs;
		this.setVisible=prisms.widget.TabWidget.prototype.setVisible;
		this.isSelected=prisms.widget.TabWidget.prototype.setSelected;
		this.getAddToIndex=prisms.widget.TabWidget.prototype.getAddToIndex;
		this.getContainerIndex=prisms.widget.TabWidget.prototype.getContainerIndex;
		this.getWidgetParent=prisms.widget.TabWidget.prototype.getWidgetParent;
		this.inherited("postCreate", arguments);
		this.initTabParents();
		this.loadParentTabs();
	},

	setSelected: function(selected, recursive){
		if(selected && recursive)
		{
			var widget=PrismsUtils.getParent(this);
			while(widget)
			{
				if(widget.isTabWidget)
				{
					widget.setSelected(true, true);
					break;
				}
				widget=PrismsUtils.getParent(widget);
			}
		}
	}
});