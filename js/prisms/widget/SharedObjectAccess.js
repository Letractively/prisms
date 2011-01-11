__dojo.require("dijit.form.CheckBox");

__dojo.provide("prisms.widget.SharedObjectAccess");
__dojo.declare("prisms.widget.SharedObjectAccess", [__dijit._Widget, __dijit._Templated],
{  
	templatePath: "__webContentRoot/view/prisms/templates/sharedObjectAccess.html",

	widgetsInTemplate: true,

	enabled: true,

	viewColor: "#8080ff",

	editColor: "#60d060",

	allViewable: false,

	allEditable: false,

	users: [],

	postCreate: function(){
		this.inherited("postCreate", arguments);
		this.setUsers(this.users);
	},

	setAllViewable: function(av){
		this.allViewable=av;
		if(!av)
			this.setAllEditable(false);
		this.viewCheck.setAttribute("disabled", !this.enabled || this.allViewable);
		this.evalColors();
	},

	setAllEditable: function(ae){
		this.allEditable=ae;
		if(ae)
			this.setAllViewable(true);
		this.editCheck.setAttribute("disabled", !this.enabled || this.allEditable);
		this.evalColors();
	},

	setVisible: function(visible){
		if(visible)
			this.domNode.style.display="table";
		else
			this.domNode.style.display="none";
	},

	setEnabled: function(enabled){
		this.enabled=enabled;
		this.viewCheck.setAttribute("disabled", !this.enabled || this.allViewable);
		this.editCheck.setAttribute("disabled", !this.enabled || this.allEditable);
	},

	setValue: function(value){
		this.setAllViewable(value.isViewPublic);
		this.setAllEditable(value.isEditPublic);
		this.setUsers(value.shareUsers);
	},

	getUsers: function(){
		return this.users;
	},

	setUsers: function(users){
		this.dataLock=true;
		try{
			var selUser=null;
			if(this.userSelect.selectedIndex>=0)
				selUser=this.users[this.userSelect.selectedIndex];
			this.users=users;
			while(this.userSelect.options.length>0)
				this.userSelect.remove(this.userSelect.options.length-1);
			var selIdx=-1;
			for(var u=0;u<users.length;u++)
			{
				var option=document.createElement("option");
				var display=users[u].userName;
				if(users[u].local)
					display+=" (local)";
				else if(users[u].center)
					display+=" (from "+users[u].center+")";
				else
					display+=" (unknown origin)";
				option.text=display;
				if(users[u].canEdit)
					option.style.backgroundColor=this.editColor;
				else if(users[u].canView)
					option.style.backgroundColor=this.viewColor;
				else
					option.style.backgroundColor="#ffffff";
				if(selUser!=null && users[u].id==selUser.id)
					selIdx=u;
				if(__dojo.isIE > 6)
					this.userSelect.add(option);
				else
					this.userSelect.add(option, null);
			}
			if(selIdx>=0)
				this.userSelect.selectedIndex=selIdx;
		} finally{
			this.dataLock=false;
		}
		this.evalColors();
	},

	evalColors: function(){
		for(var i=0;i<this.userSelect.options.length;i++)
		{
			var user=this.users[i];
			var canEdit=user.canEdit;
			var canView=canEdit || user.canView;
			var color;
			if(canEdit)
				color=this.editColor;
			else if(canView)
				color=this.viewColor;
			else
				color="#ffffff";
			this.userSelect.options[i].style.backgroundColor=color;
		}
		this._userChanged();
	},

	_userChanged: function(){
		if(this.dataLock)
			return;
		var selIdx=this.userSelect.selectedIndex;
		this.dataLock=true;
		try{
			if(selIdx<0)
			{
				this.userSelect.style.backgroundColor="#ffffff";
				this.viewCheck.setAttribute("checked", false);
				this.viewCheck.setAttribute("disabled", true);
				this.editCheck.setAttribute("checked", false);
				this.editCheck.setAttribute("disabled", true);
			}
			else
			{
				var user=this.users[selIdx];
				var canEdit=user.canEdit;
				var canView=canEdit || user.canView;
				var color;
				if(canEdit)
					color=this.editColor;
				else if(canView)
					color=this.viewColor;
				else
					color="#ffffff";
				this.userSelect.style.backgroundColor=color;
				this.userSelect.options[selIdx].style.backgroundColor=color;

				var viewEnabled=this.enabled && !this.allViewable && !user.globalView;
				this.viewCheck.setAttribute("disabled", !viewEnabled);
				this.viewCheck.setAttribute("checked", canView);
				var editEnabled=this.enabled && !this.allEditable && !user.globalEdit;
				this.editCheck.setAttribute("disabled", !editEnabled);
				this.editCheck.setAttribute("checked", canEdit);
			}
		} finally{
			this.dataLock=false;
		}
	},

	getUserIndex: function(id){
		for(var v=0;v<this.users.length;v++)
			if(this.users[v].id==id)
				return v;
		return -1;
	},

	_viewChanged: function(){
		if(this.dataLock)
			return;
		var selIdx=this.userSelect.selectedIndex;
		var checked=this.viewCheck.checked ? true : false;
		var user=this.users[selIdx];
		user.canView=checked;
		if(!checked)
		{
			user.canEdit=false;
			this.dataLock=true;
			try{
				this.editCheck.setAttribute("checked", false);
			} finally{
				this.dataLock=false;
			}
		}
		this.onChange(user);
	},

	_editChanged: function(){
		if(this.dataLock)
			return;
		var selIdx=this.userSelect.selectedIndex;
		var checked=this.editCheck.checked ? true : false;
		var user=this.users[selIdx];
		user.canEdit=checked;
		if(checked)
		{
			user.canView=true;
			this.dataLock=true;
			try{
				this.viewCheck.setAttribute("checked", true);
			} finally{
				this.dataLock=false;
			}
		}
		this.onChange(user);
	},

	onChange: function(change){
		var selIdx=this.userSelect.selectedIndex;
		this._userChanged();
	}
});
