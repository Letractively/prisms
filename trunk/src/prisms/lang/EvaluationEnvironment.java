/*
 * EvaluationEnvironment.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** An environment for parsing. Allows state to be kept between evaluations. */
public interface EvaluationEnvironment
{
	/**
	 * @return Whether evaluations should only allow access to publicly declared classes, methods,
	 *         and fields
	 */
	boolean usePublicOnly();

	/**
	 * @param name The name of the variable to refer to
	 * @return The type of the variable, or null if no variable with the given name has been
	 *         declared
	 */
	Class<?> getVariableType(String name);

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
	void declareVariable(String name, Class<?> type, boolean isFinal, ParsedItem struct, int index)
		throws EvaluationException;

	/**
	 * @param name The name of the variable to set the value of
	 * @param value The value of the variable to set
	 * @param struct The parsed structure to use if an error needs to be thrown
	 * @param index The index to use if an error needs to be thrown
	 * @throws EvaluationException If the variable cannot be assigned to the given value for any
	 *         reason
	 */
	void setVariable(String name, Object value, ParsedItem struct, int index)
		throws EvaluationException;

	/** @return The number of history items present in this environment */
	int getHistoryCount();

	/**
	 * @param index The index of the history item to get, indexed from 0 (most recent)
	 * @return The type of the given history item
	 */
	Class<?> getHistoryType(int index);

	/**
	 * @param index The index of the history item to get, indexed from 0 (most recent)
	 * @return The value of the given history item
	 */
	Object getHistory(int index);

	/**
	 * @param type The type of the item to add to the history
	 * @param result The value of the item to add to the history
	 */
	void addHistory(Class<?> type, Object result);

	/**
	 * Allows types to be imported so they can be referred to by name instead of fully-qualified
	 * 
	 * @param type The type to import
	 */
	void addImportType(Class<?> type);

	/**
	 * Allows all classes in a package to be imported so they can be referred to by name instead of
	 * fully-qualified
	 * 
	 * @param packageName The name of the package to import
	 */
	void addImportPackage(String packageName);

	/**
	 * @param name The name of the class to get
	 * @return The class whose name matches the given name and whose type or package has been
	 *         imported
	 */
	Class<?> getImportType(String name);

	/**
	 * Allows a static method to be imported so it can be referred to by name without a class name
	 * qualifier
	 * 
	 * @param type The type that the method belongs to
	 * @param method The name of the method to import
	 */
	void addImportMethod(Class<?> type, String method);

	/**
	 * @param methodName The name of the method to get
	 * @return The type that has been imported for the given name
	 */
	Class<?> getImportMethodType(String methodName);

	/**
	 * Creates a new scope or subscope with this environment as a parent
	 * 
	 * @param canOverride Whether the new scope can create new local variables with the same names
	 *        as those in this scope
	 * @return The new evaluation environment
	 */
	EvaluationEnvironment scope(boolean canOverride);
}
