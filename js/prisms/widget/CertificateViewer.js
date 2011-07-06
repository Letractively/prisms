
__dojo.require("prisms.widget.PrismsTree");

__dojo.provide("prisms.widget.CertViewerTreeModel");

__dojo.declare("prisms.widget.CertViewerTreeModel", null, {
	certPathData: {
		id: "ROOT",
		text: "No certificate",
		icon: "prisms/certificate",
		actions: [],
		children: []
	},

	getRoot: function(onItem, onError){
		onItem(this.certPathData);
	},

	mayHaveChildren: function(item){
		return item.children && item.children.length>0;
	},

	getChildren: function(parentItem, onComplete){
		return parentItem.children;
	},

	getIdentity: function(item){
		return item.id;
	},

	getLabel: function(item){
		return item.text;
	},

	newItem: function(args, parent){},

	onChange: function(item){
	},

	onChildrenChange: function(parent, newChildrenList){
	},

	notifySelection: function(items){
		if(items.length==1 && this.viewer)
			this.viewer._selected(items[0].cert);
	}
});

__dojo.provide("prisms.widget.CertificateViewer");

__dojo.declare("prisms.widget.CertificateViewer", [dijit._Widget, dijit._Templated], {
	templatePath: "__webContentRoot/view/prisms/templates/certificateViewer.html",

	widgetsInTemplate: true,

	postCreate: function(){
		this.inherited(arguments);
		this.model=this.certPathTree.model;
		this.model.viewer=this;
		this.defCertPathData=this.model.certPathData;
	},

	setValue: function(cert){
		this.tabs.startup();
		if(!cert)
		{
			this.model.certPathData=this.defCertPathData;
			this._selected(null);
			this.model.onChange(this.model.certPathData);
			this.model.onChildrenChange(this.model.certPathData, []);
			return;
		}

		var path=[];
		while(cert)
		{
			path.splice(0, 0, cert);
			cert=cert.parent;
		}
		var node=this.toTreeNode(path[0]);
		this.model.certPathData=node;
		node.id="ROOT";
		this.model.onChange(node);
		for(var i=1;i<path.length;i++)
		{
			var child=this.toTreeNode(path[i]);
			child.id="LEVEL"+i;
			node.children=[child];
			this.model.onChildrenChange(node, node.children);
			this.model.onChange(child);
			node=child;
		}
		this._selected(path[path.length-1]);
	},

	toTreeNode: function(cert){
		var ret={};
		ret.text=cert.subject;
		ret.icon="prisms/certificate";
		if(cert.iconError)
			ret.icon+="Error";
		ret.actions=[];
		ret.cert=cert;
		return ret;
	},

	_selected: function(cert){
		this.selectedCert=cert;
		while(this.certDetailsTable.rows.length>1)
			this.certDetailsTable.deleteRow(this.certDetailsTable.rows.length-1);
		this.certDetailsField.value="";
		if(!cert)
		{
			this.certNameField.innerHTML="";
			this.certIssuedField.innerHTML="";
			this.certValidField.innerHTML="";
			this.certDetailsLabel.innerHTML="";
			return;
		}
		this.certNameField.domNode.innerHTML=PrismsUtils.fixUnicodeString(cert.subject);
		this.certIssuedField.domNode.innerHTML=PrismsUtils.fixUnicodeString(cert.issuer);
		this.certValidField.domNode.innerHTML=PrismsUtils.fixUnicodeString(cert.validFrom)
			+"&nbsp;to&nbsp;"+PrismsUtils.fixUnicodeString(cert.validTo);
		this.certStatusField.value="Status: "+PrismsUtils.fixUnicodeString(cert.status);
		this.certDetailsLabel.domNode.innerHTML=PrismsUtils.fixUnicodeString(cert.subject);
		for(var df=0;df<cert.details.length;df++)
		{
			var tr=this.certDetailsTable.insertRow(-1);
			tr.style.cursor="pointer";
			tr.style.backgroundColor="#c0c0c0";
			tr.insertCell(0).innerHTML=PrismsUtils.fixUnicodeString(cert.details[df].name);
			tr.insertCell(1).innerHTML=PrismsUtils.fixUnicodeString(cert.details[df].value);
		}
	},

	_detailsClicked: function(evt){
		if(!this.selectedCert)
			return;
		var target=evt.target;
		if(!target)
			target=evt.srcElement;
		var rowIdx;
		if(typeof target.rowIndex =="number")
			rowIdx=target.rowIndex;
		else if(typeof target.parentNode.rowIndex=="number")
			rowIdx=target.parentNode.rowIndex;
		else
			return;
		rowIdx--; //Ignore the header row
		if(rowIdx<0)
			this.certDetailsField.value="";
		else
			this.certDetailsField.value=this.selectedCert.details[rowIdx].descrip;
		for(var i=0;i<this.selectedCert.details.length;i++)
		{
			if(i==rowIdx)
				this.certDetailsTable.rows[i+1].style.backgroundColor="#a0a0ff";
			else
				this.certDetailsTable.rows[i+1].style.backgroundColor="#c0c0c0";
		}
	}
});
