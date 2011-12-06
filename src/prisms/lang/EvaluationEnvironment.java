/*
 * EvaluationEnvironment.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** An environment for parsing. Allows state to be kept between evaluations. */
public interface EvaluationEnvironment
{
	/** Represents a variable declared within an evaluation environment */
	public static class Variable
	{
		final Type theType;

		final String theName;

		final boolean isFinal;

		boolean isInitialized;

		Object theValue;

		/**
		 * @param type The type of the variable
		 * @param name The name for the variable
		 * @param _final Whether the variable is declared as final
		 */
		public Variable(Type type, String name, boolean _final)
		{
			theType = type;
			theName = name;
			isFinal = _final;
		}

		/** @return The name of the variable */
		public String getName()
		{
			return theName;
		}

		/** @return The type of the variable */
		public Type getType()
		{
			return theType;
		}

		/** @return Whether this variable is final or not */
		public boolean isFinal()
		{
			return isFinal;
		}

		/** @return Whether this variable has been initialized with a value or not */
		public boolean isInitialized()
		{
			return isInitialized;
		}

		/** @return The value currently assigned to the variable */
		public Object getValue()
		{
			return theValue;
		}

		@Override
		public String toString()
		{
			return theType.toString() + " " + theName;
		}
	}

	/** Used for {@link EvaluationEnvironment#getImportMethods()} */
	public static class ImportMethod
	{
		/** The type that the method is for */
		public final Class<?> type;

		/** The name of the method imported */
		public final String method;

		/**
		 * @param c The type that the method is for
		 * @param m The name of the method imported
		 */
		public ImportMethod(Class<?> c, String m)
		{
			type = c;
			method = m;
		}
	}

	/** @return Whether evaluations should only allow access to publicly declared classes, methods, and fields */
	boolean usePublicOnly();

	/** @return The class getter to use with this environment */
	ClassGetter getClassGetter();

	/**
	 * @param name The name of the variable to refer to
	 * @return The type of the variable, or null if no variable with the given name has been declared
	 */
	Type getVariableType(String name);

	/**
	 * @param name The name of the variable to refer to
	 * @param struct The parsed structure to use if an error needs to be thrown
	 * @param index The index to use if an error needs to be thrown
	 * @return The value of the given variable
	 * @throws EvaluationException If the given variable has not been defined or initialized
	 */
	Object getVariable(String name, ParsedItem struct, int index) throws EvaluationException;

	/**
	 * @param name The name of the variable to define
	 * @param type The type of the variable
	 * @param isFinal Whether the value may only be assigned once
	 * @param struct The parsed structure to use if an error needs to be thrown
	 * @param index The index to use if an error needs to be thrown
	 * @throws EvaluationException If a variable already exists with the given name
	 */
	void declareVariable(String name, Type type, boolean isFinal, ParsedItem struct, int index)
		throws EvaluationException;

	/**
	 * @param name The name of the variable to set the value of
	 * @param value The value of the variable to set
	 * @param struct The parsed structure to use if an error needs to be thrown
	 * @param index The index to use if an error needs to be thrown
	 * @throws EvaluationException If the variable cannot be droppeds for any reason
	 */
	void setVariable(String name, Object value, ParsedItem struct, int index) throws EvaluationException;

	/**
	 * @param name The name of the variable to drop
	 * @param struct The parsed structure to use if an error needs to be thrown
	 * @param index The index to use if an error needs to be thrown
	 * @throws EvaluationException If the variable cannot be assigned to the given value for any reason
	 */
	void dropVariable(String name, ParsedItem struct, int index) throws EvaluationException;

	/** @return All variables that have been declared in this environment */
	Variable [] getDeclaredVariables();

	/**
	 * Stores a function for later use
	 * 
	 * @param function The function to store
	 */
	void declareFunction(prisms.lang.types.ParsedFunctionDeclaration function);

	/** @return All functions that have been declared in this environment */
	prisms.lang.types.ParsedFunctionDeclaration[] getDeclaredFunctions();

	/**
	 * @param function The function to drop
	 * @param struct The parsed structure to use if an error needs to be thrown
	 * @param index The index to use if an error needs to be thrown
	 * @throws EvaluationException If the function cannot be dropped for any reason
	 */
	void dropFunction(prisms.lang.types.ParsedFunctionDeclaration function, ParsedItem struct, int index)
		throws EvaluationException;

	/** @param type Sets the return type that is expected from this scoped enviroment */
	void setReturnType(Type type);

	/** @return The return type that is expected within this scoped environment */
	Type getReturnType();

	/** @param types The exception types that are handleable within this scoped environment */
	void setHandledExceptionTypes(Type [] types);

	/**
	 * @param exType The exception type to check
	 * @return Whether the given exception may be thrown from within this scoped environment
	 */
	boolean canHandle(Type exType);

	/** @return The number of history items present in this environment */
	int getHistoryCount();

	/**
	 * @param index The index of the history item to get, indexed from 0 (most recent)
	 * @return The type of the given history item
	 */
	Type getHistoryType(int index);

	/**
	 * @param index The index of the history item to get, indexed from 0 (most recent)
	 * @return The value of the given history item
	 */
	Object getHistory(int index);

	/**
	 * @param type The type of the item to add to the history
	 * @param result The value of the item to add to the history
	 */
	void addHistory(Type type, Object result);

	/**
	 * Allows types to be imported so they can be referred to by name instead of fully-qualified
	 * 
	 * @param type The type to import
	 */
	void addImportType(Class<?> type);

	/**
	 * Allows all classes in a package to be imported so they can be referred to by name instead of fully-qualified
	 * 
	 * @param packageName The name of the package to import
	 */
	void addImportPackage(String packageName);

	/** @return All packages that have been imported into this environment */
	String [] getImportPackages();

	/**
	 * @param name The name of the class to get
	 * @return The class whose name matches the given name and whose type or package has been imported
	 */
	Class<?> getImportType(String name);

	/** @return All types that have been imported into this environment */
	Class<?> [] getImportTypes();

	/**
	 * Allows a static method to be imported so it can be referred to by name without a class name qualifier
	 * 
	 * @param type The type that the method belongs to
	 * @param method The name of the method to import
	 */
	void addImportMethod(Class<?> type, String method);

	/** @return All methods that have been statically imported into this environment */
	ImportMethod [] getImportMethods();

	/**
	 * @param methodName The name of the method to get
	 * @return The type that has been imported for the given name
	 */
	Class<?> getImportMethodType(String methodName);

	/**
	 * Creates a new scope or subscope with this environment as a parent
	 * 
	 * @param dependent Whether the new scope can use variables from this scope dynamically. If this is false, only
	 *        those variables that are declared as final and have been initialized will be copied into the new
	 *        environment.
	 * @return The new evaluation environment
	 */
	EvaluationEnvironment scope(boolean dependent);

	/**
	 * Creates a new evaluation environment that is a copy of this one, where modifications can be done without
	 * affecting this environment.
	 * 
	 * @return The transaction environment
	 */
	EvaluationEnvironment transact();

	/** May be called by a user interaction to cancel a currently executing loop */
	void cancel();

	/** @return Whether the user has called {@link #cancel()} on this environment */
	boolean isCanceled();

	/** Called prior to evaluation so the environment is not marked to cancel execution */
	void uncancel();
}
