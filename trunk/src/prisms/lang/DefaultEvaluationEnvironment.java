/*
 * DefaultEvaluationEnvironment.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import prisms.lang.types.ParsedFunctionDeclaration;
import prisms.util.json.JsonSerialReader.StructState;

/** Default implementation of {@link EvaluationEnvironment} */
public class DefaultEvaluationEnvironment implements EvaluationEnvironment
{
	private final DefaultEvaluationEnvironment theParent;

	private final boolean isTransaction;

	private boolean canOverride;

	private boolean isPublic;

	private ClassGetter theClassGetter;

	private java.util.HashMap<String, Variable> theVariables;

	private java.util.ArrayList<Variable> theHistory;

	private java.util.HashMap<String, Class<?>> theImportTypes;

	private java.util.HashMap<String, Class<?>> theImportMethods;

	private java.util.HashSet<String> theImportPackages;

	private java.util.ArrayList<prisms.lang.types.ParsedFunctionDeclaration> theFunctions;

	private Type theReturnType;

	private Type [] theHandledExceptionTypes;

	private volatile boolean isCanceled;

	/** Creates the environment */
	public DefaultEvaluationEnvironment()
	{
		theParent = null;
		isTransaction = false;
		isPublic = true;
		theClassGetter = new ClassGetter();
		theVariables = new java.util.LinkedHashMap<String, DefaultEvaluationEnvironment.Variable>();
		theHistory = new java.util.ArrayList<Variable>();
		theImportTypes = new java.util.HashMap<String, Class<?>>();
		theImportMethods = new java.util.HashMap<String, Class<?>>();
		theImportPackages = new java.util.HashSet<String>();
		theFunctions = new java.util.ArrayList<prisms.lang.types.ParsedFunctionDeclaration>();
	}

	DefaultEvaluationEnvironment(DefaultEvaluationEnvironment parent, ClassGetter cg, boolean override,
		boolean transaction)
	{
		theParent = parent;
		isTransaction = transaction;
		isPublic = parent == null ? true : parent.isPublic;
		canOverride = override;
		theVariables = new java.util.HashMap<String, DefaultEvaluationEnvironment.Variable>();
		theFunctions = new java.util.ArrayList<prisms.lang.types.ParsedFunctionDeclaration>();
		if(theParent == null)
			theClassGetter = cg;
		if(canOverride)
			theHistory = new java.util.ArrayList<Variable>();
		if(canOverride || transaction)
		{
			theImportTypes = new java.util.HashMap<String, Class<?>>();
			theImportMethods = new java.util.HashMap<String, Class<?>>();
			theImportPackages = new java.util.HashSet<String>();
		}
	}

	/** @param publicOnly Whether evaluations in this environment should see only public methods */
	public void setPublicOnly(boolean publicOnly)
	{
		isPublic = publicOnly;
	}

	@Override
	public boolean usePublicOnly()
	{
		return isPublic;
	}

	@Override
	public ClassGetter getClassGetter()
	{
		if(theParent != null)
			return theParent.getClassGetter();
		else
			return theClassGetter;
	}

	@Override
	public Type getVariableType(String name)
	{
		Variable vbl = getVariable(name, true);
		return vbl == null ? null : vbl.theType;
	}

	/**
	 * Checks for a variable within or beyond the current scope, which may still be larger than just this instance
	 * 
	 * @param name The name of the variable to get the type of
	 * @param lookBack Whether to look beyond the current scope
	 * @return The type of the variable, or null if none has been declared
	 */
	protected Variable getVariable(String name, boolean lookBack)
	{
		Variable vbl;
		synchronized(theVariables)
		{
			vbl = theVariables.get(name);
		}
		if(vbl == null)
		{
			if(theParent != null && (lookBack || !canOverride))
				return theParent.getVariable(name, lookBack);
			else
				return null;
		}
		return vbl;
	}

	@Override
	public Object getVariable(String name, ParsedItem struct, int index) throws EvaluationException
	{
		Variable vbl;
		synchronized(theVariables)
		{
			vbl = theVariables.get(name);
		}
		if(vbl == null)
		{
			if(theParent != null)
				return theParent.getVariable(name, struct, index);
			else
				throw new EvaluationException(name + " has not been declared", struct, index);
		}
		if(!vbl.isInitialized)
			throw new EvaluationException("Variable " + name + " has not been intialized", struct, index);
		return vbl.theValue;
	}

	@Override
	public void declareVariable(String name, Type type, boolean isFinal, ParsedItem struct, int index)
		throws EvaluationException
	{
		if(theParent != null && !canOverride)
		{
			Variable parentVar = theParent.getVariable(name, false);
			if(parentVar != null)
				throw new EvaluationException("Duplicate local variable " + name, struct, index);
		}
		synchronized(theVariables)
		{
			Variable vbl = theVariables.get(name);
			if(vbl != null)
				throw new EvaluationException("Duplicate local variable " + name, struct, index);
			theVariables.put(name, new Variable(type, name, isFinal));
		}
	}

	@Override
	public void setVariable(String name, Object value, ParsedItem struct, int index) throws EvaluationException
	{
		Variable vbl;
		synchronized(theVariables)
		{
			vbl = theVariables.get(name);
		}
		if(vbl == null && theParent != null)
		{
			vbl = theParent.getVariable(name, true);
			if(vbl != null && isTransaction)
				vbl = new Variable(vbl.theType, vbl.theName, vbl.isFinal);
		}
		if(vbl == null)
			throw new EvaluationException(name + " cannot be resolved to a variable ", struct, index);
		if(vbl.theType.isPrimitive())
		{
			if(value == null)
				throw new EvaluationException(
					"Variable of type " + vbl.theType.toString() + " cannot be assigned null", struct, index);
			Class<?> prim = Type.getPrimitiveType(value.getClass());
			if(prim == null || !vbl.theType.isAssignableFrom(prim))
				throw new EvaluationException(Type.typeString(value.getClass()) + " cannot be cast to " + vbl.theType,
					struct, index);
		}
		else
		{
			if(value != null && !vbl.theType.isAssignableFrom(value.getClass()))
				throw new EvaluationException(Type.typeString(value.getClass()) + " cannot be cast to " + vbl.theType,
					struct, index);
		}
		if(vbl.isInitialized && vbl.isFinal)
			throw new EvaluationException("Final variable " + name + " has already been assigned", struct, index);
		vbl.isInitialized = true;
		vbl.theValue = value;
	}

	@Override
	public void dropVariable(String name, ParsedItem struct, int index) throws EvaluationException
	{
		Variable vbl;
		synchronized(theVariables)
		{
			vbl = theVariables.get(name);
		}
		if(vbl == null)
		{
			if(getVariable(name, true) != null)
			{
				if(isTransaction)
				{
					theParent.dropVariable(name, struct, index);
					return;
				}
				else
					throw new EvaluationException("Variable " + name
						+ " can only be dropped from the scope in which it was declared", struct, index);
			}
			else
				throw new EvaluationException("No such variable named " + name, struct, index);
		}
		if(vbl.isFinal)
			throw new EvaluationException("The final variable " + name + " cannot be dropped", struct, index);
		synchronized(theVariables)
		{
			theVariables.remove(name);
		}
	}

	@Override
	public Variable [] getDeclaredVariables()
	{
		java.util.ArrayList<Variable> ret = new java.util.ArrayList<Variable>();
		DefaultEvaluationEnvironment env = this;
		while(env != null)
		{
			synchronized(env.theVariables)
			{
				for(Variable vbl : env.theVariables.values())
				{
					if(!ret.contains(vbl))
						ret.add(vbl);
				}
			}
			env = env.theParent;
		}
		return ret.toArray(new Variable [ret.size()]);
	}

	@Override
	public Variable getDeclaredVariable(String name)
	{
		return getVariable(name, true);
	}

	@Override
	public void declareFunction(ParsedFunctionDeclaration function)
	{
		synchronized(theFunctions)
		{
			theFunctions.add(function);
		}
	}

	@Override
	public ParsedFunctionDeclaration [] getDeclaredFunctions()
	{
		ParsedFunctionDeclaration [] ret;
		synchronized(theFunctions)
		{
			ret = theFunctions.toArray(new ParsedFunctionDeclaration [theFunctions.size()]);
		}
		if(theParent != null)
			ret = prisms.util.ArrayUtils.addAll(ret, theParent.getDeclaredFunctions());
		return ret;
	}

	@Override
	public void dropFunction(ParsedFunctionDeclaration function, ParsedItem struct, int index)
		throws EvaluationException
	{
		synchronized(theFunctions)
		{
			int fIdx = theFunctions.indexOf(function);
			if(fIdx < 0)
			{
				if(theParent != null && prisms.util.ArrayUtils.indexOf(theParent.getDeclaredFunctions(), function) >= 0)
				{
					if(isTransaction)
						theParent.dropFunction(function, struct, index);
					else
						throw new EvaluationException("Function " + function.getShortSig()
							+ " can only be dropped from the scope in which it was declared", struct, index);
				}
				else
					throw new EvaluationException("No such function " + function.getShortSig(), struct, index);
			}
			else
				theFunctions.remove(fIdx);
		}
	}

	@Override
	public void setReturnType(Type type)
	{
		theReturnType = type;
	}

	@Override
	public Type getReturnType()
	{
		if(theReturnType != null)
			return theReturnType;
		else if(theParent != null)
			return theParent.getReturnType();
		else
			return null;
	}

	@Override
	public void setHandledExceptionTypes(Type [] types)
	{
		theHandledExceptionTypes = types;
	}

	@Override
	public boolean canHandle(Type exType)
	{
		if(exType.canAssignTo(Error.class))
			return true;
		if(exType.canAssignTo(RuntimeException.class))
			return true;
		if(theHandledExceptionTypes != null)
		{
			for(Type et : theHandledExceptionTypes)
				if(et.isAssignable(exType))
					return true;
			return false;
		}
		else if(theParent != null)
			return theParent.canHandle(exType);
		else
			return false;
	}

	@Override
	public int getHistoryCount()
	{
		if(theParent != null)
			return theParent.getHistoryCount();
		synchronized(theHistory)
		{
			return theHistory.size();
		}
	}

	@Override
	public Type getHistoryType(int index)
	{
		if(theParent != null)
			return theParent.getHistoryType(index);
		Variable vbl;
		synchronized(theHistory)
		{
			vbl = theHistory.get(theHistory.size() - index - 1);
		}
		return vbl.theType;
	}

	@Override
	public Object getHistory(int index)
	{
		if(theParent != null)
			return theParent.getHistory(index);
		Variable vbl;
		synchronized(theHistory)
		{
			vbl = theHistory.get(theHistory.size() - index - 1);
		}
		return vbl.theValue;
	}

	@Override
	public void addHistory(Type type, Object result)
	{
		if(theParent != null)
		{
			if(isTransaction)
				theParent.addHistory(type, result);
			else
				throw new IllegalStateException("History can only be added to a root-level evaluation environment");
		}
		Variable vbl = new Variable(type, "%", false);
		vbl.theValue = result;
		synchronized(theHistory)
		{
			theHistory.add(vbl);
		}
	}

	@Override
	public void clearHistory()
	{
		theHistory.clear();
	}

	@Override
	public void addImportPackage(String packageName)
	{
		if(theParent != null && !isTransaction)
			throw new IllegalStateException("Imports may only be used at the top level");
		synchronized(theImportPackages)
		{
			theImportPackages.add(packageName);
		}
	}

	@Override
	public String [] getImportPackages()
	{
		synchronized(theImportPackages)
		{
			return theImportPackages.toArray(new String [theImportPackages.size()]);
		}
	}

	@Override
	public void clearImportPackages()
	{
		theImportPackages.clear();
	}

	@Override
	public void addImportType(Class<?> type)
	{
		if(theParent != null && !isTransaction)
			throw new IllegalStateException("Imports may only be used at the top level");
		String name = type.getName();
		int dotIdx = name.lastIndexOf('.');
		if(dotIdx >= 0)
			name = name.substring(dotIdx + 1);
		synchronized(theImportTypes)
		{
			theImportTypes.put(name, type);
		}
	}

	@Override
	public Class<?> getImportType(String name)
	{
		if(theParent != null)
			return theParent.getImportType(name);
		Class<?> ret;
		synchronized(theImportTypes)
		{
			ret = theImportTypes.get(name);
		}
		if(ret != null)
			return ret;
		synchronized(theImportPackages)
		{
			for(String pkg : theImportPackages)
				try
				{
					ret = Class.forName(pkg + "." + name);
					return ret;
				} catch(ClassNotFoundException e)
				{}
		}
		return null;
	}

	@Override
	public Class<?> [] getImportTypes()
	{
		synchronized(theImportTypes)
		{
			return theImportTypes.values().toArray(new Class [theImportTypes.size()]);
		}
	}

	@Override
	public void clearImportTypes()
	{
		theImportTypes.clear();
	}

	@Override
	public void addImportMethod(Class<?> type, String method)
	{
		if(theParent != null && !isTransaction)
			throw new IllegalStateException("Imports may only be used at the top level");
		synchronized(theImportMethods)
		{
			theImportMethods.put(method, type);
		}
	}

	@Override
	public Class<?> getImportMethodType(String methodName)
	{
		if(theParent != null)
			return theParent.getImportMethodType(methodName);
		synchronized(theImportMethods)
		{
			return theImportMethods.get(methodName);
		}
	}

	@Override
	public ImportMethod [] getImportMethods()
	{
		synchronized(theImportMethods)
		{
			ImportMethod [] ret = new ImportMethod [theImportMethods.size()];
			int i = 0;
			for(java.util.Map.Entry<String, Class<?>> entry : theImportMethods.entrySet())
				ret[i++] = new ImportMethod(entry.getValue(), entry.getKey());
			return ret;
		}
	}

	@Override
	public void clearImportMethods()
	{
		theImportMethods.clear();
	}

	@Override
	public EvaluationEnvironment scope(boolean dependent)
	{
		if(dependent)
			return new DefaultEvaluationEnvironment(this, null, false, false);
		DefaultEvaluationEnvironment root = this;
		while(root.theParent != null)
			root = root.theParent;
		DefaultEvaluationEnvironment ret = new DefaultEvaluationEnvironment(null, getClassGetter(), true, false);
		for(String pkg : root.theImportPackages)
			ret.theImportPackages.add(pkg);
		for(java.util.Map.Entry<String, Class<?>> entry : root.theImportTypes.entrySet())
			ret.theImportTypes.put(entry.getKey(), entry.getValue());
		for(java.util.Map.Entry<String, Class<?>> entry : root.theImportMethods.entrySet())
			ret.theImportMethods.put(entry.getKey(), entry.getValue());
		for(Variable var : root.theHistory)
			ret.theHistory.add(var);
		for(ParsedFunctionDeclaration func : getDeclaredFunctions())
			ret.theFunctions.add(func);
		root = this;
		while(root != null)
		{
			for(java.util.Map.Entry<String, Variable> var : root.theVariables.entrySet())
				if(var.getValue().isFinal && var.getValue().isInitialized)
					ret.theVariables.put(var.getKey(), var.getValue());
			root = root.theParent;
		}
		return ret;
	}

	@Override
	public EvaluationEnvironment transact()
	{
		return new DefaultEvaluationEnvironment(this, null, false, true);
	}

	@Override
	public void cancel()
	{
		isCanceled = true;
	}

	@Override
	public boolean isCanceled()
	{
		return isCanceled || (theParent != null && theParent.isCanceled());
	}

	@Override
	public void uncancel()
	{
		isCanceled = false;
	}

	@Override
	public Variable [] save(OutputStream out) throws IOException
	{
		java.io.OutputStreamWriter charWriter = new java.io.OutputStreamWriter(out);
		prisms.util.json.JsonStreamWriter jsonWriter = new prisms.util.json.JsonStreamWriter(charWriter);
		prisms.util.HexStreamWriter hexWriter = new prisms.util.HexStreamWriter();

		jsonWriter.startObject();
		jsonWriter.startProperty("variables");
		jsonWriter.startArray();
		java.util.ArrayList<Variable> fails = new java.util.ArrayList<Variable>();
		for(Variable var : theVariables.values())
		{
			if(var.theValue != null && !(var.theValue instanceof java.io.Serializable))
			{
				fails.add(var);
				continue;
			}
			try
			{
				writeVar(var, jsonWriter, hexWriter, charWriter, false);
			} catch(java.io.ObjectStreamException e)
			{
				e.printStackTrace();
				fails.add(var);
			}
		}
		jsonWriter.endArray();

		jsonWriter.startProperty("functions");
		jsonWriter.startArray();
		for(ParsedFunctionDeclaration func : theFunctions)
			jsonWriter.writeString(func.getMatch().text);
		jsonWriter.endArray();

		jsonWriter.startProperty("importPackages");
		jsonWriter.startArray();
		for(String pkg : theImportPackages)
			jsonWriter.writeString(pkg);
		jsonWriter.endArray();

		jsonWriter.startProperty("importTypes");
		jsonWriter.startArray();
		for(Class<?> type : theImportTypes.values())
			jsonWriter.writeString(type.getName());
		jsonWriter.endArray();

		jsonWriter.startProperty("importMethods");
		jsonWriter.startArray();
		for(java.util.Map.Entry<String, Class<?>> entry : theImportMethods.entrySet())
		{
			jsonWriter.startObject();
			jsonWriter.startProperty("methodName");
			jsonWriter.writeString(entry.getKey());
			jsonWriter.startProperty("type");
			jsonWriter.writeString(entry.getValue().getName());
			jsonWriter.endObject();
		}
		jsonWriter.endArray();

		jsonWriter.startProperty("history");
		jsonWriter.startArray();
		for(Variable var : theHistory)
		{
			if(var.theValue != null && !(var.theValue instanceof java.io.Serializable))
			{
				fails.add(var);
				continue;
			}
			try
			{
				writeVar(var, jsonWriter, hexWriter, charWriter, true);
			} catch(java.io.ObjectStreamException e)
			{
				e.printStackTrace();
				fails.add(var);
			}
		}
		jsonWriter.endArray();

		jsonWriter.endObject();
		charWriter.close();
		return fails.toArray(new Variable [fails.size()]);
	}

	private void writeVar(Variable var, prisms.util.json.JsonStreamWriter jsonWriter,
		prisms.util.HexStreamWriter hexWriter, java.io.Writer charWriter, boolean isShort) throws IOException
	{
		jsonWriter.startObject();
		jsonWriter.startProperty("name");
		jsonWriter.writeString(var.theName);
		jsonWriter.startProperty("type");
		jsonWriter.writeString(var.theType.toString());
		if(!isShort)
		{
			jsonWriter.startProperty("final");
			jsonWriter.writeBoolean(var.isFinal);
			jsonWriter.startProperty("initialized");
			jsonWriter.writeBoolean(var.isInitialized);
		}
		jsonWriter.startProperty("value");
		hexWriter.setWrapped(jsonWriter.writeStringAsWriter());
		ObjectOutputStream oos = new ObjectOutputStream(hexWriter);
		oos.writeObject(var.theValue);
		oos.close();
		jsonWriter.endObject();
	}

	@Override
	public void load(InputStream in, PrismsParser parser) throws IOException
	{
		java.io.InputStreamReader charReader = new java.io.InputStreamReader(in);
		prisms.util.json.JsonSerialReader jsonReader = new prisms.util.json.JsonSerialReader(charReader);

		try (prisms.util.HexStreamReader hexReader = new prisms.util.HexStreamReader())
		{
			StructState rootState = jsonReader.startObject();
			if(!"variables".equals(jsonReader.getNextProperty()))
				throw new IOException("Unrecognizable JSON stream--no variables");
			StructState arrayState = jsonReader.startArray();
			while(jsonReader.getNextItem(true, false) instanceof prisms.util.json.JsonSerialReader.ObjectItem)
			{
				StructState varState = jsonReader.save();
				try
				{
					Variable var = parseNextVariable(jsonReader, hexReader, parser);
					theVariables.put(var.getName(), var);
				} catch(IOException e)
				{
					e.printStackTrace();
				} finally
				{
					jsonReader.endObject(varState);
				}
			}
			jsonReader.endArray(arrayState);

			if(!"functions".equals(jsonReader.getNextProperty()))
				throw new IOException("Unrecognizable JSON stream--no functions");
			arrayState = jsonReader.startArray();
			String text = jsonReader.parseString();
			while(text != null)
			{
				ParseStructRoot root = new ParseStructRoot(text);
				ParsedFunctionDeclaration func;
				try
				{
					func = (ParsedFunctionDeclaration) parser.parseStructures(root, parser.parseMatches(text))[0];
					theFunctions.add(func);
				} catch(ParseException e)
				{
					e.printStackTrace();
				}
				text = jsonReader.parseString();
			}
			jsonReader.endArray(arrayState);

			if(!"importPackages".equals(jsonReader.getNextProperty()))
				throw new IOException("Unrecognizable JSON stream--no importPackages");
			arrayState = jsonReader.startArray();
			String next = jsonReader.parseString();
			while(next != null)
			{
				theImportPackages.add(next);
				next = jsonReader.parseString();
			}
			jsonReader.endArray(arrayState);

			if(!"importTypes".equals(jsonReader.getNextProperty()))
				throw new IOException("Unrecognizable JSON stream--no importTypes");
			arrayState = jsonReader.startArray();
			text = jsonReader.parseString();
			while(text != null)
			{
				String name = text;
				int dotIdx = name.lastIndexOf('.');
				if(dotIdx >= 0)
					name = name.substring(dotIdx + 1);
				try
				{
					theImportTypes.put(name, Class.forName(text));
				} catch(ClassNotFoundException e)
				{
					e.printStackTrace();
				}
				text = jsonReader.parseString();
			}
			jsonReader.endArray(arrayState);

			if(!"importMethods".equals(jsonReader.getNextProperty()))
				throw new IOException("Unrecognizable JSON stream--no importMethods");
			arrayState = jsonReader.startArray();
			while(jsonReader.getNextItem(true, false) instanceof prisms.util.json.JsonSerialReader.ObjectItem)
			{
				StructState objState = jsonReader.save();
				if(!"methodName".equals(jsonReader.getNextProperty()))
					throw new IOException("Unrecognizable JSON stream--no methodName on importMethod");
				String name = jsonReader.parseString();
				if(!"type".equals(jsonReader.getNextProperty()))
					throw new IOException("Unrecognizable JSON stream--no type on importMethod");
				String className = jsonReader.parseString();
				try
				{
					theImportMethods.put(name, Class.forName(className));
				} catch(ClassNotFoundException e)
				{
					e.printStackTrace();
				}
				jsonReader.endObject(objState);
			}
			jsonReader.endArray(arrayState);

			if(!"history".equals(jsonReader.getNextProperty()))
				throw new IOException("Unrecognizable JSON stream--no history");
			arrayState = jsonReader.startArray();
			while(jsonReader.getNextItem(true, false) instanceof prisms.util.json.JsonSerialReader.ObjectItem)
			{
				StructState histState = jsonReader.save();
				try
				{
					Variable var = parseNextVariable(jsonReader, hexReader, parser);
					theHistory.add(var);
				} catch(IOException e)
				{
					e.printStackTrace();
				} finally
				{
					jsonReader.endObject(histState);
				}
			}
			jsonReader.endArray(arrayState);
			jsonReader.endObject(rootState);
		} catch(ClassCastException e)
		{
			throw new IOException("Could not parse environment", e);
		} catch(prisms.util.json.SAJParser.ParseException e)
		{
			throw new IOException("Could not parse environment", e);
		}
	}

	private Variable parseNextVariable(prisms.util.json.JsonSerialReader jsonReader,
		prisms.util.HexStreamReader hexReader, PrismsParser parser) throws IOException,
		prisms.util.json.SAJParser.ParseException
	{
		if(!"name".equals(jsonReader.getNextProperty()))
			throw new IOException("Unrecognizable JSON stream--variable has no name");
		String name = jsonReader.parseString();
		if(!"type".equals(jsonReader.getNextProperty()))
			throw new IOException("Unrecognizable JSON stream--variable has no type");
		String typeName = jsonReader.parseString();
		ParseStructRoot root = new ParseStructRoot(typeName);
		Type type;
		try
		{
			type = parser.parseStructures(root, parser.parseMatches(typeName))[0].evaluate(this, true, false).getType();
		} catch(ParseException e)
		{
			throw new IOException("Could not parse type \"" + typeName + "\" of variable " + name, e);
		} catch(EvaluationException e)
		{
			throw new IOException("Could not parse type \"" + typeName + "\" of variable " + name, e);
		}
		String prop = jsonReader.getNextProperty();
		boolean isFinal = false;
		boolean initialized = true;
		if(prop.equals("final"))
		{
			isFinal = jsonReader.parseBoolean();
			prop = jsonReader.getNextProperty();
		}
		if(prop.equals("initialized"))
		{
			initialized = jsonReader.parseBoolean();
			prop = jsonReader.getNextProperty();
		}
		if(!prop.equals("value"))
			throw new IOException("Unrecognizable JSON stream--variable has no value");
		Object value;
		try
		{
			hexReader.setWrapped(jsonReader.parseStringAsReader());
			value = new ObjectInputStream(hexReader).readObject();
		} catch(Exception e)
		{
			throw new IOException("Could not parse variable " + name, e);
		}
		Variable ret = new Variable(type, name, isFinal);
		ret.theValue = value;
		ret.isInitialized = initialized;
		return ret;
	}
}
