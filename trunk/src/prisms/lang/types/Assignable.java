/*
 * Assignable.java Created Nov 18, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Marks a parsed item as being capable of being assigned to a value */
public abstract class Assignable extends prisms.lang.ParsedItem
{
	/**
	 * Retrieves the current value of the variable being assigned. This is used for assignment operators such as "+="
	 * where the initial value is used to compute the value assigned.
	 * 
	 * @param env The environment to get the value from
	 * @param assign The assignment operator that the assignment is for
	 * @return The current value of the variable being assigned
	 * @throws prisms.lang.EvaluationException If an error occurs getting the current value
	 */
	public abstract prisms.lang.EvaluationResult getValue(prisms.lang.EvaluationEnvironment env,
		ParsedAssignmentOperator assign) throws prisms.lang.EvaluationException;

	/**
	 * Assigns the value to this variable
	 * 
	 * @param value The type and value to assign
	 * @param env The environment to assign the variable in
	 * @param assign The assignment operator that is assigning the variable
	 * @throws prisms.lang.EvaluationException If an error occurs assigning the value
	 */
	public abstract void assign(prisms.lang.EvaluationResult value, prisms.lang.EvaluationEnvironment env,
		ParsedAssignmentOperator assign) throws prisms.lang.EvaluationException;
}
