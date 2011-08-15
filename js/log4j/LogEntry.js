
__dojo.provide("log4j.LogEntry");
__dojo.declare("log4j.LogEntry", [__dijit._Widget, __dijit._Templated], {

	templatePath: "__webContentRoot/view/log4j/templates/logEntry.html",

	defaultSettings: {
		id: true,
		time: true,
		instance: false,
		app: false,
		client: false,
		user: true,
		logger: true,
		tracking: false,
		hideStack: true,
		showDuplicate: true
	},

	expanded: true,

	stackExpanded: false,

	linesNoCollapse: 2,

	linesInCollapsed: 1,

	postCreate: function(){
		this.inherited(arguments);
		this.settings=this.defaultSettings;
	},

	setValue: function(entry){
		this.entry=entry;
		this.render();
	},

	render: function(){
		var entry=this.entry;
		var msg="";
		
		if(entry.message)
			msg+=entry.message;
		else
			msg+="(No message)";
		var i;
		if(entry.stackTrace)
		{
			this.stackTraceNode.style.display="block";
			this.stackTraceNode.innerHTML="";
			var stack=entry.stackTrace;
			for(i=0;i<stack.length;i++)
			{
				if(stack.charAt(i)!='\n' && stack.charAt(i)!='\r')
					break;
			}
			if(i>0)
				stack=stack.substring(i);
			for(i=stack.length-1;i>=0;i--)
				if(stack.charAt(i)!='\n' && stack.charAt(i)!='\r'
					&& stack.charAt(i)!=' ' && stack.charAt(i)!='\t')
					break;
			if(i<stack.length-1)
				stack=stack.substring(0, i+1);
			var lines=1;
			for(i=0;lines<=this.linesNoCollapse && i<stack.length;i++)
				if(stack.charAt(i)=='\n')
					lines++;

			this.stackExpandNode.style.display=lines>this.linesNoCollapse ? "inline" : "none";
			if(this.stackExpanded || lines<=this.linesNoCollapse)
				this.stackExpandNode.src="__webContentRoot/rsrc/icons/prisms/collapseNode.png";
			else
			{
				this.stackExpandNode.src="__webContentRoot/rsrc/icons/prisms/expandNode.png";
				lines=this.linesInCollapsed;
				for(i=0;i<stack.length && lines>0;i++)
					if(stack.charAt(i)=='\n')
						lines--;
				if(i<stack.length)
					stack=stack.substring(0, i);
			}
			this.stackTraceNode.innerHTML=this.fix(" "+stack);
			this.stackTraceNode.insertBefore(this.stackExpandNode, this.stackTraceNode.childNodes[0]);
		}
		else
			this.stackTraceNode.style.display="none";
		for(i=msg.length;i>0;i--)
		{
			if(msg.charAt(i-1)!='\n' && msg.charAt(i-1)!='\r' && msg.charAt(i-1)!=' '
				&& msg.charAt(i-1)!='\t')
				break;
		}
		if(i<msg.length-1)
			msg=msg.substring(0, i);
		
		var lines=1;
		for(i=0;lines<=this.linesNoCollapse && i<msg.length;i++)
			if(msg.charAt(i)=='\n')
				lines++;
		this.expandNode.style.display=lines>this.linesNoCollapse ? "inline" : "none";
		if(this.expanded || lines<=this.linesNoCollapse)
			this.expandNode.src="__webContentRoot/rsrc/icons/prisms/collapseNode.png";
		else
		{
			this.expandNode.src="__webContentRoot/rsrc/icons/prisms/expandNode.png";
			lines=this.linesInCollapsed;
			for(i=0;i<msg.length && lines>0;i++)
				if(msg.charAt(i)=='\n')
					lines--;
			if(i<msg.length)
				msg=msg.substring(0, i);
		}

		this.messageNode.innerHTML=this.fix("\t"+msg);
		this.messageNode.insertBefore(this.expandNode, this.messageNode.childNodes[0]);
		var br=document.createElement("br");
		this.messageNode.insertBefore(br, this.expandNode);
		this.messageNode.insertBefore(this.iconNode, br);
		var s=this.settings;
		if(s.id)
		{
			var span=document.createElement("span");
			span.innerHTML=entry.id+" ";
			span.style.display="inline";
			this.messageNode.insertBefore(span, br);
		}
		if(s.time)
		{
			var span=document.createElement("span");
			span.innerHTML=entry.time+" ";
			span.style.display="inline";
			span.style.fontWeight="bold";
			this.messageNode.insertBefore(span, br);
		}
		{
			var span=document.createElement("span");
			span.innerHTML=entry.level+" ";
			span.style.display="inline";
			span.style.fontWeight="bold";
			span.style.color=entry.levelColor;
			this.messageNode.insertBefore(span, br);
		}
		if(s.logger)
		{
			var span=document.createElement("span");
			span.innerHTML=entry.logger+" ";
			span.style.display="inline";
			span.style.fontWeight="bold";
			span.style.color="#00d000";
			this.messageNode.insertBefore(span, br);
		}
		if(s.user)
		{
			var span=document.createElement("span");
			if(entry.user)
				span.innerHTML=entry.user+" ";
			else
				span.innerHTML="(No User) ";
			span.style.display="inline";
			span.style.fontWeight="bold";
			span.style.color="#0000ff";
			this.messageNode.insertBefore(span, br);
		}

		if(s.instance)
		{
			var span=document.createElement("span");
			span.innerHTML+=entry.instance+" ";
			this.messageNode.insertBefore(span, br);
		}
		if(s.app)
		{
			var span=document.createElement("span");
			if(entry.app)
				span.innerHTML+=entry.app+" ";
			else
				span.innerHTML+="(No App) ";
			this.messageNode.insertBefore(span, br);
		}
		if(s.client)
		{
			var span=document.createElement("span");
			if(entry.client)
				span.innerHTML+=entry.client+" ";
			else if(entry.app)
				span.innerHTML+="(No Client) ";
			this.messageNode.insertBefore(span, br);
		}
		if(s.tracking && entry.tracking)
		{
			var span=document.createElement("span");
			span.innerHTML+="tracking:"+entry.tracking;
			span.style.display="inline";
			span.style.textDecoration="underline";
			this.messageNode.insertBefore(span, br);
			var text=document.createTextElement(" ");
			this.messageNode.insertBefore(text, br);
		}
		if(s.showDuplicate && typeof entry.duplicate=="number")
		{
			var span=document.createElement("span");
			span.innerHTML+="(duplicate)";
			this.messageNode.insertBefore(span, br);
		}

		var title="Instance:"+entry.instance+"            \n";
		title+="Application:"+(entry.app ? entry.app : "None")+"            \n";
		if(entry.app)
			title+="Client:"+(entry.client ? entry.client : "None")+"            \n";
		if(entry.client)
			title+="User:"+(entry.user ? entry.user : "None")+"            \n";
		if(entry.client)
			title+="SessionID:"+(entry.session ? entry.session : "None")+"            \n";
		title+="Logger:"+entry.logger+"            \n";
		title+="TrackingInfo:"+(entry.tracking ? entry.tracking : "Not Available")+"            \n";

		this.domNode.title=title;
	},

	fix: function(str){
		var ret="";
		for(var i=0;i<str.length;i++)
		{
			if(str.charAt(i)=='\n')
				ret+="\n\t";
			else if(str.charAt(i)==' ' && i<str.length-1 && str.charAt(i+1)==' ')
			{
				ret+="&nbsp;";
				var j;
				for(j=i+1;j<str.length && str.charAt(j)==' ';j++)
					ret+="&nbsp;";
				i=j-1;
			}
			else
				ret+=str.charAt(i);
		}
		ret=PrismsUtils.fixUnicodeString(ret);
		return ret;
	},

	_toggleExpand: function(){
		this.expanded=!this.expanded;
		this.render();
	},

	_toggleStackExpand: function(){
		this.stackExpanded=!this.stackExpanded;
		this.render();
	},

	remove: function(){
		this.domNode.innerHTML="";
		this.domNode.parentNode.removeChild(this.domNode);
	}
});
