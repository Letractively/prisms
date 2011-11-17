/*
 * DefaultEvaluationEnvironment.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** Default implementation of {@link EvaluationEnvironment} */
public class DefaultEvaluationEnvironment implements EvaluationEnvironment
{
	private static class Variable
	{
		final Class<?> theType;

		final boolean isFinal;

		boolean isInitialized;

		Object theValue;

		Variable(Class<?> type, boolean _final)
		{
			theType = type;
			isFinal = _final;
		}
	}

	private final DefaultEvaluationEnvironment theParent;

	private boolean canOverride;

	private java.util.HashMap<String, Variable> theVariables;

	private java.util.ArrayList<Variable> theHistory;

	private java.util.HashMap<String, Class<?>> theImportTypes;

	private java.util.HashMap<String, Class<?>> theImportMethods;

	private java.util.HashSet<String> theImportPackages;

	/** Creates the environment */
	public DefaultEvaluationEnvironment()
	{
		theParent = null;
		theVariables = new java.util.HashMap<String, DefaultEvaluationEnvironment.Variable>();
		theHistory = new java.util.ArrayList<Variable>();
		theImportTypes = new java.util.HashMap<String, Class<?>>();
		theImportMethods = new java.util.HashMap<String, Class<?>>();
		theImportPackages = new java.util.HashSet<String>();
	}

	DefaultEvaluationEnvironment(DefaultEvaluationEnvironment parent, boolean override)
	{
		theParent = parent;
		canOverride = override;
		theVariables = new java.util.HashMap<String, DefaultEvaluationEnvironment.Variable>();
	}

	public Class<?> getVariableType(String name)
	{
		return getVariableType(name, true);
	}

	/**
	 * Checks for a variable within or beyond the current scope, which may still be larger than just
	 * this instance
	 * 
	 * @param name The name of the variable to get the type of
	 * @param lookBack Whether to look beyond the current scope
	 * @return The type of the variable, or null if none has been declared
	 */
	protected Class<?> getVariableType(String name, boolean lookBack)
	{
		Variable vbl;
		synchronized(theVariables)
		{
			vbl = theVariables.get(name);
		}
		if(vbl == null)
		{
			if(theParent != null && (lookBack || !canOverride))
				return theParent.getVariableType(name, lookBack);
			else
				return null;
		}
		return vbl.theType;
	}

	public Object getVariable(String name, ParseStruct struct, int index)
		throws EvaluationException
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
			throw new EvaluationException(name + " has not been intialized", struct, index);
		return vbl.theValue;
	}

	public void declareVariable(String name, Class<?> type, boolean isFinal, ParseStruct struct,
		int index) throws EvaluationException
	{
		if(theParent != null && !canOverride)
		{
			Class<?> parentType = theParent.getVariableType(name, false);
			if(parentType != null)
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

	public void setVariable(String name, Object value, ParseStruct struct, int index)
		throws EvaluationException
	{
		Variable vbl;
		synchronized(theVariables)
		{
			vbl = theVariables.get(name);
		}
		if(vbl == null)
		{
			if(theParent != null)
			{
				theParent.setVariable(name, value, struct, index);
				return;
			}
			else
				throw new EvaluationException(name + " cannot be resolved to a variable ", struct,
					index);
		}
		if(vbl.theType.isPrimitive())
		{
			if(value == null)
				throw new EvaluationException("Variable of type "
					+ PrismsEvaluator.EvalResult.typeString(vbl.theType)
					+ " cannot be assigned null", struct, index);
			Class<?> prim = getPrimitiveType(value.getClass());
			if(prim == null || !DefaultJavaEvaluator.canAssign(vbl.theType, prim))
				throw new EvaluationException(PrismsEvaluator.EvalResult.typeString(value
					.getClass())
					+ " cannot be cast to "
					+ PrismsEvaluator.EvalResult.typeString(vbl.theType), struct, index);
		}
		else
		{
			if(value != null && !DefaultJavaEvaluator.canAssign(vbl.theType, value.getClass()))
				throw new EvaluationException(PrismsEvaluator.EvalResult.typeString(value
					.getClass())
					+ " cannot be cast to "
					+ PrismsEvaluator.EvalResult.typeString(vbl.theType), struct, index);
		}
		if(vbl.isInitialized && vbl.isFinal)
			throw new EvaluationException("Final variable " + name + " has already been assigned",
				struct, index);
		vbl.isInitialized = true;
		vbl.theValue = value;
	}

	static Class<?> getPrimitiveType(Class<?> wrapper)
	{
		if(wrapper.equals(Double.class))
			return Double.TYPE;
		else if(wrapper.equals(Float.class))
			return Float.TYPE;
		else if(wrapper.equals(Long.class))
			return Long.TYPE;
		else if(wrapper.equals(Integer.class))
			return Integer.TYPE;
		else if(wrapper.equals(Character.class))
			return Character.TYPE;
		else if(wrapper.equals(Short.class))
			return Short.TYPE;
		else if(wrapper.equals(Byte.class))
			return Byte.TYPE;
		else if(wrapper.equals(Boolean.class))
			return Boolean.TYPE;
		else
			return null;
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

	public Class<?> getHistoryType(int index)
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

	public void addHistory(Class<?> type, Object result)
	{
		if(theParent != null)
			throw new IllegalStateException(
				"History can only be added to a root-level evaluation environment");
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

	public EvaluationEnvironment scope(boolean override)
	{
		return new DefaultEvaluationEnvironment(this, override);
	}
}
