/*
 * DefaultEvaluationEnvironment.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

import prisms.lang.types.ParsedFunctionDeclaration;

/** Default implementation of {@link EvaluationEnvironment} */
public class DefaultEvaluationEnvironment implements EvaluationEnvironment
{
	private static class Variable
	{
		final Type theType;

		final boolean isFinal;

		boolean isInitialized;

		Object theValue;

		Variable(Type type, boolean _final)
		{
			theType = type;
			isFinal = _final;
		}

		@Override
		public String toString()
		{
			return theType.toString();
		}
	}

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

	DefaultEvaluationEnvironment(DefaultEvaluationEnvironment parent, boolean override, boolean transaction)
	{
		theParent = parent;
		isTransaction = transaction;
		isPublic = parent == null ? true : parent.isPublic;
		canOverride = override;
		theVariables = new java.util.HashMap<String, DefaultEvaluationEnvironment.Variable>();
		theFunctions = new java.util.ArrayList<prisms.lang.types.ParsedFunctionDeclaration>();
		if(canOverride)
		{
			theHistory = new java.util.ArrayList<Variable>();
			theImportTypes = new java.util.HashMap<String, Class<?>>();
			theImportMethods = new java.util.HashMap<String, Class<?>>();
			theImportPackages = new java.util.HashSet<String>();
		}
	}

	public boolean usePublicOnly()
	{
		return isPublic;
	}

	public ClassGetter getClassGetter()
	{
		if(theParent != null)
			return theParent.getClassGetter();
		else
			return theClassGetter;
	}

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
			theVariables.put(name, new Variable(type, isFinal));
		}
	}

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
				vbl = new Variable(vbl.theType, vbl.isFinal);
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
					theParent.dropVariable(name, struct, index);
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

	public String [] getDeclaredVariableNames()
	{
		java.util.ArrayList<String> ret = new java.util.ArrayList<String>();
		DefaultEvaluationEnvironment env = this;
		while(env != null)
		{
			synchronized(env.theVariables)
			{
				for(String name : env.theVariables.keySet())
				{
					if(!ret.contains(name))
						ret.add(name);
				}
			}
			env = env.theParent;
		}
		return ret.toArray(new String [ret.size()]);
	}

	public void declareFunction(ParsedFunctionDeclaration function)
	{
		synchronized(theFunctions)
		{
			theFunctions.add(function);
		}
	}

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
			theFunctions.remove(fIdx);
		}
	}

	public void setReturnType(Type type)
	{
		theReturnType = type;
	}

	public Type getReturnType()
	{
		if(theReturnType != null)
			return theReturnType;
		else if(theParent != null)
			return theParent.getReturnType();
		else
			return null;
	}

	public void setHandledExceptionTypes(Type [] types)
	{
		theHandledExceptionTypes = types;
	}

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

	public int getHistoryCount()
	{
		if(theParent != null)
			return theParent.getHistoryCount();
		synchronized(theHistory)
		{
			return theHistory.size();
		}
	}

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

	public void addHistory(Type type, Object result)
	{
		if(theParent != null)
		{
			if(isTransaction)
				theParent.addHistory(type, result);
			else
				throw new IllegalStateException("History can only be added to a root-level evaluation environment");
		}
		Variable vbl = new Variable(type, false);
		vbl.theValue = result;
		synchronized(theHistory)
		{
			theHistory.add(vbl);
		}
	}

	public void addImportType(Class<?> type)
	{
		if(theParent != null)
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

	public void addImportPackage(String packageName)
	{
		if(theParent != null)
			throw new IllegalStateException("Imports may only be used at the top level");
		synchronized(theImportPackages)
		{
			theImportPackages.add(packageName);
		}
	}

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

	public void addImportMethod(Class<?> type, String method)
	{
		if(theParent != null)
			throw new IllegalStateException("Imports may only be used at the top level");
		synchronized(theImportMethods)
		{
			theImportMethods.put(method, type);
		}
	}

	public Class<?> getImportMethodType(String methodName)
	{
		if(theParent != null)
			return theParent.getImportMethodType(methodName);
		synchronized(theImportMethods)
		{
			return theImportMethods.get(methodName);
		}
	}

	public EvaluationEnvironment scope(boolean dependent)
	{
		if(dependent)
			return new DefaultEvaluationEnvironment(this, false, false);
		DefaultEvaluationEnvironment root = this;
		while(root.theParent != null)
			root = root.theParent;
		DefaultEvaluationEnvironment ret = new DefaultEvaluationEnvironment(null, true, false);
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

	public EvaluationEnvironment transact()
	{
		return new DefaultEvaluationEnvironment(this, false, true);
	}
}
