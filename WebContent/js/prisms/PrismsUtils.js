dojo.provide("prisms.PrismsUtils");

window.PrismsUtils =  {
	getPrisms: function(node){
		if(node.prisms)
			return node.prisms;
		var parent=this.getParent(node);
		var prisms=null;
		if(parent)
			prisms=this.getPrisms(parent);
		if(prisms)
			node.prisms=prisms;
		return prisms;
	},

	getParent: function(node){
		if(typeof node.getParent=="function")
		{
			var ret= node.getParent();
			if(ret)
				return ret;
		}
		if(node.domNode)
			node=node.domNode;
		var ret=node.parentNode;
		if(ret && ret.id)
		{
			var widget=dijit.byId(ret.id);
			if(widget && widget.domNode==ret)
				ret=widget;
		}
		return ret;
	},

	displayTab: function(widget){
		var tab=widget;
		var prevTab;
		while(tab!=null)
		{
			if(tab.tablist)
			{
				var parentTab=prevTab;
				var parentTabContainer=tab;
				parentTabContainer.selectChild(parentTab);
			}
			if(tab.domNode)
				prevTab=tab;
			else if(tab.id && tab.id.length()>0)
				prevTab=dijit.byId(tab.id);
			tab=this.getParent(tab);
		}
	},

	fixUnicodeString: function(str){
		if(str==null)
			return str;
		var idx=str.indexOf("\\u");
		while(idx>=0)
		{
			var codeChar=eval("\"\\u"+str.substring(idx+2, idx+6)+"\"");
			str=str.substring(0, idx)+codeChar+str.substring(idx+6);
			idx=str.indexOf("\\u");
		}
		return str;
	},

	setTableRowVisible: function(row, show) {
		if(dojo.isIE > 6 && show)
			row.style.display="block";
		else if(show)
			row.style.display="table-row";
		else
			row.style.display="none";
	},

	setTableCellVisible: function(cell, show) {
		if(dojo.isIE > 6 && show)
			cell.style.display="block";
		else if(show)
			cell.style.display="table-cell";
		else
			cell.style.display="none";
	},

	setComboValue: function(select, value){
		var i;
		for(i=0;i<select.options.length;i++)
			if(select.options[i].value==value)
			{
				select.selectedIndex=i;
				break;
			}
		if(i==select.options.length)
			throw new Error("Value \""+value+"\" not an option in select box");
	},

	getComboValue: function(select, value){
		return select.options[select.selectedIndex].value;
	}
}
