
__dojo.provide("prisms.widget.MultiSelect");
__dojo.declare("prisms.widget.MultiSelect", __dijit._Widget, {

	selectColor: "#316ac5",

	selectTextColor: "#ffffff",

	unselectColor: "#ffffff",

	unselectTextColor: "#000000",

	postCreate: function(){
		this.inherited(arguments);
		__dojo.connect(this.domNode, "onblur", this, function(event){
			this.domNode.selectedIndex=-1;
			this.domNode.style.backgroundColor="white";
			this.domNode.style.color="black";
		});
		__dojo.connect(this.domNode, "onclick", this, function(event){
			this.domNode.selectedIndex=-1;
			this.domNode.style.backgroundColor="white";
			this.domNode.style.color="black";
		});
		this._selection=[];
		for(var i=0;i<this.domNode.options.length;i++)
		{
			var option=this.domNode.options[i];
			option.connects=[];
			option.connects.push(__dojo.connect(option, "onclick", this, this._optionClicked));
			option.connects.push(__dojo.connect(option, "onmouseup", this, function(event){
				this._stop(event);
			}));
			option.connects.push(__dojo.connect(option, "onmouseover", this, function(event){
				this._stop(event);
			}));
			option.connects.push(__dojo.connect(option, "onmousemove", this, function(event){
				this._stop(event);
			}));
			option.style.backgroundColor=this.unselectColor;
			option.style.color=this.unselectTextColor;
		}
		this.domNode.selectedIndex=-1;
	},
	
	setDisabled: function(disabled){
		if(disabled)
			this.domNode.disabled=true;
		else
			this.domNode.disabled=false;
	},

	_optionClicked: function(e){
		var option=e.target;
		option.selected=false;
		this.domNode.selectedIndex=-1;

		this.setOptionSelected(option, !option.isSelected);
	},

	getOptions: function(){
		return this.domNode.options;
	},

	/** @return The indices of all options that have been selected */
	getSelection: function(){
		return this._selection;
	},

	addOption: function(option, index){
		if(!option.text)
			option={text: option};
		var optionEl=document.createElement("option");
		optionEl.text=option.text;
		optionEl.value=option.value;
		optionEl.connects=[];
		optionEl.connects.push(__dojo.connect(optionEl, "onclick", this, this._optionClicked));
		optionEl.connects.push(__dojo.connect(optionEl, "onmouseup", this, function(event){
			this._stop(event);
		}));
		optionEl.connects.push(__dojo.connect(optionEl, "onmouseover", this, function(event){
			this._stop(event);
		}));
		optionEl.connects.push(__dojo.connect(optionEl, "onmousemove", this, function(event){
			this._stop(event);
		}));
		optionEl.style.backgroundColor=this.unselectColor;
		optionEl.style.color=this.unselectTextColor;
		if(typeof index=="number" && index>=0 && index<this.domNode.options.length)
		{
			this.domNode.add(optionEl, this.domNode.options[index]);
			for(var i=0;i<this._selection.length;i++)
				if(this._selection[i]>=index)
					this._selection[i]++;
		}
		else if(__dojo.isIE > 6)
			this.domNode.add(optionEl);
		else
			this.domNode.add(optionEl, null);
		this.domNode.selectedIndex=-1;
		return option;
	},

	removeOption: function(index){
		if(index<0 || index>=this.domNode.length)
			return null;
		var selChanged=false;
		for(var i=0;i<this._selection.length;i++)
		{
			if(this._selection[i]==index)
			{
				selChanged=true;
				this._selection.splice(i, 1);
				i--;
			}
			else if(this._selection[i]>index)
				this._selection[i]--;
		}
		var option=this.domNode.options[index];
		this.domNode.remove(index);
		for(var c=0;c<option.connects.length;c++)
			__dojo.disconnect(option.connects[c]);
		delete option["connects"];
		if(selChanged)
			this.onChange();
		return option;
	},

	clearOptions: function(){
		while(this.domNode.options.length>0)
			this.removeOption(this.domNode.options.length-1);
	},

	setSelected: function(index, selected){
		if(index<0 || index>=this.domNode.options.length)
			return;
		this.setOptionSelected(this.domNode.options[index], selected);
	},

	setOptionSelected: function(option, selected){
		selected= selected ? true : false;
		if(option.isSelected==selected)
			return;
		option.isSelected=selected;
		var index=option.index;
		if(selected)
		{
			this._selection.push(index);
			option.style.backgroundColor=this.selectColor;
			option.style.color=this.selectTextColor;
		}
		else
		{
			for(var i=0;i<this._selection.length;i++)
				if(this._selection[i]==index)
				{
					this._selection.splice(i, 1);
					i--;
				}
			option.style.backgroundColor=this.unselectColor;
			option.style.color=this.unselectTextColor;
		}
		this.onChange();
	},

	onChange: function(){
	},

	_stop: function(event){
		event.cancelBubble=true;
		if(typeof event.stopPropagation=="function")
			event.stopPropagation();
		if(typeof event.preventDefault=="function")
			event.preventDefault();
	}
});
