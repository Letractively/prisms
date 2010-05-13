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

	setTableVisible: function(table, show){
		if(__dojo.isIE > 6 && show)
			table.style.display="block";
		else if(show)
			table.style.display="table";
		else
			table.style.display="none";
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
	},

	/**
	 * Validates a password according to server constraints.
	 * 
	 * @return An error message if the password does not meet the constraints, or null if it does
	 */
	validatePassword: function(password, constraints){
		if(!constraints)
			return null;
		if(password.length<constraints.minLength)
			return "Password must be at least "+constraints.minLength+" character(s) long";
		var count;
		if(constraints.minUpperCase>0)
		{
			count=0;
			for(var i=0;i<password.length;i++)
				if(password.charAt(i)>='A' && password.charAt(i)<='Z')
					count++;
			if(count<constraints.minUpperCase)
				return "Password must contain at least "+constraints.minUpperCase
					+" upper-case character(s)";
		}
		if(constraints.minLowerCase>0)
		{
			count=0;
			for(var i=0;i<password.length;i++)
				if(password.charAt(i)>='a' && password.charAt(i)<='z')
					count++;
			if(count<constraints.minLowerCase)
				return "Password must contain at least "+constraints.minLowerCase
					+" lower-case character(s)";
		}
		if(constraints.minDigits>0)
		{
			count=0;
			for(var i=0;i<password.length;i++)
				if(password.charAt(i)>='0' && password.charAt(i)<='9')
					count++;
			if(count<constraints.minDigits)
				return "Password must contain at least "+constraints.minDigits+" digit(s) (0-9)";
		}
		if(constraints.minSpecialChars>0)
		{
			count=0;
			for(var i=0;i<password.length;i++)
			{
				if(password.charAt(i)>='0' && password.charAt(i)<='9')
					continue;
				if(password.charAt(i)>='A' && password.charAt(i)<='Z')
					continue;
				if(password.charAt(i)>='a' && password.charAt(i)<='z')
					continue;
				count++;
			}
			if(count<constraints.minSpecialChars)
				return "Password must contain at least "+constraints.minSpecialChars
					+" special character(s) (&, *, _, @, etc.)";
		}
		return null;
	},

	/**
	 * Ported from prisms.util.ArrayUtils.adjust(T1 [], T2 [], DifferenceListenerE<T1, T2, E>)
	 */
	adjust: function(original, modifier, dl)
	{
		var oMappings = new Array(original.length);
		var mMappings = new Array(modifier.length);
		var o, m, r;
		for(o=0;o<oMappings.length;o++)
			oMappings[o]=0;
		for(m = 0; m < mMappings.length; m++)
			mMappings[m] = -1;
		r = original.length + modifier.length;
		var crossMapping = false;
		for(o = 0; o < original.length; o++)
		{
			oMappings[o] = -1;
			for(m = 0; m < modifier.length; m++)
			{
				if(mMappings[m] >= 0)
					continue;
				if(dl.identity(original[o], modifier[m]))
				{
					crossMapping = true;
					oMappings[o] = m;
					mMappings[m] = o;
					r--;
					break;
				}
			}
		}
		var ret = new Array(r);
		var incMods = new Array(original.length);
		for(var i = 0; i < incMods.length; i++)
			incMods[i] = i;
		r = 0;
		o = 0;
		m = 0;
		if(crossMapping)
		{
			/* If there are items that match, remove the items in the original that occur before
			 * the first match */
			for(; o < original.length && oMappings[o] < 0; o++)
			{
				ret[r] = dl.removed(original[o], o, incMods[o], r);
				if(ret[r] != null)
					r++;
				else
				{ // Adjust the incremental modification indexes
					for(var i = o + 1; i < incMods.length; i++)
						incMods[i]--;
				}
			}
		}
		else
		{
			/* If there were no matches, we want to remove the original items before the modifiers */
			for(o = 0; o < original.length; o++)
			{
				ret[r] = dl.removed(original[o], o, incMods[o], r);
				if(ret[r] != null)
					r++;
				else
				{
					for(var i = o + 1; i < incMods.length; i++)
						incMods[i]--;
				}
			}
		}
		for(m = 0; m < modifier.length; m++)
		{
			/* Add or set each modifier
			 */
			o = mMappings[m];
			if(o >= 0)
			{
				ret[r] = dl.set(original[o], o, incMods[o], modifier[m], m, r);
				if(ret[r] != null)
				{
					// Adjust the incremental modification indexes
					var incMod = incMods[o];
					if(r > incMod)
					{ // Element moved forward--decrement incMods up to the new index
						for(var i = 0; i < incMods.length; i++)
							if(incMods[i] >= incMod && incMods[i] <= r)
								incMods[i]--;
					}
					else if(r < incMod)
					{ // Element moved backward--increment incMods up to the original index
						for(var i = 0; i < incMods.length; i++)
							if(incMods[i] >= r && incMods[i] < incMod)
								incMods[i]++;
					}
					r++;
				}
				else
				{
					for(var i = 0; i < incMods.length; i++)
						if(incMods[i] >= r)
							incMods[i]--;
				}
			}
			else
			{
				ret[r] = dl.added(modifier[m], m, r);
				if(ret[r] != null)
				{
					r++;
					// Adjust the incremental modification indexes
					for(var i = 0; i < incMods.length; i++)
					{
						if(incMods[i] >= r)
							incMods[i]++;
					}
				}
			}
			if(o >= 0)
			{
				/* After each modifier, remove the originals that occur before the next match */
				for(o++; o < original.length && oMappings[o] < 0; o++)
				{
					ret[r] = dl.removed(original[o], o, incMods[o], r);
					if(ret[r] != null)
						r++;
					else
					{ // Adjust the incremental modification indexes
						for(var i = o + 1; i < incMods.length; i++)
							incMods[i]--;
					}
				}
			}
		}

		var actualRet = new Array(r);
		for(var i=0;i<r;i++)
			actualRet[i]=ret[i];
		return actualRet;
	}
}
